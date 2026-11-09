package com.aionemu.gameserver.services.playerbot;

import org.recast4j.detour.DefaultQueryFilter;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshQuery;

import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geoEngine.models.GeoMap;

/**
 * Leap of faith for a bot that stopped getting anywhere. Ordinary walking and jumping stay strict, so
 * a rock perch above a steep face offers them no legal way down. Once a bot has stayed in one place,
 * its goal stops mattering: it goes down wherever down is, and slides off steep ground to the floor below.
 */
public final class PlayerBotLedgeLeap {
 /** Stall ages: leap along a clear arc, leap through anything but a door, then move to nearby open ground. */
 static final long LEAP_MS=6000,GHOST_MS=12000,RELOCATE_MS=30000;
 static final float MIN_DROP=1;
 /** No bot dives further than a player could survive. A longer way down waits for the move to open ground. */
 static final float MAX_DROP=45;
 private static final float ANCHOR_RADIUS=6,ANCHOR_HEIGHT=3;
 private static final long OBSERVATION_GAP_MS=5000;
 private static final int DIRECTIONS=16;
 private static final float[] REACHES={1.5f,2.5f,4,6,8};
 private static final float[] RINGS={12,16,24,32,48,64},INSTANCE_RINGS={12,16};
 private static final int SLIDE_STEPS=64;
 private static final float SLIDE_STEP=1;
 private static final float[][] AROUND=new float[8][2];
 static {
  for(int i=0;i<8;i++){AROUND[i][0]=(float)Math.cos(i*Math.PI/4);AROUND[i][1]=(float)Math.sin(i*Math.PI/4);}
 }
 public enum Tier {NONE,LEAP,GHOST,RELOCATE}

