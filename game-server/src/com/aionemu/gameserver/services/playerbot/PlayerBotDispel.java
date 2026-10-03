package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.skillengine.effect.EffectType.*;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.skillengine.effect.AbstractDispelEffect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Prioritizes only effects that this learned cleanse can actually dispel. No effect initialization or power changes. */
final class PlayerBotDispel {
	static double score(PlayerBotSkills.Entry entry, Creature target) {
		double best = 0;
		for (var effect : target.getEffectController().getAbnormalEffects()) {
			if (effect.getRemainingTimeMillis() <= 500 || entry.template().getEffects() == null) continue;
			boolean removable = entry.template().getEffects().getEffects().stream().filter(e -> e instanceof AbstractDispelEffect)
				.map(e -> (AbstractDispelEffect) e).anyMatch(e -> e.canDispelDebuff(effect, entry.level()));
			if (removable) best = Math.max(best, severity(effect.getSkillTemplate(), target.getLifeStats().getHpPercentage()));
		}
		return best;
	}
	static double severity(SkillTemplate debuff, double health) {
		if (debuff == null) return 0;
		double result = debuff.hasAnyEffect(PARALYZE, FEAR, SLEEP, STUN, STUMBLE, STAGGER) ? 30
			: debuff.hasAnyEffect(SILENCE) ? 25 : debuff.hasAnyEffect(ROOT, SNARE) ? 18
			: debuff.hasAnyEffect(BLEED, POISON, SPELLATTACK) ? 12 : 5;
		return result + Math.clamp((100 - health) / 10, 0, 10);
	}
	private PlayerBotDispel() {}
}
