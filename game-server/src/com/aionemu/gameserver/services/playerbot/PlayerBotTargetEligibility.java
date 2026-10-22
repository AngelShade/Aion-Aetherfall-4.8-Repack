/* AttackersValue::IsPossibleTarget / PossibleTargetsValue::AcceptUnit,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; AUTHORS.md. Aion rendering/detection and known-list adapters. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Shared fresh visibility admission; script controllers are not hostile combat actors. */
final class PlayerBotTargetEligibility {
 static boolean visible(Player observer,Creature target) {
  return observer!=null && target!=null && target.getObjectTemplate()!=null
   && (!(target instanceof Npc npc) || npc.getObjectTemplate().isClientVisible()) && target.isSpawned()
   && observer.canSee(target) && observer.getKnownList().sees(target);
 }
 private PlayerBotTargetEligibility(){}
}
