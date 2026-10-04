import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Actual native target conditions; isolated unsaved actors, no account/DB writes. */
public class PlayerBotPortRevisionCheckAgent {
 static Player make()throws Exception {int id=IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("PortRevisionFixture"+id);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(PlayerClass.CLERIC);common.setDaeva(true);common.setLevel(20);var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("PortRevisionFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));var p=PlayerService.newPlayer(data,account);p.setPlayerBotOwner(Integer.MAX_VALUE);PlayerBotGenerationOptions.initialize(p);return p;}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();List<Player> players=new ArrayList<>();WorldMapInstance instance=null;
  synchronized(PlayerBotService.getInstance()) {try {
   for(int id=3365;id<=3369;id++)if(PlayerBotSkills.classify(DataManager.SKILL_DATA.getSkillTemplate(id))!=PlayerBotRules.SkillKind.DAMAGE)throw new AssertionError("Mixed rune/damage/hate rank was classified as a taunt: "+id);
   lines.add("OK: five actual Ripclaw Strike ranks remain offensive Assassin actions.");
   var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new java.io.StringReader("""
    <skill_data>
     <skill_template skill_id="990001" name="ground-only heal fixture" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="BUFF">
      <properties first_target="TARGET" first_target_range="20" target_relation="FRIEND" target_type="ONLYONE"/>
      <startconditions><targetflying restriction="GROUND"/></startconditions><effects><healinstant value="100"/></effects>
     </skill_template>
     <skill_template skill_id="990002" name="ground-only buff fixture" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="BUFF">
      <properties first_target="TARGET" first_target_range="20" target_relation="FRIEND" target_type="ONLYONE"/>
      <startconditions><targetflying restriction="GROUND"/></startconditions><effects><statup stat="MAXHP" value="100"/></effects>
     </skill_template>
    </skill_data>
    """));
   var world=World.getInstance();var map=world.getWorldMap(300040000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);
   for(int i=0;i<3;i++){var p=make();players.add(p);world.setPosition(p,map.getMapId(),instance.getInstanceId(),500,500,100,(byte)0);world.storeObject(p);world.spawn(p);}
   var bot=players.get(0);var flying=players.get(1);var ground=players.get(2);flying.setState(CreatureState.FLYING);flying.getLifeStats().setCurrentHpPercent(10);ground.getLifeStats().setCurrentHpPercent(50);
   Constructor<PlayerBotSession> ctor=PlayerBotSession.class.getDeclaredConstructor(Player.class,Player.class,PlayerBotLease.class,int.class,boolean.class);ctor.setAccessible(true);var lease=PlayerBotLease.acquire(bot.getObjectId());PlayerBotSession session=null;
   try {session=ctor.newInstance(ground,bot,lease,0,false);Method recipient=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,com.aionemu.gameserver.model.gameobjects.Npc.class,boolean.class);recipient.setAccessible(true);
    for(int id:new int[]{990001,990002}) {var template=data.getSkillTemplate(id);var entry=new PlayerBotSkills.Entry(template,1,PlayerBotSkills.classify(template),20);int hp=flying.getLifeStats().getCurrentHp(),mp=bot.getLifeStats().getCurrentMp();Object target=recipient.invoke(session,entry,List.of(flying,ground),null,false);if(target!=ground || flying.getLifeStats().getCurrentHp()!=hp || bot.getLifeStats().getCurrentMp()!=mp)throw new AssertionError("Invalid first recipient starved support or planning mutated life/resources: "+id);lines.add("OK: "+entry.kind()+" bypasses ineligible first native target; no cast, healing, buff or resource changes.");}
   }finally{if(session!=null){Method close=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestSync").getDeclaredMethod("close",PlayerBotSession.class);close.setAccessible(true);close.invoke(null,session);}lease.close();}
   lines.add("SUCCESS: five production skill ranks and two actual native support selection cases. No live character or database changes.");
  }catch(Throwable e){var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);throw e;}
  finally{for(var p:players){if(p.isSpawned())World.getInstance().despawn(p);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);for(var item:p.getAllItems())IDFactory.getInstance().releaseId(item.getObjectId());IDFactory.getInstance().releaseId(p.getObjectId());}if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());}}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
