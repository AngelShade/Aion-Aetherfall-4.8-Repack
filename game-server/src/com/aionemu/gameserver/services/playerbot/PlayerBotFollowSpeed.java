package com.aionemu.gameserver.services.playerbot;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** One following speed for native server movement, spawn vectors and client interpolation. */
public final class PlayerBotFollowSpeed {
 private record Value(double ratio,float advertised,long expires){}
 private static final ConcurrentHashMap<Integer,Value> VALUES=new ConcurrentHashMap<>();
 public static float speed(Player player,float nativeSpeed){
  var value=VALUES.get(player.getObjectId());
  if(!player.isPlayerBot() || value==null || value.expires()<System.currentTimeMillis() || !player.getMoveController().isInMove() || player.getController().isInCombat())return nativeSpeed;
  return (float)(nativeSpeed*value.ratio());
 }
 public static void update(Player player,float x,float y,float z){
  if(!player.isPlayerBot())return;
  double ratio=PlayerBotFormation.speedMultiplier(player,x,y,z);float natural=player.getGameStats().getMovementSpeed().getCurrent()/1000f,desired=(float)(natural*ratio);var old=VALUES.get(player.getObjectId());
  VALUES.put(player.getObjectId(),new Value(ratio,old==null ? desired : old.advertised(),System.currentTimeMillis()+600));
  if(old==null || Math.abs(desired-old.advertised())>.15f){VALUES.put(player.getObjectId(),new Value(ratio,desired,System.currentTimeMillis()+600));PacketSendUtility.broadcastToSightedPlayers(player,new SM_EMOTION(player,EmotionType.CHANGE_SPEED),true);}
 }
 public static void close(Player player){if(VALUES.remove(player.getObjectId())!=null)PacketSendUtility.broadcastToSightedPlayers(player,new SM_EMOTION(player,EmotionType.CHANGE_SPEED),true);}
 private PlayerBotFollowSpeed(){}
}
