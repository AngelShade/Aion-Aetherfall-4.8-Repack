package com.aionemu.gameserver.dao;

import java.sql.*;

/** Immutable world-helper quotes and paid terms in the native SQL repository. */
public final class PlayerBotHiringDAO {
 public record Contract(String id,int account,int owner,int bot,long fee,long duration,long quoteUntil,String rank,
                        String state,long started,long ends,String endedReason) {}
 public static final String SCHEMA="""
  CREATE TABLE IF NOT EXISTS playerbot_hire_contracts (
   contract_id VARCHAR(36) NOT NULL PRIMARY KEY, account_id INT NOT NULL, owner_id INT NOT NULL, bot_id INT NOT NULL,
   fee BIGINT NOT NULL, duration_ms BIGINT NOT NULL, quote_until BIGINT NOT NULL, quoted_rank VARCHAR(16) NOT NULL,
   state VARCHAR(12) NOT NULL DEFAULT 'QUOTED', started_at BIGINT NOT NULL DEFAULT 0, ends_at BIGINT NOT NULL DEFAULT 0,
   ended_reason VARCHAR(32) NOT NULL DEFAULT '', created_at BIGINT NOT NULL,
   INDEX bot_terms(bot_id,state), INDEX owner_terms(account_id,owner_id,state)
  ) ENGINE=InnoDB
  """;
 private PlayerBotHiringDAO() {}
 public static void prepare(Connection c)throws SQLException {try(var s=c.createStatement()){s.execute(SCHEMA);}}
 private static Contract read(ResultSet r)throws SQLException {
  return new Contract(r.getString("contract_id"),r.getInt("account_id"),r.getInt("owner_id"),r.getInt("bot_id"),r.getLong("fee"),
   r.getLong("duration_ms"),r.getLong("quote_until"),r.getString("quoted_rank"),r.getString("state"),r.getLong("started_at"),r.getLong("ends_at"),r.getString("ended_reason"));
 }
 public static void insert(Connection c,Contract q)throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO playerbot_hire_contracts(contract_id,account_id,owner_id,bot_id,fee,duration_ms,quote_until,quoted_rank,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
   s.setString(1,q.id());s.setInt(2,q.account());s.setInt(3,q.owner());s.setInt(4,q.bot());s.setLong(5,q.fee());s.setLong(6,q.duration());s.setLong(7,q.quoteUntil());s.setString(8,q.rank());s.setLong(9,System.currentTimeMillis());
   if(s.executeUpdate()!=1)throw new SQLException("Could not store helper quote");
  }
 }
 public static Contract get(Connection c,String id,boolean lock)throws SQLException {
  if(lock && c.getAutoCommit())throw new SQLException("A helper contract lock requires a transaction");
  try(var s=c.prepareStatement("SELECT * FROM playerbot_hire_contracts WHERE contract_id=?"+(lock?" FOR UPDATE":""))) {
   s.setString(1,id);try(var r=s.executeQuery()){return r.next()?read(r):null;}
  }
 }
 public static Contract active(Connection c,int bot)throws SQLException {
  try(var s=c.prepareStatement("SELECT * FROM playerbot_hire_contracts WHERE bot_id=? AND state='ACTIVE' ORDER BY started_at DESC LIMIT 1")) {
   s.setInt(1,bot);try(var r=s.executeQuery()){return r.next()?read(r):null;}
  }
 }
 public static void lockBot(Connection c,int bot)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Helper admission requires a transaction");
  try(var s=c.prepareStatement("SELECT id FROM players WHERE id=? FOR UPDATE")) {
   s.setInt(1,bot);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("World helper identity is missing");}
  }
 }
 public static Contract activate(Connection c,Contract q,long now)throws SQLException {
  if(c.getAutoCommit() || !q.state().equals("QUOTED") || q.fee()<1 || q.duration()<1 || q.quoteUntil()<now)
   throw new SQLException("Helper activation requires a valid unexpired quote and the payment transaction");
  long ends;try{ends=Math.addExact(now,q.duration());}catch(ArithmeticException e){throw new SQLException("Helper term overflow",e);}
  try(var s=c.prepareStatement("UPDATE playerbot_hire_contracts SET state='ACTIVE',started_at=?,ends_at=? WHERE contract_id=? AND state='QUOTED' AND quote_until>=?")) {
   s.setLong(1,now);s.setLong(2,ends);s.setString(3,q.id());s.setLong(4,now);
   if(s.executeUpdate()!=1)throw new SQLException("The helper quote expired or was already used");
  }
  return new Contract(q.id(),q.account(),q.owner(),q.bot(),q.fee(),q.duration(),q.quoteUntil(),q.rank(),"ACTIVE",now,ends,"");
 }
 public static void end(Connection c,int bot,String reason)throws SQLException {
  if(reason==null || !reason.matches("[A-Z_]{1,32}"))throw new IllegalArgumentException("Invalid helper term end reason");
  try(var s=c.prepareStatement("UPDATE playerbot_hire_contracts SET state='ENDED',ended_reason=? WHERE bot_id=? AND state='ACTIVE'")) {
   s.setString(1,reason);s.setInt(2,bot);s.executeUpdate();
  }
 }
}
