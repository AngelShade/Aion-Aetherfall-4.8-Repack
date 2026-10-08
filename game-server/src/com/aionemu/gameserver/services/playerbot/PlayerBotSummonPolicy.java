package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Explicit summon policy, separate from automatic native map/wipe recovery. */
public final class PlayerBotSummonPolicy {
 static void request(){
  if(!PlayerBotConfig.ENABLED)throw new IllegalArgumentException("Player companions are disabled in the server configuration.");
  if(!PlayerBotConfig.SUMMON_ENABLED)throw new IllegalArgumentException("Companion summoning is disabled in the server configuration.");
 }
 static boolean busy(Player player){
  return player.isCasting() || player.isTrading() || player.isLooting()
   || player.getController().hasTask(TaskId.ITEM_USE) || player.getController().hasScheduledTask(TaskId.ACTION_ITEM_NPC);
 }
 static void preflight(List<PlayerBotSession> sessions){
  if(sessions.isEmpty())throw new IllegalArgumentException("Recruit a companion first.");
  Player owner=sessions.getFirst().owner();
  for(var session:sessions)synchronized(session){
   if(session.owner()!=owner || !PlayerBotRecovery.ready(session))throw new IllegalArgumentException("Summon while alive, with your own active party, out of combat and after casting, trading, looting or using an item has finished.");
  }
 }
 static boolean partySafe(Player owner){
  return owner.getPlayerGroup().getMembers().stream().noneMatch(member->member.getController().isInCombat() || !member.isDead() && member.getAggroList().stream().findAny().isPresent());
 }
 static void regroup(List<PlayerBotSession> sessions){
  synchronized(PlayerBotService.getInstance()){
   preflight(sessions);
   for(var session:sessions)synchronized(session){
    // Recheck immediately before each native transition; no force/cancel path.
    preflight(List.of(session));PlayerBotRecovery.revive(session);
    if(!PlayerBotService.getInstance().relocate(session))throw new IllegalArgumentException("The companion cannot be summoned yet.");
    PlayerBotTravel.close(session);PlayerBotQuestSync.returnToOwner(session.bot());
   }
  }
 }
 private PlayerBotSummonPolicy(){}
}
