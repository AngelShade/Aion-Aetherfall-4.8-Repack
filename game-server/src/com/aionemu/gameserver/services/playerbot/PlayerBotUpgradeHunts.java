package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.atomic.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.LootCodexService;

/** Hunt-only alternatives to stage-end goals. Never writes goals or creates equipment. */
final class PlayerBotUpgradeHunts {
 record Opportunity(int map,int npc,double gain,List<PlayerBotWorldTravel.Place> places) {}
 record Candidates(List<Opportunity> hunts,Set<PlayerBotGoalHunts.Fit> rejected) {}
 private record Sources(Object items,Object drops,Object rules,Object spawns,Object maps,Object npcs,Object exclusions,Object zones) {
  static Sources current(){return new Sources(DataManager.ITEM_DATA,DataManager.CUSTOM_NPC_DROP,DataManager.GLOBAL_DROP_DATA,DataManager.SPAWNS_DATA,DataManager.WORLD_MAPS_DATA,DataManager.NPC_DATA,DataManager.GLOBAL_EXCLUSION_DATA,DataManager.ZONE_DATA);}
 }
 private record Drops(int npc,List<ItemTemplate> items,List<PlayerBotWorldTravel.Place> places,Race race) {}
 /** id identifies one build: cache keys compare it, never the index contents. */
 private record Index(long id,Sources source,Map<Integer,NavigableMap<Integer,List<Drops>>> maps,int monsters,int items,int links,long nanos) {}
 private static final AtomicLong BUILDS=new AtomicLong();
 private static volatile Index index;
 private static final AtomicBoolean WARMING=new AtomicBoolean();
 private static final AtomicLong CHECKS=new AtomicLong(),NANOS=new AtomicLong(),MAX=new AtomicLong(),COMPUTED=new AtomicLong(),COMPUTED_NANOS=new AtomicLong();
 /** Everything a result depends on except claims, which goalHunt filters after the lookup. */
 private record Inputs(long index,int level,Race race,int map,boolean resident,PlayerBotLoadouts.Key key,PlayerBotGearPolicy.Settings settings,long pinned,List<Long> worn) {}
 private record Cached(Inputs inputs,long until,Candidates result) {}
 /** A result only changes with level, gear, build, map or residency, so most checks are lookups. */
 static final long CACHE_MS=15*60*1000L;
 private static final Map<Integer,Cached> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 /** Worn gear identity; market upgrade picks share this cache key. */
 static List<Long> worn(Player bot) {
  var worn=new ArrayList<Long>();
  for(var item:bot.getEquipment().getEquippedItems())
   worn.add(((long)item.getObjectId()<<32)^((long)item.getItemTemplate().getTemplateId()<<8)^((long)item.getEnchantLevel()<<1)^item.getEquipmentSlot());
  Collections.sort(worn);return worn;
 }
 static void forget(int bot){CACHE.remove(bot);}

