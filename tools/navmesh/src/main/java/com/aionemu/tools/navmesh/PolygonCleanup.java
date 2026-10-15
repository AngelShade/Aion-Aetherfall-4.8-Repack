package com.aionemu.tools.navmesh;

import java.util.Arrays;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;

/** Remove only exactly zero-area raster polygons, preserving real small islands and tile portals. */
final class PolygonCleanup {
    private static final int NONE=0xffff;
    private PolygonCleanup() {}
    static int removeDegenerate(PolyMesh mesh,PolyMeshDetail detail) {
        int[] remap=new int[mesh.npolys];Arrays.fill(remap,-1);int retained=0,n=mesh.nvp;
        for(int p=0;p<mesh.npolys;p++){
            int base=p*2*n,count=0;while(count<n && mesh.polys[base+count]!=NONE)count++;
            long area=0;
            for(int i=0;i<count;i++){
                int a=mesh.polys[base+i]*3,b=mesh.polys[base+(i+1)%count]*3;
                area+=(long)mesh.verts[a]*mesh.verts[b+2]-(long)mesh.verts[b]*mesh.verts[a+2];
            }
            if(count>=3 && area!=0)remap[p]=retained++;
        }
        if(retained==mesh.npolys)return 0;
        int removed=mesh.npolys-retained;
        int[] polygons=new int[retained*2*n],areas=new int[retained],flags=new int[retained],regs=new int[retained],details=new int[retained*4];
        int width=Math.round((mesh.bmax[0]-mesh.bmin[0])/mesh.cs),height=Math.round((mesh.bmax[2]-mesh.bmin[2])/mesh.cs);
        for(int old=0;old<remap.length;old++)if(remap[old]>=0){
            int p=remap[old],base=p*2*n;System.arraycopy(mesh.polys,old*2*n,polygons,base,2*n);
            areas[p]=mesh.areas[old];flags[p]=mesh.flags[old];regs[p]=mesh.regs[old];
            System.arraycopy(detail.meshes,old*4,details,p*4,4);
            int count=0;while(count<n && polygons[base+count]!=NONE)count++;
            for(int edge=0;edge<count;edge++){
                int index=base+n+edge,neighbor=polygons[index];
                if((neighbor&0x8000)!=0)continue;
                if(neighbor>=remap.length)throw new IllegalArgumentException("Invalid Recast neighbor");
                if(remap[neighbor]>=0){polygons[index]=remap[neighbor];continue;}
                polygons[index]=NONE;
                // A discarded tile-edge sliver must not conceal a real cross-tile portal.
                if(mesh.borderSize>0){
                    int a=polygons[base+edge]*3,b=polygons[base+(edge+1)%count]*3;
                    if(mesh.verts[a]==0 && mesh.verts[b]==0)polygons[index]=0x8000;
                    else if(mesh.verts[a+2]==height && mesh.verts[b+2]==height)polygons[index]=0x8001;
                    else if(mesh.verts[a]==width && mesh.verts[b]==width)polygons[index]=0x8002;
                    else if(mesh.verts[a+2]==0 && mesh.verts[b+2]==0)polygons[index]=0x8003;
                }
            }
        }
        mesh.polys=polygons;mesh.areas=areas;mesh.flags=flags;mesh.regs=regs;mesh.npolys=retained;mesh.maxpolys=retained;
        detail.meshes=details;detail.nmeshes=retained;
        return removed;
    }
}
