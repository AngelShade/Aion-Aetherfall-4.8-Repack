package com.aionemu.gameserver.services.playerbot;

import java.sql.SQLException;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.services.mail.MailService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import org.slf4j.LoggerFactory;

/**
 * Companions window item transfer: one whole tradeable stack or a Kinah amount between the owner and an active private
 * companion, anywhere outside combat. Same custody as owner gifts (PlayerBotTradeStore): both inventories are checkpointed,
 * then one transaction moves the row; an unacknowledged commit holds both inventories. Alts outside the party receive items
 * through the native express mail with its normal rules and postage.
 */
public final class PlayerBotItemTransfer {
 public static boolean transferable(Item item) {
  return item != null && item.getObjectId() > 0 && item.getItemCount() > 0 && !item.isEquipped() && item.getItemLocation() == 0
   && !item.getItemTemplate().isKinah() && (item.getPackCount() > 0 || item.isTradeable());
 }
 static String blocker(PlayerBotSession session) {
  if (!PlayerBotWarehouse.privateOwner(session)) return "Item transfer belongs to your own companion; invited world bots keep their own items.";
  if (session.closing()) return "The companion is waiting for dismissal or custody verification.";
  Player owner = session.owner(), bot = session.bot();
  if (GameServer.isShuttingDownSoon()) return "Item transfer is unavailable during shutdown.";
  if (!owner.isOnline() || !owner.isSpawned() || !bot.isSpawned() || !bot.isPlaying()) return "Recruit this companion before moving items.";
  if (!PlayerBotWarehouse.idle(owner) || !PlayerBotWarehouse.idle(bot)) return "Move items while both characters are alive, on the ground and outside combat, casting, trade, looting and item use.";
  return "";
 }
 /** The owner's tradeable bag, for the window's transfer panel. */
 public static List<Map<String, Object>> ownerBag(Player owner) {
  return owner.getInventory().getItems().stream().filter(PlayerBotItemTransfer::transferable).sorted(Comparator.comparingInt(Item::getObjectId))
   .map(PlayerBotItemView::stored).toList();
 }
 public static String move(Player owner, String name, boolean give, int object, long kinah) {
  var service = PlayerBotService.getInstance();
  var session = service.find(owner, name);
  synchronized (session) {
   if (!service.current(session)) throw new IllegalArgumentException("This companion has left; refresh before retrying.");
   String reason = blocker(session);
   if (!reason.isEmpty()) throw new IllegalArgumentException(reason);
   Player bot = session.bot(), from = give ? owner : bot, to = give ? bot : owner;
   Item item = null;
   if (object > 0) {
    item = from.getInventory().getItemByObjId(object);
    if (!transferable(item)) throw new IllegalArgumentException("Choose an unequipped, tradeable item stack.");
    PlayerBotWarehouse.freeSlot(to.getInventory(), item);
   } else if (kinah <= 0 || kinah > from.getInventory().getKinah()) throw new IllegalArgumentException("Enter a Kinah amount you have.");
   long money = object > 0 ? 0 : kinah;
   bot.getMoveController().abortMove();
   try {
    commit(owner, bot, from, to, item, money);
   } catch (PlayerBotTradeStore.Indeterminate error) {
    PlayerBotTrade.hold(session, error);
    throw new IllegalStateException("Item transfer persistence needs custody verification; see the server log.", error);
   } catch (SQLException | RuntimeException error) {
    LoggerFactory.getLogger(PlayerBotItemTransfer.class).error("Companion item transfer rolled back", error);
    throw new IllegalStateException("Item transfer was rolled back; nothing moved.", error);
   }
   try {
    project(owner, from, to, item, money);
   } catch (RuntimeException error) {
    PlayerBotTrade.hold(session, error);
    throw new IllegalStateException("Item transfer persistence needs custody verification; see the server log.", error);
   }
   String what = item != null ? item.getItemTemplate().getName() + " x" + item.getItemCount() : money + " Kinah";
   return give ? "Gave " + what + " to " + bot.getName() + "." : "Took " + what + " from " + bot.getName() + ".";
  }
 }
 private static void commit(Player owner, Player bot, Player from, Player to, Item item, long money) throws SQLException {
  // Checkpoint both inventories first, exactly as owner gifts do; never mark them committed on failure.
  if (!InventoryDAO.store(owner.getDirtyItemsToUpdate(), owner.getObjectId(), owner.getAccount().getId(),
   owner.getLegion() == null ? null : owner.getLegion().getLegionId())) throw new SQLException("Owner checkpoint failed");
  if (money > 0 && to.getInventory().getKinahItem() == null) to.getInventory().increaseKinah(0);
  try (var c = DatabaseFactory.getConnection()) {
   c.setAutoCommit(false);
   try {
    var pending = InventoryDAO.storeCompanionInventory(c, bot);
    var metadata = PlayerBotMetadata.pending(bot.getAccount().getId(), bot.getObjectId());
    PlayerBotMetadata.store(c, metadata); c.commit(); PlayerBotMetadata.committed(metadata); InventoryDAO.companionInventoryCommitted(pending);
   } catch (SQLException | RuntimeException e) { c.rollback(); InventoryDAO.markCompanionInventoryDirty(bot); throw e; }
  }
  if (to == owner && money > 0 && !InventoryDAO.store(owner.getDirtyItemsToUpdate(), owner.getObjectId(), owner.getAccount().getId(),
   owner.getLegion() == null ? null : owner.getLegion().getLegionId())) throw new SQLException("Owner wallet checkpoint failed");
  try (var c = DatabaseFactory.getConnection()) {
   c.setAutoCommit(false);
   try {
    if (item != null) {
     PlayerBotTradeStore.source(c, from, item, false);
     try (var s = c.prepareStatement("UPDATE inventory SET item_owner=?,slot=0,pack_count=CASE WHEN pack_count>0 THEN -pack_count ELSE pack_count END WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=0 AND item_count=? AND is_equipped=0")) {
      s.setInt(1, to.getObjectId()); s.setInt(2, item.getObjectId()); s.setInt(3, from.getObjectId()); s.setInt(4, item.getItemId()); s.setLong(5, item.getItemCount());
      if (s.executeUpdate() != 1) throw new SQLException("Transfer item custody changed");
     }
    } else {
     long before = from.getInventory().getKinah(), total = PlayerBotTradeStore.balance(to.getInventory().getKinah(), money);
     PlayerBotTradeStore.currency(c, from, from.getInventory().getKinahItem(), before, before - money);
     PlayerBotTradeStore.currency(c, to, to.getInventory().getKinahItem(), to.getInventory().getKinah(), total);
    }
    try { c.commit(); } catch (SQLException e) { throw new PlayerBotTradeStore.Indeterminate(e); }
   } catch (SQLException | RuntimeException e) {
    if (!(e instanceof PlayerBotTradeStore.Indeterminate)) try { c.rollback(); } catch (SQLException cleanup) { e.addSuppressed(cleanup); }
    throw e;
   }
  }
 }
 private static void project(Player owner, Player from, Player to, Item item, long money) {
  if (item != null) {
   from.getInventory().remove(item);
   item.setEquipmentSlot(0); if (item.getPackCount() > 0) item.setPackCount(-item.getPackCount());
   if (to.getInventory().add_CharacterTransfer(item) == null) throw new IllegalStateException("Committed transfer could not be projected");
   item.setPersistentState(PersistentState.UPDATED);
   if (from == owner) PacketSendUtility.sendPacket(owner, new SM_DELETE_ITEM(item.getObjectId()));
   else PacketSendUtility.sendPacket(owner, new SM_INVENTORY_ADD_ITEM(List.of(item), owner, ItemPacketService.ItemAddType.PLAYER_EXCHANGE_GET));
   try { QuestEngine.getInstance().onItemGet(to, item.getItemId()); }
   catch (RuntimeException error) { LoggerFactory.getLogger(PlayerBotItemTransfer.class).error("Committed transfer quest notification failed", error); }
  } else {
   Item a = from.getInventory().getKinahItem(), b = to.getInventory().getKinahItem();
   a.setItemCount(a.getItemCount() - money); b.setItemCount(b.getItemCount() + money);
   a.setPersistentState(PersistentState.UPDATED); b.setPersistentState(PersistentState.UPDATED);
   PacketSendUtility.sendPacket(owner, new SM_INVENTORY_UPDATE_ITEM(owner, owner.getInventory().getKinahItem(), ItemPacketService.ItemUpdateType.INC_PLAYER_EXCHANGE_GET_BACK));
  }
 }
 /** Send one of the owner's stacks (or Kinah) to an alt on this account through the native express mail. */
 public static String sendToAlt(Player owner, String alt, int object, long kinah) {
  var data = owner.getAccount().getPlayerAccDataList().stream().map(d -> d.getPlayerCommonData())
   .filter(c -> c.getPlayerObjId() != owner.getObjectId() && c.getName().equalsIgnoreCase(alt)).findFirst()
   .orElseThrow(() -> new IllegalArgumentException("Choose one of this account's other characters."));
  Item item = object > 0 ? owner.getInventory().getItemByObjId(object) : null;
  if (object > 0 && !transferable(item)) throw new IllegalArgumentException("Choose an unequipped, tradeable item stack.");
  if (object <= 0 && (kinah <= 0 || kinah > owner.getInventory().getKinah())) throw new IllegalArgumentException("Enter a Kinah amount you have.");
  long beforeKinah = owner.getInventory().getKinah();
  MailService.sendMail(owner, data.getName(), "From " + owner.getName(), "Sent from the Companions window.", object > 0 ? object : 0,
   item == null ? 0 : item.getItemCount(), object > 0 ? 0 : kinah, LetterType.EXPRESS);
  boolean sent = item != null ? owner.getInventory().getItemByObjId(object) == null : owner.getInventory().getKinah() < beforeKinah;
  if (!sent) throw new IllegalArgumentException("The mail was not sent; check postage Kinah and the mail rules shown in chat.");
  return "Sent by express mail to " + data.getName() + "'s mailbox.";
 }
 private PlayerBotItemTransfer() {}
}
