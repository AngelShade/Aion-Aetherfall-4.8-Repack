package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.RVController;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;
import com.aionemu.gameserver.services.rift.RiftManager;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;

/**
 * PvP-1, owner decision 8 Nov ("Shared maps and rifts"): enemies where a human can meet them.
 * Shared maps: up to pvp.shared_enemies_per_human enemy PVP bots near each human, arriving unseen at their own
 * faction's camps 300 m or more away, then walking in (PlayerBotPvpSeek). Rifts: up to pvp.rifts.invaders_per_rift
 * enemy PVP bots per open rift into a human's homeland, staged beside the rift on their own empty homeland and
 * crossing through the native rift. Only sleeping bots are invited, so no human sees them leave; each bot carries
 * out its invite in its own dormant turn, under its own lock, through the normal placement guards.
 */
final class PlayerBotPvpPresence {
 record Invite(int map,float x,float y,float z,Npc rift,long until) {}
 static final long PLAN_MS=5000,INVITE_MS=60000,REST_MS=20*60000L;
 static final float MIN_LANDING=300;
 private static final Map<Integer,Invite> INVITES=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> REST=new ConcurrentHashMap<>();
 /** rift -> invaders staged; dropped once the rift despawns. */
 private static final Map<Npc,Integer> SENT=new ConcurrentHashMap<>();
 /** race:map -> whether that race has native camps there (camps are an immutable startup cache). */
 private static final Map<String,Boolean> SHARED=new ConcurrentHashMap<>();
 /** Candidates tried per human or rift each pass; bounds the planner's work on the maintenance thread. */
 static final int TRIES=32;
 private static volatile long nextPlan;

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED;}
 static Race enemy(Race race){return race==Race.ELYOS?Race.ASMODIANS:race==Race.ASMODIANS?Race.ELYOS:Race.NONE;}
 /** A shared map is one with no faction homeland where the enemy has native camps. */
 static boolean shared(int map,Race enemy){
  return PlayerBotTerritory.homeland(map)==Race.NONE
   && SHARED.computeIfAbsent(enemy+":"+map,k->PlayerBotWorldPlacement.locations().stream().anyMatch(p->p.map()==map && p.race()==enemy));
 }
 /** Nearest suitable camp of the bot's own faction on the map, at least MIN_LANDING and at most the seek distance from the human. */
 static PlayerBotWorldPlacement.Location landing(List<PlayerBotWorldPlacement.Location> camps,Race race,int level,int map,float hx,float hy,float hz) {
  PlayerBotWorldPlacement.Location best=null;double nearest=Double.MAX_VALUE;
  for(var p:camps) {
   if(p.map()!=map || p.city() || !PlayerBotWorldPlacement.suitable(p,race,level,Set.of()))continue;
   double d=Math.sqrt(Math.pow(p.x()-hx,2)+Math.pow(p.y()-hy,2)+Math.pow(p.z()-hz,2));
   if(d>=MIN_LANDING && d<=PlayerBotConfig.PVP_SEEK_DISTANCE && d<nearest){nearest=d;best=p;}
  }
  return best;
 }
 static boolean levelFits(int bot,int human,int range){return Math.abs(bot-human)<=range;}
 private static boolean invitable(PlayerBotSession s,long now) {
  var bot=s.bot();int id=bot.getObjectId();
  return s.autonomous() && !s.closing() && s.dormant() && PlayerBotPlayerPresence.global(bot) && bot.isSpawned() && !bot.isDead()
   && PlayerBotLeanings.pvp(bot) && !INVITES.containsKey(id) && now>=REST.getOrDefault(id,0L) && !PlayerBotRoadDuty.assigned(bot)
   && !PlayerBotFindGroup.committed(bot) && !PlayerBotWorldAscension.pending(bot) && bot.getCurrentTeam()==null;
 }
 /** Maintenance thread, at most every PLAN_MS: one invite per human area and per rift each pass, so arrivals are spread out. */
 static void plan(Collection<PlayerBotSession> sessions,long now) {
  if(!enabled() || now<nextPlan)return;nextPlan=now+PLAN_MS;
  INVITES.values().removeIf(i->now>=i.until());
  var players=World.getInstance().getAllPlayers();
  SENT.keySet().removeIf(rift->!rift.isSpawned());
  for(var human:players) {
   if(human.isPlayerBot() || !PlayerBotPlayerZones.eligible(false,human.isOnline(),human.isSpawned(),human.isStaff() && human.isInVisualState(CreatureVisualState.HIDE20),
    human.getInstanceId(),DataManager.WORLD_MAPS_DATA.getTemplate(human.getWorldId())))continue;
   Race foe=enemy(human.getRace());if(foe==Race.NONE)continue;
   if(PlayerBotConfig.PVP_SHARED_ENEMIES>0 && shared(human.getWorldId(),foe))sharedMap(sessions,players,human,foe,now);
   if(PlayerBotConfig.PVP_RIFTS && PlayerBotConfig.PVP_RIFT_INVADERS>0 && PlayerBotTerritory.homeland(human.getWorldId())==human.getRace())rifts(sessions,human,foe,now);
  }
 }
 private static void sharedMap(Collection<PlayerBotSession> sessions,Collection<Player> players,Player human,Race foe,long now) {
  int map=human.getWorldId();float radius=PlayerBotConfig.PVP_SEEK_DISTANCE;
  long near=players.stream().filter(p->p.isWorldBot() && p.getRace()==foe && p.isSpawned() && !p.isDead() && p.getWorldId()==map && p.getInstanceId()==human.getInstanceId()
   && PositionUtil.isInRange(human,p,radius)).count();
  long coming=INVITES.values().stream().filter(i->i.rift()==null && i.map()==map && PositionUtil.getDistance(human,i.x(),i.y(),i.z())<=radius).count();
  if(near+coming>=PlayerBotConfig.PVP_SHARED_ENEMIES)return;
  // The enemy's camps on this map, once; usually none lie near a human standing deep on their own side.
  var camps=PlayerBotWorldPlacement.locations().stream().filter(p->p.map()==map && p.race()==foe && !p.city()
   && PositionUtil.getDistance(human,p.x(),p.y(),p.z())<=radius).toList();
  if(camps.isEmpty())return;
  int tries=0;
  for(var s:sessions) {
   var bot=s.bot();
   if(bot.getRace()!=foe || !levelFits(bot.getLevel(),human.getLevel(),PlayerBotConfig.PVP_SHARED_LEVEL_RANGE) || !invitable(s,now))continue;
   var camp=landing(camps,foe,bot.getLevel(),map,human.getX(),human.getY(),human.getZ());
   if(camp==null){if(++tries>=TRIES)return;continue;}
   INVITES.put(bot.getObjectId(),new Invite(map,camp.x(),camp.y(),camp.z(),null,now+INVITE_MS));PlayerBotPvpStats.SHARED_INVITED.incrementAndGet();return;
  }
 }
 private static void rifts(Collection<PlayerBotSession> sessions,Player human,Race foe,long now) {
  int home=human.getWorldId(),source=PlayerBotTerritory.counterpart(home);
  // A human on the source map would see the staging; those bots are awake and take the ordinary rift trips.
  if(source==0 || PlayerBotWorldActivity.occupiedMap(source))return;
  for(var rift:RiftManager.getSpawnedRifts(source)) {
   if(!(rift.getController() instanceof RVController rv) || !rv.isMaster() || rv.isVortex() || rv.getSlave()==null || rv.getSlave().getWorldId()!=home || !rift.isSpawned())continue;
   long staged=INVITES.values().stream().filter(i->i.rift()==rift).count();
   if(SENT.getOrDefault(rift,0)+staged>=PlayerBotConfig.PVP_RIFT_INVADERS)continue;
   int tries=0;
   for(var s:sessions) {
    var bot=s.bot();
    if(bot.getRace()!=foe || bot.getWorldId()!=source || bot.getInstanceId()!=rift.getInstanceId()
     || !levelFits(bot.getLevel(),human.getLevel(),PlayerBotConfig.PVP_LEVEL_DIFFERENCE) || !invitable(s,now))continue;
    if(!rv.canPlayerBotEnter(bot)){if(++tries>=TRIES)break;continue;}
    INVITES.put(bot.getObjectId(),new Invite(source,rift.getX(),rift.getY(),rift.getZ(),rift,now+INVITE_MS));PlayerBotPvpStats.RIFT_INVITED.incrementAndGet();break;
   }
  }
 }
 static boolean due(PlayerBotSession session){return enabled() && session.dormant() && INVITES.containsKey(session.bot().getObjectId());}
 /** The invited bot's own dormant turn: one unseen move, then (for a rift) the rift is handed to its normal walk. */
 static void turn(PlayerBotSession session,long now) {
  var bot=session.bot();var invite=INVITES.remove(bot.getObjectId());
  if(invite==null || now>=invite.until() || !session.dormant() || !PlayerBotLeanings.pvp(bot))return;
  REST.put(bot.getObjectId(),now+REST_MS);
  if(invite.rift()==null) {
   var landing=new PlayerBotWorldPlacement.Location(bot.getRace(),invite.map(),invite.x(),invite.y(),invite.z(),1,65,false,false);
   session.standUp();
   if(PlayerBotWorldPlacement.placeAt(session,landing)){PlayerBotSupplies.provision(bot);PlayerBotPvpStats.SHARED_PLACED.incrementAndGet();}
   return;
  }
  var rift=invite.rift();
  if(!rift.isSpawned() || !(rift.getController() instanceof RVController rv) || !rv.canPlayerBotEnter(bot))return;
  if(!PositionUtil.isInRange(bot,rift,4) && !PlayerBotOffscreenTurnIns.moveBeside(session,rift))return;
  if(session.inviteRift(rift,now)){SENT.merge(rift,1,Integer::sum);PlayerBotPvpStats.RIFT_STAGED.incrementAndGet();}
 }
 static void closed(int bot){INVITES.remove(bot);REST.remove(bot);}
 private PlayerBotPvpPresence(){}
}
