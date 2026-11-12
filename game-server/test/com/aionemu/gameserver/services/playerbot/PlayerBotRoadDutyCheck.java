package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig;
import com.aionemu.gameserver.model.Race;

/** Pure road-duty decisions: no actors, native world data, SQL or gameplay fixtures. */
public final class PlayerBotRoadDutyCheck {
 private static int checks;
 private static final int ACCOUNT=PlayerBotPopulationPolicy.account(Race.ELYOS,0),CHARACTER=100001;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static PlayerBotPatrolRoutes.Route route(String id,int level,boolean guardable) {
  return new PlayerBotPatrolRoutes.Route(id,1,Race.ELYOS,level,1,id,List.of(
   new PlayerBotNavigation.Point(0,0,5),new PlayerBotNavigation.Point(10,0,5),
   new PlayerBotNavigation.Point(20,0,5),new PlayerBotNavigation.Point(30,0,5)),Map.of(),guardable);
 }
 private static void advance() {
  for(int point=0;point<3;point++) {
   var step=PlayerBotRoadDuty.advance(point,1,4);
   check(step.point()==point+1 && step.direction()==1 && !step.pause(),"outbound intermediate waypoints keep walking");
  }
  var turn=PlayerBotRoadDuty.advance(3,1,4);
  check(turn.point()==2 && turn.direction()==-1 && turn.pause(),"only the outbound endpoint pauses and reverses");
  for(int point=3;point>0;point--) {
   var step=PlayerBotRoadDuty.advance(point,-1,4);
   check(step.point()==point-1 && step.direction()==-1 && !step.pause(),"return intermediate waypoints keep walking");
  }
  turn=PlayerBotRoadDuty.advance(0,-1,4);
  check(turn.point()==1 && turn.direction()==1 && turn.pause(),"the return endpoint pauses and reverses");
  check(PlayerBotRoadDuty.arrived(3,2.5,true),"arrival includes the ground-floor tolerance boundary");
  check(!PlayerBotRoadDuty.arrived(0,2.51,true),"a stacked road directly overhead is not arrival");
  check(!PlayerBotRoadDuty.arrived(3.01,0,true),"the ground-floor check retains the horizontal arrival radius");
  check(PlayerBotRoadDuty.arrived(3,20,false),"a raw native walker height does not invent a required floor");
  check(!PlayerBotRoadDuty.arrived(3.01,0,false),"raw walker targets still require horizontal arrival");
 }
 private static void progress() {
  var stationary=new PlayerBotRoadDuty.Progress();
  check(!stationary.stalled(1000,4,5,6),"the first movement sample starts the watchdog");
  for(long now=6000;now<61000;now+=5000)
   check(!stationary.stalled(now,4,5,6),"stationary planning has the full minute before recovery");
  check(stationary.stalled(61000,4,5,6),"planning without physical displacement stalls after one minute");
  check(!stationary.stalled(66000,5,5,6),"one metre of physical movement clears a stall");

  var climbing=new PlayerBotRoadDuty.Progress();
  for(int sample=0;sample<=20;sample++)
   check(!climbing.stalled(1000+sample*5000L,0,0,sample*.25f),"cumulative vertical climbing is physical progress");
  var diagonal=new PlayerBotRoadDuty.Progress();
  check(!diagonal.stalled(1000,0,0,0),"diagonal progress starts from a physical anchor");
  for(long now=6000;now<61000;now+=5000)diagonal.stalled(now,0,0,0);
  check(!diagonal.stalled(61000,.6f,.6f,.6f),"the one-metre threshold uses full 3D displacement");

  var paused=new PlayerBotRoadDuty.Progress();
  paused.stalled(1000,0,0,0);
  for(long now=6000;now<=56000;now+=5000)paused.stalled(now,0,0,0);
  paused.reset();
  check(!paused.stalled(61000,0,0,0),"an attended shop, endpoint pause or escort reset starts a new movement interval");
  for(long now=66000;now<121000;now+=5000)
   check(!paused.stalled(now,0,0,0),"the completed pause is not charged against the next movement interval");
  check(paused.stalled(121000,0,0,0),"stationary travel still stalls one minute after a reset");

  var interrupted=new PlayerBotRoadDuty.Progress();
  interrupted.stalled(1000,0,0,0);
  for(long now=6000;now<=56000;now+=5000)interrupted.stalled(now,0,0,0);
  check(!interrupted.stalled(120000,0,0,0),"a gap outside road duty does not cause immediate stall recovery");
  check(!interrupted.stalled(125000,0,0,0),"normal sampling resumes after a duty gap");
 }
 private static int identity(PlayerBotRoadDuty.Career career) {
  for(int id=100000;id<101000;id++)if(PlayerBotRoadDuty.career(id)==career)return id;
  throw new AssertionError("No identity with career "+career);
 }
 private static void careers() {
  var city=route("city",65,false);var field=route("field",20,true);
  int id=identity(PlayerBotRoadDuty.Career.WARDEN);var career=PlayerBotRoadDuty.career(id);
  check(!PlayerBotPatrolRoutes.suitable(city,career),"a Warden cannot take a city-only walker route");
  check(PlayerBotPatrolRoutes.suitable(field,career),"a Warden can take a native field route");
  check(!PlayerBotPatrolRoutes.suitable(null,career),"a missing route cannot become Warden duty");
  check(PlayerBotPatrolRoutes.suitable(city,PlayerBotRoadDuty.Career.MERCHANT),"merchants retain their city routes");
  check(!PlayerBotRoadDuty.recheck(1,20,1,20,1000,2000),"unchanged location and level do not re-evaluate uncommitted duty");
  check(PlayerBotRoadDuty.recheck(1,20,2,20,1000,2000),"a map change re-evaluates an uncommitted city identity");
  check(PlayerBotRoadDuty.recheck(1,20,1,21,1000,2000),"a level change re-evaluates uncommitted duty");
  check(PlayerBotRoadDuty.recheck(1,20,2,21,1000,2000),"a combined map and level change re-evaluates duty");
  check(PlayerBotRoadDuty.recheck(1,20,1,20,2000,2000),"a resident walking the same map gets another look when the retry falls due");
  for(int evaluation=0;evaluation<5;evaluation++)
   check(PlayerBotRoadDuty.career(id)==career,"re-evaluation preserves the identity's uncommitted Warden career");
  int merchant=identity(PlayerBotRoadDuty.Career.MERCHANT),ordinary=identity(PlayerBotRoadDuty.Career.NONE);
  check(PlayerBotRoadDuty.career(merchant)==PlayerBotRoadDuty.Career.MERCHANT,"merchant identity stays independently allocated");
  check(PlayerBotRoadDuty.career(ordinary)==PlayerBotRoadDuty.Career.NONE,"an ordinary resident does not gain a road career");
 }
 private static Properties saved(String career,PlayerBotPatrolRoutes.Route route) {
  var p=new Properties();
  p.setProperty("account",Integer.toString(ACCOUNT));p.setProperty("character",Integer.toString(CHARACTER));
  p.setProperty("career",career);p.setProperty("rank","MASTER");p.setProperty("route",route.id());
  p.setProperty("point","2");p.setProperty("direction","-1");p.setProperty("visitor","9001");
  p.setProperty("stopUntil","50000");p.setProperty("escortUntil","40000");p.setProperty("waitUntil","30000");
  p.setProperty("suspended","true");p.setProperty("restockAt","20000");p.setProperty("stockCapacity","23");
  p.setProperty("stock.501","210001:210010000");return p;
 }
 private static void valid(Properties p,String why)throws IOException {
  PlayerBotMetadata.validate(ACCOUNT,CHARACTER,"road-duty",PlayerBotMetadata.values(p));check(true,why);
 }
 private static void rejected(Properties base,String key,String value)throws IOException {
  var p=(Properties)base.clone();p.setProperty(key,value);
  try{PlayerBotMetadata.validate(ACCOUNT,CHARACTER,"road-duty",PlayerBotMetadata.values(p));throw new AssertionError("Accepted "+key+"="+value);}
  catch(IOException expected){check(true,"malformed road metadata is rejected: "+key);}
 }
 private static void migration()throws IOException {
  var city=route("city",65,false);var field=route("field",20,true);var p=saved("WARDEN",city);
  valid(p,"the saved city Warden metadata is structurally valid before route migration");
  var retained=new HashMap<>(PlayerBotMetadata.values(p));
  var cleared=List.of("route","point","direction","visitor","stopUntil","escortUntil","waitUntil","suspended");
  for(String key:cleared)retained.remove(key);
  check(PlayerBotRoadDuty.migrate(p,city),"a saved city Warden loses its invalid road assignment");
  for(String key:cleared)check(!p.containsKey(key),"city Warden migration clears stale "+key);
  check(PlayerBotMetadata.values(p).equals(retained),"migration preserves identity, career, rank, restock and stock provenance exactly");
  valid(p,"a migrated Warden remains valid native metadata without a route");
  check(!PlayerBotRoadDuty.migrate(p,city),"city Warden migration is idempotent");

  var existing=saved("WARDEN",field);var before=PlayerBotMetadata.values(existing);
  check(!PlayerBotRoadDuty.migrate(existing,field) && PlayerBotMetadata.values(existing).equals(before),"field Wardens keep saved progress, escort and rank unchanged");
  valid(existing,"saved field Warden metadata remains valid");
  var merchant=saved("MERCHANT",city);before=PlayerBotMetadata.values(merchant);
  check(!PlayerBotRoadDuty.migrate(merchant,city) && PlayerBotMetadata.values(merchant).equals(before),"a city merchant keeps its route and committed provenance");
  valid(merchant,"a city merchant's retained stock metadata is valid");
  var missing=saved("WARDEN",field);
  check(PlayerBotRoadDuty.migrate(missing,null) && missing.getProperty("career").equals("WARDEN"),"a missing saved Warden route is cleared without rerolling its career");
  valid(missing,"an unavailable route leaves a valid uncommitted Warden identity");
  rejected(existing,"direction","0");rejected(existing,"waitUntil","-1");
  rejected(existing,"suspended","yes");rejected(merchant,"stock.501","210001:0");
 }
 private static void routes() {
  var fit=route("level-fit",40,true);var bridge=route("bridge",1,false);var far=route("far",42,true);
  var distance=Map.of(fit,1d,bridge,2d,far,50d);
  check(PlayerBotPatrolRoutes.choose(List.of(fit,bridge),40,distance::get,r->r==bridge)==bridge,"a reachable out-of-level bridge wins over an unreachable level fit");
  check(PlayerBotPatrolRoutes.choose(List.of(fit,bridge),40,distance::get,r->true)==fit,"reachable level-fit routes retain their preference");
  check(PlayerBotPatrolRoutes.choose(List.of(far,fit),40,distance::get,r->true)==fit,"distance decides between reachable level-fit routes");
  check(PlayerBotPatrolRoutes.choose(List.of(fit,bridge),40,distance::get,r->false)==null,"an unreachable route is never selected as a fallback");
  check(PlayerBotPatrolRoutes.choose(List.of(),40,r->0,r->true)==null,"an empty route catalogue leaves duty unassigned");
  var a=route("a",40,true);var b=route("b",40,true);
  check(PlayerBotPatrolRoutes.choose(List.of(b,a),40,r->5,r->true)==a,"equal reachable candidates have stable ordering");
 }
 private static void merchants() {
  var current=route("current",40,true);var blocked=route("blocked-empty",40,true);
  var reachable=route("reachable",40,true);var bridge=route("bridge",1,false);
  var used=Map.of(current.id(),3,reachable.id(),1,bridge.id(),2);
  check(PlayerBotRoadDuty.emptiest(List.of(current,blocked,reachable,bridge),current,40,used,r->r!=blocked)==reachable,"merchant distribution chooses the emptiest reachable route");
  check(PlayerBotRoadDuty.emptiest(List.of(current,bridge),current,40,Map.of(current.id(),2),r->true)==bridge,"an emptier reachable bridge retains merchant distribution ahead of level fit");
  check(PlayerBotRoadDuty.emptiest(List.of(current,reachable),current,40,Map.of(),r->true)==current,"equally occupied fitting routes retain the current merchant road");
  check(PlayerBotRoadDuty.emptiest(List.of(current,bridge),bridge,40,Map.of(),r->true)==current,"level fit breaks an occupancy tie among reachable routes");
  check(PlayerBotRoadDuty.emptiest(List.of(current,reachable),current,40,Map.of(),r->r==reachable)==reachable,"an unreachable current road is not retained on an occupancy tie");
  check(PlayerBotRoadDuty.emptiest(List.of(current,blocked),current,40,Map.of(),r->false)==null,"merchants remain unassigned when every candidate is unreachable");
 }
 private static void guards() {
  var points=List.of(new PlayerBotNavigation.Point(0,0,5),new PlayerBotNavigation.Point(100,0,5),new PlayerBotNavigation.Point(200,0,5),new PlayerBotNavigation.Point(300,0,5));
  var road=new PlayerBotPatrolRoutes.Route("road",1,Race.ELYOS,10,1,"road",points,PlayerBotPatrolRoutes.index(points),false);
  var near=new ArrayList<PlayerBotNavigation.Point>();
  check(!PlayerBotPatrolRoutes.guardable(road,near),"a road with no monsters has nothing to guard");
  near.add(new PlayerBotNavigation.Point(150,10,5));
  check(!PlayerBotPatrolRoutes.guardable(road,near),"one stray critter never qualifies a capital road");
  for(int i=1;i<PlayerBotPatrolRoutes.GUARD_MONSTERS-1;i++)near.add(new PlayerBotNavigation.Point(150,10+i,5));
  check(!PlayerBotPatrolRoutes.guardable(road,near),"one monster short of the count does not qualify");
  near.add(new PlayerBotNavigation.Point(290,-90,5));
  check(PlayerBotPatrolRoutes.guardable(road,near),"enough monsters inside the Warden corridor qualify a road");
  var far=new ArrayList<PlayerBotNavigation.Point>();
  for(int i=0;i<40;i++)far.add(new PlayerBotNavigation.Point(150,300+i,5));
  check(!PlayerBotPatrolRoutes.guardable(road,far),"a safe town road does not qualify from monsters elsewhere on its map");
  var below=new ArrayList<PlayerBotNavigation.Point>();
  for(int i=0;i<40;i++)below.add(new PlayerBotNavigation.Point(150,i,-150));
  check(!PlayerBotPatrolRoutes.guardable(road,below),"monsters far below a bridge are outside its 3D corridor");
  check(PlayerBotPatrolRoutes.suitable(road.guardable(true),PlayerBotRoadDuty.Career.WARDEN),"a guardable road accepts a Warden");
  check(!PlayerBotPatrolRoutes.suitable(road,PlayerBotRoadDuty.Career.WARDEN),"a road without monsters refuses a Warden on any map");
  check(PlayerBotPatrolRoutes.suitable(road,PlayerBotRoadDuty.Career.MERCHANT),"merchants are not gated by monsters");
 }
 public static void main(String[] args)throws Exception {
  // RoadDuty's existing rank table loads eagerly; only its nonzero weight is needed by these pure checks.
  PlayerBotMerchantConfig.CHANCE_PEDDLER=1;
  advance();progress();careers();migration();routes();merchants();guards();
  System.out.println("OK: "+checks+" road duty checks");
 }
 private PlayerBotRoadDutyCheck(){}
}
