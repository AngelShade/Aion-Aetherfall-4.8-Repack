import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.zip.*;

/** Refresh the native web token before the existing enter-world packet body runs. */
public final class StageWebSession {
 public static void main(String[] args)throws Exception {
  String name="com/aionemu/gameserver/network/aion/clientpackets/CM_ENTER_WORLD.class";
  ClassDesc packet=ClassDesc.of("com.aionemu.gameserver.network.aion.clientpackets.CM_ENTER_WORLD"),connection=ClassDesc.of("com.aionemu.gameserver.network.aion.AionConnection"),helper=ClassDesc.of("com.aionemu.gameserver.services.player.WebSessionService");
  var cf=ClassFile.of();int[] count={0};
  try(var zip=new ZipFile(args[0])) {
   var original=cf.parse(zip.getInputStream(zip.getEntry(name)).readAllBytes());
   byte[] changed=cf.transformClass(original,(b,e)->{
    if(e instanceof MethodModel m&&m.methodName().equalsString("runImpl")) b.transformMethod(m,MethodTransform.transformingCode(new CodeTransform(){
     public void atStart(CodeBuilder c){count[0]++;c.aload(0).invokevirtual(packet,"getConnection",MethodTypeDesc.of(ClassDesc.of("com.aionemu.commons.network.AConnection"))).checkcast(connection).invokestatic(helper,"send",MethodTypeDesc.of(ConstantDescs.CD_void,connection));}
     public void accept(CodeBuilder c,CodeElement e){c.with(e);}
    }));else b.with(e);
   });
   if(count[0]!=1||!cf.verify(changed).isEmpty())throw new IllegalStateException("Packet verification failed");
   Path out=Path.of(args[1]).resolve(name);Files.createDirectories(out.getParent());Files.write(out,changed);
   System.out.println("PASS: enter-world token refresh; original packet body and other methods retained");
  }
 }
}
