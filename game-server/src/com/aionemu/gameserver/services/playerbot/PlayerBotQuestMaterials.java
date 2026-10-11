/* Native material acquisition adapts LootAction::DoLoot / CanOpenLock,
 * ChooseTravelTargetAction::needItemForQuest and ItemUsageValue::IsItemNeededForQuest
 * at mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * See third-party/playerbots/AUTHORS.md. Native services own all item awards. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntToLongFunction;
import java.time.LocalDate;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.administration.AdminConfig;
import com.aionemu.gameserver.geoEngine.collision.IgnoreProperties;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.craft.Profession;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.actions.PlayerMode;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.gather.GatherableTemplate;
import com.aionemu.gameserver.model.templates.gather.Material;
import com.aionemu.gameserver.model.templates.globaldrops.*;
import com.aionemu.gameserver.model.templates.npc.*;
import com.aionemu.gameserver.model.templates.zone.ZoneClassName;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.skillengine.task.GatheringTask;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.stats.DropRewardEnum;
import com.aionemu.gameserver.world.WorldDropType;
import com.aionemu.gameserver.world.WorldMapType;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.zone.ZoneAttributes;

/** Read-only source discovery. Combat/loot remain in the shared native session. */
final class PlayerBotQuestMaterials {
 record Selector(boolean present,Set<Object> values) {
  Selector {values=Set.copyOf(values);}
  boolean accepts(Object value){return !present || value!=null && values.contains(value);}
 }
 private static Selector any(){return new Selector(false,Set.of());}
 private static <T> Selector selector(Object container,Collection<T> entries,java.util.function.Function<T,?> value) {
  if(container==null)return any();var values=new HashSet<Object>();for(var e:entries)values.add(value.apply(e));return new Selector(true,values);
 }
 record CustomSource(int item,int npc,Race race,float chance,boolean reduction) {}
 record GatherSource(int id,int skill,int points,int level,int required,int check,int erase,
                     Set<Integer> normal,Set<Integer> extra) {
  GatherSource {normal=Set.copyOf(normal);extra=Set.copyOf(extra);}
  Set<Integer> outputs(boolean equipped,long held) {
   if(required>0 && check==1 && equipped)return extra;
   if(required>0 && check==2)return held>=erase?extra:Set.of();
   return normal;
  }
  Set<Integer> declared() {
   if(required>0 && check==2)return extra;
   if(required>0 && check==1){var both=new HashSet<>(normal);both.addAll(extra);return Set.copyOf(both);}
   return normal;
  }
 }
 record Rule(float chance,boolean reduction,int minimum,int maximum,Race restriction,
             Selector maps,Selector worlds,Selector ratings,Selector races,Selector tribes,
             Selector npcs,Selector groups,Set<Integer> excluded,Selector zones,int maximumDrops) {
  Rule {excluded=Set.copyOf(excluded);}
  boolean matches(Race actor,int itemLevel,int npcLevel,int npc,int map,Object world,Object rating,Object race,Object tribe,Object group,Set<String> currentZones) {
   int diff=npcLevel-itemLevel;
   return chance>0 && maximumDrops>0 && (restriction==null || restriction==actor) && diff>=minimum && diff<=maximum
    && maps.accepts(map) && worlds.accepts(world) && ratings.accepts(rating) && races.accepts(race) && tribes.accepts(tribe)
    && npcs.accepts(npc) && groups.accepts(group) && !excluded.contains(npc)
    && (!zones.present() || currentZones.stream().anyMatch(zones.values()::contains));
  }
 }
 record GlobalSource(int item,int level,Race itemRace,Rule rule) {}
 private record Site(PlayerBotWorldTravel.Place place,int level,Object rating,Race race,Object tribe,Object group,
                     WorldDropType world,boolean globalAllowed,boolean defaultAllowed,Set<String> zones) {
  Site {zones=Set.copyOf(zones);}
 }
 private record Catalog(Object drops,Object globals,Object gather,Object spawns,Object npcs,Object zones,Object maps,Object items,Object exclusions,
                        Map<Integer,List<CustomSource>> custom,Map<Integer,List<GlobalSource>> global,
                        Map<Integer,List<GatherSource>> gathering,Map<Integer,GatherSource> nodes,
                        Map<Integer,List<Site>> sites,Map<Integer,List<PlayerBotWorldTravel.Place>> gatherPlaces,Set<PlayerBotWorldTravel.Place> aerial) {
  Catalog {aerial=Set.copyOf(aerial);}
 }
 private static volatile Catalog CATALOG;
 private record GlobalSite(GlobalSource source,Site site) {}
 /** Shared complete results for the last 64 requested material IDs; eviction changes cost, never coverage. */
 private static final Map<Integer,List<GlobalSite>> GLOBAL_SITES=new LinkedHashMap<>(64,0.75f,true);
 private static Catalog GLOBAL_CATALOG;
 private static List<GlobalSite> globalSites(Catalog catalog,int item) {
  synchronized(GLOBAL_SITES) {
   if(GLOBAL_CATALOG!=catalog){GLOBAL_SITES.clear();GLOBAL_CATALOG=catalog;}
   var cached=GLOBAL_SITES.get(item);if(cached!=null)return cached;
   var result=new ArrayList<GlobalSite>();
   for(var source:catalog.global().getOrDefault(item,List.of()))for(var list:catalog.sites().values())for(var site:list) {
    var r=source.rule();Race actor=r.restriction()==null?Race.PC_ALL:r.restriction();
    if(site.globalAllowed() && (site.defaultAllowed() || r.npcs().present())
     && r.matches(actor,source.level(),site.level(),site.place().npc(),site.place().map(),site.world(),site.rating(),site.race(),site.tribe(),site.group(),site.zones()))result.add(new GlobalSite(source,site));
   }
   var frozen=List.copyOf(result);GLOBAL_SITES.put(item,frozen);if(GLOBAL_SITES.size()>64)GLOBAL_SITES.remove(GLOBAL_SITES.keySet().iterator().next());return frozen;
  }
 }
 private static final class Actor {
  long scan,nextSearch;int map,instance,features;Map<Integer,Long> required=Map.of(),actionable=Map.of();
  Map<Integer,Long> planned=Map.of();Set<Integer> hunts=Set.of();List<PlayerBotWorldTravel.Place> places=List.of();
  PlayerBotWorldTravel.Place destination;double best=Double.POSITIVE_INFINITY;long progress;
  final Map<PlayerBotWorldTravel.Place,Long> excluded=new HashMap<>();
  final Map<Integer,Long> localRetries=new HashMap<>();
  Flight flight;String flightReason="";
  PlayerBotWorldTravel.Place launchSource,launchFloor;long launchScan;
  final Map<PlayerBotWorldTravel.Place,Long> failedLaunches=new HashMap<>();
 }
 private record Flight(int map,int instance,int source,float x,float y,float z) {}
 private static final Map<Integer,Actor> ACTORS=new ConcurrentHashMap<>();
 private record HarvestGoal(int skill,int points) {}
 static Map<Integer,Long> deficit(Map<Integer,Long> required,IntToLongFunction held) {
  var missing=new TreeMap<Integer,Long>();required.forEach((id,count)->{if(id>0 && count>0){long have=Math.max(0,held.applyAsLong(id));if(have<count)missing.put(id,count-have);}});return Map.copyOf(missing);
 }
 static void acquireRequirements(Map<Integer,Long> target,Map<Integer,Long> custody,boolean enabled) {
  if(enabled)custody.forEach((id,count)->{if(id>0 && count>0)target.merge(id,count,Math::max);});
 }
 private static Set<Integer> outputs(List<Material> materials) {
  var result=new HashSet<Integer>();long cumulative=0;
  // The loader sorted this exact native list. Controller draws 0..9,999,999
  // and picks the first cumulative rate >= draw; later saturated rows cannot produce.
  if(materials!=null)for(var m:materials) {
   if(cumulative>=9999999)break;if((m.getRate()>0 || cumulative==0 && m.getRate()==0) && m.getItemId()>0)result.add(m.getItemId());cumulative+=m.getRate();
  }return Set.copyOf(result);
 }
 static GatherSource gatherSource(GatherableTemplate t) {
  return new GatherSource(t.getTemplateId(),t.getHarvestSkill(),t.getSkillLevel(),t.getLevelLimit(),t.getRequiredItemId(),t.getCheckType(),t.getEraseValue(),
   t.getMaterials()==null?Set.of():outputs(t.getMaterials().getMaterial()),t.getExtraMaterials()==null?Set.of():outputs(t.getExtraMaterials().getMaterial()));
 }
 static Rule rule(GlobalRule r) {
  return new Rule(r.getChance(),r.isUseLevelBasedChanceReduction(),r.getMinDiff(),r.getMaxDiff(),
   r.getRestrictionRace()==null?null:r.getRestrictionRace()==GlobalRule.RestrictionRace.ELYOS?Race.ELYOS:Race.ASMODIANS,
   selector(r.getGlobalRuleMaps(),r.getGlobalRuleMaps()==null?List.of():r.getGlobalRuleMaps().getGlobalDropMaps(),GlobalDropMap::getMapId),
   selector(r.getGlobalRuleWorlds(),r.getGlobalRuleWorlds()==null?List.of():r.getGlobalRuleWorlds().getGlobalDropWorlds(),GlobalDropWorld::getWorldDropType),
   selector(r.getGlobalRuleRatings(),r.getGlobalRuleRatings()==null?List.of():r.getGlobalRuleRatings().getGlobalDropRatings(),GlobalDropRating::getRating),
   selector(r.getGlobalRuleRaces(),r.getGlobalRuleRaces()==null?List.of():r.getGlobalRuleRaces().getGlobalDropRaces(),GlobalDropRace::getRace),
   selector(r.getGlobalRuleTribes(),r.getGlobalRuleTribes()==null?List.of():r.getGlobalRuleTribes().getGlobalDropTribes(),GlobalDropTribe::getTribe),
   selector(r.getGlobalRuleNpcs(),r.getGlobalRuleNpcs()==null?List.of():r.getGlobalRuleNpcs().getGlobalDropNpcs(),GlobalDropNpc::getNpcId),
   selector(r.getGlobalRuleNpcGroups(),r.getGlobalRuleNpcGroups()==null?List.of():r.getGlobalRuleNpcGroups().getGlobalDropNpcGroups(),GlobalDropNpcGroup::getGroup),
   r.getGlobalRuleExcludedNpcs()==null?Set.of():r.getGlobalRuleExcludedNpcs().getNpcIds(),
   selector(r.getGlobalRuleZones(),r.getGlobalRuleZones()==null?List.of():r.getGlobalRuleZones().getGlobalDropZones(),GlobalDropZone::getZone),r.getMaxDropRule());
 }
 private static boolean current(Catalog c) {
  return c!=null && c.drops()==DataManager.CUSTOM_NPC_DROP && c.globals()==DataManager.GLOBAL_DROP_DATA && c.gather()==DataManager.GATHERABLE_DATA
   && c.spawns()==DataManager.SPAWNS_DATA && c.npcs()==DataManager.NPC_DATA && c.zones()==DataManager.ZONE_DATA && c.maps()==DataManager.WORLD_MAPS_DATA
   && c.items()==DataManager.ITEM_DATA && c.exclusions()==DataManager.GLOBAL_EXCLUSION_DATA;
 }
 private static <T> Map<Integer,List<T>> freeze(Map<Integer,List<T>> mutable) {
  var out=new HashMap<Integer,List<T>>();mutable.forEach((id,list)->out.put(id,List.copyOf(new LinkedHashSet<>(list))));return Map.copyOf(out);
 }
 private static Catalog catalog() {
  var cached=CATALOG;if(current(cached))return cached;
  synchronized(PlayerBotQuestMaterials.class) {
   if(current(CATALOG))return CATALOG;
   var custom=new HashMap<Integer,List<CustomSource>>();var global=new HashMap<Integer,List<GlobalSource>>();var gathering=new HashMap<Integer,List<GatherSource>>();
   var nodes=new HashMap<Integer,GatherSource>();var sites=new HashMap<Integer,List<Site>>();var gatherPlaces=new HashMap<Integer,List<PlayerBotWorldTravel.Place>>();var zoneNames=new HashSet<Object>();var aerial=new HashSet<PlayerBotWorldTravel.Place>();
   for(var npc:DataManager.NPC_DATA.getNpcData()) {
    var drops=DataManager.CUSTOM_NPC_DROP.getNpcDrop(npc.getTemplateId());if(drops==null || drops.getDropGroup()==null)continue;
    for(var group:drops.getDropGroup())if(group.getMaxItems()>0 && group.getDrop()!=null)for(var d:group.getDrop())if(d.getChance()>0 && d.getMaxAmount()>0)
     custom.computeIfAbsent(d.getItemId(),k->new ArrayList<>()).add(new CustomSource(d.getItemId(),npc.getTemplateId(),group.getRace(),d.getChance(),group.isUseLevelBasedChanceReduction()));
   }
   for(var r:DataManager.GLOBAL_DROP_DATA.getAllRules()) {
    var immutable=rule(r);zoneNames.addAll(immutable.zones().values());if(immutable.chance()<=0 || immutable.maximumDrops()<=0)continue;
    for(var item:r.getDropItems()) {var t=DataManager.ITEM_DATA.getItemTemplate(item.getId());if(t!=null && item.getMaxCount()>0)
     global.computeIfAbsent(item.getId(),k->new ArrayList<>()).add(new GlobalSource(item.getId(),t.getLevel(),t.getRace(),immutable));}
   }
   for(var map:DataManager.WORLD_MAPS_DATA)for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map.getMapId())) {
    int id=group.getNpcId();var gather=DataManager.GATHERABLE_DATA.getGatherableTemplate(id);var npc=DataManager.NPC_DATA.getNpcTemplate(id);
    if(gather!=null && !nodes.containsKey(id)) {
     var node=gatherSource(gather);nodes.put(id,node);
     for(int item:node.declared())gathering.computeIfAbsent(item,k->new ArrayList<>()).add(node);
    }
    if(map.isInstance() || group.isTemporarySpawn() || group.getHandlerType()!=null || group.getEventTemplate()!=null)continue;
    for(var spot:group.getSpawnTemplates()) {
     var place=new PlayerBotWorldTravel.Place(map.getMapId(),id,spot.getX(),spot.getY(),spot.getZ());
     if(gather!=null){gatherPlaces.computeIfAbsent(id,k->new ArrayList<>()).add(place);if(spot.isAerialSpawn())aerial.add(place);continue;}
     if(spot.isAerialSpawn())continue;
     if(npc==null || !npc.isClientVisible())continue;
     var zones=new HashSet<String>();for(var z:DataManager.ZONE_DATA.getZones(map.getMapId()))if(zoneNames.contains(z.getZoneTemplate().getName()) && z.getArea().isInside3D(spot.getX(),spot.getY(),spot.getZ()))zones.add(z.getZoneTemplate().getName());
     var exclusion=DataManager.GLOBAL_EXCLUSION_DATA;
     boolean allowed=map.getWorldDropType()!=WorldDropType.NONE && !"quest_use_item".equals(npc.getAiName())
      && !exclusion.getNpcIds().contains(id) && !exclusion.getNpcNames().contains(npc.getName()) && !exclusion.getNpcTemplateTypes().contains(npc.getNpcTemplateType())
      && !exclusion.getNpcTribes().contains(npc.getTribe()) && !exclusion.getNpcAbyssTypes().contains(npc.getAbyssNpcType());
     var drop=npc.getGroupDrop();boolean chest="chest".equals(npc.getAiName()) || drop.name().toLowerCase(Locale.ROOT).startsWith("treasure") || drop.name().toLowerCase(Locale.ROOT).endsWith("box");
     boolean defaults=!chest && (npc.getAbyssNpcType()==AbyssNpcType.NONE || npc.getAbyssNpcType()==AbyssNpcType.DEFENDER)
      && (npc.getLevel()>=2 || map.getMapId()==WorldMapType.POETA.getId() || map.getMapId()==WorldMapType.ISHALGEN.getId());
     sites.computeIfAbsent(id,k->new ArrayList<>()).add(new Site(place,npc.getLevel(),npc.getRating(),npc.getRace(),npc.getTribe(),drop,map.getWorldDropType(),allowed,defaults,zones));
    }
   }
   return CATALOG=new Catalog(DataManager.CUSTOM_NPC_DROP,DataManager.GLOBAL_DROP_DATA,DataManager.GATHERABLE_DATA,DataManager.SPAWNS_DATA,DataManager.NPC_DATA,DataManager.ZONE_DATA,
    DataManager.WORLD_MAPS_DATA,DataManager.ITEM_DATA,DataManager.GLOBAL_EXCLUSION_DATA,freeze(custom),freeze(global),freeze(gathering),Map.copyOf(nodes),freeze(sites),freeze(gatherPlaces),aerial);
  }
 }
 private static Actor actor(PlayerBotSession session,boolean refresh) {
  var bot=session.bot();var state=ACTORS.computeIfAbsent(bot.getObjectId(),k->new Actor());long now=System.currentTimeMillis();
  int features=(PlayerBotConfig.QUESTS_ENABLED?1:0)|(PlayerBotConfig.QUEST_CUSTOM?2:0)|(PlayerBotConfig.QUEST_CRAFTING?4:0)|(PlayerBotConfig.QUEST_MATERIALS?8:0)|(PlayerBotConfig.QUEST_EXCHANGE?16:0);
  if(refresh || now>=state.scan || state.features!=features || state.map!=bot.getWorldId() || state.instance!=bot.getInstanceId()) {
   var required=new TreeMap<Integer,Long>();var actionable=new TreeMap<Integer,Long>();
   for(var q:bot.getQuestStateList().getUncompletedQuests())if(q.getStatus()==QuestStatus.START && PlayerBotQuestSync.wanted(session.owner(),bot,q.getQuestId())) {
    var custody=PlayerBotQuestCraftingDriver.requirements(bot,q.getQuestId());acquireRequirements(required,custody,true);
    acquireRequirements(actionable,custody,PlayerBotConfig.QUEST_MATERIALS && PlayerBotQuestFeatures.enabled(q.getQuestId()));
   }
   if(PlayerBotConfig.QUESTS_ENABLED && PlayerBotConfig.QUEST_MATERIALS)PlayerBotQuestCraftingDriver.neededMaterials(session).forEach((id,count)->{if(id>0 && count>0)actionable.merge(id,Math.addExact(bot.getInventory().getItemCountByItemId(id),count),Math::max);});
   // A native consumable gathering gate is a real dependency too. Resolve one
   // complete held quantity, never a virtual tool or an extra-material fallback.
   var data=actionable.isEmpty()?null:catalog();var visited=new HashSet<Integer>();
   for(int depth=0;data!=null && depth<8;depth++) {
    boolean added=false;for(int item:new ArrayList<>(actionable.keySet()))if(visited.add(item) && bot.getInventory().getItemCountByItemId(item)<actionable.get(item)) {
     var node=data.gathering().getOrDefault(item,List.of()).stream().filter(s->s.required()>0 && s.check()==2 && s.extra().contains(item) && bot.getLevel()>=s.level())
      .min(Comparator.<GatherSource>comparingLong(s->Math.max(0,s.erase()-bot.getInventory().getItemCountByItemId(s.required()))).thenComparingInt(GatherSource::points).thenComparingInt(GatherSource::id)).orElse(null);
     if(node!=null) {
      long before=actionable.getOrDefault(node.required(),0L);actionable.merge(node.required(),(long)node.erase(),Math::max);added|=before<actionable.get(node.required());
     }
    }
    if(!added)break;
   }
   acquireRequirements(required,actionable,true);state.required=Map.copyOf(required);state.actionable=Map.copyOf(actionable);state.features=features;state.scan=now+1000;
   if(state.map!=bot.getWorldId() || state.instance!=bot.getInstanceId()){state.planned=Map.of();state.destination=null;state.excluded.clear();state.localRetries.clear();state.flight=null;state.launchSource=null;state.launchFloor=null;state.failedLaunches.clear();state.nextSearch=0;}
   state.map=bot.getWorldId();state.instance=bot.getInstanceId();
  }
  return state;
 }
 static Map<Integer,Long> needed(PlayerBotSession session) {var bot=session.bot();return deficit(actor(session,false).actionable,bot.getInventory()::getItemCountByItemId);}
 /** Absolute prerequisite quantities preserve already-held gathering tools/materials until native consumption. */
 static Map<Integer,Long> required(PlayerBotSession session){return actor(session,false).required;}
 /** Sale/surplus/warehouse/trade callers use owned requirements, never remaining deficits. */
 static boolean protectedItem(PlayerBotSession session,int item) {
  return item>0 && (required(session).containsKey(item) || PlayerBotQuestCraftingDriver.protectedItem(session,item));
 }
 /** Conservative recovery guard: skill, race, distance, respawn and transport cannot erase a declared source. */
 static boolean hasDeclaredSource(int item) {var data=catalog();return !data.custom().getOrDefault(item,List.of()).isEmpty() || !data.global().getOrDefault(item,List.of()).isEmpty() || !data.gathering().getOrDefault(item,List.of()).isEmpty();}
 static boolean hasSource(Player bot,int item) {
  var data=catalog();if(data.custom().getOrDefault(item,List.of()).stream().anyMatch(s->race(s.race(),bot.getRace())))return true;
  if(data.global().getOrDefault(item,List.of()).stream().anyMatch(s->race(s.itemRace(),bot.getRace()) && (s.rule().restriction()==null || s.rule().restriction()==bot.getRace())))return true;
  return data.gathering().getOrDefault(item,List.of()).stream().anyMatch(s->skill(bot,s) && output(bot,s,Set.of(item)));
 }
 /** Source discovery never hides why a native dependency has no immediately usable route. */
 static Map<Integer,String> reasons(PlayerBotSession session) {
  var needs=needed(session);if(needs.isEmpty())return Map.of();var result=new TreeMap<Integer,String>();var bot=session.bot();var data=catalog();
  for(int item:needs.keySet()) {
   if(!hasDeclaredSource(item))result.put(item,"No native NPC/global-drop or gathering source is declared; native merchant/crafting/action prerequisites remain separate");
   else if(!hasSource(bot,item)) {
    var nodes=data.gathering().getOrDefault(item,List.of());
    if(!nodes.isEmpty())result.put(item,"Native gathering is gated by profession/skill points, level, equipped tool or consumed prerequisite: "+nodes.stream().map(s->s.id()+" skill "+s.skill()+"/"+s.points()+" level "+s.level()+" item "+s.required()+" check "+s.check()+" count "+s.erase()).toList());
    else result.put(item,"Declared native drops are restricted to another race or actor eligibility");
   }else result.put(item,"Earn through the real native source; current map/instance, spawn availability, geometry, transport, drop ownership and random failure still apply"+(actor(session,false).flightReason.isEmpty()?"":"; "+actor(session,false).flightReason));
  }return Map.copyOf(result);
 }
 private static boolean skill(Player bot,GatherSource source) {return bot.getLevel()>=source.level() && bot.getSkillList().isSkillPresent(source.skill()) && bot.getSkillList().getSkillLevel(source.skill())>=source.points();}
 private static HarvestGoal harvestGoal(Player bot,Map<Integer,Long> needed,Catalog data) {
  var choices=new ArrayList<GatherSource>();
  for(int item:needed.keySet())for(var source:data.gathering().getOrDefault(item,List.of())) {
   var profession=Profession.getBySkillId(source.skill());
   if(profession==null || profession.isCrafting() || bot.getLevel()<Math.max(10,source.level()) || skill(bot,source) || !output(bot,source,needed.keySet()))continue;
   choices.add(source);
  }
  var chosen=choices.stream().min(Comparator.comparingInt(GatherSource::points).thenComparingInt(GatherSource::id)).orElse(null);
  return chosen==null?null:new HarvestGoal(chosen.skill(),chosen.points());
 }
 private static Set<Integer> trainingNodes(Player bot,HarvestGoal goal,Catalog data) {
  if(goal==null || !bot.getSkillList().isSkillPresent(goal.skill()))return Set.of();int level=bot.getSkillList().getSkillLevel(goal.skill());
  if(Profession.getBySkillId(goal.skill()).getUpgradeCost(level)!=null)return Set.of();
  var result=new HashSet<Integer>();for(var source:data.nodes().values())if(source.skill()==goal.skill() && skill(bot,source) && practiceAllowed(goal.skill(),level,source.points(),CraftConfig.DISABLE_AETHER_AND_ESSENCE_TAPPING_CAP)
   && !source.outputs(source.required()>0 && !bot.getEquipment().getEquippedItemsByItemId(source.required()).isEmpty(),source.required()>0?bot.getInventory().getItemCountByItemId(source.required()):0).isEmpty())result.add(source.id());
  return Set.copyOf(result);
 }
 /** Mirrors PlayerSkillList.addSkillXp admission without granting XP or emitting messages. */
 static boolean practiceAllowed(int skill,int level,int nodePoints,boolean uncappedTapping) {
  if(level<nodePoints || level-nodePoints>40)return false;
  if(skill==30001 && level==49)return false;
  if((skill==30002 || skill==30003) && (level==449 || level>=499 && uncappedTapping))return true;
  return !Set.of(99,199,299,399,449,499,549).contains(level);
 }
 private static Set<Integer> trainers(Player bot,HarvestGoal goal) {
  if(goal==null)return Set.of();int level=bot.getSkillList().isSkillPresent(goal.skill())?bot.getSkillList().getSkillLevel(goal.skill()):0;
  return Profession.getBySkillId(goal.skill()).getUpgradeCost(level)==null?Set.of():PlayerBotQuestCrafting.trainers(goal.skill());
 }
 private static boolean output(Player bot,GatherSource source,Set<Integer> needed) {
  return source.outputs(source.required()>0 && !bot.getEquipment().getEquippedItemsByItemId(source.required()).isEmpty(),source.required()>0?bot.getInventory().getItemCountByItemId(source.required()):0).stream().anyMatch(needed::contains);
 }
 static boolean race(Race source,Race actor){return source==Race.PC_ALL || source==actor;}
 static boolean reduction(boolean reduced,int npcLevel,int actorLevel) {return !reduced || DropRewardEnum.dropRewardFrom(npcLevel-actorLevel)>0;}
 private static void plan(PlayerBotSession session) {
  var bot=session.bot();var state=actor(session,false);var needed=needed(session);long now=System.currentTimeMillis();
  if(needed.isEmpty()){state.planned=Map.of();state.hunts=Set.of();state.places=List.of();state.destination=null;state.nextSearch=now+10000;return;}
  if(now<state.nextSearch && state.planned.equals(needed))return;
  var data=catalog();var hunts=new TreeSet<Integer>();var places=new LinkedHashSet<PlayerBotWorldTravel.Place>();
  for(int item:needed.keySet()) {
   for(var source:data.custom().getOrDefault(item,List.of()))if(race(source.race(),bot.getRace())) {
    var template=DataManager.NPC_DATA.getNpcTemplate(source.npc());if(template==null || !template.isClientVisible() || !reduction(source.reduction(),template.getLevel(),bot.getLevel()))continue;
    hunts.add(source.npc());for(var site:data.sites().getOrDefault(source.npc(),List.of()))places.add(site.place());
   }
   for(var match:globalSites(data,item)) {var source=match.source();var site=match.site();
    var r=source.rule();if(race(source.itemRace(),bot.getRace()) && (r.restriction()==null || r.restriction()==bot.getRace()) && reduction(r.reduction(),site.level(),bot.getLevel())) {
     hunts.add(site.place().npc());places.add(site.place());
    }
   }
   for(var source:data.gathering().getOrDefault(item,List.of()))if(skill(bot,source) && output(bot,source,needed.keySet()))places.addAll(data.gatherPlaces().getOrDefault(source.id(),List.of()));
  }
  var harvest=harvestGoal(bot,needed,data);
  for(int trainer:trainers(bot,harvest))places.addAll(PlayerBotWorldTravel.places(trainer));
  for(int node:trainingNodes(bot,harvest,data))places.addAll(data.gatherPlaces().getOrDefault(node,List.of()));
  state.planned=needed;state.hunts=Set.copyOf(hunts);state.places=List.copyOf(places);state.nextSearch=now+10000;
 }
 /** Local live global-source matching also includes instance/temporary spawns unavailable for autonomous travel. */
 static Set<Integer> huntNpcIds(PlayerBotSession session) {
  var needs=needed(session);if(needs.isEmpty())return Set.of();plan(session);var bot=session.bot();var result=new TreeSet<>(actor(session,false).hunts);var data=catalog();var service=DropRegistrationService.getInstance();
  if(needs.keySet().stream().noneMatch(id->!data.global().getOrDefault(id,List.of()).isEmpty()))return Set.copyOf(result);
  bot.getKnownList().forEachNpc(n->{if(!n.isSpawned() || n.isDead() || !n.getObjectTemplate().isClientVisible() || n.getWorldId()!=bot.getWorldId() || n.getInstanceId()!=bot.getInstanceId() || !bot.getKnownList().sees(n))return;
   var modifiers=service.createDropModifiers(n,bot,bot.getLevel());
   for(int item:needs.keySet())for(var s:data.global().getOrDefault(item,List.of())) {
    var r=s.rule();var zones=new HashSet<String>();for(var z:r.zones().values())if(n.isInsideZone((String)z))zones.add((String)z);
    if(race(s.itemRace(),bot.getRace()) && !service.hasGlobalNpcExclusions(n) && n.getWorldDropType()!=WorldDropType.NONE && !"quest_use_item".equals(n.getAi().getName())
     && (service.isAllowedDefaultGlobalDropNpc(n,modifiers.isDropNpcChest()) || r.npcs().present()) && modifiers.calculateDropChance(r.chance(),r.reduction())>0
     && r.matches(bot.getRace(),s.level(),n.getLevel(),n.getNpcId(),n.getWorldId(),n.getWorldDropType(),n.getRating(),n.getRace(),n.getTribe(),n.getGroupDrop(),zones))result.add(n.getNpcId());
   }
  });return Set.copyOf(result);
 }
 static List<PlayerBotWorldTravel.Place> routePlaces(PlayerBotSession session){if(needed(session).isEmpty())return List.of();plan(session);return actor(session,false).places;}
 /** Root keeps this continuation eligible even after material requirements or manual activity permission vanish. */
 static boolean flightPending(PlayerBotSession session) {
  var state=ACTORS.get(session.bot().getObjectId());return state!=null && state.flight!=null;
 }
 static int flightBudget(double distance,double speed) {
  if(!Double.isFinite(distance) || distance<0 || !Double.isFinite(speed) || speed<=0)return Integer.MAX_VALUE;
  double points=Math.ceil(2*distance/speed)+12;return points>=Integer.MAX_VALUE?Integer.MAX_VALUE:(int)points;
 }
 static boolean ground(float z,float floor){return Float.isFinite(z) && Float.isFinite(floor) && Math.abs(z-floor)<=1.5f;}
 private static float flightSpeed(Player bot) {
  if(bot.isFlying() || bot.isInPlayerMode(PlayerMode.RIDE))return bot.getGameStats().getMovementSpeedFloat();
  var stats=bot.getGameStats();return stats.getStat(StatEnum.FLY_SPEED,Math.round(stats.getStatsTemplate().getFlySpeed()*1000)).getCurrent()/1000f;
 }
 private static boolean flightZone(int map,float x,float y,float z) {
  var template=DataManager.WORLD_MAPS_DATA.getTemplate(map);if(template==null)return false;boolean yes=template.hasAttribute(ZoneAttributes.FLY);
  for(var zone:DataManager.ZONE_DATA.getZones(map))if(zone.getArea().isInside3D(x,y,z)) {
   if(zone.getZoneTemplate().getZoneType()==ZoneClassName.NO_FLY)return false;
   if(zone.getZoneTemplate().getZoneType()==ZoneClassName.FLY)yes=true;
  }return yes;
 }
 private static String flightRestriction(Player bot) {
  if(!bot.getCommonData().isDaeva())return "Native flight requires completed Ascension/Daeva status";
  if(bot.getFlyReuseTime()>System.currentTimeMillis())return "Native flight cooldown remains active";
  if(bot.getEffectController().isAbnormalSet(AbnormalState.NOFLY))return "Native NOFLY abnormal effect prevents takeoff";
  if(bot.getTransformModel().cantFly())return "Native transformation prevents takeoff";
  if(bot.getStore()!=null)return "Native personal-store custody prevents takeoff";
  if(!bot.hasAccess(AdminConfig.FREE_FLIGHT) && !bot.isInsideFlyZone())return "A supported reachable native flight-zone launch floor is required";
  return "";
 }
 private static float floorBelow(Player bot,float x,float y,float z) {
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(bot.getWorldId());if(map==null)return Float.NaN;
  float floor=GeoService.getInstance().getZ(bot.getWorldId(),x,y,z+2,-Math.max(4096,map.getWorldSize()),bot.getInstanceId());
  return Float.isFinite(floor) && GeoService.getInstance().isPlayerBotLanding(bot.getWorldId(),bot.getInstanceId(),x,y,floor)?floor:Float.NaN;
 }
 /** Native point-to-point flight only; the existing navigator rechecks obstacles/hazards and actual movement speed. */
 private static boolean aerialMove(PlayerBotSession session,PlayerBotWorldTravel.Place target,PlayerBotNavigation navigation) {
  var bot=session.bot();var state=actor(session,false);var geo=GeoService.getInstance();
  if(!PlayerBotConfig.QUEST_FLIGHT){state.flightReason="Autonomous aerial acquisition is disabled by gameserver.playerbots.quests.flight";return false;}
  if(target.map()!=bot.getWorldId())return PlayerBotWorldTravel.move(bot,target,navigation);
  if(bot.isFlying())return navigation.move(target.x(),target.y(),target.z());
  if(!session.world()){state.flightReason="Companion aerial acquisition follows the owner's existing flight/order scope";return false;}
  if(!bot.getCommonData().isDaeva()){state.flightReason=flightRestriction(bot);return false;}
  float floor=geo.getZ(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ(),bot.getInstanceId());
  double distance=PositionUtil.getDistance(bot,target.x(),target.y(),target.z());
  int required=flightBudget(2*distance,flightSpeed(bot));
  if(flightRestriction(bot).isEmpty() && ground(bot.getZ(),floor) && bot.getLifeStats().getCurrentFp()>required
   && geo.canSee(bot,target.x(),target.y(),target.z(),IgnoreProperties.of(bot.getRace()))) {
   navigation.stop();if(bot.getMoveController().isInMove())return true;
   var launch=new Flight(bot.getWorldId(),bot.getInstanceId(),target.npc(),bot.getX(),bot.getY(),floor);
   if(!bot.getFlyController().startFly(true,false)){state.flightReason="Native FlyController rejected fresh takeoff admission";return false;}
   state.flight=launch;state.flightReason="";return navigation.move(target.x(),target.y(),target.z());
  }
  // Find actual geodata floor positions around the native source, then walk to
  // a legal flight-zone launch point. These are normal pathfinding candidates,
  // never forced coordinates or a replacement for native floor validation.
  long now=System.currentTimeMillis();
  state.failedLaunches.entrySet().removeIf(e->e.getValue()<=now);
  if(!target.equals(state.launchSource))state.failedLaunches.clear();
  if(!target.equals(state.launchSource) || now>=state.launchScan) {
   var launches=new ArrayList<PlayerBotWorldTravel.Place>();
   for(int ring=0;ring<=4;ring++)for(int angle=0;angle<(ring==0?1:8);angle++) {
    double radians=angle*Math.PI/4;float x=target.x()+(float)(ring*32*Math.cos(radians)),y=target.y()+(float)(ring*32*Math.sin(radians));
    float z=floorBelow(bot,x,y,target.z());
    if(!Float.isFinite(z) || !bot.hasAccess(AdminConfig.FREE_FLIGHT) && !flightZone(bot.getWorldId(),x,y,z))continue;
    var candidate=new PlayerBotWorldTravel.Place(target.map(),target.npc(),x,y,z);if(!state.failedLaunches.containsKey(candidate))launches.add(candidate);
   }
   state.launchFloor=launches.stream().min(Comparator.comparingDouble(p->PositionUtil.getDistance(bot,p.x(),p.y(),p.z()))).orElse(null);
   state.launchSource=target;state.launchScan=now+5000;
  }
  var launch=state.launchFloor;
  if(launch==null){state.flightReason="No geodata/body-supported floor in a native flight zone is available near aerial source "+target.npc();return false;}
  if(PositionUtil.getDistance(bot,launch.x(),launch.y(),launch.z())<=2) {
   state.flightReason=flightRestriction(bot);if(state.flightReason.isEmpty())state.flightReason=bot.getLifeStats().getCurrentFp()<=required?
    "Native flight points "+bot.getLifeStats().getCurrentFp()+"/"+bot.getLifeStats().getMaxFp()+" must exceed round-trip/landing budget "+required+
     (bot.getLifeStats().getMaxFp()<=required?"; an earned native flight-time equipment/buff dependency is required":"; wait for native ground regeneration"):
    "Native launch floor or air geometry is not currently safe";
   if(bot.getLifeStats().getCurrentFp()>required && ground(bot.getZ(),floor) && !geo.canSee(bot,target.x(),target.y(),target.z(),IgnoreProperties.of(bot.getRace()))) {
    state.failedLaunches.put(launch,now+60000);state.launchScan=0;
   }
   return false;
  }
  state.flightReason="Walking to a supported native flight-zone launch floor";boolean moved=navigation.move(launch.x(),launch.y(),launch.z());
  if(!moved){state.failedLaunches.put(launch,now+60000);state.launchScan=0;}return moved;
 }
 /** Owns only flights started for autonomous materials; never force landing while above unsupported floor. */
 private static boolean land(PlayerBotSession session,PlayerBotNavigation navigation) {
  var bot=session.bot();var state=ACTORS.get(bot.getObjectId());if(state==null || state.flight==null)return false;var flight=state.flight;
  if(bot.isDead() || !bot.isSpawned() || bot.getWorldId()!=flight.map() || bot.getInstanceId()!=flight.instance() || !bot.isFlying()) {state.flight=null;return false;}
  if(bot.isCasting() || !bot.canPerformMove() || bot.isUsingFlightTransporterOrWindstream() || bot.isTrading() || bot.isLooting()
   || bot.getController().hasScheduledTask(TaskId.ITEM_USE))return false;
  if(bot.getInteractionTask() instanceof GatheringTask task && task.isInProgress())bot.getObserveController().notifyMoveObservers();
  if(bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress())return false;
  var geo=GeoService.getInstance();float floor=floorBelow(bot,bot.getX(),bot.getY(),bot.getZ());
  if(ground(bot.getZ(),floor)) {navigation.stop();bot.getFlyController().endFly(true);state.flight=null;state.flightReason="";return true;}
  var target=new PlayerBotWorldTravel.Place(flight.map(),flight.source(),flight.x(),flight.y(),flight.z());
  if(!geo.canSee(bot,target.x(),target.y(),target.z(),IgnoreProperties.of(bot.getRace()))) {
   if(Float.isFinite(floor) && geo.canSee(bot,bot.getX(),bot.getY(),floor,IgnoreProperties.of(bot.getRace())))target=new PlayerBotWorldTravel.Place(bot.getWorldId(),flight.source(),bot.getX(),bot.getY(),floor);
   else {state.flightReason="Native air geometry currently blocks both return to launch and vertical safe landing; flight expiry/fall rules remain active";return false;}
  }
  state.flightReason="Returning through native flight movement to a supported landing floor";return navigation.move(target.x(),target.y(),target.z());
 }
 static boolean tick(PlayerBotSession session,PlayerBotNavigation navigation,boolean allowed) {
  if(!allowed || !PlayerBotConfig.QUESTS_ENABLED || !PlayerBotConfig.QUEST_MATERIALS
   || flightPending(session) && !PlayerBotConfig.QUEST_FLIGHT)return land(session,navigation);
  var needs=needed(session);if(needs.isEmpty())return land(session,navigation);
  var bot=session.bot();if(flightPending(session) && (GameServer.isShuttingDownSoon() || !bot.isSpawned() || bot.isDead() || bot.getInventory().isFull() || bot.isGatherRestricted()))return land(session,navigation);
  if(GameServer.isShuttingDownSoon() || !bot.isSpawned() || bot.isDead() || bot.isCasting() || bot.isTrading() || bot.isLooting() || bot.isUsingFlightTransporterOrWindstream()
   || bot.getController().isInCombat() || bot.getController().isUnderStance() || bot.getController().hasScheduledTask(TaskId.ITEM_USE)
   || bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress() || bot.getInventory().isFull() || bot.isGatherRestricted())return false;
  var state=actor(session,true);needs=needed(session);if(needs.isEmpty())return land(session,navigation);final var freshNeeds=needs;var data=catalog();long now=System.currentTimeMillis();
  if(state.flight!=null) {
   if(bot.getWorldId()!=state.flight.map() || bot.getInstanceId()!=state.flight.instance() || !bot.isFlying())state.flight=null;
   else if(bot.getLifeStats().getCurrentFp()<=flightBudget(PositionUtil.getDistance(bot,state.flight.x(),state.flight.y(),state.flight.z()),bot.getGameStats().getMovementSpeedFloat()))return land(session,navigation);
  }
  state.localRetries.entrySet().removeIf(e->e.getValue()<=now);
  var harvest=harvestGoal(bot,needs,data);var practice=trainingNodes(bot,harvest,data);
  var nodes=new ArrayList<Gatherable>();bot.getKnownList().forEachObject(o->{if(o instanceof Gatherable g && g.isSpawned() && g.getWorldId()==bot.getWorldId() && g.getInstanceId()==bot.getInstanceId()
   && bot.getKnownList().sees(g) && !state.localRetries.containsKey(g.getObjectId()) && g.getController().getGatheringPlayerId()==0 && (session.world() || session.owner().getWorldId()==g.getWorldId() && session.owner().getInstanceId()==g.getInstanceId()
    && PositionUtil.isInRange(session.owner(),g,25))) {var s=data.nodes().get(g.getObjectTemplate().getTemplateId());if(s!=null && skill(bot,s) && (output(bot,s,freshNeeds.keySet()) || practice.contains(s.id())))nodes.add(g);}});
  var gather=nodes.stream().min(Comparator.comparingDouble(g->PositionUtil.getDistance(bot,g))).orElse(null);
  if(gather!=null) {
   if(!PositionUtil.isInRange(bot,gather,3,false) || !GeoService.getInstance().canSee(bot,gather)) {
    boolean moved=gather.getSpawn().isAerialSpawn()?aerialMove(session,new PlayerBotWorldTravel.Place(gather.getWorldId(),gather.getObjectTemplate().getTemplateId(),gather.getX(),gather.getY(),gather.getZ()),navigation)
     :navigation.move(gather.getX(),gather.getY(),gather.getZ());if(!moved)state.localRetries.put(gather.getObjectId(),now+5000);return moved;
   }
   navigation.stop();if(bot.getMoveController().isInMove())return true;
   // Re-read the current node metadata and missing inventory immediately before native admission.
   var fresh=gatherSource(gather.getObjectTemplate());if(!skill(bot,fresh) || !output(bot,fresh,needed(session).keySet()) && !trainingNodes(bot,harvestGoal(bot,needed(session),data),data).contains(fresh.id()) || gather.getController().getGatheringPlayerId()!=0)return false;
   bot.setTarget(gather);gather.getController().startGathering(bot);
   boolean started=bot.getInteractionTask() instanceof GatheringTask task && task.isInProgress();if(!started)state.localRetries.put(gather.getObjectId(),now+5000);return started;
  }
  // A vanished/occupied/backed-off node hands owned flight back to safe landing,
  // never to a ground merchant, arbitrary world goal or an airborne idle loop.
  if(state.flight!=null)return land(session,navigation);
  var trainIds=trainers(bot,harvest);var nearby=new ArrayList<Npc>();bot.getKnownList().forEachNpc(n->{if(trainIds.contains(n.getNpcId()) && n.isSpawned() && !n.isDead()
   && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && !bot.isEnemy(n) && bot.getKnownList().sees(n)
   && (session.world() || PositionUtil.isInRange(session.owner(),n,25)))nearby.add(n);});
  var trainer=nearby.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
  if(trainer!=null) {
   if(!PositionUtil.isInTalkRange(bot,trainer) || !GeoService.getInstance().canSee(bot,trainer))return navigation.approach(trainer,2);
   if(!DialogService.isInteractionAllowed(bot,trainer))return false;navigation.stop();
   var care=PlayerBotQuestSync.state(session);String day=LocalDate.now().toString();if(!day.equals(care.spentDay)){care.spentDay=day;care.spent=0;care.save();}
   long before=bot.getInventory().getKinah(),allowance=PlayerBotCare.allowance(before,care.reserve,care.dailyBudget,care.spent);
   int points=bot.getSkillList().isSkillPresent(harvest.skill())?bot.getSkillList().getSkillLevel(harvest.skill()):0;Integer cost=Profession.getBySkillId(harvest.skill()).getUpgradeCost(points);
   if(cost==null || cost>allowance)return false;
   try{return PlayerBotQuestCrafting.train(bot,trainer,harvest.skill());}finally{long spent=Math.max(0,before-bot.getInventory().getKinah());if(spent>0){care.spent+=spent;care.save();}}
  }
  if(!session.world() || bot.isInInstance())return false;plan(session);
  state.excluded.entrySet().removeIf(e->e.getValue()<=now);
  var distances=PlayerBotWorldTravel.distances(bot);
  var destination=state.places.stream().filter(p->!state.excluded.containsKey(p)).min(Comparator.comparingDouble(distances::distance)).orElse(null);
  if(destination==null || !Double.isFinite(distances.distance(destination)))return false;
  double distance=distances.distance(destination);
  if(!destination.equals(state.destination)){state.destination=destination;state.progress=now;state.best=distance;}
  if(distance+1<state.best){state.best=distance;state.progress=now;}
  if(distance<=4 || now-state.progress>30000){state.excluded.put(destination,now+60000);state.destination=null;return false;}
  return data.aerial().contains(destination)?aerialMove(session,destination,navigation):PlayerBotWorldTravel.move(bot,destination,navigation);
 }
 static void close(Player bot){ACTORS.remove(bot.getObjectId());}
 private PlayerBotQuestMaterials() {}
}
