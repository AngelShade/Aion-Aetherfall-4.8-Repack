package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.zone.ZoneLevelService;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Aion has drowning damage rather than WoW's swimming movement. Only water admission changes. */
public final class PlayerBotWater {
 private static final float MARGIN=.15f;
 static float boundary(float water,float mouth){return water-mouth+MARGIN;}
 static float floorLimit(float safe,float origin,float floor){return origin<safe?Math.min(safe,floor):safe;}
 static float minimumZ(Creature actor) {
  if(!(actor instanceof Player player))return Float.NEGATIVE_INFINITY;
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(actor.getWorldId());
  return map==null?Float.POSITIVE_INFINITY:boundary(map.getWaterLevel(),ZoneLevelService.mouthHeight(player));
 }
 static boolean endangered(Player player){return player.isSpawned() && !player.isDead() && player.getZ()<minimumZ(player);}
 static boolean safe(Creature actor,float z){return Float.isFinite(z) && z>=minimumZ(actor);}
 public static Vector3f walk(Creature actor,int world,int instance,float x,float y,float z,float tx,float ty,float tz) {
  // Already submerged actors may walk level or uphill toward shore, never deeper.
  return GeoService.getInstance().findGroundMovementCollision(world,instance,x,y,z,tx,ty,tz,minimumZ(actor));
 }
 static boolean polygon(float[] vertices,int[] indexes,int count,float minimum) {
  for(int i=0;i<count;i++)if(vertices[indexes[i]*3+1]>=minimum)return true;
  return false;
 }
 static final class Escape {
  private static final int DIRECTIONS=16;
  private static final int[] RADII={4,8,16,32,64,96,128};
  private int map,instance,cursor,rotation;
  private boolean active;
  private Point lastSafe,goal,progress;
  private long progressed,retry;
  void safe(Player bot,PlayerBotNavigation navigation) {
   if(active){navigation.stop();active=false;goal=null;cursor=0;}
   if(map!=bot.getWorldId() || instance!=bot.getInstanceId()){lastSafe=null;map=bot.getWorldId();instance=bot.getInstanceId();}
   lastSafe=new Point(bot.getX(),bot.getY(),bot.getZ());
  }
  void escape(Player bot,PlayerBotNavigation navigation) {
   long now=System.currentTimeMillis();
   if(active && (map!=bot.getWorldId() || instance!=bot.getInstanceId())){active=false;lastSafe=null;goal=null;}
   if(!active){
    navigation.stop();active=true;cursor=0;retry=0;progress=null;
    if(map==bot.getWorldId() && instance==bot.getInstanceId() && lastSafe!=null
     && Math.hypot(lastSafe.x()-bot.getX(),lastSafe.y()-bot.getY())<=128 && PlayerBotWater.safe(bot,lastSafe.z()))goal=lastSafe;
    else goal=null;
    map=bot.getWorldId();instance=bot.getInstanceId();progressed=now;
   }
   if(progress==null || Math.hypot(progress.x()-bot.getX(),progress.y()-bot.getY())>1){progress=new Point(bot.getX(),bot.getY(),bot.getZ());progressed=now;}
   if(goal!=null && now-progressed>4000){goal=null;navigation.stop();progressed=now;}
   if(goal==null && now>=retry)goal=navigation.waterShore(this);
   if(goal!=null && !navigation.waterMove(goal)){goal=null;navigation.stop();}
   if(cursor>=RADII.length*DIRECTIONS && goal==null){cursor=0;rotation=(rotation+3)%DIRECTIONS;retry=now+1000;}
  }
  Point seek(Player bot,long deadline) {
   float minimum=minimumZ(bot),water=DataManager.WORLD_MAPS_DATA.getTemplate(bot.getWorldId()).getWaterLevel();
   float size=bot.getWorldMapInstance().getParent().getWorldSize();
   for(int work=0;work<8 && cursor<RADII.length*DIRECTIONS && System.nanoTime()<deadline;work++) {
    int n=cursor++;double angle=((n%DIRECTIONS+rotation)%DIRECTIONS)*Math.PI*2/DIRECTIONS;
    float x=bot.getX()+(float)Math.cos(angle)*RADII[n/DIRECTIONS],y=bot.getY()+(float)Math.sin(angle)*RADII[n/DIRECTIONS];
    if(x<=0 || y<=0 || x>=size || y>=size)continue;
    float z=GeoService.getInstance().getZ(bot.getWorldId(),x,y,Math.max(water+8,bot.getZ()+8),minimum,bot.getInstanceId());
    if(!Float.isFinite(z) || z<minimum)continue;
    var end=walk(bot,bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),x,y,z);
    if(end==null || !Float.isFinite(end.z))continue;
    if(RADII[n/DIRECTIONS]<=32) {
     if(Math.hypot(end.x-x,end.y-y)<.3 && Math.abs(end.z-z)<.75)return new Point(x,y,z);
    }else if(Math.hypot(end.x-bot.getX(),end.y-bot.getY())>2)return new Point(x,y,z);
   }
   return null;
  }
 }
 private PlayerBotWater(){}
}
