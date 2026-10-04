import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.PositionUtil;
/** Check the exact live target guard, then release only companions attacking a native flag. */
public class EltnenFlagTargetCheckAgent {
 public static void agentmain(String report,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();
  try{
   Player owner=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()&&p.getName().equalsIgnoreCase("Baby")).findFirst().orElseThrow();
   var service=PlayerBotService.getInstance();
   var valid=PlayerBotSession.class.getDeclaredMethod("validEnemy",Npc.class,List.class,boolean.class);valid.setAccessible(true);
   var flags=new ArrayList<Npc>();var enemies=new ArrayList<Npc>();
   World.getInstance().forEachObject(o->{if(o instanceof Npc n&&n.isFlag()&&n.getWorldId()==owner.getWorldId()&&n.getInstanceId()==owner.getInstanceId())flags.add(n);});
   owner.getKnownList().forEachNpc(n->{if(!n.isFlag()&&!n.isDead()&&owner.isEnemy(n)&&PositionUtil.getDistance(owner,n)<40)enemies.add(n);});
   if(flags.isEmpty()||enemies.isEmpty())throw new IllegalStateException("Need real map flags and a nearby normal enemy for native checks");
   int checks=0;
   for(var s:service.companions(owner))synchronized(s){
    var b=s.bot();
    for(var flag:flags){
     for(boolean explicit:new boolean[]{false,true}){if((boolean)valid.invoke(s,flag,b.getPlayerGroup().getMembers(),explicit))throw new IllegalStateException("Flag accepted by "+b.getName());checks++;}
     if(PlayerBotService.allowsTarget(b,flag))throw new IllegalStateException("Effect-time flag allowed");checks++;
    }
    boolean normal=enemies.stream().anyMatch(n->{try{return (boolean)valid.invoke(s,n,b.getPlayerGroup().getMembers(),true);}catch(Exception e){throw new RuntimeException(e);}});
    if(!normal)throw new IllegalStateException("No normal explicit enemy accepted by "+b.getName());checks++;
    for(var flag:flags){
     if(b.getTarget()==flag){b.getController().cancelCurrentSkill(null);b.getMoveController().abortMove();b.setTarget(null);lines.add("RELEASED flag target: "+b.getName());}
     flag.getAggroList().remove(b,false);b.getAggroList().remove(flag,false);
     if(b.getSummon()!=null){var pet=b.getSummon();if(pet.getTarget()==flag){pet.getController().cancelCurrentSkill(null);pet.setTarget(null);}flag.getAggroList().remove(pet,false);pet.getAggroList().remove(flag,false);}
    }
    lines.add("OK "+b.getName()+": native flag rejected for automatic/explicit selection and effect-time eligibility; real normal enemy allowed. role="+s.combatRole());
   }
   lines.add("OK "+checks+" checks; humans="+World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count()+" companions="+service.companions(owner).size()+". No character/item/quest/flag ownership changes.");
  }catch(Throwable t){lines.add("FAIL "+t);for(var f:t.getStackTrace())lines.add(f.toString());}
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),Path.of(a[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
