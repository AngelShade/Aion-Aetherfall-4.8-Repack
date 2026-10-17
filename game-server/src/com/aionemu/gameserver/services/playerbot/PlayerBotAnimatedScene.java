package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import com.aionemu.commons.utils.Json;
import com.google.gson.JsonObject;
import com.aionemu.gameserver.geoEngine.scene.Mesh;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.collision.*;
import com.aionemu.gameserver.geoEngine.bounding.BoundingVolume;

/** Immutable authored moving physics, shared between actors. No inferred floors or clock epoch.
 * Matching Game.dll packet27/11eb0/12388 synchronizes SyncAnimation to unsigned server uptime.
 * CGA Node823/Controller826/Timing918 and native node creation order are retained by the exporter. */
final class PlayerBotAnimatedScene {
    private static final java.lang.management.RuntimeMXBean CLOCK=ManagementFactory.getRuntimeMXBean();
    private final Map<Integer,List<Entity>> maps;
    final long bytes;
    private PlayerBotAnimatedScene(Map<Integer,List<Entity>> maps,long bytes){this.maps=Map.copyOf(maps);this.bytes=bytes;}
    List<Entity> entities(int map){return maps.getOrDefault(map,List.of());}
    static long uptime(){return Integer.toUnsignedLong((int)CLOCK.getUptime());}

