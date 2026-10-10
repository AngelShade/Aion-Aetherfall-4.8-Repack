package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.*;
import org.slf4j.LoggerFactory;

/** Native mail/inventory rows commit together. No runtime file, partial attachment or foreign-row overwrite. */
final class PlayerBotMailStore {
 static final class Held extends SQLException {private static final long serialVersionUID=1L;Held(String text,Exception cause){super(text,cause);}}
 @FunctionalInterface interface Work {void run()throws SQLException;}
 static void transaction(Connection c,Work write,Runnable publish)throws SQLException {
  c.setAutoCommit(false);boolean attempted=false,acknowledged=false;
  try {write.run();attempted=true;c.commit();acknowledged=true;publish.run();}
  catch(SQLException|RuntimeException e) {
   if(acknowledged)throw new Held("Mail committed but native projection failed",e);
   try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);throw new Held("Mail rollback could not be acknowledged",e);}
   if(attempted)throw new Held("Mail commit acknowledgement failed",e);throw e;
  }
 }
 static void verifyLoaded(Connection c,Player bot,Mailbox mailbox)throws SQLException {
  Map<Integer,Letter> loaded=new HashMap<>();for(var l:mailbox.getAllLetters())loaded.put(l.getObjectId(),l);
  try(var s=c.prepareStatement("SELECT mail_unique_id,mail_recipient_id,attached_item_id,attached_kinah_count,unread,express FROM mail WHERE mail_recipient_id=?")) {
   s.setInt(1,bot.getObjectId());try(var rows=s.executeQuery()){while(rows.next()) {
    var l=loaded.remove(rows.getInt(1));
    if(l==null || l.getRecipientId()!=rows.getInt(2) || l.getAttachedKinah()!=rows.getLong(4) || l.isUnread()!=rows.getBoolean(5) || l.getLetterType().getId()!=rows.getInt(6)
     || rows.getInt(3)!=(l.getAttachedItem()==null?0:l.getAttachedItem().getObjectId()))throw new SQLException("Incomplete native mailbox load");
    if(l.getAttachedItem()!=null)source(c,bot,l.getAttachedItem(),127,false);
   }}
  }
  if(!loaded.isEmpty())throw new SQLException("Unexpected loaded mailbox identity");
 }
 static void source(Connection c,Player bot,Item item,int location,boolean pending)throws SQLException {
  if(item==null || item.getItemLocation()!=location || item.isEquipped())throw new SQLException("Mail source invalid");
  try(var s=c.prepareStatement("SELECT item_owner,item_id,item_location,item_count,is_equipped,slot FROM inventory WHERE item_unique_id=? FOR UPDATE")) {
   s.setInt(1,item.getObjectId());try(var row=s.executeQuery()) {
    if(!row.next()){if(pending && item.getPersistentState()==PersistentState.NEW)return;throw new SQLException("Mail source row missing");}
    boolean dirty=pending && item.getPersistentState()==PersistentState.UPDATE_REQUIRED;
    if(row.getInt(1)!=bot.getObjectId() || row.getInt(2)!=item.getItemId() || row.getInt(3)!=location
     || !dirty && (row.getLong(4)!=item.getItemCount() || row.getBoolean(5) || row.getLong(6)!=item.getEquipmentSlot()))throw new SQLException("Mail source custody/count/slot mismatch");
   }
  }
 }
 static void letter(Connection c,PlayerBotMail.Request r)throws SQLException {
  var letter=r.letter();try(var s=c.prepareStatement("SELECT mail_recipient_id,attached_item_id,attached_kinah_count,unread,express FROM mail WHERE mail_unique_id=? FOR UPDATE")) {
   s.setInt(1,letter.getObjectId());try(var row=s.executeQuery()) {
    if(!row.next() || row.getInt(1)!=r.bot().getObjectId() || row.getInt(2)!=(r.item()==null?0:r.item().getObjectId())
     || row.getLong(3)!=r.money() || row.getBoolean(4)!=letter.isUnread() || row.getInt(5)!=letter.getLetterType().getId())throw new SQLException("Mail letter custody/attachment changed");
   }
  }
 }
 static void update(Connection c,String sql,Object... values)throws SQLException {
  try(var s=c.prepareStatement(sql)){for(int n=0;n<values.length;n++)s.setObject(n+1,values[n]);if(s.executeUpdate()!=1)throw new SQLException("Mail guarded row update failed");}
 }
 static void wallet(Connection c,PlayerBotMail.Request r,long after)throws SQLException {
  var item=r.bot().getInventory().getKinahItem();source(c,r.bot(),item,0,false);
  if(!item.getItemTemplate().isKinah() || item.getItemCount()!=r.wallet() || after<0)throw new SQLException("Mail wallet changed");
  if(after!=r.wallet())update(c,"UPDATE inventory SET item_count=? WHERE item_unique_id=? AND item_owner=? AND item_id=182400001 AND item_location=0 AND item_count=?",after,item.getObjectId(),r.bot().getObjectId(),r.wallet());
 }
 static void destination(Connection c,PlayerBotMail.Request r)throws SQLException {
  if(r.slot()<0 || r.slot()>=PlayerBotWarehouse.slotLimit(r.bot().getInventory(),r.item()))throw new SQLException("Mail destination slot invalid");
  try(var s=c.prepareStatement("SELECT item_unique_id,item_id FROM inventory WHERE item_owner=? AND item_location=0 AND slot=? AND is_equipped=0 AND item_id<>182400001 FOR UPDATE")) {
   s.setInt(1,r.bot().getObjectId());s.setInt(2,r.slot());try(var row=s.executeQuery()){while(row.next()) {
    Item existing=r.bot().getInventory().getItemByObjId(row.getInt(1));
    if(existing==null || existing.getItemId()!=row.getInt(2) || PlayerBotWarehouse.sameSlots(r.bot().getInventory(),existing,r.item()))throw new SQLException("Mail destination slot changed");
   }}
  }
 }
 static void transfer(Connection c,PlayerBotMail.Request r)throws SQLException {
  if(c.getAutoCommit() || !r.bot().isPlayerBot() || r.bot().isWorldBot() || r.bot().getPlayerBotOwnerId()!=r.owner().getObjectId()
   || r.bot().getAccount().getId()!=r.owner().getAccount().getId())throw new SQLException("Mail requires exact private transaction");
  boolean send=r.action().startsWith("send");var item=r.item();var letter=r.letter();
  if(send) {
   if(!Set.of("senditem","sendkinah").contains(r.action()) || letter.getObjectId()<=0 || letter.getRecipientId()!=r.owner().getObjectId()
    || letter.getLetterType()!=LetterType.NORMAL || letter.getAttachedItem()!=item || r.money()!=letter.getAttachedKinah() || r.cost()<r.money() || r.cost()>r.wallet()
    || r.money()<0 || item==null && r.money()==0 || item!=null && (r.money()!=0 || !PlayerBotMail.sendable(item) || r.bot().getInventory().getItemByObjId(item.getObjectId())!=item))throw new SQLException("Mail send plan changed");
   // Serialize recipient capacity against other transactional mail and verify real native rows.
   try(var s=c.prepareStatement("SELECT id,race FROM players WHERE id=? FOR UPDATE")){s.setInt(1,r.owner().getObjectId());try(var row=s.executeQuery()){if(!row.next() || row.getInt(1)!=r.owner().getObjectId() || !r.bot().getRace().name().equals(row.getString(2)))throw new SQLException("Mail recipient changed");}}
   int count;try(var s=c.prepareStatement("SELECT COUNT(*) FROM mail WHERE mail_recipient_id=?")){s.setInt(1,r.owner().getObjectId());try(var row=s.executeQuery()){if(!row.next())throw new SQLException("Mail capacity unavailable");count=row.getInt(1);}}
   if(count>=100)throw new SQLException("Mail recipient full");
   if(item!=null)source(c,r.bot(),item,0,false);wallet(c,r,r.wallet()-r.cost());
   if(item!=null)update(c,"UPDATE inventory SET item_owner=?,item_location=127,slot=0 WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=0 AND item_count=? AND is_equipped=0 AND slot=?",r.owner().getObjectId(),item.getObjectId(),r.bot().getObjectId(),item.getItemId(),item.getItemCount(),item.getEquipmentSlot());
   update(c,"INSERT INTO mail (mail_unique_id,mail_recipient_id,sender_name,mail_title,mail_message,unread,attached_item_id,attached_kinah_count,express,recieved_time) VALUES (?,?,?,?,?,?,?,?,?,?)",letter.getObjectId(),letter.getRecipientId(),letter.getSenderName(),letter.getTitle(),letter.getMessage(),true,item==null?0:item.getObjectId(),r.money(),0,letter.getTimeStamp());
   // Human checkpoints may update this counter later; native letter rows remain authoritative.
   update(c,"UPDATE players SET mailbox_letters=? WHERE id=?",count+1,r.owner().getObjectId());
  }else {
   if(letter.getRecipientId()!=r.bot().getObjectId() || r.bot().getMailbox().getLetterFromMailbox(letter.getObjectId())!=letter)throw new SQLException("Mail letter changed");
   letter(c,r);
   if(r.action().equals("delete")) {
    if(item!=null || r.money()!=0)throw new SQLException("Mail deletion still has attachments");
    update(c,"DELETE FROM mail WHERE mail_unique_id=? AND mail_recipient_id=? AND attached_item_id=0 AND attached_kinah_count=0",letter.getObjectId(),r.bot().getObjectId());
    try(var s=c.prepareStatement("UPDATE players SET mailbox_letters=(SELECT COUNT(*) FROM mail WHERE mail_recipient_id=?) WHERE id=?")){s.setInt(1,r.bot().getObjectId());s.setInt(2,r.bot().getObjectId());s.executeUpdate();}
   }else if(r.action().equals("read")) {
    if(letter.isUnread())update(c,"UPDATE mail SET unread=0 WHERE mail_unique_id=? AND mail_recipient_id=? AND unread=1",letter.getObjectId(),r.bot().getObjectId());
   }else if(r.action().equals("take")) {
    if(r.money()<0 || item==null && r.money()==0)throw new SQLException("Mail has no valid attachment");
    if(item!=null){source(c,r.bot(),item,127,false);if(!r.expired())destination(c,r);}
    long after;try{after=Math.addExact(r.wallet(),r.money());}catch(ArithmeticException e){throw new SQLException("Mail Kinah overflow",e);}wallet(c,r,after);
    update(c,"UPDATE mail SET attached_item_id=0,attached_kinah_count=0,unread=0 WHERE mail_unique_id=? AND mail_recipient_id=? AND attached_item_id=? AND attached_kinah_count=?",letter.getObjectId(),r.bot().getObjectId(),item==null?0:item.getObjectId(),r.money());
    if(item!=null) {
     if(r.expired())update(c,"DELETE FROM inventory WHERE item_unique_id=? AND item_owner=? AND item_location=127 AND item_count=?",item.getObjectId(),r.bot().getObjectId(),item.getItemCount());
     else update(c,"UPDATE inventory SET item_location=0,slot=? WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=127 AND item_count=? AND is_equipped=0",r.slot(),item.getObjectId(),r.bot().getObjectId(),item.getItemId(),item.getItemCount());
    }
   }else throw new SQLException("Unknown mail write");
  }
 }
 static void commit(PlayerBotMail.Request r)throws SQLException {
  Connection c=DatabaseFactory.getConnection();var pending=new ArrayList<Item>();
  try {transaction(c,()->{
   if(r.action().startsWith("send") || r.action().equals("take")) {
    if(r.item()!=null)source(c,r.bot(),r.item(),r.action().startsWith("send")?0:127,true);
    source(c,r.bot(),r.bot().getInventory().getKinahItem(),0,true);
    pending.addAll(InventoryDAO.storeCompanionInventory(c,r.bot()));
   }
   transfer(c,r);
  },()->{InventoryDAO.companionInventoryCommitted(pending);PlayerBotMail.project(r);});}
  catch(SQLException|RuntimeException e){InventoryDAO.markCompanionInventoryDirty(r.bot());throw e;}
  finally{try{c.close();}catch(SQLException e){LoggerFactory.getLogger(PlayerBotMailStore.class).warn("Mail connection cleanup failed after outcome was determined",e);}}
 }
 private PlayerBotMailStore(){}
}
