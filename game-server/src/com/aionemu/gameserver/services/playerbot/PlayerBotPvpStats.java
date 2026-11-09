package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.EmotionId;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** PvP-1 measurement: what bots actually do in PvP, by map. RAM only, reset at start. */
public final class PlayerBotPvpStats {
 static final AtomicLong STARTED=new AtomicLong(),DEFENDED=new AtomicLong();
 static final AtomicLong KILLS_HUMANS=new AtomicLong(),KILLS_BOTS=new AtomicLong(),DEATHS_HUMANS=new AtomicLong(),DEATHS_BOTS=new AtomicLong();
 static final AtomicLong SHARED_INVITED=new AtomicLong(),SHARED_PLACED=new AtomicLong(),RIFT_INVITED=new AtomicLong(),RIFT_STAGED=new AtomicLong(),RIFT_CROSSED=new AtomicLong();
 static final AtomicLong SEEKS=new AtomicLong(),SEEK_ARRIVED=new AtomicLong(),DEFENDS=new AtomicLong(),RIFT_ALERTS=new AtomicLong(),BATTLEGROUND_SEEKS=new AtomicLong();
 /** rift exit -> last alert time; one alert per rift every ALERT_MS. */
 private static final Map<Integer,Long> ALERTED=new ConcurrentHashMap<>();
 static final long ALERT_MS=120000;
 static final float ALERT_RANGE=400;
 /** PvP-2: the first invader of a wave out of a rift tells enemy humans nearby, so a raid is noticed. */
 static void arrived(Player bot,int exitId,long now) {
  Long last=ALERTED.get(exitId);if(last!=null && now-last<ALERT_MS)return;
  ALERTED.put(exitId,now);ALERTED.values().removeIf(at->now-at>=ALERT_MS*5);
  String who=bot.getRace()==com.aionemu.gameserver.model.Race.ASMODIANS?"Asmodians":"Elyos";boolean told=false;
  for(var p:com.aionemu.gameserver.world.World.getInstance().getAllPlayers())
   if(!p.isPlayerBot() && p.isOnline() && p.getRace()!=bot.getRace() && p.getWorldId()==bot.getWorldId() && p.getInstanceId()==bot.getInstanceId()
    && com.aionemu.gameserver.utils.PositionUtil.isInRange(p,bot,ALERT_RANGE)){PacketSendUtility.sendMessage(p,"[PvP] Enemy "+who+" came through a rift nearby!");told=true;}
  if(told)RIFT_ALERTS.incrementAndGet();
 }
 private static final Map<Integer,AtomicLong> KILLS_BY_MAP=new ConcurrentHashMap<>();
 /** PlayerBotPvpTactics in use: kite, strafe, interrupt, defend, burst, snare. */
 private static final Map<String,AtomicLong> TACTICS=new ConcurrentHashMap<>();
 static void tactic(String name){TACTICS.computeIfAbsent(name,n->new AtomicLong()).incrementAndGet();}
 static Map<String,Long> tactics(){var out=new TreeMap<String,Long>();TACTICS.forEach((k,v)->out.put(k,v.get()));return out;}

 static void engaged(boolean defence){(defence?DEFENDED:STARTED).incrementAndGet();}
 /** Called by PvpService once the native kill credit is decided. Neither side a bot: nothing to count. */
 public static void killed(Player winner,Player victim) {
  if(winner==null || victim==null || !winner.isPlayerBot() && !victim.isPlayerBot())return;
  if(winner.isPlayerBot())(victim.isPlayerBot()?KILLS_BOTS:KILLS_HUMANS).incrementAndGet();
  if(victim.isPlayerBot())(winner.isPlayerBot()?DEATHS_BOTS:DEATHS_HUMANS).incrementAndGet();
  KILLS_BY_MAP.computeIfAbsent(victim.getWorldId(),m->new AtomicLong()).incrementAndGet();
  if(winner.isWorldBot() && ThreadLocalRandom.current().nextInt(100)<PlayerBotConfig.PVP_VICTORY_EMOTE)victory(winner);
 }
 /** A real player's habit: a native victory emote once the fight around the winner has calmed down. */
 private static void victory(Player bot) {
  ThreadPoolManager.getInstance().schedule(()->{
   if(!bot.isSpawned() || bot.isDead() || bot.getController().isInCombat() || bot.isCasting())return;
   PacketSendUtility.broadcastToSightedPlayers(bot,new SM_EMOTION(bot,EmotionType.EMOTE,EmotionId.VICTORY.id(),bot.getX(),bot.getY(),bot.getZ(),bot.getHeading(),0),true);
  },1500);
 }
 static String stats() {
  var maps=new TreeMap<Integer,Long>();KILLS_BY_MAP.forEach((m,c)->maps.put(m,c.get()));
  return "fights started="+STARTED.get()+", defended="+DEFENDED.get()+", bot kills of humans="+KILLS_HUMANS.get()+" of bots="+KILLS_BOTS.get()
   +", bot deaths to humans="+DEATHS_HUMANS.get()+" to bots="+DEATHS_BOTS.get()+", kills by map="+maps
   +", shared-map enemies invited="+SHARED_INVITED.get()+" placed="+SHARED_PLACED.get()+", rift invaders invited="+RIFT_INVITED.get()+" staged="+RIFT_STAGED.get()+" crossed="+RIFT_CROSSED.get()
   +", seeks="+SEEKS.get()+" reached="+SEEK_ARRIVED.get()+" (defending their homeland "+DEFENDS.get()+", in battlegrounds and arenas "+BATTLEGROUND_SEEKS.get()+"), rift alerts="+RIFT_ALERTS.get()+", tactics="+tactics();
 }
 private PlayerBotPvpStats(){}
}
