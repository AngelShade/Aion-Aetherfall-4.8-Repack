package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.flypath.FlyPathEntry;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Native transport has already authorized/charged the journey. Complete the headless landing on its native timer. */
public final class PlayerBotHeadlessFlight {
 private record Journey(FlyPathEntry path,int map,int instance,Object flight,long until){}
 private static final Map<Integer,Journey> JOURNEYS=new ConcurrentHashMap<>();
 public static void begin(Player bot,FlyPathEntry path){
  if(!bot.isPlayerBot() || path==null || path.getTimeInMs()<=0 || bot.getWorldId()!=path.getStartWorldId())return;
  bot.setCurrentFlypath(path);
  JOURNEYS.put(bot.getObjectId(),new Journey(path,bot.getWorldId(),bot.getInstanceId(),bot.getFlightPath(),System.currentTimeMillis()+path.getTimeInMs()));
 }
 static boolean tick(Player bot){Journey j=JOURNEYS.get(bot.getObjectId());if(j==null)return false;
  if(bot.isDead() || !bot.isSpawned() || bot.getFlightPath()!=j.flight() || bot.getWorldId()!=j.map() || bot.getInstanceId()!=j.instance()){close(bot);return false;}
  if(System.currentTimeMillis()<j.until())return true;
  JOURNEYS.remove(bot.getObjectId(),j);var p=j.path();
  bot.getController().onFlyTeleportEnd();bot.setCurrentFlypath(null);
  TeleportService.teleportTo(bot,p.getEndWorldId(),p.getEndWorldId()==j.map()?j.instance():1,p.getEndX(),p.getEndY(),p.getEndZ(),bot.getHeading());
  PacketSendUtility.broadcastPacket(bot,new SM_EMOTION(bot,EmotionType.LAND_FLYTELEPORT),true);return true;
 }
 static void close(Player bot){Journey j=JOURNEYS.remove(bot.getObjectId());if(j!=null && bot.getFlightPath()==j.flight()){
  bot.setCurrentFlypath(null);bot.setFlightPath(null);bot.unsetState(com.aionemu.gameserver.model.gameobjects.state.CreatureState.FLYING);bot.setState(com.aionemu.gameserver.model.gameobjects.state.CreatureState.ACTIVE);
 }}
 private PlayerBotHeadlessFlight(){}
}
