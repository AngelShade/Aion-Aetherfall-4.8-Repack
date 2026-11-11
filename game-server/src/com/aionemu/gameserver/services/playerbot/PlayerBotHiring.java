package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dao.PlayerBotHiringDAO;
import com.aionemu.gameserver.dao.PlayerBotHiringDAO.Contract;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INVENTORY_UPDATE_ITEM;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import org.slf4j.LoggerFactory;

/** One paid term borrows a native world character; it never transfers ownership or creates a companion identity. */
public final class PlayerBotHiring {
 /** STR_DURATION_ONLYMIN "%0m": the client has no free-text question, and this one lets the prompt end in the term. */
 private static final int QUESTION=900853;
 private static final int BUSY_RETRIES=20;
 private static final Map<Integer,String> ASKED=new ConcurrentHashMap<>();
 private static final Map<Integer,Contract> ACTIVE=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> PREVIEW_AT=new ConcurrentHashMap<>();
 private static final AtomicInteger PREVIEWS=new AtomicInteger();
 private static final ThreadLocal<Contract> ADMISSION=new ThreadLocal<>();
 private static volatile boolean prepared;
 private PlayerBotHiring() {}

 public static synchronized void initialize() {
  if(prepared)return;
  try(var c=DatabaseFactory.getConnection()){PlayerBotHiringDAO.prepare(c);prepared=true;}
  catch(SQLException error){throw new IllegalStateException("Cannot prepare native world-helper contracts",error);}
 }
 private static Player bot(String name) {
  Player bot=World.getInstance().getPlayer(name);
  if(bot==null || !bot.isWorldBot())throw new IllegalArgumentException("Choose an available world helper of your faction.");
  return bot;
 }
 /** Owner, 9 Nov: one catch-all sentence ("road residents and committed groups keep their current work") did not say
  * why a bot refused. Each reason now gets its own plain answer; null means the bot can be hired. */
 static String unavailableReason(boolean ownerUnavailable,boolean otherFaction,boolean botUnavailable,boolean grouped,boolean busy,
  boolean roadDuty,boolean findGroup,String name) {
  if(ownerUnavailable)return "You can't hire a helper right now.";
  if(otherFaction)return "You can only hire helpers of your own faction.";
  if(botUnavailable)return name+" can't be hired right now.";
  if(roadDuty)return name+" is on duty guarding or trading on the road and won't leave it to join you.";
  if(findGroup)return name+" has already promised to help another group.";
  if(grouped)return name+" is already in a group.";
  if(busy)return name+" is busy right now. Try again in a moment.";
  return null;
 }
 private static PlayerBotSession available(Player owner,Player bot,boolean confirm) {
  var service=PlayerBotService.getInstance();var actor=service.currentActor(bot);
  if(!PlayerBotEconomyConfig.HIRING_ENABLED || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || GameServer.isShuttingDownSoon())
   throw new IllegalArgumentException("World-helper hiring is unavailable.");
  String busy=unavailableReason(owner.isPlayerBot() || !owner.isOnline() || !owner.isSpawned() || owner.getClientConnection()==null,
   owner.getRace()!=bot.getRace(),actor==null || !actor.autonomous() || bot.isDead() || !bot.isSpawned(),bot.isInTeam(),
   bot.isTrading() || bot.getFlightPath()!=null,PlayerBotRoadDuty.assigned(bot),PlayerBotFindGroup.committed(bot),bot.getName());
  if(busy!=null)throw new IllegalArgumentException(busy);
  if(confirm && !PlayerBotWarehouse.idle(owner))
   throw new IllegalArgumentException("Hire while you are alive, on the ground and outside combat, casting, trade, looting and item use.");
  // Helpers are nearly always busy and join fights already under way; only their inventory must hold still.
  if(confirm && (bot.isLooting() || bot.getController().hasScheduledTask(TaskId.ITEM_USE)))throw new Busy();
  if(owner.getBlockList().contains(bot.getObjectId()) || bot.getBlockList().contains(owner.getObjectId()))
   throw new IllegalArgumentException("This character's block settings prevent hiring.");
  PlayerBotFindGroup.validateHire(owner,bot);
  return actor;
 }
 private static int[] percentages() {
  String[] values=PlayerBotEconomyConfig.HIRING_RANK_PERCENT.split(",");
  if(values.length!=5)throw new IllegalStateException("World-helper prices need five combat-rank percentages");
  int[] out=new int[5];for(int i=0;i<5;i++){out[i]=Integer.parseInt(values[i].trim());if(out[i]<1 || out[i]>10000)throw new IllegalStateException("Invalid world-helper rank percentage");}
  return out;
 }
 public static long price(int level,int rank) {
  if(level<1 || level>65 || rank < -1 || rank>4 || PlayerBotEconomyConfig.HIRING_MINIMUM_KINAH<1 || PlayerBotEconomyConfig.HIRING_LEVEL_KINAH<0)
   throw new IllegalArgumentException("Invalid world-helper price configuration");
  try {
   long base=Math.max(PlayerBotEconomyConfig.HIRING_MINIMUM_KINAH,Math.multiplyExact((long)level*level,PlayerBotEconomyConfig.HIRING_LEVEL_KINAH));
   return Math.addExact(Math.multiplyExact(base,rank<0?100:percentages()[rank]),99)/100;
  }catch(ArithmeticException error){throw new IllegalStateException("World-helper price overflows native Kinah",error);}
 }
 static String offerText(Player bot) {
  try {
   var profile=PlayerBotGrowth.profile(bot);int rank=profile.traitsAssigned()?profile.combatRank().ordinal():-1;
   String role=switch(PlayerBotRules.roleFor(bot.getPlayerClass())){case TANK->"tank";case HEALER->"healer";case SUPPORT->"support";default->"DPS";};
   return price(bot.getLevel(),rank)+" Kinah/"+PlayerBotEconomyConfig.HIRING_DURATION_MINUTES+"m Lv"+bot.getLevel()+" "+role+" Hire";
  }catch(java.io.IOException error){throw new IllegalStateException("Cannot verify helper combat rank",error);}
 }
 private static Contract quote(Player owner,String name,String id) {
  initialize();var bot=bot(name);var actor=available(owner,bot,false);
  synchronized(actor) {
   available(owner,bot,false);
   try {
    var profile=PlayerBotGrowth.profile(bot);int rank=profile.traitsAssigned()?profile.combatRank().ordinal():-1;
    int minutes=PlayerBotEconomyConfig.HIRING_DURATION_MINUTES,seconds=PlayerBotEconomyConfig.HIRING_QUOTE_SECONDS;
    if(minutes<1 || minutes>10080 || seconds<10 || seconds>3600)throw new IllegalStateException("World-helper duration/quote expiry is invalid");
    long now=System.currentTimeMillis();
    var quote=new Contract(id,owner.getAccount().getId(),owner.getObjectId(),bot.getObjectId(),price(bot.getLevel(),rank),
     Math.multiplyExact(minutes,60000L),Math.addExact(now,seconds*1000L),rank<0?"UNRANKED":profile.combatRank().name(),"QUOTED",0,0,"");
    try(var c=DatabaseFactory.getConnection()){PlayerBotHiringDAO.insert(c,quote);}
    return quote;
   }catch(java.io.IOException|SQLException error){throw new IllegalStateException("Could not prepare helper terms; no Kinah was charged",error);}
  }
 }
 private static Map<String,Object> view(Contract q,String name) {
  var out=new LinkedHashMap<String,Object>();
  out.put("contract",q.id());out.put("name",name);out.put("bot",q.bot());out.put("fee",q.fee());out.put("durationMinutes",q.duration()/60000);
  out.put("quoteExpires",q.quoteUntil());out.put("rank",q.rank());out.put("state",q.state());out.put("ends",q.ends());
  return out;
 }
 /** Text before the native "m" of QUESTION, so the box reads "... 2,250 Kinah / 60m". */
 static String prompt(String name,String identity,long fee,int minutes){return prompt(identity.isEmpty()?name:name+" ("+identity+")",fee,minutes);}
 static String prompt(String name,long fee,int minutes) {
  return String.format(Locale.US,"Hire %s? One payment to the helper. No renewal or refund after joining. Disconnect, dismiss or leave ends it. Failed join costs nothing. %,d Kinah / %d",name,fee,minutes);
 }
 /** Native Invite opens the Hire question. SQL is queued outside native team/service monitors; only Yes joins or pays. */
 public static void offer(Player owner,Player bot) {
  long now=System.currentTimeMillis();
  if(ASKED.containsKey(owner.getObjectId())){PacketSendUtility.sendMessage(owner,"Answer your open Hire window or wait for its payment to finish first.");return;}
  if(now<PREVIEW_AT.getOrDefault(owner.getObjectId(),0L))return;
  PREVIEW_AT.put(owner.getObjectId(),now+2000);
  if(PREVIEW_AT.size()>4096)PREVIEW_AT.entrySet().removeIf(e->e.getValue()<now);
  if(PREVIEWS.incrementAndGet()>64){PREVIEWS.decrementAndGet();PacketSendUtility.sendMessage(owner,"Helper quotes are busy. Press Invite again shortly.");return;}
  try{ThreadPoolManager.getInstance().execute(()->{try{ask(owner,bot.getName());}
   catch(RuntimeException error){PacketSendUtility.sendMessage(owner,error.getMessage());}
   finally{PREVIEWS.decrementAndGet();}});}
  catch(RuntimeException error){PREVIEWS.decrementAndGet();PacketSendUtility.sendMessage(owner,"Helper quotes could not be queued. Press Invite again shortly.");}
 }
 private static void settle(Player owner,String name,String id,int retries) {
  boolean retry=false;
  try{PacketSendUtility.sendMessage(owner,confirm(owner,name,id));}
  catch(Busy busy) {
   if(retries>0){ThreadPoolManager.getInstance().schedule(()->settle(owner,name,id,retries-1),500);retry=true;}
   else PacketSendUtility.sendMessage(owner,name+" stayed busy, so no Kinah was charged. Press Invite again.");
  }
  catch(RuntimeException error){PacketSendUtility.sendMessage(owner,error.getMessage());}
  finally{if(!retry)ASKED.remove(owner.getObjectId(),id);}
 }
 private static void ask(Player owner,String name) {
  String id=UUID.randomUUID().toString();int key=owner.getObjectId();
  // Reserve before SQL: repeated Invite presses must not create unbounded quotes while one box/payment is open.
  if(ASKED.putIfAbsent(key,id)!=null){PacketSendUtility.sendMessage(owner,"Answer your open Hire window or wait for its payment to finish first.");return;}
  Contract quote;
  try{quote=quote(owner,name,id);}catch(RuntimeException error){ASKED.remove(key,id);throw error;}
  var responded=new AtomicBoolean();
  var handler=new RequestResponseHandler<Creature>(null) {
   @Override public void handle(Player responder,int response) {
    if(response==1)acceptRequest(null,responder);else denyRequest(null,responder);
   }
   @Override public void acceptRequest(Creature requester,Player responder) {
    if(!responded.compareAndSet(false,true) || !id.equals(ASKED.get(key)))return;
    // CM_QUESTION_RESPONSE must not wait on the payment's SQL or actor claim.
    try{ThreadPoolManager.getInstance().execute(()->settle(responder,name,id,BUSY_RETRIES));}
    catch(RuntimeException error){ASKED.remove(key,id);PacketSendUtility.sendMessage(responder,"Hire could not be queued. No Kinah was charged. Press Invite again.");}
   }
   @Override public void denyRequest(Creature requester,Player responder) {
    if(!responded.compareAndSet(false,true))return;
    if(ASKED.remove(key,id) && responder.isOnline())PacketSendUtility.sendMessage(responder,"Hire cancelled. No Kinah was charged.");
   }
  };
  if(!owner.isOnline() || !owner.getResponseRequester().putRequest(QUESTION,handler)) {
   ASKED.remove(key,id);PacketSendUtility.sendMessage(owner,"Answer your open Hire window first.");return;
  }
  // A window answered after its quote expires charges nothing: confirm() refuses it.
  // Keep the old response handler until the client answers/closes it. Reusing the same native question ID
  // on a timer would let a late Yes for the old window accept a different helper's newer quote.
  try {
   ThreadPoolManager.getInstance().schedule(()->{
    if(responded.get() || !id.equals(ASKED.get(key)))return;
    // A disconnected owner can never answer, and must not stay locked out after relogging.
    if(!owner.isOnline()){if(responded.compareAndSet(false,true))ASKED.remove(key,id);return;}
    PacketSendUtility.sendMessage(owner,"Your Hire quote expired. Close its window and press Invite again for current terms. No Kinah was charged.");
   },Math.max(1,quote.quoteUntil()-System.currentTimeMillis()));
   PacketSendUtility.sendPacket(owner,new SM_QUESTION_WINDOW(QUESTION,0,0,prompt(name,PlayerBotGrowth.identityText(bot(name)),quote.fee(),(int)(quote.duration()/60000))));
  }catch(RuntimeException error) {
   if(responded.compareAndSet(false,true) && ASKED.remove(key,id))owner.getResponseRequester().remove(QUESTION);
   throw error;
  }
 }
 public static boolean confirmedAdmission(Player owner,Player bot) {
  var quote=ADMISSION.get();return quote!=null && quote.owner()==owner.getObjectId() && quote.account()==owner.getAccount().getId() && quote.bot()==bot.getObjectId();
 }
 public static boolean paidControl(Player owner,Player bot) {
  var term=ACTIVE.get(bot.getObjectId());return term!=null && term.owner()==owner.getObjectId() && term.account()==owner.getAccount().getId() && term.ends()>System.currentTimeMillis();
 }
 public static String confirm(Player owner,String name,String id) {
  if(id==null || !id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Choose a current helper quote and confirm Hire.");
  initialize();var bot=bot(name);var service=PlayerBotService.getInstance();Object guard=owner.getClientConnection();
  if(guard==null)throw new IllegalArgumentException("Reconnect and review the helper terms.");
  synchronized(guard) {
   if(owner.getClientConnection()!=guard)throw new IllegalArgumentException("Reconnect and review the helper terms.");
   // Replayed confirmations remain receipts, even after the term or native party has ended.
   try(var c=DatabaseFactory.getConnection()) {
    var prior=PlayerBotHiringDAO.get(c,id,false);
    if(prior!=null && prior.account()==owner.getAccount().getId() && prior.owner()==owner.getObjectId() && prior.bot()==bot.getObjectId() && prior.started()>0)
     return "This helper contract was already paid once; state "+prior.state()+". No additional Kinah was charged.";
   }catch(SQLException error){throw new IllegalStateException("Could not verify the helper receipt; no new payment was attempted",error);}
   var actor=available(owner,bot,true);
   if(!service.claimTransfer(actor))throw new Busy();
   try {synchronized(actor){return payAndJoin(owner,bot,actor,id);}}
   finally{service.completeTransfer(actor);}
  }
 }
 private static String payAndJoin(Player owner,Player bot,PlayerBotSession actor,String id) {
  var service=PlayerBotService.getInstance();available(owner,bot,true);
  if(!service.current(actor))throw new IllegalArgumentException("The helper changed activity; review another quote.");
  try{service.checkpointForOwnerAction(actor);}catch(SQLException error){throw new IllegalStateException("The helper's native progress could not be checkpointed. No payment was attempted.",error);}
  if(!InventoryDAO.store(owner))throw new IllegalStateException("Your Inventory could not be checkpointed. No helper payment was attempted.");
  if(bot.getInventory().getKinahItem()==null)bot.getInventory().increaseKinah(0);
  boolean joined=false,committed=false;Contract term=null;
  long humanBefore=owner.getInventory().getKinah(),botBefore=bot.getInventory().getKinah();
  try(var c=DatabaseFactory.getConnection()) {
   c.setAutoCommit(false);
   try {
    var q=PlayerBotHiringDAO.get(c,id,true);long now=System.currentTimeMillis();
    if(q==null || q.account()!=owner.getAccount().getId() || q.owner()!=owner.getObjectId() || q.bot()!=bot.getObjectId() || !q.state().equals("QUOTED") || q.quoteUntil()<now)
     throw new IllegalArgumentException("This helper quote expired or changed. Press Invite in Find Group again.");
    PlayerBotHiringDAO.lockBot(c,bot.getObjectId());
    var previous=PlayerBotHiringDAO.active(c,bot.getObjectId());
    if(previous!=null)throw new IllegalArgumentException("The helper still has a paid term in native custody. No new payment was attempted; review its prior receipt.");
    if(q.fee()>humanBefore)throw new IllegalArgumentException("You do not have enough Inventory Kinah for this helper.");
    long total=PlayerBotTradeStore.balance(botBefore,q.fee());
    var inventory=InventoryDAO.storeCompanionInventory(c,bot);var metadata=PlayerBotMetadata.pending(bot.getAccount().getId(),bot.getObjectId());PlayerBotMetadata.store(c,metadata);
    PlayerBotTradeStore.currency(c,owner,owner.getInventory().getKinahItem(),humanBefore,humanBefore-q.fee());
    PlayerBotTradeStore.currency(c,bot,bot.getInventory().getKinahItem(),botBefore,total);
    ADMISSION.set(q);
    if(!service.admitHiredWorldBot(owner,bot) || !PlayerBotTeams.same(owner,bot))throw new IllegalArgumentException("Native party admission failed. The full payment was rolled back.");
    joined=true;
    // A paid term starts at the actual native join, after the custody writes and admission checks.
    term=PlayerBotHiringDAO.activate(c,q,System.currentTimeMillis());
    try{c.commit();}catch(SQLException uncertain){throw new Held(uncertain);}
    committed=true;
    try {
     ACTIVE.put(bot.getObjectId(),term);
     PlayerBotMetadata.committed(metadata);InventoryDAO.companionInventoryCommitted(inventory);
     owner.getInventory().getKinahItem().setItemCount(humanBefore-q.fee());owner.getInventory().getKinahItem().setPersistentState(PersistentState.UPDATED);
     bot.getInventory().getKinahItem().setItemCount(total);bot.getInventory().getKinahItem().setPersistentState(PersistentState.UPDATED);
     PacketSendUtility.sendPacket(owner,new SM_INVENTORY_UPDATE_ITEM(owner,owner.getInventory().getKinahItem(),ItemPacketService.ItemUpdateType.DEC_KINAH_BUY));
    }catch(RuntimeException projection){throw new Held(projection);}
    try{PlayerBotFindGroup.arrived(owner,bot);}catch(RuntimeException notice){LoggerFactory.getLogger(PlayerBotHiring.class).warn("Committed helper hire kept its native party; arrival notice will need a normal summon for {}",bot.getName(),notice);}
    return "Hired "+bot.getName()+" for "+q.fee()+" Kinah and "+q.duration()/60000+" minutes. No automatic renewal or post-join refund.";
   }catch(SQLException|RuntimeException error) {
    if(error instanceof Held)throw error;
    if(!committed) {
     try{c.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);throw new Held(error);}
     InventoryDAO.markCompanionInventoryDirty(bot);
     if(joined || PlayerBotTeams.same(owner,bot))try{service.rollbackHiredWorldBot(owner,bot);}catch(RuntimeException restore){error.addSuppressed(restore);throw new Held(error);}
    }
    throw error;
   }finally{ADMISSION.remove();}
  }catch(Held error) {
   var current=service.currentActor(bot);
   InventoryDAO.quarantineMarketInventory(owner.getObjectId());InventoryDAO.quarantineMarketInventory(bot.getObjectId());
   if(current!=null){current.markClosing();}
   LoggerFactory.getLogger(PlayerBotHiring.class).error("World-helper payment/admission held for native custody verification; owner={}, bot={}, contract={}",owner.getObjectId(),bot.getObjectId(),id,error);
   if(owner.getClientConnection()!=null)owner.getClientConnection().close();
   throw new IllegalStateException("Helper payment needs custody verification. Both wallets and the helper are held; review the server log before retrying.",error);
  }catch(SQLException error){
   // A pooled connection can fail while closing after a successful commit/projection. Keep the receipt
   // truthful; telling the owner that payment rolled back would invite a second, unrelated hire.
   if(committed && term!=null){LoggerFactory.getLogger(PlayerBotHiring.class).warn("Committed helper hire connection cleanup failed for {}",term.id(),error);return "Hired "+bot.getName()+" for "+term.fee()+" Kinah and "+term.duration()/60000+" minutes. No automatic renewal or post-join refund.";}
   throw new IllegalStateException("Helper admission rolled back; no payment was published",error);
  }
 }
 /** The helper's actor or inventory is briefly occupied; nothing was paid, so Yes can safely retry. */
 private static final class Busy extends IllegalArgumentException {Busy(){super("The helper is busy.");}}
 private static final class Held extends SQLException {Held(Throwable cause){super("World-helper custody is uncertain",cause);}}
 /** Expiry is observed by existing actor work. Release waits for the native safe transition, never a combat interrupt. */
 public static boolean shouldRelease(PlayerBotSession session,long now) {
  if(!session.world() || session.autonomous())return false;
  var term=ACTIVE.get(session.bot().getObjectId());
  boolean queueEntry=com.aionemu.gameserver.services.AutoGroupService.getInstance().companionEntryPending(session.owner(),session.bot());
  boolean ended=term!=null ? term.owner()!=session.owner().getObjectId() || term.ends()<=now || !session.owner().isOnline() || !PlayerBotTeams.same(session.owner(),session.bot()) && !queueEntry
   : !queueEntry && !PlayerBotFindGroup.freeHelpControl(session.owner(),session.bot()) && !PlayerBotFindGroup.holdsForRelog(session.owner(),session.bot(),now);
  var bot=session.bot();
  boolean safe=!bot.isCasting() && !bot.isLooting() && !bot.isTrading() && !bot.isFlying() && bot.getFlightPath()==null
   && !bot.getController().isInCombat() && !bot.getController().hasScheduledTask(TaskId.ITEM_USE)
   && !bot.getController().hasScheduledTask(TaskId.ACTION_ITEM_NPC)
   && (bot.getInteractionTask()==null || !bot.getInteractionTask().isInProgress());
  return ended && safe;
 }
 public static void released(Player bot,String reason) {
  // Failed pre-commit admission is unwound by its caller's transaction. Opening another SQL connection
  // here could wait on the very contract/bot locks that caller still holds.
  var admission=ADMISSION.get();if(admission!=null && admission.bot()==bot.getObjectId())return;
  var term=ACTIVE.get(bot.getObjectId());if(term==null)return;
  try(var c=DatabaseFactory.getConnection()){PlayerBotHiringDAO.end(c,bot.getObjectId(),reason);ACTIVE.remove(bot.getObjectId(),term);}
  catch(SQLException error){throw new IllegalStateException("Could not persist the ended helper term; do not re-admit without recovery",error);}
 }
 /** Server restart also disconnects the controller, so an old ACTIVE term cannot become a free renewed hire. */
 public static void loaded(Player bot) {
  if(!bot.isWorldBot())return;initialize();
  try(var c=DatabaseFactory.getConnection()){PlayerBotHiringDAO.end(c,bot.getObjectId(),"DISCONNECTED");ACTIVE.remove(bot.getObjectId());}
  catch(SQLException error){throw new IllegalStateException("Could not recover the world helper's prior contract",error);}
 }
 public static Map<String,Object> state(Player owner,Player bot) {
  var term=ACTIVE.get(bot.getObjectId());return term!=null && term.owner()==owner.getObjectId()?view(term,bot.getName()):Map.of();
 }
}
