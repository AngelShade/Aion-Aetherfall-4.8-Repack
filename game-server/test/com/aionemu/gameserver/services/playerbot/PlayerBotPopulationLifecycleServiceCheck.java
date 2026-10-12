package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Actual manager transitions with an isolated store; no actors, World, native IDs or production SQL. */
public final class PlayerBotPopulationLifecycleServiceCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Field field(String name)throws Exception{var f=PlayerBotService.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private static final class Store implements PlayerBotPopulationStorage {
  final PlayerBotPopulationTestStorage data=new PlayerBotPopulationTestStorage();boolean fail;int writes;
  public Map<Integer,PlayerBotWorldRoster.Resident> residents(){return data.residents();}
  public void residents(Map<Integer,PlayerBotWorldRoster.Resident> next)throws IOException{if(fail)throw new IOException("Synthetic uncommitted roster save");data.residents(next);writes++;}
  public Map<Integer,PlayerBotWorldPopulation.Pool> pools(boolean server){return data.pools(server);}
  public void pool(boolean server,PlayerBotWorldPopulation.Pool next){data.pool(server,next);}
  public PlayerBotPopulationSchedule.State schedule(){return data.schedule();}
  public void schedule(PlayerBotPopulationSchedule.State next){data.schedule(next);}
  public void event(int id,PlayerBotPopulationSchedule.Events next){data.event(id,next);}
 }
 private static Object service(Store store,Map<Integer,PlayerBotWorldRoster.Resident> rows)throws Exception {
  var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);var service=constructor.newInstance();
  store.residents(rows);field("worldRoster").set(service,new PlayerBotWorldRoster(store));field("populationSchedule").set(service,new PlayerBotPopulationSchedule(store));field("residents").set(service,new TreeMap<>(rows));return service;
 }
 private static PlayerBotWorldRoster.Resident row(int account,int id){return new PlayerBotWorldRoster.Resident(account,id,210030000,1,2,3,(byte)0);}
 @SuppressWarnings("unchecked")
 public static void main(String[] args)throws Exception {
  var store=new Store();var original=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();original.put(100,row(-1000000,100));original.put(101,row(-1000001,101));original.put(102,row(-1000000,102));original.put(99,row(7,99));
  var s=(PlayerBotService)service(store,original);
  var retiring=(Set<Integer>)field("retiringWorldBots").get(s);var queue=(Map<Integer,Long>)field("retireQueue").get(s);retiring.add(101);queue.put(102,0L);
  store.schedule(new PlayerBotPopulationSchedule.State(1,3,60000,Map.of(100,new PlayerBotPopulationSchedule.Events(30000,72000,90000,43))));var saved=store.schedule();
  s.initializeAutomaticAdmissions();check(store.residents().equals(original),"Incomplete factory cannot reset or load startup intents");
  check(!field("automaticAdmissionsInitialized").getBoolean(s),"Factory barrier remains closed");check(s.automaticPendingAdmissions()==2,"Pending count excludes retiring actor but includes reserved save-held slot");
  field("automaticPopulationReady").setBoolean(s,true);store.fail=true;
  try{s.initializeAutomaticAdmissions();throw new AssertionError("Failed durable reset published");}catch(IOException expected){checks++;}
  check(!field("automaticAdmissionsInitialized").getBoolean(s) && store.residents().equals(original),"Uncommitted reset keeps native slots and blocks login");
  store.fail=false;s.initializeAutomaticAdmissions();
  check(store.residents().keySet().equals(Set.of(99,101,102)),"Reset removes only ordinary automatic pending intent");
  check(field("automaticAdmissionsInitialized").getBoolean(s),"Durably saved reset opens manager admission");
  check(store.schedule().equals(saved),"Startup preserves target, cooldown, placement and character progress references");
  int writes=store.writes;s.initializeAutomaticAdmissions();check(store.writes==writes,"Startup reconstruction executes once per service lifetime");
  var ordinary=new Store();var manager=(PlayerBotService)service(ordinary,original);manager.releaseSurplusPendingAdmissions(2);
  check(ordinary.residents().keySet().equals(Set.of(99,100,101)),"Lower target releases excess offline intents while manual resident remains");
  check(manager.automaticPendingAdmissions()==2,"Remaining batch counts both faction intents");
  writes=ordinary.writes;manager.releaseSurplusPendingAdmissions(3);check(ordinary.writes==writes,"Higher target does not invent admissions or rewrite stable residency");
  manager.releaseSurplusPendingAdmissions(0);check(ordinary.residents().keySet().equals(Set.of(99)),"Zero target clears pending automatic logins without touching manual residence");
  check(manager.automaticPendingAdmissions()==0,"Released pending batch cannot stall future refill");
  ordinary.residents(original);field("residents").set(manager,new TreeMap<>(original));ordinary.fail=true;
  try{manager.releaseSurplusPendingAdmissions(1);throw new AssertionError("Failed trim published");}catch(IOException expected){checks++;}
  check(ordinary.residents().equals(original) && manager.automaticPendingAdmissions()==3,"Failed trim retains durable reservations and manager snapshot");
  Map<String,Object> settings=new LinkedHashMap<>();
  settings.put("POPULATION_MIN",100);settings.put("POPULATION_MAX",100);settings.put("POPULATION_UPDATE_INTERVAL_SECONDS",20);
  settings.put("POPULATION_PER_INTERVAL",12);settings.put("POPULATION_ELYOS_PERCENT",50);settings.put("POPULATION_MIN_LEVEL",1);settings.put("POPULATION_MAX_LEVEL",65);
  settings.put("POPULATION_STARTING_LEVEL",1);settings.put("POPULATION_PRESENCE_MIN",600);settings.put("POPULATION_PRESENCE_MAX",600);
  settings.put("POPULATION_OFFLINE_MIN",600);settings.put("POPULATION_OFFLINE_MAX",600);settings.put("POPULATION_TARGET_MIN",1800);settings.put("POPULATION_TARGET_MAX",1800);
  settings.put("POPULATION_TELEPORT_MIN",3600);settings.put("POPULATION_TELEPORT_MAX",3600);settings.put("POPULATION_OFFLINE_COOLDOWN",false);
  settings.put("WORLD_MAX_ACTIVE",4);settings.put("POPULATION_ENABLED",true);
  settings.put("ENABLED",true);settings.put("WORLD_ENABLED",true);
  var before=new LinkedHashMap<Field,Object>();
  try {
   for(var e:settings.entrySet()){var f=PlayerBotConfig.class.getField(e.getKey());before.put(f,f.get(null));f.set(null,e.getValue());}
   var automaticOnly=(PlayerBotService)service(new Store(),Map.of(100,original.get(100)));
   var manualLoad=PlayerBotService.class.getDeclaredMethod("loadWorldResidents",boolean.class);manualLoad.setAccessible(true);manualLoad.invoke(automaticOnly,true);
   check(field("nextWorldLoad").getLong(automaticOnly)==0,"Empty manual priority probe cannot postpone automatic batch draining every poll");
   check(manager.populationLoadCandidates(true,3,0,0).equals(List.of(99)),"Manual pending actors bypass automatic factory readiness");
   check(manager.populationLoadCandidates(false,3,0,0).equals(List.of(99)),"Unfinished factory cannot leak automatic native loads");
   field("automaticPopulationReady").setBoolean(manager,true);
   check(manager.populationLoadCandidates(false,3,0,0).equals(List.of(99)),"Ready factory still awaits durable startup reset");
   field("automaticAdmissionsInitialized").setBoolean(manager,true);
   check(new HashSet<>(manager.populationLoadCandidates(false,3,0,0)).equals(original.keySet()),"Finished startup opens both-faction saved batch");
   check(manager.populationLoadCandidates(true,3,0,0).equals(List.of(99)),"Manual worker priority cannot steal automatic load before manager pass");
   check(manager.populationLoadCandidates(false,0,0,0).equals(List.of(99)),"Zero target blocks only automatic entries");
   check(manager.populationLoadCandidates(false,3,3,3).equals(List.of(99)),"At target only remaining manual world capacity can enter");
   check(manager.populationLoadCandidates(false,3,0,4).isEmpty(),"Hard native world cap still blocks every new entry");
   field("playerFocus").set(manager,new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(210040000,1,2,3)),900));
   check(manager.populationLoadCandidates(false,3,0,0).equals(List.of(99)),"Focus restricts automatic destinations without starving manual ones");
   field("nextServerPopulation").setLong(s,9000);field("nextWorldLoad").setLong(s,8000);field("nextPopulation").setLong(s,7000);s.nativeLoginCompleted();
   check(field("nextServerPopulation").getLong(s)==9000 && field("nextWorldLoad").getLong(s)==0 && field("nextPopulation").getLong(s)==0,"Native login wakes draining/manual work without bypassing manager cadence");
   check(!s.serverPopulationDue(8999),"Successful login cannot admit another early population batch");
   for(int i=0;i<100;i++){s.nativeLoginCompleted();if(field("nextServerPopulation").getLong(s)!=9000)throw new AssertionError("Manager wake caused by callback "+i);}
   check(s.serverPopulationDue(9000),"100 native callbacks retain the exact scheduled manager deadline");
   long began=field("populationStartedAt").getLong(s);
   check(s.populationManagerDelay(began)==500,"Initial shortage uses pinned half-second cadence");
   check(s.populationManagerDelay(began+51000)==10000,"After initialization shortage uses half configured normal interval");
   PlayerBotConfig.POPULATION_MIN=0;check(s.populationManagerDelay(began)==1000,"Initial full population uses pinned one-second cadence");
   check(s.populationManagerDelay(began+51000)==20000,"Full normal population uses configured 20 seconds");
   PlayerBotConfig.POPULATION_UPDATE_INTERVAL_SECONDS=1;check(s.populationManagerDelay(began+51000)==1000,"Owner normal interval controls manager only");
   PlayerBotConfig.POPULATION_UPDATE_INTERVAL_SECONDS=0;try{s.populationManagerDelay(began);throw new AssertionError("Invalid interval admitted");}catch(IllegalArgumentException expected){checks++;}
   PlayerBotConfig.POPULATION_UPDATE_INTERVAL_SECONDS=3601;try{s.populationManagerDelay(began);throw new AssertionError("Unbounded interval admitted");}catch(IllegalArgumentException expected){checks++;}
   var depart=PlayerBotService.class.getDeclaredMethod("commitPopulationDeparture",int.class,long.class);depart.setAccessible(true);
   depart.invoke(s,100,1000L);var event=store.schedule().events().get(100);
   check(event.presentUntil()==0 && event.offlineUntil()==72000,"Pinned departure creates no extra cooldown and retains saved prior deadline");
   check(event.teleportAt()==90000 && event.placedLevel()==43,"Departure retains native placement history");
   PlayerBotConfig.POPULATION_OFFLINE_COOLDOWN=true;depart.invoke(s,100,2000L);
   check(store.schedule().events().get(100).offlineUntil()==72000,"Interrupted already-committed departure never samples another enabled break");
   store.event(100,new PlayerBotPopulationSchedule.Events(1000,72000,90000,43));depart.invoke(s,100,2000L);
   check(store.schedule().events().get(100).offlineUntil()==602000,"Optional owner break commits actual configured duration");
   depart.invoke(s,100,3000L);check(store.schedule().events().get(100).offlineUntil()==602000,"Repeated departure completion is idempotent with extra breaks enabled");
   PlayerBotConfig.POPULATION_OFFLINE_COOLDOWN=false;depart.invoke(s,100,3000L);check(store.schedule().events().get(100).offlineUntil()==602000,"Later disabling cannot shorten committed break");
   var events=store.schedule().events();depart.invoke(s,999,4000L);check(store.schedule().events().equals(events),"Missing saved event does not manufacture identity or timer");
  }finally{for(var e:before.entrySet())e.getKey().set(null,e.getValue());}
  System.out.println("OK: "+checks+" actual population factory/startup/batch/cadence/cooldown transitions; no World/IDs/production SQL");
 }
}