 /** Measures how long a bot that wants to travel has stayed inside one small area. Pacing counts as staying. */
 static final class Stall {
  private float x,y,z;
  private int world,instance;
  private long since,seen;
  private boolean anchored;
  void clear(){anchored=false;}
  /** Starts the wait again as if the bot had already been stuck for the given time. */
  void rewind(long now,long stalledMs){if(anchored)since=now-stalledMs;}
  long observe(long now,int world,int instance,float x,float y,float z,boolean wanted) {
   if(!wanted || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)){clear();return 0;}
   if(!anchored || this.world!=world || this.instance!=instance || now<seen || now-seen>OBSERVATION_GAP_MS
    || Math.hypot(x-this.x,y-this.y)>ANCHOR_RADIUS || Math.abs(z-this.z)>ANCHOR_HEIGHT) {
    anchored=true;this.world=world;this.instance=instance;this.x=x;this.y=y;this.z=z;since=now;
   }
   seen=now;
   return now-since;
  }
 }
 /** Leaps this bot may spend in a window. Past that, more leaps would only take it further from wherever it was going. */
 static final class Budget {
  static final int LEAPS=2;
  static final long WINDOW_MS=300000;
  private long first;
  private int used;
  boolean spent(long now){if(used>0 && now-first>=WINDOW_MS)used=0;return used>=LEAPS;}
  void spend(long now){if(used==0 || now-first>=WINDOW_MS){first=now;used=0;}used++;}
  void clear(){used=0;}
 }
 static Tier tier(long stalled){return stalled>=RELOCATE_MS?Tier.RELOCATE:stalled>=GHOST_MS?Tier.GHOST:stalled>=LEAP_MS?Tier.LEAP:Tier.NONE;}

 public record Landing(float x,float y,float z,boolean open) {}
 /** A planned leap, or a target to move to instantly when tier is RELOCATE. */
 public record Escape(Landing landing,PlayerBotHeightTraversal.Plan plan) {}

 /** Retains its place across terrain slices, as a stuck crowd must not stall the planning thread. */
 public static final class Search implements AutoCloseable {
  private final GeoMap map;
  private final int instance;
  private final float x,y,z,speed,minimumZ;
  private final Tier tier;
  private final boolean sealedMap;
  private final double start;
  private final int total;
  private int cursor;
  private final PlayerBotNavmeshStore store;
  private PlayerBotNavmeshStore.Request topologyRequest,meshRequest;
  private PlayerBotNavmeshStore.Lease meshLease;
  private PlayerBotNavmeshTopology graph;
  private NavMesh mesh;
  private NavMeshQuery query;
  private int originRegion=-1,originIsland=-1;
  private boolean prepared,columnPending;
  private float columnTop=Float.NaN;
  private Escape best;
  private boolean done;
  private Search(GeoMap map,int instance,float x,float y,float z,float goalX,float goalY,float speed,float minimumZ,Tier tier,boolean sealedMap,int seed,PlayerBotNavmeshStore store) {
   this.store=tier==Tier.RELOCATE && !sealedMap?store:null;
   this.map=map;this.instance=instance;this.x=x;this.y=y;this.z=z;this.speed=speed;this.minimumZ=minimumZ;this.tier=tier;this.sealedMap=sealedMap;
   // Goalward first. The seed spreads a crowd over different landings so it does not pile up again below.
   double toward=Float.isFinite(goalX) && Float.isFinite(goalY) && Math.hypot(goalX-x,goalY-y)>.5?Math.atan2(goalY-y,goalX-x):Math.floorMod(seed,DIRECTIONS)*2*Math.PI/DIRECTIONS;
   start=toward+Math.floorMod(seed,5)*.07;
   total=DIRECTIONS*(tier==Tier.RELOCATE?rings().length:REACHES.length);
   done=map==null || speed<=0 || !finite(x,y,z,speed);
  }
  private float[] rings(){return sealedMap?INSTANCE_RINGS:RINGS;}
  public boolean done(){return done;}
  public Escape result(){return best;}
  public Escape advance(long deadline) {
   if(done)return best;
   if(System.nanoTime()>=deadline || !prepare(deadline))return null;
   while(!done && cursor<total && System.nanoTime()<deadline) {
    int index=cursor;
    Escape found=tier==Tier.RELOCATE?ring(index,deadline):leap(index);
    if(tier!=Tier.RELOCATE || !columnPending)cursor++;
    if(found!=null && (best==null || found.landing().open() && !best.landing().open()))best=found;
    if(best!=null && best.landing().open())done=true;
   }
   if(cursor>=total)done=true;
   if(done)close();
   return done?best:null;
  }
  /** Sweep near-to-far on each side in turn, alternating left and right of the goal. */
  private double angle(int direction){
   int side=(direction+1)/2;double step=2*Math.PI/DIRECTIONS;
   return start+(direction%2==1?side:-side)*step;
  }
  private Escape leap(int index) {
   double radians=angle(index/REACHES.length);float reach=REACHES[index%REACHES.length];
   float tx=x+(float)Math.cos(radians)*reach,ty=y+(float)Math.sin(radians)*reach;
   // Inside or against rock, the column above the feet only finds the rock's own surface.
   for(float top:tier==Tier.GHOST?new float[]{z+.8f,z-.6f}:new float[]{z+.8f}) {
    var landing=settle(map,instance,tx,ty,top,z-MIN_DROP,minimumZ);
    if(landing==null || z-landing.z()>MAX_DROP)continue;
    var plan=plan(x,y,z,landing,speed);
    if(tier==Tier.GHOST?!door(map,instance,x,y,z,landing):!arc(map,instance,plan))continue;
    return new Escape(landing,plan);
   }
   return null;
  }
  /** Polling never reads files or waits for the loader. The request pins immutable
   * topology/tiles; this search alone owns the mutable Detour query. */
  private boolean prepare(long deadline) {
   if(prepared)return true;
   if(store==null){prepared=true;return true;}
   if(topologyRequest==null)topologyRequest=store.requestTopology(map.getMapId());
   var topology=topologyRequest.poll();
   if(topology.status()==PlayerBotNavmeshStore.Status.WAITING)return false;
   if(topology.status()==PlayerBotNavmeshStore.Status.UNAVAILABLE){geometryOnly();return true;}
   graph=topology.lease().topology();
   if(System.nanoTime()>=deadline)return false;
   // Reuse a resident window if its apron covers all ring points and polygon
   // extents. Otherwise request one bounded window asynchronously for this search.
   if(meshLease==null && meshRequest==null)meshLease=store.resident(map.getMapId(),x-65,y-65,x+65,y+65);
   if(meshLease==null) {
    if(meshRequest==null)meshRequest=store.request(map.getMapId(),x,y,2);
    var access=meshRequest.poll();
    if(access.status()==PlayerBotNavmeshStore.Status.WAITING)return false;
    if(access.status()==PlayerBotNavmeshStore.Status.UNAVAILABLE){geometryOnly();return true;}
    meshLease=access.lease();
   }
   if(System.nanoTime()>=deadline)return false;
   mesh=meshLease.mesh();query=new NavMeshQuery(mesh);
   int component=component(x,y,z);
   if(graph.usableGround(component))originRegion=graph.groundRegion(component);
   else if(component>=0)originIsland=graph.groundRegion(component);
   prepared=true;return true;
  }
  private void geometryOnly(){release();graph=null;prepared=true;}
  private int component(float x,float y,float z) {
   var nearest=query.findNearestPoly(new float[]{x,z,y},new float[]{1,.75f,1},new DefaultQueryFilter());
   if(nearest.failed() || nearest.result.getNearestRef()==0)return -1;
   float[] at=nearest.result.getNearestPos();
   // Detour includes walkableClimb in nearest-poly scoring; enforce the actual floor.
   if(Math.abs(at[1]-z)>.75f || Math.hypot(at[0]-x,at[2]-y)>1)return -1;
   return graph.component(mesh,nearest.result.getNearestRef());
  }
  private Escape ring(int index,long deadline) {
   float[] rings=rings();
   double radians=angle(index%DIRECTIONS);float radius=rings[index/DIRECTIONS];
   float tx=x+(float)Math.cos(radians)*radius,ty=y+(float)Math.sin(radians)*radius;
   float bottom=Math.max(minimumZ,z+3-MAX_DROP);
   if(!Float.isFinite(columnTop))columnTop=z+3;
   columnPending=true;
   while(columnTop>=bottom) {
    if(System.nanoTime()>=deadline)return null;
    float floor=PlayerBotGroundSupport.floor(map,instance,tx,ty,columnTop,bottom);
    if(!Float.isFinite(floor) || floor<bottom)break;
    columnTop=floor-.5f;
    int component=graph==null?-1:component(tx,ty,floor);
    boolean usable=graph!=null && graph.usableGround(component);
    boolean open=PlayerBotGroundNavigation.landing(map,instance,tx,ty,floor);
    if(open && (graph==null || usable && (originRegion<0 || originRegion==graph.groundRegion(component)))) {
     columnPending=false;columnTop=Float.NaN;
     return new Escape(new Landing(tx,ty,floor,true),null);
    }
    // Bypass only blocked parts of the small island carrying this stranded bot.
    // An open isolated platform can be a real roof; even connected ground can run
    // under a street. Missing polygons never prove a slab. Try another XY there.
    if(graph==null || component<0 || usable || open || originIsland!=graph.groundRegion(component))break;
   }
   columnPending=false;columnTop=Float.NaN;return null;
  }
  @Override public void close(){release();done=true;}
  private void release() {
   query=null;mesh=null;
   if(meshRequest!=null){meshRequest.close();meshRequest=null;}
   if(meshLease!=null){meshLease.close();meshLease=null;}
   if(topologyRequest!=null){topologyRequest.close();topologyRequest=null;}
  }
 }
 public static Search search(GeoMap map,int instance,float x,float y,float z,float goalX,float goalY,float speed,float minimumZ,Tier tier,boolean sealedMap,int seed) {
  var store=tier==Tier.RELOCATE && !sealedMap && map!=null && com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED
   ?PlayerBotMeshRoute.relocationStore():null;
  return search(map,instance,x,y,z,goalX,goalY,speed,minimumZ,tier,sealedMap,seed,store);
 }
 static Search search(GeoMap map,int instance,float x,float y,float z,float goalX,float goalY,float speed,float minimumZ,Tier tier,boolean sealedMap,int seed,PlayerBotNavmeshStore store) {
  return new Search(map,instance,x,y,z,goalX,goalY,speed,minimumZ,tier,sealedMap,seed,store);
 }

 /** The ground a body dropped into this column ends up on, sliding off anything too steep to stand on. */
 static Landing settle(GeoMap map,int instance,float x,float y,float top,float highest,float minimumZ) {
  float bottom=top-200;
  float floor=PlayerBotGroundSupport.floor(map,instance,x,y,top,bottom);
  if(!Float.isFinite(floor)) {
   float surface=map.getZ(x,y,top,bottom,instance);
   if(!Float.isFinite(surface))return null;
   for(int step=0;step<SLIDE_STEPS && !Float.isFinite(floor);step++) {
    float lowest=surface,nextX=x,nextY=y;
    for(float[] way:AROUND) {
     float around=map.getZ(x+way[0]*SLIDE_STEP,y+way[1]*SLIDE_STEP,surface+.5f,surface-30,instance);
     if(Float.isFinite(around) && around<lowest){lowest=around;nextX=x+way[0]*SLIDE_STEP;nextY=y+way[1]*SLIDE_STEP;}
    }
    if(nextX==x && nextY==y)return null; // Nowhere lower, so the slope has no way out.
    x=nextX;y=nextY;surface=lowest;
    floor=PlayerBotGroundSupport.floor(map,instance,x,y,surface+.4f,surface-.4f);
   }
   if(!Float.isFinite(floor))return null;
  }
  if(floor>highest || floor<minimumZ)return null;
  if(!PlayerBotGroundNavigation.clearTraversal(map,instance,new Vector3f(x,y,floor+.1f),new Vector3f(x,y,floor+1.6f)))return null;
  return new Landing(x,y,floor,PlayerBotGroundNavigation.landing(map,instance,x,y,floor));
 }
 static PlayerBotHeightTraversal.Plan plan(float x,float y,float z,Landing landing,float speed) {
  float launch=Math.min(3,speed),g=PlayerBotHeightTraversal.GRAVITY;
  float fall=(launch+(float)Math.sqrt(launch*launch+2*g*(z-landing.z())))/g;
  float travel=Math.max(.05f,(float)(Math.hypot(landing.x()-x,landing.y()-y)/speed));
  return new PlayerBotHeightTraversal.Plan(x,y,z,landing.x(),landing.y(),landing.z(),Math.max(fall,travel),launch,0,travel,true);
 }
 /** The body's middle must clear the arc. Feet may clip a lip, which is what stops the strict jump. */
 private static boolean arc(GeoMap map,int instance,PlayerBotHeightTraversal.Plan plan) {
  int samples=Math.max(8,Math.min(48,(int)Math.ceil(plan.seconds()/.1f)));
  Vector3f previous=plan.at(0);
  for(int sample=1;sample<=samples;sample++) {
   Vector3f next=plan.at(plan.seconds()*sample/samples);
   for(float height:new float[]{.75f,1.5f})
    if(!PlayerBotGroundNavigation.clearTraversal(map,instance,new Vector3f(previous.x,previous.y,previous.z+height),new Vector3f(next.x,next.y,next.z+height)))return false;
   previous=next;
  }
  return true;
 }
 private static boolean door(GeoMap map,int instance,float x,float y,float z,Landing landing) {
  return !PlayerBotGroundNavigation.closedDoorBetween(map,instance,new Vector3f(x,y,z+.75f),new Vector3f(landing.x(),landing.y(),landing.z()+.75f));
 }
 private static boolean finite(float...values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
 private PlayerBotLedgeLeap(){}
}
