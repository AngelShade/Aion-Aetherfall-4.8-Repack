import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.services.DuelService;
import com.aionemu.gameserver.custom.pvpmap.PvpMapService;

/** Read-only recruitment diagnostics. No tokens, credentials or player mutations. */
public class PlayerBotRecruitmentCheckAgent {
 public static void agentmain(String argument, Instrumentation ignored) throws Exception {
  Path receipt=Path.of(argument);
  List<String> lines=new ArrayList<>();
  for(Player p:World.getInstance().getAllPlayers()) {
   if(p.isPlayerBot())continue;
   boolean targetPvp=p.getTarget() instanceof Creature c && c.getMaster() instanceof Player other && p.isEnemy(other);
   boolean aggroPvp=p.getAggroList().stream().anyMatch(a->a.getAttacker().getMaster() instanceof Player other && p.isEnemy(other));
   lines.add("PLAYER: "+p.getName()+" map="+p.getWorldId()+" online="+p.isOnline()+" spawned="+p.isSpawned()+" dead="+p.isDead()+" prison="+p.isInPrison()+" alliance="+p.isInAlliance()+" flying="+p.isFlying()+" flight="+p.isInFlyingState()+" glide="+p.isInGlidingState()+" pvpZone="+p.isInsidePvPZone()+" duel="+DuelService.getInstance().isDueling(p)+" pvpMap="+PvpMapService.getInstance().isOnPvPMap(p)+" hostilePlayerTarget="+targetPvp+" hostilePlayerAggro="+aggroPvp);
  }
  if(lines.isEmpty())lines.add("No human characters currently in the world.");
  Files.write(receipt,lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args) throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}
 }
}
