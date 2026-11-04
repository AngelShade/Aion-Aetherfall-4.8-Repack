package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.zone.ZoneInfo;

/** Prepared road beats. Geometry searches belong to the offline builder, never the actor tick. */
final class PlayerBotWardenBeats {
 record Beat(PlayerBotPatrolRoutes.Route route,String from,String to,double length) {}
 record RoadSpace(String id,double length) {}
 private record Segment(int map,Race race,PlayerBotNavigation.Point from,PlayerBotNavigation.Point to) {}
 private static Map<String,RoadSpace> spaces=Map.of();
 static int capacity(double length){return Math.max(1,(int)(length/Math.max(1,com.aionemu.gameserver.configs.main.PlayerBotWardenConfig.BEAT_CAPACITY_METRES)));}
 /** Exact shared native mesh edges identify physical road. Differently sampled roads remain independent. */
 static Map<String,RoadSpace> roadSpaces(Map<String,Beat> beats) {
  var groups=new HashMap<String,Set<String>>();var edges=new HashMap<Segment,String>();
  for(var beat:new TreeMap<>(beats).values()) {
   String id=beat.route().id();groups.put(id,new HashSet<>(Set.of(id)));
   var points=beat.route().points();
   for(int i=1;i<points.size();i++) {
    var a=points.get(i-1);var b=points.get(i);
    if(compare(a,b)>0){var swap=a;a=b;b=swap;}
    if(a.equals(b))continue;
    var edge=new Segment(beat.route().map(),beat.route().race(),a,b);String old=edges.putIfAbsent(edge,id);
    if(old!=null && groups.get(old)!=groups.get(id)) {
     var merged=groups.get(id);merged.addAll(groups.get(old));for(String member:merged)groups.put(member,merged);
    }
   }
  }
  var lengths=new HashMap<String,Double>();var ids=new HashMap<String,String>();
  for(String id:beats.keySet())ids.put(id,Collections.min(groups.get(id)));
  for(var entry:edges.entrySet())lengths.merge(ids.get(entry.getValue()),distance(entry.getKey().from(),entry.getKey().to()),Double::sum);
  var result=new HashMap<String,RoadSpace>();for(String id:beats.keySet())result.put(id,new RoadSpace(ids.get(id),lengths.getOrDefault(ids.get(id),0d)));
  return Map.copyOf(result);
 }
 private static int compare(PlayerBotNavigation.Point a,PlayerBotNavigation.Point b) {
  int order=Float.compare(a.x(),b.x());if(order==0)order=Float.compare(a.y(),b.y());return order==0?Float.compare(a.z(),b.z()):order;
 }
 static RoadSpace roadSpace(PlayerBotPatrolRoutes.Route route){all();return spaces.get(route.id());}
 record Geometry(int map,Race race,List<PlayerBotNavigation.Point> points) {}
 static Geometry geometry(PlayerBotPatrolRoutes.Route route) {
  var points=route.points();boolean reverse=false;
  for(int i=0;i<points.size();i++) {
   var a=points.get(i);var b=points.get(points.size()-1-i);int order=Float.compare(a.x(),b.x());
   if(order==0)order=Float.compare(a.y(),b.y());if(order==0)order=Float.compare(a.z(),b.z());
   if(order!=0){reverse=order>0;break;}
  }
  return new Geometry(route.map(),route.race(),reverse?List.copyOf(points.reversed()):points);
 }
 record Threat(String name,PlayerBotNavigation.Point point) {}
 private static Map<String,List<Threat>> threats=Map.of();
 static List<Threat> threats(String beat){all();return threats.getOrDefault(beat,List.of());}
 private static volatile Map<String,Beat> cache;
 private static final Map<Integer,Settlements> TERRITORY=new java.util.concurrent.ConcurrentHashMap<>();
 static boolean field(int map,float x,float y,float z) {
  return !TERRITORY.computeIfAbsent(map,Settlements::new).contains(new PlayerBotNavigation.Point(x,y,z));
 }
 static double length(List<PlayerBotNavigation.Point> points) {
  double length=0;for(int i=1;i<points.size();i++)length+=distance(points.get(i-1),points.get(i));return length;
 }
 static double distance(PlayerBotNavigation.Point a,PlayerBotNavigation.Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
 /** Native service camps and named settlement polygons, evaluated at each point, including height. */
 static final class Settlements {
  record Boundary(float x,float y,float z,float radius,String evidence) {}
  private final Map<Long,List<Boundary>> cells=new HashMap<>();
  final List<Boundary> boundaries=new ArrayList<>();
  private final List<ZoneInfo> zones;
  private void add(Boundary p) {
   boundaries.add(p);int x=(int)Math.floor(p.x()/64),y=(int)Math.floor(p.y()/64);
   for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)cells.computeIfAbsent(cell(x+dx,y+dy),k->new ArrayList<>()).add(p);
  }
  Settlements(int map) {
   for(var p:PlayerBotWorldPlacement.locations())if(p.map()==map && (p.hub() || p.city())) {
    add(new Boundary(p.x(),p.y(),p.z(),p.city()?100:60,"native service hub"));
   }
   // A village without a shop is still a settlement. Static friendly residents,
   // excluding walkers, identify its occupied footprint without a map-name gate.
   var residents=new ArrayList<Boundary>();
   for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map)) {
    var n=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());if(n==null || group.isTemporarySpawn() || group.getHandlerType()!=null
     || n.getRace()!=Race.ELYOS && n.getRace()!=Race.ASMODIANS && n.getRace()!=Race.PC_ALL)continue;
    for(var spawn:group.getSpawnTemplates())if(!spawn.isAerialSpawn() && spawn.getWalkerId()==null)
     residents.add(new Boundary(spawn.getX(),spawn.getY(),spawn.getZ(),65,"native friendly resident cluster"));
   }
   for(var p:residents)if(residents.stream().filter(q->Math.abs(p.z()-q.z())<20 && Math.hypot(p.x()-q.x(),p.y()-q.y())<=70).limit(5).count()>=5)add(p);
   zones=DataManager.ZONE_DATA.getZones(map).stream().filter(z->z.getZoneTemplate().getName().matches(".*(VILLAGE|TOWN|CITY|FORTRESS|CITADEL).*" )).toList();
  }
  private static long cell(int x,int y){return ((long)x<<32)|Integer.toUnsignedLong(y);}
  boolean contains(PlayerBotNavigation.Point p) {
   for(var z:zones)if(z.getArea().isInside3D(p.x(),p.y(),p.z()))return true;
   for(var hub:cells.getOrDefault(cell((int)Math.floor(p.x()/64),(int)Math.floor(p.y()/64)),List.of()))
    if(Math.abs(p.z()-hub.z())<20 && Math.hypot(p.x()-hub.x(),p.y()-hub.y())<hub.radius())return true;
   return false;
  }
  /** Positive lower setback from every native protected footprint, for offline review. */
  double clearance(PlayerBotNavigation.Point p) {
   double best=Double.POSITIVE_INFINITY;
   for(var z:zones)best=Math.min(best,z.getArea().getDistance3D(p.x(),p.y(),p.z()));
   for(var hub:boundaries)if(Math.abs(p.z()-hub.z())<20)best=Math.min(best,Math.hypot(p.x()-hub.x(),p.y()-hub.y())-hub.radius());
   return best;
  }
 }
 static Beat parse(String row,Settlements settlements) {
  var c=row.split("\t",-1);if(c.length!=10 || !c[0].matches("beat:[a-zA-Z0-9:_-]+"))throw new IllegalArgumentException("beat columns/id");
  int map=Integer.parseInt(c[1]),level=Integer.parseInt(c[3]),npc=Integer.parseInt(c[4]),hostiles=Integer.parseInt(c[8]);
  var race=Race.valueOf(c[2]);double declared=Double.parseDouble(c[7]);
  if(map<=0 || level<1 || level>65 || npc<=0 || race!=Race.ELYOS && race!=Race.ASMODIANS
   || c[5].isBlank() || c[6].isBlank() || c[5].length()>120 || c[6].length()>120 || c[5].equals(c[6]))throw new IllegalArgumentException("beat identity/ends");
  var points=new ArrayList<PlayerBotNavigation.Point>();
  for(String vector:c[9].split(";")) {
   var v=vector.split(",");if(v.length!=3)throw new IllegalArgumentException("beat vector");
   var p=new PlayerBotNavigation.Point(Float.parseFloat(v[0]),Float.parseFloat(v[1]),Float.parseFloat(v[2]));
   if(!Float.isFinite(p.x()) || !Float.isFinite(p.y()) || !Float.isFinite(p.z()) || settlements!=null && settlements.contains(p))throw new IllegalArgumentException("nonfinite/settlement point");
   if(!points.isEmpty() && distance(points.getLast(),p)>20.1)throw new IllegalArgumentException("beat sample gap");points.add(p);
  }
  double length=length(points);
  if(points.size()<4 || points.size()>1024 || length<300 || length>4000 || !Double.isFinite(declared) || Math.abs(length-declared)>2
   || hostiles<Math.max(5,(int)Math.ceil(length/300)))throw new IllegalArgumentException("beat size/length/hostile density");
  var immutable=List.copyOf(points);
  return new Beat(new PlayerBotPatrolRoutes.Route(c[0],map,race,level,npc,c[5]+" to "+c[6],immutable,PlayerBotPatrolRoutes.index(immutable,Math.max(200,com.aionemu.gameserver.configs.main.PlayerBotWardenConfig.LEASH)),true),c[5],c[6],length);
 }
 static Map<String,Beat> load(Path path) throws IOException {
  return load(path,map->TERRITORY.computeIfAbsent(map,Settlements::new));
 }
 static Map<String,Beat> load(Path path,java.util.function.IntFunction<Settlements> settlements) throws IOException {
  var beats=new TreeMap<String,Beat>();var geometry=new HashSet<Geometry>();var monsters=new HashMap<String,List<PlayerBotNavigation.Point>>();int line=0;
  for(String row:Files.readAllLines(path)) {
   line++;if(row.isBlank() || row.startsWith("#"))continue;
   try {
    int map=Integer.parseInt(row.split("\t",3)[1]);var town=settlements.apply(map);var beat=parse(row,town);
    if(town!=null) {
     var r=beat.route();var template=DataManager.WORLD_MAPS_DATA.getTemplate(map);
     if(!PlayerBotWorldPlacement.mapEligible(template,r.race(),Set.of()) || DataManager.NPC_DATA.getNpcTemplate(r.npc())==null)throw new IllegalArgumentException("missing native map/segment actor");
     var tribe=r.race()==Race.ELYOS?com.aionemu.gameserver.model.TribeClass.PC:com.aionemu.gameserver.model.TribeClass.PC_DARK;
     var field=monsters.computeIfAbsent(map+":"+r.race(),key->PlayerBotPatrolRoutes.monsters(map,tribe));
     long required=Math.max(5,(long)Math.ceil(beat.length()/300));
     if(field.stream().filter(p->r.distance(p.x(),p.y(),p.z())<=100).limit(required).count()<required)throw new IllegalArgumentException("native hostile density changed");
    }
    if(town!=null && beat.route().points().stream().anyMatch(p->!PlayerBotWorldPlacement.factionSide(beat.route().race(),map,p.x(),p.y(),p.z())))throw new IllegalArgumentException("hostile faction ground");
    if(beats.containsKey(beat.route().id()))throw new IllegalArgumentException("duplicate beat");
    if(!geometry.add(geometry(beat.route())))throw new IllegalArgumentException("duplicate/reversed beat geometry");
    beats.put(beat.route().id(),beat);
   }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(PlayerBotWardenBeats.class).warn("Rejected Warden beat row {}: {}",line,error.toString());}
  }
  return Map.copyOf(beats);
 }
 static Map<String,Beat> all() {
  var known=cache;if(known!=null)return known;
  synchronized(PlayerBotWardenBeats.class) {
   if(cache==null)try {
    var loaded=load(Path.of("config/playerbots/warden-beats.tsv"));var reports=new HashMap<String,List<Threat>>();
    for(var beat:loaded.values()) {
     var found=new ArrayList<Threat>();var r=beat.route();var tribe=r.race()==Race.ELYOS?com.aionemu.gameserver.model.TribeClass.PC:com.aionemu.gameserver.model.TribeClass.PC_DARK;
     for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(r.map())) {
      var n=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());if(n==null || group.isTemporarySpawn() || group.getHandlerType()!=null
       || n.getRating()==com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL || n.getRating()==com.aionemu.gameserver.model.templates.npc.NpcRating.JUNK
       || !DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),tribe) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),tribe))continue;
      for(var spawn:group.getSpawnTemplates())if(!spawn.isAerialSpawn() && r.distance(spawn.getX(),spawn.getY(),spawn.getZ())<=100)found.add(new Threat(n.getName(),new PlayerBotNavigation.Point(spawn.getX(),spawn.getY(),spawn.getZ())));
     }reports.put(r.id(),List.copyOf(found));
    }threats=Map.copyOf(reports);spaces=roadSpaces(loaded);cache=loaded;
   }
   catch(IOException error){org.slf4j.LoggerFactory.getLogger(PlayerBotWardenBeats.class).error("Warden beats unavailable; residents remain unassigned",error);cache=Map.of();}
   return cache;
  }
 }
 static PlayerBotPatrolRoutes.Route route(String id){var b=all().get(id);return b==null?null:b.route();}
 private PlayerBotWardenBeats(){}
}
