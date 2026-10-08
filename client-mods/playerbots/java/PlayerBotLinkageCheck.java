import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.regex.*;
import java.util.jar.*;

/** Offline executable-reference audit. Never initializes or executes game classes. */
public final class PlayerBotLinkageCheck {
 private final ClassLoader loader = ClassLoader.getSystemClassLoader();
 private final Map<String,ClassModel> models = new HashMap<>();
 private final Set<String> errors = new TreeSet<>();
 private int references;
 private String context;
 private static final Pattern TYPES = Pattern.compile("L([^;]+);");
 private static boolean nativeType(String name) { return name.startsWith("com/aionemu/") || name.startsWith("playercommands/"); }
 private ClassModel model(String name) {
  if(models.containsKey(name)) return models.get(name);
  try(var in = loader.getResourceAsStream(name+".class")) {
   ClassModel result = in == null ? null : ClassFile.of().parse(in.readAllBytes());
   models.put(name,result); return result;
  } catch(Exception e) { throw new IllegalStateException("Cannot inspect "+name,e); }
 }
 private void type(String name) {
  if(name.startsWith("[")) { descriptor(name); return; }
  if(nativeType(name) && model(name)==null) errors.add(context+" -> missing class "+name);
 }
 private void descriptor(String desc) { var matcher=TYPES.matcher(desc); while(matcher.find()) type(matcher.group(1)); }
 private Boolean member(String owner,String name,String desc,boolean field,Set<String> visited) {
  if(!visited.add(owner)) return null;
  var c=model(owner); if(c==null) return null;
  if(field) {
   for(var f:c.fields()) if(f.fieldName().equalsString(name) && f.fieldType().equalsString(desc)) return f.flags().has(AccessFlag.STATIC);
  } else {
   for(var m:c.methods()) if(m.methodName().equalsString(name) && m.methodType().equalsString(desc)) return m.flags().has(AccessFlag.STATIC);
  }
  if(name.equals("<init>")) return null;
  for(var i:c.interfaces()) { var found=member(i.asInternalName(),name,desc,field,visited); if(found!=null) return found; }
  if(c.superclass().isPresent()) return member(c.superclass().get().asInternalName(),name,desc,field,visited);
  return null;
 }
 private void reference(MemberRefEntry ref,Boolean isStatic) {
  references++; var owner=ref.owner().asInternalName(); type(owner); descriptor(ref.type().stringValue());
  if(!nativeType(owner) || model(owner)==null) return;
  var found=member(owner,ref.name().stringValue(),ref.type().stringValue(),ref instanceof FieldRefEntry,new HashSet<>());
  if(found==null) errors.add(context+" -> missing member "+owner+"."+ref.name().stringValue()+ref.type().stringValue());
  else if(isStatic!=null && !found.equals(isStatic)) errors.add(context+" -> static/instance mismatch "+owner+"."+ref.name().stringValue());
 }
 private void constant(LoadableConstantEntry entry) {
  if(entry instanceof ClassEntry c) type(c.asInternalName());
  else if(entry instanceof MethodTypeEntry m) descriptor(m.descriptor().stringValue());
  else if(entry instanceof MethodHandleEntry h) reference(h.reference(),h.kind()==2 || h.kind()==4 || h.kind()==6);
  else if(entry instanceof ConstantDynamicEntry d) { descriptor(d.type().stringValue()); bootstrap(d.bootstrap()); }
 }
 private void bootstrap(BootstrapMethodEntry b) { constant(b.bootstrapMethod()); for(var a:b.arguments()) constant(a); }
 private void inspect(String name) {
  var c=model(name); if(c==null) throw new IllegalStateException("Package class unavailable: "+name);
  context=name+" hierarchy"; c.superclass().ifPresent(s->type(s.asInternalName())); for(var i:c.interfaces()) type(i.asInternalName());
  for(var f:c.fields()) { context=name+"."+f.fieldName().stringValue(); descriptor(f.fieldType().stringValue()); }
  for(var m:c.methods()) {
   context=name+"."+m.methodName().stringValue()+m.methodType().stringValue(); descriptor(m.methodType().stringValue());
   if(m.code().isEmpty()) continue;
   var code=m.code().get(); for(var h:code.exceptionHandlers()) h.catchType().ifPresent(t->type(t.asInternalName()));
   for(var element:code) {
    if(element instanceof InvokeInstruction i) reference(i.method(),i.opcode()==Opcode.INVOKESTATIC);
    else if(element instanceof FieldInstruction f) reference(f.field(),f.opcode()==Opcode.GETSTATIC || f.opcode()==Opcode.PUTSTATIC);
    else if(element instanceof NewObjectInstruction n) type(n.className().asInternalName());
    else if(element instanceof TypeCheckInstruction t) type(t.type().asInternalName());
    else if(element instanceof NewReferenceArrayInstruction n) type(n.componentType().asInternalName());
    else if(element instanceof NewMultiArrayInstruction n) type(n.arrayType().asInternalName());
    else if(element instanceof ConstantInstruction.LoadConstantInstruction l) constant(l.constantEntry());
    else if(element instanceof InvokeDynamicInstruction i) { descriptor(i.type().stringValue()); bootstrap(i.invokedynamic().bootstrap()); }
   }
  }
 }
 public static void main(String[] args)throws Exception {
  var check=new PlayerBotLinkageCheck(); int count=0;
  try(var jar=new JarFile(args[0])) {
   for(var entry:jar.stream().toList()) if(entry.getName().endsWith(".class")) {
    String name=entry.getName().substring(0,entry.getName().length()-6);
    try(var actual=check.loader.getResourceAsStream(entry.getName())) {
     if(actual==null || !Arrays.equals(actual.readAllBytes(),jar.getInputStream(entry).readAllBytes()))
      throw new IllegalStateException("Package is not first on effective classpath: "+name);
    }
    check.inspect(name); count++;
   }
  }
  if(!check.errors.isEmpty()) { for(var error:check.errors) System.err.println("FAIL: "+error); System.exit(1); }
  System.out.println("OK: "+count+" effective package classes; "+check.references+" executable member references; no game initialization");
 }
}
