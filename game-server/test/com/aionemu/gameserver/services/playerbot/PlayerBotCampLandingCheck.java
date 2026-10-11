package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Native synthetic terrain only: no World, characters, IDs, SQL or lifecycle. */
public final class PlayerBotCampLandingCheck {
 private static int checks;
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 private static void add(GeoMap map,float[] vertices,short[] faces){
  Mesh mesh=new Mesh();mesh.setVertices(FloatBuffer.wrap(vertices));mesh.setIndices(ShortBuffer.wrap(faces));
  mesh.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());mesh.updateBound();
  Geometry geometry=new Geometry("synthetic camp",mesh);geometry.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));geometry.updateModelBound();map.attachChild(geometry);
 }
 public static void main(String[] args){
  GeoMap map=new GeoMap(94);
  add(map,new float[]{0,0,0,100,0,0,100,100,0,0,100,0},new short[]{0,1,2,0,2,3});
  add(map,new float[]{50,48,0,50,52,0,50,52,3,50,48,3},new short[]{0,1,2,0,2,3});map.updateModelBound();
  Point camp=new Point(49.9f,50,0);
  check(!PlayerBotGroundNavigation.landing(map,1,camp.x(),camp.y(),camp.z()),"exact native camp point is body-blocked beside wall");
  var nativeProbe=new PlayerBotCampLanding.Ground(){
   public float height(float x,float y,float z){return PlayerBotGroundSupport.floor(map,1,x,y,z+2,z-4);}
   public boolean safe(Point p){return PlayerBotGroundNavigation.landing(map,1,p.x(),p.y(),p.z());}
  };
  var found=PlayerBotCampLanding.nearby(camp,nativeProbe,()->false);
  check(found!=null,"same native camp has safe supported adjacent terrain");
  check(PlayerBotGroundNavigation.landing(map,1,found.x(),found.y(),found.z()),"returned landing has native body clearance and walking exit");
  check(Math.hypot(found.x()-camp.x(),found.y()-camp.y())<=4.01 && Math.abs(found.z())<.01,"nearby landing keeps camp and real ground altitude");
  AtomicBoolean allowed=new AtomicBoolean(true);
  var liveProbe=new PlayerBotCampLanding.Ground(){
   public float height(float x,float y,float z){return nativeProbe.height(x,y,z);}
   public boolean safe(Point p){return allowed.get() && nativeProbe.safe(p);}
  };
  check(PlayerBotCampLanding.nearby(camp,liveProbe,()->false)!=null,"current live eligibility admits a safe point");allowed.set(false);
  check(PlayerBotCampLanding.nearby(camp,liveProbe,()->false)==null,"changed proximity/eligibility is rechecked instead of cached success");
  for(float height:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-5,4.01f}){
   int[] accepted={0};var invalid=new PlayerBotCampLanding.Ground(){public float height(float x,float y,float z){return height;}public boolean safe(Point p){accepted[0]++;return true;}};
   check(PlayerBotCampLanding.nearby(new Point(10,10,0),invalid,()->false)==null,"invalid or unrelated floor cannot repair camp");
   check(accepted[0]==0,"unsafe altitude never reaches admission predicate");
  }
  int[] attempts={0},limit={0};var blocked=new PlayerBotCampLanding.Ground(){public float height(float x,float y,float z){attempts[0]++;return z;}public boolean safe(Point p){return false;}};
  check(PlayerBotCampLanding.nearby(camp,blocked,()->limit[0]++>=5)==null && attempts[0]==5,"shared probe budget stops even when no point is safe");
  attempts[0]=0;check(PlayerBotCampLanding.nearby(camp,blocked,()->true)==null && attempts[0]==0,"expired pass performs no geometry work");
  attempts[0]=0;check(PlayerBotCampLanding.nearby(camp,blocked,()->false)==null && attempts[0]==16,"unsupported camp has a finite fallback");
  for(Point invalid:new Point[]{null,new Point(Float.NaN,0,0),new Point(0,Float.POSITIVE_INFINITY,0),new Point(0,0,Float.NaN)}){
   attempts[0]=0;check(PlayerBotCampLanding.nearby(invalid,blocked,()->false)==null && attempts[0]==0,"invalid anchor cannot probe or spawn");
  }
  GeoMap empty=new GeoMap(93);empty.updateModelBound();
  var missing=new PlayerBotCampLanding.Ground(){public float height(float x,float y,float z){return PlayerBotGroundSupport.floor(empty,1,x,y,z+2,z-4);}public boolean safe(Point p){return true;}};
  check(PlayerBotCampLanding.nearby(camp,missing,()->false)==null,"missing native floor retains actor offline");
  for(Point origin:new Point[]{new Point(10,10,0),new Point(70,20,0),new Point(30,80,0)}){
   var p=PlayerBotCampLanding.nearby(origin,nativeProbe,()->false);
   check(p!=null && PlayerBotGroundNavigation.landing(map,1,p.x(),p.y(),p.z()),"generic camp recovery across varied coordinates");
  }
  System.out.println("OK: "+checks+" bounded native camp landing checks");
 }
}
