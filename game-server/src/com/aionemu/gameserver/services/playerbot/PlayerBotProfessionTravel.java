package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.craft.Profession;
import com.aionemu.gameserver.model.gameobjects.StaticObject;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.*;

/** A bounded visit to a native station; the existing navigator owns every walking step. */
final class PlayerBotProfessionTravel {
 enum Result { MOVING,READY,FALLBACK,CANCELLED }
 record Outcome(Result result,boolean walked) {}
 private record Key(Race race,Stage stage,int skill) {}
 private static final Map<Key,List<PlayerBotWorldTravel.Place>> STATIONS=new HashMap<>();
 /** Native CraftService.checkCraft tool range. */
 static final float CRAFT_RANGE=5;
 static final class Trip {
  final Plan plan;final PlayerBotWorldTravel.Place destination;final int instance;final long started;final double initial;
  long progress;double best;Outcome ended;
  Trip(Plan plan,PlayerBotWorldTravel.Place destination,int instance,long now,double distance){this.plan=plan;this.destination=destination;this.instance=instance;started=progress=now;initial=best=distance;}
 }
 interface Boundary {
  int map();int instance();boolean allowed();double distance(PlayerBotWorldTravel.Place place);
  boolean reachable(PlayerBotWorldTravel.Place place);boolean arrived(Trip trip);boolean move(PlayerBotWorldTravel.Place place);void stop();
 }
 static synchronized void prepare(){
  STATIONS.clear();for(var profession:Profession.values())for(var race:List.of(Race.ELYOS,Race.ASMODIANS)){
   int skill=profession.getSkillId();var trainers=PlayerBotQuestCrafting.trainers(skill).stream().filter(id->{var npc=DataManager.NPC_DATA.getNpcTemplate(id);return npc!=null && npc.getRace()==race;}).collect(java.util.stream.Collectors.toSet());
   var train=new Plan(Stage.TRAIN,0,0,skill,0,Map.of(),Map.of(),"");
   STATIONS.put(new Key(race,Stage.TRAIN,skill),PlayerBotQuestCraftingDriver.places(new PlayerBotQuestCraftingDriver.Need(Stage.TRAIN,0,0,0,skill,trainers,train,"")));
   int tool=toolId(skill);if(tool>0){var craft=new Plan(Stage.CRAFT,0,0,skill,tool,Map.of(),Map.of(),"");
    STATIONS.put(new Key(race,Stage.CRAFT,skill),PlayerBotQuestCraftingDriver.places(new PlayerBotQuestCraftingDriver.Need(Stage.CRAFT,0,0,0,skill,Set.of(tool),craft,"")));}
  }
 }
 static Trip begin(PlayerBotSession session,PlayerBotNavigation navigation,Plan plan,long now){
  return begin(boundary(session,navigation),plan,STATIONS.getOrDefault(new Key(session.bot().getRace(),plan.stage(),plan.skillId()),List.of()),now);
 }
 static Trip begin(Boundary nativeWork,Plan plan,List<PlayerBotWorldTravel.Place> stations,long now){
  if(!nativeWork.allowed() || plan.stage()!=Stage.TRAIN && plan.stage()!=Stage.CRAFT)return null;
  var candidates=stations.stream().filter(p->p.map()==nativeWork.map()).sorted(Comparator.comparingDouble(nativeWork::distance)).limit(PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN).toList();
  var destination=candidates.stream().filter(nativeWork::reachable).findFirst().orElse(null);
  return destination==null?null:new Trip(plan,destination,nativeWork.instance(),now,nativeWork.distance(destination));
 }
 static Outcome step(PlayerBotSession session,PlayerBotNavigation navigation,Trip trip,boolean allowed,long now){return step(boundary(session,navigation),trip,allowed,now);}
 static Outcome step(Boundary nativeWork,Trip trip,boolean allowed,long now){
  if(!allowed || !nativeWork.allowed()){var cancelled=new Outcome(Result.CANCELLED,false);if(trip!=null)trip.ended=cancelled;return cancelled;}
  if(trip==null)return new Outcome(Result.FALLBACK,false);
  if(trip.ended!=null)return trip.ended;
  if(trip.destination.map()!=nativeWork.map() || trip.instance!=nativeWork.instance())return trip.ended=new Outcome(Result.FALLBACK,false);
  double distance=nativeWork.distance(trip.destination);
  if(distance+.5<trip.best){trip.best=distance;trip.progress=now;}
  if(now-trip.started>=PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS*1000L || now-trip.progress>=PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS*1000L){
   nativeWork.stop();return trip.ended=new Outcome(Result.FALLBACK,false);
  }
  if(nativeWork.arrived(trip)){nativeWork.stop();return trip.ended=new Outcome(Result.READY,trip.best+.25<trip.initial);}
  if(distance<=4 || !nativeWork.reachable(trip.destination) || !nativeWork.move(trip.destination)){
   nativeWork.stop();return trip.ended=new Outcome(Result.FALLBACK,false);
  }
  return new Outcome(Result.MOVING,false);
 }
 private static Boundary boundary(PlayerBotSession session,PlayerBotNavigation navigation){return new Boundary(){
  final com.aionemu.gameserver.model.gameobjects.player.Player bot=session.bot();
  public int map(){return bot.getWorldId();}public int instance(){return bot.getInstanceId();}
  public boolean allowed(){return session.autonomous() && !session.dormant() && PlayerBotWorldActivity.occupied(bot) && bot.isSpawned() && !bot.isDead() && !bot.isFlying() && !bot.isCasting() && !bot.isTrading() && !bot.isLooting()
   && !bot.getController().isInCombat() && !bot.isUsingFlightTransporterOrWindstream() && bot.canPerformMove();}
  public double distance(PlayerBotWorldTravel.Place place){return PositionUtil.getDistance(bot,place.x(),place.y(),place.z());}
  public boolean reachable(PlayerBotWorldTravel.Place place){return place.map()==map() && !PlayerBotRouteSearch.recentlyUnreachable(map(),new PlayerBotNavigation.Point(bot.getX(),bot.getY(),bot.getZ()),new PlayerBotNavigation.Point(place.x(),place.y(),place.z()));}
  public boolean arrived(Trip trip){
   if(distance(trip.destination)>10)return false;boolean[] found={false};
   if(trip.plan.stage()==Stage.TRAIN)bot.getKnownList().forEachNpc(npc->{if(npc.getNpcId()==trip.destination.npc() && npc.getRace()==bot.getRace() && !npc.isDead() && npc.isSpawned() && !bot.isEnemy(npc)
    && npc.getWorldId()==map() && npc.getInstanceId()==instance() && bot.getKnownList().sees(npc) && PositionUtil.isInTalkRange(bot,npc) && GeoService.getInstance().canSee(bot,npc))found[0]=true;});
   // Same rule as native CraftService.checkCraft: 5 m between bounding edges, no sight ray (a bench's own mesh blocks it).
   else bot.getKnownList().forEachObject(object->{if(object instanceof StaticObject tool && tool.getObjectTemplate().getTemplateId()==trip.plan.toolId() && tool.isSpawned()
    && tool.getWorldId()==map() && tool.getInstanceId()==instance() && bot.getKnownList().sees(tool) && PositionUtil.isInRange(bot,tool,CRAFT_RANGE,false))found[0]=true;});
   return found[0];
  }
  public boolean move(PlayerBotWorldTravel.Place place){return navigation.move(place.x(),place.y(),place.z()) || !navigation.routeFailed()
   && (navigation.planningRoute() || navigation.waitingForTerrainWork() || navigation.waitingForTransport() || navigation.routeProgressing(System.currentTimeMillis()));}
  public void stop(){navigation.stop();}
 };}
 private PlayerBotProfessionTravel(){}
}
