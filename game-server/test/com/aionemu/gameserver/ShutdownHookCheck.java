package com.aionemu.gameserver;

import java.util.List;
import com.aionemu.gameserver.configs.main.ShutdownConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import sun.misc.Unsafe;

/** Human grace-period decisions with inert actors; no World or native game IDs. */
public final class ShutdownHookCheck {
 static int checks;
 static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 static final class Actor extends Player {
  boolean bot,staff;
  Actor(){super(null,null);}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public boolean isStaff(){return staff;}
 }
 public static void main(String[] args)throws Exception {
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(Unsafe)field.get(null);
  var human=(Actor)unsafe.allocateInstance(Actor.class);var staff=(Actor)unsafe.allocateInstance(Actor.class);staff.staff=true;
  var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.bot=true;
  for(boolean instant:new boolean[]{true,false}) {
   ShutdownConfig.INSTANT_SHUTDOWN_WITH_ONLY_STAFF_ONLINE=instant;
   check(!ShutdownHook.hasPlayersAwaitingShutdown(List.of()),"Empty server skips grace period");
   check(!ShutdownHook.hasPlayersAwaitingShutdown(List.of(bot)),"Headless population never creates a human countdown");
   check(ShutdownHook.hasPlayersAwaitingShutdown(List.of(human)),"Ordinary human keeps configured grace period");
   check(ShutdownHook.hasPlayersAwaitingShutdown(List.of(human,bot)),"Bots do not remove ordinary human grace period");
   check(ShutdownHook.hasPlayersAwaitingShutdown(List.of(staff))==!instant,"Staff-only configuration preserved");
   check(ShutdownHook.hasPlayersAwaitingShutdown(List.of(staff,bot))==!instant,"Bots do not change staff-only configuration");
   check(ShutdownHook.hasPlayersAwaitingShutdown(List.of(human,staff,bot)),"Mixed server retains human grace period");
  }
  System.out.println("OK: "+checks+" shutdown grace-period checks; no World/DB/native IDs");
 }
}
