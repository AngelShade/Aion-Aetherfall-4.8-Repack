package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Actual early Navigation callbacks on inert actors and real terrain tickets; no World/IDs/SQL/tasks. */
public final class PlayerBotNavigationPendingCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void field(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 private static Object value(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static Object allocate(Class<?> type)throws Exception{var u=Class.forName("sun.misc.Unsafe");var f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);}
 private static void arm(PlayerBotNavigation nav)throws Exception{field(nav,"routeWorld",197);field(nav,"routeInstance",1);field(nav,"routeGoal",new Point(50,50,0));field(nav,"nextMove",System.currentTimeMillis()+60000);}
 private static void local(PlayerBotNavigation nav)throws Exception{field(nav,"localOrigin",new Point(10,10,0));field(nav,"localGoal",new Point(50,50,0));field(nav,"localHazards",List.of());field(nav,"localCursor",0);field(nav,"localLimit",7);}
 public static void main(String[] args)throws Exception{
  var actor=(PlayerBotRouteContinuationCheck.RouteActor)allocate(PlayerBotRouteContinuationCheck.RouteActor.class);actor.spawned=true;actor.movable=true;actor.world=197;actor.instance=1;actor.x=10;actor.y=10;
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);actor.stats=(PlayerBotRouteContinuationCheck.RouteStats)allocate(PlayerBotRouteContinuationCheck.RouteStats.class);
  var nav=new PlayerBotNavigation(actor);var bf=PlayerBotNavigation.class.getDeclaredField("TERRAIN");bf.setAccessible(true);var budget=(PlayerBotNavigationBudget)bf.get(null);
  Object ground=value(nav,"searchToken"),step=value(nav,"localSearchToken"),height=value(nav,"heightSearchToken");
  try {
   arm(nav);var search=new PlayerBotRouteSearch(new Point(10,10,0),new Point(50,50,0),160,4096,24);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);
   Object deadline=value(nav,"nextMove");
   check(nav.move(50,50,0),"Early stationary callback preserves acceptance of the same owned pending ground intent");
   check(search.probed()==0 && value(nav,"routeSearch")==search && budget.waiting(ground) && value(nav,"nextMove").equals(deadline),"Accepted early callback spends no probes, retains custody and does not reset native movement cadence");
   check(!actor.move.isInMove(),"Pending acceptance never fabricates physical movement");
   check(nav.move(50.1f,50,0),"Minor coordinate noise retains existing destination authority");
   check(!nav.move(52,50,0),"Different destination cannot inherit predecessor pending success");
   budget.cancel(ground);check(!nav.move(50,50,0),"Unqueued obsolete retained search cannot falsely own early action acceptance");
   budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);field(nav,"routeSearch",new PlayerBotRouteSearch(new Point(10,10,0),new Point(52,50,0),160,4096,24));
   check(!nav.move(50,50,0),"Matching public routeGoal cannot legitimize a different retained query");
   var done=new PlayerBotRouteSearch(new Point(10,10,0),new Point(50,50,0),160,4096,24);done.advance((from,x,y)->new Point(x,y,0),128,()->false);field(nav,"routeSearch",done);
   check(!nav.move(50,50,0),"Completed query with orphan ticket cannot claim pending success");
   nav.stop();arm(nav);local(nav);budget.request(step,PlayerBotNavigationBudget.Priority.NEAR);
   check(nav.move(50,50,0) && budget.waiting(step),"Fresh local-origin pending step retains acceptance inside the movement interval");
   actor.x=11;check(!nav.move(50,50,0),"Actor displacement cannot accept a stale local-origin decision");actor.x=10;
   nav.hazards(List.of(new PlayerBotHazards.Area(12,10,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE)));
   check(!nav.move(50,50,0),"Changed hazards cannot inherit old local planning acceptance");nav.hazards(List.of());
   field(nav,"localCursor",7);check(!nav.move(50,50,0),"Exhausted local cursor cannot inherit an orphan ticket");
   nav.stop();arm(nav);local(nav);field(nav,"heightCursor",0);budget.request(height,PlayerBotNavigationBudget.Priority.NEAR);
   check(nav.move(50,50,0),"Same fresh pending height intent remains accepted without initiating a jump");
   field(nav,"heightCursor",25);check(!nav.move(50,50,0),"Completed height cursor cannot claim pending work");
   nav.stop();arm(nav);budget.request(step,PlayerBotNavigationBudget.Priority.NEAR);check(!nav.move(50,50,0),"Orphan ticket without local-origin intent cannot own an action");
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);
   check(!nav.move(Float.NaN,50,0) && !budget.waiting(ground) && value(nav,"routeSearch")==null,"Invalid destination is rejected and cancels stale custody before timing gate");
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);actor.casting=true;
   check(!nav.move(50,50,0) && !budget.waiting(ground),"Native casting rejects early acceptance and cancels planning ticket");actor.casting=false;
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);actor.dead=true;
   check(!nav.move(50,50,0) && !budget.waiting(ground),"Death cannot inherit pending success");actor.dead=false;
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);actor.movable=false;
   check(!nav.move(50,50,0) && !budget.waiting(ground),"Movement restriction cannot inherit pending success");actor.movable=true;
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);actor.spawned=false;
   check(!nav.move(50,50,0),"Despawned actor cannot accept a pending movement action");actor.spawned=true;
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);actor.world=198;
   check(!nav.move(50,50,0) && !budget.waiting(ground) && value(nav,"routeSearch")==null,"Map transfer revokes predecessor before early callback acceptance");actor.world=197;
   nav.stop();arm(nav);field(nav,"routeSearch",search);budget.request(ground,PlayerBotNavigationBudget.Priority.WORLD);actor.instance=2;
   check(!nav.move(50,50,0) && !budget.waiting(ground),"Instance transfer cannot inherit same-map predecessor");actor.instance=1;
   nav.stop();arm(nav);check(!nav.move(50,50,0),"Empty navigator remains genuinely idle");
  }finally{nav.stop();}
  System.out.println("OK: "+checks+" actual early-callback intent, terrain custody, stale-origin and native safety checks; no World/IDs/SQL/tasks");
 }
 private PlayerBotNavigationPendingCheck(){}
}
