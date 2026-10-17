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
  Goal rpg;long rpgUntil;boolean rpgArea,failedTravel;
 }
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
 }
 private static final Visitors VISITORS=new Visitors();
 private static void goal(State s,Goal next){synchronized(s){if(s.closed)return;VISITORS.replace(s.goal,next);if(!Objects.equals(s.goal,next))s.travelClock.reset();if(next!=s.rpg){s.rpg=null;s.rpgUntil=0;}s.goal=next;}}
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
 private static State state(Player bot){return STATES.computeIfAbsent(bot.getObjectId(),id->new State());}
 static boolean idle(Player bot){var s=STATES.get(bot.getObjectId());return s==null || s.goal==null && s.vendor==null;}
 static int requiredReward(Player bot,int quest) {
  var s=STATES.get(bot.getObjectId());if(s==null || s.prerequisiteRoot==0)return -1;
  var root=bot.getQuestStateList().getQuestState(s.prerequisiteRoot);
  if(root!=null && root.getStatus()!=QuestStatus.LOCKED){s.prerequisiteRoot=0;s.requiredRewards=Map.of();return -1;}
  return s.requiredRewards.getOrDefault(quest,-1);
 }
 static boolean stranded(long now,long since,boolean destination){return !destination && since>0 && now-since>=60000;}
 static boolean stranded(long now,long since,boolean destination,boolean serviceArea,boolean failedTravel){return stranded(now,since,destination) && (!serviceArea || failedTravel);}
 static boolean stranded(Player bot,long now){var s=STATES.get(bot.getObjectId());return s!=null && stranded(now,s.noActivitySince,s.goal!=null || s.vendor!=null,s.rpgArea,s.failedTravel);}
 static void interacting(Player bot){var s=STATES.get(bot.getObjectId());if(s!=null){s.noActivitySince=0;s.failedTravel=false;}}
 static boolean rpgTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.rpg!=null && s.goal==s.rpg;}
 static boolean rpgPause(long now,long until){return until>now;}
 /** A selected native journey owns optional preparation until arrival. Vendor
  * work is separate; native quest dependencies remain the caller's authority. */
 static boolean committedTravel(Player bot){var s=STATES.get(bot.getObjectId());if(s==null || s.goal==null)return false;var p=s.goal.place();return bot.getWorldId()!=p.map() || PositionUtil.getDistance(bot,p.x(),p.y(),p.z())>6;}
 static boolean questTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.goal!=null && (s.goal.quest()!=0 || s.goal.kind()!=PlayerBotQuestRoutes.Kind.HUNT);}
 static boolean grindTravel(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.goal!=null && s.goal.quest()==0 && s.goal.kind()==PlayerBotQuestRoutes.Kind.HUNT;}
 static void close(Player bot){var s=STATES.remove(bot.getObjectId());if(s!=null)synchronized(s){goal(s,null);s.closed=true;}}
 /** Report the selected destination, never claim an intended fallback already ran. */
 static Map<String,Object> activity(Player bot) {
  var s=STATES.get(bot.getObjectId());var g=s==null ? null : s.goal;
  if(g==null)return Map.of("selected",false,"reason","No world destination currently selected; local actions may still run");
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
   if(session.autonomous() && now-s.deadSince>=15000){s.deadSince=now;PlayerReviveService.bindRevive(bot);goal(s,null);}
   return;
  }
  s.deadSince=0;
  if(bot.isCasting() || bot.isTrading() || bot.isLooting() || bot.getController().isInCombat())return;
  if(s.level!=bot.getLevel() || s.playerClass!=bot.getPlayerClass()) {
   session.setRole(PlayerBotRules.roleFor(bot.getPlayerClass()));
   SkillLearnService.learnNewSkills(bot,1,bot.getLevel());s.level=bot.getLevel();s.playerClass=bot.getPlayerClass();goal(s,null);s.nextScan=0;
  }
 }
 static Npc grind(Player bot) {
  List<Npc> choices=new ArrayList<>();
  bot.getKnownList().forEachNpc(n->{if(PlayerBotTargetEligibility.visible(bot,n) && !n.isDead() && n.isSpawned() && n.getMaster()==n && !n.isFlag() && bot.isEnemy(n)
   && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && levelSuitable(bot.getLevel(),n.getLevel())
   && PlayerBotHunting.template(n.getObjectTemplate()) && PlayerBotHunting.opportunistic(state(bot).goal,n.getNpcId())
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
  Player bot=session.bot();
  boolean upgrade=PlayerBotEquipment.upgrades(bot,session.combatRole()).stream().anyMatch(u->u.item()==item);
  return PlayerBotCare.disposable(item,PlayerBotGearPolicy.generated(bot,item),upgrade,PlayerBotCare.worldQuestItem(bot,item.getItemId()),
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
  for(int npc:npcs)for(var place:local?PlayerBotWorldTravel.near(npc,distances,900):PlayerBotWorldTravel.places(npc)) {
   double distance=distances.distance(place);if(!Double.isFinite(distance) || distance<0)continue;
   Goal g=new Goal(quest,kind,place,distance);
   if(s.excluded.getOrDefault(key(g),0L)<=now)goals.add(g);
  }
 }
 private static Goal available(Player bot,List<Request> requests,State s,long now,PlayerBotWorldTravel.Distances distances) {
  // First query immutable spatial cells. If local points are excluded, full or
  // unreachable, the complete catalog remains the fallback for this priority.
  // Locality must not demote an already earned reward behind another quest.
  for(int priority=0;priority<3;priority++)for(boolean local:new boolean[]{true,false}) {
   var goals=new ArrayList<Goal>();
   for(var r:requests)if((r.quest()==0?2:r.kind()==PlayerBotQuestRoutes.Kind.REWARD?0:1)==priority)add(bot,goals,r.quest(),r.kind(),r.npcs(),s,now,distances,local);
   var chosen=chooseAvailable(goals);if(chosen!=null)return chosen;
  }
  return null;
 }
 private static Goal choose(Player bot,State s,long now) {
  if(s.prerequisiteRoot!=0) {
   var root=bot.getQuestStateList().getQuestState(s.prerequisiteRoot);
   if(root!=null && root.getStatus()!=QuestStatus.LOCKED || !PlayerBotQuestSync.wanted(bot,bot,s.prerequisiteRoot)
    || QuestService.checkStartConditions(bot,s.prerequisiteRoot,false)){s.prerequisiteRoot=0;s.requiredRewards=Map.of();}
   else {var plan=PlayerBotQuestPrerequisites.plan(bot,s.prerequisiteRoot);s.requiredRewards=plan.reachable()?plan.requiredRewards():Map.of();}
  }
  var distances=PlayerBotWorldTravel.distances(bot);List<Request> requests=new ArrayList<>();
  for(var q:bot.getQuestStateList().getUncompletedQuests())for(var kind:PlayerBotQuestRoutes.Kind.values())
   requests.add(new Request(q.getQuestId(),kind,PlayerBotQuestRoutes.ids(bot,bot,q.getQuestId(),kind)));
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
  Goal directlyAvailable=available(bot,requests,s,now,distances);if(directlyAvailable!=null)return directlyAvailable;
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
  Goal hunt=available(bot,List.of(new Request(0,PlayerBotQuestRoutes.Kind.HUNT,grindTemplates(bot))),s,now,distances);
  return hunt!=null?hunt:rpg(bot,s,now);
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
  if(s.rpg!=null && !com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_RPG_ENABLED){goal(s,null);s.nextScan=0;navigation.stop();}
  // Arrival was already admitted by native range/visibility guards. Waiting
  // needs no repeated geometry ray or transport-graph construction each tick.
  if(s.rpg!=null && s.goal==s.rpg && s.rpgUntil>0) {
   if(rpgPause(now,s.rpgUntil) && bot.getWorldId()==s.goal.place().map()
    && PositionUtil.getDistance(bot,s.goal.place().x(),s.goal.place().y(),s.goal.place().z())<=6)return;
   s.excluded.put(key(s.goal),now+30000);goal(s,null);s.nextScan=0;navigation.stop();return;
  }
  if(s.goal!=null && s.goal.quest()!=0 && !PlayerBotQuestRoutes.ids(bot,bot,s.goal.quest(),s.goal.kind()).contains(s.goal.place().npc())){goal(s,null);s.nextScan=0;}
  if(s.goal!=null && PlayerBotWorldTravel.distance(bot,s.goal.place())<=6 && (s.goal!=s.rpg || rpgArrived(bot,s.goal))){
   if(s.goal==s.rpg) {
    int delay=com.aionemu.gameserver.configs.main.PlayerBotConfig.WORLD_RPG_PAUSE_MS;
    if(delay<1000 || delay>60000)throw new IllegalArgumentException("World RPG pause must be 1000..60000 milliseconds");
    s.rpgUntil=now+delay;s.noActivitySince=0;s.failedTravel=false;navigation.stop();return;
   }
   // Hand movement back to the native quest/combat/loot actions before entering
   // the NPC's exact spawn. A dead/unavailable NPC must not trap the planner.
   // Yield this callback to local native actions. The next planner callback may
   // choose another destination immediately if the hinted actor is unavailable.
   s.excluded.put(key(s.goal),now+30000);goal(s,null);s.nextScan=0;navigation.stop();return;
  }
  if(s.goal!=null) {
   double distance=PlayerBotWorldTravel.distance(bot,s.goal.place());
   if(distance<s.best-2){if(Double.isFinite(s.best)){s.noActivitySince=0;s.failedTravel=false;}s.best=distance;s.lastProgress=now;s.travelClock.reset();}
   boolean waiting=navigation.waitingForTerrainWork();
   if(!waiting && navigation.progressing(now)){s.lastProgress=now;s.noActivitySince=0;s.failedTravel=false;s.travelClock.reset();}
   if(s.travelClock.stalled(now,s.lastProgress,waiting)){s.failedTravel=true;s.excluded.put(key(s.goal),now+120000);goal(s,null);s.nextScan=0;navigation.stop();}
  }
  if(s.goal==null && now>=s.nextScan){s.nextScan=now+5000;goal(s,choose(bot,s,now));s.best=Double.POSITIVE_INFINITY;s.lastProgress=now;}
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
  Player bot=session.bot();if(!session.autonomous() || bot.isDead() || bot.isTrading() || bot.isCasting() || bot.isLooting() || travelThreatened(bot))return false;
  prepare(bot,navigation);State s=state(bot);
  Goal selected=s.goal;if(selected==null)return false;
  if(selected==s.rpg && rpgPause(System.currentTimeMillis(),s.rpgUntil))return true;
  boolean moved=PlayerBotWorldTravel.move(bot,selected.place(),navigation);
  // TravelTarget cooldown purpose: a completed bounded search with no earned
  // route is an unavailable destination, not fresh progress for another retry.
  // Explicit queue waits, native safety pauses and earned prefixes retain custody.
  if(!moved && navigation.routeFailed() && s.goal==selected && !s.closed) {
   s.failedTravel=true;if(s.noActivitySince==0)s.noActivitySince=System.currentTimeMillis();
   s.excluded.put(key(selected),System.currentTimeMillis()+120000);goal(s,null);s.nextScan=0;navigation.stop();
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
