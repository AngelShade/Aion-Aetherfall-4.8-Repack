package com.aionemu.gameserver.services.playerbot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;

/** Follow completed owner teleports, including teleports within one map. */
public final class PlayerBotTravel {
 record Position(int map,int instance,PlayerBotNavigation.Point point,long time) {}
 private static final Map<Integer,Position> POSITIONS=new ConcurrentHashMap<>();
 private static final java.util.Set<Integer> WAITING=ConcurrentHashMap.newKeySet();
 static boolean jumped(Position previous,Position current,float speed) {
  if(previous==null || previous.map()!=current.map() || previous.instance()!=current.instance())return false;
  long elapsed=current.time()-previous.time();if(elapsed<0 || elapsed>10000)return false;
  double distance=PositionUtil.getDistance(previous.point().x(),previous.point().y(),previous.point().z(),current.point().x(),current.point().y(),current.point().z());
  return Double.isFinite(distance) && distance>Math.max(60,Math.max(1,speed)*elapsed/1000.0*2+25);
 }
 static boolean followTeleport(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();
  Position current=new Position(owner.getWorldId(),owner.getInstanceId(),new PlayerBotNavigation.Point(owner.getX(),owner.getY(),owner.getZ()),System.currentTimeMillis());
  Position previous=POSITIONS.put(bot.getObjectId(),current);
  if(PlayerBotPartyBehavior.catchUp(session))return true;
  if(jumped(previous,current,owner.getGameStats().getMovementSpeedFloat()))WAITING.add(bot.getObjectId());
  if(!WAITING.contains(bot.getObjectId()) || owner.getController().isInCombat() || bot.getController().isInCombat())return false;
  if(PlayerBotService.getInstance().relocate(session)){WAITING.remove(bot.getObjectId());return true;}
  return false;
 }
 public static void summon(PlayerBotSession session) {
  PlayerBotSummonPolicy.request();PlayerBotSummonPolicy.regroup(java.util.List.of(session));
 }
 public static void summonAll(java.util.List<PlayerBotSession> sessions) {
  PlayerBotSummonPolicy.request();PlayerBotSummonPolicy.regroup(java.util.List.copyOf(sessions));
 }
 static void close(PlayerBotSession session){POSITIONS.remove(session.bot().getObjectId());WAITING.remove(session.bot().getObjectId());}
 private PlayerBotTravel() {}
}
