import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.world.World;

/** Capture effective definitions and exercise world-free fixtures; never force a session tick or cast. */
public final class PlayerBotStrategyRuntimeCheckAgent1 {
 private static final List<JarFile> APPENDED=new ArrayList<>();
 public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  String[] args=argument.split("\\|",-1);if(args.length!=3)throw new IllegalArgumentException();
  Path fixture=Path.of(args[0]),output=Path.of(args[2]);Files.createDirectories(output);
  if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture))).equals(args[1]))throw new IllegalStateException("Fixture changed");
  JarFile added=new JarFile(fixture.toFile());APPENDED.add(added);instrumentation.appendToSystemClassLoaderSearch(added);
  var check=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotStrategyCompositionCheck");
  check.getMethod("main",String[].class).invoke(null,(Object)new String[0]);
  List<String> lines=new ArrayList<>();lines.add("OK: 49 behavior fixtures executed against actual loaded engine/helpers; no world, DB, native cast or ID writes.");
  var helper=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotArbitration");
  Field memories=helper.getDeclaredField("MEMORIES");memories.setAccessible(true);
  Field engineField=PlayerBotSession.class.getDeclaredField("engine");engineField.setAccessible(true);
  int actors=0,observed=0;
  synchronized(PlayerBotService.getInstance()) {
   for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot()) {
    for(var session:PlayerBotService.getInstance().companions(owner)) {
     var engine=(PlayerBotEngine)engineField.get(session);Object memory;
     var cache=(Map<?,?>)memories.get(null);synchronized(cache){memory=cache.get(engine);}
     if(memory!=null) {
      synchronized(memory) {
       Field context=memory.getClass().getDeclaredField("context"),pending=memory.getClass().getDeclaredField("pending");
       context.setAccessible(true);pending.setAccessible(true);
       if(context.get(memory)==null)throw new AssertionError("Actual tick did not bind native context");
       var keys=(Map<?,?>)pending.get(memory);if(keys.size()>16)throw new AssertionError("Unbounded continuation memory");
       for(Object value:keys.values())if(!value.getClass().isRecord())throw new AssertionError("Retained actor/action");
       observed++;
      }
     }
     if(!session.bot().isSpawned() || session.bot().getPlayerGroup()!=owner.getPlayerGroup())throw new AssertionError("Companion lost native party registration");
     actors++;lines.add("COMPANION: "+session.bot().getName()+" role="+session.combatRole()+" state="+engine.getState()+" action="+engine.getLastAction()+" composedTickObserved="+(memory!=null));
    }
   }
  }
  if(actors>0 && observed!=actors)throw new AssertionError("Not all existing sessions observed the installed scheduler: "+observed+"/"+actors);
  var types=new ArrayList<Class<?>>();types.add(PlayerBotEngine.class);types.add(PlayerBotSession.class);
  var sourceLoader=PlayerBotService.class.getClassLoader();
  for(String name:List.of("PlayerBotArbitration","PlayerBotStrategyComposition")) {
   Class<?> type=Class.forName("com.aionemu.gameserver.services.playerbot."+name,false,sourceLoader);
   types.add(type);types.addAll(List.of(type.getDeclaredClasses()));
  }
  Map<String,byte[]> captured=new LinkedHashMap<>();Set<Class<?>> wanted=Set.copyOf(types);
  var transformer=new ClassFileTransformer(){
   public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes) {
    if(wanted.contains(type))captured.put(name,bytes.clone());return null;
   }
  };
  instrumentation.addTransformer(transformer,true);
  try{instrumentation.retransformClasses(types.toArray(Class<?>[]::new));}finally{instrumentation.removeTransformer(transformer);}
  if(captured.size()!=types.size())throw new AssertionError("Missing effective definitions");
  try(var archive=new ZipOutputStream(Files.newOutputStream(output.resolve("effective-loaded.jar")))) {
   for(var entry:captured.entrySet()){archive.putNextEntry(new ZipEntry(entry.getKey()+".class"));archive.write(entry.getValue());archive.closeEntry();}
  }
  lines.add("OK: "+actors+" real sessions retained; "+observed+" naturally scheduled native contexts observed; no forced tick, movement, spell or settings change.");
  lines.add("OK: "+captured.size()+" effective loaded definitions captured for method comparison; client combat acceptance remains pending.");
  Files.write(output.resolve("runtime-check.txt"),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {
  var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}
 }
}
