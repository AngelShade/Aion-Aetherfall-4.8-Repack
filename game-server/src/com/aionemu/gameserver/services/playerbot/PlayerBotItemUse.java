package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Native item tasks can remain registered after completion; only unfinished tasks are busy. */
final class PlayerBotItemUse {
 static boolean pause(Player bot, boolean interrupt) {
  if (!bot.getController().hasScheduledTask(TaskId.ITEM_USE)) return false;
  if (!interrupt) return true;
  // Abort the actual item observers. A synthetic MOVE also marks an unrelated
  // committed spell's StartMovingListener and makes move_casting fail at cast end.
  bot.getController().cancelUseItem();
  return false;
 }
 private PlayerBotItemUse() {}
}
