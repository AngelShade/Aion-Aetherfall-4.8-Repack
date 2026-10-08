package com.aionemu.gameserver.services.playerbot;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.geoEngine.collision.*;

/** Read-only native collision-mesh probe: no GameServer, world actors, database, IDs or services. */
public final class PlayerBotDungeonGeoProbe {
    private record Entity(String name, Vector3f location, Matrix3f rotation, Vector3f scale, byte type, short id) {}
    private record Door(int id, float x, float y, float z) {}
    private static final int INSTANCE = 1;
    private static boolean engine;
    private static Vector3f walk(GeoMap map, Vector3f start, float x, float y) {
        return engine ? PlayerBotGroundNavigation.walk(map, INSTANCE, start.x, start.y, start.z, x, y) : map.findMovementCollision(start, x, y, INSTANCE);
    }

    private static ByteBuffer mapped(Path path) throws Exception {
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ)) {
            return file.map(FileChannel.MapMode.READ_ONLY, 0, file.size());
        }
    }
    private static String string(ByteBuffer bytes) {
        byte[] data = new byte[bytes.getShort() & 0xffff]; bytes.get(data);
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }
    private static List<Entity> entities(Path path) throws Exception {
        ByteBuffer bytes = mapped(path); List<Entity> result = new ArrayList<>();
        while (bytes.hasRemaining()) {
            String name = string(bytes);
            Vector3f loc = new Vector3f(bytes.getFloat(), bytes.getFloat(), bytes.getFloat());
            Matrix3f rotation = new Matrix3f();
            for (int i=0;i<3;i++) for (int j=0;j<3;j++) rotation.set(i,j,bytes.getFloat());
            Vector3f scale = new Vector3f(bytes.getFloat(), bytes.getFloat(), bytes.getFloat());
            byte type=bytes.get(); short id=bytes.getShort(); bytes.get();
            result.add(new Entity(name,loc,rotation,scale,type,id));
        }
        return result;
    }
    private static Map<String,com.aionemu.gameserver.geoEngine.scene.Node> models(Path path, Set<String> needed) throws Exception {
        ByteBuffer bytes = mapped(path); var result = new HashMap<String,com.aionemu.gameserver.geoEngine.scene.Node>();
        while (bytes.hasRemaining()) {
            String name = string(bytes); List<String> aliases = Arrays.asList(name.split("\\|"));
            boolean selected=aliases.stream().anyMatch(needed::contains);
            var node = new com.aionemu.gameserver.geoEngine.scene.Node(null);
            byte intentions=0; int count=bytes.get() & 255;
            for (int i=0;i<count;i++) {
                int vertexBytes=(bytes.getShort() & 65535)*12;
                var vertices=bytes.slice(bytes.position(),vertexBytes).asFloatBuffer(); bytes.position(bytes.position()+vertexBytes);
                int faces=bytes.getShort() & 65535, indexSize=bytes.get() & 255, faceBytes=faces*3*indexSize;
                ByteBuffer indices=bytes.slice(bytes.position(),faceBytes); bytes.position(bytes.position()+faceBytes);
                byte material=bytes.get(), flags=bytes.get(); intentions|=flags;
                if (selected) {
                    Mesh mesh=new Mesh(); mesh.setVertices(vertices);
                    if (indexSize==1) mesh.setIndices(indices); else if(indexSize==2) mesh.setIndices(indices.asShortBuffer()); else throw new IllegalStateException("index size "+indexSize);
                    mesh.setMaterialId(material); mesh.setCollisionIntentions(flags);
                    node.attachChild(new Geometry(name,mesh));
                }
            }
            if (selected) { node.setCollisionIntentions(intentions); for(String alias:aliases) if(needed.contains(alias)) result.put(alias,node); }
        }
        return result;
    }
    private static GeoMap load(int id, List<Entity> entities, Map<String,com.aionemu.gameserver.geoEngine.scene.Node> models) throws Exception {
        GeoMap map=new GeoMap(id);
        for(Entity e:entities) {
            if(e.type()==1 || e.type()==8) continue; // unrelated seasonal/siege service geometry is never activated here
            var template=models.get(e.name()); if(template==null) throw new IllegalStateException("Missing mesh: "+e.name());
            com.aionemu.gameserver.geoEngine.scene.Node node;
            if(e.type()>0) {
                DespawnableNode dynamic=new DespawnableNode(); dynamic.copyFrom(template);
                dynamic.type=DespawnableNode.DespawnableType.getById(e.type()); dynamic.id=e.id(); node=dynamic;
            } else node=template.clone();
            node.setTransform(e.rotation(),e.location(),e.scale()); node.updateModelBound(); map.attachChild(node);
        }
        map.updateModelBound(); return map;
    }
    private static Document xml(Path path) throws Exception {
        var factory=DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }
    private static float attr(Element element,String name) {return Float.parseFloat(element.getAttribute(name));}
    private static List<Door> doors(Document doc,int mapId) {
        List<Door> result=new ArrayList<>(); NodeList worlds=doc.getElementsByTagName("world");
        for(int i=0;i<worlds.getLength();i++) {Element world=(Element)worlds.item(i); if(!world.getAttribute("world").equals(""+mapId))continue;
            NodeList entries=world.getElementsByTagName("staticdoor");
            for(int j=0;j<entries.getLength();j++){Element e=(Element)entries.item(j);result.add(new Door(Integer.parseInt(e.getAttribute("id")),attr(e,"x"),attr(e,"y"),attr(e,"z")));}
        } return result;
    }
    private static double moved(Vector3f a,Vector3f b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static Vector3f doorCenter(com.aionemu.gameserver.geoEngine.scene.Node node,int id) {
        if(node instanceof DespawnableNode d && d.type==DespawnableNode.DespawnableType.DOOR_STATE1 && d.id==id)return d.getWorldBound().getCenter();
        for(Spatial child:node.getChildren()) if(child instanceof com.aionemu.gameserver.geoEngine.scene.Node nested){Vector3f point=doorCenter(nested,id);if(point!=null)return point;}
        return null;
    }
    private static DespawnableNode doorNode(com.aionemu.gameserver.geoEngine.scene.Node node,int id,DespawnableNode.DespawnableType type) {
        if(node instanceof DespawnableNode d && d.type==type && d.id==id)return d;
        for(Spatial child:node.getChildren())if(child instanceof com.aionemu.gameserver.geoEngine.scene.Node nested){var found=doorNode(nested,id,type);if(found!=null)return found;}
        return null;
    }
    private static void room(GeoMap map,float x,float y,float z) {
        float floor=map.getZ(x,y,z+3,z-12,INSTANCE,true),any=map.getZ(x,y,z+3,z-12,INSTANCE);
        double raw=0,grounded=0;
        for(int angle=0;angle<360;angle+=45){float tx=x+(float)Math.cos(Math.toRadians(angle))*4,ty=y+(float)Math.sin(Math.toRadians(angle))*4;
            raw=Math.max(raw,moved(new Vector3f(x,y,z),walk(map,new Vector3f(x,y,z),tx,ty)));
            if(Float.isFinite(floor))grounded=Math.max(grounded,moved(new Vector3f(x,y,floor),walk(map,new Vector3f(x,y,floor),tx,ty)));}
        System.out.printf(Locale.ROOT,"ROOM map=%d x=%.3f y=%.3f z=%.3f floor=%.3f any=%.3f raw=%.3f grounded=%.3f%n",map.getMapId(),x,y,z,floor,any,raw,grounded);
        if(raw<.5){var collisions=map.getCollisions(x,y,z+3,x,y,z-12,INSTANCE,CollisionIntention.PHYSICAL.getId(),IgnoreProperties.ANY_RACE);int i=0;for(var c:collisions){if(i++>3)break;System.out.println("  ground hit="+c.getContactPoint()+" model="+c.getGeometry().getName());}}
    }
    public static void main(String[] args) throws Exception {
        Path server=Path.of(args[0]), report=Path.of(args[1]);
        engine=args.length>2 && args[2].equals("engine");
        Map<Integer,List<Entity>> entities=new LinkedHashMap<>(); Set<String> needed=new HashSet<>();
        for(int id:new int[]{300100000,300460000}){var list=entities(server.resolve("data/geo/"+id+".geo"));entities.put(id,list);list.forEach(e->needed.add(e.name()));}
        var models=models(args.length>2 && !engine?Path.of(args[2]):server.resolve("data/geo/models.mesh"),needed);
        Document doorXml=xml(server.resolve("data/static_data/staticdoors/staticdoor_templates.xml"));
        Files.createDirectories(report.getParent());
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(report))) {
            out.println("map,kind,id,x,y,z,floor,angle,raw_move,grounded_move,closed_move");
            for(var entry:entities.entrySet()) {
                int id=entry.getKey(); GeoMap map=load(id,entry.getValue(),models); List<Door> doors=doors(doorXml,id);
                String spawn=id==300100000 ? "300100000_Steel Rake.xml" : "300460000_Steel_Rake_Cabin.xml";
                NodeList spots=xml(server.resolve("data/static_data/spawns/Instances/"+spawn)).getElementsByTagName("spot");
                for(int i=0;i<spots.getLength();i++){Element e=(Element)spots.item(i);if(e.hasAttribute("static_id"))map.spawnPlaceableObject(INSTANCE,Integer.parseInt(e.getAttribute("static_id")));}
                doors.forEach(d->map.setDoorState(INSTANCE,d.id(),true));
                if(args.length==4 && args[3].equals("captain")){
                    if(id!=300100000)continue;
                    for(float[] p:new float[][]{{397.184f,504.165f},{409.184f,504.165f},{409.184f,516.165f},{397.184f,516.165f},{403.184f,510.165f},{403.88f,510.13f},{427.45f,509.88f}}){
                        float expected=p[0]>425?1075.3801f:1071.736f;
                        float floor=map.getZ(p[0],p[1],expected+1.1f,expected-1.25f,INSTANCE,true);
                        System.out.printf(Locale.ROOT,"CAPTAIN floor x=%.4f y=%.4f z=%.4f%n",p[0],p[1],floor);
                        if(!Float.isFinite(floor))throw new AssertionError("Captain floor missing");
                    }
                    for(int wave=1;wave<=2;wave++)for(int slot=0;slot<4;slot++){
                        float x=(wave==1?379.4199f:381.26767f)+slot*1.5f,y=wave==1?495.36453f:526.40845f;
                        float floor=map.getZ(x,y,1073,1070.5f,INSTANCE,true);
                        if(!Float.isFinite(floor))throw new AssertionError("Captain add floor missing at "+x+","+y);
                        System.out.printf(Locale.ROOT,"CAPTAIN add x=%.4f y=%.4f z=%.4f%n",x,y,floor);
                        var end=walk(map,new Vector3f(x,y,floor),403.88f,510.13f);
                        if(Math.hypot(end.x-403.88f,end.y-510.13f)>.5)throw new AssertionError("Captain add lane obstructed from "+x+","+y+" end="+end);
                    }
                    continue;
                }
                // Optional exact reported coordinates; fixture data never enters production routing.
                if(args.length==10){
                    if(id!=Integer.parseInt(args[3]))continue;
                    float x=Float.parseFloat(args[4]),y=Float.parseFloat(args[5]),z=Float.parseFloat(args[6]);
                    float tx=Float.parseFloat(args[7]),ty=Float.parseFloat(args[8]),tz=Float.parseFloat(args[9]);
                    room(map,x,y,z);room(map,tx,ty,tz);
                    for(int offset=0;offset<360;offset+=15){
                        double angle=Math.atan2(ty-y,tx-x)+Math.toRadians(offset);
                        float nx=x+(float)Math.cos(angle)*14.4f,ny=y+(float)Math.sin(angle)*14.4f;
                        Vector3f from=new Vector3f(x,y,z),end=walk(map,from,nx,ny);
                        System.out.printf(Locale.ROOT,"POINT offset=%d end=%s moved=%.4f%n",offset,end,moved(from,end));
                    }
                    Vector3f current=new Vector3f(x,y,z);
                    for(int tick=0;tick<200 && Math.hypot(current.x-tx,current.y-ty)>.05;tick++){
                        double left=Math.hypot(current.x-tx,current.y-ty),fraction=Math.min(1,.15/left);
                        Vector3f next=walk(map,current.clone(),current.x+(float)((tx-current.x)*fraction),current.y+(float)((ty-current.y)*fraction));
                        if(moved(current,next)<.001)break;current=next;
                    }
                    System.out.println("POINT tick end="+current+" remaining="+Math.hypot(current.x-tx,current.y-ty));
                    current=new Vector3f(x,y,z);Vector3f destination=null;
                    for(int tick=0;tick<1200 && Math.hypot(current.x-tx,current.y-ty)>.3;tick++){
                        if(tick%2==0 || destination==null){
                            double angle=Math.atan2(ty-current.y,tx-current.x),distance=Math.min(14.4,Math.hypot(current.x-tx,current.y-ty));double score=-Double.MAX_VALUE;destination=null;
                            for(int offset:new int[]{0,30,-30,60,-60,90,-90}){
                                double direction=angle+Math.toRadians(offset);
                                var candidate=walk(map,current.clone(),current.x+(float)(Math.cos(direction)*distance),current.y+(float)(Math.sin(direction)*distance));
                                if(moved(current,candidate)<.15)continue;
                                double value=-Math.hypot(candidate.x-tx,candidate.y-ty)-Math.abs(offset)*.015;
                                if(value>score){score=value;destination=candidate;}
                            }
                            if(destination==null)break;
                        }
                        double left=moved(current,destination),fraction=Math.min(1,.15/left);
                        var next=walk(map,current.clone(),current.x+(float)((destination.x-current.x)*fraction),current.y+(float)((destination.y-current.y)*fraction));
                        if(moved(current,next)<.001){destination=null;continue;}current=next;
                    }
                    System.out.println("POINT planner/tick end="+current+" remaining="+Math.hypot(current.x-tx,current.y-ty));
                    var hits=map.getCollisions(x,y,z+3,x,y,z-12,INSTANCE,CollisionIntention.PHYSICAL.getId(),IgnoreProperties.ANY_RACE);
                    for(var hit:hits)System.out.println("POINT floor hit="+hit.getContactPoint()+" model="+hit.getGeometry().getName());
                    for(var hit:map.getCollisions(tx,ty,tz+3,tx,ty,tz-20,INSTANCE,CollisionIntention.ALL.getId(),IgnoreProperties.ANY_RACE))System.out.println("POINT goal ALL hit="+hit.getContactPoint()+" flags="+hit.getGeometry().getCollisionIntentions()+" model="+hit.getGeometry().getName());
                    for(float px=x;px<=tx+1;px+=1)System.out.printf(Locale.ROOT,"POINT floor slice x=%.2f physical=%.3f%n",px,map.getZ(px,y+(ty-y)*(px-x)/(tx-x),z+1,z-12,INSTANCE));
                    long deadline=System.nanoTime()+6_000_000L;
                    var route=PlayerBotPathfinder.find(new PlayerBotNavigation.Point(x,y,z),new PlayerBotNavigation.Point(tx,ty,tz),(from,nx,ny)->{var end=walk(map,new Vector3f(from.x(),from.y(),from.z()),nx,ny);return new PlayerBotNavigation.Point(end.x,end.y,end.z);},128,()->System.nanoTime()>deadline);
                    System.out.println("POINT bounded route="+route);
                    continue;
                }
                int crossings=0;
                for(Door door:doors) {
                    double bestOpen=0,bestClosed=0,bestDelta=-999; int bestAngle=0;
                    Vector3f center=Objects.requireNonNull(doorCenter(map,door.id()));
                    for(int angle=0;angle<360;angle+=15) {
                        double radians=Math.toRadians(angle);float dx=(float)Math.cos(radians)*4,dy=(float)Math.sin(radians)*4;
                        // Door templates are leaf/hinge heights. Start below their centre, not on a room's roof.
                        float x=center.x-dx,y=center.y-dy,z=map.getZ(x,y,door.z(),door.z()-4,INSTANCE,true);
                        if(!Float.isFinite(z))continue;
                        Vector3f start=new Vector3f(x,y,z); Vector3f open=walk(map,start.clone(),center.x+dx,center.y+dy);
                        map.setDoorState(INSTANCE,door.id(),false);Vector3f closed=walk(map,start.clone(),center.x+dx,center.y+dy);map.setDoorState(INSTANCE,door.id(),true);
                        double distance=moved(start,open);out.printf(Locale.ROOT,"%d,door,%d,%.4f,%.4f,%.4f,%.4f,%d,%.4f,%.4f,%.4f%n",id,door.id(),x,y,z,z,angle,distance,distance,moved(start,closed));
                        if(distance-moved(start,closed)>bestDelta){bestDelta=distance-moved(start,closed);bestOpen=distance;bestClosed=moved(start,closed);bestAngle=angle;}
                    }
                    if(bestOpen>7.5 && bestClosed<bestOpen-1)crossings++;
                    System.out.printf(Locale.ROOT,"map=%d door=%d angle=%d open=%.3f closed=%.3f%n",id,door.id(),bestAngle,bestOpen,bestClosed);
                    if(engine) {
                        var openNode=Objects.requireNonNull(doorNode(map,door.id(),DespawnableNode.DespawnableType.DOOR_STATE2));
                        int ghostStalls=0;
                        for(float sx=center.x-1.6f;sx<=center.x+1.6f;sx+=.2f)for(float sy=center.y-1.6f;sy<=center.y+1.6f;sy+=.2f) {
                            openNode.setActive(INSTANCE,false);
                            float floor=map.getZ(sx,sy,door.z()-.8f,door.z()-4,INSTANCE,true);
                            openNode.setActive(INSTANCE,true);
                            if(!Float.isFinite(floor))continue;
                            double best=0,without=0;
                            for(int direction=0;direction<360;direction+=45) {
                                float tx=sx+(float)Math.cos(Math.toRadians(direction))*1.5f,ty=sy+(float)Math.sin(Math.toRadians(direction))*1.5f;
                                Vector3f start=new Vector3f(sx,sy,floor);
                                best=Math.max(best,moved(start,walk(map,start.clone(),tx,ty)));
                                openNode.setActive(INSTANCE,false);without=Math.max(without,moved(start,walk(map,start.clone(),tx,ty)));openNode.setActive(INSTANCE,true);
                            }
                            if(best<.15 && without>1) {ghostStalls++;if(ghostStalls<=5)System.out.printf(Locale.ROOT,"GHOST STALL map=%d door=%d at=(%.4f,%.4f,%.4f) move=%.3f without=%.3f floor=%.4f%n",id,door.id(),sx,sy,floor,best,without,map.getZ(sx,sy,floor+.6f,floor-1.25f,INSTANCE,true));}
                        }
                        System.out.printf("GHOST STALLS map=%d door=%d count=%d%n",id,door.id(),ghostStalls);
                        double rad=Math.toRadians(bestAngle);
                        float dx=(float)Math.cos(rad)*4,dy=(float)Math.sin(rad)*4;
                        float ax=center.x-dx,ay=center.y-dy,bx=center.x+dx,by=center.y+dy;
                        for(float tickStep:new float[]{.07f,.15f,.3f,.5f,.8f})for(boolean reverse:new boolean[]{false,true}) {
                            float x=reverse?bx:ax,y=reverse?by:ay,tx=reverse?ax:bx,ty=reverse?ay:by;
                            float z=map.getZ(x,y,door.z(),door.z()-4,INSTANCE,true);
                            Vector3f current=new Vector3f(x,y,z);int ticks=0;
                            while(ticks++<200 && Math.hypot(current.x-tx,current.y-ty)>.05) {
                                double left=Math.hypot(current.x-tx,current.y-ty);double fraction=Math.min(1,tickStep/left);
                                Vector3f next=walk(map,current.clone(),current.x+(float)((tx-current.x)*fraction),current.y+(float)((ty-current.y)*fraction));
                                if(moved(current,next)<.001)break;current=next;
                            }
                            double remaining=Math.hypot(current.x-tx,current.y-ty);
                            System.out.printf(Locale.ROOT,"TICKS map=%d door=%d step=%.2f reverse=%s left=%.3f at=(%.4f,%.4f,%.4f)%n",id,door.id(),tickStep,reverse,remaining,current.x,current.y,current.z);
                            if(remaining>.1) {
                                var hits=map.getCollisions(current.x,current.y,current.z+.6f,current.x,current.y,current.z-1.25f,INSTANCE,CollisionIntention.PHYSICAL.getId(),IgnoreProperties.ANY_RACE);
                                int printed=0;for(var hit:hits){if(printed++>=3)break;System.out.println("  TICK ground hit="+hit.getContactPoint()+" geom="+hit.getGeometry().getName());}
                            }
                        }
                    }
                }
                int stalled=0,groundFixed=0;
                for(int i=0;i<spots.getLength();i++) {
                    Element e=(Element)spots.item(i);float x=attr(e,"x"),y=attr(e,"y"),z=attr(e,"z"),floor=map.getZ(x,y,z+3,z-12,INSTANCE,true);double rawMax=0,groundMax=0;
                    for(int angle=0;angle<360;angle+=45){double rad=Math.toRadians(angle);float tx=x+(float)Math.cos(rad)*4,ty=y+(float)Math.sin(rad)*4;Vector3f from=new Vector3f(x,y,z);
                        double raw=moved(from,walk(map,from.clone(),tx,ty));double grounded=Float.isFinite(floor)?moved(from,walk(map,new Vector3f(x,y,floor),tx,ty)):0;
                        rawMax=Math.max(rawMax,raw);groundMax=Math.max(groundMax,grounded);out.printf(Locale.ROOT,"%d,room,%d,%.4f,%.4f,%.4f,%.4f,%d,%.4f,%.4f,0%n",id,i,x,y,z,floor,angle,raw,grounded);
                    }
                    if(rawMax<.5){stalled++;if(groundMax>2)groundFixed++;if(stalled<20)System.out.printf(Locale.ROOT,"map=%d stalled spot=%d x=%.3f y=%.3f z=%.3f floor=%.3f raw=%.3f grounded=%.3f%n",id,i,x,y,z,floor,rawMax,groundMax);}
                }
                System.out.printf("map=%d loaded=%d door crossings=%d/%d room starts=%d stalled=%d corrected-by-ground=%d%n",id,map.getEntityCount(),crossings,doors.size(),spots.getLength(),stalled,groundFixed);
                if(engine && crossings!=doors.size())throw new AssertionError("Opened/closed walking distinction failed: "+id+" "+crossings+"/"+doors.size());
                for(float[] point:new float[][]{{659.8f,509.1f,867.8f},{670,509,867.8f},{695.76f,508.37f,867.3649f},{450,540,951.7f},{340,544,951.7f},{390,500,949}})room(map,point[0],point[1],point[2]);
                if(id==300460000) {
                    room(map,333.34015f,543.85626f,951.60925f);
                    room(map,324.8792f,557.62787f,951.7954f);
                    for(int offset:new int[]{0,30,-30,60,-60,90,-90}) {
                        double angle=Math.atan2(557.62787-543.85626,324.8792-333.34015)+Math.toRadians(offset);
                        float tx=333.34015f+(float)Math.cos(angle)*14.4f,ty=543.85626f+(float)Math.sin(angle)*14.4f;
                        var end=walk(map,new Vector3f(333.34015f,543.85626f,951.60925f),tx,ty);
                        System.out.printf(Locale.ROOT,"USER offset=%d target=(%.4f,%.4f) end=%s moved=%.4f floorStart=%.4f%n",offset,tx,ty,end,moved(new Vector3f(333.34015f,543.85626f,951.60925f),end),map.getZ(333.34015f,543.85626f,952.20925f,950.35925f,INSTANCE,true));
                    }
                }
            }
        }
        System.out.println("OK: read-only native mesh probes saved to "+report);
    }
}
