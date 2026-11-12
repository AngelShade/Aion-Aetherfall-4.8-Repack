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
 * In-memory playerbot_metadata table and players ownership for world-free checks. It answers exactly the statements
 * PlayerBotMetadataDAO sends, keeps what was written, and never connects anywhere. Any other statement finds no rows.
 * Companion settings moved from per-character files to this table; checks of those settings install it instead of
 * expecting files.
 */
final class PlayerBotMemoryMetadata implements AutoCloseable {
 record Row(int account,long revision,String settings) {}
 final Map<String,Row> rows=new HashMap<>();
 final Map<Integer,Integer> owners=new HashMap<>();
 int commits;
 boolean selfOwned;
 private final Field source;
 private final Object prior;

 private PlayerBotMemoryMetadata()throws Exception {
  source=DatabaseFactory.class.getDeclaredField("dataSource");source.setAccessible(true);prior=source.get(null);
  source.set(null,PlayerBotMetadataCheck.proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?connection():PlayerBotMetadataCheck.zero(m.getReturnType())));
 }
 static PlayerBotMemoryMetadata install()throws Exception {return new PlayerBotMemoryMetadata();}
 /** The players row the DAO checks before every read and write. */
 PlayerBotMemoryMetadata owner(int character,int account) {owners.put(character,account);return this;}
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
   if(m.getName().equals("executeQuery"))return result(query,params);
   if(m.getName().equals("executeUpdate"))return update(query,params);
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });
 }
 private ResultSet result(String query,Map<Integer,Object> params) {
  Integer owner=query.contains("FROM players")?owners.getOrDefault(params.get(1),selfOwned?(Integer)params.get(1):null):null;
  Row row=query.contains("FROM playerbot_metadata")?rows.get(params.get(1)+"/"+params.get(2)):null;
  boolean[] seen={false};
  return PlayerBotMetadataCheck.proxy(ResultSet.class,(r,m,a)->switch(m.getName()) {
   case "next" -> {boolean next=(owner!=null || row!=null) && !seen[0];seen[0]=true;yield next;}
   case "getInt" -> owner!=null?owner:row!=null?row.account():0;
   case "getLong" -> row==null?0L:row.revision();
   case "getString" -> row==null?null:row.settings();
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
  return 0;
 }
}
