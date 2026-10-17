package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;
import com.aionemu.gameserver.geoEngine.models.GeoMap;

/** Real Navigation handoffs with inert actors; no World, IDs, SQL or movement tasks. */
public final class PlayerBotNavigationHandoffCheck {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Object field(Object actor,String name)throws Exception {
  var f=actor.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(actor);
 }
 static class ProbedMap extends GeoMap {
  int probes;
  ProbedMap(){super(197);}
  @Override public int collideWith(com.aionemu.gameserver.geoEngine.collision.Collidable shape,com.aionemu.gameserver.geoEngine.collision.CollisionResults results){probes++;return super.collideWith(shape,results);}
 }
 static void exhaustedGround(Unsafe unsafe)throws Exception {
  var bot=PlayerBotCpuPlanningCheck.actor(unsafe,10);
  var nav=new PlayerBotNavigation(bot);var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();
  var f=geo.getClass().getDeclaredField("geoMaps");f.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(geo);var mesh=new ProbedMap();
  PlayerBotRouteContinuationCheck.add(mesh,PlayerBotRouteContinuationCheck.floor(9.7f,9.7f,10.3f,10.3f,0));var previous=maps.put(197,mesh);
  boolean prepared=com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED;
  var quotaField=PlayerBotNavigation.class.getDeclaredField("TERRAIN");quotaField.setAccessible(true);
  var quota=quotaField.get(null);var credit=quota.getClass().getDeclaredField("credit");credit.setAccessible(true);double saved=credit.getDouble(quota);
  try {
   com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED=false;
   for(int turn=0;turn<30 && !Boolean.TRUE.equals(field(nav,"groundRouteUnavailable"));turn++) {
    credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(30,10,0);
   }
   check(Boolean.TRUE.equals(field(nav,"groundRouteUnavailable")),"Actual exhausted native ground search opens supported fallback");
   check(!nav.planningRoute() && !((PlayerBotNavigationBudget)quota).waiting(field(nav,"searchToken")),"Exhausted route releases its ground search and terrain ticket");
   credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(30,10,0);
   check(!nav.planningRoute(),"Retry cooldown cannot preempt fallback with another long-distance ground search");
   nav.stop();check(Boolean.FALSE.equals(field(nav,"groundRouteUnavailable")),"New orders clear exhausted-ground fallback custody");
  }finally{nav.stop();credit.setDouble(quota,saved);com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED=prepared;if(previous==null)maps.remove(197);else maps.put(197,previous);}
 }
 static void committedPath(Unsafe unsafe)throws Exception {
  var bot=PlayerBotCpuPlanningCheck.actor(unsafe,10);
  bot.spawned=true;bot.movable=true;bot.world=197;bot.instance=1;bot.x=10;bot.y=10;
  bot.move=new PlayerBotRouteContinuationCheck.RouteMove(bot);bot.stats=(PlayerBotRouteContinuationCheck.RouteStats)unsafe.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);
  var nav=new PlayerBotNavigation(bot);var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();
  var f=geo.getClass().getDeclaredField("geoMaps");f.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(geo);var mesh=new ProbedMap();
  PlayerBotRouteContinuationCheck.add(mesh,PlayerBotRouteContinuationCheck.floor(0,0,160,160,0));var previous=maps.put(197,mesh);
  boolean prepared=com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED;
  try {
   com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED=false;
   check(nav.move(20,10,0),"Initial native ground commitment is valid");int probes=mesh.probes;check(probes>0,"First commitment proves actual native physical path");
   nav.hazards(List.of(new PlayerBotHazards.Area(60,60,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE)));
   PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(nav.move(20,10,0) && mesh.probes==probes,"Unrelated visible hazard cannot rebuild an unchanged verified moving path");
   nav.hazards(List.of(new PlayerBotHazards.Area(15,10,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE)));
   // This fixture isolates fresh hazard invalidation. Shared steering admission
   // is covered separately; grant its normal work credit after the cold mesh probe.
   var quotaField=PlayerBotNavigation.class.getDeclaredField("TERRAIN");quotaField.setAccessible(true);
   var quota=quotaField.get(null);var credit=quota.getClass().getDeclaredField("credit");credit.setAccessible(true);credit.setDouble(quota,6e6);
   PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(20,10,0);
   check(mesh.probes>probes,"New hazard on committed path requires fresh geometry and avoidance");
   for(int turn=0;field(nav,"publishedDestination")==null && turn<7;turn++){
    credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(20,10,0);
   }
   check(PlayerBotHazards.safePath(new Point(bot.x,bot.y,bot.z),(Point)field(nav,"publishedDestination"),List.of(new PlayerBotHazards.Area(15,10,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE))),"Replacement published path remains safe against fresh hazard");
  }finally{nav.stop();com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED=prepared;if(previous==null)maps.remove(197);else maps.put(197,previous);}
 }
 public static void main(String[] args)throws Exception {
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(Unsafe)uf.get(null);
  if(args.length>0){committedPath(unsafe);System.out.println("OK: "+checks+" native committed-path probe checks");return;}
  var bot=(PlayerBotRouteContinuationCheck.RouteActor)unsafe.allocateInstance(PlayerBotRouteContinuationCheck.RouteActor.class);
  bot.spawned=true;bot.movable=true;bot.world=197;bot.instance=1;bot.x=10;bot.y=10;
  bot.move=new PlayerBotRouteContinuationCheck.RouteMove(bot);
  bot.stats=(PlayerBotRouteContinuationCheck.RouteStats)unsafe.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);
  var nav=new PlayerBotNavigation(bot);
  var f=PlayerBotNavigation.class.getDeclaredField("TERRAIN");f.setAccessible(true);var quota=(PlayerBotNavigationBudget)f.get(null);
  Object height=field(nav,"heightSearchToken"),ground=field(nav,"searchToken"),seed=new Object();
  long now=System.currentTimeMillis();
  var permit=PlayerBotNavigationBudget.class.getDeclaredField("credit");permit.setAccessible(true);double oldPermit=permit.getDouble(quota);
  try {
   permit.setDouble(quota,-1e12);
   quota.request(height,PlayerBotNavigationBudget.Priority.NEAR);
   check(quota.begin(height,PlayerBotNavigationBudget.Priority.NEAR)==null,"Height intent is queued while another terrain permit is occupied");
   check(quota.waiting(height),"Old height request remains queued before ground commitment");
   var search=new PlayerBotRouteSearch(new Point(10,10,0),new Point(30,10,0),160,4096);
   PlayerBotQuestObjectivesCheck.field(nav,"routeSearch",search);
   PlayerBotQuestObjectivesCheck.field(nav,"routeGoal",new Point(30,10,0));
   PlayerBotQuestObjectivesCheck.field(nav,"routeWorld",197);PlayerBotQuestObjectivesCheck.field(nav,"routeInstance",1);
   PlayerBotQuestObjectivesCheck.field(nav,"lastProgress",now);PlayerBotQuestObjectivesCheck.field(nav,"searchProgress",now);
   check(nav.move(30,10,0),"Pending real ground commitment remains admitted");
   check(!quota.waiting(height),"Ground planning revokes its own unreachable height ticket instead of waiting five seconds");
   check(quota.waiting(ground) && !quota.nominated(ground),"Real Navigation immediately promotes its valid ground commitment");
   permit.setDouble(quota,6e6);
   check(nav.waitingForSearchTurn(),"Production scheduler observes nominated reaction once actual work credit is available");
   permit.setDouble(quota,-1e12);
   check(search.probed()==0 && nav.planningRoute(),"Promotion never bypasses global terrain permit or drops retained search");
   bot.casting=true;check(!nav.waitingForSearchTurn() && !quota.waiting(ground),"Native casting cancels nomination while retaining normal cast authority");bot.casting=false;
   PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(nav.move(30,10,0) && quota.waiting(ground),"Interrupted commitment resumes fairly when casting ends");
   nav.stop();check(!nav.planningRoute() && !quota.waiting(ground) && !quota.waiting(height),"Explicit order cancellation removes both commitments");
   // New plans nominate without wasting the first available permit; unrelated
   // actors keep their original queue order and global rate limit.
   var isolated=new PlayerBotNavigationSearch();Object first=new Object(),second=new Object();
   isolated.request(first,0);check(isolated.waiting(first) && isolated.isTurn(first,0),"Creation queues a route before the first idle deadline");
   isolated.request(first,1);isolated.request(second,2);
   check(!isolated.isTurn(second,2) && isolated.acquire(first,2),"Repeated nomination is idempotent and consumes no work permit");
   check(!isolated.acquire(second,101) && isolated.acquire(second,102),"Immediate nomination preserves exact100ms global work spacing");
   isolated.request(first,103);isolated.cancel(first);check(!isolated.waiting(first),"Cancelled plan cannot retain queue ownership");
  }finally{nav.stop();quota.cancel(seed);permit.setDouble(quota,oldPermit);}
  committedPath(unsafe);
  exhaustedGround(unsafe);
  System.out.println("OK: "+checks+" actual navigation handoff, committed-path terrain work, hazard and quota checks; no World/IDs/SQL/tasks");
 }
 private PlayerBotNavigationHandoffCheck(){}
}
