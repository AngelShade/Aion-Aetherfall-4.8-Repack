package com.aionemu.gameserver.services.playerbot;

/** Keep a native movement commitment responsive through arrival and the next decision. */
final class PlayerBotMovementCadence {
 private volatile long handoffUntil;
 void moving(long now){handoffUntil=now+600;}
 void clear(){handoffUntil=0;}
 boolean active(long now,boolean resting,boolean moving){
  if(resting)return false;
  if(moving)moving(now);
  return moving || now<handoffUntil;
 }
 static long reaction(long ordinary,boolean moving){return moving?Math.min(ordinary,200):ordinary;}
}