 /** Only the worker scans templates/acquisitions and discovers world spawns. A cold bot check returns immediately. */
 static boolean warm() {
  var source=Sources.current();var ready=index;
  if(ready!=null && ready.source().equals(source))return true;
  if(!LootCodexService.warm())return false;
  if(WARMING.compareAndSet(false,true)) {
   var worker=new Thread(()->{
    try {var next=build(source);if(source.equals(Sources.current()))index=next;}
    catch(Exception e){org.slf4j.LoggerFactory.getLogger(PlayerBotUpgradeHunts.class).warn("Near-term gear hunt index could not be prepared",e);}
    finally {WARMING.set(false);}
   },"PlayerBotUpgradeHunts");
   worker.setDaemon(true);worker.setPriority(Thread.MIN_PRIORITY);worker.start();
  }
  return false;
 }
 private static Index build(Sources source) {
  long start=System.nanoTime();var monsters=new HashMap<Integer,List<ItemTemplate>>();var items=new HashSet<Integer>();int links=0;
  for(var item:DataManager.ITEM_DATA.getItemTemplates()) {
   if(!PlayerBotCombatGear.usable(item) || !(item.isWeapon() || item.isArmor()) || item.getItemSlot()==0 || ItemSlot.isStigma(item.getItemSlot()))continue;
   boolean found=false;
   // NPC acquisitions have the same admission for both races; listed()/fit() apply the actor's restrictions later.
   for(var acquisition:LootCodexService.acquisitions(item.getTemplateId(),Race.ELYOS))if(acquisition.kind().equals("npc")) {
    int npc=acquisition.id();
    if(!PlayerBotHunting.goalTemplate(DataManager.NPC_DATA.getNpcTemplate(npc)) || PlayerBotWorldTravel.places(npc).isEmpty())continue;
    monsters.computeIfAbsent(npc,k->new ArrayList<>()).add(item);links++;found=true;
   }
   if(found)items.add(item.getTemplateId());
  }
  var maps=new HashMap<Integer,NavigableMap<Integer,List<Drops>>>();
  monsters.forEach((npc,goods)->{
   var frozen=List.copyOf(goods);int level=DataManager.NPC_DATA.getNpcTemplate(npc).getLevel();
   for(int map:PlayerBotWorldTravel.places(npc).stream().map(PlayerBotWorldTravel.Place::map).distinct().toList())
    maps.computeIfAbsent(map,k->new TreeMap<>()).computeIfAbsent(level,k->new ArrayList<>()).add(new Drops(npc,frozen,PlayerBotWorldTravel.places(npc).stream().filter(p->p.map()==map).toList(),Race.PC_ALL));
  });
  // Broad world gear pools are real loot too, although stage-end saved goals deliberately omit them.
  // Retain exact matching permanent ground spawns: zone/map restrictions must survive destination selection.
  var spots=new HashMap<Integer,List<com.aionemu.gameserver.model.templates.spawns.SpawnTemplate>>();
  for(var map:DataManager.WORLD_MAPS_DATA)if(!map.isInstance())for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map.getMapId())) {
   if(group.isTemporarySpawn() || group.getHandlerType()!=null || !PlayerBotHunting.goalTemplate(DataManager.NPC_DATA.getNpcTemplate(group.getNpcId())))continue;
   for(var spawn:group.getSpawnTemplates())if(!spawn.isAerialSpawn())spots.computeIfAbsent(group.getNpcId(),k->new ArrayList<>()).add(spawn);
  }
  for(var rule:DataManager.GLOBAL_DROP_DATA.getAllRules()) {
   if(rule.getGlobalRuleNpcs()!=null || rule.getChance()<=0 || rule.getMaxDropRule()<=0)continue;
   var goods=rule.getDropItems().stream().filter(drop->drop.getChance()>0).map(drop->DataManager.ITEM_DATA.getItemTemplate(drop.getId()))
    .filter(item->PlayerBotCombatGear.usable(item) && (item.isWeapon() || item.isArmor()) && item.getItemSlot()!=0 && !ItemSlot.isStigma(item.getItemSlot())).distinct().toList();
   if(goods.isEmpty())continue;
   var byLevel=new HashMap<Integer,List<ItemTemplate>>();
   for(var entry:spots.entrySet()) {
    int npc=entry.getKey();var template=DataManager.NPC_DATA.getNpcTemplate(npc);int level=template.getLevel();
    var loot=byLevel.computeIfAbsent(level,l->goods.stream().filter(item->l-item.getLevel()>=rule.getMinDiff() && l-item.getLevel()<=rule.getMaxDiff()).toList());
    if(loot.isEmpty())continue;
    var places=new HashMap<Integer,List<PlayerBotWorldTravel.Place>>();
    for(var spawn:entry.getValue())if(LootCodexService.monsterRuleAt(rule,template,spawn))
     places.computeIfAbsent(spawn.getWorldId(),k->new ArrayList<>()).add(new PlayerBotWorldTravel.Place(spawn.getWorldId(),npc,spawn.getX(),spawn.getY(),spawn.getZ()));
    if(places.isEmpty())continue;
    monsters.putIfAbsent(npc,List.of());loot.forEach(item->items.add(item.getTemplateId()));links+=loot.size();
    Race race=rule.getRestrictionRace()==null?Race.PC_ALL:Race.valueOf(rule.getRestrictionRace().name());
    places.forEach((map,points)->maps.computeIfAbsent(map,k->new TreeMap<>()).computeIfAbsent(level,k->new ArrayList<>()).add(new Drops(npc,loot,PlayerBotWorldTravel.catalog(points),race)));
   }
  }
  maps.replaceAll((map,levels)->{levels.replaceAll((level,rows)->List.copyOf(rows));return Collections.unmodifiableNavigableMap(levels);});
  return new Index(BUILDS.incrementAndGet(),source,Map.copyOf(maps),monsters.size(),items.size(),links,System.nanoTime()-start);
 }

 static Candidates candidates(Player bot) {
  if(!warm())return new Candidates(List.of(),Set.of());
  long start=System.nanoTime();boolean computed=false;
  try {
   var ready=index;var role=PlayerBotGearPolicy.role(bot);var key=PlayerBotLoadouts.key(bot,role);var weapon=PlayerBotLoadouts.weapon(key);
   boolean resident=PlayerBotPlayerPresence.resident(bot);long now=System.currentTimeMillis();
   var inputs=new Inputs(ready.id(),bot.getLevel(),bot.getRace(),bot.getWorldId(),resident,key,PlayerBotGearPolicy.settings(bot),PlayerBotGearPolicy.pinned(bot),worn(bot));
   var cached=CACHE.get(bot.getObjectId());
   if(cached!=null && now<cached.until() && cached.inputs().equals(inputs))return cached.result();
   computed=true;
   var gains=new HashMap<Integer,Double>();var fits=new HashMap<Integer,PlayerBotGoalHunts.Fit>();
   // Global rules share one loot list per rule and level across every monster, and acquisitions one per monster
   // across maps: score each list once per check instead of once per monster.
   var lists=new IdentityHashMap<List<ItemTemplate>,Double>();
   var hunts=new ArrayList<Opportunity>();var rejected=EnumSet.noneOf(PlayerBotGoalHunts.Fit.class);
   for(var map:ready.maps().entrySet()) {
    if(!PlayerBotWorldActivities.goalPlaceAllowed(resident,bot.getWorldId(),map.getKey()))continue;
    // fit() owns the exact XP/rating/hostility admission. The level buckets only prune impossible levels.
    for(var level:map.getValue().headMap(bot.getLevel()+4,true).entrySet()) {
     if(!PlayerBotWorldActivities.levelSuitable(bot.getLevel(),level.getKey()))continue;
     for(var drops:level.getValue()) {
      if(drops.race()!=Race.PC_ALL && drops.race()!=bot.getRace())continue;
      var fit=fits.computeIfAbsent(drops.npc(),npc->PlayerBotGoalHunts.fit(bot,npc));
      if(fit!=PlayerBotGoalHunts.Fit.SOLO){rejected.add(fit);continue;}
      double best=lists.computeIfAbsent(drops.items(),loot->{
       double most=1;
       for(var item:loot)most=Math.max(most,gains.computeIfAbsent(item.getTemplateId(),id->{
        if(!PlayerBotLoadouts.listed(item,key,bot.getLevel()) || item.getRequiredLevel(bot.getPlayerClass())>bot.getLevel()
         || item.getMaxLevelRestrict(bot.getPlayerClass())>0 && bot.getLevel()>item.getMaxLevelRestrict(bot.getPlayerClass()))return -Double.MAX_VALUE;
        double score=PlayerBotGearPolicy.score(bot,item,role,key.build()),gain=-Double.MAX_VALUE;
        for(var slot:PlayerBotTemporary.GEAR)if(PlayerBotLoadouts.fits(item,slot,key,weapon))
         gain=Math.max(gain,PlayerBotGearPolicy.gain(bot,item,score,item.isTwoHandWeapon()?ItemSlot.MAIN_OR_SUB.getSlotIdMask():slot.getSlotIdMask(),role));
        return gain;
       }));
       return most;
      });
      if(best>1)hunts.add(new Opportunity(map.getKey(),drops.npc(),best,drops.places()));
     }
    }
   }
   hunts.sort(Comparator.<Opportunity>comparingInt(h->h.map()==bot.getWorldId()?0:1).thenComparing(Comparator.comparingDouble(Opportunity::gain).reversed()).thenComparingInt(Opportunity::npc));
   var result=new Candidates(List.copyOf(hunts),Set.copyOf(rejected));
   if(CACHE.size()>8192)CACHE.clear();
   CACHE.put(bot.getObjectId(),new Cached(inputs,now+CACHE_MS,result));
   return result;
  } finally {
   long spent=System.nanoTime()-start;CHECKS.incrementAndGet();NANOS.addAndGet(spent);MAX.accumulateAndGet(spent,Math::max);
   if(computed){COMPUTED.incrementAndGet();COMPUTED_NANOS.addAndGet(spent);}
  }
 }
 static String stats() {
  var ready=index;long checks=CHECKS.get(),computed=COMPUTED.get();
  return "upgrade checks="+checks+", average="+(checks==0?0:NANOS.get()/checks/1000)+"us, slowest="+MAX.get()/1000+"us, computed="+computed
   +" (average "+(computed==0?0:COMPUTED_NANOS.get()/computed/1000)+"us), upgrade index="+(ready==null?"warming":ready.monsters()+" monsters/"+ready.items()+" items/"+ready.links()+" links/"+ready.maps().size()+" maps, build="+ready.nanos()/1000000+"ms");
 }
 private PlayerBotUpgradeHunts() {}
}
