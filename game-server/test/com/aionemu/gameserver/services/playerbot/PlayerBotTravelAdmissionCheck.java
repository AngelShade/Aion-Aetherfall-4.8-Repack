package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import sun.misc.Unsafe;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Native supported movement and production activity custody; no World, IDs, SQL or scheduled tasks. */
public final class PlayerBotTravelAdmissionCheck {
 static class MapShell extends com.aionemu.gameserver.world.WorldMap {
  MapShell(){super(null);} // Unsafe allocation bypasses native map registration.
  @Override public int getWorldSize(){return 160;}
 }
 static class SceneShell extends com.aionemu.gameserver.world.WorldMapInstance {
  MapShell map;SceneShell(){super(null,1,0,null);}
  @Override public com.aionemu.gameserver.world.WorldMap getParent(){return map;}
  @Override public com.aionemu.gameserver.world.MapRegion getRegion(float x,float y,float z){return null;}
  @Override protected void initMapRegions(){}
  @Override public boolean isPersonal(){return false;}
  @Override public int getOwnerId(){return 0;}
 }
 static class Actor extends PlayerBotCpuPlanningCheck.Actor {
  SceneShell scene;@Override public com.aionemu.gameserver.world.WorldMapInstance getWorldMapInstance(){return scene;}
 }
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void clocks(){
  var clock=new PlayerBotTravelClock();
  check(!clock.stalled(29999,0,false),"Active travel retains original thirty-second failure boundary");
  check(clock.stalled(30000,0,false),"Actual failed travel still expires");clock.reset();
  check(!clock.stalled(1000,0,true),"Terrain admission wait is not travel failure");
  check(!clock.stalled(61000,0,true),"Long terrain wait retains destination");
  check(!clock.stalled(62000,0,false),"Resuming excludes only observed wait time");
  check(!clock.stalled(63000,0,true),"Another wait suspends remaining travel allowance");
  check(!clock.stalled(123000,0,false),"Multiple waits retain accumulated active allowance");
  check(!clock.stalled(150999,0,false),"Active failure boundary remains exact after both waits");
  check(clock.stalled(151000,0,false),"Unsupported active travel cannot wait forever");
  clock.reset();check(clock.stalled(30000,0,false),"New goal never inherits predecessor wait credit");
  clock.reset();check(!clock.stalled(1000,0,true),"Waiting starts again after earned progress");
  check(!clock.stalled(500,0,false),"Backwards wall clock does not invent elapsed travel");
 }
 static void nativeTravel(boolean oldProof)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();f=geo.getClass().getDeclaredField("geoMaps");f.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(geo);var old=maps.put(197,PlayerBotRouteContinuationCheck.level());
  var actor=(Actor)u.allocateInstance(Actor.class);actor.id=870055;actor.spawned=true;actor.movable=true;actor.world=197;actor.instance=1;actor.x=actor.y=10;
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);actor.stats=(PlayerBotRouteContinuationCheck.RouteStats)u.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);
  actor.scene=(SceneShell)u.allocateInstance(SceneShell.class);actor.scene.map=(MapShell)u.allocateInstance(MapShell.class);
  var navigation=new PlayerBotNavigation(actor);
  f=PlayerBotNavigation.class.getDeclaredField("TERRAIN");f.setAccessible(true);var quota=(PlayerBotNavigationBudget)f.get(null);
  f=quota.getClass().getDeclaredField("credit");f.setAccessible(true);var credit=f;double saved=credit.getDouble(quota);
  try {
   credit.setDouble(quota,-1e12);navigation.move(20,10,0);
   check(!actor.move.moving,"Denied terrain work does not publish an unproved walking step");
   PlayerBotQuestObjectivesCheck.field(navigation,"lastProgress",System.currentTimeMillis()-5000);
   PlayerBotQuestObjectivesCheck.field(navigation,"closestGoalDistance",10d);
   // Reproduce a supported local step reached after prolonged admission waiting.
   for(int turn=0;!actor.move.moving && turn<24;turn++){
    credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(navigation,"nextMove",0L);navigation.move(20,10,0);
    if(PlayerBotRouteContinuationCheck.value(navigation,"routeSearch")!=null)throw new AssertionError("Queue delay must not divert a proved useful step into a full route restart");
   }
   check(PlayerBotRouteContinuationCheck.value(navigation,"routeSearch")==null,"Proved useful walking remains the first published commitment");
   check(actor.move.moving,"Native supported useful step publishes after terrain admission");
   check(actor.move.getTargetX2()>actor.x,"Published native destination makes actual forward progress");
   if(oldProof)return;
   navigation.stop();credit.setDouble(quota,-1e12);PlayerBotQuestObjectivesCheck.field(navigation,"nextMove",0L);navigation.move(20,10,0);
   check(navigation.waitingForTerrainWork(),"Actual denied native planning has explicit wait custody");
   var type=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var c=type.getDeclaredConstructor();c.setAccessible(true);var state=c.newInstance();
   var writer=PlayerBotWorldActivities.class.getDeclaredMethod("goal",type,PlayerBotWorldActivities.Goal.class);writer.setAccessible(true);
   var goal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(197,101,20,10,0),10);
   writer.invoke(null,state,goal);PlayerBotQuestObjectivesCheck.field(state,"best",10d);
   long last=System.currentTimeMillis()-60000;PlayerBotQuestObjectivesCheck.field(state,"lastProgress",last);
   var statesField=PlayerBotWorldActivities.class.getDeclaredField("STATES");statesField.setAccessible(true);
   @SuppressWarnings("unchecked") var states=(Map<Integer,Object>)statesField.get(null);states.put(actor.id,state);
   PlayerBotWorldActivities.prepare(actor,navigation);
   check(PlayerBotRouteContinuationCheck.value(state,"goal")==goal,"Production activity retains goal beyond thirty seconds of terrain waiting");
   check((long)PlayerBotRouteContinuationCheck.value(state,"lastProgress")==last,"Waiting never grants fake movement progress");
   check(((Map<?,?>)PlayerBotRouteContinuationCheck.value(state,"excluded")).isEmpty(),"Waiting cannot blacklist a valid destination");
   navigation.stop();
   PlayerBotQuestObjectivesCheck.field(navigation,"routeSearch",new PlayerBotRouteSearch(new Point(10,10,0),new Point(20,10,0),160,4096));
   PlayerBotQuestObjectivesCheck.field(navigation,"searchProgress",last);
   quota.request(PlayerBotRouteContinuationCheck.value(navigation,"searchToken"),PlayerBotNavigationBudget.Priority.WORLD);
   PlayerBotWorldActivities.prepare(actor,navigation);
   check(PlayerBotRouteContinuationCheck.value(state,"goal")==goal,"Queued full route with zero new native probes retains its destination");
   check((long)PlayerBotRouteContinuationCheck.value(state,"lastProgress")==last,"Queued full route cannot rewrite actual progress time");
   check(((Map<?,?>)PlayerBotRouteContinuationCheck.value(state,"excluded")).isEmpty(),"Zero-probe admission wait cannot blacklist destination");
   PlayerBotWorldActivities.close(actor);check(!states.containsKey(actor.id),"Closing releases activity state normally");
  }finally{navigation.stop();PlayerBotWorldActivities.close(actor);credit.setDouble(quota,saved);if(old==null)maps.remove(197);else maps.put(197,old);}
 }
 public static void main(String[]args)throws Exception{nativeTravel(args.length>0);if(args.length==0)clocks();System.out.println("OK: "+checks+" native travel admission and active failure-clock checks; no World/IDs/SQL/tasks");}
 private PlayerBotTravelAdmissionCheck(){}
}
