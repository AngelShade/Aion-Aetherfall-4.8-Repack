package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Production planners/Session priorities and engine selection on world-free actors.
 * No database, native cast, world registration, character configuration or ID operations. */
public final class PlayerBotSorcererCheck {
 static int checks;static Unsafe unsafe;static Actor bot,target;static SkillData data;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Life extends PlayerLifeStats {
  int hp=1000,mp=1000;Life(){super(null);}
  @Override public int getCurrentHp(){return hp;}@Override public int getMaxHp(){return 1000;}
  @Override public int getCurrentMp(){return mp;}@Override public int getMaxMp(){return 1000;}
 }
 static class Effects extends PlayerEffectController {
  List<Effect> observed=new ArrayList<>();Effects(Creature c){super(c);}
  @Override public List<Effect> getAbnormalEffects(){return List.copyOf(observed);}
  @Override public boolean hasAbnormalEffect(Predicate<Effect> p){return observed.stream().anyMatch(p);}
 }
 static class Move extends PlayerMoveController {boolean moving;Move(Player p){super(p);}@Override public boolean isInMove(){return moving;}}
 static class Actor extends Player {
  Life life;Effects effects;Move move;ChainSkills chains;PlayerClass pc;boolean disabled;PlayerSkillList learned;
  Actor(){super(null,null);}
  @Override public PlayerLifeStats getLifeStats(){return life;}
  @Override public PlayerEffectController getEffectController(){return effects;}
  @Override public PlayerMoveController getMoveController(){return move;}
  @Override public ChainSkills getChainSkills(){return chains;}
  @Override public PlayerSkillList getSkillList(){return learned;}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public boolean isSkillDisabled(SkillTemplate t){return disabled;}
  @Override public boolean isDead(){return life.hp==0;}
 }
 static Actor actor()throws Exception {var a=(Actor)unsafe.allocateInstance(Actor.class);a.life=(Life)unsafe.allocateInstance(Life.class);a.life.hp=a.life.mp=1000;a.effects=new Effects(a);a.move=new Move(a);a.chains=new ChainSkills();a.pc=PlayerClass.SORCERER;return a;}
 static PlayerBotSkills.Entry entry(int id){var t=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(t,1,PlayerBotSkills.classify(t),25);}
 static double score(int id){return PlayerBotOffense.routine(bot,entry(id),target,List.of(),3);}
 static Effect observed(SkillTemplate skill,int level,long time){return new Effect(bot,target,skill,level){@Override public long getRemainingTimeMillis(){return time;}};}
 static String choose(int... ids){
  var engine=new PlayerBotEngine();var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);List<String> selected=new ArrayList<>();
  var triggers=plan.triggers(PlayerBotSorcerer.strategy(bot.pc),State.COMBAT);
  for(int id:ids){var e=entry(id);double score=score(id);triggers.add(new Trigger(()->score>0,new Action(){
   public String name(){return "skill:"+id;}public boolean isUseful(){return PlayerBotOffense.useful(bot,e,target);}
   public boolean isPossible(){return !bot.isSkillDisabled(e.template()) && PlayerBotSkills.chainAvailable(bot,e) && PlayerBotSkills.canPlan(bot,e,target,false);}
   public boolean execute(){selected.add(name());return true;}
  },()->score));}
  plan.defaults("weapon fallback",new Action(){public String name(){return "shoot";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){selected.add(name());return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Engine must resolve a learned spell or native weapon fallback");return selected.getFirst();
 }
 static double priority(PlayerBotSession session,PlayerBotSkills.Entry e,Creature recipient,boolean combat)throws Exception {
  var m=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);m.setAccessible(true);return (double)m.invoke(session,e,recipient,combat,List.of(bot));
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);bot=actor();target=actor();
  var context=JAXBContext.newInstance(SkillData.class);data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="990801" activation="ACTIVE" duration="2000" skilltype="MAGICAL" stack="S_FILL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><spellatkinstant value="300" e="1"/></effects></skill_template>
    <skill_template skill_id="990802" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="20"/></endconditions><effects><spellatkinstant value="100" e="1"/></effects></skill_template>
    <skill_template skill_id="990803" activation="ACTIVE" duration="1000" skilltype="MAGICAL" stack="S_DOT" lvl="1"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatk value="80" e="1" checktime="2000" duration2="10000"/></effects></skill_template>
    <skill_template skill_id="990804" activation="ACTIVE" duration="1000" skilltype="MAGICAL" stack="S_WEAK" lvl="1"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatkinstant value="100" e="1"/><statdown e="2" duration2="10000"><change stat="FIRE_RESISTANCE" value="-60" func="ADD"/></statdown></effects></skill_template>
    <skill_template skill_id="990805" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="S_2TH" precategory="S_1TH" time="4000"/></startconditions><effects><spellatkinstant value="400" e="1"/></effects></skill_template>
   </skill_data>
   """));
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().map(s->new PlayerSkillEntry(s.getSkillId(),1,0,PersistentState.NEW)).toList());
  check(score(990801)>score(990802) && score(990802)>5,"Stationary filler order retains shoot fallback below spells");
  check(choose(990801,990802).equals("skill:990801"),"Engine chooses stationary cast filler");
  bot.move.moving=true;check(choose(990801,990802).equals("skill:990802"),"Moving native instant precedes stop-to-cast filler");bot.move.moving=false;
  target.life.hp=200;check(choose(990801,990802,990803).equals("skill:990802"),"Finish with quick direct damage instead of establishing a pure DoT");target.life.hp=1000;
  check(choose(990801,990803).equals("skill:990803"),"Missing damage-over-time upkeep precedes filler");
  target.effects.observed.add(observed(entry(990803).template(),1,9000));check(choose(990801,990803).equals("skill:990801"),"Active DoT lets filler execute");
  target.effects.observed.clear();target.effects.observed.add(observed(entry(990803).template(),1,1000));check(choose(990801,990803).equals("skill:990803"),"Last native tick window refreshes DoT");
  target.effects.observed.clear();target.effects.observed.add(observed(entry(990803).template(),2,1000));check(choose(990801,990803).equals("skill:990801"),"Stronger existing learned effect protected by final usefulness");target.effects.observed.clear();
  check(choose(990801,990803,990804).equals("skill:990804"),"Native magic vulnerability established before DoT and filler");
  target.effects.observed.add(observed(entry(990804).template(),1,9000));check(score(990804)<19,"Vulnerability already present loses upkeep priority");target.effects.observed.clear();
  check(choose(990801,990805).equals("skill:990801"),"Unavailable native chain cannot be manufactured");bot.chains.updateChain("S_1TH",4000);
  int before=bot.chains.getCurrentChainSkill().getUseCount();check(choose(990801,990803,990805).equals("skill:990805"),"Admitted proc outranks upkeep and fillers");
  check(bot.chains.getCurrentChainSkill().getUseCount()==before,"Planning never consumes native chain");bot.chains.resetChain();
  bot.life.mp=50;check(choose(990801,990802).equals("skill:990802"),"Unaffordable primary falls back to affordable learned spell");check(bot.life.mp==50,"Native MP planning does not spend resources");
  bot.life.mp=0;check(choose(990801,990802).equals("shoot"),"No affordable spell falls back to weapon attack");bot.life.mp=1000;
  bot.disabled=true;check(choose(990801,990802).equals("shoot"),"Cooldown/disabled spells cannot strand filler fallback");bot.disabled=false;
  bot.pc=PlayerClass.GLADIATOR;check(score(990801)==13,"Other classes retain installed routine bands");check(PlayerBotSorcerer.strategy(bot.pc).equals("class skills"),"Other named class strategy intact");bot.pc=PlayerClass.SORCERER;
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);var sf=PlayerBotSession.class.getDeclaredField("bot");sf.setAccessible(true);sf.set(session,bot);sf=PlayerBotSession.class.getDeclaredField("role");sf.setAccessible(true);sf.set(session,Role.RANGED);
  for(int id:new int[]{1270,1350,1540}){
   var t=nativeData.getSkillTemplate(id);check(t!=null && PlayerBotSkills.classify(t)==SkillKind.BUFF,"Native caster boost remains a learned friendly buff");
   check(PlayerBotCombatBuffs.useful(PlayerClass.SORCERER,Role.RANGED,t,100,100),"Native cast-speed/damage boost admitted in combat");
   var e=new PlayerBotSkills.Entry(t,1,SkillKind.BUFF,1);check(priority(session,e,bot,true)==(id==1540?18:17.5),"Final Session uses original Mage boost ordering");
   check(!PlayerBotCombatBuffs.useful(PlayerClass.SORCERER,Role.RANGED,t,30,100) && !PlayerBotCombatBuffs.useful(PlayerClass.SORCERER,Role.RANGED,t,100,20),"Unsafe health/MP still vetoes combat preparations");
   check(!PlayerBotCombatBuffs.useful(PlayerClass.ASSASSIN,Role.MELEE,t,100,100),"Caster-only boost fix does not change physical classes");
  }
  var mana=nativeData.getSkillTemplate(1192);var me=new PlayerBotSkills.Entry(mana,1,SkillKind.MANA,1);
  check(priority(session,me,bot,true)==59,"Native low-MP recovery receives normalized Mage resource priority");
  check(priority(session,me,bot,true)<PlayerBotEngine.ENCOUNTER,"Native encounter protection and escape precede resource setup");bot.life.hp=200;
  check(priority(session,me,bot,true)==21,"Critical survival retains precedence over mana setup");bot.life.hp=1000;
  check(priority(session,me,bot,false)==21,"Noncombat mana remains on installed path");check(priority(session,me,target,true)==21,"Another recipient does not use self-recovery priority");
  sf.set(session,Role.HEALER);check(priority(session,me,bot,true)==21,"Explicit alternate role retains existing support strategy");
  check(PlayerBotSorcerer.applies(PlayerClass.MAGE) && !PlayerBotSorcerer.applies(PlayerClass.SPIRIT_MASTER),"Starter Mage shares slice; pet class unchanged");
  System.out.println("OK: "+checks+" Sorcerer production/engine/native-template checks; no casts, DB or ID writes.");
 }
}


