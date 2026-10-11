package com.aionemu.gameserver.services.playerbot;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;

/** Transient recovery intent survives standing to cast; it never replaces native rest or resource state. */
final class PlayerBotRecoveryIntent {
 private static final Set<Integer> RECOVERING=ConcurrentHashMap.newKeySet();
 static boolean active(Player bot){return RECOVERING.contains(bot.getObjectId()) || bot.isInState(CreatureState.RESTING);}
 static void start(Player bot){RECOVERING.add(bot.getObjectId());}
 static void clear(Player bot){RECOVERING.remove(bot.getObjectId());}
 static void update(Player bot,boolean engaged,boolean leaderMoving) {
  if(bot.isDead() || engaged || leaderMoving || PlayerBotHealing.health(bot)>=95 && PlayerBotDefense.mana(bot)>=95)clear(bot);
  else if(bot.isInState(CreatureState.RESTING))start(bot);
 }
 private PlayerBotRecoveryIntent(){}
}
