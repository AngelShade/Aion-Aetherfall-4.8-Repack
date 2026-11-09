package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** PvP-3a Dredgion objective play: the pure rules, the tables against the real map handlers, and the wiring. No World/SQL. */
public final class PlayerBotDredgionCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  rules();
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  tables(root);
  wiring(root);
  System.out.println("OK: "+checks+" Dredgion checks; no World/SQL/tasks");
 }
 static void rules() {
  var c=PlayerBotDredgion.CHANTRA;
  check(PlayerBotDredgion.ship(300110000)==PlayerBotDredgion.BARANATH && PlayerBotDredgion.ship(300210000)==c && PlayerBotDredgion.ship(300440000)==PlayerBotDredgion.TERATH
   && PlayerBotDredgion.ship(300100000)==null,"Only the three Dredgion maps");
  check(PlayerBotDredgion.value(c,700843,false)==1100 && PlayerBotDredgion.value(c,700838,false)==400,"A Surkana is worth its native points");
  check(PlayerBotDredgion.value(c,730350,false)==PlayerBotDredgion.OPENER_VALUE && PlayerBotDredgion.value(c,216882,false)==PlayerBotDredgion.OPENER_VALUE,"Hatches and the deck officer open the way");
  check(PlayerBotDredgion.value(c,216886,false)==0 && PlayerBotDredgion.value(c,216886,true)==PlayerBotDredgion.CAPTAIN_VALUE,
   "Chantra's captain gives the win to the side with more points: only when ours leads");
  check(PlayerBotDredgion.value(PlayerBotDredgion.TERATH,233371,false)==0 && PlayerBotDredgion.value(PlayerBotDredgion.TERATH,233371,true)>0,"Terath's captain likewise");
  check(PlayerBotDredgion.value(PlayerBotDredgion.BARANATH,214823,false)==PlayerBotDredgion.CAPTAIN_VALUE,"Baranath's captain wins for the killer's side: always worth it");
  check(PlayerBotDredgion.value(c,216887,true)==0 && PlayerBotDredgion.value(null,700843,true)==0,"Anything else is not an objective");
  check(PlayerBotDredgion.score(1100,30,false)>PlayerBotDredgion.score(400,30,false),"A richer Surkana scores higher at the same distance");
  check(PlayerBotDredgion.score(400,10,false)>PlayerBotDredgion.score(1100,200,false),"A near cheap room beats a far rich one");
  check(PlayerBotDredgion.score(700,60,true)==2*PlayerBotDredgion.score(700,60,false),"A Surkana a player is attacking counts double: bots back the player up");
  check(PlayerBotDredgion.score(0,1,true)==0,"Not an objective, never picked");
  check(PlayerBotDredgion.runner(false) && !PlayerBotDredgion.runner(true),"Non-PvP bots play the objectives; PvP-leaning bots keep hunting players");
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
  }
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var manager=Files.readString(src.resolve("PlayerBotPopulationManager.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(session.contains("engaged |= autonomous() && PlayerBotDredgion.mechanicEnemy(bot,npc,party);"),"Its own target line, world bots only");
  int travel=session.indexOf("PlayerBotDredgion.travel(this,navigation,now)"),seek=session.indexOf("pvp.seek.tick(this,navigation,now)");
  check(travel>0 && seek>travel,"Its own travel step, before the player hunt");
  check(service.contains("lines.add(\"Dredgion: \"+PlayerBotDredgion.stats());") && manager.contains("PlayerBotDredgion.forget(id);"),"Reported, and forgotten when a bot leaves");
  check(config.contains("gameserver.playerbots.pvp.battlegrounds.dredgion = "),"Its own switch");
 }
 private PlayerBotDredgionCheck(){}
}
