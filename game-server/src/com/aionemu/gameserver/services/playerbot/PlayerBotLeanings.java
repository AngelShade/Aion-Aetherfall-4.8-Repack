package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * One lifelong leaning per generated world resident (PvP-1 and Phase 4). Picked once from
 * {@code pvp.leaning_weights} by a fixed per-character hash, saved in metadata section {@code leaning}
 * and never changed, like the saved trade and growth traits. Held in RAM after the resident loader.
 * Not the companions' {@link PlayerBotFocus}, which an owner chooses for a ticket recruit.
 */
public final class PlayerBotLeanings {
 public enum Leaning { QUESTER, HUNTER, TRADE, TRADER, PVP }
 /** Phase 4: the activity timers a leaning tilts. */
 enum Activity { HUNT, TRADE, MARKET }
 private static final Map<Leaning,long[]> DONE=new EnumMap<>(Leaning.class);
 static{for(var l:Leaning.values())DONE.put(l,new long[Activity.values().length]);}
 /** Multiplier for an activity's waiting time. Below level 10 everyone plays alike; a favoured activity comes round
  * focusPercent/100 as long (more often); a quester waits longer between gear hunts and so keeps questing. */
 static double pace(Leaning leaning,Activity activity,int level,int focusPercent) {
  if(leaning==null || level<10)return 1;
  double f=Math.max(10,Math.min(100,focusPercent))/100.0;
  return switch(activity) {
   case HUNT->leaning==Leaning.HUNTER?f:leaning==Leaning.QUESTER?1/f:1;
   case TRADE->leaning==Leaning.TRADE?f:1;
   case MARKET->leaning==Leaning.TRADER?f:1;
  };
 }
 static double pace(Player bot,Activity activity){return bot==null?1:pace(of(bot),activity,bot.getLevel(),PlayerBotConfig.PVP_LEANING_FOCUS_PERCENT);}
 /** Counts an activity a bot actually started, per leaning, for the report. */
 static void did(Player bot,Activity activity){var l=of(bot);if(l!=null)synchronized(DONE){DONE.get(l)[activity.ordinal()]++;}}
 private static final Map<Integer,Leaning> LEANINGS=new ConcurrentHashMap<>();

 /** name:weight pairs; every name once, weights 0..1000, at least one positive. */
 public static Map<Leaning,Integer> weights(String text) {
  var result=new EnumMap<Leaning,Integer>(Leaning.class);
  if(text==null || text.isBlank())throw new IllegalArgumentException("Playerbots pvp.properties: leaning_weights needs name:weight pairs");
  for(String pair:text.split(",")) {
   var parts=pair.trim().split(":");
   if(parts.length!=2)throw new IllegalArgumentException("Playerbots pvp.properties: leaning_weights entry '"+pair.trim()+"' is not name:weight");
   Leaning leaning;int weight;
   try{leaning=Leaning.valueOf(parts[0].trim());weight=Integer.parseInt(parts[1].trim());}
   catch(IllegalArgumentException e){throw new IllegalArgumentException("Playerbots pvp.properties: leaning_weights entry '"+pair.trim()+"' uses an unknown leaning or weight",e);}
   if(weight<0 || weight>1000 || result.put(leaning,weight)!=null)throw new IllegalArgumentException("Playerbots pvp.properties: leaning_weights lists "+leaning+" twice or outside 0..1000");
  }
  if(result.values().stream().mapToInt(Integer::intValue).sum()<=0)throw new IllegalArgumentException("Playerbots pvp.properties: leaning_weights needs a positive weight");
  return Collections.unmodifiableMap(result);
 }
 /** Stable for a character: the same hash and weights always give the same leaning. */
 static Leaning choose(Map<Leaning,Integer> weights,int character) {
  long h=character*0x9E3779B97F4A7C15L;h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>29;
  int total=weights.values().stream().mapToInt(Integer::intValue).sum(),roll=(int)Math.floorMod(h,(long)total);
  for(var e:weights.entrySet()){roll-=e.getValue();if(roll<0)return e.getKey();}
  throw new IllegalStateException("Leaning weights changed during selection");
 }
 public static void validate(Map<String,String> values) {
  if(!values.keySet().equals(Set.of("version","account","character","leaning")) || !"1".equals(values.get("version")))
   throw new IllegalArgumentException("Invalid leaning fields/version");
  Leaning.valueOf(values.get("leaning"));
 }
 static void validateUpdate(Map<String,String> before,Map<String,String> after) {
  if(!before.isEmpty() && !Objects.equals(before.get("leaning"),after.get("leaning")))throw new IllegalArgumentException("Saved world leaning is immutable");
 }
 static boolean eligible(PlayerBotSession session) {
  var bot=session.bot();return session.autonomous() && bot.isWorldBot() && bot.getAccount()!=null && PlayerBotPopulationManager.global(bot);
 }
 /** Runs at the resident loader beside road duty and trades. Bad saved data means no leaning this run. */
 static void initialize(PlayerBotSession session) {
  var bot=session.bot();int id=bot.getObjectId();
  try {
   if(!eligible(session) || LEANINGS.containsKey(id))return;
   var saved=PlayerBotMetadata.load(bot.getAccount().getId(),id,"leaning",null);
   Leaning leaning;
   if(saved.isEmpty()) {
    leaning=choose(weights(PlayerBotConfig.PVP_LEANING_WEIGHTS),id);
    var p=new Properties();p.setProperty("version","1");p.setProperty("account",Integer.toString(bot.getAccount().getId()));
    p.setProperty("character",Integer.toString(id));p.setProperty("leaning",leaning.name());
    PlayerBotMetadata.save(bot.getAccount().getId(),id,"leaning",p);
   } else leaning=Leaning.valueOf(saved.getProperty("leaning"));
   LEANINGS.put(id,leaning);
  }catch(java.io.IOException|RuntimeException error){org.slf4j.LoggerFactory.getLogger(PlayerBotLeanings.class).warn("World bot {} has no leaning this run",id,error);}
 }
 static Leaning of(Player bot){return bot==null?null:LEANINGS.get(bot.getObjectId());}
 /** A PVP leaning counts from {@code pvp.leaning_min_level}; below it the bot plays like any other. */
 static boolean pvp(Player bot){return PlayerBotConfig.PVP_ENABLED && of(bot)==Leaning.PVP && bot.getLevel()>=PlayerBotConfig.PVP_LEANING_MIN_LEVEL;}
 static void closed(int bot){LEANINGS.remove(bot);}
 static String stats() {
  var counts=new EnumMap<Leaning,Integer>(Leaning.class);for(var l:Leaning.values())counts.put(l,0);
  for(var l:LEANINGS.values())counts.merge(l,1,Integer::sum);
  return "leanings "+counts;
 }
 /** Per leaning: bots, then hunts / trade turns / market visits started since start. */
 static String activity() {
  var counts=new EnumMap<Leaning,Integer>(Leaning.class);for(var l:LEANINGS.values())counts.merge(l,1,Integer::sum);
  var out=new StringBuilder("focus "+PlayerBotConfig.PVP_LEANING_FOCUS_PERCENT+"%;");
  synchronized(DONE){for(var l:Leaning.values()){var d=DONE.get(l);out.append(' ').append(l).append(' ').append(counts.getOrDefault(l,0)).append(" bots hunts/trade/market=").append(d[0]).append('/').append(d[1]).append('/').append(d[2]).append(';');}}
  return out.toString();
 }
 private PlayerBotLeanings(){}
}
