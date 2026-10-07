package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Order;

/** Bounded follow timer/order checks without native movement or world mutation. */
public final class PlayerBotFollowRecoveryCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  var s=new PlayerBotFollowRecovery.State();s.mark=10000;s.last=16000;
  check(PlayerBotFollowRecovery.stalled(s,16000,10),"Six-second fresh stall");
  check(!PlayerBotFollowRecovery.stalled(s,15999,10),"Backward clock cannot count as fresh observation");
  check(!PlayerBotFollowRecovery.stalled(s,17501,10),"Non-follow gap expires observation");
  check(!PlayerBotFollowRecovery.stalled(s,16000,6),"Nearby formation is not relocated");
  check(!PlayerBotFollowRecovery.stalled(s,16000,2.8,false),"Visible short gap is not relocated");
  check(PlayerBotFollowRecovery.stalled(s,16000,2.8,true),"Short portal across a wall can recover");
  check(!PlayerBotFollowRecovery.stalled(s,16000,1.6,true),"At owner tolerance even a blocked gap is ignored");
  check(!PlayerBotFollowRecovery.stalled(s,16000,Double.NaN),"Invalid distance rejected");
  check(!PlayerBotFollowRecovery.stalled(null,16000,10),"Missing state is not a stall");
  for(Order order:Order.values())check(PlayerBotFollowRecovery.follows(order)==(order==Order.FOLLOW || order==Order.PASSIVE),"Explicit "+order+" permission");
  s.last=15000;check(!PlayerBotFollowRecovery.stalled(s,15999,10),"Short stall duration rejected");
  System.out.println("OK: "+checks+" follow timer/order checks; no live movement");
 }
}
