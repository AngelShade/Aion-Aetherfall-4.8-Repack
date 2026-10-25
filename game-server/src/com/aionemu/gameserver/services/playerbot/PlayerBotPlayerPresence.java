package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;

/** Optional local demand alongside the pinned worldwide manager. No extra identities or saved state.
 * Human-zone filtering follows RandomPlayerbotMgr::GetPlayerZoneTeleportLocations,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c (GPL-2.0-or-later).
 * Maintaining nearby residency is the owner's Aion extension, not an upstream parity claim. */
final class PlayerBotPlayerPresence {
 record Position(int map,float x,float y,float z) {}
 record Snapshot(List<PlayerBotPopulationFocus.Anchor> anchors,int target,int radius) {
  Snapshot {
   anchors=List.copyOf(anchors);
   if(target<1 || target>120 || radius<200 || radius>1500)
    throw new IllegalArgumentException("Player presence requires 1..120 residents per area and a 200..1500m radius");
  }
  int count(PlayerBotPopulationFocus.Anchor area,Collection<Position> positions) {
   int count=0;for(var p:positions)if(near(area,p.map(),p.x(),p.y(),p.z(),radius))count++;return count;
  }
  boolean nearby(int map,float x,float y,float z) {
   return anchors.stream().anyMatch(a->near(a,map,x,y,z,radius));
  }
  PlayerBotPopulationFocus.Snapshot demand(Collection<Position> positions) {
   return demand(positions,a->true);
  }
  PlayerBotPopulationFocus.Snapshot demand(Collection<Position> positions,java.util.function.Predicate<PlayerBotPopulationFocus.Anchor> compatible) {
   int least=target;var wanted=new ArrayList<PlayerBotPopulationFocus.Anchor>();
   for(var area:anchors) {
    if(!compatible.test(area))continue;
    int count=count(area,positions);if(count>=target || count>least)continue;
    if(count<least){least=count;wanted.clear();}wanted.add(area);
   }
   return new PlayerBotPopulationFocus.Snapshot(wanted,radius);
  }
 }
 private static final Snapshot EMPTY=new Snapshot(List.of(),40,600);
 private static volatile Snapshot observed=EMPTY;
 static boolean enabled() {
  return PlayerBotConfig.POPULATION_PLAYER_PRESENCE && PlayerBotConfig.ENABLED
   && PlayerBotConfig.WORLD_ENABLED && PlayerBotConfig.POPULATION_ENABLED;
 }
 static Snapshot snapshot(){return enabled()?observed:EMPTY;}
 static boolean near(PlayerBotPopulationFocus.Anchor a,int map,float x,float y,float z,int radius) {
  return a.map()==map && Math.pow(a.x()-x,2)+Math.pow(a.y()-y,2)+Math.pow(a.z()-z,2)<=Math.pow(radius,2);
 }
 static void refresh(Collection<Player> players) {
  if(!enabled()){observed=EMPTY;return;}
  var anchors=new ArrayList<PlayerBotPopulationFocus.Anchor>();
  for(var p:players)if(p!=null && PlayerBotPlayerZones.eligible(p.isPlayerBot(),p.isOnline(),p.isSpawned(),
   p.isStaff() && p.isInVisualState(CreatureVisualState.HIDE20),p.getInstanceId(),DataManager.WORLD_MAPS_DATA.getTemplate(p.getWorldId())))
   anchors.add(new PlayerBotPopulationFocus.Anchor(p.getWorldId(),p.getX(),p.getY(),p.getZ()));
  // Actual human areas only. No synthetic opposing-faction demand.
  observed=new Snapshot(anchors,PlayerBotConfig.POPULATION_PRESENCE_PER_AREA,PlayerBotConfig.POPULATION_PRESENCE_RADIUS);
 }
 static boolean global(Player p) {
  return p.isWorldBot() && p.getPlayerBotOwnerId()==0 && PlayerBotPopulationPolicy.serverAccount(p.getAccount().getId());
 }
 static List<Position> positions(Collection<Player> players) {
  var positions=new ArrayList<Position>();
  for(var p:players)if(global(p) && p.isSpawned() && !p.isDead() && p.getInstanceId()==1)
   positions.add(new Position(p.getWorldId(),p.getX(),p.getY(),p.getZ()));
  return positions;
 }
 static PlayerBotPopulationFocus.Snapshot demand(Collection<Player> players){return snapshot().demand(positions(players));}
 static PlayerBotPopulationFocus.Snapshot demand(Collection<Player> players,Race race,int level) {
  var state=snapshot();
  return state.demand(positions(players),a->PlayerBotWorldPlacement.presenceCompatible(race,level,a,state.radius()));
 }
 static boolean resident(Player bot) {
  return enabled() && global(bot) && bot.isSpawned() && bot.getInstanceId()==1
   && snapshot().nearby(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ());
 }
 static String summary(Collection<Player> players) {
  if(!enabled())return "Player presence: disabled; worldwide population policy unchanged";
  var state=snapshot();var positions=positions(players);var areas=new ArrayList<String>();
  for(var a:state.anchors())areas.add(a.map()+"@"+(int)a.x()+","+(int)a.y()+":"+state.count(a,positions)+"/"+state.target()
   +" ("+positions.stream().filter(p->near(a,p.map(),p.x(),p.y(),p.z(),150)).count()+" within 150m)");
  return "Player presence: existing global residents within "+state.radius()+"m of actual players="+areas+"; no extra population or mirrored quest activity";
 }
 private PlayerBotPlayerPresence(){}
}
