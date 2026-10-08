package com.aionemu.gameserver.dao;

import java.sql.*;

/** Native account documents and durable roster quarantine; no character deletion or ID release. */
public final class PlayerBotRepositoryDAO {
 public static void account(Connection c,int account)throws SQLException {
  if(account<=0)throw new SQLException("Invalid account");
  try(var s=c.prepareStatement("SELECT account_id FROM players WHERE account_id=? UNION SELECT account_id FROM playerbot_roster WHERE account_id=? LIMIT 1")){
   s.setInt(1,account);s.setInt(2,account);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Saved-party account unavailable");}
  }
 }
 public static void roster(Connection c,int account,int character)throws SQLException {
  if(account<=0 || character<=0)throw new SQLException("Invalid roster identity");
  try(var s=c.prepareStatement("SELECT account_id FROM playerbot_roster WHERE player_id=?")){
   s.setInt(1,character);try(var r=s.executeQuery()){if(!r.next() || r.getInt(1)!=account)throw new SQLException("Removed companion is not an owned Temporary Bot");}
  }
 }
 public static String parties(Connection c,int account)throws SQLException {
  account(c,account);
  try(var s=c.prepareStatement("SELECT document FROM playerbot_saved_parties WHERE account_id=?")){
   s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}
  }
 }
 public static String removed(Connection c,int account,int character)throws SQLException {
  roster(c,account,character);
  try(var s=c.prepareStatement("SELECT account_id,document FROM playerbot_removed WHERE player_id=?")){
   s.setInt(1,character);try(var r=s.executeQuery()){if(!r.next())return null;if(r.getInt(1)!=account)throw new SQLException("Removed marker owner mismatch");return r.getString(2);}
  }
 }
 public static void parties(Connection c,int account,String document)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Repository transaction required");account(c,account);
  try(var s=c.prepareStatement("INSERT INTO playerbot_saved_parties (account_id,document) VALUES (?,?) ON DUPLICATE KEY UPDATE document=VALUES(document)")){
   s.setInt(1,account);s.setString(2,document);s.executeUpdate();
  }
 }
 public static void removed(Connection c,int account,int character,String document)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Repository transaction required");roster(c,account,character);
  try(var s=c.prepareStatement("INSERT INTO playerbot_removed (player_id,account_id,document) VALUES (?,?,?)")){
   s.setInt(1,character);s.setInt(2,account);s.setString(3,document);s.executeUpdate();
  }
 }
 private PlayerBotRepositoryDAO(){}
}
