package com.aionemu.gameserver.services.playerbot;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A world target can need more memory than the host's heap allows. Near that limit every server thread
 * loses its time to garbage collection, so stop admissions first; timed rotation then lowers the population. */
final class PlayerBotHeapGuard {
 static final int HOLD_PERCENT=70,RELEASE_PERCENT=60;
 private static final Logger log=LoggerFactory.getLogger(PlayerBotHeapGuard.class);
 private final MemoryPoolMXBean oldGeneration=oldGeneration();
 private final long maximum=Runtime.getRuntime().maxMemory();
 private boolean holding;

 static int percent(long used,long maximum){return maximum<=0?0:(int)Math.min(100,Math.max(0,used)*100/maximum);}
 static boolean holding(boolean previously,int percent){return percent>=(previously?RELEASE_PERCENT:HOLD_PERCENT);}
 static int target(int requested,int spawned,boolean holding){return holding?Math.min(requested,Math.max(0,spawned)):requested;}

 synchronized int target(int requested,int spawned) {
  int percent=percent(used(),maximum);
  boolean next=holding(holding,percent);
  if(next!=holding) {
   if(next)log.warn("World bot admissions paused: old heap at {}% of {} MiB with {} bots spawned; target stays at {} until it falls below {}%",percent,maximum>>20,spawned,target(requested,spawned,true),RELEASE_PERCENT);
   else log.info("World bot admissions resumed: old heap at {}% of {} MiB with {} bots spawned",percent,maximum>>20,spawned);
   holding=next;
  }
  return target(requested,spawned,holding);
 }
 synchronized String summary() {
  return "World bot heap: old generation "+(used()>>20)+"/"+(maximum>>20)+" MiB, admissions "+(holding?"paused":"open");
 }
 private long used(){return oldGeneration==null?0:oldGeneration.getUsage().getUsed();}
 private static MemoryPoolMXBean oldGeneration() {
  for(var pool:ManagementFactory.getMemoryPoolMXBeans()) {
   String name=pool.getName();
   if(pool.getType()==MemoryType.HEAP && (name.contains("Old") || name.contains("Tenured")))return pool;
  }
  return null;
 }
}
