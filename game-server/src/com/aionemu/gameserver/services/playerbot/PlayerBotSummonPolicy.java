package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayList;
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
   || player.getController().hasScheduledTask(TaskId.ITEM_USE) || player.getController().hasScheduledTask(TaskId.ACTION_ITEM_NPC);
 }
 static void preflight(List<PlayerBotSession> sessions){
  if(sessions.isEmpty())throw new IllegalArgumentException("Recruit a companion first.");
  Player owner=sessions.getFirst().owner();
  for(var session:sessions)synchronized(session){
   if(session.owner()!=owner || !PlayerBotRecall.eligible(session))throw new IllegalArgumentException("Summon your own active companions while you are alive and in their party.");
   if(owner.getController().isInCombat())throw new IllegalArgumentException("You cannot summon companions while you are in combat.");
  }
 }
 static boolean partySafe(Player owner){
  return PlayerBotTeams.members(owner).stream().noneMatch(member->member.getController().isInCombat() || !member.isDead() && member.getAggroList().stream().findAny().isPresent());
 }
 static void regroup(List<PlayerBotSession> sessions){
  synchronized(PlayerBotService.getInstance()){
   preflight(sessions);
   var failed=new ArrayList<String>();String reason=null;
   // One companion that cannot come must not strand the rest of the party.
   for(var session:sessions)synchronized(session){
    preflight(List.of(session));
    // Summon is the player's explicit choice, so it ends this companion's wait at the dungeon entrance.
    boolean released=PlayerBotFindGroup.releaseFromEntranceWait(session),moved=false;
    try{moved=PlayerBotRecall.recall(session);}
    catch(IllegalArgumentException refused){reason=refused.getMessage();}
    finally{if(!moved && released)PlayerBotFindGroup.restoreEntranceWait(session);}
    if(!moved)failed.add(session.bot().getName());
   }
   if(!failed.isEmpty())throw new IllegalArgumentException(failure(sessions.size(),failed,reason));
  }
 }
 static String failure(int total,List<String> failed,String reason) {
  String names=failed.size()==1?failed.getFirst():String.join(", ",failed.subList(0,failed.size()-1))+" and "+failed.getLast();
  String why=reason!=null?reason:"There is no safe spot beside you right now. Step onto open ground and try again.";
  return (failed.size()<total?"Summoned "+(total-failed.size())+" of "+total+" companions. ":"")+names+" could not be summoned. "+why;
 }
 private PlayerBotSummonPolicy(){}
}
