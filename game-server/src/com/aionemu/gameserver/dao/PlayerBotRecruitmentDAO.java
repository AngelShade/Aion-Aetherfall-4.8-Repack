package com.aionemu.gameserver.dao;

import java.sql.*;
import java.util.function.Supplier;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.CombatRank;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.GearRarity;
import com.aionemu.gameserver.services.playerbot.PlayerBotRecruitmentPolicy.Roll;

/** Account ticket wallet, idempotent receipts and persisted recruitment rolls. Each change locks the wallet inside the caller's transaction. */
public final class PlayerBotRecruitmentDAO {
 public static final int CREDIT_PER_TICKET=100;
 public static final int RECYCLE_CREDIT=33;
 public record Wallet(long tickets,int credit,long revision) {}
 public record Receipt(String key,int account,String kind,long tickets,int credit,long ticketsAfter,int creditAfter,boolean replayed) {}
 public record Operation(long id,int account,String requestKey,Roll roll,boolean completed,int character,boolean replayed) {}

 public static Wallet wallet(Connection c,int account)throws SQLException {
  try(var s=c.prepareStatement("SELECT tickets,recycle_credit,revision FROM playerbot_ticket_wallet WHERE account_id=?")) {
   s.setInt(1,account);
   try(var r=s.executeQuery()){return r.next()?new Wallet(r.getLong(1),r.getInt(2),r.getLong(3)):new Wallet(0,0,0);}
  }
 }
 private static Wallet lock(Connection c,int account)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Ticket wallet requires a transaction");
  // Takes the row's exclusive lock at once. INSERT IGNORE holds a shared lock on an existing row, so two writers deadlock on the upgrade below.
  try(var s=c.prepareStatement("INSERT INTO playerbot_ticket_wallet (account_id) VALUES (?) ON DUPLICATE KEY UPDATE account_id=account_id")){s.setInt(1,account);s.executeUpdate();}
  try(var s=c.prepareStatement("SELECT tickets,recycle_credit,revision FROM playerbot_ticket_wallet WHERE account_id=? FOR UPDATE")) {
   s.setInt(1,account);
   try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Ticket wallet row missing");return new Wallet(r.getLong(1),r.getInt(2),r.getLong(3));}
  }
 }
 private static Receipt recorded(Connection c,String key)throws SQLException {
  try(var s=c.prepareStatement("SELECT account_id,kind,ticket_delta,credit_delta,tickets_after,credit_after FROM playerbot_ticket_receipts WHERE receipt_key=?")) {
   s.setString(1,key);
   try(var r=s.executeQuery()){return r.next()?new Receipt(key,r.getInt(1),r.getString(2),r.getLong(3),r.getInt(4),r.getLong(5),r.getInt(6),true):null;}
  }
 }
 private static Receipt post(Connection c,int account,String key,String kind,long tickets,int credit)throws SQLException {
  return post(c,account,key,kind,tickets,credit,lock(c,account));
 }
 private static Receipt post(Connection c,int account,String key,String kind,long tickets,int credit,Wallet wallet)throws SQLException {
  var prior=recorded(c,key);
  if(prior!=null) {
   if(prior.account()!=account || !prior.kind().equals(kind) || prior.tickets()!=tickets || prior.credit()!=credit)
    throw new SQLException("Ticket receipt key reused with different terms");
   return prior;
  }
  long pooled=wallet.credit()+(long)credit;
  long after=Math.addExact(Math.addExact(wallet.tickets(),tickets),pooled/CREDIT_PER_TICKET);
  int creditAfter=(int)(pooled%CREDIT_PER_TICKET);
  if(after<0)throw new IllegalArgumentException("No Recruitment Ticket is available.");
  try(var s=c.prepareStatement("UPDATE playerbot_ticket_wallet SET tickets=?,recycle_credit=?,revision=revision+1 WHERE account_id=? AND revision=?")) {
   s.setLong(1,after);s.setInt(2,creditAfter);s.setInt(3,account);s.setLong(4,wallet.revision());
   if(s.executeUpdate()!=1)throw new SQLException("Ticket wallet revision changed");
  }
  try(var s=c.prepareStatement("INSERT INTO playerbot_ticket_receipts (receipt_key,account_id,kind,ticket_delta,credit_delta,tickets_after,credit_after) VALUES (?,?,?,?,?,?,?)")) {
   s.setString(1,key);s.setInt(2,account);s.setString(3,kind);s.setLong(4,tickets);s.setInt(5,credit);s.setLong(6,after);s.setInt(7,creditAfter);s.executeUpdate();
  }
  return new Receipt(key,account,kind,tickets,credit,after,creditAfter,false);
 }
 /** Earned tickets are whole and guaranteed. The caller's key names one real completion for one recipient. */
 public static Receipt grant(Connection c,int account,String key,String kind,long tickets)throws SQLException {
  if(tickets<=0)throw new IllegalArgumentException("Ticket grants are whole positive amounts");
  return post(c,account,key,kind,tickets,0);
 }
 /** Account starter policy v1: one durable grant, regardless of later amount changes. */
 public static Receipt starter(Connection c,int account,long tickets)throws SQLException {
  if(tickets<=0)return null;
  var wallet=lock(c,account);String key="starter:v1:"+account;var prior=recorded(c,key);
  if(prior!=null) {
   if(prior.account()!=account || !prior.kind().equals("STARTER") || prior.tickets()<=0 || prior.credit()!=0)
    throw new SQLException("Invalid starter ticket receipt");
   return prior;
  }
  return post(c,account,key,"STARTER",tickets,0,wallet);
 }
 public static Receipt recycle(Connection c,int account,String key)throws SQLException { return post(c,account,key,"RECYCLE",0,RECYCLE_CREDIT); }
 /** Counts one distinct finished help request; every needed-th pays. Returns the account's new total, or 0 for a request already counted. */
 public static long helped(Connection c,int account,String request,int needed,long tickets)throws SQLException {
  if(needed<=0 || tickets<=0)throw new IllegalArgumentException("Invalid help contract");
  var wallet=lock(c,account);
  try(var s=c.prepareStatement("INSERT IGNORE INTO playerbot_ticket_help (request_key,account_id) VALUES (?,?)")){s.setString(1,request);s.setInt(2,account);if(s.executeUpdate()==0)return 0;}
  long total;
  boolean bounty=request.startsWith("road-bounty:");
  try(var s=c.prepareStatement("SELECT COUNT(*) FROM playerbot_ticket_help WHERE account_id=? AND request_key "+(bounty?"LIKE":"NOT LIKE")+" 'road-bounty:%'")){s.setInt(1,account);try(var r=s.executeQuery()){r.next();total=r.getLong(1);}}
  if(total%needed==0)post(c,account,(bounty?"road-bounty:":"help:")+account+":"+total,"HELP",tickets,0,wallet);
  return total;
 }

 /** Hub updates carry no SQL, so an updated server brings its own ticket tables to an existing database. */
 public static void prepare(Connection c)throws SQLException {
  try(var s=c.createStatement()){
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_ticket_wallet (account_id INT NOT NULL PRIMARY KEY,tickets BIGINT NOT NULL DEFAULT 0,recycle_credit INT NOT NULL DEFAULT 0,revision BIGINT NOT NULL DEFAULT 0,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_ticket_receipts (receipt_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,account_id INT NOT NULL,kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,ticket_delta BIGINT NOT NULL,credit_delta INT NOT NULL,tickets_after BIGINT NOT NULL,credit_after INT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY account_ticket_receipts (account_id,created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_recruitment_operations (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,account_id INT NOT NULL,request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,advanced_class VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,auto_equip_rarity VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,combat_rank VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,character_id INT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,completed_at TIMESTAMP NULL,UNIQUE KEY account_recruitment_request (account_id,request_key),UNIQUE KEY recruited_character (character_id),KEY account_recruitment_state (account_id,state)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }
  prepareDelivery(c);
  try(var s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_ticket_help (request_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,account_id INT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY account_help (account_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}
 }
 public static void prepareDelivery(Connection c)throws SQLException {
  try(var s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_ticket_entitlements (receipt_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,account_id INT NOT NULL,tickets BIGINT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY pending_account(account_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}
 }
 public static void enqueue(Connection c,int account,String key,long tickets)throws SQLException {
  if(tickets<=0)throw new IllegalArgumentException("Invalid ticket entitlement");
  try(var s=c.prepareStatement("INSERT INTO playerbot_ticket_entitlements (receipt_key,account_id,tickets) VALUES (?,?,?) ON DUPLICATE KEY UPDATE receipt_key=receipt_key")) {
   s.setString(1,key);s.setInt(2,account);s.setLong(3,tickets);s.executeUpdate();
  }
  try(var s=c.prepareStatement("SELECT account_id,tickets FROM playerbot_ticket_entitlements WHERE receipt_key=?")){s.setString(1,key);try(var r=s.executeQuery()){if(!r.next() || r.getInt(1)!=account || r.getLong(2)!=tickets)throw new SQLException("Entitlement terms changed");}}
 }
 public record Entitlement(int account,String key,long tickets) {}
 public static java.util.List<Entitlement> pending(Connection c)throws SQLException {
  var rows=new java.util.ArrayList<Entitlement>();
  try(var s=c.prepareStatement("SELECT account_id,receipt_key,tickets FROM playerbot_ticket_entitlements ORDER BY created_at,receipt_key LIMIT 100");var r=s.executeQuery()){while(r.next())rows.add(new Entitlement(r.getInt(1),r.getString(2),r.getLong(3)));}return rows;
 }
 public static void deliver(Connection c,Entitlement award)throws SQLException {
  grant(c,award.account(),award.key(),"INSTANCE",award.tickets());
  try(var s=c.prepareStatement("DELETE FROM playerbot_ticket_entitlements WHERE receipt_key=? AND account_id=? AND tickets=?")){s.setString(1,award.key());s.setInt(2,award.account());s.setLong(3,award.tickets());s.executeUpdate();}
 }

 private static final String OPERATION="SELECT id,request_key,state,advanced_class,auto_equip_rarity,combat_rank,character_id FROM playerbot_recruitment_operations ";
 private static Operation operation(ResultSet r,int account,boolean replayed)throws SQLException {
  var roll=new Roll(PlayerClass.valueOf(r.getString(4)),GearRarity.valueOf(r.getString(5)),CombatRank.valueOf(r.getString(6)));
  return new Operation(r.getLong(1),account,r.getString(2),roll,"COMPLETED".equals(r.getString(3)),r.getInt(7),replayed);
 }
 /** The unfinished roll for this account, if any. Used to resume a reveal after a crash, relog or closed window. */
 public static Operation open(Connection c,int account)throws SQLException {
  try(var s=c.prepareStatement(OPERATION+"WHERE account_id=? AND state='ROLLED' ORDER BY id LIMIT 1")) {
   s.setInt(1,account);
   try(var r=s.executeQuery()){return r.next()?operation(r,account,true):null;}
  }
 }
 public static Operation latest(Connection c,int account)throws SQLException {
  // Repair every old completed-but-hidden result for this account, including one older than the latest roll.
  try(var s=c.prepareStatement("UPDATE playerbot_roster b JOIN playerbot_recruitment_operations o ON o.character_id=b.player_id AND o.account_id=b.account_id AND o.state='COMPLETED' JOIN players p ON p.id=b.player_id AND p.account_id=b.account_id JOIN playerbot_metadata m ON m.player_id=p.id AND m.account_id=p.account_id AND m.section='growth' SET b.ready=TRUE WHERE b.account_id=? AND b.ready=FALSE AND JSON_UNQUOTE(JSON_EXTRACT(m.settings,'$.origin'))='GACHA' AND NOT EXISTS (SELECT 1 FROM playerbot_removed r WHERE r.player_id=b.player_id) AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=b.player_id)")) {
   s.setInt(1,account);s.executeUpdate();
  }
  try(var s=c.prepareStatement(OPERATION+"WHERE account_id=? AND state='COMPLETED' ORDER BY id DESC LIMIT 1")) {
   s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?operation(r,account,true):null;}
  }
 }
 /** Bind the identity before creation. A retry after a process loss keeps this exact reservation. */
 public static Operation reserve(Connection c,int account,long id,java.util.function.IntSupplier allocate)throws SQLException {
  lock(c,account);
  Operation op;
  try(var s=c.prepareStatement(OPERATION+"WHERE account_id=? AND id=? FOR UPDATE")) {
   s.setInt(1,account);s.setLong(2,id);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Missing paid roll");op=operation(r,account,true);}
  }
  if(op.completed() || op.character()>0)return op;
  int character=allocate.getAsInt();
  try(var s=c.prepareStatement("INSERT INTO playerbot_roster (player_id,account_id,ready) VALUES (?,?,FALSE)")) {
   s.setInt(1,character);s.setInt(2,account);s.executeUpdate();
  }
  try(var s=c.prepareStatement("UPDATE playerbot_recruitment_operations SET character_id=? WHERE id=? AND account_id=? AND character_id IS NULL AND state='ROLLED'")) {
   s.setInt(1,character);s.setLong(2,id);s.setInt(3,account);if(s.executeUpdate()!=1)throw new SQLException("Paid identity reservation changed");
  }
  return new Operation(op.id(),account,op.requestKey(),op.roll(),false,character,op.replayed());
 }
 /** Recovery for the former split commit: only a completed, saved growth identity may become ready. */
 public static void ready(Connection c,Operation op)throws SQLException {
  if(!op.completed())return;
  try(var s=c.prepareStatement("UPDATE playerbot_roster b JOIN players p ON p.id=b.player_id AND p.account_id=b.account_id JOIN playerbot_metadata m ON m.player_id=p.id AND m.account_id=p.account_id AND m.section='growth' SET b.ready=TRUE WHERE b.player_id=? AND b.account_id=? AND b.ready=FALSE AND NOT EXISTS (SELECT 1 FROM playerbot_removed r WHERE r.player_id=b.player_id) AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=b.player_id)")) {
   s.setInt(1,op.character());s.setInt(2,op.account());s.executeUpdate();
  }
 }
 /** Pays one ticket and stores the draw before it can be revealed. A repeat or an unfinished roll returns that same outcome without paying. */
 public static Operation begin(Connection c,int account,String requestKey,Supplier<Roll> draw)throws SQLException {
  var wallet=lock(c,account);
  try(var s=c.prepareStatement(OPERATION+"WHERE account_id=? AND request_key=?")) {
   s.setInt(1,account);s.setString(2,requestKey);
   try(var r=s.executeQuery()){if(r.next()){var op=operation(r,account,true);ready(c,op);return op;}}
  }
  var unfinished=open(c,account);
  if(unfinished!=null)return unfinished;
  if(wallet.tickets()<1)throw new IllegalArgumentException("No Recruitment Ticket is available.");
  var roll=draw.get();long id;
  try(var s=c.prepareStatement("INSERT INTO playerbot_recruitment_operations (account_id,request_key,state,advanced_class,auto_equip_rarity,combat_rank) VALUES (?,?,'ROLLED',?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
   s.setInt(1,account);s.setString(2,requestKey);s.setString(3,roll.advancedClass().name());s.setString(4,roll.autoEquipRarity().name());s.setString(5,roll.combatRank().name());s.executeUpdate();
   try(var keys=s.getGeneratedKeys()){if(!keys.next())throw new SQLException("Recruitment operation id missing");id=keys.getLong(1);}
  }
  post(c,account,"recruit:"+id,"RECRUIT",-1,0);
  return new Operation(id,account,requestKey,roll,false,0,false);
 }
 /** Joins the character's first native checkpoint, so the identity and its receipt commit together. */
 public static void complete(Connection c,int account,long id,int character)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Recruitment completion requires a transaction");
  PlayerBotMetadataDAO.owner(c,account,character);
  try(var s=c.prepareStatement("UPDATE playerbot_roster SET ready=TRUE WHERE player_id=? AND account_id=? AND NOT EXISTS (SELECT 1 FROM playerbot_removed r WHERE r.player_id=playerbot_roster.player_id) AND NOT EXISTS (SELECT 1 FROM playerbot_recycles consumed WHERE consumed.source_id=playerbot_roster.player_id)")) {
   s.setInt(1,character);s.setInt(2,account);s.executeUpdate();
  }
  try(var s=c.prepareStatement("UPDATE playerbot_recruitment_operations SET state='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=? AND account_id=? AND character_id=? AND state='ROLLED'")) {
   s.setLong(1,id);s.setInt(2,account);s.setInt(3,character);
   if(s.executeUpdate()==1)return;
  }
  try(var s=c.prepareStatement("SELECT 1 FROM playerbot_recruitment_operations WHERE id=? AND account_id=? AND state='COMPLETED' AND character_id=?")) {
   s.setLong(1,id);s.setInt(2,account);s.setInt(3,character);
   try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Recruitment operation does not match this character");}
  }
 }
 private PlayerBotRecruitmentDAO() {}
}
