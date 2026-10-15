package com.aionemu.tools.navmesh;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.recast4j.detour.*;
import org.recast4j.detour.io.MeshDataReader;

/** Offline round-trip/path inspection. A generated candidate is never a gameplay movement command. */
public final class Inspect {
    private Inspect() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3)
            throw new IllegalArgumentException("Inspect MAP_DIRECTORY [START_X,Y,Z END_X,Y,Z] (Aion coordinates)");
        var nav = load(Path.of(args[0]));
        System.out.println("Verified and connected " + nav.getTileCount() + " tiles");
        if (args.length == 1) return;
        float[] start = aionPosition(args[1]), end = aionPosition(args[2]);
        var query = new NavMeshQuery(nav); var filter = new DefaultQueryFilter();
        var a = query.findNearestPoly(start, new float[]{1, .75f, 1}, filter);
        var b = query.findNearestPoly(end, new float[]{1, .75f, 1}, filter);
        if (a.failed() || b.failed() || a.result.getNearestRef() == 0 || b.result.getNearestRef() == 0)
            throw new IOException("Endpoint has no nearby supported navigation polygon on its floor");
        var status = query.initSlicedFindPath(a.result.getNearestRef(), b.result.getNearestRef(),
                a.result.getNearestPos(), b.result.getNearestPos(), filter, 0);
        int turns = 0;
        while (status.isInProgress() && turns++ < 4096) status = query.updateSlicedFindPath(128).status;
        if (status.isInProgress()) throw new IOException("Query exceeded offline expansion limit");
        var path = query.finalizeSlicedFindPath();
        if (path.failed() || path.result.isEmpty()) throw new IOException("No path");
        boolean complete = path.result.get(path.result.size()-1) == b.result.getNearestRef();
        System.out.println("Path complete=" + complete + ", polygons=" + path.result.size() + ", batches=" + turns);
        float[] endpoint=b.result.getNearestPos();
        if(!complete) {
            var closest=query.closestPointOnPoly(path.result.get(path.result.size()-1),endpoint);
            if(closest.failed()) throw new IOException("Cannot resolve partial endpoint");
            endpoint=closest.result.getClosest();
        }
        var points = query.findStraightPath(a.result.getNearestPos(), endpoint, path.result, 4096, 0);
        if (points.failed()) throw new IOException("Straight-path extraction failed");
        for (var point : points.result) {
            var p = point.getPos(); System.out.println(p[0] + "," + p[2] + "," + p[1]);
        }
        if (!complete) throw new IOException("Disconnected/partial route; not an arrival or whole-map failure");
    }
    static float[] aionPosition(String text) {
        String[] v=text.split(",",-1);
        if(v.length!=3) throw new IllegalArgumentException("Expected X,Y,Z");
        float x=Float.parseFloat(v[0]), y=Float.parseFloat(v[1]), z=Float.parseFloat(v[2]);
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)) throw new IllegalArgumentException("Nonfinite coordinate");
        return new float[]{x,z,y};
    }
    static NavMesh load(Path dir) throws IOException {
        Properties manifest=Artifacts.read(dir.resolve("map.properties"));
        if (!"aion-detour-1".equals(manifest.getProperty("format")) || !"1.5.8".equals(manifest.getProperty("recast4j"))
                || !"BUILT_REQUIRES_RUNTIME_VALIDATION".equals(manifest.getProperty("status")))
            throw new IOException("Incomplete or incompatible map build");
        int nx=Integer.parseInt(manifest.getProperty("tiles.x")), ny=Integer.parseInt(manifest.getProperty("tiles.y"));
        if(nx<1 || ny<1 || (long)nx*ny>1_000_000) throw new IOException("Invalid tile count");
        var params=new NavMeshParams(); params.maxTiles=nx*ny; params.maxPolys=32768;
        params.tileWidth=params.tileHeight=Float.parseFloat(manifest.getProperty("tile.metres"));
        String[] origin=manifest.getProperty("origin").replace("[", "").replace("]", "").split(",");
        for(int i=0;i<3;i++)params.orig[i]=Float.parseFloat(origin[i].trim());
        var mesh=new NavMesh(params,6);
        for(int y=0;y<ny;y++)for(int x=0;x<nx;x++) {
            String stem=x+"_"+y; Path tile=dir.resolve(stem+".navtile");
            var receipt=Artifacts.read(dir.resolve(stem+".properties"));
            if("EMPTY".equals(receipt.getProperty("status")) && !Files.exists(tile)) continue;
            if(!"BUILT".equals(receipt.getProperty("status")) || !Artifacts.hash(tile).equals(receipt.getProperty("sha256")))
                throw new IOException("Unverified tile " + stem);
            try(var in=Files.newInputStream(tile)) {
                var data=new MeshDataReader().read(in,6);
                if(data.header.x!=x || data.header.y!=y || data.header.polyCount<1)
                    throw new IOException("Invalid tile header " + stem);
                mesh.addTile(data,0,0);
            }
        }
        return mesh;
    }
}
