package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Pure local-route regressions. No actors, database, native world or IDs. */
public final class PlayerBotWorldRepairCheck {
 private static int checks;
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 public static void main(String[] args){
  Point start=new Point(0,0,0),far=new Point(100,0,0);
  PlayerBotPathfinder.Ground wall=(from,x,y)->x>=3 && x<=5 && Math.abs(y)<4 ? null : new Point(x,y,0);
  var route=PlayerBotPathfinder.find(start,far,wall,128,()->false);
  check(!route.isEmpty(),"distant destination needs a validated local detour instead of an empty route");
  Point previous=start;boolean allProbed=true;
  for(var p:route){allProbed &= wall.step(previous,p.x(),p.y())!=null;previous=p;}
  check(allProbed,"every partial-route point passes the ground probe");
  check(previous.x()>5,"local detour makes progress past the obstruction");
  check(Math.hypot(previous.x()-far.x(),previous.y()-far.y())<100,"partial route advances toward the full goal");
  check(PlayerBotPathfinder.find(start,far,(from,x,y)->null,128,()->false).isEmpty(),"enclosed origin cannot invent a route");
  check(PlayerBotPathfinder.find(start,far,wall,128,()->true).isEmpty(),"expired search cannot invent progress");
  check(PlayerBotPathfinder.find(start,new Point(4,0,0),wall,128,()->false).isEmpty(),"near obstructed destination still requires complete reachability");
  check(PlayerBotPopulationPolicy.target(1000,1000,0)==1000,"world population independent of the 100-companion quota");
  check(PlayerBotPopulationPolicy.target(1000,1000,20)==980,"manual world residents keep their world capacity");
  check(PlayerBotPopulationPolicy.target(800,1000,20)==800,"smaller configured target retained");
  check(PlayerBotPopulationPolicy.target(1000,100,0)==100,"explicit world cap retained");
  check(PlayerBotPopulationPolicy.target(1000,10,20)==0,"overcommitted manual residents cannot yield a negative target");
  check(PlayerBotPopulationPolicy.nextLoad(List.of(1,2),Set.of(),Set.of(),Map.of(),0)==1,"first eligible resident can enter");
  check(PlayerBotPopulationPolicy.nextLoad(List.of(1,2),Set.of(),Set.of(),Map.of(1,30000L),1000)==2,"one failed resident cannot block the next admission");
  check(PlayerBotPopulationPolicy.nextLoad(List.of(1,2),Set.of(2),Set.of(),Map.of(1,30000L),1000)==null,"active residents and delayed failures are not admitted again");
  check(PlayerBotPopulationPolicy.nextLoad(List.of(1,2),Set.of(2),Set.of(),Map.of(1,30000L),30000)==1,"failed identity remains eligible after its retry delay");
  check(PlayerBotPopulationPolicy.nextLoad(List.of(1,2),Set.of(),Set.of(1),Map.of(),0)==2,"retiring resident cannot reenter");
  var a=new PlayerBotWorldActivities.Goal(12,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(1,2,10,0,0),10);
  var b=new PlayerBotWorldActivities.Goal(12,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(1,2,20,0,0),20);
  check(PlayerBotWorldActivities.spread(List.of(a,b),List.of(a),new Random(1)).equals(b),"active quest objective claims another spawn point");
  check(PlayerBotWorldActivities.spread(List.of(a,b),Collections.nCopies(100,a),new Random(1))==null,"quest objective destination retains upstream 100-visitor limit");
  var grind=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,a.place(),10);
  check(PlayerBotWorldActivities.spread(List.of(grind),List.of(grind),new Random(1))!=null,"grind has no one-per-point cap upstream");
  check(PlayerBotWorldActivities.spread(List.of(grind),Collections.nCopies(100,grind),new Random(1))==null,"grind destination retains upstream 100-visitor limit");
  var npc=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.CONVERSATION,a.place(),10);
  check(PlayerBotWorldActivities.spread(List.of(npc),Collections.nCopies(15,npc),new Random(1))==null,"quest giver retains upstream 15-visitor limit");
  check(PlayerBotWorldActivities.spread(List.of(a,npc),List.of(),new Random(1)).equals(a),"active quest still outranks optional new quest");
  Set<PlayerBotWorldActivities.Goal> distributed=new HashSet<>();for(int i=0;i<30;i++)distributed.add(PlayerBotWorldActivities.spread(List.of(a,b),List.of(),new Random(i*719L)));
  check(distributed.size()==2,"unoccupied spawn points are spread rather than always choosing the nearest");
  System.out.println("OK: "+checks+" world population and local-route repair checks");
 }
}
