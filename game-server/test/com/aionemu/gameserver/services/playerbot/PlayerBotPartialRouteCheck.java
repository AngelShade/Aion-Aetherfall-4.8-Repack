package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.world.WorldMap;
import com.aionemu.gameserver.world.WorldMapInstance;

/** Native terrain and actual Navigation streaming, with inert actors and no World/IDs/SQL/tasks. */
public final class PlayerBotPartialRouteCheck {
 static int checks;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
 static Object field(Object o,String key)throws Exception{return PlayerBotRouteContinuationCheck.value(o,key);}
 static List<Point> solve(PlayerBotRouteSearch search,PlayerBotPathfinder.Ground ground){
  for(int i=0;i<1000 && search.pending();i++)search.advance(ground,128,()->false);
  check(!search.pending(),"partial/full work terminates within bounded fixture turns");return search.route();
 }
 static void proved(List<Point> path,Point from,PlayerBotPathfinder.Ground ground){
  check(!path.isEmpty(),"a supported path is published");
  for(Point p:path){Point actual=ground.step(from,p.x(),p.y());check(actual!=null && distance(actual,p)<.15,"every published partial edge retains native support/body clearance");from=p;}
 }
 static class Map extends WorldMap {Map(){super(null);}@Override public int getWorldSize(){return 2000;}}
 static class Instance extends WorldMapInstance {
  WorldMap parent;Instance(){super(null,1,0,null);}@Override public WorldMap getParent(){return parent;}
  @Override public com.aionemu.gameserver.world.MapRegion getRegion(float x,float y,float z){return null;}
  @Override protected void initMapRegions(){} @Override public boolean isPersonal(){return false;}@Override public int getOwnerId(){return 0;}
 }
 static class Actor extends PlayerBotRouteContinuationCheck.RouteActor {
  WorldMapInstance map;@Override public WorldMapInstance getWorldMapInstance(){return map;}
 }
 static void production(Unsafe unsafe)throws Exception {
  var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.spawned=true;actor.movable=true;actor.world=197;actor.instance=1;actor.x=10;actor.y=10;
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);actor.stats=(PlayerBotRouteContinuationCheck.RouteStats)unsafe.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);
  var instance=(Instance)unsafe.allocateInstance(Instance.class);instance.parent=(Map)unsafe.allocateInstance(Map.class);actor.map=instance;
  var nav=new PlayerBotNavigation(actor);
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();var mapsField=geo.getClass().getDeclaredField("geoMaps");mapsField.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(java.util.Map<Integer,GeoMap>)mapsField.get(geo);var mesh=new GeoMap(197);
  PlayerBotRouteContinuationCheck.add(mesh,PlayerBotRouteContinuationCheck.floor(0,0,2000,2000,0));var previous=maps.put(197,mesh);
  var bf=PlayerBotNavigation.class.getDeclaredField("TERRAIN");bf.setAccessible(true);var budget=(PlayerBotNavigationBudget)bf.get(null);
  var credit=PlayerBotNavigationBudget.class.getDeclaredField("credit");credit.setAccessible(true);double saved=credit.getDouble(budget);
  Point goal=new Point(510,10,0);
  PlayerBotQuestObjectivesCheck.field(nav,"routeGoal",goal);PlayerBotQuestObjectivesCheck.field(nav,"routeWorld",197);PlayerBotQuestObjectivesCheck.field(nav,"routeInstance",1);
  PlayerBotQuestObjectivesCheck.field(nav,"routeSearch",new PlayerBotRouteSearch(new Point(10,10,0),goal,2000,4096,24));
  PlayerBotQuestObjectivesCheck.field(nav,"lastProgress",System.currentTimeMillis());
  try {
   boolean first=false,lookahead=false;
   for(int i=0;i<800 && distance(new Point(actor.x,actor.y,actor.z),goal)>.15;i++){
    credit.setDouble(budget,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
    nav.move(goal.x(),goal.y(),goal.z());
    lookahead |= (boolean)field(nav,"lookahead");
    if(actor.move.isInMove()){
     Point endpoint=new Point(actor.move.getTargetX2(),actor.move.getTargetY2(),actor.move.getTargetZ2());
     var supported=geo.findGroundMovementCollision(197,1,actor.x,actor.y,actor.z,endpoint.x(),endpoint.y(),endpoint.z());
     check(supported!=null && distance(endpoint,new Point(supported.x,supported.y,supported.z))<.15,"production mover only publishes actual native supported steps");
     if(!first){check(distance(endpoint,goal)>400,"movement starts before resolving the complete500m destination");first=true;}
     actor.x=endpoint.x();actor.y=endpoint.y();actor.z=endpoint.z();actor.move.moving=false;
    }
   }
   check(first && lookahead,"actual Navigation prefetches remaining terrain while following partial paths");
   check(distance(new Point(actor.x,actor.y,actor.z),goal)<.15,"partial segments earn final arrival rather than falsely completing a local endpoint");
   nav.stop();actor.x=10;actor.y=10;
   @SuppressWarnings("unchecked") var route=(Deque<Point>)field(nav,"route");route.add(new Point(20,10,0));route.add(new Point(34,10,0));
   PlayerBotQuestObjectivesCheck.field(nav,"routeGoal",goal);PlayerBotQuestObjectivesCheck.field(nav,"routeWorld",197);PlayerBotQuestObjectivesCheck.field(nav,"routeInstance",1);
   PlayerBotQuestObjectivesCheck.field(nav,"partialRoute",true);PlayerBotQuestObjectivesCheck.field(nav,"lastProgress",System.currentTimeMillis());
   nav.hazards(List.of(new PlayerBotHazards.Area(15,10,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE)));
   credit.setDouble(budget,-1e12);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(!nav.move(goal.x(),goal.y(),goal.z()),"new hazard blocks the existing earned path before movement");
   check(!nav.planningRoute() && !(boolean)field(nav,"lookahead"),"blocked old prefix discards planning from its unearned future endpoint");
   check(!budget.waiting(field(nav,"searchToken")),"discarded prefetch cannot retain global queue ownership");
  }finally{nav.stop();credit.setDouble(budget,saved);if(previous==null)maps.remove(197);else maps.put(197,previous);}
 }
 public static void main(String[] args)throws Exception {
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(Unsafe)uf.get(null);
  var mesh=new GeoMap(197);PlayerBotRouteContinuationCheck.add(mesh,PlayerBotRouteContinuationCheck.floor(0,0,2000,2000,0));
  Point origin=new Point(10,10,0);
  for(float length:new float[]{40,500,1300}){
   Point goal=new Point(10+length,10,0);var ground=PlayerBotRouteContinuationCheck.nativeGround(mesh,goal);
   var partial=new PlayerBotRouteSearch(origin,goal,2000,4096,24);var path=solve(partial,ground);proved(path,origin,ground);
   check(partial.partial() && distance(path.getLast(),goal)>1,"distant final goal remains distinct from supported incomplete path");
   check(partial.probed()<=64 && distance(origin,path.getLast())>=24,"cheap native segment starts movement without world-sized expansion");
   check(distance(origin,path.getLast())<30,"segment endpoint is a bounded local horizon");
   try{path.clear();throw new AssertionError("mutable route");}catch(UnsupportedOperationException expected){check(true,"published proof cannot be mutated by a caller");}
  }
  Point shortGoal=new Point(12.3f,10,0);var shortSearch=new PlayerBotRouteSearch(origin,shortGoal,2000,4096,24);
  check(solve(shortSearch,PlayerBotRouteContinuationCheck.nativeGround(mesh,shortGoal)).getLast().equals(shortGoal) && !shortSearch.partial(),"near2.3m combat movement requires actual final native connection");
  var wall=PlayerBotRouteContinuationCheck.level();PlayerBotRouteContinuationCheck.add(wall,PlayerBotRouteContinuationCheck.wall(35,0,35,160));
  Point behind=new Point(100,10,0);var ground=PlayerBotRouteContinuationCheck.nativeGround(wall,behind);
  var blocked=new PlayerBotRouteSearch(origin,behind,160,4096,24);var blockedPath=solve(blocked,ground);
  if(!blockedPath.isEmpty())proved(blockedPath,origin,ground);
  check(blockedPath.stream().allMatch(p->p.x()<35),"partial publication cannot cross a solid native wall");
  var denied=new PlayerBotRouteSearch(origin,new Point(510,10,0),2000,4096,24);
  check(solve(denied,(from,x,y)->null).isEmpty() && !denied.partial(),"absent floor cannot invent partial progress");
  var expired=new PlayerBotRouteSearch(origin,new Point(510,10,0),2000,4096,24);expired.advance((from,x,y)->new Point(x,y,0),128,()->true);
  check(expired.pending() && expired.route().isEmpty() && expired.probed()==0,"deadline alone cannot fabricate a segment");
  for(float invalid:new float[]{Float.NaN,0,3,129,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY})try{
   new PlayerBotRouteSearch(origin,new Point(100,10,0),2000,4096,invalid);throw new AssertionError("invalid segment admitted");
  }catch(IllegalArgumentException expected){check(true,"documented segment bounds reject malformed owner values");}
  production(unsafe);
  System.out.println("OK: "+checks+" native partial route, streaming, final-arrival and hazard-cancellation checks");
 }
 private PlayerBotPartialRouteCheck(){}
}
