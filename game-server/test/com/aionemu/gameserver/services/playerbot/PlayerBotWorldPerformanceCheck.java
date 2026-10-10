package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.alliance.PlayerAlliance;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import sun.misc.Unsafe;

/** Route equivalence, bounded work and native team identity without World, DB or allocated game IDs. */
public final class PlayerBotWorldPerformanceCheck {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static PlayerBotWorldTravel.Edge edge(int from,int to,int id){return new PlayerBotWorldTravel.Edge(new PlayerBotWorldTravel.Place(from,id,3,4,0),to,id);}
 // Independent reference: the deployed per-destination breadth-first traversal.
 static List<PlayerBotWorldTravel.Edge> reference(int from,int to,List<PlayerBotWorldTravel.Edge> edges){
  if(from==to)return List.of();var queue=new ArrayDeque<List<PlayerBotWorldTravel.Edge>>();var seen=new HashSet<Integer>();seen.add(from);
  for(var edge:edges)if(edge.source().map()==from && seen.add(edge.destination()))queue.add(List.of(edge));
  while(!queue.isEmpty()) {var path=queue.remove();int map=path.getLast().destination();if(map==to)return path;
   for(var edge:edges)if(edge.source().map()==map && seen.add(edge.destination())){var next=new ArrayList<>(path);next.add(edge);queue.add(next);}}
  return null;
 }
 static void routes(){
  var random=new Random(7801);
  for(int run=0;run<10;run++){
   var graph=new ArrayList<PlayerBotWorldTravel.Edge>();for(int i=1;i<=160;i++)graph.add(edge(random.nextInt(16)+1,random.nextInt(16)+1,i));
   graph.add(edge(1,2,161));graph.add(edge(1,2,162));
   for(int from=1;from<=17;from++){var paths=PlayerBotWorldTravel.routes(from,graph,e->true);
    for(int to=1;to<=17;to++)check(Objects.equals(paths.get(to),reference(from,to,graph)),"Route/tie/direction/cycle equivalence "+from+"->"+to);}
  }
  var gates=new AtomicInteger();var graph=List.of(edge(1,2,1),edge(2,3,2),edge(3,1,3),edge(1,4,4));
  var paths=PlayerBotWorldTravel.routes(1,graph,e->{gates.incrementAndGet();return e.location()!=4;});
  var distances=new PlayerBotWorldTravel.Distances(1,0,0,0,paths);
  for(int i=0;i<20000;i++)distances.distance(new PlayerBotWorldTravel.Place(3,1,0,0,0));
  check(gates.get()==4,"Permissions evaluated once per edge, never once per candidate");
  check(distances.distance(new PlayerBotWorldTravel.Place(3,1,0,0,0))==4005,"Same original hop/distance scoring");
  check(distances.distance(new PlayerBotWorldTravel.Place(1,1,3,4,0))==5,"Same-map Euclidean distance");
  check(Double.isInfinite(distances.distance(new PlayerBotWorldTravel.Place(4,1,0,0,0))),"Disallowed destination stays unreachable");
  check(PlayerBotWorldTravel.routes(1,graph,e->true).containsKey(4),"Changed permissions refreshed next decision");
  check(!PlayerBotWorldTravel.routes(1,graph,e->false).containsKey(2),"Lost eligibility does not use stale routes");
 }
 static void work()throws Exception {
  var worker=new PlayerBotBackgroundWork();var queue=new ArrayList<Runnable>();Executor queued=queue::add;var calls=new AtomicInteger();
  check(worker.submit(queued,calls::incrementAndGet),"Initial job admitted");check(worker.busy(),"Queued job occupies budget");
  for(int i=0;i<60;i++)check(!worker.submit(queued,calls::incrementAndGet),"No backlog while initialization pending");
  check(queue.size()==1,"Only one native job queued");queue.removeFirst().run();check(calls.get()==1 && !worker.busy(),"Job completion releases budget");
  try{worker.submit(r->{throw new RejectedExecutionException();},calls::incrementAndGet);throw new AssertionError();}catch(RejectedExecutionException e){check(!worker.busy(),"Rejected scheduling releases budget");}
  check(worker.submit(queued,()->{throw new IllegalStateException();}),"Failure job admitted");
  try{queue.removeFirst().run();throw new AssertionError();}catch(IllegalStateException e){check(!worker.busy(),"Native failure releases budget");}
  check(worker.submit(queued,calls::incrementAndGet),"Retry admitted after failure");worker.close();queue.removeFirst().run();
  check(calls.get()==1 && !worker.busy(),"Shutdown skips queued creation");check(!worker.submit(queued,calls::incrementAndGet),"Shutdown cannot reopen work");
  var concurrent=new PlayerBotBackgroundWork();var pool=Executors.newFixedThreadPool(2);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);
  try {
   check(concurrent.submit(pool,()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{done.countDown();}}),"Concurrent job admitted");
   check(entered.await(3,TimeUnit.SECONDS),"Worker starts independently of caller");
   check(!concurrent.submit(pool,()->{throw new AssertionError("Overlapping native initialization");}),"Running worker rejects overlap");
   release.countDown();check(done.await(3,TimeUnit.SECONDS),"Worker completes normally");
  }finally{release.countDown();pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS),"Executor terminates");}
  check(!concurrent.busy(),"Running completion restores budget");
 }
 static class Group extends PlayerGroup {List<Player> players;Group(){super(null,null,1);}@Override public List<Player> getMembers(){return players;}}
 static class Alliance extends PlayerAlliance {List<Player> players;Alliance(){super(null,TeamType.ALLIANCE);}@Override public List<Player> getMembers(){return players;}@Override public TeamType getTeamType(){return TeamType.ALLIANCE;}}
 static class Actor extends PlayerBotTravelFormationCheck.Actor {
  Alliance alliance;Race race;String name;int world,instance;float x;
  Actor(){super();}@Override public boolean isInAlliance(){return alliance!=null;}@Override public PlayerAlliance getPlayerAlliance(){return alliance;}
  @Override public Race getRace(){return race;}@Override public String getName(boolean customTag){return name;}@Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}@Override public float getX(){return x;}
  @Override public boolean isInAnyHide(){return false;}@Override public boolean isWorldBot(){return owner!=0;}
 }
 static Actor actor(Unsafe u,int id)throws Exception{var a=(Actor)u.allocateInstance(Actor.class);a.id=id;a.online=true;a.spawned=true;a.race=Race.ELYOS;a.world=1;a.instance=1;a.name="Bot";a.tasks=new PlayerBotTravelFormationCheck.Tasks();return a;}
 static void teams()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);var a=actor(u,1);var b=actor(u,2);b.owner=2;
  check(!PlayerBotTeams.same(a,b),"Unattached players are not a team");check(PlayerBotTeams.members(a).isEmpty(),"No invented solo team");
  var party=(PlayerBotTravelFormationCheck.Group)u.allocateInstance(PlayerBotTravelFormationCheck.Group.class);party.players=List.of(a,b);a.group=b.group=party;
  check(PlayerBotTeams.same(a,b),"Actual native party identity shared");check(PlayerBotTeams.members(a).equals(List.of(a,b)),"Party support members preserved");
  a.group=b.group=null;var alliance=(Alliance)u.allocateInstance(Alliance.class);alliance.players=List.of(a,b);a.alliance=b.alliance=alliance;
  check(PlayerBotTeams.same(a,b),"Alliance identity accepted without a separate party");check(PlayerBotTeams.members(a).equals(List.of(a,b)),"Alliance support recipients use native membership");
  b.alliance=(Alliance)u.allocateInstance(Alliance.class);check(!PlayerBotTeams.same(a,b),"Different alliances never share control");
  check(PlayerBotTeams.allowed(alliance),"Ordinary native alliance permitted");check(PlayerBotTeams.allowed(TeamType.GROUP),"Party permitted");
  for(var type:TeamType.values())check(PlayerBotTeams.allowed(type)==(type==TeamType.GROUP || type==TeamType.ALLIANCE),"No league/arena type invented");
  a.tasks.combat=true;check(PlayerBotWorldTrade.refusal(a,b).contains("Your combat timer"),"Human combat refusal explained");a.tasks.combat=false;
  b.tasks.combat=true;check(PlayerBotWorldTrade.refusal(a,b).contains("Bot is still in combat"),"Bot combat refusal explained");b.tasks.combat=false;
  b.x=6;check(PlayerBotWorldTrade.refusal(a,b).contains("5 metres"),"Native five-metre range explained");b.x=0;
  b.world=2;check(PlayerBotWorldTrade.refusal(a,b).contains("same map"),"Map mismatch explained");b.world=1;
  b.race=Race.ASMODIANS;check(PlayerBotWorldTrade.refusal(a,b).contains("own faction"),"Faction mismatch explained");b.race=Race.ELYOS;
  b.dead=true;check(PlayerBotWorldTrade.refusal(a,b).contains("alive"),"Death refusal explained");b.dead=false;
  b.spawned=false;check(PlayerBotWorldTrade.refusal(a,b).contains("entered the world"),"Pending entry explained");
 }
 public static void main(String[] args)throws Exception{routes();work();teams();System.out.println("OK: "+checks+" world performance/team checks; no World/DB/native IDs/casts");}
}
