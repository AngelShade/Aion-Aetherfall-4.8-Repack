import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.ai.*;
import com.aionemu.gameserver.controllers.NpcController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.properties.Properties.CastState;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.*;

/** Activate bounded mappings; test native target selection in an unsaved, isolated instance. */
public class KromedeActivationAgent24 {
 static final List<String> lines=new ArrayList<>();
 static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);lines.add("OK: "+label);}
 static Player make(int index)throws Exception{
  int id=com.aionemu.gameserver.utils.idfactory.IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("KromedeFixture"+index);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(PlayerClass.CLERIC);common.setDaeva(true);common.setLevel(37);
  var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000001);account.setName("KromedeFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));var p=PlayerService.newPlayer(data,account);p.setPlayerBotOwner(Integer.MAX_VALUE);com.aionemu.gameserver.services.playerbot.PlayerBotGenerationOptions.initialize(p);p.setMotions(new com.aionemu.gameserver.model.gameobjects.player.motion.MotionList(p));return p;
 }
 static void nativeChecks()throws Exception{
  List<Player> players=new ArrayList<>();List<Npc> npcs=new ArrayList<>();WorldMapInstance instance=null;
  try{
   var world=World.getInstance();var map=world.getWorldMap(320100000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);
   for(int i=0;i<3;i++){var p=make(i);players.add(p);world.setPosition(p,map.getMapId(),instance.getInstanceId(),422+(i==2?20:i),93,117.3f,(byte)0);world.storeObject(p);world.spawn(p);}
   for(int id:new int[]{212846,214621}){
    var boss=(Npc)SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(320100000,id,421.99f,93.19f,117.3f,(byte)0),instance.getInstanceId());npcs.add(boss);
    check(boss.getAi().getName().equals("kromede"),"native AI mapping "+id);
    synchronized(boss.getAi()){var pulse=boss.getAi().getClass().getDeclaredField("pulseTask");pulse.setAccessible(true);((Future<?>)pulse.get(boss.getAi())).cancel(false);var epoch=boss.getAi().getClass().getDeclaredField("generation");epoch.setAccessible(true);epoch.setInt(boss.getAi(),epoch.getInt(boss.getAi())+1);}
    var skills=boss.getAi().getClass().getClassLoader().loadClass("ai.instance.fireTemple.KromedeSkills");var create=skills.getMethod("create",Npc.class,int.class,Creature.class);
    for(int skillId:new int[]{16674,17056}){
     Skill skill=(Skill)create.invoke(null,boss,skillId,boss);
     check(skill.canUseSkill(CastState.CAST_START),"native self-centered cast validates "+id+" / "+skillId);
     check(skill.getFirstTarget()==boss && skill.getEffectedList().contains(players.get(0)) && skill.getEffectedList().contains(players.get(1)) && !skill.getEffectedList().contains(players.get(2)) && !skill.getEffectedList().contains(boss),"native AoE includes nearby players, excludes distant player/caster "+skillId);
     if(skillId==16674)check(skill.getSkillTemplate().getType()==SkillType.PHYSICAL && DataManager.SKILL_DATA.getSkillTemplate(skillId).getType()==SkillType.MAGICAL,"physical Verdict clone preserves shared source skill");
    }
    Skill impact=(Skill)create.invoke(null,boss,16847,players.get(2));check(!impact.canUseSkill(CastState.CAST_START),"Impact preserves range against distant target");
   }
   var trap=(Npc)SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(320100000,280501,421.99f,93.19f,117.3f,(byte)0),instance.getInstanceId());npcs.add(trap);
   check(trap.getAi().getName().equals("kromede_trap") && !((NpcAI)trap.getAi()).isMoveSupported(),"native trap mapping and stationary object");
   var task=trap.getAi().getClass().getDeclaredField("task");task.setAccessible(true);((Future<?>)task.get(trap.getAi())).cancel(false);
   var explosion=com.aionemu.gameserver.skillengine.SkillEngine.getInstance().getSkill(trap,17050,28,trap);
   check(explosion.canUseSkill(CastState.CAST_START) && explosion.getEffectedList().contains(players.get(0)) && !explosion.getEffectedList().contains(players.get(2)),"native trap AoE at object location validates without nearest-player targeting");
   // Trigger the reset while actual owned traps are present, then repeat the next-pull state.
   var boss=npcs.get(0);var owned=boss.getAi().getClass().getDeclaredField("traps");owned.setAccessible(true);((Set<Npc>)owned.get(boss.getAi())).add(trap);
   boss.getAi().onGeneralEvent(com.aionemu.gameserver.ai.event.AIEventType.BACK_HOME);
   check(!trap.isSpawned() && ((Set<?>)owned.get(boss.getAi())).isEmpty(),"native home reset deletes owned traps");
   var encounter=boss.getAi().getClass().getDeclaredField("encounter");encounter.setAccessible(true);var state=encounter.get(boss.getAi());
   check((int)state.getClass().getMethod("next",long.class,int.class).invoke(state,0L,100)==17047,"native home reset restores opening state");
   // Exercise a real trap cast and delayed cleanup with only isolated unsaved actors.
   var armed=(Npc)SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(320100000,280501,421.99f,93.19f,117.3f,(byte)0),instance.getInstanceId());npcs.add(armed);
   var armedTask=armed.getAi().getClass().getDeclaredField("task");armedTask.setAccessible(true);((Future<?>)armedTask.get(armed.getAi())).cancel(false);
   var epoch=armed.getAi().getClass().getDeclaredField("generation");epoch.setAccessible(true);
   var detonate=armed.getAi().getClass().getDeclaredMethod("detonate",int.class);detonate.setAccessible(true);detonate.invoke(armed.getAi(),epoch.getInt(armed.getAi()));
   var cast=armed.getAi().getClass().getDeclaredField("explosion");cast.setAccessible(true);var nativeCast=(Skill)cast.get(armed.getAi());
   check(nativeCast!=null && armed.isCasting(),"native trap actually starts Area Aggravate Wound");
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while(armed.isCasting() && System.nanoTime()<deadline)Thread.sleep(20);
   check(!armed.isCasting() && armed.isSpawned(),"trap survives cast completion until native hit time");
   while(armed.isSpawned() && System.nanoTime()<deadline)Thread.sleep(20);
   check(!armed.isSpawned(),"trap deletes after actual cast/hit completion; no ghost object");
  }finally{
   for(var n:npcs)if(n.isSpawned())n.getController().delete();
   for(var p:players){if(p.isSpawned())World.getInstance().despawn(p);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);com.aionemu.gameserver.utils.idfactory.IDFactory.getInstance().releaseId(p.getObjectId());}
   if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());
  }
 }
 static void recoverPortals(Instrumentation inst)throws Exception{
  inst.redefineModule(Object.class.getModule(),Set.of(),Map.of(),Map.of("java.util.concurrent",Set.of(KromedeActivationAgent24.class.getModule())),Set.of(),Map.of());
  var poolField=ThreadPoolManager.class.getDeclaredField("scheduledPool");poolField.setAccessible(true);var pool=(ScheduledThreadPoolExecutor)poolField.get(ThreadPoolManager.getInstance());
  var callable=FutureTask.class.getDeclaredField("callable");callable.setAccessible(true);var runnable=Class.forName("java.util.concurrent.Executors$RunnableAdapter").getDeclaredField("task");runnable.setAccessible(true);var wrapped=com.aionemu.commons.utils.concurrent.RunnableWrapper.class.getDeclaredField("runnable");wrapped.setAccessible(true);
  int scheduled=0;
  for(var entry:pool.getQueue())if(entry instanceof FutureTask<?>){var c=callable.get(entry);if(c!=null && c.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter")){Object r=runnable.get(c);if(r instanceof com.aionemu.commons.utils.concurrent.RunnableWrapper)r=wrapped.get(r);if(r.getClass().getName().endsWith("CronJobService$IdianDepthPortalSpawner"))scheduled++;}}
  check(scheduled<=1,"no duplicate scheduled portal rotation");
  if(scheduled==0){
   var type=Class.forName("com.aionemu.gameserver.services.CronJobService$IdianDepthPortalSpawner");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object job=ctor.newInstance();
   World.getInstance().forEachObject(o->{if(o instanceof Npc n && (n.getNpcId()==731631 || n.getNpcId()==731632))try{var field=type.getDeclaredField(n.getNpcId()==731631?"elyosUndergroundEntrance":"asmodianUndergroundEntrance");field.setAccessible(true);field.set(job,n);}catch(Exception e){throw new IllegalStateException(e);}});
   ((Runnable)job).run();lines.add("OK: recovered the portal rotation lost during the failed reload; existing entrance witnesses retained until replacement.");
  }
  var entrances=new ArrayList<Npc>();World.getInstance().forEachObject(o->{if(o instanceof Npc n && n.isSpawned() && (n.getNpcId()==731631 || n.getNpcId()==731632))entrances.add(n);});
  check(entrances.size()==2 && entrances.stream().anyMatch(n->n.getNpcId()==731631) && entrances.stream().anyMatch(n->n.getNpcId()==731632),"one live Idian Depths portal for each faction");
 }
 public static void agentmain(String argument,Instrumentation inst)throws Exception{
  Path report=Path.of(argument);var humans=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).map(Player::getObjectId).sorted().toList();var bots=World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).map(Player::getObjectId).sorted().toList();
  try{
   var ai=NpcTemplate.class.getDeclaredField("ai");ai.setAccessible(true);
   for(int id:new int[]{212846,214621,280501}){var template=DataManager.NPC_DATA.getNpcTemplate(id);String name=id==280501?"kromede_trap":"kromede";lines.add("MAPPING: "+id+" "+template.getAiName()+" -> "+name);ai.set(template,name);}
   var dummy=make(99);
   try{
    var command=ChatProcessorHolder.reload();var helper=Class.forName("com.aionemu.gameserver.ai.AIRegistryReload");var requested=helper.getDeclaredField("requested");requested.setAccessible(true);var flag=(java.util.concurrent.atomic.AtomicBoolean)requested.get(null);
    long start=System.nanoTime();command.getClass().getMethod("execute",Player.class,String[].class).invoke(command,dummy,new String[]{"ai"});long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);check(millis<500,"actual Reload.execute returns in "+millis+"ms with asynchronous compilation");
    var witness=new Npc(new NpcController(),SpawnEngine.newSingleTimeSpawn(600100000,731631,721.39f,268.67f,291.636f,(byte)60),DataManager.NPC_DATA.getNpcTemplate(731631));
    int reads=0;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
    while(flag.get() && System.nanoTime()<deadline){checkPortal(witness);reads++;Thread.sleep(5);}
    check(!flag.get(),"bounded background reload completed; "+reads+" portal lookups during compilation");
    var active=helper.getDeclaredField("active");active.setAccessible(true);var map=(Map<?,?>)active.get(null);check(map!=null && map.containsKey("portal") && map.containsKey("kromede") && map.containsKey("kromede_trap"),"published complete live AI registry ("+(map==null?0:map.size())+" handlers)");
   }finally{dummy.getLifeStats().cancelAllTasks();dummy.getEffectController().removeAllEffects(true);com.aionemu.gameserver.utils.idfactory.IDFactory.getInstance().releaseId(dummy.getObjectId());}
   nativeChecks();recoverPortals(inst);
   check(humans.equals(World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).map(Player::getObjectId).sorted().toList()),"human characters retained");
   check(bots.equals(World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).map(Player::getObjectId).sorted().toList()),"live companions retained; isolated fixtures removed");
   Files.write(report,lines,StandardOpenOption.CREATE_NEW);
  }catch(Throwable e){var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);Files.write(report,lines,StandardOpenOption.CREATE_NEW);throw e;}
 }
 static void checkPortal(Npc witness){if(!AIEngine.getInstance().newAI("portal",witness).getName().equals("portal"))throw new AssertionError("Portal handler missing during reload");}
 static class ChatProcessorHolder {static com.aionemu.gameserver.utils.chathandlers.ChatCommand reload(){return com.aionemu.gameserver.utils.chathandlers.ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals("//reload")).findFirst().orElseThrow();}}
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
