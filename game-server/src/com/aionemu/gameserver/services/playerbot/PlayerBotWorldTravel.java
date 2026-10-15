/* ChooseTravelTargetAction / TravelMgr / active TravelNode spatial-stop and
 * remaining-money routing purpose adapted from mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.teleport.*;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native spawn destinations and directed transport routes; walking always uses the existing geodata navigator. */
final class PlayerBotWorldTravel {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PlayerBotWorldTravel.class);
 private static final java.util.concurrent.atomic.AtomicBoolean CAPACITY_REPORTED=new java.util.concurrent.atomic.AtomicBoolean();
 record Place(int map,int npc,float x,float y,float z) {
  Place {if(map<=0 || npc<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))throw new IllegalArgumentException("Invalid travel destination");}
 }
 record Edge(Place source,int destination,int location) {}
 record Stop(int map,float x,float y,float z) {
  Stop {if(map<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))throw new IllegalArgumentException("Invalid native transport stop");}
 }
 record Transport(Edge edge,Stop arrival,long price,int durationMs) {
  Transport(Edge edge,Stop arrival,long price){this(edge,arrival,price,0);}
  Transport {if(edge.destination()!=arrival.map() || price<0 || durationMs<0)throw new IllegalArgumentException("Invalid native transport route");}
 }
 private record NativeRoute(Edge edge,Stop arrival,TeleportLocation location,Race race,TribeClass tribe) {}
 private static Map<Edge,NativeRoute> nativeRoutes=Map.of();
 private static SpatialGraph nativeGraph;
 private static final Map<Integer,List<Place>> PLACES=new HashMap<>();
 private record Cell(int map,int x,int y) {}
 static final class Catalog {
  private final List<Place> all;
  private final Map<Cell,List<Place>> cells;
  Catalog(Collection<Place> points) {
   all=catalog(points);var mutable=new HashMap<Cell,List<Place>>();
   for(var p:all)mutable.computeIfAbsent(new Cell(p.map(),cell(p.x()),cell(p.y())),k->new ArrayList<>()).add(p);
   var frozen=new HashMap<Cell,List<Place>>();mutable.forEach((key,value)->frozen.put(key,List.copyOf(value)));cells=Map.copyOf(frozen);
  }
  List<Place> all(){return all;}
  List<Place> near(int map,float x,float y,float z,float radius) {
   if(!Float.isFinite(radius) || radius<0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))throw new IllegalArgumentException("Invalid catalog query");
   var result=new ArrayList<Place>();
   for(int cx=cell(x-radius);cx<=cell(x+radius);cx++)for(int cy=cell(y-radius);cy<=cell(y+radius);cy++)
    for(var p:cells.getOrDefault(new Cell(map,cx,cy),List.of()))if(Math.pow(x-p.x(),2)+Math.pow(y-p.y(),2)+Math.pow(z-p.z(),2)<=radius*radius)result.add(p);
   return List.copyOf(result);
  }
  private static int cell(float v){return (int)Math.floor(v/256);}
 }
 private static final Map<Integer,Catalog> CATALOGS=new HashMap<>();
 private static List<Edge> edges;
 static synchronized List<Place> places(int npc) {
  return PLACES.computeIfAbsent(npc,id->{
   var result=new ArrayList<Place>();
   for(var map:DataManager.WORLD_MAPS_DATA)if(!map.isInstance()) {
    for(var group:DataManager.SPAWNS_DATA.getSpawnsForNpc(map.getMapId(),id)) {
     if(group.isTemporarySpawn() || group.getHandlerType()!=null)continue;
     for(var spawn:group.getSpawnTemplates())if(!spawn.isAerialSpawn())result.add(new Place(map.getMapId(),id,spawn.getX(),spawn.getY(),spawn.getZ()));
    }
   }
   return catalog(result);
  });
 }
 /** All permanent ground points remain reachable; duplicates do not multiply destination weights. */
 static List<Place> catalog(Collection<Place> places){return List.copyOf(new LinkedHashSet<>(places));}
 static synchronized List<Place> near(int npc,Distances from,float radius){return CATALOGS.computeIfAbsent(npc,id->new Catalog(places(id))).near(from.map(),from.x(),from.y(),from.z(),radius);}
 private static synchronized List<Edge> edges() {
  if(edges!=null)return edges;
  var result=new ArrayList<Edge>();
  for(var teleporter:DataManager.TELEPORTER_DATA.getTemplates())for(int npc:teleporter.getNpcIds())for(var source:places(npc))
   for(var location:teleporter.getTeleLocIdData().getTelelocations()) {
    var destination=DataManager.TELELOCATION_DATA.getTelelocationTemplate(location.getLocId());
    if((location.getType()!=TeleportType.REGULAR && location.getType()!=TeleportType.FLIGHT) || destination==null)continue;
    Stop arrival=arrival(location,source);if(arrival==null)continue;
    var map=DataManager.WORLD_MAPS_DATA.getTemplate(arrival.map());
    if(map!=null && !map.isInstance())result.add(new Edge(source,arrival.map(),location.getLocId()));
   }
  var details=new HashMap<Edge,NativeRoute>();
  for(var edge:result){
   var template=DataManager.TELEPORTER_DATA.getTeleporterTemplateByNpcId(edge.source().npc());
   var npc=DataManager.NPC_DATA.getNpcTemplate(edge.source().npc());
   var landing=DataManager.TELELOCATION_DATA.getTelelocationTemplate(edge.location());
   if(template==null || npc==null || landing==null)continue;
   var location=template.getTeleLocIdData().getTelelocations().stream().filter(l->l.getLocId()==edge.location() && (l.getType()==TeleportType.REGULAR || l.getType()==TeleportType.FLIGHT)).findFirst().orElse(null);
   Stop arrival=location==null?null:arrival(location,edge.source());
   if(arrival!=null)details.put(edge,new NativeRoute(edge,arrival,location,npc.getRace(),npc.getTribe()));
  }
  nativeRoutes=Map.copyOf(details);edges=List.copyOf(new LinkedHashSet<>(result.stream().filter(details::containsKey).toList()));
  nativeGraph=new SpatialGraph(edges.stream().map(e->{var route=details.get(e);var path=route.location().getType()==TeleportType.FLIGHT?DataManager.FLY_PATH.getPathTemplate(e.location()):null;
   return new Transport(e,route.arrival(),0,path==null?0:path.getTimeInMs());}).toList());return edges;
 }
 private static Stop arrival(TeleportLocation location,Place source){
  if(location.getType()==TeleportType.FLIGHT){
   var path=DataManager.FLY_PATH.getPathTemplate(location.getLocId());
   if(path==null || path.getTimeInMs()<=0 || path.getStartWorldId()!=source.map()
    || between(stop(source),new Stop(path.getStartWorldId(),path.getStartX(),path.getStartY(),path.getStartZ()))>7)return null;
   return new Stop(path.getEndWorldId(),path.getEndX(),path.getEndY(),path.getEndZ());
  }
  var landing=DataManager.TELELOCATION_DATA.getTelelocationTemplate(location.getLocId());
  return landing==null?null:new Stop(landing.getMapId(),landing.getX(),landing.getY(),landing.getZ());
 }
 /** One breadth-first traversal per planning pass, rather than one full graph scan per destination. */
 static Map<Integer,List<Edge>> routes(int from,List<Edge> candidates,java.util.function.Predicate<Edge> permitted) {
  Map<Integer,List<Edge>> outgoing=new HashMap<>();
  for(var edge:candidates)if(permitted.test(edge))outgoing.computeIfAbsent(edge.source().map(),k->new ArrayList<>()).add(edge);
  Map<Integer,List<Edge>> paths=new HashMap<>();Deque<Integer> queue=new ArrayDeque<>();paths.put(from,List.of());queue.add(from);
  while(!queue.isEmpty()) {
   int map=queue.removeFirst();
   for(var edge:outgoing.getOrDefault(map,List.of()))if(!paths.containsKey(edge.destination())) {
    var path=new ArrayList<>(paths.get(map));path.add(edge);paths.put(edge.destination(),List.copyOf(path));queue.addLast(edge.destination());
   }
  }
  return Map.copyOf(paths);
 }
 static List<Edge> route(int from,int to,List<Edge> candidates) {
  return from==to?List.of():routes(from,candidates,e->true).get(to);
 }
 /** Short-lived snapshot: quest/cost/faction eligibility is refreshed for every decision and native execution. */
 record Distances(int map,float x,float y,float z,Map<Integer,List<Edge>> routes,Spatial spatial) {
  Distances(int map,float x,float y,float z,Map<Integer,List<Edge>> routes){this(map,x,y,z,routes,null);}
  double distance(Place place) {
   if(spatial!=null)return spatial.distance(place);
   if(map==place.map())return distanceTo(place);
   var path=routes.get(place.map());return path==null?Double.POSITIVE_INFINITY:2000*path.size()+distanceTo(path.getFirst().source());
  }
  private double distanceTo(Place p){return Math.sqrt(Math.pow(x-p.x(),2)+Math.pow(y-p.y(),2)+Math.pow(z-p.z(),2));}
 }
 /** One action's read-only planning pass. Never retain this in actor/static state;
  * native execution still refreshes eligibility, costs and the live teleporter. */
 static final class Decision {
  record Selection(Place place,double score) {}
  private final Player bot;
  private final Stop origin;
  private final int instance;
  private final Map<Place,Double> scores=new HashMap<>();
  private Distances snapshot;
  Decision(Player bot){this.bot=bot;origin=new Stop(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ());instance=bot.getInstanceId();}
  double planningDistance(Place place){return scores.computeIfAbsent(place,p->snapshot().distance(p));}
  /** Flat candidate lists need one score per entry, not a growing score cache
   * or repeated incumbent lookups. The graph remains fresh for this decision. */
  Selection nearest(Iterable<Place> places,java.util.function.Predicate<Place> available){
   Place chosen=null;double best=Double.POSITIVE_INFINITY;
   for(var place:places)if(available.test(place)){
    double score=snapshot().distance(place);
    if(chosen==null || Double.compare(score,best)<0){chosen=place;best=score;}
   }
   return chosen==null?null:new Selection(chosen,best);
  }
  /** Arrival/progress uses the actual same-map XYZ separation, never a route cost. */
  double distance(Place place){return origin.map()==place.map()?between(origin,stop(place)):planningDistance(place);}
  double distance(Selection selection){return origin.map()==selection.place().map()?between(origin,stop(selection.place())):selection.score();}
  private Distances snapshot(){if(snapshot==null)snapshot=distances(bot);return snapshot;}
  boolean move(Place place,PlayerBotNavigation navigation){
   // A map/instance/pose change invalidates even this short-lived nomination.
   boolean current=instance==bot.getInstanceId() && origin.equals(new Stop(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()));
   return PlayerBotWorldTravel.move(bot,place,navigation,current?snapshot:null);
  }
 }
 static Distances distances(Player bot) {
  return distances(bot,null,0);
 }
 private static Distances distances(Player bot,Place destination,int destinationMap) {
  var candidates=edges();
  if(candidates.isEmpty())return new Distances(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ(),Map.of());
  long kinah=bot.getInventory().getKinah();boolean hiPass=bot.getEffectController().hasAbnormalEffect(com.aionemu.gameserver.skillengine.model.Effect::isHiPass);
  long[] prices=new long[candidates.size()];Arrays.fill(prices,-1);var costs=new HashMap<Integer,Long>();
  for(int index=0;index<candidates.size();index++){var edge=candidates.get(index);
   if(!nativeGraph.reachable(bot.getWorldId(),edge.source().map()) || destinationMap!=0 && !nativeGraph.reachable(edge.destination(),destinationMap))continue;
   var nativeRoute=nativeRoutes.get(edge);
   if(nativeRoute==null || !eligible(bot,nativeRoute.race(),nativeRoute.tribe(),nativeRoute.location()))continue;
   long price=costs.computeIfAbsent(edge.location(),id->hiPass?1L:com.aionemu.gameserver.services.trade.PricesService.getPriceForService(nativeRoute.location().getPrice(),bot.getRace()));
   if(price>=0 && price<=kinah)prices[index]=price;
  }
  float speed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(bot,
   com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,bot.getGameStats().getMovementSpeedFloat());
  var spatial=new Spatial(new Stop(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()),nativeGraph,prices,kinah,speed,destination,destinationMap);
  if(spatial.capped() && CAPACITY_REPORTED.compareAndSet(false,true))LOG.warn("Playerbot native stop planning reached its bounded work limit; candidate costs cover admitted paths only, not complete map reachability.");
  return new Distances(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ(),Map.of(),spatial);
 }
 /** Native stop graph, never a floor proof. Walking connectors between stops on
  * one map are lower-bound costs; the navigator still proves every actual step.
  * Labels are ephemeral per planning pass and retain affordable alternatives.
  * Immutable native stop adjacency and walk lower bounds, built once per
  * static-data catalog. No per-actor all-pairs graph or eligibility cache. */
 static final class SpatialGraph {
  private record Arc(int index,double walking) {}
  final List<Transport> transports;
  final Map<Integer,List<Integer>> outgoing;
  final Map<Stop,List<Arc>> successors;
  private final Map<Integer,Set<Integer>> reachable;
  SpatialGraph(Collection<Transport> transports){
   this.transports=List.copyOf(transports);var byMap=new HashMap<Integer,List<Integer>>();
   for(int i=0;i<this.transports.size();i++)byMap.computeIfAbsent(this.transports.get(i).edge().source().map(),key->new ArrayList<>()).add(i);
   var frozen=new HashMap<Integer,List<Integer>>();byMap.forEach((map,indexes)->frozen.put(map,List.copyOf(indexes)));outgoing=Map.copyOf(frozen);
   var adjacency=new HashMap<Stop,List<Arc>>();
   for(var transport:this.transports)adjacency.computeIfAbsent(transport.arrival(),arrival->{
    var arcs=new ArrayList<Arc>();for(int index:outgoing.getOrDefault(arrival.map(),List.of()))arcs.add(new Arc(index,between(arrival,stop(this.transports.get(index).edge().source()))));return List.copyOf(arcs);
   });successors=Map.copyOf(adjacency);
   var maps=new HashSet<Integer>();var destinations=new HashMap<Integer,Set<Integer>>();
   for(var transport:this.transports){int from=transport.edge().source().map(),to=transport.arrival().map();maps.add(from);maps.add(to);destinations.computeIfAbsent(from,key->new HashSet<>()).add(to);}
   var reach=new HashMap<Integer,Set<Integer>>();
   for(int map:maps){var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();seen.add(map);queue.add(map);
    while(!queue.isEmpty())for(int next:destinations.getOrDefault(queue.removeFirst(),Set.of()))if(seen.add(next))queue.addLast(next);
    reach.put(map,Set.copyOf(seen));
   }reachable=Map.copyOf(reach);
  }
  boolean reachable(int from,int to){return from==to || reachable.getOrDefault(from,Set.of()).contains(to);}
 }
 static final class Spatial {
  private static final int MAX_LABELS=4096,MAX_RELAXATIONS=65536;
  private record Label(Stop stop,double score,long spent,Label parent,Edge edge,long sequence) {}
  private final Stop origin;
  private final Map<Integer,List<Label>> reached=new HashMap<>();
  private boolean capped;
  private int relaxations;
  Spatial(Stop origin,Collection<Transport> transports,long wallet,float speed) {
   this(origin,new SpatialGraph(transports),null,wallet,speed);
  }
  Spatial(Stop origin,SpatialGraph graph,long[] prices,long wallet,float speed) {
   this(origin,graph,prices,wallet,speed,null,0);
  }
  /** A single destination uses an incumbent complete-route cost as its exact
   * Dijkstra bound. No geometric heuristic prunes a possible transport shortcut. */
  Spatial(Stop origin,SpatialGraph graph,long[] prices,long wallet,float speed,Place destination,int destinationMap) {
   this.origin=origin;
   if(destination!=null && destination.map()!=destinationMap)throw new IllegalArgumentException("Native route destination mismatch");
   Stop goal=destination==null?null:stop(destination);
   double ceiling=destinationMap==origin.map()?(goal==null?0:between(origin,goal)):Double.POSITIVE_INFINITY;
   if(prices!=null && prices.length!=graph.transports.size())throw new IllegalArgumentException("Native route eligibility count mismatch");
   if(wallet<0 || !Float.isFinite(speed) || speed<=0)speed=6;
   double boardingCost=Math.max(.1,speed*.1); // active TravelNode's finite non-walking time cost
   var frontier=new PriorityQueue<Label>(Comparator.comparingDouble(Label::score).thenComparingLong(Label::spent).thenComparingLong(Label::sequence));
   var labels=new HashMap<Stop,List<Label>>();long sequence=0;int admitted=1,relaxed=0;
   var first=new Label(origin,0,0,null,null,sequence++);labels.put(origin,new ArrayList<>(List.of(first)));frontier.add(first);
   var initial=new ArrayList<SpatialGraph.Arc>();for(int index:graph.outgoing.getOrDefault(origin.map(),List.of()))initial.add(new SpatialGraph.Arc(index,between(origin,stop(graph.transports.get(index).edge().source()))));
   while(!frontier.isEmpty()){
    var current=frontier.remove();if(!labels.get(current.stop()).contains(current))continue;
    if(destinationMap!=0 && current.score()>ceiling)break;
    if(destinationMap!=0 && current.stop().map()==destinationMap)ceiling=Math.min(ceiling,current.score()+(goal==null?0:between(current.stop(),goal)));
    for(var arc:current.edge()==null?initial:graph.successors.getOrDefault(current.stop(),List.of())){
     var t=graph.transports.get(arc.index());
     if(destinationMap!=0 && !graph.reachable(t.arrival().map(),destinationMap))continue;
     if(++relaxed>MAX_RELAXATIONS){capped=true;break;}
     long price=prices==null?t.price():prices[arc.index()];
     if(price<0 || price>wallet-current.spent())continue;
     long spent=current.spent()+price;double score=current.score()+arc.walking()+boardingCost+t.durationMs()*speed/1000d;
     if(destinationMap!=0 && score>ceiling)continue;
     var at=labels.computeIfAbsent(t.arrival(),key->new ArrayList<>());
     boolean dominated=false;
     for(int i=0;i<at.size();i++){var old=at.get(i);if(old.score()<=score && old.spent()<=spent){dominated=true;break;}}
     if(dominated)continue;
     if(admitted>=MAX_LABELS){capped=true;break;}
     for(int i=at.size()-1;i>=0;i--){var old=at.get(i);if(score<=old.score() && spent<=old.spent())at.remove(i);}
     var next=new Label(t.arrival(),score,spent,current,t.edge(),sequence++);at.add(next);frontier.add(next);admitted++;
    }
    if(capped)break;
   }
   relaxations=relaxed;
   for(var at:labels.values())for(var label:at)reached.computeIfAbsent(label.stop().map(),key->new ArrayList<>()).add(label);
  }
  boolean capped(){return capped;}
  int relaxations(){return relaxations;}
  double distance(Place place){var best=best(place);return best==null?Double.POSITIVE_INFINITY:best.score()+between(best.stop(),stop(place));}
  List<Edge> route(Place place){var best=best(place);return best==null?null:path(best);}
  List<Edge> route(int map){return reached.getOrDefault(map,List.of()).stream().min(Comparator.comparingDouble(Label::score).thenComparingLong(Label::spent)).map(Spatial::path).orElse(null);}
  private Label best(Place place){
   Label chosen=null;double score=Double.POSITIVE_INFINITY;Stop destination=stop(place);double direct=between(origin,destination);
   for(var label:reached.getOrDefault(place.map(),List.of())){
    double candidate=label.score()+between(label.stop(),destination);
    // Avoid paying/turning back for gains smaller than native interaction/arrival tolerance.
    if(label.edge()!=null && origin.map()==place.map() && candidate>=direct-6)continue;
    if(candidate<score || candidate==score && chosen!=null && label.spent()<chosen.spent()){chosen=label;score=candidate;}
   }
   return chosen;
  }
  private static List<Edge> path(Label label){var result=new ArrayList<Edge>();for(var step=label;step.edge()!=null;step=step.parent())result.add(step.edge());Collections.reverse(result);return List.copyOf(result);}
 }
 private static Stop stop(Place p){return new Stop(p.map(),p.x(),p.y(),p.z());}
 private static double between(Stop a,Stop b){return a.map()==b.map()?Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2)):Double.POSITIVE_INFINITY;}
 static TeleportLocation location(Edge edge) {
  var t=DataManager.TELEPORTER_DATA.getTeleporterTemplateByNpcId(edge.source().npc());
  return t==null?null:t.getTeleLocIdData().getTelelocations().stream().filter(l->l.getLocId()==edge.location()).findFirst().orElse(null);
 }
 static boolean permitted(Player bot,Edge edge) {
  var npc=DataManager.NPC_DATA.getNpcTemplate(edge.source().npc());var l=location(edge);
  return npc!=null && l!=null && (l.getType()==TeleportType.REGULAR || l.getType()==TeleportType.FLIGHT) && arrival(l,edge.source())!=null && eligible(bot,npc.getRace(),npc.getTribe(),l)
   && (bot.getEffectController().hasAbnormalEffect(com.aionemu.gameserver.skillengine.model.Effect::isHiPass)?1L:com.aionemu.gameserver.services.trade.PricesService.getPriceForService(l.getPrice(),bot.getRace()))<=bot.getInventory().getKinah();
 }
 private static boolean eligible(Player bot,Race race,TribeClass tribe,TeleportLocation l){return friendly(bot.getRace(),race,tribe) && (l.getRequiredQuest()==0 || bot.isCompleteQuest(l.getRequiredQuest()));}
 /** Race.NONE transporters inherit native base-tribe friendliness;
  * race NONE alone grants nothing. Fresh live NPC type remains mandatory. */
 static boolean friendly(Race player,Race npc,TribeClass tribe){
  if(npc==player || npc==Race.PC_ALL)return true;
  if(npc!=Race.NONE || tribe==null || DataManager.TRIBE_RELATIONS_DATA==null)return false;
  TribeClass pc=player==Race.ELYOS?TribeClass.PC:player==Race.ASMODIANS?TribeClass.PC_DARK:null;
  if(pc==null)return false;var relations=DataManager.TRIBE_RELATIONS_DATA;
  if(relations.isNoneRelation(tribe,pc) || relations.isAggressiveRelation(tribe,pc) || relations.isHostileRelation(tribe,pc))return false;
  var base=relations.getBaseTribe(tribe);
  boolean light=(base==TribeClass.GENERAL && tribe!=TribeClass.DRAMA_EVE_NONPC_A && tribe!=TribeClass.DRAMA_EVE_NONPC_B) || base==TribeClass.FIELD_OBJECT_LIGHT;
  boolean dark=(base==TribeClass.GENERAL_DARK && tribe!=TribeClass.DRAMA_EVE_NONPC_DARKA && tribe!=TribeClass.DRAMA_EVE_NONPC_DARKB) || base==TribeClass.FIELD_OBJECT_DARK;
  return light && pc==TribeClass.PC || dark && pc==TribeClass.PC_DARK
   || relations.isFriendlyRelation(tribe,pc) || relations.isNeutralRelation(tribe,pc) || relations.isSupportRelation(tribe,pc);
 }
 static List<Edge> route(Player bot,int destination) {var d=distances(bot,null,destination);return d.spatial()==null?(bot.getWorldId()==destination?List.of():d.routes().get(destination)):d.spatial().route(destination);}
 static double distance(Player bot,Place place) {
  // Existing arrival/progress callers require actual same-map XYZ separation,
  // never the shorter transport-assisted planning estimate from Distances.
  if(bot.getWorldId()==place.map())return between(new Stop(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ()),stop(place));
  return distances(bot,place,place.map()).distance(place);
 }
 static boolean move(Player bot,Place destination,PlayerBotNavigation navigation) {
  return move(bot,destination,navigation,null);
 }
 private static boolean move(Player bot,Place destination,PlayerBotNavigation navigation,Distances prepared) {
  // This transport attempt may return before invoking the ground navigator.
  // It must not inherit another action's terminal search result.
  navigation.clearRouteFailure();
  var d=prepared==null?distances(bot,destination,destination.map()):prepared;
  var path=d.spatial()==null?(bot.getWorldId()==destination.map()?List.<Edge>of():d.routes().get(destination.map())):d.spatial().route(destination);
  if(path==null)return false;
  if(path.isEmpty())return navigation.move(destination.x(),destination.y(),destination.z());
  var edge=path.getFirst();Npc[] nearby={null};
  bot.getKnownList().forEachNpc(n->{if(n.getNpcId()==edge.source().npc() && !n.isDead() && n.isSpawned() && n.getWorldId()==bot.getWorldId()
   && n.getInstanceId()==bot.getInstanceId() && PositionUtil.getDistance(n,edge.source().x(),edge.source().y(),edge.source().z())<=8
   && (nearby[0]==null || PositionUtil.getDistance(bot,n)<PositionUtil.getDistance(bot,nearby[0])))nearby[0]=n;});
  var teleporter=nearby[0];
  if(teleporter==null)return navigation.move(edge.source().x(),edge.source().y(),edge.source().z());
  if(!PositionUtil.isInTalkRange(bot,teleporter))return navigation.approach(teleporter,2);
  navigation.stop();
  var template=TeleportService.validateTeleporterAndGetTemplate(bot,teleporter);
  var l=template==null?null:template.getTeleLocIdData().getTelelocations().stream().filter(t->t.getLocId()==edge.location() && (t.getType()==TeleportType.REGULAR || t.getType()==TeleportType.FLIGHT)).findFirst().orElse(null);
  var landing=DataManager.TELELOCATION_DATA.getTelelocationTemplate(edge.location());
  var expected=l==null?null:arrival(l,edge.source());
  if(l==null || landing==null || expected==null || expected.map()!=edge.destination() || !permitted(bot,edge))return false;
  Stop before=new Stop(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ());int instance=bot.getInstanceId();
  TeleportService.teleport(bot,l,TeleportAnimation.NONE);
  if(l.getType()==TeleportType.FLIGHT)return PlayerBotHeadlessFlight.active(bot);
  return arrived(before,instance,bot.isSpawned(),bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),expected);
 }
 static boolean arrived(Stop before,int beforeInstance,boolean spawned,int map,int instance,float x,float y,float z,Stop landing){
  if(!spawned || map!=landing.map() || instance!=(before.map()==map?beforeInstance:1) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))return false;
  var current=new Stop(map,x,y,z);return between(current,landing)<=1 && (before.map()!=map || between(before,current)>.15);
 }
 private PlayerBotWorldTravel(){}
}
