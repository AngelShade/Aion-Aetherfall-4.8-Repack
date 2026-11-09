/* PlayerbotAI::AllowActive (force-active radius, BotActiveAlone rotation, SmartScale),
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Aion keeps map dormancy as the far tier; the near tier and the per-human cap are the owner's extension. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Phase 2: level of detail inside an occupied map.
 * SEEN: a human knows the bot, or it is among the nearest of the 95..200 m buffer. FORCED: combat, death, a
 * native task, a human's party, instance or legion. ROTATING: a small hashed share of the rest, shrunk under lag.
 * NEAR: everyone else on the map; same activities with fewer idle decisions and no upgrade-hunt search.
 * FAR: map dormancy, unchanged. Only out-of-sight bots ever change behaviour.
 */
final class PlayerBotDetailTier {
 enum Tier {
  SEEN,FORCED,ROTATING,NEAR,FAR;
  boolean full(){return this==SEEN || this==FORCED || this==ROTATING;}
 }
 record Bot(int id,long zone,float x,float y,float z) {}
 record Human(long zone,float x,float y,float z,Set<Integer> known) {}
 record Selection(Set<Integer> visible,int capped) {}

 /** A tier is cached this long plus a per-bot stagger. Upstream re-checks every ~5 s; 2 s here so a bot a human walks toward wakes sooner. */
 static final long RECHECK_MS=2000;
 private static volatile Selection selection=new Selection(Set.of(),0);
 private static volatile Set<String> legionZones=Set.of();
 private static final Set<Integer> NEAR_NOW=ConcurrentHashMap.newKeySet();
 private static final Map<Tier,AtomicLong> DECISIONS=new EnumMap<>(Tier.class);
 static{for(var t:Tier.values())DECISIONS.put(t,new AtomicLong());}
 private static final AtomicLong DEFERRED_UPGRADES=new AtomicLong();
 private static long lagWindowStart,lagSum,lagCount;
 private static volatile long lag;

 static boolean enabled(){return PlayerBotConfig.WORLD_DETAIL_TIERS;}
 static int enterRadius(){return Math.max(120,Math.min(400,PlayerBotConfig.WORLD_DETAIL_VISIBLE_RADIUS));}
 static int leaveRadius(){return Math.max(enterRadius(),Math.min(500,PlayerBotConfig.WORLD_DETAIL_VISIBLE_LEAVE_RADIUS));}
 static int cap(){return Math.max(0,Math.min(120,PlayerBotConfig.WORLD_DETAIL_VISIBLE_PER_HUMAN));}

 /** Humans' known bots are always visible; the out-of-view buffer keeps the nearest {@code cap}, favouring bots already in it. */
 static Selection select(List<Human> humans,List<Bot> bots,Set<Integer> previous,int enter,int leave,int cap) {
  var visible=new HashSet<Integer>();int capped=0;
  var byZone=new HashMap<Long,List<Bot>>();for(var b:bots)byZone.computeIfAbsent(b.zone(),z->new ArrayList<>()).add(b);
  for(var h:humans) {
   visible.addAll(h.known());
   record Close(int id,double score) {}
   var close=new ArrayList<Close>();
   for(var b:byZone.getOrDefault(h.zone(),List.of())) {
    if(h.known().contains(b.id()))continue;
    double d=Math.sqrt(Math.pow(b.x()-h.x(),2)+Math.pow(b.y()-h.y(),2)+Math.pow(b.z()-h.z(),2));
    boolean was=previous.contains(b.id());
    if(d<=enter || was && d<=leave)close.add(new Close(b.id(),was?d-(leave-enter):d));
   }
   close.sort(Comparator.comparingDouble(Close::score).thenComparingInt(Close::id));
   for(int i=0;i<close.size();i++)if(i<cap)visible.add(close.get(i).id());else if(!visible.contains(close.get(i).id()))capped++;
  }
  return new Selection(Set.copyOf(visible),capped);
 }
 static void observe(List<Human> humans,List<Bot> bots,Set<String> legions) {
  if(!enabled()){selection=new Selection(Set.of(),0);legionZones=Set.of();return;}
  selection=select(humans,bots,selection.visible(),enterRadius(),leaveRadius(),cap());
  legionZones=Set.copyOf(legions);
 }
 static String legionZone(int legion,int map,int instance){return legion+"@"+map+"/"+instance;}
 static boolean humanLegionHere(Player bot) {
  var legion=bot.getLegion();
  return PlayerBotConfig.WORLD_DETAIL_LEGION_FORCE && legion!=null && legionZones.contains(legionZone(legion.getLegionId(),bot.getWorldId(),bot.getInstanceId()));
 }

