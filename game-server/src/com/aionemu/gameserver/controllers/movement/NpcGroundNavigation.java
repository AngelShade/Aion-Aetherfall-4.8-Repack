package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.geoEngine.collision.*;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;

/** Ground patrol steps follow physical support; authored waypoint Z is not a flight path. */
public final class NpcGroundNavigation {
 /** Native current state owns live locomotion; initial metadata applies before admission only. */
 public static boolean isAerialPatrol(Npc npc) {
  if(npc.isFlying() || npc.getSpawn()!=null && npc.getSpawn().isAerialSpawn())return true;
  if(npc.isSpawned())return false;
  int state=npc.getSpawn()!=null && npc.getSpawn().getState()>0?npc.getSpawn().getState():npc.getObjectTemplate().getState();
  return (state&CreatureState.GLIDING.getId())!=0
   || (state&CreatureState.FLYING.getId())!=0 && (state&CreatureState.RESTING.getId())==0;
 }
 public static Vector3f step(GeoMap map,int instance,Vector3f from,float x,float y,float height) {
  if(map==null || !finite(from) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(height))return null;
  float floor=map.getPlayerBotGroundZ(from.x,from.y,from.z+.6f,from.z-1.5f,instance);
  if(!Float.isFinite(floor))return null;
  Vector3f current=new Vector3f(from.x,from.y,floor);
  for(float h:new float[]{.25f,Math.max(.25f,height-.15f)})
   if(!clear(map,instance,new Vector3f(from.x,from.y,from.z+h),new Vector3f(current.x,current.y,current.z+h)))return null;
  double distance=Math.hypot(x-from.x,y-from.y);
  int samples=Math.max(1,(int)Math.ceil(distance/.35));
  if(samples>128)return null; // Caller bounds a native movement tick; no unbounded recursive movement.
  for(int i=1;i<=samples;i++) {
   float nx=i==samples?x:from.x+(x-from.x)*i/samples,ny=i==samples?y:from.y+(y-from.y)*i/samples;
   float z=map.getPlayerBotGroundZ(nx,ny,current.z+.6f,current.z-.85f,instance);
   double span=Math.hypot(nx-current.x,ny-current.y);
   if(!Float.isFinite(z) || z-current.z>span+.15 || current.z-z>span+.3)return current;
   Vector3f next=new Vector3f(nx,ny,z);
   for(float h:new float[]{.25f,Math.max(.25f,height-.15f)})
    if(!clear(map,instance,new Vector3f(current.x,current.y,current.z+h),new Vector3f(next.x,next.y,next.z+h)))return current;
   current=next;
  }
  return current;
 }
 /** Pre-spawn formation admission: every edge begins at the authored route layer.
  * A partial/blocked projection is never accepted as the formation's height. */
 public static Vector3f spawnPoint(GeoMap map,int instance,Vector3f authored,float x,float y,float height) {
  Vector3f supported=step(map,instance,authored,x,y,height);
  return supported!=null && supported.x==x && supported.y==y?supported:null;
 }
 private static boolean clear(GeoMap map,int instance,Vector3f from,Vector3f to) {
  return from.distance(to)<.001 || map.getPlayerBotCollisions(from,to.x,to.y,to.z,instance,
   CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE).size()==0;
 }
 private static boolean finite(Vector3f p){return p!=null && Float.isFinite(p.x) && Float.isFinite(p.y) && Float.isFinite(p.z);}
 private NpcGroundNavigation(){}
}
