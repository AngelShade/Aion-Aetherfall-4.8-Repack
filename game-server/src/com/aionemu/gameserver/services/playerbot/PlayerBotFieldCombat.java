/* GrindTargetValue, LootNonCombatStrategy and HealthTriggers at mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md.
 * Aion's timed combat flag still governs native interactions. A new field pull
 * is a separate action behind recovery, loot and useful preparation. */
package com.aionemu.gameserver.services.playerbot;

/** Independent field recovery and pull readiness; does not mutate native combat. */
final class PlayerBotFieldCombat {
 static boolean recover(double health,double mana,boolean resting,boolean engaged) {
  return PlayerBotRecoveryPolicy.recover(health,mana,resting,engaged);
 }
 static boolean ready(double health,double mana,boolean resting) {
  return PlayerBotRecoveryPolicy.ready(health,mana,resting);
 }
 private PlayerBotFieldCombat(){}
}
