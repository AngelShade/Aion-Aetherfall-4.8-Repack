/* Formation destinations follow mod-playerbots FollowActions.cpp's formation
 * contract. Stable slots, heading damping and Aion movement timing are local.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

public final class PlayerBotFormation {
 static final class State { int owner,slot; double angle; long time,following,packet; boolean initialized; }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 private static State state(Player owner,Player bot,int fallback) {
  return STATES.computeIfAbsent(bot.getObjectId(),id->{State s=new State();s.owner=owner.getObjectId();s.slot=fallback;return s;});
 }
 static double turn(double previous,double desired,double seconds) {
  double delta=Math.atan2(Math.sin(desired-previous),Math.cos(desired-previous));
  return previous+Math.max(-Math.max(0,seconds)*2.1,Math.min(Math.max(0,seconds)*2.1,delta));
 }
 static PlayerBotNavigation.Point offset(float x,float y,float z,double angle,int slot,Role role,boolean moving,float speed) {
  double side=(slot%2==0 ? -1 : 1)*(2.6+2.6*(slot/2));
  double front=role==Role.TANK ? 1.4 : role==Role.MELEE ? -1.5 : -3.8;
  if(moving)front+=Math.min(10,Math.max(0,speed)*.8);
  return new PlayerBotNavigation.Point(x+(float)(Math.cos(angle)*front-Math.sin(angle)*side),y+(float)(Math.sin(angle)*front+Math.cos(angle)*side),z);
 }
 static PlayerBotNavigation.Point destination(Player owner,Player bot,int fallback) {
  State s=state(owner,bot,fallback);long now=System.currentTimeMillis();double desired=owner.getHeading()*Math.PI/60;
  synchronized(s){if(!s.initialized){s.angle=desired;s.initialized=true;}else s.angle=turn(s.angle,desired,Math.min(1,(now-s.time)/1000.0));s.time=now;
   var point=PlayerBotFormationLayout.destination(owner,bot,s.angle);
   PlayerBotFollowIntent.remember(owner,bot,s.angle,point);return point;}
 }
 /** Formation is a placement preference, not permission to recruit/summon.
  * Try nearby connected space, then use the already occupied owner position. */
 static PlayerBotNavigation.Point arrival(Player owner,Player bot) {
  var point=destination(owner,bot,0);
  if(owner.isFlying())return point;
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();
  double angle=Math.atan2(point.y()-owner.getY(),point.x()-owner.getX());
  for(int attempt=0;attempt<9;attempt++) {
   float x=attempt==0?point.x():owner.getX()+(float)(Math.cos(angle+(attempt-1)*Math.PI/4)*1.2);
   float y=attempt==0?point.y():owner.getY()+(float)(Math.sin(angle+(attempt-1)*Math.PI/4)*1.2);
   var reached=geo.findGroundMovementCollision(owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),x,y,owner.getZ());
   if(reached!=null && Float.isFinite(reached.x) && Float.isFinite(reached.y) && Float.isFinite(reached.z)
    && Math.hypot(reached.x-owner.getX(),reached.y-owner.getY())>=.6
    && owner.getWorldMapInstance().getRegion(reached.x,reached.y,reached.z)!=null)
    return new PlayerBotNavigation.Point(reached.x,reached.y,reached.z);
  }
  return new PlayerBotNavigation.Point(owner.getX(),owner.getY(),owner.getZ());
 }
 static boolean needsFollow(Player owner,Player bot,int slot) {
  return needsFollow(owner,bot,destination(owner,bot,slot));
 }
 static boolean needsFollow(Player owner,Player bot,PlayerBotNavigation.Point point) {
  double distance=bot.isFlying()?PositionUtil.getDistance(bot,point.x(),point.y(),point.z()):Math.hypot(bot.getX()-point.x(),bot.getY()-point.y());
  // The destination's Z is a hint until the native strip proves the floor.
  // A sloping slot may be higher/lower than the leader without being another
  // floor. Large vertical separation still admits the dedicated descent path.
  boolean separateFloor=!bot.isFlying() && Math.abs(bot.getZ()-point.z())>Math.max(2,Math.hypot(point.x()-owner.getX(),point.y()-owner.getY())+1);
  return owner.getMoveController().isInMove() && PlayerBotFollowIntent.active(bot)
   || separateFloor || distance>(bot.getMoveController().isInMove() ? .6 : .75);
 }
 static void following(Player owner,Player bot,int slot){state(owner,bot,slot).following=System.currentTimeMillis()+PlayerBotFollowIntent.lifetime();}
 public static boolean sendUpdate(Player bot) {State s=STATES.get(bot.getObjectId());if(s==null)return true;long now=System.currentTimeMillis();if(now-s.packet<200)return false;s.packet=now;return true;}
 static double speed(double nativeSpeed,double leaderSpeed,double distance,boolean moving) {
  if(!Double.isFinite(nativeSpeed)||!Double.isFinite(leaderSpeed)||nativeSpeed<=0)return 1;
  double wanted=moving && leaderSpeed>0?leaderSpeed:nativeSpeed;
  double maximum=com.aionemu.gameserver.configs.main.PlayerBotConfig.COMPANION_CATCHUP_MULTIPLIER;
  if(Double.isFinite(maximum) && maximum>1 && Double.isFinite(distance) && distance>1) {
   double catchup=Math.max(nativeSpeed,wanted)*maximum;
   wanted+=(catchup-wanted)*Math.min(1,(distance-1)/8);
  }
  // At the role slot, match a slower moving leader instead of overshooting.
  // A stopped leader still needs rapid regrouping, rather than losing the bonus.
  return wanted/nativeSpeed;
 }
 public static double speedMultiplier(Player bot,float x,float y,float z) {
  State s=STATES.get(bot.getObjectId());
  boolean motion=bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover && mover.followMotionActive();
  if(s==null || !motion && (s.following<System.currentTimeMillis() || !PlayerBotFollowIntent.permitsSpeed(bot)))return 1;
  Player owner=com.aionemu.gameserver.world.World.getInstance().getPlayer(s.owner);
  if(owner==null || !owner.isOnline() || !owner.isSpawned() || owner.isDead() || !bot.canPerformMove()
   || bot.getEffectController().isUnderFear() || bot.getEffectController().isConfused() || bot.getEffectController().isAbnormalSet(com.aionemu.gameserver.skillengine.effect.AbnormalState.SLOW)
   || owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId())return 1;
  var goal=PlayerBotFollowIntent.goal(bot);if(goal==null)return 1;
  return speed(bot.getGameStats().getMovementSpeed().getCurrent()/1000f,owner.getGameStats().getMovementSpeedFloat(),PositionUtil.getDistance(bot,goal.x(),goal.y(),goal.z()),owner.getMoveController().isInMove());
 }
 static void close(Player bot){var state=STATES.remove(bot.getObjectId());if(state!=null)PlayerBotCombatPosition.closeFormation(state.owner);PlayerBotFollowIntent.forget(bot);PlayerBotSpacing.close(bot);}
 private PlayerBotFormation(){}
}
