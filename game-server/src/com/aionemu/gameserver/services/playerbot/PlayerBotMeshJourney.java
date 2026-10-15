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
    private Point finalGoal;
    private Point frontier;
    private int frontierCursor;
    private ArrayList<Point> extracting;
    private int extractionCursor;
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
                if(search==null)search=graph.new Search(startComponent,endComponent);
                work+=search.advance(allowance-work,expired);reason="planning directed journey across tile components";
                if(search.pending())return work;
                if(search.failure()!=null){fail(search.failure());return work;}
                extracting=new ArrayList<>();phase=4;
            }
            if(phase==4){
                int[] route=search.route();
                while(extractionCursor<route.length && work<allowance && !expired.getAsBoolean()){
                    int arc=route[extractionCursor++];work++;
                    add(extracting,graph.source(arc));
                    var ride=graph.ride(arc);if(ride!=null)rides.put(extracting.size()-1,ride);
                    add(extracting,graph.destination(arc));
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
        if(waypoints!=null)while(cursor<waypoints.size()-1 && !rides.containsKey(cursor) && distance(physical,waypoints.get(cursor))<.75)cursor++;
    }
    Point target(Point plannedOrigin){
        if(waypoints==null)return finalGoal;
        int at=frontier!=null && distance(plannedOrigin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        while(at<waypoints.size()-1 && !rides.containsKey(at) && distance(plannedOrigin,waypoints.get(at))<.75)at++;
        return waypoints.get(at);
    }
    void provedSegment(Point origin,Point endpoint,Point target){
        if(waypoints==null)return;
        int at=frontier!=null && distance(origin,frontier)<.25?Math.max(cursor,frontierCursor):cursor;
        while(at<waypoints.size()-1 && !rides.containsKey(at) && distance(origin,waypoints.get(at))<.75)at++;
        if(!waypoints.get(at).equals(target))return;
        if(at<waypoints.size()-1 && !rides.containsKey(at) && distance(endpoint,target)<.75)at++;
        frontier=endpoint;frontierCursor=at; // Earned prefetch only; does not advance physical cursor.
    }
    boolean guided(){return waypoints!=null;}
    PlayerBotAnimatedLinks.Ride boarding(Point physical){return waypoints!=null && distance(physical,waypoints.get(cursor))<.75?rides.get(cursor):null;}
    boolean completeRide(PlayerBotAnimatedLinks.Ride ride,Point physical){
        if(waypoints==null || rides.get(cursor)!=ride || distance(physical,ride.to().ground())>.4)return false;
        cursor=Math.min(cursor+1,waypoints.size()-1);frontier=physical;frontierCursor=cursor;observe(physical);return true;
    }
    void pause(){
        if(!ready()){graph=null;search=null;extracting=null;releaseRequests();extractionCursor=phase=0;startComponent=endComponent=-1;}
    }
    private void fail(String why){unavailable=reason=why;graph=null;search=null;extracting=null;releaseRequests();}
    private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    private void releaseRequests(){
        if(endpointRequest!=null){endpointRequest.close();endpointRequest=null;}
        if(topologyRequest!=null){topologyRequest.close();topologyRequest=null;}
        if(topologyLease!=null){topologyLease.close();topologyLease=null;}
        if(admission!=null)admission.cancel(this);
    }
    @Override public void close(){graph=null;search=null;extracting=null;releaseRequests();waypoints=null;rides.clear();finalGoal=frontier=null;phase=cursor=frontierCursor=extractionCursor=0;unavailable=null;startComponent=endComponent=-1;}
}
