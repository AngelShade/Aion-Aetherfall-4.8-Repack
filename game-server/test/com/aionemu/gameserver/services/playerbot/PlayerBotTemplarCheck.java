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
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.templates.item.ItemAttackType;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.world.knownlist.KnownList;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Production recipient/priority/final admission and native metadata; no casts/world/DB/IDs. */
public final class PlayerBotTemplarCheck {
 static int checks;static Unsafe u;static SkillData data;static Actor bot,ally,peer;
 static Enemy enemy;static PlayerBotSession session;static Method recipient,priority;static Constructor<?> cast;
 static class Gear extends Equipment {
  boolean shield=true;Gear(){super(null);}
  @Override public boolean isShieldEquipped(){return shield;}
 }
 static class Actor extends PlayerBotClericCheck.Actor {
  KnownList known;Gear gear;
  Actor(){super();}
  @Override public KnownList getKnownList(){return known;}
  @Override public Equipment getEquipment(){return gear;}
  @Override public boolean isPlayerBot(){return false;}
 }
 static class Enemy extends Npc {
  Skill cast;boolean dead,spawned=true;int world=1,instance=1;float x;ItemAttackType type=ItemAttackType.PHYSICAL;
  Enemy(){super(null,null,null);}
  void target(Creature target)throws Exception {PlayerBotClericCheck.field(this,VisibleObject.class,"target",target);}
  @Override public int getObjectId(){return 820010;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public Skill getCastingSkill(){return cast;}
  @Override public ItemAttackType getAttackType(){return type;}
  @Override public NpcTemplate getObjectTemplate(){return new NpcTemplate(){@Override public int getTemplateId(){return 820010;}};}
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)u.allocateInstance(Actor.class);a.id=id;a.spawned=true;a.pc=PlayerClass.TEMPLAR;
  a.life=(PlayerBotSorcererCheck.Life)u.allocateInstance(PlayerBotSorcererCheck.Life.class);a.life.hp=a.life.mp=1000;
  a.stats=(PlayerBotClericCheck.Stats)u.allocateInstance(PlayerBotClericCheck.Stats.class);
  a.effects=new PlayerBotSorcererCheck.Effects(a);a.move=new PlayerBotSorcererCheck.Move(a);a.chains=new ChainSkills();a.learned=new PlayerSkillList();
  a.gear=(Gear)u.allocateInstance(Gear.class);a.gear.shield=true;
  a.known=new KnownList(a){@Override public void forEachNpc(java.util.function.Consumer<Npc> consumer){consumer.accept(enemy);}};
  PlayerBotClericCheck.field(a,Creature.class,"aggroList",new AggroList(a));return a;
 }
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static Creature recipient(int id,boolean combat)throws Exception{return (Creature)recipient.invoke(session,entry(id),List.of(bot,ally),null,combat);}
 static double score(int id,Creature target,boolean combat)throws Exception{return (double)priority.invoke(session,entry(id),target,combat,List.of(bot,ally));}
 static Action action(int id,Creature target)throws Exception{return (Action)cast.newInstance(session,entry(id),target,List.of(enemy));}
 static String choose(int...ids)throws Exception {
  var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);var engine=new PlayerBotEngine();
  for(int id:ids){var target=recipient(id,true);if(target==null)continue;var real=action(id,target);double value=score(id,target,true);
   plan.triggers(PlayerBotTemplar.strategy(bot.pc),State.COMBAT).add(new Trigger(()->value>0,new Action(){
    public String name(){return "skill:"+id;}public boolean isUseful(){return real.isUseful();}public boolean isPossible(){return real.isPossible();}public boolean execute(){return true;}
   },()->value));}
  plan.defaults("native melee",new Action(){public String name(){return "melee";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Engine resolves final native planning gates or fallback");return engine.getLastAction();
 }
 static void observe(Actor target,int id){target.effects.observed.add(new Effect(bot,target,entry(id).template(),1){@Override public long getRemainingTimeMillis(){return 10000;}});}
 public static void main(String[] args)throws Exception {
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(Unsafe)uf.get(null);
  enemy=(Enemy)u.allocateInstance(Enemy.class);enemy.spawned=true;enemy.world=enemy.instance=1;enemy.type=ItemAttackType.PHYSICAL;
  bot=actor(820002);ally=actor(820003);peer=actor(820004);ally.pc=PlayerClass.CLERIC;enemy.target(bot);
  var group=(PlayerBotClericCheck.Group)u.allocateInstance(PlayerBotClericCheck.Group.class);group.members=List.of(bot,ally);bot.group=ally.group=group;
  var context=JAXBContext.newInstance(SkillData.class);
  data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991401" activation="ACTIVE" duration="0" stack="T_BLOCK"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><startconditions><lefthandweapon type="SHIELD"/></startconditions><endconditions><mp value="100"/></endconditions><effects><alwaysblock value="10" duration2="30000" e="1"/></effects></skill_template>
    <skill_template skill_id="991402" activation="ACTIVE" duration="0" stack="T_SHIELD"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><effects><shield hitvalue="5000" value="5000" duration2="15000" effectid="154" hittype="EVERYHIT" e="1"/></effects></skill_template>
    <skill_template skill_id="991403" activation="ACTIVE" duration="0" stack="T_HYBRID"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><dispeldebuff dispel_level="2" power="50" value="255" e="1"/><shield hitvalue="50" value="10000000" percent="true" duration2="15000" effectid="154" hittype="EVERYHIT" e="2" preeffect="1"/></effects></skill_template>
    <skill_template skill_id="991404" activation="ACTIVE" duration="0" stack="T_PARTY"><properties first_target="TARGET" first_target_range="25" target_relation="MYPARTY" target_type="ONLYONE" target_species="PC"/><effects><shield hitvalue="5000" value="5000" duration2="10000" effectid="154" hittype="EVERYHIT" e="1"/></effects></skill_template>
    <skill_template skill_id="991405" activation="ACTIVE" duration="0" stack="T_PHYSICAL"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><effects><shield hitvalue="100" value="10000000" percent="true" duration2="15000" effectid="154" hittype="PHHIT" e="1"/></effects></skill_template>
    <skill_template skill_id="991406" activation="ACTIVE" duration="0"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="300" e="1"/></effects></skill_template>
    <skill_template skill_id="991407" activation="ACTIVE" duration="0"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><effects><dispeldebuff dispel_level="2" power="50" value="255" e="1"/></effects></skill_template>
    <skill_template skill_id="991408" activation="ACTIVE" duration="0" tslot="DEBUFF" dispel_category="DEBUFF_PHYSICAL" req_dispel_level="1" req_dispel_count="1"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><stun duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991409" activation="ACTIVE" duration="1500" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE"/><effects><spellatkinstant value="100" e="1"/></effects></skill_template>
    <skill_template skill_id="991410" activation="ACTIVE" duration="1500" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="100" e="1"/></effects></skill_template>
   </skill_data>
   """));DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().map(s->new PlayerSkillEntry(s.getSkillId(),1,0,Persistable.PersistentState.NEW)).toList());
  session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);PlayerBotClericCheck.field(session,PlayerBotSession.class,"bot",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"owner",bot);PlayerBotClericCheck.field(session,PlayerBotSession.class,"role",Role.TANK);
  PlayerBotClericCheck.field(session,PlayerBotSession.class,"skills",List.of());
  recipient=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,Npc.class,boolean.class);recipient.setAccessible(true);
  priority=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);priority.setAccessible(true);
  cast=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructors()[0];cast.setAccessible(true);
  check(recipient(991401,true)==bot && score(991401,bot,true)==41,"Physical pressure admits proactive Templar block at full health");
  check(choose(991401).equals("skill:991401"),"Proactive block passes actual final gates and engine");
  check(score(991402,bot,true)==0,"Healthy tank retains larger shield cooldown");
  bot.life.hp=490;check(score(991402,bot,true)==75 && recipient(991402,true)==bot,"Low threatened tank shields before old 35-percent panic threshold");
  check(choose(991401,991402).equals("skill:991402"),"Low-health shield outranks routine block");
  bot.life.hp=500;check(score(991402,bot,true)==0,"Exact low-health boundary retains cooldown");bot.life.hp=490;
  check(entry(991403).kind()==SkillKind.CLEANSE,"Native cleanse/shield classification preserved");
  check(recipient(991403,true)==bot && choose(991403).equals("skill:991403"),"Hybrid protective payload passes recipient and final usefulness with no debuff");
  bot.life.hp=340;check(score(991403,bot,true)==91,"Critical hybrid defense reaches existing emergency band");
  bot.life.hp=490;enemy.target(ally);check(recipient(991403,true)==null,"Unthreatened hybrid does not spend cooldown solely on low HP");
  ally.life.hp=490;check(recipient(991404,true)==ally,"Protect actually threatened low-health native party member");
  observe(ally,991404);check(recipient(991404,true)==null && !action(991404,ally).isUseful(),"Active party shield remains protected");ally.effects.observed.clear();ally.life.hp=1000;enemy.target(bot);
  observe(bot,991402);check(recipient(991403,true)==null && !action(991403,bot).isUseful(),"Hybrid shield cannot overwrite another native effect-slot shield");bot.effects.observed.clear();
  bot.life.hp=1000;enemy.target(ally);check(recipient(991401,true)==null && choose(991401).equals("melee"),"No physical pressure retains native melee fallback");enemy.target(bot);
  check(recipient(991401,false)==null,"No proactive blocking out of combat");
  bot.life.hp=340;check(recipient(991402,false)==bot,"Earlier noncombat emergency defense policy preserved");bot.life.hp=490;
  enemy.type=ItemAttackType.MAGICAL_FIRE;check(score(991401,bot,true)==0,"Block charges not newly spent on magical autoattacks");
  check(score(991405,bot,true)==0 && score(991402,bot,true)==75,"Physical-only shield coverage differs from native all-hit shield");
  enemy.cast=new Skill(entry(991409).template(),enemy,1,bot,null);enemy.type=ItemAttackType.PHYSICAL;
  check(score(991401,bot,true)==0 && score(991405,bot,true)==0 && score(991403,bot,true)==75,"Incoming native magical cast uses all-hit shield, not physical protection");
  enemy.cast=new Skill(entry(991410).template(),enemy,1,bot,null);check(score(991401,bot,true)==0 && score(991403,bot,true)==0,"Friendly/nonhostile NPC cast is not incoming pressure");enemy.cast=null;
  for(int i=0;i<4;i++){enemy.dead=i==0;enemy.spawned=i!=1;enemy.world=i==2?2:1;enemy.instance=i==3?2:1;
   check(score(991403,bot,true)==0,"Dead/unspawned/foreign map/instance pressure rejected: "+i);}
  enemy.dead=false;enemy.spawned=true;enemy.world=enemy.instance=1;enemy.x=41;check(score(991403,bot,true)==0,"Distant pressure cannot spend defensive cooldown");enemy.x=0;
  bot.life.hp=1000;bot.gear.shield=false;check(choose(991401).equals("melee"),"Native offhand shield condition remains authoritative");bot.gear.shield=true;
  bot.life.mp=50;check(choose(991401).equals("melee"),"Native MP cost blocks setup");bot.life.mp=1000;
  bot.disabled=true;check(choose(991401).equals("melee"),"Native disabled/cooldown gate retains fallback");bot.disabled=false;
  bot.life.hp=200;check(choose(991401,991406).equals("skill:991406"),"Critical native healing retains precedence over proactive block");bot.life.hp=490;
  enemy.target(ally);observe(bot,991408);check(recipient(991403,true)==bot && score(991403,bot,true)>0,"Hybrid keeps existing real-cleanse purpose without incoming pressure");
  check(recipient(991407,true)==bot,"Pure cleanse behavior preserved");bot.effects.observed.clear();enemy.target(bot);
  PlayerBotService.getInstance().reserve(peer,bot,SkillKind.CLEANSE,System.currentTimeMillis()+60000);
  check(recipient(991403,true)==null && !action(991403,bot).isUseful(),"Existing peer cleanse reservation still protects hybrid action");PlayerBotService.getInstance().reserve(peer,bot,SkillKind.CLEANSE,0);
  for(var pc:PlayerClass.values()){bot.pc=pc;if(pc!=PlayerClass.TEMPLAR)check(!PlayerBotTemplar.managed(bot,entry(991403)),"Other class hybrid policy unchanged: "+pc);}bot.pc=PlayerClass.TEMPLAR;
  check(PlayerBotTemplar.strategy(PlayerClass.CHANTER).equals("chanter mantras") && PlayerBotTemplar.strategy(PlayerClass.CLERIC).equals("cleric recovery"),"Previous named class strategies retained");
  check(PlayerBotTank.priority(1000,true,false,0,100)==70,"Existing loose-target taunt recovery band preserved above proactive block");
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  for(int id:new int[]{2974,3069,2922,3127,3168}){
   var s=nativeData.getSkillTemplate(id);var e=new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);
   check(PlayerBotTemplar.managed(bot,e),"Real Templar protective native mapping: "+id);
   check(s.getEffects().getEffects().stream().anyMatch(x->x instanceof ShieldEffect sh && sh.getHitType()==(id==3168?HitType.PHHIT:HitType.EVERYHIT)) || s.hasAnyEffect(EffectType.ALWAYSBLOCK),"Real native shield coverage or block charges: "+id);
  }
  check(PlayerBotSkills.classify(nativeData.getSkillTemplate(3127))==SkillKind.CLEANSE && PlayerBotSkills.classify(nativeData.getSkillTemplate(3168))==SkillKind.CLEANSE,"Real Iron Skin/Empyrean Shield retain native cleanse classification");
  check(!PlayerBotTemplar.managed(bot,new PlayerBotSkills.Entry(nativeData.getSkillTemplate(3129),1,SkillKind.HEAL,1)),"Existing Empyrean Armor recovery remains with native healing");
  check(bot.life.hp==490 && bot.life.mp==1000 && bot.effects.observed.isEmpty(),"Planning writes no HP/MP/effects");
  System.out.println("OK: "+checks+" Templar protection, hybrid cleanse/shield, native coverage, Session/final gates and fallback checks; no casts/world/DB/IDs");
 }
}
