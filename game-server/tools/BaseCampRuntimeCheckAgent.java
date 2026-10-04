import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.base.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.templates.spawns.*;
import com.aionemu.gameserver.model.templates.spawns.basespawns.BaseSpawnTemplate;
import com.aionemu.gameserver.model.templates.base.BaseTemplate;
import com.aionemu.gameserver.services.*;
import com.aionemu.gameserver.services.base.LegacyCampBattle;
import com.aionemu.gameserver.spawnengine.*;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.ai.event.AIEventType;
public class BaseCampRuntimeCheckAgent {
 static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static Npc make(BaseSpawnTemplate template,int id)throws Exception{
  var spot=new SpawnSpotTemplate();set(spot,"x",template.getX());set(spot,"y",template.getY());set(spot,"z",template.getZ());set(spot,"h",template.getHeading());
  var group=new SpawnGroup(template.getWorldId(),new Spawn(template.getNpcId(),0,template.getHandlerType()));
  var copy=new BaseSpawnTemplate(group,spot);copy.setId(id);copy.setOccupier(template.getOccupier());group.addSpawnTemplate(copy);
  return new Npc(new com.aionemu.gameserver.controllers.NpcController(),copy,DataManager.NPC_DATA.getNpcTemplate(copy.getNpcId()));
 }
 static void require(boolean pass,String why){if(!pass)throw new AssertionError(why);}
 public static void agentmain(String report,Instrumentation ignored)throws Exception{
  var lines=new ArrayList<String>();var fixtures=new ArrayList<Npc>();WorldMapInstance instance=null;
  try{
   int checks=0;
   for(var loc:BaseService.getInstance().getBaseLocations())if(LegacyCampBattle.legacy(loc.getWorldId())){
    int id=990000+loc.getId();var captains=new EnumMap<BaseOccupier,Npc>(BaseOccupier.class);var attackers=new EnumMap<BaseOccupier,Npc>(BaseOccupier.class);var guards=new EnumMap<BaseOccupier,Npc>(BaseOccupier.class);
    for(var g:DataManager.SPAWNS_DATA.getBaseSpawnsByLocId(loc.getId()))for(var t:g.getSpawnTemplates()){
     var s=(BaseSpawnTemplate)t;var dest=s.getHandlerType()==SpawnHandlerType.BOSS?captains:s.getHandlerType()==SpawnHandlerType.ATTACKER?attackers:s.getHandlerType()==SpawnHandlerType.SENTINEL?guards:null;
     if(dest!=null&&!dest.containsKey(s.getOccupier())){var n=make(s,id);fixtures.add(n);dest.put(s.getOccupier(),n);}
    }
    require(captains.size()==3&&attackers.size()==3&&guards.size()==3,"Missing faction roles at "+loc.getId());checks++;
    var t=new BaseTemplate();set(t,"id",id);set(t,"world",loc.getWorldId());set(t,"type",BaseType.CASUAL);var b=new CasualBase(new BaseLocation(t));
    for(var e:attackers.entrySet()){
     require(b.getOccupier(e.getValue())==e.getKey(),"NPC capture faction lost at "+loc.getId());checks++;
     for(var other:captains.entrySet())if(other.getKey()!=e.getKey()){
      require(e.getValue().isEnemy(other.getValue()),"Raider cannot oppose captain "+e.getValue().getNpcId()+" -> "+other.getValue().getNpcId());checks++;
      require(!TribeRelationService.isFriend(e.getValue(),other.getValue()),"Raider still friends with enemy captain");checks++;
     }
    }
    for(var faction:List.of(BaseOccupier.ELYOS,BaseOccupier.ASMODIANS)){
     var guard=guards.get(faction);var enemy=captains.get(BaseOccupier.BALAUR);
     require(TribeRelationService.isAggressive(guard,enemy)&&!TribeRelationService.isFriend(guard,enemy),"Specific guard hostility suppressed at "+loc.getId());checks++;
     require(!guard.isEnemy(captains.get(faction)),"Guard attacks own captain");checks++;
    }
    lines.add("OK camp "+loc.getId()+": all factions, 6 hostile captain pairs, native guard defense and NPC capture faction.");
   }
   var world=World.getInstance();var map=world.getWorldMap(210020000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,0);
   var templates=DataManager.SPAWNS_DATA.getBaseSpawnsByLocId(2120).stream().flatMap(g->g.getSpawnTemplates().stream()).map(t->(BaseSpawnTemplate)t).toList();
   var guardTemplate=templates.stream().filter(t->t.getOccupier()==BaseOccupier.ELYOS&&t.getHandlerType()==SpawnHandlerType.SENTINEL).findFirst().orElseThrow();
   var bossTemplate=templates.stream().filter(t->t.getOccupier()==BaseOccupier.ELYOS&&t.getHandlerType()==SpawnHandlerType.BOSS).findFirst().orElseThrow();
   var attackerTemplate=templates.stream().filter(t->t.getOccupier()==BaseOccupier.BALAUR&&t.getHandlerType()==SpawnHandlerType.ATTACKER).findFirst().orElseThrow();
   // Clone native templates into an isolated instance, with non-production base IDs.
   Npc guard=make(guardTemplate,992120),boss=make(bossTemplate,992120),raider=make(attackerTemplate,992120);fixtures.addAll(List.of(guard,boss,raider));
   for(var n:List.of(guard,boss,raider)){
    n.setKnownlist(new com.aionemu.gameserver.world.knownlist.NpcKnownList(n));n.setEffectController(new com.aionemu.gameserver.controllers.effect.EffectController(n));
    float x=1811.875f+(n==guard?-1:n==raider?30:0);
    float z=com.aionemu.gameserver.world.geo.GeoService.getInstance().getZ(map.getMapId(),x,1101.75f,445,415,instance.getInstanceId());
    n.getSpawn().setX(x);n.getSpawn().setY(1101.75f);n.getSpawn().setZ(z);
    n.setPosition(new WorldPosition(map.getMapId(),x,1101.75f,z,(byte)0,instance.getRegion(x,1101.75f,z)));world.storeObject(n);world.spawn(n);
   }
   var activate=MapRegion.class.getDeclaredMethod("activate");activate.setAccessible(true);activate.invoke(guard.getPosition().getMapRegion());
   for(var n:List.of(guard,boss,raider))n.updateKnownlist();
   require(LegacyCampBattle.captain(raider)==boss,"Raider fails same-camp captain selection");checks++;
   double originalDistance=com.aionemu.gameserver.utils.PositionUtil.getDistance(raider,boss);
   require(LegacyCampBattle.advance(raider)&&raider.getMoveController().isInMove(),"Distant raider did not submit native movement: "+raider.getAi().getState());checks++;
   long movementUntil=System.currentTimeMillis()+5000;
   while(com.aionemu.gameserver.utils.PositionUtil.getDistance(raider,boss)>originalDistance-1&&System.currentTimeMillis()<movementUntil)Thread.sleep(50);
   require(com.aionemu.gameserver.utils.PositionUtil.getDistance(raider,boss)<originalDistance-1,"Native raider movement did not approach the captain");checks++;
   // Move only the isolated fixture near its captain to test the separate hate branch.
   raider.getMoveController().abortMove();raider.getAggroList().clear();raider.getAi().setStateIfNot(com.aionemu.gameserver.ai.AIState.IDLE);
   raider.getSpawn().setX(1813.875f);raider.getSpawn().setZ(435.2578f);
   world.updatePosition(raider,1813.875f,1101.75f,435.2578f,(byte)0);raider.updateKnownlist();boss.updateKnownlist();
   long raidUntil=System.currentTimeMillis()+2500;
   boolean advanced=LegacyCampBattle.advance(raider);
   while(!advanced&&!raider.getAggroList().isHating(boss)&&System.currentTimeMillis()<raidUntil){Thread.sleep(50);advanced=LegacyCampBattle.advance(raider);}
   require(advanced||raider.getAggroList().isHating(boss),"Native raider advance failed: state="+raider.getAi().getState()+" target="+raider.getTarget()+" hp="+raider.getLifeStats().getCurrentHp()+" spawned="+raider.isSpawned()+" active="+raider.getPosition().isMapRegionActive()+" casting="+raider.isCasting()+" move="+raider.canPerformMove());checks++;
   // Guard specifically opposes an ordinary Krall actor, without a player attacking it first.
   var monsterTemplate=new SpawnTemplate(new SpawnGroup(210020000,212011,0,null),1810.875f,1101.75f,435.2578f,(byte)0,0,null,0);
   var monster=new Npc(new com.aionemu.gameserver.controllers.NpcController(),monsterTemplate,DataManager.NPC_DATA.getNpcTemplate(212011));fixtures.add(monster);
   monster.setKnownlist(new com.aionemu.gameserver.world.knownlist.NpcKnownList(monster));monster.setEffectController(new com.aionemu.gameserver.controllers.effect.EffectController(monster));
   monster.setPosition(new WorldPosition(map.getMapId(),1810.875f,1101.75f,435.2578f,(byte)0,instance.getRegion(1810.875f,1101.75f,435.2578f)));world.storeObject(monster);world.spawn(monster);
   monster.updateKnownlist();guard.updateKnownlist();guard.getAi().onCreatureEvent(AIEventType.CREATURE_SEE,monster);
   long until=System.currentTimeMillis()+6000;while(!guard.getAggroList().isHating(monster)&&System.currentTimeMillis()<until)Thread.sleep(50);
   require(guard.getAggroList().isHating(monster),"Guard never acquired native Krall threat: state="+guard.getAi().getState()+" target="+guard.getTarget()+" active="+guard.getPosition().isMapRegionActive()+" knows="+guard.getKnownList().knows(monster)+" sees="+guard.canSee(monster)+" aggressive="+TribeRelationService.isAggressive(guard,monster)+" friend="+TribeRelationService.isFriend(guard,monster)+" enemy="+guard.isEnemy(monster)+" range="+guard.getAggroRange()+" angle="+guard.getAggroAngle()+" canThink="+guard.getAi().canThink()+" hp="+guard.getLifeStats().getCurrentHp());checks++;
   int hp=monster.getLifeStats().getCurrentHp();until=System.currentTimeMillis()+10000;while(monster.getLifeStats().getCurrentHp()>=hp&&System.currentTimeMillis()<until)Thread.sleep(50);
   require(monster.getLifeStats().getCurrentHp()<hp,"Native NPC combat did not deal damage");checks++;
   lines.add("OK isolated native assault captain selection, actual collision-checked movement, native hate acquisition and guard/NPC combat damage. No player intervention.");
   lines.add("OK "+checks+" checks; fixture IDs are not production bases; no DB or human character writes.");
  }catch(Throwable t){var s=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(s));lines.add("FAIL "+s);}
  finally{
   for(var n:fixtures){n.getController().cancelCurrentSkill(null);if(n.getEffectController()!=null)n.getEffectController().removeAllEffects(true);n.getLifeStats().cancelAllTasks();n.getAggroList().clear();if(n.isSpawned())World.getInstance().despawn(n);World.getInstance().removeObject(n);}
   if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());
  }
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),Path.of(a[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
