package com.aionemu.tools.navmesh;

import java.util.*;

/** Clip triangles at native allocated world boundaries, retaining intersecting walls and their heights. */
final class DomainClip {
    private DomainClip() {}
    static AionGeometry.Triangles clip(float[] vertices, int[] indices, float[] min, float[] max) {
        boolean inside=true;
        for(int i=0;i<vertices.length;i+=3)
            if(vertices[i]<min[0] || vertices[i]>max[0] || vertices[i+2]<min[2] || vertices[i+2]>max[2]) { inside=false; break; }
        if(inside)return new AionGeometry.Triangles(vertices,indices);
        float[] output=new float[Math.max(9,indices.length*3)]; int count=0;
        for(int i=0;i<indices.length;i+=3) {
            List<float[]> polygon=new ArrayList<>(3);
            for(int j=0;j<3;j++)polygon.add(Arrays.copyOfRange(vertices,indices[i+j]*3,indices[i+j]*3+3));
            polygon=plane(polygon,0,min[0],true); polygon=plane(polygon,0,max[0],false);
            polygon=plane(polygon,2,min[2],true); polygon=plane(polygon,2,max[2],false);
            for(int j=1;j+1<polygon.size();j++) {
                if(count+9>output.length)output=Arrays.copyOf(output,Math.max(count+9,output.length*2));
                for(float[] p:List.of(polygon.get(0),polygon.get(j),polygon.get(j+1))) {
                    System.arraycopy(p,0,output,count,3); count+=3;
                }
            }
        }
        int[] faces=new int[count/3]; for(int i=0;i<faces.length;i++)faces[i]=i;
        return new AionGeometry.Triangles(Arrays.copyOf(output,count),faces);
    }
    private static List<float[]> plane(List<float[]> points,int axis,float limit,boolean minimum) {
        List<float[]> result=new ArrayList<>(); if(points.isEmpty())return result;
        float[] previous=points.get(points.size()-1);
        boolean wasInside=minimum?previous[axis]>=limit:previous[axis]<=limit;
        for(float[] current:points) {
            boolean inside=minimum?current[axis]>=limit:current[axis]<=limit;
            if(inside!=wasInside) {
                double fraction=((double)limit-previous[axis])/((double)current[axis]-previous[axis]);
                float[] intersection=new float[3];
                for(int k=0;k<3;k++)intersection[k]=(float)(previous[k]+fraction*((double)current[k]-previous[k]));
                intersection[axis]=limit; result.add(intersection);
            }
            if(inside)result.add(current);
            previous=current; wasInside=inside;
        }
        return result;
    }
}
