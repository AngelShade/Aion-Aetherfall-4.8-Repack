package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotWardenConfig;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION.ActionType;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Native daily quest state/quest rewards, with the existing Recruitment Ticket ledger. */
public final class PlayerBotRoadBounty {
 public static final int QUEST=14997;
 public static boolean available(Player player) {
  if(!PlayerBotWardenConfig.ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotConfig.ROAD_DUTY || player.isPlayerBot())return false;
  var q=player.getQuestStateList().getQuestState(QUEST);return q==null || q.isStartable();
 }
 static void accept(Player player) {
  if(!available(player) || player.getWorldId()%10000!=0)return;
  if(QuestService.startQuest(new QuestEnv(null,player,QUEST),QuestStatus.START,false)) {
   var q=player.getQuestStateList().getQuestState(QUEST);
   // The native packed quest variables retain the accepted map, step and counter.
   q.setQuestVar((player.getWorldId()/10000)<<12);PacketSendUtility.sendPacket(player,new SM_QUEST_ACTION(ActionType.UPDATE,q));
  }
 }
 static boolean counts(int acceptedMap,int map,boolean xpEligible,double corridor) {
  return acceptedMap==map && xpEligible && corridor<=PlayerBotWardenConfig.BOUNTY_CORRIDOR;
 }
 public static void killed(Player player,Npc npc,boolean xpEligible) {
  if(!PlayerBotWardenConfig.ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotConfig.ROAD_DUTY || player.isPlayerBot() || player.isDead() || npc.getWorldId()!=player.getWorldId() || player.isInInstance())return;
  var q=player.getQuestStateList().getQuestState(QUEST);if(q==null || q.getStatus()!=QuestStatus.START)return;
  int accepted=(q.getQuestVars().getQuestVars()>>>12)*10000;
  double corridor=PlayerBotWardenBeats.all().values().stream().map(PlayerBotWardenBeats.Beat::route)
   .filter(r->r.map()==npc.getWorldId() && r.race()==player.getRace()).mapToDouble(r->r.distance(npc.getX(),npc.getY(),npc.getZ())).min().orElse(Double.POSITIVE_INFINITY);
  if(!counts(accepted,player.getWorldId(),xpEligible,corridor))return;
  if(!npc.getController().markRoadBountyCredit(player.getObjectId()))return;
  int required=Math.clamp(PlayerBotWardenConfig.BOUNTY_KILLS,1,63),count=Math.min(required,q.getQuestVarById(1)+1);q.setQuestVarById(1,count);
  PacketSendUtility.sendPacket(player,new SM_QUEST_ACTION(ActionType.UPDATE,q));
  if(count==required) {
   q.setQuestVarById(0,1);q.setStatus(QuestStatus.REWARD);q.setRewardGroup(Math.clamp(player.getLevel()-1,0,64));
   QuestService.finishQuest(new QuestEnv(null,player,QUEST,DialogAction.SELECTED_QUEST_NOREWARD));
  }
 }
 public static void entered(Player player) {
  PlayerBotWardens.entered(player);
  var q=player.getQuestStateList().getQuestState(QUEST);if(q!=null && q.getStatus()==QuestStatus.COMPLETE)completed(player);
 }
 public static void completed(Player player) {
  if(player.isPlayerBot())return;var q=player.getQuestStateList().getQuestState(QUEST);
  if(q!=null && q.getStatus()==QuestStatus.COMPLETE)PlayerBotTickets.bountyCompleted(player,"road-bounty:"+player.getAccount().getId()+":"+player.getObjectId()+":"+q.getCompleteCount());
 }
 public static long reward(long nativeValue,boolean xp) {
  int percent=xp?PlayerBotWardenConfig.BOUNTY_XP_PERCENT:PlayerBotWardenConfig.BOUNTY_KINAH_PERCENT;
  return Math.multiplyExact(nativeValue,Math.clamp(percent,0,10000))/100;
 }
 private PlayerBotRoadBounty(){}
}
