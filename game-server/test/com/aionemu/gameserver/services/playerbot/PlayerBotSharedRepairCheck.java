package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Mixed population fake-clock and durable identity tests: no World, database, actors or game IDs. */
public final class PlayerBotSharedRepairCheck {
 private static final class SessionActor extends Player {
  SessionActor(){super(null,null);}
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isDead(){return false;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.CLERIC;}
 }
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static long[] simulate(boolean legacy) {
  var scheduler=new PlayerBotDecisionSchedule();
  long[] last=new long[1000],worst=new long[1000],runs=new long[1000];
  Arrays.fill(last,-1);long now=0;int cursor=0;
  while(now<20000) {
   var ids=new ArrayList<Integer>();for(int i=0;i<1000;i++)ids.add(i);
   if(!legacy)ids.sort(Comparator.comparingLong((Integer i)->scheduler.due(i,period(i))).thenComparingInt(i->i));
   long began=now;
   for(int visited=0;visited<1000;visited++) {
    int id=legacy?cursor:ids.get(visited);if(legacy)cursor=(cursor+1)%1000;
    if(!legacy && now<scheduler.due(id,period(id)))continue;
    if(last[id]>=1200)worst[id]=Math.max(worst[id],now-last[id]);
    last[id]=now;runs[id]++;now++; // Each callback costs 1ms, without executing native AI.
    scheduler.complete(id,now);
    if(now-began>=50)break;
   }
   long earliest=Long.MAX_VALUE;for(int id=0;id<1000;id++)earliest=Math.min(earliest,scheduler.due(id,period(id)));
   now+=legacy?400:PlayerBotDecisionSchedule.delay(now,earliest,400);
  }
  if(!legacy) {
   check(Arrays.stream(runs).allMatch(n->n>=5),"Every resident remains scheduled under mixed 1000-character load");
   for(int id=0;id<20;id++)check(worst[id]<=500,"Interactive decisions remain responsive under 1000 x 1ms artificial work: "+id+" gap="+worst[id]);
   for(int id=20;id<100;id++)check(worst[id]<=900,"Shared combat decisions remain responsive under artificial work: "+id+" gap="+worst[id]);
  }
  return worst;
 }
 private static long period(int id){return PlayerBotDecisionSchedule.reaction(id<20,id>=20 && id<100,false,20);}
 public static void main(String[] args)throws Exception {
  check(PlayerBotDecisionSchedule.reaction(true,false,false,20)==100,"Client-master upstream reaction delay");
  check(PlayerBotDecisionSchedule.reaction(false,true,false,20)==500,"Combat upstream reaction delay");
  check(PlayerBotDecisionSchedule.reaction(false,false,false,20)==2000,"Independent idle upstream reaction delay");
  check(PlayerBotDecisionSchedule.reaction(false,false,true,100)==10000,"Native rest permits reduced planning work");
  var scheduler=new PlayerBotDecisionSchedule();check(scheduler.due(1,500)==0,"New character receives an immediate first callback");
  scheduler.complete(1,1000);check(scheduler.due(1,2000)==3000,"Completion determines the next deadline");
  check(scheduler.due(1,100)==1100,"A changed controller/state advances the same shared callback");
  scheduler.retain(Set.of());check(scheduler.due(1,2000)==0,"Retired session deadline does not delay a new admission");
  check(PlayerBotDecisionSchedule.delay(1000,0,400)==1,"Unfinished batch continues promptly");
  check(PlayerBotDecisionSchedule.delay(1000,1080,400)==80,"Future callback waits until due");
  check(PlayerBotDecisionSchedule.delay(1000,Long.MAX_VALUE,400)==400,"Empty population does not busy-loop");
  var access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);var unsafe=(sun.misc.Unsafe)access.get(null);
  var resident=(SessionActor)unsafe.allocateInstance(SessionActor.class);var leader=(SessionActor)unsafe.allocateInstance(SessionActor.class);
  var independent=new PlayerBotSession(resident,resident,null,0,false);var controlled=new PlayerBotSession(leader,resident,null,0,false);
  var questing=PlayerBotSession.class.getDeclaredField("questing");questing.setAccessible(true);
  check(questing.getBoolean(independent),"Independent context uses the shared quest engine");
  check(questing.getBoolean(controlled),"Invitation/control transition must not disable shared quest actions");
  check(independent.autonomous() && !controlled.autonomous(),"Ownership changes orders/context while retaining quest actions");
  var repaired=simulate(false);var legacy=simulate(true);
  check(legacy[0]>repaired[0]*5,"Installed fixed batch sleep reproduces population-dependent response regression");
  for(var tribe:List.of(TribeClass.TARGETBASFELT_DF1,TribeClass.TARGETBASFELT2_DF1,TribeClass.DUMMY,TribeClass.DUMMY2,
   TribeClass.LF5_DUMMY1,TribeClass.LF5_DUMMY2,TribeClass.DF5_DUMMY1,TribeClass.DF5_DUMMY2))
   check(PlayerBotHunting.training(tribe),"Native regenerating training dummy excluded from opportunistic hunting: "+tribe);
  check(!PlayerBotHunting.training(TribeClass.PC),"Classification does not invent a universal town combat ban");
  var place=new PlayerBotWorldTravel.Place(1,301,10,0,0);
  var giver=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.CONVERSATION,place,10);
  var objective=new PlayerBotWorldActivities.Goal(4,PlayerBotQuestRoutes.Kind.HUNT,place,10);
  check(!PlayerBotHunting.opportunistic(giver,302),"Travel to eligible new quest is not displaced by an unrelated nearby mob");
  check(!PlayerBotHunting.opportunistic(objective,302),"Active quest travel keeps its required target");
  check(PlayerBotHunting.opportunistic(objective,301),"Required quest mob may interrupt travel to execute shared combat");
  check(PlayerBotHunting.opportunistic(null,302),"Idle bot still has a hunting fallback");
  check(!PlayerBotWorldActivities.stranded(60000,0,false),"Unscanned actor is not declared stranded");
  check(!PlayerBotWorldActivities.stranded(60000,1,false),"Idle destination recovery waits for a full minute");
  check(PlayerBotWorldActivities.stranded(60001,1,false),"No reachable native activity permits bounded field recovery");
  check(!PlayerBotWorldActivities.stranded(60001,1,true),"A live quest or vendor goal keeps ordinary activity travel");
  check(PlayerBotPopulationPolicy.creationDeficit(12,0,1000,1000)==0,"Offline residents at capacity cannot cause replacement identities");
  check(PlayerBotPopulationPolicy.creationDeficit(12,0,1050,1000)==0,"Previous extra characters are retained and reused, not deleted");
  check(PlayerBotPopulationPolicy.creationDeficit(12,0,998,1000)==2,"Creation is limited to the genuine pool shortfall");
  check(PlayerBotPopulationPolicy.creationDeficit(12,12,10,1000)==0,"Successful reuse requires no creation");
  check(PlayerBotPopulationPolicy.creationDeficit(12,0,0,1000)==12,"Initial provisioning remains bounded");
  Path dir=Files.createTempDirectory(Path.of("."),"shared-persistence-");
  try {
   var pool=PlayerBotPopulationTestStorage.population(dir.resolve("pool.json"));
   pool.put(new PlayerBotWorldPopulation.Pool(-1000000,"saved-elyos",(byte)0,12,Race.ELYOS,Gender.MALE,new PlayerAppearance(),2,List.of(81,82)));
   pool.put(new PlayerBotWorldPopulation.Pool(-1000001,"saved-asmodians",(byte)0,12,Race.ASMODIANS,Gender.FEMALE,new PlayerAppearance(),2,List.of(83,84)));
   var reopened=PlayerBotPopulationTestStorage.population(dir.resolve("pool.json")).load();
   check(reopened.get(-1000000).characters().equals(List.of(81,82)),"Elyos persistent IDs survive a new loader");
   check(reopened.get(-1000001).characters().equals(List.of(83,84)),"Asmodian persistent IDs survive a new loader");
   var residence=PlayerBotPopulationTestStorage.roster(dir.resolve("residents.properties"));residence.save(Map.of());
   check(PlayerBotPopulationTestStorage.population(dir.resolve("pool.json")).load().values().stream().mapToInt(p->p.characters().size()).sum()==4,
    "Logging out every resident preserves the pool for future admission");
  } finally {try(var files=Files.list(dir)){for(var file:files.toList())Files.delete(file);}Files.delete(dir);}
  System.out.println("OK: "+checks+" shared reaction, hunting intent and persistent pool checks; fake clock only");
 }
}
