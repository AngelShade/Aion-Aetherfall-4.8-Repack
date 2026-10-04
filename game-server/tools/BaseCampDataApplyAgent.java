import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.alibaba.fastjson2.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.spawns.*;
import com.aionemu.gameserver.model.templates.spawns.basespawns.BaseSpawnTemplate;
import com.aionemu.gameserver.services.RespawnService;
import com.aionemu.gameserver.services.base.LegacyCampBattle;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE;
import com.aionemu.gameserver.utils.PacketSendUtility;
public class BaseCampDataApplyAgent {
 public static void agentmain(String arg,Instrumentation ignored)throws Exception{
  String[] a=arg.split("\\|",-1);byte[] payload=Files.readAllBytes(Path.of(a[0]));
  if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)).equals(a[1]))throw new IllegalStateException("Plan hash changed");
  var plan=JSON.parseObject(new String(payload,java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("changes");
  var heights=new IdentityHashMap<SpawnTemplate,Float>();var removals=Collections.newSetFromMap(new IdentityHashMap<SpawnTemplate,Boolean>());
  var lines=new ArrayList<String>();
  for(int i=0;i<plan.size();i++){
   var r=plan.getJSONObject(i);int world=r.getIntValue("world");if(!LegacyCampBattle.legacy(world))throw new IllegalStateException("Unscoped world");
   var matches=new ArrayList<SpawnTemplate>();boolean height=r.getString("kind").equals("height");
   var groups=height?DataManager.SPAWNS_DATA.getBaseSpawnsByLocId(r.getIntValue("base")):DataManager.SPAWNS_DATA.getSpawnsForNpc(world,r.getIntValue("npc"));
   for(var g:groups)for(var t:g.getSpawnTemplates())if(t.getNpcId()==r.getIntValue("npc")&&Math.abs(t.getX()-r.getFloatValue("x"))<.001&&Math.abs(t.getY()-r.getFloatValue("y"))<.001&&Math.abs(t.getZ()-r.getFloatValue("oldZ"))<.001
    &&(!height||t instanceof BaseSpawnTemplate s&&s.getOccupier().name().equals(r.getString("occupier"))&&s.getHandlerType().name().equals(r.getString("handler"))))matches.add(t);
   if(matches.size()!=1)throw new IllegalStateException("Expected one exact loaded template for "+r+", found "+matches.size());
   var t=matches.get(0);
   if(height){float z=r.getFloatValue("newZ"),ground=GeoService.getInstance().getZ(world,t.getX(),t.getY(),z+2,z-3,1);if(!Float.isFinite(ground)||Math.abs(ground-z)>.08)throw new IllegalStateException("Ground verification failed "+r+" actual="+ground);heights.put(t,z);}
   else{if(t.getClass()!=SpawnTemplate.class)throw new IllegalStateException("Special spawn must be preserved");removals.add(t);}
  }
  // All exact old coordinates and new terrain heights checked before any runtime mutation.
  for(var e:heights.entrySet()){lines.add("HEIGHT npc="+e.getKey().getNpcId()+" old="+e.getKey().getZ()+" new="+e.getValue());e.getKey().setZ(e.getValue());}
  int cancelled=RespawnService.cancelRespawns(removals::contains);
  for(int world:List.of(210020000,210040000,220020000,220040000))for(var g:DataManager.SPAWNS_DATA.getSpawnsByWorldId(world))synchronized(g.getSpawnTemplates()){g.getSpawnTemplates().removeIf(removals::contains);}
  int[] moved={0},removed={0},deferred={0};
  World.getInstance().forEachObject(o->{if(o instanceof Npc n){
   if(removals.contains(n.getSpawn())){n.getController().deleteIfAliveOrCancelRespawn();removed[0]++;}
   Float z=heights.get(n.getSpawn());if(z!=null&&n.isSpawned()&&!n.isDead()){
    if(!n.getMoveController().isInMove()&&!n.isCasting()&&n.getAi().getState()!=com.aionemu.gameserver.ai.AIState.FIGHT&&Math.hypot(n.getX()-n.getSpawn().getX(),n.getY()-n.getSpawn().getY())<.5){World.getInstance().updatePosition(n,n.getX(),n.getY(),z,n.getHeading());PacketSendUtility.broadcastPacket(n,new SM_MOVE(n,(byte)0));moved[0]++;}
    else deferred[0]++;
   }
  }});
  lines.add("OK: "+heights.size()+" loaded camp heights corrected; "+removals.size()+" exact ordinary templates removed; cancelledRespawns="+cancelled+" removedActors="+removed[0]+" repositionedActors="+moved[0]+" deferredMovingOrFighting="+deferred[0]+". Captures, HP, quests, items and human characters untouched.");
  Files.write(Path.of(a[2]),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),a[2]);}finally{vm.detach();}}
}
