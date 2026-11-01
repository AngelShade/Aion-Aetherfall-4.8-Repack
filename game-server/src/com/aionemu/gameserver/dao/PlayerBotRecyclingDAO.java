package com.aionemu.gameserver.dao;

import java.sql.*;

/** Native quotes, permanent consumed identities and non-recyclable training provenance. */
public final class PlayerBotRecyclingDAO {
 public record Quote(String key,int account,int source,int target,int receiver,String sourceName,String targetName,String fingerprint,
                     int learning,long kinah,long appraisal,int items,long actualLearning,long recycledLearning,long expires) {}
 public record Receipt(String key,int account,int source,int target,int receiver,int learning,long kinah,long completed) {}
 private static final String FIELDS="quote_key,account_id,source_id,target_id,receiver_id,source_name,target_name,fingerprint,learning_xp,kinah,item_appraisal,appraised_items,actual_learning,recycled_learning,expires_at";
 public static void prepare(Connection c)throws SQLException {
  try(var s=c.createStatement()) {
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_recycle_quotes (quote_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,account_id INT NOT NULL,source_id INT NOT NULL,target_id INT NOT NULL,receiver_id INT NOT NULL,source_name VARCHAR(64) NOT NULL,target_name VARCHAR(64) NOT NULL,fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,learning_xp INT NOT NULL,kinah BIGINT NOT NULL,item_appraisal BIGINT NOT NULL,appraised_items INT NOT NULL,actual_learning BIGINT NOT NULL,recycled_learning BIGINT NOT NULL,expires_at BIGINT NOT NULL,KEY account_recycle_quotes(account_id,expires_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_recycles (source_id INT NOT NULL PRIMARY KEY,account_id INT NOT NULL,target_id INT NOT NULL,receiver_id INT NOT NULL,quote_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,learning_xp INT NOT NULL,kinah BIGINT NOT NULL,completed_at BIGINT NOT NULL,UNIQUE KEY recycle_quote(quote_key),KEY account_recycles(account_id,completed_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_recycle_learning (player_id INT NOT NULL PRIMARY KEY,account_id INT NOT NULL,received_xp BIGINT NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }
 }
 public static void quote(Connection c,Quote q)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Recycling quote requires a transaction");
  try(var s=c.prepareStatement("INSERT INTO playerbot_recycle_quotes ("+FIELDS+") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
   int n=0;s.setString(++n,q.key());s.setInt(++n,q.account());s.setInt(++n,q.source());s.setInt(++n,q.target());s.setInt(++n,q.receiver());
   s.setString(++n,q.sourceName());s.setString(++n,q.targetName());s.setString(++n,q.fingerprint());s.setInt(++n,q.learning());s.setLong(++n,q.kinah());
   s.setLong(++n,q.appraisal());s.setInt(++n,q.items());s.setLong(++n,q.actualLearning());s.setLong(++n,q.recycledLearning());s.setLong(++n,q.expires());s.executeUpdate();
  }
  // Quotes have no rewards. Completed quotes remain with their immutable receipts.
  try(var s=c.prepareStatement("DELETE FROM playerbot_recycle_quotes WHERE account_id=? AND expires_at<? AND NOT EXISTS (SELECT 1 FROM playerbot_recycles r WHERE r.quote_key=playerbot_recycle_quotes.quote_key) LIMIT 100")) {
   s.setInt(1,q.account());s.setLong(2,System.currentTimeMillis()-86400000L);s.executeUpdate();
  }
 }
 public static Quote quote(Connection c,String key,boolean lock)throws SQLException {
  try(var s=c.prepareStatement("SELECT "+FIELDS+" FROM playerbot_recycle_quotes WHERE quote_key=?"+(lock?" FOR UPDATE":""))) {
   s.setString(1,key);try(var r=s.executeQuery()){return r.next()?new Quote(r.getString(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getString(6),r.getString(7),r.getString(8),r.getInt(9),r.getLong(10),r.getLong(11),r.getInt(12),r.getLong(13),r.getLong(14),r.getLong(15)):null;}
  }
 }
 public static Receipt receipt(Connection c,int source)throws SQLException {
  try(var s=c.prepareStatement("SELECT quote_key,account_id,source_id,target_id,receiver_id,learning_xp,kinah,completed_at FROM playerbot_recycles WHERE source_id=?")) {
   s.setInt(1,source);try(var r=s.executeQuery()){return r.next()?new Receipt(r.getString(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getInt(6),r.getLong(7),r.getLong(8)):null;}
  }
 }
 public static long received(Connection c,int account,int character)throws SQLException {
  try(var s=c.prepareStatement("SELECT account_id,received_xp FROM playerbot_recycle_learning WHERE player_id=?")) {
   s.setInt(1,character);try(var r=s.executeQuery()) {
    if(!r.next())return 0;
    long xp=r.getLong(2);if(r.getInt(1)!=account || xp<0)throw new SQLException("Recycled training ownership/provenance mismatch");return xp;
   }
  }
 }
 /** Native roster identity remains dedicated forever; consumption does not release a character or item ID. */
 public static void actor(Connection c,int account,int character)throws SQLException {
  try(var s=c.prepareStatement("SELECT b.account_id,b.ready,p.account_id,r.player_id,JSON_UNQUOTE(JSON_EXTRACT(m.settings,'$.origin')) FROM playerbot_roster b JOIN players p ON p.id=b.player_id JOIN playerbot_metadata m ON m.player_id=b.player_id AND m.account_id=b.account_id AND m.section='growth' LEFT JOIN playerbot_removed r ON r.player_id=b.player_id WHERE b.player_id=? FOR UPDATE")) {
   s.setInt(1,character);try(var r=s.executeQuery()) {
    if(!r.next() || r.getInt(1)!=account || r.getInt(3)!=account || !r.getBoolean(2) || r.getObject(4)!=null || !"GACHA".equals(r.getString(5)) || receipt(c,character)!=null)
     throw new SQLException("Recycling needs an owned, ready, non-archived ticket recruit");
   }
  }
 }
 public static void complete(Connection c,Quote q,String marker)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Recycling requires one native transaction");
  PlayerBotRepositoryDAO.removed(c,q.account(),q.source(),marker);
  try(var s=c.prepareStatement("UPDATE playerbot_roster SET ready=FALSE WHERE player_id=? AND account_id=? AND ready=TRUE")) {
   s.setInt(1,q.source());s.setInt(2,q.account());if(s.executeUpdate()!=1)throw new SQLException("Recycled roster identity changed");
  }
  long before=received(c,q.account(),q.target()),after=Math.addExact(before,q.learning());
  try(var s=c.prepareStatement("INSERT INTO playerbot_recycle_learning (player_id,account_id,received_xp) VALUES (?,?,?) ON DUPLICATE KEY UPDATE received_xp=VALUES(received_xp)")) {
   s.setInt(1,q.target());s.setInt(2,q.account());s.setLong(3,after);s.executeUpdate();
  }
  try(var s=c.prepareStatement("INSERT INTO playerbot_recycles (source_id,account_id,target_id,receiver_id,quote_key,learning_xp,kinah,completed_at) VALUES (?,?,?,?,?,?,?,?)")) {
   s.setInt(1,q.source());s.setInt(2,q.account());s.setInt(3,q.target());s.setInt(4,q.receiver());s.setString(5,q.key());s.setInt(6,q.learning());s.setLong(7,q.kinah());s.setLong(8,System.currentTimeMillis());s.executeUpdate();
  }
 }
 private PlayerBotRecyclingDAO() {}
}
