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
  if(camp==null || !Float.isFinite(camp.x()) || !Float.isFinite(camp.y()) || !Float.isFinite(camp.z()))return null;
  // Stay in the original camp and altitude band. Every point needs a fresh
  // native supported floor, body clearance and walking exit; no negative cache.
  for(int radius:new int[]{2,4})for(int direction=0;direction<8;direction++) {
   if(exhausted.getAsBoolean())return null;
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
