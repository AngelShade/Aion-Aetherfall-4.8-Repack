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
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.team.group.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Installed healing selection/casting using isolated unsaved native players and a summon. */
public class PlayerBotHealingCheckAgent7 {
 static Player make()throws Exception {int id=IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("HealingFixture"+id);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(PlayerClass.CLERIC);common.setDaeva(true);common.setLevel(20);var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("HealingFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));var p=PlayerService.newPlayer(data,account);p.setPlayerBotOwner(Integer.MAX_VALUE);PlayerBotGenerationOptions.initialize(p);p.setMotions(new com.aionemu.gameserver.model.gameobjects.player.motion.MotionList(p));return p;}
 static void set(Object o,String name,Object value)throws Exception {for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static PlayerBotSkills.Entry entry(com.aionemu.gameserver.skillengine.model.SkillTemplate t){return new PlayerBotSkills.Entry(t,t.getLvl(),PlayerBotSkills.classify(t),Math.max(1,t.getProperties().getFirstTargetRange()));}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();List<Player> players=new ArrayList<>();WorldMapInstance instance=null;PlayerGroup group=null;Summon pet=null;PlayerBotSession session=null;PlayerBotLease lease=null;
  synchronized(PlayerBotService.getInstance()){try {
   var world=World.getInstance();var map=world.getWorldMap(300040000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);
   for(int i=0;i<4;i++){var p=make();players.add(p);world.setPosition(p,map.getMapId(),instance.getInstanceId(),500+i*.4f,500,100,(byte)0);world.storeObject(p);world.spawn(p);}
   var healer=players.get(0);var ally=players.get(1);var other=players.get(2);var second=players.get(3);
   group=new PlayerGroup(new PlayerGroupMember(healer),com.aionemu.gameserver.model.team.TeamType.GROUP,0);for(var p:players)group.addMember(new PlayerGroupMember(p));
   var healing=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotHealing");
   Method select=healing.getDeclaredMethod("recipient",Player.class,PlayerBotSkills.Entry.class,List.class),priority=healing.getDeclaredMethod("priority",Player.class,PlayerBotSkills.Entry.class,Creature.class,List.class),reserve=healing.getDeclaredMethod("reserve",Player.class,PlayerBotSkills.Entry.class,Skill.class,long.class);select.setAccessible(true);priority.setAccessible(true);reserve.setAccessible(true);
   var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new java.io.StringReader("""
    <skill_data>
     <skill_template skill_id="990101" name="single health fixture" lvl="1" activation="PROVOKED" skilltype="MAGICAL" skillsubtype="HEAL"><properties first_target="TARGET" first_target_range="20" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="250" e="1" noresist="true"/></effects></skill_template>
     <skill_template skill_id="990102" name="player-only health fixture" lvl="1" activation="PROVOKED" skilltype="MAGICAL" skillsubtype="HEAL"><properties first_target="TARGET" first_target_range="20" target_relation="FRIEND" target_type="ONLYONE" target_species="PC"/><effects><healinstant value="250" e="1" noresist="true"/></effects></skill_template>
     <skill_template skill_id="990103" name="ground health fixture" lvl="1" activation="PROVOKED" skilltype="MAGICAL" skillsubtype="HEAL"><properties first_target="TARGET" first_target_range="20" target_relation="FRIEND" target_type="ONLYONE"/><startconditions><targetflying restriction="GROUND"/></startconditions><effects><healinstant value="250" e="1" noresist="true"/></effects></skill_template>
     <skill_template skill_id="990104" name="out-of-combat res fixture" lvl="1" activation="PROVOKED" skilltype="MAGICAL"><properties first_target="TARGET" first_target_range="20" target_relation="FRIEND" target_type="ONLYONE" target_species="PC"/><startconditions><combatcheck/></startconditions><effects><resurrect skill_id="8296" e="1" noresist="true"/></effects></skill_template>
     <skill_template skill_id="990105" name="native party pet heal fixture" lvl="1" activation="PROVOKED" skilltype="MAGICAL" skillsubtype="HEAL"><properties first_target="ME" target_relation="MYPARTY" target_type="PARTY_WITHPET" effective_range="25"/><effects><healinstant value="250" e="1" noresist="true"/></effects></skill_template>
    </skill_data>
    """));
   Field sf=com.aionemu.gameserver.model.skill.PlayerSkillList.class.getDeclaredField("skills");sf.setAccessible(true);var learned=(Map<Integer,com.aionemu.gameserver.model.skill.PlayerSkillEntry>)sf.get(healer.getSkillList());
   for(int id:new int[]{4195,1699,990101,990102,990103,990104,990105})learned.put(id,new com.aionemu.gameserver.model.skill.PlayerSkillEntry(id,1,0,com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.NEW));
   var wind=entry(DataManager.SKILL_DATA.getSkillTemplate(4195));var single=entry(data.getSkillTemplate(990101));var pc=entry(data.getSkillTemplate(990102));
   ally.getLifeStats().setCurrentHpPercent(60);other.getLifeStats().setCurrentHpPercent(60);second.getLifeStats().setCurrentHpPercent(60);int h=healer.getLifeStats().getCurrentHp(),a=ally.getLifeStats().getCurrentHp(),m=healer.getLifeStats().getCurrentMp();
   if(select.invoke(null,healer,wind,players)!=healer)throw new AssertionError("Healthy healer did not select self-centred Healing Wind for injured party");
   double groupScore=(double)priority.invoke(null,healer,wind,healer,players);other.getLifeStats().setCurrentHpPercent(100);second.getLifeStats().setCurrentHpPercent(100);double oneScore=(double)priority.invoke(null,healer,wind,healer,players);if(groupScore<=oneScore)throw new AssertionError("Three actual injured members did not increase group-heal relevance: group="+groupScore+" one="+oneScore);other.getLifeStats().setCurrentHpPercent(60);second.getLifeStats().setCurrentHpPercent(60);
   if(healer.getLifeStats().getCurrentHp()!=h || ally.getLifeStats().getCurrentHp()!=a || healer.getLifeStats().getCurrentMp()!=m)throw new AssertionError("Planning altered health/resources");lines.add("OK: real Healing Wind selects a healthy healer for injured allies, gains priority for multiple actual injuries and planning has no life/resource changes.");
   for(var p:players)p.getLifeStats().setCurrentHpPercent(100);if(select.invoke(null,healer,wind,players)!=null)throw new AssertionError("Healthy group wasted a group heal");
   ally.getLifeStats().setCurrentHpPercent(60);world.setPosition(ally,map.getMapId(),instance.getInstanceId(),540,500,100,(byte)0);if(select.invoke(null,healer,wind,players)!=null)throw new AssertionError("Out-of-range injury counted as a native group target");world.setPosition(ally,map.getMapId(),instance.getInstanceId(),502,500,100,(byte)0);lines.add("OK: healthy group and injuries outside native party-heal radius do not trigger a wasted cast.");
   ally.getLifeStats().setCurrentHpPercent(10);other.getLifeStats().setCurrentHpPercent(50);ally.setFlyState(com.aionemu.gameserver.model.gameobjects.state.FlyState.FLYING);if(select.invoke(null,healer,entry(data.getSkillTemplate(990103)),players)!=other)throw new AssertionError("Native ground target restriction ignored");ally.setFlyState(com.aionemu.gameserver.model.gameobjects.state.FlyState.NONE);lines.add("OK: native flying-target rejection selects the next legal wounded ally.");
   ally.getLifeStats().setCurrentHpPercent(75);other.getLifeStats().setCurrentHpPercent(100);PlayerBotService.getInstance().reserve(second,ally,PlayerBotRules.SkillKind.HEAL,System.currentTimeMillis()+5000);if(select.invoke(null,healer,single,players)!=null)throw new AssertionError("Duplicate mild heal ignored reservation");ally.getLifeStats().setCurrentHpPercent(20);if(select.invoke(null,healer,single,players)!=ally){Method targets=healing.getDeclaredMethod("targets",Player.class,PlayerBotSkills.Entry.class,Creature.class,List.class);targets.setAccessible(true);lines.add("SINGLE kind="+single.kind()+" hp="+ally.getLifeStats().getCurrentHp()+" visible="+com.aionemu.gameserver.world.geo.GeoService.getInstance().canSee(healer,ally)+" plan="+PlayerBotSkills.canPlan(healer,single,ally)+" targets="+targets.invoke(null,healer,single,ally,players)+" priority="+priority.invoke(null,healer,single,ally,players)+" selected="+select.invoke(null,healer,single,players));throw new AssertionError("Heal reservation starved emergency health");};lines.add("OK: in-flight reservation suppresses duplicate mild healing but not emergency support.");
   // Native summon constructor, known list and stats; no pet data/owner rows are persisted.
   var template=DataManager.NPC_DATA.getNpcData().stream().filter(t->"fire spirit".equals(t.getName())).findFirst().orElseThrow();var spawn=new com.aionemu.gameserver.model.templates.spawns.SpawnTemplate(new com.aionemu.gameserver.model.templates.spawns.SpawnGroup(map.getMapId(),template.getTemplateId(),0,null),501,500,100,(byte)0,0,null,0,0,com.aionemu.gameserver.model.templates.spawns.SpawnTemplate.NO_AI);
   pet=new Summon(IDFactory.getInstance().nextId(),new com.aionemu.gameserver.controllers.SummonController(),spawn,template,other,0);pet.setKnownlist(new com.aionemu.gameserver.world.knownlist.KnownList(pet));pet.setEffectController(new com.aionemu.gameserver.controllers.effect.EffectController(pet));other.setSummon(pet);world.setPosition(pet,map.getMapId(),instance.getInstanceId(),501,500,100,(byte)0);world.storeObject(pet);world.spawn(pet);pet.getLifeStats().synchronizeWithMaxStats();pet.getLifeStats().setCurrentHpPercent(10);
   ally.getLifeStats().setCurrentHpPercent(25);if(select.invoke(null,healer,single,players)!=ally)throw new AssertionError("Pet outranked injured player");for(var p:players)p.getLifeStats().setCurrentHpPercent(100);if(select.invoke(null,healer,single,players)!=pet || select.invoke(null,healer,pc,players)!=null)throw new AssertionError("Pet heal missing or PC-only restriction bypassed");
   if(select.invoke(null,healer,wind,players)!=null || select.invoke(null,healer,entry(data.getSkillTemplate(990105)),players)!=healer)throw new AssertionError("PARTY versus PARTY_WITHPET native distinction lost");lines.add("OK: injured players precede pets; legal friendly pet healing works; PC-only/PARTY exclusion and PARTY_WITHPET inclusion stay native.");
   pet.getLifeStats().setCurrentHpPercent(100);ally.getLifeStats().setCurrentHpPercent(60);other.getLifeStats().setCurrentHpPercent(60);
   // Construct the actual installed session action and execute a production spell.
   Constructor<PlayerBotSession> ctor=PlayerBotSession.class.getDeclaredConstructor(Player.class,Player.class,PlayerBotLease.class,int.class,boolean.class);ctor.setAccessible(true);lease=PlayerBotLease.acquire(healer.getObjectId());session=ctor.newInstance(second,healer,lease,0,false);
   var actionType=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotSession$CastAction");var actionCtor=actionType.getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);actionCtor.setAccessible(true);Object action=actionCtor.newInstance(session,wind,healer,List.of());Method useful=actionType.getDeclaredMethod("isUseful"),execute=actionType.getDeclaredMethod("execute");useful.setAccessible(true);execute.setAccessible(true);
   if(!(boolean)useful.invoke(action))throw new AssertionError("Installed CastAction discarded healthy group-heal anchor");int aBefore=ally.getLifeStats().getCurrentHp(),oBefore=other.getLifeStats().getCurrentHp(),mpBefore=healer.getLifeStats().getCurrentMp();if(!(boolean)execute.invoke(action))throw new AssertionError("Installed native Healing Wind action failed");
   if(!PlayerBotService.getInstance().isReserved(second,ally,PlayerBotRules.SkillKind.HEAL) || !PlayerBotService.getInstance().isReserved(second,other,PlayerBotRules.SkillKind.HEAL))throw new AssertionError("Actual native group cast did not reserve injured members");
   long deadline=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<deadline && (healer.isCasting() || ally.getLifeStats().getCurrentHp()==aBefore || other.getLifeStats().getCurrentHp()==oBefore))Thread.sleep(100);
   if(ally.getLifeStats().getCurrentHp()<=aBefore || other.getLifeStats().getCurrentHp()<=oBefore || healer.getLifeStats().getCurrentMp()>=mpBefore)throw new AssertionError("Native group cast did not heal both members and pay mana");lines.add("OK: actual installed CastAction cast production Healing Wind, healed two native party members, paid mana and reserved both targets.");
   ally.getLifeStats().setCurrentHp(0);ally.setState(CreatureState.DEAD);set(healer.getController(),"lastAttackedMillis",System.currentTimeMillis());Method recipient=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,Npc.class,boolean.class);recipient.setAccessible(true);
   if(recipient.invoke(session,entry(DataManager.SKILL_DATA.getSkillTemplate(1699)),players,null,true)!=ally)throw new AssertionError("Legal native resurrection still blocked by combat");if(recipient.invoke(session,entry(data.getSkillTemplate(990104)),players,null,true)!=null)throw new AssertionError("Native CombatCheck condition bypassed");lines.add("OK: native learned resurrection is selectable in combat, while CombatCheck-restricted resurrection is rejected.");
   lines.add("SUCCESS: actual native target sets/casting, party/pet healing, range/species restrictions, reservations, emergency override and spell-specific combat resurrection. No database/human-character writes.");
  }catch(Throwable e){var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);}
  finally{
   if(session!=null){Method close=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestSync").getDeclaredMethod("close",PlayerBotSession.class);close.setAccessible(true);close.invoke(null,session);}if(lease!=null)lease.close();
   if(pet!=null){pet.getMaster().setSummon(null);pet.getController().cancelCurrentSkill(null);pet.getEffectController().removeAllEffects(true);World.getInstance().removeObject(pet);}
   if(group!=null)for(var p:players){group.removeMember(p.getObjectId());p.setPlayerGroup(null);}
   for(var p:players){p.getController().cancelCurrentSkill(null);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);for(var item:p.getAllItems())IDFactory.getInstance().releaseId(item.getObjectId());IDFactory.getInstance().releaseId(p.getObjectId());Files.deleteIfExists(Path.of("config/playerbots/character-"+p.getObjectId()+".properties"));}
   if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());
  }}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);if(lines.stream().anyMatch(s->s.startsWith("FAIL:")))throw new AssertionError("Native healing fixture failed; diagnostic saved");
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}


