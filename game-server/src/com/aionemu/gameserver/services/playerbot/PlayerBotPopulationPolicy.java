/* Population target/faction/level decisions from RandomPlayerbotMgr::AddRandomBots /
 * RandomizeFirst, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.random.RandomGenerator;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.Race;

/** Upstream probabilities and admission budgets, independent of native actor creation. */
final class PlayerBotPopulationPolicy {
 static final int MAX_TARGET=3000;
 static final int MAX_ADMISSION_BUDGET=120;
 static final int MAX_RESERVE=MAX_TARGET*4;
 static final int ACCOUNT_SHARDS=MAX_RESERVE; // Capacity=1 and a single faction must also fit the full reserve.
 record Settings(int minimum,int maximum,int budget,int elyosPercent,int minLevel,int maxLevel,
                 int minChance,int maxChance,boolean randomLevels,int startingLevel,boolean syncLevels,
                 boolean periodic,int presenceMin,int presenceMax,int offlineMin,int offlineMax,
                 int targetMin,int targetMax,int teleportMin,int teleportMax,int cityChance) {
  Settings {
   if(minimum<0 || maximum<minimum || maximum>MAX_TARGET || budget<1 || budget>MAX_ADMISSION_BUDGET || elyosPercent<0 || elyosPercent>100
    || minLevel<1 || maxLevel<minLevel || maxLevel>65 || minChance<0 || maxChance<0 || minChance>100 || maxChance>100 || minChance+maxChance>100
    || startingLevel<1 || startingLevel>65 || presenceMin<30 || presenceMax<presenceMin
    || offlineMin<30 || offlineMax<offlineMin || targetMin<30 || targetMax<targetMin
    || teleportMin<30 || teleportMax<teleportMin || cityChance<0 || cityChance>100)
    throw new IllegalArgumentException("Invalid server population settings; no population reset performed");
  }
 }
 static Settings settings(){return new Settings(PlayerBotConfig.POPULATION_MIN,PlayerBotConfig.POPULATION_MAX,
  PlayerBotConfig.POPULATION_PER_INTERVAL,PlayerBotConfig.POPULATION_ELYOS_PERCENT,PlayerBotConfig.POPULATION_MIN_LEVEL,
  PlayerBotConfig.POPULATION_MAX_LEVEL,PlayerBotConfig.POPULATION_MIN_LEVEL_PERCENT,PlayerBotConfig.POPULATION_MAX_LEVEL_PERCENT,
  PlayerBotConfig.POPULATION_RANDOM_LEVELS,PlayerBotConfig.POPULATION_STARTING_LEVEL,PlayerBotConfig.POPULATION_SYNC_LEVELS,
  PlayerBotConfig.POPULATION_PERIODIC_PRESENCE,PlayerBotConfig.POPULATION_PRESENCE_MIN,PlayerBotConfig.POPULATION_PRESENCE_MAX,
  PlayerBotConfig.POPULATION_OFFLINE_MIN,PlayerBotConfig.POPULATION_OFFLINE_MAX,PlayerBotConfig.POPULATION_TARGET_MIN,
  PlayerBotConfig.POPULATION_TARGET_MAX,PlayerBotConfig.POPULATION_TELEPORT_MIN,PlayerBotConfig.POPULATION_TELEPORT_MAX,
  PlayerBotConfig.POPULATION_CITY_PERCENT);}
 static int between(RandomGenerator rng,int minimum,int maximum){return rng.nextInt(minimum,maximum+1);}
 static int target(int requested,int worldLimit,int manualResidents){return Math.max(0,Math.min(requested,worldLimit-Math.max(0,manualResidents)));}
 // RandomPlayerbotMgr::AddRandomBots counts the complete admitted set, never a geographic subset.
 static int admissionSlots(int target,int admitted,int residents,int cap,int budget) {
  return Math.max(0,Math.min(budget,Math.min(target-admitted,cap-residents)));
 }
 // UpdateAIInternal also bounds native logins against the complete online set.
 // Closing/save-held sessions still occupy a slot until their lease is released.
 static boolean mayEnter(boolean global,boolean enabled,int target,int activeGlobal,int activeWorld,int cap) {
  return activeWorld<cap && (!global || enabled && activeGlobal<target);
 }
 static int creationDeficit(int slots,int admitted,int registered,int maximum){
  // Timed logout, failed admission and restart never erase a registered identity.
  return Math.max(0,Math.min(slots-admitted,maximum-registered));
 }
 static Integer nextLoad(Collection<Integer> residents,Set<Integer> active,Set<Integer> retiring,Map<Integer,Long> retries,long now){
  return residents.stream().filter(id->!active.contains(id) && !retiring.contains(id) && retries.getOrDefault(id,0L)<=now).findFirst().orElse(null);
 }
 static long deadline(long now,RandomGenerator rng,int minimum,int maximum){return Math.addExact(now,1000L*between(rng,minimum,maximum));}
 static int level(Settings s,int maxPlayerLevel,RandomGenerator rng) {
  if(!s.randomLevels())return s.startingLevel();
  int max=s.syncLevels()?Math.max(s.minLevel(),Math.min(s.maxLevel(),maxPlayerLevel+3)):s.maxLevel();
  int roll=rng.nextInt(100);
  if(roll<s.maxChance())return max;
  if(roll<s.maxChance()+s.minChance())return s.minLevel();
  return between(rng,s.minLevel(),max);
 }
 record Candidate(int character,Race race) {Candidate{if(character<=0 || race!=Race.ELYOS && race!=Race.ASMODIANS)throw new IllegalArgumentException("Invalid candidate");}}
 static List<Integer> admit(List<Candidate> candidates,int slots,int percent,RandomGenerator rng) {
  if(slots<=0)return List.of();if(percent<0 || percent>100)throw new IllegalArgumentException("Faction ratio");
  var shuffled=new ArrayList<>(candidates);Collections.shuffle(shuffled,new Random(rng.nextLong()));
  int elyos=slots*percent/100;int remainder=slots*percent%100;if(rng.nextInt(100)<remainder)elyos++;
  var result=new ArrayList<Integer>();
  for(var c:shuffled)if(c.race()==Race.ELYOS && elyos>0){result.add(c.character());elyos--;}
  for(var c:shuffled)if(c.race()==Race.ASMODIANS && result.size()<slots)result.add(c.character());
  for(var c:shuffled)if(c.race()==Race.ELYOS && result.size()<slots && !result.contains(c.character()))result.add(c.character());
  return List.copyOf(result);
 }
 /** Signed native account columns permit a disjoint headless namespace. LS auto-increment IDs are positive. */
 static boolean serverAccount(int id){return id<=-1000000 && id>=-1000000-2*ACCOUNT_SHARDS+1;}
 static boolean account(int id){return id>0 || serverAccount(id);}
 static int account(Race race,int shard){if(race!=Race.ELYOS && race!=Race.ASMODIANS || shard<0 || shard>=ACCOUNT_SHARDS)throw new IllegalArgumentException("Population account limit");return -1000000-2*shard-(race==Race.ELYOS?0:1);}
 private PlayerBotPopulationPolicy(){}
}
