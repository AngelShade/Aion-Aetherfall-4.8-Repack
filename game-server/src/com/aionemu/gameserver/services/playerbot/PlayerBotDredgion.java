package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.CreatureType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.instancescore.DredgionInstanceScore;
import com.aionemu.gameserver.model.instance.instancescore.PvpInstanceScore;
import com.aionemu.gameserver.model.team.TemporaryPlayerTeam;
import com.aionemu.gameserver.model.templates.portal.PortalLoc;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * Native Dredgion teams share an objective while each bot navigates from its own position.
 * The instance handlers own all scoring, spawn changes, and match completion.
 */
final class PlayerBotDredgion {
 /** One Dredgion: scored targets (npc id -> points), path openers, the captain and who wins on its death. */
 record Lane(int armory,int shieldGenerator) {}
 record Ship(int map,String name,Map<Integer,Integer> points,Set<Integer> openers,int captain,boolean killerWins,
             int gravity,Lane port,Lane starboard) {
  Lane lane(boolean east){return east?starboard:port;}
 }
 static final Ship BARANATH=new Ship(300110000,"Baranath Dredgion",Map.ofEntries(
  Map.entry(700485,500),Map.entry(700486,500),Map.entry(700495,500),Map.entry(700496,500),Map.entry(700490,600),Map.entry(700491,600),
  Map.entry(700497,700),Map.entry(700498,700),Map.entry(700492,800),Map.entry(700493,800),Map.entry(700487,900),Map.entry(700488,1080),Map.entry(700489,1080)),
  Set.of(700506,700508),214823,true,700487,new Lane(700486,0),new Lane(700485,0));
 static final Ship CHANTRA=new Ship(300210000,"Chantra Dredgion",Map.ofEntries(
  Map.entry(700838,400),Map.entry(700839,400),Map.entry(700840,700),Map.entry(700848,700),Map.entry(700849,700),Map.entry(700850,700),Map.entry(700851,700),
  Map.entry(700845,800),Map.entry(700846,800),Map.entry(700847,900),Map.entry(700841,1000),Map.entry(700842,1000),Map.entry(700843,1100),Map.entry(700844,1100),
  Map.entry(216941,1000),Map.entry(216885,500),Map.entry(700836,100)),
  Set.of(730349,730350,730351,730352,216882),216886,false,700840,new Lane(700838,730351),new Lane(700839,730352));
 static final Ship TERATH=new Ship(300440000,"Terath Dredgion",Map.ofEntries(
  Map.entry(701441,400),Map.entry(701442,400),Map.entry(701443,700),Map.entry(701451,700),Map.entry(701452,700),Map.entry(701453,700),Map.entry(701454,700),
  Map.entry(701448,800),Map.entry(701449,800),Map.entry(701450,900),Map.entry(701444,1000),Map.entry(701445,1000),Map.entry(701446,1100),Map.entry(701447,1100),
  Map.entry(233377,1000),Map.entry(233370,500),Map.entry(701439,100)),
  Set.of(730570,730571,730572,730573,233378),233371,false,701443,new Lane(701441,730572),new Lane(701442,730573));
 static final List<Ship> SHIPS=List.of(BARANATH,CHANTRA,TERATH);
 /** A hatch or deck officer is worth this much to a bot: below most Surkanas, so the near rooms go first. */
 static final int OPENER_VALUE=300,CAPTAIN_VALUE=2000;
 static final float RANGE=45;
 private static final int CAPTAIN_POINTS=1000;
 private static final int OPENING_WINDOW_MS=240_000;
 private static final long CONTEST_OBSERVATION_MS=8000;
 private static final float GUARD_RADIUS=25, FLOOR_GAP=5, PORTAL_MARGIN=8;
 private static final long UNREACHABLE_MS=20000,CALL_MS=60000,STALL_MS=12000,MAX_TRIP_MS=90000,REFRESH_MS=1500;
 static final AtomicLong TARGETED=new AtomicLong(),PICKS=new AtomicLong(),ARRIVALS=new AtomicLong(),UNREACHABLE=new AtomicLong(),CALLS=new AtomicLong(),
  SURKANAS=new AtomicLong(),CAPTAINS=new AtomicLong();
 private static final AtomicLong ARMORY_OPENINGS=new AtomicLong(),GRAVITY_OPENINGS=new AtomicLong(),SHIELD_OPENINGS=new AtomicLong(),
  CONTEST_ROTATIONS=new AtomicLong();
 private static final Set<Integer> CHANTRA_PORTALS=Set.of(730311,730312,730314,730315,730357,730358);
 private static final Set<Integer> TERATH_PORTALS=Set.of(730558,730559,730560,730561,730562,730563);
 private static final Set<Integer> CAPTAIN_PORTALS=Set.of(730357,730358,730562,730563);
 private static final Set<Integer> SHIELDS=Set.of(700502,730345,730346,730566,730567);
 private record TeamKey(WorldMapInstance instance, Race race, TemporaryPlayerTeam<?> party) {}
 private record Assignment(Npc objective, Npc guard, boolean human) {
  Npc focus(){return guard==null ? objective : guard;}
 }
 private static final class TeamPlan {
  final Map<Integer,Long> skipped=new HashMap<>(), called=new HashMap<>();
  final Set<Integer> members=ConcurrentHashMap.newKeySet();
  final Map<Integer,Npc> blocked=new HashMap<>();
  List<Npc> portals=List.of();
  volatile Assignment assignment;
  volatile long refreshAt;
  volatile long bypassUntil;
  long portalsAt;
  boolean openingChosen, closing;
  int deficit;
  Npc openingGenerator;
 }
 private static final class Plan {
  final TeamKey key;
  final TeamPlan team;
  final Map<Integer,Long> skipped=new HashMap<>();
  Npc focus, waypoint;
  long pickedAt,progressAt,lastTravel,tryRouteAt;
  float x,y,z;

