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
import com.aionemu.gameserver.model.templates.zone.ZoneInfo;

/** Background population placement, separate from ordinary paid-route activity travel. */
final class PlayerBotWorldPlacement {
 enum Purpose { POPULATION, FIELD_RESCUE, RESERVE_CREATION, PLAYER_PRESENCE }
 record Location(Race race,int map,float x,float y,float z,int minimum,int maximum,boolean city,boolean hub) {
  Location {if(race!=Race.ELYOS && race!=Race.ASMODIANS || map<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
    || minimum<1 || maximum<minimum || maximum>65)throw new IllegalArgumentException("Invalid population location");}
 }
 record Spawn(int npc,float x,float y,float z,int level,boolean hub,boolean city) {}
 private static List<Location> cache;
 private static Map<Integer,Location> mapAnchors=Map.of();
 private static final Map<String,List<Spawn>> FACTION_ANCHORS=new HashMap<>();
 private static final Map<String,List<Spawn>> FACTION_ENEMIES=new HashMap<>();
 record Selection(Location location,String reason,int checked,boolean exhausted){}
 // Bounded native faction/level keys retain scan position across deferred admissions.
 private static final class Scan {
  Set<Location> candidates=Set.of();final Set<Location> tested=new HashSet<>();
  List<Location> nearby=List.of();int anchor;final int[] step={0};
  void reset(){tested.clear();nearby=List.of();anchor=step[0]=0;}
 }
 private static final Map<String,Scan> admissionCursor=new HashMap<>();
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
  return focusChoices(race,level,focus,Purpose.POPULATION,rng);
 }
 static List<Location> focusChoices(Race race,int level,PlayerBotPopulationFocus.Snapshot focus,Purpose purpose,RandomGenerator rng) {
  // Cache only immutable native faction/level bands. Live maps/players/occupancy are never cached here.
  var all=focusCandidates(race,level);
  var choices=new ArrayList<Location>();
  for(int map:focus.maps()) {
   if(!PlayerBotConfig.POPULATION_MAPS.isEmpty() && !PlayerBotConfig.POPULATION_MAPS.contains(map))continue;
   var local=all.getOrDefault(map,List.of());
   var fields=local.stream().filter(p->!p.hub() && !p.city()).toList();
   // A player in a native capital needs service/RPG residents rather than an empty field tier.
   choices.addAll(purpose==Purpose.FIELD_RESCUE?fields:fields.isEmpty()?local:fields);
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
 static boolean presenceCompatible(Race race,int level,PlayerBotPopulationFocus.Anchor area,int radius) {
  if(!PlayerBotConfig.POPULATION_MAPS.isEmpty() && !PlayerBotConfig.POPULATION_MAPS.contains(area.map()))return false;
  return focusCandidates(race,level).getOrDefault(area.map(),List.of()).stream()
   .anyMatch(p->PlayerBotPlayerPresence.near(area,p.map(),p.x(),p.y(),p.z(),radius));
 }
 static boolean mapEligible(WorldMapTemplate map,Race race,Set<Integer> enabled) {
  return map!=null && !map.isInstance() && !map.isPrison() && map.getWorldType()!=WorldType.NONE
   && map.getWorldType()!=WorldType.PRISON && map.getWorldType()!=WorldType.PANESTERRA
   && (map.getWorldType()!=WorldType.ELYSEA || race==Race.ELYOS)
   && (map.getWorldType()!=WorldType.ASMODAE || race==Race.ASMODIANS)
   && (!PlayerBotConfig.POPULATION_FACTION_HOME || PlayerBotTerritory.homeland(map.getMapId())==Race.NONE || PlayerBotTerritory.homeland(map.getMapId())==race)
   && (enabled.isEmpty() || enabled.contains(map.getMapId()));
 }
 /** How far above its own level a bot may be sent to a camp. Every mob in a camp can aggro at once, and low characters have the least to spare. */
 static int campMargin(int level){return level<10?1:level<30?2:3;}
 static int campMinimum(int mob){for(int level=1;level<mob;level++)if(mob<=level+campMargin(level))return level;return Math.max(1,mob);}
 /** Poeta and Ishalgen are for characters before Ascension. Camps there allowed a mob's level +10, so ascended level 10-19
 * bots were placed and pulled beside new players (owner, 9 Nov). The starting map now stops at level 9. */
 static final int ASCENSION_LEVEL=10;
 static int starterMaximum(int maximum){return Math.min(ASCENSION_LEVEL-1,maximum);}
 static boolean suitable(Location p,Race race,int level,Set<Integer> enabled) {
  return p.race()==race && level>=p.minimum() && level<=p.maximum() && (enabled.isEmpty() || enabled.contains(p.map()));
 }
 static List<Location> select(List<Location> locations,Race race,int level,Set<Integer> enabled,
                             Set<Integer> playerMaps,boolean concentrate,int cityPercent,RandomGenerator random) {
  return select(locations,race,level,enabled,playerMaps,concentrate,cityPercent,Purpose.POPULATION,random);
 }
 static List<Location> select(List<Location> locations,Race race,int level,Set<Integer> enabled,
                             Set<Integer> playerMaps,boolean concentrate,int cityPercent,Purpose purpose,RandomGenerator random) {
  var all=locations.stream().filter(p->suitable(p,race,level,enabled)).toList();
  var cities=all.stream().filter(Location::city).toList();
  var hubs=all.stream().filter(p->p.hub() && !p.city()).toList();
  // Field intent must not resolve to an RPG service hub or an arena lobby.
  // Preserve native starter alternatives before low-level ground validation.
  var fields=all.stream().filter(p->!p.city() && !p.hub()).toList();
  var ordinary=fields.isEmpty()?hubs:level<=5?java.util.stream.Stream.concat(fields.stream(),hubs.stream()).toList():fields;
  var choices=purpose==Purpose.FIELD_RESCUE?fields:level>=10 && random.nextInt(100)<cityPercent && !cities.isEmpty()?cities:ordinary;
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
    var ownSide=new ArrayList<Spawn>();var enemySide=new ArrayList<Spawn>();
    TribeClass opposite=race==Race.ELYOS?TribeClass.PC_DARK:TribeClass.PC;
    for(int id:ids) {
     var n=DataManager.NPC_DATA.getNpcTemplate(id);if(n==null)continue;
     boolean hostile=DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),tribe) || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),tribe);
     boolean mob=hostile && PlayerBotHunting.template(n);
     boolean friendly=!hostile && (n.getRace()==race || n.getRace()==Race.PC_ALL || DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(n.getTribe(),tribe));
     boolean bank=friendly && (n.supportsAction(DialogAction.OPEN_PERSONAL_WAREHOUSE) || n.supportsAction(DialogAction.RETRIEVE_CHAR_WAREHOUSE));
     boolean hub=friendly && (bank || n.supportsAction(DialogAction.BUY) || DataManager.TELEPORTER_DATA.getTeleporterTemplateByNpcId(id)!=null);
     boolean own=n.getRace()==race || !hostile && DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(n.getTribe(),tribe)
      && (DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),opposite) || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),opposite));
     boolean enemy=hostile && (n.getRace()==(race==Race.ELYOS?Race.ASMODIANS:Race.ELYOS)
      || DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(n.getTribe(),opposite));
     if(!mob && !hub && !own && !enemy)continue;
     for(var group:DataManager.SPAWNS_DATA.getSpawnsForNpc(map.getMapId(),id)) {
      if(group.isTemporarySpawn() || group.getHandlerType()!=null)continue;
      for(var s:group.getSpawnTemplates())if(!s.isAerialSpawn()) {
       // PrepareDestinationCache puts service camps 5m in front of the NPC.
       double facing=Math.toRadians(Byte.toUnsignedInt(s.getHeading())*3.0);
       var spawn=new Spawn(id,s.getX()+(hub?(float)Math.cos(facing)*5:0),s.getY()+(hub?(float)Math.sin(facing)*5:0),s.getZ(),n.getLevel(),hub,bank);
       if(mob)mobs.add(spawn);if(hub)hubs.add(spawn);
       if(own)ownSide.add(spawn);if(enemy)enemySide.add(spawn);
      }
     }
    }
    if(PlayerBotConfig.POPULATION_FACTION_HOME) {
     FACTION_ANCHORS.put(race+":"+map.getMapId(),List.copyOf(ownSide));
     FACTION_ENEMIES.put(race+":"+map.getMapId(),List.copyOf(enemySide));
     mobs.removeIf(p->!factionSide(p,ownSide,enemySide));
     hubs.removeIf(p->!factionSide(p,ownSide,enemySide));
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
      result.add(new Location(race,map.getMapId(),m.x(),m.y(),m.z(),campMinimum(m.level()),Math.min(65,m.level()+10),false,false));
    }
   }
   var start=DataManager.PLAYER_INITIAL_DATA.getSpawnLocation(race);
   // TravelMgr::PrepareDestinationCache includes race starting positions in
   // the preferred hub cache for levels 1..5, alongside service hubs. Otherwise
   // select() drops this native alternative whenever any service hub exists.
   result.add(new Location(race,start.getMapId(),start.getX(),start.getY(),start.getZ(),1,5,false,true));
  }
  cache=result.stream().map(p->{int start=DataManager.PLAYER_INITIAL_DATA.getSpawnLocation(p.race()).getMapId();
   return p.map()==start?new Location(p.race(),p.map(),p.x(),p.y(),p.z(),p.minimum(),starterMaximum(p.maximum()),p.city(),p.hub()):new Location(p.race(),p.map(),p.x(),p.y(),p.z(),Math.max(10,p.minimum()),Math.max(10,p.maximum()),p.city(),p.hub());}).distinct().toList();
  var anchors=new HashMap<Integer,Location>();
  for(var p:cache)if(!anchors.containsKey(p.map()) || p.hub() && !anchors.get(p.map()).hub())anchors.put(p.map(),p);
  mapAnchors=Map.copyOf(anchors);return cache;
 }
 static Location mapAnchor(int map){locations();return mapAnchors.get(map);}
 private static double distance(Spawn a,Spawn b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2);}
 private static boolean factionSide(Spawn point,List<Spawn> own,List<Spawn> enemy) {
  if(enemy.isEmpty())return true;
  double hostile=enemy.stream().mapToDouble(p->distance(point,p)).min().orElse(Double.POSITIVE_INFINITY);
  double friendly=own.stream().mapToDouble(p->distance(point,p)).min().orElse(Double.POSITIVE_INFINITY);
  return hostile>=Math.pow(Math.max(0,PlayerBotConfig.POPULATION_ENEMY_CAMP_DISTANCE),2) && friendly<hostile;
 }
 static boolean factionSide(Race race,int map,float x,float y,float z) {
  if(!PlayerBotConfig.POPULATION_FACTION_HOME)return true;
  locations();
  return factionSide(new Spawn(0,x,y,z,1,false,false),FACTION_ANCHORS.getOrDefault(race+":"+map,List.of()),
   FACTION_ENEMIES.getOrDefault(race+":"+map,List.of()));
 }
 static boolean idle(PlayerBotSession session) {
  Player bot=session.bot();return session.autonomous() && !session.closing() && bot.isSpawned() && !bot.isDead()
   && !(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController move && (move.ridingPlatform() || move.traversingHeight()))
   && !bot.isInInstance() && bot.getPlayerGroup()==null && !bot.isInAlliance() && !bot.isFlying() && bot.getFlightPath()==null
   && !bot.isCasting() && !bot.isTrading() && !bot.isLooting() && !bot.getController().isInCombat()
   && bot.canPerformMove() && bot.getAggroList().stream().findAny().isEmpty()
   && !bot.isInState(CreatureState.RESTING) && !bot.getController().hasTask(TaskId.ITEM_USE);
 }
 static boolean nearHuman(int map,float x,float y,float z) {
  return World.getInstance().getAllPlayers().stream().anyMatch(p->!p.isPlayerBot() && p.isSpawned() && p.getWorldId()==map
   && p.getInstanceId()==1 && PositionUtil.getDistance(p,x,y,z)<Math.max(150,PlayerBotConfig.POPULATION_UNSEEN_DISTANCE));
 }
 private static boolean occupiedLanding(int map,float x,float y,float z) {
  return World.getInstance().getAllPlayers().stream().anyMatch(p->p.isPlayerBot() && p.isSpawned() && p.getWorldId()==map && p.getInstanceId()==1
   && PlayerBotCampDistribution.overlaps(x,y,z,p.getX(),p.getY(),p.getZ()));
 }
 static boolean ground(WorldMapTemplate map,float x,float y,float z,float ground) {
  return Float.isFinite(ground) && Math.abs(ground-z)<=4 && ground>=-2 && ground>=map.getWaterLevel()
   && x>0 && y>0 && x<map.getWorldSize() && y<map.getWorldSize();
 }
 /** Native player-zone preference includes altitude: overlapping halls are distinct areas.
  * Preserve map demand first and keep all other safe camps as fallback candidates. */
 static List<Location> preferPlayerAreas(List<Location> choices,PlayerBotPopulationFocus.Snapshot focus,
   Map<Integer,Integer> counts,Map<Integer,List<ZoneInfo>> zones) {
  var preferred=focus.prefer(choices,counts);if(!focus.active())return preferred;
  var occupied=new HashSet<PlayerBotPlayerZones.Zone>();
  for(var anchor:focus.anchors()) {
   var zone=PlayerBotPlayerZones.resolve(zones.getOrDefault(anchor.map(),List.of()),anchor.map(),anchor.x(),anchor.y(),anchor.z());
   if(!zone.name().isEmpty())occupied.add(zone);
  }
  if(occupied.isEmpty())return preferred;
  var matches=new HashSet<Location>();
  for(var p:preferred)if(occupied.contains(PlayerBotPlayerZones.resolve(zones.getOrDefault(p.map(),List.of()),p.map(),p.x(),p.y(),p.z())))matches.add(p);
  var ordered=new ArrayList<>(preferred);
  ordered.sort(Comparator.comparingInt((Location p)->counts.getOrDefault(p.map(),0)).thenComparingInt(p->matches.contains(p)?0:1));
  return List.copyOf(ordered);
 }
 private static List<Location> preferPlayerAreas(List<Location> choices,PlayerBotPopulationFocus.Snapshot focus,Map<Integer,Integer> counts) {
  var zones=new HashMap<Integer,List<ZoneInfo>>();
  for(int map:focus.maps())zones.put(map,List.copyOf(DataManager.ZONE_DATA.getZones(map)));
  return preferPlayerAreas(choices,focus,counts,zones);
 }
 static Location destination(Race race,int level,int cityPercent,RandomGenerator rng) {
  return destination(race,level,cityPercent,rng,Set.of());
 }
 private static Location destination(Race race,int level,int cityPercent,RandomGenerator rng,Set<Integer> focusMaps) {
  return chooseDestination(race,level,cityPercent,rng,focusMaps).location();
 }
 private static Location destination(Race race,int level,int cityPercent,Purpose purpose,RandomGenerator rng,Set<Integer> focusMaps) {
  return chooseDestination(race,level,cityPercent,purpose,rng,focusMaps).location();
 }
 static Selection chooseDestination(Race race,int level,int cityPercent,RandomGenerator rng){
  return chooseDestination(race,level,cityPercent,rng,Set.of());
 }
 static Selection chooseReserveDestination(Race race,int level,int cityPercent,RandomGenerator rng) {
  return chooseDestination(race,level,cityPercent,Purpose.RESERVE_CREATION,rng,Set.of());
 }
 static Selection chooseGrindDestination(Race race,int level,RandomGenerator rng) {
  return chooseDestination(race,level,0,Purpose.FIELD_RESCUE,rng,Set.of());
 }
 private static Selection chooseDestination(Race race,int level,int cityPercent,RandomGenerator rng,Set<Integer> focusMaps) {
  return chooseDestination(race,level,cityPercent,Purpose.POPULATION,rng,focusMaps);
 }
 private static Selection chooseDestination(Race race,int level,int cityPercent,Purpose purpose,RandomGenerator rng,Set<Integer> focusMaps) {
  if(purpose==Purpose.POPULATION && focusMaps.isEmpty() && PlayerBotPlayerPresence.enabled()) {
   var demand=PlayerBotPlayerPresence.demand(World.getInstance().getAllPlayers(),race,level);
   if(demand.active() && focusCompatible(race,level,demand)) {
    var local=choosePresenceDestination(race,level,rng,demand);
    if(local.location()!=null)return local;
   }
   // Local shortage must never block the original manager's safe admission.
  }
  return chooseDestination(race,level,cityPercent,purpose,rng,focusMaps,null);
 }
 static Selection choosePresenceDestination(Race race,int level,RandomGenerator rng,PlayerBotPopulationFocus.Snapshot demand) {
  return chooseDestination(race,level,0,Purpose.PLAYER_PRESENCE,rng,Set.of(),demand);
 }
 private static Selection chooseDestination(Race race,int level,int cityPercent,Purpose purpose,RandomGenerator rng,Set<Integer> focusMaps,PlayerBotPopulationFocus.Snapshot explicitPresence) {
  var enabled=PlayerBotConfig.POPULATION_MAPS;
  var players=World.getInstance().getAllPlayers();
  // Saved reserves are not visible admissions. They need native faction/level
  // camps even when the only human occupies the other faction's homeland.
  // Player focus still governs actual residency; all landing guards below stay live.
  var focus=PlayerBotPopulationFocus.capture(purpose==Purpose.RESERVE_CREATION?List.<Player>of():players);
  var presence=explicitPresence;
  boolean nearbyPresence=presence!=null && presence.active() && focusCompatible(race,level,presence);
  if(purpose==Purpose.PLAYER_PRESENCE && !nearbyPresence)return new Selection(null,"no compatible native camps for actual player demand",0,true);
  if(nearbyPresence)focus=presence;
  // A rescue needs an actual grind camp. Human focus must not convert it into a capital service visit.
  var choices=focus.active()?focusChoices(race,level,focus,purpose,rng):select(locations(),race,level,enabled,Set.of(),false,cityPercent,purpose,rng);
   // A stranded low/high-level resident may have no camps on the human's map.
   // Rescue may use another enabled native map; normal focus admission is unchanged.
   if(purpose==Purpose.FIELD_RESCUE && choices.isEmpty()){
    choices=select(locations(),race,level,enabled,Set.of(),false,0,purpose,rng);
    focus=new PlayerBotPopulationFocus.Snapshot(List.of(),focus.radius());
   }
  // The overlay fills only the requested nearby area. Ordinary worldwide/city
  // selection resumes once its quota is met or this native level cannot fit.
  final var landingFocus=focus;
  if(nearbyPresence)choices=choices.stream().filter(p->landingFocus.nearby(p.map(),p.x(),p.y(),p.z())).toList();
  if(!focusMaps.isEmpty())choices=choices.stream().filter(p->focusMaps.contains(p.map())).toList();
  var counts=new HashMap<Integer,Integer>();
  if(focus.active())for(var player:players)if(player.isWorldBot() && player.getPlayerBotOwnerId()==0 && player.isSpawned()
   && PlayerBotPopulationPolicy.serverAccount(player.getAccount().getId()))counts.merge(player.getWorldId(),1,Integer::sum);
  var occupied=PlayerBotConfig.POPULATION_CONCENTRATE?PlayerBotPlayerZones.occupied(players):Set.<PlayerBotPlayerZones.Zone>of();
  var preferred=focus.active()?preferPlayerAreas(choices,focus,counts):PlayerBotPlayerZones.prefer(choices,occupied,PlayerBotConfig.POPULATION_CONCENTRATE);
  var positions=new ArrayList<PlayerBotCampDistribution.Position>();
  for(var p:players)if(p.isPlayerBot() && p.isSpawned() && p.getInstanceId()==1)positions.add(new PlayerBotCampDistribution.Position(p.getWorldId(),p.getX(),p.getY(),p.getZ()));
  var distribution=new PlayerBotCampDistribution(positions);preferred=distribution.prefer(preferred,counts);
  if(nearbyPresence) {
   // Fill closer native camps first; equal 100m distance bands retain local
   // crowd spreading. The outer radius is fallback, not a ring to hide bots in.
   var nearby=new ArrayList<>(preferred);
   nearby.sort(Comparator.comparingInt(p->(int)(landingFocus.distance(p.map(),p.x(),p.y(),p.z())/100)));
   preferred=List.copyOf(nearby);
  }
  if(preferred.isEmpty())return new Selection(null,"no native faction/level camps on the current enabled focus maps",0,true);
  Scan scan;String scanKey=race+":"+level+":"+purpose;
  synchronized(admissionCursor){scan=admissionCursor.computeIfAbsent(scanKey,key->new Scan());}
  // The old primary anchor loop was unbounded; the 20ms limit applied only afterwards.
  // Retain only a bounded sweep cursor, never native floor/occupancy/visibility results.
  synchronized(scan){
  var membership=Set.copyOf(preferred);
  if(!scan.candidates.equals(membership)){scan.reset();scan.candidates=membership;}
  int checked=0;long deadline=System.nanoTime()+20_000_000;long now=System.currentTimeMillis();
  for(var p:preferred) {
   if(scan.tested.contains(p))continue;
   if(checked>=128 || System.nanoTime()>=deadline)break;
   scan.tested.add(p);checked++;
   var map=DataManager.WORLD_MAPS_DATA.getTemplate(p.map());if(!mapEligible(map,race,enabled) || nearHuman(p.map(),p.x(),p.y(),p.z()))continue;
   float z=GeoService.getInstance().getZ(p.map(),p.x(),p.y(),p.z()+2,p.z()-4,1);
   if(!ground(map,p.x(),p.y(),p.z(),z) || !distribution.clear(p.map(),p.x(),p.y(),z) || !GeoService.getInstance().isPlayerBotLanding(p.map(),1,p.x(),p.y(),z))continue;
   var worldMap=World.getInstance().getWorldMap(p.map());
   if(worldMap==null || worldMap.getMainWorldMapInstance().getRegion(p.x(),p.y(),z)==null)continue;
   if(occupiedLanding(p.map(),p.x(),p.y(),z))continue;
   if(PlayerBotCampSafety.avoided(p.map(),p.x(),p.y(),level,now))continue;
   if(PlayerBotCampSafety.dangerous(p.map(),p.x(),p.y(),z,race,level)) {
    // A grind camp is a monster spawn point: land outside the pack's aggro range and let the bot pull.
    var standoff=PlayerBotCampSafety.standoff(p.map(),p.x(),p.y(),z,race,level,landingGround(map,p,race,distribution,nearbyPresence?landingFocus:null),now);
    if(standoff==null){PlayerBotCampSafety.DANGER_SKIPS.incrementAndGet();continue;}
    scan.reset();return new Selection(new Location(p.race(),p.map(),standoff.x(),standoff.y(),standoff.z(),p.minimum(),p.maximum(),p.city(),p.hub()),"validated native camp standoff",checked,false);
   }
   scan.reset();return new Selection(new Location(p.race(),p.map(),p.x(),p.y(),z,p.minimum(),p.maximum(),p.city(),p.hub()),"validated native camp",checked,false);
  }
  if(scan.tested.size()<membership.size())return new Selection(null,"native camp scan deferred at its work budget; next admission continues",checked,false);
  // Native spawns can sit against a tree/column or in a narrow actor gap.
  // Exhaust the original anchors first, then bound nearby probes so an
  // unsuitable map cannot turn admission into an unbounded geometry job.
  int[] probes={checked};
  if(scan.nearby.isEmpty())scan.nearby=List.copyOf(preferred);
  while(scan.anchor<scan.nearby.size()) {
   var p=scan.nearby.get(scan.anchor);
   var map=DataManager.WORLD_MAPS_DATA.getTemplate(p.map());if(!mapEligible(map,race,enabled)){scan.anchor++;scan.step[0]=0;continue;}
   var point=PlayerBotCampLanding.nearby(new PlayerBotNavigation.Point(p.x(),p.y(),p.z()),new PlayerBotCampLanding.Ground(){
    public float height(float x,float y,float sourceZ){return GeoService.getInstance().getZ(p.map(),x,y,sourceZ+2,sourceZ-4,1);}
    public boolean safe(PlayerBotNavigation.Point point){
     var worldMap=World.getInstance().getWorldMap(p.map());
     return (!nearbyPresence || landingFocus.nearby(p.map(),point.x(),point.y(),point.z()))
      && ground(map,point.x(),point.y(),p.z(),point.z()) && factionSide(race,p.map(),point.x(),point.y(),point.z()) && !nearHuman(p.map(),point.x(),point.y(),point.z())
      && distribution.clear(p.map(),point.x(),point.y(),point.z())
      && worldMap!=null && worldMap.getMainWorldMapInstance().getRegion(point.x(),point.y(),point.z())!=null
      && GeoService.getInstance().isPlayerBotLanding(p.map(),1,point.x(),point.y(),point.z()) && !occupiedLanding(p.map(),point.x(),point.y(),point.z())
      && !PlayerBotCampSafety.unsafe(p.map(),point.x(),point.y(),point.z(),race,level,now);
    }
   },scan.step,()->{if(probes[0]>=128 || System.nanoTime()>=deadline)return true;probes[0]++;return false;});
   if(point!=null){scan.reset();return new Selection(new Location(p.race(),p.map(),point.x(),point.y(),point.z(),p.minimum(),p.maximum(),p.city(),p.hub()),"validated nearby native camp",probes[0],false);}
   if(scan.step[0]>=16){scan.anchor++;scan.step[0]=0;}
   if(probes[0]>=128 || System.nanoTime()>=deadline)break;
  }
  boolean exhausted=scan.anchor>=scan.nearby.size();if(exhausted)scan.reset();
  return new Selection(null,exhausted?"current native camps rejected by ground, visibility or occupancy guards":"nearby native camp scan deferred at its work budget; next admission continues",probes[0],exhausted);
  }
 }
 /** The same native landing guards the nearby probe applies, for standoff points around a camp anchor. */
 static PlayerBotCampSafety.Ground landingGround(WorldMapTemplate map,Location p,Race race,PlayerBotCampDistribution distribution,PlayerBotPopulationFocus.Snapshot focus) {
  return new PlayerBotCampSafety.Ground(){
   public float height(float x,float y,float sourceZ){return GeoService.getInstance().getZ(p.map(),x,y,sourceZ+6,sourceZ-8,1);}
   public boolean valid(float x,float y,float z){
    var worldMap=World.getInstance().getWorldMap(p.map());
    return (focus==null || focus.nearby(p.map(),x,y,z)) && ground(map,x,y,z,z) && factionSide(race,p.map(),x,y,z) && !nearHuman(p.map(),x,y,z)
     && (distribution==null || distribution.clear(p.map(),x,y,z)) && worldMap!=null && worldMap.getMainWorldMapInstance().getRegion(x,y,z)!=null
     && GeoService.getInstance().isPlayerBotLanding(p.map(),1,x,y,z) && !occupiedLanding(p.map(),x,y,z);
   }
  };
 }
 /** Nearest landing on the bot's own map that strong aggressive monsters cannot reach, for a bot about to sleep or just revived. */
 static Location nearestSafe(Player bot,int level) {
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(bot.getWorldId());if(map==null)return null;
  long now=System.currentTimeMillis();long deadline=System.nanoTime()+20_000_000;
  var own=locations().stream().filter(p->p.map()==bot.getWorldId() && suitable(p,bot.getRace(),level,Set.of()))
   .sorted(Comparator.comparingDouble(p->PositionUtil.getDistance(bot,p.x(),p.y(),p.z()))).limit(24).toList();
  for(var p:own) {
   if(System.nanoTime()>=deadline)break;
   float z=GeoService.getInstance().getZ(p.map(),p.x(),p.y(),p.z()+2,p.z()-4,1);
   var guard=landingGround(map,p,bot.getRace(),null,null);
   if(Float.isFinite(z) && guard.valid(p.x(),p.y(),z) && !PlayerBotCampSafety.unsafe(p.map(),p.x(),p.y(),z,bot.getRace(),level,now))
    return new Location(p.race(),p.map(),p.x(),p.y(),z,p.minimum(),p.maximum(),p.city(),p.hub());
   var standoff=PlayerBotCampSafety.standoff(p.map(),p.x(),p.y(),Float.isFinite(z)?z:p.z(),bot.getRace(),level,guard,now);
   if(standoff!=null)return new Location(p.race(),p.map(),standoff.x(),standoff.y(),standoff.z(),p.minimum(),p.maximum(),p.city(),p.hub());
  }
  return null;
 }
 /** Own-faction service points per map (vendor, warehouse, teleporter camps); the camp list is an immutable startup cache. */
 private static final Map<String,List<Location>> HUBS=new java.util.concurrent.ConcurrentHashMap<>();
 static final float HUB_PARK_RADIUS=15;
 private static final float[] HUB_RINGS={0,4,8};
 static List<Location> hubs(Race race,int map){
  return HUBS.computeIfAbsent(race+":"+map,k->locations().stream().filter(p->p.map()==map && p.race()==race && p.hub()).toList());
 }
 /** Standing at (or beside) a service point of its own faction, where a sleeping bot is safe. */
 static boolean atHub(Player bot){
  for(var p:hubs(bot.getRace(),bot.getWorldId()))if(PositionUtil.getDistance(bot,p.x(),p.y(),p.z())<=HUB_PARK_RADIUS)return true;
  return false;
 }
 /** Nearest free spot at an own-faction service point: the hub itself, then rings of 4 and 8 m, each with the
  * same native landing guard as a camp landing. Null when the 12 nearest hubs have no free ground. */
 static Location nearestHub(Player bot) {
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(bot.getWorldId());if(map==null)return null;
  var near=hubs(bot.getRace(),bot.getWorldId()).stream().sorted(Comparator.comparingDouble(p->PositionUtil.getDistance(bot,p.x(),p.y(),p.z()))).limit(12).toList();
  long deadline=System.nanoTime()+20_000_000;
  for(var p:near) {
   var guard=landingGround(map,p,bot.getRace(),null,null);
   for(float ring:HUB_RINGS)for(int step=0;step<(ring==0?1:8);step++) {
    if(System.nanoTime()>=deadline)return null;
    float x=p.x()+(float)(Math.cos(step*Math.PI/4)*ring),y=p.y()+(float)(Math.sin(step*Math.PI/4)*ring);
    float z=GeoService.getInstance().getZ(p.map(),x,y,p.z()+2,p.z()-4,1);
    if(Float.isFinite(z) && Math.abs(z-p.z())<=1.5f && guard.valid(x,y,z) && !occupiedLanding(p.map(),x,y,z))
     return new Location(p.race(),p.map(),x,y,z,p.minimum(),p.maximum(),p.city(),true);
   }
  }
  return null;
 }
 static boolean current(Location p){
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(p.map());var world=World.getInstance().getWorldMap(p.map());
  return mapEligible(map,p.race(),PlayerBotConfig.POPULATION_MAPS) && factionSide(p.race(),p.map(),p.x(),p.y(),p.z()) && !nearHuman(p.map(),p.x(),p.y(),p.z())
   && !occupiedLanding(p.map(),p.x(),p.y(),p.z()) && world!=null && world.getMainWorldMapInstance().getRegion(p.x(),p.y(),p.z())!=null
   && GeoService.getInstance().isPlayerBotLanding(p.map(),1,p.x(),p.y(),p.z());
 }
 static boolean place(PlayerBotSession session,int cityPercent,RandomGenerator rng) {
  return place(session,cityPercent,rng,Set.of());
 }
 static boolean place(PlayerBotSession session,int cityPercent,RandomGenerator rng,Set<Integer> focusMaps) {
  return place(session,Purpose.POPULATION,cityPercent,rng,focusMaps);
 }
 static boolean place(PlayerBotSession session,Purpose purpose,int cityPercent,RandomGenerator rng,Set<Integer> focusMaps) {
   Player bot=session.bot();if(!idle(session)){PlayerBotWorldActivities.placementDeferred(bot,"native action, combat or party custody");return false;}
   if(nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ())){PlayerBotWorldActivities.placementDeferred(bot,"human nearby; keeping visible position");return false;}
   var selection=chooseDestination(bot.getRace(),bot.getLevel(),cityPercent,purpose,rng,focusMaps);
   if(selection.location()==null){PlayerBotWorldActivities.placementDeferred(bot,selection.reason());return false;}
   boolean placed=placeAt(session,selection.location());
   if(!placed)PlayerBotWorldActivities.placementDeferred(bot,"landing or native custody changed");
   return placed;
 }
 static boolean placePresence(PlayerBotSession session,RandomGenerator rng) {
  var bot=session.bot();if(!idle(session) || nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()))return false;
  var selection=choosePresenceDestination(bot.getRace(),bot.getLevel(),rng,PlayerBotPlayerPresence.demand(World.getInstance().getAllPlayers(),bot.getRace(),bot.getLevel()));
  var p=selection.location();
  if(p==null || !PlayerBotPlayerPresence.demand(World.getInstance().getAllPlayers(),bot.getRace(),bot.getLevel()).nearby(p.map(),p.x(),p.y(),p.z()))return false;
  return placeAt(session,p);
 }
 static boolean placeAt(PlayerBotSession session,Location p) {
  if(p==null)return false;Player bot=session.bot();
  if(PlayerBotRoadDuty.wardenCareer(bot) && !PlayerBotWardenBeats.field(p.map(),p.x(),p.y(),p.z()))return false;
  if(!idle(session) || nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()) || nearHuman(p.map(),p.x(),p.y(),p.z()) || occupiedLanding(p.map(),p.x(),p.y(),p.z()))return false;
  session.preparePopulationPlacement();
  TeleportService.teleportTo(bot,p.map(),1,p.x(),p.y(),p.z(),bot.getHeading());
  return bot.isSpawned() && bot.getWorldId()==p.map() && PositionUtil.getDistance(bot,p.x(),p.y(),p.z())<3;
 }
 private PlayerBotWorldPlacement(){}
}
