import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;

/** Read-only verification of the actual running GameServer command registry. */
public final class LiveCheckV2 {
 public static void agentmain(String receipt,Instrumentation instrumentation)throws Exception {
  Class<?> server=null;
  for(Class<?> c:instrumentation.getAllLoadedClasses())
   if(c.getName().equals("com.aionemu.gameserver.GameServer")){server=c;break;}
  if(server==null)throw new IllegalStateException("Not an Aion GameServer");
  ClassLoader loader=server.getClassLoader();
  Class<?> registry=Class.forName("com.aionemu.gameserver.utils.chathandlers.ChatProcessor",false,loader);
  Object processor=registry.getMethod("getInstance").invoke(null);
  boolean exists=(boolean)registry.getMethod("isCommandExists",String.class).invoke(processor,".speechbubble");
  if(!exists)throw new IllegalStateException("Speech bubble command was not registered");
  Class<?> command=Class.forName("com.aionemu.gameserver.utils.chathandlers.ChatCommand",false,loader);
  Object found=null;
  for(Object c:(List<?>)registry.getMethod("getCommandList").invoke(processor))
   if(command.getMethod("getAliasWithPrefix").invoke(c).equals(".speechbubble")){found=c;break;}
  int level=((Number)command.getMethod("getLevel").invoke(found)).intValue();
  if(level!=0)throw new IllegalStateException("Command unavailable to ordinary players");
  Class<?> settings=Class.forName("com.aionemu.gameserver.model.gameobjects.player.PlayerSettings",false,loader);
  settings.getMethod("getSpeechBubbleStyle");settings.getMethod("setSpeechBubbleStyle",int.class);
  Class<?> connection=Class.forName("com.aionemu.gameserver.network.aion.AionConnection",false,loader);
  connection.getMethod("isSpeechBubbleClient");connection.getMethod("enableSpeechBubbleClient");
  Class<?> packet=Class.forName("com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE",false,loader);
  packet.getMethod("speechBubbleAcknowledgement",Class.forName("com.aionemu.gameserver.model.gameobjects.player.Player",false,loader));
  Files.writeString(Path.of(receipt),"Speech bubble command registered: true\nAccess level: 0\nCharacter settings, connection negotiation, and acknowledgement API loaded: true\n");
 }
 public static void main(String[] args)throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(args[1],args[2]);}finally{vm.detach();}
 }
}
