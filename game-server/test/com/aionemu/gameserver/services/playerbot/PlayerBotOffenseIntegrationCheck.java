package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Actual final CastAction gates on world-free observed fixtures. No native cast,
 * world registration, database, account configuration or ID reservation/release. */
public final class PlayerBotOffenseIntegrationCheck {
 static int checks;
 static Unsafe unsafe;
 static SkillData data;
 static PlayerBotSession session;
 static Actor bot,target;
 static Constructor<?> cast;
 static final class Life extends PlayerLifeStats {
  int hp,mp; Life(){super(null);}
  @Override public int getCurrentHp(){return hp;}
  @Override public int getMaxHp(){return 1000;}
  @Override public int getCurrentMp(){return mp;}
  @Override public int getMaxMp(){return 1000;}
 }
 static final class Effects extends PlayerEffectController {
  final List<Effect> observed=new ArrayList<>();
  Effects(Creature owner){super(owner);}
  @Override public List<Effect> getAbnormalEffects(){return List.copyOf(observed);}
  @Override public boolean hasAbnormalEffect(Predicate<Effect> test){return observed.stream().anyMatch(test);}
  @Override public Effect getAbnormalEffect(String stack){return observed.stream().filter(e->Objects.equals(e.getStack(),stack)).findFirst().orElse(null);}
 }
 static final class Actor extends Player {
  Life life; Effects effects; PlayerSkillList learned; boolean casting,spawned,disabled; int id;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public PlayerLifeStats getLifeStats(){return life;}
  @Override public PlayerEffectController getEffectController(){return effects;}
  @Override public PlayerSkillList getSkillList(){return learned;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.ASSASSIN;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isDead(){return life.hp==0;}
  @Override public boolean isCasting(){return casting;}
  @Override public boolean canAttack(){return true;}
  @Override public boolean isSkillDisabled(SkillTemplate skill){return disabled;}
  @Override public ChainSkills getChainSkills(){return new ChainSkills();}
 }
 static void set(Object value,String name,Object fieldValue)throws Exception {
  Field field=PlayerBotSession.class.getDeclaredField(name);field.setAccessible(true);field.set(value,fieldValue);
 }
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Actor actor(int id)throws Exception {
  var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.id=id;actor.spawned=true;
  actor.life=(Life)unsafe.allocateInstance(Life.class);actor.life.hp=actor.life.mp=1000;
  actor.effects=new Effects(actor);actor.learned=new PlayerSkillList();return actor;
 }
 static Effect effect(int id,int level,long remaining) {
  return new Effect(bot,target,data.getSkillTemplate(id),level){@Override public long getRemainingTimeMillis(){return remaining;}};
 }
 static PlayerBotEngine.Action action(int id)throws Exception {
  var skill=data.getSkillTemplate(id);
  return (PlayerBotEngine.Action)cast.newInstance(session,new PlayerBotSkills.Entry(skill,1,SkillKind.DAMAGE,20),target,List.of());
 }
 static void builder(boolean enabled) {
  var learned=new ArrayList<PlayerSkillEntry>();
  for(int id=991003;id<=991010;id++)learned.add(new PlayerSkillEntry(id,1,0,PersistentState.NEW));
  if(enabled)learned.add(new PlayerSkillEntry(991002,1,0,PersistentState.NEW));
  bot.learned=new PlayerSkillList(learned);
 }
 static void rune(int count,long remaining,double hp,boolean builder)throws Exception {
  target.effects.observed.clear();target.life.hp=(int)(hp*10);builder(builder);
  if(count>0)target.effects.observed.add(effect(991001,count,remaining));
  var action=action(991003);int mp=bot.life.mp;var effects=List.copyOf(target.effects.observed);
  check(action.isUseful(),"Final CastAction rejected legal finisher: runes="+count+", hp="+hp+", builder="+builder+", remaining="+remaining);
  check(action.isPossible(),"Native planning rejected legal fixture finisher");
  check(mp==bot.life.mp && effects.equals(target.effects.observed),"Finisher planning consumed MP/runes");
 }
 static void runes()throws Exception {
  rune(0,10000,100,false);rune(4,10000,100,true);rune(2,10000,100,false);
  rune(2,3000,100,true);rune(5,10000,100,true);rune(2,10000,24,true);
  target.effects.observed.clear();target.life.hp=1000;builder(true);
  check(!action(991003).isUseful(),"Available builder must suppress weak zero-rune finisher");
  target.effects.observed.add(effect(991001,2,10000));
  check(!action(991003).isUseful(),"Available builder must suppress immature nonexpiring finisher");
  builder(false);target.effects.observed.clear();target.effects.observed.add(effect(991011,5,10000));
  check(PlayerBotOffense.runes(data.getSkillTemplate(991003),PlayerBotOffense.active(target))==0,"Unrelated stack must not count as runes");
  check(action(991003).isUseful(),"Unrelated stack must not veto legal zero-rune fallback");
  target.effects.observed.clear();target.effects.observed.add(effect(991001,9,10000));
  check(PlayerBotOffense.runes(data.getSkillTemplate(991003),PlayerBotOffense.active(target))==5,"Native burst cap limits observed runes");
  check(action(991003).isUseful(),"Capped rune stack must remain usable");
 }
 static void periodic()throws Exception {
  builder(false);target.life.hp=1000;target.effects.observed.clear();
  check(action(991004).isUseful(),"Absent periodic effect must be useful");
  target.effects.observed.add(effect(991004,1,4000));
  check(action(991004).isUseful(),"Final CastAction vetoed same-ID final-window refresh");
  target.effects.observed.clear();target.effects.observed.add(effect(991012,1,4000));
  check(action(991004).isUseful(),"Final CastAction vetoed same-stack final-window refresh");
  target.effects.observed.clear();target.effects.observed.add(effect(991004,1,8000));
  check(!action(991004).isUseful(),"Pure periodic effect must not refresh early");
  target.effects.observed.clear();target.effects.observed.add(effect(991005,1,1000));
  check(!action(991004).isUseful(),"Refresh must not downgrade a stronger rank");
  target.effects.observed.clear();target.effects.observed.add(effect(991004,2,1000));
  check(!action(991004).isUseful(),"Refresh must not downgrade a stronger learned level");
  target.effects.observed.clear();target.effects.observed.add(effect(991013,1,8000));
  check(!action(991004).isUseful(),"Native conflict ID must block redundant periodic effect");
  target.effects.observed.clear();target.life.hp=240;
  check(!action(991004).isUseful(),"Pure DoT must not monopolize nearly-dead target");
  check(action(991006).isUseful(),"Hybrid direct damage must survive low-health policy");
  target.effects.observed.add(effect(991006,1,8000));
  check(action(991006).isUseful(),"Final CastAction vetoed hybrid direct damage on active stack");
  target.effects.observed.clear();target.effects.observed.add(effect(991007,1,1000));
  check(!action(991006).isUseful(),"Hybrid attack must not overwrite a stronger periodic rank");
  target.effects.observed.clear();target.effects.observed.add(effect(991008,1,8000));
  check(!action(991008).isUseful(),"Nonperiodic duplicate debuff veto must remain");
 }
 static void safeguards()throws Exception {
  target.effects.observed.clear();target.life.hp=1000;builder(false);var action=action(991003);
  bot.casting=true;check(!action.isUseful(),"Casting lock must remain");bot.casting=false;
  target.spawned=false;check(!action.isUseful(),"Despawned target must remain rejected");target.spawned=true;
  target.life.hp=0;check(!action.isUseful(),"Dead target must remain rejected");target.life.hp=1000;
  bot.disabled=true;check(!action.isPossible(),"Native cooldown gate must remain");bot.disabled=false;
  check(!action(991009).isPossible(),"Unavailable native chain must remain rejected");
  check(!action(991010).isPossible(),"Area-off gate must remain rejected");
  int mp=bot.life.mp;bot.life.mp=10;
  check(!action.isPossible(),"Native MP action must reject insufficient mana");
  check(bot.life.mp==10,"Planning must not pay MP");bot.life.mp=mp;
 }
 public static void main(String[] args)throws Exception {
  Field access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);unsafe=(Unsafe)access.get(null);
  data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991001" activation="ACTIVE" stack="RUNE_TEST" lvl="1" tslot="DEBUFF"><effects><signet e="1" duration2="15000"/></effects></skill_template>
    <skill_template skill_id="991002" activation="ACTIVE" lvl="1"><properties first_target="TARGET" first_target_range="20" target_relation="ENEMY" target_type="ONLYONE"/><effects><carvesignet e="1" value="1"/></effects></skill_template>
    <skill_template skill_id="991003" activation="PROVOKED" lvl="1" duration="1000"><properties first_target="TARGET" first_target_range="20" target_relation="ENEMY" target_type="ONLYONE"/><actions><mpuse value="50"/></actions><effects><signetburst value="100" e="1" signet="RUNE_TEST" signetlvl="5"/></effects></skill_template>
    <skill_template skill_id="991004" activation="PROVOKED" stack="DOT_TEST" conflict_id="55" lvl="1" duration="1000" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatk value="100" e="1" checktime="3000" duration2="15000" effectid="77"/></effects></skill_template>
    <skill_template skill_id="991005" activation="PROVOKED" stack="DOT_TEST" lvl="2" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatk value="200" e="1" checktime="3000" duration2="15000" effectid="77"/></effects></skill_template>
    <skill_template skill_id="991006" activation="PROVOKED" stack="HYBRID_TEST" lvl="1" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="100" e="1"/><bleed value="100" e="2" checktime="2000" duration2="10000" effectid="78"/></effects></skill_template>
    <skill_template skill_id="991007" activation="PROVOKED" stack="HYBRID_TEST" lvl="2" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="200" e="1"/><bleed value="200" e="2" checktime="2000" duration2="10000" effectid="78"/></effects></skill_template>
    <skill_template skill_id="991008" activation="PROVOKED" stack="DEBUFF_TEST" lvl="1" tslot="DEBUFF"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><statdown e="1" duration2="10000"><change stat="PHYSICAL_DEFENSE" func="ADD" value="-100"/></statdown></effects></skill_template>
    <skill_template skill_id="991009" activation="PROVOKED" lvl="1"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><startconditions><chain category="STRIKE_2TH" precategory="STRIKE_1TH" precount="2" time="1000"/></startconditions><effects><skillatk value="100" e="1"/></effects></skill_template>
    <skill_template skill_id="991010" activation="PROVOKED" lvl="1"><properties first_target="TARGET" target_relation="ENEMY" target_type="AREA" effective_range="5"/><effects><skillatk value="100" e="1"/></effects></skill_template>
    <skill_template skill_id="991011" activation="ACTIVE" stack="OTHER_RUNE" lvl="1" tslot="DEBUFF"><effects><signet e="1" duration2="15000"/></effects></skill_template>
    <skill_template skill_id="991012" activation="ACTIVE" stack="DOT_TEST" lvl="1" tslot="DEBUFF"><effects><spellatk value="100" e="1" checktime="3000" duration2="15000" effectid="77"/></effects></skill_template>
    <skill_template skill_id="991013" activation="ACTIVE" stack="OTHER_DOT" conflict_id="55" lvl="1" tslot="DEBUFF"><effects><spellatk value="100" e="1" checktime="3000" duration2="15000" effectid="79"/></effects></skill_template>
   </skill_data>
   """));
  bot=actor(1999998001);target=actor(1999998002);
  session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);set(session,"bot",bot);set(session,"owner",bot);set(session,"role",Role.MELEE);
  cast=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);cast.setAccessible(true);
  var previous=DataManager.SKILL_DATA;
  try {
   DataManager.SKILL_DATA=data;
   if(args.length==0 || args[0].equals("runes"))runes();
   if(args.length==0 || args[0].equals("periodic"))periodic();
   if(args.length==0)safeguards();
   System.out.println("OK: "+checks+" final CastAction offense/usefulness/native-planning safeguards; no native casts, world, DB or IDs touched");
  }finally{DataManager.SKILL_DATA=previous;}
 }
}
