package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Fixed native friendly-NPC walker routes. Pathfinding retains physical movement authority. */
final class PlayerBotPatrolRoutes {
 /** Inside the corridor a Warden already defends; one stray critter never makes a road worth guarding. */
 static final int GUARD_CORRIDOR=100,GUARD_MONSTERS=5;
 record Route(String id,int map,Race race,int level,int npc,String name,List<PlayerBotNavigation.Point> points,Map<Long,int[]> nearby,boolean guardable) {
  Route guardable(boolean value){return new Route(id,map,race,level,npc,name,points,nearby,value);}
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
   return nearest(player.getX(),player.getY(),player.getZ());
  }
  int nearest(float x,float y,float z) {
   int best=0;double distance=Double.POSITIVE_INFINITY;
   for(int i=0;i<points.size();i++){var p=points.get(i);double d=Math.pow(x-p.x(),2)+Math.pow(y-p.y(),2)+Math.pow(z-p.z(),2);if(d<distance){distance=d;best=i;}}
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
 static Map<Long,int[]> index(List<PlayerBotNavigation.Point> points) {
  return index(points,100);
 }
 static Map<Long,int[]> index(List<PlayerBotNavigation.Point> points,int radius) {
  var cells=new HashMap<Long,List<Integer>>();
  for(int i=1;i<points.size();i++) {
   var a=points.get(i-1);var b=points.get(i);
   for(int x=(int)Math.floor((Math.min(a.x(),b.x())-radius)/64);x<=(int)Math.floor((Math.max(a.x(),b.x())+radius)/64);x++)
    for(int y=(int)Math.floor((Math.min(a.y(),b.y())-radius)/64);y<=(int)Math.floor((Math.max(a.y(),b.y())+radius)/64);y++)
     cells.computeIfAbsent(cell(x,y),key->new ArrayList<>()).add(i);
  }
  var result=new HashMap<Long,int[]>();cells.forEach((cell,segments)->result.put(cell,segments.stream().mapToInt(Integer::intValue).toArray()));return Map.copyOf(result);
 }
 /** Decided per road from what actually spawns beside it, so map type and labels never matter. */
 static boolean guardable(Route route,Collection<PlayerBotNavigation.Point> monsters) {
  int found=0;
  for(var p:monsters)if(route.distance(p.x(),p.y(),p.z())<=GUARD_CORRIDOR && ++found>=GUARD_MONSTERS)return true;
  return false;
 }
 /** Native spawns a Warden of this tribe could fight: the same test field placement uses for monsters. */
 static List<PlayerBotNavigation.Point> monsters(int map,TribeClass tribe) {
  var result=new ArrayList<PlayerBotNavigation.Point>();
  for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(map)) {
   var npc=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
   if(npc==null || group.isTemporarySpawn() || group.getHandlerType()!=null || !PlayerBotHunting.template(npc)
    || !DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(npc.getTribe(),tribe) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(npc.getTribe(),tribe))continue;
   for(var spawn:group.getSpawnTemplates())if(!spawn.isAerialSpawn())result.add(new PlayerBotNavigation.Point(spawn.getX(),spawn.getY(),spawn.getZ()));
  }
  return result;
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
    var fields=PlayerBotWorldPlacement.locations().stream().filter(l->l.race()==race && l.map()==map.getMapId() && !l.city() && !l.hub()).toList();
    var monsters=monsters(map.getMapId(),tribe);
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
      int level=fields.stream()
       .min(Comparator.comparingDouble(l->Math.pow(l.x()-spawn.getX(),2)+Math.pow(l.y()-spawn.getY(),2)))
       .map(l->(l.minimum()+l.maximum())/2).orElse(Math.clamp(npc.getLevel(),1,65));
      var road=new Route(key,map.getMapId(),race,level,npc.getTemplateId(),npc.getName()+" route",List.copyOf(points),index(points),false);
      result.put(key,road.guardable(guardable(road,monsters)));
     }
    }
   }
   routes=Map.copyOf(result);
   org.slf4j.LoggerFactory.getLogger(PlayerBotPatrolRoutes.class).info("Road duty catalogue: {} fixed friendly native walker routes, {} guardable by Wardens",routes.size(),routes.values().stream().filter(Route::guardable).count());
   return routes;
  }
 }
 static boolean suitable(Route route,PlayerBotRoadDuty.Career career){return route!=null && (career!=PlayerBotRoadDuty.Career.WARDEN || route.guardable());}
 /** A single native ground-edge probe, not a path search. Never select the floor below a bridge by distance alone. */
 static boolean reachable(Player bot,Route route) {
  if(route.map()!=bot.getWorldId())return false;
  var p=route.points().get(route.nearest(bot));var map=DataManager.WORLD_MAPS_DATA.getTemplate(route.map());
  float z=com.aionemu.gameserver.world.geo.GeoService.getInstance().getZ(route.map(),p.x(),p.y(),p.z()+3,p.z()-8,bot.getInstanceId());
  if(map==null || !PlayerBotWorldPlacement.ground(map,p.x(),p.y(),z,z))return false;
  var reached=PlayerBotWater.walk(bot,route.map(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),p.x(),p.y(),z);
  return reached!=null && Math.hypot(reached.x-p.x(),reached.y-p.y())<.3 && Math.abs(reached.z-z)<.75;
 }
 static Route choose(Collection<Route> candidates,int level,java.util.function.ToDoubleFunction<Route> distance,java.util.function.Predicate<Route> reachable) {
  // Reachability wins over level: a safe city merchant can use a low-level bridge on its own floor.
  record Choice(Route route,double distance) {}
  return candidates.stream().map(r->new Choice(r,distance.applyAsDouble(r)))
   .sorted(Comparator.comparingInt((Choice c)->Math.abs(c.route().level()-level)<=12?0:1).thenComparingDouble(Choice::distance).thenComparing(c->c.route().id()))
   .map(Choice::route).filter(reachable).findFirst().orElse(null);
 }
 static Route choose(Player bot,PlayerBotRoadDuty.Career career) {
  return choose(all().values().stream().filter(r->r.race()==bot.getRace() && r.map()==bot.getWorldId() && suitable(r,career)
   && (career!=PlayerBotRoadDuty.Career.WARDEN || Math.abs(r.level()-bot.getLevel())<=12)).toList(),
   bot.getLevel(),r->r.selectionDistance(bot),r->reachable(bot,r));
 }
 private PlayerBotPatrolRoutes(){}
}
