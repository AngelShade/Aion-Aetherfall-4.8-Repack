/* PlayerbotAI::AllowActive/AllowActivity, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Aion's owner-requested
 * policy activates a complete occupied map/instance and disables activity alone. */
package com.aionemu.gameserver.services.playerbot;

import java.util.HashSet;
import java.util.Set;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.World;

/** One human-presence observation shared by all actors, independent of population placement. */
final class PlayerBotWorldActivity {
 private static volatile Set<Long> occupied=Set.of();
 private static long nextObservation;
 private static long zone(int map,int instance){return ((long)map<<32)|Integer.toUnsignedLong(instance);}
 static void refresh(long now){
  if(now<nextObservation)return;
  int interval=PlayerBotConfig.WORLD_PRESENCE_REFRESH_MS;
  if(interval<100 || interval>10000)throw new IllegalArgumentException("World-bot presence refresh must be 100..10000 milliseconds");
  var next=new HashSet<Long>();
  for(var player:World.getInstance().getAllPlayers())
   if(!player.isPlayerBot() && player.isOnline() && player.isSpawned())next.add(zone(player.getWorldId(),player.getInstanceId()));
  occupied=Set.copyOf(next);nextObservation=now+interval;
 }
 static boolean occupied(Player bot){return occupied.contains(zone(bot.getWorldId(),bot.getInstanceId()));}
 static boolean allowed(PlayerBotSession session){
  return !PlayerBotConfig.WORLD_ONLY_OCCUPIED_MAPS || !session.autonomous() || occupied(session.bot());
 }
 private PlayerBotWorldActivity(){}
}
