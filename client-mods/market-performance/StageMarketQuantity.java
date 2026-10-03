import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Add narrow UI hooks to installed methods; keep all their existing instructions/helpers. */
public final class StageMarketQuantity {
 static final ClassDesc HELPER=ClassDesc.of("com.aionemu.gameserver.services.CentralMarketPreferences"),PLAYER=ClassDesc.of("com.aionemu.gameserver.model.gameobjects.player.Player"),ITEM=ClassDesc.of("com.aionemu.gameserver.model.gameobjects.Item"),MAP=ClassDesc.of("java.util.Map");
 public static void main(String[] args)throws Exception {
  var cf=ClassFile.of();String path="com/aionemu/gameserver/services/CentralMarketService.class";int[] actions={0},snapshots={0},items={0};
  try(var zip=new ZipFile(args[0])) {
   var old=cf.parse(zip.getInputStream(zip.getEntry(path)).readAllBytes());
   byte[] patched=cf.transformClass(old,(b,e)->{
    if(e instanceof MethodModel method&&Set.of("action","snapshot","itemView").contains(method.methodName().stringValue())) {
     String name=method.methodName().stringValue();
     b.transformMethod(method,MethodTransform.transformingCode(new CodeTransform(){
      @Override public void atStart(CodeBuilder code) {
       if(name.equals("action")) {
        actions[0]++;
        code.getstatic(ClassDesc.of("com.aionemu.gameserver.services.CentralMarketService"),"ready",ConstantDescs.CD_boolean).ifThen(ready->ready.aload(0).aload(1).aload(2)
         .invokestatic(HELPER,"tryAction",MethodTypeDesc.of(ConstantDescs.CD_String,PLAYER,MAP,ConstantDescs.CD_String)).dup().ifThen(Opcode.IFNONNULL,found->found.areturn()).pop());
       }
      }
      @Override public void accept(CodeBuilder code,CodeElement element) {
       if(element instanceof ReturnInstruction ret&&ret.opcode()==Opcode.ARETURN) {
        if(name.equals("snapshot")){snapshots[0]++;code.dup().aload(0).invokestatic(HELPER,"decorateSnapshot",MethodTypeDesc.of(ConstantDescs.CD_void,MAP,PLAYER));}
        if(name.equals("itemView")){items[0]++;code.dup().aload(0).iload(1).invokestatic(HELPER,"decorateItem",MethodTypeDesc.of(ConstantDescs.CD_void,MAP,ITEM,ConstantDescs.CD_int));}
       }
       code.with(element);
      }
     }));
    } else b.with(e);
   });
   if(actions[0]!=1||snapshots[0]!=3||items[0]!=1)throw new IllegalStateException("Hook counts changed: action="+actions[0]+", snapshot="+snapshots[0]+", item="+items[0]);
   var errors=cf.verify(patched);if(!errors.isEmpty())throw new IllegalStateException(errors.toString());
   Path out=Path.of(args[1]).resolve(path);Files.createDirectories(out.getParent());Files.write(out,patched);
   System.out.println("PASS: preference dispatch, snapshot preference and item eligibility hooks; all other installed methods retained");
  }
 }
}
