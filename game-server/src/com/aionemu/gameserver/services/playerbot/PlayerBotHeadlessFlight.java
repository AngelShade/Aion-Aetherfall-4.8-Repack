package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.flypath.FlightPath;
import com.aionemu.gameserver.model.templates.flypath.FlyPathEntry;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Native transport has already authorized/charged the journey. Complete the headless landing on its native timer. */
public final class PlayerBotHeadlessFlight {
 private record Journey(FlyPathEntry path,int map,int instance,FlightPath flight,long until){}
 private static final Map<Integer,Journey> JOURNEYS=new ConcurrentHashMap<>();
 static boolean active(Player bot){return JOURNEYS.containsKey(bot.getObjectId());}
 public static void begin(Player bot,FlyPathEntry path){
  if(!bot.isPlayerBot() || path==null || path.getTimeInMs()<=0 || bot.getWorldId()!=path.getStartWorldId() || path.getEndWorldId()!=bot.getWorldId())return;
  bot.setCurrentFlypath(path);
  JOURNEYS.put(bot.getObjectId(),new Journey(path,bot.getWorldId(),bot.getInstanceId(),bot.getFlightPath(),System.currentTimeMillis()+path.getTimeInMs()));
 }
 static boolean tick(Player bot){Journey j=JOURNEYS.get(bot.getObjectId());if(j==null)return false;
  if(bot.isDead() || !bot.isSpawned() || bot.getFlightPath()!=j.flight() || bot.getWorldId()!=j.map() || bot.getInstanceId()!=j.instance()){close(bot);return false;}
  if(System.currentTimeMillis()<j.until())return true;
  JOURNEYS.remove(bot.getObjectId(),j);var p=j.path();
  // Supply the client's final position before native landing; its quest hook may teleport again.
  World.getInstance().updatePosition(bot,p.getEndX(),p.getEndY(),p.getEndZ(),bot.getHeading());
  bot.getController().onFlyTeleportEnd();
  PacketSendUtility.broadcastPacket(bot,new SM_EMOTION(bot,EmotionType.LAND_FLYTELEPORT),true);return true;
 }
 static void close(Player bot){Journey j=JOURNEYS.remove(bot.getObjectId());if(j!=null && bot.getFlightPath()==j.flight()){
  bot.setCurrentFlypath(null);bot.setFlightPath(null);bot.unsetState(com.aionemu.gameserver.model.gameobjects.state.CreatureState.FLYING);bot.setState(com.aionemu.gameserver.model.gameobjects.state.CreatureState.ACTIVE);
 }}
 private PlayerBotHeadlessFlight(){}
}
