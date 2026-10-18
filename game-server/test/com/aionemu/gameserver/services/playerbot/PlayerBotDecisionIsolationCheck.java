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
  // These inert active-bot fixtures have no native human-presence World.
  com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_ONLY_OCCUPIED_MAPS=false;
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
  var navigation=new PlayerBotNavigation(actor,false);PlayerBotShutdownCheck.set(session,"navigation",navigation);
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
  var petNavigation=new PlayerBotNavigation(actor,false);PlayerBotShutdownCheck.set(petNavigation,"routeWorld",1);PlayerBotShutdownCheck.set(petNavigation,"routeInstance",1);
  var pets=new PlayerBotPets();PlayerBotShutdownCheck.set(pets,"navigation",petNavigation);PlayerBotShutdownCheck.set(session,"pets",pets);
  Object petToken=tokenField.get(petNavigation);searches.request(petToken,PlayerBotNavigationBudget.Priority.WORLD);check(searches.waiting(petToken),"Summon navigation shares the same measured global work budget");
  check(session.reactionDelay()==100 && selection.invoke(service,session)==active,"Summon turn promotes its master through real scheduler lane selection");
  searches.cancel(petToken);check(session.reactionDelay()>=1000,"Released summon turn cannot keep master promoted");seedLease.close();
  // Exercise production domain binding, rather than two unrelated test budgets.
  var partyNavigation=new PlayerBotNavigation(actor,true);
  var terrainField=PlayerBotNavigation.class.getDeclaredField("terrain");terrainField.setAccessible(true);
  var partyTerrain=(PlayerBotNavigationBudget)terrainField.get(partyNavigation);
  check(partyTerrain!=searches,"Companion navigation owns a different terrain allowance");
  var worldLeases=new ArrayList<PlayerBotNavigationBudget.Lease>();
  try {
   boolean blocked=false;
   for(int i=0;i<=com.aionemu.gameserver.configs.main.PlayerBotConfig.TERRAIN_WORKERS;i++){
    var lease=searches.begin(new Object(),PlayerBotNavigationBudget.Priority.WORLD);
    if(lease==null){blocked=true;break;}worldLeases.add(lease);
   }
   check(blocked,"World terrain admission is occupied or repaying its own debt");
   try(var companionLease=partyTerrain.begin(new Object(),PlayerBotNavigationBudget.Priority.CONTROLLED)){
    check(companionLease!=null,"Companion terrain admission succeeds while every world slot is occupied");
   }
  }finally{for(var lease:worldLeases)if(lease!=null)lease.close();}
  var meshField=PlayerBotNavigation.class.getDeclaredField("meshAdmission");meshField.setAccessible(true);
  var worldMesh=(PlayerBotMeshRoute.Admission)meshField.get(navigation);
  var partyMesh=(PlayerBotMeshRoute.Admission)meshField.get(partyNavigation);
  check(worldMesh!=partyMesh,"Companion prepared-route admission is separate from world admission");
  var meshOwners=new ArrayList<Object>();Object partyQuery=new Object();
  try {
   for(int i=0;i<com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_MAX_QUERIES;i++){
    Object owner=new Object();meshOwners.add(owner);check(worldMesh.demand(owner) && worldMesh.retain(owner) && worldMesh.begin(owner),"World query fills its own bounded loading/memory/calculation slots");
   }
   check(!worldMesh.demand(new Object()) && !worldMesh.retain(new Object()) && !worldMesh.begin(new Object()),"World query capacity is exhausted");
   check(partyMesh.demand(partyQuery) && partyMesh.retain(partyQuery) && partyMesh.begin(partyQuery),"Companion query bypasses occupied world loading/memory/calculation slots");
   partyMesh.end(partyQuery);
  }finally{for(Object owner:meshOwners){worldMesh.end(owner);worldMesh.cancel(owner);}partyMesh.cancel(partyQuery);}
  var runtimeMethod=PlayerBotMeshRoute.class.getDeclaredMethod("runtime",PlayerBotMeshRoute.Admission.class);runtimeMethod.setAccessible(true);
  Object worldRuntime=runtimeMethod.invoke(null,worldMesh),partyRuntime=runtimeMethod.invoke(null,partyMesh);
  var storeField=worldRuntime.getClass().getDeclaredField("store");storeField.setAccessible(true);
  check(worldRuntime!=partyRuntime && storeField.get(worldRuntime)!=storeField.get(partyRuntime),"Companion routes own a separate cache and map-loading queue");
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
   // The real companion pass must use its published index and dedicated executor.
   var worldActiveEntered=new CountDownLatch(1);
   check(active.submit(executor::execute,()->{worldActiveEntered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}),"Active world lane blocked too");
   check(worldActiveEntered.await(2,TimeUnit.SECONDS),"Both world lanes are occupied");
   var indexed=new ConcurrentHashMap<Integer,PlayerBotSession>();indexed.put(actor.getObjectId(),session);
   var all=new ConcurrentHashMap<Integer,PlayerBotSession>(){
    @Override public Collection<PlayerBotSession> values(){throw new AssertionError("Companion pass scanned world population");}
   };all.putAll(indexed);
   PlayerBotShutdownCheck.set(session,"owner",unsafe.allocateInstance(Actor.class));
   PlayerBotShutdownCheck.set(service,"sessions",all);PlayerBotShutdownCheck.set(service,"controlledSessions",indexed);
   PlayerBotShutdownCheck.set(service,"decisions",new PlayerBotDecisionSchedule());PlayerBotShutdownCheck.set(service,"execution",new PlayerBotExecution());
   var dedicated=Executors.newSingleThreadExecutor();var workerEntered=new CountDownLatch(1);var workerRelease=new CountDownLatch(1);
   dedicated.execute(()->{workerEntered.countDown();try{workerRelease.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   check(workerEntered.await(2,TimeUnit.SECONDS),"Dedicated test worker held before native session execution");
   PlayerBotShutdownCheck.set(service,"companionWorker",dedicated);
   try {
    var passMethod=PlayerBotService.class.getDeclaredMethod("companionTickPass",long.class);passMethod.setAccessible(true);
    var pass=(PlayerBotDecisionSchedule.Pass<?,?>)passMethod.invoke(service,System.currentTimeMillis());
    check(pass.ready().size()==1 && controlled.busy(),"Production companion pass admits on its own executor while world lanes remain blocked");
   }finally{controlled.close();workerRelease.countDown();controlled.awaitCompletion();dedicated.shutdown();check(dedicated.awaitTermination(3,TimeUnit.SECONDS),"Dedicated companion test worker drains without native gameplay");}
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
