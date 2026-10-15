package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.recast4j.detour.*;

/** Compact, directed tile-component connectivity derived offline from actual Detour links.
 * It selects a journey; live tiled Detour and native movement still prove its steps. */
final class PlayerBotNavmeshTopology {
    private static final int MAGIC=0x50425431, VERSION=1;
    private static final int MAX_TILES=1_000_000, MAX_POLYS=20_000_000, MAX_EDGES=5_000_000;
    final int nx,ny,nodes;
    final int[] tiles,components,offsets,to;
    private final int[] nodeTiles;
    final float[] portals; // source XYZ then destination XYZ, in Recast coordinates
    private PlayerBotAnimatedLinks animated;
    void animated(PlayerBotAnimatedLinks links){if(animated!=null)throw new IllegalStateException("Topology already published");animated=links;}
    PlayerBotAnimatedLinks.Ride ride(int arc){return animated!=null && arc>=to.length?animated.rides.get(arc-to.length):null;}
    PlayerBotNavmeshTopology(int nx,int ny,int nodes,int[] tiles,int[] components,int[] offsets,int[] to,float[] portals)throws IOException{
        this.nx=nx;this.ny=ny;this.nodes=nodes;this.tiles=tiles;this.components=components;
        this.offsets=offsets;this.to=to;this.portals=portals;
        nodeTiles=new int[nodes];Arrays.fill(nodeTiles,-1);
        for(int tile=0;tile<tiles.length-1;tile++)for(int p=tiles[tile];p<tiles[tile+1];p++){
            int node=components[p];if(node<0)continue;
            require(nodeTiles[node]<0 || nodeTiles[node]==tile,"component crosses native tiles");nodeTiles[node]=tile;
        }
        for(int node=0;node<nodes;node++){
            require(nodeTiles[node]>=0,"unowned topology component");
            for(int arc=offsets[node];arc<offsets[node+1];arc++){
                int a=nodeTiles[node],b=nodeTiles[to[arc]];
                require(b>=0 && Math.abs(a%nx-b%nx)<=1 && Math.abs(a/nx-b/nx)<=1,"nonadjacent topology arc");
            }
        }
    }
    long bytes(){return 256L+4L*(tiles.length+components.length+offsets.length+to.length+portals.length+nodeTiles.length);}
    int component(NavMesh mesh,long ref){
        var result=mesh.getTileAndPolyByRef(ref);if(result.failed())return -1;
        var tile=result.result.first.data;int x=tile.header.x,y=tile.header.y,p=result.result.second.index;
        if(x<0 || x>=nx || y<0 || y>=ny || p<0 || p>=tiles[y*nx+x+1]-tiles[y*nx+x])return -1;
        return components[tiles[y*nx+x]+p];
    }
    float[] source(int arc){var ride=ride(arc);return ride==null?Arrays.copyOfRange(portals,arc*6,arc*6+3):new float[]{ride.from().ground().x(),ride.from().ground().z(),ride.from().ground().y()};}
    float[] destination(int arc){var ride=ride(arc);return ride==null?Arrays.copyOfRange(portals,arc*6+3,arc*6+6):new float[]{ride.to().ground().x(),ride.to().ground().z(),ride.to().ground().y()};}

    /** Sliced A*: actual adjacent-tile arcs admit a tile-hop lower bound, including detours. */
    final class Search {
        private record Visit(int node,double cost,int parent,int arc){}
        private final PriorityQueue<Visit> open=new PriorityQueue<>(Comparator
            .comparingDouble((Visit v)->v.cost+lowerBound(v.node)).thenComparingInt(Visit::node));
        private final Map<Integer,Visit> best=new HashMap<>();
        private final int goal;
        private Visit current;
        private int edge;
        private boolean done;
        private String failure;
        private int[] route;
        private Visit tracing;
        private final ArrayList<Integer> reversePath=new ArrayList<>();
        private int copying=-1;
        Search(int start,int goal){
            this.goal=goal;
            if(start<0 || start>=nodes || goal<0 || goal>=nodes){done=true;failure="no topology endpoint";return;}
            var initial=new Visit(start,0,-1,-1);best.put(start,initial);open.add(initial);
        }
        int advance(int allowance,BooleanSupplier expired){
            int work=0;
            while(!done && work<allowance && !expired.getAsBoolean()){
                if(copying>=0){
                    work++;
                    if(copying==route.length){done=true;break;}
                    route[copying]=reversePath.get(reversePath.size()-1-copying);copying++;continue;
                }
                if(tracing!=null){
                    work++;
                    if(tracing.parent>=0){reversePath.add(tracing.arc);tracing=best.get(tracing.parent);continue;}
                    route=new int[reversePath.size()];copying=0;tracing=null;continue;
                }
                if(current==null){
                    work++;
                    if(open.isEmpty()){done=true;failure="disconnected directed ground components";break;}
                    current=open.remove();if(best.get(current.node)!=current){current=null;continue;}
                    if(current.node==goal){
                        tracing=current;continue;
                    }
                    edge=offsets[current.node];
                    if(work>=allowance || expired.getAsBoolean())break;
                }
                int limit=offsets[current.node+1];int[] rides=animated==null?null:animated.outgoing.get(current.node);
                if(edge>=limit+(rides==null?0:rides.length)){current=null;continue;}
                int arc=edge<limit?edge:to.length+rides[edge-limit];edge++;
                var ride=ride(arc);int target=ride==null?to[arc]:ride.to().component();work++;
                // Every arc is a native directed link; SCC membership supplies intra-tile reachability.
                double cost=current.cost+1;var previous=best.get(target);
                if(previous!=null && previous.cost<=cost)continue;
                if(previous==null && best.size()>=8192 || open.size()>=16384){done=true;failure="directed journey search capacity reached";break;}
                var next=new Visit(target,cost,current.node,arc);best.put(target,next);open.add(next);
            }
            return work;
        }
        private int lowerBound(int node){
            // Native rides may cross many tiles in one arc; tile hops are no longer admissible.
            if(animated!=null && !animated.rides.isEmpty())return 0;
            int a=nodeTiles[node],b=nodeTiles[goal];return Math.max(Math.abs(a%nx-b%nx),Math.abs(a/nx-b/nx));
        }
        boolean pending(){return !done;}
        String failure(){return failure;}
        int[] route(){return route;}
    }

