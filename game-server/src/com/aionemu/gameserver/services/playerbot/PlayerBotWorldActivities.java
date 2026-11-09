/* Autonomous quest/grind/travel purposes from pinned mod-playerbots TravelMgr,
 * RandomPlayerbotMgr and ItemUsageValue (037c01418b5d01506917a3db9b44fd56ac5f965c).
 * GPL-2.0-or-later; see third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.trade.TradeList;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.*;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Chooses destinations and actions only; native combat, quest handlers and loot award progress. */
final class PlayerBotWorldActivities {
 record Goal(int quest,PlayerBotQuestRoutes.Kind kind,PlayerBotWorldTravel.Place place,double distance) {}
 private record Request(int quest,PlayerBotQuestRoutes.Kind kind,Collection<Integer> npcs) {}
 private static final class State {
  int level=-1; com.aionemu.gameserver.model.PlayerClass playerClass; long deadSince,nextScan,lastProgress,nextSell,noActivitySince; double best=Double.POSITIVE_INFINITY;
  PlayerBotWorldTravel.Place vendor;long vendorSince;double vendorBest;
  int prerequisiteCursor;
  int prerequisiteRoot;
  Map<Integer,Integer> requiredRewards=Map.of();
  final PlayerBotTravelClock travelClock=new PlayerBotTravelClock(),vendorClock=new PlayerBotTravelClock();
  volatile Goal goal; boolean closed; final Map<String,Long> excluded=new HashMap<>();
  Goal rpg;long rpgUntil;boolean rpgArea,failedTravel,unsuitableMap;
  volatile String why="awaiting first admitted planning decision";
  long nextGoalHunt,lostSince,huntUntil;Goal goalHunt;
 }
 private static final java.util.concurrent.atomic.AtomicLong GOAL_HUNTS=new java.util.concurrent.atomic.AtomicLong();
 private static final java.util.concurrent.atomic.AtomicLong UPGRADE_HUNTS=new java.util.concurrent.atomic.AtomicLong();
 /** One bot hunts a monster for an equipment goal at a time, so bots wanting the same item split up. */
 private static final Map<Integer,State> GOAL_CLAIMS=new ConcurrentHashMap<>();
 static long goalHuntsStarted(){return GOAL_HUNTS.get();}
 private static final java.util.concurrent.atomic.AtomicLong HUNT_CHECKS=new java.util.concurrent.atomic.AtomicLong(),HUNT_NO_PLACE=new java.util.concurrent.atomic.AtomicLong(),HUNT_CLAIMED=new java.util.concurrent.atomic.AtomicLong();
 private static final Map<PlayerBotGoalHunts.Fit,java.util.concurrent.atomic.AtomicLong> HUNT_REJECTIONS=new EnumMap<>(PlayerBotGoalHunts.Fit.class);
 static {for(var fit:PlayerBotGoalHunts.Fit.values())HUNT_REJECTIONS.put(fit,new java.util.concurrent.atomic.AtomicLong());}
 /** Why hunts did or did not start, so a live report explains a zero. Only awake bots ever check. */
 // Each rejection counts checks seeing that reason, once per check; a check may see several reasons and still start a hunt.
 static String huntStats(){return "hunt checks="+HUNT_CHECKS.get()+", no monster source="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.NO_MONSTER_SOURCE).get()
  +", only in instances="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.ONLY_INSTANCES).get()+", above solo level="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.ABOVE_SOLO_LEVEL).get()
  +", elite above bot level="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.ELITE_ABOVE_LEVEL).get()+", not hostile="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.NOT_HOSTILE).get()
  +", already claimed="+HUNT_CLAIMED.get()+", no XP="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.NO_XP).get()+", unsupported monster="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.UNSUPPORTED).get()
  +", no world spawn="+HUNT_REJECTIONS.get(PlayerBotGoalHunts.Fit.NO_WORLD_PLACE).get()+", no reachable place="+HUNT_NO_PLACE.get()+", help requests opened="+PlayerBotFindGroup.goalHelpRequestsOpened()
  +", upgrade hunts started="+UPGRADE_HUNTS.get()+", "+PlayerBotUpgradeHunts.stats();}
 /** Travelling to its hunting ground, or already there fighting. */
 static boolean goalHuntTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.goalHunt!=null && (s.goalHunt.equals(s.goal) || s.goal==null && s.huntUntil>0);}
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 private record Destination(int quest,PlayerBotQuestRoutes.Kind kind,int npc) {}
 private record Location(int quest,PlayerBotQuestRoutes.Kind kind,PlayerBotWorldTravel.Place place) {}
 private record Region(int map,int x,int y,int z) {
  static Region of(Goal g){var p=g.place();return new Region(p.map(),(int)Math.floor(p.x()/50),(int)Math.floor(p.y()/50),(int)Math.floor(p.z()/50));}
 }
 /** TravelTarget::addVisitors/removeVisitors: maintain occupancy on commitment,
  * not by rebuilding every bot's formatted destination for every candidate. */
 static final class Visitors {
  private final Map<Destination,Integer> totals=new ConcurrentHashMap<>();
  private final Map<Location,Integer> points=new ConcurrentHashMap<>();
  private final Map<Region,Integer> regions=new ConcurrentHashMap<>();
  private static Destination destination(Goal g){return new Destination(g.quest(),g.kind(),g.place().npc());}
  private static Location location(Goal g){return new Location(g.quest(),g.kind(),g.place());}
  private static <T> void change(Map<T,Integer> counts,T key,int delta){
   int count=counts.getOrDefault(key,0)+delta;if(count<=0)counts.remove(key);else counts.put(key,count);
  }
  synchronized void replace(Goal old,Goal next){
   if(Objects.equals(old,next))return;
   if(old!=null){change(totals,destination(old),-1);change(points,location(old),-1);change(regions,Region.of(old),-1);}
   if(next!=null){change(totals,destination(next),1);change(points,location(next),1);change(regions,Region.of(next),1);}
  }
  Goal choose(List<Goal> goals,java.util.random.RandomGenerator random){
   Goal chosen=null;int priority=3,crowding=Integer.MAX_VALUE,count=0;
   for(var g:goals){
    if(!Double.isFinite(g.distance()) || g.distance()<0 || totals.getOrDefault(destination(g),0)>=visitorLimit(g)
     || pointExclusive(g) && points.getOrDefault(location(g),0)>0)continue;
    int p=g.quest()==0?2:g.kind()==PlayerBotQuestRoutes.Kind.REWARD?0:1;
    if(p>priority)continue;
    if(p<priority){priority=p;crowding=Integer.MAX_VALUE;count=0;chosen=null;}
    int occupied=regions.getOrDefault(Region.of(g),0);
    if(occupied>crowding)continue;
    if(occupied<crowding){crowding=occupied;count=0;chosen=null;}
    // Retain native priorities and visitor caps, then shuffle equally used
    // native 50m XYZ grind regions. Different quests/NPCs cannot hide crowding.
    if(random.nextInt(++count)==0)chosen=g;
   }return chosen;
  }
  /** Gear prefers distance while retaining this owner's native visitor admission limits. */
  Goal nearest(List<Goal> goals){return select(goals.stream().filter(g->totals.getOrDefault(destination(g),0)<visitorLimit(g)
   && (!pointExclusive(g) || points.getOrDefault(location(g),0)==0)).toList());}
 }
 private static final Visitors VISITORS=new Visitors();
 private static void goal(State s,Goal next){synchronized(s){if(s.closed)return;VISITORS.replace(s.goal,next);if(!Objects.equals(s.goal,next)){s.travelClock.reset();s.why=next==null?"destination released: awaiting selection":"selected "+next.kind()+(next.quest()==0?"":" quest")+(next==s.rpg?" service visit":"");}if(s.goalHunt!=null && !Objects.equals(next,s.goalHunt)){GOAL_CLAIMS.remove(s.goalHunt.place().npc(),s);s.goalHunt=null;s.huntUntil=0;}if(next!=s.rpg){s.rpg=null;s.rpgUntil=0;}s.goal=next;}}
 private static Map<Integer,Set<Integer>> starters;
 private static final Map<Integer,List<Map.Entry<Integer,Set<Integer>>>> STARTERS_BY_LEVEL=new HashMap<>();
 private static final Map<String,List<Integer>> GRIND=new HashMap<>();
 private static List<Integer> vendors;
 private static synchronized List<Integer> grindTemplates(Player bot){
  return GRIND.computeIfAbsent(bot.getRace()+":"+bot.getLevel(),key->DataManager.NPC_DATA.getNpcData().stream().filter(n->levelSuitable(bot.getLevel(),n.getLevel())
   && PlayerBotHunting.template(n)
   && (DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),bot.getTribe()) || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),bot.getTribe())))
   .map(n->n.getTemplateId()).toList());
 }
 private static synchronized List<Integer> vendors(){if(vendors==null)vendors=DataManager.NPC_DATA.getNpcData().stream().filter(n->n.supportsAction(DialogAction.BUY)).map(n->n.getTemplateId()).toList();return vendors;}
 private static State state(Player bot){return STATES.computeIfAbsent(bot.getObjectId(),PlayerBotWorldActivities::fresh);}
 static void begin(Player bot){state(bot);}
 /** Diagnostic: the last world-travel outcome, for the population report. */
 static String why(Player bot){var s=STATES.get(bot.getObjectId());return s==null?"planning reset: awaiting next admitted decision":s.why;}
 static void placementDeferred(Player bot,String reason){state(bot).why="placement deferred: "+reason;}
 static boolean idle(Player bot){var s=STATES.get(bot.getObjectId());return s==null || s.goal==null && s.vendor==null;}
 static int requiredReward(Player bot,int quest) {
  var s=STATES.get(bot.getObjectId());if(s==null || s.prerequisiteRoot==0)return -1;
  var root=bot.getQuestStateList().getQuestState(s.prerequisiteRoot);
  if(root!=null && root.getStatus()!=QuestStatus.LOCKED){s.prerequisiteRoot=0;s.requiredRewards=Map.of();return -1;}
  return s.requiredRewards.getOrDefault(quest,-1);
 }
 static boolean stranded(long now,long since,boolean destination){return !destination && since>0 && now-since>=60000;}
 static boolean stranded(long now,long since,boolean destination,boolean serviceArea,boolean failedTravel){return stranded(now,since,destination) && (!serviceArea || failedTravel);}
 static boolean stranded(Player bot,long now){var s=STATES.get(bot.getObjectId());return s!=null && (s.goal==null && s.vendor==null && waitingLost(now,s.lostSince) || stranded(now,s.noActivitySince,s.goal!=null || s.vendor!=null,s.rpgArea,s.failedTravel));}
 static void interacting(Player bot){var s=STATES.get(bot.getObjectId());if(s!=null){s.noActivitySince=0;s.failedTravel=false;}}
 static boolean rpgTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.rpg!=null && s.goal==s.rpg;}
 static boolean rpgPause(long now,long until){return until>now;}
 /** A selected native journey owns optional preparation until arrival. Vendor
  * work is separate; native quest dependencies remain the caller's authority. */
 static boolean committedTravel(Player bot){var s=STATES.get(bot.getObjectId());if(s==null || s.goal==null)return false;var p=s.goal.place();return bot.getWorldId()!=p.map() || PositionUtil.getDistance(bot,p.x(),p.y(),p.z())>6;}
 static boolean questTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.goal!=null && (s.goal.quest()!=0 || s.goal.kind()!=PlayerBotQuestRoutes.Kind.HUNT);}
 static boolean grindTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.goal!=null && s.goal.quest()==0 && s.goal.kind()==PlayerBotQuestRoutes.Kind.HUNT;}
 static void close(Player bot){PlayerBotBystanders.forget(bot);var s=STATES.remove(bot.getObjectId());if(s!=null)synchronized(s){goal(s,null);s.closed=true;}}
 /** Report the selected destination, never claim an intended fallback already ran. */
 static Map<String,Object> activity(Player bot) {
  var s=STATES.get(bot.getObjectId());var g=s==null ? null : s.goal;
   if(g==null)return Map.of("selected",false,"reason",why(bot));
  return Map.of("selected",true,"quest",g.quest(),"kind",s.rpg==g?"RPG":g.kind().name(),"npc",g.place().npc(),"map",g.place().map());
 }
 // GrindTargetValue uses XP-bearing normal targets and excludes solo targets
 // above +4. Native Aion's XPRewardEnum supplies the lower reward boundary.
 static boolean levelSuitable(int player,int npc){return player>=1 && npc>=1 && npc<=player+4
  && com.aionemu.gameserver.utils.stats.XPRewardEnum.xpRewardFrom(npc-player)>0;}
 static boolean stalled(long now,long last){return now-last>=30000;}
 static String key(Goal g){return g.quest()+":"+g.kind()+":"+g.place();}
 static Goal select(List<Goal> goals){return goals.stream().filter(g->Double.isFinite(g.distance()) && g.distance()>=0)
  .min(Comparator.<Goal>comparingInt(g->g.quest()==0 ? 2 : g.kind()==PlayerBotQuestRoutes.Kind.REWARD ? 0 : 1)
   .thenComparingDouble(Goal::distance).thenComparingInt(g->g.place().npc())).orElse(null);}
 static int visitorLimit(Goal g){return g.kind()==PlayerBotQuestRoutes.Kind.HUNT || g.kind()==PlayerBotQuestRoutes.Kind.OBJECT ? 100 : 15;}
 static boolean pointExclusive(Goal g){return g.quest()!=0 && (g.kind()==PlayerBotQuestRoutes.Kind.HUNT || g.kind()==PlayerBotQuestRoutes.Kind.OBJECT);}
 private static String destinationKey(Goal g){return g.quest()+":"+g.kind()+":"+g.place().npc();}
 static Goal spread(List<Goal> goals,Collection<Goal> claimed,java.util.random.RandomGenerator random) {
  var visitors=new Visitors();for(var g:claimed)visitors.replace(null,g);return visitors.choose(goals,random);
 }
 private static Goal chooseAvailable(List<Goal> goals){return VISITORS.choose(goals,java.util.concurrent.ThreadLocalRandom.current());}
 static void maintenance(PlayerBotSession session) {
  if(!session.world())return;Player bot=session.bot();State s=state(bot);long now=System.currentTimeMillis();
  if(bot.isDead()) {
   if(s.deadSince==0)s.deadSince=now;
   // Global revival belongs to the SQL-backed manager, including its instance,
   // human-party and queue checks. Visible activity must not bypass that owner.
   if(!PlayerBotPopulationManager.global(bot) && session.autonomous() && now-s.deadSince>=15000){s.deadSince=now;PlayerReviveService.bindRevive(bot);goal(s,null);}
   return;
  }
  s.deadSince=0;
  if(bot.isCasting() || bot.isTrading() || bot.isLooting() || bot.getController().isInCombat())return;
  if(s.level!=bot.getLevel() || s.playerClass!=bot.getPlayerClass()) {
   session.setRole(PlayerBotRules.roleFor(bot.getPlayerClass()));
   SkillLearnService.learnNewSkills(bot,1,bot.getLevel());s.level=bot.getLevel();s.playerClass=bot.getPlayerClass();goal(s,null);s.nextScan=0;
  }
 }
 /** A hunting bot fights near the spawn it came to; chasing further away ends the stay. */
 static final double HUNT_LEASH=60;
 /** Arrival at a hunt's spawn: the goal ends, but the claim and the prey stay for the configured time. */
 static boolean startHuntStay(State s,long now) {
  if(s.goalHunt==null || !s.goalHunt.equals(s.goal))return false;
  var hunt=s.goalHunt;goal(s,null);
  synchronized(s){if(s.closed)return true;s.goalHunt=hunt;GOAL_CLAIMS.put(hunt.place().npc(),s);}
  s.huntUntil=now+Math.max(60,com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_HUNT_STAY_SECONDS)*1000L;
  s.noActivitySince=0;s.why="hunting "+npcName(hunt.place().npc())+" for gear";return true;
 }
 /** True while the bot is still on its hunting ground. Time up, another map or a chase past the leash ends the stay. */
 static boolean huntStay(Player bot,State s,long now) {
  if(s.goal!=null || s.goalHunt==null || s.huntUntil<=0)return false;
  var hunt=s.goalHunt;
  if(now<s.huntUntil && bot.getWorldId()==hunt.place().map() && PlayerBotWorldTravel.distance(bot,hunt.place())<=HUNT_LEASH) {
   s.noActivitySince=0;s.why="hunting "+npcName(hunt.place().npc())+" for gear";return true;
  }
  endHunt(s);s.nextScan=0;return false;
 }
 private static void endHunt(State s){synchronized(s){if(s.goalHunt!=null)GOAL_CLAIMS.remove(s.goalHunt.place().npc(),s);s.goalHunt=null;s.huntUntil=0;}}
 private static String npcName(int npc){var t=DataManager.NPC_DATA.getNpcTemplate(npc);return t==null?"monster "+npc:t.getName();}
 /** The monster this bot came here to hunt, which may be an elite ordinary grinding leaves alone. */
 private static boolean goalPrey(Player bot,Npc n){var hunt=state(bot).goalHunt;return hunt!=null && hunt.place().npc()==n.getNpcId();}
 static Npc grind(Player bot) {
  List<Npc> choices=new ArrayList<>();
  bot.getKnownList().forEachNpc(n->{if(PlayerBotTargetEligibility.visible(bot,n) && !n.isDead() && n.isSpawned() && n.getMaster()==n && !n.isFlag() && bot.isEnemy(n)
   && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && levelSuitable(bot.getLevel(),n.getLevel())
   && (PlayerBotHunting.template(n.getObjectTemplate()) || goalPrey(bot,n)) && PlayerBotHunting.opportunistic(state(bot).goal,n.getNpcId())
   && n.getAggroList().stream().findAny().isEmpty() && PositionUtil.isInRange(bot,n,25) && GeoService.getInstance().canSee(bot,n))choices.add(n);});
  return choices.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
 }
 static Npc materialHunt(PlayerBotSession session) {
  if(!PlayerBotQuestObjectives.materialPull(session.bot()))return null;
  var bot=session.bot();var required=PlayerBotQuestMaterials.huntNpcIds(session);var choices=new ArrayList<Npc>();
  bot.getKnownList().forEachNpc(n->{if(PlayerBotTargetEligibility.visible(bot,n) && PlayerBotPartyBehavior.hostileObjective(bot,n,required)
   && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && n.getAggroList().stream().findAny().isEmpty()
   && PositionUtil.isInRange(bot,n,25) && GeoService.getInstance().canSee(bot,n) && PlayerBotPartyBehavior.isolated(bot,bot,n))choices.add(n);});
  return choices.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
 }
 static boolean surplus(PlayerBotSession session,Item item) {
  if(PlayerBotRoadMerchants.merchandise(session.bot(),item) || PlayerBotGearPolicy.referenced(session.bot(),item)
   || PlayerBotQuestSync.state(session).protectedItems.contains(item.getObjectId())
   || PlayerBotQuestCraftingDriver.protectedItem(session,item.getItemId()))return false;
  Player bot=session.bot();
  boolean upgrade=PlayerBotEquipment.upgrades(bot,session.combatRole()).stream().anyMatch(u->u.item()==item);
  // Keep valuable enhanced surplus for its exact market variant rather than ordinary NPC disposal.
  return item.getEnchantLevel()==0 && item.getTempering()==0 && !PlayerBotProfessions.forSale(bot,item.getItemId()) && PlayerBotCare.disposable(item,PlayerBotGearPolicy.generated(bot,item),upgrade,PlayerBotCare.worldQuestItem(bot,item.getItemId()),
   item.getItemTemplate().getRequiredLevel(bot.getPlayerClass())>bot.getLevel());
 }
 private static synchronized Map<Integer,Set<Integer>> starters() {
  if(starters!=null)return starters;var index=new TreeMap<Integer,Set<Integer>>();Set<Integer> ids=new TreeSet<>();DataManager.SPAWNS_DATA.addAllNpcIdsToSet(ids);
  for(int npc:ids){var q=QuestEngine.getInstance().getQuestNpc(npc);if(q!=null)for(int quest:q.getOnQuestStart())
   if(DataManager.QUEST_DATA.getQuestById(quest)!=null)index.computeIfAbsent(quest,k->new TreeSet<>()).add(npc);}
  starters=index;return starters;
 }
 /** TravelMgr's native destination catalog is static; character prerequisites remain dynamic. */
 private static synchronized List<Map.Entry<Integer,Set<Integer>>> starters(int level) {
  return STARTERS_BY_LEVEL.computeIfAbsent(level,key->starters().entrySet().stream().filter(e->{
   var template=DataManager.QUEST_DATA.getQuestById(e.getKey());
   return template!=null && template.getMinlevelPermitted()<=key && template.getMinlevelPermitted()>=key-5;
  }).map(e->Map.entry(e.getKey(),Collections.unmodifiableSet(new TreeSet<>(e.getValue())))).toList());
 }
 // Preserve the existing helper contract; the planner supplies one shared snapshot to the overload below.
 private static void add(Player bot,List<Goal> goals,int quest,PlayerBotQuestRoutes.Kind kind,Collection<Integer> npcs,State s,long now) {
  add(bot,goals,quest,kind,npcs,s,now,PlayerBotWorldTravel.distances(bot));
 }
 private static void add(Player bot,List<Goal> goals,int quest,PlayerBotQuestRoutes.Kind kind,Collection<Integer> npcs,State s,long now,PlayerBotWorldTravel.Distances distances) {
  add(bot,goals,quest,kind,npcs,s,now,distances,false);
 }
 private static void add(Player bot,List<Goal> goals,int quest,PlayerBotQuestRoutes.Kind kind,Collection<Integer> npcs,State s,long now,PlayerBotWorldTravel.Distances distances,boolean local) {
  boolean nearbyResident=PlayerBotPlayerPresence.resident(bot);
  for(int npc:npcs)for(var place:local?PlayerBotWorldTravel.near(npc,distances,900):PlayerBotWorldTravel.places(npc)) {
   // Phase 1.1: residents work inside the player area, for new work, accepted quests and earned rewards alike.
   // Off-area steps wait until the bot is no longer resident (or, asleep, Phase 1.2 turns rewards in unseen).
   if(nearbyResident && !PlayerBotPlayerPresence.snapshot().nearby(place.map(),place.x(),place.y(),place.z()))continue;
   double distance=distances.distance(place);if(!Double.isFinite(distance) || distance<0)continue;
   Goal g=new Goal(quest,kind,place,distance);
   if(s.excluded.getOrDefault(key(g),0L)<=now)goals.add(g);
  }
 }
 /** A goal that left a bot leaping is not retried for a long while, including after the rebuild a move to a new camp causes. */
 static final long LOST_GOAL_MS=1800000,LOST_WAIT_MS=120000;
 private static final Map<Integer,Map<String,Long>> LOST_GOALS=new ConcurrentHashMap<>();
 private static void rememberLostGoal(Player bot,State s,String key,long until) {
  s.excluded.put(key,until);
  LOST_GOALS.computeIfAbsent(bot.getObjectId(),id->new ConcurrentHashMap<>()).put(key,until);
 }
 private static State fresh(int id) {
  var s=new State();var lost=LOST_GOALS.get(id);
  if(lost!=null){long now=System.currentTimeMillis();lost.values().removeIf(until->until<=now);if(lost.isEmpty())LOST_GOALS.remove(id,lost);else s.excluded.putAll(lost);}
  return s;
 }
 static boolean waitingLost(long now,long since){return since>0 && now-since<LOST_WAIT_MS;}
 /** The field-camp move never happens while a player is near (it would be seen), so a lost bot there waited the whole
  * two minutes, failed its next goal and waited again: it stood idle in front of the owner (9 Nov). Near a player it
  * tries another goal after a short pause instead. */
 static final long LOST_VISIBLE_RETRY_MS=20000;
 static boolean lostHold(long now,long since,boolean humanNear){return since>0 && now-since<(humanNear?LOST_VISIBLE_RETRY_MS:LOST_WAIT_MS);}
 private static final boolean[] LOCAL_THEN_ALL={true,false},LOCAL={true},ALL={false};
 private static Goal available(Player bot,List<Request> requests,State s,long now,PlayerBotWorldTravel.Distances distances) {
  return available(bot,requests,s,now,distances,LOCAL_THEN_ALL);
 }
 private static Goal available(Player bot,List<Request> requests,State s,long now,PlayerBotWorldTravel.Distances distances,boolean[] passes) {
  // First query immutable spatial cells. If local points are excluded, full or
  // unreachable, the complete catalog remains the fallback for this priority.
  // Locality must not demote an already earned reward behind another quest.
  for(int priority=0;priority<3;priority++)for(boolean local:passes) {
   var goals=new ArrayList<Goal>();
   for(var r:requests)if((r.quest()==0?2:r.kind()==PlayerBotQuestRoutes.Kind.REWARD?0:1)==priority)add(bot,goals,r.quest(),r.kind(),r.npcs(),s,now,distances,local);
   var chosen=chooseAvailable(goals);if(chosen!=null)return chosen;
  }
  return null;
 }
 /** Bots kept near a player stay on that map, but may cross it for gear they want. Other bots may travel anywhere. */
 private static Goal goalPlace(Player bot,List<Integer> npcs,State s,long now,PlayerBotWorldTravel.Distances distances) {
  return goalPlace(bot,npcs,s,now,distances,place->true);
 }
 private static Goal goalPlace(Player bot,List<Integer> npcs,State s,long now,PlayerBotWorldTravel.Distances distances,java.util.function.Predicate<PlayerBotWorldTravel.Place> eligible) {
  boolean resident=PlayerBotPlayerPresence.resident(bot);
  for(boolean local:LOCAL_THEN_ALL) {
   var goals=new ArrayList<Goal>();
   for(int npc:npcs)for(var place:local?PlayerBotWorldTravel.near(npc,distances,900):PlayerBotWorldTravel.places(npc)) {
    if(!eligible.test(place))continue;
    double distance=distances.distance(place);
    if(!Double.isFinite(distance) || distance<0 || !goalPlaceAllowed(resident,bot.getWorldId(),place.map()))continue;
    var goal=new Goal(0,PlayerBotQuestRoutes.Kind.HUNT,place,distance);
    if(s.excluded.getOrDefault(key(goal),0L)<=now)goals.add(goal);
   }
   var chosen=VISITORS.nearest(goals);if(chosen!=null)return chosen;
  }
  return null;
 }
 static boolean goalPlaceAllowed(boolean resident,int botMap,int placeMap){return !resident || botMap==placeMap;}
 private static Goal upgradePlace(Player bot,List<PlayerBotUpgradeHunts.Opportunity> hunts,State s,long now,PlayerBotWorldTravel.Distances distances) {
  // Equal gains share the normal nearest-place selector. Map preference precedes gain, even beyond the local radius.
  for(int from=0;from<hunts.size();) {
   var first=hunts.get(from);boolean own=first.map()==bot.getWorldId();int to=from+1;
   while(to<hunts.size() && (hunts.get(to).map()==bot.getWorldId())==own && Double.compare(hunts.get(to).gain(),first.gain())==0)to++;
   var npcs=hunts.subList(from,to).stream().map(PlayerBotUpgradeHunts.Opportunity::npc).distinct().toList();
   var places=new HashSet<PlayerBotWorldTravel.Place>();for(var hunt:hunts.subList(from,to))places.addAll(hunt.places());
   var chosen=goalPlace(bot,npcs,s,now,distances,places::contains);
   if(chosen!=null)return chosen;from=to;
  }
  return null;
 }
 /** Occasional solo gear trips; hard local sources nominate a request through the normal field LFG worker. */
 private static Goal goalHunt(Player bot,State s,long now,PlayerBotWorldTravel.Distances distances) {
  if(!com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.ECONOMY_ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_HUNT || now<s.nextGoalHunt)return null;
  var candidates=PlayerBotGoalHunts.candidates(bot);
  boolean resident=PlayerBotPlayerPresence.resident(bot);
  var solo=candidates.solo().stream().filter(npc->PlayerBotWorldTravel.places(npc).stream().anyMatch(place->goalPlaceAllowed(resident,bot.getWorldId(),place.map()))).toList();
  var npcs=solo.stream().filter(npc->GOAL_CLAIMS.getOrDefault(npc,s)==s).toList();
  // Phase 2: the upgrade search (avg 17 ms) waits until a human can see the bot or its rotation turn comes.
  boolean deferred=solo.isEmpty() && PlayerBotDetailTier.near(bot.getObjectId());
  if(deferred)PlayerBotDetailTier.deferredUpgrade();
  boolean upgrade=solo.isEmpty() && !deferred;
  var alternatives=upgrade?PlayerBotUpgradeHunts.candidates(bot):new PlayerBotUpgradeHunts.Candidates(List.of(),Set.of());
  var hunts=alternatives.hunts().stream().filter(h->GOAL_CLAIMS.getOrDefault(h.npc(),s)==s).toList();
  // Goals are refreshed after a bot first decides, so having none yet costs a short wait, not the whole interval.
  HUNT_CHECKS.incrementAndGet();
  for(var reason:candidates.rejected())HUNT_REJECTIONS.get(reason).incrementAndGet();
  for(var reason:alternatives.rejected())if(!candidates.rejected().contains(reason))HUNT_REJECTIONS.get(reason).incrementAndGet();
  if(npcs.size()<solo.size() || hunts.size()<alternatives.hunts().size())HUNT_CLAIMED.incrementAndGet();
  // Preserve the existing saved-goal help nomination independently of the near-term fallback.
  if(!candidates.help().isEmpty() && candidates.solo().stream().noneMatch(npc->GOAL_CLAIMS.getOrDefault(npc,s)==s))PlayerBotFindGroup.requestGoalHelp(bot);
  if(npcs.isEmpty() && hunts.isEmpty()){
   s.goalHunt=null;s.nextGoalHunt=now+60000;return null;
  }
  // Phase 4: hunters hunt more often, questers less; the next quest step still follows when no hunt is due.
  s.nextGoalHunt=now+(long)(Math.max(60,com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_HUNT_INTERVAL_SECONDS)*1000L*PlayerBotLeanings.pace(bot,PlayerBotLeanings.Activity.HUNT));
  var chosen=upgrade?upgradePlace(bot,hunts,s,now,distances):goalPlace(bot,npcs,s,now,distances);
  // Bots are only awake while a player is on their map, so waiting the full interval after a miss could lose the chance.
  if(chosen==null){HUNT_NO_PLACE.incrementAndGet();s.nextGoalHunt=Math.min(s.nextGoalHunt,now+300000);}
  if(chosen!=null && GOAL_CLAIMS.putIfAbsent(chosen.place().npc(),s) instanceof State other && other!=s){HUNT_CLAIMED.incrementAndGet();chosen=null;}
  s.goalHunt=chosen;if(chosen!=null){GOAL_HUNTS.incrementAndGet();if(upgrade)UPGRADE_HUNTS.incrementAndGet();PlayerBotLeanings.did(bot,PlayerBotLeanings.Activity.HUNT);}
  return chosen;
 }
 private static Goal choose(Player bot,State s,long now) {
  s.rpgArea=false;s.unsuitableMap=false;
  if(s.prerequisiteRoot!=0) {
   var root=bot.getQuestStateList().getQuestState(s.prerequisiteRoot);
   if(root!=null && root.getStatus()!=QuestStatus.LOCKED || !PlayerBotQuestSync.wanted(bot,bot,s.prerequisiteRoot)
    || QuestService.checkStartConditions(bot,s.prerequisiteRoot,false)){s.prerequisiteRoot=0;s.requiredRewards=Map.of();}
   else {var plan=PlayerBotQuestPrerequisites.plan(bot,s.prerequisiteRoot);s.requiredRewards=plan.reachable()?plan.requiredRewards():Map.of();}
  }
  var distances=PlayerBotWorldTravel.distances(bot);List<Request> requests=new ArrayList<>();
  for(var q:bot.getQuestStateList().getUncompletedQuests())for(var kind:PlayerBotQuestRoutes.Kind.values())
   requests.add(new Request(q.getQuestId(),kind,PlayerBotQuestRoutes.ids(bot,bot,q.getQuestId(),kind)));
  // Gear goals would never win against a quest log that is never empty. The hunt timer keeps them occasional.
  Goal wanted=goalHunt(bot,s,now,distances);if(wanted!=null)return wanted;
  Goal active=available(bot,requests,s,now,distances);if(active!=null)return active;
  requests.clear();
  var blockedRoots=new ArrayList<Integer>();
  if(bot.getQuestStateList().getUncompletedQuests().size()<20 && com.aionemu.gameserver.configs.main.PlayerBotConfig.QUESTS_ENABLED)for(var e:starters(bot.getLevel())) {
   var template=DataManager.QUEST_DATA.getQuestById(e.getKey());
   if(template.getMinlevelPermitted()>bot.getLevel() || template.getMinlevelPermitted()<bot.getLevel()-5
    || !PlayerBotQuests.autonomous(bot,e.getKey()) || !PlayerBotQuestExchange.eligible(bot,e.getKey())
    || !PlayerBotQuestSync.wanted(bot,bot,e.getKey()))continue;
   if(QuestService.checkStartConditions(bot,e.getKey(),false))requests.add(new Request(0,PlayerBotQuestRoutes.Kind.CONVERSATION,e.getValue()));
   else blockedRoots.add(e.getKey());
  }
  // Rotate graph work across decisions instead of expanding every blocked root
  // for every world bot in one tick. The complete catalogue remains eligible.
  // WoW random bots quest and grind where they stand (TravelMgr prefers near
  // targets). A new quest giver or grind spot in this area beats a long trip to
  // another region; distant starters are only chosen when nothing is local.
  var hunting=List.of(new Request(0,PlayerBotQuestRoutes.Kind.HUNT,grindTemplates(bot)));
  Goal localStart=available(bot,requests,s,now,distances,LOCAL);if(localStart!=null)return localStart;
  Goal localHunt=available(bot,hunting,s,now,distances,LOCAL);if(localHunt!=null)return localHunt;
  Goal directlyAvailable=available(bot,requests,s,now,distances,ALL);if(directlyAvailable!=null)return directlyAvailable;
  if(!blockedRoots.isEmpty()) {
   int root=blockedRoots.get(Math.floorMod(s.prerequisiteCursor++,blockedRoots.size()));
   var prerequisites=PlayerBotQuestPrerequisites.plan(bot,root);
   var prerequisiteRequests=new ArrayList<Request>();
   if(prerequisites.reachable())for(int id:prerequisites.starters())if(PlayerBotQuestSync.wanted(bot,bot,id)
    && PlayerBotQuests.autonomous(bot,id) && PlayerBotQuestExchange.eligible(bot,id)
    && QuestService.checkStartConditions(bot,id,false))
     prerequisiteRequests.add(new Request(0,PlayerBotQuestRoutes.Kind.CONVERSATION,starters().getOrDefault(id,Set.of())));
   Goal chosen=available(bot,prerequisiteRequests,s,now,distances);
   if(chosen!=null) {
    if(prerequisiteRequests.stream().anyMatch(r->r.npcs().contains(chosen.place().npc()))){s.prerequisiteRoot=root;s.requiredRewards=prerequisites.requiredRewards();}
    return chosen;
   }
  }
  Goal quest=available(bot,requests,s,now,distances);if(quest!=null)return quest;
  Goal hunt=available(bot,hunting,s,now,distances,ALL);
   if(hunt!=null)return hunt;
   // Service NPCs on an unsuitable map must not suppress field rescue forever.
   // Reuse the immutable race/level camp cache, only on the five-second scan.
   s.unsuitableMap=!PlayerBotWorldPlacement.focusCompatibleOnMap(bot.getRace(),bot.getLevel(),bot.getWorldId());
   return s.unsuitableMap?null:rpg(bot,s,now);
 }
 /** Pinned RpgStayAction/RpgSubAction: actual nearby NPCs, real ground travel,
  * finite arrival pauses and visitor/ignore custody. No purchases or quest
  * progress are fabricated. Existing useful services and quests run first. */
 private static boolean serviceNpc(Player bot,Npc npc) {
  if(npc==null || npc.isDead() || !PlayerBotTargetEligibility.visible(bot,npc) || bot.isEnemy(npc)
   || npc.getWorldId()!=bot.getWorldId() || npc.getInstanceId()!=bot.getInstanceId())return false;
  var template=npc.getObjectTemplate();
  return template.supportsAction(DialogAction.BUY) || template.supportsAction(DialogAction.OPEN_PERSONAL_WAREHOUSE)
   || template.supportsAction(DialogAction.RETRIEVE_CHAR_WAREHOUSE)
   || DataManager.TELEPORTER_DATA.getTeleporterTemplateByNpcId(npc.getNpcId())!=null;
 }
 private static Goal rpg(Player bot,State s,long now) {
  var choices=new ArrayList<Goal>();s.rpgArea=false;
  bot.getKnownList().forEachNpc(n->{
   if(!serviceNpc(bot,n))return;
   double distance=PositionUtil.getDistance(bot,n);if(distance>100)return;
   s.rpgArea=true;
   if(!com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_RPG_ENABLED)return;
   var p=new PlayerBotWorldTravel.Place(n.getWorldId(),n.getNpcId(),n.getX(),n.getY(),n.getZ());
   var candidate=new Goal(0,PlayerBotQuestRoutes.Kind.CONVERSATION,p,distance);
   if(s.excluded.getOrDefault(key(candidate),0L)<=now)choices.add(candidate);
  });
  Goal next=chooseAvailable(choices);s.rpg=next;s.rpgUntil=0;return next;
 }
 private static boolean rpgArrived(Player bot,Goal target) {
  boolean[] arrived={false};bot.getKnownList().forEachNpc(n->{
   if(n.getNpcId()==target.place().npc() && serviceNpc(bot,n)
    && PositionUtil.getDistance(n,target.place().x(),target.place().y(),target.place().z())<6
    && PositionUtil.isInTalkRange(bot,n) && DialogService.isInteractionAllowed(bot,n)
    && GeoService.getInstance().canSee(bot,n))arrived[0]=true;
  });return arrived[0];
 }
 static void prepare(Player bot,PlayerBotNavigation navigation) {
  State s=state(bot);long now=System.currentTimeMillis();s.excluded.entrySet().removeIf(e->e.getValue()<=now);
  // On its hunting ground a bot stays and fights the monster it came for. Ordinary grinding never attacks
  // that elite, and picking the next quest at once would end the hunt after a kill or two.
  if(huntStay(bot,s,now))return;
  if(s.rpg!=null && !com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_RPG_ENABLED){goal(s,null);s.nextScan=0;navigation.stop();}
  // Arrival was already admitted by native range/visibility guards. Waiting
  // needs no repeated geometry ray or transport-graph construction each tick.
  if(s.rpg!=null && s.goal==s.rpg && s.rpgUntil>0) {
    if(rpgPause(now,s.rpgUntil) && bot.getWorldId()==s.goal.place().map()
     && PositionUtil.getDistance(bot,s.goal.place().x(),s.goal.place().y(),s.goal.place().z())<=6){s.why="service visit: finite arrival pause";return;}
   s.excluded.put(key(s.goal),now+30000);goal(s,null);s.nextScan=0;navigation.stop();return;
  }
  if(s.goal!=null && s.goal.quest()!=0 && !PlayerBotQuestRoutes.ids(bot,bot,s.goal.quest(),s.goal.kind()).contains(s.goal.place().npc())){goal(s,null);s.nextScan=0;}
  if(s.goal!=null && PlayerBotWorldTravel.distance(bot,s.goal.place())<=6 && (s.goal!=s.rpg || rpgArrived(bot,s.goal))){
   if(s.goal==s.rpg) {
    int delay=com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_RPG_PAUSE_MS;
    if(delay<1000 || delay>60000)throw new IllegalArgumentException("World RPG pause must be 1000..60000 milliseconds");
    s.rpgUntil=now+delay;s.noActivitySince=0;s.failedTravel=false;s.why="service visit: finite arrival pause";navigation.stop();return;
   }
   // Hand movement back to the native quest/combat/loot actions before entering
   // the NPC's exact spawn. A dead/unavailable NPC must not trap the planner.
   // Yield this callback to local native actions. The next planner callback may
   // choose another destination immediately if the hinted actor is unavailable.
   if(startHuntStay(s,now)){navigation.stop();return;}
   s.excluded.put(key(s.goal),now+30000);goal(s,null);s.nextScan=0;navigation.stop();return;
  }
  if(s.goal!=null) {
   double distance=PlayerBotWorldTravel.distance(bot,s.goal.place());
   if(distance<s.best-2){if(Double.isFinite(s.best)){s.noActivitySince=0;s.failedTravel=false;}s.best=distance;s.lastProgress=now;s.travelClock.reset();}
   if(navigation.transportCongested()){s.excluded.put(key(s.goal),now+45000);goal(s,null);s.nextScan=0;navigation.stop();return;}
   boolean waiting=navigation.waitingForTerrainWork() || navigation.waitingForTransport();
   if(!waiting && navigation.progressing(now)){s.lastProgress=now;s.noActivitySince=0;s.failedTravel=false;s.travelClock.reset();}
   if(s.travelClock.stalled(now,s.lastProgress,waiting)){s.failedTravel=true;s.excluded.put(key(s.goal),now+120000);goal(s,null);s.nextScan=0;navigation.stop();}
  }
   if(s.goal==null && now>=s.nextScan && !lostHold(now,s.lostSince,s.lostSince>0 && PlayerBotWorldPlacement.nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()))){s.nextScan=now+5000;goal(s,choose(bot,s,now));s.best=Double.POSITIVE_INFINITY;s.lastProgress=now;
    if(s.goal==null)s.why=s.unsuitableMap?"no reachable quest or XP target: map has no level-appropriate camp; awaiting field placement"
     :s.rpgArea?"service area: eligible destinations busy or cooling down":"no reachable eligible quest, XP target or service; awaiting retry or field placement";}
  // Selecting a replacement goal is intent, not earned movement. Preserve a
  // failed-origin episode across retries; genuine movement or native local
  // interaction clears it. Queued live goals still protect against relocation.
  if(s.goal==null && s.noActivitySince==0)s.noActivitySince=now;
 }
 /** Walking after a kill is not an NPC interaction. Keep the native combat
  * timer for dialogs/transport, but stop travel for actual current attackers. */
 static boolean travelThreatened(Player bot) {
  if(bot.getAggroList().stream().anyMatch(a->a.getHate()>0 && liveHostile(bot,a.getAttacker())))return true;
  var owned=bot.getSummon();
  var summon=owned!=null && !owned.isDead() && owned.isSpawned() ? owned : null;
  if(summon!=null && summon.getAggroList().stream().anyMatch(a->a.getHate()>0 && liveHostile(bot,a.getAttacker())))return true;
  boolean[] threatened={false};
  bot.getKnownList().forEachObject(object->{
   if(object instanceof Creature enemy && liveHostile(bot,enemy)
    && (enemy.getAggroList().isHating(bot) || summon!=null && enemy.getAggroList().isHating(summon)))threatened[0]=true;
  });
  return threatened[0];
 }
 private static boolean liveHostile(Player bot,Creature enemy) {
  return enemy!=null && !enemy.isDead() && enemy.isSpawned() && enemy.getWorldId()==bot.getWorldId()
   && enemy.getInstanceId()==bot.getInstanceId() && bot.getKnownList().sees(enemy) && bot.isEnemy(enemy);
 }
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation) {
  Player bot=session.bot();if(!session.autonomous() || bot.isDead() || bot.isTrading() || bot.isCasting() || bot.isLooting())return false;
  if(travelThreatened(bot)){state(bot).why="travel paused: threatened";return false;}
   // Session.tick planned before action arbitration; do not repeat native goal
   // validation, catalog work or arrival handling in the winning action.
   State s=state(bot);
   Goal selected=s.goal;if(selected==null)return false;
  if(selected==s.rpg && rpgPause(System.currentTimeMillis(),s.rpgUntil)){s.why="rpg pause";return true;}
  boolean moved=PlayerBotWorldTravel.move(bot,selected.place(),navigation);
  s.why=(moved?"travelling to ":"travel refused to ")+selected.kind()+(selected.quest()==0?"":" quest")+(bot.getWorldId()==selected.place().map()?"":" other map");
  // TravelTarget cooldown purpose: a completed bounded search with no earned
  // route is an unavailable destination, not fresh progress for another retry.
  // Explicit queue waits, native safety pauses and earned prefixes retain custody.
  if(!moved && navigation.routeFailed() && s.goal==selected && !s.closed) {
   s.failedTravel=true;if(s.noActivitySince==0)s.noActivitySince=System.currentTimeMillis();
   long failedAt=System.currentTimeMillis();
   if(navigation.takeGoalLost())rememberLostGoal(bot,s,key(selected),failedAt+LOST_GOAL_MS);
   else s.excluded.put(key(selected),failedAt+120000);
   boolean lost=navigation.takeLost();if(lost)s.lostSince=failedAt;
   goal(s,null);s.nextScan=0;navigation.stop();
   // Set after the release, which writes its own "awaiting selection" text over any earlier reason.
   if(lost)s.why="lost: waiting to move to a field camp";
  }
  return moved;
 }
 static boolean sell(PlayerBotSession session,PlayerBotNavigation navigation) {
  Player bot=session.bot();State s=state(bot);long now=System.currentTimeMillis();
  if(!session.autonomous() || bot.getInventory().getFreeSlots()>8 || now<s.nextSell)return false;
  List<Item> items=bot.getInventory().getItems().stream().filter(i->i.isSellable() && surplus(session,i)).limit(8).toList();if(items.isEmpty())return false;
  List<Npc> vendors=new ArrayList<>();bot.getKnownList().forEachNpc(n->{if(!n.isDead() && n.isSpawned() && !bot.isEnemy(n)
   && n.getObjectTemplate().supportsAction(DialogAction.BUY) && DialogService.isInteractionAllowed(bot,n))vendors.add(n);});
  var vendor=vendors.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
  if(vendor==null){
   var distances=PlayerBotWorldTravel.distances(bot);
   var destination=vendors().stream().filter(id->{var n=DataManager.NPC_DATA.getNpcTemplate(id);return n.getRace()==bot.getRace() || n.getRace()==com.aionemu.gameserver.model.Race.PC_ALL;})
    .flatMap(id->PlayerBotWorldTravel.places(id).stream()).filter(p->s.excluded.getOrDefault("vendor:"+p,0L)<=now)
    .filter(p->Double.isFinite(distances.distance(p))).min(Comparator.comparingDouble(distances::distance)).orElse(null);
   if(destination==null){s.nextSell=now+30000;return false;}
   // A failed vendor route cannot permanently displace questing/combat.
   if(s.vendor==null || !s.vendor.equals(destination)){s.vendor=destination;s.vendorSince=now;s.vendorBest=Double.POSITIVE_INFINITY;s.vendorClock.reset();}
   double distance=PlayerBotWorldTravel.distance(bot,destination);if(distance<s.vendorBest-2){s.vendorBest=distance;s.vendorSince=now;s.vendorClock.reset();}
   boolean waiting=navigation.waitingForTerrainWork();
   if(!waiting && navigation.progressing(now)){s.vendorSince=now;s.vendorClock.reset();}
   if(s.vendorClock.stalled(now,s.vendorSince,waiting)){s.excluded.put("vendor:"+destination,now+120000);s.vendor=null;s.nextSell=now+5000;return false;}
   return PlayerBotWorldTravel.move(bot,destination,navigation);
  }
  if(!PositionUtil.isInTalkRange(bot,vendor) || !GeoService.getInstance().canSee(bot,vendor))return navigation.approach(vendor,2);
  navigation.stop();s.vendor=null;s.nextSell=now+30000;var list=new TradeList(vendor.getObjectId());for(var item:items)list.addItem(item.getObjectId(),item.getItemCount());
  return TradeService.performSellToShop(bot,list,null);
 }
 private PlayerBotWorldActivities(){}
}
