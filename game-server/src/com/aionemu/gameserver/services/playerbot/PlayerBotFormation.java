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
 private static synchronized State state(Player owner,Player bot,int fallback) {
  return STATES.computeIfAbsent(bot.getObjectId(),id->{State s=new State();s.owner=owner.getObjectId();Set<Integer> used=new HashSet<>();STATES.values().stream().filter(v->v.owner==s.owner).forEach(v->used.add(v.slot));s.slot=0;while(used.contains(s.slot))s.slot++;return s;});
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
   return PlayerBotFormationLayout.destination(owner,bot,s.angle);}
 }
 static boolean needsFollow(Player owner,Player bot,int slot) {
  var point=destination(owner,bot,slot);
  return owner.getMoveController().isInMove() || PositionUtil.getDistance(bot,point.x(),point.y(),point.z())>(bot.getMoveController().isInMove() ? .8 : 1.6);
 }
 static void following(Player owner,Player bot,int slot){state(owner,bot,slot).following=System.currentTimeMillis()+1600;}
 public static boolean sendUpdate(Player bot) {State s=STATES.get(bot.getObjectId());if(s==null)return true;long now=System.currentTimeMillis();if(now-s.packet<200)return false;s.packet=now;return true;}
 static double speed(double nativeSpeed,double leaderSpeed,double distance,boolean moving) {
  if(!Double.isFinite(nativeSpeed)||!Double.isFinite(leaderSpeed)||nativeSpeed<=0)return 1;
  double wanted=moving ? Math.max(nativeSpeed,leaderSpeed) : nativeSpeed;
  if(distance>5)wanted*=Math.min(1.5,1+(distance-5)*.045);
  return Math.max(1,Math.min(4,wanted/nativeSpeed));
 }
 public static double speedMultiplier(Player bot,float x,float y,float z) {
  State s=STATES.get(bot.getObjectId());if(s==null || s.following<System.currentTimeMillis())return 1;
  Player owner=com.aionemu.gameserver.world.World.getInstance().getPlayer(s.owner);
  if(owner==null || owner.getController().isInCombat() || bot.getController().isInCombat() || !bot.canPerformMove()
   || bot.getEffectController().isUnderFear() || bot.getEffectController().isConfused() || bot.getEffectController().isAbnormalSet(com.aionemu.gameserver.skillengine.effect.AbnormalState.SLOW)
   || owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId() || PositionUtil.getDistance(owner,x,y,z)>20)return 1;
  return speed(bot.getGameStats().getMovementSpeed().getCurrent()/1000f,owner.getGameStats().getMovementSpeedFloat(),PositionUtil.getDistance(owner,bot),owner.getMoveController().isInMove());
 }
 static void close(Player bot){STATES.remove(bot.getObjectId());PlayerBotSpacing.close(bot);}
 private PlayerBotFormation(){}
}
