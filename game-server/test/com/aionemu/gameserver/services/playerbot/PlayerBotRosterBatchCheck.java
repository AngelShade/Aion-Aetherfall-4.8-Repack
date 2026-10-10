package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;

/** Production joined roster query with recording JDBC; no World, native IDs or database. */
public final class PlayerBotRosterBatchCheck {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static class Jdbc {
  List<Object[]> rows=new ArrayList<>();int queries,prepares;String sql;boolean fail;
  Map<Integer,Integer> params=new TreeMap<>();
  Connection connection(){return PlayerBotMetadataCheck.proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("prepareStatement")){
    prepares++;sql=(String)a[0];params.clear();
    return PlayerBotMetadataCheck.proxy(PreparedStatement.class,(s,sm,sa)->{
     if(sm.getName().equals("setInt")){params.put((Integer)sa[0],(Integer)sa[1]);return null;}
     if(sm.getName().equals("executeQuery")){
      queries++;if(fail)throw new SQLException("Synthetic unavailable DB");int[] index={-1};
      return PlayerBotMetadataCheck.proxy(ResultSet.class,(r,rm,ra)->{
       if(rm.getName().equals("next"))return ++index[0]<rows.size();
       if(Set.of("getObject","getInt","getString","getBoolean").contains(rm.getName())){
        Object value=rows.get(index[0])[(Integer)ra[0]-1];return rm.getName().equals("getInt") && value==null?0:value;
       }return PlayerBotMetadataCheck.zero(rm.getReturnType());
      });
     }
     if(sm.getName().startsWith("execute"))throw new AssertionError("Roster read must not write");
     return PlayerBotMetadataCheck.zero(sm.getReturnType());
    });
   }
   if(Set.of("commit","rollback","setAutoCommit").contains(m.getName()))throw new AssertionError("Roster read must not mutate a transaction");
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });}
 }
 static Object[] row(int account,int id,boolean ready,Integer archived,String marker){return new Object[]{account,id,"Bot"+id,ready,archived,marker};}
 static String marker(int account,int id){return JSON.toJSONString(Map.of("version",1,"account",account,"id",id,"name","Archived","removedAt",1));}
 static void fails(Jdbc db,Collection<Integer> accounts,String why)throws Exception {
  try{PlayerBotRoster.lists(db.connection(),accounts);throw new AssertionError(why);}catch(SQLException|IllegalArgumentException expected){check(true,why);}
 }
 public static void main(String[] args)throws Exception {
  var db=new Jdbc();int world=-1000000;
  db.rows.add(row(7,101,true,null,null));db.rows.add(row(7,102,false,null,null));db.rows.add(row(world,103,true,null,null));
  db.rows.add(row(7,104,true,7,marker(7,104)));
  var result=PlayerBotRoster.lists(db.connection(),List.of(7,world,7,8));
  check(db.queries==1 && db.prepares==1,"Mixed human/world roster and archives share one query");
  check(db.sql.contains("LEFT JOIN playerbot_removed") && db.sql.contains("IN (?,?,?)"),"Bound account cohort and joined archive marker");
  check(new ArrayList<>(db.params.values()).equals(List.of(world,7,8)),"Accounts are deduplicated and bound, including signed world owner");
  check(result.get(7).size()==2 && result.get(7).get(0).id()==101,"Archived identity remains hidden");
  check(!result.get(7).get(1).ready(),"Pending creations remain visible for capacity accounting");
  check(result.get(world).getFirst().id()==103 && result.get(8).isEmpty(),"World identity and empty account results preserved");
  try{result.put(9,List.of());throw new AssertionError("Mutable map");}catch(UnsupportedOperationException expected){check(true,"Snapshot cannot acquire another account");}
  try{result.get(7).clear();throw new AssertionError("Mutable rows");}catch(UnsupportedOperationException expected){check(true,"Snapshot rows immutable");}
  db.rows.removeLast();check(PlayerBotRoster.lists(db.connection(),List.of(7,world)).get(7).size()==2 && db.queries==2,"Reads refresh native visibility instead of caching ownership");
  var bad=new Jdbc();
  for(Object[] row:List.of(row(7,104,true,8,marker(7,104)),row(7,104,true,7,marker(8,104)),row(7,104,true,7,marker(7,105)),
   row(7,104,true,7,null),row(7,104,true,null,marker(7,104)),row(8,104,true,null,null),row(7,0,true,null,null),row(7,104,true,7,"broken"))) {
   bad.rows=List.<Object[]>of(row);fails(bad,List.of(7),"Foreign, malformed and impossible identities fail closed");
  }
  bad.rows=List.of(row(7,104,true,null,null),row(7,104,true,null,null));fails(bad,List.of(7),"Duplicate identity fails closed");
  bad.rows=List.of();bad.fail=true;fails(bad,List.of(7),"SQL failure is not treated as an empty pool");bad.fail=false;
  fails(bad,List.of(0),"Invalid account refused");
  check(PlayerBotRoster.lists(bad.connection(),List.of()).isEmpty(),"Empty cohorts issue no SQL");
  for(int size:List.of(821,2000)) {
   var large=new Jdbc();var accounts=new TreeSet<Integer>();
   for(int i=1;i<=size;i++){int account=7+(i-1)/20;accounts.add(account);large.rows.add(row(account,i,true,null,null));}
   var snapshot=PlayerBotRoster.lists(large.connection(),accounts);
   check(snapshot.values().stream().mapToInt(List::size).sum()==size,"Entire persistent reserve survives bulk visibility");
   check(large.queries==1 && large.params.size()==accounts.size(),"Population-size workload remains one query, not two checks per character");
  }
  System.out.println("OK: "+checks+" roster batch/ownership/archive checks; no world/DB/native IDs");
 }
}
