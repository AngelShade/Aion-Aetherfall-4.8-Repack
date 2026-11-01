package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.commons.utils.WeightedRandom;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig;
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
  int point,direction=1,visitor,shopOffset;long next,stopUntil,escortUntil,progressAt,attendCheck,spacingCheck;boolean attended;double best=Double.POSITIVE_INFINITY;Lane lane;
  State(PlayerBotSession session,Properties values) {
   this.session=session;this.values=values;career=Career.valueOf(values.getProperty("career","NONE"));rank=Rank.valueOf(values.getProperty("rank","PEDDLER"));
   route=PlayerBotPatrolRoutes.all().get(values.getProperty("route",""));
   point=Integer.parseInt(values.getProperty("point","0"));direction=Integer.parseInt(values.getProperty("direction","1"));
   visitor=Integer.parseInt(values.getProperty("visitor","0"));stopUntil=Long.parseLong(values.getProperty("stopUntil","0"));
   escortUntil=Long.parseLong(values.getProperty("escortUntil","0"));next=Long.parseLong(values.getProperty("waitUntil","0"));
   if(route!=null)point=Math.clamp(point,0,route.points().size()-1);
  }
  void save() {
   values.setProperty("point",Integer.toString(point));values.setProperty("direction",Integer.toString(direction));
   values.setProperty("visitor",Integer.toString(visitor));values.setProperty("stopUntil",Long.toString(stopUntil));
   values.setProperty("escortUntil",Long.toString(escortUntil));values.setProperty("waitUntil",Long.toString(next));
   try{PlayerBotMetadata.save(session.bot().getAccount().getId(),session.bot().getObjectId(),"road-duty",values);}
   catch(java.io.IOException error){throw new IllegalStateException("Cannot preserve road duty",error);}
  }
 }
 private static final int TARGET_PAUSE_RANGE=30;
 private static final float ARRIVAL_RANGE=3,FLOOR_RANGE=2.5f;
 private static final int MERCHANT_SPACING=30;
 /** floor: point.z() is walkable ground. Otherwise it is a raw walker height, which can float above any floor. */
 record Lane(PlayerBotNavigation.Point point,boolean floor) {}
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 static State state(Player bot){return bot==null?null:STATES.get(bot.getObjectId());}
 public static boolean assigned(Player bot){var s=state(bot);return PlayerBotConfig.ROAD_DUTY && s!=null && s.career!=Career.NONE && s.route!=null;}
 public static boolean publicWarden(Player bot){var s=state(bot);return assigned(bot) && s.career==Career.WARDEN && s.session.autonomous();}
 static boolean merchant(Player bot){var s=state(bot);return assigned(bot) && s.career==Career.MERCHANT;}
 static PlayerBotPersistence.Home savedHome(Player bot) throws java.io.IOException {
  if(!PlayerBotConfig.ROAD_DUTY || !PlayerBotPopulationManager.global(bot))return null;
  var values=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"road-duty",null);
  if(values.getProperty("career","NONE").equals("NONE"))return null;
  var route=PlayerBotPatrolRoutes.all().get(values.getProperty("route",""));if(route==null)return null;
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
 static void maintenance(PlayerBotSession session) {
  var bot=session.bot();
  if(bot.hasStore() && (!PlayerBotConfig.ROAD_DUTY || !session.autonomous() || bot.isDead() || bot.getController().isInCombat()))PlayerBotRoadMerchants.close(bot);
  if(!PlayerBotConfig.ROAD_DUTY || !session.autonomous() || !PlayerBotPopulationManager.global(bot))return;
  var state=STATES.get(bot.getObjectId());
  if(state==null) {
   try {
    var p=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"road-duty",null);
    if(p.isEmpty()) {
     p.setProperty("account",Integer.toString(bot.getAccount().getId()));p.setProperty("character",Integer.toString(bot.getObjectId()));
     // Stable career allocation; the separately rolled rank is committed, never re-rolled at login.
     int bucket=Math.floorMod(Integer.rotateLeft(bot.getObjectId()*0x9e3779b9,13),100);
     var route=PlayerBotPatrolRoutes.choose(bot);
     Career career=route==null?Career.NONE:bucket<12?Career.MERCHANT:bucket<36?Career.WARDEN:Career.NONE;
     p.setProperty("career",career.name());p.setProperty("rank",(career==Career.MERCHANT?RANKS.sample():Rank.PEDDLER).name());
     if(route!=null && career!=Career.NONE){p.setProperty("route",route.id());p.setProperty("point",Integer.toString(route.nearest(bot)));}
    }
    state=new State(session,p);STATES.put(bot.getObjectId(),state);spread(state);state.save();
   }catch(java.io.IOException error){throw new IllegalStateException("Cannot load native road-duty career",error);}
  }
  if(merchant(bot))PlayerBotRoadMerchants.maintenance(state);
 }
 /** Hub spawns all pick the same nearest road and would walk it together, so each merchant takes the map's emptiest one. */
 private static void spread(State s) {
  if(s.career!=Career.MERCHANT || s.route==null)return;
  var bot=s.session.bot();var used=new HashMap<String,Integer>();
  for(var other:STATES.values())if(other!=s && other.career==Career.MERCHANT && other.route!=null)used.merge(other.route.id(),1,Integer::sum);
  var route=emptiest(PlayerBotPatrolRoutes.all().values().stream().filter(r->r.map()==s.route.map() && r.race()==s.route.race()).toList(),s.route,bot.getLevel(),used);
  if(route==s.route)return;
  s.route=route;s.point=route.nearest(bot);s.direction=1;s.lane=null;s.best=Double.POSITIVE_INFINITY;
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
 /** Roads cross and run side by side: the later of two merchants waits for the gap, so they never travel as a group. */
 private static boolean crowded(State s,long now) {
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
  var s=state(bot);return !assigned(bot) || target==bot || s.route.map()==target.getWorldId() && s.route.distance(target)<=80;
 }
 static boolean assisting(Player bot,Npc enemy) {
  var s=state(bot);if(!assigned(bot) || s.career!=Career.WARDEN || enemy.isDead()
   || enemy.getWorldId()!=s.route.map() || s.route.distance(enemy.getX(),enemy.getY(),enemy.getZ())>100)return false;
  if(!(enemy.getTarget() instanceof com.aionemu.gameserver.model.gameobjects.Creature victim))return false;
  if(!(victim.getMaster() instanceof Player player) || player.isDead() || player.getRace()!=bot.getRace()
   || !PlayerBotSupport.eligible(bot,player) || !enemy.getAggroList().isHating(victim))return false;
  return PositionUtil.isInRange(bot,enemy,40) && recipient(bot,player);
 }
 static boolean enemyAllowed(Player bot,Npc enemy) {
  var s=state(bot);return !assigned(bot) || enemy.getAggroList().isHating(bot)
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
  var bot=session.bot();var s=state(bot);if(!session.autonomous() || !assigned(bot))return false;
  if(bot.getWorldId()!=s.route.map()) {
   var point=s.route.points().get(s.point);
   PlayerBotWorldTravel.move(bot,new PlayerBotWorldTravel.Place(s.route.map(),s.route.npc(),point.x(),point.y(),point.z()),navigation);return true;
  }
  long now=System.currentTimeMillis();
  if(s.visitor!=0 && now<s.stopUntil) {
   var visitor=World.getInstance().getPlayer(s.visitor);
   if(visitor!=null && visitor.isOnline() && !visitor.isDead() && visitor.getWorldId()==bot.getWorldId()
    && visitor.getInstanceId()==bot.getInstanceId() && s.route.distance(visitor)<=80 && PositionUtil.isInRange(bot,visitor,80)) {
    if(now<s.escortUntil && !PositionUtil.isInRange(bot,visitor,6))navigation.approach(visitor,4);else navigation.stop();
    return true;
   }
  }
  if(s.visitor!=0){s.visitor=0;s.stopUntil=s.escortUntil=0;s.save();}
  if(attended(s,now)){navigation.stop();if(s.career==Career.MERCHANT)PlayerBotRoadMerchants.open(s);return true;}
  PlayerBotRoadMerchants.close(bot);
  if(s.lane==null)s.lane=lane(bot,s.route,s.point,s.direction,bot.getInstanceId());
  var point=s.lane.point();
  double flat=PositionUtil.getDistance(bot.getX(),bot.getY(),point.x(),point.y()),rise=Math.abs(bot.getZ()-point.z());
  if(arrived(flat,rise,s.lane.floor())) {
   navigation.stop();s.point+=s.direction;
   if(s.point>=s.route.points().size()){s.direction=-1;s.point=s.route.points().size()-2;}
   else if(s.point<0){s.direction=1;s.point=1;}
   s.lane=null;s.best=Double.POSITIVE_INFINITY;s.progressAt=now;s.next=now+1500+Math.floorMod(bot.getObjectId(),2500);s.save();return true;
  }
  if(now<s.next)return true;
  if(crowded(s,now)){navigation.stop();s.next=now+5000;s.progressAt=now;return true;}
  // Climbing toward a floor directly overhead is progress too.
  double distance=s.lane.floor()?flat+rise:flat;
  if(distance+1<s.best){s.best=distance;s.progressAt=now;}
  // A blocked route retries in place; it never teleports through obstacles or chooses an unrelated area.
  if(s.progressAt!=0 && now-s.progressAt>60000){navigation.stop();s.lane=new Lane(s.route.points().get(s.point),false);s.next=now+10000;s.progressAt=now;s.best=Double.POSITIVE_INFINITY;s.save();return true;}
  navigation.move(point.x(),point.y(),point.z());return true;
 }
 /** Native group invitation: a Warden starts or ends a short escort, a merchant points to its shop. */
 static void invited(Player human,Player bot) {
  var s=state(bot);
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
 static void close(Player bot){PlayerBotRoadMerchants.close(bot);STATES.remove(bot.getObjectId());}
 private PlayerBotRoadDuty(){}
}
