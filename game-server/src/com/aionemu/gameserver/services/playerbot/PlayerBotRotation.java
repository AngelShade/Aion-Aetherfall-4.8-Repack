package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Metadata-driven offensive ranking. Estimates never run native damage calculations or observers. */
final class PlayerBotRotation {
	record Context(PlayerClass playerClass, Role role, double health, double mana, double targetHealth,
		double targetHp, double weaponDamage, int nearbyEnemies, boolean targetCasting, boolean tankHasTarget) {}
	static double score(SkillTemplate skill, int level, Context context) {
		if (skill == null || skill.getEffects() == null) return 0;
		double damage = estimatedDamage(skill, level, context.weaponDamage(), context.targetHp());
		// A short cast with useful damage ranks above long casts and skills with no damage payload.
		double speed = Math.max(0.6, 0.6 + Math.max(0, skill.getDuration()) / 1000.0);
		double result = Math.clamp(Math.log1p(Math.max(0, damage) / speed) / 1.8, 0, 5);
		boolean drain = skill.hasAnyEffect(EffectType.SKILLATKDRAININSTANT, EffectType.SPELLATKDRAIN, EffectType.SPELLATKDRAININSTANT);
		if (drain && context.health() < 65) result += 2;
		boolean periodic = skill.hasAnyEffect(EffectType.SPELLATTACK, EffectType.POISON, EffectType.BLEED);
		if (periodic && context.targetHealth() < 25) result -= 4;
		if (periodic && context.targetHealth() > 65) result += 1;
		if (context.mana() < 25 && skill.getDuration() > 2000) result -= 1;
		result += switch (context.playerClass()) {
			case ASSASSIN -> skill.hasAnyEffect(EffectType.CARVESIGNET) ? 2 : skill.hasAnyEffect(EffectType.SIGNETBURST) ? 3 : 0;
			case TEMPLAR -> context.role() == Role.TANK && !context.tankHasTarget() && skill.hasAnyEffect(EffectType.HOSTILEUP, EffectType.PROVOKER) ? 3 : 0;
			case GLADIATOR, WARRIOR -> drain && context.health() < 50 ? 1 : 0;
			case SPIRIT_MASTER -> periodic && context.targetHealth() > 65 ? 1 : 0;
			case SORCERER, MAGE -> context.targetCasting() && PlayerBotSkills.canInterrupt(skill) ? 2 : 0;
			case RANGER -> skill.hasAnyEffect(EffectType.ROOT, EffectType.STATDOWN) && !context.tankHasTarget() ? 1 : 0;
			case CHANTER, PRIEST, CLERIC -> drain && context.health() < 65 ? 1 : 0;
			case GUNNER, ENGINEER -> skill.getSkillChargeCondition() != null && context.targetHealth() > 65 ? 1 : 0;
			case RIDER -> skill.hasAnyEffect(EffectType.MPHEALINSTANT, EffectType.MPHEAL) && context.mana() < 40 ? 2 : 0;
			case BARD, ARTIST -> context.mana() < 40 && drain ? 1 : 0;
			case SCOUT -> 0;
		};
		return Math.clamp(result, -4, 7);
	}
	static double estimatedDamage(SkillTemplate skill, int level, double weaponDamage, double targetHp) {
		double damage = 0;
		for (var effect : skill.getEffects().getEffects()) {
			double value = Math.max(0, effect.getValue() + (double) effect.getDelta() * level);
			if (effect instanceof DamageEffect instant) {
				double attack = effect.getElement() == com.aionemu.gameserver.model.SkillElement.NONE ? Math.max(0, weaponDamage) : 0;
				damage += switch (instant.getMode()) {
					case ADD -> value + attack;
					case PERCENT -> attack * value / 100;
					case REPLACE -> value;
				};
			} else if (effect instanceof AbstractOverTimeEffect periodic
				&& (effect instanceof SpellAttackEffect || effect instanceof BleedEffect || effect instanceof PoisonEffect)) {
				int ticks = periodic.getChecktime() <= 0 ? 1 : Math.min(6, Math.max(1, periodic.getDuration2() / periodic.getChecktime()));
				damage += (periodic.isPercent() ? Math.max(0, targetHp) * value / 100 : value) * ticks;
			}
		}
		return Double.isFinite(damage) ? damage : 0;
	}
	/** Stable ties prefer higher ranks; retain cheaper/lower ranks for native cost/cooldown fallback. */
	static List<PlayerBotSkills.Entry> rankedSkills(List<PlayerBotSkills.Entry> entries) {
		return entries.stream().sorted(java.util.Comparator.<PlayerBotSkills.Entry>comparingInt(e -> e.template().getLvl()).reversed()
			.thenComparing(java.util.Comparator.comparingInt(PlayerBotSkills.Entry::level).reversed())
			.thenComparingInt(e -> e.template().getSkillId())).toList();
	}
	private PlayerBotRotation() {}
}
