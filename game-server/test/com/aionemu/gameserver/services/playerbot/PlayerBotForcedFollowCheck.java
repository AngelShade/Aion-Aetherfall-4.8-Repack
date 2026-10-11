package com.aionemu.gameserver.services.playerbot;

/** Clock-driven withdrawal acceptance without native actors, World or IDs. */
public final class PlayerBotForcedFollowCheck {
 private static int count;
 private static void check(boolean pass, String label) { count++; if (!pass) throw new AssertionError(label); }
 public static void main(String[] args) throws Exception {
  var f=new PlayerBotForcedFollow();
  check(!f.active(), "ordinary FOLLOW permits normal AI");
  check(!f.regrouped(10000,false,false,false,true), "normal AI cannot finish unrequested withdrawal");
  f.request(10000);
  check(f.active(), "manual Follow engages withdrawal");
  check(!f.regrouped(11999,false,false,false,true), "minimum hold allows player to begin moving after clicking");
  check(!f.regrouped(12000,true,false,false,true), "moving leader retains override even when close");
  check(!f.regrouped(12500,false,true,false,true), "lagging companion retains override");
  check(!f.regrouped(13499,false,false,false,true), "brief movement pause cannot reengage a target");
  check(!f.regrouped(13500,false,false,true,true), "native combat retains withdrawal after arrival");
  check(!f.regrouped(14000,false,false,false,false), "hazard or movement restriction cannot finish regrouping");
  check(!f.regrouped(14999,false,false,false,true), "quiet interval begins after safe arrival");
  check(f.regrouped(15000,false,false,false,true), "safe stationary arrival out of combat resumes AI");
  check(!f.active(), "completed withdrawal is transient");
  f.request(20000); f.request(21999);
  check(!f.regrouped(22000,false,false,false,true), "repeated Follow renews explicit hold");
  check(f.regrouped(23999,false,false,false,true), "renewed command can finish normally");
  f.request(30000); f.cancel();
  check(!f.active(), "Attack or another order cancels override");
  check(!f.regrouped(40000,false,false,false,true), "cancelled override cannot consume an ordinary turn");
  f.request(50000);
  check(!f.regrouped(49999,false,false,false,true), "clock reversal does not prematurely resume AI");
  check(f.regrouped(52000,false,false,false,true), "clock recovery allows completion");
  check(java.lang.reflect.Modifier.isSynchronized(PlayerBotSession.class.getMethod("command",PlayerBotRules.Order.class).getModifiers()), "manual command serializes with native decisions");
  check(java.lang.reflect.Modifier.isSynchronized(PlayerBotSession.class.getMethod("order",PlayerBotRules.Order.class).getModifiers()), "automatic order entrypoint remains compatible");
  if(count!=20)throw new AssertionError("wrong check count "+count);
  System.out.println("OK: 20 manual withdrawal, regroup/combat/movement safety, repeated command and native entrypoint checks");
 }
}