  Plan(TeamKey key,TeamPlan team){this.key=key;this.team=team;}

  void progress(Player bot,long now) {
   x=bot.getX();y=bot.getY();z=bot.getZ();progressAt=now;
  }
 }
 private static final Map<Integer,Plan> PLANS=new ConcurrentHashMap<>();
 private static final Map<TeamKey,TeamPlan> TEAMS=new ConcurrentHashMap<>();
 private static final Set<Integer> SEEN=ConcurrentHashMap.newKeySet();

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_DREDGION;}
 static Ship ship(int map){for(var s:SHIPS)if(s.map()==map)return s;return null;}
 static boolean aboard(Player player){return player.getPosition()!=null && ship(player.getWorldId())!=null && player.isInInstance();}
 /** What an objective is worth to a bot right now; 0 = not an objective (or a captain that would hand the enemy the win). */
 static int value(Ship ship,int npcId,boolean captainWins) {
  if(ship==null)return 0;
  if(npcId==ship.captain())return ship.killerWins() || captainWins?CAPTAIN_VALUE:0;
  if(ship.openers().contains(npcId) || SHIELDS.contains(npcId))return OPENER_VALUE;
  return ship.points().getOrDefault(npcId,0);
 }
 static double score(int value,double distance){return value<=0?0:value/(Math.max(0,distance)+20);}
 /** A room's Surkana: a 700xxx/701xxx device worth 400+ (the 100-point items and named officers are not). */
 static boolean surkana(Ship ship,int npcId){return npcId>=700000 && npcId<702000 && ship.points().getOrDefault(npcId,0)>=400;}
 static boolean captainWins(Ship ship,int ownPoints,int opposingPoints) {
  return ship.killerWins() || (long)ownPoints+CAPTAIN_POINTS>opposingPoints;
 }
 private static boolean captainWins(Player bot,Ship ship) {
  if(ship.killerWins())return true;
  var handler=bot.getPosition().getWorldMapInstance().getInstanceHandler();
  Race race=bot.getRace();
  if(handler==null || !(handler.getInstanceScore() instanceof PvpInstanceScore<?> score))return false;
  synchronized(score) {
   return captainWins(ship,score.getPointsByRace(race),score.getPointsByRace(race==Race.ELYOS?Race.ASMODIANS:Race.ELYOS));
  }
 }
 static boolean captainAttackAllowed(Player bot,Npc npc) {
  if(!enabled() || !aboard(bot))return true;
  Ship ship=ship(bot.getWorldId());
  return npc.getNpcId()!=ship.captain() || captainWins(bot,ship);
 }
 static boolean started(Player bot) {
  var handler=bot.getPosition().getWorldMapInstance().getInstanceHandler();
  return handler!=null && handler.getInstanceScore()!=null && handler.getInstanceScore().getInstanceProgressionType()!=null
   && handler.getInstanceScore().getInstanceProgressionType().isStartProgress();
 }
 private static boolean alive(Npc npc) {
  return npc!=null && npc.isSpawned() && !npc.isDead() && !npc.getLifeStats().isAboutToDie();
 }
 private static Assignment assignment(Player bot) {
  Plan plan=PLANS.get(bot.getObjectId());
  return plan==null || plan.key.instance()!=bot.getWorldMapInstance() ? null : plan.team.assignment;
 }
 static boolean hasHumanObjective(Player bot) {
  var assigned=assignment(bot);
  return assigned!=null && assigned.human();
 }
 static boolean bypassingContest(Player bot,long now) {
  Plan plan=PLANS.get(bot.getObjectId());
  return plan!=null && plan.key.instance()==bot.getWorldMapInstance() && now<plan.team.bypassUntil;
 }
 /** Admit only the assigned objective or its room guard to the ordinary combat system. */
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party) {
  if(!enabled() || !aboard(bot))return false;
  var assigned=assignment(bot);
  if(assigned==null || npc!=assigned.focus() || !alive(npc) || !captainAttackAllowed(bot,npc)
   || PlayerBotMarks.nativeCcMarked(bot,npc) || !PositionUtil.isInRange(bot,npc,RANGE)
   || !PlayerBotFieldCombat.ready(bot.getLifeStats().getHpPercentage(),bot.getLifeStats().getMpPercentage(),PlayerBotRecoveryIntent.active(bot)))return false;
  boolean yes=visibleHere(bot,npc);
  if(yes && SEEN.add(npc.getObjectId())){TARGETED.incrementAndGet();if(SEEN.size()>20000)SEEN.clear();}
  return yes;
 }
 static Npc objectiveFocus(Player bot,List<Npc> enemies) {
  if(!enabled() || !aboard(bot))return null;
  var assigned=assignment(bot);
  return assigned!=null && enemies.contains(assigned.focus()) && captainAttackAllowed(bot,assigned.focus()) ? assigned.focus() : null;
 }
 private static boolean visibleHere(Player bot,Npc npc) {
  return npc.getWorldMapInstance()==bot.getWorldMapInstance() && Math.abs(bot.getZ()-npc.getZ())<=FLOOR_GAP
   && PlayerBotTargetEligibility.visible(bot,npc) && GeoService.getInstance().canSee(bot,npc);
 }
 static boolean portal(Ship ship,int npcId) {
  return ship==CHANTRA && CHANTRA_PORTALS.contains(npcId) || ship==TERATH && TERATH_PORTALS.contains(npcId);
 }
 static boolean canUsePortal(Player bot,Npc npc) {
  return PositionUtil.isInTalkRange(bot,npc) && PlayerBotTargetEligibility.visible(bot,npc)
   && GeoService.getInstance().canSee(bot,npc) && DialogService.isInteractionAllowed(bot,npc);
 }
 private static PortalLoc destination(Player bot,Ship ship,Npc portal) {
  var path=DataManager.PORTAL2_DATA.getPortalUsePath(portal.getNpcId(),bot);
  if(path==null || path.getRace()!=Race.PC_ALL && path.getRace()!=bot.getRace())return null;
  var destination=DataManager.PORTAL_LOC_DATA.getPortalLoc(path.getLocId());
  return destination!=null && destination.getWorldId()==ship.map() ? destination : null;
 }
 static boolean portalImproves(double direct,double toPortal,double fromLanding,boolean directBlocked) {
  return fromLanding+PORTAL_MARGIN<direct && (directBlocked || toPortal+fromLanding+PORTAL_MARGIN<direct);
 }
 private static Npc waypoint(Player bot,Ship ship,Plan plan,Npc focus,long now) {
  if(PositionUtil.isInRange(bot,focus,RANGE) && visibleHere(bot,focus) && !plan.skipped.containsKey(focus.getObjectId()))return focus;
  boolean blocked=plan.skipped.containsKey(focus.getObjectId());
  double direct=PositionUtil.getDistance(bot,focus);
  double best=Double.POSITIVE_INFINITY;
  Npc waypoint=blocked ? null : focus;
  for(var portal:portals(ship,plan,now)) {
   if(!alive(portal) || plan.skipped.containsKey(portal.getObjectId()))continue;
   if(CAPTAIN_PORTALS.contains(portal.getNpcId()) && focus.getNpcId()!=ship.captain())continue;
   var landing=destination(bot,ship,portal);
   if(landing==null)continue;
   double toPortal=PositionUtil.getDistance(bot,portal);
   double fromLanding=PositionUtil.getDistance(focus,landing.getX(),landing.getY(),landing.getZ());
   double travel=toPortal+fromLanding;
   if(travel<best && portalImproves(direct,toPortal,fromLanding,blocked)){waypoint=portal;best=travel;}
  }
  return waypoint;
 }
 private static List<Npc> portals(Ship ship,Plan plan,long now) {
  if(ship==BARANATH)return List.of();
  TeamPlan team=plan.team;
  synchronized(team) {
   if(now>=team.portalsAt) {
    var ids=ship==CHANTRA ? CHANTRA_PORTALS : TERATH_PORTALS;
    team.portals=plan.key.instance().getNpcs(ids.stream().mapToInt(Integer::intValue).toArray());
    team.portalsAt=now+REFRESH_MS;
   }
   return team.portals;
  }
 }
 private static boolean canSelect(Player bot,Ship ship,Npc npc,boolean canFinish) {
  return alive(npc) && npc.getWorldMapInstance()==bot.getWorldMapInstance() && !portal(ship,npc.getNpcId())
   && (npc.getNpcId()!=ship.captain() || canFinish) && !PlayerBotMarks.nativeCcMarked(bot,npc)
   && npc.getObjectTemplate().isClientVisible() && bot.isEnemy(npc);
 }
 private static Npc pick(Player bot,Ship ship,TeamPlan team,boolean canFinish,int deficit,int remaining) {
  Npc best=null,lateBest=null;double top=0,lateTop=0;
  boolean late=PlayerBotDredgionTactics.late(remaining);
  float speed=late?Math.max(.1f,bot.getGameStats().getMovementSpeedFloat()):1;
  for(var npc:bot.getWorldMapInstance().getNpcs()) {
   int npcId=npc.getNpcId();
   if(npcId==ship.port().shieldGenerator() || npcId==ship.starboard().shieldGenerator())continue;
   int value=value(ship,npcId,canFinish);
   if(value<=0 || team.skipped.containsKey(npc.getObjectId()) || !canSelect(bot,ship,npc,canFinish))continue;
   double distance=PositionUtil.getDistance(bot,npc);
   double s=score(value,distance);
   if(distance<=RANGE && visibleHere(bot,npc))s*=3;
   if(s>top || s==top && best!=null && npc.getObjectId()<best.getObjectId()){top=s;best=npc;}
   if(late) {
    int points=npcId==ship.captain()?CAPTAIN_POINTS:ship.points().getOrDefault(npcId,0);
    // Straight-line time is a lower bound. Native routing and combat still have to earn the kill.
    double lateValue=PlayerBotDredgionTactics.lateScore(points,distance/speed,deficit,remaining);
    if(lateValue>lateTop || lateValue==lateTop && lateBest!=null && npc.getObjectId()<lateBest.getObjectId()){
     lateTop=lateValue;lateBest=npc;
    }
   }
  }
  return lateBest!=null?lateBest:best;
 }
 private record ReadyParty(int count,boolean tank,boolean healer) {}
 private static ReadyParty readyParty(Player bot,TemporaryPlayerTeam<?> party) {
  if(party==null)return new ReadyParty(0,false,false);
  int ready=0;
  boolean tank=false,healer=false;
  for(Player member:party.getOnlineMembers()) {
   if(!member.isSpawned() || member.isDead() || member.getWorldMapInstance()!=bot.getWorldMapInstance()
    || !PlayerBotFieldCombat.ready(PlayerBotDefense.health(member),PlayerBotDefense.mana(member),false))continue;
   ready++;
   var role=PlayerBotService.getInstance().combatRole(member);
   tank|=role==PlayerBotRules.Role.TANK;
   healer|=role==PlayerBotRules.Role.HEALER;
  }
  return new ReadyParty(ready,tank,healer);
 }
 private static Npc available(Player bot,Ship ship,TeamPlan team,int npcId,boolean canFinish) {
  if(npcId==0)return null;
  Npc npc=bot.getWorldMapInstance().getNpc(npcId);
  return canSelect(bot,ship,npc,canFinish) && !team.skipped.containsKey(npc.getObjectId())?npc:null;
 }
 private static Npc openingTarget(Player bot,Ship ship,TeamPlan team,TemporaryPlayerTeam<?> party,
                                  DredgionInstanceScore match,int remaining,boolean canFinish) {
  if(match==null || remaining<DredgionInstanceScore.MATCH_MS-OPENING_WINDOW_MS)return null;
  if(team.openingGenerator!=null && !alive(team.openingGenerator)) {
   team.openingGenerator=null;
   return available(bot,ship,team,ship.gravity(),canFinish);
  }
  if(team.openingChosen)return null;
  team.openingChosen=true;
  boolean east=match.starboard(bot.getRace());
  Npc armory=available(bot,ship,team,ship.lane(east).armory(),canFinish);
  Npc gravity=available(bot,ship,team,ship.gravity(),canFinish);
  Npc generator=available(bot,ship,team,ship.lane(!east).shieldGenerator(),canFinish);
  var ready=readyParty(bot,party);
  var opening=PlayerBotDredgionTactics.opening(match.openingDraw(bot.getRace()),gravity!=null && ready.count()>=3,
   generator!=null && ready.count()>=4 && ready.tank() && ready.healer());
  return switch(opening) {
   case CLEAR_LANE -> {
    if(armory!=null)ARMORY_OPENINGS.incrementAndGet();
    yield armory;
   }
   case RUSH_GRAVITY -> {
    if(gravity!=null)GRAVITY_OPENINGS.incrementAndGet();
    yield gravity;
   }
   case BLOCK_OPPONENT -> {
    team.openingGenerator=generator;
    SHIELD_OPENINGS.incrementAndGet();
    yield generator;
   }
  };
 }
 private static int deficit(Player bot,DredgionInstanceScore match) {
  if(match==null)return 0;
  Race own=bot.getRace();
  synchronized(match) {
   return match.getPointsByRace(own==Race.ELYOS?Race.ASMODIANS:Race.ELYOS)-match.getPointsByRace(own);
  }
 }
 private static boolean attacking(Player bot,Npc npc,long now) {
  return npc!=null && npc.getAggroList().stream().anyMatch(hit->hit.getDamage()>0
   && now-hit.getLastInteractionTime()<=CONTEST_OBSERVATION_MS && hit.getAttacker().getMaster() instanceof Player ally
   && ally.getRace()==bot.getRace());
 }
 private static boolean outnumberedAt(Player bot,Ship ship,TemporaryPlayerTeam<?> party,Npc npc,long now) {
  if(bot.getController().isInCombat() || !alive(npc) || !surkana(ship,npc.getNpcId()) || !visibleHere(bot,npc))return false;
  long opponents=npc.getAggroList().stream()
   .filter(hit->hit.getDamage()>0 && now-hit.getLastInteractionTime()<=CONTEST_OBSERVATION_MS)
   .map(hit->hit.getAttacker().getMaster())
   .filter(actor->actor instanceof Player opponent && opponent.getRace()!=bot.getRace() && opponent.isSpawned() && !opponent.isDead()
    && opponent.getWorldMapInstance()==bot.getWorldMapInstance() && PlayerBotTargetEligibility.visible(bot,opponent)
    && GeoService.getInstance().canSee(bot,opponent))
   .distinct().count();
  if(opponents==0)return false;
  var allies=party==null?List.of(bot):party.getOnlineMembers();
  long available=allies.stream().filter(ally->ally.isSpawned() && !ally.isDead() && ally.getWorldMapInstance()==bot.getWorldMapInstance()
   && PositionUtil.isInRange(ally,npc,35) && PlayerBotFieldCombat.ready(PlayerBotDefense.health(ally),PlayerBotDefense.mana(ally),false)).count();
  return opponents>available;
 }
 private static Npc roomGuard(Player bot,Ship ship,TeamPlan team,Npc objective,Npc previousGuard) {
  // SurkanaAI calls all nearby NPCs on attack. Clear one live, reachable guard at a time.
  if(!surkana(ship,objective.getNpcId()) || objective.getAggroList().stream().findAny().isPresent())return null;
  if(guardEligible(bot,ship,team,objective,previousGuard))return previousGuard;
  var nearby=new ArrayList<Npc>();
  objective.getKnownList().forEachNpc(nearby::add);
  return nearby.stream().filter(npc->guardEligible(bot,ship,team,objective,npc))
   .min(Comparator.comparingDouble((Npc npc)->PositionUtil.getDistance(objective,npc)).thenComparingInt(Npc::getObjectId)).orElse(null);
 }
 private static boolean guardEligible(Player bot,Ship ship,TeamPlan team,Npc objective,Npc npc) {
  return npc!=null && npc!=objective && npc.getNpcId()!=ship.captain() && !team.skipped.containsKey(npc.getObjectId())
   && alive(npc) && PositionUtil.isInRange(objective,npc,GUARD_RADIUS) && Math.abs(objective.getZ()-npc.getZ())<=FLOOR_GAP
   && npc.getType(bot)==CreatureType.AGGRESSIVE && !PlayerBotMarks.controlled(npc) && canSelect(bot,ship,npc,false)
   && GeoService.getInstance().canSee(objective,npc);
 }
 /** Establish or refresh one shared assignment. Unchanged objectives never rescan the ship. */
 static void prepare(Player bot,long now) {
  if(!enabled() || !aboard(bot) || !started(bot) || bot.isDead()){forget(bot.getObjectId());return;}
  var party=bot.getCurrentTeam();
  var key=new TeamKey(bot.getWorldMapInstance(),bot.getRace(),party);
  var plan=PLANS.get(bot.getObjectId());
  if(plan==null || !plan.key.equals(key))plan=attach(bot.getObjectId(),key);
  TeamPlan team=plan.team;
  Ship ship=ship(bot.getWorldId());
  if(!refreshDue(bot,ship,team,now))return;
  Npc announce=null;
  synchronized(team) {
   if(!refreshDue(bot,ship,team,now))return;
   team.refreshAt=now+REFRESH_MS;
   team.skipped.values().removeIf(until->now>=until);
   var previous=team.assignment;
   Npc objective=previous==null ? null : previous.objective();
   boolean canFinish=captainWins(bot,ship);
   var match=bot.getWorldMapInstance().getInstanceHandler().getInstanceScore() instanceof DredgionInstanceScore nativeScore?nativeScore:null;
   int remaining=match==null?0:match.timeRemaining(now);
   boolean closing=PlayerBotDredgionTactics.late(remaining);
   int deficit=deficit(bot,match);
   boolean engaged=attacking(bot,objective,now);
   boolean reconsider=closing && (!team.closing || team.deficit!=deficit) && !engaged;
   team.closing=closing;
   team.deficit=deficit;
   Npc human=PlayerBotDredgionParty.humanObjective(bot,npc->!team.skipped.containsKey(npc.getObjectId())
    && canSelect(bot,ship,npc,canFinish));
   Npc next=human;
   if(human!=null) {
    team.openingChosen=true;
    team.openingGenerator=null;
    team.bypassUntil=0;
   } else if(!engaged && outnumberedAt(bot,ship,party,objective,now)) {
    team.skipped.put(objective.getObjectId(),now+UNREACHABLE_MS);
    team.bypassUntil=now+CONTEST_OBSERVATION_MS;
    CONTEST_ROTATIONS.incrementAndGet();
   }
   if(next==null && !reconsider && objective!=null && !team.skipped.containsKey(objective.getObjectId())
    && canSelect(bot,ship,objective,canFinish) && value(ship,objective.getNpcId(),canFinish)>0)next=objective;
   if(next==null)next=openingTarget(bot,ship,team,party,match,remaining,canFinish);
   if(next==null)next=pick(bot,ship,team,canFinish,deficit,remaining);
   if(objective!=next) {
    if(objective!=null && objective.isDead()) {
     if(surkana(ship,objective.getNpcId()))SURKANAS.incrementAndGet();
     if(objective.getNpcId()==ship.captain())CAPTAINS.incrementAndGet();
    }
    team.blocked.clear();
    if(next!=null) {
     PICKS.incrementAndGet();
     if(human==null && party!=null) {
      team.called.values().removeIf(at->now-at>=CALL_MS);
      if(team.called.putIfAbsent(next.getObjectId(),now)==null)announce=next;
     }
    }
   }
   if(next==null){team.assignment=null;return;}
   Npc guard=roomGuard(bot,ship,team,next,objective==next ? previous.guard() : null);
   if(previous!=null && previous.guard()!=guard)team.blocked.clear();
   team.assignment=new Assignment(next,guard,human!=null);
  }
  if(announce!=null && party==bot.getCurrentTeam())callOut(bot,party,announce,ship);
 }
 private static boolean refreshDue(Player bot,Ship ship,TeamPlan team,long now) {
  if(now>=team.refreshAt)return true;
  var assigned=team.assignment;
  return assigned!=null && (!alive(assigned.objective()) || !alive(assigned.focus())
   || assigned.objective().getNpcId()==ship.captain() && !captainWins(bot,ship));
 }
 private static Plan attach(int bot,TeamKey key) {
  return PLANS.compute(bot,(id,current)->{
   if(current!=null && current.key.equals(key))return current;
   if(current!=null)detach(id,current);
   var team=TEAMS.compute(key,(k,value)->{
    TeamPlan active=value==null ? new TeamPlan() : value;
    active.members.add(id);
    return active;
   });
   return new Plan(key,team);
  });
 }
 private static void detach(int bot,Plan plan) {
  TEAMS.computeIfPresent(plan.key,(key,team)->{
   team.members.remove(bot);
   synchronized(team){team.blocked.remove(bot);}
   return team.members.isEmpty() ? null : team;
  });
 }
 private static void callOut(Player bot,TemporaryPlayerTeam<?> party,Npc npc,Ship ship) {
  var members=party.getOnlineMembers().stream().filter(member->!member.isPlayerBot()).toList();
  if(members.isEmpty())return;
  CALLS.incrementAndGet();
  int points=ship.points().getOrDefault(npc.getNpcId(),0);
  String what=npc.getNpcId()==ship.captain()?"the captain - let's finish this!":ship.openers().contains(npc.getNpcId())?npc.getName()+" to open the way!"
   :npc.getName()+(points>0?" ("+points+" points)!":"!");
  for(var member:members)PacketSendUtility.sendPacket(member,new SM_MESSAGE(bot,"Heading for "+what,ChatType.GROUP));
 }
 /** Hold a progressing route. Native combat and portal interactions own the final action. */
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();int id=bot.getObjectId();
  if(!enabled() || !session.autonomous() || !aboard(bot) || !started(bot)){forget(id);return false;}
  prepare(bot,now);
  Plan plan=PLANS.get(id);
  if(plan==null)return false;
  var assigned=plan.team.assignment;
  Npc focus=assigned==null ? null : assigned.focus();
  if(focus!=plan.focus) {
   if(plan.waypoint!=null)navigation.stop();
   plan.focus=focus;
   plan.waypoint=null;
   plan.tryRouteAt=now;
  }
  if(focus==null)return false;
  Ship ship=ship(bot.getWorldId());
  if(plan.waypoint!=null && !alive(plan.waypoint)) {
   navigation.stop();
   plan.waypoint=null;
  }
  if(plan.waypoint==null) {
   if(now<plan.tryRouteAt)return false;
   plan.tryRouteAt=now+REFRESH_MS;
   plan.skipped.values().removeIf(until->now>=until);
   plan.waypoint=waypoint(bot,ship,plan,focus,now);
   if(plan.waypoint==null){blocked(bot,plan,assigned,now);return false;}
   synchronized(plan.team){plan.team.blocked.remove(id);}
   plan.pickedAt=now;
   plan.progress(bot,now);
  }
  if(now-plan.lastTravel>REFRESH_MS){plan.pickedAt=now;plan.progress(bot,now);}
  plan.lastTravel=now;
  if(PositionUtil.getDistance(bot,plan.x,plan.y,plan.z)>=1.25)plan.progress(bot,now);
  Npc npc=plan.waypoint;
  boolean use=npc!=focus;
  if(use && canUsePortal(bot,npc)) {
   navigation.stop();
   plan.skipped.put(npc.getObjectId(),now+UNREACHABLE_MS);
   plan.waypoint=null;
   npc.getController().onDialogRequest(bot);
   ARRIVALS.incrementAndGet();
   return true;
  }
  if(now-plan.progressAt>=STALL_MS || now-plan.pickedAt>=MAX_TRIP_MS
   || !navigation.approach(npc,use?1.5f:3) && navigation.routeFailed()) {
   navigation.stop();
   plan.skipped.put(npc.getObjectId(),now+UNREACHABLE_MS);
   plan.waypoint=null;
   UNREACHABLE.incrementAndGet();
   return false;
  }
  return true;
 }
 private static void blocked(Player bot,Plan plan,Assignment assigned,long now) {
  TeamPlan team=plan.team;
  synchronized(team) {
   if(team.assignment!=assigned)return;
   team.blocked.put(bot.getObjectId(),assigned.focus());
   if(team.members.stream().allMatch(id->team.blocked.get(id)==assigned.focus())) {
    team.skipped.put(assigned.focus().getObjectId(),now+UNREACHABLE_MS);
    team.blocked.clear();
    team.refreshAt=now;
   }
  }
 }
 static void forget(int bot){PLANS.computeIfPresent(bot,(id,plan)->{detach(id,plan);return null;});}
 static String stats(){return "enabled="+enabled()+", objective targets="+TARGETED.get()+", picks="+PICKS.get()+" arrived="+ARRIVALS.get()+" unreachable="+UNREACHABLE.get()
  +", call-outs="+CALLS.get()+", Surkanas down="+SURKANAS.get()+", captains down="+CAPTAINS.get()
  +", armory openings="+ARMORY_OPENINGS.get()+", gravity openings="+GRAVITY_OPENINGS.get()+", shield openings="+SHIELD_OPENINGS.get()
  +", contested rooms bypassed="+CONTEST_ROTATIONS.get();}
 private PlayerBotDredgion(){}
}
