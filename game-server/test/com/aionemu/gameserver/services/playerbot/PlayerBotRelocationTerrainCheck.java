package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.recast4j.detour.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.Node;

/** Terrain AND models, real spawn samples and authenticated installed walking maps. No World/SQL/game tasks. */
public final class PlayerBotRelocationTerrainCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static float floor(GeoMap map,float x,float y,float top,float bottom){return PlayerBotGroundSupport.floor(map,1,x,y,top,bottom);}
 private static boolean under(GeoMap map,float[] from,PlayerBotLedgeLeap.Landing landing){
  if(landing==null || landing.z()>=from[2]-.5f)return false;
  float above=floor(map,landing.x(),landing.y(),from[2]+.5f,landing.z()+.5f);
  return Float.isFinite(above) && above>landing.z()+.5f;
 }
 private static List<float[]> spots(Path server,int id)throws Exception {
  var result=new ArrayList<float[]>();var factory=DocumentBuilderFactory.newInstance();
  factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  Path folder=server.getParent().resolve("target-deploy/game-server/data/static_data/spawns/Npcs");
  try(var paths=Files.list(folder)){
   for(Path path:paths.filter(p->p.getFileName().toString().startsWith(id+"_")).sorted().toList()){
    var nodes=factory.newDocumentBuilder().parse(path.toFile()).getElementsByTagName("spot");
    for(int i=0;i<nodes.getLength();i++){
     var element=(org.w3c.dom.Element)nodes.item(i);
     result.add(new float[]{Float.parseFloat(element.getAttribute("x")),Float.parseFloat(element.getAttribute("y")),Float.parseFloat(element.getAttribute("z"))});
    }
   }
  }
  Collections.shuffle(result,new Random(20261107L+id));
  return result.subList(0,Math.min(400,result.size()));
 }
 /** Paired historical algorithms, same direction/rings/admission and real-floor measure. */
 private static PlayerBotLedgeLeap.Landing before(GeoMap map,float[] from,int mode){
  for(float radius:new float[]{12,16,24,32,48,64})for(int direction=0;direction<16;direction++){
   int side=(direction+1)/2;double angle=(direction%2==1?side:-side)*2*Math.PI/16;
   float x=from[0]+(float)Math.cos(angle)*radius,y=from[1]+(float)Math.sin(angle)*radius;
   float z=floor(map,x,y,from[2]+(mode==0?24:3),from[2]-200);
   if(mode==2)for(int step=0;step<8 && Float.isFinite(z);step++){
    float lower=floor(map,x,y,z-.5f,from[2]+3-PlayerBotLedgeLeap.MAX_DROP);
    if(!Float.isFinite(lower))break;z=lower;
   }
   if(Float.isFinite(z) && PlayerBotGroundNavigation.landing(map,1,x,y,z))return new PlayerBotLedgeLeap.Landing(x,y,z,true);
  }
  return null;
 }
 private static Result<FindNearestPolyResult> nearest(NavMeshQuery query,float[] from){
  return query.findNearestPoly(new float[]{from[0],from[2],from[1]},new float[]{1,.75f,1},new DefaultQueryFilter());
 }
 private static int component(PlayerBotNavmeshTopology graph,NavMesh mesh,NavMeshQuery query,float[] from){
  var near=nearest(query,from);
  if(near.failed() || near.result.getNearestRef()==0 || Math.abs(near.result.getNearestPos()[1]-from[2])>.75f)return -1;
  return graph.component(mesh,near.result.getNearestRef());
 }
 private static void verify(GeoMap map,PlayerBotNavmeshTopology graph,NavMesh mesh,NavMeshQuery query,float[] from,boolean required,PlayerBotLedgeLeap.Escape escape)throws Exception {
  check(!required || escape!=null,"Regression spot must find ground: "+Arrays.toString(from));
  var landing=escape==null?null:escape.landing();
  check(!under(map,from,landing),"Relocation under a real floor: "+Arrays.toString(from)+" -> "+landing);
  if(landing==null){check(true,"No landing has no disconnected destination");return;}
  int origin=component(graph,mesh,query,from),end=component(graph,mesh,query,new float[]{landing.x(),landing.y(),landing.z()});
  boolean connected=graph.usableGround(end) && (!graph.usableGround(origin) || graph.groundRegion(origin)==graph.groundRegion(end));
  check(connected,"Relocation must stay on the usable walking component: "+Arrays.toString(from)+" -> "+landing);
  if(required){
   var a=nearest(query,from);var b=nearest(query,new float[]{landing.x(),landing.y(),landing.z()});
   var path=query.findPath(a.result.getNearestRef(),b.result.getNearestRef(),a.result.getNearestPos(),b.result.getNearestPos(),new DefaultQueryFilter());
   check(path.succeeded() && !path.result.isEmpty() && path.result.getLast()==b.result.getNearestRef(),"Actual directed Detour path completes from the regression origin");
  }
 }
 private static void topologyChecks()throws Exception {
  // Two mutually connected tiles are one walking region; a one-way link is not a return path.
  int[] owners=new int[134];Arrays.fill(owners,0,32,0);Arrays.fill(owners,32,64,1);Arrays.fill(owners,64,70,2);Arrays.fill(owners,70,134,3);
  var graph=new PlayerBotNavmeshTopology(4,1,4,new int[]{0,32,64,70,134},owners,new int[]{0,1,2,3,3},new int[]{1,0,3},new float[18]);
  check(graph.usableGround(0) && graph.usableGround(1),"Polygon counts combine across native walking seams");
  check(graph.groundRegion(0)==graph.groundRegion(1),"Mutual walking connectivity is map-wide");
  check(!graph.usableGround(2),"A small island with only a one-way exit is rejected");
  check(graph.usableGround(3) && graph.groundRegion(2)!=graph.groundRegion(3),"A large isolated floor is usable without invented reverse links");
  // Geometry-only fallback has no license to tunnel through the highest floor.
  var stacked=PlayerBotLedgeLeapCheck.map(PlayerBotLedgeLeapCheck.quad(-80,80,-80,80,20,20),PlayerBotLedgeLeapCheck.quad(-80,80,-80,80,0,0));
  var escape=PlayerBotInvisibleFloorCheck.escape(stacked,null,new float[]{0,0,20},0);
  check(escape!=null && Math.abs(escape.landing().z()-20)<.01,"Fallback keeps the highest valid floor");
  var deep=PlayerBotLedgeLeapCheck.map(PlayerBotLedgeLeapCheck.quad(-80,80,-80,80,-50,-50));
  check(PlayerBotInvisibleFloorCheck.escape(deep,null,new float[]{0,0,0},0)==null,"Fallback respects the 45m column bound");
  try(var search=PlayerBotLedgeLeap.search(stacked,1,0,0,20,30,0,6,21,PlayerBotLedgeLeap.Tier.RELOCATE,false,0,null)){
   check(search.advance(Long.MAX_VALUE)==null && search.done(),"minimumZ remains authoritative");
  }
 }
 private static void plannerChecks()throws Exception {
  var map=PlayerBotLedgeLeapCheck.map(PlayerBotLedgeLeapCheck.quad(-80,80,-80,80,20,20),PlayerBotLedgeLeapCheck.quad(-80,80,-80,80,0,0));
  var jobs=new ArrayDeque<Runnable>();
  // A nonexistent child is intentional: the loader must report missing data, not create it.
  Path missing=Path.of(System.getProperty("java.io.tmpdir"),"aion-relocation-missing-"+UUID.randomUUID());
  try(var store=new PlayerBotNavmeshStore(missing,64L*1024*1024,jobs::add)){
   try(var cancelled=PlayerBotLedgeLeap.search(map,1,0,0,20,30,0,6,Float.NEGATIVE_INFINITY,PlayerBotLedgeLeap.Tier.RELOCATE,false,0,store)){
    check(cancelled.advance(0)==null && !cancelled.done() && jobs.isEmpty(),"An expired slice starts no loading");
    check(cancelled.advance(Long.MAX_VALUE)==null && !cancelled.done() && !jobs.isEmpty(),"Planner yields while I/O is queued; no synchronous loading");
   }
   check(store.pendingCount()==0,"Cancellation releases queued topology demand");
   while(!jobs.isEmpty())jobs.remove().run();
   try(var fallback=PlayerBotLedgeLeap.search(map,1,0,0,20,30,0,6,Float.NEGATIVE_INFINITY,PlayerBotLedgeLeap.Tier.RELOCATE,false,0,store)){
    fallback.advance(Long.MAX_VALUE);while(!jobs.isEmpty())jobs.remove().run();
    var result=fallback.advance(Long.MAX_VALUE);
    check(fallback.done() && result!=null && result.landing().z()==20,"Unavailable topology uses highest-floor fallback");
   }
  }
  jobs.clear();
  try(var store=new PlayerBotNavmeshStore(missing,64L*1024*1024,jobs::add);
      var sealed=PlayerBotLedgeLeap.search(map,2,0,0,20,30,0,6,Float.NEGATIVE_INFINITY,PlayerBotLedgeLeap.Tier.RELOCATE,true,0,store)){
   var result=sealed.advance(Long.MAX_VALUE);
   check(result!=null && result.landing().z()==20 && jobs.isEmpty(),"Instances use geometry only without navmesh demand");
  }
 }
 public static void main(String[] args)throws Exception {
  topologyChecks();plannerChecks();Path server=Path.of(args[0]);Path geo=server.resolve("data/geo");Map<String,Node> models=PlayerBotInvisibleFloorCheck.models(geo);
  var loader=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var store=new PlayerBotNavmeshStore(server.getParent().resolve("target-deploy/game-server/data/navmesh"),1024L*1024*1024,loader::execute)){
   for(int id:new int[]{120010000,220020000,210020000,210010000,220010000}){
    var map=PlayerBotInvisibleFloorCheck.map(geo,id,models,true);check(map.hasTerrain(),"Heightmap loaded for "+id);
    try(var request=store.requestTopology(id)){
     PlayerBotNavmeshStore.Result topology;
     while((topology=request.poll()).status()==PlayerBotNavmeshStore.Status.WAITING)Thread.sleep(1);
     check(topology.status()==PlayerBotNavmeshStore.Status.READY,"Authenticated topology loaded: "+id+" "+topology.reason());
     PlayerBotNavmeshStore.Result full;
     while((full=store.acquire(id)).status()==PlayerBotNavmeshStore.Status.WAITING)Thread.sleep(1);
     check(full.status()==PlayerBotNavmeshStore.Status.READY,"Authenticated mesh loaded: "+id+" "+full.reason());
     try(var lease=full.lease()){
      var mesh=lease.mesh();var graph=topology.lease().topology();var query=new NavMeshQuery(mesh);
      if(id==120010000)for(float[] from:new float[][]{{1601,1392,193.1f},{1369,1181,208.4f},{945,1126,206.8f},{1397,1037,207.4f}})verify(map,graph,mesh,query,from,true,PlayerBotInvisibleFloorCheck.escape(map,store,from,0));
      int[] buried=new int[4],missing=new int[4];int count=0;
      for(float[] from:spots(server,id)){
       float support=floor(map,from[0],from[1],from[2]+3,from[2]-3);
       if(!Float.isFinite(support))continue;
       // Spawn Z is the player-level reference. All algorithms see exactly the same origin.
       count++;
       for(int mode=0;mode<3;mode++){var landing=before(map,from,mode);if(landing==null)missing[mode]++;if(under(map,from,landing))buried[mode]++;}
       var result=PlayerBotInvisibleFloorCheck.escape(map,store,from,0);var land=result==null?null:result.landing();
       if(land==null)missing[3]++;if(under(map,from,land))buried[3]++;
       verify(map,graph,mesh,query,from,false,result);
      }
      check(count>300,"At least 300 grounded real NPC spots per map: "+id+" got "+count);
      System.out.println("MAP "+id+" samples="+count+" buried[z+24,z+3,lowest,new]="+Arrays.toString(buried)+" noLanding="+Arrays.toString(missing));
     }
    }
   }
  } finally {loader.shutdownNow();}
  System.out.println("OK: "+checks+" terrain relocation checks; five real maps, NPC spawns, heightmaps and walking topology; no World/SQL/game tasks");
 }
 private PlayerBotRelocationTerrainCheck(){}
}
