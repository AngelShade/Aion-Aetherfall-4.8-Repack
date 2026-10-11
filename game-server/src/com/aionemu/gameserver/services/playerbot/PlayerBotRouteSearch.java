/* MovementActions::SearchForBestPath purpose, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md.
 * Aion has physical geometry rather than WoW Detour polygons. Every edge here
 * is supplied by native supported-ground probes; this is an Aion adapter. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Resumable ground search: deadline expiry retains even a partly expanded node. */
final class PlayerBotRouteSearch {
 private record Cell(int x,int y,int height) {}
 private record Node(Cell cell,Point point,Node parent,double cost,double remaining,long sequence) {}
 private static final int[][] DIRECTIONS={{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,1},{-1,-1},{1,-1}};
 private final Point start,goal;
 private final float mapSize;
 private final int capacity;
 private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator
  .comparingDouble((Node n)->n.cost()+n.remaining()).thenComparingDouble(Node::remaining).thenComparingLong(Node::sequence));
 private final Map<Cell,Double> best=new HashMap<>();
 private Node current;
 private int edge,expanded;
 private long sequence;
 private boolean complete;
 private List<Point> route=List.of();
 private String reason="searching supported ground";

 PlayerBotRouteSearch(Point start,Point goal,float mapSize,int capacity) {
  this.start=start;this.goal=goal;this.mapSize=mapSize;this.capacity=Math.max(1,capacity);
  if(!finite(start) || !finite(goal) || !inside(start.x(),start.y()) || !inside(goal.x(),goal.y())) {
   complete=true;reason="destination outside native map bounds";return;
  }
  Cell cell=new Cell(0,0,Math.round(start.z()*4));
  open.add(new Node(cell,start,null,0,distance(start,goal),sequence++));best.put(cell,0d);
 }
 boolean pending(){return !complete;}
 List<Point> route(){return route;}
 String reason(){return reason;}
 int retained(){return best.size();}
 int expanded(){return expanded;}

 void advance(PlayerBotPathfinder.Ground ground,int probes,BooleanSupplier expired) {
  int used=0;
  while(!complete && used<probes && !expired.getAsBoolean()) {
   if(current==null) {
    if(open.isEmpty()){complete=true;reason="no supported ground route";break;}
    current=open.remove();
    if(current.cost()>best.getOrDefault(current.cell(),Double.POSITIVE_INFINITY)){current=null;continue;}
    if(++expanded>capacity){complete=true;reason="supported route search capacity reached";break;}
    edge=-1;
   }
   // Try a final native connection, including the destination's height layer.
   if(edge==-1) {
    edge=0;
    if(current.remaining()<=6) {
     used++;Point end=ground.step(current.point(),goal.x(),goal.y());
     if(valid(current.point(),end,goal.x(),goal.y()) && distance(end,goal)<.5) {finish(current,end);break;}
     if(used>=probes || expired.getAsBoolean())break;
    }
   }
   if(edge==16){current=null;continue;}
   int index=edge++,stride=index<8?4:1;
   int cx=current.cell().x()+DIRECTIONS[index%8][0]*stride;
   int cy=current.cell().y()+DIRECTIONS[index%8][1]*stride;
   float x=start.x()+cx,y=start.y()+cy;
   if(!inside(x,y))continue;
   used++;Point point=ground.step(current.point(),x,y);
   if(!valid(current.point(),point,x,y))continue;
   Cell cell=new Cell(cx,cy,Math.round(point.z()*4));
   double cost=current.cost()+distance(current.point(),point);
   if(cost+1e-7>=best.getOrDefault(cell,Double.POSITIVE_INFINITY))continue;
   // Both stored cells and superseded queue entries are bounded per intent.
   if(!best.containsKey(cell) && best.size()>=capacity || open.size()>=capacity*2) {
    complete=true;reason="supported route search capacity reached";break;
   }
   best.put(cell,cost);open.add(new Node(cell,point,current,cost,distance(point,goal),sequence++));
  }
 }
 private void finish(Node node,Point end) {
  var points=new ArrayList<Point>();points.add(end);
  for(Node n=node;n.parent()!=null;n=n.parent())points.add(n.point());
  Collections.reverse(points);route=List.copyOf(points);complete=true;reason="supported route found";
  open.clear();best.clear();current=null;
 }
 private boolean inside(float x,float y){return Float.isFinite(mapSize) && mapSize>0 && x>=0 && y>=0 && x<mapSize && y<mapSize;}
 private static boolean valid(Point from,Point p,float x,float y) {
  return finite(p) && Math.hypot(p.x()-x,p.y()-y)<=.15
   && Math.abs(p.z()-from.z())<=Math.hypot(x-from.x(),y-from.y())+.5;
 }
 private static boolean finite(Point p){return p!=null && Float.isFinite(p.x()) && Float.isFinite(p.y()) && Float.isFinite(p.z());}
 private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
}
