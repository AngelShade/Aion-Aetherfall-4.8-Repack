/* Native Aion login/logout projection for headless legion members. GuildAcceptAction
 * uses the host's normal guild path; pinned mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c, GPL-2.0-or-later.
 * See third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.sql.Timestamp;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.LegionService;

/** Actor lifecycle notifications, without fabricating a socket or account login. */
final class PlayerBotLegionPresence {
 static void entered(Player bot) {
  if(bot.isLegionMember())LegionService.getInstance().onPlayerBotEnter(bot);
 }
 static void left(Player bot) {
  bot.getCommonData().setLastOnline(new Timestamp(System.currentTimeMillis()));
  if(bot.isLegionMember())LegionService.getInstance().onLogout(bot);
 }
 private PlayerBotLegionPresence(){}
}
