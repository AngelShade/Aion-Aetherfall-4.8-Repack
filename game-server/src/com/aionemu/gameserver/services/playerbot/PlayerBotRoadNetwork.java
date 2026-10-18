package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.recast4j.detour.*;

/** Offline main corridors between component hubs, with local mesh connectors at
 * either end. These are real directed polygon corridors, not painted-road guesses
 * or straight rays across terrain. Native instance checks still authorize movement. */
final class PlayerBotRoadNetwork {
    private static final int MAGIC=0x50425231,VERSION=2,MAX_POINTS=12_000_000,MAX_JUMPS=2_000_000;
    // Offline enumeration is deliberately separate from retained runtime data.
    private static final int MAX_PROPOSAL_PAIRS=8_000_000;
    record Jump(int from,int to,PlayerBotNavigation.Point start,PlayerBotNavigation.Point end){}
    final List<Jump> jumps;
    final Map<Integer,int[]> outgoingJumps;
    final float[] hubs,costs,points;
    final int[] offsets;
    private PlayerBotRoadNetwork(float[] hubs,float[] costs,int[] offsets,float[] points){this(hubs,costs,offsets,points,List.of());}
    private PlayerBotRoadNetwork(float[] hubs,float[] costs,int[] offsets,float[] points,List<Jump> jumps){
        this.hubs=hubs;this.costs=costs;this.offsets=offsets;this.points=points;
        this.jumps=List.copyOf(jumps);var groups=new HashMap<Integer,List<Integer>>();
        for(int i=0;i<jumps.size();i++)groups.computeIfAbsent(jumps.get(i).from(),k->new ArrayList<>()).add(i);
        var out=new HashMap<Integer,int[]>();groups.forEach((k,v)->out.put(k,v.stream().mapToInt(Integer::intValue).toArray()));outgoingJumps=Map.copyOf(out);
    }
    long bytes(){return 64L+4L*(hubs.length+costs.length+offsets.length+points.length)+160L*jumps.size();}
    float[] hub(int node){return Arrays.copyOfRange(hubs,node*3,node*3+3);}
    int count(int arc){return offsets[arc+1]-offsets[arc];}
    float[] point(int arc,int index){int at=(offsets[arc]+index)*3;return Arrays.copyOfRange(points,at,at+3);}
    static PlayerBotRoadNetwork build(NavMesh mesh,PlayerBotNavmeshTopology graph)throws IOException{
        var query=new NavMeshQuery(mesh);var clearanceQuery=new NavMeshQuery(mesh);var normal=new DefaultQueryFilter();
        long[] hubRefs=new long[graph.nodes];float[] hubs=new float[Math.multiplyExact(graph.nodes,3)];
        double[] areas=new double[graph.nodes];float[] clearance=new float[graph.components.length];Arrays.fill(clearance,-1);
        for(int i=0;i<mesh.getMaxTiles();i++){
            var tile=mesh.getTile(i);if(tile==null || tile.data==null)continue;long base=mesh.getPolyRefBase(tile);
            for(var poly:tile.data.polys){
                long ref=base|poly.index;int node=graph.component(mesh,ref);if(node<0)continue;
                double area=0;for(int v=0;v<poly.vertCount;v++){
                    int a=poly.verts[v]*3,b=poly.verts[(v+1)%poly.vertCount]*3;
                    area+=(double)tile.data.verts[a]*tile.data.verts[b+2]-(double)tile.data.verts[b]*tile.data.verts[a+2];
                }
                area=Math.abs(area);
                if(hubRefs[node]!=0 && area<=areas[node])continue;
                float[] center=new float[3];for(int v=0;v<poly.vertCount;v++)for(int axis=0;axis<3;axis++)center[axis]+=tile.data.verts[poly.verts[v]*3+axis]/poly.vertCount;
                var height=query.getPolyHeight(ref,center);require(height.succeeded() && height.result!=null,"hub floor");center[1]=height.result;
                hubRefs[node]=ref;areas[node]=area;System.arraycopy(center,0,hubs,node*3,3);
            }
        }
        for(long ref:hubRefs)require(ref!=0,"component hub missing");
        class Filter extends DefaultQueryFilter {
            final int node;
            Filter(int node){this.node=node;}
            @Override public boolean passFilter(long ref,MeshTile tile,Poly poly){return super.passFilter(ref,tile,poly) && graph.component(mesh,ref)==node;}
            @Override public float getCost(float[] a,float[] b,long previous,MeshTile pt,Poly pp,long current,MeshTile tile,Poly poly,long next,MeshTile nt,Poly np){
                int at=graph.tiles[tile.data.header.y*graph.nx+tile.data.header.x]+poly.index;
                if(clearance[at]<0){
                    float[] center=new float[3];for(int v=0;v<poly.vertCount;v++)for(int axis=0;axis<3;axis++)center[axis]+=tile.data.verts[poly.verts[v]*3+axis]/poly.vertCount;
                    var wall=clearanceQuery.findDistanceToWall(current,center,3,normal);
                    clearance[at]=wall.succeeded()?Math.max(0,wall.result.getDistance()):0;
                }
                double horizontal=Math.hypot(a[0]-b[0],a[2]-b[2]);
                return (float)(distance(a,b)*(1+.35/(.3+clearance[at])+.2*Math.abs(a[1]-b[1])/Math.max(.2,horizontal)));
            }
        }
        // Cached per-polygon spokes avoid repeating the same hub search for the
        // many native seam links on one polygon. A map is released between builds.
        var outward=new HashMap<Long,List<Long>>();var inward=new HashMap<Long,List<Long>>();
        var vectors=new ArrayList<float[]>();int[] offsets=new int[graph.to.length+1];float[] costs=new float[graph.to.length];
        for(int from=0;from<graph.nodes;from++){
            var filter=new Filter(from);float[] first=Arrays.copyOfRange(hubs,from*3,from*3+3);
            for(int arc=graph.offsets[from];arc<graph.offsets[from+1];arc++){
                int to=graph.to[arc];var targetFilter=new Filter(to);float[] last=Arrays.copyOfRange(hubs,to*3,to*3+3);
                var a=query.findNearestPoly(graph.source(arc),new float[]{.1f,.2f,.1f},filter);
                var b=query.findNearestPoly(graph.destination(arc),new float[]{.1f,.2f,.1f},targetFilter);
                require(a.succeeded() && b.succeeded() && a.result.getNearestRef()!=0 && b.result.getNearestRef()!=0,"road seam floor");
                long ar=a.result.getNearestRef(),br=b.result.getNearestRef();
                var left=outward.get(ar);if(left==null){left=path(query,hubRefs[from],ar,first,a.result.getNearestPos(),filter);outward.put(ar,left);}
                var right=inward.get(br);if(right==null){right=path(query,br,hubRefs[to],b.result.getNearestPos(),last,targetFilter);inward.put(br,right);}
                var corridor=new ArrayList<Long>(left.size()+right.size());corridor.addAll(left);corridor.addAll(right);
                var straight=query.findStraightPath(first,last,corridor,32768,0);
                require(straight.succeeded() && !straight.result.isEmpty() && (straight.result.getLast().getFlags()&NavMeshQuery.DT_STRAIGHTPATH_END)!=0,"complete road corridor");
                float length=0;float[] previous=null;
                for(var corner:straight.result){float[] point=corner.getPos().clone();if(previous!=null)length+=distance(previous,point);vectors.add(point);previous=point;}
                require(vectors.size()<=MAX_POINTS,"prepared road point capacity");
                offsets[arc+1]=vectors.size();
                // Distance is authoritative; width/slope select the spokes above.
                costs[arc]=Math.max((float)distance(first,last),length)+.01f;
            }
        }
        float[] points=new float[Math.multiplyExact(vectors.size(),3)];for(int i=0;i<vectors.size();i++)System.arraycopy(vectors.get(i),0,points,i*3,3);
        return new PlayerBotRoadNetwork(hubs,costs,offsets,points);
    }
    private PlayerBotRoadNetwork heightLinks(NavMesh mesh,PlayerBotNavmeshTopology graph)throws IOException{
        int[] regions=groundRegions(graph);
        record Boundary(int node,int region,PlayerBotNavigation.Point point){}
        var cells=new HashMap<Long,List<Boundary>>();var unique=new HashSet<String>();var query=new NavMeshQuery(mesh);
        for(int i=0;i<mesh.getMaxTiles();i++){
            var tile=mesh.getTile(i);if(tile==null || tile.data==null)continue;long base=mesh.getPolyRefBase(tile);
            for(var poly:tile.data.polys){
                int node=graph.component(mesh,base|poly.index);if(node<0)continue;
                float[] center=new float[3];for(int v=0;v<poly.vertCount;v++)for(int axis=0;axis<3;axis++)center[axis]+=tile.data.verts[poly.verts[v]*3+axis]/poly.vertCount;
                for(int edge=0;edge<poly.vertCount;edge++){
                    if(poly.neis[edge]!=0)continue;
                    int a=poly.verts[edge]*3,b=poly.verts[(edge+1)%poly.vertCount]*3;
                    float[] point=new float[3];for(int axis=0;axis<3;axis++)point[axis]=(tile.data.verts[a+axis]+tile.data.verts[b+axis])*.5f;
                    double d=Math.hypot(center[0]-point[0],center[2]-point[2]);float inset=(float)Math.min(.75,.5/Math.max(.001,d));
                    for(int axis=0;axis<3;axis++)point[axis]+=(center[axis]-point[axis])*inset;
                    var height=query.getPolyHeight(base|poly.index,point);if(height.failed() || height.result==null)continue;point[1]=height.result;
                    String key=node+":"+Math.round(point[0]/2)+":"+Math.round(point[1]*2)+":"+Math.round(point[2]/2);
                    if(!unique.add(key))continue;
                    var p=new PlayerBotNavigation.Point(point[0],point[2],point[1]);
                    cells.computeIfAbsent(cell(p.x(),p.y()),k->new ArrayList<>()).add(new Boundary(node,regions[node],p));
                }
            }
        }
        var selected=new TreeMap<Long,List<Jump>>();long candidates=0;
        for(var bucket:cells.values())for(var from:bucket){
            int x=(int)Math.floor(from.point.x()/8),y=(int)Math.floor(from.point.y()/8);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(var to:cells.getOrDefault(cell(x+dx,y+dy),List.of())){
                if(from.region==to.region)continue;
                double range=Math.hypot(from.point.x()-to.point.x(),from.point.y()-to.point.y()),rise=to.point.z()-from.point.z();
                if(range<.5 || range>PlayerBotHeightTraversal.MAX_RANGE || rise>PlayerBotHeightTraversal.MAX_RISE || rise< -200)continue;
                candidates++;
                // Both ends are mutually reachable within their respective
                // directed ground regions. Store inter-region portals instead
                // of a quadratic fanout from every equivalent takeoff tile.
                // The selected Jump retains its exact component endpoints.
                long pair=((long)from.region<<32)|(to.region&0xffffffffL);var list=selected.computeIfAbsent(pair,k->new ArrayList<>());
                require(selected.size()<=MAX_PROPOSAL_PAIRS,"height proposal pair capacity");
                var candidate=new Jump(from.node,to.node,from.point,to.point);
                int replace=-1;for(int n=0;n<list.size();n++)if(pointDistance(list.get(n).start(),candidate.start())<3){replace=n;break;}
                double score=jumpScore(candidate);
                if(replace>=0){if(score<jumpScore(list.get(replace)))list.set(replace,candidate);}
                else if(list.size()<3)list.add(candidate);
                else {int worst=0;for(int n=1;n<list.size();n++)if(jumpScore(list.get(n))>jumpScore(list.get(worst)))worst=n;if(score<jumpScore(list.get(worst)))list.set(worst,candidate);}
            }
        }
        var links=sparsePortals(regions,selected);
        System.out.println("Height portals: nearby candidates="+candidates+" region pairs="+selected.size()+" retained="+links.size());
        return new PlayerBotRoadNetwork(hubs,costs,offsets,points,links);
    }
    /** Preserve directed candidate reachability without publishing its dense
     * all-pairs fanout. Native geometry still has to prove every selected arc. */
    private static List<Jump> sparsePortals(int[] regions,TreeMap<Long,List<Jump>> pairs)throws IOException{
        int n=0;for(int region:regions)n=Math.max(n,region+1);
        int m=pairs.size(),at=0;int[] from=new int[m],to=new int[m],offsets=new int[n+1];
        var choices=new ArrayList<List<Jump>>(m);
        for(var entry:pairs.entrySet()){
            from[at]=(int)(entry.getKey()>>>32);to[at]=(int)(long)entry.getKey();offsets[from[at]+1]++;
            entry.getValue().sort(Comparator.comparingDouble(PlayerBotRoadNetwork::jumpScore));choices.add(entry.getValue());at++;
        }
        for(int i=1;i<=n;i++)offsets[i]+=offsets[i-1];
        int[] components=strongRegions(n,offsets,to),reverseCounts=new int[n+1];
        for(int target:to)reverseCounts[target+1]++;for(int i=1;i<=n;i++)reverseCounts[i]+=reverseCounts[i-1];
        int[] reverse=new int[m],position=reverseCounts.clone();for(int arc=0;arc<m;arc++)reverse[position[to[arc]]++]=arc;
        var selected=new BitSet(m);var bridges=new HashMap<Long,Integer>();
        // Root -> all and all -> root inside each proposal SCC. The exact
        // directed endpoints are retained; no reverse edge is invented.
        boolean[] seen=new boolean[n];int[] stack=new int[n];
        for(int root=0;root<n;root++)if(!seen[root]){
            int top=0;stack[0]=root;seen[root]=true;
            while(top>=0){int v=stack[top--];for(int arc=offsets[v];arc<offsets[v+1];arc++){
                int target=to[arc];if(components[target]==components[root] && !seen[target]){seen[target]=true;stack[++top]=target;selected.set(arc);}
            }}
        }
        Arrays.fill(seen,false);
        for(int root=0;root<n;root++)if(!seen[root]){
            int top=0;stack[0]=root;seen[root]=true;
            while(top>=0){int v=stack[top--];for(int k=reverseCounts[v];k<reverseCounts[v+1];k++){
                int arc=reverse[k],target=from[arc];if(components[target]==components[root] && !seen[target]){seen[target]=true;stack[++top]=target;selected.set(arc);}
            }}
        }
        // Keep each condensation bridge, combining only mutually reachable
        // endpoint groups. This preserves reachability between proposal SCCs.
        for(int arc=0;arc<m;arc++)if(components[from[arc]]!=components[to[arc]]){
            long key=((long)components[from[arc]]<<32)|(components[to[arc]]&0xffffffffL);
            Integer previous=bridges.get(key);if(previous==null || jumpScore(choices.get(arc).getFirst())<jumpScore(choices.get(previous).getFirst()))bridges.put(key,arc);
        }
        bridges.values().forEach(selected::set);
        // Add short directional entrances/exits without losing the backbone.
        // Incoming choices matter for small destinations beside a large floor.
        Jump[] directional=new Jump[Math.multiplyExact(n,16)];
        for(int arc=0;arc<m;arc++)for(var jump:choices.get(arc)){
            int direction=(jump.end().x()>=jump.start().x()?0:2)+(jump.end().y()>=jump.start().y()?0:1);
            int up=jump.end().z()>jump.start().z()+.3f?4:0;
            prefer(directional,from[arc]*16+direction+up,jump);
            prefer(directional,to[arc]*16+8+(direction^3)+up,jump);
        }
        var retained=new LinkedHashSet<Jump>();
        for(int arc=selected.nextSetBit(0);arc>=0;arc=selected.nextSetBit(arc+1))retained.addAll(choices.get(arc));
        for(var jump:directional)if(jump!=null)retained.add(jump);
        require(retained.size()<=MAX_JUMPS,"sparse height portal capacity");
        // Independent partition/condensation comparison checks the derivative,
        // rather than assuming degree or a nearby exit proves connectivity.
        int[] keptOffsets=new int[n+1];for(var jump:retained)keptOffsets[regions[jump.from()]+1]++;
        for(int i=1;i<=n;i++)keptOffsets[i]+=keptOffsets[i-1];
        int[] keptTo=new int[retained.size()];position=keptOffsets.clone();var keptBridges=new HashSet<Long>();
        for(var jump:retained){int a=regions[jump.from()],b=regions[jump.to()];keptTo[position[a]++]=b;
            if(components[a]!=components[b])keptBridges.add(((long)components[a]<<32)|(components[b]&0xffffffffL));}
        int[] keptComponents=strongRegions(n,keptOffsets,keptTo),forward=new int[n],backward=new int[n];Arrays.fill(forward,-1);Arrays.fill(backward,-1);
        for(int node=0;node<n;node++){
            int a=components[node],b=keptComponents[node];require((forward[a]<0 || forward[a]==b) && (backward[b]<0 || backward[b]==a),"sparse portal directed partition");forward[a]=b;backward[b]=a;
        }
        require(keptBridges.containsAll(bridges.keySet()),"sparse portal condensation bridges");
        return List.copyOf(retained);
    }
    private static void prefer(Jump[] choices,int slot,Jump jump){if(choices[slot]==null || jumpScore(jump)<jumpScore(choices[slot]))choices[slot]=jump;}
    private static double jumpScore(Jump jump){return Math.hypot(jump.start().x()-jump.end().x(),jump.start().y()-jump.end().y())+Math.abs(jump.start().z()-jump.end().z())*.1;}
    /** Iterative native directed SCCs; a one-way ground drop must not erase
     * a possible return jump by treating the two floors as bidirectional. */
    private static int[] groundRegions(PlayerBotNavmeshTopology graph){
        return strongRegions(graph.nodes,graph.offsets,graph.to);
    }
    private static int[] strongRegions(int n,int[] offsets,int[] targets){
        int[] counts=new int[n+1];for(int target:targets)counts[target+1]++;
        for(int i=1;i<=n;i++)counts[i]+=counts[i-1];int[] reverse=new int[targets.length],position=counts.clone();
        for(int from=0;from<n;from++)for(int arc=offsets[from];arc<offsets[from+1];arc++)reverse[position[targets[arc]]++]=from;
        boolean[] seen=new boolean[n];int[] stack=new int[n],cursor=new int[n],order=new int[n],regions=new int[n];int ordered=0;
        for(int node=0;node<n;node++)if(!seen[node]){
            int top=0;stack[0]=node;cursor[0]=offsets[node];seen[node]=true;
            while(top>=0){int v=stack[top];if(cursor[top]<offsets[v+1]){int w=targets[cursor[top]++];if(!seen[w]){seen[w]=true;stack[++top]=w;cursor[top]=offsets[w];}}
                else {order[ordered++]=v;top--;}}
        }
        Arrays.fill(seen,false);int region=0;
        for(int j=ordered-1;j>=0;j--)if(!seen[order[j]]){
            int top=0;stack[0]=order[j];seen[order[j]]=true;
            while(top>=0){int v=stack[top--];regions[v]=region;
                for(int k=counts[v];k<counts[v+1];k++){int w=reverse[k];if(!seen[w]){seen[w]=true;stack[++top]=w;}}}
            region++;
        }
        return regions;
    }
    private static long cell(float x,float y){return cell((int)Math.floor(x/8),(int)Math.floor(y/8));}
    private static long cell(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    private static double pointDistance(PlayerBotNavigation.Point a,PlayerBotNavigation.Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    private static List<Long> path(NavMeshQuery query,long from,long to,float[] a,float[] b,QueryFilter filter)throws IOException{
        var path=query.findPath(from,to,a,b,filter);
        require(path.succeeded() && !path.result.isEmpty() && path.result.getLast()==to,"complete directed hub spoke");return List.copyOf(path.result);
    }
    void write(Path file,String fingerprint,String topology)throws IOException{
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))){
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(fingerprint);out.writeUTF(topology);
            out.writeInt(hubs.length/3);out.writeInt(costs.length);out.writeInt(points.length/3);
            for(float f:hubs)out.writeFloat(f);for(float f:costs)out.writeFloat(f);for(int i:offsets)out.writeInt(i);for(float f:points)out.writeFloat(f);
            out.writeInt(jumps.size());for(var jump:jumps){out.writeInt(jump.from());out.writeInt(jump.to());for(var p:List.of(jump.start(),jump.end())){out.writeFloat(p.x());out.writeFloat(p.y());out.writeFloat(p.z());}}
        }
        Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static PlayerBotRoadNetwork read(Path file,String fingerprint,String topology,String hash,PlayerBotNavmeshTopology graph)throws IOException{
        return read(file,fingerprint,topology,hash,graph,false);
    }
    private static PlayerBotRoadNetwork read(Path file,String fingerprint,String topology,String hash,PlayerBotNavmeshTopology graph,boolean groundCache)throws IOException{
        byte[] bytes=Files.readAllBytes(file);require(PlayerBotNavmeshTopology.hash(bytes).equals(hash),"road digest");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            require(in.readInt()==MAGIC,"road magic");int version=in.readInt();
            require((version==VERSION || groundCache && version==1) && fingerprint.equals(in.readUTF()) && topology.equals(in.readUTF()),"road provenance");
            int nodes=in.readInt(),arcs=in.readInt(),count=in.readInt();
            require(nodes==graph.nodes && arcs==graph.to.length && count>=0 && count<=MAX_POINTS,"road dimensions");
            long size=(long)nodes*12+(long)arcs*8+4+(long)count*12;
            require(version==1?size==in.available():size+4<=in.available(),"road payload size");
            float[] hubs=new float[nodes*3],costs=new float[arcs],points=new float[count*3];int[] offsets=new int[arcs+1];
            for(int i=0;i<hubs.length;i++){hubs[i]=in.readFloat();require(Float.isFinite(hubs[i]),"finite road hub");}
            for(int i=0;i<arcs;i++){costs[i]=in.readFloat();require(Float.isFinite(costs[i]) && costs[i]>0,"road cost");}
            for(int i=0;i<offsets.length;i++){offsets[i]=in.readInt();require(offsets[i]>=0 && offsets[i]<=count && (i==0?offsets[i]==0:offsets[i]>offsets[i-1]),"road point offsets");}
            require(offsets[arcs]==count,"road point count");
            for(int i=0;i<points.length;i++){points[i]=in.readFloat();require(Float.isFinite(points[i]),"finite road point");}
            var jumps=new ArrayList<Jump>();
            if(version==VERSION){
                int total=in.readInt();require(total>=0 && total<=MAX_JUMPS && (long)total*32==in.available(),"height proposal dimensions");
                for(int i=0;i<total;i++){
                    int from=in.readInt(),to=in.readInt();var a=new PlayerBotNavigation.Point(in.readFloat(),in.readFloat(),in.readFloat());var b=new PlayerBotNavigation.Point(in.readFloat(),in.readFloat(),in.readFloat());
                    double range=Math.hypot(a.x()-b.x(),a.y()-b.y()),rise=b.z()-a.z();
                    require(from>=0 && from<nodes && to>=0 && to<nodes && from!=to && Double.isFinite(range) && Float.isFinite(a.z()) && Float.isFinite(b.z())
                        && range>=.49 && range<=PlayerBotHeightTraversal.MAX_RANGE+.01 && rise<=PlayerBotHeightTraversal.MAX_RISE+.01 && rise>=-200.01,"height proposal bounds");
                    jumps.add(new Jump(from,to,a,b));
                }
            }
            var result=new PlayerBotRoadNetwork(hubs,costs,offsets,points,jumps);
            for(int node=0;node<nodes;node++)for(int arc=graph.offsets[node];arc<graph.offsets[node+1];arc++){
                require(distance(result.point(arc,0),result.hub(node))<.01 && distance(result.point(arc,result.count(arc)-1),result.hub(graph.to[arc]))<.01,"road endpoint hubs");
                float length=0;for(int i=1;i<result.count(arc);i++)length+=distance(result.point(arc,i-1),result.point(arc,i));
                require(Math.abs(length-costs[arc])<.05,"road length");
            }
            return result;
        }
    }
    /** Derivative-only all-map bake, never starts World, SQL or gameplay. */
    public static void main(String[] args)throws Exception{
        if(args.length<2 || args.length>3)throw new IllegalArgumentException("NAVMESH_DIRECTORY EXTERNAL_ROAD_OUTPUT [PREVIOUS_GROUND_CACHE]");
        Path root=Path.of(args[0]).toRealPath(),out=Path.of(args[1]).toAbsolutePath().normalize();
        require(!out.startsWith(root),"road output must be external to runtime");Files.createDirectories(out);
        String reportHash=PlayerBotNavmeshTopology.hash(root.resolve("report.properties"));
        Path topology=root.resolve("topology");String topologyHash=PlayerBotNavmeshTopology.hash(topology.resolve("topology.properties"));
        Properties index=new Properties(),topologyIndex=new Properties();try(var in=Files.newInputStream(topology.resolve("topology.properties"))){topologyIndex.load(in);}
        index.setProperty("format","aion-roads-1");index.setProperty("report.sha256",reportHash);index.setProperty("topology.sha256",topologyHash);
        try(var in=PlayerBotRoadNetwork.class.getResourceAsStream("PlayerBotRoadNetwork.class")){index.setProperty("builder.class.sha256",PlayerBotNavmeshTopology.hash(in.readAllBytes()));}
        var maps=new TreeSet<Integer>();for(String key:topologyIndex.stringPropertyNames())if(key.matches("map\\.\\d+\\.fingerprint"))maps.add(Integer.parseInt(key.split("\\.")[1]));
        var executor=Executors.newSingleThreadExecutor();
        try(var store=new PlayerBotNavmeshStore(root,768L*1024*1024,executor::execute)){
            int done=0;
            for(int map:maps){
                PlayerBotNavmeshStore.Result access;while((access=store.acquire(map)).status()==PlayerBotNavmeshStore.Status.WAITING)Thread.sleep(10);
                require(access.status()==PlayerBotNavmeshStore.Status.READY,"road map load: "+map+" "+access.reason());
                try(var lease=access.lease()){
                    String key="map."+map,digest=topologyIndex.getProperty(key+".sha256"),fingerprint=lease.fingerprint();
                    var graph=PlayerBotNavmeshTopology.read(topology.resolve(map+".pbtopology"),fingerprint,digest);
                    Path cached=args.length==3?Path.of(args[2]).resolve(map+".pbroads"):null;
                    var ground=cached!=null && Files.isRegularFile(cached)?read(cached,fingerprint,digest,PlayerBotNavmeshTopology.hash(cached),graph,true):build(lease.mesh(),graph);
                    Path checkpoint=out.resolve("ground-cache");Files.createDirectories(checkpoint);ground.write(checkpoint.resolve(map+".pbroads"),fingerprint,digest);
                    var roads=ground.heightLinks(lease.mesh(),graph);Path file=out.resolve(map+".pbroads");roads.write(file,fingerprint,digest);
                    String hash=PlayerBotNavmeshTopology.hash(file);var check=read(file,fingerprint,digest,hash,graph);
                    require(Arrays.equals(check.points,roads.points) && Arrays.equals(check.hubs,roads.hubs) && Arrays.equals(check.costs,roads.costs) && Arrays.equals(check.offsets,roads.offsets) && check.jumps.equals(roads.jumps),"road roundtrip");
                    index.setProperty(key+".fingerprint",fingerprint);index.setProperty(key+".sha256",hash);
                    System.out.println("Road network "+(++done)+"/"+maps.size()+": map="+map+" hubs="+graph.nodes+" corridors="+graph.to.length+" points="+roads.points.length/3+" jump/drop proposals="+roads.jumps.size());
                }
            }
        }finally{executor.shutdown();if(!executor.awaitTermination(1,TimeUnit.MINUTES))throw new IOException("road worker not finished");}
        require(reportHash.equals(PlayerBotNavmeshTopology.hash(root.resolve("report.properties"))) && topologyHash.equals(PlayerBotNavmeshTopology.hash(topology.resolve("topology.properties"))),"road inputs changed");
        try(var stream=Files.newOutputStream(out.resolve("roads.properties"))){index.store(stream,"Complete directed main/local corridor network; gameplay acceptance remains separate");}
        System.out.println("PASS: "+maps.size()+" complete road networks; no geometry rebake or gameplay");
    }
    private static double distance(float[] a,float[] b){double d=0;for(int i=0;i<3;i++)d+=(a[i]-b[i])*(a[i]-b[i]);return Math.sqrt(d);}
    private static void require(boolean good,String reason)throws IOException{if(!good)throw new IOException(reason);}
}
