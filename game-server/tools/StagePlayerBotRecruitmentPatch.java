import java.lang.classfile.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Copy only six reviewed methods into the exact deployed class baseline. */
public class StagePlayerBotRecruitmentPatch {
 public static void main(String[] args)throws Exception {
  var cf=ClassFile.of();Path compiled=Path.of(args[1]),out=Path.of(args[2]);
  try(var zip=new ZipFile(args[0])) {
   for(var entry:Map.of("PlayerBotService",Set.of("ensureOwner","allowsTarget","relocate","canEnter"),"PlayerBotSession",Set.of("tick","inPvp")).entrySet()) {
    String name="com/aionemu/gameserver/services/playerbot/"+entry.getKey()+".class";
    var original=cf.parse(zip.getInputStream(zip.getEntry(name)).readAllBytes());var updated=cf.parse(compiled.resolve(name));
    Map<String,MethodModel> replacements=new HashMap<>();
    for(var method:updated.methods())if(entry.getValue().contains(method.methodName().stringValue()))replacements.put(method.methodName().stringValue()+method.methodType().stringValue(),method);
    if(replacements.size()!=entry.getValue().size())throw new IllegalStateException("Ambiguous replacement methods");
    byte[] result=cf.transformClass(original,(builder,element)->{
     if(element instanceof MethodModel method && entry.getValue().contains(method.methodName().stringValue()))builder.with(replacements.get(method.methodName().stringValue()+method.methodType().stringValue()));
     else builder.with(element);
    });
    if(!cf.verify(result).isEmpty() || cf.parse(result).methods().size()!=original.methods().size())throw new IllegalStateException("Invalid or changed class schema");
    Path target=out.resolve(name);Files.createDirectories(target.getParent());Files.write(target,result);
    System.out.println("OK: "+name+"; preserved original fields/methods; changed only "+entry.getValue());
   }
  }
 }
}
