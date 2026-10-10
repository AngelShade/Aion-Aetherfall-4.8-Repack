package com.aionemu.gameserver.geoEngine.scene;

import java.util.*;
import com.aionemu.gameserver.geoEngine.bounding.BoundingBox;
import com.aionemu.gameserver.geoEngine.collision.CollisionResults;
import com.aionemu.gameserver.geoEngine.math.Vector3f;

/** Conservative finite-segment broad phase. Native child collision/door checks still run. */
final class SegmentCollisionIndex {
 private record Entry(int order,Spatial child,BoundingBox box) {}
 private record Branch(BoundingBox box,Branch left,Branch right,List<Entry> leaf) {}
 private final Branch root;
 private final List<Entry> fallback;
 final int size;
 SegmentCollisionIndex(List<Spatial> children) {
  size=children.size();var indexed=new ArrayList<Entry>();var unknown=new ArrayList<Entry>();
  for(int i=0;i<size;i++) {
   var child=children.get(i);
   if(child.getWorldBound() instanceof BoundingBox box && finite(box))
    indexed.add(new Entry(i,child,new BoundingBox(box)));
   else unknown.add(new Entry(i,child,null));
  }
  root=build(indexed);fallback=List.copyOf(unknown);
 }
 private static boolean finite(BoundingBox box) {
  var c=box.getCenter();return Float.isFinite(c.x) && Float.isFinite(c.y) && Float.isFinite(c.z)
   && Float.isFinite(box.getXExtent()) && Float.isFinite(box.getYExtent()) && Float.isFinite(box.getZExtent())
   && box.getXExtent()>=0 && box.getYExtent()>=0 && box.getZExtent()>=0;
 }
 private static Branch build(List<Entry> entries) {
  if(entries.isEmpty())return null;
  // Outward-rounded union: an index must never reject a child that the native broad phase accepts.
  float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
  for(var e:entries){var c=e.box().getCenter();minX=Math.min(minX,c.x-e.box().getXExtent());maxX=Math.max(maxX,c.x+e.box().getXExtent());
   minY=Math.min(minY,c.y-e.box().getYExtent());maxY=Math.max(maxY,c.y+e.box().getYExtent());
   minZ=Math.min(minZ,c.z-e.box().getZExtent());maxZ=Math.max(maxZ,c.z+e.box().getZExtent());}
  var center=new Vector3f(minX+(maxX-minX)/2,minY+(maxY-minY)/2,minZ+(maxZ-minZ)/2);
  var bounds=new BoundingBox(center,Math.nextUp(Math.max(center.x-minX,maxX-center.x)),
   Math.nextUp(Math.max(center.y-minY,maxY-center.y)),Math.nextUp(Math.max(center.z-minZ,maxZ-center.z)));
  if(entries.size()<=8)return new Branch(bounds,null,null,List.copyOf(entries));
  int axis=maxX-minX>=maxY-minY && maxX-minX>=maxZ-minZ?0:maxY-minY>=maxZ-minZ?1:2;
  entries.sort(Comparator.comparingDouble(e->axis==0?e.box().getCenter().x:axis==1?e.box().getCenter().y:e.box().getCenter().z));
  int middle=entries.size()/2;
  return new Branch(bounds,build(new ArrayList<>(entries.subList(0,middle))),build(new ArrayList<>(entries.subList(middle,entries.size()))),List.of());
 }
 List<Spatial> candidates(CollisionResults query) {
  var entries=new ArrayList<>(fallback);collect(root,query,entries);
  // Preserve native child ordering, including onlyFirst behavior and equal-distance contacts.
  entries.sort(Comparator.comparingInt(Entry::order));return entries.stream().map(Entry::child).toList();
 }
 private static void collect(Branch branch,CollisionResults query,List<Entry> out) {
  if(branch==null || !query.intersectsSegmentBounds(branch.box()))return;
  if(!branch.leaf().isEmpty()) {
   for(var e:branch.leaf())if(query.intersectsSegmentBounds(e.box()))out.add(e);
  }else {collect(branch.left(),query,out);collect(branch.right(),query,out);}
 }
}
