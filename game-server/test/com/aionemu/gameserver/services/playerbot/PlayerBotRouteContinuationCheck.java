package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.movement.NpcGroundNavigation;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.geoEngine.scene.DespawnableNode.DespawnableType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Actual native meshes and production retained search/cadence; no World, native IDs or SQL. */
public final class PlayerBotRouteContinuationCheck {
 static int checks;
 static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);checks++;}
 static Mesh mesh(float[] vertices,short[] triangles,byte flags){var m=new Mesh();m.setVertices(FloatBuffer.wrap(vertices));m.setIndices(ShortBuffer.wrap(triangles));m.setCollisionIntentions(flags);m.updateBound();return m;}
 static Mesh floor(float x0,float y0,float x1,float y1,float z){return mesh(new float[]{x0,y0,z,x1,y0,z,x1,y1,z,x0,y1,z},new short[]{0,1,2,0,2,3},CollisionIntention.PHYSICAL.getId());}
 static Mesh wall(float x0,float y0,float x1,float y1){return mesh(new float[]{x0,y0,0,x1,y1,0,x1,y1,5,x0,y0,5},new short[]{0,1,2,0,2,3},CollisionIntention.PHYSICAL.getId());}
 static void add(GeoMap map,Mesh mesh){var g=new Geometry("native fixture",mesh);g.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));g.updateModelBound();map.attachChild(g);map.updateModelBound();}
 static GeoMap level(){var m=new GeoMap(197);add(m,floor(0,0,160,160,0));return m;}
 static PlayerBotPathfinder.Ground nativeGround(GeoMap m,Point goal){return (from,x,y)->{var p=PlayerBotGroundNavigation.walk(m,1,from.x(),from.y(),from.z(),x,y,goal.z());return new Point(p.x,p.y,p.z);};}
 static List<Point> solve(PlayerBotRouteSearch search,PlayerBotPathfinder.Ground ground,int slice,int limit) {
  int passes=0;
  while(search.pending() && passes++<limit){int[] calls={0};search.advance((from,x,y)->{calls[0]++;return ground.step(from,x,y);},slice,()->false);check(calls[0]<=slice,"native probe count remains bounded per search turn");check(search.retained()<=4096,"retained state stays bounded");}
  check(!search.pending(),"bounded search terminates within fixture limit");return search.route();
 }
 static void validate(List<Point> route,Point start,Point goal,PlayerBotPathfinder.Ground ground){check(!route.isEmpty(),"supported route exists");Point previous=start;for(Point p:route){Point actual=ground.step(previous,p.x(),p.y());check(actual!=null && Math.hypot(actual.x()-p.x(),actual.y()-p.y())<.15 && Math.abs(actual.z()-p.z())<.1,"each retained edge has actual native support and body clearance");previous=p;}check(previous.equals(goal),"native goal layer reached");}
 static class Control extends PlayerController {boolean combat;@Override public boolean isInCombat(){return combat;}}
 static class Actor extends PlayerBotClericCheck.Actor {
  Control control;boolean resting;
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isInState(CreatureState state){return state==CreatureState.RESTING && resting;}
  @Override public PlayerController getController(){return control;}
 }
 static class RouteMove extends PlayerBotSorcererCheck.Move {
  RouteMove(com.aionemu.gameserver.model.gameobjects.player.Player bot){super(bot);}
  @Override public void abortMove(){moving=false;}
  @Override public void startMovingToDestination(){moving=true;}
 }
 static class RouteStats extends PlayerBotClericCheck.Stats {
  @Override public float getMovementSpeedFloat(){return 6;}
 }
 static class RouteActor extends Actor {
  float x,y,z;int world=197,instance=1;boolean movable=true,casting,dead;
  @Override public float getX(){return x;}@Override public float getY(){return y;}@Override public float getZ(){return z;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public boolean canPerformMove(){return movable;}@Override public boolean isCasting(){return casting;}@Override public boolean isDead(){return dead;}
 }
 static Object value(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static class PatrolGroup extends com.aionemu.gameserver.spawnengine.WalkerGroup {
  com.aionemu.gameserver.spawnengine.ClusteredNpc cluster;int step;
  PatrolGroup(){super(List.of());} // Allocated without constructors; no native actor or group registration.
  @Override public com.aionemu.gameserver.spawnengine.ClusteredNpc getClusterData(com.aionemu.gameserver.model.gameobjects.Npc npc){return cluster;}
  @Override public void setStep(com.aionemu.gameserver.model.gameobjects.Npc npc,int index){step=index;}
 }
 static class PatrolNpc extends com.aionemu.gameserver.model.gameobjects.Npc {
  com.aionemu.gameserver.controllers.movement.NpcMoveController mover;
  com.aionemu.gameserver.model.templates.npc.NpcTemplate template;
  float x=10,y=10,z;
  PatrolNpc(){super(null,null,null);} // Inert allocation bypasses native ID constructor.
  @Override public int getWorldId(){return 197;}@Override public int getInstanceId(){return 1;}
  @Override public int getObjectId(){return 1900000000;}
  @Override public float getX(){return x;}@Override public float getY(){return y;}@Override public float getZ(){return z;}
  @Override public byte getHeading(){return 0;}
  @Override public boolean isSpawned(){return true;} // Movement fixtures represent an admitted actor without constructing World.
  @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate(){return template;}
  @Override public com.aionemu.gameserver.controllers.movement.NpcMoveController getMoveController(){return mover;}
 }
 static void patrolPacket()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var npc=(PatrolNpc)unsafe.allocateInstance(PatrolNpc.class);npc.x=10.4f;npc.y=10;
  npc.template=new com.aionemu.gameserver.model.templates.npc.NpcTemplate();PlayerBotQuestObjectivesCheck.field(npc.template,"height",1.8f);
  npc.mover=new com.aionemu.gameserver.controllers.movement.NpcMoveController(npc);
  PlayerBotQuestObjectivesCheck.field(npc.mover,"started",new java.util.concurrent.atomic.AtomicBoolean(true));
  PlayerBotQuestObjectivesCheck.field(npc.mover,"targetDestZ",8f);
  var publish=npc.mover.getClass().getDeclaredMethod("publishPatrolTarget",Vector3f.class,float.class,float.class,float.class);publish.setAccessible(true);
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();var mapsField=geo.getClass().getDeclaredField("geoMaps");mapsField.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)mapsField.get(geo);var previous=maps.put(197,level());
  try {
   check(npc.mover.getTargetZ2()==8,"prior native packet destination can still advertise airborne authored height");
   publish.invoke(npc.mover,new Vector3f(npc.x,npc.y,npc.z),30f,10f,2f);
   check(Math.abs(npc.mover.getTargetX2()-12.4)<.01 && npc.mover.getTargetZ2()==0,"actual mover advertises bounded supported client horizon at native speed");
   var packet=new com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE(npc,com.aionemu.gameserver.controllers.movement.MovementMask.NPC_STARTMOVE);
   var bytes=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);packet.setBuf(bytes);
   var write=packet.getClass().getDeclaredMethod("writeImpl",com.aionemu.gameserver.network.aion.AionConnection.class);write.setAccessible(true);write.invoke(packet,new Object[]{null});
   check(bytes.position()==30,"actual native movement body contains position and absolute destination");
   check(Math.abs(bytes.getFloat(18)-12.4)<.01 && bytes.getFloat(22)==10 && bytes.getFloat(26)==0,"actual SM_MOVE wire destination preserves supported horizon instead of authored airborne Z");
   add(maps.get(197),wall(11,0,11,20));publish.invoke(npc.mover,new Vector3f(npc.x,npc.y,npc.z),30f,10f,2f);
   check(npc.mover.getTargetX2()<11 && npc.mover.getTargetZ2()==0,"client horizon cannot predict through live native wall");
   var ramp=new GeoMap(197);add(ramp,mesh(new float[]{0,0,0,40,0,16,40,20,16,0,20,0},new short[]{0,1,2,0,2,3},CollisionIntention.PHYSICAL.getId()));maps.put(197,ramp);
   publish.invoke(npc.mover,new Vector3f(10,10,4),30f,10f,2f);
   check(Math.abs(npc.mover.getTargetZ2()-4.8)<.01,"client horizon follows actual supported slope rather than flattening it");
   maps.put(197,level());publish.invoke(npc.mover,new Vector3f(10,10,0),10f,10f,2f);
   check(npc.mover.getTargetX2()==10 && npc.mover.getTargetZ2()==0,"same-XY native client endpoint remains on supported floor");
  }finally{if(previous==null)maps.remove(197);else maps.put(197,previous);}
 }
 static void groupPatrol()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var npc=(com.aionemu.gameserver.model.gameobjects.Npc)unsafe.allocateInstance(com.aionemu.gameserver.model.gameobjects.Npc.class);
  var group=(PatrolGroup)unsafe.allocateInstance(PatrolGroup.class);
  group.cluster=(com.aionemu.gameserver.spawnengine.ClusteredNpc)unsafe.allocateInstance(com.aionemu.gameserver.spawnengine.ClusteredNpc.class);
  npc.setWalkerGroup(group);
  var mover=new com.aionemu.gameserver.controllers.movement.NpcMoveController(npc);
  var before=new com.aionemu.gameserver.model.templates.walker.RouteStep(10,10,3,0);
  var next=new com.aionemu.gameserver.model.templates.walker.RouteStep(20,10,8,0);next.setStepIndex(7);
  PlayerBotQuestObjectivesCheck.field(mover,"currentStep",before);mover.setRouteStep(next);
  check((float)value(mover,"pointZ")==3 && (float)value(mover,"pointX")==10,"restored native group uses original route-origin formation and height");
  check(group.step==7 && mover.getCurrentStep()==next,"group route index and authored next waypoint remain native");
 }
 static void navigation()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var bot=(RouteActor)unsafe.allocateInstance(RouteActor.class);bot.spawned=true;bot.movable=true;bot.world=197;bot.instance=1;bot.x=10;bot.y=10;
  bot.move=new RouteMove(bot);bot.stats=(RouteStats)unsafe.allocateInstance(RouteStats.class);
  var nav=new PlayerBotNavigation(bot);var token=value(nav,"searchToken");
  var quotaField=PlayerBotNavigation.class.getDeclaredField("TERRAIN");quotaField.setAccessible(true);var quota=(PlayerBotNavigationBudget)quotaField.get(null);
  Object seed=new Object();long now=System.currentTimeMillis();quota.request(token,PlayerBotNavigationBudget.Priority.WORLD);
  var search=new PlayerBotRouteSearch(new Point(10,10,0),new Point(15,10,0),160,4096);
  PlayerBotQuestObjectivesCheck.field(nav,"routeSearch",search);PlayerBotQuestObjectivesCheck.field(nav,"routeWorld",197);PlayerBotQuestObjectivesCheck.field(nav,"routeInstance",1);
  PlayerBotQuestObjectivesCheck.field(nav,"searchProgress",now-60000);
  check(nav.progressing(now),"queued valid route retains world goal beyond distance-only30s timeout");
  bot.casting=true;check(!nav.waitingForSearchTurn() && !quota.waiting(token),"native cast cancels only its nominated terrain ticket");bot.casting=false;
  quota.request(token,PlayerBotNavigationBudget.Priority.WORLD);nav.stop();check(!nav.planningRoute() && !quota.waiting(token),"explicit stop revokes retained route and search ticket");
  var successor=new PlayerBotRouteSearch(new Point(10,10,0),new Point(20,10,0),160,4096);PlayerBotQuestObjectivesCheck.field(nav,"routeSearch",successor);
  check(nav.planningRoute() && value(nav,"routeSearch")==successor,"cancelled predecessor does not own successor intent");
  PlayerBotQuestObjectivesCheck.field(nav,"searchProgress",now-60000);
  check(!nav.progressing(now),"unqueued route without probe progress cannot indefinitely mask real stall");
  // Map changes must revoke the old plan before even the nextMove time gate.
  PlayerBotQuestObjectivesCheck.field(nav,"nextMove",now+60000);bot.world=198;
  check(!nav.move(20,10,0) && !nav.planningRoute(),"map transfer clears old route before native local movement deadline");
  bot.world=197;PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.stop();
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();var mapsField=geo.getClass().getDeclaredField("geoMaps");mapsField.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)mapsField.get(geo);var previous=maps.put(197,level());
  try {
   @SuppressWarnings("unchecked") var route=(Deque<Point>)value(nav,"route");route.add(new Point(6,10,0));
   PlayerBotQuestObjectivesCheck.field(nav,"routeGoal",new Point(20,10,0));PlayerBotQuestObjectivesCheck.field(nav,"routeWorld",197);PlayerBotQuestObjectivesCheck.field(nav,"routeInstance",1);
   PlayerBotQuestObjectivesCheck.field(nav,"lastProgress",System.currentTimeMillis());
   check(nav.move(20,10,0) && bot.move.getTargetX2()==6,"actual Navigation follows proved waypoint away from world goal");
   nav.hazards(List.of(new PlayerBotHazards.Area(7,10,0,1,0,0,0,0,3,PlayerBotHazards.Shape.CIRCLE)));
   PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(!nav.move(20,10,0),"new visible hazard revokes previously proved waypoint before consumption");
   nav.hazards(List.of());
   add(maps.get(197),wall(8,0,8,20));PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   // The mover's native segment revalidation is represented by the exact same production probe.
   var segment=geo.findGroundMovementCollision(197,1,10,10,0,6,10,0);
   check(segment.x>8,"live wall blocks a previously published retained edge");
   nav.stop();check(!bot.move.isInMove(),"stop clears native movement commitment through isolated mover");
  } finally {nav.stop();if(previous==null)maps.remove(197);else maps.put(197,previous);}
 }
 static void cadence()throws Exception {
  var cadence=new PlayerBotMovementCadence();
  check(!cadence.active(1000,false,false),"idle does not promote all world bots");
  check(cadence.active(1000,false,true),"moving commitment is responsive");
  check(cadence.active(1599,false,false) && !cadence.active(1600,false,false),"arrival handoff is bounded rather than perpetual urgent polling");
  check(!cadence.active(1001,true,true),"native rest retains its own cadence");
  check(PlayerBotMovementCadence.reaction(3000,true)==200 && PlayerBotMovementCadence.reaction(100,true)==100,"movement shortens idle without slowing controlled following");
  cadence.clear();check(!cadence.active(1002,false,false),"cancellation clears transient handoff");
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.spawned=true;bot.control=new Control();bot.move=new PlayerBotSorcererCheck.Move(bot);
  PlayerBotQuestObjectivesCheck.field(bot,"aggroList",new com.aionemu.gameserver.controllers.attack.AggroList(bot));
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  PlayerBotQuestObjectivesCheck.field(session,"owner",bot);PlayerBotQuestObjectivesCheck.field(session,"bot",bot);
  PlayerBotQuestObjectivesCheck.field(session,"engine",new PlayerBotEngine());PlayerBotQuestObjectivesCheck.field(session,"idleReaction",20);
  PlayerBotQuestObjectivesCheck.field(session,"movementCadence",new PlayerBotMovementCadence());
  check(session.reactionDelay()==2000,"actual Session idle policy unchanged");
  bot.move.moving=true;check(session.reactionDelay()==200 && session.activeDecision(),"actual independent movement joins active scheduling lane");
  bot.move.moving=false;check(session.reactionDelay()==200,"actual arrival retains a brief next-decision handoff");
  PlayerBotQuestObjectivesCheck.field(session,"movementCadence",new PlayerBotMovementCadence());bot.control.combat=true;
  check(session.reactionDelay()==500,"actual stationary combat reaction preserved");
  bot.control.combat=false;bot.resting=true;check(session.reactionDelay()==10000,"actual rest cadence preserved");
 }
 public static void main(String[] args)throws Exception {
  cadence();navigation();groupPatrol();
  Point start=new Point(20,80,0),goal=new Point(100,80,0);
  var wall=level();add(wall,wall(60,35,60,125));var ground=nativeGround(wall,goal);
  var oldRoute=PlayerBotPathfinder.find(start,goal,ground,128,()->false);
  check(oldRoute.isEmpty() || !oldRoute.getLast().equals(goal),"old24m/128node planner cannot complete long native wall detour");
  var retained=new PlayerBotRouteSearch(start,goal,160,4096);
  retained.advance(ground,128,()->true);check(retained.pending() && retained.expanded()==0,"expired slice retains untouched frontier");
  var route=solve(retained,ground,128,600);validate(route,start,goal,ground);
  check(route.stream().anyMatch(p->p.y()<35 || p.y()>125),"long wall detour leaves old local radius");
  var u=level();add(u,wall(35,65,35,95));add(u,wall(35,65,80,65));add(u,wall(35,95,80,95));
  start=new Point(50,80,0);goal=new Point(20,80,0);ground=nativeGround(u,goal);
  var away=solve(new PlayerBotRouteSearch(start,goal,160,4096),ground,64,1200);validate(away,start,goal,ground);
  check(away.stream().anyMatch(p->p.x()>80),"U-shaped route temporarily travels away from goal without cutting wall");
  Point origin=new Point(10,10,0),end=new Point(12,10,0);
  var cursor=new PlayerBotRouteSearch(origin,end,160,4096);int[] calls={0};
  cursor.advance((from,x,y)->{calls[0]++;return null;},128,()->calls[0]>=1);
  check(calls[0]==1 && cursor.pending(),"deadline retains a partly expanded node after one failed final edge");
  cursor.advance((from,x,y)->null,1,()->false);check(cursor.expanded()==1,"next slice resumes node instead of discarding useful expansion");
  var denied=new PlayerBotRouteSearch(origin,end,160,4096);check(solve(denied,(from,x,y)->null,2,20).isEmpty(),"missing support produces no fabricated route");
  for(Point bad:List.of(new Point(-1,10,0),new Point(160,10,0),new Point(Float.NaN,10,0))) {
   var bounds=new PlayerBotRouteSearch(origin,bad,160,4096);check(!bounds.pending() && bounds.route().isEmpty(),"native map edge or malformed goal rejected before probes");
  }
  var capacity=new PlayerBotRouteSearch(origin,new Point(140,140,0),160,8);solve(capacity,(from,x,y)->new Point(x,y,0),16,20);check(capacity.route().isEmpty(),"capacity exhaustion does not publish unproved shortcut");
  // A corridor between integer grid lines is unreachable to4m sampling, but the fine pass retains it.
  var narrowStart=new Point(10,10,0);var narrowGoal=new Point(20,10,0);
  PlayerBotPathfinder.Ground narrow=(from,x,y)->{
   if(x<10 || x>20 || y<10 || y>20)return null;
   if(from.x()==10 && x==10 || from.y()==13 && y==13 || from.x()==20 && x==20)return new Point(x,y,0);
   return null;
  };
  var narrowRoute=solve(new PlayerBotRouteSearch(narrowStart,narrowGoal,160,4096),narrow,16,200);validate(narrowRoute,narrowStart,narrowGoal,narrow);
  var plain=level();
  var p=NpcGroundNavigation.step(plain,1,new Vector3f(10,10,.7f),12,10,1.8f);
  check(p!=null && Math.abs(p.z)<.01 && p.x==12,"ground patrol removes unsupported interpolation height");
  var ramp=new GeoMap(73);add(ramp,mesh(new float[]{0,0,0,20,0,8,20,20,8,0,20,0},new short[]{0,1,2,0,2,3},CollisionIntention.PHYSICAL.getId()));
  p=NpcGroundNavigation.step(ramp,1,new Vector3f(4,10,1.6f),8,10,1.8f);check(p!=null && Math.abs(p.z-3.2)<.01,"patrol follows native sloped surface at intermediate point");
  check(NpcGroundNavigation.step(plain,1,new Vector3f(10,10,5),12,10,1.8f)==null,"distant unsupported starting layer is reported instead of snapped through geometry");
  var gap=new GeoMap(37);add(gap,floor(0,0,11,20,0));add(gap,floor(13,0,20,20,0));
  p=NpcGroundNavigation.step(gap,1,new Vector3f(10,10,0),14,10,1.8f);check(p!=null && p.x<=11,"ground patrol cannot float across missing support");
  add(plain,wall(11,0,11,20));p=NpcGroundNavigation.step(plain,1,new Vector3f(10,10,0),12,10,1.8f);check(p!=null && p.x<11,"patrol body collision blocks solid wall");
  var transparent=new GeoMap(83);var transparentFloor=floor(0,0,20,20,0);transparentFloor.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());add(transparent,transparentFloor);
  p=NpcGroundNavigation.step(transparent,1,new Vector3f(10,10,0),12,10,1.8f);check(p!=null && p.x==12,"physical transparent mesh remains valid patrol support");
  var layered=level();add(layered,floor(0,0,160,160,5));p=NpcGroundNavigation.step(layered,1,new Vector3f(10,10,5),12,10,1.8f);check(p!=null && p.z==5,"upper patrol layer is preserved above lower floor");
  var doorMap=level();var shut=new DespawnableNode();shut.id=1437;shut.type=DespawnableType.DOOR_STATE1;shut.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());
  shut.attachChild(new Geometry("door",wall(11,0,11,20)));shut.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));shut.updateModelBound();doorMap.attachChild(shut);doorMap.updateModelBound();
  shut.setActive(1,true);shut.setActive(2,false);
  p=NpcGroundNavigation.step(doorMap,1,new Vector3f(10,10,0),12,10,1.8f);check(p!=null && p.x<11,"closed patrol door remains native obstruction");
  p=NpcGroundNavigation.step(doorMap,2,new Vector3f(10,10,0),12,10,1.8f);check(p!=null && p.x==12,"another instance's open patrol door cannot inherit closed state");
  shut.setActive(1,false);p=NpcGroundNavigation.step(doorMap,1,new Vector3f(10,10,0),12,10,1.8f);check(p!=null && p.x==12,"patrol retries valid ground after native door opens");
  shut.setActive(1,true);p=NpcGroundNavigation.step(doorMap,1,new Vector3f(10,10,0),12,10,1.8f);check(p!=null && p.x<11,"reclosed door revokes a prior valid patrol segment");
  System.out.println("OK: "+checks+" retained native route and patrol checks");
 }
}
