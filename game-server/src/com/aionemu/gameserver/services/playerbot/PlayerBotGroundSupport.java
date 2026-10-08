package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.geoEngine.collision.*;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;

/** Sight transparency does not remove physical support. WALK-only meshes are not floors. */
final class PlayerBotGroundSupport {
 static float floor(GeoMap map,int instance,float x,float y,float top,float bottom){
  // Retain native terrain handling and its slope checks.
  float physical=map.getZ(x,y,top,bottom,instance,true);
  CollisionResults results=new CollisionResults(CollisionIntention.PHYSICAL_SEE_THROUGH.getId(),instance,IgnoreProperties.ANY_RACE);
  results.setInvalidateSlopingSurface(true);
  Ray ray=new Ray(new Vector3f(x,y,top),new Vector3f(0,0,-1));ray.setLimit(top-bottom);
  map.collideWith(ray,results);
  CollisionResult hit=results.getClosestCollision();
  if(hit==null || !Float.isFinite(hit.getContactPoint().z))return physical;
  float transparent=hit.getContactPoint().z;
  // Match native floor semantics: the highest supported surface in the
  // same narrow vertical window, including physical see-through collision.
  return Float.isFinite(physical)?Math.max(physical,transparent):transparent;
 }
 private PlayerBotGroundSupport(){}
}
