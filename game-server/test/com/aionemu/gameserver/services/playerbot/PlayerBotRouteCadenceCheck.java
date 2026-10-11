package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Actual maintained helpers with inert ground and a fake clock; no World or timers. */
public final class PlayerBotRouteCadenceCheck {
 private static int checks;
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 private static Point p(float x,float y,float z){return new Point(x,y,z);}
 private static boolean near(Point a,Point b){return a!=null&&b!=null&&Math.abs(a.x()-b.x())<.01&&Math.abs(a.y()-b.y())<.01&&Math.abs(a.z()-b.z())<.01;}
 private static void finish(PlayerBotRouteSearch search,PlayerBotPathfinder.Ground ground){
  int turns=0;while(search.pending()&&turns++<1000)search.advance(ground,32,()->false);
  check(!search.pending(),"bounded fixture finishes supported search");
 }
 private static boolean wall(Point a,float x,float y){
  if((a.x()<18&&x>=18)||(a.x()>18&&x<=18)||a.x()==18||x==18){
   float crossing=a.x()==x?Math.min(a.y(),y):a.y()+(y-a.y())*(18-a.x())/(x-a.x());
   return crossing<20;
  }return false;
 }
 public static void main(String[] args)throws Exception{
  var flat=(PlayerBotPathfinder.Ground)(from,x,y)->p(x,y,from.z());
  var search=new PlayerBotRouteSearch(p(10,10,0),p(26,10,0),40,4096);
  var probes=new AtomicInteger();
  PlayerBotPathfinder.Ground obstacle=(from,x,y)->{probes.incrementAndGet();return wall(from,x,y)?null:p(x,y,from.z());};
  search.advance(obstacle,0,()->false);
  check(search.pending()&&probes.get()==0&&search.expanded()==0,"zero probe budget retains untouched intent");
  search.advance(obstacle,32,()->true);
  check(probes.get()==0&&search.expanded()==0,"expired deadline invokes no ground probe");
  search.advance(obstacle,1,()->false);
  check(probes.get()==1&&search.expanded()==1&&search.pending(),"one probe leaves a partially expanded node");
  int retained=search.retained();search.advance(obstacle,32,()->true);
  check(probes.get()==1&&search.retained()==retained&&search.expanded()==1,"deadline preserves partial cursor and supported cells");
  search.advance(obstacle,1,()->false);
  check(probes.get()==2&&search.expanded()==1,"resumption advances the same node rather than restarting expansion");
  finish(search,obstacle);
  check(!search.route().isEmpty()&&near(search.route().getLast(),p(26,10,0)),"obstacle detour reaches exact native destination");
  check(search.route().stream().anyMatch(q->q.y()>=20),"route goes around wall instead of through it");
  Point last=p(10,10,0);for(Point step:search.route()){
   check(!wall(last,step.x(),step.y()),"every returned edge respects supplied obstacle rejection");
   check(step.x()>=0&&step.y()>=0&&step.x()<40&&step.y()<40,"returned points remain inside native map bounds");last=step;
  }
  check(search.retained()==0,"successful route releases retained search cells");
  try{search.route().add(p(0,0,0));throw new AssertionError("mutable route");}catch(UnsupportedOperationException expected){checks++;}
  int before=probes.get();search.advance(obstacle,32,()->false);
  check(probes.get()==before,"completed search cannot restart on later polling");

  // The same XY must be revisited on the upper layer before the final connection.
  Point[] stairs={p(10,10,0),p(14,10,0),p(14,14,4),p(10,14,4),p(10,10,4),p(8,10,4)};
  PlayerBotPathfinder.Ground layered=(from,x,y)->{
   for(int i=0;i<stairs.length-1;i++)if(near(from,stairs[i])&&Math.abs(x-stairs[i+1].x())<.01&&Math.abs(y-stairs[i+1].y())<.01)return stairs[i+1];
   return null;
  };
  var layers=new PlayerBotRouteSearch(stairs[0],stairs[5],40,4096);finish(layers,layered);
  check(near(layers.route().isEmpty()?null:layers.route().getLast(),stairs[5]),"height-layer revisit reaches upper destination");
  check(layers.route().contains(stairs[4]),"same XY on upper floor retains distinct supported cell");

  for(PlayerBotPathfinder.Ground rejected:List.<PlayerBotPathfinder.Ground>of(
   (from,x,y)->null,(from,x,y)->p(x,y,Float.NaN),(from,x,y)->p(x+.2f,y,0),(from,x,y)->p(x,y,100))){
   var bad=new PlayerBotRouteSearch(p(10,10,0),p(12,10,0),40,4096);finish(bad,rejected);
   check(bad.route().isEmpty()&&bad.reason().equals("no supported ground route"),"null, nonfinite, clipped or unsupported-height edges never become routes");
  }
  var wrongFloor=new PlayerBotRouteSearch(p(10,10,0),p(12,10,4),40,32);finish(wrongFloor,flat);
  check(wrongFloor.route().isEmpty(),"nearby XY cannot satisfy goal on unsupported height layer");
  var tiny=new PlayerBotRouteSearch(p(10,10,0),p(30,30,0),40,2);finish(tiny,flat);
  check(tiny.retained()<=2&&tiny.route().isEmpty()&&tiny.reason().contains("capacity"),"capacity rejection bounds retained cells and publishes no partial route");
  var clamped=new PlayerBotRouteSearch(p(10,10,0),p(30,30,0),40,0);finish(clamped,flat);
  check(clamped.retained()<=1&&clamped.reason().contains("capacity"),"nonpositive capacity has a finite minimum");
  for(Point goal:new Point[]{p(-1,10,0),p(40,10,0),p(10,40,0),p(10,10,Float.NaN),null}){
   var bad=new PlayerBotRouteSearch(p(10,10,0),goal,40,4096);
   check(!bad.pending()&&bad.route().isEmpty()&&bad.reason().contains("bounds"),"malformed or outside destination fails before ground probing");
  }
  for(float size:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
   check(!new PlayerBotRouteSearch(p(10,10,0),p(12,10,0),size,4096).pending(),"invalid native map extent rejects search");
  check(!new PlayerBotRouteSearch(p(-1,10,0),p(12,10,0),40,4096).pending(),"invalid source rejects search");

  var cadence=new PlayerBotMovementCadence();
  check(!cadence.active(1000,false,false),"idle begins with ordinary cadence");
  check(cadence.active(1000,false,true),"actual movement activates responsive cadence");
  check(cadence.active(1599,false,false),"arrival retains handoff through599ms");
  check(!cadence.active(1600,false,false),"handoff expires at600ms");
  check(cadence.active(2000,false,true)&&cadence.active(2300,false,true),"continued motion refreshes bounded handoff");
  check(cadence.active(2899,false,false)&&!cadence.active(2900,false,false),"latest movement owns handoff boundary");
  cadence.moving(3000);
  check(!cadence.active(3100,true,true)&&!cadence.active(3100,true,false),"resting retains ordinary reaction even with stale movement or handoff");
  check(!cadence.active(3600,false,false),"rest does not refresh movement commitment");
  cadence.moving(4000);cadence.clear();check(!cadence.active(4001,false,false),"clear removes transient handoff");
  for(long ordinary:new long[]{100,200,400,3000}){
   check(PlayerBotMovementCadence.reaction(ordinary,true)==Math.min(ordinary,200),"movement caps reaction at200ms without slowing faster native decisions");
   check(PlayerBotMovementCadence.reaction(ordinary,false)==ordinary,"ordinary and resting deadlines remain unchanged");
  }
  check(PlayerBotNavigation.class.getDeclaredField("routeSearch").getType()==PlayerBotRouteSearch.class,"actual Navigation links maintained resumable search");
  check(PlayerBotSession.class.getDeclaredField("movementCadence").getType()==PlayerBotMovementCadence.class,"actual Session links maintained movement cadence");
  check(PlayerBotSession.class.getDeclaredMethod("reactionDelay").getReturnType()==long.class,"native decision deadline entry remains linked");
  System.out.println("OK: "+checks+" world-free supported route and movement cadence checks");
 }
}
