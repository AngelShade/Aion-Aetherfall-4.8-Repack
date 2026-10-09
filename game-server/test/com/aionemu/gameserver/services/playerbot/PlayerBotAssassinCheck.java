package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Actual Session ordering and final admission on observed actors; no casts/world/DB/IDs. */
public final class PlayerBotAssassinCheck {
 static int checks;static SkillData data;static PlayerBotSession session;static Method priority;static Constructor<?> cast;
 static PlayerBotGladiatorCheck.Actor bot,target;
 static PlayerBotTemplarCheck.Enemy enemy;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static Creature recipient(int id){return entry(id).template().getProperties().getFirstTarget()==com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute.ME ? bot : target;}
 static double score(int id)throws Exception{return (double)priority.invoke(session,entry(id),recipient(id),true,List.of(bot,target));}
 static Action action(int id)throws Exception{return (Action)cast.newInstance(session,entry(id),recipient(id),List.of(enemy));}
 static void runes(int level,long remaining){
  target.effects.observed.clear();
  if(level>0)target.effects.observed.add(new Effect(bot,target,data.getSkillTemplate(991701),level){@Override public long getRemainingTimeMillis(){return remaining;}});
 }
 static void learned(boolean builder){
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().filter(s->builder || !s.hasAnyEffect(com.aionemu.gameserver.skillengine.effect.EffectType.CARVESIGNET))
   .map(s->new PlayerSkillEntry(s.getSkillId(),1,0,Persistable.PersistentState.NEW)).toList());
  try{PlayerBotClericCheck.field(session,PlayerBotSession.class,"skills",PlayerBotSkills.read(bot));}catch(Exception e){throw new RuntimeException(e);}
 }
 static String choose(int...ids)throws Exception {
  var engine=new PlayerBotEngine();var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);
  for(int id:ids){var real=action(id);double value=score(id);
   plan.triggers("assassin runes",State.COMBAT).add(new Trigger(()->value>0,new Action(){
    public String name(){return "skill:"+id;}public boolean isUseful(){return real.isUseful();}public boolean isPossible(){return real.isPossible();}public boolean execute(){return true;}
   },()->value));}
  plan.defaults("native melee",new Action(){public String name(){return "melee";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Fresh engine resolves real admission or fallback");return engine.getLastAction();
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);PlayerBotGladiatorCheck.u=(Unsafe)f.get(null);
  bot=PlayerBotGladiatorCheck.actor(840002);target=PlayerBotGladiatorCheck.actor(840003);bot.pc=target.pc=PlayerClass.ASSASSIN;
  enemy=(PlayerBotTemplarCheck.Enemy)PlayerBotGladiatorCheck.u.allocateInstance(PlayerBotTemplarCheck.Enemy.class);enemy.spawned=true;enemy.world=enemy.instance=1;
  bot.setKnownlist(new com.aionemu.gameserver.world.knownlist.KnownList(bot));target.setKnownlist(new com.aionemu.gameserver.world.knownlist.KnownList(target));
  var group=(PlayerBotClericCheck.Group)PlayerBotGladiatorCheck.u.allocateInstance(PlayerBotClericCheck.Group.class);group.members=List.of(bot,target);bot.group=target.group=group;
  var context=JAXBContext.newInstance(SkillData.class);
  data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991701" activation="ACTIVE" stack="A_RUNE" lvl="1" tslot="DEBUFF"><effects><signet e="1" duration2="15000"/></effects></skill_template>
    <skill_template skill_id="991702" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="1500" e="1"/><carvesignet e="2" signet="A_RUNE" signet_cap="5"/></effects></skill_template>
    <skill_template skill_id="991703" activation="ACTIVE" duration="1000" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><signetburst value="100" e="1" signet="A_RUNE" signetlvl="5"/></effects></skill_template>
    <skill_template skill_id="991704" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="A_2TH" precategory="A_1TH" time="4000"/></startconditions><effects><skillatk value="1500" e="1"/><carvesignet e="2" signet="A_RUNE" signet_cap="5"/></effects></skill_template>
    <skill_template skill_id="991705" activation="ACTIVE" duration="1000" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="B_2TH" precategory="B_1TH" time="4000"/></startconditions><effects><signetburst value="100" e="1" signet="A_RUNE" signetlvl="5"/></effects></skill_template>
    <skill_template skill_id="991706" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="100" e="1"/><stun duration2="5000" e="2"/></effects></skill_template>
    <skill_template skill_id="991707" activation="ACTIVE" duration="0"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="300" e="1"/></effects></skill_template>
    <skill_template skill_id="991708" activation="ACTIVE" duration="0" skilltype="PHYSICAL" stack="A_POISON" lvl="1" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><poison value="50" checktime="2000" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991709" activation="ACTIVE" duration="0" skilltype="PHYSICAL" stack="A_POISON" lvl="2" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><poison value="100" checktime="2000" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991710" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="AREA" effective_range="7"/><effects><signetburst value="100" e="1" signet="A_RUNE" signetlvl="5"/></effects></skill_template>
    <skill_template skill_id="991711" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><effects><alwaysdodge value="100" duration2="10000" e="1"/></effects></skill_template>
   </skill_data>
   """));DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  session=(PlayerBotSession)PlayerBotGladiatorCheck.u.allocateInstance(PlayerBotSession.class);
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"bot",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"owner",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"role",Role.MELEE);
  priority=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);priority.setAccessible(true);
  cast=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructors()[0];cast.setAccessible(true);
  learned(true);bot.chains.updateChain("A_1TH",4000);runes(5,10000);
  if(args.length>0 && args[0].equals("--baseline")){
   check(action(991703).isUseful() && action(991703).isPossible(),"Installed final gates already admit mature finisher");
   check(score(991703)<score(991704) && choose(991703,991704).equals("skill:991704"),"Installed chain builder displaces mature native rune finisher");
   System.out.println("CONFIRMED: mature rune finisher loses to ordinary chain builder on installed baseline; "+checks+" checks");return;
  }
  check(score(991703)==25 && score(991703)>score(991704),"Existing mature finisher band precedes ordinary chain");
  int before=bot.chains.getCurrentChainSkill().getUseCount();var beforeRunes=List.copyOf(target.effects.observed);
  check(choose(991703,991704).equals("skill:991703"),"Actual Session and final gates select mature rune finisher");
  check(before==bot.chains.getCurrentChainSkill().getUseCount() && beforeRunes.equals(target.effects.observed) && bot.life.mp==1000,"Planning spends no native chain/runes/MP");
  for(int level:new int[]{4,5,9}){runes(level,10000);check(choose(991703,991704).equals("skill:991703"),"Four-plus native runes precede builder: "+level);}
  for(int level:new int[]{1,2,3}){
   runes(level,10000);check(!action(991703).isUseful() && choose(991703,991704).equals("skill:991704"),"Immature runes preserve building: "+level);
   runes(level,3000);check(choose(991703,991704).equals("skill:991703"),"Expiring native runes precede building: "+level);
   runes(level,10000);target.life.hp=240;check(choose(991703,991704).equals("skill:991703"),"Nearly-dead target finisher precedes building: "+level);target.life.hp=1000;
  }
  runes(0,0);check(!action(991703).isUseful() && choose(991703,991702).equals("skill:991702"),"Available builder suppresses zero-rune finisher");
  learned(false);check(action(991703).isUseful() && score(991703)==8,"Existing zero-rune fallback is unchanged");runes(2,10000);check(score(991703)==12,"Existing no-builder immature fallback is unchanged");learned(true);runes(5,10000);
  bot.life.mp=50;check(choose(991703,991704).equals("skill:991704"),"Unaffordable finisher retains cheaper available chain");bot.life.mp=1000;
  bot.disabled=true;check(choose(991703,991704).equals("melee"),"Native cooldown/disabled gates remain final");bot.disabled=false;
  check(!action(991705).isPossible() && choose(991705,991704).equals("skill:991704"),"Finisher priority cannot invent native chain");
  var pending=action(991703);runes(0,0);check(!pending.isUseful(),"Lost rune stack after scoring is rechecked at final admission");runes(5,10000);
  target.casting=new Skill(entry(991706).template(),target,bot,1);check(choose(991703,991706).equals("skill:991706"),"Existing interrupt precedes rune finisher");target.casting=null;
  target.life.hp=200;check(choose(991703,991707).equals("skill:991707"),"Critical healing remains higher");target.life.hp=1000;
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"withholdDamage",true);check(choose(991703,991704).equals("melee"),"Threat hold suppresses finisher damage");PlayerBotClericCheck.field(session,PlayerBotSession.class,"withholdDamage",false);
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"areaSkills",false);check(score(991710)==25 && !action(991710).isPossible(),"Area burst retains prior ranking and final AoE safety");
  bot.chains.resetChain();runes(0,0);check(score(991708)>score(991702),"Missing poison upkeep retains priority over filler");
  target.effects.observed.add(new Effect(bot,target,entry(991708).template(),1){@Override public long getRemainingTimeMillis(){return 9000;}});
  check(!action(991708).isUseful() && choose(991708,991702).equals("skill:991702"),"Active poison is not refreshed early");
  target.effects.observed.clear();target.effects.observed.add(new Effect(bot,target,entry(991709).template(),1){@Override public long getRemainingTimeMillis(){return 1000;}});
  check(!action(991708).isUseful(),"Existing poison stronger-rank protection remains");runes(5,10000);
  for(var pc:PlayerClass.values())if(pc!=PlayerClass.ASSASSIN){bot.pc=pc;check(Double.isNaN(PlayerBotAssassin.chain(bot,entry(991704),entry(991704).template(),7)),"Other class chain ranking unchanged: "+pc);}bot.pc=PlayerClass.ASSASSIN;
  bot.life.hp=400;check(choose(991703,991711).equals("skill:991711"),"Low-health Assassin evasion remains above finisher: score="+score(991711)+", useful="+action(991711).isUseful()+", possible="+action(991711).isPossible());bot.life.hp=1000;
  check(PlayerBotAssassin.strategy(PlayerClass.GLADIATOR).equals("gladiator counters") && PlayerBotAssassin.strategy(PlayerClass.TEMPLAR).equals("templar protection"),"Previously installed class strategy names remain");
  for(double fitness:new double[]{0,3,7,99,Double.NaN})check(PlayerBotAssassin.chain(bot,entry(991704),entry(991704).template(),fitness)<24,"Chain fitness stays below urgent finisher: "+fitness);
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  var bursts=nativeData.getSkillTemplates().stream().filter(s->s.hasAnyEffect(com.aionemu.gameserver.skillengine.effect.EffectType.SIGNETBURST)
   && s.getProperties()!=null && s.getProperties().getTargetType()==com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute.ONLYONE).toList();
  check(!bursts.isEmpty(),"Native single-target rune burst templates exist");
  for(var s:bursts)check(PlayerBotSkills.classify(s)==SkillKind.DAMAGE,"Real native burst remains classified as damage: "+s.getSkillId());
  check(bot.life.mp==1000 && target.effects.observed.size()==1,"Planning preserves MP and native observed rune state");
  System.out.println("OK: "+checks+" Assassin rune/chain priority, actual Session/final gates, poison/interrupt/threat/fallback checks; no casts/world/DB/IDs");
 }
}
