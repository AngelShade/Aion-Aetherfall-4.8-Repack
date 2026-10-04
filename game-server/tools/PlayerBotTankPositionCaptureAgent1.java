import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.world.World;

/** Captures effective definitions and reads pure tank policy; never ticks or moves an actor. */
public final class PlayerBotTankPositionCaptureAgent1 {
 public static void agentmain(String directory,Instrumentation instrumentation)throws Exception {
  Path output=Path.of(directory);Files.createDirectories(output);var loader=PlayerBotService.class.getClassLoader();
  List<Class<?>> types=new ArrayList<>();
  for(String name:List.of("PlayerBotCoordination","PlayerBotSession","PlayerBotSession$ReachAction","PlayerBotSession$CastAction","PlayerBotNavigation","PlayerBotHazards"))
   types.add(Class.forName("com.aionemu.gameserver.services.playerbot."+name,false,loader));
  Map<String,byte[]> captured=new LinkedHashMap<>();Set<Class<?>> wanted=Set.copyOf(types);
  var transformer=new ClassFileTransformer(){public byte[] transform(ClassLoader l,String name,Class<?> type,ProtectionDomain d,byte[] bytes){if(wanted.contains(type))captured.put(name,bytes.clone());return null;}};
  instrumentation.addTransformer(transformer,true);
  try{instrumentation.retransformClasses(types.toArray(Class<?>[]::new));}finally{instrumentation.removeTransformer(transformer);}
  if(captured.size()!=types.size())throw new AssertionError("Missing loaded definition");
  try(var archive=new ZipOutputStream(Files.newOutputStream(output.resolve("effective-loaded.jar"),StandardOpenOption.CREATE_NEW))){for(var e:captured.entrySet()){archive.putNextEntry(new ZipEntry(e.getKey()+".class"));archive.write(e.getValue());archive.closeEntry();}}
  var coordination=types.getFirst();Method facing=coordination.getDeclaredMethod("tankFacing",Player.class,Npc.class,List.class);facing.setAccessible(true);
  Method spread=coordination.getDeclaredMethod("spread",Player.class,List.class,List.class);spread.setAccessible(true);
  List<String> lines=new ArrayList<>();int tanks=0,checks=0;
  synchronized(PlayerBotService.getInstance()) {
   for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot()) {
    var sessions=PlayerBotService.getInstance().companions(owner);var party=new ArrayList<Player>();party.add(owner);for(var s:sessions)party.add(s.bot());
    for(var s:sessions)if(s.combatRole()==PlayerBotRules.Role.TANK) {
     var bot=s.bot();var target=bot.getTarget() instanceof Npc n?n:null;
     if(facing.invoke(null,bot,target,party)!=null)throw new AssertionError("Tank has an automatic facing-movement goal");checks++;tanks++;
     if(target!=null && target.getTarget()==bot){if(spread.invoke(null,bot,party,List.of(target))!=null)throw new AssertionError("Holding tank spreads its own boss cast");checks++;}
     lines.add("TANK: "+bot.getName()+" target="+(target==null?"none":target.getName())+" holding="+(target!=null&&target.getTarget()==bot)+" facingMove=none");
    }
   }
  }
  lines.add("OK: "+captured.size()+" loaded definitions captured with null transformer; "+checks+" real read-only policy checks across "+tanks+" tanks. No movement/casts/ticks/character/DB/ID writes; actual client boss fight pending.");
  Files.write(output.resolve("capture-check.txt"),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
