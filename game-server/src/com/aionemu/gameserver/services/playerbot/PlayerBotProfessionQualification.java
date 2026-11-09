package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.IntToLongFunction;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.questEngine.handlers.models.CraftingRewardsData;
import com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Only native, finite qualification chains enter the background profession ledger. */
final class PlayerBotProfessionQualification {
 record Requirement(int quest,int skill,int reward,Map<Integer,Long> items,long kinah,boolean readable,String reason) {
  Requirement {items=Map.copyOf(items);}
 }
 private record Key(Race race,int skill,int reward) {}
 private static final Map<Key,List<Integer>> FINALS=new HashMap<>();
 private static final Set<Integer> RETAINED=new HashSet<>();
 private static final Set<Integer> COLLECTIONS=Set.of(1972,1973,1974,1975,1976,1977,1978,1979,1980,1981,1982,1983,
  2972,2973,2974,2975,2976,2977,2978,2979,2980,2981,2982,2983,19052,19053,19055,29052,29053,29055,
  19000,19002,29000,29002,19008,19014,19020,19026,19032,29008,29014,29020,29026,29032);
 private static final Set<Integer> COOKING=Set.of(19038,29038),CONSTRUCTION=Set.of(19057,29057);
 static final long COOKING_FEE=6500,CONSTRUCTION_FEE=167500;
 private static final Map<Integer,Integer> TAPPING=Map.of(19001,19000,19003,19002,29001,29000,29003,29002);
 static void prepare() {
  FINALS.clear();RETAINED.clear();for(var template:DataManager.QUEST_DATA.getQuestTemplates()) {
   var model=DataManager.XML_QUESTS.getQuest(template.getId());int skill=0,reward=0;
   if(model instanceof CraftingRewardsData){skill=PlayerBotQuestConversations.number(model,"skillId");reward=PlayerBotQuestConversations.number(model,"levelReward");}
   else if(TAPPING.containsKey(template.getId())){var before=DataManager.QUEST_DATA.getQuestById(TAPPING.get(template.getId()));if(before!=null){skill=before.getCombineSkill();reward=before.getCombineSkillPoint()+1;}}
   if(skill>0 && (reward==400 || reward==500))FINALS.computeIfAbsent(new Key(template.getRacePermitted(),skill,reward),k->new ArrayList<>()).add(template.getId());
  }
  FINALS.replaceAll((k,v)->v.stream().sorted().toList());
  var pending=new ArrayDeque<Integer>();FINALS.values().forEach(pending::addAll);var seen=new HashSet<Integer>();
  while(!pending.isEmpty()) {
   int id=pending.removeFirst();if(!seen.add(id))continue;var template=DataManager.QUEST_DATA.getQuestById(id);if(template==null)continue;
   var model=DataManager.XML_QUESTS.getQuest(id);
   if(!(model instanceof CraftingRewardsData) && !(model instanceof ItemCollectingData) && !COLLECTIONS.contains(id) && !COOKING.contains(id) && !CONSTRUCTION.contains(id) && !TAPPING.containsKey(id))continue;
   RETAINED.add(id);for(var condition:template.getXMLStartConditions())if(condition.getFinishedPreconditions()!=null)for(var before:condition.getFinishedPreconditions())pending.add(before.getQuestId());
  }
 }
 static boolean qualification(int quest){return RETAINED.contains(quest);}
 static Requirement plan(PlayerBotSession session) {
  if(!session.dormant())return null;var bot=session.bot();var profession=PlayerBotProfessions.state(bot);if(profession==null)return null;
  int points=PlayerBotProfessionCatalog.points(bot,profession.primary);Requirement blocked=null;
  for(int reward:new int[]{400,500})for(int quest:FINALS.getOrDefault(new Key(bot.getRace(),profession.primary,reward),List.of())) {
   var state=bot.getQuestStateList().getQuestState(quest);if(state!=null && state.getStatus()==QuestStatus.COMPLETE)continue;
   if(points!=reward-1 && !(points==reward && state!=null && state.getStatus()==QuestStatus.REWARD))continue;
   var path=PlayerBotQuestPrerequisites.plan(bot,quest);
   if(!path.reachable()){if(blocked==null)blocked=new Requirement(quest,profession.primary,reward,Map.of(),0,false,"Native qualification "+quest+" blocked: "+path.blockers());continue;}
   var chain=new ArrayList<>(path.chain());chain.add(quest);
   for(int step:chain) {
    var current=bot.getQuestStateList().getQuestState(step);if(current!=null && current.getStatus()==QuestStatus.COMPLETE)continue;
    var required=requirement(DataManager.QUEST_DATA.getQuestById(step),DataManager.XML_QUESTS.getQuest(step),profession.primary,step==quest?reward:0,current);
    if(CONSTRUCTION.contains(step) && missing(bot,required).isEmpty())required=new Requirement(step,required.skill(),required.reward(),required.items(),0,required.readable(),required.reason());
    if(!required.readable()){if(blocked==null)blocked=required;break;}
    if(step==quest && profession.primary>=40000 && points<reward && !PlayerBotQuestCrafting.qualificationAllowed(bot,reward))return new Requirement(step,profession.primary,reward,Map.of(),0,false,"Native qualification profession slot limit");
    return required;
   }
  }
  return blocked;
 }
 static Requirement requirement(QuestTemplate template,Object model,int skill,int reward,QuestState state) {
  if(template==null)return new Requirement(0,skill,reward,Map.of(),0,false,"Native qualification template unavailable");
  int quest=template.getId();boolean finalStep=model instanceof CraftingRewardsData || TAPPING.containsKey(quest);
  if(!finalStep && !(model instanceof ItemCollectingData) && !COLLECTIONS.contains(quest) && !COOKING.contains(quest) && !CONSTRUCTION.contains(quest))return new Requirement(quest,skill,reward,Map.of(),0,false,"Native qualification "+quest+" has no readable background requirement adapter");
  var required=new TreeMap<Integer,Long>();
  // A native REWARD state already paid its objective cost before the checkpoint.
  long fee=0;int stage=state==null?0:state.getQuestVarById(0),index=0;
  if(state==null || state.getStatus()!=QuestStatus.REWARD)if(template.getCollectItems()!=null)for(var item:template.getCollectItems().getCollectItem()) {
   if(!COOKING.contains(quest) || index++>=stage/3)required.merge(item.getItemId(),(long)item.getCount(),Math::addExact);
  }
  // Scripted cooking purchases each stage; construction can accept an already owned product at any stage.
  if((state==null || state.getStatus()!=QuestStatus.REWARD) && COOKING.contains(quest)) {
   if(stage<0 || stage>11 || template.getCollectItems()==null || template.getCollectItems().getCollectItem().size()!=4)return new Requirement(quest,skill,reward,Map.of(),0,false,"Native cooking qualification "+quest+" has unreadable stage/collection");
   fee=COOKING_FEE*(required.size()-(stage%3==2?1:0));
  }
  if((state==null || state.getStatus()!=QuestStatus.REWARD) && CONSTRUCTION.contains(quest)) {
   if(stage<0 || stage>2)return new Requirement(quest,skill,reward,Map.of(),0,false,"Native construction qualification "+quest+" has unreadable stage");
   fee=stage==1?0:CONSTRUCTION_FEE;
  }
  if(!finalStep && required.isEmpty() && (state==null || state.getStatus()!=QuestStatus.REWARD))return new Requirement(quest,skill,reward,Map.of(),0,false,"Native qualification "+quest+" has no declared collection");
  return new Requirement(quest,skill,reward,required,fee,true,"Native qualification "+quest+" requires "+required+(fee>0?" and "+fee+" Kinah":""));
 }
 static Map<Integer,Long> missing(Player bot,Requirement required){return PlayerBotQuestCrafting.missing(required.items(),bot.getInventory()::getItemCountByItemId);}
 interface Access {
  boolean background();QuestState state(int quest);long held(int item);long kinah();long allowance();
  boolean accept(int quest);boolean pay(long amount);boolean consume(int item,long amount);void paid(long amount);boolean complete(Requirement required);
 }
 static boolean finish(Access access,Requirement required) {
  if(required==null || !required.readable() || !access.background())return false;
  var state=access.state(required.quest());if(state!=null && state.getStatus()==QuestStatus.COMPLETE)return false;
  if(state!=null && state.getStatus()==QuestStatus.LOCKED)return false;
  if(state==null || state.getStatus()!=QuestStatus.REWARD) {
   if(required.kinah()>access.kinah() || required.kinah()>access.allowance() || !missing(required.items(),access::held).isEmpty())return false;
   if(state==null && !access.accept(required.quest()))return false;
   state=access.state(required.quest());if(state==null || state.getStatus()!=QuestStatus.START)return false;
   if(!access.pay(required.kinah()))return false;
   for(var entry:required.items().entrySet())if(!access.consume(entry.getKey(),entry.getValue()))throw new IllegalStateException("Qualification custody changed under actor lease");
   access.paid(required.kinah());state.setQuestVar(0);state.setStatus(QuestStatus.REWARD);
  }
  return access.complete(required);
 }
 private static Map<Integer,Long> missing(Map<Integer,Long> required,IntToLongFunction held){return PlayerBotQuestCrafting.missing(required,held);}
 static boolean finish(PlayerBotSession session,Requirement required) {
  if(required==null || !session.dormant())return false;var bot=session.bot();var saved=bot.getQuestStateList().getQuestState(required.quest());
  int points=PlayerBotProfessionCatalog.points(bot,required.skill());
  if(saved==null || saved.getStatus()!=QuestStatus.REWARD) {
   if(points!=399 && points!=499 || required.reward()>0 && points!=required.reward()-1)return false;
   var fresh=requirement(DataManager.QUEST_DATA.getQuestById(required.quest()),DataManager.XML_QUESTS.getQuest(required.quest()),required.skill(),required.reward(),saved);
   if(CONSTRUCTION.contains(required.quest()) && missing(bot,fresh).isEmpty())fresh=new Requirement(fresh.quest(),fresh.skill(),fresh.reward(),fresh.items(),0,fresh.readable(),fresh.reason());
   if(!fresh.readable() || !fresh.items().equals(required.items()) || fresh.kinah()!=required.kinah())return false;
   if(required.reward()>0 && required.skill()>=40000 && !PlayerBotQuestCrafting.qualificationAllowed(bot,required.reward()))return false;
  }
  return finish(new Access(){
   public boolean background(){return session.dormant() && PlayerBotProfessions.backgroundAllowed(bot);}
   public QuestState state(int quest){return bot.getQuestStateList().getQuestState(quest);}
   public long held(int item){return bot.getInventory().getItemCountByItemId(item);}
   public long kinah(){return bot.getInventory().getKinah();}
   public long allowance(){return PlayerBotProfessions.allowance(session,true);}
   public boolean accept(int quest){return QuestService.startQuest(new QuestEnv(null,bot,quest));}
   public boolean pay(long amount){return amount==0 || bot.getInventory().tryDecreaseKinah(amount);}
   public boolean consume(int item,long amount){return bot.getInventory().decreaseByItemId(item,amount);}
   public void paid(long amount){PlayerBotProfessions.paid(session,amount,true);}
   public boolean complete(Requirement r){
    var state=state(r.quest());var template=DataManager.QUEST_DATA.getQuestById(r.quest());QuestService.validateAndFixRewardGroup(state,r.quest());
    var choices=state.getRewardGroup()==null?List.<com.aionemu.gameserver.model.templates.quest.QuestItems>of():template.getRewards().get(state.getRewardGroup()).getSelectableRewardItem();
    int choice=PlayerBotGearPolicy.rewardChoice(bot,session.combatRole(),choices);int extra=template.getExtendedRewards()==null?-1:PlayerBotGearPolicy.rewardChoice(bot,session.combatRole(),template.getExtendedRewards().getSelectableRewardItem());
    if(r.reward()>0)bot.getSkillList().addSkill(bot,r.skill(),r.reward());
    if(!QuestService.finishPlayerBotQuest(new QuestEnv(null,bot,r.quest()),choice,extra))return false;
    QuestService.removeQuestWorkItems(bot,state);return true;
   }
  },required);
 }
 private PlayerBotProfessionQualification(){}
}
