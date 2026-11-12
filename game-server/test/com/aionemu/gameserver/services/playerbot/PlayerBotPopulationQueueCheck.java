package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayList;
import java.util.List;

/** Actual bounded queue planner; no world, database, factory or gameplay fixtures. */
public final class PlayerBotPopulationQueueCheck {
 private static int checks;
 private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 private static PlayerBotPopulationQueues.Candidate actor(int id,boolean elyos,int level,boolean ready,boolean rebuildable){
  return new PlayerBotPopulationQueues.Candidate(id,elyos,level,ready,rebuildable);
 }
 private static PlayerBotPopulationQueues.Admission plan(List<PlayerBotPopulationQueues.Candidate> pool,int elyos,int asmodians){
  return PlayerBotPopulationQueues.plan(pool,56,65,elyos,asmodians);
 }
 private static void rebuilt(List<PlayerBotPopulationQueues.Candidate> pool,PlayerBotPopulationQueues.Admission admission){
  for(int i=0;i<pool.size();i++)if(pool.get(i).character()==admission.character()){
   var previous=pool.get(i);pool.set(i,actor(previous.character(),previous.elyos(),admission.level(),true,false));return;
  }
  throw new AssertionError("Rebuild selected a character outside the available pool");
 }
 public static void main(String[] args){
  // Captured Terath stall: one human + three Elyos bots, six Asmodian bots.
  // Existing eligible actors are committed elsewhere; safe lower actors must fill precisely two Elyos seats.
  var stall=new ArrayList<>(List.of(actor(1,true,60,false,false),actor(2,true,55,true,true),
   actor(3,true,54,true,true),actor(4,false,60,true,false),actor(5,true,20,false,false)));
  int elyos=2;
  for(int expected:List.of(2,3)){
   var rebuild=plan(stall,elyos,0);
   check(rebuild!=null && rebuild.character()==expected && rebuild.level()==56,"E4/A6 recruits the nearest protected-safe Elyos identity at the native lower bound");
   rebuilt(stall,rebuild);
   var join=plan(stall,elyos,0);
   check(join!=null && join.character()==expected && join.level()==0,"Rebuilt actor joins unchanged through native admission rather than rebuilding again");
   stall.removeIf(candidate->candidate.character()==join.character());elyos--;
  }
  check(plan(stall,elyos,0)==null,"E6/A6 stops recruitment even with additional ready or rebuildable actors");
  check(plan(List.of(actor(6,true,55,true,false)),1,0)==null,"Out-of-range actors with a rebuild protection remain unchanged");
  check(plan(List.of(actor(7,true,60,false,false)),1,0)==null,"An in-range actor that is unsafe or committed is not recruited");
  check(plan(List.of(actor(8,true,55,true,true)),0,1)==null,"An Elyos candidate cannot fill an Asmodian shortage");

  var priority=List.of(actor(9,true,55,true,true),actor(10,false,60,true,false));
  var ready=plan(priority,1,1);
  check(ready!=null && ready.character()==10 && ready.level()==0,"Every ready faction is considered before an earlier faction is rebuilt");
  var upper=PlayerBotPopulationQueues.plan(List.of(actor(11,true,65,true,true)),56,60,1,0);
  check(upper!=null && upper.level()==60,"A bot above the queue ceiling targets the native upper bound");
  var single=PlayerBotPopulationQueues.plan(List.of(actor(12,true,64,true,true)),65,65,1,0);
  check(single!=null && single.level()==65,"A single valid native level remains recruitable");

  var both=new ArrayList<>(List.of(actor(20,true,60,true,false),actor(21,false,62,true,false),
   actor(22,true,63,true,false),actor(23,false,64,true,false)));
  int e=2,a=2;
  for(int i=0;i<4;i++){
   var admission=plan(both,e,a);
   check(admission!=null && admission.level()==0,"Both factions progress through ready actors");
   var selected=both.stream().filter(candidate->candidate.character()==admission.character()).findFirst().orElseThrow();
   if(selected.elyos())e--;else a--;
   check(e>=0 && a>=0,"Neither faction exceeds its remaining seats");
   both.remove(selected);
  }
  check(e==0 && a==0 && plan(both,e,a)==null,"Both faction shortages are filled exactly");
  var available=List.of(actor(30,true,60,true,false),actor(31,false,60,true,false));
  check(plan(available,0,0)==null,"Cancelled demand schedules no work");
  check(plan(available,-1,-4)==null,"Over-capacity factions schedule no work");
  check(plan(List.of(),1,1)==null,"An exhausted eligible pool does not invent a character");
  for(int[] range:new int[][]{{0,65},{56,66},{65,56}})
   check(PlayerBotPopulationQueues.plan(available,range[0],range[1],1,1)==null,"Invalid native level range is refused");

  // Other native battlegrounds have larger faction caps; the planner must obey supplied vacancies.
  var large=new ArrayList<PlayerBotPopulationQueues.Candidate>();
  for(int i=0;i<50;i++)large.add(actor(100+i,i<25,65,true,false));
  e=24;a=24;
  for(int i=0;i<48;i++){
   var admission=plan(large,e,a);
   check(admission!=null && admission.level()==0,"A native 24-seat faction capacity is not silently limited to six");
   var selected=large.stream().filter(candidate->candidate.character()==admission.character()).findFirst().orElseThrow();
   if(selected.elyos())e--;else a--;
   check(e>=0 && a>=0,"Larger native capacity never overfills either faction");large.remove(selected);
  }
  check(e==0 && a==0 && large.size()==2 && plan(large,e,a)==null,"Full larger-capacity match leaves surplus actors in the world");
  System.out.println("OK: "+checks+" population queue checks");
 }
}
