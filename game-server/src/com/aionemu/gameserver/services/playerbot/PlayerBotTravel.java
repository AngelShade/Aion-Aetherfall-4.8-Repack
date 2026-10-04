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
  synchronized(PlayerBotService.getInstance()){synchronized(session){
   if(session.closing() || !session.owner().isOnline() || !session.owner().isSpawned())throw new IllegalArgumentException("Wait until you and the companion are in the world.");
   if(!PlayerBotRecovery.ready(session))throw new IllegalArgumentException("Wait until you are alive and the party is out of combat before reviving and summoning companions.");
   PlayerBotRecovery.revive(session);
   if(!PlayerBotService.getInstance().relocate(session))throw new IllegalArgumentException("The companion cannot be summoned yet.");
   WAITING.remove(session.bot().getObjectId());PlayerBotQuestSync.returnToOwner(session.bot());
  }}
 }
 public static void summonAll(java.util.List<PlayerBotSession> sessions) {
  synchronized(PlayerBotService.getInstance()) {
   if(sessions.isEmpty())throw new IllegalArgumentException("Recruit a companion first.");
   for(var session:sessions)synchronized(session) {
    if(!PlayerBotRecovery.ready(session))throw new IllegalArgumentException("Wait until you are alive and the party is out of combat before reviving and summoning companions.");
   }
   for(var session:sessions)summon(session);
  }
 }
 static void close(PlayerBotSession session){POSITIONS.remove(session.bot().getObjectId());WAITING.remove(session.bot().getObjectId());}
 private PlayerBotTravel() {}
}
