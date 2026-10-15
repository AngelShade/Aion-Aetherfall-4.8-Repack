package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded native admission, blocked-worker isolation and actual stale Service callback.
 * Synthetic actors only; no World, production SQL, casts or ID allocation. */
public final class PlayerBotDecisionWorkersCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void await(CountDownLatch latch){try{if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("Fixture latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
 public static void main(String[] args)throws Exception {
  // These inert active-bot fixtures have no native human-presence World.
  com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_ONLY_OCCUPIED_MAPS=false;
  var work=new PlayerBotBackgroundWork(4);var executor=Executors.newFixedThreadPool(5);
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  var remainingEntered=new CountDownLatch(3);var remainingRelease=new CountDownLatch(1);
  try {
   check(work.available()==4 && !work.busy(),"Four bounded active slots initially available");
   check(work.submit(executor::execute,()->{entered.countDown();await(release);}),"Slow native actor admitted");
   check(entered.await(2,TimeUnit.SECONDS),"First actor deliberately blocked");
   for(int i=0;i<3;i++)check(work.submit(executor::execute,()->{remainingEntered.countDown();await(remainingRelease);}),"Another independent actor admitted");
   check(remainingEntered.await(2,TimeUnit.SECONDS),"All three progress while first actor is blocked");
   check(work.busy() && work.available()==0 && work.running()==4,"No unbounded executor queue");
   check(!work.submit(executor::execute,()->{throw new AssertionError("Fifth callback ran");}),"Fifth callback rejected without queuing");
   work.close();check(work.available()==0 && !work.submit(executor::execute,()->{}),"Shutdown closes every active slot");
   var draining=new AtomicBoolean();var drained=new CountDownLatch(1);
   var waiter=executor.submit(()->{try{draining.set(true);work.awaitCompletion();drained.countDown();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   while(!draining.get())Thread.onSpinWait();
   remainingRelease.countDown();
   check(!drained.await(30,TimeUnit.MILLISECONDS),"Shutdown waits for the blocked actor after siblings finish");
   release.countDown();check(drained.await(2,TimeUnit.SECONDS),"All admitted futures drain");waiter.get(2,TimeUnit.SECONDS);
   check(work.running()==0 && !work.busy(),"All four reservations released exactly once");
  }finally{release.countDown();remainingRelease.countDown();executor.shutdownNow();check(executor.awaitTermination(3,TimeUnit.SECONDS),"Offline workers terminate");}
  var serial=new PlayerBotBackgroundWork();var queued=new ArrayList<Runnable>();
  check(serial.submit(queued::add,()->{}),"Default serial admission retained");
  check(serial.busy() && !serial.submit(queued::add,()->{}),"Party/load/team work remains serial");
  queued.removeFirst().run();check(!serial.busy(),"Serial reservation released");
  serial.close();serial.awaitCompletion();
  var rejecting=new PlayerBotBackgroundWork(4);
  try{rejecting.submit(job->{throw new RejectedExecutionException("Fixture");},()->{});throw new AssertionError("Rejection swallowed");}
  catch(RejectedExecutionException expected){check(rejecting.available()==4 && rejecting.running()==0,"Executor rejection releases admission");}
  try{rejecting.submit(Runnable::run,()->{throw new IllegalStateException("Fixture");});throw new AssertionError("Failure swallowed");}
  catch(IllegalStateException expected){check(rejecting.available()==4 && rejecting.running()==0,"Synchronous job failure cannot release twice");}
  var cancelled=new AtomicBoolean();check(rejecting.submit(queued::add,()->cancelled.set(true)),"Queued job admitted before shutdown");
  rejecting.close();queued.removeFirst().run();rejecting.awaitCompletion();
  check(!cancelled.get() && rejecting.running()==0,"Unstarted native callback cancels and drains after close");
  var history=new PlayerBotBackgroundWork(4);
  check(history.submit(queued::add,()->{}),"One long-lived reservation retained");
  for(int i=0;i<1000;i++)check(history.submit(Runnable::run,()->{}),"Completed siblings release their history");
  var pending=PlayerBotBackgroundWork.class.getDeclaredField("pending");pending.setAccessible(true);
  check(((Set<?>)pending.get(history)).size()==1,"Shutdown retains only unfinished work, not an unbounded future chain");
  history.close();queued.removeFirst().run();history.awaitCompletion();
  for(int size:new int[]{0,1,3,305,1500})for(int slots=0;slots<=4;slots++){
   var actors=new ArrayList<Integer>();for(int i=0;i<size;i++)actors.add(i);
   var batches=PlayerBotDecisionSchedule.batches(actors,slots);var visited=new HashSet<Integer>();
   check(batches.size()==Math.min(size,slots),"Only available bounded slices created");
   for(var batch:batches){int previous=-1;for(int actor:batch){check(actor>previous && visited.add(actor),"Deadline order and unique actor in each slice");previous=actor;}}
   check(visited.size()==(slots==0?0:size),"No ready actor lost across slices");
  }
  // Invoke the real production callback with a stale snapshot. It must release the
  // per-actor claim without running Session.tick or resetting its completion clock.
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  var unsafe=(sun.misc.Unsafe)field.get(null);
  var service=(PlayerBotService)unsafe.allocateInstance(PlayerBotService.class);
  var actor=(PlayerBotDecisionIsolationCheck.Actor)unsafe.allocateInstance(PlayerBotDecisionIsolationCheck.Actor.class);
  actor.control=new PlayerBotFieldCombatCheck.Clock();actor.spawned=true;
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  PlayerBotShutdownCheck.set(session,"bot",actor);PlayerBotShutdownCheck.set(session,"owner",actor);
  PlayerBotShutdownCheck.set(session,"engine",new PlayerBotEngine());
  var sessions=new ConcurrentHashMap<Integer,PlayerBotSession>();sessions.put(actor.getObjectId(),session);
  var decisions=new PlayerBotDecisionSchedule();var execution=new PlayerBotExecution();
  PlayerBotShutdownCheck.set(service,"sessions",sessions);PlayerBotShutdownCheck.set(service,"decisions",decisions);
  PlayerBotShutdownCheck.set(service,"execution",execution);PlayerBotShutdownCheck.set(service,"focusJobId",-1);
  long completed=System.currentTimeMillis()+10000;decisions.complete(actor.getObjectId(),completed);
  var stale=List.of(new PlayerBotDecisionSchedule.Ready<>(session,actor.getObjectId(),1,true));
  var decide=PlayerBotService.class.getDeclaredMethod("decide",List.class);decide.setAccessible(true);decide.invoke(service,stale);
  check(decisions.due(actor.getObjectId(),100)==completed+100,"Stale production callback preserves native reaction deadline");
  check(!execution.busy(actor.getObjectId()),"Skipped stale callback releases native actor exclusion");
  check(execution.claim(actor.getObjectId(),"checkpoint",1),"Native checkpoint can own actor");
  decide.invoke(service,stale);check(execution.busy(actor.getObjectId()),"Duplicate decision cannot remove checkpoint claim");execution.complete(actor.getObjectId());
  System.out.println("OK: "+checks+" bounded active-worker, shutdown, batching and stale native callback checks; no World/SQL/IDs/casts");
 }
}
