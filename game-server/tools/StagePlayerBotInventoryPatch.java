import java.lang.classfile.*;
import java.nio.file.*;
import java.util.zip.*;

/** Preserve the effective installed override; replace only the inventory mapper. */
public class StagePlayerBotInventoryPatch {
 public static void main(String[] args)throws Exception {
  String path="com/aionemu/gameserver/services/playerbot/PlayerBotSession.class";
  var cf=ClassFile.of();
  try(var zip=new ZipFile(args[0])) {
   var original=cf.parse(zip.getInputStream(zip.getEntry(path)).readAllBytes());
   var compiled=cf.parse(Path.of(args[1]).resolve(path));
   var replacement=compiled.methods().stream().filter(m->m.methodName().stringValue().equals("lambda$snapshot$0")).findFirst().orElseThrow();
   byte[] result=cf.transformClass(original,(builder,element)->{
    if(element instanceof MethodModel method && method.methodName().stringValue().equals("lambda$snapshot$0")) {
     if(!method.methodType().equalsString(replacement.methodType().stringValue()))throw new IllegalStateException("Mapper signature changed");
     builder.with(replacement);
    }else builder.with(element);
   });
   if(!cf.verify(result).isEmpty() || cf.parse(result).methods().size()!=original.methods().size())throw new IllegalStateException("Invalid or changed class schema");
   Path target=Path.of(args[2]).resolve(path);Files.createDirectories(target.getParent());Files.write(target,result);
   System.out.println("OK: transplanted only inventory mapper into effective installed companion class; recruitment fix retained.");
  }
 }
}
