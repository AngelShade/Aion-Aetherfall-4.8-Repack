import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
public class PlayerBotTargetValuesCheckAgent {
 static Player make(PlayerClass pc)throws Exception {int id=IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("TargetValuesFixture"+id);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(pc);common.setDaeva(true);common.setLevel(20);var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("TargetValuesFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));var p=PlayerService.newPlayer(data,account);p.setPlayerBotOwner(Integer.MAX_VALUE);PlayerBotGenerationOptions.initialize(p);p.setMotions(new com.aionemu.gameserver.model.gameobjects.player.motion.MotionList(p));return p;}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();List<Player> players=new ArrayList<>();List<Npc> mobs=new ArrayList<>();WorldMapInstance instance=null;PlayerBotSession session=null;PlayerBotLease lease=null;
  synchronized(PlayerBotService.getInstance()){try {
   var world=World.getInstance();var map=world.getWorldMap(300040000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);
   for(var pc:List.of(PlayerClass.CLERIC,PlayerClass.TEMPLAR,PlayerClass.TEMPLAR)){var p=make(pc);players.add(p);world.setPosition(p,map.getMapId(),instance.getInstanceId(),500,500,100,(byte)0);world.storeObject(p);world.spawn(p);}
   var owner=players.get(0);var tank=players.get(1);var otherTank=players.get(2);
   var template=DataManager.NPC_DATA.getNpcTemplate(210001);if(template==null)throw new AssertionError("Native NPC template missing");
   for(int i=0;i<3;i++){
    var group=new com.aionemu.gameserver.model.templates.spawns.SpawnGroup(map.getMapId(),template.getTemplateId(),0,null);
    var spawn=new com.aionemu.gameserver.model.templates.spawns.SpawnTemplate(group,503+i*2,500,100,(byte)0,0,null,0,0,com.aionemu.gameserver.model.templates.spawns.SpawnTemplate.NO_AI);
    var npc=new Npc(new com.aionemu.gameserver.controllers.NpcController(){@Override public void onAddHate(Creature c,boolean first){}},spawn,template);mobs.add(npc);
    npc.setKnownlist(new com.aionemu.gameserver.world.knownlist.NpcKnownList(npc));npc.setEffectController(new com.aionemu.gameserver.controllers.effect.EffectController(npc));world.setPosition(npc,map.getMapId(),instance.getInstanceId(),spawn.getX(),500,100,(byte)0);world.storeObject(npc);world.spawn(npc);
   }
   var near=mobs.get(0);var weak=mobs.get(1);var loose=mobs.get(2);var party=List.of(owner,tank,otherTank);
   near.setTarget(tank);weak.setTarget(tank);loose.setTarget(owner);near.getAggroList().addHate(tank,1000);weak.getAggroList().addHate(tank,10);
   if(near.getAggroList().getHate(tank)!=1000 || weak.getAggroList().getHate(tank)!=10)throw new AssertionError("Native fixture hate was not admitted");
   Constructor<PlayerBotSession> ctor=PlayerBotSession.class.getDeclaredConstructor(Player.class,Player.class,PlayerBotLease.class,int.class,boolean.class);ctor.setAccessible(true);lease=PlayerBotLease.acquire(tank.getObjectId());session=ctor.newInstance(owner,tank,lease,0,false);
   Method choose=PlayerBotSession.class.getDeclaredMethod("chooseTarget",List.class,List.class);choose.setAccessible(true);
   owner.setTarget(near);if(choose.invoke(session,mobs,party)!=loose)throw new AssertionError("Tank ignored loose party victim");lines.add("OK: installed session recovers a party victim before leader assistance.");
   loose.setTarget(otherTank);owner.setTarget(null);world.setPosition(weak,map.getMapId(),instance.getInstanceId(),503,501,100,(byte)0);
   if(choose.invoke(session,mobs,party)!=weak)throw new AssertionError("Tank did not reinforce weaker native hate or stole another tank's enemy");lines.add("OK: installed session selects weaker real native hate in range and protects another tank's target.");
   Method value=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotTargetValues").getDeclaredMethod("choose",Player.class,Player.class,PlayerBotRules.Role.class,int.class,List.class,List.class,List.class);value.setAccessible(true);
   near.getLifeStats().setCurrentHpPercent(90);weak.getLifeStats().setCurrentHpPercent(20);var skills=List.of(new PlayerBotSkills.Entry(null,1,PlayerBotRules.SkillKind.DAMAGE,25));
   if(value.invoke(null,owner,tank,PlayerBotRules.Role.RANGED,0,List.of(near,weak),party,skills)!=weak)throw new AssertionError("Ranged target did not finish the lower native-health enemy in range");
   if(value.invoke(null,owner,tank,PlayerBotRules.Role.RANGED,near.getObjectId(),List.of(near,weak),party,skills)!=near)throw new AssertionError("Direct attack command was ignored");lines.add("OK: native skill range/health finishing and direct attack priority.");
   lines.add("SUCCESS: installed session/target values use real NPC victims, hate, life, range and party roles. No database or human character writes.");
  }catch(Throwable e){var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);}
  finally{
   if(session!=null){Method close=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestSync").getDeclaredMethod("close",PlayerBotSession.class);close.setAccessible(true);close.invoke(null,session);}if(lease!=null)lease.close();
   for(var n:mobs){n.getAggroList().clear();if(n.isSpawned())World.getInstance().despawn(n);World.getInstance().removeObject(n);}
   for(var p:players){if(p.isSpawned())World.getInstance().despawn(p);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);for(var i:p.getAllItems())IDFactory.getInstance().releaseId(i.getObjectId());IDFactory.getInstance().releaseId(p.getObjectId());Files.deleteIfExists(Path.of("config/playerbots/character-"+p.getObjectId()+".properties"));}
   if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());
  }}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);if(lines.stream().anyMatch(s->s.startsWith("FAIL:")))throw new AssertionError("Native targets failed; diagnostic saved");
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
