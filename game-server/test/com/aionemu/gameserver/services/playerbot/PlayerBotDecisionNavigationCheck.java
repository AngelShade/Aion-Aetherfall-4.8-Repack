package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Actual Session/Engine navigation custody; no World, native IDs, casts, SQL or tasks. */
public final class PlayerBotDecisionNavigationCheck {
 static int checks;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String name,Object value)throws Exception{PlayerBotQuestObjectivesCheck.field(o,name,value);}
 static Action action(boolean useful,boolean executed){return new Action(){public String name(){return "current native travel";}public boolean isUseful(){return useful;}public boolean isPossible(){return true;}public boolean execute(){return executed;}};}
 static boolean run(PlayerBotSession session,PlayerBotStrategyComposition.Plan plan)throws Exception {
  try{var m=PlayerBotSession.class.getDeclaredMethod("runDecision",PlayerBotStrategyComposition.Plan.class);m.setAccessible(true);return (boolean)m.invoke(session,plan);}
  catch(NoSuchMethodException original){return plan.tick((PlayerBotEngine)field(session,"engine"),64);} // Exact installed055 decision seam.
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var actor=(PlayerBotRouteContinuationCheck.RouteActor)u.allocateInstance(PlayerBotRouteContinuationCheck.RouteActor.class);actor.spawned=true;actor.movable=true;actor.world=197;actor.instance=1;
  actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);
  var navigation=new PlayerBotNavigation(actor);var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);
  set(session,"navigation",navigation);set(session,"engine",new PlayerBotEngine());
  var bf=PlayerBotNavigation.class.getDeclaredField("TERRAIN");bf.setAccessible(true);var budget=(PlayerBotNavigationBudget)bf.get(null);Object token=field(navigation,"searchToken");
  try {
   for(int kind=0;kind<3;kind++) {
    var goal=new PlayerBotNavigation.Point(30,10,0);set(navigation,"routeSearch",new PlayerBotRouteSearch(new PlayerBotNavigation.Point(10,10,0),goal,160,4096,24));set(navigation,"routeGoal",goal);
    budget.request(token,PlayerBotNavigationBudget.Priority.WORLD);actor.move.moving=true;
    var plan=new PlayerBotStrategyComposition.Plan(State.NON_COMBAT);
    if(kind>0)plan.defaults("travel",action(kind==2,false),DEFAULT,State.NON_COMBAT);
    check(!run(session,plan),"Absent, no-longer-useful and failed actions produce an idle decision");
    check(!navigation.planningRoute(),"Idle Session releases a former quest path rather than falsely continuing it");
    check(!budget.waiting(token),"Unowned ground nomination cannot block fresh native work");
    check(!actor.move.moving,"Former physical movement stops when no admitted action owns it");
   }
   var goal=new PlayerBotNavigation.Point(30,10,0);set(navigation,"routeSearch",new PlayerBotRouteSearch(new PlayerBotNavigation.Point(10,10,0),goal,160,4096,24));set(navigation,"routeGoal",goal);budget.request(token,PlayerBotNavigationBudget.Priority.WORLD);
   var plan=new PlayerBotStrategyComposition.Plan(State.NON_COMBAT);plan.defaults("travel",action(true,true),DEFAULT,State.NON_COMBAT);
   check(run(session,plan),"Accepted asynchronous movement remains an executed decision");
   check(navigation.planningRoute() && budget.waiting(token),"Genuine owned pending navigation retains queue age and search across decisions");
  }finally{navigation.stop();}
  System.out.println("OK: "+checks+" actual idle navigation release and accepted continuation custody checks");
 }
 private PlayerBotDecisionNavigationCheck(){}
}
