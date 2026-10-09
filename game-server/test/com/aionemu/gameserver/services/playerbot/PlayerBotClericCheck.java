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
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Actual production recovery/Session/CastAction/engine planning, no casts, world, DB or ID operations. */
public final class PlayerBotClericCheck {
 static int checks; static Unsafe unsafe; static SkillData data; static Actor bot,target;
 static PlayerBotSession session; static Method priority; static Constructor<?> cast;
 static class Stats extends PlayerGameStats {
  Stats(){super(null);}
  @Override protected Stat2 getStat(StatEnum stat,float base,Set<com.aionemu.gameserver.utils.stats.CalculationType> types){return new AdditionStat(stat,stat==StatEnum.MAXHP?1000:base,null);}
  @Override public Stat2 getMaxHp(){return new AdditionStat(StatEnum.MAXHP,1000,null);}
 }
 static class Group extends com.aionemu.gameserver.model.team.group.PlayerGroup {
  List<Player> members;
  Group(){super(null,null,1);}
  @Override public int getObjectId(){return 800005;}
  @Override public List<Player> getMembers(){return members;}
 }
 static class Actor extends PlayerBotSorcererCheck.Actor {
  Stats stats; Group group; int id; boolean spawned=true, disease; Skill casting;
  Actor(){super();}
  @Override public int getObjectId(){return id;}
  @Override public int getPlayerBotOwnerId(){return 800001;}
  @Override public PlayerGameStats getGameStats(){return stats;}
  @Override public com.aionemu.gameserver.model.templates.VisibleObjectTemplate getObjectTemplate(){return new com.aionemu.gameserver.model.templates.VisibleObjectTemplate(){public int getTemplateId(){return 0;}public String getName(){return "offline";}public int getL10nId(){return 0;}};}
  @Override public int getWorldId(){return 1;}
  @Override public int getInstanceId(){return 1;}
  @Override public float getX(){return 0;} @Override public float getY(){return 0;} @Override public float getZ(){return 0;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isEnemy(Creature other){return false;}
  @Override public boolean isInsidePvPZone(){return false;}
  @Override public Skill getCastingSkill(){return casting;}
  @Override public boolean isCasting(){return casting!=null;}
  @Override public boolean canAttack(){return true;}
  @Override public boolean isPlaying(){return true;}
  @Override public boolean isSpawnProtectedFrom(Creature other){return false;}
  @Override public boolean isInGroup(){return group!=null;}
  @Override public com.aionemu.gameserver.model.team.group.PlayerGroup getPlayerGroup(){return group;}
 }
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void field(Object object,Class<?> owner,String name,Object value)throws Exception{
  var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(object,value);
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.spawned=true;a.pc=PlayerClass.CLERIC;
  a.life=(PlayerBotSorcererCheck.Life)unsafe.allocateInstance(PlayerBotSorcererCheck.Life.class);a.life.hp=a.life.mp=1000;
  a.stats=(Stats)unsafe.allocateInstance(Stats.class);a.move=new PlayerBotSorcererCheck.Move(a);a.chains=new ChainSkills();
  a.effects=new PlayerBotSorcererCheck.Effects(a){@Override public boolean isAbnormalSet(AbnormalState state){return state==AbnormalState.DISEASE && a.disease;}};
  a.learned=new PlayerSkillList();field(a,Creature.class,"aggroList",new AggroList(a));return a;
 }
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 static double score(int id)throws Exception{return (double)priority.invoke(session,entry(id),target,true,List.of(bot,target));}
 static Action action(int id)throws Exception{return (Action)cast.newInstance(session,entry(id),target,List.of());}
 static String choose(int... ids)throws Exception {
  var plan=new PlayerBotStrategyComposition.Plan(State.COMBAT);var engine=new PlayerBotEngine();
  for(int id:ids){var e=entry(id);var real=action(id);double score=score(id);
   plan.triggers(PlayerBotCleric.strategy(bot.pc),State.COMBAT).add(new Trigger(()->score>0,new Action(){
    public String name(){return "heal:"+id;}public boolean isUseful(){return real.isUseful();}
    public boolean isPossible(){return real.isPossible();}public boolean execute(){return true;}
   },()->score));}
  plan.defaults("fallback",new Action(){public String name(){return "fallback";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}},5,State.COMBAT);
  check(plan.tick(engine,64),"Fresh engine resolves actual final gates or fallback");return engine.getLastAction();
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  bot=actor(800002);target=actor(800003);
  var group=(Group)unsafe.allocateInstance(Group.class);group.members=List.of(bot,target);bot.group=target.group=group;
  var context=JAXBContext.newInstance(SkillData.class);data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991201" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><healinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991202" activation="ACTIVE" duration="0" skilltype="MAGICAL" stack="C_HOT"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><heal value="900" checktime="2000" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991203" activation="ACTIVE" duration="3000" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="900" e="1"/></effects></skill_template>
    <skill_template skill_id="991204" activation="ACTIVE" duration="0" skilltype="MAGICAL" stack="C_CASE"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><caseheal value="600" type="HP" cond_value="20" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991205" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="25" e="1"/><heal value="900" checktime="3000" duration2="10000" e="2"/></effects></skill_template>
    <skill_template skill_id="991206" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><caseheal value="900" type="MP" cond_value="90" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991207" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><heal value="900" checktime="0" duration2="10000" e="1"/></effects></skill_template>
    <skill_template skill_id="991208" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="ME" target_relation="MYPARTY" target_type="PARTY" target_maxcount="6" effective_range="25"/><effects><healinstant value="250" e="1"/></effects></skill_template>
   </skill_data>
   """));
  bot.learned=new PlayerSkillList(data.getSkillTemplates().stream().map(s->new PlayerSkillEntry(s.getSkillId(),1,0,PersistentState.NEW)).toList());
  DataManager.MATERIAL_DATA=new MaterialData();DataManager.SKILL_DATA=data;
  session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  field(session,PlayerBotSession.class,"bot",bot);field(session,PlayerBotSession.class,"owner",bot);
  field(session,PlayerBotSession.class,"role",Role.HEALER);field(session,PlayerBotSession.class,"skills",data.getSkillTemplates().stream().map(s->entry(s.getSkillId())).toList());
  priority=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);priority.setAccessible(true);
  var c=Class.forName(PlayerBotSession.class.getName()+"$CastAction");cast=c.getDeclaredConstructors()[0];cast.setAccessible(true);
  target.life.hp=250;
  double oldFast=PlayerBotTactics.healFit(750,PlayerBotHealing.snapshot(bot,entry(991201),target),0,true);
  double oldHot=PlayerBotTactics.healFit(750,PlayerBotHealing.snapshot(bot,entry(991202),target),0,true);
  check(oldHot>oldFast,"Reproduce installed gap: delayed HoT fit beats smaller immediate recovery");
  check(choose(991201,991202,991203,991204).equals("heal:991201"),"Critical HP chooses immediate recovery before delayed/armed heals");
  check(PlayerBotCleric.impact(bot,entry(991202),target).delayMillis()==2300,"Native first periodic tick includes 300ms initial offset");
  check(PlayerBotCleric.impact(bot,entry(991205),target).amount()==25,"Hybrid future ticks do not become immediate recovery");
  check(choose(991201,991205).equals("heal:991201"),"Immediate useful amount distinguishes hybrid and direct recovery");
  check(score(991204)<HIGH+3,"Conditional protection above threshold is not emergency recovery");
  target.life.hp=200;
  check(PlayerBotCleric.impact(bot,entry(991204),target).amount()==600,"Exact native conditional threshold heals immediately");
  check(choose(991201,991204).equals("heal:991204"),"Admitted conditional recovery can beat smaller direct heal");
  target.life.hp=450;
  check(choose(991201,991202,991203).equals("heal:991201"),"Low HP favors timely recovery");
  target.life.hp=800;
  check(score(991202)>0,"Almost-full health still admits needed HoT maintenance");
  target.effects.observed.add(new Effect(bot,target,entry(991202).template(),1));
  check(score(991202)==0 && !action(991202).isUseful(),"Active HoT is not recast by new strategy/final gate");target.effects.observed.clear();
  target.life.hp=250;bot.life.mp=50;
  check(choose(991201,991203).equals("heal:991203"),"Unaffordable fast spell keeps legal slower fallback");bot.life.mp=1000;
  bot.disabled=true;check(choose(991201,991202).equals("fallback"),"Disabled/cooldown heals cannot steal a decision");bot.disabled=false;
  target.life.hp=1000;check(score(991201)==0 && !action(991201).isUseful(),"Full health rejected by final usefulness");
  target.life.hp=0;check(score(991201)==0 && !action(991201).isUseful(),"Dead recipient keeps resurrection path separate");
  target.life.hp=250;target.spawned=false;check(score(991201)==0,"Unspawned targets rejected");target.spawned=true;
  target.disease=true;check(score(991201)==0,"Native disease cannot produce a positive immediate heal snapshot");target.disease=false;
  check(PlayerBotCleric.impact(bot,entry(991206),target).amount()==0,"Conditional MP effect does not masquerade as HP recovery");
  check(score(991207)==0,"Zero-checktime HoT never schedules a tick and cannot be emergency recovery");
  var peer=actor(800004);peer.life.hp=1000;
  PlayerBotService.getInstance().reserve(peer,target,SkillKind.HEAL,System.currentTimeMillis()+10000);
  target.life.hp=750;check(!PlayerBotHealing.needs(bot,entry(991201),target,List.of(bot,target)),"Existing healthy-recipient reservation remains effective");
  target.life.hp=250;check(PlayerBotHealing.needs(bot,entry(991201),target,List.of(bot,target)),"Existing emergency reservation exception retained");
  double groupOne=PlayerBotHealing.priority(bot,entry(991208),bot,List.of(bot,target));
  check(groupOne>0 && PlayerBotHealing.targets(bot,entry(991208),bot,List.of(bot,target)).contains(target),"Healthy group anchor still heals native affected ally");
  bot.life.hp=250;check(PlayerBotHealing.priority(bot,entry(991208),bot,List.of(bot,target))>groupOne,"Two actual injured group recipients retain group-heal preference");bot.life.hp=1000;
  bot.pc=PlayerClass.CHANTER;check(score(991202)>score(991201),"Chanter retains its previous general heal scoring for separate review");bot.pc=PlayerClass.CLERIC;
  var passive=new PlayerBotStrategyComposition.Plan(State.COMBAT);passive.defaults(PlayerBotCleric.strategy(bot.pc),new Action(){public String name(){return "disabled cleric";}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){throw new AssertionError("Passive strategy executed");}},100,State.COMBAT);
  passive.enable(PlayerBotCleric.strategy(bot.pc),false);check(!passive.tick(new PlayerBotEngine(),64),"Named Cleric strategy respects existing passive-order composition");
  var nativeData=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  for(int id:new int[]{1840,3951,3968,3939,3924,3932,3998,4176,4195,4213}) {
   var s=nativeData.getSkillTemplate(id);var e=new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);
   check(PlayerBotHealing.heals(e),"Production Cleric recovery effect exists: "+id);
   check(PlayerBotCleric.impact(bot,e,target).amount()>0,"Production snapshot uses native metadata: "+id);
  }
  for(var pc:PlayerClass.values()) {
   bot.pc=pc;check(PlayerBotCleric.applies(pc)==(pc==PlayerClass.CLERIC),"Cleric-only policy: "+pc);
   if(pc!=PlayerClass.CLERIC) check(PlayerBotCleric.recovery(bot,entry(991201),target,90)==null,"Existing class healing unchanged: "+pc);
  }
  check(PlayerBotCleric.strategy(PlayerClass.CHANTER).equals("class skills"),"Chanter remains a separate unfinished slice");
  check(PlayerBotCleric.strategy(PlayerClass.SPIRIT_MASTER).equals("spiritmaster single target"),"Spiritmaster strategy retained");
  check(bot.life.hp==1000 && bot.life.mp==1000 && target.life.hp==250 && target.effects.observed.isEmpty(),"Planning did not cast, pay resources or apply effects");
  System.out.println("OK: "+checks+" Cleric first-heal timing, conditional recovery, Session/final gates and native metadata checks; no casts/world/DB/IDs");
 }
}
