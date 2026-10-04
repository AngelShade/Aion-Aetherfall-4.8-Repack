import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.world.World;

/** Apply reviewed same-schema method changes without disconnecting live players. */
public class PlayerBotRecruitmentPatchAgent {
 public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  String[] args=argument.split("\\|",-1);if(args.length!=3)throw new IllegalArgumentException("patch|sha256|receipt");
  Path patch=Path.of(args[0]),receipt=Path.of(args[2]);
  if(!instrumentation.isRedefineClassesSupported())throw new IllegalStateException("Runtime class update unavailable");
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(patch)));
  if(!hash.equals(args[1]))throw new IllegalStateException("Reviewed runtime payload changed");
  List<ClassDefinition> changes=new ArrayList<>(),originals=new ArrayList<>();
  try(var staged=new ZipFile(patch.toFile())) {
   if(staged.size()!=2)throw new IllegalStateException("Expected exactly two class entries");
   for(String name:List.of("PlayerBotService","PlayerBotSession")) {
    Class<?> type=Class.forName("com.aionemu.gameserver.services.playerbot."+name);
    if(!instrumentation.isModifiableClass(type))throw new IllegalStateException("Unmodifiable companion class");
    String path=type.getName().replace('.','/')+".class";
    try(var input=type.getClassLoader().getResourceAsStream(path)){originals.add(new ClassDefinition(type,input.readAllBytes()));}
    changes.add(new ClassDefinition(type,staged.getInputStream(staged.getEntry(path)).readAllBytes()));
   }
  }
  synchronized(PlayerBotService.getInstance()) {
   int humans=(int)World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count();
   instrumentation.redefineClasses(changes.toArray(ClassDefinition[]::new));
   try {
    Method guard=PlayerBotService.class.getDeclaredMethod("ensureOwner",Player.class);guard.setAccessible(true);
    List<String> lines=new ArrayList<>();lines.add("OK: redefined only two companion classes; existing singleton/session state retained; no disconnect or character mutation.");
    for(Player p:World.getInstance().getAllPlayers())if(!p.isPlayerBot()) {
     String result="allowed";
     try{guard.invoke(PlayerBotService.getInstance(),p);}
     catch(InvocationTargetException error){
      if(!(error.getCause() instanceof IllegalArgumentException))throw error;
      result=error.getCause().getMessage();
      if(result.contains("outside PvP")||result.contains("ground"))throw new IllegalStateException("Old recruitment guard still active");
     }
     lines.add("PLAYER: "+p.getName()+" map="+p.getWorldId()+" pvpCapable="+p.isInsidePvPZone()+" nativeCombat="+p.getController().isInCombat()+" recruitment="+result);
    }
    lines.add("Human connections before="+humans+" after="+World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count());
    Files.write(receipt,lines,StandardOpenOption.CREATE_NEW);
   }catch(Throwable error){instrumentation.redefineClasses(originals.toArray(ClassDefinition[]::new));throw error;}
  }
 }
 public static void main(String[] args)throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath()+"|"+args[3]+"|"+Path.of(args[4]).toAbsolutePath());}finally{vm.detach();}
 }
}
