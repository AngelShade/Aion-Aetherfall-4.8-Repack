package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
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

 /** Rechecked when delayed native item use completes; normal owned alts are not enrolled. */
 static boolean rewardIdle(PlayerBotSession session) {
  Player bot=session.bot(),owner=session.owner();
  return (session.generated() || session.world()) && !session.closing()
   && !bot.isDead() && !bot.isCasting() && !bot.isLooting() && !bot.isTrading()
   && !bot.isFlying() && !bot.isInAnyHide() && !bot.getController().isInCombat()
   && !bot.getMoveController().isInMove() && bot.getAggroList().stream().findAny().isEmpty()
   && (bot.getInteractionTask()==null || !bot.getInteractionTask().isInProgress())
   && !owner.isDead() && !owner.getController().isInCombat() && !owner.getMoveController().isInMove()
   && session.party().stream().allMatch(p -> !p.getController().isInCombat() && p.getAggroList().stream().findAny().isEmpty());
 }

 public static boolean canOpenReward(Player bot) {
  var session=PlayerBotService.getInstance().currentActor(bot);
  return session!=null && activeSubject(bot) && rewardIdle(session)
   && !bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ITEM_USE);
 }

 /** The timer must reacquire actor custody before changing inventory or racing a save/dismissal. */
 public static boolean finishIdleItem(Player bot,Runnable finish) {
  var service=PlayerBotService.getInstance();var session=service.currentActor(bot);
  if(session==null || !service.claimTransfer(session))return false;
  try {synchronized(session) {
   if(!service.current(session) || !activeSubject(bot) || !rewardIdle(session))return false;
   finish.run();return true;
  }}finally{service.completeTransfer(session);}
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

 /** Runs under the existing actor decision lease and session lock; one idle operation per admission. */
 static boolean idle(PlayerBotSession session) {
  Player bot=session.bot();
  if(!rewardIdle(session) || !PlayerBotWarehouse.idle(bot))return false;
  try {
   if(PlayerBotCollectionRenown.idleStipend(session))return true;
   PlayerBotMail.ensureLoaded(bot);
   if(bot.getInventory().getKinahItem()==null)bot.getInventory().increaseKinah(0);
   // Character-owned reward mail only; player correspondence remains private and untouched.
   for(var letter:bot.getMailbox().getLetters())if("Daeva Pass".equals(letter.getSenderName())) {
    String action=letter.getAttachedItem()!=null || letter.getAttachedKinah()!=0 ? "take" : "delete";
    if(action.equals("take") && bot.getInventory().isFull())continue;
    PlayerBotService.getInstance().checkpointForOwnerAction(session);
    PlayerBotMailStore.commit(PlayerBotMail.plan(bot,bot,action,letter.getObjectId(),0,0));return true;
   }
   var pass=SeasonPassService.companionSnapshot(bot);
   Object rows=pass.get("rewards");boolean available=false;
   if(rows instanceof java.util.List<?> rewards)for(Object row:rewards)
    if(row instanceof Map<?,?> reward && Boolean.TRUE.equals(reward.get("available"))){available=true;break;}
   if(available) {
    PlayerBotService.getInstance().checkpointForOwnerAction(session);
    SeasonPassService.companionAction(bot,Map.of("action","claimAll"),java.util.UUID.randomUUID().toString());return true;
   }
   if(worldPurchase(session,pass))return true;
   // A box with no grantable random reward must not indefinitely postpone earned Pass claims.
   for(var item:bot.getInventory().getItems()) {
    if(item.isEquipped() || !com.aionemu.gameserver.services.item.BundleUseService.isBundle(item))continue;
    if(com.aionemu.gameserver.model.templates.item.actions.DecomposeAction.openForCompanion(bot,item))return true;
   }
  }catch(SeasonPassService.CompanionHeld | PlayerBotMailStore.Held error) {
   InventoryDAO.quarantineMarketInventory(bot.getObjectId());session.markClosing();
   LoggerFactory.getLogger(PlayerBotPass.class).error("Automatic reward custody held for bot {}",bot.getObjectId(),error);
  }catch(IllegalArgumentException unavailable) {
   // Capacity, cooldown and eligibility are retried by the next bounded idle admission.
  }catch(Exception error) {
   LoggerFactory.getLogger(PlayerBotPass.class).warn("Automatic reward work deferred for bot {}",bot.getObjectId(),error);
  }
  return false;
 }

 /** Only a world bot buys for itself, one tier at a time, from what it earned above its reserve. Owned companions wait for their owner. */
 static long worldAllowance(int level,long wealth,long savedReserve) {
  long reserve=Math.max(Math.max(0,savedReserve),PlayerBotMarket.reserve(level,PlayerBotEconomyConfig.PASS_RESERVE_KINAH));
  return PlayerBotMarket.budget(wealth,reserve,PlayerBotEconomyConfig.PASS_BUDGET_PERCENT,Long.MAX_VALUE);
 }
 private static boolean worldPurchase(PlayerBotSession session,Map<String,Object> pass)throws Exception {
  if(!PlayerBotEconomyConfig.ECONOMY_ENABLED || !PlayerBotEconomyConfig.PASS_AUTONOMOUS || !session.autonomous() || !(pass.get("tier") instanceof Number held))return false;
  // Spend for a season the bot has actually played, rather than buying on a newly created or dormant identity.
  if(!(pass.get("level") instanceof Number progress) || progress.intValue()<1)return false;
  int tier=held.intValue();long cost=SeasonPassService.companionNextCost(tier);
  long allowance=worldAllowance(session.bot().getLevel(),session.bot().getInventory().getKinah(),PlayerBotQuestSync.state(session).reserve);
  if(cost<=0 || cost>allowance)return false;
  PlayerBotService.getInstance().checkpointForOwnerAction(session);
  SeasonPassService.companionAction(session.bot(),Map.of("action","purchase","tier",Integer.toString(tier+1)),java.util.UUID.randomUUID().toString());
  return true;
 }

 public static void loaded(Player bot) {
  if(!bot.isPlayerBot())return;
  try {SeasonPassService.refreshCompanionBenefits(bot);}
  catch(Exception error) {throw new IllegalStateException("Could not load this companion's own Season Pass benefits",error);}
 }
}
