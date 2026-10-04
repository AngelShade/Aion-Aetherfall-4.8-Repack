import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.alibaba.fastjson2.JSON;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.PlayerBotHttpService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.world.World;

/** Scoped live mapper repair and read-only verification of existing panel state. */
public class PlayerBotInventoryPatchAgent {
 private static byte[] definition(Path jar,String hash)throws Exception {
  if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))).equals(hash))throw new IllegalStateException("Reviewed override changed");
  try(var zip=new ZipFile(jar.toFile())) {
   if(zip.size()!=2)throw new IllegalStateException("Expected effective two-class override");
   return zip.getInputStream(zip.getEntry("com/aionemu/gameserver/services/playerbot/PlayerBotSession.class")).readAllBytes();
  }
 }
 private static List<String> inspect(boolean requireSuccess)throws Exception {
  List<String> lines=new ArrayList<>();
  Method panel=PlayerBotHttpService.class.getDeclaredMethod("snapshot",Player.class);panel.setAccessible(true);
  for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot()) {
   var sessions=PlayerBotService.getInstance().companions(owner);
   lines.add("PLAYER: "+owner.getName()+" activeCompanions="+sessions.size());
   for(var session:sessions) {
    long ordinary=session.bot().getInventory().getItems().stream().filter(i->i.getItemTemplate().getItemSlot()==0).count();
    lines.add("COMPANION: "+session.bot().getName()+" spawned="+session.bot().isSpawned()+" ordinaryInventoryItems="+ordinary);
   }
   try {
    var state=panel.invoke(null,owner);
    // Serialize the exact production panel snapshot; never issue action tickets.
    var serialized=JSON.parseObject(JSON.toJSONString(state));
    if(serialized.getJSONArray("active").size()!=sessions.size())throw new IllegalStateException("Panel lost existing companions");
    lines.add("OK: complete production panel snapshot and JSON serialization; active="+sessions.size()+" roster="+serialized.getJSONArray("roster").size());
   }catch(InvocationTargetException error){
    if(requireSuccess)throw error;
    lines.add("PANEL ERROR: "+error.getCause().getClass().getSimpleName()+": "+error.getCause().getMessage());
   }
  }
  return lines;
 }
 public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  String[] args=argument.split("\\|",-1);
  synchronized(PlayerBotService.getInstance()) {
   if(args.length==2 && args[0].equals("diagnose")) {Files.write(Path.of(args[1]),inspect(false),StandardOpenOption.CREATE_NEW);return;}
   if(args.length!=6 || !args[0].equals("apply"))throw new IllegalArgumentException("Invalid scoped patch arguments");
   if(!instrumentation.isRedefineClassesSupported() || !instrumentation.isModifiableClass(PlayerBotSession.class))throw new IllegalStateException("Runtime class update unavailable");
   byte[] updated=definition(Path.of(args[1]),args[2]),baseline=definition(Path.of(args[3]),args[4]);
   long humans=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count();
   // The explicit previous override is the rollback definition. Classloader
   // resources can still expose an older base JAR following a live redefinition.
   instrumentation.redefineClasses(new ClassDefinition(PlayerBotSession.class,updated));
   try {
    List<String> lines=inspect(true);
    lines.add("OK: only PlayerBotSession inventory mapper changed; existing characters, singleton/session state retained.");
    lines.add("Human connections before="+humans+" after="+World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count());
    Files.write(Path.of(args[5]),lines,StandardOpenOption.CREATE_NEW);
   }catch(Throwable error){instrumentation.redefineClasses(new ClassDefinition(PlayerBotSession.class,baseline));throw error;}
  }
 }
 public static void main(String[] args)throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}
 }
}
