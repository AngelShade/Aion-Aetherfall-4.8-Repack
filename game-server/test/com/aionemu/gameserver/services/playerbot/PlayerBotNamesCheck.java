package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.regex.Pattern;
import com.aionemu.gameserver.configs.main.NameConfig;
import com.aionemu.gameserver.model.Gender;

public final class PlayerBotNamesCheck {
 static int checks;
 static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);checks++;}
 interface Work {void run()throws Exception;}
 static void refused(Work work)throws Exception {try{work.run();throw new AssertionError("Refusal missing");}catch(IllegalArgumentException|IllegalStateException|SQLException expected){checks++;}}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
 static Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
 static class Jdbc {
  boolean auto,occupied,failQuery,failUpdate,failHistory;int writes,queries;long historyId=23;int changed=1;
  List<String> sql=new ArrayList<>();Map<Integer,Object> update=new HashMap<>(),history=new HashMap<>();
  Connection connection(){return proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("getAutoCommit"))return auto;
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);return zero(m.getReturnType());
  });}
  PreparedStatement statement(String query){var params=new HashMap<Integer,Object>();sql.add(query);return proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().startsWith("set")){params.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeQuery")){queries++;if(failQuery)throw new SQLException("Name lookup failed");return rows(occupied?1:0,0);}
   if(m.getName().equals("executeUpdate")){
    writes++;if(query.startsWith("UPDATE")){update.putAll(params);if(failUpdate)throw new SQLException("Rename failed");return changed;}
    history.putAll(params);if(failHistory)throw new SQLException("History failed");return 1;
   }
   if(m.getName().equals("getGeneratedKeys"))return rows(historyId>0?1:0,historyId);return zero(m.getReturnType());
  });}
  ResultSet rows(int count,long value){int[] left={count};return proxy(ResultSet.class,(p,m,a)->m.getName().equals("next")?left[0]-->0:m.getName().equals("getLong")?value:zero(m.getReturnType()));}
 }
 public static void main(String[] args)throws Exception {
  var pattern=NameConfig.CHAR_NAME_PATTERN;var words=NameConfig.FORBIDDEN_WORDS;var sequences=NameConfig.FORBIDDEN_SEQUENCE_PATTERN;int days=NameConfig.RESERVE_OLD_NAME_DAYS;
  try {
   NameConfig.CHAR_NAME_PATTERN=Pattern.compile("[a-zA-Z]{2,16}");NameConfig.FORBIDDEN_WORDS=new String[0];NameConfig.FORBIDDEN_SEQUENCE_PATTERN=null;NameConfig.RESERVE_OLD_NAME_DAYS=30;
   var names=new HashSet<String>();int maxAttempts=0;
   for(int id=1;id<=12000;id++) {
    Gender gender=id%2==0?Gender.FEMALE:Gender.MALE;
    String name=PlayerBotNames.choose(id,gender,n->names.contains(n.toLowerCase(Locale.ROOT)));
    if(!name.matches("[A-Z][a-z]{3,15}") || name.startsWith("Wander"))throw new AssertionError("Invalid generated name");
    if(!names.add(name.toLowerCase(Locale.ROOT)))throw new AssertionError("Duplicate generated name");
    if(!name.equals(PlayerBotNames.choose(id,gender,n->false)))maxAttempts++;
   }
   check(names.size()==12000,"Entire maximum persistent reserve has unique pronounceable names");
   check(maxAttempts>0,"Occupied names actually cause bounded alternatives");
   for(Gender gender:Gender.values()) {
    check(PlayerBotNames.candidate(701,gender,0).equals(PlayerBotNames.candidate(701,gender,0)),"Stable seed");
    check(!PlayerBotNames.candidate(701,gender,0).equals(PlayerBotNames.candidate(701,gender,1)),"Independent retry");
   }
   check(!PlayerBotNames.candidate(701,Gender.MALE,0).equals(PlayerBotNames.candidate(701,Gender.FEMALE,0)),"Gender-sensitive output");
   String first=PlayerBotNames.candidate(701,Gender.MALE,0);
   NameConfig.FORBIDDEN_WORDS=new String[]{first};check(!PlayerBotNames.choose(701,Gender.MALE,n->false).equals(first),"Native forbidden word skipped");
   NameConfig.FORBIDDEN_WORDS=new String[0];NameConfig.FORBIDDEN_SEQUENCE_PATTERN=Pattern.compile(first,Pattern.CASE_INSENSITIVE);
   check(!PlayerBotNames.choose(701,Gender.MALE,n->false).equals(first),"Native forbidden sequence skipped");NameConfig.FORBIDDEN_SEQUENCE_PATTERN=null;
   NameConfig.CHAR_NAME_PATTERN=Pattern.compile("Z{2}");refused(()->PlayerBotNames.choose(701,Gender.MALE,n->false));NameConfig.CHAR_NAME_PATTERN=Pattern.compile("[a-zA-Z]{2,16}");
   int[] attempts={0};refused(()->PlayerBotNames.choose(701,Gender.MALE,n->{attempts[0]++;return true;}));check(attempts[0]==512,"Exhaustion bounded without random-letter or ID suffix fallback");
   refused(()->PlayerBotNames.candidate(0,Gender.MALE,0));refused(()->PlayerBotNames.candidate(-1,Gender.MALE,0));refused(()->PlayerBotNames.candidate(1,null,0));refused(()->PlayerBotNames.candidate(1,Gender.MALE,-1));refused(()->PlayerBotNames.candidate(1,Gender.MALE,512));
   String old=PlayerBotNames.legacy(701);
   check(old.equals("Wanderbaz"),"Exact previous ID encoding retained only for migration");
   check(PlayerBotNames.legacyWorld(701,-1000000,old,-1000000,-1000000,true),"Confirmed global legacy actor eligible");
   check(PlayerBotNames.legacyWorld(701,7,old,7,7,true),"Confirmed manually provisioned world actor eligible");
   check(!PlayerBotNames.legacyWorld(701,7,"Customname",7,7,true),"Player-chosen name preserved");
   check(!PlayerBotNames.legacyWorld(701,7,old,null,7,true),"Human/private companion without world pool preserved");
   check(!PlayerBotNames.legacyWorld(701,7,old,7,null,true),"Missing roster refused");
   check(!PlayerBotNames.legacyWorld(701,7,old,8,7,true),"Foreign pool refused");
   check(!PlayerBotNames.legacyWorld(701,7,old,7,8,true),"Foreign roster refused");
   check(!PlayerBotNames.legacyWorld(701,7,old,7,7,false),"Pending creation preserved");
   check(!PlayerBotNames.legacyWorld(701,-1,old,-1,-1,true),"Unreserved signed account refused");
   check(!PlayerBotNames.legacyWorld(702,7,old,7,7,true),"Name must match exact native ID");
   var db=new Jdbc();check(!PlayerBotNames.unavailable(db.connection(),first),"Available native name");db.occupied=true;
   check(PlayerBotNames.unavailable(db.connection(),first),"Native occupied or old-name reservation refused");
   check(db.sql.getFirst().contains("old_names") && db.sql.getFirst().contains("INTERVAL ? DAY"),"Native reservation lifetime shared");
   db.occupied=false;db.failQuery=true;refused(()->PlayerBotNames.unavailable(db.connection(),first));db.failQuery=false;
   check(PlayerBotNames.rename(db.connection(),701,7,old,first)==23,"Native history identity returned within caller transaction");
   check(db.writes==2 && db.update.equals(Map.of(1,first,2,701,3,7,4,old)),"Only requested name and exact native identity updated");
   check(db.history.equals(Map.of(1,701,2,old,3,first)),"Native rename history recorded");
   check(db.sql.stream().anyMatch(s->s.contains("r.ready=TRUE") && s.contains("playerbot_population_members")),"SQL revalidates world membership and ready roster");
   db.auto=true;int before=db.writes;refused(()->PlayerBotNames.rename(db.connection(),701,7,old,first));check(db.writes==before,"Auto-commit writes forbidden");db.auto=false;
   db.occupied=true;refused(()->PlayerBotNames.rename(db.connection(),701,7,old,first));check(db.writes==before,"Colliding rename cannot write");db.occupied=false;
   refused(()->PlayerBotNames.rename(db.connection(),701,7,"Playerchosen",first));check(db.writes==before,"Custom names cannot be migrated");
   db.changed=0;refused(()->PlayerBotNames.rename(db.connection(),701,7,old,first));check(db.writes==before+1,"Failed compare/update never creates false history");db.changed=1;
   db.failHistory=true;refused(()->PlayerBotNames.rename(db.connection(),701,7,old,first));db.failHistory=false;
   db.historyId=0;refused(()->PlayerBotNames.rename(db.connection(),701,7,old,first));
   System.out.println("OK: "+checks+" generated-name/reserve/native-policy/ownership/transaction checks; 12000 simulated unique names; no World/DB/IDs");
  } finally {NameConfig.CHAR_NAME_PATTERN=pattern;NameConfig.FORBIDDEN_WORDS=words;NameConfig.FORBIDDEN_SEQUENCE_PATTERN=sequences;NameConfig.RESERVE_OLD_NAME_DAYS=days;}
 }
}
