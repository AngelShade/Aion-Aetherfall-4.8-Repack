package com.aionemu.gameserver.services.playerbot;

import java.awt.image.DataBufferUShort;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import com.aionemu.gameserver.geoEngine.bounding.BoundingBox;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.*;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Captured native terrain routes and actual Navigation retargeting; no World/IDs/SQL/tasks. */
public final class PlayerBotNativeRouteYieldCheck {
 private record Case(String name,int world,Point start,Point goal){}
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
 private static final Case HARONEL=new Case("Haronel",210010000,new Point(486.09125f,1615.1636f,119.049614f),new Point(241.094f,1639.46f,100.375f));
 private static final Case EROMIRA=new Case("Eromira",210010000,new Point(303.75842f,1817.9537f,151.29205f),HARONEL.goal());
 private static final Case VELERINIA=new Case("Velerinia",220010000,new Point(1208.2411f,1877.1191f,252.76555f),new Point(1210.2f,1881.66f,254.671f));
 private static final Point OLD_TARGET=new Point(1209.3188f,1879.6176f,253.87021f);
 private static void field(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 private static Object value(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 private static Object allocate(Class<?> type)throws Exception{var u=Class.forName("sun.misc.Unsafe");var f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);}
 private static PlayerBotPathfinder.Ground ground(GeoMap mesh,Point goal){return (from,x,y)->{var p=PlayerBotGroundNavigation.walk(mesh,1,from.x(),from.y(),from.z(),x,y,goal.z());return new Point(p.x,p.y,p.z);};}
 private static void proof(PlayerBotRouteSearch search,Point start,PlayerBotPathfinder.Ground ground){
  check(!search.route().isEmpty(),"Published prefix contains actual walking edges");
  for(Point end:search.route()){Point nativeEnd=ground.step(start,end.x(),end.y());check(distance(nativeEnd,end)<.15,"Every returned edge preserves native supported floor/body collision proof");start=end;}
 }
 private static ByteBuffer read(Path path)throws Exception{
  try(var file=FileChannel.open(path)){return file.map(FileChannel.MapMode.READ_ONLY,0,file.size());}
 }
 private static String name(ByteBuffer bytes){byte[] name=new byte[Short.toUnsignedInt(bytes.getShort())];bytes.get(name);return new String(name,StandardCharsets.UTF_8);}
 private static Map<String,Node> models(Path folder)throws Exception{
  var bytes=read(folder.resolve("models.mesh"));var result=new HashMap<String,Node>();
  while(bytes.hasRemaining()){
   String name=name(bytes);var node=new Node(name);byte intentions=0;
   int count=Byte.toUnsignedInt(bytes.get());
   for(int i=0;i<count;i++){
    var mesh=new Mesh();int length=Short.toUnsignedInt(bytes.getShort())*12;
    mesh.setVertices(bytes.slice(bytes.position(),length).asFloatBuffer());bytes.position(bytes.position()+length);
    int indices=Short.toUnsignedInt(bytes.getShort())*3;int width=Byte.toUnsignedInt(bytes.get());
    var buffer=bytes.slice(bytes.position(),indices*width);
    if(width==1)mesh.setIndices(buffer);else if(width==2)mesh.setIndices(buffer.asShortBuffer());else throw new IllegalArgumentException("Native index width "+width);
    bytes.position(bytes.position()+indices*width);mesh.setMaterialId(bytes.get());mesh.setCollisionIntentions(bytes.get());mesh.updateBound();
    intentions|=mesh.getCollisionIntentions();node.attachChild(new Geometry(name,mesh));
   }
   node.setCollisionIntentions(intentions);
   for(String alias:name.split("\\|"))result.put(alias,node);
  }
  return result;
 }
 private static GeoMap map(Path folder,Map<String,Node> models,Case point)throws Exception{
  var map=new GeoMap(point.world());var imagePath=folder.resolve(point.world()+".png");
  if(Files.exists(imagePath)) {
   var image=ImageIO.read(imagePath.toFile());var terrain=new Terrain();
   terrain.setHeightmap(((DataBufferUShort)image.getRaster().getDataBuffer()).getData(),image.getWidth(),image.getHeight());map.setTerrain(terrain);
  }
  var bytes=read(folder.resolve(point.world()+".geo"));int visited=0;
  while(bytes.hasRemaining()){
   String name=name(bytes);var position=new Vector3f(bytes.getFloat(),bytes.getFloat(),bytes.getFloat());var rotation=new Matrix3f();
   for(int row=0;row<3;row++)for(int col=0;col<3;col++)rotation.set(row,col,bytes.getFloat());
   var scale=new Vector3f(bytes.getFloat(),bytes.getFloat(),bytes.getFloat());int type=Byte.toUnsignedInt(bytes.get());bytes.getShort();bytes.get();
   // Static physical support only. No dynamic event/town/door service or material-zone creation.
   var model=models.get(name);if(type!=0 || model==null)continue;visited++;
   var child=model.clone();child.setTransform(rotation,position,scale);child.updateModelBound();
   var bounds=(BoundingBox)child.getWorldBound();var center=bounds.getCenter();
   // Cull on transformed world bounds, never entity origin. Retain every floor/layer in the XY strip.
   if(center.x+bounds.getXExtent()<Math.min(point.start.x(),point.goal.x())-100 || center.x-bounds.getXExtent()>Math.max(point.start.x(),point.goal.x())+100 || center.y+bounds.getYExtent()<Math.min(point.start.y(),point.goal.y())-100 || center.y-bounds.getYExtent()>Math.max(point.start.y(),point.goal.y())+100)continue;
   map.attachChild(child);
  }
  map.updateModelBound();check(visited>0 && map.getQuantity()>0,"Actual native static geometry decoded and retained by transformed corridor bounds");return map;
 }
 private static void retarget()throws Exception {
  var actor=(PlayerBotRouteContinuationCheck.RouteActor)allocate(PlayerBotRouteContinuationCheck.RouteActor.class);
  actor.spawned=true;actor.movable=true;actor.world=220010000;actor.instance=1;actor.x=VELERINIA.start.x();actor.y=VELERINIA.start.y();actor.z=VELERINIA.start.z();
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);actor.stats=(PlayerBotRouteContinuationCheck.RouteStats)allocate(PlayerBotRouteContinuationCheck.RouteStats.class);
  var nav=new PlayerBotNavigation(actor);
  var budgetField=PlayerBotNavigation.class.getDeclaredField("TERRAIN");budgetField.setAccessible(true);var budget=(PlayerBotNavigationBudget)budgetField.get(null);
  var credit=PlayerBotNavigationBudget.class.getDeclaredField("credit");credit.setAccessible(true);double saved=credit.getDouble(budget);
  try {
   field(nav,"routeWorld",actor.world);field(nav,"routeInstance",actor.instance);field(nav,"routeGoal",VELERINIA.goal);
   var predecessor=new PlayerBotRouteSearch(VELERINIA.start,OLD_TARGET,4096,4096,24);field(nav,"routeSearch",predecessor);
   var token=value(nav,"searchToken");budget.request(token,PlayerBotNavigationBudget.Priority.NEAR);credit.setDouble(budget,-1e12);
   check(nav.move(VELERINIA.goal.x(),VELERINIA.goal.y(),VELERINIA.goal.z()),"Current goal retains pending native local admission under unfunded quota");
   check(value(nav,"routeSearch")==null && !budget.waiting(token),"Actual Navigation cancels old search even when its routeGoal already names the successor");
   check(predecessor.probed()==0 && actor.move.getTargetX2()==0,"Retargeting neither probes obsolete terrain nor publishes unearned movement");
   nav.stop();credit.setDouble(budget,-1e12);field(nav,"nextMove",0L);field(nav,"routeWorld",actor.world);field(nav,"routeInstance",actor.instance);field(nav,"routeGoal",OLD_TARGET);
   field(nav,"routeSearch",new PlayerBotRouteSearch(VELERINIA.start,OLD_TARGET,4096,4096,24));
   nav.move(VELERINIA.goal.x(),VELERINIA.goal.y(),VELERINIA.goal.z());check(value(nav,"routeGoal").equals(VELERINIA.goal) && value(nav,"routeSearch")==null,"Sub-four-metre movement replaces obsolete route commitment instead of retaining stale target");
  }finally{nav.stop();credit.setDouble(budget,saved);}
 }
 public static void main(String[] args)throws Exception {
  if(args.length>1 && args[1].equals("retarget")){retarget();System.out.println("OK: "+checks+" actual Navigation successor checks; no World/IDs/SQL/tasks");return;}
  Path folder=Path.of(args[0]).resolve("data/geo");var models=models(folder);
  var mesh=map(folder,models,HARONEL);var ground=ground(mesh,HARONEL.goal);
  var shortPrefix=new PlayerBotRouteSearch(HARONEL.start,HARONEL.goal,4096,4096,24);shortPrefix.advance(ground,128,()->false);
  check(!shortPrefix.pending() && shortPrefix.partial(),"Captured narrow native detour yields useful proved prefix within one bounded128-probe turn");
  proof(shortPrefix,HARONEL.start,ground);Point endpoint=shortPrefix.route().getLast();
  check(distance(HARONEL.start,endpoint)>=4 && distance(HARONEL.start,endpoint)<24,"Useful incomplete path can be shorter than desired streaming horizon");
  check(distance(endpoint,HARONEL.goal)<distance(HARONEL.start,HARONEL.goal)-1 && distance(endpoint,HARONEL.goal)>.5,"Partial path earns nearer progress without reporting final arrival");
  var untouched=new PlayerBotRouteSearch(HARONEL.start,HARONEL.goal,4096,4096,24);untouched.advance(ground,128,()->true);
  check(untouched.pending() && untouched.route().isEmpty() && untouched.probed()==0,"An expired deadline fabricates no partial path");
  var absent=new PlayerBotRouteSearch(HARONEL.start,HARONEL.goal,4096,4096,24);absent.advance((from,x,y)->null,128,()->false);
  check(absent.route().isEmpty() && !absent.partial(),"Absent support cannot authorize prefix publication");
  var full=new PlayerBotRouteSearch(HARONEL.start,HARONEL.goal,4096,4096);full.advance(ground,128,()->false);
  check(full.pending() && full.route().isEmpty() && !full.partial(),"Legacy full-route callers retain complete destination proof");
  var eMap=map(folder,models,EROMIRA);var eGround=ground(eMap,EROMIRA.goal);var longer=new PlayerBotRouteSearch(EROMIRA.start,EROMIRA.goal,4096,4096,24);
  for(int i=0;i<128 && longer.pending();i++)longer.advance(eGround,128,()->false);
  check(!longer.pending() && longer.partial(),"Captured longer real detour retains its bounded search until supported progress exists");proof(longer,EROMIRA.start,eGround);
  check(distance(longer.route().getLast(),EROMIRA.goal)<distance(EROMIRA.start,EROMIRA.goal)-1,"Long detour cannot substitute an arbitrary away-going edge for earned goal progress");
  var vMap=map(folder,models,VELERINIA);var currentGround=ground(vMap,VELERINIA.goal);var current=new PlayerBotRouteSearch(VELERINIA.start,VELERINIA.goal,4096,4096,24);current.advance(currentGround,128,()->false);
  check(!current.pending() && !current.partial() && distance(current.route().getLast(),VELERINIA.goal)<.5,"Captured successor has a full native supported short route on correct floor");proof(current,VELERINIA.start,currentGround);
  var predecessor=new PlayerBotRouteSearch(VELERINIA.start,OLD_TARGET,4096,4096,24);
  check(!predecessor.targets(VELERINIA.goal),"Moved target is distinguishable from retained old query");
  check(predecessor.targets(new Point(OLD_TARGET.x()+.1f,OLD_TARGET.y(),OLD_TARGET.z())),"Native minor coordinate noise retains the same intent");
  var legacy=new PlayerBotRouteSearch(VELERINIA.start,OLD_TARGET,4096,4096);var oldGround=ground(vMap,OLD_TARGET);legacy.advance(oldGround,256,()->false);
  check(legacy.pending() && legacy.route().isEmpty(),"Old mismatching height layer cannot falsely satisfy the final destination");
  retarget();
  System.out.println("OK: "+checks+" native recorded-route prefix, full-arrival, terrain-safety and actual Navigation successor checks; no World/IDs/SQL/tasks");
 }
 private PlayerBotNativeRouteYieldCheck(){}
}
