package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.State;

/** Actual lane selection and committed cache during a blocked recording-JDBC commit.
 * No production SQL, native World, casts or ID allocation. */
public final class PlayerBotDecisionIsolationCheck {
 static class Actor extends PlayerBotFieldCombatCheck.Actor {
  @Override public boolean canPerformMove(){return true;}
 }
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  var unsafe=(sun.misc.Unsafe)field.get(null);
  var service=(PlayerBotService)unsafe.allocateInstance(PlayerBotService.class);
  var controlled=new PlayerBotBackgroundWork();var active=new PlayerBotBackgroundWork();var idle=new PlayerBotBackgroundWork();
  PlayerBotShutdownCheck.set(service,"controlledWork",controlled);
  PlayerBotShutdownCheck.set(service,"activeWorldWork",active);
  PlayerBotShutdownCheck.set(service,"autonomousWork",idle);
  var actor=(Actor)unsafe.allocateInstance(Actor.class);
  actor.control=new PlayerBotFieldCombatCheck.Clock();
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  PlayerBotShutdownCheck.set(session,"bot",actor);PlayerBotShutdownCheck.set(session,"owner",actor);
  var engine=new PlayerBotEngine();PlayerBotShutdownCheck.set(session,"engine",engine);
  var selection=PlayerBotService.class.getDeclaredMethod("decisionWork",PlayerBotSession.class);selection.setAccessible(true);
  check(selection.invoke(service,session)==idle,"Idle independent work selects idle lane");
  actor.control.last=System.currentTimeMillis();
  check(selection.invoke(service,session)==active,"Native combat selects active lane");actor.control.last=0;
  engine.tick(State.COMBAT,List.of(),List.of(),1);
  check(selection.invoke(service,session)==active,"Committed engine combat remains active without client combat flag");
  engine.tick(State.NON_COMBAT,List.of(),List.of(),1);actor.looting=true;
  check(selection.invoke(service,session)==active,"Native looting stays active after combat");actor.looting=false;
  PlayerBotShutdownCheck.set(session,"questTarget",1);
  check(selection.invoke(service,session)==active,"Selected field pull remains active before first damage");
  PlayerBotShutdownCheck.set(session,"owner",unsafe.allocateInstance(PlayerBotFieldCombatCheck.Actor.class));
  check(selection.invoke(service,session)==controlled,"Controlled world actor retains serial party lane");
  // Actual nomination -> Session reaction -> Service lane, including summon turns.
  PlayerBotShutdownCheck.set(session,"owner",actor);PlayerBotShutdownCheck.set(session,"questTarget",0);actor.spawned=true;
  var navigation=new PlayerBotNavigation(actor);PlayerBotShutdownCheck.set(session,"navigation",navigation);
  PlayerBotShutdownCheck.set(navigation,"routeWorld",1);PlayerBotShutdownCheck.set(navigation,"routeInstance",1);
  var searchesField=PlayerBotNavigation.class.getDeclaredField("TERRAIN");searchesField.setAccessible(true);
  var searches=(PlayerBotNavigationBudget)searchesField.get(null);
  var tokenField=PlayerBotNavigation.class.getDeclaredField("searchToken");tokenField.setAccessible(true);Object token=tokenField.get(navigation);
  var seedLease=searches.begin(new Object(),PlayerBotNavigationBudget.Priority.WORLD);check(seedLease!=null,"Initial lease preserves measured work admission");
  searches.request(token,PlayerBotNavigationBudget.Priority.WORLD);
  check(searches.waiting(token),"Queued actor remains subject to global measured budget");
  check(session.reactionDelay()==100,"Nominated idle actor receives prompt production scheduler reaction");
  check(selection.invoke(service,session)==active,"Production scheduler puts nominated actor in active world lane");
  searches.cancel(token);check(session.reactionDelay()>=1000,"Cancellation restores ordinary idle reaction");
  var petNavigation=new PlayerBotNavigation(actor);PlayerBotShutdownCheck.set(petNavigation,"routeWorld",1);PlayerBotShutdownCheck.set(petNavigation,"routeInstance",1);
  var pets=new PlayerBotPets();PlayerBotShutdownCheck.set(pets,"navigation",petNavigation);PlayerBotShutdownCheck.set(session,"pets",pets);
  Object petToken=tokenField.get(petNavigation);searches.request(petToken,PlayerBotNavigationBudget.Priority.WORLD);check(searches.waiting(petToken),"Summon navigation shares the same measured global work budget");
  check(session.reactionDelay()==100 && selection.invoke(service,session)==active,"Summon turn promotes its master through real scheduler lane selection");
  searches.cancel(petToken);check(session.reactionDelay()>=1000,"Released summon turn cannot keep master promoted");seedLease.close();
  var executor=Executors.newFixedThreadPool(3);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try {
   check(idle.submit(executor::execute,()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}),"Idle planner admitted");
   check(entered.await(2,TimeUnit.SECONDS),"Idle planner blocked deliberately");
   var reacted=new CountDownLatch(1);
   check(active.submit(executor::execute,reacted::countDown),"Combat work admits while idle planner is blocked");
   check(reacted.await(2,TimeUnit.SECONDS),"Combat callback finishes without waiting for idle planner");
   check(!idle.submit(executor::execute,()->{}),"Idle lane keeps bounded serial admission");
   var partyReacted=new CountDownLatch(1);controlled.submit(executor::execute,partyReacted::countDown);
   check(partyReacted.await(2,TimeUnit.SECONDS),"Companion callbacks also remain independent of idle planner");
   idle.close();active.close();controlled.close();
   check(!active.submit(executor::execute,()->{}),"Shutdown closes new active lane admission");
   release.countDown();idle.awaitCompletion();active.awaitCompletion();controlled.awaitCompletion();
   check(!idle.busy() && !active.busy() && !controlled.busy(),"All three serial decision lanes drain");

