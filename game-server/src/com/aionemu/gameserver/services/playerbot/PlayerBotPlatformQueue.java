package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Bounded per-instance physical lift queues, separate from destination visitor counts. */
final class PlayerBotPlatformQueue {
 private record Key(int map,int instance,int entity,int part,int dock){}
 private static final Map<Key,LinkedHashMap<Integer,Entry>> QUEUES=new HashMap<>();
 private static final class Entry { final Player bot;long touched;Entry(Player bot,long now){this.bot=bot;touched=now;} }
 private static Key key(Player bot,PlayerBotAnimatedLinks.Ride ride){return new Key(bot.getWorldId(),bot.getInstanceId(),ride.entity().id,ride.part(),ride.from().component());}
 static synchronized int enter(Player bot,PlayerBotAnimatedLinks.Ride ride,long now){
  QUEUES.values().forEach(q->q.values().removeIf(e->now-e.touched>15000 || !e.bot.isSpawned() || e.bot.isDead()));
  QUEUES.entrySet().removeIf(e->e.getValue().isEmpty());
  var q=QUEUES.computeIfAbsent(key(bot,ride),k->new LinkedHashMap<>());
  var entry=q.get(bot.getObjectId());
  if(entry==null){if(q.size()>=Math.max(1,Math.min(32,PlayerBotConfig.PLATFORM_QUEUE_LIMIT)))return -1;q.put(bot.getObjectId(),new Entry(bot,now));}
  else entry.touched=now;
  int rank=0;for(int id:q.keySet()){if(id==bot.getObjectId())return rank;rank++;}return -1;
 }
 static synchronized void leave(Player bot){QUEUES.values().forEach(q->q.remove(bot.getObjectId()));QUEUES.entrySet().removeIf(e->e.getValue().isEmpty());}
 private PlayerBotPlatformQueue(){}
}
