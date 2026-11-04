package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.Race;

/** Road lanes stay beside the walked direction, swap sides on the way back and spread residents. */
public final class PlayerBotRoadLaneCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static boolean near(float a,float b){return Math.abs(a-b)<.01f;}
 public static void main(String[] args) {
  var points=List.of(new PlayerBotNavigation.Point(0,0,5),new PlayerBotNavigation.Point(10,0,5),new PlayerBotNavigation.Point(20,0,5),
   new PlayerBotNavigation.Point(10,0,5),new PlayerBotNavigation.Point(10,0,5));
  var route=new PlayerBotPatrolRoutes.Route("lane",1,Race.ELYOS,10,1,"lane",points,Map.of(),true);
  var forward=route.beside(1,1,2.5f);
  check(near(forward.x(),10) && near(forward.y(),2.5f) && near(forward.z(),5),"lane sits on the traveller's left");
  var back=route.beside(1,-1,2.5f);
  check(near(back.x(),10) && near(back.y(),-2.5f),"the return leg uses the opposite side of the road");
  var turn=route.beside(2,1,2.5f);
  check(near(turn.x(),20) && near(turn.y(),2.5f),"a doubled-back turn keeps the leg just walked");
  check(route.beside(4,1,2.5f).equals(points.get(4)),"a stationary end stays on the route");
  var lanes=new HashSet<Float>();int left=0;
  for(int id=100000;id<101000;id++) {
   float offset=PlayerBotPatrolRoutes.laneOffset(id);lanes.add(offset);if(offset>0)left++;
   check(Math.abs(offset)>=2 && Math.abs(offset)<=3,"lanes stay 2-3m from the road");
   check(offset==PlayerBotPatrolRoutes.laneOffset(id),"a resident keeps its lane");
  }
  check(lanes.size()==22,"every lane is used, found "+lanes.size());
  check(left>400 && left<600,"both sides are used, left "+left);
  System.out.println("Road lane checks passed: "+checks);
 }
 private PlayerBotRoadLaneCheck(){}
}
