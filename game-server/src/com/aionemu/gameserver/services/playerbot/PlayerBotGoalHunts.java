package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.LootCodexService;

/** Hunt-time projection of saved item goals; the saved source and equipment UI stay unchanged. */
final class PlayerBotGoalHunts {
 enum Fit { SOLO, NO_MONSTER_SOURCE, ONLY_INSTANCES, NO_WORLD_PLACE, ABOVE_SOLO_LEVEL, NO_XP, ELITE_ABOVE_LEVEL, NOT_HOSTILE, UNSUPPORTED }
 record Candidates(List<Integer> solo,List<Integer> help,Set<Fit> rejected) {}
 private record Key(int item,Race race) {}
 private static final Map<Key,List<Integer>> SOURCES=new ConcurrentHashMap<>();

 static Map<Integer,List<Integer>> sources(Collection<String> goals,Race race) {
  if(goals.isEmpty() || !LootCodexService.warm())return Map.of();
  var result=new LinkedHashMap<Integer,List<Integer>>();
  for(String goal:goals) {
   int item=Integer.parseInt(goal.substring(0,goal.indexOf(':')));
   result.computeIfAbsent(item,id->SOURCES.computeIfAbsent(new Key(id,race),key->LootCodexService.acquisitions(key.item(),key.race()).stream()
    .filter(source->source.kind().equals("npc")).map(LootCodexService.Acquisition::id).distinct().toList()));
  }
  return result;
 }

 static Candidates candidates(Player bot) {return candidates(PlayerBotGearPolicy.goalSources(bot),id->fit(bot,id));}
 /** A hard source needs help only when that item has no source the bot can solo now. */
 static Candidates candidates(Map<Integer,List<Integer>> items,IntFunction<Fit> classify) {
  var solo=new LinkedHashSet<Integer>();var help=new LinkedHashSet<Integer>();var rejected=EnumSet.noneOf(Fit.class);
  var classified=new HashMap<Integer,Fit>();boolean any=false;
  for(var sources:items.values()) {
   var itemSolo=new ArrayList<Integer>();var itemHelp=new ArrayList<Integer>();
   for(int npc:sources) {
    any=true;var fit=classified.computeIfAbsent(npc,classify::apply);
    if(fit==Fit.SOLO)itemSolo.add(npc);
    // Anything too hard to solo may still be worth a group: elites above level, hero/legendary monsters and
    // ones well above the bot. PlayerBotGroupNeeds.gear keeps only hard, hostile world monsters on the bot's map.
    else {rejected.add(fit);if(fit==Fit.ELITE_ABOVE_LEVEL || fit==Fit.ABOVE_SOLO_LEVEL || fit==Fit.UNSUPPORTED)itemHelp.add(npc);}
   }
   solo.addAll(itemSolo);if(itemSolo.isEmpty())help.addAll(itemHelp);
  }
  if(!any)rejected.add(Fit.NO_MONSTER_SOURCE);
  return new Candidates(List.copyOf(solo),List.copyOf(help),Set.copyOf(rejected));
 }

 static Fit fit(Player bot,int npc) {
  var target=DataManager.NPC_DATA.getNpcTemplate(npc);
  if(!PlayerBotHunting.goalTemplate(target))return Fit.UNSUPPORTED;
  // places() is the cached native permanent ground-spawn catalogue and excludes instances.
  if(PlayerBotWorldTravel.places(npc).isEmpty())return instanceSource(npc)?Fit.ONLY_INSTANCES:Fit.NO_WORLD_PLACE;
  if(target.getLevel()>bot.getLevel()+4)return Fit.ABOVE_SOLO_LEVEL;
  if(!PlayerBotWorldActivities.levelSuitable(bot.getLevel(),target.getLevel()))return Fit.NO_XP;
  if(!DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(target.getTribe(),bot.getTribe())
   && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(target.getTribe(),bot.getTribe()))return Fit.NOT_HOSTILE;
  return PlayerBotHunting.goalLevelFits(bot.getLevel(),target.getLevel(),target.getRating())?Fit.SOLO:Fit.ELITE_ABOVE_LEVEL;
 }

 private record SpawnSource(Object maps,Object spawns,int npc) {}
 private static final Map<SpawnSource,Boolean> INSTANCES=new ConcurrentHashMap<>();
 private static boolean instanceSource(int npc) {
  return INSTANCES.computeIfAbsent(new SpawnSource(DataManager.WORLD_MAPS_DATA,DataManager.SPAWNS_DATA,npc),key->{
   for(var map:DataManager.WORLD_MAPS_DATA)if(map.isInstance() && !DataManager.SPAWNS_DATA.getSpawnsForNpc(map.getMapId(),npc).isEmpty())return true;
   return false;
  });
 }
 private PlayerBotGoalHunts() {}
}
