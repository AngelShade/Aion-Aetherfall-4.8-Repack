package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Real offense/chain/resource/engine planning; no casts, world, DB, item IDs or build writes. */
public final class PlayerBotSpiritmasterCheck {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static PlayerBotSorcererCheck.Actor bot,target;static SkillData data;
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static double score(int id){return PlayerBotOffense.routine(bot,entry(id),target,List.of(),3);}
 static String choose(int...ids) {
  var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);var engine=new PlayerBotEngine();
  for(int id:ids){var e=entry(id);double score=score(id);plan.triggers(PlayerBotSpiritmaster.strategy(bot.pc),State.COMBAT)
   .add(new Trigger(()->score>0,new Action(){
    public String name(){return "spell:"+id;}public boolean isUseful(){return PlayerBotOffense.useful(bot,e,target);}
    public boolean isPossible(){return !bot.isSkillDisabled(e.template())&&PlayerBotSkills.chainAvailable(bot,e)&&PlayerBotSkills.canPlan(bot,e,target,false);}
    public boolean execute(){return true;}
   },()->score));}
  plan.defaults("native weapon",new Action(){public String name(){return "weapon";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Native spell or weapon fallback resolves");return engine.getLastAction();
 }
 static Effect observed(int id,int level,long time){return new Effect(bot,target,entry(id).template(),level){@Override public long getRemainingTimeMillis(){return time;}};}
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);PlayerBotSorcererCheck.unsafe=(Unsafe)f.get(null);
  bot=PlayerBotSorcererCheck.actor();target=PlayerBotSorcererCheck.actor();bot.pc=PlayerClass.SPIRIT_MASTER;
  var context=JAXBContext.newInstance(SkillData.class);
  data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991101" activation="ACTIVE" skilltype="MAGICAL" duration="1000"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><spellatkinstant value="300" e="1"/></effects></skill_template>
    <skill_template skill_id="991102" activation="ACTIVE" skilltype="MAGICAL" duration="0"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><endconditions><mp value="20"/></endconditions><effects><spellatkdraininstant value="200" hp_percent="100" mp_percent="100" e="1"/></effects></skill_template>
    <skill_template skill_id="991103" activation="ACTIVE" skilltype="MAGICAL" duration="1000" stack="SP_DOT" lvl="1"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatk value="80" checktime="2000" duration2="12000" e="1"/></effects></skill_template>
    <skill_template skill_id="991104" activation="ACTIVE" skilltype="MAGICAL" duration="0"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="SP_2TH" precategory="SP_1TH" time="4000"/></startconditions><effects><spellatkdraininstant value="300" hp_percent="100" mp_percent="50" e="1"/></effects></skill_template>
    <skill_template skill_id="991105" activation="ACTIVE" skilltype="MAGICAL" duration="0"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatkdraininstant value="300" hp_percent="0" mp_percent="0" e="1"/></effects></skill_template>
    <skill_template skill_id="991106" activation="ACTIVE" skilltype="MAGICAL" duration="0" stack="SP_WEAK"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatkinstant value="100" e="1"/><statdown duration2="12000" e="2"><change stat="MAGICAL_RESIST" value="-100" func="ADD"/></statdown></effects></skill_template>
   </skill_data>
   """));
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().map(s->new PlayerSkillEntry(s.getSkillId(),1,0,PersistentState.NEW)).toList());
  check(choose(991101,991103).equals("spell:991103"),"Missing periodic upkeep beats filler");
  bot.life.mp=300;check(choose(991101,991102,991103).equals("spell:991102"),"Low MP uses learned restorative damage before DoT/filler");
  check(score(991105)<score(991102),"Drain tag without positive native HP/MP recovery is not restoration");
  check(bot.life.mp==300&&bot.life.hp==1000,"Planning spends no MP or grants HP");
  bot.life.mp=1000;bot.life.hp=600;check(choose(991101,991102,991103).equals("spell:991102"),"Injured caster prefers native HP-recovering damage");
  bot.life.hp=1000;check(choose(991101,991102,991103).equals("spell:991103"),"Healthy/full-MP caster returns to DoT upkeep");
  check(choose(991101,991103,991106).equals("spell:991106"),"Missing native vulnerability precedes periodic upkeep");
  target.effects.observed.add(observed(991106,1,9000));check(score(991106)<19,"Existing vulnerability does not repeatedly preempt damage");target.effects.observed.clear();
  target.effects.observed.add(observed(991103,1,9000));check(choose(991101,991103).equals("spell:991101"),"Active periodic effect is not clipped");target.effects.observed.clear();
  target.effects.observed.add(observed(991103,1,1000));check(choose(991101,991103).equals("spell:991103"),"Final native tick window refreshes periodic damage");target.effects.observed.clear();
  target.effects.observed.add(observed(991103,2,1000));check(choose(991101,991103).equals("spell:991101"),"Final native usefulness preserves stronger effects");target.effects.observed.clear();
  bot.life.mp=300;check(choose(991102,991104).equals("spell:991102"),"Unavailable native chain does not steal restorative priority");
  bot.chains.updateChain("SP_1TH",4000);int uses=bot.chains.getCurrentChainSkill().getUseCount();
  check(choose(991102,991103,991104).equals("spell:991104"),"Admitted native follow-up precedes restorative and periodic damage");
  check(bot.chains.getCurrentChainSkill().getUseCount()==uses,"Planning never consumes native chain");bot.chains.resetChain();
  bot.life.mp=1000;target.life.hp=200;check(choose(991101,991102,991103).equals("spell:991102"),"Low-health enemy gets direct native drain, not pure DoT setup");target.life.hp=1000;
  bot.life.mp=10;check(choose(991101,991102).equals("weapon"),"Unaffordable restorative attack cannot grant free MP or strand fallback");
  bot.life.mp=1000;bot.disabled=true;check(choose(991101,991102,991103).equals("weapon"),"Disabled/cooldown spells keep native weapon fallback");bot.disabled=false;
  bot.pc=PlayerClass.GLADIATOR;check(score(991101)==13,"Unrelated classes keep existing priority");bot.pc=PlayerClass.SPIRIT_MASTER;
  check(PlayerBotSpiritmaster.strategy(PlayerClass.SORCERER).equals("sorcerer single target"),"Sorcerer strategy preserved");
  check(PlayerBotSpiritmaster.strategy(PlayerClass.CLERIC).equals("class skills"),"Cleric support strategy preserved");
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  for(int id:new int[]{3640,3625}) {
   var skill=nativeData.getSkillTemplate(id);
   check(PlayerBotSorcerer.singleTarget(skill)&&PlayerBotSkills.classify(skill)==PlayerBotRules.SkillKind.DAMAGE,"Native Backdraft family is single-target damage: "+id);
   check(PlayerBotSpiritmaster.restores(skill,true)&&PlayerBotSpiritmaster.restores(skill,false),"Native HP/MP percentages drive restoration: "+id);
   check(PlayerBotSpiritmaster.band(skill,false,false,false,100,30,100,0)>18,"Native Backdraft benefits from confirmed missing MP-recovery priority: "+id);
  }
  for(var pc:PlayerClass.values())check(PlayerBotSpiritmaster.applies(pc)==(pc==PlayerClass.SPIRIT_MASTER),"Bounded Spiritmaster-only policy: "+pc);
  check(!com.aionemu.gameserver.configs.main.PlayerBotConfig.APPEARANCE_ENABLED,"Unfinished outfit feature stays disabled in full build");
  check(PlayerBotAppearance.inventory(null,List.of()).isEmpty(),"Disabled outfit inventory requires no owner/DB access");
  try{PlayerBotAppearance.configure(null,0,0,false);throw new AssertionError("Unfinished outfit route enabled");}catch(IllegalArgumentException expected){check(true,"Unfinished route fails before touching actors");}
  System.out.println("OK: "+checks+" Spiritmaster native metadata, offense/resource/chain/engine and release-gate checks; no casts/world/DB");
 }
}
