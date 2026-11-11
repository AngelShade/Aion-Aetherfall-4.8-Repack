package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Upstream-style batch world login: pure batch rules and real service dispatch, no World/production SQL/native IDs. */
public final class PlayerBotParallelLoginCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Field field(String name)throws Exception{var f=PlayerBotService.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private static final class Store implements PlayerBotPopulationStorage {
  final PlayerBotPopulationTestStorage data=new PlayerBotPopulationTestStorage();
  public Map<Integer,PlayerBotWorldRoster.Resident> residents(){return data.residents();}
  public void residents(Map<Integer,PlayerBotWorldRoster.Resident> next)throws IOException{data.residents(next);}
  public Map<Integer,PlayerBotWorldPopulation.Pool> pools(boolean server){return data.pools(server);}
  public void pool(boolean server,PlayerBotWorldPopulation.Pool next){data.pool(server,next);}
  public PlayerBotPopulationSchedule.State schedule(){return data.schedule();}
  public void schedule(PlayerBotPopulationSchedule.State next){data.schedule(next);}
  public void event(int id,PlayerBotPopulationSchedule.Events next){data.event(id,next);}
 }
 /** Accepts work without running it, so dispatch can be inspected before any native login. */
 private static final class Held extends AbstractExecutorService {
  final ArrayDeque<Runnable> queue=new ArrayDeque<>();boolean down;
  public void execute(Runnable job){queue.add(job);}
  public void shutdown(){down=true;}
  public List<Runnable> shutdownNow(){down=true;return List.copyOf(queue);}
  public boolean isShutdown(){return down;}
  public boolean isTerminated(){return down && queue.isEmpty();}
  public boolean awaitTermination(long t,TimeUnit u){return isTerminated();}
 }
 private static PlayerBotWorldRoster.Resident row(int id){return new PlayerBotWorldRoster.Resident(-1000000,id,210030000,1,2,3,(byte)0);}

 private static void pure() {
  check(PlayerBotLoginLane.workers(0,12,40)==12,"Automatic workers: one per CPU core");
  check(PlayerBotLoginLane.workers(0,64,40)==28,"Automatic workers keep twelve database connections free");
  check(PlayerBotLoginLane.workers(48,12,100)==48,"Configured workers are not capped at the core count");
  check(PlayerBotLoginLane.workers(0,12,5)==1,"A tiny pool still keeps one login worker");
  check(PlayerBotLoginLane.workers(0,0,40)==1,"Unknown core count still keeps one login worker");
  try{PlayerBotLoginLane.workers(-1,12,40);check(false,"Negative workers accepted");}catch(IllegalArgumentException expected){checks++;}
  check(PlayerBotLoginLane.budget(0)==1 && PlayerBotLoginLane.budget(60)==60 && PlayerBotLoginLane.budget(500)==120,"Batch budget follows the validated per_interval range");
  var none=Set.<Integer>of();var retries=new HashMap<Integer,Long>();
  var pending=List.of(1,2,3,4,5,6,7,8);
  check(PlayerBotLoginLane.batch(pending,id->true,none,none,none,retries,0,5,100,100).equals(List.of(1,2,3,4,5)),"A whole batch, not one login, leaves per tick in candidate order");
  check(PlayerBotLoginLane.batch(pending,id->true,none,none,none,retries,0,60,3,100).equals(List.of(1,2,3)),"World room bounds the batch");
  check(PlayerBotLoginLane.batch(pending,id->true,none,none,none,retries,0,60,100,2).equals(List.of(1,2)),"Population target room bounds automatic logins");
  check(PlayerBotLoginLane.batch(pending,id->id<=6,none,none,none,retries,0,60,100,0).equals(List.of(7,8)),"Manual residents still enter when the automatic target is full");
  retries.put(2,1000L);
  check(PlayerBotLoginLane.batch(pending,id->true,Set.of(1),Set.of(3),Set.of(4),retries,999,60,100,100).equals(List.of(5,6,7,8)),"Active, retiring, in-flight and not-yet-due residents are skipped");
  check(PlayerBotLoginLane.batch(pending,id->true,Set.of(1),Set.of(3),Set.of(4),retries,1000,60,100,100).equals(List.of(2,5,6,7,8)),"A retry joins the batch exactly when due");
  check(PlayerBotLoginLane.batch(List.of(9,9,9),id->true,none,none,none,Map.of(),0,60,100,100).equals(List.of(9)),"A duplicated candidate is dispatched once");
  check(PlayerBotLoginLane.batch(pending,id->true,none,none,none,Map.of(),0,60,0,100).isEmpty(),"A full world dispatches nothing");
  var rate=new PlayerBotLoginLane.Rate();for(int i=0;i<5;i++)rate.record(1000+i);
  check(rate.perMinute(1004)==5 && rate.perMinute(61001)==4 && rate.perMinute(70000)==0,"Login rate counts exactly the last minute");
  var executor=PlayerBotLoginLane.executor(3);
  try{check(PlayerBotLoginLane.size(executor)==3,"Login pool has the chosen worker count");}finally{executor.shutdownNow();}
 }

