import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
public class EltnenUndergroundInspectAgent {
 static String pos(VisibleObject o){return o.getName()+" oid="+o.getObjectId()+" world="+o.getWorldId()+" instance="+o.getInstanceId()+" xyz="+o.getX()+","+o.getY()+","+o.getZ();}
 public static void agentmain(String report,Instrumentation ignored)throws Exception{
  var lines=new ArrayList<String>();
  try{
   for(Player p:World.getInstance().getAllPlayers())lines.add("ONLINE "+pos(p)+" bot="+p.isPlayerBot());
   Player owner=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()&&(p.getName().equalsIgnoreCase("Babe")||p.getName().equalsIgnoreCase("Baby"))).findFirst().orElse(null);
   if(owner==null)throw new IllegalStateException("Babe offline");
   lines.add("OWNER "+pos(owner)+" target="+(owner.getTarget()==null?"none":pos(owner.getTarget())));
   for(Player p:World.getInstance().getAllPlayers())if(p.isPlayerBot()&&p.getPlayerBotOwnerId()==owner.getObjectId())lines.add("BOT "+pos(p)+" target="+(p.getTarget()==null?"none":pos(p.getTarget())));
   World.getInstance().forEachObject(o->{if(o instanceof Npc n&&n.getWorldId()==owner.getWorldId()&&n.getInstanceId()==owner.getInstanceId()&&PositionUtil.getDistance(owner,n)<65){
    var g=GeoService.getInstance();var s=n.getSpawn();
    lines.add("NPC "+pos(n)+" template="+n.getNpcId()+" hp="+n.getLifeStats().getCurrentHp()+" dead="+n.isDead()+" spawned="+n.isSpawned()+" see="+g.canSee(owner,n)+" target="+(n.getTarget()==null?"none":n.getTarget().getName())+" groundNearOwner="+g.getZ(n.getWorldId(),n.getX(),n.getY(),owner.getZ()+8,owner.getZ()-40,n.getInstanceId())+" groundAtNpc="+g.getZ(n.getWorldId(),n.getX(),n.getY(),n.getZ()+3,n.getZ()-10,n.getInstanceId())+" spawn="+(s==null?"none":s.getX()+","+s.getY()+","+s.getZ()+" static="+s.getStaticId())+" AI="+n.getAi().getClass().getName());
   }});
  }catch(Throwable t){lines.add("FAIL "+t);for(var s:t.getStackTrace())lines.add(s.toString());}
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),Path.of(a[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
