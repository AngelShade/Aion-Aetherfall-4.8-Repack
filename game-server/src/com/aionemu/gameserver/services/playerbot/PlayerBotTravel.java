package com.aionemu.gameserver.services.playerbot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;

/** Follow completed owner teleports, including teleports within one map. */
public final class PlayerBotTravel {
 record Position(int map,int instance,PlayerBotNavigation.Point point,long time) {}
 private static final Map<Integer,Position> POSITIONS=new ConcurrentHashMap<>();
 private static final java.util.Set<Integer> WAITING=ConcurrentHashMap.newKeySet();
 private record PortalPass(int map,int instance,int npc,float x,float y,float z,long expires) {}
 private static final Map<Integer,PortalPass> DREDGION_PASSES=new ConcurrentHashMap<>();
 static boolean jumped(Position previous,Position current,float speed) {
  if(previous==null || previous.map()!=current.map() || previous.instance()!=current.instance())return false;
  long elapsed=current.time()-previous.time();if(elapsed<0 || elapsed>10000)return false;
  double distance=PositionUtil.getDistance(previous.point().x(),previous.point().y(),previous.point().z(),current.point().x(),current.point().y(),current.point().z());
  return Double.isFinite(distance) && distance>Math.max(60,Math.max(1,speed)*elapsed/1000.0*2+25);
 }
 static boolean followTeleport(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();
  if(PlayerBotDredgion.aboard(bot) && !PlayerBotTransfers.different(owner,bot)){close(session);return false;}
  Position current=new Position(owner.getWorldId(),owner.getInstanceId(),new PlayerBotNavigation.Point(owner.getX(),owner.getY(),owner.getZ()),System.currentTimeMillis());
  Position previous=POSITIONS.put(bot.getObjectId(),current);
  if(PlayerBotPartyBehavior.catchUp(session))return true;
  if(jumped(previous,current,owner.getGameStats().getMovementSpeedFloat()))WAITING.add(bot.getObjectId());
  if(!WAITING.contains(bot.getObjectId()) || owner.getController().isInCombat() || bot.getController().isInCombat())return false;
  if(PlayerBotService.getInstance().relocate(session)){WAITING.remove(bot.getObjectId());return true;}
  return false;
 }
 public static void observeDredgionPortal(Player owner,Npc portal) {
  if(owner.isPlayerBot() || !PlayerBotDredgion.aboard(owner) || portal==null
   || portal.getWorldMapInstance()!=owner.getWorldMapInstance()
   || !PlayerBotDredgion.portal(PlayerBotDredgion.ship(owner.getWorldId()),portal.getNpcId()))return;
  long now=System.currentTimeMillis();
  DREDGION_PASSES.values().removeIf(pass->now>=pass.expires());
  DREDGION_PASSES.put(owner.getObjectId(),new PortalPass(owner.getWorldId(),owner.getInstanceId(),portal.getObjectId(),
   owner.getX(),owner.getY(),owner.getZ(),now+90000));
 }
 static boolean followDredgionPortal(PlayerBotSession session,PlayerBotNavigation navigation) {
  return followDredgionPortal(session.owner(),session.bot(),navigation);
 }
 static boolean followDredgionPortal(Player owner,Player bot,PlayerBotNavigation navigation) {
  var pass=DREDGION_PASSES.get(owner.getObjectId());
  if(pass==null || System.currentTimeMillis()>=pass.expires() || pass.map()!=bot.getWorldId() || pass.instance()!=bot.getInstanceId()
   || PlayerBotTransfers.different(owner,bot) || !owner.isSpawned() || owner.isDead() || bot.isDead() || PlayerBotSummonPolicy.busy(bot)
   || bot.getController().isInCombat() || !bot.canPerformMove())return false;
  if(!(bot.getWorldMapInstance().getObject(pass.npc()) instanceof Npc portal) || !portal.isSpawned() || portal.isDead())return false;
  double distance=PositionUtil.getDistance(bot,portal);
  if(distance>60 || distance>=PositionUtil.getDistance(bot,pass.x(),pass.y(),pass.z())
   || PositionUtil.getDistance(owner,pass.x(),pass.y(),pass.z())>=PositionUtil.getDistance(owner,portal))return false;
  if(PlayerBotDredgion.canUsePortal(bot,portal)){
   navigation.stop();
   portal.getController().onDialogRequest(bot);
   return true;
  }
  return navigation.approach(portal,1.5f);
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
