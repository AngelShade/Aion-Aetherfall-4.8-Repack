package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Real bounded worker contention/drain and production stale-decision admission;
 * inert actor shells only, no World/IDs/SQL/native casts or server lifecycle. */
public final class PlayerBotActiveWorkerCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var worker=new PlayerBotBackgroundWork(4);var controlled=new PlayerBotBackgroundWork();
  var pool=Executors.newFixedThreadPool(5);var entered=new CountDownLatch(4);var release=new CountDownLatch(1);
  var completed=new AtomicInteger();var drainDone=new AtomicBoolean();Thread drain=null;
  try{
   check(worker.available()==4 && worker.running()==0 && !worker.busy(),"Independent lane starts with bounded free slots");
   for(int i=0;i<4;i++)check(worker.submit(pool,()->{entered.countDown();try{release.await();completed.incrementAndGet();}catch(InterruptedException e){throw new AssertionError(e);}}),"Four independent jobs are admitted "+i);
   check(entered.await(3,TimeUnit.SECONDS),"All four jobs execute concurrently rather than serializing combat");
   check(worker.available()==0 && worker.running()==4 && worker.busy(),"Queued/running work occupies every bounded slot");
   check(!worker.submit(pool,()->{throw new AssertionError("Unbounded backlog");}),"Fifth work item cannot accumulate a backlog");
   var replied=new CountDownLatch(1);check(controlled.submit(pool,replied::countDown) && replied.await(3,TimeUnit.SECONDS),"Companion lane responds during four blocked independent decisions");
   worker.close();controlled.close();check(!worker.submit(pool,()->{}) && worker.available()==0,"Shutdown closes all independent admission");
   drain=new Thread(()->{try{worker.awaitCompletion();controlled.awaitCompletion();drainDone.set(true);}catch(InterruptedException e){throw new AssertionError(e);}});
   drain.start();drain.join(50);check(!drainDone.get(),"Shutdown cannot finish while native work remains admitted");
   release.countDown();drain.join(3000);check(drainDone.get() && completed.get()==4 && worker.running()==0,"Shutdown drains every admitted independent operation");
  }finally{release.countDown();if(drain!=null)drain.join(3000);pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS),"All isolated executor workers terminate");}
  var rejected=new PlayerBotBackgroundWork(4);
  try{rejected.submit(job->{throw new RejectedExecutionException("fixture");},()->{});throw new AssertionError("Missing rejection");}catch(RejectedExecutionException expected){check(rejected.running()==0 && rejected.available()==4,"Executor rejection returns native admission slot exactly once");}
  check(rejected.submit(Runnable::run,()->{}) && rejected.running()==0,"Synchronous completion releases slot without losing future admission");
  rejected.close();rejected.awaitCompletion();check(rejected.running()==0,"Rejected/completed lane remains drainable");
  for(int count:List.of(0,1,3,4,5,17,1000))for(int slots:List.of(0,1,2,4)){
   var ready=new ArrayList<Integer>();for(int i=0;i<count;i++)ready.add(i);
   var batches=PlayerBotDecisionSchedule.batches(ready,slots);var flat=batches.stream().flatMap(List::stream).toList();
   check(batches.size()==Math.min(slots,count),"Only available slots form independent batches");
   check(slots==0?flat.isEmpty():flat.size()==count && new HashSet<>(flat).size()==count,"Every ready actor occurs exactly once across admitted batches");
   for(var batch:batches){check(!batch.isEmpty(),"No worker receives an empty batch");for(int i=1;i<batch.size();i++)check(batch.get(i)>batch.get(i-1),"Each worker retains original deadline order");}
  }
  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(sun.misc.Unsafe)uf.get(null);PlayerBotSupportCooperationCheck.unsafe=unsafe;
  // The actual service admission must skip an old snapshot before calling any
  // native actor decision, even after that snapshot successfully claims an actor.
  PlayerBotSupportCooperationCheck.data=new com.aionemu.gameserver.dataholders.SkillData();
  var bot=PlayerBotSupportCooperationCheck.actor(1999999471);var session=PlayerBotSupportCooperationCheck.session(bot,bot);
  var ctor=PlayerBotService.class.getDeclaredConstructor();ctor.setAccessible(true);var service=ctor.newInstance();
  var sf=PlayerBotService.class.getDeclaredField("sessions");sf.setAccessible(true);((Map<Integer,PlayerBotSession>)sf.get(service)).put(bot.id,session);
  var df=PlayerBotService.class.getDeclaredField("decisions");df.setAccessible(true);var decisions=(PlayerBotDecisionSchedule)df.get(service);
  long future=System.currentTimeMillis()+60000;decisions.complete(bot.id,future);
  var admission=PlayerBotService.class.getDeclaredMethod("decide",List.class);admission.setAccessible(true);
  admission.invoke(service,List.of(new PlayerBotDecisionSchedule.Ready<>(session,bot.id,0,false)));
  check(decisions.due(bot.id,session.reactionDelay())==future+session.reactionDelay(),"Production stale snapshot cannot execute or reset a completed reaction deadline");
  var ef=PlayerBotService.class.getDeclaredField("execution");ef.setAccessible(true);var execution=(PlayerBotExecution)ef.get(service);
  check(!execution.busy(bot.id),"Skipped stale decision still releases per-actor exclusion");
  var aw=PlayerBotService.class.getDeclaredField("activeWorldWork");aw.setAccessible(true);var actual=(PlayerBotBackgroundWork)aw.get(service);
  check(actual.available()==4,"Production service uses bounded parallel active-world lane");
  var cw=PlayerBotService.class.getDeclaredField("controlledWork");cw.setAccessible(true);check(((PlayerBotBackgroundWork)cw.get(service)).available()==1,"Production controlled party lane remains serial");
  System.out.println("OK: "+checks+" native active-worker concurrency/drain/rejection, disjoint deadlines and production stale-decision checks; no World/IDs/SQL/casts");
 }
 private PlayerBotActiveWorkerCheck(){}
}
