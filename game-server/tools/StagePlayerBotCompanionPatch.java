import java.lang.classfile.*;
import java.nio.file.*;
import java.util.*;

/** Transplant reviewed existing methods, retaining every other installed member. */
public class StagePlayerBotCompanionPatch {
 public static void main(String[] args)throws Exception {
  var cf=ClassFile.of();int total=0;
  for(String line:Files.readAllLines(Path.of(args[3]))) {
   String[] columns=line.split("\\t");String path=columns[0]+".class";
   Set<String> selected=new HashSet<>(Arrays.asList(columns[1].split(",")));
   var baseline=cf.parse(Path.of(args[0]).resolve(path));var compiled=cf.parse(Path.of(args[1]).resolve(path));
   Map<String,MethodModel> replacements=new HashMap<>();
   for(var method:compiled.methods())if(selected.contains(method.methodName().stringValue()))replacements.put(method.methodName().stringValue()+method.methodType().stringValue(),method);
   Set<String> replaced=new HashSet<>();
   byte[] result=cf.transformClass(baseline,(builder,element)->{
    if(element instanceof MethodModel method && selected.contains(method.methodName().stringValue())) {
     String key=method.methodName().stringValue()+method.methodType().stringValue();var replacement=replacements.get(key);
     if(replacement==null)throw new IllegalStateException("Reviewed installed method removed: "+path+" "+key);
     builder.with(replacement);replaced.add(key);
    }else builder.with(element);
   });
   if(!replaced.equals(replacements.keySet()) || !cf.verify(result).isEmpty())throw new IllegalStateException("Invalid transplant: "+path);
   var changed=cf.parse(result);
   if(changed.methods().size()!=baseline.methods().size() || changed.fields().size()!=baseline.fields().size())throw new IllegalStateException("Installed schema changed: "+path);
   Path output=Path.of(args[2]).resolve(path);Files.createDirectories(output.getParent());Files.write(output,result);total+=replaced.size();
  }
  System.out.println("OK: transplanted "+total+" reviewed methods; all other installed members retained.");
 }
}
