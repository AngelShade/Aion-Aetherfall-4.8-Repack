import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.services.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.templates.spawns.basespawns.BaseSpawnTemplate;
public class BaseCampInspectAgent {
 static boolean map(int id){return Set.of(210020000,210040000,220020000,220040000).contains(id);}
 public static void agentmain(String report,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();lines.add("kind\tbase\tworld\toccupier\thandler\tnpc\tx\ty\tz\tground\tdelta\tai\ttribe\tbaseTribe");
  try{
   for(var loc:BaseService.getInstance().getBaseLocations())if(map(loc.getWorldId())){
    var active=BaseService.getInstance().getActiveBase(loc.getId());lines.add("BASE "+loc.getId()+" owner="+loc.getOccupier()+" active="+(active!=null)+" assault="+(active!=null&&active.isUnderAssault()));
    for(var group:DataManager.SPAWNS_DATA.getBaseSpawnsByLocId(loc.getId()))for(var t:group.getSpawnTemplates()){
     var s=(BaseSpawnTemplate)t;var nt=DataManager.NPC_DATA.getNpcTemplate(s.getNpcId());
     float ground=GeoService.getInstance().getZ(s.getWorldId(),s.getX(),s.getY(),s.getZ()+2,s.getZ()-20,1);
     lines.add("SPAWN\t"+s.getId()+"\t"+s.getWorldId()+"\t"+s.getOccupier()+"\t"+s.getHandlerType()+"\t"+s.getNpcId()+"\t"+s.getX()+"\t"+s.getY()+"\t"+s.getZ()+"\t"+ground+"\t"+(s.getZ()-ground)+"\t"+nt.getAiName()+"\t"+nt.getTribe()+"\t"+nt.getName());
    }
   }
   var camps=new ArrayList<Npc>();World.getInstance().forEachObject(o->{if(o instanceof Npc n&&n.getSpawn() instanceof BaseSpawnTemplate s&&map(n.getWorldId())){camps.add(n);lines.add("LIVE "+s.getId()+" "+s.getOccupier()+" "+s.getHandlerType()+" npc="+n.getNpcId()+" oid="+n.getObjectId()+" xyz="+n.getX()+","+n.getY()+","+n.getZ()+" ai="+n.getAi().getClass().getName()+" tribe="+n.getTribe()+" base="+n.getBaseTribe()+" hp="+n.getLifeStats().getCurrentHp()+" target="+(n.getTarget()==null?"none":n.getTarget().getName()));}});
   for(var a:camps)for(var b:camps)if(a!=b&&((BaseSpawnTemplate)a.getSpawn()).getId()==((BaseSpawnTemplate)b.getSpawn()).getId()&&a.getRace()!=b.getRace())lines.add("REL "+a.getNpcId()+" -> "+b.getNpcId()+" enemy="+a.isEnemy(b)+" aggressive="+TribeRelationService.isAggressive(a,b)+" friend="+TribeRelationService.isFriend(a,b)+" distance="+com.aionemu.gameserver.utils.PositionUtil.getDistance(a,b));
  }catch(Throwable t){lines.add("FAIL "+t);for(var s:t.getStackTrace())lines.add(s.toString());}
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),Path.of(a[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}

