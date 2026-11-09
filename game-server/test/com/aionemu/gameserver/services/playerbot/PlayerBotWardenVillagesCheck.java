package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Owner 8 Nov ("Yes, through villages"): Warden beats may cross villages with their ends outside and at most half
 * inside; there a warden only walks its own road out of combat. Fights, escorts and stand-aside spots stay outside.
 * Also the catalogue: walker-less 4.x zones and the longer Poeta and Altgard roads. No World/SQL. */
public final class PlayerBotWardenVillagesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  rules();
  wiring(root);
  catalogue(root);
  System.out.println("OK: "+checks+" Warden village checks; no World/SQL/tasks");
 }
 static void rules() {
  check(PlayerBotWardenBeats.villageAllowed(100,0,false),"A beat outside every settlement stays valid");
  check(PlayerBotWardenBeats.villageAllowed(100,50,false) && !PlayerBotWardenBeats.villageAllowed(100,51,false),"At most half a beat lies inside villages");
  check(!PlayerBotWardenBeats.villageAllowed(100,1,true),"A beat never turns or pauses inside a village");
  check(PlayerBotWardenBeats.END_POINTS==3,"The first and last three points stay outside");
  check(PlayerBotWardenBeats.villageStep(true,2.9) && !PlayerBotWardenBeats.villageStep(true,3.1),"Inside a village a warden keeps to its road");
  check(!PlayerBotWardenBeats.villageStep(false,0),"No village steps in combat");
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var duty=Files.readString(src.resolve("PlayerBotRoadDuty.java"));
  check(duty.contains("boolean through=s.route!=null && s.route.id().startsWith(\"beat:\") && !bot.getController().isInCombat();"),"Only a patrolling warden on a beat may cross a village");
  check(duty.contains("if(route.id().startsWith(\"beat:\") && !PlayerBotWardenBeats.field(route.map(),beside.x(),beside.y(),centre))continue;"),"Stand-aside lanes stay outside villages");
  check(duty.contains("!PlayerBotWardenBeats.field(enemy.getWorldId(),enemy.getX(),enemy.getY(),enemy.getZ())"),"Wardens still do not fight inside villages");
  check(Files.readString(src.resolve("PlayerBotWardens.java")).contains("!PlayerBotWardenBeats.field(h.getWorldId(),h.getX(),h.getY(),h.getZ())"),"Escorts still end at the village edge");
  check(Files.readString(src.resolve("PlayerBotWardenBeats.java")).contains("if(!villageAllowed(points.size(),inside,ends))throw new IllegalArgumentException(\"settlement point\");"),"The loader applies the village rule");
 }
 static void catalogue(Path root) throws Exception {
  var beats=PlayerBotWardenBeats.load(root.resolve("config/playerbots/warden-beats.tsv"),m->null);
  check(beats.size()==48,"Catalogue holds 48 beats: "+beats.size());
  check(beats.values().stream().map(b->b.route().map()).distinct().count()==14,"On fourteen maps");
  var poeta=beats.values().stream().filter(b->b.route().map()==210010000).toList();
  check(poeta.size()==1 && poeta.getFirst().length()>1300,"Poeta's road grows from 412 m past 1,300 m");
  var altgard=beats.values().stream().filter(b->b.route().map()==220030000).toList();
  check(altgard.size()==12 && altgard.stream().allMatch(b->b.length()>=2500),"Altgard gets twelve roads of 2.5 km or more");
  check(beats.values().stream().filter(b->b.route().map()==210070000).count()==3 && beats.values().stream().filter(b->b.route().map()==220080000).count()==1,"Cygnea and Enshar keep their beats");
  check(beats.values().stream().noneMatch(b->b.route().map()==110010000 || b.route().map()==120010000),"No beats in the capitals");
 }
 private PlayerBotWardenVillagesCheck(){}
}
