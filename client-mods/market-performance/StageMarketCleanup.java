import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Patch only authorized deployed methods using Java 25's class-file API. */
public final class StageMarketCleanup {
 static byte[] patch(byte[] before,byte[] compiled,String kind) {
  var cf=ClassFile.of();var original=cf.parse(before);Set<String> changed=new HashSet<>();int[] edits={0};
  Map<String,MethodModel> replacements=new HashMap<>();
  if(compiled!=null)for(var m:cf.parse(compiled).methods())replacements.put(m.methodName().stringValue()+m.methodType().stringValue(),m);
  byte[] after=cf.transformClass(original,(builder,element)->{
   if(!(element instanceof MethodModel method)){builder.with(element);return;}
   String name=method.methodName().stringValue(),key=name+method.methodType().stringValue();
   if(kind.equals("market")&&name.equals("snapshot")||kind.equals("config")&&(name.equals("<clinit>")||name.equals("getClasses"))) {
    var replacement=replacements.get(key);if(replacement==null||replacement.flags().flagsMask()!=method.flags().flagsMask())throw new IllegalStateException("Method shape changed: "+key);
    builder.with(replacement);changed.add(key);edits[0]++;
   } else if(kind.equals("server")&&method.code().stream().flatMap(CodeModel::elementStream).anyMatch(e->e instanceof InvokeInstruction call&&call.owner().asInternalName().equals("com/aionemu/gameserver/services/AfkKeepAliveService"))) {
    builder.transformMethod(method,MethodTransform.transformingCode((code,e)->{
     if(e instanceof InvokeInstruction call&&call.owner().asInternalName().equals("com/aionemu/gameserver/services/AfkKeepAliveService")) {
      if(!Set.of("getInstance","start").contains(call.name().stringValue()))throw new IllegalStateException("Unexpected AFK invocation");edits[0]++;
     } else code.with(e);
    }));changed.add(key);
   } else builder.with(method);
  });
  if(edits[0]!=(kind.equals("market")?1:2))throw new IllegalStateException("Unexpected patch count: "+edits[0]);
  var errors=cf.verify(after);if(!errors.isEmpty())throw new IllegalStateException("Class verification: "+errors);
  if(original.methods().size()!=cf.parse(after).methods().size())throw new IllegalStateException("Method count changed");
  System.out.println("OK: "+kind+"; changed only "+changed);return after;
 }
 public static void main(String[] args)throws Exception {
  Path jar=Path.of(args[0]),compiled=Path.of(args[1]),out=Path.of(args[2]);Files.createDirectories(out);
  try(var zip=new ZipFile(jar.toFile())) {
   for(String[] entry:new String[][]{{"com/aionemu/gameserver/services/CentralMarketService.class","market"},{"com/aionemu/gameserver/GameServer.class","server"},{"com/aionemu/gameserver/configs/Config.class","config"}}) {
    byte[] original=zip.getInputStream(zip.getEntry(entry[0])).readAllBytes();Path target=out.resolve(entry[0]);Files.createDirectories(target.getParent());
    Files.write(target,patch(original,entry[1].equals("server")?null:Files.readAllBytes(compiled.resolve(entry[0])),entry[1]));
   }
  }
 }
}
