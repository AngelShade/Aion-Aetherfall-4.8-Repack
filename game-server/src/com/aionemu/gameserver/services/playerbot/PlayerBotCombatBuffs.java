package com.aionemu.gameserver.services.playerbot;

import java.util.Set;
import java.util.EnumSet;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.effect.*;

/** Class-appropriate combat preparations from native stat changes; no spell-name or faction-ID guesses. */
final class PlayerBotCombatBuffs {
	private static final Set<StatEnum> ATTACK_STATS=Set.of(StatEnum.PHYSICAL_ATTACK,StatEnum.PHYSICAL_CRITICAL,
		StatEnum.PHYSICAL_ACCURACY,StatEnum.ATTACK_SPEED,StatEnum.BOOST_MAGICAL_SKILL,StatEnum.BOOST_SPELL_ATTACK,
		StatEnum.MAGICAL_CRITICAL,StatEnum.MAGICAL_ACCURACY,StatEnum.BOOST_CASTING_TIME,
		StatEnum.BOOST_CASTING_TIME_SKILL,StatEnum.BOOST_CASTING_TIME_ATTACK);
	private static final Set<StatEnum> COMBAT_STATS=combatStats();
	private static Set<StatEnum> combatStats() {
		var stats=EnumSet.copyOf(ATTACK_STATS);
		stats.addAll(Set.of(StatEnum.PHYSICAL_DEFENSE,StatEnum.MAGICAL_DEFEND,StatEnum.MAGICAL_RESIST,
			StatEnum.MAGIC_SKILL_BOOST_RESIST,StatEnum.BLOCK,StatEnum.PARRY,StatEnum.EVASION,
			StatEnum.CONCENTRATION,StatEnum.BOOST_HATE,StatEnum.DAMAGE_REDUCE,StatEnum.DAMAGE_REDUCE_MAX));
		for(var stat:StatEnum.values())if(stat.name().endsWith("_RESISTANCE") || stat.name().endsWith("_RESISTANCE_PENETRATION"))stats.add(stat);
		return Set.copyOf(stats);
	}
	/** CombatStrategy burst purpose: reserve temporary combat preparations and
	 * cancel-on-cast stances for actual combat; keep genuine idle maintenance. */
	static boolean nonCombatUseful(SkillTemplate skill) {
		if(skill==null)return false;
		if(!PlayerBotAethertech.nonCombatAllowed(skill))return false;
		// Aion stances are cancelled by the next ordinary native cast. Repeatedly
		// rebuilding one while idle serves neither a threat nor a lasting aura.
		if(skill.isStance())return false;
		if(skill.isToggle() || skill.getCooldown()<=0 || skill.getEffects()==null || skill.getEffects().getEffects().isEmpty())return true;
		boolean attackBurst=skill.getEffects().getEffects().stream().allMatch(e->e.getSubEffect()==null && e.getDuration2()>0 && e.getDuration2()<skill.getCooldown()*100L
			&& e.getChange()!=null && !e.getChange().isEmpty() && e.getChange().stream().allMatch(c->ATTACK_STATS.contains(c.getStat())
				&& (c.getStat().getSign()<0 ? c.getValue()<0 : c.getValue()>0)));
		// WoW uses distinct combat boost and persistent noncombat strategies.
		// Aion's explicit host fit also recognizes sub-minute combat-only effects
		// whose cooldown is shorter than their duration (native 10s/15s bursts).
		// Unknown, movement, HP/MP, healing and lasting payloads remain admitted.
		boolean temporaryCombat=skill.getEffects().getEffects().stream().allMatch(e->e.getDuration2()>0
			&& e.getDuration2()<60000 && combatPayload(e))
			&& skill.getEffects().getEffects().stream().anyMatch(e->!(e instanceof BoostSkillCostEffect));
		return !attackBurst && !temporaryCombat;
	}
	private static boolean combatPayload(EffectTemplate effect) {
		if(effect.getSubEffect()!=null)return false;
		if(effect instanceof StatupEffect || effect instanceof StatboostEffect
			|| effect instanceof WeaponStatupEffect || effect instanceof WeaponStatboostEffect)
			return effect.getChange()!=null && !effect.getChange().isEmpty()
				&& effect.getChange().stream().allMatch(c->COMBAT_STATS.contains(c.getStat())
					&& c.getStat()!=StatEnum.BOOST_CASTING_TIME && c.getStat()!=StatEnum.BOOST_CASTING_TIME_SKILL);
		return effect instanceof ReflectorEffect || effect instanceof ProvokerEffect
			|| effect instanceof OneTimeBoostSkillAttackEffect || effect instanceof OneTimeBoostSkillCriticalEffect
			|| effect instanceof BoostSpellAttackEffect || effect instanceof BoostHateEffect || effect instanceof BoostSkillCostEffect;
	}
	static boolean useful(PlayerClass pc, Role role, SkillTemplate skill, double health, double mana) {
		Boolean aethertech=PlayerBotAethertech.combatBuffUseful(pc,role,skill,health,mana);
		if(aethertech!=null)return aethertech;
		if (skill == null || skill.getEffects() == null || health < 55 || mana < 35 || skill.getDuration() > 1500) return false;
		if (PlayerBotChanter.applies(pc) && PlayerBotChanter.mantra(skill)) return true;
		if (skill.isToggle()) return false;
		if (PlayerBotSorcerer.applies(pc) && PlayerBotSorcerer.offensiveBoost(skill)) return true;
		Set<StatEnum> relevant = switch (pc) {
			case WARRIOR, GLADIATOR, TEMPLAR, SCOUT, ASSASSIN, RANGER, CHANTER -> Set.of(StatEnum.PHYSICAL_ATTACK, StatEnum.PHYSICAL_CRITICAL, StatEnum.ATTACK_SPEED, StatEnum.PHYSICAL_ACCURACY);
			case ENGINEER, GUNNER, RIDER -> Set.of(StatEnum.BOOST_MAGICAL_SKILL, StatEnum.MAGICAL_CRITICAL, StatEnum.ATTACK_SPEED, StatEnum.MAGICAL_ACCURACY);
			case PRIEST, CLERIC, MAGE, SORCERER, SPIRIT_MASTER, ARTIST, BARD -> Set.of(StatEnum.BOOST_MAGICAL_SKILL, StatEnum.BOOST_CASTING_TIME, StatEnum.HEAL_BOOST, StatEnum.MAGICAL_ACCURACY);
		};
		return skill.getEffects().getEffects().stream().filter(e -> e.getChange() != null).flatMap(e -> e.getChange().stream()).anyMatch(c ->
			relevant.contains(c.getStat()) && c.getValue() != 0 && (c.getStat().getSign() < 0 ? c.getValue() < 0 : c.getValue() > 0)
			|| role == Role.TANK && (c.getStat() == StatEnum.BOOST_HATE || c.getStat() == StatEnum.PHYSICAL_DEFENSE || c.getStat() == StatEnum.BLOCK) && c.getValue() > 0);
	}
	private PlayerBotCombatBuffs() {}
}
