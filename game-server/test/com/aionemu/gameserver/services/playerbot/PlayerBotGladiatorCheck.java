package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.attack.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.templates.stats.StatsTemplate;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Actual Session priorities/final admission and fresh engine planning; no casts/world/DB/IDs. */
public final class PlayerBotGladiatorCheck {
 static int checks;static Unsafe u;static Actor bot,target;static SkillData data;
 static PlayerBotSession session;static Method priority;static Constructor<?> cast;
 static class Stats extends PlayerBotClericCheck.Stats {
  @Override public StatsTemplate getStatsTemplate(){return new StatsTemplate(){@Override public int getAttack(){return 100;}};}
 }
 static class Gear extends Equipment {
  Gear(){super(null);}@Override public Item getMainHandWeapon(){return null;}
 }
 static class Actor extends PlayerBotClericCheck.Actor {
  long parry;Gear gear;
  Actor(){super();}
  @Override public Equipment getEquipment(){return gear;}
  @Override public boolean isPlayerBot(){return false;}
  @Override public long getLastCounterSkill(AttackStatus status){return status==AttackStatus.PARRY?parry:0;}
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)u.allocateInstance(Actor.class);a.id=id;a.spawned=true;a.pc=PlayerClass.GLADIATOR;
  a.life=(PlayerBotSorcererCheck.Life)u.allocateInstance(PlayerBotSorcererCheck.Life.class);a.life.hp=a.life.mp=1000;
  a.stats=(Stats)u.allocateInstance(Stats.class);a.gear=(Gear)u.allocateInstance(Gear.class);
  a.effects=new PlayerBotSorcererCheck.Effects(a);a.move=new PlayerBotSorcererCheck.Move(a);a.chains=new ChainSkills();a.learned=new PlayerSkillList();
  PlayerBotClericCheck.field(a,Creature.class,"aggroList",new AggroList(a));return a;
 }
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static double score(int id)throws Exception{return (double)priority.invoke(session,entry(id),target,true,List.of(bot,target));}
 static Action action(int id)throws Exception{return (Action)cast.newInstance(session,entry(id),target,List.of());}
 static String choose(int...ids)throws Exception {
  var engine=new PlayerBotEngine();var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);
  for(int id:ids){var real=action(id);double value=score(id);
   plan.triggers("gladiator counters",State.COMBAT).add(new Trigger(()->value>0,new Action(){
    public String name(){return "skill:"+id;}public boolean isUseful(){return real.isUseful();}public boolean isPossible(){return real.isPossible();}public boolean execute(){return true;}
   },()->value));}
  plan.defaults("native melee",new Action(){public String name(){return "melee";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Fresh engine resolves real final gates or native fallback");return engine.getLastAction();
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);u=(Unsafe)f.get(null);bot=actor(830002);target=actor(830003);
  var group=(PlayerBotClericCheck.Group)u.allocateInstance(PlayerBotClericCheck.Group.class);group.members=List.of(bot,target);bot.group=target.group=group;
  var context=JAXBContext.newInstance(SkillData.class);
  data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991601" activation="ACTIVE" duration="0" skilltype="PHYSICAL" counter_skill="PARRY"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><skillatk value="25" e="1"/></effects></skill_template>
    <skill_template skill_id="991602" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="20"/></endconditions><effects><skillatk value="1000" e="1"/></effects></skill_template>
    <skill_template skill_id="991603" activation="ACTIVE" duration="0" skilltype="PHYSICAL" counter_skill="PARRY"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><skillatkdraininstant hp_percent="150" value="25" e="1"/></effects></skill_template>
    <skill_template skill_id="991604" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="G_2TH" precategory="G_1TH" time="4000"/></startconditions><effects><skillatk value="1500" e="1"/></effects></skill_template>
    <skill_template skill_id="991605" activation="ACTIVE" duration="0" skilltype="PHYSICAL" counter_skill="PARRY"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="G_2TH" precategory="G_1TH" time="4000"/></startconditions><effects><skillatk value="1500" e="1"/></effects></skill_template>
    <skill_template skill_id="991606" activation="ACTIVE" duration="0" skilltype="PHYSICAL" counter_skill="BLOCK"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="2000" e="1"/></effects></skill_template>
    <skill_template skill_id="991607" activation="ACTIVE" duration="0" skilltype="PHYSICAL" counter_skill="PARRY"><properties first_target="ME" target_relation="ENEMY" target_type="AREA" effective_range="7" target_maxcount="6"/><effects><skillatk value="2000" e="1"/></effects></skill_template>
    <skill_template skill_id="991608" activation="ACTIVE" duration="0" skilltype="PHYSICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="25" e="1"/><stun duration2="5000" e="2"/></effects></skill_template>
    <skill_template skill_id="991609" activation="ACTIVE" duration="0" skilltype="PHYSICAL" stack="G_DOT"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><bleed value="50" checktime="2000" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991610" activation="ACTIVE" duration="0"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="300" e="1"/></effects></skill_template>
   </skill_data>
   """));DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().map(s->new PlayerSkillEntry(s.getSkillId(),1,0,Persistable.PersistentState.NEW)).toList());
  session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);PlayerBotClericCheck.field(session,PlayerBotSession.class,"bot",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"owner",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"role",Role.MELEE);
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"skills",List.of());
  priority=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);priority.setAccessible(true);
  cast=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructors()[0];cast.setAccessible(true);
  bot.parry=System.currentTimeMillis();
  if(args.length>0 && args[0].equals("--baseline")){
   check(action(991601).isUseful() && action(991601).isPossible(),"Installed final gates already admit native counter");
   check(score(991601)<score(991602) && choose(991601,991602).equals("skill:991602"),"Installed ordinary filler displaces admitted reactive attack");
   System.out.println("CONFIRMED: admitted counter loses to ordinary filler on installed baseline; "+checks+" checks");return;
  }
  check(score(991601)>score(991602) && score(991601)<INTERRUPT,"Admitted counter uses bounded reactive band below interrupts");
  check(choose(991601,991602).equals("skill:991601"),"Native window is used before larger filler through final gates");
  check(bot.parry>0 && bot.life.mp==1000 && bot.effects.observed.isEmpty(),"Reactive planning fabricates no parry, damage, healing or MP spending");
  bot.parry=0;check(!action(991601).isPossible() && choose(991601,991602).equals("skill:991602"),"No native parry event preserves affordable learned filler");
  bot.parry=System.currentTimeMillis()-5100;check(!action(991601).isPossible() && choose(991601).equals("melee"),"Expired native window preserves weapon fallback");
  bot.parry=System.currentTimeMillis();double planned=score(991601);var pending=action(991601);bot.parry=System.currentTimeMillis()-5100;
  check(planned>30 && !pending.isPossible(),"Window expiring after scoring is rejected by the actual final native gate");
  bot.parry=System.currentTimeMillis();check(!action(991606).isPossible() && choose(991601,991606).equals("skill:991601"),"Parry cannot manufacture a block event");
  bot.life.mp=50;check(choose(991601,991602).equals("skill:991602"),"Native unaffordable counter falls back to cheaper filler");bot.life.mp=1000;
  bot.disabled=true;check(choose(991601,991602).equals("melee"),"Native disabled/cooldown gates remain final");bot.disabled=false;
  check(choose(991602,991605).equals("skill:991602"),"Counter with missing native chain cannot bypass chain prerequisites");
  bot.chains.updateChain("G_1TH",4000);int count=bot.chains.getCurrentChainSkill().getUseCount();
  check(choose(991601,991604).equals("skill:991601"),"Bounded reactive decision precedes ordinary native chain at its existing band");
  check(bot.chains.getCurrentChainSkill().getUseCount()==count,"Planning does not consume a native chain");bot.chains.resetChain();
  bot.life.hp=400;check(choose(991601,991603).equals("skill:991603"),"Existing drain fitness selects useful learned counter recovery");bot.life.hp=1000;
  target.casting=new Skill(entry(991608).template(),target,bot,1);check(choose(991601,991608).equals("skill:991608"),"Existing interrupt takes precedence over reactive offense");target.casting=null;
  target.life.hp=200;check(choose(991601,991610).equals("skill:991610"),"Critical native heal retains precedence");target.life.hp=1000;
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"withholdDamage",true);check(choose(991601,991602).equals("melee"),"Existing threat hold suppresses reactive damage");PlayerBotClericCheck.field(session,PlayerBotSession.class,"withholdDamage",false);
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"areaSkills",false);check(!action(991607).isPossible(),"Counter flag cannot bypass existing disabled-AoE safety gate");
  double upkeep=score(991609);check(upkeep>score(991602),"Existing periodic upkeep remains above filler");
  target.effects.observed.add(new Effect(bot,target,entry(991609).template(),1){@Override public long getRemainingTimeMillis(){return 9000;}});
  check(!action(991609).isUseful() && choose(991602,991609).equals("skill:991602"),"Existing active DoT final gate and fallback preserved");target.effects.observed.clear();
  for(var pc:PlayerClass.values())if(pc!=PlayerClass.GLADIATOR){bot.pc=pc;check(score(991601)<31,"Other class reactive ranking unchanged: "+pc);}bot.pc=PlayerClass.GLADIATOR;
  var strategy=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotGladiator").getDeclaredMethod("strategy",PlayerClass.class);strategy.setAccessible(true);
  check(strategy.invoke(null,PlayerClass.TEMPLAR).equals("templar protection") && strategy.invoke(null,PlayerClass.CHANTER).equals("chanter mantras"),"Previous named class strategies retained");
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  for(int id:new int[]{584,585,586,587,588,589,759,760}){
   var s=nativeData.getSkillTemplate(id);check(s.getCounterSkill()==AttackStatus.PARRY && PlayerBotSkills.classify(s)==SkillKind.DAMAGE,"Real learned native counter mapping: "+id);
   check(s.getProperties().getTargetType()==com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute.ONLYONE && PlayerBotOffense.instant(s),"Real direct single-target payload: "+id);
  }
  check(bot.parry>0 && bot.life.mp==1000 && bot.life.hp==1000 && target.effects.observed.isEmpty(),"Planning left native state/resources/effects unchanged");
  System.out.println("OK: "+checks+" Gladiator reactive window, Session/final gates, drain/chain/threat/interrupt/fallback checks; no casts/world/DB/IDs");
 }
}
