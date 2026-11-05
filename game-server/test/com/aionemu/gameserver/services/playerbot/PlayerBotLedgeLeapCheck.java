package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;

/** The stuck-bot leap on isolated native meshes; no World, actors, IDs or SQL. */
public final class PlayerBotLedgeLeapCheck {
 static int checks;
 static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
 static Mesh mesh(float[] vertices,short[] faces){var m=new Mesh();m.setVertices(FloatBuffer.wrap(vertices));m.setIndices(ShortBuffer.wrap(faces));m.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());m.updateBound();return m;}
 static Mesh quad(float x0,float x1,float y0,float y1,float z0,float z1){return mesh(new float[]{x0,y0,z0,x1,y0,z1,x1,y1,z1,x0,y1,z0},new short[]{0,1,2,0,2,3});}
 static final short[] BOTH={0,1,2,0,2,3,2,1,0,3,2,0}; // Native collision culls back faces, so a solid wall needs both windings.
 static Mesh wall(float x,float bottom,float top){return mesh(new float[]{x,-40,bottom,x,40,bottom,x,40,top,x,-40,top},BOTH);}
 /** A closed room: floor at the given height and four double-sided walls from the ground up. */
 static Mesh shell(float half,float floor,float top){
  float[] v={-half,-half,floor,half,-half,floor,half,half,floor,-half,half,floor,
   -half,-half,0,half,-half,0,half,-half,top,-half,-half,top, -half,half,0,half,half,0,half,half,top,-half,half,top,
   -half,-half,0,-half,half,0,-half,half,top,-half,-half,top, half,-half,0,half,half,0,half,half,top,half,-half,top};
  short[] f=new short[60];int at=0;
  for(int q=0;q<5;q++){short o=(short)(q*4);
   for(int i:new int[]{0,1,2,0,2,3})f[at++]=(short)(o+i);
   if(q>0)for(int i:new int[]{2,1,0,3,2,0})f[at++]=(short)(o+i);}
  return mesh(v,java.util.Arrays.copyOf(f,at));
 }
 static void add(GeoMap map,Mesh mesh){var g=new Geometry("fixture",mesh);g.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));g.updateModelBound();map.attachChild(g);map.updateModelBound();}
 static GeoMap map(Mesh...meshes){var map=new GeoMap(919);for(var m:meshes)add(map,m);return map;}
 static PlayerBotLedgeLeap.Escape run(GeoMap map,float x,float y,float z,float goalX,float goalY,PlayerBotLedgeLeap.Tier tier,boolean sealed,float minimumZ) {
  var search=PlayerBotLedgeLeap.search(map,1,x,y,z,goalX,goalY,6,minimumZ,tier,sealed,7);
  var escape=search.advance(Long.MAX_VALUE);
  check(search.done(),"an unbounded lease finishes the search");
  return escape;
 }
 static void stallChecks() {
  var stall=new PlayerBotLedgeLeap.Stall();
  check(stall.observe(0,1,1,0,0,0,true)==0,"first sight only anchors");
  check(stall.observe(3000,1,1,4,0,0,true)==3000,"pacing inside the area keeps counting");
  check(PlayerBotLedgeLeap.tier(6000)==PlayerBotLedgeLeap.Tier.LEAP && PlayerBotLedgeLeap.tier(5999)==PlayerBotLedgeLeap.Tier.NONE,"leap starts at six seconds");
  check(PlayerBotLedgeLeap.tier(12000)==PlayerBotLedgeLeap.Tier.GHOST && PlayerBotLedgeLeap.tier(30000)==PlayerBotLedgeLeap.Tier.RELOCATE,"tiers escalate");
  check(stall.observe(6000,1,1,7,0,0,true)==0,"leaving the area is progress");
  stall.observe(8000,1,1,7,0,0,true);
  check(stall.observe(14000,1,1,7,0,0,true)==0,"a long gap means the bot stopped asking to move");
  stall.observe(15000,1,1,7,0,0,true);
  check(stall.observe(16000,1,1,7,0,4,true)==0,"a drop is progress");
  stall.observe(17000,1,1,7,0,4,true);
  check(stall.observe(18000,1,1,7,0,4,false)==0,"waiting for a lift never counts");
  check(stall.observe(19000,2,1,7,0,4,true)==0,"another map starts over");
  check(stall.observe(20000,2,2,7,0,4,true)==0,"another instance starts over");
  stall.rewind(20000,12000);
  check(stall.observe(20000,2,2,7,0,4,true)==12000,"a failed relocation waits from the ghost age");
  check(stall.observe(20100,2,2,Float.NaN,0,4,true)==0,"a broken position never counts");
 }
 public static void main(String[] args) {
  stallChecks();
  // A high ledge with its goal above it: the strict jump refuses, the leap does not.
  var ledge=map(quad(-20,0,-20,20,30,30),quad(0,40,-20,20,0,0),wall(0,0,30));
  check(PlayerBotHeightTraversal.search(ledge,1,-.5f,0,30,4,0,45,6,200).advance(Long.MAX_VALUE)==null,"goal above: strict traversal refuses the drop");
  var leap=run(ledge,-.5f,0,30,-15,0,PlayerBotLedgeLeap.Tier.LEAP,false,Float.NEGATIVE_INFINITY);
  check(leap!=null && leap.plan()!=null && leap.plan().leap(),"goal above: the leap goes anyway, even directly away from the goal");
  check(Math.abs(leap.landing().z())<.01 && leap.landing().open(),"the leap reaches the real ground and can walk from it");
  check(PlayerBotHeightTraversal.landing(ledge,1,leap.plan()),"the mover accepts the chosen landing");
  check(leap.plan().valid() && leap.plan().horizontalSpeed()<=6.01,"a leap never exceeds run speed");
  var arc=leap.plan().at(leap.plan().seconds());
  check(arc.x==leap.landing().x() && arc.z==leap.landing().z(),"the arc ends exactly on the landing");
  // A steep face below the ledge: every native floor query there answers "no floor".
  var face=map(quad(-20,0,-20,20,30,30),quad(0,20,-20,20,30,0),quad(20,60,-20,20,0,0));
  check(!Float.isFinite(PlayerBotGroundSupport.floor(face,1,4,0,30.8f,-170)),"fixture: the steep face has no native floor");
  check(PlayerBotHeightTraversal.search(face,1,-.5f,0,30,4,0,-5,6,200).advance(Long.MAX_VALUE)==null,"steep face: strict traversal finds no landing");
  var slide=run(face,-.5f,0,30,40,0,PlayerBotLedgeLeap.Tier.LEAP,false,Float.NEGATIVE_INFINITY);
  check(slide!=null && Math.abs(slide.landing().z())<.01 && slide.landing().x()>=19,"steep face: the bot slides to the ground at the bottom");
  check(slide.plan().horizontalSpeed()<=6.01,"a long slide still respects run speed");
  for(int i=1;i<=20;i++){var at=slide.plan().at(slide.plan().seconds()*i/20f);
   float faceZ=at.x<=0?30:at.x>=20?0:30-1.5f*at.x;check(at.z>=faceZ-.5f || i==20,"a slide stays on top of the slope, not under it");}
  // A lip right at the feet stops the strict arc's foot rays, but not a body-height leap.
  var lip=map(quad(-20,0,-20,20,30,30),quad(0,40,-20,20,0,0),wall(0,0,30),quad(-1,.4f,-20,20,30.12f,30.12f));
  check(run(lip,-.5f,0,30.12f,8,0,PlayerBotLedgeLeap.Tier.LEAP,false,Float.NEGATIVE_INFINITY)!=null,"a low lip does not hold the bot");
  // Boxed in by rock: only the ghost tier goes through it.
  // One solid shell around the platform, as thin separate planes do not stop short native rays reliably.
  var box=map(quad(-20,20,-20,20,0,0),shell(3,20,40));
  check(run(box,0,0,20,10,0,PlayerBotLedgeLeap.Tier.LEAP,false,Float.NEGATIVE_INFINITY)==null,"walls stop the arc tier");
  var ghost=run(box,0,0,20,10,0,PlayerBotLedgeLeap.Tier.GHOST,false,Float.NEGATIVE_INFINITY);
  check(ghost!=null && Math.abs(ghost.landing().z())<.01,"the ghost tier gets out through the walls");
  // Nothing to do on open flat ground, so a blocked route can never make a bot hop about.
  var flat=map(quad(-40,40,-40,40,0,0));
  for(var tier:new PlayerBotLedgeLeap.Tier[]{PlayerBotLedgeLeap.Tier.LEAP,PlayerBotLedgeLeap.Tier.GHOST})
   check(run(flat,0,0,0,30,0,tier,false,Float.NEGATIVE_INFINITY)==null,"flat ground has no leap: "+tier);
  // A knee-high step is not a ledge.
  var step=map(quad(-40,0,-40,40,.5f,.5f),quad(0,40,-40,40,0,0),wall(0,0,.5f));
  check(run(step,-.5f,0,.5f,30,0,PlayerBotLedgeLeap.Tier.LEAP,false,Float.NEGATIVE_INFINITY)==null,"a drop under a metre is left to walking");
  // Water below the safe height is refused, including at the end of a slide.
  check(run(ledge,-.5f,0,30,10,0,PlayerBotLedgeLeap.Tier.LEAP,false,5)==null,"a landing under water is refused");
  check(run(face,-.5f,0,30,40,0,PlayerBotLedgeLeap.Tier.LEAP,false,5)==null,"a slide into water is refused");
  // Last resort finds open ground at any height, and stays close on a sealed instance map.
  var tower=map(quad(-1.5f,1.5f,-1.5f,1.5f,20,20),quad(-80,80,-80,80,0,0));
  var far=run(tower,0,0,20,30,0,PlayerBotLedgeLeap.Tier.RELOCATE,false,Float.NEGATIVE_INFINITY);
  check(far!=null && far.plan()==null && Math.abs(far.landing().z())<.01 && far.landing().open(),"relocation lands on open ground");
  var perch=map(quad(-1.5f,1.5f,-1.5f,1.5f,20,20),quad(30,80,-80,80,0,0));
  check(run(perch,0,0,20,50,0,PlayerBotLedgeLeap.Tier.RELOCATE,false,Float.NEGATIVE_INFINITY)!=null,"open maps look up to 64 metres away");
  check(run(perch,0,0,20,50,0,PlayerBotLedgeLeap.Tier.RELOCATE,true,Float.NEGATIVE_INFINITY)==null,"instance maps never relocate through distant walls");
  // A deadline only pauses: the retained search resumes where it stopped.
  var paused=PlayerBotLedgeLeap.search(ledge,1,-.5f,0,30,-15,0,6,Float.NEGATIVE_INFINITY,PlayerBotLedgeLeap.Tier.LEAP,false,7);
  check(paused.advance(0)==null && !paused.done(),"an exhausted slice is not a failed search");
  check(paused.advance(Long.MAX_VALUE)!=null && paused.done(),"the retained search still finishes");
  // A crowd spreads out instead of piling up on one landing again.
  var spots=new java.util.HashSet<String>();
  for(int seed=0;seed<16;seed++){var e=PlayerBotLedgeLeap.search(ledge,1,-.5f,0,30,Float.NaN,Float.NaN,6,Float.NEGATIVE_INFINITY,PlayerBotLedgeLeap.Tier.LEAP,false,seed).advance(Long.MAX_VALUE);
   check(e!=null,"every member of a crowd finds a way down");spots.add(Math.round(e.landing().x()*2)+","+Math.round(e.landing().y()*2));}
  check(spots.size()>=3,"a crowd lands in different places: "+spots.size());
  System.out.println("OK: "+checks+" ledge leap checks");
 }
 private PlayerBotLedgeLeapCheck(){}
}
