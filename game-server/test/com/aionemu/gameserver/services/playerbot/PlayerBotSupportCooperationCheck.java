package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.attack.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.world.knownlist.KnownList;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Real support recipient/recheck/arbitration and reaction deadlines, no World/SQL/IDs/native casts. */
public final class PlayerBotSupportCooperationCheck {
 static Unsafe unsafe;static SkillData data;static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static class Actor extends PlayerBotFieldCombatCheck.Actor {
  Race race;boolean playing,pvp,hostile,worldBot;int world,instance;float x;
  List<Player> nearby;
  @Override public Race getRace(){return race;}
  @Override public int getWorldId(){return world;}
  @Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}
  @Override public boolean isPlaying(){return playing;}
  @Override public boolean isInsidePvPZone(){return pvp;}
  @Override public boolean isWorldBot(){return worldBot;}
  @Override public boolean isPlayerBot(){return worldBot;}
  @Override public String getName(boolean tag){return "offline-support-"+id;}
  @Override public boolean isEnemy(Creature c){return hostile || c instanceof Actor a && a.hostile;}
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.pc=PlayerClass.CLERIC;a.race=Race.ELYOS;
  a.playing=a.spawned=a.attack=a.worldBot=true;a.world=a.instance=1;a.nearby=new ArrayList<>();
  a.life=(PlayerBotSorcererCheck.Life)unsafe.allocateInstance(PlayerBotSorcererCheck.Life.class);a.life.hp=a.life.mp=1000;
  a.stats=(PlayerBotClericCheck.Stats)unsafe.allocateInstance(PlayerBotClericCheck.Stats.class);
  a.effects=new PlayerBotSorcererCheck.Effects(a);a.move=new PlayerBotSorcererCheck.Move(a);a.chains=new ChainSkills();
  a.control=new PlayerBotFieldCombatCheck.Clock();
  a.learned=new PlayerSkillList(data.getSkillTemplates().stream().map(s->new PlayerSkillEntry(s.getSkillId(),1,0,PersistentState.NEW)).toList());
  PlayerBotCastExecutionCheck.set(a,"aggroList",new AggroList(a));
  a.seen=new KnownList(a){
   @Override public void forEachPlayer(java.util.function.Consumer<Player> consumer){List.copyOf(a.nearby).forEach(consumer);}
   @Override public boolean sees(VisibleObject other){return a.nearby.contains(other);}
  };
  return a;
 }
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static PlayerBotSession session(Actor bot,Player owner)throws Exception {
  var s=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  PlayerBotCastExecutionCheck.set(s,"bot",bot);PlayerBotCastExecutionCheck.set(s,"owner",owner);
  PlayerBotCastExecutionCheck.set(s,"engine",new PlayerBotEngine());PlayerBotCastExecutionCheck.set(s,"role",Role.HEALER);
  PlayerBotCastExecutionCheck.set(s,"order",Order.FOLLOW);PlayerBotCastExecutionCheck.set(s,"idleReaction",20);
  return s;
 }
 static Creature recipient(PlayerBotSession s,int id,List<Player> allies)throws Exception {
  var method=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,Npc.class,boolean.class);method.setAccessible(true);
  return (Creature)method.invoke(s,entry(id),allies,null,false);
 }
 static Action action(PlayerBotSession s,int id,Creature target)throws Exception {
  var c=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructors()[0];c.setAccessible(true);
  return (Action)c.newInstance(s,entry(id),target,List.of());
 }
 public static void main(String[] args)throws Exception {
  // These inert active-bot fixtures have no native human-presence World.
  com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_ONLY_OCCUPIED_MAPS=false;
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(Unsafe)field.get(null);
  data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991601" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991602" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="MYPARTY" target_type="ONLYONE"/><effects><healinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991603" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="ME" target_relation="MYPARTY" target_type="PARTY" target_maxcount="6" effective_range="25"/><effects><healinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991604" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><mphealinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991605" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><healinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991606" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="MYPET" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991607" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><resurrect value="50" e="1"/></effects></skill_template>
    <skill_template skill_id="991608" activation="ACTIVE" duration="0" skilltype="MAGICAL" stack="SUPPORT_BUFF"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><statup e="1" duration2="10000"><change stat="PHYSICAL_ATTACK" value="10"/></statup></effects></skill_template>
    <skill_template skill_id="991609" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><shield value="250" duration2="10000" e="1"/></effects></skill_template>
   </skill_data>
   """));
  DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  var bot=actor(800601);var human=actor(800602);human.worldBot=false;human.life.hp=250;human.x=5;bot.nearby.add(human);
  var s=session(bot,bot);var allies=PlayerBotSupport.members(bot,s.party());
  check(s.party().equals(List.of(bot)),"Autonomous offensive party still self-only");
  check(allies.equals(List.of(bot,human)),"Nearby injured human is in separate public support set");
  check(recipient(s,991601,allies)==human,"Production Session chooses nearby human for legal FRIEND heal");
  check(PlayerBotHealing.useful(bot,entry(991601),human),"Final heal recheck retains public recipient");
  check(action(s,991601,human).isUseful(),"Actual CastAction does not reject its selected public heal");
  var service=PlayerBotService.getInstance();var sessionsField=PlayerBotService.class.getDeclaredField("sessions");sessionsField.setAccessible(true);
  @SuppressWarnings("unchecked") var registered=(Map<Integer,PlayerBotSession>)sessionsField.get(service);
  try {
   check(registered.putIfAbsent(bot.id,s)==null,"Isolated synthetic service slot contains no production actor");
   check(PlayerBotService.allowsTarget(bot,human),"Actual native Skill admission/effect guard allows public assistance");
   human.pvp=true;check(!PlayerBotService.allowsTarget(bot,human),"Native effect recheck rejects changed PvP area");human.pvp=false;
   human.x=41;check(!PlayerBotService.allowsTarget(bot,human),"Native effect recheck rejects public recipient beyond40m");human.x=5;
  } finally {registered.remove(bot.id,s);}
  check(PlayerBotHealing.targets(bot,entry(991601),human,allies).equals(List.of(human)),"Native target validation retains only actual single heal beneficiary");
  bot.effects.observed.add(new Effect(bot,bot,entry(991608).template(),1));
  check(recipient(s,991608,allies)==human,"Missing legal friendly buff reaches nearby player after self is already buffed");
  check(action(s,991608,human).isUseful(),"Actual friendly buff retains final native effect-slot admission");
  check(recipient(s,991609,allies)==human && action(s,991609,human).isUseful(),"Emergency protective spell remains useful for public friendly recipient");
  check(recipient(s,991602,allies)==null,"Native party-only heal never selects outsider");
  check(PlayerBotHealing.targets(bot,entry(991603),bot,allies).equals(List.of(bot)),"Native group heal does not become public AoE");
  var friendBot=actor(800603);friendBot.life.hp=100;bot.nearby.add(friendBot);
  check(recipient(s,991601,PlayerBotSupport.members(bot))==friendBot,"Independent bots can heal other independent bots");
  var companion=actor(800604);var group=(PlayerBotClericCheck.Group)unsafe.allocateInstance(PlayerBotClericCheck.Group.class);
  group.members=List.of(bot,companion);bot.group=companion.group=group;companion.life.hp=200;
  check(PlayerBotSupport.members(bot).contains(companion),"Native companion party is retained alongside public allies");
  check(recipient(s,991602,PlayerBotSupport.members(bot))==companion,"Party-only heal still chooses actual companion");
  check(PlayerBotHealing.targets(bot,entry(991603),bot,PlayerBotSupport.members(bot)).equals(List.of(bot,companion)),"Actual native group targets exclude nearby outsiders");
  var controlled=session(bot,companion);
  check(recipient(controlled,991601,PlayerBotSupport.members(bot))==friendBot,"Controlled companion shares public support planning");
  bot.group=companion.group=null;
  human.race=Race.ASMODIANS;
  check(!PlayerBotSupport.members(bot).contains(human),"Opposing race is excluded even with neutral synthetic PvP relation");human.race=Race.ELYOS;
  human.hostile=true;check(!PlayerBotSupport.members(bot).contains(human),"Hostile/duel/FFA relation excluded");human.hostile=false;
  human.pvp=true;check(!PlayerBotSupport.members(bot).contains(human),"Native PvP area boundary excluded");human.pvp=false;
  human.world=2;check(!PlayerBotSupport.members(bot).contains(human),"Other map excluded");human.world=1;
  human.instance=2;check(!PlayerBotSupport.members(bot).contains(human),"Other instance excluded");human.instance=1;
  human.spawned=false;check(!PlayerBotSupport.members(bot).contains(human),"Despawned actor excluded");human.spawned=true;
  human.playing=false;check(!PlayerBotSupport.members(bot).contains(human),"Offline/nonplaying actor excluded");human.playing=true;
  human.x=41;check(!PlayerBotSupport.members(bot).contains(human),"Public assistance does not chase outside40m");human.x=5;
  var beforeMp=bot.life.mp;var beforeHp=human.life.hp;
  check(PlayerBotSkills.canPlan(bot,entry(991605),human,false),"Native resource admission accepts affordable heal");
  check(bot.life.mp==beforeMp && human.life.hp==beforeHp,"Planning neither pays nor heals");
  bot.life.mp=0;check(!PlayerBotSkills.canPlan(bot,entry(991605),human,false),"Insufficient native MP rejects unavailable spell");bot.life.mp=1000;
  bot.disabled=true;check(!action(s,991601,human).isPossible(),"Actual CastAction retains native cooldown/disabled veto");bot.disabled=false;
  check(!PlayerBotSupport.legalAnchor(bot,entry(991606),human),"Own-pet spell cannot select another player");
  human.dead=true;
  check(recipient(s,991607,PlayerBotSupport.members(bot))==human,"Legal resurrection may select public friendly corpse");human.dead=false;
  bot.nearby.clear();bot.resting=true;bot.life.hp=900;bot.life.mp=900;
  check(recipient(s,991601,PlayerBotSupport.members(bot))==bot,"Already resting actor uses self-heal through95% hysteresis");
  check(PlayerBotHealing.useful(bot,entry(991601),bot),"Self-recovery remains useful at90% HP");
  check(recipient(s,991604,PlayerBotSupport.members(bot))==bot,"Already resting actor uses available mana restoration through95%");
  PlayerBotRecoveryIntent.start(bot);bot.resting=false;
  check(recipient(s,991601,List.of(bot))==bot && recipient(s,991604,List.of(bot))==bot,"Standing to cast preserves recovery95/95 intent");
  check(!PlayerBotFieldCombat.ready(90,90,PlayerBotRecoveryIntent.active(bot)),"A recovery spell cannot lower pull readiness to85/60");
  PlayerBotRecoveryIntent.update(bot,false,true);
  check(!PlayerBotRecoveryIntent.active(bot),"Leader movement interrupts recovery intent");bot.resting=true;
  var real=action(s,991601,bot);var engine=new PlayerBotEngine();
  check(engine.tick(State.NON_COMBAT,List.of(new Strategy("recovery",List.of(
   new Trigger(()->true,new Action(){public String name(){return "sit";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},()->HIGH-1),
   new Trigger(()->true,new Action(){public String name(){return real.name();}public boolean isUseful(){return real.isUseful();}public boolean isPossible(){return real.isPossible();}public boolean execute(){return true;}},()->PlayerBotHealing.priority(bot,entry(991601),bot,List.of(bot)))
  ))),List.of(),64),"Actual heal admission and priorities participate in recovery arbitration");
  check(engine.getLastAction().startsWith("cast "),"Useful recovery spell precedes sitting fallback");
  bot.life.hp=bot.life.mp=950;
  check(recipient(s,991601,List.of(bot))==null && recipient(s,991604,List.of(bot))==null,"Recovery spells stop at95% and do not spam near-full resources");
  bot.resting=false;bot.life.hp=bot.life.mp=900;
  check(recipient(s,991601,List.of(bot))==null && recipient(s,991604,List.of(bot))==null,"Ordinary85HP/40MP support thresholds retained outside rest");
  bot.resting=true;bot.control.last=System.currentTimeMillis();PlayerBotCastExecutionCheck.set(s,"questTarget",123);
  check(s.reactionDelay()==10000 && !s.activeDecision(),"Resting stale pull/ten-second native timer cannot misclassify active backlog");
  PlayerBotCastExecutionCheck.set(s,"supporting",true);
  check(s.reactionDelay()==500 && s.activeDecision(),"Actual useful recovery/support remains on active lane");PlayerBotCastExecutionCheck.set(s,"supporting",false);
  var infoConstructor=AggroInfo.class.getDeclaredConstructor(Creature.class);infoConstructor.setAccessible(true);
  var info=infoConstructor.newInstance(human);info.addHate(1);bot.nearby.add(human);
  var aggroField=AggroList.class.getDeclaredField("aggroList");aggroField.setAccessible(true);
  @SuppressWarnings("unchecked") var aggro=(Map<Integer,AggroInfo>)aggroField.get(bot.getAggroList());aggro.put(human.id,info);
  check(s.reactionDelay()==500,"Live visible incoming threat wakes resting actor");aggro.clear();
  var deadlines=new PlayerBotDecisionSchedule();deadlines.complete(1,1000);
  check(deadlines.due(1,15000,1000)==16000,"Idle/rest reaction deadline begins at completion");
  check(deadlines.due(1,500,12000)==12500,"New urgency starts500ms from observation, not11s retroactive backlog");
  check(deadlines.due(1,500,13000)==12500,"Unserved urgent deadline is retained so real stalls remain visible");
  check(18000-deadlines.due(1,500,18000)==5500,"Genuine5s active backlog is still measured");
  deadlines.complete(1,18000);check(deadlines.due(1,500,18000)==18500,"Completion resets actual reaction interval");
  deadlines.complete(2,1000);deadlines.due(2,1000,1000);
  check(deadlines.due(2,100,5000)==2000,"Already overdue idle work is not granted a fresh grace period");
  deadlines.retain(Set.of());check(deadlines.due(1,500,20000)==0,"Removed actor deadlines do not survive retirement");
  bot.nearby.clear();
  for(int i=0;i<50;i++){var p=actor(801000+i);p.life.hp=900-i*10;p.x=1+i%20;bot.nearby.add(p);}
  var bounded=PlayerBotSupport.members(bot);
  check(bounded.size()==17 && bounded.get(1).getObjectId()==801049,"Public support candidate cap16 retains most injured nearby actor");
  check(bot.getCurrentTeam()==null && human.getCurrentTeam()==null,"Support never creates membership/custody");
  PlayerBotRecoveryIntent.clear(bot);
  System.out.println("OK: "+checks+" native spell support/recovery and reaction deadline checks; no World/SQL/IDs/casts");
 }
}
