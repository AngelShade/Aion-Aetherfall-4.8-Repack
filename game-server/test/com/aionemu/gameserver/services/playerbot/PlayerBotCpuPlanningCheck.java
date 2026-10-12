package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.geoEngine.collision.*;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Real native terrain/cached field locations/visitor custody; no World, IDs, SQL or tasks. */
public final class PlayerBotCpuPlanningCheck {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Parent {private int value=7;private String inherited="native";}
 static class Child extends Parent {private int value=9;}
 static void fields()throws Exception {
  var a=new Child();var b=new Child();
  check(PlayerBotQuestConversations.field(null,"value")==null,"Null native metadata remains absent");
  check(PlayerBotQuestConversations.number(a,"value")==9,"Nearest private shadow wins");
  check(PlayerBotQuestConversations.field(a,"inherited").equals("native"),"Inherited private field remains readable");
  a.value=17;check(PlayerBotQuestConversations.number(a,"value")==17,"Cached location reads changed native value");
  check(PlayerBotQuestConversations.number(b,"value")==9,"Location caching never retains another actor's value");
  boolean missing=true;for(int i=0;i<10000;i++)missing&=PlayerBotQuestConversations.field(a,"missing")==null;
  check(missing,"Repeated missing native field reads remain absent");
  var f=PlayerBotQuestConversations.class.getDeclaredField("FIELDS");f.setAccessible(true);
  @SuppressWarnings("unchecked") var cache=(ClassValue<Map<String,Optional<java.lang.reflect.Field>>>)f.get(null);
  check(cache.get(Child.class).size()==3 && cache.get(Child.class).get("missing").isEmpty(),"Positive and negative locations are bounded by class/field, not read count");
  var effect=new com.aionemu.gameserver.skillengine.effect.HostileUpEffect();
  check(PlayerBotQuestConversations.field(effect,"hopType")==null,"Actual inherited native effect metadata is read without guessing");
  PlayerBotQuestObjectivesCheck.field(effect,"hopB",37);
  check(PlayerBotQuestConversations.number(effect,"hopB")==37,"Native inherited hate coefficient remains fresh");
 }
 static PlayerBotWorldActivities.Goal goal(int q,PlayerBotQuestRoutes.Kind kind,int npc,float x,double distance){
  return new PlayerBotWorldActivities.Goal(q,kind,new PlayerBotWorldTravel.Place(197,npc,x,10,0),distance);
 }
 static void visitors()throws Exception {
  var v=new PlayerBotWorldActivities.Visitors();var random=new Random(23);
  var hunt=goal(0,PlayerBotQuestRoutes.Kind.HUNT,1,10,1);var other=goal(0,PlayerBotQuestRoutes.Kind.HUNT,2,10,1);
  for(int i=0;i<100;i++)v.replace(null,hunt);
  check(v.choose(List.of(hunt,other),random).equals(other),"Destination visitor cap excludes all points of a full NPC destination");
  v.replace(hunt,null);check(v.choose(List.of(hunt),random).equals(hunt),"Departing visitor releases native destination immediately");
  for(int i=0;i<99;i++)v.replace(hunt,null);
  var object=goal(101,PlayerBotQuestRoutes.Kind.OBJECT,3,11,1);var second=goal(101,PlayerBotQuestRoutes.Kind.OBJECT,3,12,1);
  v.replace(null,object);check(v.choose(List.of(object,second),random).equals(second),"Quest object point remains exclusive while sibling points remain eligible");
  v.replace(object,second);check(v.choose(List.of(object),random).equals(object),"Replaced goal releases old point exactly once");
  v.replace(second,null);
  var reward=goal(101,PlayerBotQuestRoutes.Kind.REWARD,4,13,1000);var quest=goal(101,PlayerBotQuestRoutes.Kind.HUNT,5,14,1);
  check(v.choose(List.of(hunt,quest,reward),random).equals(reward),"Earned reward priority survives uniform point selection");
  check(v.choose(List.of(hunt,quest),random).equals(quest),"Quest objectives remain above independent grinding");
  check(v.choose(List.of(goal(0,PlayerBotQuestRoutes.Kind.HUNT,7,10,Double.NaN),goal(0,PlayerBotQuestRoutes.Kind.HUNT,8,10,-1)),random)==null,"Malformed/unreachable distance remains excluded");
  int[] samples=new int[2];for(int i=0;i<10000;i++)samples[v.choose(List.of(hunt,other),random).equals(hunt)?0:1]++;
  check(samples[0]>4500 && samples[1]>4500,"Eligible points retain uniform random spread");
  var dialog=goal(0,PlayerBotQuestRoutes.Kind.CONVERSATION,9,10,1);for(int i=0;i<15;i++)v.replace(null,dialog);
  check(v.choose(List.of(dialog),random)==null,"Native conversation visitor cap stays fifteen");
  for(int i=0;i<15;i++)v.replace(dialog,null);
  for(String name:List.of("totals","points")){var f=v.getClass().getDeclaredField(name);f.setAccessible(true);check(((Map<?,?>)f.get(v)).isEmpty(),"Completed/cancelled claims leave no occupancy residue");}
 }
 static class Actor extends PlayerBotRouteContinuationCheck.RouteActor {
  com.aionemu.gameserver.controllers.movement.PlayerMoveController actualMove;
  @Override public com.aionemu.gameserver.controllers.movement.PlayerMoveController getMoveController(){return actualMove==null?super.getMoveController():actualMove;}
  @Override public byte getHeading(){return 0;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public boolean isTransformed(){return false;}
  @Override public com.aionemu.gameserver.model.templates.spawns.SpawnTemplate getSpawn(){return null;}
 }
 static class Mesh extends GeoMap {
  int ground,sight;boolean blockedSight,delayOnce;
  Mesh(){super(197);}
  @Override public CollisionResults getPlayerBotCollisions(Vector3f p,float x,float y,float z,int instance,byte flags,IgnoreProperties ignore){ground++;if(delayOnce){delayOnce=false;java.util.concurrent.locks.LockSupport.parkNanos(4_000_000);}return super.getPlayerBotCollisions(p,x,y,z,instance,flags,ignore);}
  @Override public boolean canSee(float x,float y,float z,float tx,float ty,float tz,int instance,IgnoreProperties ignore){sight++;return !blockedSight || x>35;}
 }
 static Actor actor(Unsafe u,float x)throws Exception {
  var a=(Actor)u.allocateInstance(Actor.class);a.spawned=true;a.movable=true;a.world=197;a.instance=1;a.x=x;a.y=10;
  a.move=new PlayerBotRouteContinuationCheck.RouteMove(a);a.stats=(PlayerBotRouteContinuationCheck.RouteStats)u.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);return a;
 }
 static void navigation()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();f=geo.getClass().getDeclaredField("geoMaps");f.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(geo);var mesh=new Mesh();
  PlayerBotRouteContinuationCheck.add(mesh,PlayerBotRouteContinuationCheck.floor(0,0,160,160,0));var old=maps.put(197,mesh);
  var a=actor(u,10);var nav=new PlayerBotNavigation(a);
  f=PlayerBotNavigation.class.getDeclaredField("TERRAIN");f.setAccessible(true);var quota=(PlayerBotNavigationBudget)f.get(null);
  var credit=quota.getClass().getDeclaredField("credit");credit.setAccessible(true);double oldCredit=credit.getDouble(quota);
  boolean oldSight=com.aionemu.gameserver.configs.main.GeoDataConfig.CANSEE_ENABLE;
  try {
   credit.setDouble(quota,-1e12);check(nav.move(20,10,0) && mesh.ground==0,"Ordinary local steering cannot bypass exhausted terrain quota");
   check(nav.status().contains("budget"),"Pending local movement stays visible as terrain work");
   credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(nav.move(20,10,0) && mesh.ground>0 && a.move.moving,"Granted local turn proves native ground before publishing movement");
   int probes=mesh.ground;PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(nav.move(20,10,0) && mesh.ground==probes,"Unchanged active commitment never repeats whole ground planning");
   nav.stop();PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);credit.setDouble(quota,-1e12);check(nav.move(30,10,0),"Another local commitment can wait without being abandoned");
   nav.stop();check(!nav.status().contains("budget"),"Explicit stop clears local planning ticket");
   mesh.blockedSight=true;com.aionemu.gameserver.configs.main.GeoDataConfig.CANSEE_ENABLE=true;
   var target=actor(u,40);credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   check(nav.approach(target,10),"Occluded ranged target creates a retained firing intent");
   var selected=PlayerBotNavigation.class.getDeclaredField("approachDestination");selected.setAccessible(true);
   // A single native probe can overrun its slice; its measured debt cannot
   // force the selected firing point to be discarded before movement admission.
   for(int i=0;selected.get(nav)==null && i<20;i++){credit.setDouble(quota,6e6);nav.approach(target,10);}
   check(selected.get(nav)!=null,"Measured firing slices eventually retain a native-supported destination");
   credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.approach(target,10);probes=mesh.ground;int sights=mesh.sight;
   credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.approach(target,10);
   check(mesh.ground==probes && mesh.sight>sights,"Committed firing movement avoids repeated shell walks but retains fresh native sight");
   nav.stop();credit.setDouble(quota,-1e12);nav.approach(target,10);probes=mesh.ground;
   check(nav.status().contains("budget") && mesh.ground==probes,"Firing shell cannot bypass measured planning budget");
   var approachTicket=PlayerBotNavigation.class.getDeclaredField("approachSearchToken");approachTicket.setAccessible(true);
   Object firing=approachTicket.get(nav);check(quota.waiting(firing),"Denied firing branch owns its actual queue ticket");
   PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(10,20,0);
   check(!quota.waiting(firing),"Safety/order successor cancels firing ticket before waiting on another branch");
   nav.stop();nav.approach(target,10);probes=mesh.ground;
   a.casting=true;check(!nav.approach(target,10) && mesh.ground==probes,"Native committed cast never spends speculative terrain work");a.casting=false;
   nav.stop();check(!nav.waitingForSearchTurn(),"Explicit stop releases all new planning nominations");
   a.actualMove=new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController(a);
   for(var entry:Map.of("routeWorld",197,"routeInstance",1,"localLimit",7,"localCursor",7).entrySet())PlayerBotQuestObjectivesCheck.field(nav,entry.getKey(),entry.getValue());
   PlayerBotQuestObjectivesCheck.field(nav,"routeGoal",new Point(20,10,0));PlayerBotQuestObjectivesCheck.field(nav,"localOrigin",new Point(10,10,0));
   PlayerBotQuestObjectivesCheck.field(nav,"localGoal",new Point(20,10,0));PlayerBotQuestObjectivesCheck.field(nav,"localHazards",List.of());
   PlayerBotQuestObjectivesCheck.field(nav,"localBest",null);PlayerBotQuestObjectivesCheck.field(nav,"lastProgress",System.currentTimeMillis()-5000);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);
   credit.setDouble(quota,-1e12);check(nav.move(20,10,0),"Denied height turn remains an admitted movement commitment");
   var heightTicket=PlayerBotNavigation.class.getDeclaredField("heightSearchToken");heightTicket.setAccessible(true);Object height=heightTicket.get(nav);
   check(quota.waiting(height),"Route-origin successor cannot discard a valid denied height ticket");
   PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(20,10,0);
   check(quota.waiting(height),"Repeated waiting preserves the original height queue age");
   var cursor=PlayerBotNavigation.class.getDeclaredField("heightCursor");cursor.setAccessible(true);mesh.delayOnce=true;
   // Cold native stat/geometry initialization may consume a first slice before
   // any candidate. Retry actual turns without resetting its retained cursor.
   for(int turn=0;cursor.getInt(nav)==0 && turn<8;turn++){credit.setDouble(quota,6e6);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(20,10,0);}
   int next=cursor.getInt(nav);
   check(next>0 && next<25 && quota.waiting(height),"Actual native probe overrun yields a retained height candidate cursor: "+next+" queued="+quota.waiting(height)+" delay="+mesh.delayOnce+" status="+nav.status());
   credit.setDouble(quota,-1e12);PlayerBotQuestObjectivesCheck.field(nav,"nextMove",0L);nav.move(20,10,0);
   check(cursor.getInt(nav)==next && quota.waiting(height),"Denied successor turn cannot restart or silently complete partial height work");
   nav.stop();check(!quota.waiting(height),"Explicit stop releases retained native height work");
   var type=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var state=constructor.newInstance();
   var goalWriter=PlayerBotWorldActivities.class.getDeclaredMethod("goal",type,PlayerBotWorldActivities.Goal.class);goalWriter.setAccessible(true);
   var statesField=PlayerBotWorldActivities.class.getDeclaredField("STATES");statesField.setAccessible(true);
   @SuppressWarnings("unchecked") var states=(Map<Integer,Object>)statesField.get(null);a.id=870041;states.put(a.id,state);
   var visitorsField=PlayerBotWorldActivities.class.getDeclaredField("VISITORS");visitorsField.setAccessible(true);var registry=visitorsField.get(null);
   var totalsField=registry.getClass().getDeclaredField("totals");totalsField.setAccessible(true);var totals=(Map<?,?>)totalsField.get(registry);
   goalWriter.invoke(null,state,goal(0,PlayerBotQuestRoutes.Kind.HUNT,101,10,1));
   check(totals.size()==1 && totals.values().iterator().next().equals(1),"Production goal commitment adds its own visitor once");
   PlayerBotWorldActivities.close(a);check(totals.isEmpty() && !states.containsKey(a.id),"Production despawn releases destination claim and actor state");
   goalWriter.invoke(null,state,goal(0,PlayerBotQuestRoutes.Kind.HUNT,102,10,1));
   check(totals.isEmpty(),"Late callback cannot reclaim destination after production close");
  }finally{nav.stop();credit.setDouble(quota,oldCredit);com.aionemu.gameserver.configs.main.GeoDataConfig.CANSEE_ENABLE=oldSight;if(old==null)maps.remove(197);else maps.put(197,old);}
 }
 public static void main(String[]args)throws Exception{if(args.length>0){navigation();return;}fields();visitors();navigation();System.out.println("OK: "+checks+" CPU planning, inherited fresh-field, visitor custody and native terrain checks; no World/IDs/SQL/tasks");}
 private PlayerBotCpuPlanningCheck(){}
}
