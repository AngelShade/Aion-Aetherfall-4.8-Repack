package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.geoEngine.scene.DespawnableNode.DespawnableType;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import sun.misc.Unsafe;

/** Actual isolated native meshes and production arcs; no World, actors, IDs or SQL. */
public final class PlayerBotHeightTraversalCheck {
 static int checks;
 static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
 static Mesh mesh(float[] vertices,short[] faces){var m=new Mesh();m.setVertices(FloatBuffer.wrap(vertices));m.setIndices(ShortBuffer.wrap(faces));m.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());m.updateBound();return m;}
 static Mesh floor(float from,float to,float z){return mesh(new float[]{from,-10,z,to,-10,z,to,10,z,from,10,z},new short[]{0,1,2,0,2,3});}
 static Mesh wall(float x,float top){return mesh(new float[]{x,-10,-15,x,10,-15,x,10,top,x,-10,top},new short[]{0,1,2,0,2,3});}
 static void add(GeoMap map,Mesh mesh){var g=new Geometry("fixture",mesh);g.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));g.updateModelBound();map.attachChild(g);map.updateModelBound();}
 static GeoMap level(float height){var map=new GeoMap(918);add(map,floor(-10,0,0));add(map,floor(0,10,height));return map;}
 static PlayerBotHeightTraversal.Plan plan(GeoMap m,float from,float to,float height){return PlayerBotHeightTraversal.plan(m,1,from,0,0,to,0,height,6);}
 static final class Actor extends Player {
  boolean spawned=true,dead,flying,casting,movable=true;int world=1,instance=1;
  Actor(){super(null,null);}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isFlying(){return flying;}
  @Override public boolean isCasting(){return casting;}
  @Override public boolean canPerformMove(){return movable;}
  @Override public int getWorldId(){return world;}
  @Override public int getInstanceId(){return instance;}
 }
 static void field(Object object,String name,Object value)throws Exception{
  var f=PlayerBotMoveController.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);
 }
 static void moverChecks(PlayerBotHeightTraversal.Plan plan)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  for(String state:new String[]{"despawned","dead","flying","casting","rooted"}) {
   var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.spawned=true;actor.movable=true;actor.world=actor.instance=1;
   switch(state){case "despawned"->actor.spawned=false;case "dead"->actor.dead=true;case "flying"->actor.flying=true;case "casting"->actor.casting=true;case "rooted"->actor.movable=false;}
   var mover=new PlayerBotMoveController(actor);
   check(!mover.startHeightTraversal(plan),"native "+state+" cannot launch without reaching World/tasks");
   check(!mover.traversingHeight() && !mover.isJumping(),"rejected traversal changes no movement state");
  }
  var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.spawned=true;actor.movable=true;actor.world=actor.instance=1;
  var mover=new PlayerBotMoveController(actor);field(mover,"heightPlan",plan);field(mover,"heightWorld",1);field(mover,"heightInstance",1);field(mover,"heightTime",.2);mover.setIsJumping(true);
  mover.abortMove();check(mover.traversingHeight() && mover.isJumping(),"midair order cannot freeze actor or skip landing");
  var flag=PlayerBotMoveController.class.getDeclaredField("interruptedHeight");flag.setAccessible(true);
  check(flag.getBoolean(mover),"midair stop queues horizontal interruption for gravity mover");
  actor.spawned=false;mover.abortMove();check(!mover.traversingHeight() && !mover.isJumping(),"despawn cleans transient airborne state without World/task mutation");
  for(boolean transfer:new boolean[]{true,false}) {
   actor.spawned=true;actor.dead=!transfer;actor.world=transfer?2:1;field(mover,"heightPlan",plan);mover.setIsJumping(true);mover.abortMove();
   check(!mover.traversingHeight() && !mover.isJumping(),"death or map transfer never retains old airborne path");
  }
 }
 static PlayerBotEngine.Action action(String name){return new PlayerBotEngine.Action(){public String name(){return name;}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}};}
 static void combatFollowChecks() {
  for(var order:PlayerBotRules.Order.values())check(PlayerBotHeightTraversal.followRequired(false,order,true,true,false,3,0,-4)
   ==(order==PlayerBotRules.Order.FOLLOW || order==PlayerBotRules.Order.PASSIVE),"height follows only controlled following orders");
  check(!PlayerBotHeightTraversal.followRequired(true,PlayerBotRules.Order.FOLLOW,true,true,false,3,0,-4),"independent world intent retains its own purpose");
  check(!PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,false,true,false,3,0,-4),"dead owner cannot request height regroup");
  check(!PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,true,false,false,3,0,-4),"rooted mover waits instead of jumping");
  check(!PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,true,true,true,3,0,-4),"explicit independent mission retains custody");
  check(!PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,true,true,false,3,0,1),"ordinary same-floor formations unchanged");
  check(!PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,true,true,false,50,0,-4),"distant relocation policy remains separate");
  check(!PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,true,true,false,Float.NaN,0,-4),"invalid leader sample rejected");
  check(PlayerBotHeightTraversal.followRequired(false,PlayerBotRules.Order.FOLLOW,true,true,false,3,0,4),"both upward and downward follow qualify");
  for(var state:new PlayerBotEngine.State[]{PlayerBotEngine.State.COMBAT,PlayerBotEngine.State.NON_COMBAT}) {
   var engine=new PlayerBotEngine();
   var movement=new PlayerBotEngine.Trigger(()->true,action("height follow"),()->PlayerBotHeightTraversal.FOLLOW_PRIORITY);
   var offense=new PlayerBotEngine.Trigger(()->true,action("ordinary offense"),()->PlayerBotEngine.HIGH);
   engine.tick(state,java.util.List.of(new PlayerBotEngine.Strategy("shared",java.util.List.of(offense,movement))),java.util.List.of(),8);
   check(engine.getLastAction().equals("height follow"),"height regroup outranks ordinary offense in "+state);
   var emergency=new PlayerBotEngine.Trigger(()->true,action("emergency heal"),()->PlayerBotEngine.EMERGENCY);
   engine.tick(state,java.util.List.of(new PlayerBotEngine.Strategy("shared",java.util.List.of(movement,emergency))),java.util.List.of(),8);
   check(engine.getLastAction().equals("emergency heal"),"native emergency support retains priority in "+state);
   var encounter=new PlayerBotEngine.Trigger(()->true,action("encounter safety"),()->PlayerBotEngine.ENCOUNTER);
   engine.tick(state,java.util.List.of(new PlayerBotEngine.Strategy("shared",java.util.List.of(movement,encounter))),java.util.List.of(),8);
   check(engine.getLastAction().equals("encounter safety"),"boss hazard remains ahead of height follow in "+state);
  }
 }
 public static void main(String[] args)throws Exception{
  var drop=level(-4);var p=plan(drop,-.5f,3,-4);
  check(p!=null,"four metre ledge gets physical traversal");
  check(PlayerBotGroundNavigation.walk(drop,1,-.5f,0,0,3,0,-4).x<=0,"ordinary walking still stops at cliff");
  check(p.at(0).distance(new Vector3f(-.5f,0,0))<.001 && p.at(p.seconds()).distance(new Vector3f(3,0,-4))<.001,"arc endpoints match native ray support");
  check(p.at(p.seconds()/4).z>0,"launch clears edge before descending");
  check(Math.hypot(p.endX()-p.x(),p.endY()-p.y())/p.seconds()<=6,"horizontal speed remains native bounded");
  check(p.fallDistance()>4 && p.fallDistance()<5,"fall damage includes actual launch apex");
  Vector3f previous=p.at(0);
  for(int i=1;i<=16;i++){var next=p.at(p.seconds()*i/16);check(PlayerBotHeightTraversal.segment(drop,1,previous,next),"each airborne step rechecks body/feet");previous=next;}
  check(PlayerBotHeightTraversal.landing(drop,1,p),"body footprint has supported landing");
  add(drop,wall(1,8));check(!PlayerBotHeightTraversal.segment(drop,1,p.at(0),p.at(p.seconds())) ,"new wall blocks an already planned segment");
  check(plan(drop,-.5f,3,-4)==null,"wall is never traversed by jump");
  var up=level(1.2f);add(up,wall(0,1.2f));var climb=plan(up,-.5f,2,1.2f);
  check(climb!=null && Math.abs(climb.endZ()-1.2f)<.001,"boulder step can be jumped up");
  check(plan(level(2.5f),-.5f,2,2.5f)==null,"unsupported tall climb rejected");
  check(plan(level(-20),-.5f,3,-20)==null,"unbounded cliff drop rejected");
  check(plan(level(-4),-.5f,3,0)==null,"another lower floor does not silently replace goal layer");
  check(plan(level(0),-3,3,0)==null,"supported flat movement stays walking");
  var gap=new GeoMap(919);add(gap,floor(-10,-.5f,0));add(gap,floor(.5f,10,0));
  check(plan(gap,-1,2,0)!=null,"small gap has valid arc and landing");
  check(plan(gap,-1,0,0)==null,"missing destination support rejected");
  var ceiling=level(-4);add(ceiling,floor(-10,10,1.7f));check(plan(ceiling,-.5f,3,-4)==null,"low ceiling blocks full body arc");
  check(PlayerBotHeightTraversal.plan(null,1,0,0,0,2,0,-4,6)==null,"missing map rejected");
  check(PlayerBotHeightTraversal.plan(level(-4),1,Float.NaN,0,0,2,0,-4,6)==null,"nonfinite origin rejected");
  check(PlayerBotHeightTraversal.plan(level(-4),1,-.5f,0,0,2,0,-4,0)==null,"no movement speed cannot jump");
  check(PlayerBotHeightTraversal.plan(level(-4),1,-.5f,0,0,9,0,-4,6)==null,"long airborne shortcut rejected");
  check(PlayerBotHeightTraversal.plan(level(-4),1,-.5f,0,5,2,0,-4,6)==null,"unsupported starting actor cannot launch");
  var fall=PlayerBotHeightTraversal.fall(level(-4),1,3,0,0);
  check(fall!=null && fall.launch()==0 && fall.endX()==fall.x() && fall.endY()==fall.y(),"midair stop drops vertically without teleport");
  check(fall.at(fall.seconds()/2).z<0 && Math.abs(fall.at(fall.seconds()).z+4)<.001,"gravity advances and reaches ground");
  var none=new GeoMap(920);check(PlayerBotHeightTraversal.fall(none,1,3,0,0)==null,"no floor is invented on interrupted fall");
  var dynamic=level(-4);var before=plan(dynamic,-.5f,3,-4);var door=new DespawnableNode();door.id=4;door.type=DespawnableType.DOOR_STATE1;
  door.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());door.attachChild(new Geometry("door",wall(1,8)));door.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));door.updateModelBound();dynamic.attachChild(door);dynamic.updateModelBound();
  door.setActive(1,true);door.setActive(2,false);
  check(plan(dynamic,-.5f,3,-4)==null,"closed dynamic door blocks jump");
  check(PlayerBotHeightTraversal.plan(dynamic,2,-.5f,0,0,3,0,-4,6)!=null,"other instance inactive door leaves physical arc available");
  check(!PlayerBotHeightTraversal.segment(dynamic,1,before.at(0),before.at(before.seconds())),"in-flight closed door is rechecked");
  var staleLanding=level(-4);var stale=plan(staleLanding,-.5f,3,-4);add(staleLanding,floor(1,5,-3));
  check(!PlayerBotHeightTraversal.landing(staleLanding,1,stale),"changed landing layer rejected");
  check(PlayerBotHeightTraversal.plan(level(-4),1,-.5f,0,0,3,0,-4,1)==null,"slow actor cannot inherit fast traversal");
  check(!PlayerBotHeightTraversal.segment(null,1,new Vector3f(),new Vector3f(1,1,1)),"no collision map cannot authorize flight");
  check(!new PlayerBotHeightTraversal.Plan(0,0,0,1,0,0,0,1).valid(),"zero duration cannot reach mover");
  check(!new PlayerBotHeightTraversal.Plan(0,0,0,1,0,0,1,Float.NaN).valid(),"nonfinite launch cannot reach mover");
  check(!new PlayerBotHeightTraversal.Plan(0,0,0,1,0,0,1,1,1).valid(),"horizontal duration cannot be zero");
  check(PlayerBotHeightTraversal.plan(level(-4),1,-.5f,0,0,3,0,-4,6,0)==null,"expired shared search deadline cannot begin collision work");
  moverChecks(p);
  combatFollowChecks();
  System.out.println("OK: "+checks+" native jump/drop/height/body/door/gravity checks; no spawned actors/World/SQL/IDs");
 }
}
