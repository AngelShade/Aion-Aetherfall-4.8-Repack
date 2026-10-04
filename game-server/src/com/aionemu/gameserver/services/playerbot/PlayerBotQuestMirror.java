/* Adapts QuestAction::CompleteQuest from mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Attribution: third-party/playerbots/AUTHORS.md. Automatic leader mirroring is
 * explicitly requested Aion behavior, not a claim about WoW's default policy. */
package com.aionemu.gameserver.services.playerbot;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.item.ItemService;

final class PlayerBotQuestMirror {
 static boolean shouldComplete(boolean enabled,boolean acceptedTogether,boolean skipped,int baselineCompletions,int leaderCompletions,QuestStatus leader,QuestStatus bot) {
  return enabled && acceptedTogether && !skipped && leader==QuestStatus.COMPLETE && bot==QuestStatus.START && leaderCompletions>baselineCompletions;
 }
 static boolean ready(Player bot,QuestState quest) {
  var template=DataManager.QUEST_DATA.getQuestById(quest.getQuestId());
  if(template==null || template.getExtendedRewards()!=null || PlayerBotQuestMetadata.rewardNpcIds(bot,quest.getQuestId()).isEmpty())return false;
  var model=DataManager.XML_QUESTS==null ? null : DataManager.XML_QUESTS.getQuest(quest.getQuestId());
  if(model==null || !(model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData.class
   || model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ReportToData.class
   || model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class))return false;
  // WoW's CompleteQuest fills missing required quest items before completion.
  // Retain Aion's inventory capacity and native grant/consume behavior.
  java.util.Map<Integer,Long> requirements=new java.util.TreeMap<>();
  if(template.getQuestWorkItems()!=null)for(var item:template.getQuestWorkItems().getQuestWorkItem())requirements.merge(item.getItemId(),item.getCount(),Math::max);
  if(model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class) {
   if(template.getCollectItems()!=null)for(var item:template.getCollectItems().getCollectItem())requirements.merge(item.getItemId(),item.getCount().longValue(),Math::max);
   else if(template.getInventoryItems()!=null)for(var item:template.getInventoryItems().getInventoryItems()) {
    if(item.getCount()==null)return false;requirements.merge(item.getItemId(),item.getCount().longValue(),Math::max);
   }
  }
  long slots=0;
  for(var item:requirements.entrySet())if(item.getValue()>held(bot,item.getKey())) {
   if(item.getKey()==com.aionemu.gameserver.model.items.ItemId.KINAH || DataManager.ITEM_DATA.getItemTemplate(item.getKey())==null)return false;
   slots++;
  }
  if(slots>bot.getInventory().getFreeSlots())return false;
  for(var item:requirements.entrySet()) {
   long missing=item.getValue()-held(bot,item.getKey());
   if(missing>0) {
    ItemService.addItem(bot,item.getKey(),missing);
    if(bot.getInventory().getItemCountByItemId(item.getKey())<item.getValue())return false;
   }
  }
  if(model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class
   && !com.aionemu.gameserver.services.QuestService.collectItemCheck(new com.aionemu.gameserver.questEngine.model.QuestEnv(null,bot,quest.getQuestId()),true))return false;
  quest.setStatus(QuestStatus.REWARD);
  com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(bot,new com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION(com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION.ActionType.UPDATE,quest));
  PlayerBotQuestSync.notice(bot,"Your party completed "+com.aionemu.gameserver.utils.ChatUtil.quest(quest.getQuestId())+". I am ready to turn it in at its NPC.","mirror:"+quest.getQuestId(),1000);
  return true;
 }
 static long held(Player bot,int item){return item==com.aionemu.gameserver.model.items.ItemId.KINAH ? bot.getInventory().getKinah() : bot.getInventory().getItemCountByItemId(item);}
 private PlayerBotQuestMirror(){}
}
