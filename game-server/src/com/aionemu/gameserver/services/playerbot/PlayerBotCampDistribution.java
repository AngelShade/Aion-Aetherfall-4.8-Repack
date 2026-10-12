/* TravelMgr::PrepareDestinationCache camp scale, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md.
 * Fresh local crowding is the user's Aion regional spreading extension. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Fresh admission-only crowding, using TravelMgr's native 50m XYZ camp scale.
 * Local occupancy is an explicit Aion spreading extension, not saved population state. */
final class PlayerBotCampDistribution {
 record Position(int map,float x,float y,float z) {
  Position {if(map<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))throw new IllegalArgumentException("Invalid live camp occupancy");}
 }
 private record Cell(int map,int x,int y,int z) {}
 private record Candidate(PlayerBotWorldPlacement.Location place,int nearby,double nearest) {}
 private final Map<Cell,List<Position>> cells=new HashMap<>();
 PlayerBotCampDistribution(Collection<Position> positions) {
  for(var p:positions)cells.computeIfAbsent(cell(p.map(),p.x(),p.y(),p.z()),key->new ArrayList<>()).add(p);
 }
 List<PlayerBotWorldPlacement.Location> prefer(List<PlayerBotWorldPlacement.Location> choices,Map<Integer,Integer> mapCounts) {
  var candidates=new ArrayList<Candidate>();
  for(var p:choices) {
   int nearby=0;double nearest=Double.POSITIVE_INFINITY;
   var c=cell(p.map(),p.x(),p.y(),p.z());
   for(int x=c.x()-1;x<=c.x()+1;x++)for(int y=c.y()-1;y<=c.y()+1;y++)for(int z=c.z()-1;z<=c.z()+1;z++)
    for(var actor:cells.getOrDefault(new Cell(p.map(),x,y,z),List.of())) {
     double distance=squared(actor.x()-p.x(),actor.y()-p.y(),actor.z()-p.z());
     if(distance<=2500){nearby++;nearest=Math.min(nearest,distance);}
    }
   candidates.add(new Candidate(p,nearby,nearest));
  }
  // Stable ties retain native player-area/radius preference and shuffled camp spreading.
  candidates.sort(Comparator.comparingInt((Candidate p)->mapCounts.getOrDefault(p.place().map(),0))
   .thenComparingInt(Candidate::nearby).thenComparing(Comparator.comparingDouble(Candidate::nearest).reversed()));
  return candidates.stream().map(Candidate::place).toList();
 }
 boolean clear(int map,float x,float y,float z) {
  var c=cell(map,x,y,z);
  for(int cx=c.x()-1;cx<=c.x()+1;cx++)for(int cy=c.y()-1;cy<=c.y()+1;cy++)for(int cz=c.z()-1;cz<=c.z()+1;cz++)
   for(var actor:cells.getOrDefault(new Cell(map,cx,cy,cz),List.of()))if(overlaps(x,y,z,actor))return false;
  return true;
 }
 static boolean overlaps(float x,float y,float z,Position actor) {return overlaps(x,y,z,actor.x(),actor.y(),actor.z());}
 static boolean overlaps(float x,float y,float z,float actorX,float actorY,float actorZ) {return squared(actorX-x,actorY-y,actorZ-z)<1;}
 private static double squared(double x,double y,double z){return x*x+y*y+z*z;}
 private static Cell cell(int map,float x,float y,float z){return new Cell(map,(int)Math.floor(x/50),(int)Math.floor(y/50),(int)Math.floor(z/50));}
}
