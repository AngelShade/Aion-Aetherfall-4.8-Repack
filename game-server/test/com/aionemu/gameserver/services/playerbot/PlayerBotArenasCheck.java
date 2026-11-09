package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** PvP-4 arenas: the point table against PvPArenaInstance.getPoints, use objects against their native AI, the
 * seven arena maps against their handlers, and the hook's own wiring. No World/SQL. */
public final class PlayerBotArenasCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  rules();
  tables(root);
  wiring(root);
  System.out.println("OK: "+checks+" arena checks; no World/SQL/tasks");
 }
 static void rules() {
  var f=PlayerBotArenas.field(300350000);
  check(f!=null && PlayerBotArenas.field(300100000)==null,"Arena maps only");
  check(PlayerBotBattlegroundPlay.value(f,701187,0,0,false)==1750 && PlayerBotBattlegroundPlay.use(f,701187),"Relics are worth their points and are used, not fought");
  check(PlayerBotBattlegroundPlay.value(f,218690,0,0,false)==1500 && !PlayerBotBattlegroundPlay.use(f,218690),"Arena monsters are fought for their points");
  check(f.boss()==0,"Arenas have no boss; they end on the third round");
  for(int map:PlayerBotArenas.MAPS)check(PlayerBotArenas.field(map)!=null && PlayerBotArenas.field(map).map()==map,"Arena "+map+" has its table");
 }
 static Map<Integer,Integer> points(String source) {
  int at=source.indexOf("private int getPoints(int npcId)");String body=source.substring(at,source.indexOf("default:",at));
  var out=new HashMap<Integer,Integer>();var pending=new java.util.ArrayList<Integer>();
  var c=Pattern.compile("case (\\d+):");var r=Pattern.compile("return (\\d+);");
  for(String line:body.split("\\R")){var m=c.matcher(line);if(m.find())pending.add(Integer.parseInt(m.group(1)));var v=r.matcher(line);if(v.find()){for(int id:pending)out.put(id,Integer.parseInt(v.group(1)));pending.clear();}}
  return out;
 }
 static void tables(Path root) throws Exception {
  var dir=root.resolve("data/handlers/instance/pvparenas");
  var all=points(Files.readString(dir.resolve("PvPArenaInstance.java")));
  var ours=new HashMap<Integer,Integer>();ours.putAll(PlayerBotArenas.KILLS);ours.putAll(PlayerBotArenas.USES);
  check(all.equals(ours),"Every arena point target and value matches PvPArenaInstance.getPoints "+all.size()+" vs "+ours.size());
  check(PlayerBotArenas.KILLS.keySet().stream().noneMatch(PlayerBotArenas.USES::containsKey),"No target is both fought and used");
  var npcs=Files.readString(root.resolve("data/static_data/npcs/npc_templates.xml"));
  for(int id:PlayerBotArenas.USES.keySet()) {
   var m=Pattern.compile("<npc_template npc_id=\""+id+"\"[^>]*ai=\"([^\"]+)\"").matcher(npcs);
   check(m.find() && (m.group(1).equals("pvparenarelics") || m.group(1).equals("plaza_flame_thrower") || m.group(1).equals("harmony_shifter")),"Use object "+id+" has a native use AI");
  }
  String[] handlers={"ArenaOfChaosInstance","ArenaOfDisciplineInstance","ArenaOfGloryInstance","ArenaOfHarmonyInstance","ChaosTrainingGroundsInstance","DisciplineTrainingGroundsInstance","HarmonyTrainingGroundsInstance"};
  var maps=new java.util.HashSet<Integer>();for(int map:PlayerBotArenas.MAPS)maps.add(map);
  for(var h:handlers) {
   var s=Files.readString(dir.resolve(h+".java"));var m=Pattern.compile("@InstanceID\\((\\d+)\\)").matcher(s);
   check(m.find() && maps.contains(Integer.parseInt(m.group(1))),h+" is one of the arena maps");
   check(!s.contains("int getPoints("),h+" keeps the shared point table");
  }
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  check(session.contains("engaged |= autonomous() && PlayerBotArenas.mechanicEnemy(bot,npc,party);"),"Its own target line");
  int travel=session.indexOf("PlayerBotArenas.travel(this,navigation,now)"),seek=session.indexOf("pvp.seek.tick(this,navigation,now)");
  check(travel>0 && travel<seek,"Its own travel step, before the opponent hunt");
  check(Files.readString(src.resolve("PlayerBotService.java")).contains("lines.add(\"Arenas: \"+PlayerBotArenas.stats());"),"Its own report line");
  check(Files.readString(root.resolve("config/playerbots/pvp.properties")).contains("gameserver.playerbots.pvp.battlegrounds.arenas = "),"Its own switch");
 }
 private PlayerBotArenasCheck(){}
}
