/* Leader flight-state mirroring adapted from mod-playerbots MovementActions.cpp,
 * revision 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Upstream attribution: third-party/playerbots/AUTHORS.md. Aion flight APIs are local. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.geoEngine.collision.IgnoreProperties;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Order;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

public final class PlayerBotFlight {
 public static boolean followsFlyingOwner(Player bot) {
  if(!bot.isPlayerBot())return false;
  var owner=com.aionemu.gameserver.world.World.getInstance().getPlayer(bot.getPlayerBotOwnerId());
  return owner!=null && owner.isOnline() && owner.isSpawned() && owner.isInFlyingState() && owner.getPlayerGroup()!=null
   && owner.getPlayerGroup()==bot.getPlayerGroup() && owner.getWorldId()==bot.getWorldId() && owner.getInstanceId()==bot.getInstanceId()
   && PositionUtil.isInRange(owner,bot,60);
 }
 enum Transition { NONE, TAKE_OFF, GLIDE, LAND }
 static Transition transition(boolean follow, boolean ownerFlying, boolean ownerGliding, boolean botFlying, boolean botGliding, boolean grounded) {
  if (!follow) return Transition.NONE;
  if (ownerGliding && !botGliding) return Transition.GLIDE;
  if (ownerFlying && !ownerGliding && (!botFlying || botGliding)) return Transition.TAKE_OFF;
  if (!ownerFlying && (botFlying || botGliding) && grounded) return Transition.LAND;
  return Transition.NONE;
 }
 static void synchronize(Player owner, Player bot, Order order) {
  if (bot.isDead() || !bot.canPerformMove() || bot.isCasting() || owner.isUsingFlightTransporterOrWindstream()) return;
  float ground = GeoService.getInstance().getZ(bot.getWorldId(), bot.getX(), bot.getY(), bot.getZ(), bot.getInstanceId());
  boolean grounded = Float.isFinite(ground) && Math.abs(bot.getZ() - ground) <= 1.5;
  switch (transition(order == Order.FOLLOW || order == Order.PASSIVE, owner.isFlying(), owner.isInGlidingState(), bot.isInFlyingState(), bot.isInGlidingState(), grounded)) {
   case TAKE_OFF -> {
    if(bot.isInFlyingState() && bot.isInGlidingState()) { bot.getFlyController().onStopGliding(); break; }
    if (bot.getLifeStats().getCurrentFp() > 0 && bot.getFlyReuseTime() <= System.currentTimeMillis()) {
     bot.getMoveController().abortMove();
     if (bot.isInGlidingState()) bot.getFlyController().onStopGliding();
     if (!bot.getFlyController().startFly(true, false)) PlayerBotQuestSync.notice(bot, "I cannot take off here under my character's flight rules.", "flight", 30000);
    }
   }
   case GLIDE -> {
    if (bot.getLifeStats().getCurrentFp() > 0) {
     bot.getMoveController().abortMove();
     if (bot.getFlyController().switchToGliding())
      com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(bot,
       new com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION(bot, com.aionemu.gameserver.model.EmotionType.START_GLIDE), true);
    }
   }
   case LAND -> { bot.getMoveController().abortMove(); bot.getFlyController().endFly(true); }
   default -> { }
  }
 }
 static PlayerBotNavigation.Point step(PlayerBotNavigation.Point from, PlayerBotNavigation.Point to, float distance) {
  double length = PositionUtil.getDistance(from.x(), from.y(), from.z(), to.x(), to.y(), to.z());
  if (!Double.isFinite(length) || !Float.isFinite(distance) || distance <= 0 || length < 0.01) return null;
  float ratio = (float) Math.min(1, distance / length);
  return new PlayerBotNavigation.Point(from.x() + (to.x() - from.x()) * ratio, from.y() + (to.y() - from.y()) * ratio, from.z() + (to.z() - from.z()) * ratio);
 }
 static boolean move(Creature bot, float x, float y, float z, java.util.List<PlayerBotHazards.Area> hazards) {
  var from = new PlayerBotNavigation.Point(bot.getX(), bot.getY(), bot.getZ());
	var next = step(from, new PlayerBotNavigation.Point(x,y,z), Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2f));
  if (next == null || !GeoService.getInstance().canSee(bot, next.x(), next.y(), next.z(), IgnoreProperties.ANY_RACE)
   || !PlayerBotHazards.safePath(from, next, hazards)) { bot.getMoveController().abortMove(); return false; }
  byte heading = (byte) Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(next.y()-bot.getY(),next.x()-bot.getX()))/3),120);
  bot.getMoveController().setNewDirection(next.x(),next.y(),next.z(),heading);
  bot.getMoveController().startMovingToDestination();
  return true;
 }
 private PlayerBotFlight() {}
}
