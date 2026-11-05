/* Health/Mana defaults from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c,
 * PlayerbotAIConfig.cpp and UseFoodStrategy.cpp. GPL-2.0-or-later; AUTHORS.md.
 * Aion policy: real skills/supplies first, no healthy post-fight top-offs, and
 * bounded recovery to almost-full instead of the donor's food cheat/grind defaults. */
package com.aionemu.gameserver.services.playerbot;

final class PlayerBotRecoveryPolicy {
 static final double LOW_HEALTH=45, LOW_MANA=15, RECOVERED=85, SELF_IN_COMBAT=60;
 static double healthLimit(boolean recovering,boolean combat) { return combat || recovering ? RECOVERED : LOW_HEALTH; }
 static double manaLimit(boolean recovering,boolean combat) { return combat ? 40 : recovering ? RECOVERED : LOW_MANA; }
 static boolean recover(double health,double mana,boolean recovering,boolean engaged) {
  return valid(health,mana) && health>0 && !engaged
   && (health<healthLimit(recovering,false) || mana<manaLimit(recovering,false));
 }
 static boolean ready(double health,double mana,boolean recovering) {
  return valid(health,mana) && health>0 && !recover(health,mana,recovering,false);
 }
 /** Mid-fight, only a healer tops itself up early. Everyone else keeps hitting until a heal is worth the lost casts. */
 static double selfCombatLimit(PlayerBotRules.Role role,double limit){return role==PlayerBotRules.Role.HEALER?limit:Math.min(limit,SELF_IN_COMBAT);}
 private static boolean valid(double health,double mana) {
  return Double.isFinite(health) && Double.isFinite(mana) && health>=0 && mana>=0;
 }
 private PlayerBotRecoveryPolicy(){}
}
