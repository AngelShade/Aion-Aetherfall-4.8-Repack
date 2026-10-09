/* Adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Priest/Strategy/HealPriestStrategy.cpp and Base/Trigger/HealthTriggers.cpp.
 * GPL-2.0-or-later; contributors: third-party/playerbots/AUTHORS.md.
 * Native first-heal timing/conditional thresholds replace named WoW spells. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;

/** Bounded Cleric recovery ordering; existing target, reservation and cast gates remain authoritative. */
final class PlayerBotCleric {
 static boolean applies(PlayerClass pc) { return pc == PlayerClass.CLERIC; }
 static String strategy(PlayerClass pc) { return applies(pc) ? "cleric recovery" : PlayerBotSpiritmaster.strategy(pc); }
 record Impact(double amount, long delayMillis, boolean armedProtection, boolean periodic) {}
 record Recovery(double urgency, double fit) {}

 static Impact impact(Player bot, PlayerBotSkills.Entry entry, Creature target) {
  var effect = new Effect(bot, target, entry.template(), entry.level());
  double amount = 0; long delay = Long.MAX_VALUE; boolean armed = false, periodic = false;
  boolean disease = target.getEffectController().isAbnormalSet(AbnormalState.DISEASE);
  for (var template : entry.template().getEffects().getEffects()) {
   double value = 0; long wait = 0;
   if (template instanceof HealInstantEffect heal) {
    if (!disease) value = heal.applyHealDeboost(effect, heal.calculateSnapshotHealValue(effect, HealType.HP));
   } else if (template instanceof CaseHealEffect heal && heal.getType() == HealType.HP) {
    // CaseHeal.startEffect heals immediately only when its native HP threshold is met.
    if (target.getLifeStats().getCurrentHp() <= target.getLifeStats().getMaxHp() * heal.getCondValue() / 100f) {
     if (!disease) value = heal.applyHealDeboost(effect, heal.calculateSnapshotHealValue(effect, HealType.HP));
    } else armed = true;
   } else if (template instanceof HealEffect heal && heal.getChecktime() > 0) {
    // AbstractOverTimeEffect's first tick is scheduled at checktime + 300ms.
    value = heal.applyHealDeboost(effect, heal.calculateSnapshotHealValue(effect, HealType.HP));
    wait = heal.getChecktime() + 300L; periodic = true;
   }
   if (value <= 0) continue;
   if (wait < delay) { delay = wait; amount = value; }
   else if (wait == delay) amount += value;
  }
  return new Impact(amount, delay == Long.MAX_VALUE ? 0 : Math.max(0, entry.template().getDuration()) + delay, armed, periodic);
 }

 static Recovery recovery(Player bot, PlayerBotSkills.Entry entry, Creature target, double urgency) {
  if (!applies(bot.getPlayerClass())) return null;
  var impact = impact(bot, entry, target);
  if (impact.amount() <= 0)
   // Armed protection can still be useful, but it is not an emergency heal yet.
   return new Recovery(impact.armedProtection() ? Math.min(urgency, PlayerBotEngine.HIGH + 2) : 0, 0);
  double hp = PlayerBotHealing.health(target);
  double fit = PlayerBotTactics.healFit(target.getLifeStats().getMaxHp() - target.getLifeStats().getCurrentHp(),
   impact.amount(), (int)Math.min(Integer.MAX_VALUE, impact.delayMillis()), hp < 30);
  // Priest's critical/low-health fast recovery vs almost-full Renew purpose.
  // This is ordering only: a slower learned heal remains available as fallback.
  if (hp < 55) fit += Math.clamp((hp < 30 ? 6 : 3) - impact.delayMillis() / 500.0, -6, 6);
  else if (hp >= 70 && impact.periodic()) fit += 1;
  return new Recovery(urgency, fit);
 }
 private PlayerBotCleric() {}
}
