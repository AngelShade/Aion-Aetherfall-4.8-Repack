package com.aionemu.gameserver.services.playerbot;

/** Opening selection and end-of-match objective scoring for a Dredgion party. */
final class PlayerBotDredgionTactics {
 enum Opening {
  CLEAR_LANE,
  RUSH_GRAVITY,
  BLOCK_OPPONENT
 }

 private static final long LATE_WINDOW_MS=120_000;

 static Opening opening(int draw,boolean canRush,boolean canBlock) {
  if(!canRush)return Opening.CLEAR_LANE;
  return switch(Math.floorMod(draw,canBlock ? 3 : 2)) {
   case 0 -> Opening.CLEAR_LANE;
   case 1 -> Opening.RUSH_GRAVITY;
   default -> Opening.BLOCK_OPPONENT;
  };
 }

 static boolean late(long remainingMillis) {
  return remainingMillis>0 && remainingMillis<=LATE_WINDOW_MS;
 }

 /**
  * Rank objectives whose estimated approach fits the remaining time. A strict projected lead wins the
  * first comparison, with points per estimated second deciding between candidates in the same score position.
  */
 static double lateScore(int value,double seconds,int deficit,long remainingMillis) {
  if(!late(remainingMillis) || value<=0 || !Double.isFinite(seconds) || seconds<0 || seconds>=remainingMillis/1000d)return 0;
  double rate=value/(seconds+5);
  double efficiency=rate/(rate+100);
  return (value>deficit ? 1 : 0)+efficiency;
 }

 private PlayerBotDredgionTactics() {}
}
