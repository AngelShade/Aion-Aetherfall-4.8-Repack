/* Native exchange adapter following pinned QuestAction / AcceptQuestAction,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c, GPL-2.0-or-later.
 * Aion handlers alone accept, consume the owned offering and grant rewards. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.questEngine.handlers.models.FountainRewardsData;
import com.aionemu.gameserver.questEngine.handlers.models.RelicRewardsData;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

final class PlayerBotQuestExchange {
 static Object model(int quest){return DataManager.XML_QUESTS==null?null:DataManager.XML_QUESTS.getQuest(quest);}
 static boolean exchange(int quest){var m=model(quest);return m instanceof FountainRewardsData || m instanceof RelicRewardsData;}
 /** Native relic choices are alternatives. Never call the mutating category helper to plan. */
 static int relicChoice(QuestTemplate template,java.util.function.IntToLongFunction held) {
  if(template==null || template.getCollectItems()==null || template.getRewards()==null)return -1;
  var items=template.getCollectItems().getCollectItem();
  for(int i=0;i<Math.min(4,Math.min(items.size(),template.getRewards().size()));i++) {
   var item=items.get(i);if(item.getCount()>0 && held.applyAsLong(item.getItemId())>=item.getCount())return i;
  }return -1;
 }
 static boolean eligible(Player bot,int quest) {
  var template=DataManager.QUEST_DATA.getQuestById(quest);var m=model(quest);
  if(m instanceof RelicRewardsData)return relicChoice(template,bot.getInventory()::getItemCountByItemId)>=0;
  if(m instanceof FountainRewardsData)return template!=null && template.getCollectItems()!=null
   && !template.getCollectItems().getCollectItem().isEmpty()
   && template.getCollectItems().getCollectItem().stream().allMatch(i->bot.getInventory().getItemCountByItemId(i.getItemId())>=i.getCount());
  return true;
 }
 static boolean accept(Player bot,Npc npc,int quest,PlayerBotQuestDialog dialog) {
  var m=model(quest);if(!exchange(quest) || !eligible(bot,quest))return false;
  dialog.clearPage();
  npc.getController().onDialogSelect(m instanceof RelicRewardsData?DialogAction.EXCHANGE_COIN:DialogAction.USE_OBJECT,0,bot,quest,0);
  if(m instanceof FountainRewardsData) {
   if(!dialog.offered(10))return false;
   dialog.clearPage();npc.getController().onDialogSelect(DialogAction.SETPRO1,10,bot,quest,0);
  } else if(!selectRelic(bot,npc,quest,dialog))return false;
  var state=bot.getQuestStateList().getQuestState(quest);
  return state!=null && (state.getStatus()==QuestStatus.START || state.getStatus()==QuestStatus.REWARD);
 }
 static boolean selectRelic(Player bot,Npc npc,int quest,PlayerBotQuestDialog dialog) {
  var state=bot.getQuestStateList().getQuestState(quest);
  int choice=relicChoice(DataManager.QUEST_DATA.getQuestById(quest),bot.getInventory()::getItemCountByItemId);
  int page=dialog.offered(4762)?4762:dialog.offered(1011)?1011:0;
  if(state==null || state.getStatus()!=QuestStatus.START || state.getQuestVarById(0)!=0 || choice<0 || page==0)return false;
  int action=switch(choice){case 0->DialogAction.SELECT1;case 1->DialogAction.SELECT2;case 2->DialogAction.SELECT3;case 3->DialogAction.SELECT4;default->throw new IllegalStateException();};
  dialog.clearPage();npc.getController().onDialogSelect(action,page,bot,quest,0);
  return state.getStatus()==QuestStatus.REWARD;
 }
 static boolean ready(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);
  return model(quest) instanceof RelicRewardsData && state!=null && state.getStatus()==QuestStatus.START
   && state.getQuestVarById(0)==0 && eligible(bot,quest);
 }
 static void openReward(Player bot,Npc npc,int quest,PlayerBotQuestDialog dialog) {
  npc.getController().onDialogSelect(DialogAction.USE_OBJECT,0,bot,quest,0);
  if(ready(bot,quest))selectRelic(bot,npc,quest,dialog);
 }
 private PlayerBotQuestExchange(){}
}
