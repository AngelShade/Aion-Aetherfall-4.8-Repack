package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.siege.SiegeNpc;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * PvP-5 sieges, a hook of its own (pvp.sieges.enable): while a siege runs, enemy siege NPCs within reach (gates,
 * guardians, the lord, siege weapons) are fair targets for world bots, and PVP-leaning bots on that map head for the
 * fortress, attackers and defenders alike. Nothing here is shared with bases or battlegrounds.
 */
final class PlayerBotSieges {
 static final float RANGE=45,ARRIVED=60,TRAVEL_LIMIT=3000;
 static final long RETRY_MS=60000,GIVE_UP_MS=300000;
 static final AtomicLong TARGETED=new AtomicLong(),TRIPS_STARTED=new AtomicLong(),ARRIVALS=new AtomicLong(),AGENT_TRIPS=new AtomicLong();
 /** Siege targets by kind (fortress, artifact, outpost, agent fight, Balaur assault), for the report. */
 private static final Map<String,AtomicLong> KINDS=new ConcurrentHashMap<>();
 static String kind(boolean balaur,Object location) {
  if(balaur)return "Balaur assault";
  return location instanceof com.aionemu.gameserver.model.siege.AgentLocation?"agent fight":location instanceof com.aionemu.gameserver.model.siege.ArtifactLocation?"artifact"
   :location instanceof com.aionemu.gameserver.model.siege.OutpostLocation?"outpost":"fortress";
 }
 /** Every bot joins an Agent fight on its map (agent_all_bots); other sieges draw PvP-leaning bots only. */
 static boolean traveller(boolean pvpLeaning,boolean agentFight,boolean agentAll){return pvpLeaning || agentFight && agentAll;}
 private record Trip(int siege,float x,float y,float z,long started) {}
 private static final Set<Integer> SEEN=ConcurrentHashMap.newKeySet();
 private static final Map<Integer,List<float[]>> SPOTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Trip> TRIPS=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> REST=new ConcurrentHashMap<>();

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_SIEGES;}
 static boolean objective(boolean enabled,boolean siegeNpc,boolean inProgress,boolean enemy,boolean alive,boolean inReach) {
  return enabled && siegeNpc && inProgress && enemy && alive && inReach;
 }
 /** An enemy siege NPC of a running siege, within reach and in sight: a target like any attacker. */
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party) {
  if(!enabled() || !(npc instanceof SiegeNpc siege))return false;
  boolean yes=objective(true,true,SiegeService.getInstance().isSiegeInProgress(siege.getSiegeId()),bot.isEnemy(npc),!npc.isDead(),
   npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(bot,npc,RANGE) && PlayerBotTargetEligibility.visible(bot,npc));
  if(yes && SEEN.add(npc.getObjectId())){
   TARGETED.incrementAndGet();if(SEEN.size()>20000)SEEN.clear();
   KINDS.computeIfAbsent(kind(npc.getRace()==com.aionemu.gameserver.model.Race.DRAKAN,SiegeService.getInstance().getSiegeLocation(siege.getSiegeId())),k->new AtomicLong()).incrementAndGet();
  }
  return yes;
 }
 /** Ground spawn points of a siege location from static data: where its gates, guards and lord stand. */
 static List<float[]> spots(int siege) {
  return SPOTS.computeIfAbsent(siege,id->{
   var points=new ArrayList<float[]>();
   for(var group:DataManager.SPAWNS_DATA.getSiegeSpawnsByLocId(id))for(var s:group.getSpawnTemplates())if(!s.isAerialSpawn())points.add(new float[]{s.getX(),s.getY(),s.getZ()});
   return List.copyOf(points);
  });
 }
 static float[] nearest(List<float[]> spots,float x,float y,float z,float limit) {
  float[] best=null;double closest=limit;
  for(var p:spots){double d=Math.sqrt(Math.pow(p[0]-x,2)+Math.pow(p[1]-y,2)+Math.pow(p[2]-z,2));if(d<=closest){closest=d;best=p;}}
  return best;
 }
 /** PVP-leaning bots walk to the nearest part of a running siege on their map; at the walls the normal fight takes over. */
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();int id=bot.getObjectId();
  var agent=SiegeService.getInstance().getAgentLocation();
  boolean agentFight=agent!=null && agent.getWorldId()==bot.getWorldId() && SiegeService.getInstance().isSiegeInProgress(agent.getLocationId());
  boolean pvpLeaning=PlayerBotLeanings.pvp(bot);
  if(!enabled() || !session.autonomous() || !traveller(pvpLeaning,agentFight,com.aionemu.gameserver.configs.main.PlayerBotConfig.PVP_SIEGE_AGENT_ALL)){TRIPS.remove(id);return false;}
  if(bot.isInInstance() || bot.getCurrentTeam()!=null || !PlayerBotWorldActivity.occupied(bot)){TRIPS.remove(id);return false;}
  var trip=TRIPS.get(id);
  if(trip!=null && (!SiegeService.getInstance().isSiegeInProgress(trip.siege()) || now-trip.started()>GIVE_UP_MS)){TRIPS.remove(id);REST.put(id,now+RETRY_MS);navigation.stop();return false;}
  if(trip==null) {
   if(now<REST.getOrDefault(id,0L))return false;
   REST.put(id,now+RETRY_MS);
   for(var loc:SiegeService.getInstance().getSiegeLocations(bot.getWorldId()).values()) {
    if(!SiegeService.getInstance().isSiegeInProgress(loc.getLocationId()))continue;
    // Bots that are not PvP-leaning only answer the Agent fight.
    if(!pvpLeaning && loc!=agent)continue;
    var spot=nearest(spots(loc.getLocationId()),bot.getX(),bot.getY(),bot.getZ(),TRAVEL_LIMIT);
    if(spot!=null){trip=new Trip(loc.getLocationId(),spot[0],spot[1],spot[2],now);break;}
   }
   if(trip==null)return false;
   TRIPS.put(id,trip);TRIPS_STARTED.incrementAndGet();if(agent!=null && trip.siege()==agent.getLocationId())AGENT_TRIPS.incrementAndGet();PlayerBotWorldActivities.close(bot);PlayerBotQuestRoutes.close(bot);
  }
  if(PositionUtil.getDistance(bot,trip.x(),trip.y(),trip.z())<=ARRIVED){TRIPS.remove(id);ARRIVALS.incrementAndGet();return false;}
  if(!navigation.move(trip.x(),trip.y(),trip.z()) && navigation.routeFailed()){TRIPS.remove(id);REST.put(id,now+RETRY_MS*5);return false;}
  return true;
 }
 static void forget(int bot){TRIPS.remove(bot);REST.remove(bot);}
 static String stats(){return "enabled="+enabled()+", siege targets="+TARGETED.get()+" "+KINDS.entrySet().stream().map(e->e.getKey()+"="+e.getValue().get()).sorted().toList()
  +", trips to running sieges="+TRIPS_STARTED.get()+" (agent fights "+AGENT_TRIPS.get()+") arrived="+ARRIVALS.get();}
 private PlayerBotSieges(){}
}
