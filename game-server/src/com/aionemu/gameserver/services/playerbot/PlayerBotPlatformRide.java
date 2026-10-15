package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Physical walking/boarding/carry/dismount adapter. Time advances the authored floor,
 * not quest progress or a teleport. Native body/door/support checks remain mandatory. */
public final class PlayerBotPlatformRide {
    public enum State { BOARDING, RIDING, DISEMBARKING, COMPLETE, FAILED }
    public record Step(float x,float y,float z,State state){}
    public static final class Plan {
        private final PlayerBotAnimatedLinks.Ride ride;
        private final int world,instance;
        private volatile List<PlayerBotHazards.Area> hazards;
        private State state=State.BOARDING;
        private long previousTime;
        private double[] previous;
        private double[] relative;
        private boolean interrupted;
        Plan(Player player,PlayerBotAnimatedLinks.Ride ride,List<PlayerBotHazards.Area> hazards){
            this.ride=ride;world=player.getWorldId();instance=player.getInstanceId();this.hazards=List.copyOf(hazards);
            previous=new double[]{player.getX(),player.getY(),player.getZ()};previousTime=PlayerBotAnimatedScene.uptime();
            relative=ride.local().clone();
        }
        PlayerBotAnimatedLinks.Ride route(){return ride;}
        public int entityId(){return ride.entity().id;}
        public int part(){return ride.part();}
        public int wirePart(){return ride.entity().model.nodes[part()].wirePart;}
        public State state(){return state;}
        public boolean onboard(){return state==State.RIDING || state==State.DISEMBARKING;}
        public void interrupt(){interrupted=true;}
        public void hazards(List<PlayerBotHazards.Area> areas){hazards=List.copyOf(areas);}
        public double[] local(){return ride.entity().frame(previousTime).vehicle(part(),previous);}
        public boolean begin(Player player){
            if(!valid(player) || PositionUtil.getDistance(player,ride.from().ground().x(),ride.from().ground().y(),ride.from().ground().z())>.75)return false;
            long time=PlayerBotAnimatedScene.uptime();var frame=ride.entity().frame(time);double[] destination=frame.point(part(),state==State.BOARDING?ride.local():relative);
            var dock=ride.from().ground();double[] shore={dock.x(),dock.y(),dock.z()};
            // Ground waypoints are consumed within a radius. First finish that
            // small real walking approach; do not require an exact pose already
            // reached, or expand the physical boarding span by that radius.
            if(distance(shore,destination)>1.3 || !frame.supports(part(),destination)
                || !walkable(frame,previous,shore) || !walkable(frame,shore,destination))return false;
            previousTime=time;return true;
        }
        public boolean valid(Player player){return player.isSpawned() && !player.isDead() && !player.isFlying() && player.getWorldId()==world && player.getInstanceId()==instance;}
        public Step advance(Player player){
            if(!valid(player) || state==State.FAILED || state==State.COMPLETE){state=valid(player)?state:State.FAILED;return step();}
            long time=PlayerBotAnimatedScene.uptime();var frame=ride.entity().frame(time);double[] destination=frame.point(part(),state==State.BOARDING?ride.local():relative);
            if(state==State.BOARDING){
                if(interrupted || !player.canPerformMove() || player.isCasting() || player.isTrading() || player.isLooting()){state=State.FAILED;return step();}
                double speed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(player,
                    com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,player.getGameStats().getMovementSpeedFloat());
                var dock=ride.from().ground();double[] shore={dock.x(),dock.y(),dock.z()};
                boolean finishingApproach=distance(previous,shore)>.01;
                if(finishingApproach)destination=shore;
                double elapsed=Math.min(200,unsignedElapsed(previousTime,time))/1000.;double separation=distance(previous,destination);
                double fraction=separation<.001?1:Math.min(1,speed*elapsed/separation);double[] next=interpolate(previous,destination,fraction);
                if(separation>1.5 || !walkable(frame,previous,next)){state=State.FAILED;return step();}
                previous=next;if(fraction==1 && !finishingApproach){state=State.RIDING;}
            }else {
                long elapsed=unsignedElapsed(previousTime,time);int steps=Math.max(1,(int)Math.max(Math.ceil(elapsed/50.),Math.ceil(distance(previous,destination)/.2)));
                // A scheduler outage cannot authorize an unchecked long carry segment.
                if(steps>256){state=State.FAILED;return step();}
                double[] from=previous;
                for(int i=1;i<=steps;i++){
                    long sample=(previousTime+elapsed*i/steps)&0xffffffffL;var moving=ride.entity().frame(sample);double[] next=moving.point(part(),relative);
                    if(!moving.supports(part(),next) || !bodyClear(moving,from,next)){state=State.FAILED;return step();}
                    from=next;
                }
                previous=destination;
                Point goal=ride.to().ground();double[] landing={goal.x(),goal.y(),goal.z()};
                if(interrupted && staticSupport(previous))landing=previous;
                if(distance(previous,landing)<=1.3 && staticSupport(landing) && walkable(frame,previous,landing)){
                    if(player.canPerformMove() && !player.isCasting()){
                        double speed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(player,
                            com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,player.getGameStats().getMovementSpeedFloat());
                        double fraction=Math.min(1,speed*Math.min(200,elapsed)/1000./Math.max(.001,distance(previous,landing)));
                        previous=interpolate(previous,landing,fraction);state=fraction==1?State.COMPLETE:State.DISEMBARKING;
                        if(fraction<1)relative=frame.local(part(),previous);
                    }
                }
            }
            previousTime=time;return step();
        }
        private Step step(){return new Step((float)previous[0],(float)previous[1],(float)previous[2],state);}
        private boolean walkable(PlayerBotAnimatedScene.Frame frame,double[] from,double[] to){
            int count=Math.max(1,(int)Math.ceil(distance(from,to)/.1));if(count>16)return false;
            for(int i=0;i<=count;i++){
                double[] at=interpolate(from,to,i/(double)count);
                for(int sample=-1;sample<8;sample++){
                    float x=(float)(at[0]+(sample<0?0:.3*Math.cos(sample*Math.PI/4))),y=(float)(at[1]+(sample<0?0:.3*Math.sin(sample*Math.PI/4)));
                    var floor=frame.floor(part(),x,y,(float)at[2]+.2f,(float)at[2]-.2f);
                    float ground=GeoService.getInstance().playerBotStaticFloor(world,instance,x,y,(float)at[2]);
                    if(floor==null && !Float.isFinite(ground))return false;
                }
            }
            return bodyClear(frame,from,to);
        }
        private boolean staticSupport(double[] point){
            for(int sample=-1;sample<8;sample++){
                float x=(float)(point[0]+(sample<0?0:.3*Math.cos(sample*Math.PI/4))),y=(float)(point[1]+(sample<0?0:.3*Math.sin(sample*Math.PI/4)));
                if(!Float.isFinite(GeoService.getInstance().playerBotStaticFloor(world,instance,x,y,(float)point[2])))return false;
            }
            return true;
        }
        private boolean bodyClear(PlayerBotAnimatedScene.Frame frame,double[] from,double[] to){
            var a=new Point((float)from[0],(float)from[1],(float)from[2]);var b=new Point((float)to[0],(float)to[1],(float)to[2]);
            if(!PlayerBotHazards.safePath(a,b,hazards) || !GeoService.getInstance().validatePlayerBotHeightSegment(world,instance,new Vector3f(a.x(),a.y(),a.z()),new Vector3f(b.x(),b.y(),b.z())))return false;
            for(double height:new double[]{.05,.75,1.5})for(int side=0;side<8;side++){
                double dx=.3*Math.cos(side*Math.PI/4),dy=.3*Math.sin(side*Math.PI/4);
                if(!frame.clear(new double[]{from[0]+dx,from[1]+dy,from[2]+height},new double[]{to[0]+dx,to[1]+dy,to[2]+height},part()))return false;
                double[] center={to[0],to[1],to[2]+height},edge={to[0]+dx,to[1]+dy,to[2]+height};
                if(!frame.clear(center,edge,-1) || !GeoService.getInstance().playerBotClearBodyRay(world,instance,
                    new Vector3f((float)center[0],(float)center[1],(float)center[2]),new Vector3f((float)edge[0],(float)edge[1],(float)edge[2])))return false;
            }
            for(int side=-1;side<8;side++){
                double dx=side<0?0:.3*Math.cos(side*Math.PI/4),dy=side<0?0:.3*Math.sin(side*Math.PI/4);
                double[] low={to[0]+dx,to[1]+dy,to[2]+.05},high={to[0]+dx,to[1]+dy,to[2]+1.5};
                if(!frame.clear(low,high,-1) || !GeoService.getInstance().playerBotClearBodyRay(world,instance,
                    new Vector3f((float)low[0],(float)low[1],(float)low[2]),new Vector3f((float)high[0],(float)high[1],(float)high[2])))return false;
            }
            return true;
        }
    }
    static Plan plan(Player player,PlayerBotAnimatedLinks.Ride ride,List<PlayerBotHazards.Area> hazards){return new Plan(player,ride,hazards);}
    static boolean docking(PlayerBotAnimatedLinks.Ride ride){
        double phase=ride.entity().phase(PlayerBotAnimatedScene.uptime())-ride.from().time();
        if(phase<0)phase+=ride.entity().model.duration;
        return phase/ride.entity().speed<=1;
    }
    private static long unsignedElapsed(long from,long to){return (to-from)&0xffffffffL;}
    private static double[] interpolate(double[] a,double[] b,double t){return new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};}
    private static double distance(double[] a,double[] b){double total=0;for(int i=0;i<3;i++)total+=(a[i]-b[i])*(a[i]-b[i]);return Math.sqrt(total);}
    private PlayerBotPlatformRide(){}
}
