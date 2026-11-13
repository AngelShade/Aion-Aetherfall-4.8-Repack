package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import com.aionemu.commons.database.DatabaseFactory;

/**
 * In-memory companion tables for world-free checks: playerbot_metadata, the players ownership both DAOs check, and the
 * saved-party repository (playerbot_roster, playerbot_saved_parties, playerbot_removed). It answers exactly the
 * statements PlayerBotMetadataDAO and PlayerBotRepositoryDAO send, keeps what was written, and never connects anywhere.
 * Any other statement finds no rows. Companion settings moved from per-character and per-account files to these
 * tables; checks of those settings install this instead of expecting files.
 */
final class PlayerBotMemoryMetadata implements AutoCloseable {
 record Row(int account,long revision,String settings) {}
 record Removed(int account,String document) {}
 /** playerbot_metadata by "character/section". */
 final Map<String,Row> rows=new HashMap<>();
 /** players: character -> account. */
 final Map<Integer,Integer> owners=new HashMap<>();
 /** playerbot_roster: Temporary Bot -> account. */
 final Map<Integer,Integer> roster=new HashMap<>();
 /** playerbot_saved_parties: account -> document. */
 final Map<Integer,String> parties=new HashMap<>();
 /** playerbot_removed: Temporary Bot -> marker. */
 final Map<Integer,Removed> removed=new HashMap<>();
 int commits;
 boolean selfOwned;
 private final Field source;
 private final Object prior;

 private PlayerBotMemoryMetadata()throws Exception {
  source=DatabaseFactory.class.getDeclaredField("dataSource");source.setAccessible(true);prior=source.get(null);
  source.set(null,PlayerBotMetadataCheck.proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?connection():PlayerBotMetadataCheck.zero(m.getReturnType())));
 }
 static PlayerBotMemoryMetadata install()throws Exception {return new PlayerBotMemoryMetadata();}
 /** The players row the DAOs check before every read and write. */
 PlayerBotMemoryMetadata owner(int character,int account) {owners.put(character,account);return this;}
 /** A Temporary Bot: a players row and its playerbot_roster row. */
 PlayerBotMemoryMetadata temporary(int character,int account) {owners.put(character,account);roster.put(character,account);return this;}
 /** Characters without an explicit owner belong to the account with their own id, as the world-free actors do. */
 PlayerBotMemoryMetadata selfOwned() {selfOwned=true;return this;}
 Row row(int character,String section) {return rows.get(character+"/"+section);}
 @Override public void close()throws Exception {source.set(null,prior);}

 private Connection connection() {
  boolean[] autoCommit={true};
  return PlayerBotMetadataCheck.proxy(Connection.class,(p,m,a)->switch(m.getName()) {
   case "getAutoCommit" -> autoCommit[0];
   case "setAutoCommit" -> {autoCommit[0]=(boolean)a[0];yield null;}
   case "commit" -> {commits++;yield null;}
   case "prepareStatement" -> statement((String)a[0]);
   case "createStatement" -> PlayerBotMetadataCheck.proxy(Statement.class,(s,sm,sa)->PlayerBotMetadataCheck.zero(sm.getReturnType()));
   default -> PlayerBotMetadataCheck.zero(m.getReturnType());
  });
 }
 private PreparedStatement statement(String query) {
  var params=new HashMap<Integer,Object>();
  return PlayerBotMetadataCheck.proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().startsWith("set") && a!=null && a.length==2 && a[0] instanceof Integer index){params.put(index,a[1]);return null;}
   if(m.getName().equals("executeQuery"))return result(select(query,params));
   if(m.getName().equals("executeUpdate"))return update(query,params);
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });
 }
 /** The one row a statement finds (its columns in order), or null. */
 private Object[] select(String query,Map<Integer,Object> params) {
  Object first=params.get(1);
  if(query.startsWith("SELECT account_id FROM players WHERE account_id=? UNION"))
   return owners.containsValue(first) || roster.containsValue(first)?new Object[]{first}:null;
  if(query.startsWith("SELECT account_id FROM players WHERE id=?")) {
   Integer owner=owners.getOrDefault(first,selfOwned?(Integer)first:null);return owner==null?null:new Object[]{owner};
  }
  if(query.startsWith("SELECT account_id FROM playerbot_roster WHERE player_id=?") || query.startsWith("SELECT player_id FROM playerbot_roster WHERE player_id=?")) {
   Integer account=roster.get(first);return account==null?null:new Object[]{query.startsWith("SELECT account_id")?account:first};
  }
  if(query.startsWith("SELECT account_id,revision,settings FROM playerbot_metadata")) {
   Row row=rows.get(first+"/"+params.get(2));return row==null?null:new Object[]{row.account(),row.revision(),row.settings()};
  }
  if(query.startsWith("SELECT document FROM playerbot_saved_parties")) {
   String document=parties.get(first);return document==null?null:new Object[]{document};
  }
  if(query.startsWith("SELECT account_id,document FROM playerbot_removed")) {
   Removed marker=removed.get(first);return marker==null?null:new Object[]{marker.account(),marker.document()};
  }
  return null;
 }
 private ResultSet result(Object[] row) {
  boolean[] seen={false};
  return PlayerBotMetadataCheck.proxy(ResultSet.class,(r,m,a)->switch(m.getName()) {
   case "next" -> {boolean next=row!=null && !seen[0];seen[0]=true;yield next;}
   case "getInt" -> ((Number)row[(Integer)a[0]-1]).intValue();
   case "getLong" -> ((Number)row[(Integer)a[0]-1]).longValue();
   case "getString" -> (String)row[(Integer)a[0]-1];
   default -> PlayerBotMetadataCheck.zero(m.getReturnType());
  });
 }
 private int update(String query,Map<Integer,Object> params)throws SQLException {
  if(query.startsWith("INSERT INTO playerbot_metadata")) {
   String key=params.get(1)+"/"+params.get(3);
   if(rows.containsKey(key))throw new SQLException("Duplicate playerbot_metadata row "+key);
   rows.put(key,new Row((Integer)params.get(2),1,(String)params.get(4)));return 1;
  }
  if(query.startsWith("UPDATE playerbot_metadata")) {
   String key=params.get(2)+"/"+params.get(4);Row row=rows.get(key);
   if(row==null || row.account()!=(Integer)params.get(3) || row.revision()!=(Long)params.get(5))return 0;
   rows.put(key,new Row(row.account(),row.revision()+1,(String)params.get(1)));return 1;
  }
  if(query.startsWith("INSERT INTO playerbot_saved_parties")) {parties.put((Integer)params.get(1),(String)params.get(2));return 1;}
  if(query.startsWith("INSERT INTO playerbot_removed")) {
   if(removed.containsKey(params.get(1)))throw new SQLException("Duplicate playerbot_removed row "+params.get(1));
   removed.put((Integer)params.get(1),new Removed((Integer)params.get(2),(String)params.get(3)));return 1;
  }
  return 0;
 }
}
