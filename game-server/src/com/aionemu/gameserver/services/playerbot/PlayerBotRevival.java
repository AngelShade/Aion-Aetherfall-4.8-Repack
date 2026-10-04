package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_INFO;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** A headless companion must wait for death/rebirth animations like a real client. */
public final class PlayerBotRevival {
 static final long DEATH_DELAY = 2500, REVIVE_DELAY = 2000;
 static final class State { volatile long death, recovery; }
 private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

 public static void beginRecovery(Player bot) {
  if (bot.isPlayerBot()) STATES.computeIfAbsent(bot.getObjectId(), id -> new State()).recovery = System.currentTimeMillis();
 }

 static boolean ready(Player bot) {
  long now = System.currentTimeMillis();
  State state = STATES.get(bot.getObjectId());
  if (bot.isDead()) {
   if (state == null) { state = new State(); STATES.put(bot.getObjectId(), state); }
   // A second death during recovery starts a fresh death animation window.
   if (state.death == 0 || state.recovery != 0) { state.death = now; state.recovery = 0; }
   if (bot.getMoveController().isInMove()) bot.getMoveController().abortMove();
   if (now - state.death < DEATH_DELAY) return false;
   if (bot.getResStatus()) PlayerReviveService.skillRevive(bot);
   else if (bot.canUseRebirthRevive()) PlayerReviveService.rebirthRevive(bot);
   if (!bot.isDead()) state.recovery = now;
   return false;
  }
  boolean corpse = bot.isInState(CreatureState.DEAD)
   || (bot.getState() & CreatureState.ANY_STANCE.getId()) == CreatureState.FLOATING_CORPSE.getId();
  if (state == null && !corpse) return true;
  if (state == null) { state = new State(); STATES.put(bot.getObjectId(), state); }
  if (state.recovery == 0) state.recovery = now;
  if (bot.getMoveController().isInMove()) bot.getMoveController().abortMove();
  if (now - state.recovery < REVIVE_DELAY) return false;
  refresh(bot);
  STATES.remove(bot.getObjectId());
  return true;
 }

 /** Refresh only an already living actor; never grant HP, revive rights or a build. */
 public static void refresh(Player bot) {
  if (!bot.isPlayerBot() || bot.isDead() || !bot.isSpawned()) return;
  if (bot.isInState(CreatureState.DEAD)) {
   bot.unsetState(CreatureState.DEAD); bot.setState(CreatureState.ACTIVE);
  }
  if ((bot.getState() & CreatureState.ANY_STANCE.getId()) == CreatureState.FLOATING_CORPSE.getId()) {
   bot.unsetState(CreatureState.FLOATING_CORPSE); bot.setState(CreatureState.ACTIVE);
  }
  bot.getKnownList().forEachPlayer(observer -> {
   if (observer.isOnline() && observer.getKnownList().sees(bot)) {
    PacketSendUtility.sendPacket(observer, new SM_PLAYER_INFO(bot, observer.isAggroIconTo(bot)));
    PacketSendUtility.sendPacket(observer, new SM_EMOTION(bot, EmotionType.RESURRECT));
   }
  });
 }
 static void close(Player bot) { STATES.remove(bot.getObjectId()); }
 private PlayerBotRevival() {}
}
