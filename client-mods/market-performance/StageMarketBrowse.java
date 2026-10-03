import java.lang.classfile.*;
import java.nio.file.*;
import java.util.zip.*;
/** Keep installed logic and synthetic helpers intact; replace only catalog discovery. */
public final class StageMarketBrowse {
 public static void main(String[] args)throws Exception {
  var cf=ClassFile.of();Path compiled=Path.of(args[1]),out=Path.of(args[2]);String name="com/aionemu/gameserver/services/CentralMarketService.class";
  try(var zip=new ZipFile(args[0])) {
   var old=cf.parse(zip.getInputStream(zip.getEntry(name)).readAllBytes());
   var replacement=cf.parse(Files.readAllBytes(compiled.resolve(name))).methods().stream().filter(m->m.methodName().stringValue().equals("catalogView")).findFirst().orElseThrow();
   byte[] patched=cf.transformClass(old,(b,e)->{
    if(e instanceof MethodModel m && m.methodName().stringValue().equals("catalogView")) b.with(replacement);
    else if(e instanceof MethodModel m && m.methodName().stringValue().equals("templateView")) b.transformMethod(m,(mb,me)->{if(me instanceof AccessFlags f)mb.withFlags(f.flagsMask()&~ClassFile.ACC_PRIVATE);else mb.with(me);});
    else b.with(e);
   });
   var errors=cf.verify(patched);if(!errors.isEmpty())throw new IllegalStateException(errors.toString());
   Path target=out.resolve(name);Files.createDirectories(target.getParent());Files.write(target,patched);
   System.out.println("PASS: catalogView replaced; templateView access exposed to package; other methods retained");
  }
 }
}
