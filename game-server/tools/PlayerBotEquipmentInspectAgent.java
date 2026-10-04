import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.chathandlers.ChatProcessor;
import com.aionemu.gameserver.world.World;

/** Read-only native linkage/default inspection; never creates characters or items. */
public class PlayerBotEquipmentInspectAgent {
 public static void agentmain(String report,Instrumentation instrumentation)throws Exception {
  List<String> lines=new ArrayList<>();ClassLoader loader=PlayerBotService.class.getClassLoader();
  Class<?> policy=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotGearPolicy",true,loader);
  Field defaults=policy.getDeclaredField("DEFAULT");defaults.setAccessible(true);String selected=defaults.get(null).toString();
  if(!selected.contains("mode=EARNED") || !selected.contains("vendors=false"))throw new IllegalStateException("Unexpected automatic generation/spending defaults");
  lines.add("DEFAULTS: "+selected);lines.add("HELPER SOURCE: "+policy.getProtectionDomain().getCodeSource().getLocation());
  for(String nested:List.of("Settings","State","Mode","Profile","Rolls","Candidate","Purchase"))Class.forName(policy.getName()+"$"+nested,true,loader);
  Class<?> equipment=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotEquipment",true,loader);
  Method upgrades=equipment.getDeclaredMethod("upgrades",Player.class,PlayerBotRules.Role.class);upgrades.setAccessible(true);
  try{upgrades.invoke(null,null,PlayerBotRules.Role.HEALER);throw new IllegalStateException("Expected invalid-player refusal");}
  catch(InvocationTargetException expected){if(!(expected.getCause() instanceof NullPointerException) || Arrays.stream(expected.getCause().getStackTrace()).noneMatch(frame->frame.getClassName().equals(policy.getName())))throw expected;}
  lines.add("OK: actual runtime equipment entry point delegates to the installed gear policy; no player passed or modified.");
  var command=ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals(".bot")).findFirst().orElseThrow();
  Field help=com.aionemu.gameserver.utils.chathandlers.ChatCommand.class.getDeclaredField("syntaxInfo");help.setAccessible(true);
  if(command.getLevel()!=0 || !String.valueOf(help.get(command)).contains("gearpolicy"))throw new IllegalStateException("Equipment command is missing");
  lines.add("OK: normal-player .bot gearpolicy command/help and all policy helper types linked.");
  lines.add("Human connections="+World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count());
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception{VirtualMachine vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
