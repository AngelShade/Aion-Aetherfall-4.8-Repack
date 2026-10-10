package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real worker contention and shutdown coordination; no native actors, SQL or server startup. */
public final class PlayerBotExecutionCheck {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  var work=new PlayerBotExecution();
  check(work.claim(1,"checkpoint",100),"Checkpoint admitted");
  check(!work.claim(1,"decision",101),"Same actor decision cannot overlap checkpoint");
  check(work.claim(2,"decision",101),"Different actor remains admitted");
  check(work.busy(1) && work.busy(2),"Both independent work owners recorded");
  check(!work.slow(1,30100,30000) && work.slow(1,30101,30000),"Worker health has precise slow threshold");
  check(work.health(1,120).equals("checkpoint running for 20ms"),"Health identifies actual operation");
  work.complete(1);check(work.claim(1,"decision",130),"Completion releases actor admission");
  work.complete(1);work.complete(2);check(!work.busy(1) && work.health(2,150)==null,"Healthy idle actors have no error");
  var checkpoint=new PlayerBotCheckpointSchedule();var ids=Set.of(1,2,3);
  checkpoint.next(ids,0,30000);var first=checkpoint.next(ids,30000,30000);
  check(first!=null,"Checkpoint becomes due");
  var other=checkpoint.next(ids,30000,30000,id->id!=first);
  check(other!=null && !other.equals(first),"Slow checkpoint does not monopolize other due actors");
  check(new PlayerBotPopulationSchedule.Events(0,200,0,1).pendingOffline(199),"Committed retirement intent blocks restart admission before native offline deadline");
  check(!new PlayerBotPopulationSchedule.Events(0,200,0,1).pendingOffline(200),"Offline deadline expiry returns identity to eligibility");
  check(!new PlayerBotPopulationSchedule.Events(300,200,0,1).pendingOffline(199),"A current presence event is not mistaken for retirement intent");
  check(!new PlayerBotPopulationSchedule.Events(0,0,0,1).pendingOffline(199),"Focus relocation without cooldown is not an offline retirement timer");
  check(PlayerBotDecisionSchedule.maximum(400,true)==25,"Running lane completion is checked promptly, not after another configured 400ms batch sleep");
  check(PlayerBotDecisionSchedule.maximum(400,false)==400,"Idle polling retains configured bound");
  check(PlayerBotDecisionSchedule.maximum(0,false)==25,"Invalid tick cannot create an idle busy loop");
  var world=new PlayerBotBackgroundWork();var saving=new PlayerBotBackgroundWork();var controlled=new PlayerBotBackgroundWork();
  var pool=Executors.newFixedThreadPool(3);var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var replied=new CountDownLatch(1);
  Runnable block=()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}};
  try {
   check(world.submit(pool,block),"World lane admitted");check(saving.submit(pool,block),"Checkpoint lane admitted");
   check(entered.await(3,TimeUnit.SECONDS),"Both slow operations execute");
   check(!world.submit(pool,()->{throw new AssertionError("Backlog");}),"World work cannot build a backlog");
   check(!saving.submit(pool,()->{throw new AssertionError("Backlog");}),"Save work cannot build a backlog");
   check(controlled.submit(pool,replied::countDown),"Companion response admitted during world/save contention");
   check(replied.await(3,TimeUnit.SECONDS),"Companion response completes while both slow operations remain blocked");
   check(world.busy() && saving.busy(),"Response did not require slow operations to finish");
   world.close();saving.close();controlled.close();
   check(!controlled.submit(pool,()->{}),"Shutdown closes companion admission");
   var drained=new AtomicBoolean();var drain=new Thread(()->{try{world.awaitCompletion();saving.awaitCompletion();controlled.awaitCompletion();drained.set(true);}catch(InterruptedException e){throw new AssertionError(e);}});
   drain.start();drain.join(50);check(!drained.get(),"Shutdown waits for admitted operations");
   release.countDown();drain.join(3000);check(drained.get(),"Shutdown drains every lane");
  }finally{release.countDown();pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS),"All offline workers terminate");}
  // Exercise production registration/lease/health and the read-only command path
  // with allocated shells. These actors never enter World or allocate native IDs.
  var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
  PlayerBotTravelFormationCheck.unsafe=(sun.misc.Unsafe)unsafeField.get(null);
  var owner=PlayerBotTravelFormationCheck.actor(1999976000);var bot=PlayerBotTravelFormationCheck.actor(1999976001);
  var session=PlayerBotTravelFormationCheck.session(owner,bot);
  var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);var service=constructor.newInstance();
  var sessionField=PlayerBotService.class.getDeclaredField("sessions");sessionField.setAccessible(true);
  @SuppressWarnings("unchecked") var sessions=(Map<Integer,PlayerBotSession>)sessionField.get(service);
  check(service.headlessHealth(bot).contains("unregistered"),"Unregistered headless actor remains an error");
  sessions.put(bot.id,session);check(service.headlessHealth(bot).contains("lease"),"Registered actor without lease remains an error");
  try(var lease=PlayerBotLease.acquire(bot.id)) {
   check(lease!=null,"Isolated lease fixture admitted without ID allocation");
   bot.online=false; // Native Player.isOnline() requires a client; headless actors have none.
   check(service.headlessHealth(bot)==null,"Healthy leased headless actor is not a disconnected client");
   bot.spawned=false;check(service.headlessHealth(bot).contains("outside"),"Broken world lifecycle remains visible");bot.spawned=true;
   PlayerBotTravelFormationCheck.field(session,"closing",true);check(service.headlessHealth(bot).contains("custody/save"),"Custody-held actor remains visible");PlayerBotTravelFormationCheck.field(session,"closing",false);
   var executionField=PlayerBotService.class.getDeclaredField("execution");executionField.setAccessible(true);var executing=(PlayerBotExecution)executionField.get(service);
   executing.claim(bot.id,"checkpoint",System.currentTimeMillis()-40000);check(service.headlessHealth(bot).startsWith("checkpoint running"),"Production health exposes stalled save");executing.complete(bot.id);
   check(service.headlessHealth(bot)==null,"Completed worker health recovers");
   var readers=Executors.newSingleThreadExecutor();
   try {synchronized(service){var result=readers.submit(()->service.companions(owner));check(result.get(3,TimeUnit.SECONDS).equals(List.of(session)),"Actual companion lookup completes while global service monitor is held");}}
   finally{readers.shutdown();readers.awaitTermination(3,TimeUnit.SECONDS);}
   sessions.remove(bot.id);check(service.headlessHealth(bot).contains("unregistered"),"Removed session cannot hide as healthy using an old lease");
  }
  System.out.println("OK: "+checks+" actor/lane contention and health checks; no World/DB/native IDs");
 }
}
