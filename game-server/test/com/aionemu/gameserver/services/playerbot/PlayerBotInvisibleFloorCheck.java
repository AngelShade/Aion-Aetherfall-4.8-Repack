package com.aionemu.gameserver.services.playerbot;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.aionemu.gameserver.geoEngine.math.Matrix3f;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.Geometry;
import com.aionemu.gameserver.geoEngine.scene.Mesh;
import com.aionemu.gameserver.geoEngine.scene.Node;

/** Real Sanctum geometry: invisible nowalk meshes block bots but never hold them up. No World/IDs/SQL/tasks. */
public final class PlayerBotInvisibleFloorCheck {
 private static final int SANCTUM=110010000;
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static ByteBuffer read(Path path)throws Exception{try(var file=FileChannel.open(path)){return file.map(FileChannel.MapMode.READ_ONLY,0,file.size());}}
 private static String name(ByteBuffer bytes){byte[] name=new byte[Short.toUnsignedInt(bytes.getShort())];bytes.get(name);return new String(name,StandardCharsets.UTF_8);}
 static Map<String,Node> models(Path folder)throws Exception {
  var bytes=read(folder.resolve("models.mesh"));var result=new HashMap<String,Node>();
  while(bytes.hasRemaining()){
   String name=name(bytes);var node=new Node(name);byte intentions=0;
   int count=Byte.toUnsignedInt(bytes.get());
   for(int i=0;i<count;i++){
    var mesh=new Mesh();int length=Short.toUnsignedInt(bytes.getShort())*12;
    mesh.setVertices(bytes.slice(bytes.position(),length).asFloatBuffer());bytes.position(bytes.position()+length);
    int indices=Short.toUnsignedInt(bytes.getShort())*3;int width=Byte.toUnsignedInt(bytes.get());
    var buffer=bytes.slice(bytes.position(),indices*width);
    if(width==1)mesh.setIndices(buffer);else mesh.setIndices(buffer.asShortBuffer());
    bytes.position(bytes.position()+indices*width);mesh.setMaterialId(bytes.get());mesh.setCollisionIntentions(bytes.get());mesh.updateBound();
    intentions|=mesh.getCollisionIntentions();node.attachChild(new Geometry(name,mesh));
   }
   node.setCollisionIntentions(intentions);
   for(String alias:name.split("\\|"))result.put(alias,node);
  }
  return result;
 }
 static GeoMap map(Path folder,int id,Map<String,Node> models,boolean terrain)throws Exception {
  var map=new GeoMap(id);var bytes=read(folder.resolve(id+".geo"));
  while(bytes.hasRemaining()){
   String name=name(bytes);var position=new Vector3f(bytes.getFloat(),bytes.getFloat(),bytes.getFloat());var rotation=new Matrix3f();
   for(int row=0;row<3;row++)for(int col=0;col<3;col++)rotation.set(row,col,bytes.getFloat());
   var scale=new Vector3f(bytes.getFloat(),bytes.getFloat(),bytes.getFloat());int type=Byte.toUnsignedInt(bytes.get());bytes.getShort();bytes.get();
   var model=models.get(name);if(type!=0 || model==null)continue;
   var child=model.clone();child.setTransform(rotation,position,scale);child.updateModelBound();map.attachChild(child);
  }
  if(terrain){
   var image=javax.imageio.ImageIO.read(folder.resolve(id+".png").toFile());
   var ground=new com.aionemu.gameserver.geoEngine.models.Terrain();
   ground.setHeightmap(((java.awt.image.DataBufferUShort)image.getRaster().getDataBuffer()).getData(),image.getWidth(),image.getHeight());map.setTerrain(ground);
  }
  map.updateModelBound();return map;
 }
 private static float floor(GeoMap map,float x,float y,float top,float bottom){return PlayerBotGroundSupport.floor(map,1,x,y,top,bottom);}
 private static void near(float actual,float expected,String why){check(Math.abs(actual-expected)<1.5f,why+", got "+actual);}
 public static void main(String[] args)throws Exception {
  check(new Geometry("levels/common/nowalk_obstacle4_16m.cgf",new Mesh()).isWalkBlocker(),"nowalk_obstacle4 is a blocker");
  check(new Geometry("LEVELS/COMMON/NOWALK_NOCAMERA__08M01.CGF",new Mesh()).isWalkBlocker(),"nowalk_nocamera is a blocker whatever its case");
  check(new Geometry("levels/x/walk_obstacle4_100m.cgf",new Mesh()).isWalkBlocker(),"walk_obstacle is a blocker");
  check(!new Geometry("levels/x/bu_l_davaholl_ground_02b.cgf",new Mesh()).isWalkBlocker(),"a real hall floor is not");
  Path server=Path.of(args[0]),geo=server.resolve("data/geo");
  var map=map(geo,SANCTUM,models(geo),false);
  var loader=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var store=new PlayerBotNavmeshStore(server.getParent().resolve("target-deploy/game-server/data/navmesh"),768L*1024*1024,loader::execute)) {
  // Geometry-only fallback deliberately keeps the top floor, even when it is a slab.
  var fallback=escape(map,null,new float[]{1860,1500,601.9f},0);
  check(fallback!=null && fallback.landing().z()>591,"No topology means highest-floor geometry-only fallback");

  // The sheet at z 805.5 past the hall's edge, with the market about 200 m below.
  for(float[] air:new float[][]{{1840,1496},{1840,1500},{1840,1504},{1840,1508}}) {
   check(!Float.isFinite(floor(map,air[0],air[1],806.5f,800)),"No floor on the invisible sheet at "+air[0]+","+air[1]);
  }
  near(floor(map,1850,1500,806.5f,500),601.9f,"A ray through the sheet reaches the real surface below");
  // Real floors, including ones that share a height with a blocker, still hold.
  near(floor(map,1875,1536,806.5f,800),805.5f,"Upper hall floor stays");
  near(floor(map,1875,1492,806.5f,800),805.5f,"Hall floor shared with a blocker stays");
  near(floor(map,1900,1490,591,585),590,"Market floor stays");
  near(floor(map,1875,1492,591,585),590,"Artisans Hall floor stays");
  // An invisible block standing above the real floor no longer carries a bot.
  near(floor(map,1841,1409,611,600),606.5f,"nowalk_nocamera at z 609.3 above the door frame at 1841,1409 is skipped");
  check(!Float.isFinite(floor(map,1841,1409,611,607.5f)),"Nothing to stand on at the height of that block");

  // Walking off the real hall floor over the sheet's open-air edge stops at the edge.
  var end=PlayerBotGroundNavigation.walk(map,1,1875,1536,805.5f,1875,1560,805.5f);
  near(end.z,805.5f,"A short walk on the hall floor stays on it");
  var edge=PlayerBotGroundNavigation.walk(map,1,1860,1500,805.5f,1840,1500,805.5f);
  check(edge.x>1842 && edge.z>800,"A bot walking toward the open-air sheet stops before it, at "+edge.x+","+edge.y+","+edge.z);
  float lastX=Float.NaN;
  for(float x=1838;x<=1890;x+=4)for(float y=1484;y<=1534;y+=4) {
   float z=floor(map,x,y,806.5f,800);
   if(Float.isFinite(z)){lastX=x;var stack=map.getPlayerBotCollisions(new Vector3f(x,y,z+.1f),x,y,z-.1f,1,(byte)-1,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE);
    boolean real=false;for(var hit:stack)real|=hit.getGeometry()==null || !hit.getGeometry().isWalkBlocker();
    check(real,"Every floor found at the sheet's height is real, at "+x+","+y);}
  }
  check(Float.isFinite(lastX),"The sweep crossed real hall floor");
  // A stuck bot moved to open ground from the Artisans Hall floor stays on it, and one on any invisible slab overhead comes down.
  int slabs=0;
  for(float[] from:new float[][]{{1860,1490,590},{1875,1492,590},{1850,1480,590},{1870,1460,590},{1858,1487,602.1f},{1854.58f,1495.37f,602.1f},{1860,1500,601.9f},{1852,1490,598.6f},{1845,1480,598.6f},{1860,1500,615.9f}}) {
   if(from[2]>591)slabs++;
   for(int seed=0;seed<16;seed++) {
    var escape=escape(map,store,from,seed);
    check(escape!=null,"Relocation from "+from[0]+","+from[1]+","+from[2]+" seed "+seed+" finds ground");
    check(escape.landing().z()<592,"Relocation from "+from[0]+","+from[1]+","+from[2]+" seed "+seed+" ended at floor level, got "+escape.landing().z());
   }
  }
  check(slabs==6,"Slabs at several heights were covered");
  // The upper hall has nothing within a survivable drop below it, so a bot there stays on that level.
  for(int seed=0;seed<16;seed++) {
   var escape=escape(map,store,new float[]{1875,1536,805.5f},seed);
   check(escape==null || escape.landing().z()>760 ,"A bot in the upper hall does not fall to the market, got "+(escape==null?0:escape.landing().z()));
  }
  } finally {loader.shutdownNow();}
  System.out.println("OK: "+checks+" invisible floor checks on real Sanctum geometry and walking map; no World/IDs/SQL/game tasks");
 }
 static PlayerBotLedgeLeap.Escape escape(GeoMap map,PlayerBotNavmeshStore store,float[] from,int seed)throws Exception {
  try(var search=PlayerBotLedgeLeap.search(map,1,from[0],from[1],from[2],Float.NaN,Float.NaN,6,Float.NEGATIVE_INFINITY,PlayerBotLedgeLeap.Tier.RELOCATE,false,seed,store)) {
   long timeout=System.nanoTime()+30_000_000_000L;
   while(!search.done() && System.nanoTime()<timeout){search.advance(System.nanoTime()+1_000_000L);if(!search.done())Thread.sleep(1);}
   if(!search.done())throw new AssertionError("Relocation timed out");
   return search.result();
  }
 }
 private PlayerBotInvisibleFloorCheck(){}
}
