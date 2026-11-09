package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotProfessionTravel.*;

/** The real trip state machine with recording native movement/arrival boundaries. */
public final class PlayerBotProfessionTravelCheck {
 static int checks;
 static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
 static final class Native implements Boundary {
  int map=1,instance=1,moves,stops;boolean allowed=true,move=true,arrived;double distance=100;
  final Set<Integer> unreachable=new HashSet<>();
  public int map(){return map;}public int instance(){return instance;}public boolean allowed(){return allowed;}
  public double distance(PlayerBotWorldTravel.Place p){return distance+p.x();}
  public boolean reachable(PlayerBotWorldTravel.Place p){return !unreachable.contains(p.npc());}
  public boolean arrived(Trip trip){return arrived;}public boolean move(PlayerBotWorldTravel.Place p){moves++;return move;}public void stop(){stops++;}
 }
 static final Plan TRAIN=new Plan(Stage.TRAIN,0,0,40001,0,Map.of(),Map.of(),"train");
 static final Plan CRAFT=new Plan(Stage.CRAFT,0,155000075,40001,150000009,Map.of(),Map.of(),"craft");
 static final List<PlayerBotWorldTravel.Place> STATIONS=List.of(new PlayerBotWorldTravel.Place(1,203780,0,0,0),new PlayerBotWorldTravel.Place(1,203781,20,0,0));
 static Trip trip(Native n){return PlayerBotProfessionTravel.begin(n,TRAIN,STATIONS,1000);}
 public static void main(String[] args){
  int sources=PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN,seconds=PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS,stall=PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS;
  try {
   PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN=2;PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS=120;PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS=30;
   var n=new Native();var t=trip(n);
   check(t!=null && t.destination.equals(STATIONS.getFirst()),"an awake bot selects the nearest reachable native trainer");
   check(PlayerBotProfessionTravel.step(n,t,true,1000).result()==Result.MOVING && n.moves==1,"an admitted visit delegates movement to the native owner");
   n.distance=20;check(PlayerBotProfessionTravel.step(n,t,true,2000).result()==Result.MOVING,"physical progress keeps the same trip alive");
   n.distance=3;n.arrived=true;var ready=PlayerBotProfessionTravel.step(n,t,true,3000);
   check(ready.result()==Result.READY && ready.walked() && n.stops==1,"actual movement and live arrival report one walked trip");
   check(PlayerBotProfessionTravel.step(n,t,true,4000).equals(ready) && n.moves==2,"a completed trip never moves again");
   n=new Native();n.arrived=true;ready=PlayerBotProfessionTravel.step(n,trip(n),true,1000);
   check(ready.result()==Result.READY && !ready.walked() && n.moves==0,"a bot already beside a station does not count a walk");
   n=new Native();check(PlayerBotProfessionTravel.begin(n,CRAFT,STATIONS,1000)!=null,"a craft uses the same station trip owner");
   n=new Native();n.allowed=false;
   check(trip(n)==null && n.moves==0,"a dormant bot never starts a trip");
   n=new Native();t=trip(n);n.allowed=false;
   check(PlayerBotProfessionTravel.step(n,t,true,2000).result()==Result.CANCELLED && n.moves==0,"dormancy cancels a planned trip before movement");
   n.allowed=true;check(PlayerBotProfessionTravel.step(n,t,true,3000).result()==Result.CANCELLED && n.moves==0,"a cancelled trip cannot revive after wakeup");
   n=new Native();t=trip(n);
   check(PlayerBotProfessionTravel.step(n,t,false,2000).result()==Result.CANCELLED && n.moves==0,"a higher priority wins without a movement or work attempt");
   for(String priority:List.of("combat","party","Find Group","road duty","quest turn-in","quest travel")){
    n=new Native();check(PlayerBotProfessionTravel.step(n,trip(n),false,1000).result()==Result.CANCELLED && n.moves==0,priority+" can withdraw trip admission");
   }
   n=new Native();n.move=false;t=trip(n);var failed=PlayerBotProfessionTravel.step(n,t,true,1000);int work=0;if(failed.result()==Result.FALLBACK)work++;
   check(failed.result()==Result.FALLBACK && n.moves==1 && work==1,"a failed native move immediately releases the same ledgered work turn");
   check(PlayerBotProfessionTravel.step(n,t,true,2000).equals(failed) && n.moves==1,"a failed trip cannot retry movement or stall the worker");
   n=new Native();t=trip(n);check(PlayerBotProfessionTravel.step(n,t,true,31000).result()==Result.FALLBACK && n.moves==0,"a stalled trip expires without another move");
   n=new Native();t=trip(n);n.distance=99;
   check(PlayerBotProfessionTravel.step(n,t,true,121000).result()==Result.FALLBACK && n.moves==0,"the total trip timeout holds even after fresh physical progress");
   n=new Native();t=trip(n);n.distance=3;n.arrived=true;
   check(PlayerBotProfessionTravel.step(n,t,true,121000).result()==Result.FALLBACK && n.moves==0,"a late station arrival cannot revive an expired trip");
   n=new Native();t=trip(n);n.map=2;
   check(PlayerBotProfessionTravel.step(n,t,true,2000).result()==Result.FALLBACK && n.moves==0,"a map change releases a trip without crossing maps");
   n=new Native();t=trip(n);n.instance=2;
   check(PlayerBotProfessionTravel.step(n,t,true,2000).result()==Result.FALLBACK && n.moves==0,"an instance change cannot reuse a station nomination");
   n=new Native();n.distance=3;
   check(PlayerBotProfessionTravel.step(n,trip(n),true,1000).result()==Result.FALLBACK && n.moves==0,"a missing live station at the destination falls back");
   n=new Native();n.unreachable.add(203780);t=trip(n);
   check(t!=null && t.destination.npc()==203781,"a native route failure skips a known unreachable nearer station");
   n.unreachable.add(203781);check(trip(n)==null,"no reachable station leaves the ledgered no-walk path available");
   n=new Native();t=trip(n);n.unreachable.add(203780);
   check(PlayerBotProfessionTravel.step(n,t,true,2000).result()==Result.FALLBACK && n.moves==0,"a fresh native route failure releases the trip");
   n=new Native();n.map=2;check(trip(n)==null,"an awake trip never picks a station on another map");
   n=new Native();check(PlayerBotProfessionTravel.begin(n,TRAIN,List.of(),1000)==null,"a map with no station cannot stall the turn");
   check(PlayerBotProfessionTravel.step(n,null,true,1000).result()==Result.FALLBACK && n.moves==0,"a missing destination falls back without movement");
   check(PlayerBotProfessionTravel.step(n,null,false,1000).result()==Result.CANCELLED && n.moves==0,"a higher priority cancels even when no station exists");
   var learn=new Plan(Stage.LEARN_RECIPE,0,1,40001,0,Map.of(),Map.of(),"");
   check(PlayerBotProfessionTravel.begin(n,learn,STATIONS,1000)==null,"ordinary design learning does not invent a station visit");
   n=new Native();n.unreachable.add(203780);PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN=1;
   check(trip(n)==null,"station selection respects the existing per-plan source cap");
   System.out.println("OK: "+checks+" world profession station trip checks");
  }finally{PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN=sources;PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS=seconds;PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS=stall;}
 }
 private PlayerBotProfessionTravelCheck(){}
}
