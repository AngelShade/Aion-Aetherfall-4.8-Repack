package com.aionemu.gameserver.services.playerbot;

/** A travel failure clock measures attempted travel, excluding explicit terrain
 * admission waits. Waiting is not movement progress and awards no arrival credit. */
final class PlayerBotTravelClock {
 private long waitingSince=-1,waited;
 void reset(){waitingSince=-1;waited=0;}
 boolean stalled(long now,long lastProgress,boolean waiting){
  if(waiting){if(waitingSince<0)waitingSince=now;return false;}
  if(waitingSince>=0){waited+=Math.max(0,now-waitingSince);waitingSince=-1;}
  return Math.max(0,now-lastProgress)-waited>=30_000;
 }
}