   var db=new PlayerBotPopulationRepositoryCheck.Jdbc();PlayerBotPopulationRepositoryCheck.seed(db);
   var block=new AtomicBoolean();var committing=new CountDownLatch(1);var commitRelease=new CountDownLatch(1);
   var repository=new PlayerBotPopulationRepository(()->{
    Connection connection=db.open();
    return PlayerBotMetadataCheck.proxy(Connection.class,(proxy,method,arguments)->{
     if(method.getName().equals("commit") && block.get()){
      committing.countDown();if(!commitRelease.await(3,TimeUnit.SECONDS))throw new SQLException("Fixture timeout");
     }
     try{return method.invoke(connection,arguments);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
    });
   });
   var schedule=new PlayerBotPopulationSchedule(repository);
   check(schedule.peek()==null,"Cold decision snapshot does not initialize SQL");
   var before=schedule.load();check(schedule.peek()==before,"Warm snapshot is exactly committed state");
   var next=new PlayerBotPopulationSchedule.Events(1,2,3,4);block.set(true);
   Future<?> writer=executor.submit(()->{try{schedule.event(91,next);}catch(Exception e){throw new RuntimeException(e);}});
   try {
    check(committing.await(2,TimeUnit.SECONDS),"Production event writer holds native schedule/repository monitors at SQL commit");
    check(executor.submit(schedule::peek).get(1,TimeUnit.SECONDS)==before,"AI reads last committed state while writer is blocked");
    check(!schedule.peek().events().get(91).equals(next),"Uncommitted event never leaks into decision snapshot");
   } finally {commitRelease.countDown();}
   writer.get(2,TimeUnit.SECONDS);
   check(schedule.peek().events().get(91).equals(next),"Commit atomically publishes new event");
   db.failWrite=true;
   try{schedule.event(91,new PlayerBotPopulationSchedule.Events(2,3,4,5));throw new AssertionError("Accepted failed write");}
   catch(java.io.IOException expected){check(schedule.peek()==null,"Failed commit invalidates decision snapshot without synchronous reload");}
   db.failWrite=false;block.set(false);
   check(schedule.load().events().get(91).equals(next),"Maintenance reload retains prior committed event after rollback");
   check(schedule.peek()!=null,"Maintenance restores decision snapshot");
  } finally {release.countDown();executor.shutdownNow();check(executor.awaitTermination(3,TimeUnit.SECONDS),"Offline executor terminates");}
  System.out.println("OK: "+checks+" decision lane and committed-cache isolation checks; no production World/SQL/IDs/casts");
 }
}
