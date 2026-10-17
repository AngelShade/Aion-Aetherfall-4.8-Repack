package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geoEngine.models.GeoMap;

/** Aion headless jump/drop adapter for MovementActions::JumpTo; ordinary walking stays strict.
 * The bounded ballistic envelope is a host adaptation, not a client physics emulation. */
public final class PlayerBotHeightTraversal {
 public static final float GRAVITY=9.81f, MAX_RISE=1.6f, MAX_DROP=12, MAX_RANGE=8;
 static final double FOLLOW_PRIORITY=PlayerBotEngine.MOVE+5;
 static boolean followRequired(boolean autonomous,PlayerBotRules.Order order,boolean leaderAlive,boolean movable,boolean mission,float dx,float dy,float dz) {
  return !autonomous && leaderAlive && movable && !mission && (order==PlayerBotRules.Order.FOLLOW || order==PlayerBotRules.Order.PASSIVE)
   && finite(dx,dy,dz) && Math.abs(dz)>1.75f && Math.hypot(dx,dy)<=40;
 }
 public record Plan(float x,float y,float z,float endX,float endY,float endZ,float seconds,float launch,float delay) {
  public Plan(float x,float y,float z,float endX,float endY,float endZ,float seconds,float launch){this(x,y,z,endX,endY,endZ,seconds,launch,0);}
  public Vector3f at(double time) {
   double t=Math.max(0,Math.min(seconds,time)),ratio=Math.max(0,(t-delay)/(seconds-delay));
   return new Vector3f(x+(float)((endX-x)*ratio),y+(float)((endY-y)*ratio),
    t>=seconds?endZ:z+(float)(launch*t-GRAVITY*t*t/2));
  }
  public float fallDistance(){return Math.max(0,z+launch*launch/(2*GRAVITY)-endZ);}
  public boolean descending(double time){return launch-GRAVITY*time<=0;}
  public boolean falling(){return launch==0 && endX==x && endY==y && endZ<z;}
  public boolean valid(){return finite(x,y,z,endX,endY,endZ,seconds,launch,delay) && seconds>0 && delay>=0 && delay<seconds && launch>=0;}
 }
 public static Plan plan(GeoMap map,int instance,float x,float y,float z,float tx,float ty,float goalZ,float speed) {
  return plan(map,instance,x,y,z,tx,ty,goalZ,speed,Long.MAX_VALUE,MAX_DROP);
 }
 public static Plan plan(GeoMap map,int instance,float x,float y,float z,float tx,float ty,float goalZ,float speed,long deadline) {
  // Immune bots use the same bounded physical floor-query depth as interrupted gravity.
  return plan(map,instance,x,y,z,tx,ty,goalZ,speed,deadline,200);
 }
 /** Production is independent of human fall damage. Explicit/untimed helper
  * envelopes remain available for offline compatibility. */
 public static Plan plan(GeoMap map,int instance,float x,float y,float z,float tx,float ty,float goalZ,float speed,long deadline,float maximumDrop) {
  if(System.nanoTime()>=deadline)return null;
  if(map==null || !finite(x,y,z,tx,ty,goalZ,speed,maximumDrop) || speed<=0 || maximumDrop<=0)return null;
  float drop=goalZ<z-.3f?Math.min(200,maximumDrop):MAX_DROP;
  double distance=Math.hypot(tx-x,ty-y);
  if(distance<.3 || distance>MAX_RANGE)return null;
  float floor=PlayerBotGroundSupport.floor(map,instance,x,y,z+.3f,z-.3f);
  if(!Float.isFinite(floor) || Math.abs(floor-z)>.3)return null;
  float end=PlayerBotGroundSupport.floor(map,instance,tx,ty,z+MAX_RISE+.1f,z-drop);
  // Distant goals may require successive supported terraces in either direction.
  // Each arc still earns its local rise/drop, body and landing proof; it may not
  // overshoot the requested floor or substitute an unrelated layer.
  boolean intermediateDescent=goalZ<z-.3f && end<z-.3f && end>=goalZ;
  boolean intermediateAscent=goalZ>z+.3f && end>z+.3f && end<=goalZ;
  if(!Float.isFinite(end) || (Math.abs(end-goalZ)>2 && !intermediateDescent && !intermediateAscent) || end-z>MAX_RISE || z-end>drop)return null;
  // Never jump instead of a supported direct walk. For equal-height gaps the walk ends short.
  var walk=PlayerBotGroundNavigation.walk(map,instance,x,y,z,tx,ty,end);
  if(System.nanoTime()>=deadline)return null;
  if(Math.hypot(walk.x-tx,walk.y-ty)<.3 && Math.abs(walk.z-end)<.3)return null;
  float launch=end>z+.3f?Math.min(6,speed):Math.min(3,speed);
  double discriminant=launch*launch-2*GRAVITY*(end-z);
  if(discriminant<=0)return null;
  float seconds=(float)((launch+Math.sqrt(discriminant))/GRAVITY);
  float delay=end>z+.3f?(float)((launch-Math.sqrt(Math.max(0,launch*launch-2*GRAVITY*(end-z+.1f))))/GRAVITY):0;
  double durationLimit=end<z-.3f?(launch+Math.sqrt(launch*launch+2*GRAVITY*drop))/GRAVITY:3;
  if(!Float.isFinite(seconds) || seconds<=delay || seconds>durationLimit+.001 || distance/(seconds-delay)>speed)return null;
  Plan plan=new Plan(x,y,z,tx,ty,end,seconds,launch,delay);
  if(maximumDrop<200 && goalZ<z-.3f && plan.fallDistance()>=drop)return null;
  if(!landing(map,instance,plan))return null;
  Vector3f previous=plan.at(0);
  int samples=Math.max(8,(int)Math.ceil(Math.max(distance/.2,seconds/.05)));
  for(int i=1;i<=samples;i++) {
   if(System.nanoTime()>=deadline)return null;
   Vector3f next=plan.at(seconds*i/samples);
   if(!segment(map,instance,previous,next))return null;
   previous=next;
  }
  return plan;
 }
 public static boolean landing(GeoMap map,int instance,Plan plan) {
  if(map==null || plan==null || !plan.valid())return false;
  float ground=PlayerBotGroundSupport.floor(map,instance,plan.endX,plan.endY,plan.endZ+.2f,plan.endZ-.3f);
  if(!Float.isFinite(ground) || Math.abs(ground-plan.endZ)>.2)return false;
  if(!PlayerBotGroundNavigation.clearTraversal(map,instance,new Vector3f(plan.endX,plan.endY,plan.endZ+.05f),
   new Vector3f(plan.endX,plan.endY,plan.endZ+1.6f)))return false;
  // A planned jump may choose a wide landing. Already airborne gravity must
  // settle on its actual first supporting floor, including a narrow ledge.
  if(plan.falling())return true;
  // A supported body-sized landing, rather than a single ray on an edge.
  for(int d=0;d<8;d++) {
   float dx=.3f*(float)Math.cos(d*Math.PI/4),dy=.3f*(float)Math.sin(d*Math.PI/4);
   float support=PlayerBotGroundSupport.floor(map,instance,plan.endX+dx,plan.endY+dy,plan.endZ+.3f,plan.endZ-.4f);
   if(!Float.isFinite(support) || Math.abs(support-plan.endZ)>.35)return false;
   for(float h:new float[]{.1f,.75f,1.5f})if(!PlayerBotGroundNavigation.clearTraversal(map,instance,
    new Vector3f(plan.endX,plan.endY,plan.endZ+h),new Vector3f(plan.endX+dx,plan.endY+dy,plan.endZ+h)))return false;
  }
  return true;
 }
 public static boolean segment(GeoMap map,int instance,Vector3f from,Vector3f to) {
  if(map==null || from==null || to==null || !finite(from.x,from.y,from.z,to.x,to.y,to.z))return false;
  double distance=Math.hypot(to.x-from.x,to.y-from.y);
  float dx=distance<.001?1:(float)((to.x-from.x)/distance),dy=distance<.001?0:(float)((to.y-from.y)/distance);
  for(float side:new float[]{-.3f,0,.3f})for(float height:new float[]{.05f,.75f,1.5f})
   if(!PlayerBotGroundNavigation.clearTraversal(map,instance,new Vector3f(from.x-dy*side,from.y+dx*side,from.z+height),
    new Vector3f(to.x-dy*side,to.y+dx*side,to.z+height)))return false;
  return true;
 }
 /** Midair interruption sheds horizontal intent but keeps real gravity to supported ground. */
 public static Plan fall(GeoMap map,int instance,float x,float y,float z) {
  if(map==null || !finite(x,y,z))return null;
  float end=PlayerBotGroundSupport.floor(map,instance,x,y,z+.02f,z-200);
  if(!Float.isFinite(end) || end>z || z-end<.02)return null;
  Plan fall=new Plan(x,y,z,x,y,end,(float)Math.sqrt(2*(z-end)/GRAVITY),0);
  return segment(map,instance,fall.at(0),fall.at(fall.seconds))?fall:null;
 }
 private static boolean finite(float...values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
 private PlayerBotHeightTraversal(){}
}
