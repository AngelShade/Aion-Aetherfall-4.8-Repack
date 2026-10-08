package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.model.account.Account;
import sun.misc.Unsafe;

/** Isolated configuration/cache boundaries using recording JDBC; no world, real DB or allocated IDs. */
public final class PlayerBotMetadataPreferencesCheck {
 static int checks,commits,rollbacks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Map<String,String> values(String section,int character){
  var p=new HashMap<String,String>();p.put("account","7");p.put("character",""+character);
  switch(section){
   case "preferences" -> p.putAll(Map.of("role","RANGED","area","true","supplies","true","gear","false","loot","false","questing","false"));
   case "behavior" -> p.put("questCombat","false");
   case "spacing" -> p.putAll(Map.of("follow","2.0","attack","7.0"));
   case "formation" -> p.put("formation","line");
   case "party-rewards" -> p.put("npc.1401","203123");
   default -> throw new AssertionError(section);
  }return p;
 }
 static void rejects(String section,Map<String,String> p)throws Exception{
  try{PlayerBotMetadata.validate(7,Integer.parseInt(p.get("character")),section,p);throw new AssertionError("Invalid "+section+" accepted");}catch(IOException expected){check(true,"Invalid "+section+" refused");}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var db=new PlayerBotMetadataCheck.Jdbc();var data=DatabaseFactory.class.getDeclaredField("dataSource");data.setAccessible(true);var original=data.get(null);
  var cacheField=PlayerBotMetadata.class.getDeclaredField("CACHE");cacheField.setAccessible(true);var cache=(Map<?,?>)cacheField.get(null);
  data.set(null,PlayerBotMetadataCheck.proxy(DataSource.class,(p,m,a)->{
   if(!m.getName().equals("getConnection"))return PlayerBotMetadataCheck.zero(m.getReturnType());
   db.autoCommit=true;var delegate=db.connection();return PlayerBotMetadataCheck.proxy(Connection.class,(c,cm,ca)->{
    if(cm.getName().equals("commit")){commits++;return null;}
    if(cm.getName().equals("rollback")){rollbacks++;return null;}
    try{return cm.invoke(delegate,ca);}catch(InvocationTargetException e){throw e.getCause();}
   });
  }));
  Path directory=Files.createTempDirectory(Path.of("."),"metadata-preferences-");
  try{
   for(String section:List.of("preferences","behavior","spacing","formation","party-rewards")){
    var p=values(section,12001);PlayerBotMetadata.validate(7,12001,section,p);check(true,"Valid "+section+" snapshot accepted");
    var bad=new HashMap<>(p);bad.put("account","8");rejects(section,bad);
   }
   var bad=new HashMap<>(values("preferences",12001));bad.put("role","INVALID");rejects("preferences",bad);
   bad=new HashMap<>(values("preferences",12001));bad.put("loot","yes");rejects("preferences",bad);
   bad=new HashMap<>(values("behavior",12001));bad.put("questCombat","yes");rejects("behavior",bad);
   bad=new HashMap<>(values("spacing",12001));bad.put("follow","NaN");rejects("spacing",bad);
   bad=new HashMap<>(values("spacing",12001));bad.put("attack","19");rejects("spacing",bad);
   bad=new HashMap<>(values("formation",12001));bad.put("formation","INVALID");rejects("formation",bad);
   bad=new HashMap<>(values("party-rewards",12001));bad.put("npc.1401","-1");rejects("party-rewards",bad);
   var prefs=new PlayerBotPreferences(directory);db.stored=values("preferences",12001);
   var loaded=prefs.load(7,12001,PlayerBotRules.Role.MELEE);check(loaded.role()==PlayerBotRules.Role.RANGED&&loaded.area()&&!loaded.gear()&&!loaded.questing(),"Production preference loader preserves native roles/flags");
   int reads=db.queries,writes=db.updates;var desired=new PlayerBotPreferences.Values(PlayerBotRules.Role.SUPPORT,false,true,false,true,true);prefs.save(7,12001,desired);
   check(prefs.load(7,12001,PlayerBotRules.Role.TANK).equals(desired),"Production preference save/reload uses cache");
   check(reads==db.queries&&writes==db.updates,"Preference changes queue without SQL");
   var unsafeField=Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var u=(Unsafe)unsafeField.get(null);
   var account=(Account)u.allocateInstance(Account.class);PlayerBotMetadataCheck.field(account,"id",7);
   var owner=(PlayerBotMetadataCheck.Actor)u.allocateInstance(PlayerBotMetadataCheck.Actor.class);owner.account=account;owner.id=77;
   var bot=(PlayerBotMetadataCheck.Actor)u.allocateInstance(PlayerBotMetadataCheck.Actor.class);bot.account=account;bot.id=12001;
   var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);PlayerBotMetadataCheck.field(session,"owner",owner);PlayerBotMetadataCheck.field(session,"bot",bot);
   db.stored=values("behavior",12001);var behavior=new PlayerBotPartyBehavior.State(session);check(!behavior.enabled,"Production behavior constructor preserves questCombat=false");
   reads=db.queries;behavior.enabled=true;behavior.save();check(db.queries==reads&&db.updates==writes,"Behavior save queues without SQL");
   db.stored=values("spacing",12001);check(PlayerBotSpacing.values(bot).follow()==2&&PlayerBotSpacing.values(bot).attack()==7,"Production spacing loader preserves distances");
   reads=db.queries;PlayerBotSpacing.configure(session,5,12);check(PlayerBotSpacing.values(bot).follow()==5&&PlayerBotSpacing.values(bot).attack()==12,"Production spacing configure updates cache");
   check(db.queries==reads&&db.updates==writes,"Spacing configure queues without SQL");
   db.stored=values("party-rewards",12001);PlayerBotMetadata.load(7,12001,"party-rewards",directory.resolve("missing-rewards"));
   check(PlayerBotMetadata.pending(7,12001).size()==3,"Bot preference/behavior/spacing snapshots join one native checkpoint; clean witnesses remain clean");
   db.stored=values("formation",77);db.autoCommit=true;var formation=PlayerBotMetadataConfiguration.load(7,77,"formation",directory.resolve("missing-formation"));check(formation.getProperty("formation").equals("line"),"Owner formation loads through native DAO");
   formation.setProperty("formation","box");PlayerBotMetadataConfiguration.save(7,77,"formation",formation);check(commits==1&&db.updates==writes+1,"Explicit owner formation commits its own transaction");
   db.failWrite=true;try{PlayerBotMetadataConfiguration.save(7,77,"formation",formation);throw new AssertionError("Failed formation acknowledged");}catch(IOException expected){check(commits==1&&rollbacks==1,"Failed owner formation rolls back and reports failure");}finally{db.failWrite=false;}
   db.stored=values("formation",77);db.failLoad=true;try{PlayerBotMetadataConfiguration.load(7,77,"formation",directory.resolve("missing"));throw new AssertionError("DB failure became defaults");}catch(IOException expected){check(true,"Formation DB failure refuses defaults");}finally{db.failLoad=false;}
   try(var files=Files.list(directory)){check(files.findAny().isEmpty(),"Production adapters create no preference files");}
  }finally{PlayerBotSpacing.close(nullSafeBot());cache.clear();data.set(null,original);Files.delete(directory);}
  System.out.println("OK: "+checks+" native preference/behavior/spacing/formation/witness validation and production adapter checks; isolated recording JDBC only");
 }
 static PlayerBotMetadataCheck.Actor nullSafeBot()throws Exception{
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var actor=(PlayerBotMetadataCheck.Actor)((Unsafe)f.get(null)).allocateInstance(PlayerBotMetadataCheck.Actor.class);actor.id=12001;return actor;
 }
}