    static PlayerBotNavmeshTopology build(NavMesh mesh)throws IOException{
        int nx=0,ny=0;
        for(int i=0;i<mesh.getMaxTiles();i++){var t=mesh.getTile(i);if(t!=null && t.data!=null){nx=Math.max(nx,t.data.header.x+1);ny=Math.max(ny,t.data.header.y+1);}}
        require(nx>0 && ny>0 && (long)nx*ny<=MAX_TILES,"topology grid limit");
        MeshTile[] grid=new MeshTile[nx*ny];int[] tiles=new int[grid.length+1];
        for(int i=0;i<mesh.getMaxTiles();i++){var t=mesh.getTile(i);if(t!=null && t.data!=null){int index=t.data.header.y*nx+t.data.header.x;require(grid[index]==null,"duplicate topology tile/layer");grid[index]=t;}}
        for(int i=0;i<grid.length;i++)tiles[i+1]=Math.addExact(tiles[i],grid[i]==null?0:grid[i].data.header.polyCount);
        require(tiles[grid.length]<=MAX_POLYS,"topology polygon limit");
        int[] components=new int[tiles[grid.length]];Arrays.fill(components,-1);int nodes=0;
        var filter=new DefaultQueryFilter();
        for(int i=0;i<grid.length;i++){
            var tile=grid[i];if(tile==null)continue;int n=tile.data.header.polyCount;long base=mesh.getPolyRefBase(tile);
            var edges=new ArrayList<int[]>();int[] counts=new int[n+1],inverseCounts=new int[n+1];
            for(var p:tile.data.polys)if(filter.passFilter(base|p.index,tile,p)){
                int visited=0;
                for(int link=tile.polyLinks[p.index];link!=NavMesh.DT_NULL_LINK;link=tile.links.get(link).next){
                    require(link>=0 && link<tile.links.size() && ++visited<=tile.links.size(),"invalid topology links");
                    var target=mesh.getTileAndPolyByRef(tile.links.get(link).ref);require(target.succeeded(),"invalid topology polygon ref");
                    if(target.result.first!=tile || !filter.passFilter(tile.links.get(link).ref,tile,target.result.second))continue;
                    edges.add(new int[]{p.index,target.result.second.index});counts[p.index+1]++;inverseCounts[target.result.second.index+1]++;
                }
            }
            for(int j=1;j<=n;j++){counts[j]+=counts[j-1];inverseCounts[j]+=inverseCounts[j-1];}
            int[] adjacency=new int[edges.size()],reverse=new int[edges.size()],a=counts.clone(),b=inverseCounts.clone();
            for(var e:edges){adjacency[a[e[0]]++]=e[1];reverse[b[e[1]]++]=e[0];}
            // Iterative Kosaraju: directed edges are never silently made bidirectional.
            boolean[] seen=new boolean[n];int[] stack=new int[n],cursor=new int[n],order=new int[n];int ordered=0;
            for(int p=0;p<n;p++)if(!seen[p] && filter.passFilter(base|p,tile,tile.data.polys[p])){
                int top=0;stack[0]=p;cursor[0]=counts[p];seen[p]=true;
                while(top>=0){int v=stack[top];if(cursor[top]<counts[v+1]){int w=adjacency[cursor[top]++];if(!seen[w]){seen[w]=true;stack[++top]=w;cursor[top]=counts[w];}}
                    else {order[ordered++]=v;top--;}}
            }
            Arrays.fill(seen,false);
            for(int j=ordered-1;j>=0;j--)if(!seen[order[j]]){
                int top=0;stack[0]=order[j];seen[order[j]]=true;
                while(top>=0){int v=stack[top--];components[tiles[i]+v]=nodes;
                    for(int k=inverseCounts[v];k<inverseCounts[v+1];k++){int w=reverse[k];if(!seen[w]){seen[w]=true;stack[++top]=w;}}}
                nodes++;
            }
        }
        var arcs=new ArrayList<int[]>();var points=new ArrayList<float[]>();var query=new NavMeshQuery(mesh);
        int[] offsets=new int[nodes+1];
        for(int i=0;i<grid.length;i++){
            var tile=grid[i];if(tile==null)continue;long base=mesh.getPolyRefBase(tile);
            for(var p:tile.data.polys){int from=components[tiles[i]+p.index];if(from<0)continue;
                for(int link=tile.polyLinks[p.index];link!=NavMesh.DT_NULL_LINK;link=tile.links.get(link).next){
                    var target=mesh.getTileAndPolyByRef(tile.links.get(link).ref);require(target.succeeded(),"invalid topology link");
                    var td=target.result.first.data;int to=components[tiles[td.header.y*nx+td.header.x]+target.result.second.index];
                    if(to<0 || from==to)continue;
                    require(arcs.size()<MAX_EDGES,"topology arc limit");
                    arcs.add(new int[]{from,to});offsets[from+1]++;
                    float[] first=center(query,base|p.index,tile.data,p),last=center(query,tile.links.get(link).ref,td,target.result.second);
                    points.add(new float[]{first[0],first[1],first[2],last[0],last[1],last[2]});
                }
            }
        }
        for(int i=1;i<=nodes;i++)offsets[i]+=offsets[i-1];int[] to=new int[arcs.size()],cursor=offsets.clone();float[] portals=new float[Math.multiplyExact(arcs.size(),6)];
        for(int i=0;i<arcs.size();i++){int at=cursor[arcs.get(i)[0]]++;to[at]=arcs.get(i)[1];System.arraycopy(points.get(i),0,portals,at*6,6);}
        return new PlayerBotNavmeshTopology(nx,ny,nodes,tiles,components,offsets,to,portals);
    }
    private static float[] center(NavMeshQuery query,long ref,MeshData tile,Poly poly)throws IOException{
        float[] point=new float[3];for(int v=0;v<poly.vertCount;v++)for(int axis=0;axis<3;axis++)point[axis]+=tile.verts[poly.verts[v]*3+axis]/poly.vertCount;
        var height=query.getPolyHeight(ref,point);require(height.succeeded() && height.result!=null && Float.isFinite(height.result),"unsupported topology portal height");point[1]=height.result;return point;
    }
    void write(Path file,String fingerprint)throws IOException{
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))){
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(fingerprint);out.writeInt(nx);out.writeInt(ny);out.writeInt(nodes);
            for(int[] array:List.of(tiles,components,offsets,to)){out.writeInt(array.length);for(int v:array)out.writeInt(v);}
            out.writeInt(portals.length);for(float v:portals)out.writeFloat(v);
        }
        Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static PlayerBotNavmeshTopology read(Path file,String fingerprint,String digest)throws IOException{
        long size=Files.size(file);require(size>0 && size<=256L*1024*1024,"topology size limit");
        byte[] payload;
        try(var input=Files.newInputStream(file)){payload=input.readNBytes((int)size);require(payload.length==size && input.read()==-1,"topology size changed");}
        require(hash(payload).equals(digest),"topology payload hash mismatch");
        try(var in=new DataInputStream(new ByteArrayInputStream(payload))){
            require(in.readInt()==MAGIC && in.readInt()==VERSION && in.readUTF().equals(fingerprint),"topology version/input mismatch");
            int nx=in.readInt(),ny=in.readInt(),nodes=in.readInt();require(nx>0 && ny>0 && (long)nx*ny<=MAX_TILES && nodes>=0 && nodes<=MAX_POLYS,"topology counts");
            int[] tiles=ints(in,nx*ny+1,nx*ny+1),components=ints(in,0,MAX_POLYS),offsets=ints(in,nodes+1,nodes+1),to=ints(in,0,MAX_EDGES);
            require(nodes<=components.length,"topology node ownership count");
            require(tiles[0]==0 && tiles[tiles.length-1]==components.length && offsets[0]==0 && offsets[nodes]==to.length,"topology array bounds");
            for(int i=1;i<tiles.length;i++)require(tiles[i]>=tiles[i-1] && tiles[i]<=components.length,"topology tile offsets");
            for(int i=1;i<offsets.length;i++)require(offsets[i]>=offsets[i-1] && offsets[i]<=to.length,"topology arc offsets");
            for(int c:components)require(c>=-1 && c<nodes,"topology component");for(int t:to)require(t>=0 && t<nodes,"topology destination");
            require(in.readInt()==to.length*6 && (long)to.length*24==in.available(),"topology portal count/remaining payload");float[] points=new float[to.length*6];for(int i=0;i<points.length;i++){points[i]=in.readFloat();require(Float.isFinite(points[i]),"topology finite point");}
            require(in.read()==-1,"topology trailing bytes");return new PlayerBotNavmeshTopology(nx,ny,nodes,tiles,components,offsets,to,points);
        }
    }
    private static int[] ints(DataInputStream in,int minimum,int maximum)throws IOException{
        int count=in.readInt();require(count>=minimum && count<=maximum && (long)count*4<=in.available(),"topology array count/payload");int[] result=new int[count];for(int i=0;i<count;i++)result[i]=in.readInt();return result;
    }
    private static void require(boolean valid,String why)throws IOException{if(!valid)throw new IOException(why);}
    static String hash(Path file)throws IOException{
        try(var in=Files.newInputStream(file)){var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)digest.update(buffer,0,n);return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    /** Offline derivative utility: authenticates existing maps; never invokes World or SQL. */
    public static void main(String[] args)throws Exception{
        if(args.length!=2 && !(args.length==3 && args[2].equals("--verify")))
            throw new IllegalArgumentException("NAVMESH_DIRECTORY EXTERNAL_TOPOLOGY_OUTPUT [--verify]");
        boolean verify=args.length==3;
        Path root=Path.of(args[0]).toRealPath(),output=Path.of(args[1]).toAbsolutePath();Files.createDirectories(output);
        byte[] reportBytes=Files.readAllBytes(root.resolve("report.properties"));String reportHash=hash(reportBytes);
        Properties report=new Properties();try(var in=new ByteArrayInputStream(reportBytes)){report.load(in);}
        Properties index=new Properties();index.setProperty("format","aion-topology-1");index.setProperty("report.sha256",reportHash);
        Properties recorded=new Properties();
        if(verify){
            try(var in=Files.newInputStream(output.resolve("topology.properties"))){recorded.load(in);}
            require(recorded.getProperty("format","").equals("aion-topology-1")
                && recorded.getProperty("report.sha256","").equals(reportHash),"topology catalog binding");
        }
        var executor=Executors.newSingleThreadExecutor();
        try(var store=new PlayerBotNavmeshStore(root,768L*1024*1024,executor::execute)){
            var maps=new TreeSet<Integer>();for(String key:report.stringPropertyNames())if(key.matches("map\\.\\d+\\.directory"))maps.add(Integer.parseInt(key.split("\\.")[1]));
            for(int map:maps){PlayerBotNavmeshStore.Result access;
                while((access=store.acquire(map)).status()==PlayerBotNavmeshStore.Status.WAITING)Thread.sleep(10);
                require(access.status()==PlayerBotNavmeshStore.Status.READY,"map unavailable: "+map+" "+access.reason());
                try(var lease=access.lease()){
                    var graph=build(lease.mesh());Path file=output.resolve(map+".pbtopology");
                    if(!verify)graph.write(file,lease.fingerprint());
                    String digest=verify?recorded.getProperty("map."+map+".sha256",""):hash(file);
                    require(!verify || recorded.getProperty("map."+map+".fingerprint","").equals(lease.fingerprint()),"topology native fingerprint");
                    var reread=read(file,lease.fingerprint(),digest);
                    require(reread.nodes==graph.nodes && Arrays.equals(reread.tiles,graph.tiles) && Arrays.equals(reread.components,graph.components)
                        && Arrays.equals(reread.offsets,graph.offsets) && Arrays.equals(reread.to,graph.to) && Arrays.equals(reread.portals,graph.portals),"full topology round trip");
                    index.setProperty("map."+map+".fingerprint",lease.fingerprint());index.setProperty("map."+map+".sha256",digest);
                    System.out.println("Map "+map+" components="+graph.nodes+" arcs="+graph.to.length+" bytes="+graph.bytes());
                }
            }
        }finally{executor.shutdown();if(!executor.awaitTermination(1,TimeUnit.MINUTES))throw new IOException("topology worker did not finish");}
        require(hash(root.resolve("report.properties")).equals(reportHash),"catalog changed during topology generation");
        if(verify){require(recorded.equals(index),"complete topology coverage");
            System.out.println("PASS: complete topology/native directed links and all arrays verified");return;}
        Path temporary=output.resolve("topology.properties.tmp");
        try(var out=Files.newOutputStream(temporary)){index.store(out,"Complete native directed tile connectivity; not gameplay acceptance");}
        Files.move(temporary,output.resolve("topology.properties"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
