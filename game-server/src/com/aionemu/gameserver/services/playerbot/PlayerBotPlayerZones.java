/* RandomPlayerbotMgr::GetPlayerZoneTeleportLocations / RandomTeleportForLevel,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import com.aionemu.gameserver.model.templates.zone.ZoneClassName;
import com.aionemu.gameserver.model.templates.zone.ZoneInfo;

/** Native open-world zone preference; never an owner-follow or summon decision. */
final class PlayerBotPlayerZones {
 record Zone(int map,String name) {}
 private static final Map<Integer,List<ZoneInfo>> ZONES=new ConcurrentHashMap<>();
 private static final Map<PlayerBotWorldPlacement.Location,Zone> DESTINATIONS=new ConcurrentHashMap<>();

 static boolean eligible(boolean bot,boolean online,boolean spawned,boolean observer,int instance,WorldMapTemplate map) {
  return !bot && online && spawned && !observer && instance==1 && map!=null && !map.isInstance() && !map.isPrison();
 }

 static Zone resolve(Collection<ZoneInfo> zones,int map,float x,float y,float z) {
  // Aion SUB is the named geographic area. Weather, PvP and quest overlays are
  // not geographic zones. Match native MapRegion's ascending priority/name order.
  return zones.stream().filter(zone->zone.getZoneTemplate().getZoneType()==ZoneClassName.SUB)
   .filter(zone->zone.getArea().isInside3D(x,y,z))
   .min(Comparator.comparingInt((ZoneInfo zone)->zone.getZoneTemplate().getPriority())
    .thenComparingInt(zone->zone.getZoneTemplate().getName().hashCode()))
   .map(zone->new Zone(map,zone.getZoneTemplate().getName())).orElse(new Zone(map,""));
 }

 private static Zone resolve(int map,float x,float y,float z) {
  return resolve(ZONES.computeIfAbsent(map,id->List.copyOf(DataManager.ZONE_DATA.getZones(id))),map,x,y,z);
 }

 static Set<Zone> occupied(Collection<Player> players) {
  var occupied=new HashSet<Zone>();
  for(var player:players) {
   if(player==null)continue;
   // WoW excludes GM mode. Aion has staff access rather than a matching mode:
   // visible staff playing normally remain participants; .invis observers do not.
   boolean observer=player.isStaff() && player.isInVisualState(CreatureVisualState.HIDE20);
   if(eligible(player.isPlayerBot(),player.isOnline(),player.isSpawned(),observer,player.getInstanceId(),
      DataManager.WORLD_MAPS_DATA.getTemplate(player.getWorldId())))
    occupied.add(resolve(player.getWorldId(),player.getX(),player.getY(),player.getZ()));
  }
  return Set.copyOf(occupied);
 }

 static List<PlayerBotWorldPlacement.Location> prefer(List<PlayerBotWorldPlacement.Location> choices,
   Set<Zone> occupied,boolean concentrate,Function<PlayerBotWorldPlacement.Location,Zone> resolve) {
  // Upstream's city branch returns before player-zone concentration.
  if(!concentrate || occupied.isEmpty() || choices.isEmpty() || choices.getFirst().city())return choices;
  var local=choices.stream().filter(location->occupied.contains(resolve.apply(location))).toList();
  return local.isEmpty()?choices:local;
 }

 static List<PlayerBotWorldPlacement.Location> prefer(List<PlayerBotWorldPlacement.Location> choices,
   Set<Zone> occupied,boolean concentrate) {
  return prefer(choices,occupied,concentrate,location->DESTINATIONS.computeIfAbsent(location,
   point->resolve(point.map(),point.x(),point.y(),point.z())));
 }
 private PlayerBotPlayerZones() {}
}
