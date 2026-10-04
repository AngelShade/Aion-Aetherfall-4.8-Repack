import java.lang.classfile.*;
import java.nio.file.*;
import java.util.zip.*;

/** Preserve deployed projectile timing when adding independent companion targeting guards. */
public class PreservePlayerBotBaseline {
 public static void main(String[] args) throws Exception {
  String name="com/aionemu/gameserver/skillengine/model/Skill.class";
  var format=ClassFile.of();
  try(var zip=new ZipFile(args[0])) {
   var original=format.parse(zip.getInputStream(zip.getEntry(name)).readAllBytes());
   var originalMethod=original.methods().stream().filter(m->m.methodName().stringValue().equals("updateHitTime")&&m.methodType().stringValue().equals("(Z)V")).findFirst().orElseThrow();
   Path target=Path.of(args[1]).resolve(name);var compiled=format.parse(target);
   byte[] result=format.transformClass(compiled,(builder,element)-> {
    if(element instanceof MethodModel method && method.methodName().stringValue().equals("updateHitTime") && method.methodType().stringValue().equals("(Z)V")) builder.with(originalMethod);
    else builder.with(element);
   });
   Files.write(target,result);
   System.out.println("OK: preserved original deployed projectile timing method; companion targeting guards retained.");
  }
 }
}
