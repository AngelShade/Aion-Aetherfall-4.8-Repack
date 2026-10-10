/* Quest/travel capability adaptation of pinned mod-playerbots QuestAction and
 * ChooseTravelTargetAction. Native Aion handlers retain all authority.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Current native action evidence, distinct from a model name or whole-chain support. */
final class PlayerBotQuestCapabilities {
 static String missing(Object model) {
  if(model==null)return "A native custom-handler objective/dialog adapter is missing for this stage";
  return switch(model.getClass().getSimpleName()) {
   case "KillSpawnedData" -> "Native spawner-object interaction and spawned-target objective adapters are missing";
   case "KillInWorldData" -> "Native world-kill target and eligibility adapter is missing";
   case "KillInZoneData" -> "Native zone-kill target and zone eligibility adapter is missing";
   case "SkillUseData" -> "Native required-skill objective selection is missing";
   case "WorkOrdersData" -> "Native work-order recipe/crafting objective adapter is missing";
   case "CraftingRewardsData" -> "Native crafting qualification and reward-transition adapter is missing";
   case "RelicRewardsData" -> "Native relic exchange and reward-transition adapter is missing";
   case "FountainRewardsData" -> "Native fountain offering and reward-transition adapter is missing";
   case "ItemOrdersData" -> "Native item-start or remaining item-order dialog adapter is missing";
   case "ReportOnLevelUpData" -> "Waiting for native enter-world/level eligibility; no manual acceptance substitute";
   case "XmlQuestData" -> "No supported declared XML action is available at the current native stage";
   default -> "No current native objective, conversation or reward action is available; required items or targets may be absent";
  };
 }
 static Map<String,Object> describe(Player bot,int quest,Object model) {
  var engine=QuestEngine.getInstance();String handler=engine.getPlayerBotHandlerType(quest);
  var q=bot.getQuestStateList().getQuestState(quest);
  if(handler.isEmpty())return Map.of("registered",false,"handler","","action","UNAVAILABLE","reason","No native quest handler is registered; metadata cannot execute this quest");
  if(q==null || q.getStatus()!=QuestStatus.START && q.getStatus()!=QuestStatus.REWARD)
   return Map.of("registered",true,"handler",handler,"action","NOT_ACTIVE","reason","Native acceptance and prerequisites have not produced an active quest");
  Set<Integer> npcs=new TreeSet<>();String action="UNSUPPORTED_STAGE";
  if(PlayerBotQuestMetadata.ready(bot,quest)) {action="REWARD";npcs.addAll(PlayerBotQuestMetadata.rewardNpcIds(bot,quest));}
  else {
   npcs.addAll(PlayerBotQuestConversations.objectiveIds(null,bot,quest));if(!npcs.isEmpty())action="CONVERSATION";
   if(npcs.isEmpty()){npcs.addAll(engine.getRequiredKillNpcIds(bot,quest));if(!npcs.isEmpty())action="HUNT";}
   if(npcs.isEmpty()){npcs.addAll(PlayerBotQuestObjects.objectiveIds(bot,quest));if(!npcs.isEmpty())action="OBJECT";}
  }
  String reason=npcs.isEmpty() ? missing(model) : "Current native stage exposes eligible action NPC IDs; reachability, prerequisites and handler execution still apply";
  if(action.equals("REWARD") && npcs.isEmpty())reason="Native reward state is present, but its authorized reward NPC adapter is missing";
  return Map.of("registered",true,"handler",handler,"action",action,"npcs",Set.copyOf(npcs),"reason",reason);
 }
 private PlayerBotQuestCapabilities(){}
}
