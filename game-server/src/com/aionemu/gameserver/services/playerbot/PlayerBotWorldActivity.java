/* PlayerbotAI::AllowActive/AllowActivity, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Aion's owner-requested
 * policy activates a complete occupied map/instance and disables activity alone. */
package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayList;
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
  var players=World.getInstance().getAllPlayers();PlayerBotPlayerPresence.refresh(players);
  boolean tiers=PlayerBotDetailTier.enabled();
  var humans=new ArrayList<PlayerBotDetailTier.Human>();var bots=new ArrayList<PlayerBotDetailTier.Bot>();var legions=new HashSet<String>();
  for(var player:players) {
   if(tiers && player.isWorldBot() && player.isSpawned())
    bots.add(new PlayerBotDetailTier.Bot(player.getObjectId(),zone(player.getWorldId(),player.getInstanceId()),player.getX(),player.getY(),player.getZ()));
   if(!player.isPlayerBot() && player.isOnline() && player.isSpawned()) {
    next.add(zone(player.getWorldId(),player.getInstanceId()));
    if(tiers) {
     var known=new HashSet<Integer>();player.getKnownList().forEachPlayer(p->{if(p.isWorldBot())known.add(p.getObjectId());});
     humans.add(new PlayerBotDetailTier.Human(zone(player.getWorldId(),player.getInstanceId()),player.getX(),player.getY(),player.getZ(),known));
     if(player.getLegion()!=null)legions.add(PlayerBotDetailTier.legionZone(player.getLegion().getLegionId(),player.getWorldId(),player.getInstanceId()));
    }
    if(!PlayerBotPlayerPresence.enabled() && player.getInstanceId()==1 && PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_RIFTS && PlayerBotConfig.PVP_RIFT_HOME_ACTIVITY) {
     int home=PlayerBotTerritory.counterpart(player.getWorldId());if(home!=0)next.add(zone(home,1));
    }
   }
  }
  occupied=Set.copyOf(next);nextObservation=now+interval;
  PlayerBotDetailTier.observe(humans,bots,legions);
 }
 static boolean occupied(Player bot){return occupied.contains(zone(bot.getWorldId(),bot.getInstanceId()));}
 /** A human is on this map's open-world channel, as the dormancy observer last saw it. */
 static boolean occupiedMap(int map){return occupied.contains(zone(map,1));}
 static boolean allowed(PlayerBotSession session){
  return !PlayerBotConfig.WORLD_ONLY_OCCUPIED_MAPS || !session.autonomous() || session.worldExcursion() || occupied(session.bot());
 }
 /** A mob on an empty map may never finish a fight, so hate alone holds a bot awake only this long once blows have stopped. */
 static final long SETTLE_MS=30000;
 static boolean lingering(long now,long quietSince){return now-quietSince<SETTLE_MS;}
 private PlayerBotWorldActivity(){}
}
