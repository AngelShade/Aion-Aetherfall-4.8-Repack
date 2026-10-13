package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Pure action-purpose gates; native actors, quest custody and travel stay with the caller.
 * Pinned WoW combat boosts/noncombat maintenance and active TravelTarget arbitration
 * are adapted to Aion. No town IDs, spell IDs, timers or saved policy state. */
final class PlayerBotTownPolicy {
 static boolean buffUseful(SkillTemplate skill,boolean combat,boolean requiredByQuest) {
  return skill!=null && (combat || requiredByQuest || PlayerBotCombatBuffs.nonCombatUseful(skill));
 }
 /** Optional preparation must not replace committed travel. Native bag pressure
  * and the currently active quest's design/material/training work remain useful. */
 static boolean serviceUseful(boolean autonomous,boolean committedTravel,boolean requiredByActiveQuest,boolean necessary) {
  return !autonomous || !committedTravel || requiredByActiveQuest || necessary;
 }
 private PlayerBotTownPolicy() {}
}
