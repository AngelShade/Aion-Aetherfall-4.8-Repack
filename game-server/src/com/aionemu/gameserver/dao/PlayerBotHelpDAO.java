package com.aionemu.gameserver.dao;

import java.sql.*;
import java.util.*;

/** Account help slot, immutable accepted contract and checkpointed progress in native SQL.
 * Payload is a versioned projection of native quest data, never a second quest engine. */
public final class PlayerBotHelpDAO {
 public record Row(String key,int account,int helper,int bot,long revision,String status,String payload) {}
 public static void prepare(Connection c)throws SQLException {
  try(var s=c.createStatement()) {
   s.executeUpdate("CREATE TABLE IF NOT EXISTS playerbot_help_requests (request_key VARCHAR(128) NOT NULL PRIMARY KEY, account_id INT NOT NULL, helper_id INT NOT NULL, requester_id INT NOT NULL, active_account INT NULL, active_requester INT NULL, revision BIGINT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL, payload MEDIUMTEXT NOT NULL, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), UNIQUE KEY help_account (active_account), UNIQUE KEY help_requester (active_requester), KEY help_history (account_id,updated_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }
 }
 private static Row row(ResultSet r)throws SQLException {
  return new Row(r.getString("request_key"),r.getInt("account_id"),r.getInt("helper_id"),r.getInt("requester_id"),r.getLong("revision"),r.getString("status"),r.getString("payload"));
 }
 public static Row current(Connection c,int account)throws SQLException {
  try(var s=c.prepareStatement("SELECT * FROM playerbot_help_requests WHERE active_account=?")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?row(r):null;}}
 }
 public static Row requester(Connection c,int bot)throws SQLException {
  try(var s=c.prepareStatement("SELECT * FROM playerbot_help_requests WHERE active_requester=?")){s.setInt(1,bot);try(var r=s.executeQuery()){return r.next()?row(r):null;}}
 }
 public static Row latest(Connection c,int account)throws SQLException {
  var active=current(c,account);if(active!=null)return active;
  try(var s=c.prepareStatement("SELECT * FROM playerbot_help_requests WHERE account_id=? ORDER BY updated_at DESC,request_key DESC LIMIT 1")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?row(r):null;}}
 }
 public static Row accept(Connection c,Row proposed)throws SQLException {
  var existing=current(c,proposed.account());
  if(existing!=null){if(existing.key().equals(proposed.key()))return existing;throw new IllegalArgumentException("Your account already has a help request. Review or abandon it first.");}
  try(var s=c.prepareStatement("SELECT * FROM playerbot_help_requests WHERE request_key=?")) {
   s.setString(1,proposed.key());try(var r=s.executeQuery()) {if(r.next())throw new IllegalArgumentException("You have already accepted this bot's need. Its saved credit is retained.");}
  }
  try(var s=c.prepareStatement("INSERT INTO playerbot_help_requests (request_key,account_id,helper_id,requester_id,active_account,active_requester,status,payload) VALUES (?,?,?,?,?,?,?,?)")) {
   s.setString(1,proposed.key());s.setInt(2,proposed.account());s.setInt(3,proposed.helper());s.setInt(4,proposed.bot());s.setInt(5,proposed.account());s.setInt(6,proposed.bot());s.setString(7,proposed.status());s.setString(8,proposed.payload());
   try{s.executeUpdate();}catch(SQLIntegrityConstraintViolationException conflict){throw new IllegalArgumentException("This account or bot already has an accepted request. Refresh its details.",conflict);}
  }
  return proposed;
 }
 /** Compare-and-save shares the requesting bot's native inventory/quest checkpoint. */
 public static Row store(Connection c,Row before,String status,String payload)throws SQLException {
  if(before.status().equals(status) && before.payload().equals(payload))return before;
  try(var s=c.prepareStatement("UPDATE playerbot_help_requests SET status=?,payload=?,revision=revision+1 WHERE request_key=? AND revision=? AND active_account=?")) {
   s.setString(1,status);s.setString(2,payload);s.setString(3,before.key());s.setLong(4,before.revision());s.setInt(5,before.account());
   if(s.executeUpdate()!=1) {
    // A connection can fail after COMMIT reached MariaDB. Adopt the persisted
    // revision on retry; later native checkpoints publish any newer work.
    try(var read=c.prepareStatement("SELECT * FROM playerbot_help_requests WHERE request_key=? AND account_id=?")) {
     read.setString(1,before.key());read.setInt(2,before.account());
     try(var r=read.executeQuery()){if(r.next()){var saved=row(r);if(saved.revision()>before.revision())return saved;}}
    }
    throw new SQLException("Help request changed during its native checkpoint");
   }
  }
  return new Row(before.key(),before.account(),before.helper(),before.bot(),before.revision()+1,status,payload);
 }
 public static Row close(Connection c,int account,String key,boolean claim,int needed,long tickets)throws SQLException {
  Row before;
  try(var s=c.prepareStatement("SELECT * FROM playerbot_help_requests WHERE request_key=? AND account_id=? FOR UPDATE")) {
   s.setString(1,key);s.setInt(2,account);try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose your accepted request.");before=row(r);}
  }
  if(before.status().equals("CLAIMED") || before.status().equals("ABANDONED"))return before;
  String status="ABANDONED";
  if(claim) {
   if(!before.status().equals("COMPLETE"))throw new IllegalArgumentException("The server has not confirmed this request's completion.");
   PlayerBotRecruitmentDAO.helped(c,account,key,needed,tickets);status="CLAIMED";
  }
  try(var s=c.prepareStatement("UPDATE playerbot_help_requests SET active_account=NULL,active_requester=NULL,status=?,revision=revision+1 WHERE request_key=?")) {s.setString(1,status);s.setString(2,key);s.executeUpdate();}
  return new Row(key,account,before.helper(),before.bot(),before.revision()+1,status,before.payload());
 }
 private PlayerBotHelpDAO() {}
}
