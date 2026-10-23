package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Shared learned map geometry, never character/quest progress. Recorded paths
 * are directed suggestions; the native mover still proves actual movement. */
public final class PlayerBotRouteMemory {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PlayerBotRouteMemory.class);
 private static final Store MEMORY=new Store(Path.of("data","navmesh","learned-routes.csv"));
 private PlayerBotRouteMemory(){}
 static void remember(int world,Point start,Point goal,List<Point> reached){
  if(PlayerBotConfig.NAVIGATION_MEMORY_FIRST)MEMORY.remember(world,start,goal,reached);
 }
 static List<Point> lookup(int world,Point start,Point goal){
  if(!PlayerBotConfig.NAVIGATION_MEMORY_FIRST)return List.of();
  List<Point> path=MEMORY.lookup(world,start,goal);
  Point from=start;
  for(Point to:path){
   if(PlayerBotMeshRoute.learnedObstruction(world,from,to)){
    MEMORY.forget(world,start,goal);return List.of();
   }
   from=to;
  }
  return path;
 }
 /** Called only after an actual native ground step failed, never for a hazard,
  * casting pause, queued search, moving platform or an untested mesh proposal. */
 public static void obstructed(int world,float x,float y,float z,float wantedX,float wantedY){
  if(!PlayerBotConfig.NAVIGATION_MEMORY_FIRST)return;
  double distance=Math.hypot(wantedX-x,wantedY-y);
  if(!Double.isFinite(distance) || distance<.01 || !Float.isFinite(z))return;
  double fraction=Math.min(1,.75/distance);
  PlayerBotMeshRoute.rememberObstruction(world,(float)(x+(wantedX-x)*fraction),(float)(y+(wantedY-y)*fraction),z);
 }
 static void flush(){MEMORY.flush();}

 /** Short synchronized map operations; disk snapshots never hold the read lock.
  * The bound is shared geometry memory, not a queue or a per-character state file. */
 static final class Store {
  private static final int LIMIT=4096,POINTS=64,PER_ORIGIN=8;
  private record Cell(int world,int x,int y,int z){}
  private record Key(Cell origin,int x,int y,int z){}
  private record PathRecord(int world,Point start,Point goal,List<Point> points){}
  private final Path file;
  private final LinkedHashMap<Key,PathRecord> paths=new LinkedHashMap<>();
  private final Map<Cell,LinkedHashSet<Key>> origins=new HashMap<>();
  private final Object diskLock=new Object();
  private volatile boolean loaded;
  private long revision,saved;
  Store(Path file){this.file=file;}
  private static Cell cell(int world,Point p){return new Cell(world,(int)Math.floor(p.x()/4),(int)Math.floor(p.y()/4),(int)Math.floor(p.z()/2));}
  private static Key key(int world,Point start,Point goal){var end=cell(world,goal);return new Key(cell(world,start),end.x(),end.y(),end.z());}
  private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
  private static boolean finite(Point p){return p!=null && Float.isFinite(p.x()) && Float.isFinite(p.y()) && Float.isFinite(p.z());}
  private void load(){
   if(loaded)return;
   synchronized(diskLock){
    if(loaded)return;
    try{
     if(Files.isRegularFile(file) && Files.size(file)<=32L*1024*1024){
      try(var lines=Files.lines(file,StandardCharsets.UTF_8)){
       lines.limit(LIMIT+1L).forEach(line->{
        String[] values=line.split(",",8);if(values.length!=8 || values[0].equals("map"))return;
        try{
         int world=Integer.parseInt(values[0]);
         var start=new Point(Float.parseFloat(values[1]),Float.parseFloat(values[2]),Float.parseFloat(values[3]));
         var goal=new Point(Float.parseFloat(values[4]),Float.parseFloat(values[5]),Float.parseFloat(values[6]));
         var points=new ArrayList<Point>();String[] encoded=values[7].split("\\|",POINTS+1);
         if(encoded.length>POINTS)return;
         for(String point:encoded){String[] xyz=point.split(":");if(xyz.length!=3)return;points.add(new Point(Float.parseFloat(xyz[0]),Float.parseFloat(xyz[1]),Float.parseFloat(xyz[2])));}
         put(world,start,goal,points);
        }catch(NumberFormatException ignored){/* An incomplete/corrupt row grants no path. */}
       });
      }
     }
    }catch(IOException e){LOG.warn("Could not read recorded navigation routes {}: {}",file,e.getMessage());}
    synchronized(this){saved=revision;}loaded=true;
   }
  }
  void remember(int world,Point start,Point goal,List<Point> points){load();put(world,start,goal,points);}
  private synchronized void put(int world,Point start,Point goal,List<Point> points){
   if(world<=0 || !finite(start) || !finite(goal) || points.isEmpty() || points.size()>POINTS || points.stream().anyMatch(p->!finite(p)))return;
   double length=0;Point previous=start;
   for(Point p:points){length+=distance(previous,p);previous=p;}
   if(length<4 || length>128 || distance(start,previous)<2 || distance(previous,goal)>distance(start,goal)-.6)return;
   var key=key(world,start,goal);var old=paths.get(key);
   if(old!=null && distance(old.start(),start)<.25 && distance(old.goal(),goal)<.25
    && distance(old.points().getLast(),goal)<=distance(previous,goal))return;
   remove(key);
   var bucket=origins.computeIfAbsent(key.origin(),k->new LinkedHashSet<>());
   if(bucket.size()>=PER_ORIGIN){remove(bucket.iterator().next());bucket=origins.computeIfAbsent(key.origin(),k->new LinkedHashSet<>());}
   paths.put(key,new PathRecord(world,start,goal,List.copyOf(points)));bucket.add(key);revision++;
   while(paths.size()>LIMIT)remove(paths.keySet().iterator().next());
  }
  List<Point> lookup(int world,Point start,Point goal){
   load();if(!finite(start) || !finite(goal))return List.of();
   synchronized(this){
    Cell at=cell(world,start);PathRecord chosen=null;double best=Double.POSITIVE_INFINITY;
    for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
     var bucket=origins.get(new Cell(world,at.x()+dx,at.y()+dy,at.z()+dz));if(bucket==null)continue;
     for(Key key:bucket){var candidate=paths.get(key);
      if(distance(start,candidate.start())>.75 || distance(goal,candidate.goal())>.75)continue;
      double remaining=distance(candidate.points().getLast(),goal);
      if(remaining>=distance(start,goal)-.6 || remaining>=best)continue;
      chosen=candidate;best=remaining;
     }
    }
    return chosen==null?List.of():chosen.points();
   }
  }
  void forget(int world,Point start,Point goal){
   synchronized(this){
    var invalid=new ArrayList<Key>();
    for(var entry:paths.entrySet())if(entry.getValue().world()==world && distance(start,entry.getValue().start())<=.75 && distance(goal,entry.getValue().goal())<=.75)invalid.add(entry.getKey());
    invalid.forEach(this::remove);
   }
  }
  private void remove(Key key){
   if(paths.remove(key)==null)return;
   var bucket=origins.get(key.origin());if(bucket!=null){bucket.remove(key);if(bucket.isEmpty())origins.remove(key.origin());}revision++;
  }
  void flush(){
   if(!loaded)return;
   synchronized(diskLock){
    List<PathRecord> copy;long version;
    synchronized(this){if(revision==saved)return;copy=List.copyOf(paths.values());version=revision;}
    var lines=new ArrayList<String>();lines.add("map,start_x,start_y,start_z,goal_x,goal_y,goal_z,points");
    for(var path:copy){var points=new StringJoiner("|");for(Point p:path.points())points.add(p.x()+":"+p.y()+":"+p.z());
     lines.add(path.world()+","+path.start().x()+","+path.start().y()+","+path.start().z()+","+path.goal().x()+","+path.goal().y()+","+path.goal().z()+","+points);
    }
    Path temporary=file.resolveSibling(file.getFileName()+".tmp");
    try{
     Files.createDirectories(file.toAbsolutePath().getParent());Files.write(temporary,lines,StandardCharsets.UTF_8);
     try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
     catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
     synchronized(this){saved=version;}
    }catch(IOException e){LOG.warn("Could not save recorded navigation routes {}: {}",file,e.getMessage());}
   }
  }
 }
}
