package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.regex.Pattern;
import com.aionemu.gameserver.configs.main.NameConfig;

/** Simulated JDBC exercises the actual migration transaction and independent fingerprint verifier. */
public final class PlayerBotNameMigrationCheck {
 static int checks;
 static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);checks++;}
 interface Work{void run()throws Exception;}
 static void refused(Work work)throws Exception{try{work.run();throw new AssertionError("Expected refusal");}catch(SQLException|IllegalArgumentException|IllegalStateException expected){checks++;}}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h);}
 static Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
 static class Db {
  final Map<Integer,String> names=new TreeMap<>(Map.of(701,PlayerBotNames.legacy(701),702,"Playerchosen",703,"Humannamed"));
  final List<List<Object>> history=new ArrayList<>();long exp=42;int writes,rollbacks,commits;boolean failHistory;
  Db(){history.add(new ArrayList<>(List.of(8,703,"Humanbefore","Humannamed","date")));}
  Connection connection(){return proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("getAutoCommit"))return false;
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);
   if(m.getName().equals("commit")){commits++;return null;}
   if(m.getName().equals("rollback")){rollbacks++;names.put(701,PlayerBotNames.legacy(701));history.removeIf(x->((Number)x.get(0)).longValue()!=8);return null;}
   return zero(m.getReturnType());
  });}
  PreparedStatement statement(String sql){var params=new HashMap<Integer,Object>();return proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().startsWith("set")){params.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeQuery"))return query(sql,params);
   if(m.getName().equals("executeUpdate")){
    writes++;
    if(sql.startsWith("UPDATE players")){int id=(Integer)params.get(2);if(id!=701 || !names.get(id).equals(params.get(4)))return 0;names.put(id,(String)params.get(1));return 1;}
    if(sql.startsWith("INSERT INTO old_names")){if(failHistory)throw new SQLException("Simulated history failure");history.add(new ArrayList<>(List.of(9,params.get(1),params.get(2),params.get(3),"newdate")));return 1;}
    throw new SQLException("Unexpected mutation");
   }
   if(m.getName().equals("getGeneratedKeys"))return rows(new String[]{"id"},List.of(List.of(9)));
   return zero(m.getReturnType());
  });}
  ResultSet query(String sql,Map<Integer,Object> params)throws SQLException {
   if(sql.startsWith("SELECT name FROM players")) {
    var data=new ArrayList<List<Object>>();names.values().forEach(n->data.add(List.of(n)));return rows(new String[]{"name"},data);
   }
   if(sql.startsWith("SELECT p.id,p.account_id"))return rows(new String[]{"id","account_id","name","gender","pool","roster","ready"},
    List.of(List.of(701,7,names.get(701),"MALE",7,7,true),List.of(702,7,names.get(702),"FEMALE",7,7,true)));
   if(sql.startsWith("SELECT 1 FROM players")) {
    boolean used=names.values().stream().anyMatch(n->n.equalsIgnoreCase((String)params.get(1)));
    return rows(new String[]{"used"},used?List.of(List.of(1)):List.of());
   }
   if(sql.equals("SELECT * FROM players ORDER BY id")) {
    var data=new ArrayList<List<Object>>();names.forEach((id,n)->data.add(List.of(id,n,id==703?9:7,exp)));return rows(new String[]{"id","name","account_id","exp"},data);
   }
   if(sql.equals("SELECT * FROM old_names ORDER BY id"))return rows(new String[]{"id","player_id","old_name","new_name","renamed_date"},history);
   if(sql.equals("SELECT id,player_id,old_name,new_name FROM old_names ORDER BY id"))return rows(new String[]{"id","player_id","old_name","new_name"},history.stream().map(x->x.subList(0,4)).toList());
   if(sql.startsWith("SELECT * FROM playerbot_"))return rows(new String[]{"id","data"},List.of(List.of(701,"unchanged native state")));
   throw new SQLException("Unexpected query: "+sql);
  }
 }
 static ResultSet rows(String[] columns,List<? extends List<?>> rows){int[] index={-1};return proxy(ResultSet.class,(p,m,a)->{
  if(m.getName().equals("next"))return ++index[0]<rows.size();
  if(m.getName().equals("findColumn")){for(int i=0;i<columns.length;i++)if(columns[i].equals(a[0]))return i+1;throw new SQLException("Unknown column");}
  if(m.getName().equals("getMetaData"))return proxy(ResultSetMetaData.class,(q,n,b)->n.getName().equals("getColumnCount")?columns.length:n.getName().equals("getColumnLabel")?columns[(Integer)b[0]-1]:zero(n.getReturnType()));
  if(m.getName().startsWith("get") && a!=null && a[0] instanceof Integer) {
   Object value=rows.get(index[0]).get((Integer)a[0]-1);
   return switch(m.getName()){case "getString"->value==null?null:value.toString();case "getInt"->((Number)value).intValue();case "getLong"->((Number)value).longValue();case "getBoolean"->value;default->value;};
  }
  return zero(m.getReturnType());
 });}
 public static void main(String[] args)throws Exception {
  NameConfig.CHAR_NAME_PATTERN=Pattern.compile("[a-zA-Z]{2,16}");NameConfig.FORBIDDEN_WORDS=new String[0];NameConfig.FORBIDDEN_SEQUENCE_PATTERN=null;NameConfig.RESERVE_OLD_NAME_DAYS=30;
  Path root=Files.createTempDirectory(Path.of("."),"names-migration-check-");
  try {
   var db=new Db();Path success=root.resolve("success");Files.createDirectories(success);
   PlayerBotNameMigration.plan(db.connection(),success);var changes=PlayerBotNameMigration.changes(success);
   check(changes.size()==1 && changes.getFirst().id()==701,"Only exact automatic world name eligible");
   check(db.writes==0 && db.commits==1,"Review reads without native mutation");
   check(Files.readString(success.resolve("restore-names.sql")).contains("AND account_id=7"),"Recovery scoped to native owner/ID/name");
   PlayerBotNameMigration.apply(db.connection(),success);
   check(db.writes==2 && db.history.size()==2,"Single name/history transaction");
   check(db.names.get(702).equals("Playerchosen") && db.names.get(703).equals("Humannamed") && db.exp==42,"Custom/human names and all progress retained");
   check(Files.exists(success.resolve("commit-attempted.txt")) && Files.readString(success.resolve("restore-committed.sql")).contains("DELETE FROM old_names WHERE id=9"),"Exact history recovery and replay marker");
   PlayerBotNameMigration.verify(db.connection(),success);check(Files.exists(success.resolve("verified.txt")),"Committed fingerprint and history independently verified");
   refused(()->PlayerBotNameMigration.apply(db.connection(),success));
   db.exp++;refused(()->PlayerBotNameMigration.verify(db.connection(),success));db.exp--;
   db.history.getLast().set(3,"Wrongname");refused(()->PlayerBotNameMigration.verify(db.connection(),success));db.history.getLast().set(3,changes.getFirst().name());
   var failed=new Db();Path failure=root.resolve("failure");Files.createDirectories(failure);PlayerBotNameMigration.plan(failed.connection(),failure);
   failed.failHistory=true;refused(()->PlayerBotNameMigration.apply(failed.connection(),failure));
   check(failed.rollbacks==1 && failed.names.get(701).equals(PlayerBotNames.legacy(701)) && failed.history.size()==1,"History failure rolls back the actual preceding name update");
   check(!Files.exists(failure.resolve("commit-attempted.txt")),"Precommit failure has no successful/attempted commit marker");
   failed.failHistory=false;failed.exp++;refused(()->PlayerBotNameMigration.apply(failed.connection(),failure));check(failed.writes==2,"Changed snapshot rejected before additional writes");
   Files.writeString(failure.resolve("names.tsv"),"701\t7\tWanderbaz\tExample\n701\t7\tWanderbaz\tAnother\n");refused(()->PlayerBotNameMigration.changes(failure));
   System.out.println("OK: "+checks+" migration transaction/rollback/fingerprint/history/replay checks; synthetic JDBC only");
  }finally{try(var paths=Files.walk(root)){for(var file:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
 }
}
