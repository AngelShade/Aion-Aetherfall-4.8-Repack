package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.Executor;

/** Actual service deadlines and single-worker transitions; no actors, World, DB or native IDs. */
public final class PlayerBotPopulationPipelineCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Field field(String name)throws Exception{var f=PlayerBotService.class.getDeclaredField(name);f.setAccessible(true);return f;}
 public static void main(String[] args)throws Exception {
  var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);
  var service=constructor.newInstance();
  var next=field("nextServerPopulation");var manual=field("nextPopulation");var load=field("nextWorldLoad");
  var worker=(PlayerBotBackgroundWork)field("backgroundWork").get(service);
  var queue=new ArrayList<Runnable>();Executor executor=queue::add;
  next.setLong(service,5000);manual.setLong(service,6000);load.setLong(service,7000);
  check(!service.serverPopulationDue(4999),"Idle manager retains its normal interval");
  check(service.serverPopulationDue(5000),"Normal interval expires exactly");
  check(worker.submit(executor,service::worldWorkCompleted),"One native completion queued");
  check(!service.serverPopulationDue(5000),"A busy worker cannot consume the due manager interval");
  check(next.getLong(service)==5000,"Pending native initialization retains the manager deadline");
  check(!worker.submit(executor,()->{throw new AssertionError("Overlapping native job");}),"Busy worker rejects an initialization burst");
  queue.removeFirst().run();
  check(next.getLong(service)==0 && manual.getLong(service)==0 && load.getLong(service)==0,"Successful native work wakes global/manual admission and saved loading together");
  check(service.serverPopulationDue(5001),"Successful load does not add a five-second wait before the next character");
  next.setLong(service,9000);manual.setLong(service,10000);load.setLong(service,11000);
  check(worker.submit(executor,()->{throw new IllegalStateException("Synthetic native failure");}),"Failure admitted as one bounded job");
  try{queue.removeFirst().run();throw new AssertionError("Failure swallowed");}catch(IllegalStateException expected){}
  check(!worker.busy() && next.getLong(service)==9000 && manual.getLong(service)==10000 && load.getLong(service)==11000,"Failure releases worker without erasing retry or manager deadlines");
  check(!service.serverPopulationDue(8999),"Failed work cannot start a rapid retry loop");
  // A fresh completion after every saved load must release the due population
  // interval at every pool size; callback costs here are deliberately artificial.
  for(int i=0;i<1000;i++) {
   long now=12000L+i*500L;next.setLong(service,now+5000);
   if(!worker.submit(executor,service::worldWorkCompleted))throw new AssertionError("Worker not released at "+i);
   if(service.serverPopulationDue(now+5000))throw new AssertionError("Busy manager at "+i);
   queue.removeFirst().run();
   if(!service.serverPopulationDue(now+500) || next.getLong(service)!=0)throw new AssertionError("Stale successful-load delay at "+i);
  }
  check(queue.isEmpty() && !worker.busy(),"1000 completions drain without a queued burst or stale population interval");
  worker.close();worker.awaitCompletion();
  check(!worker.submit(executor,service::worldWorkCompleted),"Shutdown drain remains closed to new native jobs");
  // Reproduce a saved 805 target below the user's new 2800..3000 range while
  // the single native loader is occupied. Only target SQL/cache changes; events remain identical.
  var storage=new PlayerBotPopulationTestStorage();var schedule=new PlayerBotPopulationSchedule(storage);
  var events=java.util.Map.of(12,new PlayerBotPopulationSchedule.Events(30000,0,90000,6));
  schedule.save(new PlayerBotPopulationSchedule.State(1,805,999999,events));
  var settings=new PlayerBotPopulationPolicy.Settings(2800,3000,120,50,1,65,10,65,true,1,false,true,600,28800,600,28800,1800,7200,3600,18000,35);
  var changed=schedule.reconcile(1000,settings,true,new java.util.Random(7));
  check(changed.target()>=2800 && changed.target()<=3000,"Stale 805 target reconciled before saved residents load");
  check(changed.events().equals(events),"Presence/absence/placement events and native identities retained");
  check(changed.targetUntil()>=1801000 && changed.targetUntil()<=7201000,"New target retains pinned lifetime bounds");
  var reused=schedule.reconcile(1001,settings,true,new java.util.Random(999));
  check(reused.equals(changed),"Valid unexpired target is not rerolled at startup");
  var disabled=schedule.reconcile(99999999,settings,false,new java.util.Random(7));
  check(disabled.equals(changed),"Disabling population does not erase its saved target/events");
  var expired=schedule.reconcile(changed.targetUntil(),settings,true,new java.util.Random(7));
  check(expired.targetUntil()>changed.targetUntil() && expired.events().equals(events),"Expiry retains events and refreshes target lifetime");
  var lower=new PlayerBotPopulationPolicy.Settings(40,60,12,50,1,65,10,65,true,1,false,true,600,28800,600,28800,1800,7200,3600,18000,35);
  var reduced=schedule.reconcile(2000,lower,true,new java.util.Random(7));
  check(reduced.target()>=40 && reduced.target()<=60 && reduced.events().equals(events),"Lower settings also reconcile without resetting characters");
  var fixed=new PlayerBotPopulationPolicy.Settings(3000,3000,120,50,1,65,10,65,true,1,false,true,600,28800,600,28800,1800,7200,3600,18000,35);
  check(schedule.reconcile(3000,fixed,true,new java.util.Random(7)).target()==3000,"Fixed 3000 target is exact");
  System.out.println("OK: "+checks+" population pipeline checks; 1000 artificial completions; no World/DB/native IDs");
 }
}
