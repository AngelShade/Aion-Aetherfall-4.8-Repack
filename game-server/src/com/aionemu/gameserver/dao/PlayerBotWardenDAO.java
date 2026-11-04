package com.aionemu.gameserver.dao;

import java.sql.*;

/** Native traveller timers; SQL is authoritative across Warden and player restarts. */
public final class PlayerBotWardenDAO {
 public static void prepare(Connection c)throws SQLException {
  try(var s=c.createStatement()) {
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_warden_travellers (player_id INT NOT NULL PRIMARY KEY,bot_id INT NOT NULL DEFAULT 0,escort_until BIGINT NOT NULL DEFAULT 0,cooldown_until BIGINT NOT NULL DEFAULT 0,rescue_until BIGINT NOT NULL DEFAULT 0,monster_death BIGINT NOT NULL DEFAULT 0,distress_until BIGINT NOT NULL DEFAULT 0,KEY warden_bot(bot_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_warden_companies (map_id INT NOT NULL,race VARCHAR(16) NOT NULL,company_no INT NOT NULL,legion_id INT NULL UNIQUE,PRIMARY KEY(map_id,race,company_no)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }
 }
 private static void ensure(Connection c,int player)throws SQLException {
  if(c.getAutoCommit() || player<=0)throw new SQLException("Warden transaction requires native player custody");
  try(var s=c.prepareStatement("SELECT account_id FROM players WHERE id=? FOR UPDATE")) {
   s.setInt(1,player);try(var r=s.executeQuery()){if(!r.next() || r.getInt(1)<=0)throw new SQLException("Warden services require a human character");}
  }
  try(var s=c.prepareStatement("INSERT IGNORE INTO playerbot_warden_travellers (player_id) VALUES (?)")){s.setInt(1,player);s.executeUpdate();}
 }
 public static long escort(Connection c,int player,int bot,long now,long duration,long cooldown)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Warden escort requires a native transaction");
  // Lock the Warden before the traveller so competing reservations share one order.
  // The native Warden row serializes both human and bot slots across restarts.
  try(var s=c.prepareStatement("SELECT account_id FROM players WHERE id=? FOR UPDATE")) {
   s.setInt(1,bot);try(var r=s.executeQuery()){if(!r.next() || !com.aionemu.gameserver.services.playerbot.PlayerBotPopulationPolicy.serverAccount(r.getInt(1)))throw new SQLException("Warden custody changed");}
  }
  ensure(c,player);var active=currentEscort(c,bot,now);if(active!=null)return active.until();
  try(var s=c.prepareStatement("SELECT escort_until,cooldown_until FROM playerbot_warden_travellers WHERE player_id=? FOR UPDATE")) {
   s.setInt(1,player);try(var r=s.executeQuery()){r.next();long until=Math.max(r.getLong(1),r.getLong(2));if(until>now)return until;}
  }
  try(var s=c.prepareStatement("UPDATE playerbot_warden_travellers SET bot_id=?,escort_until=?,cooldown_until=? WHERE player_id=?")) {
   s.setInt(1,bot);s.setLong(2,now+duration);s.setLong(3,now+duration+cooldown);s.setInt(4,player);s.executeUpdate();
  }
  return 0;
 }
 public record Escort(int player,long until) {}
 public static Escort currentEscort(Connection c,int bot,long now)throws SQLException {
  try(var s=c.prepareStatement("SELECT player_id,escort_until FROM playerbot_warden_travellers WHERE bot_id=? AND escort_until>? ORDER BY escort_until DESC LIMIT 2 FOR UPDATE")) {
   s.setInt(1,bot);s.setLong(2,now);try(var r=s.executeQuery()) {
    if(!r.next())return null;var result=new Escort(r.getInt(1),r.getLong(2));
    if(r.next())throw new SQLException("Multiple native escort owners require custody recovery");return result;
   }
  }
 }
 public static void ended(Connection c,int player,int bot,long now,long cooldown)throws SQLException {
  ensure(c,player);
  try(var s=c.prepareStatement("UPDATE playerbot_warden_travellers SET bot_id=0,escort_until=0,cooldown_until=? WHERE player_id=? AND bot_id=?")) {
   s.setLong(1,now+cooldown);s.setInt(2,player);s.setInt(3,bot);s.executeUpdate();
  }
 }
 public static void death(Connection c,int player,long death)throws SQLException {
  ensure(c,player);try(var s=c.prepareStatement("UPDATE playerbot_warden_travellers SET monster_death=? WHERE player_id=?")){s.setLong(1,death);s.setInt(2,player);s.executeUpdate();}
 }
 public static long death(Connection c,int player)throws SQLException {
  try(var s=c.prepareStatement("SELECT monster_death FROM playerbot_warden_travellers WHERE player_id=?")){s.setInt(1,player);try(var r=s.executeQuery()){return r.next()?r.getLong(1):0;}}
 }
 public static boolean rescue(Connection c,int player,long now,long cooldown)throws SQLException {
  ensure(c,player);try(var s=c.prepareStatement("UPDATE playerbot_warden_travellers SET rescue_until=? WHERE player_id=? AND rescue_until<=?")) {
   s.setLong(1,now+cooldown);s.setInt(2,player);s.setLong(3,now);return s.executeUpdate()==1;
  }
 }
 public static boolean distress(Connection c,int player,long now,long cooldown)throws SQLException {
  ensure(c,player);try(var s=c.prepareStatement("UPDATE playerbot_warden_travellers SET distress_until=? WHERE player_id=? AND distress_until<=?")) {
   s.setLong(1,now+cooldown);s.setInt(2,player);s.setLong(3,now);return s.executeUpdate()==1;
  }
 }
 public static long rescueDue(Connection c,int player)throws SQLException {
  try(var s=c.prepareStatement("SELECT rescue_until FROM playerbot_warden_travellers WHERE player_id=?")){s.setInt(1,player);try(var r=s.executeQuery()){return r.next()?r.getLong(1):0;}}
 }
 private PlayerBotWardenDAO(){}
}
