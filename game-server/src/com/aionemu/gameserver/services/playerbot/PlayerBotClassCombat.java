package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.skillengine.effect.SignetBurstEffect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Class mechanics are evaluated without running native damage calculations or consuming effects. */
final class PlayerBotClassCombat {
	static boolean useful(PlayerClass pc, SkillTemplate skill, Creature target) {
		if (pc == PlayerClass.ASSASSIN && skill.getEffects() != null)
			for (var template : skill.getEffects().getEffects())
				if (template instanceof SignetBurstEffect burst) {
					var signet = target.getEffectController().getAbnormalEffect(burst.getSignet());
					if (!shouldBurst(signet == null ? 0 : signet.getSkillLevel(), burst.getSignetlvl(),
						target.getLifeStats().getHpPercentage(), signet == null ? 0 : signet.getRemainingTimeMillis())) return false;
				}
		return true;
	}
	static boolean shouldBurst(int runes, int maximum, double health, long remainingMillis) {
		return runes > 0 && (runes >= Math.max(1, maximum) || health > 0 && health < 25 || remainingMillis > 0 && remainingMillis <= 2000);
	}
	private PlayerBotClassCombat() {}
}
