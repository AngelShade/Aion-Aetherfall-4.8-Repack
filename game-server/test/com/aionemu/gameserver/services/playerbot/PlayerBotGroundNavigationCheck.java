package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.geoEngine.scene.DespawnableNode.DespawnableType;

/** Isolated native mesh checks: no actors, database, services, IDs or GameServer startup. */
public final class PlayerBotGroundNavigationCheck {
    private static int checks;
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);checks++;}
    private static Mesh mesh(float[] vertices,short[] faces) {
        Mesh mesh=new Mesh();mesh.setVertices(FloatBuffer.wrap(vertices));mesh.setIndices(ShortBuffer.wrap(faces));mesh.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());mesh.updateBound();return mesh;
    }
    private static Mesh floor(float from,float to,float height) {
        return mesh(new float[]{from,-10,height,to,-10,height,to,10,height,from,10,height},new short[]{0,1,2,0,2,3});
    }
    private static Mesh leaf() {
        return mesh(new float[]{0,-1.5f,0,0,1.5f,0,0,1.5f,3,0,-1.5f,3},new short[]{0,1,2,0,2,3});
    }
    private static void add(GeoMap map,Mesh mesh,String name) {
        Geometry geometry=new Geometry(name,mesh);geometry.setTransform(new Matrix3f(),new Vector3f(),new Vector3f(1,1,1));geometry.updateModelBound();map.attachChild(geometry);
    }
    private static DespawnableNode door(GeoMap map,Mesh mesh,int id,DespawnableType type,Vector3f position) {
        DespawnableNode node=new DespawnableNode();node.id=id;node.type=type;node.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());node.attachChild(new Geometry("arbitrary-door",mesh));
        node.setTransform(new Matrix3f(),position,new Vector3f(1,1,1));node.updateModelBound();map.attachChild(node);return node;
    }
    private static Vector3f walk(GeoMap map,int instance,float x,float y,float z,float tx,float ty) {return PlayerBotGroundNavigation.walk(map,instance,x,y,z,tx,ty);}
    public static void main(String[] args) {
        GeoMap map=new GeoMap(1);add(map,floor(-10,10,0),"floor");Mesh shared=leaf();
        var closed=door(map,shared,123,DespawnableType.DOOR_STATE1,new Vector3f());
        var opened=door(map,shared,123,DespawnableType.DOOR_STATE2,new Vector3f());map.updateModelBound();
        map.setDoorState(1,123,false);check(walk(map,1,-3,0,0,3,0).x<0,"closed door blocks");
        map.setDoorState(1,123,true);check(walk(map,1,-3,0,0,3,0).x>2.9,"opened aliased leaf clears in arbitrary map");
        map.setDoorState(2,123,false);check(walk(map,2,-3,0,0,3,0).x<0,"another instance remains closed");
        map.setDoorState(1,123,false);check(walk(map,1,-3,0,0,3,0).x<0,"cached probe respects reclosed door");
        check(walk(map,1,-.1f,0,0,1,0).x>=-.101f,"near closed door never moves backwards from native collision offset");
        map.setDoorState(1,123,true);closed.setActive(1,true);check(walk(map,1,-3,0,0,3,0).x<0,"inconsistent both-active states fail closed");closed.setActive(1,false);
        add(map,mesh(new float[]{-10,-1.6f,0,10,-1.6f,0,10,-1.6f,4,-10,-1.6f,4,-10,1.6f,0,10,1.6f,0,10,1.6f,4,-10,1.6f,4},new short[]{0,1,2,0,2,3,4,5,6,4,6,7}),"corridor walls");map.updateModelBound();
        check(!map.canSee(-3,0,1.25f,6,0,1.25f,1,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE),"native sight remains blocked by stale opened mesh before crossing");
        Vector3f firing=null;
        for(double factor:new double[]{1,.85,.7,.55}) {
            for(double angle:new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2}) {
                float tx=6+(float)(Math.cos(angle)*-10*factor),ty=(float)(Math.sin(angle)*-10*factor);
                Vector3f candidate=walk(map,1,-3,0,0,tx,ty);
                if(Math.hypot(candidate.x-tx,candidate.y-ty)<.3 && map.canSee(6,0,1.25f,candidate.x,candidate.y,candidate.z+1.25f,1,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)){firing=candidate;break;}
            }
            if(firing!=null)break;
        }
        check(firing!=null,"ranged sight recovery finds a reachable firing position");
        check(firing.x>0 && Math.hypot(firing.x-6,firing.y)>=5,"firing position crosses opened doorway while retaining range");
        check(walk(map,1,-3,0,0.8f,3,0).x>2.9,"small initial floor offset recovers by walking");
        check(walk(map,1,-3,0,4,3,0).x==-3,"different floor is not invented below unsupported actor");
        check(walk(map,1,Float.NaN,0,0,3,0).x!=walk(map,1,Float.NaN,0,0,3,0).x,"invalid origin rejected");
        add(map,leaf(),"ordinary wall");map.updateModelBound();check(walk(map,1,-3,0,0,3,0).x<0,"ordinary wall still blocks behind opened aliased door");
        GeoMap moved=new GeoMap(2);add(moved,floor(-10,10,0),"floor");
        door(moved,shared,321,DespawnableType.DOOR_STATE1,new Vector3f(5,0,0));door(moved,shared,321,DespawnableType.DOOR_STATE2,new Vector3f());moved.updateModelBound();moved.setDoorState(1,321,true);
        check(walk(moved,1,-3,0,0,3,0).x<0,"correctly transformed opened leaf is retained");
        GeoMap duplicate=new GeoMap(3);add(duplicate,floor(-10,10,0),"floor");door(duplicate,shared,7,DespawnableType.DOOR_STATE1,new Vector3f());door(duplicate,leaf(),7,DespawnableType.DOOR_STATE2,new Vector3f());duplicate.updateModelBound();duplicate.setDoorState(1,7,true);
        check(walk(duplicate,1,-3,0,0,3,0).x>2.9,"duplicate mesh records also detect aliased opened collision");
        GeoMap distinct=new GeoMap(10);add(distinct,floor(-10,10,0),"floor");door(distinct,shared,7,DespawnableType.DOOR_STATE1,new Vector3f());door(distinct,mesh(new float[]{0,-1.5f,0,0,1.5f,0,0,1.5f,2.8f,0,-1.5f,2.8f},new short[]{0,1,2,0,2,3}),7,DespawnableType.DOOR_STATE2,new Vector3f());distinct.updateModelBound();distinct.setDoorState(1,7,true);
        check(walk(distinct,1,-3,0,0,3,0).x<0,"different opened geometry is retained");
        GeoMap cliff=new GeoMap(4);add(cliff,floor(-10,0,0),"ledge");add(cliff,floor(0,10,-4),"lower floor");cliff.updateModelBound();check(walk(cliff,1,-3,0,0,3,0).x<=0,"cliff cannot be walked through");
        GeoMap gap=new GeoMap(5);add(gap,floor(-10,-1,0),"left");add(gap,floor(1,10,0),"right");gap.updateModelBound();check(walk(gap,1,-3,0,0,3,0).x<=-1,"unsupported gap remains blocked");
        GeoMap frame=new GeoMap(8);add(frame,floor(-10,10,0),"floor");
        Mesh frameMesh=mesh(new float[]{0,-1.5f,0,0,-1,0,0,-1,3,0,-1.5f,3,0,1,0,0,1.5f,0,0,1.5f,3,0,1,3,0,-1,2.7f,0,1,2.7f,0,1,3,0,-1,3},new short[]{0,1,2,0,2,3,4,5,6,4,6,7,8,9,10,8,10,11});
        door(frame,frameMesh,88,DespawnableType.DOOR_STATE1,new Vector3f());door(frame,frameMesh,88,DespawnableType.DOOR_STATE2,new Vector3f());frame.updateModelBound();frame.setDoorState(1,88,true);
        check(walk(frame,1,-3,1.25f,0,3,1.25f).x<0,"shared door frame remains solid");
        GeoMap slope=new GeoMap(6);add(slope,mesh(new float[]{-4,-10,0,4,-10,8,4,10,8,-4,10,0},new short[]{0,1,2,0,2,3}),"slope");slope.updateModelBound();check(walk(slope,1,-3,0,1,3,0).x>2.9,"walkable ramp follows ground");
        GeoMap stair=new GeoMap(7);add(stair,floor(-10,0,0),"low");add(stair,floor(0,10,.3f),"step");stair.updateModelBound();check(walk(stair,1,-3,0,0,3,0).x>2.9,"small step can be walked");
        Vector3f actor=new Vector3f(-3,0,0);
        for(int tick=0;tick<12;tick++) { actor=walk(stair,1,actor.x,actor.y,actor.z,Math.min(3,actor.x+.5f),0);check(Math.abs(actor.z-(actor.x<0?0:.3f))<.01,"each controller-sized step stays on the floor"); }
        check(actor.x>2.9,"ground steps complete the route without straight-height interpolation");
        GeoMap steep=new GeoMap(9);add(steep,mesh(new float[]{-4,-10,0,4,-10,16,4,10,16,-4,10,0},new short[]{0,1,2,0,2,3}),"steep");steep.updateModelBound();check(walk(steep,1,-3,0,2,3,0).x<0,"steep surfaces remain blocked");
        var start=new PlayerBotNavigation.Point(0,0,0);var goal=new PlayerBotNavigation.Point(8,0,0);
        var route=PlayerBotPathfinder.find(start,goal,(from,x,y)->{
            for(int i=1;i<=20;i++){float sx=from.x()+(x-from.x())*i/20,sy=from.y()+(y-from.y())*i/20;
                if(sx>2.5 && sx<4.5 && (sy<.7 || sy>1.3))return null;}
            return new PlayerBotNavigation.Point(x,y,0);
        },128,()->false);
        check(!route.isEmpty() && route.getLast().equals(goal),"one-metre search finds narrow off-axis passage");
        check(PlayerBotPathfinder.find(start,goal,(from,x,y)->null,128,()->false).isEmpty(),"routing cannot invent edges");
        check(PlayerBotPathfinder.find(start,goal,(from,x,y)->new PlayerBotNavigation.Point(x,y,0),128,()->true).isEmpty(),"search deadline respected");
        check(PlayerBotPathfinder.find(start,goal,(from,x,y)->new PlayerBotNavigation.Point(x,y,20),128,()->false).isEmpty(),"route rejects a different elevation layer");
        var around=PlayerBotPathfinder.find(start,new PlayerBotNavigation.Point(10,0,0),(from,x,y)->{
            for(int i=1;i<=20;i++){float sx=from.x()+(x-from.x())*i/20,sy=from.y()+(y-from.y())*i/20;if(sx>=3&&sx<=5&&sy>=-4&&sy<=4)return null;}
            return new PlayerBotNavigation.Point(x,y,0);
        },128,()->false);
        check(!around.isEmpty(),"existing broad wall detour remains available within node budget");
        GeoMap transparent=new GeoMap(77);
        Mesh visibleFloor=floor(-10,10,0);visibleFloor.setCollisionIntentions((byte)(CollisionIntention.PHYSICAL_SEE_THROUGH.getId()|CollisionIntention.WALK.getId()));
        add(transparent,visibleFloor,"arbitrary sight-transparent floor");transparent.updateModelBound();
        check(Float.isNaN(transparent.getZ(0,0,1,-1,1,true)),"native physical-only query reproduces missing support");
        check(PlayerBotGroundSupport.floor(transparent,1,0,0,1,-1)==0,"transparent physical floor is real support");
        check(walk(transparent,1,-3,0,0,3,0).x>2.9,"transparent floor can be crossed");
        actor=new Vector3f(-3,0,0);
        for(int tick=0;tick<60;tick++)actor=walk(transparent,1,actor.x,actor.y,actor.z,Math.min(3,actor.x+.1f),0);
        check(actor.x>2.9 && Math.abs(actor.z)<.001,"controller-sized steps remain grounded");
        add(transparent,leaf(),"opaque wall");transparent.updateModelBound();check(walk(transparent,1,-3,0,0,3,0).x<0,"transparent floor retains solid walls");
        GeoMap transparentCliff=new GeoMap(78);Mesh upper=floor(-10,0,0),lower=floor(0,10,-4);
        upper.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());lower.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());
        add(transparentCliff,upper,"upper");add(transparentCliff,lower,"lower");transparentCliff.updateModelBound();
        check(walk(transparentCliff,1,-3,0,0,3,0).x<=0,"transparent cliff remains blocked");
        GeoMap transparentSteep=new GeoMap(79);Mesh incline=mesh(new float[]{-4,-10,0,4,-10,16,4,10,16,-4,10,0},new short[]{0,1,2,0,2,3});incline.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());add(transparentSteep,incline,"steep");transparentSteep.updateModelBound();
        check(walk(transparentSteep,1,-3,0,2,3,0).x<0,"transparent steep slope remains blocked");
        GeoMap walkOnly=new GeoMap(80);Mesh noPhysical=floor(-10,10,0);noPhysical.setCollisionIntentions(CollisionIntention.WALK.getId());add(walkOnly,noPhysical,"walk flag only");walkOnly.updateModelBound();
        check(walk(walkOnly,1,-3,0,0,3,0).x==-3,"WALK-only flag cannot invent physical support");
        GeoMap stacked=new GeoMap(81);add(stacked,floor(-10,10,0),"ordinary lower");Mesh high=floor(-10,10,5);high.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());add(stacked,high,"transparent upper");stacked.updateModelBound();
        check(PlayerBotGroundSupport.floor(stacked,1,0,0,5.6f,3.75f)==5,"upper layer selected");
        check(walk(stacked,1,-3,0,5,3,0).z==5,"upper floor never drops to lower layer");
        check(walk(stacked,1,-3,0,3,3,0).x==-3,"unsupported height cannot snap to another layer");
        GeoMap transition=new GeoMap(82);add(transition,floor(-10,0,0),"ordinary support");Mesh continuation=floor(0,10,0);continuation.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());add(transition,continuation,"transparent continuation");transition.updateModelBound();
        check(walk(transition,1,-3,0,0,3,0).x>2.9,"physical to see-through transition crosses");
        check(walk(transition,1,3,0,0,-3,0).x< -2.9,"see-through to physical transition crosses");
        GeoMap overlap=new GeoMap(83);add(overlap,floor(-10,10,0),"real floor");Mesh shell=floor(-10,10,.35f);shell.setCollisionIntentions(CollisionIntention.PHYSICAL_SEE_THROUGH.getId());add(overlap,shell,"overlapping shell");overlap.updateModelBound();
        check(PlayerBotGroundSupport.floor(overlap,1,0,0,.6f,-1.25f)==.35f,"physical floor types share native highest-support semantics");
        var sharedLeaf=leaf();door(transition,sharedLeaf,555,DespawnableType.DOOR_STATE1,new Vector3f());door(transition,sharedLeaf,555,DespawnableType.DOOR_STATE2,new Vector3f());transition.updateModelBound();transition.setDoorState(1,555,false);
        check(walk(transition,1,-3,0,0,3,0).x<0,"closed door still blocks support transition");
        transition.setDoorState(1,555,true);check(walk(transition,1,-3,0,0,3,0).x>2.9,"opened aliased door crosses support transition");
        transition.setDoorState(2,555,false);check(walk(transition,2,-3,0,0,3,0).x<0,"separate instance remains closed");
        System.out.println("OK: "+checks+" generic door/floor/route checks; no native actors or service startup");
    }
}
