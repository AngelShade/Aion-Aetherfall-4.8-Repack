package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.SeasonPassService;
import org.slf4j.LoggerFactory;

/** Explicit character-owned pass subject; it never borrows a human client or account entitlement. */
public final class PlayerBotPass {
 private PlayerBotPass() {}

 /** Constant-time actor admission for real native activity callbacks. Normal owned alts keep their existing path. */
 public static boolean activeSubject(Player player) {
  if(player==null || !player.isPlayerBot() || !player.isPlaying() || !player.isSpawned())return false;
  var actor=PlayerBotService.getInstance().currentActor(player);
  return actor!=null && (actor.generated() || actor.world());
 }

 private static void check(PlayerBotSession session,Player owner,boolean mutate) {
  if(session.owner()!=owner || !PlayerBotService.getInstance().current(session) || !activeSubject(session.bot()))
   throw new IllegalArgumentException("Choose an active generated companion and refresh its details.");
  if(mutate && (!PlayerBotWarehouse.privateOwner(session) || !session.generated()))
   throw new IllegalArgumentException("World bots own their pass and purchases. You may manage only your own generated companion's pass.");
  if(mutate) {
   String reason=PlayerBotItemTransfer.blocker(session);
   if(!reason.isEmpty())throw new IllegalArgumentException(reason);
  }
 }

 public static Map<String,Object> snapshot(Player owner,String name)throws Exception {
  var session=PlayerBotService.getInstance().find(owner,name);
  synchronized(session) {
   check(session,owner,false);
   var state=SeasonPassService.companionSnapshot(session.bot());
   state.put("canManage",PlayerBotWarehouse.privateOwner(session) && session.generated());
   state.put("paymentOwner",session.bot().getName());
   return state;
  }
 }

 public static String action(Player owner,String name,Map<String,String> args,String request)throws Exception {
  if(request==null || !request.matches("[A-Za-z0-9_-]{20,64}"))throw new IllegalArgumentException("Refresh the companion pass before selecting a reward or upgrade.");
  var service=PlayerBotService.getInstance();var session=service.find(owner,name);
  if(!service.claimTransfer(session))throw new IllegalArgumentException("This companion is busy; retry when its current action finishes.");
  try {synchronized(session) {
   check(session,owner,true);
   session.bot().getMoveController().abortMove();
   service.checkpointForOwnerAction(session);
   try {return SeasonPassService.companionAction(session.bot(),args,request);}
   catch(SeasonPassService.CompanionHeld error) {
    InventoryDAO.quarantineMarketInventory(session.bot().getObjectId());session.markClosing();
    LoggerFactory.getLogger(PlayerBotPass.class).error("Companion pass custody held for bot {}; native identities and reward item IDs retained",session.bot().getObjectId(),error);
    throw new IllegalStateException("This companion's pass payment or reward needs custody verification. The companion is held; see the server log before retrying.",error);
   }
  }}finally{service.completeTransfer(session);}
 }

 public static void loaded(Player bot) {
  if(!bot.isPlayerBot())return;
  try {SeasonPassService.refreshCompanionBenefits(bot);}
  catch(Exception error) {throw new IllegalStateException("Could not load this companion's own Season Pass benefits",error);}
 }
}
