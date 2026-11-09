package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** PvP-3/4 generic layer: inside battlegrounds and arenas every bot fights native enemies instance-wide. Wiring only. */
public final class PlayerBotBattlegroundCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var seek=Files.readString(src.resolve("PlayerBotPvpSeek.java"));
  var pvp=Files.readString(src.resolve("PlayerBotPvp.java"));
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var stats=Files.readString(src.resolve("PlayerBotPvpStats.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(seek.contains("bot.getPosition()!=null && bot.isInInstance() && com.aionemu.gameserver.model.autogroup.AutoGroupType.getAutoGroupByWorld(bot.getLevel(),bot.getWorldId())!=null"),
   "A battleground or arena is recognised from native Find Group instance types, safely for bots without a position");
  check(seek.contains("p!=bot && p.isSpawned() && !p.isDead() && bot.isEnemy(p)"),"Inside, the game's own enemy rule decides (factions, or everyone in a free-for-all arena)");
  check(seek.contains("if(arena){if(PlayerBotPvp.hp(bot)<40){release();return false;}return hunt(session,navigation,bot,now);}")
   && seek.indexOf("if(arena)") < seek.indexOf("if(!pvpLeaning && !visit)"),"Every bot fights in a battleground, whatever its leaning, and party membership does not stop it");
  check(seek.contains("(arena || !guarded(") && seek.contains("!arena && visited.containsKey(p.getObjectId())"),"Guards and the lone arena opponent are never avoided");
  check(pvp.contains("PlayerBotPvpSeek.battleground(bot)?100:PlayerBotConfig.PVP_AGGRESSION)"),"In a battleground the bot always starts the fight");
  check(session.contains("&& (!PlayerBotFindGroup.committed(bot) || bot.isInInstance())"),"Bots that joined through Find Group still fight inside the instance");
  check(stats.contains("in battlegrounds and arenas \"+BATTLEGROUND_SEEKS.get()") && config.contains("gameserver.playerbots.pvp.battlegrounds.enable = "),"Reported and documented");
  System.out.println("OK: "+checks+" battleground checks; no World/SQL/tasks");
 }
 private PlayerBotBattlegroundCheck(){}
}
