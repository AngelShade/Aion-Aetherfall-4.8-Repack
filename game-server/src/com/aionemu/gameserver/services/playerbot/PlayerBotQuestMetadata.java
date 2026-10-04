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
  for(int page:new int[]{1011,4762})if(dialog.offered(page)) {
   npc.getController().onDialogSelect(com.aionemu.gameserver.model.DialogAction.QUEST_ACCEPT_1,page,bot,quest,0);return true;
  }
  PlayerBotQuestSync.notice(bot,"The NPC uses a custom dialog for "+com.aionemu.gameserver.utils.ChatUtil.quest(quest)+". I need your guidance.","custom:"+quest,120000);
  return false;
 }
 private PlayerBotQuestMetadata(){}
}
