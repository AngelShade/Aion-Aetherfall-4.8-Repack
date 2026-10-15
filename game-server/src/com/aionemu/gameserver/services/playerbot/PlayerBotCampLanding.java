package com.aionemu.gameserver.services.playerbot;

import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Aion adaptation: a native NPC camp is an anchor, not proof of body-clear XY. */
final class PlayerBotCampLanding {
 interface Ground {
  float height(float x,float y,float sourceZ);
  boolean safe(Point point);
 }
 static Point nearby(Point camp,Ground ground,BooleanSupplier exhausted) {
  return nearby(camp,ground,new int[]{0},exhausted);
 }
 static Point nearby(Point camp,Ground ground,int[] cursor,BooleanSupplier exhausted) {
  if(camp==null || !Float.isFinite(camp.x()) || !Float.isFinite(camp.y()) || !Float.isFinite(camp.z()))return null;
  // Stay in the original camp and altitude band. Every point needs a fresh
  // native supported floor, body clearance and walking exit; no negative cache.
  while(cursor[0]<16) {
   if(exhausted.getAsBoolean())return null;
   int step=cursor[0]++,radius=step<8?2:4,direction=step%8;
   double angle=direction*Math.PI/4;
   float x=camp.x()+radius*(float)Math.cos(angle),y=camp.y()+radius*(float)Math.sin(angle);
   float z=ground.height(x,y,camp.z());
   if(!Float.isFinite(z) || Math.abs(z-camp.z())>4)continue;
   Point point=new Point(x,y,z);if(ground.safe(point))return point;
  }
  return null;
 }
 private PlayerBotCampLanding(){}
}
