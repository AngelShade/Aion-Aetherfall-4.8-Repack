/* RandomPlayerbotMgr::RandomTeleportForLevel / RandomTeleport and TravelMgr::PrepareDestinationCache,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.random.RandomGenerator;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.world.geo.GeoService;

/** Background population placement, separate from ordinary paid-route activity travel. */
final class PlayerBotWorldPlacement {
 record Location(Race race,int map,float x,float y,float z,int minimum,int maximum,boolean city,boolean hub) {
  Location {if(race!=Race.ELYOS && race!=Race.ASMODIANS || map<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
    || minimum<1 || maximum<minimum || maximum>65)throw new IllegalArgumentException("Invalid population location");}
 }
 record Spawn(int npc,float x,float y,float z,int level,boolean hub,boolean city) {}
 private static List<Location> cache;
 private static final Map<String,Map<Integer,List<Location>>> FOCUS_CHOICES=new java.util.concurrent.ConcurrentHashMap<>();
 private static Map<Integer,List<Location>> focusCandidates(Race race,int level) {
  return FOCUS_CHOICES.computeIfAbsent(race+":"+level,k->{
   var grouped=locations().stream().filter(p->suitable(p,race,level,Set.of()))
    .collect(java.util.stream.Collectors.groupingBy(Location::map));
   grouped.replaceAll((map,points)->{
    var fields=points.stream().filter(p->!p.hub() && !p.city()).toList();
    boolean capital=map==WorldMapType.SANCTUM.getId() || map==WorldMapType.PANDAEMONIUM.getId();
    return fields.isEmpty() && capital?List.copyOf(points):fields;
   });
   grouped.values().removeIf(List::isEmpty);return Map.copyOf(grouped);
  });
 }
 static List<Location> focusChoices(Race race,int level,PlayerBotPopulationFocus.Snapshot focus,RandomGenerator rng) {
  // Cache only immutable native faction/level bands. Live maps/players/occupancy are never cached here.
  var all=focusCandidates(race,level);
  var choices=new ArrayList<Location>();
  for(int map:focus.maps()) {
   if(!PlayerBotConfig.POPULATION_MAPS.isEmpty() && !PlayerBotConfig.POPULATION_MAPS.contains(map))continue;
   var local=all.getOrDefault(map,List.of());
   var fields=local.stream().filter(p->!p.hub() && !p.city()).toList();
   // A player in a native capital needs service/RPG residents rather than an empty field tier.
   choices.addAll(fields.isEmpty()?local:fields);
  }
  Collections.shuffle(choices,new Random(rng.nextLong()));return List.copyOf(choices);
 }
 static boolean focusCompatible(Race race,int level,PlayerBotPopulationFocus.Snapshot focus) {
  if(!focus.active())return true;
  var candidates=focusCandidates(race,level);
  // Admission only needs compatibility, not a shuffled allocation of thousands of camps per identity.
  return focus.maps().stream().anyMatch(map->candidates.containsKey(map)
   && (PlayerBotConfig.POPULATION_MAPS.isEmpty() || PlayerBotConfig.POPULATION_MAPS.contains(map)));
 }
 static boolean focusCompatibleOnMap(Race race,int level,int map) {
  return focusCandidates(race,level).containsKey(map)
   && (PlayerBotConfig.POPULATION_MAPS.isEmpty() || PlayerBotConfig.POPULATION_MAPS.contains(map));
 }
 static boolean mapEligible(WorldMapTemplate map,Race race,Set<Integer> enabled) {
  return map!=null && !map.isInstance() && !map.isPrison() && map.getWorldType()!=WorldType.NONE
   && map.getWorldType()!=WorldType.PRISON && map.getWorldType()!=WorldType.PANESTERRA
   && (map.getWorldType()!=WorldType.ELYSEA || race==Race.ELYOS)
   && (map.getWorldType()!=WorldType.ASMODAE || race==Race.ASMODIANS)
   && (enabled.isEmpty() || enabled.contains(map.getMapId()));
 }
 static boolean suitable(Location p,Race race,int level,Set<Integer> enabled) {
  return p.race()==race && level>=p.minimum() && level<=p.maximum() && (enabled.isEmpty() || enabled.contains(p.map()));
 }
 static List<Location> select(List<Location> locations,Race race,int level,Set<Integer> enabled,
                             Set<Integer> playerMaps,boolean concentrate,int cityPercent,RandomGenerator random) {
  var all=locations.stream().filter(p->suitable(p,race,level,enabled)).toList();
  var cities=all.stream().filter(Location::city).toList();
  var hubs=all.stream().filter(p->p.hub() && !p.city()).toList();
  // Field intent must not resolve to an RPG service hub or an arena lobby.
  // Preserve native starter alternatives before low-level ground validation.
  var fields=all.stream().filter(p->!p.city() && !p.hub()).toList();
  var ordinary=fields.isEmpty()?hubs:level<=5?java.util.stream.Stream.concat(fields.stream(),hubs.stream()).toList():fields;
  var choices=level>=10 && random.nextInt(100)<cityPercent && !cities.isEmpty()?cities:ordinary;
  if(concentrate && !playerMaps.isEmpty()) {
   var local=choices.stream().filter(p->playerMaps.contains(p.map())).toList();if(!local.isEmpty())choices=local;
  }
  var shuffled=new ArrayList<>(choices);Collections.shuffle(shuffled,new Random(random.nextLong()));return List.copyOf(shuffled);
 }
 static synchronized List<Location> locations() {
  if(cache!=null)return cache;
  Set<Integer> ids=new TreeSet<>();DataManager.SPAWNS_DATA.addAllNpcIdsToSet(ids);var result=new ArrayList<Location>();
  for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
   TribeClass tribe=race==Race.ELYOS?TribeClass.PC:TribeClass.PC_DARK;
   for(var map:DataManager.WORLD_MAPS_DATA) {
    if(!mapEligible(map,race,Set.of()))continue;
    var mobs=new ArrayList<Spawn>();var hubs=new ArrayList<Spawn>();
    for(int id:ids) {
     var n=DataManager.NPC_DATA.getNpcTemplate(id);if(n==null)continue;
     boolean hostile=DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),tribe) || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),tribe);
     boolean mob=hostile && PlayerBotHunting.template(n);
     boolean friendly=!hostile && (n.getRace()==race || n.getRace()==Race.PC_ALL || DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(n.getTribe(),tribe));
     boolean bank=friendly && (n.supportsAction(DialogAction.OPEN_PERSONAL_WAREHOUSE) || n.supportsAction(DialogAction.RETRIEVE_CHAR_WAREHOUSE));
     boolean hub=friendly && (bank || n.supportsAction(DialogAction.BUY) || DataManager.TELEPORTER_DATA.getTeleporterTemplateByNpcId(id)!=null);
     if(!mob && !hub)continue;
     for(var group:DataManager.SPAWNS_DATA.getSpawnsForNpc(map.getMapId(),id)) {
      if(group.isTemporarySpawn() || group.getHandlerType()!=null)continue;
      for(var s:group.getSpawnTemplates())if(!s.isAerialSpawn()) {
       // PrepareDestinationCache puts service camps 5m in front of the NPC.
       double facing=Math.toRadians(Byte.toUnsignedInt(s.getHeading())*3.0);
       var spawn=new Spawn(id,s.getX()+(hub?(float)Math.cos(facing)*5:0),s.getY()+(hub?(float)Math.sin(facing)*5:0),s.getZ(),n.getLevel(),hub,bank);
       if(mob)mobs.add(spawn);if(hub)hubs.add(spawn);
      }
     }
    }
    // Native Aion has no WoW area_level table. Nearby ordinary mob bands replace its zone brackets.
    for(var hub:hubs) {
     var nearby=mobs.stream().filter(m->distance(hub,m)<=250*250).toList();
     var band=nearby.isEmpty()?mobs:nearby;
     int low=band.stream().mapToInt(Spawn::level).min().orElse(1),high=band.stream().mapToInt(Spawn::level).max().orElse(65);
     result.add(new Location(race,map.getMapId(),hub.x(),hub.y(),hub.z(),Math.max(1,low-1),Math.min(65,high+3),hub.city() || nearby.isEmpty(),true));
    }
    // Upstream legacy grind cache: populated 50m cells, not bosses/temporary encounters.
    var cells=new HashMap<String,List<Spawn>>();
    for(var m:mobs)cells.computeIfAbsent((int)(m.x()/50)+":"+(int)(m.y()/50),k->new ArrayList<>()).add(m);
    for(var cell:cells.values()) {
     // TravelMgr registers individual grind spawns, including sparse camps.
     // One representative per level keeps the native XP band attached to an
     // actual monster, rather than a mixed cell's unrelated min/max bracket.
     var levels=new HashSet<Integer>();
     for(var m:cell)if(levels.add(m.level()))
      result.add(new Location(race,map.getMapId(),m.x(),m.y(),m.z(),Math.max(1,m.level()-4),Math.min(65,m.level()+10),false,false));
    }
   }
   var start=DataManager.PLAYER_INITIAL_DATA.getSpawnLocation(race);
   // TravelMgr::PrepareDestinationCache includes race starting positions in
   // the preferred hub cache for levels 1..5, alongside service hubs. Otherwise
   // select() drops this native alternative whenever any service hub exists.
   result.add(new Location(race,start.getMapId(),start.getX(),start.getY(),start.getZ(),1,5,false,true));
  }
  cache=result.stream().map(p->{int start=DataManager.PLAYER_INITIAL_DATA.getSpawnLocation(p.race()).getMapId();
   return p.map()==start?p:new Location(p.race(),p.map(),p.x(),p.y(),p.z(),Math.max(10,p.minimum()),Math.max(10,p.maximum()),p.city(),p.hub());}).distinct().toList();return cache;
 }
 private static double distance(Spawn a,Spawn b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2);}
 static boolean idle(PlayerBotSession session) {
  Player bot=session.bot();return session.autonomous() && !session.closing() && bot.isSpawned() && !bot.isDead()
   && !bot.isInInstance() && bot.getPlayerGroup()==null && !bot.isInAlliance() && !bot.isFlying() && bot.getFlightPath()==null
   && !bot.isCasting() && !bot.isTrading() && !bot.isLooting() && !bot.getController().isInCombat()
   && bot.canPerformMove() && bot.getAggroList().stream().findAny().isEmpty()
   && !bot.isInState(CreatureState.RESTING) && !bot.getController().hasTask(TaskId.ITEM_USE);
 }
 static boolean nearHuman(int map,float x,float y,float z) {
  return World.getInstance().getAllPlayers().stream().anyMatch(p->!p.isPlayerBot() && p.isSpawned() && p.getWorldId()==map
   && p.getInstanceId()==1 && PositionUtil.getDistance(p,x,y,z)<150);
 }
 static boolean ground(WorldMapTemplate map,float x,float y,float z,float ground) {
  return Float.isFinite(ground) && Math.abs(ground-z)<=4 && ground>map.getDeathLevel() && ground>=map.getWaterLevel()
   && x>0 && y>0 && x<map.getWorldSize() && y<map.getWorldSize();
 }
 static Location destination(Race race,int level,int cityPercent,RandomGenerator rng) {
  return destination(race,level,cityPercent,rng,Set.of());
 }
 private static Location destination(Race race,int level,int cityPercent,RandomGenerator rng,Set<Integer> focusMaps) {
  var enabled=PlayerBotConfig.POPULATION_MAPS;
  var players=World.getInstance().getAllPlayers();var focus=PlayerBotPopulationFocus.capture(players);
  var choices=focus.active()?focusChoices(race,level,focus,rng):select(locations(),race,level,enabled,Set.of(),false,cityPercent,rng);
  if(!focusMaps.isEmpty())choices=choices.stream().filter(p->focusMaps.contains(p.map())).toList();
  var counts=new HashMap<Integer,Integer>();
  if(focus.active())for(var player:players)if(player.isWorldBot() && player.getPlayerBotOwnerId()==0 && player.isSpawned()
   && PlayerBotPopulationPolicy.serverAccount(player.getAccount().getId()))counts.merge(player.getWorldId(),1,Integer::sum);
  var occupied=PlayerBotConfig.POPULATION_CONCENTRATE?PlayerBotPlayerZones.occupied(players):Set.<PlayerBotPlayerZones.Zone>of();
  var preferred=focus.active()?focus.prefer(choices,counts):PlayerBotPlayerZones.prefer(choices,occupied,PlayerBotConfig.POPULATION_CONCENTRATE);
  for(var p:preferred) {
   var map=DataManager.WORLD_MAPS_DATA.getTemplate(p.map());if(!mapEligible(map,race,enabled) || nearHuman(p.map(),p.x(),p.y(),p.z()))continue;
   float z=GeoService.getInstance().getZ(p.map(),p.x(),p.y(),p.z()+2,p.z()-4,1);
   if(!ground(map,p.x(),p.y(),p.z(),z) || !GeoService.getInstance().isPlayerBotLanding(p.map(),1,p.x(),p.y(),z))continue;
   var worldMap=World.getInstance().getWorldMap(p.map());
   if(worldMap==null || worldMap.getMainWorldMapInstance().getRegion(p.x(),p.y(),z)==null)continue;
   return new Location(p.race(),p.map(),p.x(),p.y(),z,p.minimum(),p.maximum(),p.city(),p.hub());
  }
  // Native spawns can sit against a tree/column or in a narrow actor gap.
  // Exhaust the original anchors first, then bound nearby probes so an
  // unsuitable map cannot turn admission into an unbounded geometry job.
  int[] probes={0};long deadline=System.nanoTime()+20_000_000;
  for(var p:preferred) {
   var map=DataManager.WORLD_MAPS_DATA.getTemplate(p.map());if(!mapEligible(map,race,enabled))continue;
   var point=PlayerBotCampLanding.nearby(new PlayerBotNavigation.Point(p.x(),p.y(),p.z()),new PlayerBotCampLanding.Ground(){
    public float height(float x,float y,float sourceZ){return GeoService.getInstance().getZ(p.map(),x,y,sourceZ+2,sourceZ-4,1);}
    public boolean safe(PlayerBotNavigation.Point point){
     var worldMap=World.getInstance().getWorldMap(p.map());
     return ground(map,point.x(),point.y(),p.z(),point.z()) && !nearHuman(p.map(),point.x(),point.y(),point.z())
      && worldMap!=null && worldMap.getMainWorldMapInstance().getRegion(point.x(),point.y(),point.z())!=null
      && GeoService.getInstance().isPlayerBotLanding(p.map(),1,point.x(),point.y(),point.z());
    }
   },()->probes[0]++>=128 || System.nanoTime()>=deadline);
   if(point!=null)return new Location(p.race(),p.map(),point.x(),point.y(),point.z(),p.minimum(),p.maximum(),p.city(),p.hub());
   if(probes[0]>=128 || System.nanoTime()>=deadline)break;
  }
  return null;
 }
 static boolean place(PlayerBotSession session,int cityPercent,RandomGenerator rng) {
  return place(session,cityPercent,rng,Set.of());
 }
 static boolean place(PlayerBotSession session,int cityPercent,RandomGenerator rng,Set<Integer> focusMaps) {
  Player bot=session.bot();if(!idle(session) || nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()))return false;
  var p=destination(bot.getRace(),bot.getLevel(),cityPercent,rng,focusMaps);if(p==null)return false;
  if(!idle(session) || nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()) || nearHuman(p.map(),p.x(),p.y(),p.z()))return false;
  session.preparePopulationPlacement();
  TeleportService.teleportTo(bot,p.map(),1,p.x(),p.y(),p.z(),bot.getHeading());
  return bot.isSpawned() && bot.getWorldId()==p.map() && PositionUtil.getDistance(bot,p.x(),p.y(),p.z())<3;
 }
 private PlayerBotWorldPlacement(){}
}
