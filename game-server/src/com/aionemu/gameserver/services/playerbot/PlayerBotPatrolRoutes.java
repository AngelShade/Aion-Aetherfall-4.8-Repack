package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Fixed native friendly-NPC walker routes. Pathfinding retains physical movement authority. */
final class PlayerBotPatrolRoutes {
 record Route(String id,int map,Race race,int level,int npc,String name,List<PlayerBotNavigation.Point> points,Map<Long,int[]> nearby) {
  double distance(float x,float y,float z) {
   return distance(x,y,z,nearby.getOrDefault(cell((int)Math.floor(x/64),(int)Math.floor(y/64)),EMPTY));
  }
  private double distance(float x,float y,float z,int[] segments) {
   double best=Double.POSITIVE_INFINITY;
   for(int i:segments) {
    var a=points.get(i-1);var b=points.get(i);
    double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z(),length=dx*dx+dy*dy+dz*dz;
    double t=length==0?0:Math.clamp(((x-a.x())*dx+(y-a.y())*dy+(z-a.z())*dz)/length,0,1);
    best=Math.min(best,Math.sqrt(Math.pow(x-a.x()-t*dx,2)+Math.pow(y-a.y()-t*dy,2)+Math.pow(z-a.z()-t*dz,2)));
   }
   return best;
  }
  double distance(Player player){return player.getWorldId()==map?distance(player.getX(),player.getY(),player.getZ()):Double.POSITIVE_INFINITY;}
  double selectionDistance(Player player){return distance(player.getX(),player.getY(),player.getZ(),java.util.stream.IntStream.range(1,points.size()).toArray());}
  int nearest(Player player) {
   int best=0;double distance=Double.POSITIVE_INFINITY;
   for(int i=0;i<points.size();i++){var p=points.get(i);double d=Math.pow(player.getX()-p.x(),2)+Math.pow(player.getY()-p.y(),2)+Math.pow(player.getZ()-p.z(),2);if(d<distance){distance=d;best=i;}}
   return best;
  }
  /** A parallel point beside this waypoint, on the traveller's left for a positive offset. */
  PlayerBotNavigation.Point beside(int index,int direction,float offset) {
   var p=points.get(index);var from=points.get(Math.clamp(index-direction,0,points.size()-1));var to=points.get(Math.clamp(index+direction,0,points.size()-1));
   double dx=to.x()-from.x(),dy=to.y()-from.y();
   // Walker routes double back on themselves; the turn uses the leg just walked.
   if(Math.hypot(dx,dy)<.5){dx=p.x()-from.x();dy=p.y()-from.y();}
   double length=Math.hypot(dx,dy);if(length<.5)return p;
   return new PlayerBotNavigation.Point((float)(p.x()-dy/length*offset),(float)(p.y()+dx/length*offset),p.z());
  }
 }
 /** Stable 2-3m lane on one side of the road; the centre stays with the native patrol. */
 static float laneOffset(int id) {
  int hash=id*0x9e3779b9;float width=2+Integer.remainderUnsigned(hash>>>8,11)/10f;return (hash>>>28&1)==0?width:-width;
 }
 private static final int[] EMPTY={};
 private static long cell(int x,int y){return ((long)x<<32)|Integer.toUnsignedLong(y);}
 private static Map<Long,int[]> index(List<PlayerBotNavigation.Point> points) {
  var cells=new HashMap<Long,List<Integer>>();
  for(int i=1;i<points.size();i++) {
   var a=points.get(i-1);var b=points.get(i);
   for(int x=(int)Math.floor((Math.min(a.x(),b.x())-100)/64);x<=(int)Math.floor((Math.max(a.x(),b.x())+100)/64);x++)
    for(int y=(int)Math.floor((Math.min(a.y(),b.y())-100)/64);y<=(int)Math.floor((Math.max(a.y(),b.y())+100)/64);y++)
     cells.computeIfAbsent(cell(x,y),key->new ArrayList<>()).add(i);
  }
  var result=new HashMap<Long,int[]>();cells.forEach((cell,segments)->result.put(cell,segments.stream().mapToInt(Integer::intValue).toArray()));return Map.copyOf(result);
 }
 private static volatile Map<String,Route> routes;
 static Map<String,Route> all() {
  var known=routes;if(known!=null)return known;
  synchronized(PlayerBotPatrolRoutes.class) {
   if(routes!=null)return routes;
   var result=new TreeMap<String,Route>();
   for(var map:DataManager.WORLD_MAPS_DATA)for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
    if(!PlayerBotWorldPlacement.mapEligible(map,race,Set.of()))continue;
    TribeClass tribe=race==Race.ELYOS?TribeClass.PC:TribeClass.PC_DARK;
    for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map.getMapId())) {
     var npc=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
     if(npc==null || group.isTemporarySpawn() || group.getHandlerType()!=null
      || DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(npc.getTribe(),tribe)
      || DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(npc.getTribe(),tribe)
      || !(npc.getRace()==race || DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(npc.getTribe(),tribe)))continue;
     for(var spawn:group.getSpawnTemplates()) {
      if(spawn.isAerialSpawn() || spawn.getWalkerId()==null)continue;
      String key=map.getMapId()+":"+race+":"+spawn.getWalkerId();if(result.containsKey(key))continue;
      var walker=DataManager.WALKER_DATA.getWalkerTemplate(spawn.getWalkerId());if(walker==null)continue;
      var points=new ArrayList<PlayerBotNavigation.Point>();double length=0;boolean valid=true;
      for(var step:walker.getRouteSteps()) {
       var p=new PlayerBotNavigation.Point(step.getX(),step.getY(),step.getZ());
       if(!Float.isFinite(p.x()) || !Float.isFinite(p.y()) || !Float.isFinite(p.z())){valid=false;break;}
       if(!points.isEmpty()) {var a=points.getLast();double gap=Math.sqrt(Math.pow(p.x()-a.x(),2)+Math.pow(p.y()-a.y(),2)+Math.pow(p.z()-a.z(),2));if(gap>100){valid=false;break;}length+=gap;}
       points.add(p);
      }
      if(!valid || points.size()<4 || points.size()>1024 || length<100)continue;
      // Level derives from nearby native field actors, rather than level-65 town guards.
      int level=PlayerBotWorldPlacement.locations().stream().filter(l->l.race()==race && l.map()==map.getMapId() && !l.city() && !l.hub())
       .min(Comparator.comparingDouble(l->Math.pow(l.x()-spawn.getX(),2)+Math.pow(l.y()-spawn.getY(),2)))
       .map(l->(l.minimum()+l.maximum())/2).orElse(Math.clamp(npc.getLevel(),1,65));
      result.put(key,new Route(key,map.getMapId(),race,level,npc.getTemplateId(),npc.getName()+" route",List.copyOf(points),index(points)));
     }
    }
   }
   routes=Map.copyOf(result);
   org.slf4j.LoggerFactory.getLogger(PlayerBotPatrolRoutes.class).info("Road duty catalogue: {} fixed friendly native walker routes",routes.size());
   return routes;
  }
 }
 static Route choose(Player bot) {
  return all().values().stream().filter(r->r.race()==bot.getRace() && r.map()==bot.getWorldId() && Math.abs(r.level()-bot.getLevel())<=12)
   .min(Comparator.comparingDouble(r->r.selectionDistance(bot))).orElse(null);
 }
 private PlayerBotPatrolRoutes(){}
}
