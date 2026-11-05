package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Bots help a human in a hard fight, gear hunts can cross a map, Wardens in player legions stop retrying, and long falls end on the floor. */
public final class PlayerBotBystandersCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  check(PlayerBotBystanders.calls(true,100,70),"an elite always calls for help");
  check(PlayerBotBystanders.calls(false,69,70),"a losing fight calls for help");
  check(!PlayerBotBystanders.calls(false,70,70),"a healthy player keeps an ordinary kill");
  check(PlayerBotBystanders.room(1,2) && !PlayerBotBystanders.room(2,2),"only a few bots join one player");
  check(PlayerBotWorldActivities.goalPlaceAllowed(true,210020000,210020000),"a bot kept near a player may cross its map for gear");
  check(!PlayerBotWorldActivities.goalPlaceAllowed(true,210020000,210040000),"a bot kept near a player does not leave the map");
  check(PlayerBotWorldActivities.goalPlaceAllowed(false,210020000,210040000),"other bots may travel for gear");
  check(PlayerBotLedgeLeap.MAX_DROP<50,"no bot dives further than a player could survive");
  var src=Path.of(args[0]).resolve("src/com/aionemu/gameserver");var bot=src.resolve("services/playerbot");
  check(Files.readString(src.resolve("controllers/attack/AggroInfo.java")).contains("PlayerBotBystanders.answering(player)"),"a helper's hits leave the kill with the player");
  var session=Files.readString(bot.resolve("PlayerBotSession.java"));
  check(session.contains("PlayerBotBystanders.observe(this)") && session.contains("PlayerBotBystanders.helping(bot,npc)"),"the session looks for fights and treats the answered monster as an enemy");
  check(Files.readString(bot.resolve("PlayerBotWorldActivities.java")).contains("var chosen=goalPlace(bot,npcs,s,now,distances);"),"gear hunts use their own place rule");
  check(Files.readString(bot.resolve("PlayerBotNavigation.java")).contains("tier==PlayerBotLedgeLeap.Tier.GHOST && settled("),"no bot sinks through a town floor");
  check(Files.readString(bot.resolve("PlayerBotWardenCompanies.java")).contains("instanceof PlayerBotLegionRepository.PlayerLegion"),"a Warden in a player's legion is not retried");
  var fall=Files.readString(src.resolve("controllers/movement/PlayerMoveController.java"));
  check(fall.contains("getWalkableZ(owner, newZ, BELOW_WORLD_Z)"),"a fall ends in midair only with no floor anywhere below");
  System.out.println("OK: "+checks+" bystander, gear hunt, Warden legion and long fall checks");
 }
 private PlayerBotBystandersCheck(){}
}