 /** A fixed per-bot hash picks the active share; the set changes every rotation window. */
 static boolean rotating(int id,long now,int percent,int seconds) {
  if(percent<=0)return false;if(percent>=100)return true;
  long h=id*0x9E3779B97F4A7C15L ^ (now/(Math.max(5,seconds)*1000L))*0xC2B2AE3D27D4EB4FL;
  h^=h>>>31;h*=0x94D049BB133111EBL;h^=h>>>29;
  return Math.floorMod(h,100)<percent;
 }
 /** SmartScale: full share at or below the floor, none at or above the ceiling, linear in between. */
 static int share(int percent,long lagMs,int floor,int ceiling) {
  if(ceiling<=floor)return lagMs<=floor?percent:0;
  if(lagMs<=floor)return percent;if(lagMs>=ceiling)return 0;
  return (int)(percent*(ceiling-lagMs)/(double)(ceiling-floor));
 }
 static int share(){return share(Math.max(0,Math.min(100,PlayerBotConfig.WORLD_DETAIL_ROTATION_PERCENT)),lag,
  PlayerBotConfig.WORLD_DETAIL_SMARTSCALE_FLOOR_MS,PlayerBotConfig.WORLD_DETAIL_SMARTSCALE_CEILING_MS);}

 /** Tiers judge only an autonomous world bot with a settled position; anything else keeps native pacing. */
 static boolean applies(boolean enabled,boolean autonomous,boolean spawned,boolean positioned){return enabled && autonomous && spawned && positioned;}
 /** The cheap visibility answer comes first; the force checks run only for bots no human can see. */
 static Tier classify(boolean awake,boolean enabled,boolean controlled,java.util.function.BooleanSupplier forced,boolean seen,boolean rotating) {
  if(!awake)return Tier.FAR;
  if(!enabled || controlled)return Tier.FORCED;
  if(seen)return Tier.SEEN;
  if(forced.getAsBoolean())return Tier.FORCED;
  return rotating?Tier.ROTATING:Tier.NEAR;
 }
 static Tier of(PlayerBotSession session,java.util.function.BooleanSupplier forced,long now) {
  var bot=session.bot();int id=bot.getObjectId();
  var tier=classify(PlayerBotWorldActivity.allowed(session),enabled(),!session.autonomous(),forced,
   selection.visible().contains(id),rotating(id,now,share(),PlayerBotConfig.WORLD_DETAIL_ROTATION_SECONDS));
  if(tier==Tier.NEAR)NEAR_NOW.add(id);else NEAR_NOW.remove(id);
  return tier;
 }
 static boolean near(int bot){return NEAR_NOW.contains(bot);}
 static void closed(int bot){NEAR_NOW.remove(bot);}

 /** Out of sight, an idle bot thinks a few times less often and a walking one checks its path less often. */
 static long nearReaction(long ordinary,boolean moving,int multiplier,int movingMs) {
  long slowed=ordinary*Math.max(1,Math.min(10,multiplier));
  return moving?Math.min(slowed,Math.max(200,Math.min(2000,movingMs))):Math.min(slowed,20000);
 }
 static long nearReaction(long ordinary,boolean moving){return nearReaction(ordinary,moving,PlayerBotConfig.WORLD_DETAIL_NEAR_MULTIPLIER,PlayerBotConfig.WORLD_DETAIL_NEAR_MOVING_MS);}

 static void decided(Tier tier){DECISIONS.get(tier).incrementAndGet();}
 static void deferredUpgrade(){DEFERRED_UPGRADES.incrementAndGet();}
 /** Average decision admission lateness over 5 s windows; the last full window drives SmartScale. */
 static synchronized void late(long ms,long now) {
  if(ms<0 || ms>60000)return;
  if(now-lagWindowStart>=5000){if(lagCount>0)lag=lagSum/lagCount;else if(now-lagWindowStart>=10000)lag=0;lagWindowStart=now;lagSum=lagCount=0;}
  lagSum+=ms;lagCount++;
 }
 static long lag(){return lag;}
 static synchronized void resetLag(){lag=lagSum=lagCount=lagWindowStart=0;}

 static String stats(Collection<PlayerBotSession> sessions) {
  var now=new EnumMap<Tier,Integer>(Tier.class);for(var t:Tier.values())now.put(t,0);
  for(var s:sessions)if(s.autonomous() && !s.closing()){var t=s.dormant()?Tier.FAR:s.cachedDetail();if(t!=null)now.merge(t,1,Integer::sum);}
  var made=new StringBuilder();for(var t:Tier.values())made.append(made.isEmpty()?"":"/").append(DECISIONS.get(t).get());
  return "enabled="+enabled()+", now seen="+now.get(Tier.SEEN)+" forced="+now.get(Tier.FORCED)+" rotating="+now.get(Tier.ROTATING)
   +" near="+now.get(Tier.NEAR)+" far="+now.get(Tier.FAR)+", buffer over cap="+selection.capped()
   +", rotation share="+share()+"% at decision lag "+lag+"ms, decisions seen/forced/rotating/near/far="+made
   +", upgrade searches deferred out of sight="+DEFERRED_UPGRADES.get();
 }
 private PlayerBotDetailTier(){}
}
