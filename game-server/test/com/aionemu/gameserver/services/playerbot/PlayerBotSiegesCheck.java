package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** PvP-5 sieges: its own hook, switch, travel step and report. Pure rules plus wiring; no World/SQL/tasks. */
public final class PlayerBotSiegesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  check(PlayerBotSieges.objective(true,true,true,true,true,true),"An enemy siege NPC of a running siege within reach is a target");
  check(!PlayerBotSieges.objective(true,true,false,true,true,true),"No siege running: the fortress is left alone");
  check(!PlayerBotSieges.objective(false,true,true,true,true,true) && !PlayerBotSieges.objective(true,false,true,true,true,true),"Switched off, or not a siege NPC: nothing");
  check(!PlayerBotSieges.objective(true,true,true,false,true,true) && !PlayerBotSieges.objective(true,true,true,true,false,true) && !PlayerBotSieges.objective(true,true,true,true,true,false),
   "Own side, dead or out of reach: nothing");
  var spots=List.of(new float[]{0,0,0},new float[]{100,0,0},new float[]{5000,0,0});
  check(PlayerBotSieges.nearest(spots,90,0,0,3000)[0]==100,"The nearest part of the fortress is the walk target");
  check(PlayerBotSieges.nearest(List.of(new float[]{5000,0,0}),0,0,0,3000)==null,"A siege far across the map is not walked to");
  check(PlayerBotSieges.traveller(true,false,true) && PlayerBotSieges.traveller(false,true,true) && !PlayerBotSieges.traveller(false,false,true) && !PlayerBotSieges.traveller(false,true,false),
   "A running Agent fight draws every bot (agent_all_bots); other sieges only PvP-leaning bots");
  check(PlayerBotSieges.kind(true,null).equals("Balaur assault") && PlayerBotSieges.kind(false,null).equals("fortress"),"Siege targets are reported by kind");
  check(PlayerBotSieges.RANGE<=45 && PlayerBotSieges.ARRIVED>=PlayerBotSieges.RANGE && PlayerBotSieges.GIVE_UP_MS>=60000,"Reach matches normal combat; arrival hands over to the fight; trips give up");
  var root=args.length>0?Path.of(args[0]):Path.of(".");var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));var service=Files.readString(src.resolve("PlayerBotService.java"));
  var manager=Files.readString(src.resolve("PlayerBotPopulationManager.java"));var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(session.contains("engaged |= autonomous() && PlayerBotSieges.mechanicEnemy(bot,npc,party);"),"Its own statement in the combat code, for world bots only");
  check(session.contains("&& PlayerBotSieges.travel(this,navigation,now)) {") && session.contains("status=\"joining a running siege\""),"Its own travel step");
  check(service.contains("lines.add(\"Sieges: \"+PlayerBotSieges.stats());") && manager.contains("PlayerBotSieges.forget(id);") && config.contains("gameserver.playerbots.pvp.sieges.enable = "),
   "Its own report line, cleanup and switch");
  System.out.println("OK: "+checks+" siege checks; no World/SQL/tasks");
 }
 private PlayerBotSiegesCheck(){}
}
