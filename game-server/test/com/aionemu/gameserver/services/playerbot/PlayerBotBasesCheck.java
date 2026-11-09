package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.aionemu.gameserver.model.base.BaseOccupier;

/** PvP-5 bases: its own hook, switch, travel step and report. Pure rules plus wiring; no World/SQL/tasks. */
public final class PlayerBotBasesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  check(PlayerBotBases.objective(true,true,true,true,true,true,true),"A PVP bot fights an enemy base NPC within reach");
  check(!PlayerBotBases.objective(true,false,true,true,true,true,true),"Ordinary bots leave base guards alone");
  check(!PlayerBotBases.objective(false,true,true,true,true,true,true) && !PlayerBotBases.objective(true,true,false,true,true,true,true) && !PlayerBotBases.objective(true,true,true,false,true,true,true),
   "Switched off, not a base NPC, or the base is not active: nothing");
  check(!PlayerBotBases.objective(true,true,true,true,false,true,true) && !PlayerBotBases.objective(true,true,true,true,true,false,true) && !PlayerBotBases.objective(true,true,true,true,true,true,false),
   "Own base, dead or out of reach: nothing");
  check(PlayerBotBases.contested(BaseOccupier.ASMODIANS,BaseOccupier.ELYOS) && PlayerBotBases.contested(BaseOccupier.BALAUR,BaseOccupier.ELYOS) && !PlayerBotBases.contested(BaseOccupier.ELYOS,BaseOccupier.ELYOS),
   "Any base not held by the bot's own faction is worth taking");
  check(PlayerBotBases.nearest(List.of(new float[]{10,0,0},new float[]{400,0,0}),300,0,0,1500)[0]==400 && PlayerBotBases.nearest(List.of(new float[]{9000,0,0}),0,0,0,1500)==null,
   "The nearest base spot within walking distance");
  check(PlayerBotBases.RANGE<=45 && PlayerBotBases.ARRIVED>=PlayerBotBases.RANGE,"Reach matches normal combat and arrival hands over to the fight");
  var root=args.length>0?Path.of(args[0]):Path.of(".");var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));var service=Files.readString(src.resolve("PlayerBotService.java"));
  var manager=Files.readString(src.resolve("PlayerBotPopulationManager.java"));var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(session.contains("engaged |= autonomous() && PlayerBotBases.mechanicEnemy(bot,npc,party);"),"Its own statement in the combat code, for world bots only");
  check(session.contains("&& PlayerBotBases.travel(this,navigation,now)) {") && session.contains("status=\"heading to an enemy-held base\""),"Its own travel step");
  check(service.contains("lines.add(\"Bases: \"+PlayerBotBases.stats());") && manager.contains("PlayerBotBases.forget(id);")
   && config.contains("gameserver.playerbots.pvp.bases.enable = ") && config.contains("gameserver.playerbots.pvp.bases.trip_minutes = "),"Its own report line, cleanup and switches");
  System.out.println("OK: "+checks+" base checks; no World/SQL/tasks");
 }
 private PlayerBotBasesCheck(){}
}
