package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.trade.Exchange;
import com.aionemu.gameserver.model.trade.ExchangeItem;

/** DB-first owner gifts: exact source custody, one item/Kinah transaction, no foreign-row overwrite. */
final class PlayerBotTradeStore {
 static final class Indeterminate extends SQLException {Indeterminate(SQLException cause){super("Trade commit acknowledgement failed",cause);}}
 static void source(Connection c, Player owner, Item item, boolean fresh) throws SQLException {
  try(var s=c.prepareStatement("SELECT item_owner,item_id,item_location,item_count,is_equipped FROM inventory WHERE item_unique_id=? FOR UPDATE")) {
   s.setInt(1,item.getObjectId());try(var r=s.executeQuery()) {
    if(!r.next()){if(fresh && item.getPersistentState()==PersistentState.NEW)return;throw new SQLException("Trade source row missing");}
    if(r.getInt(1)!=owner.getObjectId() || r.getInt(2)!=item.getItemId() || r.getInt(3)!=0 || r.getBoolean(5)
      || !fresh && r.getLong(4)!=item.getItemCount())throw new SQLException("Trade source custody/count mismatch");
   }
  }
 }
 static long balance(long current,long incoming) throws SQLException {
  try{if(current<0 || incoming<0)throw new ArithmeticException();return Math.addExact(current,incoming);}
  catch(ArithmeticException e){throw new SQLException("Trade Kinah overflow",e);}
 }
 static void currency(Connection c,Player player,Item item,long before,long after) throws SQLException {
  if(item==null || !item.getItemTemplate().isKinah())throw new SQLException("Trade wallet missing");
  source(c,player,item,false);
  try(var s=c.prepareStatement("UPDATE inventory SET item_count=? WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=0 AND item_count=?")) {
   s.setLong(1,after);s.setInt(2,item.getObjectId());s.setInt(3,player.getObjectId());s.setInt(4,item.getItemId());s.setLong(5,before);
   if(s.executeUpdate()!=1)throw new SQLException("Trade wallet changed");
  }
 }
 static void transfer(Connection c,Player owner,Player bot,Exchange offer) throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Trade requires transaction");
  // Validate every source before writing any offered item. No caller-supplied destination ownership.
  for(var e:offer.getItems().values()) {
   Item real=owner.getInventory().getItemByObjId(e.getItemObjId());
   if(real==null || real.isEquipped() || real.getItemLocation()!=0 || e.getItemCount()<=0 || e.getItemCount()>real.getItemCount())
    throw new SQLException("Trade item changed");
   source(c,owner,real,false);
  }
  long money=offer.getKinahCount();
  if(money<0 || money>owner.getInventory().getKinah())throw new SQLException("Trade funds changed");
  long total=balance(bot.getInventory().getKinah(),money);
  if(money>0){currency(c,owner,owner.getInventory().getKinahItem(),owner.getInventory().getKinah(),owner.getInventory().getKinah()-money);
   currency(c,bot,bot.getInventory().getKinahItem(),bot.getInventory().getKinah(),total);}
  for(ExchangeItem e:offer.getItems().values()) {
   Item real=owner.getInventory().getItemByObjId(e.getItemObjId());long remaining=real.getItemCount()-e.getItemCount();
   if(remaining==0) {
    try(var s=c.prepareStatement("UPDATE inventory SET item_owner=?,slot=0,pack_count=CASE WHEN pack_count>0 THEN -pack_count ELSE pack_count END WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=0 AND item_count=? AND is_equipped=0")) {
     s.setInt(1,bot.getObjectId());s.setInt(2,real.getObjectId());s.setInt(3,owner.getObjectId());s.setInt(4,real.getItemId());s.setLong(5,real.getItemCount());
     if(s.executeUpdate()!=1)throw new SQLException("Trade item custody changed");
    }
   } else {
    Item split=e.getItem();if(split==real || split.getObjectId()==real.getObjectId() || split.getPersistentState()!=PersistentState.NEW)
     throw new SQLException("Trade split identity invalid");
    try(var s=c.prepareStatement("UPDATE inventory SET item_count=? WHERE item_unique_id=? AND item_owner=? AND item_count=? AND item_location=0")) {
     s.setLong(1,remaining);s.setInt(2,real.getObjectId());s.setInt(3,owner.getObjectId());s.setLong(4,real.getItemCount());
     if(s.executeUpdate()!=1)throw new SQLException("Trade stack changed");
    }
    split.setItemLocation(0);split.setEquipmentSlot(0);if(split.getPackCount()>0)split.setPackCount(-split.getPackCount());
    if(!InventoryDAO.insertTransactionItem(c,split,bot))throw new SQLException("Trade split insert failed");
   }
  }
 }
 static void commit(Player owner,Player bot,Exchange offer) throws SQLException {
  // Flush pre-existing changes before the ownership transaction; never mark them committed on failure.
  try(var c=DatabaseFactory.getConnection()) {c.setAutoCommit(false);
   for(var e:offer.getItems().values())source(c,owner,owner.getInventory().getItemByObjId(e.getItemObjId()),true);
   c.rollback();
  }
  // Packet dispatch already serializes the donor's client monitor; do not invert
  // service -> client locks for callbacks invoked during AI dismissal/cancellation.
  if(!InventoryDAO.store(owner.getDirtyItemsToUpdate(),owner.getObjectId(),owner.getAccount().getId(),owner.getLegion()==null?null:owner.getLegion().getLegionId()))throw new SQLException("Owner checkpoint failed");
  if(offer.getKinahCount()>0 && bot.getInventory().getKinahItem()==null)bot.getInventory().increaseKinah(0);
  try(var c=DatabaseFactory.getConnection()) {c.setAutoCommit(false);
   try{var pending=InventoryDAO.storeCompanionInventory(c,bot);var metadata=PlayerBotMetadata.pending(bot.getAccount().getId(),bot.getObjectId());
    PlayerBotMetadata.store(c,metadata);c.commit();PlayerBotMetadata.committed(metadata);InventoryDAO.companionInventoryCommitted(pending);}
   catch(SQLException|RuntimeException e){c.rollback();InventoryDAO.markCompanionInventoryDirty(bot);throw e;}
  }
  try(var c=DatabaseFactory.getConnection()) {c.setAutoCommit(false);
   try{transfer(c,owner,bot,offer);try{c.commit();}catch(SQLException e){throw new Indeterminate(e);}}
   catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException cleanup){e.addSuppressed(cleanup);}throw e;}
  }
 }
 private PlayerBotTradeStore() {}
}
