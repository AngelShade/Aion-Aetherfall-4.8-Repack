package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** PvP-3 per-map hooks (Kamar, Ophidan Bridge, Iron Wall, Idgel Dome): the shared rules, every table against its real
 * map handler, and one switch/target line/travel step/report per map. No World/SQL. */
public final class PlayerBotBattlegroundMapsCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  rules();
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  tables(root);
  wiring(root);
  System.out.println("OK: "+checks+" battleground map checks; no World/SQL/tasks");
 }
 static void rules() {
  var k=PlayerBotKamar.FIELD;
  check(PlayerBotBattlegroundPlay.value(k,232856,0,0,false)==1250 && PlayerBotBattlegroundPlay.value(k,801903,0,0,false)==1500,"Kill and use targets are worth their native points");
  check(PlayerBotBattlegroundPlay.value(k,232853,0,3000,false)==3500 && PlayerBotBattlegroundPlay.value(k,232853,0,4000,false)==0,
   "The boss only when its points put our side ahead (the run ends on it and the higher score wins)");
  check(PlayerBotBattlegroundPlay.value(k,999999,0,0,false)==0 && PlayerBotBattlegroundPlay.value(null,232856,0,0,false)==0,"Anything else is not an objective");
  var o=PlayerBotOphidanBridge.FIELD;
  check(PlayerBotBattlegroundPlay.value(o,701943,0,0,true)==0 && PlayerBotBattlegroundPlay.value(o,701944,0,0,false)==5000,"A side never captures its own generator, only the enemy's or the Balaur one");
  check(PlayerBotBattlegroundPlay.use(o,701945) && !PlayerBotBattlegroundPlay.use(PlayerBotIronWall.FIELD,701945),"Ophidan captures generators by use; Iron Wall fights them");
  check(PlayerBotBattlegroundPlay.value(PlayerBotIronWall.FIELD,233544,10000,35000,false)==30000 && PlayerBotBattlegroundPlay.value(PlayerBotIronWall.FIELD,233544,0,40000,false)==0,"Pashid likewise");
  check(PlayerBotBattlegroundPlay.value(PlayerBotIdgelDome.FIELD,287249,100,0,false)==6000 && PlayerBotIdgelDome.FIELD.uses().isEmpty(),"Kunax likewise; Idgel's chests and fires score nothing");
  check(PlayerBotBattlegroundPlay.score(1500,30,false)>PlayerBotBattlegroundPlay.score(255,30,false) && PlayerBotBattlegroundPlay.score(255,5,false)>PlayerBotBattlegroundPlay.score(1500,400,false),
   "Worth for the walk: richer at the same distance, near before far");
  check(PlayerBotBattlegroundPlay.score(500,40,true)==2*PlayerBotBattlegroundPlay.score(500,40,false),"A target a player is on counts double");
  check(PlayerBotBattlegroundPlay.runner(false) && !PlayerBotBattlegroundPlay.runner(true),"PvP-leaning bots keep hunting players");
 }
 /** case ids -> points from the handler's onDie / handleUseItemFinish switches (points = N; or updatePoints(..., N)). */
 static Map<Integer,Integer> points(String source,String method) {
  int at=source.indexOf(method);if(at<0)return Map.of();
  int end=source.indexOf("\n\t}",at);String body=source.substring(at,end<0?source.length():end);
  var out=new HashMap<Integer,Integer>();var pending=new java.util.ArrayList<Integer>();
  var cases=Pattern.compile("case ([0-9][0-9, ]*)(:| ->)");var value=Pattern.compile("points = (\\d+)|updatePoints\\(player, player\\.getRace\\(\\), npc\\.getObjectTemplate\\(\\)\\.getL10n\\(\\), (\\d+)\\)");
  for(String line:body.split("\\R")) {
   var c=cases.matcher(line);
   if(c.find())for(String id:c.group(1).split(","))pending.add(Integer.parseInt(id.trim()));
   var v=value.matcher(line);
   if(v.find() && !pending.isEmpty()){int n=Integer.parseInt(v.group(1)!=null?v.group(1):v.group(2));for(int id:pending)out.putIfAbsent(id,n);pending.clear();continue;}
   if(line.contains("break;") || line.contains("return;"))pending.clear();
  }
  return out;
 }
 static void tables(Path root) throws Exception {
  var dir=root.resolve("data/handlers/instance/pvp");
  for(var pair:List.of(Map.entry(PlayerBotKamar.FIELD,"KamarBattlefieldInstance.java"),Map.entry(PlayerBotOphidanBridge.FIELD,"EngulfedOphidanBridgeInstance.java"),
   Map.entry(PlayerBotIronWall.FIELD,"IronWallWarfrontInstance.java"),Map.entry(PlayerBotIdgelDome.FIELD,"IdgelDomeInstance.java"))) {
   var f=pair.getKey();var source=Files.readString(dir.resolve(pair.getValue()));
   check(source.contains("@InstanceID("+f.map()+")"),f.name()+": the handler is for this map");
   var kills=points(source,"public void onDie(Npc npc)");
   for(var e:f.kills().entrySet())check(e.getValue().equals(kills.get(e.getKey())),f.name()+": kill "+e.getKey()+" scores "+e.getValue()+" natively (handler "+kills.get(e.getKey())+")");
   var uses=points(source,"public void handleUseItemFinish(Player player, Npc npc)");
   for(var e:f.uses().entrySet())check(f.captureFlags() || e.getValue().equals(uses.get(e.getKey())),f.name()+": use "+e.getKey()+" scores "+e.getValue()+" natively (handler "+uses.get(e.getKey())+")");
   if(f.boss()!=0) {
    int die=source.indexOf("public void onDie(Npc npc)");int at=source.indexOf("case "+f.boss(),die);if(at<0)at=source.indexOf("== "+f.boss(),die);
    check(at>0 && source.substring(at,Math.min(source.length(),at+700)).contains("onStop(true)"),f.name()+": the boss ends the run natively");
    check(source.contains(String.valueOf(f.bossPoints())),f.name()+": the boss is worth "+f.bossPoints());
   }
  }
  var ophidan=Files.readString(dir.resolve("EngulfedOphidanBridgeInstance.java"));
  check(ophidan.contains("if (npc.getRace() == player.getRace()) {") && ophidan.contains("updatePoints(player, player.getRace(), npc.getObjectTemplate().getL10n(), 5000);"),
   "Ophidan: capturing a generator scores 5,000 and a side cannot capture its own");
  check(Files.readString(root.resolve("data/handlers/instance/pvp/BasicPvpInstance.java")).contains("Race winningRace = instanceScore.getRaceWithHighestPoints();"),
   "These maps give the win to the side with more points when they stop");
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var play=Files.readString(src.resolve("PlayerBotBattlegroundPlay.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  int seek=session.indexOf("pvp.seek.tick(this,navigation,now)");
  for(var m:List.of(new String[]{"PlayerBotKamar","kamar","Kamar"},new String[]{"PlayerBotOphidanBridge","ophidan","Ophidan Bridge"},
   new String[]{"PlayerBotIronWall","iron_wall","Iron Wall"},new String[]{"PlayerBotIdgelDome","idgel","Idgel Dome"})) {
   check(session.contains("engaged |= autonomous() && "+m[0]+".mechanicEnemy(bot,npc,party);"),m[0]+": its own target line");
   int travel=session.indexOf(m[0]+".travel(this,navigation,now)");
   check(travel>0 && travel<seek,m[0]+": its own travel step, before the player hunt");
   check(service.contains("lines.add(\""+m[2]+": \"+"+m[0]+".stats());"),m[0]+": its own report line");
   check(config.contains("gameserver.playerbots.pvp.battlegrounds."+m[1]+" = "),m[0]+": its own switch");
  }
  check(play.contains("npc.getController().onDialogRequest(bot);") && play.contains("if(usingNow(bot)){navigation.stop();return true;}"),
   "Capture objects are used through the native dialog and cast bar, standing still until it completes");
  check(play.contains("if(asked!=null && now-asked<USE_START_MS+REPICK_MS)"),"A use that does not start is set aside, not asked again every tick");
  check(play.contains("|| use(f,npc.getNpcId()))return false;"),"Use objects are never attacked");
 }
 private PlayerBotBattlegroundMapsCheck(){}
}
