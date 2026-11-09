package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcTemplateType;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;

/**
 * PvP-1: a PVP-leaning bot on a shared map, or any bot on a rift visit, walks toward the nearest enemy-faction
 * player standing in a PvP zone. It travels to a fixed point (refreshed only when the target moves away), because
 * navigation.approach re-plans whenever its target moves. Within notice range the existing PlayerBotPvp.choose and
 * native PvP rules decide whether a fight starts. Targets beside native guards are left alone.
 */
final class PlayerBotPvpSeek {
 static final long GIVE_UP_MS=120000,REFRESH_MS=15000,REST_MS=30000,VISITED_MS=120000;
 static final float MOVED=40,GUARD_RADIUS=60;
 /** target -> (seeker -> since). Claims expire after GIVE_UP_MS, so a bot unloaded mid-trip never blocks a target. */
 private static final Map<Integer,Map<Integer,Long>> SEEKERS=new ConcurrentHashMap<>();
 private static final Map<String,List<float[]>> GUARDS=new ConcurrentHashMap<>();
 private int target;
 private float x,y,z;
 private long started,refreshed,restUntil;
 private final Map<Integer,Long> visited=new HashMap<>();

 /** Where seeking applies: shared maps and (PvP-2) their own homeland for PVP-leaning bots, where any enemy came
  * through a rift; anywhere during a rift visit. A human must be on the map. */
 static boolean wanted(boolean autonomous,boolean pvpLeaning,boolean sharedMap,boolean homeland,boolean riftVisit,boolean humanOnMap,boolean inInstance,boolean inTeam) {
  return autonomous && humanOnMap && !inInstance && !inTeam && (pvpLeaning && (sharedMap || homeland) || riftVisit);
 }
 /** PvP-3/4: a battleground or arena map (every Find Group instance type is one). Core-only: handlers are runtime scripts. */
 static boolean battleground(Player bot) {
  return bot.getPosition()!=null && bot.isInInstance() && com.aionemu.gameserver.model.autogroup.AutoGroupType.getAutoGroupByWorld(bot.getLevel(),bot.getWorldId())!=null;
 }
 /** Inside a battleground everyone fights whoever the game says is an enemy (factions, or everyone in a free-for-all arena). */
 static boolean arenaCandidate(Player bot,Player p,int seekers) {
  return p!=bot && p.isSpawned() && !p.isDead() && bot.isEnemy(p) && seekers<PlayerBotConfig.PVP_ATTACKERS_PER_TARGET;
 }
 static boolean candidate(Race seeker,int level,Player p,int seekers) {
  return p.isSpawned() && !p.isDead() && !p.isFlying() && p.getRace()!=seeker && (p.getRace()==Race.ELYOS || p.getRace()==Race.ASMODIANS)
   && p.isInsidePvPZone() && Math.abs(p.getLevel()-level)<=PlayerBotConfig.PVP_LEVEL_DIFFERENCE && seekers<PlayerBotConfig.PVP_ATTACKERS_PER_TARGET;
 }
 /** Native guard spawns hostile to this race on this map, from spawn data; cached per race and map. */
 static boolean guarded(Race race,int map,float x,float y,float z) {
  var guards=GUARDS.computeIfAbsent(race+":"+map,key->{
   TribeClass tribe=race==Race.ELYOS?TribeClass.PC:TribeClass.PC_DARK;var points=new ArrayList<float[]>();
   for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map)) {
    var n=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
    if(n==null || n.getNpcTemplateType()!=NpcTemplateType.GUARD && n.getNpcTemplateType()!=NpcTemplateType.ABYSS_GUARD)continue;
    if(!DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),tribe) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),tribe))continue;
    for(var s:group.getSpawnTemplates())points.add(new float[]{s.getX(),s.getY(),s.getZ()});
   }
   return List.copyOf(points);
  });
  for(var g:guards)if(Math.pow(g[0]-x,2)+Math.pow(g[1]-y,2)+Math.pow(g[2]-z,2)<=GUARD_RADIUS*GUARD_RADIUS)return true;
  return false;
 }
 static int seekers(Map<Integer,Long> claims,long now){if(claims==null)return 0;claims.values().removeIf(since->now-since>=GIVE_UP_MS);return claims.size();}
 private int self;
 boolean seeking(){return target!=0;}
 void release(){if(target!=0){var claims=SEEKERS.get(target);if(claims!=null){claims.remove(self);if(claims.isEmpty())SEEKERS.remove(target,claims);}}target=0;}
 private boolean arena,home;
 private Player pick(Player bot,long now) {
  visited.values().removeIf(at->now-at>=VISITED_MS);
  Player best=null;double nearest=PlayerBotConfig.PVP_SEEK_DISTANCE;
  for(var p:World.getInstance().getAllPlayers()) {
   // In an arena the same opponent may be the only one, so nobody is set aside after a meeting.
   if(p.getWorldId()!=bot.getWorldId() || p.getInstanceId()!=bot.getInstanceId() || !arena && visited.containsKey(p.getObjectId()))continue;
   int claims=seekers(SEEKERS.get(p.getObjectId()),now);
   if(arena?!arenaCandidate(bot,p,claims):!candidate(bot.getRace(),bot.getLevel(),p,claims))continue;
   double d=PositionUtil.getDistance(bot,p);
   // Guards and objectives are part of a battleground, so nobody is avoided there.
   if(d<nearest && (arena || !guarded(bot.getRace(),p.getWorldId(),p.getX(),p.getY(),p.getZ()))){nearest=d;best=p;}
  }
  return best;
 }
 boolean tick(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();
  // Ordinary bots leave here, before any map data is read (a battleground check only for bots in an instance).
  boolean pvpLeaning=PlayerBotLeanings.pvp(bot),visit=session.riftVisiting(now);
  arena=session.autonomous() && PlayerBotConfig.PVP_BATTLEGROUNDS && battleground(bot) && PlayerBotWorldActivity.occupied(bot);home=false;
  if(arena){if(PlayerBotPvp.hp(bot)<40){release();return false;}return hunt(session,navigation,bot,now);}
  if(!pvpLeaning && !visit){release();return false;}
  Race land=pvpLeaning?PlayerBotTerritory.homeland(bot.getWorldId()):Race.NONE;
  boolean shared=pvpLeaning && land==Race.NONE;home=pvpLeaning && land==bot.getRace();
  if(!wanted(session.autonomous(),pvpLeaning,shared,home,visit,PlayerBotWorldActivity.occupied(bot),bot.isInInstance(),bot.getCurrentTeam()!=null)
   || PlayerBotPvp.hp(bot)<70){release();return false;}
  return hunt(session,navigation,bot,now);
 }
 private boolean hunt(PlayerBotSession session,PlayerBotNavigation navigation,Player bot,long now) {
  Player chosen=target==0?null:World.getInstance().getPlayer(target);
  if(chosen!=null && (!chosen.isSpawned() || chosen.isDead() || chosen.getWorldId()!=bot.getWorldId() || chosen.getInstanceId()!=bot.getInstanceId()
   || PositionUtil.getDistance(bot,chosen)>PlayerBotConfig.PVP_SEEK_DISTANCE*1.5))chosen=null;
  if(target!=0 && (chosen==null || now-started>GIVE_UP_MS)){if(chosen!=null)visited.put(target,now);release();navigation.stop();restUntil=now+REST_MS;return false;}
  if(target==0) {
   if(now<restUntil)return false;
   chosen=pick(bot,now);if(chosen==null){restUntil=now+REST_MS;return false;}
   target=chosen.getObjectId();self=bot.getObjectId();SEEKERS.computeIfAbsent(target,t->new ConcurrentHashMap<>()).put(self,now);
   started=refreshed=now;x=chosen.getX();y=chosen.getY();z=chosen.getZ();PlayerBotPvpStats.SEEKS.incrementAndGet();
   if(arena)PlayerBotPvpStats.BATTLEGROUND_SEEKS.incrementAndGet();
   if(home)PlayerBotPvpStats.DEFENDS.incrementAndGet();
   PlayerBotWorldActivities.close(bot);PlayerBotQuestRoutes.close(bot);
  }
  // Close enough for the normal engagement rules to take over.
  if(PositionUtil.isInRange(bot,chosen,PlayerBotConfig.PVP_NOTICE_DISTANCE)){PlayerBotPvpStats.SEEK_ARRIVED.incrementAndGet();visited.put(target,now);release();navigation.stop();return false;}
  if(now-refreshed>=REFRESH_MS && PositionUtil.getDistance(chosen,x,y,z)>MOVED){x=chosen.getX();y=chosen.getY();z=chosen.getZ();refreshed=now;}
  if(!navigation.move(x,y,z) && navigation.routeFailed()){visited.put(target,now);release();restUntil=now+REST_MS;return false;}
  return true;
 }
}
