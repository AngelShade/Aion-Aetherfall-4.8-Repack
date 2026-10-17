package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.aionemu.commons.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.recast4j.detour.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Offline-derived docks. An arc proposes a ride; fresh native boarding/body/landing
 * checks authorize its execution. Geometry, identity and phase are authored, never synthesized. */
final class PlayerBotAnimatedLinks {
    record Stop(int component,Point ground,double time){}
    record Ride(PlayerBotAnimatedScene.Entity entity,int part,double[] local,Stop from,Stop to){}
    final List<Ride> rides;
    final Map<Integer,int[]> outgoing;
    final PlayerBotAnimatedScene scene;
    private PlayerBotAnimatedLinks(PlayerBotAnimatedScene scene,List<Ride> rides){
        this.scene=scene;this.rides=List.copyOf(rides);var grouped=new HashMap<Integer,List<Integer>>();
        for(int i=0;i<rides.size();i++)grouped.computeIfAbsent(rides.get(i).from.component,k->new ArrayList<>()).add(i);
        var encoded=new HashMap<Integer,int[]>();grouped.forEach((k,v)->encoded.put(k,v.stream().mapToInt(Integer::intValue).toArray()));outgoing=Map.copyOf(encoded);
    }
    private static void require(boolean valid,String reason)throws IOException{if(!valid)throw new IOException(reason);}
    static PlayerBotAnimatedLinks read(Path file,String hash,PlayerBotAnimatedScene scene,int map,int components)throws IOException{
        long size=Files.size(file);require(size>0 && size<=16*1024*1024,"animated link size");byte[] bytes=Files.readAllBytes(file);
        require(bytes.length==size && PlayerBotNavmeshTopology.hash(bytes).equals(hash),"animated links authentication");
        var encoded=Json.read(bytes);require("aion-animated-links-1".equals(Json.string(encoded,"format")) && map==Json.integer(encoded,"map"),"animated link map/format");
        var entities=new HashMap<Integer,PlayerBotAnimatedScene.Entity>();for(var entity:scene.entities(map))entities.put(entity.id,entity);
        var array=Json.array(encoded,"rides");require(array!=null && array.size()<=32768,"animated ride count");var rides=new ArrayList<Ride>();
        for(int i=0;i<array.size();i++){
            var value=Json.object(array,i);var entity=entities.get(Json.integer(value,"entity"));int part=Json.integer(value,"part");
            require(entity!=null && part>=0 && part<entity.model.nodes.length && entity.model.nodes[part].physics!=null,"animated physical part");
            double[] local=vector(Json.array(value,"local"));var from=stop(Json.object(value,"from"),components,entity.model);var to=stop(Json.object(value,"to"),components,entity.model);
            require(from.component!=to.component,"animated ride endpoints identical");
            require(entity.at(from.time,0).supports(part,entity.at(from.time,0).point(part,local))
                && entity.at(to.time,0).supports(part,entity.at(to.time,0).point(part,local)),"animated ride slot support");
            rides.add(new Ride(entity,part,local,from,to));
        }
        return new PlayerBotAnimatedLinks(scene,rides);
    }
    private static Stop stop(JsonObject value,int components,PlayerBotAnimatedScene.Model model)throws IOException{
        int component=Json.integer(value,"component");double time=Json.decimal(value,"time");double[] point=vector(Json.array(value,"ground"));
        require(component>=0 && component<components && Double.isFinite(time) && time>=model.start && time<model.start+model.duration,"animated dock component/phase");
        return new Stop(component,new Point((float)point[0],(float)point[1],(float)point[2]),time);
    }
    private static double[] vector(JsonArray value)throws IOException{require(value!=null && value.size()==3,"animated dock vector");double[] point=new double[3];for(int i=0;i<3;i++){point[i]=Json.decimal(value,i);require(Double.isFinite(point[i]) && Math.abs(point[i])<Float.MAX_VALUE,"animated dock finite");}return point;}
    static PlayerBotAnimatedLinks derive(PlayerBotAnimatedScene scene,int map,NavMesh mesh,PlayerBotNavmeshTopology graph)throws IOException {
        var selected=new LinkedHashMap<String,Ride>();var query=new NavMeshQuery(mesh);var filter=new DefaultQueryFilter();
        for(var entity:scene.entities(map)){
            var frames=new PlayerBotAnimatedScene.Frame[entity.model.samples.length];
            for(int i=0;i<frames.length;i++)frames[i]=entity.at(entity.model.samples[i],0);
            for(var node:entity.model.nodes){
                if(node.physics==null)continue;
                boolean moves=false;double[] initial=frames[0].transforms[node.part].m;
                for(var frame:frames){double[] transform=frame.transforms[node.part].m;
                    for(int axis=0;axis<12;axis++)if(Math.abs(initial[axis]-transform[axis])>1e-6){moves=true;break;}
                    if(moves)break;
                }
                if(!moves)continue;
                var slots=new LinkedHashMap<String,double[]>();
                for(int face=0;face<node.faces.length;face+=3){
                    double[][] triangle=new double[3][];for(int i=0;i<3;i++){int at=node.faces[face+i]*3;triangle[i]=Arrays.copyOfRange(node.vertices,at,at+3);}
                    // Most native moving collision faces are walls. A face supplies
                    // a candidate only if an actual sampled pose makes it walkable.
                    boolean walkable=false;
                    for(var frame:frames){
                        var a=frame.point(node.part,triangle[0]);var b=frame.point(node.part,triangle[1]);var c=frame.point(node.part,triangle[2]);
                        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
                        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                        if(nz*nz>nx*nx+ny*ny && Math.abs(nz)>1e-9){walkable=true;break;}
                    }
                    if(!walkable)continue;
                    for(int corner=-1;corner<3;corner++){
                        double[] slot=new double[3];for(int i=0;i<3;i++)for(int axis=0;axis<3;axis++)slot[axis]+=triangle[i][axis]*(corner<0?1./3:corner==i?2./3:1./6);
                        String key=Arrays.toString(new long[]{Math.round(slot[0]*20),Math.round(slot[1]*20),Math.round(slot[2]*20)});slots.putIfAbsent(key,slot);
                    }
                }
                require(slots.size()<=131072,"animated slot preparation capacity");
                for(double[] slot:slots.values()){
                    var stops=new LinkedHashMap<Integer,Stop>();
                    for(int i=0;i<frames.length;i++){
                        var frame=frames[i];double[] position=frame.point(node.part,slot);
                        var nearest=query.findNearestPoly(new float[]{(float)position[0],(float)position[2],(float)position[1]},new float[]{1.25f,.25f,1.25f},filter);
                        if(nearest.failed() || nearest.result.getNearestRef()==0)continue;
                        float[] point=nearest.result.getNearestPos();double[] ground={point[0],point[2],point[1]};
                        // A body-sized slot lies inside the moving surface, while an eroded
                        // native ground polygon lies inside the shore. Propose their short walk;
                        // live union-of-surfaces support must prove every boarding step.
                        if(distance(position,ground)>1.3 || Math.abs(position[2]-ground[2])>.25)continue;int component=graph.component(mesh,nearest.result.getNearestRef());if(component<0 || stops.containsKey(component))continue;
                        // Preserve a real body-sized physical surface and a boarding interval;
                        // fast crossings cannot become an instantaneous teleport edge.
                        if(!frame.supports(node.part,position))continue;
                        double next=entity.model.samples[i]+.5*entity.speed;if(next>=entity.model.start+entity.model.duration)next-=entity.model.duration;
                        var later=entity.at(next,0);double[] future=later.point(node.part,slot);
                        if(distance(position,future)>.35 || !later.supports(node.part,future))continue;
                        stops.put(component,new Stop(component,new Point(point[0],point[2],point[1]),entity.model.samples[i]));
                    }
                    for(Stop from:stops.values())for(Stop to:stops.values())if(from.component!=to.component){
                        String key=entity.id+":"+node.part+":"+from.component+":"+to.component;
                        selected.putIfAbsent(key,new Ride(entity,node.part,slot.clone(),from,to));
                        require(selected.size()<=32768,"animated route capacity");
                    }
                }
            }
        }
        return new PlayerBotAnimatedLinks(scene,new ArrayList<>(selected.values()));
    }
    private static double distance(double[] a,double[] b){double total=0;for(int i=0;i<3;i++)total+=(a[i]-b[i])*(a[i]-b[i]);return Math.sqrt(total);}
    byte[] encode(int map){
        var encoded=new ArrayList<Map<String,Object>>();for(Ride ride:rides)encoded.add(Map.of("entity",ride.entity.id,"part",ride.part,"local",ride.local,"from",encode(ride.from),"to",encode(ride.to)));
        return Json.sortedBytes(Map.of("format","aion-animated-links-1","map",map,"rides",encoded));
    }
    void write(Path file,int map)throws IOException{
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");Files.write(temporary,encode(map));
        Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private static Map<String,Object> encode(Stop stop){return Map.of("component",stop.component,"time",stop.time,"ground",new float[]{stop.ground.x(),stop.ground.y(),stop.ground.z()});}
    /** Product preparation; reads authenticated meshes/scene, never starts World, SQL or components. */
    public static void main(String[] args)throws Exception{
        require(args.length==4 || args.length==5,"NAVMESH_DIRECTORY TOPOLOGY_DIRECTORY SCENE_JSON EXTERNAL_LINK_DIRECTORY [MAP_ID|--verify]");
        boolean verify=args.length==5 && args[4].equals("--verify");
        Path root=Path.of(args[0]).toRealPath(),topology=Path.of(args[1]).toRealPath(),sceneFile=Path.of(args[2]).toRealPath(),output=Path.of(args[3]).toAbsolutePath();Files.createDirectories(output);
        String sceneHash=PlayerBotNavmeshTopology.hash(sceneFile);var scene=PlayerBotAnimatedScene.read(sceneFile,sceneHash);
        var index=new Properties();try(var in=Files.newInputStream(topology.resolve("topology.properties"))){index.load(in);}
        require(index.getProperty("report.sha256","").equals(PlayerBotNavmeshTopology.hash(root.resolve("report.properties"))),"animated topology catalog binding");
        var binding=new Properties();binding.setProperty("format","aion-animated-navigation-1");binding.setProperty("report.sha256",index.getProperty("report.sha256"));
        binding.setProperty("topology.sha256",PlayerBotNavmeshTopology.hash(topology.resolve("topology.properties")));binding.setProperty("scene.sha256",sceneHash);
        var executor=Executors.newSingleThreadExecutor();
        try(var store=new PlayerBotNavmeshStore(root,768L*1024*1024,executor::execute)){
            var maps=new TreeSet<Integer>();for(String key:index.stringPropertyNames())if(key.matches("map\\.\\d+\\.sha256"))maps.add(Integer.parseInt(key.split("\\.")[1]));
            if(args.length==5 && !verify){int requested=Integer.parseInt(args[4]);require(maps.contains(requested),"native map not in catalog");maps.retainAll(Set.of(requested));}
            for(int map:maps){
                if(scene.entities(map).isEmpty()){
                    var empty=new PlayerBotAnimatedLinks(scene,List.of());Path file=output.resolve(map+".pbrides");
                    if(verify)require(Arrays.equals(empty.encode(map),Files.readAllBytes(file)),"empty native animated link mismatch "+map);else empty.write(file,map);
                    binding.setProperty("map."+map+".sha256",PlayerBotNavmeshTopology.hash(file));System.out.println("Map "+map+" authored entities=0 native dock arcs=0");continue;
                }
                PlayerBotNavmeshStore.Result access;while((access=store.acquire(map)).status()==PlayerBotNavmeshStore.Status.WAITING)Thread.sleep(10);
                require(access.status()==PlayerBotNavmeshStore.Status.READY,"animated native mesh admission "+map);
                try(var lease=access.lease()){
                    var graph=PlayerBotNavmeshTopology.read(topology.resolve(map+".pbtopology"),lease.fingerprint(),index.getProperty("map."+map+".sha256"));
                    var links=derive(scene,map,lease.mesh(),graph);Path file=output.resolve(map+".pbrides");
                    if(verify)require(Arrays.equals(links.encode(map),Files.readAllBytes(file)),"native animated links mismatch "+map);else links.write(file,map);
                    binding.setProperty("map."+map+".sha256",PlayerBotNavmeshTopology.hash(output.resolve(map+".pbrides")));
                    System.out.println("Map "+map+" authored entities="+scene.entities(map).size()+" native dock arcs="+links.rides.size());
                }
            }
        }finally{executor.shutdown();require(executor.awaitTermination(1,TimeUnit.MINUTES),"animated preparation worker completion");}
        if(args.length==5 && !verify)return; // An inspection subset cannot authenticate a full release package.
        require(PlayerBotNavmeshTopology.hash(sceneFile).equals(sceneHash),"animated scene changed during preparation");
        if(verify){var recorded=new Properties();try(var in=Files.newInputStream(output.resolve("animated.properties"))){recorded.load(in);}require(recorded.equals(binding),"complete native animated package binding");
            require(PlayerBotNavmeshTopology.hash(output.resolve("animated-scene.json")).equals(sceneHash),"bound native animated scene");System.out.println("PASS: complete authored animation and native docking package verified");return;}
        Files.copy(sceneFile,output.resolve("animated-scene.json"),StandardCopyOption.REPLACE_EXISTING);
        try(var out=Files.newOutputStream(output.resolve("animated.properties.tmp"))){binding.store(out,"Authored native platforms and static-floor docking proposals");}
        Files.move(output.resolve("animated.properties.tmp"),output.resolve("animated.properties"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Prepared authenticated native animated docks; normal artifact binding and runtime boarding remain required");
    }
}
