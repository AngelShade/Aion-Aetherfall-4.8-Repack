package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.team.legion.*;

/** Native inventory owner changes from character to legion only after locked custody validation. */
final class PlayerBotLegionStorageStore {
 static void access(Connection c,PlayerBotLegionStorage.Deposit request)throws SQLException {
  var legion=request.legion();
  // Lock native membership and permission records; reject stale native caches rather than overriding them.
  try(var s=c.prepareStatement("SELECT deputy_permission,centurion_permission,legionary_permission,volunteer_permission,disband_time FROM legions WHERE id=? FOR UPDATE")) {
   s.setInt(1,legion.getLegionId());try(var row=s.executeQuery()) {
    if(!row.next() || row.getInt(1)!=legion.getDeputyPermission() || row.getInt(2)!=legion.getCenturionPermission()
     || row.getInt(3)!=legion.getLegionaryPermission() || row.getInt(4)!=legion.getVolunteerPermission() || row.getInt(5)!=0)
     throw new SQLException("Legion permissions or disband state changed");
   }
  }
  try(var s=c.prepareStatement("SELECT legion_id,`rank` FROM legion_members WHERE player_id=? FOR UPDATE")) {
   for(var player:List.of(request.session().owner(),request.move().bot())) {
    s.setInt(1,player.getObjectId());try(var row=s.executeQuery()) {
     if(!row.next() || row.getInt(1)!=legion.getLegionId() || !player.getLegionMember().getRank().name().equals(row.getString(2)))
      throw new SQLException("Legion membership or rank changed");
    }
   }
  }
 }
 static void transfer(Connection c,PlayerBotLegionStorage.Deposit request)throws SQLException {
  var move=request.move();var bot=move.bot();var legion=request.legion();
  if(c.getAutoCommit() || !Thread.holdsLock(LegionWarehouse.CUSTODY_LOCK) || !PlayerBotWarehouse.privateOwner(request.session())
   || !PlayerBotLegionStorage.membership(request.session().owner(),bot) || bot.getLegion()!=legion || legion.isDisbanding()
   || LegionWarehouse.custodyHeld(legion.getLegionId()) || legion.getLegionWarehouse().getCurrentUser()!=bot.getObjectId()
   || move.source()!=bot.getInventory() || move.destination().getStorageType().getId()!=3 || move.source().getStorageType().getId()!=0
   || move.slot()<0 || move.slot()>=move.destination().getLimit() || move.count()<=0 || move.item().getItemCount()!=move.count()
   || !PlayerBotLegionStorage.eligible(move.item()) || move.item().getItemLocation()!=0 || move.source().getItemByObjId(move.item().getObjectId())!=move.item())
   throw new SQLException("Legion deposit requires exact native ownership, access and custody lock");
  PlayerBotWarehouseStore.source(c,move,false);
  // Verify the complete shared image. A swallowed/partial legacy load cannot authorize a deposit.
  var remaining=new HashMap<Integer,Item>();for(Item i:legion.getLegionWarehouse().getItemsWithKinah())remaining.put(i.getObjectId(),i);
  try(var s=c.prepareStatement("SELECT item_unique_id,item_id,item_count,is_equipped,slot FROM inventory WHERE item_owner=? AND item_location=3 FOR UPDATE")) {
   s.setInt(1,legion.getLegionId());try(var row=s.executeQuery()){while(row.next()) {
    Item i=remaining.remove(row.getInt(1));
    if(i==null || i.getItemId()!=row.getInt(2) || i.getItemCount()!=row.getLong(3) || i.isEquipped()!=row.getBoolean(4) || i.getEquipmentSlot()!=row.getLong(5))
     throw new SQLException("Legion storage load/custody image mismatch");
    if(!i.getItemTemplate().isKinah() && i.getEquipmentSlot()==move.slot())throw new SQLException("Legion destination slot changed");
   }}
  }
  if(!remaining.isEmpty())throw new SQLException("Legion storage rows missing");
  try(var s=c.prepareStatement("UPDATE inventory SET item_owner=?,item_location=3,slot=? WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_count=? AND item_location=0 AND is_equipped=0 AND slot=?")) {
   s.setInt(1,legion.getLegionId());s.setInt(2,move.slot());s.setInt(3,move.item().getObjectId());s.setInt(4,bot.getObjectId());
   s.setInt(5,move.item().getItemId());s.setLong(6,move.count());s.setLong(7,move.item().getEquipmentSlot());
   if(s.executeUpdate()!=1)throw new SQLException("Legion deposit source changed");
  }
 }
 static void commit(PlayerBotLegionStorage.Deposit request)throws SQLException {
  Connection c=DatabaseFactory.getConnection();var pending=new ArrayList<Item>();
  try {
   PlayerBotWarehouseStore.transaction(c,()->{
    access(c,request);PlayerBotWarehouseStore.source(c,request.move(),true);
    pending.addAll(InventoryDAO.storeCompanionInventory(c,request.move().bot()));
    pending.addAll(InventoryDAO.storeCompanionLegionInventory(c,request.legion()));transfer(c,request);
    String reason=PlayerBotLegionStorage.blocker(request.session());if(!reason.isEmpty())throw new SQLException(reason);
   },()->{
    InventoryDAO.companionInventoryCommitted(pending);
    if(request.move().bot().getLegion()!=request.legion() || !PlayerBotLegionStorage.membership(request.session().owner(),request.move().bot()))
     throw new IllegalStateException("Committed legion access changed before projection");
    PlayerBotWarehouse.project(request.move());
   });
  }catch(SQLException|RuntimeException error){InventoryDAO.markCompanionInventoryDirty(request.move().bot());request.legion().getLegionWarehouse().setPersistentState(com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.UPDATE_REQUIRED);throw error;}
  finally {try{c.close();}catch(SQLException close){org.slf4j.LoggerFactory.getLogger(PlayerBotLegionStorageStore.class).warn("Legion connection cleanup failed after established outcome",close);}}
 }
 private PlayerBotLegionStorageStore(){}
}
