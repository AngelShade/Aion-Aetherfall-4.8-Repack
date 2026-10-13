package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Production pending-route custody with an already walking inert actor. No World/IDs/SQL/tasks. */
public final class PlayerBotEarlyRoutePrefetchCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Object get(Object o,String name)throws Exception{return PlayerBotRouteContinuationCheck.value(o,name);}
 private static void set(Object o,String name,Object value)throws Exception{PlayerBotQuestObjectivesCheck.field(o,name,value);}
 public static void main(String[] args)throws Exception{
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var u=(Unsafe)uf.get(null);
  var actor=(PlayerBotPartialRouteCheck.Actor)u.allocateInstance(PlayerBotPartialRouteCheck.Actor.class);
  actor.spawned=true;actor.movable=true;actor.world=197;actor.instance=1;actor.x=10;actor.y=10;
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);
  actor.stats=(PlayerBotRouteContinuationCheck.RouteStats)u.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);
  var instance=(PlayerBotPartialRouteCheck.Instance)u.allocateInstance(PlayerBotPartialRouteCheck.Instance.class);
  instance.parent=(PlayerBotPartialRouteCheck.Map)u.allocateInstance(PlayerBotPartialRouteCheck.Map.class);actor.map=instance;
  var nav=new PlayerBotNavigation(actor);Point goal=new Point(510,10,0),first=new Point(14,10,0),end=new Point(34,10,0);
  @SuppressWarnings("unchecked") var route=(Deque<Point>)get(nav,"route");
  for(int x=14;x<=34;x+=4)route.add(new Point(x,10,0));
  set(nav,"routeWorld",197);set(nav,"routeInstance",1);set(nav,"routeGoal",goal);set(nav,"partialRoute",true);
  set(nav,"publishedGoal",goal);set(nav,"publishedDestination",first);set(nav,"publishedWorld",197);set(nav,"publishedInstance",1);
  set(nav,"closestGoalDistance",500d);set(nav,"lastProgress",System.currentTimeMillis());
  actor.move.setNewDirection(first.x(),first.y(),first.z(),(byte)0);actor.move.moving=true;
  var bf=PlayerBotNavigation.class.getDeclaredField("TERRAIN");bf.setAccessible(true);var budget=(PlayerBotNavigationBudget)bf.get(null);
  var cf=PlayerBotNavigationBudget.class.getDeclaredField("credit");cf.setAccessible(true);double saved=cf.getDouble(budget);
  try{
   cf.setDouble(budget,-1e12);set(nav,"nextMove",0L);
   check(nav.move(goal.x(),goal.y(),goal.z()),"Current native movement remains accepted while future work waits");
   var search=(PlayerBotRouteSearch)get(nav,"routeSearch");
   check(search!=null && (boolean)get(nav,"lookahead"),"Prefetch begins with the entire six-edge earned prefix still available");
   check(get(search,"start").equals(end) && search.targets(goal),"Future search begins at proved endpoint and retains actual final goal");
   check(search.probed()==0 && budget.waiting(get(nav,"searchToken")),"Unfunded prefetch owns a queue request without uncharged native probes");
   check(actor.move.isInMove() && actor.move.getTargetX2()==first.x(),"Awaiting prefetch does not stop or redirect the current native mover");
   check(!budget.waiting(get(nav,"localSearchToken")),"An unchanged already walking edge does not create competing local proof work");
   set(nav,"nextMove",0L);actor.x=11;
   check(nav.move(goal.x(),goal.y(),goal.z()) && get(nav,"routeSearch")==search,"Earned actor progress retains one future query instead of replacing it");
   check(search.probed()==0 && route.size()==6,"No quota and no arrival produce neither new proof nor consumed waypoints");
   nav.hazards(List.of(new PlayerBotHazards.Area(12,10,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE)));set(nav,"nextMove",0L);
   check(!nav.move(goal.x(),goal.y(),goal.z()),"A fresh on-path hazard revokes the current unsafe commitment before queue admission");
   check(!actor.move.isInMove() && !nav.planningRoute() && !budget.waiting(get(nav,"searchToken")),"Hazard cancellation also releases unearned future work");
   check(route.isEmpty(),"Unsafe prefix is not retained as a route around an unproved obstruction");
  }finally{nav.stop();cf.setDouble(budget,saved);}
  System.out.println("OK: "+checks+" actual early-prefetch, native movement, quota and hazard custody checks");
 }
 private PlayerBotEarlyRoutePrefetchCheck(){}
}
