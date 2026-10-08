package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import javax.sql.DataSource;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.model.account.Account;
import sun.misc.Unsafe;

/** Deferred pre-row provenance joins native cache/transaction; recording JDBC and unregistered shells only. */
public final class PlayerBotCreationMetadataCheck {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception {
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var u=(Unsafe)field.get(null);
  var account=(Account)u.allocateInstance(Account.class);PlayerBotMetadataCheck.field(account,"id",7);
  var bot=(PlayerBotMetadataCheck.Actor)u.allocateInstance(PlayerBotMetadataCheck.Actor.class);bot.id=13001;bot.account=account;
  var props=new Properties();props.putAll(Map.of("account","7","character","13001","mode","EARNED","profile","AUTO","quality","RARE","level","0","threshold","1.0","weapon","AUTO","vendors","false","rolls","UPGRADES"));props.setProperty("generated","111,222");props.setProperty("starterSlots","");
  var db=new PlayerBotMetadataCheck.Jdbc();var data=DatabaseFactory.class.getDeclaredField("dataSource");data.setAccessible(true);var original=data.get(null);
  var cacheField=PlayerBotMetadata.class.getDeclaredField("CACHE");cacheField.setAccessible(true);var cache=(Map<?,?>)cacheField.get(null);
  var pendingField=PlayerBotCreationMetadata.class.getDeclaredField("PENDING");pendingField.setAccessible(true);var deferred=(Map<?,?>)pendingField.get(null);
  data.set(null,PlayerBotMetadataCheck.proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?db.connection():PlayerBotMetadataCheck.zero(m.getReturnType())));
  try{
   PlayerBotCreationMetadata.queue(bot,props);check(db.queries==0&&db.updates==0,"Pre-player-row creation queues without database access");
   check(deferred.size()==1,"Creation snapshot retained before native player insertion");
   try{PlayerBotCreationMetadata.queue(bot,props);throw new AssertionError("Duplicate creation accepted");}catch(IOException expected){check(deferred.size()==1,"Duplicate creation cannot overwrite protected IDs");}
   db.failLoad=true;try{PlayerBotCreationMetadata.prepare(bot);throw new AssertionError("Unavailable checkpoint accepted");}catch(IOException expected){check(deferred.size()==1,"Database failure retains deferred snapshot");}db.failLoad=false;
   PlayerBotCreationMetadata.prepare(bot);var pending=PlayerBotMetadata.pending(7,13001);
   check(deferred.isEmpty()&&pending.size()==1,"Native cache takes ownership at first checkpoint");
   check(pending.getFirst().values().get("generated").equals("111,222"),"Every protected generated ID preserved");
   check(db.updates==0,"Preparation never writes SQL outside native inventory transaction");
   db.autoCommit=false;db.failWrite=true;try{PlayerBotMetadata.store(db.connection(),pending);throw new AssertionError("Failed store ignored");}catch(java.sql.SQLException expected){check(PlayerBotMetadata.pending(7,13001).equals(pending),"Failed inventory transaction retains protection metadata");}db.failWrite=false;
   PlayerBotMetadata.store(db.connection(),pending);PlayerBotMetadata.committed(pending);check(PlayerBotMetadata.pending(7,13001).isEmpty(),"Only committed native checkpoint cleans provenance");
   PlayerBotCreationMetadata.prepare(bot);check(deferred.isEmpty(),"Repeated checkpoint does not reinitialize creation state");
  }finally{cache.clear();deferred.clear();data.set(null,original);}
  System.out.println("OK: "+checks+" native creation provenance/checkpoint checks; no real database/world/ID allocation");
 }
}
