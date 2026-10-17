package com.aionemu.gameserver.services.playerbot;
import java.io.File;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.questEngine.model.*;

/** Native XP arithmetic/callback and real appearance catalog checks; isolated data, no registered actors. */
public final class PlayerBotWorldProgressCheck {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static class Data extends PlayerCommonData {Actor actor;long questExp;Data(){super(1);}@Override public Player getPlayer(){return actor;}
  @Override public void addExp(long value,Rates rate,String name){questExp+=value;}}
 static class Controller extends PlayerController {int oldLevel,newLevel,calls,nearby;@Override public void onLevelChange(int old,int level){oldLevel=old;newLevel=level;calls++;}
  @Override public void updateNearbyQuests(){nearby++;}}
 static class Actor extends Player {
  Data data;Controller controller;QuestStateList quests;
  Actor(){super(null,null);}
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isOnline(){return false;}
  @Override public PlayerController getController(){return controller;}
  @Override public PlayerCommonData getCommonData(){return data;}
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.GLADIATOR;}
 }
 @SuppressWarnings("unchecked")
 static void sharedRewards(Unsafe unsafe)throws Exception {
  var oldQuests=DataManager.QUEST_DATA;var oldNpcs=DataManager.NPC_DATA;
  try {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new java.io.StringReader("""
    <quests>
     <quest id="900091" name="Shared history" category="QUEST" max_repeat_count="5"><rewards exp="17"/></quest>
     <quest id="900092" name="Shared choices" category="QUEST" reward_repeat_count="1">
      <rewards><reward_item item_id="100" count="2"/><selectable_reward_item item_id="101" count="1"/><selectable_reward_item item_id="102" count="1"/></rewards>
      <extended_rewards><reward_item item_id="200" count="3"/><selectable_reward_item item_id="201" count="1"/><selectable_reward_item item_id="202" count="1"/></extended_rewards>
     </quest>
     <quest id="900093" name="Another pending historical quest" category="QUEST"/>
    </quests>
    """));
   DataManager.NPC_DATA=new NpcData();
   var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.data=new Data();bot.data.actor=bot;bot.controller=new Controller();bot.quests=new QuestStateList();
   var q=new QuestState(900091,QuestStatus.REWARD);bot.quests.addQuest(900091,q);
   check(com.aionemu.gameserver.services.QuestService.finishPlayerBotQuest(new QuestEnv(null,bot,900091),-1,-1),"Historical quest without an NPC completes through the real native reward service");
   check(q.getStatus()==QuestStatus.COMPLETE&&q.getCompleteCount()==1&&bot.data.questExp==17,"Native completion delivers XP and increments saved completion once");
   check(!com.aionemu.gameserver.services.QuestService.finishPlayerBotQuest(new QuestEnv(null,bot,900091),-1,-1)&&bot.data.questExp==17,"Duplicate completion cannot grant XP twice");
   var completed=new PlayerBotQuestCompletionSharing.Completed(900091,3,0,0,1);
   check(PlayerBotQuestCompletionSharing.observe(null,completed.count(),1,PlayerBotQuestCompletionSharing.limit(DataManager.QUEST_DATA.getQuestById(900091))).target()==3,"Repeatable historical rewards catch up to the owner's actual count");
   check(PlayerBotQuestCompletionSharing.needsCompletion(q,3),"Partially caught-up alt remains eligible");
   var reloaded=new QuestState(900091,QuestStatus.COMPLETE,0,0,3,null,0,null);
   check(!PlayerBotQuestCompletionSharing.needsCompletion(reloaded,3),"Reloaded native completion count prevents recruitment reward duplication");
   check(!PlayerBotQuestCompletionSharing.needsCompletion(reloaded,2),"An alt ahead of its owner never loses progress or repeats rewards");
   var ahead=PlayerBotQuestCompletionSharing.observe(null,2,5,255);
   check(ahead.target()==5&&ahead.ownerCount()==2,"Joining an owner behind this alt does not repay historical rewards");
   var live=PlayerBotQuestCompletionSharing.observe(ahead,3,5,255);
   check(live.target()==6,"A new main-player repeat pays even when the alt has more independent completions");
   check(PlayerBotQuestCompletionSharing.observe(live,3,6,255).target()==6,"Reobserving or rejoining the same owner count does not pay twice");
   check(PlayerBotQuestCompletionSharing.observe(live,4,5,255).target()==7,"New owner completions retain earlier pending awards across checkpoints");
   check(PlayerBotQuestCompletionSharing.observe(new PlayerBotQuestCompletionSharing.Progress(1,1),2,1,1).target()==1,"Nonrepeatable quest limits still prevent duplicate rewards");
   var metadata=Map.of("account","7","character","49","blocked","","quest.7.900091","3:6");
   PlayerBotMetadata.validate(7,49,"quest-completion",metadata);check(true,"Native checkpoint metadata accepts the acknowledged owner count and pending award target");
   try{PlayerBotMetadata.validate(7,49,"quest-completion",Map.of("account","7","character","49","quest.7.900091","3:-1"));throw new AssertionError("Negative shared award target accepted");}
   catch(java.io.IOException expected){check(true,"Invalid persisted sharing progress is refused rather than paying duplicate defaults");}
   q.setStatus(QuestStatus.START);q.setCompleteCount(2);
   var snapshot=PlayerBotQuestCompletionSharing.snapshot(bot,1000);
   check(snapshot.quests().size()==1&&snapshot.quests().getFirst().count()==2,"History includes previous completions of a currently active repeatable quest");
   PlayerBotQuestCompletionSharing.ownerCompleted(bot,900091);
   q.setCompleteCount(3);
   check(PlayerBotQuestCompletionSharing.snapshot(bot,1001).quests().getFirst().count()==3,"A new owner completion invalidates history immediately");
   bot.quests.addQuest(900092,new QuestState(900092,QuestStatus.REWARD));
   var items=com.aionemu.gameserver.services.QuestService.class.getDeclaredMethod("getRewardItems",QuestEnv.class,com.aionemu.gameserver.model.templates.QuestTemplate.class,boolean.class,Integer.class,Integer.class);items.setAccessible(true);
   var template=DataManager.QUEST_DATA.getQuestById(900092);var env=new QuestEnv(null,bot,900092);
   var base=(List<com.aionemu.gameserver.model.templates.quest.QuestItems>)items.invoke(null,env,template,false,0,0);
   var extra=(List<com.aionemu.gameserver.model.templates.quest.QuestItems>)items.invoke(null,env,template,true,null,1);
   check(base.stream().map(i->i.getItemId()).toList().equals(List.of(100,101)),"Base rewards retain fixed items and the independently selected first choice");
   check(extra.stream().map(i->i.getItemId()).toList().equals(List.of(200,202)),"Extended rewards retain fixed items and a different independent choice");
   env.setDialogActionId(DialogAction.SELECTED_QUEST_REWARD2);
   var human=(List<com.aionemu.gameserver.model.templates.quest.QuestItems>)items.invoke(null,env,template,false,0,null);
   check(human.stream().map(i->i.getItemId()).toList().equals(List.of(100,102)),"Ordinary human reward-dialog choice remains unchanged");
   var stateType=Class.forName(PlayerBotQuestCompletionSharing.class.getName()+"$State");var sharing=unsafe.allocateInstance(stateType);
   var progress=new HashMap<Long,PlayerBotQuestCompletionSharing.Progress>();
   for(int id:List.of(900091,900092,900093))progress.put((long)bot.getObjectId()<<32|id&0xffffffffL,new PlayerBotQuestCompletionSharing.Progress(id==900091?4:1,id==900091?4:1));
   var urgent=new LinkedHashMap<Integer,Integer>();
   for(var entry:Map.<String,Object>of("blocked",new HashSet<Integer>(),"progress",progress,"urgent",urgent).entrySet()){
    var field=stateType.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(sharing,entry.getValue());
   }
   var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
   for(String name:List.of("owner","bot")){var field=PlayerBotSession.class.getDeclaredField(name);field.setAccessible(true);field.set(session,bot);}
   var history=new PlayerBotQuestCompletionSharing.Snapshot(1000,List.of(new PlayerBotQuestCompletionSharing.Completed(900091,4,0,0,1),new PlayerBotQuestCompletionSharing.Completed(900092,1,0,0,2),new PlayerBotQuestCompletionSharing.Completed(900093,1,0,0,3)));
   var next=PlayerBotQuestCompletionSharing.class.getDeclaredMethod("next",PlayerBotSession.class,stateType,PlayerBotQuestCompletionSharing.Snapshot.class);next.setAccessible(true);
   var first=(PlayerBotQuestCompletionSharing.Completed)next.invoke(null,session,sharing,history);var second=(PlayerBotQuestCompletionSharing.Completed)next.invoke(null,session,sharing,history);
   check(first.quest()==900091&&second.quest()==900092,"A heavily repeated historical quest cannot starve the next pending quest");
   urgent.put(900091,1);
   check(((PlayerBotQuestCompletionSharing.Completed)next.invoke(null,session,sharing,history)).quest()==900091,"A new live completion precedes the remaining historical backlog");
  } finally {DataManager.QUEST_DATA=oldQuests;DataManager.NPC_DATA=oldNpcs;}
 }
 public static void main(String[]args)throws Exception{
  var u=Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);Unsafe unsafe=(Unsafe)u.get(null);
  var old=DataManager.PLAYER_EXPERIENCE_TABLE;
  try{
   var table=(PlayerExperienceTable)JAXBContext.newInstance(PlayerExperienceTable.class).createUnmarshaller().unmarshal(new File("game-server/data/static_data/player_experience_table.xml"));
   DataManager.PLAYER_EXPERIENCE_TABLE=table;
   for(int quest:List.of(1006,2008)){
    var a=(Actor)unsafe.allocateInstance(Actor.class);a.data=new Data();a.data.setPlayerClass(PlayerClass.WARRIOR);a.controller=new Controller();a.quests=new QuestStateList();a.data.actor=a;
    a.data.setExp(0);check(a.data.getLevel()==1 && a.controller.newLevel==1,"Native XP initializes a level-one resident");
    a.data.setExp(table.getStartExpForLevel(5));check(a.data.getLevel()==5 && a.controller.oldLevel==1 && a.controller.newLevel==5,"Earned levels reach native controller callback without online flag");
    a.data.setExp(table.getStartExpForLevel(20));check(a.data.getLevel()==9 && a.data.getExp()==table.getStartExpForLevel(10),"Offline headless identity cannot bypass native Ascension cap");
    int before=a.controller.calls;a.data.setExp(a.data.getExp());check(a.controller.calls==before,"Unchanged XP does not emit another callback");
    a.data.setPlayerClass(PlayerClass.GLADIATOR);check(!a.data.updateDaeva(),"Subclass alone cannot complete Ascension");
    a.quests.addQuest(quest,new QuestState(quest,QuestStatus.COMPLETE));check(a.data.updateDaeva(),"Native completion of either racial Ascension unlocks progression");
    a.data.setExp(table.getStartExpForLevel(20));check(a.data.getLevel()==20 && a.controller.newLevel==20,"Earned post-Ascension XP resumes native progression");
   }
  }finally{DataManager.PLAYER_EXPERIENCE_TABLE=old;}
  var cosmetics=(CosmeticItemsData)JAXBContext.newInstance(CosmeticItemsData.class).createUnmarshaller().unmarshal(new File("game-server/data/static_data/cosmetic_items/cosmetic_items.xml"));
  var base=new PlayerAppearance();base.setFace(0);base.setHair(0);base.setHeight(1.01f);base.setShoulders(123);base.setVoice(3);base.setEyeRGB(567);
  for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))for(Gender gender:Gender.values()){
   var allowed=cosmetics.getTemplates().stream().filter(t->t.getRace()==race && ("ALL".equals(t.getGenderPermitted()) || gender.name().equals(t.getGenderPermitted()))).toList();
   Set<String> appearances=new HashSet<>();boolean valid=true,stable=true,preserved=true;
   for(int id=1;id<=64;id++){
    var a=PlayerBotWorldLooks.create(base,race,gender,id,cosmetics.getTemplates());var repeat=PlayerBotWorldLooks.create(base,race,gender,id,cosmetics.getTemplates());
    appearances.add(a.getFace()+":"+a.getHair()+":"+a.getHairRGB()+":"+a.getSkinRGB());
    for(var entry:Map.of("face_type",a.getFace(),"hair_type",a.getHair(),"hair_color",a.getHairRGB(),"face_color",a.getSkinRGB()).entrySet())
     valid &= allowed.stream().anyMatch(t->t.getType().equals(entry.getKey()) && t.getId()==entry.getValue() && (entry.getKey().equals("face_type") || !t.getCosmeticName().startsWith("test_")));
    stable &= a.getFace()==repeat.getFace() && a.getHair()==repeat.getHair() && a.getHairRGB()==repeat.getHairRGB() && a.getSkinRGB()==repeat.getSkinRGB();
    preserved &= a!=base && a.getHeight()==base.getHeight() && a.getShoulders()==123 && a.getVoice()==3 && a.getEyeRGB()==567;
   }
   check(valid,"All sampled model IDs/colors exist for exact race/gender, excluding artificial test colors");
   check(stable,"Creation retries choose the same appearance for an existing native identity");
   check(preserved,"Template shape/height/voice retained in independent copies");
   check(appearances.size()>48,"Population has varied face/hair/skin combinations");
  }
  check(base.getFace()==0 && base.getHair()==0 && base.getHeight()==1.01f,"Population generation never changes the owner's appearance template");
  var fallback=PlayerBotWorldLooks.create(base,Race.ELYOS,Gender.MALE,1,List.of());check(fallback!=base && fallback.getHeight()==base.getHeight() && fallback.getFace()==base.getFace(),"Missing cosmetic catalog preserves valid template rather than inventing model IDs");
  sharedRewards(unsafe);
  System.out.println("OK: "+checks+" native world XP/Ascension, appearance and shared quest reward checks; no world/DB/IDs");
 }
}
