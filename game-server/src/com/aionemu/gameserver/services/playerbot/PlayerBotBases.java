package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.base.BaseOccupier;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.spawns.basespawns.BaseSpawnTemplate;
import com.aionemu.gameserver.services.BaseService;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * PvP-5 bases, a hook of its own (pvp.bases.enable): a PVP-leaning world bot fights the NPCs of an enemy-held base
 * within reach (killing its boss captures it natively) and now and then walks to the nearest base its faction does not
 * hold. Ordinary bots never pick a fight with base guards. Nothing here is shared with sieges or battlegrounds.
 */
final class PlayerBotBases {
 static final float RANGE=45,ARRIVED=50,TRAVEL_LIMIT=1500;
 static final long RETRY_MS=60000,GIVE_UP_MS=240000;
 static final AtomicLong TARGETED=new AtomicLong(),TRIPS_STARTED=new AtomicLong(),ARRIVALS=new AtomicLong();
 private record Trip(int base,float x,float y,float z,long started) {}
 private static final Set<Integer> SEEN=ConcurrentHashMap.newKeySet();
 private static final Map<Integer,List<float[]>> SPOTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Trip> TRIPS=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> REST=new ConcurrentHashMap<>();

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BASES;}
 static boolean objective(boolean enabled,boolean pvpLeaning,boolean baseNpc,boolean active,boolean enemy,boolean alive,boolean inReach) {
  return enabled && pvpLeaning && baseNpc && active && enemy && alive && inReach;
 }
 /** Held by anyone but the bot's own faction (the other faction, Balaur or a neutral occupier). */
 static boolean contested(BaseOccupier holder,BaseOccupier own){return holder!=own;}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party) {
  if(!enabled() || !(npc.getSpawn() instanceof BaseSpawnTemplate base))return false;
  boolean yes=objective(true,PlayerBotLeanings.pvp(bot),true,BaseService.getInstance().isActive(base.getId()),bot.isEnemy(npc),!npc.isDead(),
   npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(bot,npc,RANGE) && PlayerBotTargetEligibility.visible(bot,npc));
  if(yes && SEEN.add(npc.getObjectId())){TARGETED.incrementAndGet();if(SEEN.size()>20000)SEEN.clear();}
  return yes;
 }
 static List<float[]> spots(int base) {
  return SPOTS.computeIfAbsent(base,id->{
   var points=new ArrayList<float[]>();
   for(var group:DataManager.SPAWNS_DATA.getBaseSpawnsByLocId(id))for(var s:group.getSpawnTemplates())if(!s.isAerialSpawn())points.add(new float[]{s.getX(),s.getY(),s.getZ()});
   return List.copyOf(points);
  });
 }
 static float[] nearest(List<float[]> spots,float x,float y,float z,float limit) {
  float[] best=null;double closest=limit;
  for(var p:spots){double d=Math.sqrt(Math.pow(p[0]-x,2)+Math.pow(p[1]-y,2)+Math.pow(p[2]-z,2));if(d<=closest){closest=d;best=p;}}
  return best;
 }
 static long restMs(){return Math.max(1,PlayerBotConfig.PVP_BASE_TRIP_MINUTES)*60000L;}
 /** Now and then (pvp.bases.trip_minutes per bot) a PVP-leaning bot walks to the nearest base its faction does not hold. */
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();int id=bot.getObjectId();
  if(!enabled() || !session.autonomous() || !PlayerBotLeanings.pvp(bot)){TRIPS.remove(id);return false;}
  if(bot.isInInstance() || bot.getCurrentTeam()!=null || !PlayerBotWorldActivity.occupied(bot)){TRIPS.remove(id);return false;}
  var trip=TRIPS.get(id);
  if(trip!=null && now-trip.started()>GIVE_UP_MS){TRIPS.remove(id);navigation.stop();return false;}
  if(trip==null) {
   if(now<REST.getOrDefault(id,0L))return false;
   REST.put(id,now+restMs());
   var own=BaseOccupier.findBy(bot.getRace());float[] best=null;int bestBase=0;double closest=TRAVEL_LIMIT;
   for(var loc:BaseService.getInstance().getBaseLocations()) {
    if(loc.getWorldId()!=bot.getWorldId() || !BaseService.getInstance().isActive(loc.getId()) || !contested(loc.getOccupier(),own))continue;
    var spot=nearest(spots(loc.getId()),bot.getX(),bot.getY(),bot.getZ(),TRAVEL_LIMIT);
    if(spot==null)continue;
    double d=PositionUtil.getDistance(bot,spot[0],spot[1],spot[2]);if(d<closest){closest=d;best=spot;bestBase=loc.getId();}
   }
   if(best==null)return false;
   trip=new Trip(bestBase,best[0],best[1],best[2],now);TRIPS.put(id,trip);TRIPS_STARTED.incrementAndGet();
   PlayerBotWorldActivities.close(bot);PlayerBotQuestRoutes.close(bot);
  }
  if(PositionUtil.getDistance(bot,trip.x(),trip.y(),trip.z())<=ARRIVED){TRIPS.remove(id);ARRIVALS.incrementAndGet();return false;}
  if(!navigation.move(trip.x(),trip.y(),trip.z()) && navigation.routeFailed()){TRIPS.remove(id);return false;}
  return true;
 }
 static void forget(int bot){TRIPS.remove(bot);REST.remove(bot);}
 static String stats(){return "enabled="+enabled()+", base targets="+TARGETED.get()+", trips to enemy bases="+TRIPS_STARTED.get()+" arrived="+ARRIVALS.get();}
 private PlayerBotBases(){}
}
