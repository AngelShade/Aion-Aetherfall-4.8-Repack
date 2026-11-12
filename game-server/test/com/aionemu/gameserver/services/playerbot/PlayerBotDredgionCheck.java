package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.instancescore.DredgionInstanceScore;
import com.aionemu.gameserver.services.playerbot.PlayerBotDredgionTactics.Opening;
import com.aionemu.gameserver.world.WorldPosition;

/** PvP-3a Dredgion objective play: the pure rules, the tables against the real map handlers, and the wiring. No World/SQL. */
public final class PlayerBotDredgionCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  rules();
  tactics();
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  tables(root);
  openingRoutes(root);
  portals(root);
  recovery();
  wiring(root);
  System.out.println("OK: "+checks+" Dredgion checks; no World/SQL/tasks");
 }
 static void tactics() {
  check(PlayerBotDredgionTactics.opening(2,false,true)==Opening.CLEAR_LANE,"A party unable to rush clears a nearby lane");
  check(PlayerBotDredgionTactics.opening(0,true,true)==Opening.CLEAR_LANE,"A ready party can choose to clear a lane");
  check(PlayerBotDredgionTactics.opening(1,true,true)==Opening.RUSH_GRAVITY,"A ready party can race to Gravity");
  check(PlayerBotDredgionTactics.opening(2,true,true)==Opening.BLOCK_OPPONENT,"The opposing route can be blocked where a native generator is verified");
  check(PlayerBotDredgionTactics.opening(2,true,false)==Opening.CLEAR_LANE
   && PlayerBotDredgionTactics.opening(-1,true,false)==Opening.RUSH_GRAVITY,"A ship without a verified generator uses either scoring opening");
  check(!PlayerBotDredgionTactics.late(-1) && !PlayerBotDredgionTactics.late(0) && !PlayerBotDredgionTactics.late(120_001)
   && PlayerBotDredgionTactics.late(120_000) && PlayerBotDredgionTactics.late(1),"Late planning uses the last two minutes of the live clock");
  check(PlayerBotDredgionTactics.lateScore(700,40,699,60_000)>PlayerBotDredgionTactics.lateScore(699,1,699,60_000),
   "A projected win ranks ahead of a faster projected tie");
  check(PlayerBotDredgionTactics.lateScore(700,10,1200,60_000)>PlayerBotDredgionTactics.lateScore(1000,40,1200,60_000),
   "A quick objective can outscore a larger objective when neither erases the deficit");
  check(PlayerBotDredgionTactics.lateScore(1000,59.5,200,60_000)>0
   && PlayerBotDredgionTactics.lateScore(1000,60,200,60_000)==0,"An objective whose approach alone consumes the remaining time does not qualify");
  check(PlayerBotDredgionTactics.lateScore(1000,5,200,120_001)==0
   && PlayerBotDredgionTactics.lateScore(1000,5,200,0)==0,"The normal picker owns time outside the late window");
  check(PlayerBotDredgionTactics.lateScore(0,5,200,60_000)==0
   && PlayerBotDredgionTactics.lateScore(1000,Double.NaN,200,60_000)==0,"Late scoring requires a score-bearing target and a valid estimate");
  var clock=new DredgionInstanceScore(4500,2500,3750,Race.ASMODIANS);
  int opening=clock.openingDraw(Race.ASMODIANS);
  check(!clock.starboard(Race.ELYOS) && clock.starboard(Race.ASMODIANS),"Lane selection follows the native race swap");
  long now=1_000_000;
  clock.startClock(now);
  check(clock.timeRemaining(now)==120_000 && clock.timeRemaining(now+120_000)==2_400_000,
   "The native clock switches from preparation to the full match duration when gates open");
  check(clock.timeRemaining(now+2_400_000)==120_000 && clock.timeRemaining(now+2_520_000)==0,
   "Late selection uses the last two minutes of the same clock as the client");
  check(clock.openingDraw(Race.ASMODIANS)==opening,"One faction uses the same draw for the life of a match");
 }
 static void openingRoutes(Path root)throws Exception {
  var ships=Map.of(PlayerBotDredgion.BARANATH,"300110000_Dredgion.xml",PlayerBotDredgion.CHANTRA,"300210000_Chantra_Dredgion.xml",
   PlayerBotDredgion.TERATH,"300440000_Terath_Dredgion.xml");
  var parser=DocumentBuilderFactory.newInstance().newDocumentBuilder();
  for(var entry:ships.entrySet()) {
   var ship=entry.getKey();
   var spawns=parser.parse(root.resolve("data/static_data/spawns/Instances").resolve(entry.getValue()).toFile()).getElementsByTagName("spawn");
   Map<Integer,Float> firstX=new HashMap<>();
   for(int i=0;i<spawns.getLength();i++){
    var spawn=(Element)spawns.item(i);
    if(spawn.getElementsByTagName("spot").item(0) instanceof Element spot)
     firstX.putIfAbsent(Integer.parseInt(spawn.getAttribute("npc_id")),Float.parseFloat(spot.getAttribute("x")));
   }
   float mid=firstX.get(ship.gravity());
   check(firstX.get(ship.port().armory())<mid && mid<firstX.get(ship.starboard().armory()),ship.name()+": native opening rooms lie on the configured lanes");
   check(ship==PlayerBotDredgion.BARANATH?ship.port().shieldGenerator()==0 && ship.starboard().shieldGenerator()==0
    :firstX.get(ship.port().shieldGenerator())<mid && mid<firstX.get(ship.starboard().shieldGenerator()),
    ship.name()+": only verified opposing generator lanes are available to the opening policy");
  }
 }
 private static final class Actor extends Player {
  WorldPosition location;
  PlayerBotMoveController mover;
  Actor(){super(null,null);}
  @Override public WorldPosition getPosition(){return location;}
  @Override public boolean isInInstance(){return true;}
  @Override public PlayerBotMoveController getMoveController(){return mover;}
 }
 static void recovery() throws Exception {
  var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
  unsafeField.setAccessible(true);
  var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
  var stallField=PlayerBotNavigation.class.getDeclaredField("ledgeStall");
  stallField.setAccessible(true);
  var escape=PlayerBotNavigation.class.getDeclaredMethod("ledgeEscape",PlayerBotNavigation.Point.class,
   PlayerBotNavigation.Point.class,double.class,long.class);
  escape.setAccessible(true);
  for(var ship:PlayerBotDredgion.SHIPS) {
   var bot=(Actor)unsafe.allocateInstance(Actor.class);
   bot.location=new WorldPosition(ship.map(),400,166,432,(byte)0);
   bot.mover=new PlayerBotMoveController(bot);
   var nav=new PlayerBotNavigation(bot,true);
   var stall=(PlayerBotLedgeLeap.Stall)stallField.get(nav);
   long now=50000;
   stall.observe(now,ship.map(),1,400,166,432,true);
   stall.rewind(now,PlayerBotLedgeLeap.RELOCATE_MS+1);
   var from=new PlayerBotNavigation.Point(400,166,432);
   var goal=new PlayerBotNavigation.Point(414,190,430);
   check(!(boolean)escape.invoke(nav,from,goal,28d,now),ship.name()+": native navigator refuses the due stuck teleport");
   check(stall.observe(now+1,ship.map(),1,400,166,432,true)==0,ship.name()+": unsafe recovery timer was cleared");
  }
 }
 static void rules() {
  var c=PlayerBotDredgion.CHANTRA;
  check(PlayerBotDredgion.ship(300110000)==PlayerBotDredgion.BARANATH && PlayerBotDredgion.ship(300210000)==c && PlayerBotDredgion.ship(300440000)==PlayerBotDredgion.TERATH
   && PlayerBotDredgion.ship(300100000)==null,"Only the three Dredgion maps");
  check(PlayerBotDredgion.value(c,700843,false)==1100 && PlayerBotDredgion.value(c,700838,false)==400,"A Surkana is worth its native points");
  check(PlayerBotDredgion.value(c,216885,false)==500,"Chantra's Lieutenant scores 500 native points");
  check(PlayerBotDredgion.value(c,730350,false)==PlayerBotDredgion.OPENER_VALUE && PlayerBotDredgion.value(c,216882,false)==PlayerBotDredgion.OPENER_VALUE,"Hatches and the deck officer open the way");
  check(PlayerBotDredgion.value(c,216886,false)==0 && PlayerBotDredgion.value(c,216886,true)==PlayerBotDredgion.CAPTAIN_VALUE,
   "Chantra's captain is an objective when our side leads after the kill");
  check(PlayerBotDredgion.value(PlayerBotDredgion.TERATH,233371,false)==0 && PlayerBotDredgion.value(PlayerBotDredgion.TERATH,233371,true)>0,"Terath's captain likewise");
  check(PlayerBotDredgion.value(PlayerBotDredgion.BARANATH,214823,false)==PlayerBotDredgion.CAPTAIN_VALUE,"Baranath's captain wins for the killer's side: always worth it");
  for(var ship:List.of(c,PlayerBotDredgion.TERATH)) {
   check(PlayerBotDredgion.captainWins(ship,2000,2999),ship.name()+": the captain bonus turns a 999-point deficit into a win");
   check(!PlayerBotDredgion.captainWins(ship,2000,3000),ship.name()+": a projected tie is not a win");
   check(!PlayerBotDredgion.captainWins(ship,2000,3001),ship.name()+": the captain bonus cannot overcome a larger deficit");
  }
  check(PlayerBotDredgion.captainWins(PlayerBotDredgion.BARANATH,0,10000),"Baranath's captain ignores the score deficit");
  check(PlayerBotDredgion.value(c,216887,true)==0 && PlayerBotDredgion.value(null,700843,true)==0,"Anything else is not an objective");
  check(PlayerBotDredgion.score(1100,30)>PlayerBotDredgion.score(400,30),"A richer Surkana scores higher at the same distance");
  check(PlayerBotDredgion.score(400,10)>PlayerBotDredgion.score(1100,200),"A near cheap room beats a far rich one");
  check(PlayerBotDredgion.score(0,1)==0,"Not an objective, never picked");
  check(PlayerBotDredgion.portalImproves(180,20,50,false),"A portal can shorten travel to the assignment");
  check(!PlayerBotDredgion.portalImproves(100,60,60,false),"An unnecessary detour does not displace a direct route");
  check(PlayerBotDredgion.portalImproves(100,60,60,true),"A blocked direct route can justify a detour that lands closer");
  check(!PlayerBotDredgion.portalImproves(100,20,120,true),"A portal away from the assignment is never a fallback");
  check(PlayerBotDredgion.surkana(c,700843) && !PlayerBotDredgion.surkana(c,700836) && !PlayerBotDredgion.surkana(c,216941) && !PlayerBotDredgion.surkana(c,730350),
   "Surkana counting skips the 100-point items, officers and hatches");
  check(PlayerBotDredgion.OPENER_VALUE<400,"Hatches come after the near rooms");
 }
 /** case ids grouped before onDieSurkana(npc, mostPlayerDamage, N) in the real handler. */
 static Map<Integer,Integer> surkanas(String handler) {
  var out=new HashMap<Integer,Integer>();var pending=new java.util.ArrayList<Integer>();
  var caseLine=Pattern.compile("case (\\d+):");var points=Pattern.compile("onDieSurkana\\(npc, mostPlayerDamage, (\\d+)\\)");
  for(String line:handler.split("\\R")) {
   var m=caseLine.matcher(line);if(m.find()){pending.add(Integer.parseInt(m.group(1)));continue;}
   var p=points.matcher(line);if(p.find()){for(int id:pending)out.put(id,Integer.parseInt(p.group(1)));pending.clear();continue;}
   if(line.contains("return;") || line.contains("break;"))pending.clear();
  }
  return out;
 }
 static void tables(Path root) throws Exception {
  var dir=root.resolve("data/handlers/instance/dredgion");
  for(var pair:List.of(Map.entry(PlayerBotDredgion.BARANATH,"BaranathDredgionInstance.java"),Map.entry(PlayerBotDredgion.CHANTRA,"ChantraDredgionInstance.java"),
   Map.entry(PlayerBotDredgion.TERATH,"TerathDredgionInstance.java"))) {
   var ship=pair.getKey();var source=Files.readString(dir.resolve(pair.getValue()));
   check(source.contains("@InstanceID("+ship.map()+")"),ship.name()+": the handler is for this map");
   var native_=surkanas(source);
   var ours=new HashMap<Integer,Integer>();ship.points().forEach((id,pts)->{if(PlayerBotDredgion.surkana(ship,id))ours.put(id,pts);});
   check(native_.equals(ours),ship.name()+": every Surkana and its points match the handler "+native_+" vs "+ours);
   for(int id:ship.openers())check(source.contains("case "+id+":"),ship.name()+": opener "+id+" is handled natively");
   for(var e:ship.points().entrySet())if(!PlayerBotDredgion.surkana(ship,e.getKey()))
    check(source.contains("case "+e.getKey()+":") && source.contains("updateScore(mostPlayerDamage, npc, "+e.getValue()+", false)"),ship.name()+": officer "+e.getKey()+" scores "+e.getValue());
   int at=source.indexOf("case "+ship.captain()+":");
   check(at>0,ship.name()+": the captain is handled");
   String after=source.substring(at,Math.min(source.length(),at+400));
   check(ship.killerWins()?after.contains("stopInstance(race)"):after.contains("stopInstance(instanceScore.getRaceWithHighestPoints())"),
    ship.name()+": who wins on the captain's death matches the handler");
   if(!ship.killerWins()) {
    int scoreAt=after.indexOf("updateScore(mostPlayerDamage, npc, 1000, false)");
    check(scoreAt>=0 && scoreAt<after.indexOf("stopInstance(instanceScore.getRaceWithHighestPoints())"),
     ship.name()+": the 1000 points apply before native winner selection");
   }
  }
 }
 static void portals(Path root) throws Exception {
  var dir=root.resolve("data/static_data/portals");
  var parser=DocumentBuilderFactory.newInstance().newDocumentBuilder();
  var locations=parser.parse(dir.resolve("portal_loc.xml").toFile()).getElementsByTagName("portal_loc");
  var routes=parser.parse(dir.resolve("portal_template2.xml").toFile()).getElementsByTagName("portal_use");
  var destinationMaps=new HashMap<String,Integer>();
  for(int i=0;i<locations.getLength();i++) {
   var location=(Element)locations.item(i);
   destinationMaps.put(location.getAttribute("loc_id"),Integer.parseInt(location.getAttribute("world_id")));
  }
  for(var ship:PlayerBotDredgion.SHIPS) {
   int count=0;
   var mismatches=new java.util.ArrayList<Integer>();
   for(int i=0;i<routes.getLength();i++) {
    var route=(Element)routes.item(i);
    int npc=Integer.parseInt(route.getAttribute("npc_id"));
    boolean sameShip=java.util.Objects.equals(ship.map(),
     destinationMaps.get(((Element)route.getElementsByTagName("portal_path").item(0)).getAttribute("loc_id")));
    if(PlayerBotDredgion.portal(ship,npc)!=sameShip)mismatches.add(npc);
    if(sameShip)count++;
   }
   check(mismatches.isEmpty() && count==(ship==PlayerBotDredgion.BARANATH?0:6),ship.name()+": all native portal routes are covered, mismatches "+mismatches);
  }
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var manager=Files.readString(src.resolve("PlayerBotPopulationManager.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(session.contains("engaged |= autonomous() && PlayerBotDredgion.mechanicEnemy(bot,npc,party);"),"Its own target line, world bots only");
  check(session.contains("PlayerBotDredgion.prepare(bot, now)") && session.contains("PlayerBotDredgion.objectiveFocus(bot,enemies)"),
   "The team assignment feeds normal enemy admission and focus");
  check(session.contains("!PlayerBotDredgion.captainAttackAllowed(bot,npc)"),"Native aggro does not bypass the autonomous captain score gate");
  int travel=session.indexOf("PlayerBotDredgion.travel(this,navigation,now)"),seek=session.indexOf("pvp.seek.tick(this,navigation,now)");
  check(travel>0 && seek>travel,"Its own travel step, before the player hunt");
  check(service.contains("lines.add(\"Dredgion: \"+PlayerBotDredgion.stats());") && manager.contains("PlayerBotDredgion.forget(id);"),"Reported, and forgotten when a bot leaves");
  check(config.contains("gameserver.playerbots.pvp.battlegrounds.dredgion = "),"Its own switch");
 }
 private PlayerBotDredgionCheck(){}
}
