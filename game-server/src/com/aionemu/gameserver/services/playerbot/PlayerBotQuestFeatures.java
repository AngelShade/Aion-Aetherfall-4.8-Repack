package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;

/** Configuration affects AI admission, never native human handlers or saved progress. */
final class PlayerBotQuestFeatures {
 static String disabled(int quest) {
  if(!PlayerBotConfig.QUESTS_ENABLED)return "Quest automation is disabled by gameserver.playerbots.quests.enable";
  var model=DataManager.XML_QUESTS==null?null:DataManager.XML_QUESTS.getQuest(quest);
  var engine=com.aionemu.gameserver.questEngine.QuestEngine.getInstance();
  if(!PlayerBotConfig.QUEST_CRAFTING && engine.isPlayerBotCraftingQuest(quest))
   return "Native crafting decisions are disabled by gameserver.playerbots.quests.crafting";
  if(!PlayerBotConfig.QUEST_MATERIALS && engine.isPlayerBotGatheringQuest(quest))
   return "Native gathering qualification decisions are disabled by gameserver.playerbots.quests.materials";
  if(model==null && !PlayerBotConfig.QUEST_CUSTOM)return "Custom/campaign quest automation is disabled by gameserver.playerbots.quests.custom";
  if((model instanceof com.aionemu.gameserver.questEngine.handlers.models.WorkOrdersData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.CraftingRewardsData) && !PlayerBotConfig.QUEST_CRAFTING)
   return "Native crafting decisions are disabled by gameserver.playerbots.quests.crafting";
  if((model instanceof com.aionemu.gameserver.questEngine.handlers.models.RelicRewardsData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.FountainRewardsData) && !PlayerBotConfig.QUEST_EXCHANGE)
   return "Native offering exchanges are disabled by gameserver.playerbots.quests.exchange";
  return "";
 }
 static boolean enabled(int quest){return disabled(quest).isEmpty();}
 private PlayerBotQuestFeatures(){}
}
