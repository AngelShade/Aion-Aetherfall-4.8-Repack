package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** PvP-3 battleground objectives: its own hook, switch and report. Pure rules plus wiring; no World/SQL/tasks. */
public final class PlayerBotBattlegroundObjectivesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  check(PlayerBotBattlegroundObjectives.objective(true,true,true,true,true,true),"A hostile objective NPC within reach inside a battleground is a target");
  check(!PlayerBotBattlegroundObjectives.objective(true,false,true,true,true,true),"Outside battlegrounds this hook does nothing");
  check(!PlayerBotBattlegroundObjectives.objective(true,true,false,true,true,true),"Someone's summon is never treated as an objective");
  check(!PlayerBotBattlegroundObjectives.objective(false,true,true,true,true,true) && !PlayerBotBattlegroundObjectives.objective(true,true,true,false,true,true)
   && !PlayerBotBattlegroundObjectives.objective(true,true,true,true,false,true) && !PlayerBotBattlegroundObjectives.objective(true,true,true,true,true,false),
   "Switched off, friendly, dead or out of reach: nothing");
  var root=args.length>0?Path.of(args[0]):Path.of(".");var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var hook=Files.readString(src.resolve("PlayerBotBattlegroundObjectives.java"));var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(hook.contains("if(!enabled() || !PlayerBotPvpSeek.battleground(bot))return false;"),"Bots without a position leave before any instance check");
  check(hook.contains("PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_BATTLEGROUND_OBJECTIVES"),"Needs battlegrounds on, and has its own switch on top");
  check(session.contains("engaged |= autonomous() && PlayerBotBattlegroundObjectives.mechanicEnemy(bot,npc,party);"),"Its own statement in the combat code, for world bots only");
  check(service.contains("lines.add(\"Battleground objectives: \"+PlayerBotBattlegroundObjectives.stats());") && config.contains("gameserver.playerbots.pvp.battlegrounds.objectives = "),"Its own report line and switch");
  System.out.println("OK: "+checks+" battleground objective checks; no World/SQL/tasks");
 }
 private PlayerBotBattlegroundObjectivesCheck(){}
}
