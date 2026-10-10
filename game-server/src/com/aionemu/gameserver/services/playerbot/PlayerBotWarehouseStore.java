package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import org.slf4j.LoggerFactory;

/** Durable private custody first, then native projection; ambiguous commit never becomes a retry/rollback claim. */
final class PlayerBotWarehouseStore {
 static final class Held extends SQLException {
  private static final long serialVersionUID=1L;
  Held(String message,Exception cause){super(message,cause);}
 }
 @FunctionalInterface interface Work {void run()throws SQLException;}
 static void transaction(Connection c,Work write,Runnable publish)throws SQLException {
  c.setAutoCommit(false);boolean acknowledged=false,attempted=false;
  try {
   write.run();attempted=true;c.commit();acknowledged=true;publish.run();
  }catch(SQLException|RuntimeException error) {
   if(acknowledged)throw new Held("Warehouse commit succeeded but native projection failed",error);
   try{c.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);throw new Held("Warehouse rollback could not be acknowledged",error);}
   if(attempted)throw new Held("Warehouse commit acknowledgement failed",error);
   throw error;
  }
 }
 static void source(Connection c,PlayerBotWarehouse.Move move,boolean pending)throws SQLException {
  var item=move.item();
  try(var s=c.prepareStatement("SELECT item_owner,item_id,item_location,item_count,is_equipped,slot FROM inventory WHERE item_unique_id=? FOR UPDATE")) {
   s.setInt(1,item.getObjectId());try(var row=s.executeQuery()) {
    if(!row.next()){if(pending && item.getPersistentState()==PersistentState.NEW)return;throw new SQLException("Warehouse source row missing");}
    boolean dirty=pending && item.getPersistentState()==PersistentState.UPDATE_REQUIRED;
    if(row.getInt(1)!=move.bot().getObjectId() || row.getInt(2)!=item.getItemId() || row.getInt(3)!=move.source().getStorageType().getId()
     || !dirty && (row.getBoolean(5) || row.getLong(4)!=move.count() || row.getLong(6)!=item.getEquipmentSlot()))
     throw new SQLException("Warehouse source custody/count/slot mismatch");
   }
  }
 }
 static void transfer(Connection c,PlayerBotWarehouse.Move move)throws SQLException {
  if(c.getAutoCommit() || !move.bot().isPlayerBot() || move.bot().isWorldBot()
   || !((move.source().getStorageType().getId()==0 && move.destination().getStorageType().getId()==1)
       || (move.source().getStorageType().getId()==1 && move.destination().getStorageType().getId()==0))
   || move.slot()<0 || move.slot()>=PlayerBotWarehouse.slotLimit(move.destination(),move.item()) || move.count()<=0
   || move.item().getItemCount()!=move.count() || move.item().isEquipped() || move.item().getItemTemplate().isKinah()
   || move.item().getItemLocation()!=move.source().getStorageType().getId()
   || move.source().getItemByObjId(move.item().getObjectId())!=move.item())
   throw new SQLException("Warehouse requires exact private cube/warehouse transaction");
  source(c,move,false);
  try(var s=c.prepareStatement("SELECT item_unique_id,item_id FROM inventory WHERE item_owner=? AND item_location=? AND slot=? AND is_equipped=0 AND item_id<>182400001 FOR UPDATE")) {
   s.setInt(1,move.bot().getObjectId());s.setInt(2,move.destination().getStorageType().getId());s.setInt(3,move.slot());
   try(var row=s.executeQuery()){while(row.next()) {
    var existing=move.destination().getItemByObjId(row.getInt(1));
    if(existing==null || existing.getItemId()!=row.getInt(2) || existing.isEquipped()
     || existing.getItemLocation()!=move.destination().getStorageType().getId() || existing.getEquipmentSlot()!=move.slot()
     || PlayerBotWarehouse.sameSlots(move.destination(),existing,move.item()))
     throw new SQLException("Warehouse destination slot changed");
   }}
  }
  try(var s=c.prepareStatement("UPDATE inventory SET item_location=?,slot=? WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_count=? AND item_location=? AND is_equipped=0 AND slot=?")) {
   s.setInt(1,move.destination().getStorageType().getId());s.setInt(2,move.slot());s.setInt(3,move.item().getObjectId());
   s.setInt(4,move.bot().getObjectId());s.setInt(5,move.item().getItemId());s.setLong(6,move.count());
   s.setInt(7,move.source().getStorageType().getId());s.setLong(8,move.item().getEquipmentSlot());
   if(s.executeUpdate()!=1)throw new SQLException("Warehouse source changed before move");
  }
 }
 static void commit(PlayerBotWarehouse.Move move)throws SQLException {
  Connection c=DatabaseFactory.getConnection();
  try {
   final java.util.List<com.aionemu.gameserver.model.gameobjects.Item> pending=new java.util.ArrayList<>();
   transaction(c,()->{source(c,move,true);pending.addAll(InventoryDAO.storeCompanionInventory(c,move.bot()));transfer(c,move);},
    ()->{InventoryDAO.companionInventoryCommitted(pending);PlayerBotWarehouse.project(move);});
  }catch(SQLException|RuntimeException error){InventoryDAO.markCompanionInventoryDirty(move.bot());throw error;}
  finally {try{c.close();}catch(SQLException close){LoggerFactory.getLogger(PlayerBotWarehouseStore.class).warn("Warehouse connection cleanup failed after transaction outcome was determined",close);}}
 }
 private PlayerBotWarehouseStore(){}
}
