package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.sql.*;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import sun.misc.Unsafe;

/** Invoke the real recruitment guard with synthetic players, never live data. */
public final class PlayerBotRecruitmentCheck {
 private static int checks;
 private static final class Clock extends PlayerController {
  long lastCombat;
  @Override public long getLastCombatTime(){return lastCombat;}
 }
 private static final class Owner extends Player {
  Clock clock;boolean online,spawned;
  Owner(){super(null,null);}
  @Override public PlayerController getController(){return clock;}
  @Override public boolean isOnline(){return online;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isInAlliance(){return false;}
  @Override public boolean isInsidePvPZone(){throw new AssertionError("Recruitment must not inspect PvP location");}
  @Override public boolean isFlying(){throw new AssertionError("Recruitment must not inspect flight state");}
  @Override public PlayerGroup getPlayerGroup(){return null;}
 }
 private static void expect(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static void starterChecks()throws Exception {
  Class.forName("com.mysql.cj.jdbc.Driver");
  String password=System.getenv("AETHERFALL_TICKET_TEST_PASSWORD");
  if(password==null)throw new IllegalArgumentException("Temporary-table check requires its database credential");
  try(var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:33306/aetherfall_gs?connectTimeout=5000&socketTimeout=15000&serverTimezone=UTC","root",password)) {
   String expected=System.getenv("AETHERFALL_TICKET_TEST_DATA");
   if(expected==null)throw new IllegalArgumentException("Expected repack data directory is required");
   try(var s=c.createStatement();var result=s.executeQuery("SELECT @@datadir")) {
    result.next();if(!java.nio.file.Path.of(result.getString(1)).toRealPath().equals(java.nio.file.Path.of(expected).toRealPath()))
     throw new IllegalStateException("This connection is not the requested repack database");
   }
   // These two names shadow real tables on this connection only. Closing it drops both.
   try(var s=c.createStatement()) {
    s.execute("CREATE TEMPORARY TABLE playerbot_ticket_wallet LIKE aetherfall_gs.playerbot_ticket_wallet");
    s.execute("CREATE TEMPORARY TABLE playerbot_ticket_receipts LIKE aetherfall_gs.playerbot_ticket_receipts");
   }
   c.setAutoCommit(false);int account=2_000_000_001;
   expect(PlayerBotRecruitmentDAO.starter(c,account,0)==null,"Zero disables a starter grant");
   expect(PlayerBotRecruitmentDAO.wallet(c,account).tickets()==0,"Disabled grant creates no balance");
   var first=PlayerBotRecruitmentDAO.starter(c,account,50);c.commit();
   expect(first.ticketsAfter()==50 && first.kind().equals("STARTER") && !first.replayed(),"New account receives fifty with an audited starter receipt");
   var repeat=PlayerBotRecruitmentDAO.starter(c,account,50);c.commit();
   expect(repeat.replayed() && PlayerBotRecruitmentDAO.wallet(c,account).tickets()==50,"Relog-like repeat cannot claim again");
   PlayerBotRecruitmentDAO.starter(c,account,500);c.commit();
   expect(PlayerBotRecruitmentDAO.wallet(c,account).tickets()==50,"Changing the configured amount does not grant again");
   PlayerBotRecruitmentDAO.recycle(c,account,"test:recycle");c.commit();
   PlayerBotRecruitmentDAO.starter(c,account,50);c.commit();
   expect(PlayerBotRecruitmentDAO.wallet(c,account).credit()==33,"Existing fractional recycling credit is retained");
   PlayerBotRecruitmentDAO.grant(c,account+1,"test:existing","ADMIN",1000);c.commit();
   PlayerBotRecruitmentDAO.starter(c,account+1,50);c.commit();
   expect(PlayerBotRecruitmentDAO.wallet(c,account+1).tickets()==1050,"Existing balances are added to rather than reset");
   PlayerBotRecruitmentDAO.starter(c,account+1,50);c.commit();
   expect(PlayerBotRecruitmentDAO.wallet(c,account+1).tickets()==1050,"Existing account receives only one starter grant");
   PlayerBotRecruitmentDAO.starter(c,account+2,50);c.rollback();
   expect(PlayerBotRecruitmentDAO.wallet(c,account+2).tickets()==0,"Rollback retains neither an awarded wallet nor receipt");c.rollback();
   PlayerBotRecruitmentDAO.starter(c,account+2,50);c.commit();
   expect(PlayerBotRecruitmentDAO.wallet(c,account+2).tickets()==50,"An uncommitted grant remains retryable");
   try(var s=c.createStatement();var result=s.executeQuery("SELECT COUNT(*) FROM playerbot_ticket_receipts WHERE kind='STARTER'")) {
    result.next();expect(result.getInt(1)==3,"Exactly one committed starter receipt per test account");
   }
  }
 }
 public static void main(String[] args)throws Exception {
  if(args.length==1 && args[0].equals("--starter-temporary-tables")) {
   starterChecks();System.out.println("OK: "+checks+" native starter transaction checks; connection-only temporary tables, no real account balances changed");return;
  }
  // Skip native Player construction: it normally loads real account/inventory
  // dependencies. No instance is registered, spawned, saved or connected.
  Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  Owner owner=(Owner)((Unsafe)field.get(null)).allocateInstance(Owner.class);
  owner.clock=new Clock();owner.online=true;owner.spawned=true;
  PlayerBotConfig.ENABLED=true;PlayerBotConfig.MAX_ACTIVE=100;PlayerBotConfig.MAX_PER_OWNER=5;
  PlayerBotConfig.GENERATED_ENABLED=false;
  expect(!PlayerBotCreationPolicy.recruitmentAllowed(true,false),"Disabled Temporary Bots cannot be newly recruited");
  expect(PlayerBotCreationPolicy.recruitmentAllowed(true,true),"Ticket Recruits retain recruitment when Temporary Bots are disabled");
  expect(PlayerBotCreationPolicy.recruitmentAllowed(false,false),"Owned account characters remain recruitable");
  try{PlayerBotCreationPolicy.requireRecruitment(true,false);throw new AssertionError("Temporary Bot gate allowed recruitment");}
  catch(IllegalArgumentException expected){expect(expected.getMessage().contains("Temporary Bot recruitment is disabled"),"Disabled recruitment has a player-facing reason");}
  PlayerBotConfig.GENERATED_ENABLED=true;
  expect(PlayerBotCreationPolicy.recruitmentAllowed(true,false),"Enabling the configuration restores Temporary Bot recruitment");
  Method guard=PlayerBotService.class.getDeclaredMethod("ensureOwner",Player.class);guard.setAccessible(true);
  guard.invoke(PlayerBotService.getInstance(),owner);expect(true,"PvP/flight map flags cannot block idle recruitment");
  expect(!PlayerBotSession.inPvp(owner),"PvP compatibility helper no longer blocks companions");
  owner.clock.lastCombat=System.currentTimeMillis();
  try{guard.invoke(PlayerBotService.getInstance(),owner);throw new AssertionError("Combat allowed recruitment");}
  catch(InvocationTargetException error){expect(error.getCause() instanceof IllegalArgumentException && error.getCause().getMessage().contains("while in combat"),"Active combat has a specific refusal");}
  owner.clock.lastCombat=System.currentTimeMillis()-11000;
  guard.invoke(PlayerBotService.getInstance(),owner);expect(true,"Recruitment resumes after native 10-second combat window");
  owner.online=false;
  try{guard.invoke(PlayerBotService.getInstance(),owner);throw new AssertionError("Offline character allowed");}
  catch(InvocationTargetException error){expect(error.getCause().getMessage().contains("in the world"),"Disconnected character cannot spawn companions");}
  System.out.println("OK: "+checks+" actual recruitment guard checks; native combat timer, idle recruitment, no PvP/flight queries and disconnected owner rejection");
 }
}
