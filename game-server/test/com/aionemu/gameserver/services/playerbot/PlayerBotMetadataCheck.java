package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.PlayerBotMetadataDAO;
import com.alibaba.fastjson2.JSON;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Production cache/DAO/checkpoint boundaries with recording JDBC, no live database/world/IDs. */
public final class PlayerBotMetadataCheck {
 static int checks;
 static class Actor extends Player {
  int id;Account account;Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public Account getAccount(){return account;}
 }
 static void field(Object o,String key,Object value)throws Exception {var f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
 static void serializers(Jdbc db,Path directory)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var account=(Account)u.allocateInstance(Account.class);field(account,"id",7);
  var owner=(Actor)u.allocateInstance(Actor.class);owner.account=account;
  var bot=(Actor)u.allocateInstance(Actor.class);bot.id=2001;
  var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);field(session,"owner",owner);field(session,"bot",bot);
  db.stored=null;db.autoCommit=true;
  PlayerBotMetadata.load(7,2001,"care",directory.resolve("no-care-file"));PlayerBotMetadata.load(7,2001,"gear",directory.resolve("no-gear-file"));int reads=db.queries,writes=db.updates;
  var care=(PlayerBotQuestSync.State)u.allocateInstance(PlayerBotQuestSync.State.class);
  field(care,"account",7);field(care,"character",2001);field(care,"managed",new HashSet<>(Set.of(40)));field(care,"ownerCompletions",new HashMap<>(Map.of(40,2)));
  field(care,"approved",new HashSet<>(Set.of(20)));field(care,"skipped",new HashSet<>(Set.of(30)));field(care,"spentDay","2026-10-07");
  care.partySync=true;care.reserve=10000;care.dailyBudget=50000;care.spent=123;care.save();
  var gear=(PlayerBotGearPolicy.State)u.allocateInstance(PlayerBotGearPolicy.State.class);field(gear,"session",session);gear.settings=PlayerBotGearPolicy.DEFAULT;
  field(gear,"generated",new HashSet<>(Set.of(16204,27593)));field(gear,"starterSlots",new HashSet<>(Set.of(16L,32L)));gear.save();
  var rows=PlayerBotMetadata.pending(7,2001);check(rows.size()==2,"Actual care/gear State.save both queue checkpoint metadata");
  check(rows.get(0).values().get("together.40").equals("2") && rows.get(0).values().get("spent").equals("123"),"Production quest witness and spending serialized");
  check(rows.get(0).values().get("enchant").equals("false") && rows.get(0).values().get("approved").equals("20"),"Care consent and opt-in policy retained");
  check(rows.get(1).values().get("generated").equals("16204,27593") && rows.get(1).values().get("starterSlots").equals("16,32"),"Production protected item IDs and starter slots serialized");
  check(rows.get(1).values().get("mode").equals("EARNED") && rows.get(1).values().get("vendors").equals("false"),"Gear acquisition/spending preferences unchanged");
  check(db.queries==reads && db.updates==writes,"Production State.save performs no SQL");
  check(!Files.exists(care.path()) && !Files.exists(gear.path()),"Production State.save creates no runtime settings files");
  gear.save();care.save();check(PlayerBotMetadata.pending(7,2001).equals(rows),"Repeated actual saves coalesce identical metadata snapshots");
 }
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Object zero(Class<?> t){if(!t.isPrimitive() || t==void.class)return null;if(t==boolean.class)return false;if(t==long.class)return 0L;if(t==float.class)return 0f;if(t==double.class)return 0d;return 0;}
 static <T>T proxy(Class<T> t,InvocationHandler h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h));}
 static Map<String,String> care(int owner,int bot){var m=new HashMap<>(Map.of("account",""+owner,"character",""+bot,"partySync","true","enchant","false","salvage","false","reserve","10000","dailyBudget","50000","spent","0","approved","20","skipped","30"));m.put("together.40","2");return m;}
 static class Jdbc {
  int account=7,queries,updates;boolean failLoad,failWrite,autoCommit=true;long revision=1;Map<String,String> stored;List<String> sql=new ArrayList<>();
  Connection connection(){return proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("getAutoCommit"))return autoCommit;
   if(m.getName().equals("setAutoCommit")){autoCommit=(boolean)a[0];return null;}
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);
   if(Set.of("commit","rollback").contains(m.getName()))throw new AssertionError("DAO/cache must not commit caller transaction");return zero(m.getReturnType());
  });}
  PreparedStatement statement(String query){var params=new HashMap<Integer,Object>();return proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().startsWith("set")){params.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeQuery")) {
    queries++;sql.add(query);if(failLoad)throw new SQLException("fixture unavailable database");boolean[] seen={false};
    boolean row=query.contains("FROM players") || stored!=null;
    return proxy(ResultSet.class,(r,rm,ra)->{
     if(rm.getName().equals("next")){boolean next=row && !seen[0];seen[0]=true;return next;}
     if(rm.getName().equals("getInt"))return account;
     if(rm.getName().equals("getLong"))return revision;
     if(rm.getName().equals("getString"))return JSON.toJSONString(stored);
     return zero(rm.getReturnType());
    });
   }
   if(m.getName().equals("executeUpdate")){updates++;sql.add(query);if(failWrite)throw new SQLException("fixture failed transaction");
    if(query.startsWith("UPDATE") && (long)params.get(5)!=revision)return 0;
    return 1;
   }
   return zero(m.getReturnType());
  });}
 }
 static void unavailable(Jdbc db,int id,Path legacy)throws Exception {
  db.failLoad=true;try{PlayerBotMetadata.load(7,id,"care",legacy);throw new AssertionError("Load failure hidden");}
  catch(IOException e){check(e.getCause() instanceof SQLException,"Database failure refuses load rather than reading/defaulting legacy");}finally{db.failLoad=false;}
 }
 @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception {
  var db=new Jdbc();var source=DatabaseFactory.class.getDeclaredField("dataSource");source.setAccessible(true);Object prior=source.get(null);
  source.set(null,proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?db.connection():zero(m.getReturnType())));
  var cacheField=PlayerBotMetadata.class.getDeclaredField("CACHE");cacheField.setAccessible(true);var cache=(Map<Object,Object>)cacheField.get(null);
  Path directory=Files.createTempDirectory(Path.of("."),"metadata-fixture-");Path legacy=directory.resolve("legacy.properties");
  try {
   var values=care(7,1001);var props=PlayerBotMetadata.properties(values);try(var out=Files.newOutputStream(legacy)){props.store(out,"legacy retained");}byte[] original=Files.readAllBytes(legacy);
   var imported=PlayerBotMetadata.load(7,1001,"care",legacy);check(PlayerBotMetadata.values(imported).equals(values),"Validated legacy loaded into cache without defaults/loss");
   check(db.updates==0 && PlayerBotMetadata.pending(7,1001).size()==1,"Legacy queued once; no immediate DB write");
   int reads=db.queries;PlayerBotMetadata.load(7,1001,"care",legacy);check(db.queries==reads,"Warm state does not query DB again");
   props.setProperty("spent","50");PlayerBotMetadata.save(7,1001,"care",props);check(db.queries==reads && db.updates==0,"AI save updates memory only; no SQL/file replacement");
   check(Arrays.equals(original,Files.readAllBytes(legacy)),"Legacy file remains byte-identical");
   var snapshot=PlayerBotMetadata.pending(7,1001);db.autoCommit=false;db.failWrite=true;
   try{PlayerBotMetadata.store(db.connection(),snapshot);throw new AssertionError("Write failure hidden");}catch(SQLException e){check(e.getMessage().contains("failed transaction"),"Checkpoint failure propagated to native save boundary");}
   check(PlayerBotMetadata.pending(7,1001).equals(snapshot),"Failed checkpoint retains exact dirty metadata/revision");
   try{PlayerBotMetadata.release(7,1001);throw new AssertionError("Dirty state discarded");}catch(SQLException e){check(e.getMessage().contains("Unsaved"),"Failed dismissal cannot release cached state");}
   db.failWrite=false;PlayerBotMetadata.store(db.connection(),snapshot);
   check(!PlayerBotMetadata.pending(7,1001).isEmpty(),"Writing SQL does not clean state before caller commit");
   props.setProperty("spent","75");PlayerBotMetadata.save(7,1001,"care",props);PlayerBotMetadata.committed(snapshot);
   var next=PlayerBotMetadata.pending(7,1001);check(next.size()==1 && next.getFirst().revision()==1 && next.getFirst().values().get("spent").equals("75"),"Concurrent newer queued value survives earlier committed snapshot");
   db.revision=1;PlayerBotMetadata.store(db.connection(),next);PlayerBotMetadata.committed(next);check(PlayerBotMetadata.pending(7,1001).isEmpty(),"Successful checkpoint clears only committed values");
   int writes=db.updates;PlayerBotMetadata.save(7,1001,"care",props);PlayerBotMetadata.store(db.connection(),PlayerBotMetadata.pending(7,1001));check(db.updates==writes,"Identical state generates no extra metadata SQL");
   PlayerBotMetadata.release(7,1001);check(cache.isEmpty(),"Clean dismissal releases memory state");
   db.autoCommit=true;db.stored=care(7,1002);db.revision=8;var second=PlayerBotMetadata.load(7,1002,"care",legacy);
   check(second.getProperty("character").equals("1002"),"Database wins over stale legacy file");check(PlayerBotMetadata.pending(7,1002).isEmpty(),"Loaded DB state is clean");
   check(!PlayerBotMetadata.importLegacy(db.connection(),7,1002,"care",PlayerBotMetadata.properties(care(7,1002))),"Repeated migration preserves existing DB values");
   second.setProperty("spent","1");PlayerBotMetadata.save(7,1002,"care",second);var pending=PlayerBotMetadata.pending(7,1002);db.revision=9;db.autoCommit=false;
   try{PlayerBotMetadata.store(db.connection(),pending);throw new AssertionError("Stale revision overwritten");}catch(SQLException e){check(e.getMessage().contains("revision"),"Stale checkpoint cannot overwrite newer database row");}
   check(!PlayerBotMetadata.pending(7,1002).isEmpty(),"Conflict retains dirty state for explicit recovery");
   try{PlayerBotMetadata.pending(8,1002);throw new AssertionError("Wrong owner");}catch(SQLException e){check(e.getMessage().contains("owner"),"Cached account checked at checkpoint");}
   db.autoCommit=true;db.stored=null;unavailable(db,1003,legacy);
   db.account=9;try{PlayerBotMetadata.load(7,1004,"care",legacy);throw new AssertionError("Foreign character loaded");}catch(IOException e){check(e.getCause() instanceof SQLException,"Native character owner query rejects foreign account");}db.account=7;
   try{PlayerBotMetadata.load(7,1005,"care",legacy);throw new AssertionError("Mismatched legacy loaded");}catch(IOException e){check(e.getMessage().contains("Invalid bot"),"Legacy owner validation rejects another character file");}
   var invalid=new HashMap<>(care(7,1006));invalid.put("enchant","yes");try{PlayerBotMetadata.validate(7,1006,"care",invalid);throw new AssertionError("Invalid boolean");}catch(IOException e){check(true,"Malformed consent does not become a default");}
   invalid=new HashMap<>(care(7,1006));invalid.put("spent","-1");try{PlayerBotMetadata.validate(7,1006,"care",invalid);throw new AssertionError("Negative spending");}catch(IOException e){check(true,"Malformed budget rejected");}
   db.autoCommit=true;try{PlayerBotMetadataDAO.write(db.connection(),7,1007,"care",0,care(7,1007));throw new AssertionError("Autocommit accepted");}catch(SQLException e){check(e.getMessage().contains("transaction"),"Caller transaction required");}
   db.stored=care(7,1007);db.revision=0;try{PlayerBotMetadataDAO.load(db.connection(),7,1007,"care");throw new AssertionError("Invalid revision");}catch(SQLException e){check(e.getMessage().contains("Invalid stored"),"Malformed DB state rejected");}
   check(Arrays.equals(original,Files.readAllBytes(legacy)),"All operations leave retained legacy file intact");
   serializers(db,directory);
  }finally{cache.clear();source.set(null,prior);Files.deleteIfExists(legacy);Files.delete(directory);}
  System.out.println("OK: "+checks+" production DAO/cache/checkpoint/migration ownership, failure, revision and dirty-state checks; no live DB/world/IDs.");
 }
}
