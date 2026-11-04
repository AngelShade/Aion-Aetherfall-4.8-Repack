package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.commons.utils.WeightedRandom;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig;
import com.aionemu.gameserver.configs.main.PlayerBotWardenConfig;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;

/** Persistent road careers for generated world residents; no owned characters are reassigned. */
public final class PlayerBotRoadDuty {
 enum Career { NONE, WARDEN, MERCHANT }
 enum Rank { PEDDLER, TRADER, SPECIALIST, MASTER, LEGENDARY }
 private static final WeightedRandom<Rank> RANKS=WeightedRandom.of(new WeightedRandom.Entry<>(Rank.PEDDLER,PlayerBotMerchantConfig.CHANCE_PEDDLER),
  new WeightedRandom.Entry<>(Rank.TRADER,PlayerBotMerchantConfig.CHANCE_TRADER),new WeightedRandom.Entry<>(Rank.SPECIALIST,PlayerBotMerchantConfig.CHANCE_SPECIALIST),
  new WeightedRandom.Entry<>(Rank.MASTER,PlayerBotMerchantConfig.CHANCE_MASTER),new WeightedRandom.Entry<>(Rank.LEGENDARY,PlayerBotMerchantConfig.CHANCE_LEGENDARY));
 static final class State {
  final PlayerBotSession session;final Properties values;final Career career;final Rank rank;PlayerBotPatrolRoutes.Route route;
  int point,direction=1,visitor,shopOffset,checkedMap,checkedLevel;long next,stopUntil,escortUntil,attendCheck,spacingCheck,recoveryAt,assignAt;boolean attended,suspended;Lane lane;
  PlayerBotPatrolRoutes.Route pending;boolean patrolled;
  final ArrayDeque<Lane> ahead=new ArrayDeque<>();
  final Progress progress=new Progress();
  State(PlayerBotSession session,Properties values) {
   this.session=session;this.values=values;career=Career.valueOf(values.getProperty("career","NONE"));rank=Rank.valueOf(values.getProperty("rank","PEDDLER"));
   route=route(values.getProperty("route",""),career);
   point=Integer.parseInt(values.getProperty("point","0"));direction=Integer.parseInt(values.getProperty("direction","1"));
   visitor=Integer.parseInt(values.getProperty("visitor","0"));stopUntil=Long.parseLong(values.getProperty("stopUntil","0"));
   escortUntil=Long.parseLong(values.getProperty("escortUntil","0"));next=Long.parseLong(values.getProperty("waitUntil","0"));
   suspended=Boolean.parseBoolean(values.getProperty("suspended","false"));checkedMap=session.bot().getWorldId();checkedLevel=session.bot().getLevel();
   if(route!=null)point=Math.clamp(point,0,route.points().size()-1);
  }
  void save() {
   values.setProperty("point",Integer.toString(point));values.setProperty("direction",Integer.toString(direction));
   values.setProperty("visitor",Integer.toString(visitor));values.setProperty("stopUntil",Long.toString(stopUntil));
   values.setProperty("escortUntil",Long.toString(escortUntil));values.setProperty("waitUntil",Long.toString(next));
   values.setProperty("suspended",Boolean.toString(suspended));
   try{PlayerBotMetadata.save(session.bot().getAccount().getId(),session.bot().getObjectId(),"road-duty",values);}
   catch(java.io.IOException error){throw new IllegalStateException("Cannot preserve road duty",error);}
  }
 }
 private static final int TARGET_PAUSE_RANGE=30;
 private static final float ARRIVAL_RANGE=3,FLOOR_RANGE=2.5f;
 private static final int MERCHANT_SPACING=30;
 private static final long ASSIGN_RETRY=300000;
 /** floor: point.z() is walkable ground. Otherwise it is a raw walker height, which can float above any floor. */
 record Lane(PlayerBotNavigation.Point point,boolean floor) {}
 record Step(int point,int direction,boolean pause) {}
 static Step advance(int point,int direction,int size) {
  int next=point+direction;
  return next>=size?new Step(size-2,-1,true):next<0?new Step(1,1,true):new Step(next,direction,false);
 }
 /** Physical XYZ only: changing goals or a pending height search cannot manufacture progress. */
 static final class Progress {
  private PlayerBotNavigation.Point position;private long since,checked;
  void reset(){position=null;}
  boolean stalled(long now,float x,float y,float z) {
   if(position==null || now-checked>10000 || PositionUtil.getDistance(position.x(),position.y(),position.z(),x,y,z)>=1) {
    position=new PlayerBotNavigation.Point(x,y,z);since=now;
   }
   checked=now;return now-since>=60000;
  }
 }
 static Career career(int id){int bucket=Math.floorMod(Integer.rotateLeft(id*0x9e3779b9,13),100);return bucket<12?Career.MERCHANT:bucket<36?Career.WARDEN:Career.NONE;}
 /** Reachability is a straight-line probe, so a resident that walks elsewhere on the same map gets another look. */
 static boolean recheck(int previousMap,int previousLevel,int map,int level,long now,long due){return previousMap!=map || previousLevel!=level || now>=due;}
 /** Old city Wardens lose only their duty/escort, never their stable career or merchant provenance. */
 static boolean migrate(Properties values,PlayerBotPatrolRoutes.Route route) {
  if(!values.getProperty("career","NONE").equals("WARDEN") || PlayerBotPatrolRoutes.suitable(route,Career.WARDEN)
   && (!PlayerBotWardenConfig.ENABLED || route.id().startsWith("beat:")))return false;
  boolean changed=false;
  for(String key:List.of("route","point","direction","visitor","stopUntil","escortUntil","waitUntil","suspended","escortStartX","escortStartY","escortStartZ"))changed|=values.remove(key)!=null;
  return changed;
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 // One reservation per identity covers loader admission, pending placement and duty.
 private static final Map<Integer,PlayerBotPatrolRoutes.Route> BEATS=new HashMap<>();
 static PlayerBotPatrolRoutes.Route route(String id,Career career) {
  return career==Career.WARDEN && PlayerBotWardenConfig.ENABLED?PlayerBotWardenBeats.route(id):PlayerBotPatrolRoutes.all().get(id);
 }
 static State state(Player bot){return bot==null?null:STATES.get(bot.getObjectId());}
 /** Loader-only native metadata read, before ordinary player-focus admission chooses a city camp. */
 static boolean savedWardenCareer(int account,int character)throws java.io.IOException {
  if(!PlayerBotConfig.ROAD_DUTY || !PlayerBotWardenConfig.ENABLED || !PlayerBotPopulationPolicy.serverAccount(account))return false;
  return PlayerBotMetadata.load(account,character,"road-duty",null).getProperty("career",career(character).name()).equals("WARDEN");
 }
 /** Career custody also exists before an actor has a usable beat or company. No SQL on social ticks. */
 static boolean wardenCareer(Player bot) {
  if(!PlayerBotConfig.ROAD_DUTY || !PlayerBotWardenConfig.ENABLED || !PlayerBotPopulationManager.global(bot))return false;
  var s=state(bot);if(s!=null)return s.career==Career.WARDEN;
  return wardenCareer(bot.getAccount().getId(),bot.getObjectId());
 }
 /** Candidate filtering is cache-only. The loader verifies saved custody before admission. */
 static boolean wardenCareer(int account,int character) {
  if(!PlayerBotConfig.ROAD_DUTY || !PlayerBotWardenConfig.ENABLED || !PlayerBotPopulationPolicy.serverAccount(account))return false;
  try {
   var saved=PlayerBotMetadata.cached(account,character,"road-duty");
   return (saved==null?career(character).name():saved.getOrDefault("career",career(character).name())).equals("WARDEN");
  }catch(java.io.IOException error){throw new IllegalStateException("Cannot verify Warden career custody",error);}
 }
 private static boolean borrowed(Player bot){return bot.isWorldBot() && bot.getPlayerBotOwnerId()>0;}
 public static boolean assigned(Player bot){var s=state(bot);return PlayerBotConfig.ROAD_DUTY && s!=null && s.career!=Career.NONE && s.route!=null
  && (s.career!=Career.WARDEN || !PlayerBotWardenConfig.ENABLED || PlayerBotWardenCompanies.matches(bot,s.route));}
 static boolean onDuty(Player bot){var s=state(bot);return assigned(bot) && !s.suspended;}
  static String planningReason(Player bot){
   var s=state(bot);if(s==null || !onDuty(bot))return "road assignment awaiting recovery";
   long now=System.currentTimeMillis();
   if(bot.hasStore())return "merchant serving at assigned road stop";
   if(s.visitor!=0 && now<s.stopUntil)return now<s.escortUntil?"road duty: assisting visitor":"road duty: visitor stop";
   if(s.attended)return "road duty: waiting for nearby customer";
   if(now<s.next)return "road duty: timed waypoint or spacing pause";
   return s.career==Career.MERCHANT?"merchant travelling assigned road":"Warden patrolling assigned road";
  }
 public static boolean publicWarden(Player bot){var s=state(bot);return onDuty(bot) && s.career==Career.WARDEN && !borrowed(bot);}
 static boolean merchant(Player bot){var s=state(bot);return assigned(bot) && s.career==Career.MERCHANT;}
 static PlayerBotPersistence.Home savedHome(Player bot) throws java.io.IOException {
  if(!PlayerBotConfig.ROAD_DUTY || !PlayerBotPopulationManager.global(bot))return null;
  var values=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"road-duty",null);
  var career=Career.valueOf(values.getProperty("career",career(bot.getObjectId()).name()));
  var route=route(values.getProperty("route",""),career);
  if(PlayerBotWardenConfig.ENABLED && career==Career.WARDEN) {
   if(route!=null && Math.abs(route.level()-bot.getLevel())>12)route=null;
   migrate(values,route);route=beat(bot,route);
   if(route==null){values.remove("route");try{PlayerBotWardenCompanies.reserve(bot);}catch(java.sql.SQLException error){throw new java.io.IOException("Cannot release reserve company",error);}}
   // SQL owns an escort even when a crash preceded its metadata checkpoint.
   try(var c=com.aionemu.commons.database.DatabaseFactory.getConnection()) {
    var escort=com.aionemu.gameserver.dao.PlayerBotWardenDAO.currentEscort(c,bot.getObjectId(),System.currentTimeMillis());
    values.setProperty("visitor",escort==null?"0":""+escort.player());
    values.setProperty("escortUntil",escort==null?"0":""+escort.until());
    values.setProperty("stopUntil",escort==null?"0":""+escort.until());
   }catch(java.sql.SQLException error){throw new java.io.IOException("Cannot restore native Warden escort custody",error);}
   values.setProperty("account",""+bot.getAccount().getId());values.setProperty("character",""+bot.getObjectId());values.setProperty("career","WARDEN");
   PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"road-duty",values);
   if(route==null || PlayerBotWorldPlacement.nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()))return null;
   boolean retained=route.id().equals(values.getProperty("route"));
   int index=startPoint(route,bot.getObjectId(),retained?Math.clamp(Integer.parseInt(values.getProperty("point","0")),0,route.points().size()-1):-1);
   if(index<0){releaseBeat(bot);return null;}
   int direction=retained?Integer.parseInt(values.getProperty("direction","1")):1;var target=lane(bot,route,index,direction,1);var p=target.point();
   if(!target.floor() || PlayerBotWorldPlacement.nearHuman(route.map(),p.x(),p.y(),p.z())
    || !GeoService.getInstance().isPlayerBotLanding(route.map(),1,p.x(),p.y(),p.z()))return null;
   values.setProperty("account",""+bot.getAccount().getId());values.setProperty("character",""+bot.getObjectId());
   values.setProperty("career","WARDEN");values.setProperty("route",route.id());values.setProperty("point",""+index);values.setProperty("direction",""+direction);
   values.setProperty("suspended","false");PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"road-duty",values);
   return new PlayerBotPersistence.Home(route.map(),p.x(),p.y(),p.z(),bot.getHeading(),0);
  }
  if(migrate(values,route)){PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"road-duty",values);return null;}
  if(route==null || values.getProperty("career","NONE").equals("NONE") || Boolean.parseBoolean(values.getProperty("suspended","false")))return null;
  var point=lane(bot,route,Math.clamp(Integer.parseInt(values.getProperty("point","0")),0,route.points().size()-1),Integer.parseInt(values.getProperty("direction","1")),1).point();
  return new PlayerBotPersistence.Home(route.map(),point.x(),point.y(),point.z(),bot.getHeading(),0);
 }
 /** This resident's walkable lane at a waypoint; the other side, then the road itself, where the ground rejects it. */
 private static Lane lane(Player bot,PlayerBotPatrolRoutes.Route route,int index,int direction,int instance) {
  var point=route.points().get(index);var geo=GeoService.getInstance();var map=DataManager.WORLD_MAPS_DATA.getTemplate(route.map());
  float centre=geo.getZ(route.map(),point.x(),point.y(),point.z()+3,point.z()-8,instance);
  if(map==null || !Float.isFinite(centre))return new Lane(point,false);
  float offset=PlayerBotPatrolRoutes.laneOffset(bot.getObjectId());
  for(float side:new float[]{offset,-offset}) {
   var beside=route.beside(index,direction,side);
   if(route.id().startsWith("beat:") && !PlayerBotWardenBeats.field(route.map(),beside.x(),beside.y(),centre))continue;
   float ground=geo.getZ(route.map(),beside.x(),beside.y(),centre+2,centre-4,instance);
   if(!PlayerBotWorldPlacement.ground(map,beside.x(),beside.y(),ground,ground))continue;
   var reach=PlayerBotWater.walk(bot,route.map(),instance,point.x(),point.y(),centre,beside.x(),beside.y(),ground);
   if(reach!=null && Float.isFinite(reach.z) && Math.hypot(reach.x-beside.x(),reach.y-beside.y())<.3)
    return new Lane(new PlayerBotNavigation.Point(beside.x(),beside.y(),ground),true);
  }
  // Water and other unwalkable centres keep the raw walker height: there is no floor to reach.
  return PlayerBotWorldPlacement.ground(map,point.x(),point.y(),centre,centre)?new Lane(new PlayerBotNavigation.Point(point.x(),point.y(),centre),true):new Lane(point,false);
 }
 /** Inside the arrival radius and, where a walkable floor is known, on that floor: a stacked road or ramp is not arrival. */
 static boolean arrived(double flat,double rise,boolean floor) {return flat<=ARRIVAL_RANGE && (!floor || rise<=FLOOR_RANGE);}
 static void initialize(PlayerBotSession session) {
  var bot=session.bot();
  if(!PlayerBotConfig.ROAD_DUTY || !session.autonomous() || !PlayerBotPopulationManager.global(bot))return;
  var state=STATES.get(bot.getObjectId());
  if(state==null) {
   try {
    var p=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"road-duty",null);
    if(p.isEmpty()) {
     p.setProperty("account",Integer.toString(bot.getAccount().getId()));p.setProperty("character",Integer.toString(bot.getObjectId()));
     // Stable career allocation; the separately rolled rank is committed, never re-rolled at login.
     Career career=career(bot.getObjectId());
     p.setProperty("career",career.name());p.setProperty("rank",(career==Career.MERCHANT?RANKS.sample():Rank.PEDDLER).name());
    }
    migrate(p,route(p.getProperty("route",""),Career.valueOf(p.getProperty("career","NONE"))));
    state=new State(session,p);STATES.put(bot.getObjectId(),state);
    if(state.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED && state.route!=null) {
     var previous=state.route;state.route=beat(bot,previous);
     if(state.route==null)state.values.remove("route");
     else if(state.route!=previous){state.point=0;state.direction=1;state.values.setProperty("route",state.route.id());}
    }
    PlayerBotWardens.restored(state);
    if(state.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED && state.route!=null && state.route.distance(bot)>PlayerBotWardenConfig.LEASH){state.pending=state.route;state.route=null;}
    if(state.route==null)assign(state);else spread(state);state.save();
   }catch(java.io.IOException error){throw new IllegalStateException("Cannot load native road-duty career",error);}
  }
 }
 private static void assign(State s) {
  var bot=s.session.bot();s.checkedMap=bot.getWorldId();s.checkedLevel=bot.getLevel();s.assignAt=System.currentTimeMillis()+ASSIGN_RETRY;if(s.career==Career.NONE)return;
  if(s.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED){s.pending=beat(bot,null);if(s.pending!=null){s.point=startPoint(s.pending,bot.getObjectId());if(s.point<0){s.point=0;s.pending=null;releaseBeat(bot);}}return;}
  var route=PlayerBotPatrolRoutes.choose(bot,s.career);if(route==null)return;
  s.route=route;s.point=route.nearest(bot);s.direction=1;s.lane=null;s.ahead.clear();s.progress.reset();s.values.setProperty("route",route.id());spread(s);
 }
 static boolean maintenance(PlayerBotSession session) {
  var bot=session.bot();
  if(bot.hasStore() && (!PlayerBotConfig.ROAD_DUTY || !session.autonomous() || bot.isDead() || bot.getController().isInCombat()))PlayerBotRoadMerchants.close(bot);
  initialize(session);var s=state(bot);if(!PlayerBotConfig.ROAD_DUTY || !session.autonomous() || s==null)return false;
  if(PlayerBotWardenConfig.ENABLED && s.career==Career.WARDEN && s.route!=null && (s.route.map()!=bot.getWorldId() || bot.getInstanceId()!=1 || Math.abs(s.route.level()-bot.getLevel())>12)) {
   PlayerBotWardens.close(bot);s.pending=null;s.route=null;gait(bot,false);s.values.remove("route");assign(s);s.save();
  }
  if(s.pending!=null && pending(s,System.currentTimeMillis())){PlayerBotWardenCompanies.maintain(s);return true;}
  PlayerBotWardenCompanies.maintain(s);
  if(s.route==null && s.career!=Career.NONE && recheck(s.checkedMap,s.checkedLevel,bot.getWorldId(),bot.getLevel(),System.currentTimeMillis(),s.assignAt)){assign(s);s.save();}
  if(s.suspended && recover(s,System.currentTimeMillis()))return true;
  if(merchant(bot))PlayerBotRoadMerchants.maintenance(s);
  return false;
 }
 private static synchronized PlayerBotPatrolRoutes.Route beat(Player bot,PlayerBotPatrolRoutes.Route preferred) {
  BEATS.remove(bot.getObjectId());var used=new HashMap<String,Integer>();var physical=new HashMap<String,Integer>();
  for(var r:BEATS.values()) {
   used.merge(r.id(),1,Integer::sum);physical.merge(PlayerBotWardenBeats.roadSpace(r).id(),1,Integer::sum);
  }
  var selected=PlayerBotWardenBeats.all().values().stream().map(PlayerBotWardenBeats.Beat::route)
   .filter(r->r.race()==bot.getRace() && Math.abs(r.level()-bot.getLevel())<=12
    && used.getOrDefault(r.id(),0)<PlayerBotWardenBeats.capacity(PlayerBotWardenBeats.length(r.points()))
    && physical.getOrDefault(PlayerBotWardenBeats.roadSpace(r).id(),0)<PlayerBotWardenBeats.capacity(PlayerBotWardenBeats.roadSpace(r).length())
    && (PlayerBotConfig.POPULATION_MAPS.isEmpty() || PlayerBotConfig.POPULATION_MAPS.contains(r.map())))
   .min(Comparator.comparingInt((PlayerBotPatrolRoutes.Route r)->r==preferred?0:1).thenComparingInt(r->used.getOrDefault(r.id(),0))
    .thenComparingInt(r->r.map()==bot.getWorldId()?0:1).thenComparing(PlayerBotPatrolRoutes.Route::id)).orElse(null);
  if(selected!=null)BEATS.put(bot.getObjectId(),selected);return selected;
 }
 private static int startPoint(PlayerBotPatrolRoutes.Route route,int identity) {
  return startPoint(route,identity,-1);
 }
 private static int startPoint(PlayerBotPatrolRoutes.Route route,int identity,int preferred) {
  var cumulative=new double[route.points().size()];for(int i=1;i<cumulative.length;i++)cumulative[i]=cumulative[i-1]+PlayerBotWardenBeats.distance(route.points().get(i-1),route.points().get(i));
  var occupied=new ArrayList<Double>();var positions=new ArrayList<PlayerBotNavigation.Point>();occupied.add(0d);occupied.add(cumulative[cumulative.length-1]);
  for(var other:STATES.values())if(other.session.bot().getObjectId()!=identity) {
   var assigned=other.route!=null?other.route:other.pending;if(assigned==null || assigned.map()!=route.map())continue;
   var p=other.pending!=null?assigned.points().get(Math.clamp(other.point,0,assigned.points().size()-1))
    :new PlayerBotNavigation.Point(other.session.bot().getX(),other.session.bot().getY(),other.session.bot().getZ());
   if(route.distance(p.x(),p.y(),p.z())<=PlayerBotWardenConfig.SPACING){occupied.add(cumulative[route.nearest(p.x(),p.y(),p.z())]);positions.add(p);}
  }
  Collections.sort(occupied);double goal;
  if(occupied.size()==2)goal=cumulative[cumulative.length-1]*(.15+Integer.remainderUnsigned(identity*0x9e3779b9,7000)/10000d);
  else {int gap=1;for(int i=2;i<occupied.size();i++)if(occupied.get(i)-occupied.get(i-1)>occupied.get(gap)-occupied.get(gap-1))gap=i;goal=(occupied.get(gap)+occupied.get(gap-1))/2;}
  if(preferred>=0)goal=cumulative[Math.clamp(preferred,0,cumulative.length-1)];
  int nearest=-1;for(int i=0;i<cumulative.length;i++) {
   var p=route.points().get(i);if(positions.stream().anyMatch(q->PlayerBotWardenBeats.distance(p,q)<PlayerBotWardenConfig.SPACING))continue;
   if(nearest<0 || Math.abs(cumulative[i]-goal)<Math.abs(cumulative[nearest]-goal))nearest=i;
  }return nearest;
 }
 private static boolean pending(State s,long now) {
  if(now<s.recoveryAt)return false;s.recoveryAt=now+30000;var bot=s.session.bot();var route=s.pending;
  int index=Math.clamp(s.point,0,route.points().size()-1);var target=lane(bot,route,index,1,1);var p=target.point();
  if(!target.floor() || !GeoService.getInstance().isPlayerBotLanding(route.map(),1,p.x(),p.y(),p.z())
   || !PlayerBotWorldPlacement.placeAt(s.session,new PlayerBotWorldPlacement.Location(bot.getRace(),route.map(),p.x(),p.y(),p.z(),1,65,false,false)))return false;
  s.route=route;s.pending=null;s.point=index;s.direction=1;s.lane=target;s.ahead.clear();s.progress.reset();s.suspended=false;
  s.values.setProperty("route",route.id());s.save();return true;
 }
 static void beginTick(Player bot){var s=state(bot);if(s!=null){s.patrolled=false;if(bot.isDead() || bot.getController().isInCombat() || s.visitor!=0 || s.suspended || PlayerBotWardens.responding(bot))gait(bot,false);}}
 static void finishTick(PlayerBotSession session) {
  var s=state(session.bot());if(s==null)return;
  gait(session.bot(),PlayerBotWardenConfig.ENABLED && PlayerBotWardenConfig.WALK && s.career==Career.WARDEN && s.patrolled
   && onDuty(session.bot()) && session.autonomous() && !session.closing() && !session.bot().isDead() && !session.bot().getController().isInCombat());
 }
 static boolean walkDecision(boolean patrol,boolean combat,boolean support,boolean escort,boolean suspended,boolean alive,boolean autonomous,double offLane) {
  return patrol && !combat && !support && !escort && !suspended && alive && autonomous && offLane<=PlayerBotWardenConfig.RETURN_RUN;
 }
 private static void gait(Player bot,boolean walk) {
  var s=state(bot);if(s==null || s.career!=Career.WARDEN || bot.isInState(CreatureState.WALK_MODE)==walk)return;
  if(walk)bot.setState(CreatureState.WALK_MODE);else bot.unsetState(CreatureState.WALK_MODE);
  PlayerBotFollowSpeed.close(bot);PacketSendUtility.broadcastToSightedPlayers(bot,new SM_EMOTION(bot,walk?EmotionType.WALK:EmotionType.RUN),true);
 }
 public static void releaseGait(Player bot){gait(bot,false);}
 /** A bounded physical-step admission, shared by patrol, aid and combat movement. */
 public static boolean permitRoadStep(Player bot,float x,float y,float z) {
  var s=state(bot);if(!PlayerBotWardenConfig.ENABLED || s==null || s.career!=Career.WARDEN)return true;
  int steps=Math.max(1,(int)Math.ceil(PositionUtil.getDistance(bot,x,y,z)/2));if(steps>16)return false;
  for(int i=1;i<=steps;i++) {float t=i/(float)steps;
   if(!PlayerBotWardenBeats.field(bot.getWorldId(),bot.getX()+t*(x-bot.getX()),bot.getY()+t*(y-bot.getY()),bot.getZ()+t*(z-bot.getZ())))return false;
  }return true;
 }
 /** One unseen native placement per recovery turn. A refused placement leaves ordinary activity in charge. */
 private static boolean recover(State s,long now) {
  if(now<s.recoveryAt)return false;s.recoveryAt=now+30000;var bot=s.session.bot();
  if(s.route==null || !PlayerBotWorldPlacement.idle(s.session) || bot.getInstanceId()!=1 || s.session.worldExcursion()
   || PlayerBotWorldActivities.committedTravel(bot) || PlayerBotQuestObjects.pending(bot)
   || bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ACTION_ITEM_NPC)
   || bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress()
   || PlayerBotWorldPlacement.nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()))return false;
  var step=advance(s.point,s.direction,s.route.points().size());var target=lane(bot,s.route,step.point(),step.direction(),1);
  if(!target.floor())return false;var p=target.point();var geo=GeoService.getInstance();
  if(!geo.isPlayerBotLanding(s.route.map(),1,p.x(),p.y(),p.z()))return false;
  var landing=new PlayerBotWorldPlacement.Location(bot.getRace(),s.route.map(),p.x(),p.y(),p.z(),1,65,false,false);
  if(!PlayerBotWorldPlacement.placeAt(s.session,landing))return false;
  s.point=step.point();s.direction=step.direction();s.lane=target;s.ahead.clear();s.suspended=false;s.next=now+1500;s.progress.reset();s.save();
  return true;
 }
 /** Hub spawns all pick the same nearest road and would walk it together, so each merchant takes the map's emptiest one. */
 private static void spread(State s) {
  if(s.career!=Career.MERCHANT || s.route==null)return;
  var bot=s.session.bot();var used=new HashMap<String,Integer>();
  for(var other:STATES.values())if(other!=s && other.career==Career.MERCHANT && other.route!=null)used.merge(other.route.id(),1,Integer::sum);
  var route=emptiest(PlayerBotPatrolRoutes.all().values().stream().filter(r->r.map()==s.route.map() && r.race()==s.route.race()).toList(),s.route,bot.getLevel(),used,r->PlayerBotPatrolRoutes.reachable(bot,r));
  if(route==null || route==s.route)return;
  s.route=route;s.point=route.nearest(bot);s.direction=1;s.lane=null;s.ahead.clear();s.progress.reset();
  s.values.setProperty("route",route.id());
 }
 /** Fewest merchants first, then a road of the merchant's level, and the current road whenever nothing is emptier. */
 static PlayerBotPatrolRoutes.Route emptiest(Collection<PlayerBotPatrolRoutes.Route> routes,PlayerBotPatrolRoutes.Route current,int level,Map<String,Integer> used) {
  var best=current;
  for(var route:routes) {
   int count=used.getOrDefault(route.id(),0),held=used.getOrDefault(best.id(),0);
   boolean fits=Math.abs(route.level()-level)<=12,bestFits=Math.abs(best.level()-level)<=12;
   if(count<held || count==held && fits && !bestFits || count==held && fits==bestFits && best!=current && route.id().compareTo(best.id())<0)best=route;
  }
  return best;
 }
 static PlayerBotPatrolRoutes.Route emptiest(Collection<PlayerBotPatrolRoutes.Route> routes,PlayerBotPatrolRoutes.Route current,int level,Map<String,Integer> used,java.util.function.Predicate<PlayerBotPatrolRoutes.Route> reachable) {
  var choices=routes.stream().filter(reachable).toList();if(choices.isEmpty())return null;
  return emptiest(choices,choices.contains(current)?current:choices.getFirst(),level,used);
 }
 /** Roads cross and run side by side: the later of two merchants waits for the gap, so they never travel as a group. */
 private static boolean crowded(State s,long now) {
  if(s.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED) {
   if(now<s.spacingCheck)return false;s.spacingCheck=now+2000;var bot=s.session.bot();boolean[] held={false};
   bot.getKnownList().forEachPlayer(p->{var other=state(p);if(p.getObjectId()<bot.getObjectId() && other!=null && other.career==Career.WARDEN
    && onDuty(p) && PositionUtil.isInRange(bot,p,PlayerBotWardenConfig.SPACING))held[0]=true;});return held[0];
  }
  if(now<s.spacingCheck || s.career!=Career.MERCHANT)return false;
  s.spacingCheck=now+2000;var bot=s.session.bot();
  for(var other:STATES.values())if(other!=s && other.career==Career.MERCHANT && other.session.bot().getObjectId()<bot.getObjectId()
   && other.session.bot().getWorldId()==bot.getWorldId() && other.session.bot().getInstanceId()==bot.getInstanceId()
   && other.session.bot().isSpawned() && PositionUtil.isInRange(bot,other.session.bot(),MERCHANT_SPACING))return true;
  return false;
 }
 static boolean withinRoad(Player actor,float radius) {
  var s=state(actor);return s!=null && s.route!=null && s.route.distance(actor)<=radius;
 }
 static boolean recipient(Player bot,Player target) {
  if(PlayerBotWardenConfig.ENABLED && PlayerBotWardens.traveller(bot,target))return true;
  var s=state(bot);return !onDuty(bot) || borrowed(bot) || target==bot || s.route.map()==target.getWorldId() && s.route.distance(target)<=80;
 }
 static boolean assisting(Player bot,Npc enemy) {
  if(PlayerBotWardenConfig.ENABLED && PlayerBotWardens.assisting(bot,enemy))return true;
  if(PlayerBotWardenConfig.ENABLED && (!(enemy.getTarget() instanceof com.aionemu.gameserver.model.gameobjects.Creature target) || !(target.getMaster() instanceof Player player) || !player.isPlayerBot()))return false;
  var s=state(bot);if(!onDuty(bot) || borrowed(bot) || s.career!=Career.WARDEN || enemy.isDead()
   || enemy.getWorldId()!=s.route.map() || s.route.distance(enemy.getX(),enemy.getY(),enemy.getZ())>100)return false;
  if(!(enemy.getTarget() instanceof com.aionemu.gameserver.model.gameobjects.Creature victim))return false;
  if(!(victim.getMaster() instanceof Player player) || player.isDead() || player.getRace()!=bot.getRace()
   || !PlayerBotSupport.eligible(bot,player) || !enemy.getAggroList().isHating(victim))return false;
  return PositionUtil.isInRange(bot,enemy,40) && recipient(bot,player);
 }
 static boolean enemyAllowed(Player bot,Npc enemy) {
  if(PlayerBotWardenConfig.ENABLED && publicWarden(bot) && !PlayerBotWardenBeats.field(enemy.getWorldId(),enemy.getX(),enemy.getY(),enemy.getZ()))return false;
  if(PlayerBotWardenConfig.ENABLED && PlayerBotWardens.assisting(bot,enemy))return true;
  var s=state(bot);return !onDuty(bot) || borrowed(bot) || enemy.getAggroList().isHating(bot)
   || s.route.map()==enemy.getWorldId() && s.route.distance(enemy.getX(),enemy.getY(),enemy.getZ())<=100;
 }
 /** A real traveller of this faction has selected this resident, or is at a merchant with goods. */
 private static boolean attended(State s,long now) {
  if(now<s.attendCheck)return s.attended;
  s.attendCheck=now+750;var bot=s.session.bot();boolean stocked=s.career==Career.MERCHANT && PlayerBotRoadMerchants.stocked(s);
  int near=bot.hasStore()?PlayerBotMerchantConfig.SHOP_CLOSE_RANGE:PlayerBotMerchantConfig.SHOP_OPEN_RANGE;boolean[] found={false};
  bot.getKnownList().forEachPlayer(p->{if(found[0] || p.isPlayerBot() || p.isDead() || !p.isSpawned() || p.getRace()!=bot.getRace() || p.getInstanceId()!=bot.getInstanceId())return;
   found[0]=p.getTarget()==bot && PositionUtil.isInRange(p,bot,TARGET_PAUSE_RANGE) || stocked && PositionUtil.isInRange(p,bot,near);});
  return s.attended=found[0];
 }
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation) {
  var bot=session.bot();var s=state(bot);if(!session.autonomous() || !onDuty(bot))return false;
  long now=System.currentTimeMillis();
  if(s.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED && PlayerBotWardens.travel(s,navigation,now))return true;
  if(s.visitor!=0 && now<s.stopUntil) {
   var visitor=World.getInstance().getPlayer(s.visitor);
   if(visitor!=null && visitor.isOnline() && !visitor.isDead() && visitor.getWorldId()==bot.getWorldId()
    && visitor.getInstanceId()==bot.getInstanceId() && s.route.distance(visitor)<=80 && PositionUtil.isInRange(bot,visitor,80)) {
    if(now<s.escortUntil && !PositionUtil.isInRange(bot,visitor,6))navigation.approach(visitor,4);else navigation.stop();
    s.progress.reset();
    return true;
   }
  }
  if(s.visitor!=0){s.visitor=0;s.stopUntil=s.escortUntil=0;s.save();}
  if(s.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED) {
   s.patrolled=walkDecision(true,bot.getController().isInCombat(),false,false,s.suspended,!bot.isDead(),session.autonomous(),s.route.distance(bot));
   gait(bot,s.patrolled && PlayerBotWardenConfig.WALK);
  }
  if(attended(s,now)){s.progress.reset();navigation.stop();if(s.career==Career.MERCHANT)PlayerBotRoadMerchants.open(s);return true;}
  PlayerBotRoadMerchants.close(bot);
  if(now<s.next){s.progress.reset();return true;}
  if(crowded(s,now)){navigation.stop();s.next=now+5000;s.progress.reset();return true;}
  if(s.progress.stalled(now,bot.getX(),bot.getY(),bot.getZ())) {
   // A stationary planner or blocked road gets one safe native recovery attempt, then ordinary activity.
   // Keep the career/stock reserved while suspended; never teleport in view or retry duty in place forever.
   navigation.stop();s.suspended=true;s.next=0;s.progress.reset();PlayerBotRoadMerchants.close(bot);s.save();recover(s,now);return true;
  }
  if(bot.getWorldId()!=s.route.map()) {
   var point=s.route.points().get(s.point);
   PlayerBotWorldTravel.move(bot,new PlayerBotWorldTravel.Place(s.route.map(),s.route.npc(),point.x(),point.y(),point.z()),navigation);return true;
  }
  // Cache each lane once. A bounded lookahead retains dense walker samples in the mover's corridor.
  for(int passed=0;passed<8;passed++) {
   if(s.lane==null)s.lane=lane(bot,s.route,s.point,s.direction,bot.getInstanceId());
   var point=s.lane.point();
   double flat=PositionUtil.getDistance(bot.getX(),bot.getY(),point.x(),point.y()),rise=Math.abs(bot.getZ()-point.z());
   if(arrived(flat,rise,s.lane.floor()))navigation.roadPassed(point);
   else if(!navigation.roadArrived(point))break;
   var step=advance(s.point,s.direction,s.route.points().size());s.point=step.point();s.direction=step.direction();
   s.lane=step.pause()?null:s.ahead.pollFirst();
   if(step.pause())s.ahead.clear();
   if(step.pause()){navigation.stop();s.next=now+(s.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED
    ?1000L*(PlayerBotWardenConfig.STOP_MIN+Math.floorMod(bot.getObjectId(),Math.max(1,PlayerBotWardenConfig.STOP_MAX-PlayerBotWardenConfig.STOP_MIN+1)))
    :1500+Math.floorMod(bot.getObjectId(),2500));s.progress.reset();s.save();return true;}
   s.save();
  }
  if(s.lane==null)s.lane=lane(bot,s.route,s.point,s.direction,bot.getInstanceId());
  double length=0;var previous=s.lane.point();
  for(var cached:s.ahead){var p=cached.point();length+=PositionUtil.getDistance(previous.x(),previous.y(),previous.z(),p.x(),p.y(),p.z());previous=p;}
  while(s.ahead.size()<8 && length<Math.max(12,bot.getGameStats().getMovementSpeedFloat()*2+6)) {
   int index=s.point+s.direction*(s.ahead.size()+1);if(index<0 || index>=s.route.points().size())break;
   var next=lane(bot,s.route,index,s.direction,bot.getInstanceId());var p=next.point();
   length+=PositionUtil.getDistance(previous.x(),previous.y(),previous.z(),p.x(),p.y(),p.z());s.ahead.addLast(next);previous=p;
  }
  navigation.road(s.lane.point(),s.ahead.stream().map(Lane::point).toList());return true;
 }
 /** Native group invitation: a Warden starts or ends its escort, a merchant points to its shop. */
 static void invited(Player human,Player bot) {
  var s=state(bot);
  if(s.career==Career.WARDEN && PlayerBotWardenConfig.ENABLED){PlayerBotWardens.invited(human,bot);return;}
  if(s.career==Career.MERCHANT){PacketSendUtility.sendMessage(human,bot.getName()+" is a travelling merchant. Walk up to them and right-click their shop to browse.");return;}
  synchronized(s.session) {
   long now=System.currentTimeMillis();
   if(!s.session.autonomous() || !PlayerBotService.getInstance().current(s.session) || bot.isDead()){PacketSendUtility.sendMessage(human,bot.getName()+" cannot help right now.");return;}
   if(human.getRace()!=bot.getRace() || human.getWorldId()!=bot.getWorldId() || human.getInstanceId()!=bot.getInstanceId() || !PositionUtil.isInRange(human,bot,TARGET_PAUSE_RANGE)){PacketSendUtility.sendMessage(human,"Move closer to "+bot.getName()+" and invite them again.");return;}
   if(bot.getController().isInCombat()){PacketSendUtility.sendMessage(human,bot.getName()+" is fighting; ask again when the road is clear.");return;}
   boolean escorting=s.visitor==human.getObjectId() && now<s.escortUntil;
   if(s.visitor!=0 && !escorting && now<s.stopUntil){PacketSendUtility.sendMessage(human,bot.getName()+" is already helping another traveller.");return;}
   s.visitor=escorting?0:human.getObjectId();s.stopUntil=s.escortUntil=escorting?0:now+120000;s.save();
   bot.getMoveController().abortMove();
   PacketSendUtility.sendMessage(human,escorting?bot.getName()+" returns to patrol.":bot.getName()+" will escort you along this road for two minutes. Invite them again to release them.");
  }
 }
 static void close(Player bot){gait(bot,false);PlayerBotWardens.close(bot);PlayerBotRoadMerchants.close(bot);STATES.remove(bot.getObjectId());releaseBeat(bot);}
 private static synchronized void releaseBeat(Player bot){BEATS.remove(bot.getObjectId());}
 private PlayerBotRoadDuty(){}
}
