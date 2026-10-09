package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.skillengine.effect.AuraEffect;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Real Session recipient/priority/final gates and arbitration; no native auras, casts, world, DB or IDs. */
public final class PlayerBotChanterCheck {
 static int checks;static Unsafe unsafe;static Actor bot,ally;static SkillData data;
 static PlayerBotSession session;static Method recipient,priority;static Constructor<?> cast;
 static class Stats extends PlayerBotClericCheck.Stats {
  int rangeBoost=100;
  @Override protected Stat2 getStat(StatEnum stat,float base,Set<com.aionemu.gameserver.utils.stats.CalculationType> types){return stat==StatEnum.BOOST_MANTRA_RANGE?new AdditionStat(stat,rangeBoost,null):super.getStat(stat,base,types);}
 }
 static class Actor extends PlayerBotClericCheck.Actor {
  boolean flying;float x,z;int world=1,instance=1;
  Actor(){super();}
  @Override public boolean isFlying(){return flying;}
  @Override public float getX(){return x;}@Override public float getZ(){return z;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.spawned=true;a.pc=PlayerClass.CHANTER;a.world=a.instance=1;
  a.life=(PlayerBotSorcererCheck.Life)unsafe.allocateInstance(PlayerBotSorcererCheck.Life.class);a.life.hp=a.life.mp=1000;
  var stats=(Stats)unsafe.allocateInstance(Stats.class);stats.rangeBoost=100;a.stats=stats;
  a.effects=new PlayerBotSorcererCheck.Effects(a);a.move=new PlayerBotSorcererCheck.Move(a);a.chains=new ChainSkills();a.learned=new PlayerSkillList();
  PlayerBotClericCheck.field(a,Creature.class,"aggroList",new AggroList(a));return a;
 }
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static Creature recipient(int id,boolean combat)throws Exception{return (Creature)recipient.invoke(session,entry(id),List.of(bot,ally),null,combat);}
 static double score(int id,boolean combat)throws Exception{return (double)priority.invoke(session,entry(id),id==991320?ally:bot,combat,List.of(bot,ally));}
 static Action action(int id)throws Exception{return (Action)cast.newInstance(session,entry(id),id==991320?ally:bot,List.of());}
 static void observe(int...ids){bot.effects.observed.clear();for(int id:ids)bot.effects.observed.add(new Effect(bot,bot,entry(id).template(),1));}
 static String choose(int...ids)throws Exception {
  var engine=new PlayerBotEngine();var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);
  for(int id:ids){var actual=action(id);boolean admitted=id==991320 || recipient(id,true)!=null;double score=score(id,true);
   plan.triggers(PlayerBotChanter.strategy(bot.pc),State.COMBAT).add(new Trigger(()->admitted && score>0,new Action(){
    public String name(){return "skill:"+id;}public boolean isUseful(){return actual.isUseful();}public boolean isPossible(){return actual.isPossible();}public boolean execute(){return true;}
   },()->score));}
  plan.defaults("native melee fallback",new Action(){public String name(){return "melee";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Fresh engine resolves actual final gates or existing fallback");return engine.getLastAction();
 }
 static String mantra(int id,int payload,String subtype,String first) {
  return "<skill_template skill_id=\""+id+"\" lvl=\"1\" activation=\"TOGGLE\" duration=\"0\" skillsubtype=\""+subtype+"\" tslot=\"NOSHOW\" stack=\"M_"+id+"\"><properties first_target=\""+first+"\" target_relation=\"FRIEND\" target_type=\"ONLYONE\"/><endconditions><mp value=\"500\"/></endconditions><effects><aura skill_id=\""+payload+"\" distance=\"25\" distance_z=\"10\" effectid=\""+id+"\" e=\"1\"/></effects></skill_template>";
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);bot=actor(810002);ally=actor(810003);ally.pc=PlayerClass.SORCERER;
  var group=(PlayerBotClericCheck.Group)unsafe.allocateInstance(PlayerBotClericCheck.Group.class);group.members=List.of(bot,ally);bot.group=ally.group=group;
  var xml=new StringBuilder("<skill_data>");for(int i=1;i<=6;i++)xml.append(mantra(991300+i,991310+i,"CHANT","ME"));
  xml.append(mantra(991307,991311,"BUFF","ME")).append(mantra(991308,991399,"CHANT","ME")).append(mantra(991309,991311,"CHANT","TARGET"));
  xml.append("""
   <skill_template skill_id="991311" activation="ACTIVE"><effects><statup e="1"><change stat="PHYSICAL_ATTACK" value="10" func="ADD"/></statup></effects></skill_template>
   <skill_template skill_id="991312" activation="ACTIVE"><effects><statup e="1"><change stat="PHYSICAL_DEFENSE" value="10" func="ADD"/></statup></effects></skill_template>
   <skill_template skill_id="991313" activation="ACTIVE"><effects><mpheal value="50" checktime="2000" duration2="10000" e="1"/></effects></skill_template>
   <skill_template skill_id="991314" activation="ACTIVE"><effects><heal value="50" checktime="2000" duration2="10000" e="1"/></effects></skill_template>
   <skill_template skill_id="991315" activation="ACTIVE"><effects><statup e="1"><change stat="FLY_SPEED" value="100" func="ADD"/></statup></effects></skill_template>
   <skill_template skill_id="991316" activation="ACTIVE"><effects><statup e="1"><change stat="SPEED" value="100" func="ADD"/></statup></effects></skill_template>
   <skill_template skill_id="991320" activation="ACTIVE" duration="0"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="300" e="1"/></effects></skill_template>
   </skill_data>
   """);
  var context=JAXBContext.newInstance(SkillData.class);data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader(xml.toString()));DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().filter(s->s.getSkillId()<=991309 || s.getSkillId()==991320).map(s->new PlayerSkillEntry(s.getSkillId(),1,0,PersistentState.NEW)).toList());
  session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);PlayerBotClericCheck.field(session,PlayerBotSession.class,"bot",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"owner",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"role",Role.SUPPORT);
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"skills",bot.learned.getAllSkills().stream().map(s->entry(s.getSkillId())).toList());
  recipient=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,com.aionemu.gameserver.model.gameobjects.Npc.class,boolean.class);recipient.setAccessible(true);
  priority=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);priority.setAccessible(true);
  cast=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructors()[0];cast.setAccessible(true);
  check(entry(991301).kind()==SkillKind.BUFF && entry(991301).template().isToggle(),"Existing classifier already understands native mantra toggles");
  check(recipient(991301,true)==bot,"Final combat recipient now admits missing learned native mantra");
  check(recipient(991301,false)==bot,"Existing noncombat setup remains admitted");
  check(choose(991301).equals("skill:991301"),"Combat setup reaches final cast gates and engine");
  check(recipient(991307,true)==null && recipient(991309,true)==null,"Other/non-self toggles keep existing combat veto");
  check(score(991308,true)==0,"Unresolved native aura payload cannot gain invented support priority");
  ally.life.mp=300;check(choose(991301,991303).equals("skill:991303"),"Low party MP ranks native mana support before generic offense support");
  check(bot.life.mp==1000 && ally.life.mp==300 && bot.effects.observed.isEmpty(),"Mantra planning grants no MP or auras");ally.life.mp=1000;
  ally.life.hp=200;check(choose(991301,991304,991320).equals("skill:991320"),"Critical direct recovery outranks mantra setup");ally.life.hp=1000;
  check(score(991305,true)==0 && score(991305,false)==0,"Grounded party does not spend a new slot on flight-only support");
  ally.flying=true;check(score(991305,true)>0,"Actual nearby flying party member gains flight-support setup");ally.flying=false;
  double stationary=score(991306,false);ally.move.moving=true;check(score(991306,false)>stationary,"Native walking support responds to noncombat travel");ally.move.moving=false;
  observe(991301);check(recipient(991301,true)==null && !action(991301).isUseful(),"Active mantra never toggled off by planning/final gate");
  check(choose(991301,991303).equals("skill:991303"),"Fill a missing slot while retaining an active mantra");
  observe(991301,991302,991303);var held=List.copyOf(bot.effects.observed);
  check(recipient(991304,true)==null && !action(991304).isUseful(),"Three native mantra slots cannot be evicted or exceeded");
  check(choose(991304).equals("melee") && held.equals(bot.effects.observed),"Full stable set retains melee fallback and exact observed effects");observe();
  bot.life.hp=540;check(recipient(991301,true)==null,"Existing low caster HP preparation guard retained");bot.life.hp=1000;
  bot.life.mp=340;check(recipient(991301,true)==null,"Existing low caster MP preparation guard retained");
  bot.life.mp=400;check(choose(991301).equals("melee"),"Actual native cost gate blocks unaffordable setup");bot.life.mp=1000;
  bot.disabled=true;check(choose(991301).equals("melee"),"Native cooldown/disabled gate keeps fallback");bot.disabled=false;
  var aura=(AuraEffect)entry(991303).template().getEffects().getEffects().getFirst();
  ally.life.mp=300;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==2,"Native aura distance includes nearby ally");
  ally.x=30;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==1,"Out-of-range ally excluded from need scoring");
  ((Stats)bot.stats).rangeBoost=200;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==2,"Native mantra-range stat boost respected");((Stats)bot.stats).rangeBoost=100;ally.x=0;
  ally.z=11;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==1,"Native vertical range respected");ally.z=0;
  ally.instance=2;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==1,"Other instance cannot affect support scoring");ally.instance=1;
  ally.world=2;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==1,"Other world cannot affect support scoring");ally.world=1;
  ally.life.hp=0;check(PlayerBotChanter.recipients(bot,aura,List.of(bot,ally)).size()==1,"Dead party member excluded");ally.life.hp=1000;ally.life.mp=1000;
  for(var pc:PlayerClass.values()){
   bot.pc=pc;check(PlayerBotCombatBuffs.useful(pc,Role.SUPPORT,entry(991301).template(),100,100)==(pc==PlayerClass.CHANTER),"Only Chanter gains combat mantra admission: "+pc);
   if(pc!=PlayerClass.CHANTER)check(Double.isNaN(PlayerBotChanter.support(bot,entry(991301),bot,List.of(bot,ally),true)),"Other class priority unchanged: "+pc);
  }bot.pc=PlayerClass.CHANTER;
  check(PlayerBotChanter.strategy(PlayerClass.CLERIC).equals("cleric recovery"),"Cleric recovery strategy retained");
  check(PlayerBotChanter.strategy(PlayerClass.SPIRIT_MASTER).equals("spiritmaster single target"),"Spiritmaster strategy retained");
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());DataManager.SKILL_DATA=nativeData;
  for(int id:new int[]{1648,1657,1714,1746,1809,1899}){
   var s=nativeData.getSkillTemplate(id);check(PlayerBotChanter.mantra(s),"Real Chanter mantra metadata: "+id);
   var a=(AuraEffect)s.getEffects().getEffects().getFirst();check(nativeData.getSkillTemplate(a.getSkillId())!=null && a.getDistance()==25 && a.getDistanceZ()==10,"Native linked payload and range: "+id);
   check(PlayerBotCombatBuffs.useful(PlayerClass.CHANTER,Role.SUPPORT,s,100,100),"Real missing mantra admitted in combat: "+id);
  }
  check(!PlayerBotBuffs.canAdd(PlayerClass.CHANTER,nativeData.getSkillTemplate(1809),List.of(nativeData.getSkillTemplate(1648))),"Existing native flight/walking mantra conflict remains protected");
  check(bot.life.hp==1000 && bot.life.mp==1000 && ally.life.hp==1000 && ally.life.mp==1000 && bot.effects.observed.isEmpty(),"Planning left HP/MP/auras unchanged");
  System.out.println("OK: "+checks+" Chanter native mantra admission, party needs, slots/range, Session/final gates and fallback checks; no casts/auras/world/DB/IDs");
 }
}
