package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.recast4j.detour.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** A destination owns one directed map-wide journey across successive local tile queries.
 * Portal coordinates guide planning only; they never authorize movement or arrival. */
final class PlayerBotMeshJourney implements AutoCloseable {
    private PlayerBotNavmeshStore.Request topologyRequest,endpointRequest;
    private PlayerBotNavmeshStore.Lease topologyLease;
    private PlayerBotMeshRoute.Admission admission;
    private PlayerBotNavmeshTopology graph;
    private PlayerBotNavmeshTopology.Search search;
    private List<Point> waypoints;
    private final Map<Integer,PlayerBotAnimatedLinks.Ride> rides=new HashMap<>();
    private final Map<Integer,PlayerBotRoadNetwork.Jump> jumps=new HashMap<>();
    private boolean jumpLaunched;
    private Point finalGoal;
    private Point failurePosition;
    private Point frontier;
    private int frontierCursor;
    private ArrayList<Point> extracting;
    private int extractionCursor;
    private int extractionPoint;
    private final BitSet preparedEdges=new BitSet();
    private final Map<Integer,Integer> roadArcs=new HashMap<>();
    private final Map<Integer,Long> blockedArcs=new HashMap<>();
    private int world,phase,startComponent=-1,endComponent=-1,cursor;
    private String unavailable,reason="waiting for directed journey topology";
    void bind(int world,Point goal){
        if(this.world!=world || !goal.equals(finalGoal)){close();this.world=world;finalGoal=goal;}
    }
    boolean ready(){return waypoints!=null || unavailable!=null;}
    String reason(){return reason;}
    int advance(PlayerBotNavmeshStore store,Point origin,int allowance,BooleanSupplier expired){
        return advance(store,origin,allowance,expired,PlayerBotMeshRoute.admission());
    }
    int advance(PlayerBotNavmeshStore store,Point origin,int allowance,BooleanSupplier expired,PlayerBotMeshRoute.Admission admission){
        if(ready() || allowance<4 || expired.getAsBoolean())return 0;
        if(this.admission!=null && this.admission!=admission)throw new IllegalStateException("Journey admission owner changed");
        this.admission=admission;
        int work=0;
        if(phase==0){
            if(!admission.demand(this)){reason="waiting for directed journey loading admission";return 0;}
            if(topologyRequest==null)topologyRequest=store.requestTopology(world);
            var access=topologyRequest.poll();reason=access.reason();
            if(access.status()==PlayerBotNavmeshStore.Status.UNAVAILABLE){fail(reason);return 0;}
            if(access.status()!=PlayerBotNavmeshStore.Status.READY)return 0;
            topologyLease=access.lease();graph=topologyLease.topology();phase=1;
        }
        if(phase==1 || phase==2){
            Point point=phase==1?origin:finalGoal;
            if(endpointRequest==null)endpointRequest=store.request(world,point.x(),point.y(),1);
            var access=endpointRequest.poll();reason=access.reason();
            if(access.status()==PlayerBotNavmeshStore.Status.UNAVAILABLE){fail(reason);return work;}
            if(access.status()!=PlayerBotNavmeshStore.Status.READY)return work;
            if(expired.getAsBoolean())return work;
            if(!admission.retain(this)){reason="waiting for journey endpoint query memory";return work;}
            if(!admission.begin(this)){reason="waiting for journey endpoint calculation turn";return work;}
            try {
                try(var lease=access.lease()){
                    var mesh=lease.mesh();var nearest=new NavMeshQuery(mesh).findNearestPoly(new float[]{point.x(),point.z(),point.y()},new float[]{1,.75f,1},new DefaultQueryFilter());work++;
                    int component=nearest.failed() || nearest.result.getNearestRef()==0?-1:graph.component(mesh,nearest.result.getNearestRef());
                    if(component<0){fail("journey endpoint absent on the requested native floor");return work;}
                    if(phase==1)startComponent=component;else endComponent=component;
                }
            } finally {
                admission.end(this);admission.releaseMemory(this);
            }
            endpointRequest.close();endpointRequest=null;phase++;
            if(phase==3)admission.releaseDemand(this);
            // Endpoint lookup and graph expansion stay within separate funded turns.
            return work;
        }
        if(expired.getAsBoolean())return work;
        // The SCC maps/heap survive funded turns, but their memory is owned by this
        // journey (not a replaceable local route), and I/O never holds a compute slot.
        if(!admission.retain(this)){reason="waiting for directed journey search memory";return work;}
        if(!admission.begin(this)){reason="waiting for directed journey calculation turn";return work;}
        try {
            if(phase==3){
                if(search==null){blockedArcs.values().removeIf(until->until<=System.currentTimeMillis());search=graph.new Search(startComponent,endComponent,blockedArcs.keySet());}
                work+=search.advance(allowance-work,expired);reason="planning directed journey across tile components";
                if(search.pending())return work;
                if(search.failure()!=null){fail(search.failure());return work;}
                extracting=new ArrayList<>();phase=4;
            }
            if(phase==4){
                int[] route=search.route();
                while(extractionCursor<route.length && work<allowance && !expired.getAsBoolean()){
                    int arc=route[extractionCursor];work++;
                    var ride=graph.ride(arc);var jump=graph.jump(arc);var roads=graph.roads();
                    if(jump!=null){
                        add(extracting,roads.hub(jump.from()));add(extracting,graph.source(arc));
                        jumps.put(extracting.size()-1,jump);roadArcs.put(extracting.size()-1,arc);
                        add(extracting,graph.destination(arc));add(extracting,roads.hub(jump.to()));extractionCursor++;extractionPoint=0;
                    }else if(ride!=null){
                        if(roads!=null)add(extracting,roads.hub(ride.from().component()));
                        add(extracting,graph.source(arc));rides.put(extracting.size()-1,ride);
                        add(extracting,graph.destination(arc));
                        if(roads!=null)add(extracting,roads.hub(ride.to().component()));
                        extractionCursor++;extractionPoint=0;
                    }else if(roads!=null){
                        int before=extracting.size();add(extracting,roads.point(arc,extractionPoint));
                        if(extracting.size()>before && extractionPoint>0){preparedEdges.set(extracting.size()-1);roadArcs.put(extracting.size()-1,arc);}
                        if(++extractionPoint==roads.count(arc)){extractionPoint=0;extractionCursor++;}
                    }else{add(extracting,graph.source(arc));add(extracting,graph.destination(arc));extractionCursor++;}
                    if(extracting.size()>32768){fail("directed road itinerary capacity reached");extracting=null;return work;}
                }
                if(extractionCursor<route.length)return work;
                extracting.add(finalGoal);waypoints=Collections.unmodifiableList(extracting);extracting=null;reason="retained directed ground journey";
                search=null;graph=null;releaseRequests();
            }
            return work;
        } finally {admission.end(this);}
    }
    private static void add(List<Point> points,float[] p){
        Point point=new Point(p[0],p[2],p[1]);
        if(points.isEmpty() || !points.getLast().equals(point))points.add(point);
    }
    void observe(Point physical){
        if(unavailable!=null && blockedArcs.values().stream().anyMatch(until->until<=System.currentTimeMillis())){
            var remaining=new HashMap<>(blockedArcs);remaining.values().removeIf(until->until<=System.currentTimeMillis());
            int map=world;Point goal=finalGoal;close();bind(map,goal);blockedArcs.putAll(remaining);
        }
        // A missing/disconnected origin describes one physical floor/pose, not
        // this destination forever. A proved native prefix or descent may have
        // moved the actor into a component from which the journey now exists.
        if(unavailable!=null){
            if(failurePosition==null)failurePosition=physical;
            else if(distance(physical,failurePosition)>4){int map=world;Point goal=finalGoal;close();bind(map,goal);}
        }
        if(waypoints!=null)while(cursor<waypoints.size()-1 && !rides.containsKey(cursor) && !jumps.containsKey(cursor) && distance(physical,waypoints.get(cursor))<.75)cursor++;
    }
    Point target(Point plannedOrigin){
        if(waypoints==null)return finalGoal;
        int at=frontier!=null && distance(plannedOrigin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        while(at<waypoints.size()-1 && !rides.containsKey(at) && !jumps.containsKey(at) && distance(plannedOrigin,waypoints.get(at))<.75)at++;
        return waypoints.get(at);
    }
    boolean walkingTarget(Point plannedOrigin,Point target){
        if(waypoints==null)return false;
        int at=frontier!=null && distance(plannedOrigin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        while(at<waypoints.size()-1 && !rides.containsKey(at) && !jumps.containsKey(at) && distance(plannedOrigin,waypoints.get(at))<.75)at++;
        return at<waypoints.size()-1 && !rides.containsKey(at) && !jumps.containsKey(at) && waypoints.get(at).equals(target);
    }
    void provedSegment(Point origin,Point endpoint,Point target){
        if(waypoints==null)return;
        int at=frontier!=null && distance(origin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        while(at<waypoints.size()-1 && !rides.containsKey(at) && !jumps.containsKey(at) && distance(origin,waypoints.get(at))<.75)at++;
        if(!waypoints.get(at).equals(target))return;
        if(at<waypoints.size()-1 && !rides.containsKey(at) && !jumps.containsKey(at) && distance(endpoint,target)<.75)at++;
        frontier=endpoint;frontierCursor=at; // Earned prefetch only; does not advance physical cursor.
    }
    boolean guided(){return waypoints!=null;}
    /** Reuse baked main corridors. Their local entry/exit connections and
     * authored boarding points still use the ordinary tiled path query. */
    record PreparedCorridor(int start,List<Point> points){}
    PreparedCorridor preparedCorridor(Point origin,float horizon){
        if(waypoints==null)return null;
        int at=frontier!=null && distance(origin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        while(at<waypoints.size()-1 && !rides.containsKey(at) && !jumps.containsKey(at) && distance(origin,waypoints.get(at))<.15)at++;
        if(at==0 || !preparedEdges.get(at) || !onSegment(origin,waypoints.get(at-1),waypoints.get(at)))return null;
        int start=at;
        var result=new ArrayList<Point>();double length=0;Point previous=origin;
        while(at<waypoints.size() && preparedEdges.get(at) && result.size()<512){
            int index=at;Point p=waypoints.get(at++);result.add(p);length+=distance(previous,p);previous=p;
            if(rides.containsKey(index) || jumps.containsKey(index) || length>=horizon)break;
        }
        return new PreparedCorridor(start,List.copyOf(result));
    }
    void provedCorridor(Point origin,Point endpoint,int start,int completed){
        // Completion comes from the actual native-proof cursor, not proximity
        // to a later hairpin or another nearby floor in the baked itinerary.
        int earned=Math.min(waypoints.size()-1,start+completed);
        for(int at=start;at<earned;at++)if(rides.containsKey(at) || jumps.containsKey(at)){earned=at;break;}
        frontier=endpoint;frontierCursor=earned;
    }
    void obstructed(Point origin){
        if(waypoints==null)return;
        int at=frontier!=null && distance(origin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        Integer arc=roadArcs.get(at);if(arc==null || at==0 || !onSegment(origin,waypoints.get(at-1),waypoints.get(at)))return;
        // Only an exhausted native local alternative blocks a static proposal.
        // A compute wait or an incomplete query never changes connectivity.
        var rejected=new HashMap<>(blockedArcs);rejected.put(arc,System.currentTimeMillis()+45000);
        int map=world;Point goal=finalGoal;close();bind(map,goal);blockedArcs.putAll(rejected);
        reason="replanning around obstructed main corridor";
    }
    private static boolean onSegment(Point p,Point a,Point b){
        double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z(),squared=dx*dx+dy*dy+dz*dz;
        double t=squared<.001?0:Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.y()-a.y())*dy+(p.z()-a.z())*dz)/squared));
        return distance(p,new Point((float)(a.x()+dx*t),(float)(a.y()+dy*t),(float)(a.z()+dz*t)))<=1;
    }
    PlayerBotAnimatedLinks.Ride pendingRide(){return waypoints==null?null:rides.get(cursor);}
    PlayerBotRoadNetwork.Jump pendingJump(){return waypoints==null?null:jumps.get(cursor);}
    boolean jumpLaunched(){return jumpLaunched;}
    void markJumpLaunched(){if(pendingJump()==null)throw new IllegalStateException("No height proposal");jumpLaunched=true;}
    boolean completeJump(Point physical){
        var jump=pendingJump();if(!jumpLaunched || jump==null || distance(physical,jump.end())>.4)return false;
        jumpLaunched=false;cursor=Math.min(cursor+1,waypoints.size()-1);frontier=physical;frontierCursor=cursor;observe(physical);return true;
    }
    void rejectJump(Point physical){
        if(pendingJump()==null)return;Integer arc=roadArcs.get(cursor);var rejected=new HashMap<>(blockedArcs);
        if(arc!=null)rejected.put(arc,System.currentTimeMillis()+45000);
        int map=world;Point goal=finalGoal;close();bind(map,goal);blockedArcs.putAll(rejected);reason="replanning obstructed jump/drop proposal";
    }
    PlayerBotAnimatedLinks.Ride boarding(Point physical){return waypoints!=null && distance(physical,waypoints.get(cursor))<.75?rides.get(cursor):null;}
    boolean completeRide(PlayerBotAnimatedLinks.Ride ride,Point physical){
        if(waypoints==null || rides.get(cursor)!=ride || distance(physical,ride.to().ground())>.4)return false;
        cursor=Math.min(cursor+1,waypoints.size()-1);frontier=physical;frontierCursor=cursor;observe(physical);return true;
    }
    void pause(){
        if(!ready()){
            graph=null;search=null;extracting=null;releaseRequests();
            extractionCursor=extractionPoint=phase=cursor=frontierCursor=0;frontier=null;
            preparedEdges.clear();rides.clear();jumps.clear();jumpLaunched=false;roadArcs.clear();startComponent=endComponent=-1;
        }
    }
    private void fail(String why){unavailable=reason=why;graph=null;search=null;extracting=null;releaseRequests();}
    private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    private void releaseRequests(){
        if(endpointRequest!=null){endpointRequest.close();endpointRequest=null;}
        if(topologyRequest!=null){topologyRequest.close();topologyRequest=null;}
        if(topologyLease!=null){topologyLease.close();topologyLease=null;}
        if(admission!=null)admission.cancel(this);
    }
    @Override public void close(){graph=null;search=null;extracting=null;releaseRequests();waypoints=null;rides.clear();jumps.clear();jumpLaunched=false;preparedEdges.clear();roadArcs.clear();blockedArcs.clear();finalGoal=frontier=failurePosition=null;phase=cursor=frontierCursor=extractionCursor=extractionPoint=0;unavailable=null;startComponent=endComponent=-1;}
}
