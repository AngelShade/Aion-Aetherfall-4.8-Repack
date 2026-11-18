package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkStatus;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Real Sanctum mesh and native floor/body steps through the production optional
 * planner and corridor owner. No World, SQL, registered actors, or movement tasks. */
public final class PlayerBotWholeRouteCheck {
    private static int checks,cases;
    private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    private static final int WORLD=110010000;
    private static final int[][] PAIRS={
        {1608,1518,573,1606,1534,549},{1518,1543,565,1555,1598,573},
        {1519,1543,565,1607,1548,549},{1567,1521,570,1332,1512,569},
        {1617,1483,549,1551,1423,573},{1570,1515,572,1369,1512,569},
        {1616,1491,549,1606,1534,549},{1545,1503,565,1517,1426,573},
        {1505,1437,572,1551,1423,573},{1620,1544,549,1555,1598,573},
        {1545,1501,565,1561,1428,573},{1551,1507,565,1607,1548,549},
        {1520,1544,565,1471,1644,573},{1669,1448,549,1807,1415,581},
        {1815,1435,579,1615,1513,550},{1518,1543,565,1471,1644,573},
        {1546,1502,565,1838,1411,587},{1517,1543,565,1471,1644,573}
    };
    private static double xy(Point a,Point b){return Math.hypot(a.x()-b.x(),a.y()-b.y());}
    private static Point normalized(GeoMap map,int[] pair,int offset){
        float x=pair[offset]+.5f,y=pair[offset+1]+.5f,z=pair[offset+2];
        float floor=PlayerBotGroundSupport.floor(map,1,x,y,z+2.5f,z-2.5f);
        return new Point(x,y,Float.isFinite(floor)?floor:z);
    }
    /** Independent input normalization from NavAgreementProbe.pairs: both ends
     * are projected to the authenticated mesh, then only start uses native floor.
     * This supplies inputs, never a corridor or planner result to the new owner. */
    private static List<Point[]> inputs(GeoMap map,PlayerBotNavmeshStore store)throws Exception{
        try(var request=store.requestFull(WORLD)){
            PlayerBotNavmeshStore.Result loaded=null;
            for(int turn=0;turn<30000;turn++){
                loaded=request.poll();if(loaded.status()!=PlayerBotNavmeshStore.Status.WAITING)break;Thread.sleep(1);
            }
            check(loaded!=null && loaded.status()==PlayerBotNavmeshStore.Status.READY,"Missing authenticated real Sanctum mesh: "+(loaded==null?"timeout":loaded.reason()));
            var query=new org.recast4j.detour.NavMeshQuery(loaded.lease().mesh());var filter=new org.recast4j.detour.DefaultQueryFilter();var result=new ArrayList<Point[]>();
            for(int[] pair:PAIRS){
                Point[] ends=new Point[2];
                for(int end=0;end<2;end++){
                    Point raw=normalized(map,pair,end*3);
                    var near=query.findNearestPoly(new float[]{raw.x(),raw.z(),raw.y()},new float[]{2,2.5f,2},filter);
                    check(!near.failed() && near.result.getNearestRef()!=0,"Historical real input resolves to a ground polygon");
                    float[] p=near.result.getNearestPos();float z=p[1];
                    if(end==0){float floor=PlayerBotGroundSupport.floor(map,1,p[0],p[2],z+1.5f,z-1.5f);check(Float.isFinite(floor),"Projected historical start has real native floor");z=floor;}
                    ends[end]=new Point(p[0],p[2],z);
                }
                result.add(ends);
            }
            return result;
        }
    }
    /** Small physical transport only: it implements publishWalk's generation,
     * prefix, acknowledgments and tail contract; all route decisions are core code. */
    private static final class Mover implements PlayerBotWholeRoute.Environment {
        final GeoMap map;
        Point at;
        long generation,reached=-1;
        WalkPoint current;
        float physicalTargetZ;
        List<WalkPoint> ahead=List.of();
        boolean active,blocked,denySafe;
        Point hole;
        int failureCalls,successCalls,aborts;
        boolean forwardOnly;
        double walked;
        Mover(GeoMap map,Point start){this.map=map;at=start;}
        public int world(){return WORLD;}
        public int instance(){return 1;}
        public Point position(){return at;}
        public float minimumZ(){return Float.NEGATIVE_INFINITY;}
        public Point ground(Point p){if(hole!=null && xy(p,hole)<.5)return null;float z=PlayerBotGroundSupport.floor(map,1,p.x(),p.y(),p.z()+2.5f,p.z()-2.5f);return Float.isFinite(z)?new Point(p.x(),p.y(),z):null;}
        public boolean safe(Point from,Point to){return !denySafe;}
        public boolean reachable(Point from,Point to){var end=PlayerBotGroundNavigation.walk(map,1,from.x(),from.y(),from.z(),to.x(),to.y(),to.z());return Math.hypot(end.x-to.x(),end.y-to.y())<=.15 && Math.abs(end.z-to.z())<=2;}
        public WalkStatus walkStatus(){return new WalkStatus(generation,reached,current,active,blocked);}
        public long nextGeneration(){return generation+1;}
        public void abort(long owner){if(owner==generation){aborts++;active=false;current=null;ahead=List.of();}}
        public boolean publish(long owner,List<WalkPoint> points){
            check(!points.isEmpty() && points.size()<=64,"Bounded nonempty delivery tail");
            if(owner<generation || owner==generation && (!active || blocked))return false;
            int first=0;
            if(owner>generation){if(forwardOnly)check(points.getFirst().index()>reached,"Interruption cannot republish an acknowledged corner");generation=owner;reached=points.getFirst().index()-1;current=points.getFirst();physicalTargetZ=current.z();active=true;blocked=false;}
            else{
                while(first<points.size() && points.get(first).index()<current.index())first++;
                check(first<points.size() && points.get(first).equals(current),"Refresh preserves exact active corner identity");
            }
            ahead=List.copyOf(points.subList(first+1,points.size()));return true;
        }
        public boolean recentlyUnreachable(Point start,Point goal){return PlayerBotWholeRoute.Environment.super.recentlyUnreachable(start,goal);}
        public void failed(Point start,Point goal){failureCalls++;PlayerBotWholeRoute.Environment.super.failed(start,goal);}
        public void succeeded(Point start,Point goal){successCalls++;PlayerBotWholeRoute.Environment.super.succeeded(start,goal);}
        void tick(){
            double remaining=.73; // 7.3m/s, real100ms movement ticks
            for(int edges=0;active && current!=null && edges<8 && remaining>.001;edges++){
                Point corner=new Point(current.x(),current.y(),physicalTargetZ);double before=xy(at,corner);
                if(before<.01){check(Math.abs(at.z()-physicalTargetZ)<=.75,"Native zero-distance target height agrees");acknowledge();continue;}
                float t=(float)Math.min(1,remaining/before);float tx=at.x()+(corner.x()-at.x())*t,ty=at.y()+(corner.y()-at.y())*t;
                var end=PlayerBotGroundNavigation.walk(map,1,at.x(),at.y(),at.z(),tx,ty,corner.z());Point next=new Point(end.x,end.y,end.z);
                double step=xy(at,next),left=xy(next,corner);
                check(left<=before+.001,"Native movement never turns away from the next corner");
                walked+=step;remaining-=step;at=next;
                if(Math.hypot(end.x-tx,end.y-ty)>.15 || step<.001){throw new AssertionError("Native historical walk blocked at "+at+" toward "+new Point(tx,ty,corner.z())+" corner "+corner+" index "+current.index()+" remaining="+remaining+" fraction="+t);}
                if(left<=.001){check(Math.abs(at.z()-corner.z())<=2,"Native corner height agrees: "+at+" -> "+corner);acknowledge();}
                else break;
            }
        }
        private void acknowledge(){
            reached=current.index();
            if(ahead.isEmpty()){current=null;active=false;return;}
            var next=ahead.getFirst();var end=PlayerBotGroundNavigation.walk(map,1,at.x(),at.y(),at.z(),next.x(),next.y(),next.z());
            check(Math.hypot(end.x-next.x(),end.y-next.y())<=.15 && Math.abs(end.z-next.z())<=2,"Actual native full-edge handoff admission: "+at+" -> "+next+" ends "+end);
            current=next;physicalTargetZ=end.z;ahead=ahead.subList(1,ahead.size());
        }
    }
    private static PlayerBotWholeRoute route(Mover mover,PlayerBotNavmeshStore store){return new PlayerBotWholeRoute(mover,store,new PlayerBotMeshRoute.Admission(2),PlayerBotNavigationBudget.independent(50));}
    private static PlayerBotWholeRoute.Result ready(PlayerBotWholeRoute route,Point goal)throws Exception{
        PlayerBotWholeRoute.Result result=PlayerBotWholeRoute.Result.WAITING;
        for(int turn=0;turn<10000 && result==PlayerBotWholeRoute.Result.WAITING;turn++){result=route.move(goal);if(result==PlayerBotWholeRoute.Result.WAITING)Thread.sleep(1);}
        check(result!=PlayerBotWholeRoute.Result.WAITING,"Whole planning terminates within bounded fixture turns: "+route.status());return result;
    }
    private static PlayerBotNavigation navigation(PlayerBotWholeRoute route)throws Exception{
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
        var actor=(PlayerBotRouteContinuationCheck.RouteActor)unsafe.allocateInstance(PlayerBotRouteContinuationCheck.RouteActor.class);
        actor.spawned=true;actor.movable=true;actor.world=WORLD;actor.instance=1;
        actor.move=new PlayerBotRouteContinuationCheck.RouteMove(actor);
        var nav=new PlayerBotNavigation(actor,false);
        PlayerBotQuestObjectivesCheck.field(nav,"wholeRoute",route);PlayerBotQuestObjectivesCheck.field(nav,"routeWorld",WORLD);PlayerBotQuestObjectivesCheck.field(nav,"routeInstance",1);
        return nav;
    }
    private static void run(PlayerBotWholeRoute route,Mover mover,Point goal,boolean interruptions)throws Exception{
        var nav=interruptions?navigation(route):null;int nextRequest=0;
        mover.forwardOnly=true;
        for(int tick=0;tick<24000;tick++){
            int now=tick*50;
            if(interruptions && tick>0 && tick%40==0){
                if(tick%80==0)nav.stop();else nav.pause();
                check(!mover.active,"Actual Navigation stop/pause physically aborts the route");
                var actor=(PlayerBotRouteContinuationCheck.RouteActor)PlayerBotRouteContinuationCheck.value(nav,"bot");
                actor.casting=true;int aborts=mover.aborts;
                check(!nav.move(goal.x(),goal.y(),goal.z()),"Actual casting entry rejects physical movement");
                check(!mover.active && mover.aborts==aborts,"Casting cannot republish a retained route");actor.casting=false;
            }
            if(now>=nextRequest){
                var result=route.move(goal);nextRequest+=150;
                check(result!=PlayerBotWholeRoute.Result.FAILED && result!=PlayerBotWholeRoute.Result.FALLBACK,"Retained route stays accepted: "+route.status());
                if(interruptions)check(route.plans()==1 && result!=PlayerBotWholeRoute.Result.WAITING,"Interruptions retain the planned route at "+mover.at+": "+route.status());
                if(result==PlayerBotWholeRoute.Result.ARRIVED)return;
            }
            if(tick%2==0)mover.tick();
        }
        throw new AssertionError("Whole route did not arrive: "+route.status()+" at "+mover.at+" -> "+goal);
    }
    /** Runs last: the learned hole is shared by every later route on the map, as in production. */
    private static void floorHole(PlayerBotNavmeshStore store,GeoMap map)throws Exception{
        Point start=normalized(map,PAIRS[13],0),goal=normalized(map,PAIRS[13],3);var mover=new Mover(map,start);
        try(var route=route(mover,store)){
            check(ready(route,goal)==PlayerBotWholeRoute.Result.MOVING,"Hole fixture starts on an accepted complete route");
            int known=PlayerBotWholeRoute.holes(WORLD);Point corner=new Point(mover.current.x(),mover.current.y(),mover.current.z());
            float t=(float)Math.min(1,1/Math.max(.001,xy(start,corner)));
            mover.hole=new Point(start.x()+(corner.x()-start.x())*t,start.y()+(corner.y()-start.y())*t,start.z()+(corner.z()-start.z())*t);
            mover.blocked=true;mover.active=false;
            check(route.move(goal)==PlayerBotWholeRoute.Result.WAITING && PlayerBotWholeRoute.holes(WORLD)==known+1,"A blocked step over missing game floor is remembered once");
            var again=ready(route,goal);
            check(route.plans()==2,"The remembered hole costs exactly one new plan: "+route.status());
            if(again==PlayerBotWholeRoute.Result.MOVING){
                Point from=mover.at;boolean crosses=false;
                var tail=new ArrayList<WalkPoint>();tail.add(mover.current);tail.addAll(mover.ahead);
                for(var next:tail){
                    double dx=next.x()-from.x(),dy=next.y()-from.y(),length=dx*dx+dy*dy;
                    double u=length<.0001?0:Math.max(0,Math.min(1,((mover.hole.x()-from.x())*dx+(mover.hole.y()-from.y())*dy)/length));
                    if(Math.hypot(mover.hole.x()-(from.x()+dx*u),mover.hole.y()-(from.y()+dy*u))<.05)crosses=true;
                    from=new Point(next.x(),next.y(),next.z());
                }
                System.out.println("HOLE replanned route crosses the hole point: "+crosses+"; first corner "+mover.current);
                check(!crosses,"The new route does not walk through the remembered hole");
            } else System.out.println("HOLE replanned result "+again+": "+route.status());
            mover.blocked=true;mover.active=false;route.move(goal);
            check(PlayerBotWholeRoute.holes(WORLD)<=known+2,"A repeated block cannot flood the hole memory");
            cases++;
        }
    }
    private static void lifecycle(PlayerBotNavmeshStore store,GeoMap map)throws Exception{
        Point start=normalized(map,PAIRS[3],0),goal=normalized(map,PAIRS[3],3);var mover=new Mover(map,start);
        try(var route=route(mover,store)){
            check(ready(route,goal)==PlayerBotWholeRoute.Result.MOVING,"Deterministic complete route is admitted");
            mover.blocked=true;mover.active=false;
            check(route.move(goal)==PlayerBotWholeRoute.Result.WAITING,"First blocked native step replans once");
            check(ready(route,goal)==PlayerBotWholeRoute.Result.MOVING && route.plans()==2,"One blocked retry owns one new whole query");
            cases++;
            mover.blocked=true;mover.active=false;
            check(route.move(goal)==PlayerBotWholeRoute.Result.FAILED && mover.failureCalls==2,"Second block within3m fails into existing unreachable-memory contract");
            check(PlayerBotRouteSearch.recentlyUnreachable(WORLD,start,goal),"Paired native block failure enters real existing unreachable memory");
            cases++;
            route.move(goal);check(mover.failureCalls==2,"Repeated failed goal records no duplicate failure");
            cases++;
        }
        PlayerBotRouteSearch.succeeded(WORLD,start,goal);
        mover=new Mover(map,start);
        try(var route=route(mover,store)){
            ready(route,goal);route.pause();mover.at=new Point(start.x()+10,start.y()+10,start.z());
            check(route.move(goal)==PlayerBotWholeRoute.Result.WAITING,"Significant off-route displacement requests a new plan");
            cases++;
        }
        mover=new Mover(map,start);
        try(var route=route(mover,store)){
            ready(route,goal);
            for(int tick=0;tick<100 && mover.reached<0;tick++){route.move(goal);mover.tick();}
            check(mover.reached>=0,"Changed-goal fixture earns a real prior corner acknowledgment");
            Point changed=normalized(map,PAIRS[5],3);check(ready(route,changed)==PlayerBotWholeRoute.Result.MOVING && route.plans()==2,"Changed goal after progress starts a new route without consuming old acknowledgments");
            check(mover.current!=null && mover.current.index()==0,"Changed destination starts its new corridor at its own first corner");cases++;
            mover.denySafe=true;check(route.move(changed)==PlayerBotWholeRoute.Result.FAILED,"Reloaded current hazard safety revokes retained movement");cases++;
        }
        PlayerBotRouteSearch.succeeded(WORLD,start,normalized(map,PAIRS[5],3));
        mover=new Mover(map,start);
        try(var route=route(mover,store)){
            ready(route,goal);var nav=navigation(route);int aborts=mover.aborts;
            var actor=(PlayerBotRouteContinuationCheck.RouteActor)PlayerBotRouteContinuationCheck.value(nav,"bot");actor.casting=true;
            PlayerBotConfig.NAVIGATION_WHOLE_ROUTE=false;
            check(!PlayerBotNavigation.wholeRouteEnabled(false,true,PlayerBotNavigation.Intent.TRAVEL),"Production feature gate rejects otherwise eligible independent TRAVEL when disabled");
            check(PlayerBotNavigation.wholeRouteEnabled(true,true,PlayerBotNavigation.Intent.TRAVEL),"Same production feature gate admits eligible enabled TRAVEL");
            nav.move(goal.x(),goal.y(),goal.z());nav.pause();nav.stop();
            check(mover.aborts==aborts,"Feature-off production move/cast/stop/pause never enter the new owner");
            cases++;
            PlayerBotConfig.NAVIGATION_WHOLE_ROUTE=true;
        }
        // This raw NPC goal is outside its nearest polygon. Unlike the18 projected
        // baseline pairs, this exercises runtime bounded raw-goal endpoint admission.
        Point nearStart=normalized(map,PAIRS[12],0),nearGoal=normalized(map,PAIRS[12],3);mover=new Mover(map,nearStart);
        try(var route=route(mover,store)){
            ready(route,nearGoal);run(route,mover,nearGoal,false);var nav=navigation(route);
            check(nav.atWholeRouteEnd(nearGoal) && route.nearEnd(nearGoal) && xy(mover.at,nearGoal)<=6 && Math.abs(mover.at.z()-nearGoal.z())<=2.5,"Actual caller API exposes bounded physical endpoint for raw off-polygon goal");
            var roadActor=(PlayerBotRouteContinuationCheck.RouteActor)PlayerBotRouteContinuationCheck.value(nav,"bot");
            roadActor.x=mover.at.x();roadActor.y=mover.at.y();roadActor.z=mover.at.z();
            PlayerBotQuestObjectivesCheck.field(nav,"wholeRouteHandling",true);PlayerBotQuestObjectivesCheck.field(nav,"routeGoal",nearGoal);PlayerBotMetadataCheck.travelIntent(nav);
            var corridor=PlayerBotNavigation.class.getDeclaredMethod("roadCorridor");corridor.setAccessible(true);
            check(!(boolean)corridor.invoke(nav),"Whole ground travel owns road delivery; legacy road tail cannot republish its generation");
            var append=PlayerBotNavigation.class.getDeclaredMethod("appendRoad",Point.class,List.class);append.setAccessible(true);long generation=mover.generation;
            append.invoke(nav,nearGoal,List.of(new Point(nearGoal.x()+10,nearGoal.y(),nearGoal.z())));
            check(mover.generation==generation,"Actual legacy road append admission preserves the whole-route owner");
            check(!nav.roadArrived(nearGoal) && nav.routeFailed(),"A partial endpoint cannot acknowledge a stricter native road waypoint");
            nav.failWholeRouteInteraction(nearGoal);nav.failWholeRouteInteraction(nearGoal);
            check(mover.failureCalls==1 && route.move(nearGoal)==PlayerBotWholeRoute.Result.FAILED && route.plans()==1,"Unusable endpoint interaction records one failure with no new plan");cases++;
        }
        PlayerBotRouteSearch.succeeded(WORLD,nearStart,nearGoal);
        var jobs=new java.util.ArrayDeque<Runnable>();
        try(var waitingStore=new PlayerBotNavmeshStore(Path.of(System.getProperty("java.io.tmpdir"),"whole-route-missing-"+java.util.UUID.randomUUID()),64L*1024*1024,jobs::add)){
            mover=new Mover(map,start);
            try(var route=route(mover,waitingStore)){
                check(route.move(goal)==PlayerBotWholeRoute.Result.WAITING && waitingStore.pendingCount()==2,"Full map and topology are asynchronous cancellable demand");
                navigation(route).pause();check(waitingStore.pendingCount()==0 && !route.planning() && !route.waiting(),"Actual Navigation pause releases every unfinished resource demand");cases++;
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==1,"Pass the maintained game-server directory");
        PlayerBotConfig.NAVIGATION_WHOLE_ROUTE=true;
        Path server=Path.of(args[0]).toAbsolutePath().normalize(),geo=server.resolve("data/geo");
        check(java.nio.file.Files.isRegularFile(geo.resolve("models.mesh")) && java.nio.file.Files.isRegularFile(geo.resolve(WORLD+".geo")),"Missing real Sanctum geometry/models");
        var models=PlayerBotInvisibleFloorCheck.models(geo);
        // Sanctum is a model floor; unlike field maps its source has no heightmap.
        var map=PlayerBotInvisibleFloorCheck.map(geo,WORLD,models,java.nio.file.Files.isRegularFile(geo.resolve(WORLD+".png")));
        var loader=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var store=new PlayerBotNavmeshStore(server.getParent().resolve("target-deploy/game-server/data/navmesh"),3072L*1024*1024,loader::execute)){
            var inputs=inputs(map,store);
            int complete=0,partial=0,interrupted=0;
            for(int i=0;i<PAIRS.length;i++){
                System.out.println("START PAIR "+i);
                Point start=inputs.get(i)[0],goal=inputs.get(i)[1];var mover=new Mover(map,start);
                try(var route=route(mover,store)){
                    check(ready(route,goal)==PlayerBotWholeRoute.Result.MOVING,"Real pair "+i+" has accepted whole route: "+route.status());
                    run(route,mover,goal,false);
                    check(route.plans()==1 && route.generations()==1,"150ms same-goal refresh has one plan and no new physical generation, pair "+i);
                    check(mover.walked<=route.plannedLength()*1.10+.05,"Walked length stays within110% of planned, pair "+i);
                    check(route.terminal(goal),"Physical endpoint is a terminal receipt, pair "+i);
                    boolean near=route.nearEnd(goal);
                    check(near==(i==0 || i==1 || i==2 || i==4 || i==6 || i==7 || i==8 || i==9 || i==11),"Historical pair keeps its complete/island classification: "+i+" graph-complete="+PlayerBotRouteContinuationCheck.value(route,"complete")+" actual="+mover.at+" goal="+goal+" endpoint="+route.endpoint());
                    check(xy(mover.at,goal)<=6 && Math.abs(mover.at.z()-goal.z())<=2.5,"Accepted endpoint is within6m/2.5m of requested goal, pair "+i);
                    if(near)partial++;else{complete++;check(xy(mover.at,goal)<=.15,"Complete route walks exact requested goal, pair "+i);}
                    cases++;
                    System.out.printf(java.util.Locale.ROOT,"PAIR %02d %s length=%.1f walked=%.1f endpoint-distance=%.2f%n",i,near?"near end":"complete",route.plannedLength(),mover.walked,xy(mover.at,goal));
                    if(!near && interrupted<5){
                        var paused=new Mover(map,start);
                        try(var other=route(paused,store)){ready(other,goal);run(other,paused,goal,true);check(other.plans()==1,"Actual Navigation stop/pause/cast preserves one logical plan");check(paused.walked<=other.plannedLength()*1.10+.05,"Interruptions do not revisit passed corners");interrupted++;cases++;}
                    }
                }
            }
            check(complete==9 && partial==9,"Historical18 pairs retain9 complete and9 accepted island ends: "+complete+"/"+partial);
            check(interrupted==5,"Five complete real routes exercise actual stop/pause/cast entries");
            lifecycle(store,map);
            floorHole(store,map);
        }finally{loader.shutdownNow();PlayerBotConfig.NAVIGATION_WHOLE_ROUTE=false;}
        check(cases==33,"All33 whole-route cases ran");
        System.out.println("OK: "+cases+" whole-route cases; assertions="+checks+";18 real Sanctum pairs,5 interruption routes; no World/SQL/game tasks");
    }
    private PlayerBotWholeRouteCheck(){}
}
