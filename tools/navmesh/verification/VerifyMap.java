package com.aionemu.tools.navmesh;

import java.io.IOException;
import java.nio.file.*;
import org.recast4j.detour.*;

/** Structural/detail/connected-reference checks, separate from the baker and binary round trip. */
public final class VerifyMap {
    record Counts(int polygons,int links,int crossTileLinks) {}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("MAP_REVISION_DIRECTORY");
        var mesh=Inspect.load(Path.of(args[0]));var counts=check(mesh);
        System.out.println("PASS: tiles="+mesh.getTileCount()+" "+counts+"; structure only, not whole-map reachability");
    }
    static Counts check(NavMesh mesh)throws IOException{
        var query=new NavMeshQuery(mesh);int polygons=0,links=0,cross=0;
        for(int t=0;t<mesh.getMaxTiles();t++){
            var tile=mesh.getTile(t);if(tile==null || tile.data==null)continue;
            var data=tile.data;long base=mesh.getPolyRefBase(tile);
            for(float v:data.verts)require(Float.isFinite(v),"Nonfinite polygon vertex");
            for(float v:data.detailVerts)require(Float.isFinite(v),"Nonfinite detail vertex");
            require(data.polys.length==data.header.polyCount,"Polygon count mismatch");
            for(var poly:data.polys){
                require(poly.getType()==Poly.DT_POLYTYPE_GROUND && poly.vertCount>=3 && poly.vertCount<=6,"Invalid/unexpected ground polygon");
                float[] center=new float[3];
                for(int i=0;i<poly.vertCount;i++){
                    int vertex=poly.verts[i];require(vertex>=0 && vertex*3+2<data.verts.length,"Polygon vertex reference outside tile");
                    for(int axis=0;axis<3;axis++)center[axis]+=data.verts[vertex*3+axis]/poly.vertCount;
                }
                var detail=data.detailMeshes[poly.index];
                require(detail.triCount>0 && detail.triBase>=0 && (long)(detail.triBase+detail.triCount)*4<=data.detailTris.length,"Missing/invalid surface detail");
                require(detail.vertBase>=0 && (long)(detail.vertBase+detail.vertCount)*3<=data.detailVerts.length,"Invalid detail vertex range");
                for(int i=0;i<detail.triCount;i++)for(int corner=0;corner<3;corner++){
                    int vertex=data.detailTris[(detail.triBase+i)*4+corner];
                    require(vertex>=0 && vertex<poly.vertCount+detail.vertCount,"Dangling serialized detail face");
                }
                var height=query.getPolyHeight(base|poly.index,center);
                require(height.succeeded() && height.result!=null && Float.isFinite(height.result),"Polygon center has no finite supported detail height: tile="+data.header.x+","+data.header.y+" poly="+poly.index+" center="+java.util.Arrays.toString(center)+" status="+height.status+" vertices="+java.util.Arrays.toString(java.util.Arrays.stream(poly.verts).limit(poly.vertCount).mapToObj(v->java.util.Arrays.toString(java.util.Arrays.copyOfRange(data.verts,v*3,v*3+3))).toArray()));
                int visited=0;
                for(int link=tile.polyLinks[poly.index];link!=NavMesh.DT_NULL_LINK;link=tile.links.get(link).next){
                    require(link>=0 && link<tile.links.size() && ++visited<=tile.links.size(),"Invalid/cyclic navigation link");
                    var edge=tile.links.get(link);require(mesh.getTileAndPolyByRef(edge.ref).succeeded(),"Navigation link has invalid polygon reference");
                    links++;if(NavMesh.decodePolyIdTile(base)!=NavMesh.decodePolyIdTile(edge.ref))cross++;
                }
                polygons++;
            }
        }
        return new Counts(polygons,links,cross);
    }
    private static void require(boolean valid,String why)throws IOException{if(!valid)throw new IOException(why);}
}
