/* Owner trade begin/accept/cancel purpose adapted from TradeStatusAction.cpp and
 * TradeAction.cpp, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; native Aion exchange packets, item rights and equipment rules. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.trade.Exchange;
import com.aionemu.gameserver.network.aion.serverpackets.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.services.ExchangeService;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.taskmanager.tasks.TemporaryTradeTimeTask;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import org.slf4j.LoggerFactory;

/** Owner gifts through the native exchange window; alts equip only explicitly donated upgrades. */
public final class PlayerBotTrade {
 private static final ThreadLocal<Boolean> INSIDE=ThreadLocal.withInitial(()->false);
 private static final ThreadLocal<Player> FINISHING=new ThreadLocal<>();
 private static final Map<Integer,Set<Integer>> RECEIVED=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> STARTED=new ConcurrentHashMap<>();
 static PlayerBotSession session(Player owner,Player bot) {
  if(owner==null || bot==null || !bot.isPlayerBot() || bot.getPlayerBotOwnerId()!=owner.getObjectId())return null;
  return PlayerBotService.getInstance().companions(owner).stream().filter(s->s.bot()==bot && !s.closing()).findFirst().orElse(null);
 }
 static boolean ready(Player owner,Player bot) {
  return owner!=null && bot!=null && owner.isOnline() && bot.isPlaying() && owner.isSpawned() && bot.isSpawned()
   && !owner.isDead() && !bot.isDead() && !owner.isInAnyHide() && !bot.isInAnyHide()
   && owner.getWorldId()==bot.getWorldId() && owner.getInstanceId()==bot.getInstanceId() && owner.getRace()==bot.getRace()
   && owner.getPlayerGroup()!=null && owner.getPlayerGroup()==bot.getPlayerGroup()
   && !owner.getController().isInCombat() && !bot.getController().isInCombat() && PositionUtil.isInRange(owner,bot,5)
   && !GameServer.isShuttingDownSoon();
 }
 public static boolean request(Player owner,Player bot) {
  if(!bot.isPlayerBot())return false;
  synchronized(PlayerBotService.getInstance()) {
   PlayerBotSession s=session(owner,bot);
   if(s==null){PacketSendUtility.sendMessage(owner,"You can trade only with your own active companion.");return true;}
   synchronized(s) {
    if(!ready(owner,bot) || owner.isTrading() || bot.isTrading() || bot.isCasting() || bot.isLooting()) {
     PacketSendUtility.sendMessage(owner,"Trade near your companion, outside combat, while it is ready.");return true;
    }
    bot.getMoveController().abortMove();
    ExchangeService.getInstance().registerExchange(owner,bot);
    if(bot.isTrading()){STARTED.put(bot.getObjectId(),System.currentTimeMillis());PacketSendUtility.sendMessage(owner,"Give "+bot.getName()+" items or Kinah, then lock and confirm. Suitable upgrades you give will be equipped.");}
   }
  }
  return true;
 }
 public static boolean participants(Player a,Player b) {
  Player owner=a.isPlayerBot()?b:a,bot=a.isPlayerBot()?a:b;
  return session(owner,bot)!=null && ready(owner,bot) && !a.isTrading() && !b.isTrading();
 }
 static PlayerBotSession trading(Player player) {
  if(player==null)return null;
  Exchange other=ExchangeService.getInstance().getCurrentParnterExchange(player);
  if(other==null)return null;
  Player peer=other.getActiveplayer();return player.isPlayerBot()?session(peer,player):session(player,peer);
 }
 /** Native packets already hold the client monitor. AI callbacks must never acquire it under the service lock. */
 public static boolean guard(Player player,String operation,int object,long count) {
  if(player==null || INSIDE.get())return false;
  Exchange other=ExchangeService.getInstance().getCurrentParnterExchange(player);
  if(!player.isPlayerBot() && (other==null || !other.getActiveplayer().isPlayerBot()))return false;
  synchronized(PlayerBotService.getInstance()) {
   PlayerBotSession s=trading(player);if(s==null) {
    INSIDE.set(true);try{ExchangeService.getInstance().cancelExchange(player);}finally{INSIDE.remove();}return true;
   }
   synchronized(s) {
    INSIDE.set(true);
    try {
     var service=ExchangeService.getInstance();
     if(!operation.equals("cancel") && !ready(s.owner(),s.bot())){service.cancelExchange(player);return true;}
     switch(operation) {
      case "item" -> service.addItem(player,object,count);case "kinah" -> service.addKinah(player,count);
      case "lock" -> service.lockExchange(player);case "confirm" -> service.confirmExchange(player);case "cancel" -> service.cancelExchange(player);
      default -> throw new IllegalArgumentException(operation);
     }
    }finally{INSIDE.remove();}
   }
  }
  return true;
 }
 public static void locked(Player player) {
  PlayerBotSession s=trading(player);if(s!=null && player==s.owner())ExchangeService.getInstance().lockExchange(s.bot());
 }
 static boolean rights(Player owner,Player bot,Item item) {
  return item!=null && !item.isEquipped() && item.getItemLocation()==0 && (item.getPackCount()>0 || item.isTradeable()
   || TemporaryTradeTimeTask.getInstance().canTrade(item,bot.getObjectId())
   || item.isLegionTradeable() && owner.getLegion()!=null && owner.getLegion().equals(bot.getLegion()));
 }
 public static boolean confirm(Player owner) {
  PlayerBotSession s=trading(owner);if(s==null)return owner!=null && owner.isPlayerBot();
  if(owner!=s.owner())return true;
  var service=ExchangeService.getInstance();Exchange botOffer=service.getCurrentParnterExchange(owner),offer=service.getCurrentParnterExchange(s.bot());
  if(offer==null || botOffer==null || !offer.isLocked() || !botOffer.isLocked())return true;
  if(!ready(owner,s.bot()) || !botOffer.getItems().isEmpty() || botOffer.getKinahCount()!=0 || offer.getItems().size()>s.bot().getInventory().getFreeSlots()) {
   PacketSendUtility.sendMessage(owner,"Companion trade cancelled: check distance, combat and cube space.");service.cancelExchange(owner);return true;
  }
  for(var e:offer.getItems().values())if(!rights(owner,s.bot(),owner.getInventory().getItemByObjId(e.getItemObjId()))) {
   PacketSendUtility.sendMessage(owner,"An offered item can no longer be traded.");service.cancelExchange(owner);return true;
  }
  try {PlayerBotTradeStore.commit(owner,s.bot(),offer);}
  catch(PlayerBotTradeStore.Indeterminate error){hold(s,error);return true;}
  catch(Exception error) {
   LoggerFactory.getLogger(PlayerBotTrade.class).error("Companion trade refused before ownership commit",error);
   PacketSendUtility.sendMessage(owner,"Companion trade could not be saved; your items were not transferred.");service.cancelExchange(owner);return true;
  }
  // Ownership is durable before publishing success. Native storage and quest notifications follow it.
  List<Item> gifts=new ArrayList<>();
  try {
  for(var e:offer.getItems().values()) {
   Item real=owner.getInventory().getItemByObjId(e.getItemObjId()),given=e.getItem();
   if(real.getItemCount()==e.getItemCount()) {
    if(given!=real)IDFactory.getInstance().releaseId(given.getObjectId());
    given=real;owner.getInventory().remove(real);e.setItem(real);
   } else {real.setItemCount(real.getItemCount()-e.getItemCount());real.setPersistentState(PersistentState.UPDATED);}
   given.setEquipmentSlot(0);if(given.getPackCount()>0)given.setPackCount(-given.getPackCount());
   if(s.bot().getInventory().add_CharacterTransfer(given)==null)throw new IllegalStateException("Committed companion gift refresh failed");
   given.setPersistentState(PersistentState.UPDATED);gifts.add(given);
  }
  if(offer.getKinahCount()>0) {
   Item from=owner.getInventory().getKinahItem(),to=s.bot().getInventory().getKinahItem();
   from.setItemCount(from.getItemCount()-offer.getKinahCount());to.setItemCount(to.getItemCount()+offer.getKinahCount());
   from.setPersistentState(PersistentState.UPDATED);to.setPersistentState(PersistentState.UPDATED);
  }
  }catch(RuntimeException error){hold(s,error);return true;}
  // Never release split IDs already persisted in the recipient cube.
  FINISHING.set(owner);try{service.cancelExchange(owner);}finally{FINISHING.remove();}STARTED.remove(s.bot().getObjectId());
  RECEIVED.computeIfAbsent(s.bot().getObjectId(),id->ConcurrentHashMap.newKeySet()).addAll(gifts.stream().map(Item::getObjectId).toList());
  PacketSendUtility.sendPacket(owner,new SM_EXCHANGE_CONFIRMATION(0));
  // Each notification may execute native quest scripts; failure must not undo a committed gift.
  try {
   for(Item item:gifts) {
    PacketSendUtility.sendPacket(owner,new SM_DELETE_ITEM(item.getObjectId()));
    Item source=owner.getInventory().getItemByObjId(offer.getItems().values().stream().filter(e->e.getItem()==item).map(e->e.getItemObjId()).findFirst().orElse(0));
    if(source!=null)PacketSendUtility.sendPacket(owner,new SM_INVENTORY_UPDATE_ITEM(owner,source,ItemPacketService.ItemUpdateType.INC_PLAYER_EXCHANGE_GET_BACK));
    QuestEngine.getInstance().onItemGet(s.bot(),item.getItemId());
   }
   if(owner.getInventory().getKinahItem()!=null)PacketSendUtility.sendPacket(owner,new SM_INVENTORY_UPDATE_ITEM(owner,owner.getInventory().getKinahItem(),ItemPacketService.ItemUpdateType.INC_PLAYER_EXCHANGE_GET_BACK));
   PacketSendUtility.sendMessage(owner,s.bot().getName()+" received your trade. Suitable upgrades will be equipped when safe.");
  }catch(RuntimeException error){LoggerFactory.getLogger(PlayerBotTrade.class).error("Committed companion trade notification failed",error);}
  try{equip(s);}catch(RuntimeException error){LoggerFactory.getLogger(PlayerBotTrade.class).error("Received gear remains available after equipment decision failure",error);}
  return true;
 }
 static void hold(PlayerBotSession s,Exception error) {
  // An uncertain commit/failed projection is never treated as a rollback. Hold
  // both inventories and keep all IDs until an operator verifies durable custody.
  InventoryDAO.quarantineMarketInventory(s.owner().getObjectId());InventoryDAO.quarantineMarketInventory(s.bot().getObjectId());
  FINISHING.set(s.owner());try{ExchangeService.getInstance().cancelExchange(s.owner());}finally{FINISHING.remove();}
  s.markClosing();LoggerFactory.getLogger(PlayerBotTrade.class).error("Companion trade held for custody verification; no item IDs released",error);
  PacketSendUtility.sendMessage(s.owner(),"Trade persistence needs custody verification; reconnect after the server log is reviewed.");
  if(s.owner().getClientConnection()!=null)s.owner().getClientConnection().close();
 }
 static void equip(PlayerBotSession s) {
  Set<Integer> ids=RECEIVED.get(s.bot().getObjectId());if(ids==null || ids.isEmpty())return;
  Player bot=s.bot();if(s.closing() || bot.isTrading() || bot.isDead() || bot.isCasting() || bot.isLooting() || bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ITEM_USE) || bot.getController().isInCombat() || s.owner().getController().isInCombat())return;
  for(int id:List.copyOf(ids)) {
   Item item=bot.getInventory().getItemByObjId(id);if(item==null){ids.remove(id);continue;}
   if(!item.isIdentified() && PlayerBotGearPolicy.eligible(bot,item.getItemTemplate())) {
    bot.getMoveController().abortMove();PlayerBotGearPolicy.equip(s,new PlayerBotEquipment.Upgrade(item,0,0));return;
   }
   var upgrade=PlayerBotEquipment.upgrades(bot,s.combatRole()).stream().filter(u->u.item()==item).findFirst().orElse(null);
   if(upgrade!=null) {
    bot.getMoveController().abortMove();
    boolean accepted=PlayerBotGearPolicy.equip(s,upgrade);
    // Native soul-binding is an asynchronous ITEM_USE task. Retain this ID
    // until it leaves the cube or the next safe attempt, and do not start another.
    if(bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ITEM_USE))return;
    if(!accepted)PacketSendUtility.sendMessage(s.owner(),bot.getName()+" kept "+item.getItemName()+" in its cube: native equipment rules prevented equipping it.");
   } else PacketSendUtility.sendMessage(s.owner(),bot.getName()+" kept "+item.getItemName()+" in its cube: it is not a usable equipment upgrade.");
   ids.remove(id);
  }
  if(ids.isEmpty())RECEIVED.remove(bot.getObjectId());
 }
 static boolean tick(PlayerBotSession s) {
  if(s.bot().isTrading()) {
   if(!ready(s.owner(),s.bot()) || System.currentTimeMillis()-STARTED.getOrDefault(s.bot().getObjectId(),0L)>120000)ExchangeService.getInstance().cancelExchange(s.bot());
   return s.bot().isTrading();
  }
  equip(s);
  Set<Integer> pending=RECEIVED.get(s.bot().getObjectId());
  return pending!=null && !pending.isEmpty() && s.bot().getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ITEM_USE);
 }
 public static void closed(Player player) {STARTED.remove(player.getObjectId());}
 public static boolean finishing(Player player) {return player!=null && FINISHING.get()==player;}
 static void close(PlayerBotSession s) {ExchangeService.getInstance().cancelExchange(s.bot());STARTED.remove(s.bot().getObjectId());RECEIVED.remove(s.bot().getObjectId());}
 private PlayerBotTrade() {}
}
