/* RandomPlayerbotMgr::GetPlayerZoneTeleportLocations, ProcessBot, AddRandomBots;
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c, GPL-2.0-or-later.
 * Prompt player-area residency is an explicit Aion user extension of that flow. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;

/** Ephemeral real-player demand. Native character/residency/event SQL remains authoritative. */
final class PlayerBotPopulationFocus {
 record Anchor(int map,float x,float y,float z) {}
 record Snapshot(List<Anchor> anchors,int radius) {
  Snapshot {
   anchors=List.copyOf(anchors);
   if(radius<200 || radius>5000)throw new IllegalArgumentException("Invalid player-area population configuration");
  }
  // Preserve the previously installed helper signatures; the old quota has no effect.
  Snapshot(List<Anchor> anchors,int ignoredPerMap,int radius){this(anchors,radius);}
  public int perMap(){return 0;}
  boolean active(){return !anchors.isEmpty();}
  Set<Integer> maps(){var maps=new TreeSet<Integer>();anchors.forEach(a->maps.add(a.map()));return Set.copyOf(maps);}
  // Player locations distribute the configured population; they never reduce it.
  int target(int ceiling){return ceiling;}
  int mapTarget(int map,int ceiling) {
   var maps=new TreeSet<>(maps());if(!maps.contains(map))return 0;
   int total=target(ceiling),base=total/maps.size();
   return base+(maps.headSet(map).size()<total%maps.size()?1:0);
  }
  Snapshot available(Map<Integer,Integer> counts,int ceiling) {
   return new Snapshot(anchors.stream().filter(a->counts.getOrDefault(a.map(),0)<mapTarget(a.map(),ceiling)).toList(),radius);
  }
  boolean contains(int map){return anchors.stream().anyMatch(a->a.map()==map);}
  double distance(int map,float x,float y,float z){return anchors.stream().filter(a->a.map()==map)
   .mapToDouble(a->Math.sqrt(Math.pow(a.x()-x,2)+Math.pow(a.y()-y,2)+Math.pow(a.z()-z,2))).min().orElse(Double.POSITIVE_INFINITY);}
  boolean nearby(int map,float x,float y,float z){return distance(map,x,y,z)<=radius;}
  List<PlayerBotWorldPlacement.Location> prefer(List<PlayerBotWorldPlacement.Location> choices,Map<Integer,Integer> counts) {
   if(!active())return choices;
   var local=new ArrayList<>(choices.stream().filter(p->contains(p.map())).toList());
   // Preserve shuffled camp spreading within the same demand/distance tier.
   local.sort(Comparator.comparingInt((PlayerBotWorldPlacement.Location p)->counts.getOrDefault(p.map(),0))
    .thenComparingInt(p->nearby(p.map(),p.x(),p.y(),p.z())?0:1)
    .thenComparingDouble(p->nearby(p.map(),p.x(),p.y(),p.z())?0:distance(p.map(),p.x(),p.y(),p.z())));
   return List.copyOf(local);
  }
 }
 static Snapshot capture(Collection<Player> players) {
  var anchors=new ArrayList<Anchor>();
  if(PlayerBotConfig.POPULATION_PLAYER_FOCUS && PlayerBotConfig.POPULATION_ENABLED && PlayerBotConfig.WORLD_ENABLED && PlayerBotConfig.ENABLED)for(var p:players) {
   if(p!=null && PlayerBotPlayerZones.eligible(p.isPlayerBot(),p.isOnline(),p.isSpawned(),
    p.isStaff() && p.isInVisualState(CreatureVisualState.HIDE20),p.getInstanceId(),DataManager.WORLD_MAPS_DATA.getTemplate(p.getWorldId())))
    anchors.add(new Anchor(p.getWorldId(),p.getX(),p.getY(),p.getZ()));
  }
  return new Snapshot(anchors,PlayerBotConfig.POPULATION_FOCUS_RADIUS);
 }
 static boolean protectedActor(boolean autonomous,boolean closing,boolean idle,boolean nearHuman,boolean seen) {
  return !autonomous || closing || !idle || nearHuman || seen;
 }
 // Legacy signature retained. The count is ALL admitted global residents, including off-map actors.
 static int slots(int target,int admitted,int residents,int cap,int budget) {
  return PlayerBotPopulationPolicy.admissionSlots(target,admitted,residents,cap,budget);
 }
 private PlayerBotPopulationFocus() {}
}
