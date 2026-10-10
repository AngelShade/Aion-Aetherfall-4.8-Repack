package com.aionemu.gameserver.services.playerbot;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import com.aionemu.gameserver.geoEngine.collision.*;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;

/** Collision result equivalence and mesh-work counts; no world actors, IDs or database. */
public final class PlayerBotCollisionPerformanceCheck {
    private static int checks;
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);checks++;}
    private static final class Counted extends Geometry {
        int calls;
        int boundReads;
        Counted(String name,Mesh mesh) {super(name,mesh);}
        @Override public int collideWith(Collidable query,CollisionResults results) {calls++;return super.collideWith(query,results);}
        @Override public com.aionemu.gameserver.geoEngine.bounding.BoundingVolume getWorldBound() {boundReads++;return super.getWorldBound();}
    }
    private static Counted add(GeoMap map,String name,float x,float y,float z,boolean floor) {
        var mesh=new Mesh();
        mesh.setVertices(FloatBuffer.wrap(floor?new float[]{-12,-12,0,12,-12,0,12,12,0,-12,12,0}:new float[]{0,-2,-1,0,2,-1,0,2,4,0,-2,4}));
        mesh.setIndices(ShortBuffer.wrap(new short[]{0,1,2,0,2,3}));mesh.setCollisionIntentions(CollisionIntention.PHYSICAL.getId());mesh.updateBound();
        var g=new Counted(name,mesh);g.setTransform(new Matrix3f(),new Vector3f(x,y,z),new Vector3f(1,1,1));g.updateModelBound();map.attachChild(g);return g;
    }
    private record Hit(Geometry geometry,float distance,Vector3f point) {}
    private static List<Hit> hits(CollisionResults results) {
        var hits=new ArrayList<Hit>();for(var hit:results)hits.add(new Hit(hit.getGeometry(),hit.getDistance(),hit.getContactPoint().clone()));return hits;
    }
    public static void main(String[] args) {
        var map=new GeoMap(97);add(map,"local floor",0,0,0,true);
        for(int i=0;i<10;i++)add(map,"near wall "+i,-8+i*1.7f,0,0,false);
        var far=new ArrayList<Counted>();for(int i=0;i<128;i++)far.add(add(map,"far "+i,100+i,0,0,false));
        // A large mesh spans chunk boundaries: filtering must use its world bound, not its centre's chunk ID.
        add(map,"wide floor",256,0,-2,true);map.updateModelBound();
        var random=new Random(417);
        for(int i=0;i<400;i++) {
            var origin=new Vector3f(random.nextFloat()*24-12,random.nextFloat()*6-3,random.nextFloat()*8-3);
            float x=random.nextFloat()*24-12,y=random.nextFloat()*6-3,z=random.nextFloat()*8-3;
            var before=map.getCollisions(origin,x,y,z,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE);
            var after=map.getPlayerBotCollisions(origin,x,y,z,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE);
            check(hits(before).equals(hits(after)),"all native contacts retained for segment "+i);
        }
        for(int i=0;i<40;i++) {
            float x=244+i*.6f;
            check(hits(map.getCollisions(new Vector3f(x,0,2),x,0,-3,1,CollisionIntention.PHYSICAL.getId(),IgnoreProperties.ANY_RACE))
                .equals(hits(map.getPlayerBotCollisions(new Vector3f(x,0,2),x,0,-3,1,CollisionIntention.PHYSICAL.getId(),IgnoreProperties.ANY_RACE))),"cross-chunk support "+i);
        }
        far.forEach(g->g.calls=0);
        map.getCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE);
        int legacy=far.stream().mapToInt(g->g.calls).sum();far.forEach(g->g.calls=0);
        map.getPlayerBotCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE);
        int bounded=far.stream().mapToInt(g->g.calls).sum();
        check(legacy==128,"baseline visits every distant mesh in intersected native chunk");
        check(bounded==0,"bounded segment rejects all distant meshes before collision routines");
        var unrestricted=new CollisionResults(CollisionIntention.PHYSICAL.getId(),1);
        check(unrestricted.intersectsSegmentBounds(far.getFirst().getWorldBound()),"ordinary native queries remain unrestricted");
        unrestricted.boundSegment(-1,0,0,1,0,0);
        check(unrestricted.intersectsSegmentBounds(null),"unknown bounds fall back to native traversal");
        check(!unrestricted.intersectsSegmentBounds(far.getFirst().getWorldBound()),"finite bounds reject distant scene objects");
        // 009 rejected distant meshes, but still scanned their bounds for every body ray.
        far.forEach(g->g.boundReads=0);
        map.getCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE);
        int scanned=far.stream().mapToInt(g->g.boundReads).sum();far.forEach(g->g.boundReads=0);
        map.getPlayerBotCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE);
        int indexed=far.stream().mapToInt(g->g.boundReads).sum();
        check(scanned>=128 && indexed==0,"warm indexed finite probes no longer scan every distant child bound");
        var moved=far.getFirst();moved.setTransform(new Matrix3f(),new Vector3f(0,0,0),new Vector3f(1,1,1));map.updateModelBound();
        check(hits(map.getCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE))
            .equals(hits(map.getPlayerBotCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE))),"changed transforms invalidate index bounds and retain new wall contacts");
        moved.getParent().detachChild(moved);map.updateModelBound();
        check(hits(map.getCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE))
            .equals(hits(map.getPlayerBotCollisions(new Vector3f(-3,0,1),3,0,1,1,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.ANY_RACE))),"detached geometry cannot remain in the finite-probe index");
        System.out.println("OK: "+checks+" finite-probe equivalence/work checks; distant mesh calls "+legacy+" -> "+bounded+"; no world/DB/IDs");
    }
}
