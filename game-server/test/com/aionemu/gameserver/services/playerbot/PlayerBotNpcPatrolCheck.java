package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import com.aionemu.gameserver.controllers.movement.NpcGroundNavigation;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;

/** Native isolated physical meshes; no World, actor IDs, SQL or scheduled movement. */
public final class PlayerBotNpcPatrolCheck {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static void add(GeoMap map,float[] vertices,short[] faces){
  var mesh=new Mesh();mesh.setVertices(FloatBuffer.wrap(vertices));mesh.setIndices(ShortBuffer.wrap(faces));
  mesh.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());mesh.updateBound();
  var geometry=new Geometry("isolated patrol support",mesh);
  geometry.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));geometry.updateModelBound();map.attachChild(geometry);map.updateModelBound();
 }
 private static void floor(GeoMap map,float from,float to,float height){
  add(map,new float[]{from,-10,height,to,-10,height,to,10,height,from,10,height},new short[]{0,1,2,0,2,3});
 }
 private static Vector3f step(GeoMap map,float x,float z,float to){return NpcGroundNavigation.step(map,1,new Vector3f(x,0,z),to,0,2);}
 public static void main(String[] args){
  var flat=new GeoMap(91);floor(flat,-10,10,0);
  var moved=step(flat,-3,0,3);check(moved!=null && moved.x>2.99 && Math.abs(moved.z)<.01,"Patrol follows actual ground");
  var settled=step(flat,-3,.8f,-3);check(settled!=null && Math.abs(settled.z)<.01,"Supported initial authored height settles on floor");
  check(step(flat,-3,4,3)==null,"Unsupported initial height cannot invent another floor");
  check(NpcGroundNavigation.step(null,1,new Vector3f(),1,1,2)==null,"Missing mesh rejects patrol");
  check(NpcGroundNavigation.step(flat,1,null,1,1,2)==null,"Missing origin rejected");
  check(step(flat,Float.NaN,0,3)==null,"Malformed origin rejected");
  check(step(flat,-3,0,Float.POSITIVE_INFINITY)==null,"Malformed target rejected");
  check(NpcGroundNavigation.step(flat,1,new Vector3f(),1,1,Float.NaN)==null,"Malformed body height rejected");
  check(step(flat,0,0,100)==null,"Unbounded tick rejected before speculative movement");
  var wall=new GeoMap(92);floor(wall,-10,10,0);
  add(wall,new float[]{0,-10,0,0,10,0,0,10,4,0,-10,4},new short[]{0,1,2,0,2,3});
  var blocked=step(wall,-3,0,3);check(blocked!=null && blocked.x<0,"Physical wall stops supported patrol before crossing");
  var cliff=new GeoMap(93);floor(cliff,-10,0,0);floor(cliff,0,10,-3);
  var edge=step(cliff,-3,0,3);check(edge!=null && edge.x<.01 && Math.abs(edge.z)<.01,"Patrol cannot fly across unsupported drop");
  var stacked=new GeoMap(94);floor(stacked,-10,10,0);floor(stacked,-10,10,5);
  var upper=step(stacked,-3,5,3);check(upper!=null && upper.x>2.99 && Math.abs(upper.z-5)<.01,"Existing upper floor retained");
  var lower=step(stacked,-3,0,3);check(lower!=null && lower.x>2.99 && Math.abs(lower.z)<.01,"Lower floor never jumps through upper support");
  System.out.println("OK: "+checks+" native NPC patrol ground, wall, floor, drop and invalid-input checks; no World/IDs/SQL/scheduler");
 }
 private PlayerBotNpcPatrolCheck(){}
}
