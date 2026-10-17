package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.models.QuestSkillData;
import com.aionemu.gameserver.questEngine.handlers.template.SkillUse;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.action.DpUseAction;

/** Maintained Mau templates and skill-use XML, native planning/cost/event endpoints.
 * Inert actors only: no World, registration, scheduler, SQL, IDs or actual casts. */
public final class PlayerBotQuestTransformationCheck {
 private static int checks;private static sun.misc.Unsafe unsafe;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static Field field(Class<?> type,String name)throws Exception{for(;type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static String entry(Path file,String tag,String key,int id)throws Exception{var matcher=java.util.regex.Pattern.compile("<"+tag+"\\s+"+key+"=\""+id+"\"[^>]*>.*?</"+tag+">",java.util.regex.Pattern.DOTALL).matcher(Files.readString(file));if(!matcher.find())throw new AssertionError("Missing maintained "+tag+" "+id);return matcher.group();}
 private static final class Common extends PlayerCommonData {
  Common(){super(1999999461);setPlayerClass(PlayerClass.RANGER);}
  @Override public Player getPlayer(){return null;} // Actual DP arithmetic, no native World lookup/broadcast.
 }
 private static final class Stats extends PlayerGameStats {
  Stats(){super(null);}
  @Override public Stat2 getAttackRange(){return new AdditionStat(StatEnum.ATTACK_RANGE,1000,null);}
 }
 private static final class Actor extends Player {
  boolean spawned=true,dead,transformed,disabled;PlayerClass pc=PlayerClass.RANGER;
  QuestStateList quests;PlayerSkillList skills;Common common;Equipment equipment;Stats stats;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return 1999999461;}@Override public boolean isPlayerBot(){return true;}
  @Override public boolean isWorldBot(){return true;}@Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public boolean isTransformed(){return transformed;}@Override public boolean isSkillDisabled(SkillTemplate template){return disabled;}
  @Override public PlayerClass getPlayerClass(){return pc;}@Override public QuestStateList getQuestStateList(){return quests;}
  @Override public PlayerSkillList getSkillList(){return skills;}@Override public PlayerCommonData getCommonData(){return common;}
  @Override public Equipment getEquipment(){return equipment;}@Override public PlayerGameStats getGameStats(){return stats;}
 }
 private static final class Handler extends SkillUse {
  Handler(int quest,List<QuestSkillData> requirements){super(quest,List.of(203705),null,requirements);}
  @Override public void updateQuestStatus(QuestEnv env){} // Actual counters; suppress socket/native World notifications.
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);
  Path root=args.length==0?Path.of("game-server"):Path.of(args[0]);
  var oldSkills=DataManager.SKILL_DATA;var oldXml=DataManager.XML_QUESTS;var oldQuests=DataManager.QUEST_DATA;
  boolean oldEnabled=PlayerBotConfig.QUESTS_ENABLED,oldCustom=PlayerBotConfig.QUEST_CUSTOM;
  var handlers=(Map<Integer,AbstractQuestHandler>)field(QuestEngine.class,"questHandlers").get(QuestEngine.getInstance());
  var previous=new HashMap<Integer,AbstractQuestHandler>();for(int quest:List.of(3924,4924))previous.put(quest,handlers.get(quest));
  try {
   PlayerBotConfig.QUESTS_ENABLED=PlayerBotConfig.QUEST_CUSTOM=true;
   DataManager.SKILL_DATA=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("<skill_data>"+entry(root.resolve("data/static_data/skills/skill_templates.xml"),"skill_template","skill_id",872)+entry(root.resolve("data/static_data/skills/skill_templates.xml"),"skill_template","skill_id",4610)+"</skill_data>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts>"+entry(root.resolve("data/static_data/quest_script_data/sanctum.xml"),"skill_use","id",3924)+entry(root.resolve("data/static_data/quest_script_data/pandaemonium.xml"),"skill_use","id",4924)+"</quest_scripts>"));
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests>"+entry(root.resolve("data/static_data/quest_data/quest_data.xml"),"quest","id",3924)+entry(root.resolve("data/static_data/quest_data/quest_data.xml"),"quest","id",4924)+"</quests>"));
   var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.spawned=true;actor.pc=PlayerClass.RANGER;actor.quests=new QuestStateList();actor.common=new Common();actor.stats=(Stats)unsafe.allocateInstance(Stats.class);actor.equipment=new Equipment(actor);
   for(int id:List.of(872,4610)) {
    int quest=id==872?4924:3924;var model=DataManager.XML_QUESTS.getQuest(quest);
    var requirements=(List<QuestSkillData>)(List<?>)PlayerBotQuestConversations.list(model,"skills");var handler=new Handler(quest,requirements);handlers.put(quest,handler);
    var template=DataManager.SKILL_DATA.getSkillTemplate(id);var cached=new PlayerBotSkills.Entry(template,1,PlayerBotRules.SkillKind.BUFF,1);
    actor.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(id,1,0,PersistentState.UPDATED)));
    check(requirements.size()==1 && requirements.getFirst().getSkillIds().equals(List.of(id)) && requirements.getFirst().getEndVar()==10,"Maintained faction XML requires ten actual Mau uses");
    check(PlayerBotSkills.classify(template)==PlayerBotRules.SkillKind.UNSUPPORTED,"Ordinary shape-change classification stays unsupported");
    check(PlayerBotSkills.read(actor).isEmpty() && PlayerBotSkills.actualTemplate(actor,cached)==null,"Learned Mau alone never admits routine transformation");
    var state=new QuestState(quest,QuestStatus.START);actor.quests.addQuest(quest,state);
    check(PlayerBotSkills.questTransformationEligible(actor,template) && PlayerBotSkills.classify(actor,template)==PlayerBotRules.SkillKind.BUFF,"Actual unfinished native requirement admits learned Ranger transformation");
    check(PlayerBotSkills.read(actor).stream().anyMatch(e->e.template()==template) && PlayerBotSkills.actualTemplate(actor,cached)==template,"Learned selection and cached execution use current native intent");
    actor.common.setDp(1999);check(!PlayerBotSkills.canPlan(actor,cached,actor),"Native DP start/action requirements reject insufficient earned resource");
    actor.common.setDp(2000);check(PlayerBotSkills.canPlan(actor,cached,actor) && actor.common.getDp()==2000,"Legal native plan reads cost without spending or granting credit");
    check(!PlayerBotSkills.canPlan(actor,cached,null),"Quest transformation cannot target another actor or unresolved target");
    actor.disabled=true;check(!PlayerBotSkills.canPlan(actor,cached,actor),"Native cooldown still rejects quest casts");actor.disabled=false;
    for(int mode=0;mode<6;mode++) {
     switch(mode){case 0->actor.dead=true;case 1->actor.spawned=false;case 2->actor.transformed=true;case 3->actor.pc=PlayerClass.SORCERER;case 4->PlayerBotConfig.QUESTS_ENABLED=false;case 5->actor.skills=new PlayerSkillList();}
     check(!PlayerBotSkills.questTransformationEligible(actor,template) && PlayerBotSkills.actualTemplate(actor,cached)==null,"Current actor/feature/learned policy retires cached transformation "+mode);
     actor.dead=false;actor.spawned=true;actor.transformed=false;actor.pc=PlayerClass.RANGER;PlayerBotConfig.QUESTS_ENABLED=true;actor.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(id,1,0,PersistentState.UPDATED)));
    }
    var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(PlayerBotSession.class,"owner").set(session,actor);field(PlayerBotSession.class,"bot").set(session,actor);
    var actionClass=Class.forName(PlayerBotSession.class.getName()+"$CastAction");var constructor=actionClass.getDeclaredConstructors()[0];constructor.setAccessible(true);var action=constructor.newInstance(session,cached,actor,List.of());var useful=actionClass.getDeclaredMethod("isUseful");useful.setAccessible(true);
    field(PlayerBotSession.class,"order").set(session,PlayerBotRules.Order.FOLLOW);check(!(Boolean)useful.invoke(action),"Manual questing-off rejects cached native quest cast");field(PlayerBotSession.class,"questing").setBoolean(session,true);
    field(PlayerBotSession.class,"order").set(session,PlayerBotRules.Order.STAY);check(!(Boolean)useful.invoke(action),"Manual Stay rejects cached native quest cast");
    var skill=SkillEngine.getInstance().getSkillFor(actor,template,actor);var cost=template.getActions().getActions().stream().filter(a->a instanceof DpUseAction).findFirst().orElseThrow();
    check(cost.act(skill) && actor.common.getDp()==0 && SkillUse.currentCount(state,requirements.getFirst())==0,"Actual native DP action pays two thousand without synthesizing skill event credit");
    var env=new QuestEnv(null,actor,quest);for(int use=1;use<=10;use++){check(handler.onUseSkillEvent(env,id) && SkillUse.currentCount(state,requirements.getFirst())==use,"Actual native skill event progresses faction counter "+use);check(state.getStatus()==(use==10?QuestStatus.REWARD:QuestStatus.START),"Only final native skill event earns reward readiness "+use);}
    check(handler.getPlayerBotActions(actor).stream().noneMatch(a->a.kind()==QuestBotAction.Kind.SKILL) && PlayerBotSkills.actualTemplate(actor,cached)==null && PlayerBotSkills.read(actor).isEmpty(),"Completed counter retires intent and cached/learned transformation immediately");
    check(!handler.onUseSkillEvent(env,id) && state.getCompleteCount()==0,"Native readiness cannot replay credit or fabricate quest completion/reward");
   }
   System.out.println("OK: "+checks+" maintained Ranger Mau/native XML skill-intent, DP, cooldown, manual-policy and counter checks; no World/IDs/scheduler/SQL/casts");
  }finally{for(var e:previous.entrySet())if(e.getValue()==null)handlers.remove(e.getKey());else handlers.put(e.getKey(),e.getValue());DataManager.SKILL_DATA=oldSkills;DataManager.XML_QUESTS=oldXml;DataManager.QUEST_DATA=oldQuests;PlayerBotConfig.QUESTS_ENABLED=oldEnabled;PlayerBotConfig.QUEST_CUSTOM=oldCustom;}
 }
 private PlayerBotQuestTransformationCheck(){}
}
