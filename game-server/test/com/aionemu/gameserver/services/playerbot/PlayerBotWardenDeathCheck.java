package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import sun.misc.Unsafe;

/** Production death hook with pre-controller identities and recording JDBC; no live SQL/world. */
public final class PlayerBotWardenDeathCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static final class Actor extends Player {
  int id;Account account;Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public Account getAccount(){return account;}
 }
 static final class Captured extends ThreadPoolExecutor {
  final ArrayDeque<Runnable> work=new ArrayDeque<>();
  Captured(){super(1,1,0,TimeUnit.SECONDS,new LinkedBlockingQueue<>());}
  @Override public void execute(Runnable task){work.add(task);}
  void run(){work.remove().run();}
 }
 static final class Jdbc {
  int opens,commits,rollbacks,updates,account=7;boolean missing,failCommit;long recorded;
  Connection connection(){opens++;return PlayerBotMetadataCheck.proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);
   if(m.getName().equals("commit")){if(failCommit)throw new SQLException("fixture commit failure");commits++;}
   if(m.getName().equals("rollback"))rollbacks++;
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });}
  PreparedStatement statement(String sql){return PlayerBotMetadataCheck.proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().equals("setLong"))recorded=(long)a[1];
   if(m.getName().equals("executeUpdate")){updates++;return 1;}
   if(m.getName().equals("executeQuery")){int[] row={0};return PlayerBotMetadataCheck.proxy(ResultSet.class,(r,rm,ra)->{
    if(rm.getName().equals("next"))return !missing && row[0]++==0;
    if(rm.getName().equals("getInt"))return account;
    return PlayerBotMetadataCheck.zero(rm.getReturnType());
   });}
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });}
 }
 private static Actor actor(Unsafe unsafe,int id,Integer account,boolean connected)throws Exception {
  var p=(Actor)unsafe.allocateInstance(Actor.class);p.id=id;p.account=account==null?null:new Account(account);
  if(connected)p.setClientConnection((AionConnection)unsafe.allocateInstance(AionConnection.class));return p;
 }
 public static void main(String[] args)throws Exception {
  // PacketProcessor workers wait uninterruptibly; terminate this isolated fixture JVM on either outcome.
  try{run();System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
 }
 private static void run()throws Exception {
  // Native connections initialize their packet pool; mirror startup defaults before allocating inert clients.
  PlayerBotMetadataCheck.defaults();
  com.aionemu.commons.configuration.ConfigurableProcessor.process(new Properties(),
   com.aionemu.gameserver.configs.network.NetworkConfig.class,ThreadConfig.class);
  var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);var unsafe=(Unsafe)access.get(null);
  PlayerBotWardenConfig.ENABLED=true;PlayerBotConfig.ROAD_DUTY=true;
  var pool=ThreadPoolManager.getInstance();pool.shutdown();
  for(Thread t:Thread.getAllStackTraces().keySet())if(t.getName().equals("DeadLockDetector"))t.interrupt();
  var tasks=new Captured();PlayerBotQuestObjectivesCheck.field(pool,"instantPool",tasks);
  var source=DatabaseFactory.class.getDeclaredField("dataSource");source.setAccessible(true);Object prior=source.get(null);
  var db=new Jdbc();source.set(null,PlayerBotMetadataCheck.proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?db.connection():PlayerBotMetadataCheck.zero(m.getReturnType())));
  var field=PlayerBotWardens.class.getDeclaredField("DEATHS");field.setAccessible(true);
  @SuppressWarnings("unchecked") var deaths=(Map<Integer,Long>)field.get(null);
  try {
   var loading=actor(unsafe,1001,PlayerBotPopulationPolicy.account(com.aionemu.gameserver.model.Race.ELYOS,0),false);
   check(!loading.isPlayerBot(),"Loaded world bot has no controller flag during saved HP restoration");
   PlayerBotWardens.died(loading,loading);
   check(tasks.work.isEmpty() && db.opens==0 && !deaths.containsKey(1001),"Pre-controller world-bot death creates no task, connection or phantom evidence");
   var relog=actor(unsafe,1002,7,false);deaths.put(1002,123L);PlayerBotWardens.died(relog,relog);
   check(tasks.work.isEmpty() && deaths.get(1002)==123L,"Dead human login/owned-alt load preserves committed monster death until entered restores it");
   for(Integer account:new Integer[]{null,0,-7}) {
    var invalid=actor(unsafe,1003,account,true);PlayerBotWardens.died(invalid,invalid);
    check(tasks.work.isEmpty() && db.opens==0 && !deaths.containsKey(1003),"Connected actor without positive account excluded: "+account);
   }
   var bot=actor(unsafe,1004,7,false);bot.setPlayerBotOwner(99);bot.setClientConnection((AionConnection)unsafe.allocateInstance(AionConnection.class));
   PlayerBotWardens.died(bot,bot);check(tasks.work.isEmpty() && db.opens==0,"Owned/generated companion remains excluded even with a connection");
   bot.setClientConnection(null);bot.setWorldBotController(0);PlayerBotWardens.died(bot,bot);
   check(tasks.work.isEmpty() && db.opens==0,"Detached autonomous world controller remains excluded");
   var human=actor(unsafe,1005,7,true);var monster=(Npc)unsafe.allocateInstance(Npc.class);
   check(!human.isPlayerBot() && human.isOnline(),"Ordinary connected human uses native identity predicates");
   deaths.put(1005,123L);PlayerBotWardens.died(human,monster);
   check(!deaths.containsKey(1005) && tasks.work.size()==1 && db.opens==0,"New death invalidates old evidence before asynchronous storage");
   tasks.run();check(db.opens==1 && db.commits==1 && db.updates==2,"Real human death retains native guarded DAO transaction");
   check(deaths.get(1005)>0 && deaths.get(1005)==db.recorded,"Only committed NPC death becomes rescue evidence");
   PlayerBotWardens.died(human,human);tasks.run();check(deaths.get(1005)==0,"Human environment/PvP death clears monster rescue eligibility");
   deaths.put(1005,123L);db.failCommit=true;PlayerBotWardens.died(human,monster);tasks.run();
   check(db.rollbacks==1 && !deaths.containsKey(1005),"Commit failure rolls back SQL and leaves no phantom rescue evidence");db.failCommit=false;
   db.missing=true;PlayerBotWardens.died(human,monster);tasks.run();
   check(db.rollbacks==2 && !deaths.containsKey(1005),"Rare removed-row failure retains DAO guard without cache evidence");db.missing=false;
   db.account=0;PlayerBotWardens.died(human,monster);tasks.run();
   check(db.rollbacks==3 && !deaths.containsKey(1005),"DAO still rejects changed nonhuman account custody");
   int opens=db.opens;PlayerBotWardenConfig.ENABLED=false;PlayerBotWardens.died(human,monster);
   check(tasks.work.isEmpty() && db.opens==opens,"Disabled Wardens perform no death work");
   PlayerBotWardenConfig.ENABLED=true;PlayerBotConfig.ROAD_DUTY=false;PlayerBotWardens.died(human,monster);
   check(tasks.work.isEmpty() && db.opens==opens,"Disabled road duty performs no death work");
  }finally{source.set(null,prior);deaths.clear();tasks.shutdown();}
  System.out.println("OK: "+checks+" Warden death checks");
 }
}
