package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Fake-clock population and real deadline policy; no World, actors, IDs or running timers. */
public final class PlayerBotNavigationSearchCheck {
 private static int checks;
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 public static void main(String[] args){
  int actors=1200;int[] old=new int[actors];long[] oldDue=new long[actors];long last=0;
  for(int tick=0;tick<200;tick++)for(int i=0;i<actors;i++){
   long now=100+tick*200L;if(now>=oldDue[i] && now-last>=100){old[i]++;last=now;oldDue[i]=now+3000;}
  }
  check(old[actors-1]==0,"old fixed-order CAS starves later actors despite repeated opportunities");
  Object[] tokens=new Object[actors];Arrays.setAll(tokens,i->new Object());
  var quota=new PlayerBotNavigationSearch();var schedule=new PlayerBotDecisionSchedule();
  long[] nextMove=new long[actors],nextSearch=new long[actors];int[] served=new int[actors];
  long[] lastMove=new long[actors],lastOwnSearch=new long[actors];Arrays.fill(lastMove,-200);Arrays.fill(lastOwnSearch,-3000);
  var ids=java.util.stream.IntStream.range(0,actors).boxed().toList();
  long lastSearch=-100,minSpacing=Long.MAX_VALUE;boolean movedTooSoon=false,searchedTooSoon=false;
  for(long now=0;now<=250000;now+=100){
   long instant=now;
   // Snapshot readiness before any actor executes, as the actual runner does.
   var ready=PlayerBotDecisionSchedule.ready(ids,i->i,i->schedule.due(i,quota.isTurn(tokens[i],instant)?100:3000),i->false,now);
   for(var candidate:ready){
    int i=candidate.value();schedule.complete(i,now);
    // The nomination changes reaction only. Existing local200ms movement and
    // 3s per-actor search gates still decide whether a search can be requested.
    if(now<nextMove[i])continue;
    movedTooSoon|=now-lastMove[i]<200;lastMove[i]=now;nextMove[i]=now+200;
    if(now<nextSearch[i]){quota.cancel(tokens[i]);continue;}
    if(quota.acquire(tokens[i],now)){
     minSpacing=Math.min(minSpacing,now-lastSearch);lastSearch=now;
     searchedTooSoon|=now-lastOwnSearch[i]<3000;lastOwnSearch[i]=now;
     served[i]++;nextSearch[i]=now+3000;
    }
   }
  }
  for(int i=0;i<actors;i++)check(served[i]>=2,"every ordinary3s actor gets repeated bounded turns through nominated100ms scheduling");
  check(minSpacing>=100,"global quota still permits at most one native search per100ms");
  check(!movedTooSoon && !searchedTooSoon,"nomination preserves local movement and search cooldowns");
  check(Arrays.stream(served).max().orElseThrow()-Arrays.stream(served).min().orElseThrow()<=1,"early population IDs cannot monopolize search turns");
  var stale=new PlayerBotNavigationSearch();Object seed=new Object(),head=new Object(),tail=new Object();
  check(stale.acquire(seed,0),"seed uses initial permit");
  check(!stale.acquire(head,1) && !stale.acquire(tail,2),"next intents queue while permit occupied");
  check(stale.isTurn(head,5001),"nominated lease remains valid at boundary");
  check(stale.isTurn(tail,5002),"abandoned living head yields after nominated lease expires");
  check(stale.acquire(tail,5002),"abandoned actor cannot block a real search indefinitely");
  check(stale.acquire(head,5102),"resumed expired actor safely registers a new intent");
  var age=new PlayerBotNavigationSearch();check(age.acquire(seed,0),"queue-age seed admitted");
  check(!age.acquire(head,1) && !age.acquire(tail,2),"young head and tail queued");
  check(age.acquire(head,5001),"head can claim at nominated lease boundary");
  check(age.isTurn(tail,9000),"tail older than5s is retained until its own nomination");
  check(age.acquire(tail,10000),"old population waiter claims without expiry measured from enqueue");
  var cancelled=new PlayerBotNavigationSearch();check(cancelled.acquire(seed,0),"cancellation seed admitted");
  check(!cancelled.acquire(head,1) && !cancelled.acquire(tail,2),"two eligible navigation intents queued");
  check(cancelled.waiting(head) && cancelled.waiting(tail),"pending route work remains visible separately from a geometry rejection");
  cancelled.cancel(head);check(cancelled.isTurn(tail,50),"stop/cast/map-change cancellation promotes remaining waiter");
  check(!cancelled.waiting(head),"cancelled intent no longer reports pending route work");
  check(!cancelled.acquire(tail,99) && cancelled.acquire(tail,100),"cancellation never bypasses remaining global cooldown");
  cancelled.cancel(tail);cancelled.cancel(tail);check(cancelled.acquire(head,200),"repeated cancellation and completed token are harmless");
  var blocked=new PlayerBotNavigationSearch();check(blocked.acquire(seed,0),"blocked-steering seed admitted");
  check(!blocked.acquire(head,1) && !blocked.acquire(tail,2),"blocked but still wanted intents queued");
  for(long now:new long[]{20,40,60,80})check(!blocked.acquire(tail,now) && blocked.isTurn(head,now),"unsuccessful fallback polling cannot erase older wanted ticket");
  check(blocked.acquire(head,100) && blocked.acquire(tail,200),"valid blocked-navigation tickets receive their turns in order");
  System.out.println("OK: "+checks+" nominated bounded navigation search checks");
 }
}
