package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.Field;
import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;

/** Read authoritative XML end_npc_ids instead of guessing from talk registrations. */
final class PlayerBotQuestMetadata {
 static boolean ready(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);if(state==null)return false;
  if(state.getStatus()==com.aionemu.gameserver.questEngine.model.QuestStatus.REWARD)return true;
  if(state.getStatus()!=com.aionemu.gameserver.questEngine.model.QuestStatus.START || DataManager.XML_QUESTS==null)return false;
  var model=DataManager.XML_QUESTS.getQuest(quest);
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemOrdersData)
   return state.getQuestVarById(0)==0 && PlayerBotQuestConversations.number(model,"talkNpcId1")==0
    && PlayerBotQuestConversations.number(model,"talkNpcId2")==0;
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToManyData) {
   var infos=PlayerBotQuestConversations.list(model,"npcInfos");
   return !infos.isEmpty() && state.getQuestVarById(0)==infos.size()-1 && com.aionemu.gameserver.services.QuestService.collectItemCheck(new com.aionemu.gameserver.questEngine.model.QuestEnv(null,bot,quest),false);
  }
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData && PlayerBotQuestConversations.number(model,"nextNpcId")>0 && state.getQuestVarById(0)==0)return false;
  if(model!=null && model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData.class)
   return QuestEngine.getInstance().getRequiredKillNpcIds(bot,quest).isEmpty();
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToData) {
   var template=DataManager.QUEST_DATA.getQuestById(quest);
   return template!=null && (template.getQuestWorkItems()==null || template.getQuestWorkItems().getQuestWorkItem().stream()
    .allMatch(i->bot.getInventory().getItemCountByItemId(i.getItemId())>=i.getCount()));
  }
  if(model!=null && model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class)
   return com.aionemu.gameserver.services.QuestService.collectItemCheck(new com.aionemu.gameserver.questEngine.model.QuestEnv(null,bot,quest),false);
  return false;
 }
 static Set<Integer> rewardNpcIds(Player bot,int quest) {
  var observed=PlayerBotPartyCompletion.rewardNpcIds(bot,quest);if(!observed.isEmpty())return observed;
  var nativeIds=QuestEngine.getInstance().getPlayerBotRewardNpcIds(bot,quest);if(!nativeIds.isEmpty())return nativeIds;
  if(DataManager.XML_QUESTS==null)return Set.of();
  var model=DataManager.XML_QUESTS.getQuest(quest);if(model==null)return Set.of();
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemOrdersData) {
   int end=PlayerBotQuestConversations.number(model,"endNpcId");return end>0 ? Set.of(end) : Set.of();
  }
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToManyData) {
   var infos=PlayerBotQuestConversations.list(model,"npcInfos");return infos.isEmpty() ? Set.of() : Set.copyOf(((com.aionemu.gameserver.questEngine.handlers.models.NpcInfos)infos.getLast()).getNpcIds());
  }
  Object ends=field(model,"endNpcIds");if(ends==null)ends=field(model,"startNpcIds");
  if(!(ends instanceof Collection<?> values))return Set.of();
  Set<Integer> result=new TreeSet<>();for(Object id:values)if(id instanceof Integer n && n>0)result.add(n);
  return Set.copyOf(result);
 }
 private static Object field(Object object,String name) {
  for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
   Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);
  }catch(NoSuchFieldException ignored){}catch(IllegalAccessException error){throw new IllegalStateException("Cannot read native quest NPC metadata",error);}
  return null;
 }
 static boolean startPage(int page){return page==4 || page==1011 || page==4762;}
 static Set<Integer> startPages(Object model) {
  Set<Integer> pages=new LinkedHashSet<>(List.of(4,1011,4762));
  // These exact native handlers accept QUEST_ACCEPT_1 after their declared
  // QUEST_SELECT page. Custom campaign handlers are not inferred from a page.
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToManyData) {
   int declared=PlayerBotQuestConversations.number(model,"startDialogId");if(declared>0)pages.add(declared);
  }
  return Set.copyOf(pages);
 }
 static String coverage(Object model) {
  if(model==null)return "no XML model: registered native handler and objective adapter required";
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportOnLevelUpData)return "native enter-world/level acceptance and reward";
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemOrdersData)return "native item-order conversations/reward; item-start adapter required";
  if(model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData.class)return "native hunt/accept/reward";
  if(model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class)return "native collect/conversation/accept/reward";
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToData || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToManyData)return "native report/conversation/accept/reward";
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.XmlQuestData)return "XML conversation subset; native custom start/kill/reward adapter required";
  return model.getClass().getSimpleName()+": native objective adapter required";
 }
 static Map<String,Object> diagnostic(Player bot,int quest) {
  var model=DataManager.XML_QUESTS==null?null:DataManager.XML_QUESTS.getQuest(quest);
  var capability=PlayerBotQuestCapabilities.describe(bot,quest,model);
  return Map.of("quest",quest,"coverage",coverage(model),"rewardReady",ready(bot,quest),"capability",capability,
   "nextActivity",PlayerBotWorldActivities.activity(bot));
 }
 static int rewardAction(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);
  var model=DataManager.XML_QUESTS==null ? null : DataManager.XML_QUESTS.getQuest(quest);
  return state!=null && state.getStatus()==com.aionemu.gameserver.questEngine.model.QuestStatus.START && model!=null
   && model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class
   ? com.aionemu.gameserver.model.DialogAction.CHECK_USER_HAS_QUEST_ITEM_SIMPLE : com.aionemu.gameserver.model.DialogAction.SELECT_QUEST_REWARD;
 }
 static boolean accept(Player bot,com.aionemu.gameserver.model.gameobjects.Npc npc,int quest,PlayerBotQuestDialog dialog) {
  npc.getController().onDialogSelect(com.aionemu.gameserver.model.DialogAction.ASK_QUEST_ACCEPT,0,bot,quest,0);
  if(dialog.offered(4)){npc.getController().onDialogSelect(com.aionemu.gameserver.model.DialogAction.QUEST_ACCEPT,4,bot,quest,0);return true;}
  npc.getController().onDialogSelect(com.aionemu.gameserver.model.DialogAction.QUEST_SELECT,0,bot,quest,0);
  var model=DataManager.XML_QUESTS==null?null:DataManager.XML_QUESTS.getQuest(quest);
  for(int page:startPages(model))if(dialog.offered(page)) {
   npc.getController().onDialogSelect(com.aionemu.gameserver.model.DialogAction.QUEST_ACCEPT_1,page,bot,quest,0);return true;
  }
  PlayerBotQuestSync.notice(bot,"The NPC uses a custom dialog for "+com.aionemu.gameserver.utils.ChatUtil.quest(quest)+". I need your guidance.","custom:"+quest,120000);
  return false;
 }
 private PlayerBotQuestMetadata(){}
}
