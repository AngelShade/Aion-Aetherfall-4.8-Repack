package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.CentralMarketService;
import com.aionemu.gameserver.services.ItemEligibility;

/**
 * Phase 3: gear a bot can wear now that Central Market may sell it. Until now bots only shopped for their
 * stage-end goal, usually above their level, so on 8 Nov they had bought 16 gear items in total and sold 2,527.
 * Admission and gain are the ones upgrade hunts and equipping use (PlayerBotLoadouts.listed/fits,
 * PlayerBotGearPolicy.eligible/allowedWeapon/gain), so whatever is bought is worn, never resold as surplus.
 * This only nominates items. PlayerBotMarket prices, budgets and buys them through the normal market rules.
 */
final class PlayerBotMarketUpgrades {
 record Pick(int item,long slot,double gain) {}
 /** Templates more than this many levels below the bot are almost never an upgrade, so they are not scored. */
 static final int LEVEL_SPAN=9;
 static final long CACHE_MS=30*60*1000L;
 /** Best items kept per slot, so an unlisted favourite does not hide the next one. */
 static final int PER_SLOT=3;
 /** id identifies one build: cache keys compare it, never the catalogue contents. */
 private record Catalogue(long id,Object source,NavigableMap<Integer,List<ItemTemplate>> byLevel,int items,long nanos) {}
 private record Inputs(long catalogue,int level,PlayerBotLoadouts.Key key,PlayerBotGearPolicy.Settings settings,long pinned,List<Long> worn) {}
 private record Cached(Inputs inputs,long until,List<Pick> picks) {}
 private static volatile Catalogue catalogue;
 private static final AtomicBoolean WARMING=new AtomicBoolean();
 private static final AtomicLong BUILDS=new AtomicLong();
 /** An empty scan (market not started) is not kept; it is retried at most this often. */
 static final long EMPTY_RETRY_MS=10*60*1000L;
 private static volatile long retryAt;
 private static final Map<Integer,Cached> CACHE=new ConcurrentHashMap<>();
 private static final Map<Integer,Map<Integer,Long>> TRIED=new ConcurrentHashMap<>();
 private static final AtomicLong CHECKS=new AtomicLong(),COMPUTED=new AtomicLong(),COMPUTED_NANOS=new AtomicLong(),MAX=new AtomicLong();
 private static final AtomicLong QUOTED=new AtomicLong(),BOUGHT=new AtomicLong(),DORMANT_BOUGHT=new AtomicLong();
 /** Picks are computed on one low-priority worker, never on a bot's decision thread (measured 8 Nov: avg 19.6 ms,
  * worst 110 ms there). At most one queued computation per bot; the visit that queues it uses the previous picks. */
 private static final java.util.concurrent.ExecutorService PICKER=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
  var thread=new Thread(r,"PlayerbotMarketUpgrades");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;});
 private static final java.util.Set<Integer> QUEUED=ConcurrentHashMap.newKeySet();
 private static final AtomicLong QUEUED_TOTAL=new AtomicLong(),FAILED=new AtomicLong();

 static boolean enabled(){return com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.MARKET_UPGRADES;}
 static boolean market(ItemTemplate t) {
  return t!=null && PlayerBotCombatGear.usable(t) && (t.isWeapon() || t.isArmor()) && t.getItemSlot()!=0 && !ItemSlot.isStigma(t.getItemSlot())
   && t.getExpireTime()==0 && CentralMarketService.eligible(t) && ItemEligibility.isPlayerItem(t) && !ItemEligibility.isAppearanceOnly(t);
 }
 /** The template scan runs once on a low-priority worker, as the upgrade-hunt index does; a cold check returns nothing. */
 static boolean warm() {
  Object source=DataManager.ITEM_DATA;var ready=catalogue;
  if(ready!=null && ready.source()==source)return true;
  if(source!=null && System.currentTimeMillis()>=retryAt && WARMING.compareAndSet(false,true)) {
   var worker=new Thread(()->{
    try {
     long start=System.nanoTime();var byLevel=new TreeMap<Integer,List<ItemTemplate>>();int items=0;
     for(var t:DataManager.ITEM_DATA.getItemTemplates())if(market(t)){byLevel.computeIfAbsent(t.getLevel(),l->new ArrayList<>()).add(t);items++;}
     byLevel.replaceAll((level,list)->List.copyOf(list));
     if(items==0)retryAt=System.currentTimeMillis()+EMPTY_RETRY_MS;
     else if(source==DataManager.ITEM_DATA){catalogue=new Catalogue(BUILDS.incrementAndGet(),source,Collections.unmodifiableNavigableMap(byLevel),items,System.nanoTime()-start);CACHE.clear();}
    }catch(Exception e){retryAt=System.currentTimeMillis()+EMPTY_RETRY_MS;org.slf4j.LoggerFactory.getLogger(PlayerBotMarketUpgrades.class).warn("Market upgrade catalogue could not be prepared",e);}
    finally{WARMING.set(false);}
   },"PlayerBotMarketUpgrades");
   worker.setDaemon(true);worker.setPriority(Thread.MIN_PRIORITY);worker.start();
  }
  return false;
 }

 /** Best of every slot first, then each slot's second best, and so on; equal gains keep template order. */
 static List<Pick> spread(List<Pick> picks,int perSlot) {
  var bySlot=new TreeMap<Long,List<Pick>>();
  for(var p:picks)bySlot.computeIfAbsent(p.slot(),s->new ArrayList<>()).add(p);
  var rounds=new ArrayList<List<Pick>>();
  for(var slot:bySlot.values()) {
   slot.sort(Comparator.comparingDouble(Pick::gain).reversed().thenComparingInt(Pick::item));
   for(int i=0;i<Math.min(perSlot,slot.size());i++){while(rounds.size()<=i)rounds.add(new ArrayList<>());rounds.get(i).add(slot.get(i));}
  }
  var result=new ArrayList<Pick>();var seen=new HashSet<Integer>();
  for(var round:rounds) {
   round.sort(Comparator.comparingDouble(Pick::gain).reversed().thenComparingInt(Pick::item));
   for(var p:round)if(seen.add(p.item()))result.add(p);
  }
  return List.copyOf(result);
 }
 /** Every wearable upgrade of the bot's current gear, cached until level, gear, build or settings change. */
 static List<Pick> ranked(PlayerBotSession session) {
  if(!enabled() || !warm())return List.of();
  Player bot=session.bot();var ready=catalogue;var role=PlayerBotGearPolicy.role(bot,session.combatRole());var key=PlayerBotLoadouts.key(bot,role);
  var settings=PlayerBotGearPolicy.settings(bot);int level=bot.getLevel();long now=System.currentTimeMillis();
  var inputs=new Inputs(ready.id(),level,key,settings,PlayerBotGearPolicy.pinned(bot),PlayerBotUpgradeHunts.worn(bot));
  var cached=CACHE.get(bot.getObjectId());
  if(cached!=null && now<cached.until() && cached.inputs().equals(inputs))return cached.picks();
  int id=bot.getObjectId();
  if(QUEUED.add(id)) {
   QUEUED_TOTAL.incrementAndGet();
   try {
    PICKER.execute(()->{
     try{compute(bot,ready,role,key,settings,level,inputs);}
     catch(RuntimeException e){FAILED.incrementAndGet();} // gear changed mid-scan: the next visit queues it again
     finally{QUEUED.remove(id);}
    });
   }catch(java.util.concurrent.RejectedExecutionException e){QUEUED.remove(id);}
  }
  // Already held, ordered or worn items are filtered by picks(), so a previous result stays safe to use.
  return cached==null?List.of():cached.picks();
 }
 private static void compute(Player bot,Catalogue ready,PlayerBotRules.Role role,PlayerBotLoadouts.Key key,PlayerBotGearPolicy.Settings settings,int level,Inputs inputs) {
  long start=System.nanoTime();long now=System.currentTimeMillis();
  try {
   var weapon=PlayerBotLoadouts.weapon(key);var picks=new ArrayList<Pick>();
   for(var band:ready.byLevel().subMap(level-LEVEL_SPAN,true,level,true).values())for(var t:band) {
    if(!PlayerBotLoadouts.listed(t,key,level) || !PlayerBotGearPolicy.eligible(bot,t) || !PlayerBotGearPolicy.allowedWeapon(t,settings,role))continue;
    double score=PlayerBotGearPolicy.score(bot,t,role,key.build()),best=-Double.MAX_VALUE;long bestSlot=0;
    for(var slot:PlayerBotTemporary.GEAR)if(PlayerBotLoadouts.fits(t,slot,key,weapon)) {
     long mask=t.isTwoHandWeapon()?ItemSlot.MAIN_OR_SUB.getSlotIdMask():slot.getSlotIdMask();
     double gain=PlayerBotGearPolicy.gain(bot,t,score,mask,role);if(gain>best){best=gain;bestSlot=slot.getSlotIdMask();}
    }
    // Same threshold as PlayerBotGearPolicy.upgrades: anything bought here is put on when the bot next decides awake.
    if(best>1)picks.add(new Pick(t.getTemplateId(),bestSlot,best));
   }
   var result=spread(picks,PER_SLOT);
   if(CACHE.size()>8192)CACHE.clear();
   CACHE.put(bot.getObjectId(),new Cached(inputs,now+CACHE_MS,result));
  } finally {
   long spent=System.nanoTime()-start;CHECKS.incrementAndGet();MAX.accumulateAndGet(spent,Math::max);
   COMPUTED.incrementAndGet();COMPUTED_NANOS.addAndGet(spent);
  }
 }
 static boolean fresh(Map<Integer,Long> tried,int item,long now,long retryMs){Long at=tried.get(item);return at==null || now-at>=retryMs;}
 static long retryMs(){return Math.max(1,com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.MARKET_UPGRADE_RETRY_HOURS)*3600000L;}
 /** Up to {@code limit} upgrades not already held, ordered or tried within the retry window. */
 static List<Integer> picks(PlayerBotSession session,Set<Integer> skip,int limit,long now) {
  if(limit<=0)return List.of();
  Player bot=session.bot();var tried=TRIED.getOrDefault(bot.getObjectId(),Map.of());long retry=retryMs();var result=new ArrayList<Integer>();
  for(var p:ranked(session)) {
   if(result.size()>=limit)break;
   if(skip.contains(p.item()) || !fresh(tried,p.item(),now,retry) || bot.getInventory().getItemCountByItemId(p.item())>0 || !bot.getEquipment().getEquippedItemsByItemId(p.item()).isEmpty())continue;
   result.add(p.item());
  }
  return result;
 }
 /** Marked before pricing, so an item nobody sells is not quoted again on every visit. */
 static void tried(Player bot,int item,long now) {
  var map=TRIED.computeIfAbsent(bot.getObjectId(),id->new ConcurrentHashMap<>());
  long retry=retryMs();map.values().removeIf(at->now-at>=retry);map.put(item,now);QUOTED.incrementAndGet();
 }
 static void bought(PlayerBotSession session){BOUGHT.incrementAndGet();if(session.dormant())DORMANT_BOUGHT.incrementAndGet();}
 static void forget(int bot){CACHE.remove(bot);TRIED.remove(bot);}
 static String stats() {
  var ready=catalogue;long computed=COMPUTED.get();
  return "gear upgrades bought="+BOUGHT.get()+" (while dormant "+DORMANT_BOUGHT.get()+") of "+QUOTED.get()+" considered, upgrade picks="+CHECKS.get()+" (computed "+computed
   +", average "+(computed==0?0:COMPUTED_NANOS.get()/computed/1000)+"us, slowest "+MAX.get()/1000+"us, on the worker: queued "+QUEUED_TOTAL.get()+", failed "+FAILED.get()+"), catalogue="+(ready==null?"warming":ready.items()+" items/"+ready.nanos()/1000000+"ms");
 }
 private PlayerBotMarketUpgrades(){}
}
