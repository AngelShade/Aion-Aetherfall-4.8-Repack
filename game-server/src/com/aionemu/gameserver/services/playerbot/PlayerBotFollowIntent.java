/* FollowActions.cpp / MovementActions.cpp::Follow purpose, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Aion has no MotionMaster moving-target generator: refresh only a verified
 * direct formation destination, never a pathfinder waypoint or combat action. */
package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

public final class PlayerBotFollowIntent {
 private record Intent(Player owner,int slot,PlayerBotNavigation.Point target,List<PlayerBotHazards.Area> hazards,long expires,boolean direct) {}
 private record Geometry(Player owner,double front,double side,double angle,long time) {}
 private static final ConcurrentHashMap<Integer,Intent> INTENTS=new ConcurrentHashMap<>();
 private static final ConcurrentHashMap<Integer,Geometry> GEOMETRY=new ConcurrentHashMap<>();
 // Resolve native party/role/settings geometry on the AI thread. The mover
 // must never acquire the service/session locks while holding its own monitor.
 static void remember(Player owner,Player bot,double angle,PlayerBotNavigation.Point point){
  double dx=point.x()-owner.getX(),dy=point.y()-owner.getY();
  GEOMETRY.put(bot.getObjectId(),new Geometry(owner,dx*Math.cos(angle)+dy*Math.sin(angle),-dx*Math.sin(angle)+dy*Math.cos(angle),angle,System.currentTimeMillis()));
 }
 static PlayerBotNavigation.Point goal(Player bot){
  var geometry=GEOMETRY.get(bot.getObjectId());if(geometry==null)return null;
  long now=System.currentTimeMillis();var owner=geometry.owner();
  double angle=PlayerBotFormation.turn(geometry.angle(),owner.getHeading()*Math.PI/60,Math.min(1,Math.max(0,now-geometry.time())/1000.0));
  GEOMETRY.replace(bot.getObjectId(),geometry,new Geometry(owner,geometry.front(),geometry.side(),angle,now));
  return new PlayerBotNavigation.Point(owner.getX()+(float)(Math.cos(angle)*geometry.front()-Math.sin(angle)*geometry.side()),owner.getY()+(float)(Math.sin(angle)*geometry.front()+Math.cos(angle)*geometry.side()),owner.getZ());
 }
 static long lifetime(){return Math.max(1600,Math.min(10000,Math.max(0,com.aionemu.gameserver.configs.main.PlayerBotConfig.TICK_MS)*3L));}
 static boolean traveling(Order order,boolean combat,boolean moving,boolean alive,boolean incapacitated){
  return (order==Order.FOLLOW || order==Order.PASSIVE) && !combat && moving && alive && !incapacitated;
 }
 static double priority(boolean traveling){return traveling ? PlayerBotEngine.MOVE : PlayerBotEngine.DEFAULT;}
 static void bind(Player owner,Player bot,int slot,PlayerBotNavigation.Point goal,List<PlayerBotHazards.Area> hazards){
  var mover=bot.getMoveController();
  synchronized(mover){
   // Navigation may have selected a detour or a shorter collision-tested step.
   if(!mover.isInMove()){clear(bot);return;}
   boolean direct=PositionUtil.getDistance(goal.x(),goal.y(),goal.z(),mover.getTargetX2(),mover.getTargetY2(),mover.getTargetZ2())<=.4;
   INTENTS.put(bot.getObjectId(),new Intent(owner,slot,new PlayerBotNavigation.Point(mover.getTargetX2(),mover.getTargetY2(),mover.getTargetZ2()),List.copyOf(hazards),System.currentTimeMillis()+lifetime(),direct));
  }
 }
 static boolean matches(Player bot,PlayerBotNavigation.Point target){
  var mover=bot.getMoveController();
  return mover.getTargetX2()==target.x() && mover.getTargetY2()==target.y() && mover.getTargetZ2()==target.z();
 }
 public static boolean active(Player bot){
  var intent=INTENTS.get(bot.getObjectId());
  return intent!=null && intent.direct() && intent.expires()>=System.currentTimeMillis() && matches(bot,intent.target()) && intent.owner().getMoveController().isInMove();
 }
 static boolean permitsSpeed(Player bot){var intent=INTENTS.get(bot.getObjectId());return intent!=null && intent.expires()>=System.currentTimeMillis() && matches(bot,intent.target());}
 /** Called under the native mover monitor after the stale-task/cast guards. */
 public static void refresh(Player bot){
  Intent intent=INTENTS.get(bot.getObjectId());if(intent==null)return;
  Player owner=intent.owner();
  if(intent.expires()<System.currentTimeMillis() || !matches(bot,intent.target()) || !owner.isOnline() || !owner.isSpawned() || owner.isDead()
   || owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId() || owner.getController().isInCombat() || bot.getController().isInCombat()
   || bot.isCasting() || bot.isTrading() || bot.isLooting() || !bot.canPerformMove()) {clear(bot);return;}
  if(!owner.getMoveController().isInMove())return;
  if(!intent.direct())return;
  var goal=goal(bot);if(goal==null)return;
  var start=new PlayerBotNavigation.Point(bot.getX(),bot.getY(),bot.getZ());
  if(!PlayerBotHazards.safePath(start,goal,intent.hazards()))return;
  if(bot.isFlying()) {
   if(!GeoService.getInstance().canSee(bot,goal.x(),goal.y(),goal.z(),com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE))return;
  }else{
   var ground=GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),goal.x(),goal.y(),goal.z());
   if(ground==null || !Float.isFinite(ground.z) || Math.hypot(ground.x-goal.x(),ground.y-goal.y())>.3 || Math.abs(ground.z-goal.z())>2)return;
   goal=new PlayerBotNavigation.Point(ground.x,ground.y,ground.z);
  }
  var mover=bot.getMoveController();
  byte heading=(byte)Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(goal.y()-bot.getY(),goal.x()-bot.getX()))/3),120);
  mover.setNewDirection(goal.x(),goal.y(),goal.z(),heading);
  INTENTS.replace(bot.getObjectId(),intent,new Intent(owner,intent.slot(),goal,intent.hazards(),intent.expires(),true));
  mover.startMovingToDestination();
 }
 public static void clear(Player bot){INTENTS.remove(bot.getObjectId());}
 static void forget(Player bot){clear(bot);GEOMETRY.remove(bot.getObjectId());}
 private PlayerBotFollowIntent(){}
}
