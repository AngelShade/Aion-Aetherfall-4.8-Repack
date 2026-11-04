package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.recast4j.detour.*;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.dataholders.loadingutils.XmlMerger;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.utils.xml.JAXBUtil;

/** Offline native-data/navmesh derivative. No GameServer, database or world actors are started. */
public final class PlayerBotWardenBeatBuilder {
 record Camp(String name,PlayerBotNavigation.Point point,int npc) {}
 private static final Map<String,List<Camp>> CAMP_CACHE=new HashMap<>();
 static List<Camp> camps(int map,Race race) {
  var cached=CAMP_CACHE.get(map+":"+race);if(cached!=null)return cached;
  var camps=new TreeMap<String,Camp>();
  for(var hub:PlayerBotWorldPlacement.locations())if(hub.map()==map && hub.race()==race && hub.hub()) {
   var p=new PlayerBotNavigation.Point(hub.x(),hub.y(),hub.z());String label=name(map,p);camps.putIfAbsent(label,new Camp(label,p,0));
  }
  var residents=new ArrayList<Camp>();var tribe=race==Race.ELYOS?TribeClass.PC:TribeClass.PC_DARK;
  for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map)) {
   var n=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());if(n==null || group.isTemporarySpawn() || group.getHandlerType()!=null
    || DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(),tribe) || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(),tribe)
    || n.getRace()!=race && !DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(n.getTribe(),tribe))continue;
   for(var s:group.getSpawnTemplates())if(!s.isAerialSpawn() && s.getWalkerId()==null) {
    var p=new PlayerBotNavigation.Point(s.getX(),s.getY(),s.getZ());residents.add(new Camp(name(map,p),p,n.getTemplateId()));
   }
  }
  // A native quest outpost need not have a shop/warehouse. Require a settled
  // cluster, not an isolated friendly monster, before adding its named anchor.
  for(var p:residents)if(!camps.containsKey(p.name()) && residents.stream().filter(q->PlayerBotWardenBeats.distance(p.point(),q.point())<=100).limit(3).count()>=3)camps.put(p.name(),p);
  var result=List.copyOf(camps.values());CAMP_CACHE.put(map+":"+race,result);return result;
 }
 static double endDistance(PlayerBotWardenBeats.Beat beat) {
  var r=beat.route();double result=0;var hubs=camps(r.map(),r.race());
  for(int end=0;end<2;end++) {var p=r.points().get(end==0?0:r.points().size()-1);String name=end==0?beat.from():beat.to();
   result+=hubs.stream().filter(h->h.name().equals(name)).mapToDouble(h->PlayerBotWardenBeats.distance(p,h.point())).min().orElse(Double.POSITIVE_INFINITY);
  }return result;
 }
 /** Identical roads cannot add capacity merely by naming a different nearby camp. */
 static List<String> deduplicate(List<String> rows) {
  var selected=new HashMap<PlayerBotWardenBeats.Geometry,String>();var scores=new HashMap<PlayerBotWardenBeats.Geometry,Double>();var invalid=new ArrayList<String>();
  for(String row:rows) {
   if(row.isBlank() || row.startsWith("#"))continue;
   try {
    var beat=PlayerBotWardenBeats.parse(row,null);var geometry=PlayerBotWardenBeats.geometry(beat.route());double distance=endDistance(beat);String old=selected.get(geometry);
    if(old==null || distance<scores.get(geometry) || distance==scores.get(geometry) && row.compareTo(old)<0) {
     if(old!=null)System.out.println("Collapsed duplicate geometry: "+old.split("\t")[0]+" -> "+beat.route().id()+" (nearest native ends; summed distance="+distance+")");
     selected.put(geometry,row);scores.put(geometry,distance);
    }else System.out.println("Collapsed duplicate geometry: "+beat.route().id()+" -> "+old.split("\t")[0]+" (nearest native ends)");
   }catch(RuntimeException error){invalid.add(row);}
  }
  var result=new ArrayList<String>();result.add("# Warden beats v1: id map race level npc from to metres hostiles xyz; native navmesh and walker derivative");
  result.addAll(selected.values().stream().sorted().toList());result.addAll(invalid);return result;
 }
 static float[] vector(PlayerBotNavigation.Point p){return new float[]{p.x(),p.z(),p.y()};}
 static PlayerBotNavigation.Point point(float[] p){return new PlayerBotNavigation.Point(p[0],p[2],p[1]);}
 /** Short friendly patrols are road segments too; the merchant catalogue's 100 m floor is not a segment filter. */
 static List<PlayerBotPatrolRoutes.Route> segments() {
  var roads=new TreeMap<String,PlayerBotPatrolRoutes.Route>();
  for(var map:DataManager.WORLD_MAPS_DATA)for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
   if(!PlayerBotWorldPlacement.mapEligible(map,race,Set.of()))continue;var tribe=race==Race.ELYOS?TribeClass.PC:TribeClass.PC_DARK;
   for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map.getMapId())) {
    var npc=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());if(npc==null || group.isTemporarySpawn() || group.getHandlerType()!=null
     || DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(npc.getTribe(),tribe) || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(npc.getTribe(),tribe)
     || npc.getRace()!=race && !DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(npc.getTribe(),tribe))continue;
    for(var spawn:group.getSpawnTemplates()) {
     if(spawn.isAerialSpawn() || spawn.getWalkerId()==null)continue;String id=map.getMapId()+":"+race+":"+spawn.getWalkerId();if(roads.containsKey(id))continue;
     var walker=DataManager.WALKER_DATA.getWalkerTemplate(spawn.getWalkerId());if(walker==null)continue;var points=new ArrayList<PlayerBotNavigation.Point>();boolean valid=true;
     for(var step:walker.getRouteSteps()) {var p=new PlayerBotNavigation.Point(step.getX(),step.getY(),step.getZ());
      if(!Float.isFinite(p.x()) || !Float.isFinite(p.y()) || !Float.isFinite(p.z()) || !points.isEmpty() && PlayerBotWardenBeats.distance(points.getLast(),p)>100){valid=false;break;}points.add(p);
     }
     if(valid && points.size()>=2 && points.size()<=1024 && PlayerBotWardenBeats.length(points)>=10)roads.put(id,new PlayerBotPatrolRoutes.Route(id,map.getMapId(),race,Math.clamp(npc.getLevel(),1,65),npc.getTemplateId(),npc.getName(),List.copyOf(points),PlayerBotPatrolRoutes.index(points),true));
    }
   }
  }return List.copyOf(roads.values());
 }
 static String name(int map,PlayerBotNavigation.Point p) {
  return DataManager.ZONE_DATA.getZones(map).stream().filter(z->z.getArea().isInside3D(p.x(),p.y(),p.z()))
   .filter(z->!z.getZoneTemplate().getName().matches(".*(LIMITAREA|WEATHER|FLY|NOBIND).*"))
   .max(Comparator.comparingInt(z->z.getZoneTemplate().getPriority())).map(z->z.getZoneTemplate().getName().replaceAll("_[0-9]+$","").replace('_',' ')).orElse("Native service camp");
 }
 static List<PlayerBotNavigation.Point> sample(List<float[]> corners,PlayerBotWardenBeats.Settlements towns) {
  var result=new ArrayList<PlayerBotNavigation.Point>();
  for(int i=1;i<corners.size();i++) {
   var a=point(corners.get(i-1));var b=point(corners.get(i));int steps=Math.max(1,(int)Math.ceil(PlayerBotWardenBeats.distance(a,b)/8));
   for(int j=0;j<steps;j++) {float t=j/(float)steps;var p=new PlayerBotNavigation.Point(a.x()+t*(b.x()-a.x()),a.y()+t*(b.y()-a.y()),a.z()+t*(b.z()-a.z()));
    if(towns.contains(p))return List.of();result.add(p);}
  }
  if(!corners.isEmpty()) {var last=point(corners.getLast());if(towns.contains(last))return List.of();result.add(last);}return result;
 }
 static void audit(Path file,Path output,boolean prune)throws Exception {
  var rows=new ArrayList<String>();rows.add("id\tmap\trace\tlevel\tmetres\tpoints\tcapacity70\tfrom\tfromHubDistance\tfromSettlementSetback\tto\ttoHubDistance\ttoSettlementSetback\tminimumPointSetback");
  if(prune)Files.write(file,deduplicate(Files.readAllLines(file)));
  var all=PlayerBotWardenBeats.load(file);int points=0;
  if(prune) {
   var longMaps=new HashSet<String>();for(var b:all.values())if(b.length()>=1500)longMaps.add(b.route().map()+":"+b.route().race());
   var selected=new TreeMap<>(all);selected.entrySet().removeIf(e->e.getValue().length()<1500 && longMaps.contains(e.getValue().route().map()+":"+e.getValue().route().race()));
   all=Map.copyOf(selected);
  }
  long input=Files.readAllLines(file).stream().filter(s->!s.isBlank() && !s.startsWith("#")).count();
  if(!prune && all.size()!=input)throw new AssertionError("Rejected checked-in beat");
  if(prune) {
   var accepted=new ArrayList<String>();accepted.add("# Warden beats v1: id map race level npc from to metres hostiles xyz; native navmesh and walker derivative");var ids=new HashSet<String>();
   for(String row:Files.readAllLines(file))if(!row.isBlank() && !row.startsWith("#"))try {
    var candidate=PlayerBotWardenBeats.parse(row,null);var selected=all.get(candidate.route().id());
    if(selected!=null && candidate.route().points().equals(selected.route().points()) && ids.add(candidate.route().id()))accepted.add(row);
   }catch(RuntimeException ignored){}
   Files.write(file,accepted);System.out.println("REVIEWED MERGE: "+input+" input rows; "+all.size()+" native-valid distinct beats retained; see rejected-row warnings above");
  }
  for(var beat:all.values().stream().sorted(Comparator.comparing(b->b.route().id())).toList()) {
   var r=beat.route();var towns=new PlayerBotWardenBeats.Settlements(r.map());var hubs=camps(r.map(),r.race());
   var values=new ArrayList<String>(List.of(r.id(),""+r.map(),r.race().name(),""+r.level(),String.format(Locale.ROOT,"%.2f",beat.length()),""+r.points().size(),""+(int)(beat.length()/70)));
   for(int end=0;end<2;end++) {
    var p=r.points().get(end==0?0:r.points().size()-1);String label=end==0?beat.from():beat.to();
    double hub=hubs.stream().filter(h->h.name().equals(label)).mapToDouble(h->PlayerBotWardenBeats.distance(p,h.point())).min().orElseThrow();
    values.add(label);values.add(String.format(Locale.ROOT,"%.2f",hub));values.add(String.format(Locale.ROOT,"%.2f",towns.clearance(p)));
   }
   double clearance=Double.POSITIVE_INFINITY;for(var p:r.points()){if(towns.contains(p))throw new AssertionError("Settlement point "+r.id());clearance=Math.min(clearance,towns.clearance(p));points++;}
   values.add(String.format(Locale.ROOT,"%.2f",clearance));rows.add(String.join("\t",values));
  }
  for(int capital:new int[]{110010000,120010000})if(all.values().stream().anyMatch(b->b.route().map()==capital))throw new AssertionError("Capital beat");
  Files.write(output.resolve("beat-audit.tsv"),rows);
  var roads=segments();var maps=new ArrayList<String>();maps.add("map\tname\trace\tfriendlyWalkerSegments\tnativeCampAnchors\tbeats\treason");
  for(var map:DataManager.WORLD_MAPS_DATA)for(var race:List.of(Race.ELYOS,Race.ASMODIANS))if(PlayerBotWorldPlacement.mapEligible(map,race,Set.of())) {
   long segments=roads.stream().filter(r->r.map()==map.getMapId() && r.race()==race).count(),count=all.values().stream().filter(b->b.route().map()==map.getMapId() && b.route().race()==race).count();int anchors=camps(map.getMapId(),race).size();
   String reason=count>0?"native-valid outskirts beats":segments==0?"no native friendly walker road segments":anchors<2?"fewer than two native settled-place anchors":"no validated corridor in bounded native search; inspect generation rejections; not proof the map has no road";
   maps.add(String.join("\t",""+map.getMapId(),map.getName(),race.name(),""+segments,""+anchors,""+count,reason));
  }
  Files.write(output.resolve("map-audit.tsv"),maps);System.out.println("AUDIT OK: "+all.size()+" beats; "+points+" individual points; no settlement points; zero Sanctum/Pandaemonium beats");
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=3 && !(args.length==4 && Set.of("--relabel","--audit","--prune","--hubs").contains(args[3])))throw new IllegalArgumentException("STATIC_DATA NAVMESH EXTERNAL_OUTPUT [--relabel|--audit|--prune|--hubs]");
  int status=1;
  try {
   Path source=Path.of(args[0]),output=Path.of(args[2]);Files.createDirectories(output);
   ThreadConfig.BASE_THREAD_POOL_SIZE=2;ThreadConfig.SCHEDULED_THREAD_POOL_SIZE=2;ThreadConfig.MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING=60000;GSConfig.SERVER_COUNTRY_CODE=1;
   var merged=new XmlMerger(source.resolve("static_data.xml").toFile(),output.resolve("static_data.xml").toFile()).merge();
   if(!merged.waitUntilFileIsWritten())throw new IllegalStateException("static merge");StaticData data;
   try(var reader=merged.newReader()){data=JAXBUtil.deserialize(reader,StaticData.class);}
   var await=StaticData.class.getDeclaredMethod("waitForAfterUnmarshalTasksToFinish");await.setAccessible(true);await.invoke(data);
   DataManager.WORLD_MAPS_DATA=data.worldMapsData;DataManager.NPC_DATA=data.npcData;DataManager.SPAWNS_DATA=data.spawnsData;
   DataManager.TRIBE_RELATIONS_DATA=data.tribeRelationsData;DataManager.TELEPORTER_DATA=data.teleporterData;
   DataManager.PLAYER_INITIAL_DATA=data.playerInitialData;DataManager.WALKER_DATA=data.walkerData;DataManager.ZONE_DATA=data.zoneData;
   if(args.length==4 && args[3].equals("--hubs")) {
    var rows=new ArrayList<String>();rows.add("map\trace\tname\tx\ty\tz\tcity\tsettlementFootprint");
    var territory=new HashMap<Integer,PlayerBotWardenBeats.Settlements>();
    for(var h:PlayerBotWorldPlacement.locations())if(h.hub())rows.add(String.join("\t",""+h.map(),h.race().name(),name(h.map(),new PlayerBotNavigation.Point(h.x(),h.y(),h.z())),""+h.x(),""+h.y(),""+h.z(),""+h.city(),""+territory.computeIfAbsent(h.map(),PlayerBotWardenBeats.Settlements::new).contains(new PlayerBotNavigation.Point(h.x(),h.y(),h.z()))));
    Files.write(output.resolve("hubs.tsv"),rows);status=0;return;
   }
   if(args.length==4 && Set.of("--audit","--prune").contains(args[3])){audit(output.resolve("warden-beats.tsv"),output,args[3].equals("--prune"));status=0;return;}
   if(args.length==4) {
    var updated=new ArrayList<String>();
    for(String row:Files.readAllLines(output.resolve("warden-beats.tsv"))) {
     if(row.startsWith("#")){updated.add(row);continue;}var c=row.split("\t");var beat=PlayerBotWardenBeats.parse(row,new PlayerBotWardenBeats.Settlements(Integer.parseInt(c[1])));
     var route=beat.route();var hubs=PlayerBotWorldPlacement.locations().stream().filter(h->h.map()==route.map() && h.race()==route.race() && h.hub()).toList();
     for(int end=0;end<2;end++) {
      var point=route.points().get(end==0?0:route.points().size()-1);
      var hub=hubs.stream().min(Comparator.comparingDouble(h->PlayerBotWardenBeats.distance(point,new PlayerBotNavigation.Point(h.x(),h.y(),h.z())))).orElseThrow();
      c[5+end]=name(route.map(),new PlayerBotNavigation.Point(hub.x(),hub.y(),hub.z()));
     }
     if(c[5].equals(c[6])){System.out.println("Rejected same-settlement beat: "+c[0]+" "+c[5]);continue;}
     updated.add(String.join("\t",c));System.out.println(c[0]+" "+c[5]+" -> "+c[6]);
    }
    Files.write(output.resolve("warden-beats.tsv"),updated);return;
   }
   var rows=new ArrayList<String>();rows.add("# Warden beats v1: id map race level npc from to metres hostiles xyz; native navmesh and walker derivative");
   var executor=Executors.newSingleThreadExecutor();
   try(var store=new PlayerBotNavmeshStore(Path.of(args[1]),1024L*1024*1024,executor::execute)) {
    var roads=segments();var maps=new TreeSet<Integer>();roads.forEach(r->maps.add(r.map()));
    System.out.println("Native friendly walker segments="+roads.size());
    for(int map:maps) {
     if(System.getProperty("warden.beats.map")!=null && Arrays.stream(System.getProperty("warden.beats.map").split(",")).mapToInt(Integer::parseInt).noneMatch(id->id==map))continue;
     var towns=new PlayerBotWardenBeats.Settlements(map);int before=rows.size();
     var local=roads.stream().filter(r->r.map()==map).toList();
     if(local.isEmpty()){System.out.println("Map "+map+" beats=0 (no hostile road corridor)");continue;}
     PlayerBotNavmeshStore.Result access;
     while((access=store.acquire(map)).status()==PlayerBotNavmeshStore.Status.WAITING)Thread.sleep(20);
     if(access.status()!=PlayerBotNavmeshStore.Status.READY){System.out.println("Map "+map+" unavailable: "+access.reason());continue;}
     try(var lease=access.lease()) {
      var mesh=lease.mesh();var query=new NavMeshQuery(mesh);var clearanceQuery=new NavMeshQuery(mesh);var normal=new DefaultQueryFilter();
      for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
       var nativeRoads=local.stream().filter(r->r.race()==race).toList();if(nativeRoads.isEmpty())continue;
       record Anchor(String name,PlayerBotNavigation.Point point,int npc){}
       var anchors=new TreeMap<String,Anchor>();
       for(var hub:camps(map,race)) {
        var h=hub.point();String label=hub.name();if(anchors.containsKey(label))continue;
        PlayerBotPatrolRoutes.Route road=null;PlayerBotNavigation.Point near=null;double best=700;
        for(var r:nativeRoads)for(var p:r.points()) {double d=PlayerBotWardenBeats.distance(p,h);if(d>70 && d<best && !towns.contains(p)){best=d;near=p;road=r;}}
        // Friendly walker templates are sparse at several native service camps.
        // Search an actual mesh landing around that camp, rather than silently
        // dropping its long connecting road because no walker ends within 400m.
        double score=Double.POSITIVE_INFINITY;PlayerBotNavigation.Point outskirts=null;PlayerBotPatrolRoutes.Route segment=null;
        record Option(PlayerBotNavigation.Point point,PlayerBotPatrolRoutes.Route road,double score){}var options=new ArrayList<Option>();
        var departure=query.findNearestPoly(vector(h),new float[]{4,8,4},normal);
        for(int radius:new int[]{100,140,200,280})for(int direction=0;direction<16;direction++) {
         double angle=direction*Math.PI/8;var probe=new PlayerBotNavigation.Point(h.x()+(float)Math.cos(angle)*radius,h.y()+(float)Math.sin(angle)*radius,h.z());
         // Hills need height projection. A broad vertical nearest-poly query is
         // accepted only after native connectivity from the actual camp floor;
         // proximity alone must never select a stacked hall or bridge deck.
         var landing=query.findNearestPoly(vector(probe),new float[]{4,96,4},normal);if(landing.failed() || landing.result.getNearestRef()==0
          || departure.failed() || departure.result.getNearestRef()==0)continue;
         var p=point(landing.result.getNearestPos());if(towns.contains(p) || towns.clearance(p)<8 || !PlayerBotWorldPlacement.factionSide(race,map,p.x(),p.y(),p.z())
          || p.z()<=DataManager.WORLD_MAPS_DATA.getTemplate(map).getWaterLevel()+.2)continue;
         var link=query.findPath(departure.result.getNearestRef(),landing.result.getNearestRef(),departure.result.getNearestPos(),landing.result.getNearestPos(),normal);
         if(link.failed() || link.result.isEmpty() || link.result.getLast()!=landing.result.getNearestRef())continue;
         var approach=query.findStraightPath(departure.result.getNearestPos(),landing.result.getNearestPos(),link.result,1024,NavMeshQuery.DT_STRAIGHTPATH_ALL_CROSSINGS);
         if(approach.failed() || approach.result.isEmpty() || (approach.result.getLast().getFlags()&NavMeshQuery.DT_STRAIGHTPATH_END)==0
          || PlayerBotWardenBeats.length(approach.result.stream().map(s->point(s.getPos())).toList())>radius*2+100)continue;
         var nearest=nativeRoads.stream().min(Comparator.comparingDouble(r->r.distance(p.x(),p.y(),p.z()))).orElseThrow();double distance=nearest.distance(p.x(),p.y(),p.z());
         double candidate=distance+radius*.1;options.add(new Option(p,nearest,candidate));if(candidate<score){score=candidate;outskirts=p;segment=nearest;}
        }
        if(outskirts!=null){near=outskirts;road=segment;}
        if(near!=null) {
         anchors.put(label,new Anchor(label,near,road.npc()));var chosen=new ArrayList<PlayerBotNavigation.Point>();chosen.add(near);
         for(var option:options.stream().sorted(Comparator.comparingDouble(Option::score)).toList()) {
          if(chosen.size()>=Integer.getInteger("warden.beats.alternatives",3))break;
          if(chosen.stream().anyMatch(p->PlayerBotWardenBeats.distance(p,option.point())<100))continue;
          chosen.add(option.point());anchors.put(label+"#"+chosen.size(),new Anchor(label,option.point(),option.road().npc()));
         }
        }
       }
       var candidates=new ArrayList<>(anchors.values());var costs=new HashMap<Long,Float>();var admitted=new HashMap<Long,Boolean>();
       class Filter extends DefaultQueryFilter {
        @Override public boolean passFilter(long ref,MeshTile tile,Poly poly) {
         if(!super.passFilter(ref,tile,poly))return false;
         return admitted.computeIfAbsent(ref,k->{float[] c=new float[3];for(int v=0;v<poly.vertCount;v++) {
           float[] vertex=new float[3],middle=new float[3];for(int axis=0;axis<3;axis++) {
            vertex[axis]=tile.data.verts[poly.verts[v]*3+axis];c[axis]+=vertex[axis]/poly.vertCount;
            middle[axis]=(vertex[axis]+tile.data.verts[poly.verts[(v+1)%poly.vertCount]*3+axis])/2;
           }
           if(towns.contains(point(vertex)) || towns.contains(point(middle)))return false;
          }
          return !towns.contains(point(c)) && c[1]>DataManager.WORLD_MAPS_DATA.getTemplate(map).getWaterLevel()+.2 && PlayerBotWorldPlacement.factionSide(race,map,c[0],c[2],c[1]);});
        }
        @Override public float getCost(float[] a,float[] b,long previous,MeshTile pt,Poly pp,long current,MeshTile tile,Poly poly,long next,MeshTile nt,Poly np) {
         float penalty=costs.computeIfAbsent(current,k->{var p=point(a);double road=nativeRoads.stream().mapToDouble(r->r.distance(p.x(),p.y(),p.z())).min().orElse(1000);
          var wall=clearanceQuery.findDistanceToWall(current,a,4,normal);float width=wall.succeeded()?wall.result.getDistance():0;
          return (float)(1+Math.min(12,road/20)+.4/(.3+width));});
         return (float)PlayerBotWardenBeats.distance(point(a),point(b))*penalty;
        }
       }
       var filter=new Filter();int count=0;var shortBeats=new ArrayList<String>();
       record Pair(Anchor from,Anchor to,double distance){}
       var pairs=new ArrayList<Pair>();for(int i=0;i<candidates.size();i++)for(int j=i+1;j<candidates.size();j++) {
        var from=candidates.get(i);var to=candidates.get(j);double flat=PlayerBotWardenBeats.distance(from.point(),to.point());if(!from.name().equals(to.name()) && flat>=300 && flat<=3500)pairs.add(new Pair(from,to,flat));
       }
       pairs.sort(Comparator.comparingDouble(p->Math.abs(p.distance()-1800)));
       var rejection=new TreeMap<String,Integer>();
       for(var pair:pairs.stream().limit(Integer.getInteger("warden.beats.maxPairs",128)).toList()) {
        if(count>=Integer.getInteger("warden.beats.maxPerMap",12))break;var from=pair.from();var to=pair.to();
        // The nearest unfiltered polygon can straddle the protected outskirts.
        // Admit a nearby safe polygon, keeping the same narrow vertical slab.
        var a=query.findNearestPoly(vector(from.point()),new float[]{32,8,32},filter);var b=query.findNearestPoly(vector(to.point()),new float[]{32,8,32},filter);
        if(a.failed() || b.failed() || a.result.getNearestRef()==0 || b.result.getNearestRef()==0){rejection.merge("landing",1,Integer::sum);continue;}
        var path=query.findPath(a.result.getNearestRef(),b.result.getNearestRef(),a.result.getNearestPos(),b.result.getNearestPos(),filter);
        if(path.failed() || path.result.isEmpty() || path.result.getLast()!=b.result.getNearestRef()){rejection.merge("disconnected safe mesh",1,Integer::sum);continue;}
        var straight=query.findStraightPath(a.result.getNearestPos(),b.result.getNearestPos(),path.result,4096,NavMeshQuery.DT_STRAIGHTPATH_ALL_CROSSINGS);
        if(straight.failed() || straight.result.isEmpty() || (straight.result.getLast().getFlags()&NavMeshQuery.DT_STRAIGHTPATH_END)==0)continue;
        var points=sample(straight.result.stream().map(StraightPathItem::getPos).toList(),towns);double length=PlayerBotWardenBeats.length(points);
        if(points.size()<4 || points.size()>1024 || length<300 || length>4000){rejection.merge(points.isEmpty()?"settlement crossing":"length/point budget",1,Integer::sum);continue;}
        if(points.stream().anyMatch(p->!PlayerBotWorldPlacement.factionSide(race,map,p.x(),p.y(),p.z())))continue;
        var route=new PlayerBotPatrolRoutes.Route("pending",map,race,1,from.npc(),"",points,PlayerBotPatrolRoutes.index(points),true);
        var levels=new ArrayList<Integer>();int hostiles=0;var tribe=race==Race.ELYOS?TribeClass.PC:TribeClass.PC_DARK;
        for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map)) {
         var npc=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());if(group.isTemporarySpawn() || group.getHandlerType()!=null || !PlayerBotHunting.template(npc)
          || !DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(npc.getTribe(),tribe) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(npc.getTribe(),tribe))continue;
         for(var spawn:group.getSpawnTemplates())if(!spawn.isAerialSpawn() && route.distance(spawn.getX(),spawn.getY(),spawn.getZ())<=100){hostiles++;levels.add((int)npc.getLevel());}
        }
        if(hostiles<Math.max(5,(int)Math.ceil(length/300)))continue;Collections.sort(levels);int level=Math.min(65,levels.get(levels.size()/2));
        String id="beat:"+map+":"+race+":"+Integer.toUnsignedString((from.name()+"|"+to.name()).hashCode(),16);
        if(rows.stream().anyMatch(row->row.startsWith(id+"\t")))continue;
        String vectors=String.join(";",points.stream().map(p->String.format(Locale.ROOT,"%.2f,%.2f,%.2f",p.x(),p.y(),p.z())).toList());
        String row=String.join("\t",id,""+map,race.name(),""+level,""+from.npc(),from.name(),to.name(),String.format(Locale.ROOT,"%.2f",length),""+hostiles,vectors);
        PlayerBotWardenBeats.parse(row,towns);
        if(length<1500){shortBeats.add(row);continue;}rows.add(row);count++;
        System.out.println(id+" "+Math.round(length)+"m "+from.name()+" -> "+to.name()+" hostiles="+hostiles+" points="+points.size());
       }
       if(count==0 && !shortBeats.isEmpty()) {
        var longest=shortBeats.stream().max(Comparator.comparingDouble(r->Double.parseDouble(r.split("\t")[7]))).orElseThrow();rows.add(longest);count++;
        System.out.println("Short-map fallback (no valid long candidate in bounded native search): "+longest.substring(0,longest.lastIndexOf('\t')));
       }
       System.out.println("Map "+map+" "+race+" anchors="+anchors.size()+" candidatePairs="+pairs.size()+" beats="+count+" rejected="+rejection);
      }
     }
     System.out.println("Map "+map+" total beats="+(rows.size()-before));
    }
   }finally{executor.shutdownNow();}
   for(int capital:new int[]{110010000,120010000})if(rows.stream().anyMatch(r->r.startsWith("beat:"+capital+":")))throw new AssertionError("Native settlement/hostile rules admitted a capital beat");
   Files.write(output.resolve("warden-beats.tsv"),deduplicate(rows));status=0;
  }finally{com.aionemu.gameserver.utils.ThreadPoolManager.getInstance().shutdown();}
  System.exit(status);
 }
}
