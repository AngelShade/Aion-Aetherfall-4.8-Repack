/* GrindTargetValue, LootNonCombatStrategy and HealthTriggers at mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md.
 * Aion's timed combat flag still governs native interactions. A new field pull
 * is a separate action behind recovery, loot and useful preparation. */
package com.aionemu.gameserver.services.playerbot;

/** Independent field recovery and pull readiness; does not mutate native combat. */
final class PlayerBotFieldCombat {
 static boolean recover(double health,double mana,boolean resting,boolean engaged) {
  return valid(health,mana) && health>0 && !engaged
   && (resting ? health<95 || mana<95 : health<85 || mana<60);
 }
 static boolean ready(double health,double mana,boolean resting) {
  return valid(health,mana) && health>=85 && mana>=60 && !recover(health,mana,resting,false);
 }
 private static boolean valid(double health,double mana) {
  return Double.isFinite(health) && Double.isFinite(mana) && health>=0 && mana>=0;
 }
 private PlayerBotFieldCombat(){}
}
