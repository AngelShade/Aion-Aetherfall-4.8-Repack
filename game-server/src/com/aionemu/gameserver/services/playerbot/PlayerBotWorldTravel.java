/* ChooseTravelTargetAction / TravelMgr purpose adapted from mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.teleport.*;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native spawn destinations and directed transport routes; walking always uses the existing geodata navigator. */
final class PlayerBotWorldTravel {
 record Place(int map,int npc,float x,float y,float z) {
  Place {if(map<=0 || npc<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))throw new IllegalArgumentException("Invalid travel destination");}
 }
 record Edge(Place source,int destination,int location) {}
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
    if(location.getType()!=TeleportType.REGULAR || destination==null)continue;
    var map=DataManager.WORLD_MAPS_DATA.getTemplate(destination.getMapId());
    if(map!=null && !map.isInstance() && source.map()!=destination.getMapId())result.add(new Edge(source,destination.getMapId(),location.getLocId()));
   }
  edges=List.copyOf(result);return edges;
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
 record Distances(int map,float x,float y,float z,Map<Integer,List<Edge>> routes) {
  double distance(Place place) {
   if(map==place.map())return distanceTo(place);
   var path=routes.get(place.map());return path==null?Double.POSITIVE_INFINITY:2000*path.size()+distanceTo(path.getFirst().source());
  }
  private double distanceTo(Place p){return Math.sqrt(Math.pow(x-p.x(),2)+Math.pow(y-p.y(),2)+Math.pow(z-p.z(),2));}
 }
 static Distances distances(Player bot) {
  return new Distances(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ(),routes(bot.getWorldId(),edges(),e->permitted(bot,e)));
 }
 static TeleportLocation location(Edge edge) {
  var t=DataManager.TELEPORTER_DATA.getTeleporterTemplateByNpcId(edge.source().npc());
  return t==null?null:t.getTeleLocIdData().getTelelocations().stream().filter(l->l.getLocId()==edge.location()).findFirst().orElse(null);
 }
 static boolean permitted(Player bot,Edge edge) {
  var npc=DataManager.NPC_DATA.getNpcTemplate(edge.source().npc());var l=location(edge);
  return npc!=null && (npc.getRace()==bot.getRace() || npc.getRace()==Race.PC_ALL) && l!=null
   && (l.getRequiredQuest()==0 || bot.isCompleteQuest(l.getRequiredQuest()))
   && com.aionemu.gameserver.services.trade.PricesService.getPriceForService(l.getPrice(),bot.getRace())<=bot.getInventory().getKinah();
 }
 static List<Edge> route(Player bot,int destination) {return route(bot.getWorldId(),destination,edges().stream().filter(e->permitted(bot,e)).toList());}
 static double distance(Player bot,Place place) {
  if(bot.getWorldId()==place.map())return PositionUtil.getDistance(bot,place.x(),place.y(),place.z());
  var path=route(bot,place.map());return path==null?Double.POSITIVE_INFINITY:2000*path.size()+PositionUtil.getDistance(bot,path.getFirst().source().x(),path.getFirst().source().y(),path.getFirst().source().z());
 }
 static boolean move(Player bot,Place destination,PlayerBotNavigation navigation) {
  // This transport attempt may return before invoking the ground navigator.
  // It must not inherit another action's terminal search result.
  navigation.clearRouteFailure();
  if(bot.getWorldId()==destination.map())return navigation.move(destination.x(),destination.y(),destination.z());
  var path=route(bot,destination.map());if(path==null || path.isEmpty())return false;
  var edge=path.getFirst();Npc[] nearby={null};
  bot.getKnownList().forEachNpc(n->{if(n.getNpcId()==edge.source().npc() && !n.isDead() && n.isSpawned() && n.getWorldId()==bot.getWorldId()
   && n.getInstanceId()==bot.getInstanceId() && (nearby[0]==null || PositionUtil.getDistance(bot,n)<PositionUtil.getDistance(bot,nearby[0])))nearby[0]=n;});
  var teleporter=nearby[0];
  if(teleporter==null)return navigation.move(edge.source().x(),edge.source().y(),edge.source().z());
  if(!PositionUtil.isInTalkRange(bot,teleporter))return navigation.approach(teleporter,2);
  navigation.stop();
  if(TeleportService.validateTeleporterAndGetTemplate(bot,teleporter)==null || !permitted(bot,edge))return false;
  int before=bot.getWorldId();TeleportService.teleport(bot,location(edge),TeleportAnimation.NONE);
  return bot.getWorldId()!=before && bot.isSpawned();
 }
 private PlayerBotWorldTravel(){}
}
