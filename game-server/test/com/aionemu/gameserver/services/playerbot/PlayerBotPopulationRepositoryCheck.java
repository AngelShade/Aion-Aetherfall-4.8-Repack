package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.nio.file.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.aionemu.gameserver.model.*;

/** Production repository/transaction/import paths with recording JDBC, no actual DB/World/IDs. */
public final class PlayerBotPopulationRepositoryCheck {
 static int checks;
 static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 interface Attempt{void run()throws Exception;}
 static void refused(Attempt action,String why)throws Exception{try{action.run();throw new AssertionError(why);}catch(SQLException|java.io.IOException|IllegalArgumentException expected){checks++;}}
 static final String DIGEST="a".repeat(64);
 static final class Jdbc {
  Map<String,List<List<Object>>> tables=new HashMap<>();
  Map<Integer,Integer> owners=new HashMap<>();Set<Integer> pending=new HashSet<>(),missingPlayers=new HashSet<>();
  List<String> reads=new ArrayList<>(),writes=new ArrayList<>();int commits,rollbacks;boolean failWrite,failCommit,uncertainCommit;
  Jdbc(){for(String name:List.of("state","accounts","members","events"))tables.put("playerbot_population_"+name,new ArrayList<>());tables.put("playerbot_world_residents",new ArrayList<>());}
  Map<String,List<List<Object>>> copy(){var copy=new HashMap<String,List<List<Object>>>();tables.forEach((table,rows)->{var dest=new ArrayList<List<Object>>();rows.forEach(row->dest.add(new ArrayList<>(row)));copy.put(table,dest);});return copy;}
  Connection open(){boolean[] auto={true};var original=new java.util.concurrent.atomic.AtomicReference<Map<String,List<List<Object>>>>();
   return PlayerBotMetadataCheck.proxy(Connection.class,(p,m,a)->{
    switch(m.getName()) {
     case "setAutoCommit":auto[0]=(Boolean)a[0];if(!auto[0])original.set(copy());return null;
     case "getAutoCommit":return auto[0];
     case "commit":commits++;if(uncertainCommit){uncertainCommit=false;original.set(copy());throw new SQLException("Synthetic lost acknowledgement");}if(failCommit)throw new SQLException("Synthetic commit failure");original.set(copy());return null;
     case "rollback":rollbacks++;if(original.get()!=null)tables=original.get();return null;
     case "prepareStatement":String sql=(String)a[0];Map<Integer,Object> params=new TreeMap<>();return PlayerBotMetadataCheck.proxy(PreparedStatement.class,(s,sm,sa)->{
      if(sm.getName().startsWith("set")){params.put((Integer)sa[0],sa[1]);return null;}
      if(sm.getName().equals("executeQuery")){reads.add(sql);return result(query(sql,params));}
      if(sm.getName().equals("executeUpdate")){check(!auto[0],"Every population write uses a transaction");writes.add(sql);if(failWrite)throw new SQLException("Synthetic row failure");return update(sql,params);}
      return PlayerBotMetadataCheck.zero(sm.getReturnType());
     });
    }return PlayerBotMetadataCheck.zero(m.getReturnType());
   });
  }
  ResultSet result(List<List<Object>> rows){int[] index={-1};return PlayerBotMetadataCheck.proxy(ResultSet.class,(p,m,a)->{
   if(m.getName().equals("next"))return ++index[0]<rows.size();
   if(m.getName().startsWith("get")){Object value=rows.get(index[0]).get((Integer)a[0]-1);return switch(m.getName()){case "getInt"->((Number)value).intValue();case "getLong"->((Number)value).longValue();case "getFloat"->((Number)value).floatValue();case "getByte"->((Number)value).byteValue();default->value;};}
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });}
  List<List<Object>> query(String sql,Map<Integer,Object> p){
   if(sql.contains("FROM playerbot_roster")){Integer account=owners.get(p.get(1));return account==null?List.of():List.of(List.of(account,!pending.contains(p.get(1))));}
   if(sql.contains("FROM players")){Integer account=owners.get(p.get(1));return account==null || missingPlayers.contains(p.get(1))?List.of():List.of(List.of(account));}
   String table=sql.split("FROM ")[1].split(" ")[0];var rows=tables.get(table);if(rows==null)throw new AssertionError(sql);
   if(sql.startsWith("SELECT 1 "))return rows.isEmpty()?List.of():List.of(List.of(1));
   if(table.endsWith("state"))return rows.stream().map(row->{if(sql.startsWith("SELECT revision"))return List.of(row.get(4),row.get(5));if(sql.startsWith("SELECT import_digest"))return List.of(row.get(5));if(sql.startsWith("SELECT id"))return List.of(row.get(0));return List.of(row.get(1),row.get(2),row.get(3));}).toList();
   if(table.endsWith("members"))return rows.stream().sorted(Comparator.comparingInt((List<Object> row)->((Number)row.get(1)).intValue()).thenComparingInt(row->((Number)row.get(2)).intValue())).map(row->List.of(row.get(1),row.get(0),row.get(2))).toList();
   return rows;
  }
  int update(String sql,Map<Integer,Object> p){
   if(sql.startsWith("UPDATE playerbot_population_state SET revision")){var row=tables.get("playerbot_population_state").getFirst();if(((Number)row.get(4)).longValue()!=((Number)p.get(1)).longValue())return 0;row.set(4,((Number)row.get(4)).longValue()+1);return 1;}
   if(sql.startsWith("UPDATE playerbot_population_state")){var row=tables.get("playerbot_population_state").getFirst();row.set(1,p.get(1));row.set(2,p.get(2));row.set(3,p.get(3));return 1;}
   if(sql.startsWith("DELETE ")){var rows=tables.get(sql.split("FROM ")[1].split(" ")[0]);return rows.removeIf(row->row.getFirst().equals(p.get(1)))?1:0;}
   String table=sql.split("INTO ")[1].split(" ")[0];var rows=tables.get(table);var row=new ArrayList<Object>(p.values());
   if(table.endsWith("state"))row=new ArrayList<>(List.of(1,p.get(1),p.get(2),p.get(3),0L,p.get(4)));
   final Object id=row.getFirst();rows.removeIf(old->old.getFirst().equals(id));rows.add(row);return 1;
  }
 }
 static PlayerBotWorldPopulation.Pool pool(int account,List<Integer> ids){return new PlayerBotWorldPopulation.Pool(account,"native-pool",(byte)0,123,account<0?Race.ELYOS:Race.ASMODIANS,Gender.MALE,PlayerBotWorldLooks.neutral(),23,ids);}
 static PlayerBotPopulationRepository.Snapshot snapshot(){
  var resident=new PlayerBotWorldRoster.Resident(-1000000,91,210030000,12.25f,23.5f,34.75f,(byte)-3);
  return new PlayerBotPopulationRepository.Snapshot(Map.of(91,resident),Map.of(-1000000,pool(-1000000,List.of(91,92))),Map.of(7,pool(7,List.of(93))),new PlayerBotPopulationSchedule.State(1,2805,777777,Map.of(91,new PlayerBotPopulationSchedule.Events(12345,67890,34567,42))));
 }
 static void seed(Jdbc db)throws Exception{db.owners.put(91,-1000000);db.owners.put(93,7);var c=db.open();c.setAutoCommit(false);PlayerBotPopulationRepository.importSnapshot(c,snapshot(),DIGEST);c.commit();}
 public static void main(String[] args)throws Exception {
  var db=new Jdbc();seed(db);check(db.tables.get("playerbot_population_members").size()==3,"Pending member without player/roster retains creation intent");
  var repository=new PlayerBotPopulationRepository(db::open);check(PlayerBotPopulationRepository.same(snapshot(),new PlayerBotPopulationRepository.Snapshot(repository.residents(),repository.pools(true),repository.pools(false),repository.schedule())),"Native SQL round trip retains exact owners, order, coordinates, templates and milliseconds");
  int reads=db.reads.size();repository.residents();repository.pools(true);repository.schedule();check(db.reads.size()==reads,"Repeated AI reads use the committed cache");
  db.writes.clear();var next=new PlayerBotPopulationSchedule.Events(9,8,7,6);repository.event(91,next);
  check(db.writes.size()==2 && db.writes.getFirst().startsWith("INSERT INTO playerbot_population_events"),"One changed event plus revision, no full population rewrite");
  check(repository.schedule().target()==2805 && repository.schedule().events().get(91).equals(next),"Event update preserves target and deadlines");
  db.writes.clear();repository.event(91,next);check(db.writes.isEmpty(),"Unchanged events issue no SQL writes");
  db.writes.clear();repository.residents(Map.of());check(db.writes.size()==2 && db.writes.getFirst().startsWith("DELETE FROM playerbot_world_residents WHERE"),"Departure removes only residency");
  check(repository.pools(true).get(-1000000).characters().equals(List.of(91,92)),"Departure retains persistent native IDs");
  db.writes.clear();repository.pool(true,pool(-1000000,List.of(91,92)).desired(0));check(db.writes.size()==2 && db.writes.getFirst().contains("population_accounts"),"Desired capacity change retains all member rows");
  db.writes.clear();repository.pool(true,pool(-1000000,List.of(91,92,94)));check(db.writes.size()==3 && db.writes.get(1).contains("population_members"),"New intent appends only one ordered member");
  refused(()->repository.pool(true,pool(-1000000,List.of(92,91,94))),"Existing member order cannot reroll classes");
  refused(()->repository.pool(true,pool(-1000000,List.of(91))),"IDs cannot disappear from persistent pool");
  refused(()->repository.pool(false,pool(-1000000,List.of(91,92,94))),"Scope cannot change");
  refused(()->repository.pool(false,pool(7,List.of(93,91))),"Cross-account duplicate native ID refused");
  refused(()->repository.event(999,next),"Unregistered event refused");
  db.owners.put(92,8);refused(()->repository.event(92,next),"Foreign native owner refuses population writes");db.owners.remove(92);
  var before=repository.schedule();db.failWrite=true;refused(()->repository.event(91,new PlayerBotPopulationSchedule.Events(1,2,3,4)),"Row failure rolls back");db.failWrite=false;
  check(repository.schedule().equals(before) && db.rollbacks>0,"Failed SQL reloads last durable snapshot");
  db.failCommit=true;refused(()->repository.event(91,new PlayerBotPopulationSchedule.Events(5,6,7,8)),"Commit failure refuses cache publication");db.failCommit=false;check(repository.schedule().equals(before),"Failed commit keeps previous timers");
  db.uncertainCommit=true;refused(()->repository.event(91,new PlayerBotPopulationSchedule.Events(5,6,7,8)),"Lost commit acknowledgement is visible");check(repository.schedule().events().get(91).placedLevel()==8,"Unknown commit acknowledgement reloads actual durable outcome");
  db.tables.get("playerbot_population_state").getFirst().set(4,999L);refused(()->repository.event(91,next),"External revision refuses stale cache write");repository.event(91,next);check(repository.schedule().events().get(91).equals(next),"Fresh reload permits safe retry");
  var empty=new Jdbc();refused(()->new PlayerBotPopulationRepository(empty::open).schedule(),"Missing schema/import state is never an empty population");
  var repeat=new Jdbc();seed(repeat);repeat.writes.clear();var repeatConnection=repeat.open();repeatConnection.setAutoCommit(false);PlayerBotPopulationRepository.importSnapshot(repeatConnection,snapshot(),DIGEST);check(repeat.writes.isEmpty(),"Exact existing import is idempotent without overwrite");
  refused(()->PlayerBotPopulationRepository.importSnapshot(repeatConnection,snapshot(),"b".repeat(64)),"Changed legacy file digest refuses reimport");
  repeat.tables.get("playerbot_population_events").getFirst().set(4,43);refused(()->PlayerBotPopulationRepository.importSnapshot(repeatConnection,snapshot(),DIGEST),"Differing native repository refuses overwrite");
  var partial=new Jdbc();partial.tables.get("playerbot_population_events").add(new ArrayList<>(List.of(91,0L,0L,0L,0)));var partialConnection=partial.open();partialConnection.setAutoCommit(false);refused(()->PlayerBotPopulationRepository.importSnapshot(partialConnection,snapshot(),DIGEST),"Partial unmarked import refuses overwrite");
  var foreign=new Jdbc();foreign.owners.put(91,7);var foreignConnection=foreign.open();foreignConnection.setAutoCommit(false);refused(()->PlayerBotPopulationRepository.importSnapshot(foreignConnection,snapshot(),DIGEST),"Foreign legacy owner refuses all import writes");check(foreign.writes.isEmpty(),"Identity preflight occurs before any import write");
  var pending=new Jdbc();pending.owners.put(91,-1000000);pending.pending.add(91);var pendingConnection=pending.open();pendingConnection.setAutoCommit(false);refused(()->PlayerBotPopulationRepository.importSnapshot(pendingConnection,snapshot(),DIGEST),"Resident must be a ready native character");
  // Actual migration parsers, including both factions and optional manual pools.
  var dir=Files.createTempDirectory(Path.of("."),"population-import-");
  try {
   Files.writeString(dir.resolve("world-bots.properties"),"91=-1000000,210030000,12.25,23.5,34.75,-3,1\n");
   Files.writeString(dir.resolve("world-server-population.json"),JSON.toJSONString(snapshot().server().values()));
   Files.writeString(dir.resolve("world-population.json"),JSON.toJSONString(snapshot().manual().values()));
   Files.writeString(dir.resolve("world-population-events.json"),JSON.toJSONString(snapshot().schedule()));
   check(PlayerBotPopulationRepository.same(snapshot(),PlayerBotPopulationLegacy.snapshot(dir)),"Actual four-file legacy import preserves exact native state");
   String original=Files.readString(dir.resolve("world-bots.properties"));Files.writeString(dir.resolve("world-bots.properties"),original+original);refused(()->PlayerBotPopulationLegacy.snapshot(dir),"Duplicate residence cannot be silently discarded");
   Files.writeString(dir.resolve("world-bots.properties"),original.replace(",1\n",",2\n"));refused(()->PlayerBotPopulationLegacy.snapshot(dir),"Future roster version retained for review");
   Files.writeString(dir.resolve("world-bots.properties"),original);Files.writeString(dir.resolve("world-server-population.json"),"null");refused(()->PlayerBotPopulationLegacy.snapshot(dir),"Null legacy document never becomes empty pool");
   Files.writeString(dir.resolve("world-server-population.json"),JSON.toJSONString(List.of(pool(-1000000,List.of(91)),pool(-1000001,List.of(91)))));refused(()->PlayerBotPopulationLegacy.snapshot(dir),"Duplicate identity across faction pools refused");
  }finally{try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
  System.out.println("OK: "+checks+" population SQL/cache/ownership/transaction/migration checks; recording JDBC only; no world/DB/native IDs");
 }
}