 @SuppressWarnings("unchecked")
 private static void dispatch()throws Exception {
  var settings=new LinkedHashMap<String,Object>();
  settings.put("ENABLED",true);settings.put("WORLD_ENABLED",true);settings.put("POPULATION_ENABLED",true);
  settings.put("WORLD_MAX_ACTIVE",10);settings.put("POPULATION_PER_INTERVAL",3);
  var before=new LinkedHashMap<Field,Object>();
  try {
   for(var e:settings.entrySet()){var f=PlayerBotConfig.class.getField(e.getKey());before.put(f,f.get(null));f.set(null,e.getValue());}
   var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);var service=constructor.newInstance();
   var store=new Store();var rows=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();for(int id=200;id<206;id++)rows.put(id,row(id));store.residents(rows);
   field("worldRoster").set(service,new PlayerBotWorldRoster(store));field("populationSchedule").set(service,new PlayerBotPopulationSchedule(store));field("residents").set(service,new TreeMap<>(rows));
   field("automaticPopulationReady").setBoolean(service,true);field("automaticAdmissionsInitialized").setBoolean(service,true);field("requestedWorldTarget").setInt(service,5);
   var held=new Held();field("loginExecutor").set(service,held);
   var load=PlayerBotService.class.getDeclaredMethod("loadWorldResidents",boolean.class,boolean.class);load.setAccessible(true);
   var work=(PlayerBotBackgroundWork)field("loginWork").get(service);var inFlight=(Set<Integer>)field("loginsInFlight").get(service);
   var retries=(Map<Integer,Long>)field("nextWorldAdmission").get(service);
   long began=System.currentTimeMillis();load.invoke(service,false,false);
   check(held.queue.size()==3 && work.running()==3,"One tick dispatches a whole batch of per_interval logins in parallel");
   check(inFlight.size()==3 && new TreeSet<>(inFlight).equals(new TreeSet<>(List.of(200,201,202))),"The batch is three distinct residents tracked as in flight");
   check(inFlight.stream().allMatch(id->retries.get(id)>=began+30000),"Each dispatched login holds its thirty-second retry deadline");
   field("nextWorldLoad").setLong(service,0);load.invoke(service,false,false);
   check(held.queue.size()==3 && inFlight.size()==3,"No next batch while one is still in flight (botLoading must be empty)");
   check(service.loginInFlight(200) && !service.loginInFlight(203),"In-flight lookup names exactly the dispatched residents");
   service.releaseSurplusPendingAdmissions(0);
   var kept=((Map<Integer,PlayerBotWorldRoster.Resident>)field("residents").get(service)).keySet();
   check(kept.containsAll(List.of(200,201,202)) && !kept.contains(203),"Releasing surplus intents never removes a resident whose login is in flight");
   work.close();while(!held.queue.isEmpty())held.queue.poll().run();work.awaitCompletion();
   check(work.running()==0,"Shutdown discards queued logins and drains the lane");
   check(!work.submit(held,()->{throw new AssertionError("login after close");}),"A closed login lane refuses new logins");
  }finally{for(var e:before.entrySet())e.getKey().set(null,e.getValue());}
 }

 public static void main(String[] args)throws Exception {
  PlayerBotWardenExpansionCheck.defaults(com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig.class);
  pure();dispatch();
  System.out.println("OK: "+checks+" upstream-style batch login, worker sizing, in-flight custody and shutdown checks; no World/production SQL/native IDs");
 }
}
