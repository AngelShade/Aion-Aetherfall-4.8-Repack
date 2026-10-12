package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.LongSupplier;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import sun.misc.Unsafe;

/** Actual scheduler snapshots on inert actors and held executors; no World/IDs/SQL/native tasks. */
public final class PlayerBotSchedulerCpuCheck {
 private static int checks;
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
 private static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static final class Clock extends PlayerController {@Override public long getLastCombatTime(){return 0;}}
 private static final class Actor extends Player {
  int id,stateReads;Clock control;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isLooting(){return false;}
  @Override public boolean isInState(CreatureState state){if(state==CreatureState.RESTING)stateReads++;return false;}
  @Override public PlayerController getController(){return control;}
  @Override public PlayerMoveController getMoveController(){return null;}
 }
 private static final class BudgetClock implements LongSupplier {long now;int reads;@Override public long getAsLong(){reads++;return now;}}
 private static void purePass() {
  var pass=new PlayerBotDecisionSchedule.Pass<String,String>(true);
  pass.observe("idle",1,0,false,false,"idle",0,10000,true);
  pass.observe("late",2,1000,false,true,"active",0,10000,true);
  pass.observe("future",3,11000,true,true,"controlled",1,10000,true);
  pass.finish();
  check(pass.ready().isEmpty(),"full lanes and future actors produce no readiness records or sort lists");
  check(pass.earliest(lane->true)==11000,"only a dispatchable lane owns the next captured deadline");
  check(pass.earliest(lane->false)==Long.MAX_VALUE,"a newly occupied lane cannot cause immediate wakeup");
  check(pass.lateCount()==1 && pass.oldest().id()==2,"full-lane overload remains diagnosed without dispatch snapshots");
  check(PlayerBotDecisionSchedule.delay(10000,Long.MAX_VALUE,PlayerBotDecisionSchedule.maximum(400,pass.saturated()))==25,
   "a just-finished saturated lane retains bounded retry rather than waiting400ms");
  try{pass.ready().put("other",List.of());throw new AssertionError("Mutable dispatch map");}catch(UnsupportedOperationException expected){checks++;}
  try{pass.observe("late",4,0,false,true,"active",1,10000,true);throw new AssertionError("Changed sealed pass");}catch(IllegalStateException expected){checks++;}
  var ordered=new PlayerBotDecisionSchedule.Pass<Integer,String>(false);
  for(int id:new int[]{7,3,9,1})ordered.observe(id,id,id==9?100:200,false,true,"active",4,1000,true);
  ordered.finish();var ready=ordered.ready().get("active");
  check(ready.stream().map(PlayerBotDecisionSchedule.Ready::id).toList().equals(List.of(9,1,3,7)),"available work retains deadline and actor tie order");
  var batches=PlayerBotDecisionSchedule.batches(ready,2);
  check(batches.stream().flatMap(List::stream).map(PlayerBotDecisionSchedule.Ready::id).distinct().count()==4,
   "independent lanes still receive each admitted actor exactly once");
  try{ready.clear();throw new AssertionError("Mutable actor order");}catch(UnsupportedOperationException expected){checks++;}
  var mixed=new PlayerBotDecisionSchedule.Pass<Integer,String>(false);
  mixed.observe(1,1,1000,false,true,"active",1,10000,true);
  mixed.observe(50,50,1000,true,true,"controlled",1,10000,true);mixed.finish();
  check(new ArrayList<>(mixed.ready().keySet()).equals(List.of("controlled","active")),"controlled dispatch retains original global priority across lanes");
  check(mixed.oldest().id()==50,"backlog oldest tie retains original controlled-first ordering");
 }
 @SuppressWarnings("unchecked") private static void actualPass(Unsafe unsafe,int count)throws Exception {
  var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);var service=constructor.newInstance();
  field(PlayerBotService.class,"nextAiMaintenance").setLong(service,Long.MAX_VALUE);
  field(PlayerBotService.class,"nextDecisionLagReport").setLong(service,Long.MAX_VALUE);
  var sessions=(Map<Integer,PlayerBotSession>)field(PlayerBotService.class,"sessions").get(service);
  var schedule=(PlayerBotDecisionSchedule)field(PlayerBotService.class,"decisions").get(service);
  var execution=(PlayerBotExecution)field(PlayerBotService.class,"execution").get(service);
  var held=new ArrayList<Runnable>();var lanes=new ArrayList<PlayerBotBackgroundWork>();
  for(String name:List.of("controlledWork","activeWorldWork","autonomousWork")){
   var lane=(PlayerBotBackgroundWork)field(PlayerBotService.class,name).get(service);lanes.add(lane);
   while(lane.available()>0)check(lane.submit(held::add,()->{}),"isolated queued work owns its actual bounded lane");
  }
  var actors=new ArrayList<Actor>();
  try {
   for(int i=1;i<=count;i++){
    var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.id=i;actor.control=new Clock();actors.add(actor);
    var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
    field(PlayerBotSession.class,"bot").set(session,actor);field(PlayerBotSession.class,"owner").set(session,actor);
    field(PlayerBotSession.class,"engine").set(session,new PlayerBotEngine());field(PlayerBotSession.class,"idleReaction").setInt(session,10);
    sessions.put(i,session);schedule.complete(i,1000);
   }
   field(PlayerBotSession.class,"closing").setBoolean(sessions.get(1),true);
   field(PlayerBotService.class,"focusJobId").setInt(service,2);check(execution.claim(3,"inert native work",1),"actor execution is already owned");
   var tick=PlayerBotService.class.getDeclaredMethod("tickPass",long.class);tick.setAccessible(true);
   var pass=(PlayerBotDecisionSchedule.Pass<PlayerBotSession,PlayerBotBackgroundWork>)tick.invoke(service,10000L);
   check(pass.ready().isEmpty() && pass.earliest(lane->!lane.busy())==Long.MAX_VALUE,"actual saturated scheduler admits no actor or due focus wakeup population"+count);
   check(actors.get(0).stateReads==0 && actors.get(1).stateReads==0 && actors.get(2).stateReads==0,
    "closing/focus-owned/executing actors are rejected before reaction observation");
   check(actors.subList(3,count).stream().allMatch(a->a.stateReads==1),"actual scheduler classifies each eligible actor exactly once population"+count);
   check(pass.saturated() && pass.lateCount()==0,"single captured pass retains capacity and existing report throttle");
   execution.complete(3);field(PlayerBotService.class,"focusJobId").setInt(service,0);
   pass=(PlayerBotDecisionSchedule.Pass<PlayerBotSession,PlayerBotBackgroundWork>)tick.invoke(service,10025L);
   check(actors.get(0).stateReads==0 && actors.get(1).stateReads==1 && actors.get(2).stateReads==1,
    "native ownership release restores eligibility on the next fresh pass");
   check(actors.subList(3,count).stream().allMatch(a->a.stateReads==2),"no reaction is reused across separate scheduling passes");
   // A free lane with future deadlines still calculates its next wakeup once,
   // without submitting native work or revisiting actor classification.
   held.forEach(Runnable::run);held.clear();for(int id:sessions.keySet())schedule.complete(id,20000);
   pass=(PlayerBotDecisionSchedule.Pass<PlayerBotSession,PlayerBotBackgroundWork>)tick.invoke(service,10050L);
   check(pass.ready().isEmpty() && pass.earliest(lane->!lane.busy())==21000,"actual free idle lane retains the original1000ms native reaction");
   int reads=actors.stream().mapToInt(a->a.stateReads).sum();pass.earliest(lane->!lane.busy());pass.ready();
   check(actors.stream().mapToInt(a->a.stateReads).sum()==reads,"dispatch and earliest inspection reuse captured state instead of rescanning actors");
  }finally{execution.complete(3);lanes.forEach(PlayerBotBackgroundWork::close);held.forEach(Runnable::run);for(var lane:lanes)lane.awaitCompletion();}
 }
 private static void nomination() {
  var clock=new BudgetClock();var budget=new PlayerBotNavigationBudget(clock,500,3,2);int initial=clock.reads;
  for(int i=0;i<3000;i++)if(budget.nominated(new Object()))throw new AssertionError("Absent intent was nominated");
  check(clock.reads==initial,"3000 absent actors perform no clock/refill/nominee construction");
  Object first=new Object(),second=new Object();budget.request(first,PlayerBotNavigationBudget.Priority.WORLD);budget.request(second,PlayerBotNavigationBudget.Priority.WORLD);
  int before=clock.reads;budget.nominated(new Object());check(clock.reads==before,"absent token cannot nominate another actor's queued work");
  check(budget.nominated(first)&&budget.nominated(second),"present tokens retain bounded concurrent nominations");
  var lease=budget.begin(first,PlayerBotNavigationBudget.Priority.WORLD);check(lease!=null&&!budget.nominated(first),"in-flight native lease cannot nominate a second turn");
  clock.now+=1000000;lease.close();budget.cancel(second);before=clock.reads;
  check(!budget.nominated(second)&&clock.reads==before,"cancelled intent takes the same allocation-free absent path");
 }
 public static void main(String[] args)throws Exception {
  purePass();var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  for(int population:new int[]{250,1000,3000})actualPass(unsafe,population);
  nomination();System.out.println("OK: "+checks+" actual scheduler eligibility, single-pass reaction, lane saturation, deadlines and empty terrain checks; no World/IDs/SQL/native tasks");
 }
 private PlayerBotSchedulerCpuCheck(){}
}
