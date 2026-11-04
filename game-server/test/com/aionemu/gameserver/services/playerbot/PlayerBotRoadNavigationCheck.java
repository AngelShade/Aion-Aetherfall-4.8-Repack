package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Actual navigation/mover corridor proofs on inert native geometry; no World, SQL, IDs or movement tasks. */
public final class PlayerBotRoadNavigationCheck {
 static int checks;
 static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);checks++;}
 static void field(Object object,String name,Object value)throws Exception {
  for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static Object value(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
 static Mesh mesh(float[] vertices,short[] triangles){var mesh=new Mesh();mesh.setVertices(FloatBuffer.wrap(vertices));mesh.setIndices(ShortBuffer.wrap(triangles));mesh.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());mesh.updateBound();return mesh;}
 static Mesh floor(float x0,float y0,float x1,float y1,float z){return mesh(new float[]{x0,y0,z,x1,y0,z,x1,y1,z,x0,y1,z},new short[]{0,1,2,0,2,3});}
 static Mesh wall(float x0,float y0,float x1,float y1){return mesh(new float[]{x0,y0,0,x1,y1,0,x1,y1,5,x0,y0,5},new short[]{0,1,2,0,2,3});}
 static void add(GeoMap map,Mesh mesh){var geometry=new Geometry("road fixture",mesh);geometry.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));geometry.updateModelBound();map.attachChild(geometry);map.updateModelBound();}
 static class Stats extends com.aionemu.gameserver.model.stats.container.PlayerGameStats {
  Stats(){super(null);}
  @Override public float getMovementSpeedFloat(){return 6;}
 }
 static class Control extends com.aionemu.gameserver.controllers.PlayerController {
  @Override public boolean isInCombat(){return false;}
 }
 static class RoadActor extends com.aionemu.gameserver.model.gameobjects.player.Player {
  float x,y,z;int world,instance;boolean spawned,movable;
  com.aionemu.gameserver.controllers.movement.PlayerMoveController actualMove;
  com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance appearance;
  Stats stats;Control control;
  RoadActor(){super(null,null);} // Unsafe allocation skips World, native IDs and account loading.
  @Override public float getX(){return x;}@Override public float getY(){return y;}@Override public float getZ(){return z;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public int getObjectId(){return 1900000197;}@Override public int getPlayerBotOwnerId(){return 0;}
  @Override public boolean isWorldBot(){return true;}@Override public boolean isSpawned(){return spawned;}
  @Override public boolean canPerformMove(){return movable;}@Override public boolean isCasting(){return false;}
  @Override public boolean isDead(){return false;}@Override public boolean isFlying(){return false;}
  @Override public boolean isTrading(){return false;}@Override public boolean isLooting(){return false;}
  @Override public byte getHeading(){return 0;}@Override public com.aionemu.gameserver.model.Race getRace(){return com.aionemu.gameserver.model.Race.ELYOS;}
  @Override public com.aionemu.gameserver.controllers.movement.PlayerMoveController getMoveController(){return actualMove;}
  @Override public com.aionemu.gameserver.controllers.PlayerController getController(){return control;}
  @Override public com.aionemu.gameserver.model.stats.container.PlayerGameStats getGameStats(){return stats;}
  @Override public com.aionemu.gameserver.model.Gender getGender(){return com.aionemu.gameserver.model.Gender.MALE;}
  @Override public com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance getPlayerAppearance(){return appearance;}
 }
 static class RoadMap extends GeoMap {
  int probes;RoadMap(){super(197);}
  @Override public int collideWith(com.aionemu.gameserver.geoEngine.collision.Collidable shape,com.aionemu.gameserver.geoEngine.collision.CollisionResults results){probes++;return super.collideWith(shape,results);}
 }
 static PlayerBotNavigation roadNavigation(RoadActor bot,Point goal)throws Exception {
  var mover=new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController(bot);bot.actualMove=mover;
  var nav=new PlayerBotNavigation(bot,false);long now=System.currentTimeMillis();
  field(nav,"routeWorld",197);field(nav,"routeInstance",1);
  field(nav,"routeGoal",goal);field(nav,"publishedGoal",goal);
  field(nav,"publishedDestination",goal);field(nav,"publishedWorld",197);field(nav,"publishedInstance",1);
  field(nav,"lastProgress",now);field(nav,"nextMove",now+60000);
  var intent=PlayerBotNavigation.class.getDeclaredField("routeIntent");intent.setAccessible(true);intent.set(nav,intent.getType().getEnumConstants()[0]);
  field(nav,"walkGeneration",41L);
  for(var entry:Map.of("walkGeneration",41L,"walkReached",4L,"walkUntil",now+60000).entrySet())field(mover,entry.getKey(),entry.getValue());
  field(mover,"walkWorld",197);field(mover,"walkInstance",1);
  field(mover,"walkActive",true);
  field(mover,"walkCurrent",new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint(5,goal.x(),goal.y(),goal.z()));
  mover.setInMove(true);return nav;
 }
 static void roadContinuation()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var bot=(RoadActor)unsafe.allocateInstance(RoadActor.class);bot.spawned=bot.movable=true;bot.world=197;bot.instance=1;bot.x=10;bot.y=10;
  bot.stats=(Stats)unsafe.allocateInstance(Stats.class);bot.control=new Control();bot.appearance=new com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance();bot.appearance.setHeightScale(1);
  var previousData=com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA;
  var data=new com.aionemu.gameserver.dataholders.WorldMapsData();var template=new com.aionemu.gameserver.model.templates.world.WorldMapTemplate();
  field(template,"waterlevel",-10);
  @SuppressWarnings("unchecked") var templates=(Map<Integer,com.aionemu.gameserver.model.templates.world.WorldMapTemplate>)value(data,"mapsById");templates.put(197,template);
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();f=geo.getClass().getDeclaredField("geoMaps");f.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(geo);var map=new RoadMap();add(map,floor(0,0,160,160,0));var previousMap=maps.put(197,map);
  com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA=data;
  Point goal=new Point(20,10,0);var ahead=List.of(new Point(22,10,0),new Point(24,10,0),new Point(26,10,0),new Point(26,12,0),new Point(24,10,0));
  PlayerBotNavigation nav=roadNavigation(bot,goal);
  var budget=(PlayerBotNavigationBudget)value(nav,"terrain");f=budget.getClass().getDeclaredField("credit");f.setAccessible(true);double savedCredit=f.getDouble(budget);
  try {
   var mover=(com.aionemu.gameserver.controllers.movement.PlayerBotMoveController)bot.actualMove;
   var current=mover.walkStatus().current();
   for(int turn=0;turn<20 && ((Deque<?>)value(nav,"route")).size()<6;turn++){f.setDouble(budget,1e9);check(nav.road(goal,ahead),"Road tail retains an admitted current edge while its bounded proof resumes");}
   @SuppressWarnings("unchecked") var route=(Deque<Point>)value(nav,"route");
   check(route.size()==6 && route.getLast().equals(ahead.getLast()),"Dense road lanes retain their individual corner points in the native corridor");
   check(mover.walkStatus().generation()==41 && mover.walkStatus().current().equals(current),"Appending roads never cancels or replaces the native current edge");
   int probes=map.probes;check(nav.road(goal,ahead) && map.probes==probes,"An already published road tail spends no repeated geometry probes");
   check(nav.road(ahead.getFirst(),ahead.subList(1,ahead.size())) && mover.walkStatus().generation()==41 && mover.walkStatus().current().equals(current),"Logical waypoint handoff keeps the same native movement generation and target");
   check(!nav.roadArrived(ahead.getFirst()),"A planned successor cannot claim physical arrival");
   field(mover,"walkReached",7L);
   field(mover,"walkCurrent",new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint(8,26,10,0));bot.x=24;
   check(nav.roadArrived(ahead.getFirst()) && nav.roadArrived(ahead.get(1)),"Actual same-generation acknowledgments recover waypoints passed between decisions");
   check(!nav.roadArrived(ahead.getFirst()) && !nav.roadArrived(ahead.get(2)),"Consumed arrival cannot acknowledge another visit or an unearned later index");
   check(!nav.roadArrived(new Point(26,10,5)),"Another floor is never acknowledged as the published lane");
   nav.roadPassed(ahead.get(2));nav.roadPassed(ahead.get(3));
   check(!nav.roadArrived(ahead.getLast()),"Spatially passed marks cannot acknowledge a later unearned visit to repeated coordinates");
   bot.world=198;check(!nav.roadArrived(ahead.get(2)),"Map changes cannot reuse road arrival acknowledgments");bot.world=197;
   field(mover,"walkGeneration",42L);check(!nav.roadArrived(ahead.get(2)),"A successor generation cannot inherit old road acknowledgments");
   mover.setInMove(false);field(mover,"walkActive",false);nav.stop();
   nav=roadNavigation(bot,goal);mover=(com.aionemu.gameserver.controllers.movement.PlayerBotMoveController)bot.actualMove;add(map,wall(21,0,21,20));
   budget=(PlayerBotNavigationBudget)value(nav,"terrain");f=budget.getClass().getDeclaredField("credit");f.setAccessible(true);f.setDouble(budget,1e9);
   check(nav.road(goal,ahead) && ((Deque<?>)value(nav,"route")).isEmpty(),"A blocked future road edge leaves the direct current publication intact");
   probes=map.probes;check(nav.road(goal,ahead) && map.probes==probes,"A refused unchanged road edge does not repeat its native probe every decision");
  }finally {
   bot.actualMove.setInMove(false);field(bot.actualMove,"walkActive",false);nav.stop();f.setDouble(budget,savedCredit);
   com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA=previousData;if(previousMap==null)maps.remove(197);else maps.put(197,previousMap);
  }
 }
 public static void main(String[] args)throws Exception {
  roadContinuation();System.out.println("OK: "+checks+" native road corridor, waypoint acknowledgment and blocked-edge cache checks");
 }
 private PlayerBotRoadNavigationCheck(){}
}
