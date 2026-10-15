package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.knownlist.KnownList;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Native no-floor search terminal result through actual world travel; no World, IDs, SQL or tasks. */
public final class PlayerBotFailedTravelCheck {
 static int checks;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static Object field(Object o,String name)throws Exception{return PlayerBotRouteContinuationCheck.value(o,name);}
 static void set(Object o,String name,Object value)throws Exception{PlayerBotQuestObjectivesCheck.field(o,name,value);}
 static class Actor extends PlayerBotTravelAdmissionCheck.Actor {
  KnownList seen;@Override public KnownList getKnownList(){return seen;}
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var actor=(Actor)u.allocateInstance(Actor.class);actor.id=1999999295;actor.spawned=actor.movable=true;actor.world=197;actor.instance=1;actor.x=actor.y=10;
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);actor.stats=(PlayerBotRouteContinuationCheck.RouteStats)u.allocateInstance(PlayerBotRouteContinuationCheck.RouteStats.class);
  actor.scene=(PlayerBotTravelAdmissionCheck.SceneShell)u.allocateInstance(PlayerBotTravelAdmissionCheck.SceneShell.class);actor.scene.map=(PlayerBotTravelAdmissionCheck.MapShell)u.allocateInstance(PlayerBotTravelAdmissionCheck.MapShell.class);
  actor.seen=new KnownList(actor){@Override public void forEachObject(java.util.function.Consumer<com.aionemu.gameserver.model.gameobjects.VisibleObject> c){}};
  set(actor,"aggroList",new AggroList(actor));
  var navigation=new PlayerBotNavigation(actor);var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);set(session,"owner",actor);set(session,"bot",actor);
  var type=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var state=ctor.newInstance();
  var writer=PlayerBotWorldActivities.class.getDeclaredMethod("goal",type,PlayerBotWorldActivities.Goal.class);writer.setAccessible(true);
  var goal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(197,101,50,10,0),40);
  writer.invoke(null,state,goal);set(state,"best",40d);set(state,"lastProgress",System.currentTimeMillis());set(state,"nextScan",System.currentTimeMillis()+60000);
  f=PlayerBotWorldActivities.class.getDeclaredField("STATES");f.setAccessible(true);@SuppressWarnings("unchecked") var states=(Map<Integer,Object>)f.get(null);states.put(actor.id,state);
  f=GeoService.class.getDeclaredField("geoMaps");f.setAccessible(true);@SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(GeoService.getInstance());var oldMap=maps.put(197,new GeoMap(197));
  f=PlayerBotNavigation.class.getDeclaredField("TERRAIN");f.setAccessible(true);var quota=(PlayerBotNavigationBudget)f.get(null);f=quota.getClass().getDeclaredField("credit");f.setAccessible(true);var credit=f;double oldCredit=credit.getDouble(quota);
  var ef=PlayerBotWorldTravel.class.getDeclaredField("edges");ef.setAccessible(true);Object oldEdges=ef.get(null);
  boolean prepared=com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED;
  set(navigation,"routeWorld",197);set(navigation,"routeInstance",1);set(navigation,"routeGoal",new Point(50,10,0));set(navigation,"routeSearch",new PlayerBotRouteSearch(new Point(10,10,0),new Point(50,10,0),160,4096,24));
  try {
   // This native no-floor fixture has no transport catalog or async resource loader.
   // Their admission/execution contracts are covered separately against native data.
   ef.set(null,List.of());com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED=false;
   credit.setDouble(quota,-1e12);
   check(PlayerBotWorldActivities.travel(session,navigation),"Denied native terrain work remains accepted pending travel");
   check(field(state,"goal")==goal && ((Map<?,?>)field(state,"excluded")).isEmpty(),"Queue wait retains destination and awards no failure");
   for(int i=0;i<100 && field(state,"goal")!=null;i++){credit.setDouble(quota,6e6);set(navigation,"nextMove",0L);PlayerBotWorldActivities.travel(session,navigation);}
   check(field(state,"goal")==null,"Completed native no-floor search releases the unavailable world destination; installed055 repeats it");
   check(((Map<?,?>)field(state,"excluded")).size()==1,"Only the actual failed goal receives existing bounded retry cooldown");
   check((long)field(state,"nextScan")==0,"Planner can choose another eligible activity on next callback");
   check(actor.x==10 && actor.y==10 && actor.z==0 && !actor.move.moving,"No floor, movement, teleport or arrival is invented");
   check(!navigation.planningRoute() && !navigation.waitingForTerrainWork(),"Failed native work releases all queue custody");
   check(navigation.status().equals("ready"),"Abandoned navigation no longer falsely reports planning");
   check((long)field(state,"noActivitySince")>0,"Terminal native failure begins existing no-activity episode");
   long episode=System.currentTimeMillis()-61000;set(state,"noActivitySince",episode);
   writer.invoke(null,state,goal);set(state,"best",Double.POSITIVE_INFINITY);set(state,"nextScan",Long.MAX_VALUE);
   PlayerBotWorldActivities.prepare(actor,navigation);
   check((long)field(state,"noActivitySince")==episode,"Selecting another unearned route does not erase failed-origin recovery");
   check(!PlayerBotWorldActivities.stranded(actor,System.currentTimeMillis()),"A live queued destination still prevents recovery relocation");
   check(PlayerBotWorldActivities.committedTravel(actor),"Actual selected journey owns optional pre-acceptance preparation");
   actor.x=13;PlayerBotWorldActivities.prepare(actor,navigation);
   check((long)field(state,"noActivitySince")==0,"Earned native distance improvement clears failed-origin episode");
   actor.x=10;set(state,"noActivitySince",episode);writer.invoke(null,state,null);
   check(PlayerBotWorldActivities.stranded(actor,System.currentTimeMillis()),"Repeated terminal failures permit existing guarded same-character recovery after one minute");
   check(!PlayerBotWorldActivities.committedTravel(actor),"Releasing journey returns optional services to ordinary eligibility");
   PlayerBotWorldActivities.interacting(actor);
   check((long)field(state,"noActivitySince")==0,"Genuine native local interaction clears recovery episode");
   ef.set(null,List.of());var other=new PlayerBotWorldTravel.Place(198,102,50,10,0);
   var pending=new PlayerBotRouteSearch(new Point(10,10,0),new Point(50,10,0),160,4096,24);set(navigation,"routeSearch",pending);set(navigation,"routeGoal",new Point(50,10,0));
   Object token=field(navigation,"searchToken");quota.request(token,PlayerBotNavigationBudget.Priority.WORLD);set(navigation,"routeFailure",true);
   check(!PlayerBotWorldTravel.move(actor,other,navigation),"Missing native map transport returns without a new terrain search");
   check(!navigation.routeFailed(),"Transport attempt cannot inherit a preceding action's terminal terrain result");
   check(navigation.planningRoute() && quota.waiting(token),"Clearing an outcome flag does not cancel an earned/owned navigation intent");
   var successor=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,other,Double.POSITIVE_INFINITY);writer.invoke(null,state,successor);
   set(state,"best",Double.POSITIVE_INFINITY);set(state,"lastProgress",System.currentTimeMillis());set(navigation,"routeFailure",true);
   check(!PlayerBotWorldActivities.travel(session,navigation) && field(state,"goal")==successor,"Unrelated transport failure is not another goal's terrain failure");
   check(((Map<?,?>)field(state,"excluded")).size()==1,"No inherited failure can blacklist an unrelated successor");
  } finally {com.aionemu.gameserver.configs.main.PlayerBotConfig.NAVMESH_ENABLED=prepared;ef.set(null,oldEdges);navigation.stop();PlayerBotWorldActivities.close(actor);credit.setDouble(quota,oldCredit);if(oldMap==null)maps.remove(197);else maps.put(197,oldMap);}
  System.out.println("OK: "+checks+" native failed travel cooldown, fresh reselection, queue custody and no-invented-progress checks");
 }
 private PlayerBotFailedTravelCheck(){}
}
