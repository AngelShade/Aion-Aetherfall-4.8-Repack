package com.aionemu.gameserver.services.playerbot;

/** Transient manual withdrawal; normal FOLLOW remains a combat/support strategy. */
final class PlayerBotForcedFollow {
 private volatile boolean active;
 private long requested, lastMoving;
 boolean active() { return active; }
 void request(long now) { active=true; requested=lastMoving=now; }
 void cancel() { active=false; }
 boolean regrouped(long now, boolean leaderMoving, boolean needsFollow, boolean combat, boolean safe) {
  if (!active) return false;
  if (leaderMoving || needsFollow || !safe) lastMoving=now;
  // Follow is an explicit withdrawal until regrouped, not a prohibition on
  // fighting for the entire native combat timer after the owner has stopped.
  if (!safe || leaderMoving || needsFollow || now-requested<2000 || now-lastMoving<1000) return false;
  active=false;
  return true;
 }
}
