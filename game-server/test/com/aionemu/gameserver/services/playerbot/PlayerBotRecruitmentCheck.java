package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
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
 public static void main(String[] args)throws Exception {
  // Skip native Player construction: it normally loads real account/inventory
  // dependencies. No instance is registered, spawned, saved or connected.
  Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  Owner owner=(Owner)((Unsafe)field.get(null)).allocateInstance(Owner.class);
  owner.clock=new Clock();owner.online=true;owner.spawned=true;
  PlayerBotConfig.ENABLED=true;PlayerBotConfig.MAX_ACTIVE=100;PlayerBotConfig.MAX_PER_OWNER=5;
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