    static PlayerBotAnimatedScene read(Path file,String expectedHash)throws IOException {
        try {
            long size=Files.size(file);require(size>0 && size<=16*1024*1024,"animated scene size");
            byte[] snapshot=Files.readAllBytes(file);require(snapshot.length==size,"animated scene changed while reading");
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot)).equals(expectedHash),"animated scene authentication");
            var document=Json.read(snapshot);require("aion-animated-scene-1".equals(Json.string(document,"format")),"animated scene format");
            var encodedModels=Json.object(document,"models");require(encodedModels!=null && encodedModels.size()<=512,"animated model count");
            var models=new HashMap<String,Model>();
            for(String name:encodedModels.keySet())models.put(name,new Model(Json.object(encodedModels,name)));
            var encodedEntities=Json.array(document,"entities");require(encodedEntities!=null && encodedEntities.size()<=4096,"animated entity count");
            var maps=new HashMap<Integer,List<Entity>>();var identities=new HashSet<String>();
            for(int i=0;i<encodedEntities.size();i++){
                var value=Json.object(encodedEntities,i);int map=Json.integer(value,"map");
                var attributes=Json.object(value,"entity");int id=Integer.parseInt(Json.string(attributes,"EntityId"));
                require(map>0 && id>0 && id<=65535 && identities.add(map+":"+id),"animated native entity identity");
                require("1".equals(Json.string(attributes,"SyncAnimation")) && "0".equals(Json.string(attributes,"HiddenInGame")),"animated participation");
                var animation=Json.object(value,"animation");require("Default".equals(Json.string(animation,"Animation"))
                    && "1".equals(Json.string(animation,"bLoop")) && "1".equals(Json.string(animation,"bPlaying")),"unsupported native animation state");
                Model model=models.get(Json.string(value,"model"));require(model!=null,"animated model reference");
                double speed=Float.parseFloat(Json.string(animation,"Speed"));require(Double.isFinite(speed) && speed>0,"animated speed");
                Entity entity=new Entity(map,id,model,speed,Affine.entity(vector(value,"position",3),vector(value,"angles",3),vector(value,"scale",3)));
                maps.computeIfAbsent(map,k->new ArrayList<>()).add(entity);
            }
            maps.replaceAll((k,v)->List.copyOf(v));
            // Reservation includes decoded numeric arrays, tangents, immutable maps and parsing slack.
            return new PlayerBotAnimatedScene(maps,Math.addExact(1_048_576,Math.multiplyExact(size,8)));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid authored animated scene",e);}
    }
    private static double[] vector(JsonObject object,String name,int length)throws IOException {
        var array=Json.array(object,name);require(array!=null && array.size()==length,"animated "+name+" width");
        double[] values=new double[length];for(int i=0;i<length;i++){values[i]=Json.decimal(array,i);require(Double.isFinite(values[i]),"animated nonfinite "+name);}
        return values;
    }
    private static void require(boolean valid,String message)throws IOException{if(!valid)throw new IOException(message);}

    static final class Model {
        final Node[] nodes;
        final double start,duration;
        final double[] samples;
        Model(JsonObject object)throws IOException {
            require("aion-animated-model-1".equals(Json.string(object,"format")) && "centimetres".equals(Json.string(object,"units")),"native model units");
            var timing=Json.object(object,"timing");double tick=Json.decimal(timing,"secondsPerTick");int frame=Json.integer(timing,"ticksPerFrame");
            start=(float)tick*frame*Json.integer(timing,"startFrame");
            float end=(float)tick*frame*Json.integer(timing,"endFrame");duration=end-(float)start;
            require(Double.isFinite(tick) && tick>0 && frame>0 && Double.isFinite(start) && Double.isFinite(duration) && duration>0,"native animation duration");
            var tracks=new HashMap<Integer,PlayerBotAnimatedCurve>();var encodedTracks=Json.object(object,"tracks");var times=new TreeSet<Double>();times.add(start);
            require(encodedTracks!=null && encodedTracks.size()<=4096,"native track count");
            for(String id:encodedTracks.keySet()){
                var track=Json.object(encodedTracks,id);var rows=Json.array(track,"keys");require(rows!=null && rows.size()>0 && rows.size()<=100000,"native track keys");
                double[][] keys=new double[rows.size()][];
                for(int i=0;i<keys.length;i++){var row=Json.array(rows,i);require(row.size()<=10,"native track width");keys[i]=new double[row.size()];for(int j=0;j<keys[i].length;j++){keys[i][j]=Json.decimal(row,j);require(Double.isFinite(keys[i][j]),"native track finite");}}
                tracks.put(Integer.parseInt(id),new PlayerBotAnimatedCurve(Json.integer(track,"type"),Json.integer(track,"flags"),tick,keys));
                for(double[] key:keys)if(key[0]*tick>=start && key[0]*tick<start+duration)times.add(key[0]*tick);
            }
            // Docks may lie between authored keys. Sample interpolation as well as every key;
            // each emitted stop still needs an actual physical/native floor contact.
            require(duration<=3600,"native animation sampling duration");
            for(double t=start;t<start+duration;t+=.1)times.add(t);
            samples=times.stream().mapToDouble(Double::doubleValue).toArray();
            var encodedNodes=Json.array(object,"nodes");require(encodedNodes!=null && encodedNodes.size()>0 && encodedNodes.size()<=4096,"native node count");
            nodes=new Node[encodedNodes.size()];var ordinals=new HashMap<Integer,Integer>();
            for(int i=0;i<nodes.length;i++)require(ordinals.put(Json.integer(Json.object(encodedNodes,i),"id"),i)==null,"native duplicate node");
            int physicalSlots=0;
            for(int i=0;i<nodes.length;i++){
                var value=Json.object(encodedNodes,i);int parent=Json.integer(value,"parent");require(parent==-1 || ordinals.containsKey(parent),"native parent reference");
                var controllers=Json.array(value,"controllers");require(controllers.size()==3,"native controller slots");
                var curves=new PlayerBotAnimatedCurve[3];for(int j=0;j<3;j++){int id=Json.integer(controllers,j);curves[j]=id<0?null:tracks.get(id);require(id<0 || curves[j]!=null,"native controller reference");}
                double[] position=vector(value,"position",3);for(int j=0;j<3;j++)position[j]/=100;
                double[] vertices=new double[0];int[] faces=new int[0];var mesh=Json.object(value,"mesh");
                if(mesh!=null){var points=Json.array(mesh,"vertices");var triangles=Json.array(mesh,"physicalFaces");require(points.size()<=65536 && triangles.size()<=2_000_000,"native physical mesh count/index width");
                    vertices=new double[points.size()*3];for(int j=0;j<points.size();j++){var point=Json.array(points,j);require(point.size()==3,"native physical vertex");for(int k=0;k<3;k++){double v=Json.decimal(point,k)/100;require(Double.isFinite(v),"native physical vertex finite");vertices[j*3+k]=v;}}
                    faces=new int[triangles.size()*3];for(int j=0;j<triangles.size();j++){var face=Json.array(triangles,j);require(face.size()==4,"native physical triangle");for(int k=0;k<3;k++){int v=Json.integer(face,k);require(v>=0 && v<points.size(),"native physical vertex reference");faces[j*3+k]=v;}}
                }
                int wirePart=mesh!=null && Json.bool(mesh,"physicalMember")?physicalSlots++:-1;
                require(faces.length==0 || wirePart>=0,"floor without native physics membership");
                nodes[i]=new Node(i,wirePart,parent==-1?-1:ordinals.get(parent),position,vector(value,"rotation",4),vector(value,"scale",3),curves,vertices,faces);
            }
            for(Node node:nodes){int at=node.parent,depth=0;while(at>=0){require(++depth<=nodes.length,"native hierarchy cycle");at=nodes[at].parent;}}
        }
    }
    static final class Node {
        final int part,wirePart,parent;
        final double[] position,rotation,scale,vertices;
        final PlayerBotAnimatedCurve[] curves;
        final int[] faces;
        final Mesh physics;
        Node(int part,int wirePart,int parent,double[] position,double[] rotation,double[] scale,PlayerBotAnimatedCurve[] curves,double[] vertices,int[] faces){
            this.part=part;this.wirePart=wirePart;this.parent=parent;this.position=position;this.rotation=rotation;this.scale=scale;this.curves=curves;this.vertices=vertices;this.faces=faces;
            if(faces.length==0)physics=null;
            else {
                float[] points=new float[vertices.length];for(int i=0;i<points.length;i++)points[i]=(float)vertices[i];
                short[] indices=new short[faces.length];for(int i=0;i<indices.length;i++)indices[i]=(short)faces[i];
                physics=new Mesh();physics.setVertices(java.nio.FloatBuffer.wrap(points));physics.setIndices(java.nio.ShortBuffer.wrap(indices));
                physics.updateBound();physics.createCollisionData();
            }
        }
        Affine local(double time){
            double[] p=curves[0]==null?position:curves[0].sample(time);if(curves[0]!=null){p=p.clone();for(int i=0;i<3;i++)p[i]/=100;}
            return Affine.pose(p,curves[1]==null?rotation:curves[1].sample(time),curves[2]==null?scale:curves[2].sample(time));
        }
    }
    static final class Entity {
        final int map,id;
        final Model model;
        final double speed;
        final Affine placement;
        private volatile Frame cached;
        Entity(int map,int id,Model model,double speed,Affine placement){this.map=map;this.id=id;this.model=model;this.speed=speed;this.placement=placement;}
        double phase(long millis){
            float seconds=(float)Integer.toUnsignedLong((int)millis)/1000f;
            float period=(float)model.duration/(float)speed;
            float phase=(float)(seconds%(double)period)*(float)speed;
            return model.start+phase; // Native divss→double fmod→cvtsd2ss→mulss rounding.
        }
        Frame frame(long millis){Frame previous=cached;if(previous!=null && previous.millis==millis)return previous;Frame next=at(phase(millis),millis);cached=next;return next;}
        Frame at(double time,long millis){var transforms=new Affine[model.nodes.length];for(int i=0;i<transforms.length;i++)transform(i,time,transforms);return new Frame(this,millis,transforms);}
        private Affine transform(int part,double time,Affine[] output){if(output[part]!=null)return output[part];Node node=model.nodes[part];return output[part]=(node.parent<0?placement:transform(node.parent,time,output)).multiply(node.local(time));}
    }
    static final class Frame {
        final Entity entity;
        final long millis;
        final Affine[] transforms;
        final Matrix4f[] matrices;
        final BoundingVolume[] bounds;
        Frame(Entity entity,long millis,Affine[] transforms){
            this.entity=entity;this.millis=millis;this.transforms=transforms;
            matrices=new Matrix4f[transforms.length];bounds=new BoundingVolume[transforms.length];
            for(int i=0;i<transforms.length;i++)if(entity.model.nodes[i].physics!=null){matrices[i]=transforms[i].nativeMatrix();bounds[i]=entity.model.nodes[i].physics.getBound().transform(matrices[i],null);}
        }
        double[] point(int part,double[] local){return transforms[part].point(local);}
        double[] local(int part,double[] world){return transforms[part].inverse().point(world);}
        double[] vehicle(int part,double[] world){
            // Wire coordinates retain metric scale: native sender removes only world
            // physical-part translation and rotation, not the authored mesh scale.
            double[] m=transforms[part].m;double[] delta={world[0]-m[3],world[1]-m[7],world[2]-m[11]},offset=new double[3];
            for(int c=0;c<3;c++){double length=Math.sqrt(m[c]*m[c]+m[4+c]*m[4+c]+m[8+c]*m[8+c]);if(length<1e-12)throw new IllegalStateException("Degenerate physical rotation");
                offset[c]=(m[c]*delta[0]+m[4+c]*delta[1]+m[8+c]*delta[2])/length;}
            return offset;
        }
        Vector3f floor(int part,float x,float y,float upper,float lower){
            Node node=entity.model.nodes[part];if(node.physics==null || upper<lower)return null;
            var ray=new Ray(new Vector3f(x,y,upper),new Vector3f(0,0,-1));ray.setLimit(upper-lower);
            var hits=new CollisionResults((byte)0,0);hits.setInvalidateSlopingSurface(true);
            node.physics.collideWith(ray,matrices[part],bounds[part],hits);
            var nearest=hits.getClosestCollision();return nearest==null || !Float.isFinite(nearest.getContactPoint().z)?null:nearest.getContactPoint();
        }
        boolean supports(int part,double[] world){
            var center=floor(part,(float)world[0],(float)world[1],(float)world[2]+.2f,(float)world[2]-.2f);
            if(center==null)return false;
            for(int i=0;i<8;i++)if(floor(part,(float)(world[0]+.3*Math.cos(i*Math.PI/4)),(float)(world[1]+.3*Math.sin(i*Math.PI/4)),center.z+.2f,center.z-.2f)==null)return false;
            return true;
        }
        boolean clear(double[] from,double[] to,int exceptPart){
            Vector3f origin=new Vector3f((float)from[0],(float)from[1],(float)from[2]);
            Vector3f delta=new Vector3f((float)(to[0]-from[0]),(float)(to[1]-from[1]),(float)(to[2]-from[2]));float length=delta.length();if(length<.001)return true;
            for(int i=0;i<matrices.length;i++)if(i!=exceptPart && matrices[i]!=null){
                // Native BIH transforms the supplied ray in place. Each physical
                // part therefore receives its own world-origin value.
                var ray=new Ray(origin.clone(),delta.mult(1/length));ray.setLimit(length);var hits=new CollisionResults((byte)0,0,true);
                entity.model.nodes[i].physics.collideWith(ray,matrices[i],bounds[i],hits);if(hits.size()>0)return false;
            }
            return true;
        }
    }
    /** Column-vector equivalent of native row-vector node*parent*entity matrices. */
    static final class Affine {
        final double[] m;
        Affine(double[] m){this.m=m;}
        static Affine pose(double[] p,double[] q,double[] s){
            double norm=0;for(double v:q)norm+=v*v;if(norm<1e-12)throw new IllegalArgumentException("Degenerate authored quaternion");
            double x=q[0]/Math.sqrt(norm),y=q[1]/Math.sqrt(norm),z=q[2]/Math.sqrt(norm),w=q[3]/Math.sqrt(norm);
            return new Affine(new double[]{(1-2*(y*y+z*z))*s[0],2*(x*y-z*w)*s[1],2*(x*z+y*w)*s[2],p[0],
                2*(x*y+z*w)*s[0],(1-2*(x*x+z*z))*s[1],2*(y*z-x*w)*s[2],p[1],
                2*(x*z-y*w)*s[0],2*(y*z+x*w)*s[1],(1-2*(x*x+y*y))*s[2],p[2]});
        }
        static Affine entity(double[] p,double[] degrees,double[] scale){
            double x=Math.toRadians(degrees[0])/2,y=Math.toRadians(degrees[1])/2,z=Math.toRadians(degrees[2])/2;
            double sx=Math.sin(x),cx=Math.cos(x),sy=Math.sin(y),cy=Math.cos(y),sz=Math.sin(z),cz=Math.cos(z);
            return pose(p,new double[]{sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz},scale);
        }
        double[] point(double[] p){return new double[]{m[0]*p[0]+m[1]*p[1]+m[2]*p[2]+m[3],m[4]*p[0]+m[5]*p[1]+m[6]*p[2]+m[7],m[8]*p[0]+m[9]*p[1]+m[10]*p[2]+m[11]};}
        Matrix4f nativeMatrix(){return new Matrix4f((float)m[0],(float)m[1],(float)m[2],(float)m[3],(float)m[4],(float)m[5],(float)m[6],(float)m[7],(float)m[8],(float)m[9],(float)m[10],(float)m[11],0,0,0,1);}
        Affine multiply(Affine b){double[] out=new double[12];for(int r=0;r<3;r++){for(int c=0;c<3;c++)for(int k=0;k<3;k++)out[r*4+c]+=m[r*4+k]*b.m[k*4+c];out[r*4+3]=m[r*4+3];for(int k=0;k<3;k++)out[r*4+3]+=m[r*4+k]*b.m[k*4+3];}return new Affine(out);}
        Affine inverse(){double a=m[0],b=m[1],c=m[2],d=m[4],e=m[5],f=m[6],g=m[8],h=m[9],i=m[10];double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);if(Math.abs(det)<1e-12)throw new IllegalArgumentException("Singular authored transform");
            double[] out={(e*i-f*h)/det,(c*h-b*i)/det,(b*f-c*e)/det,0,(f*g-d*i)/det,(a*i-c*g)/det,(c*d-a*f)/det,0,(d*h-e*g)/det,(b*g-a*h)/det,(a*e-b*d)/det,0};
            for(int r=0;r<3;r++)out[r*4+3]=-(out[r*4]*m[3]+out[r*4+1]*m[7]+out[r*4+2]*m[11]);return new Affine(out);
        }
    }
}
