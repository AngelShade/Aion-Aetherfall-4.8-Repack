package com.aionemu.gameserver.services.playerbot;

import java.util.Set;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Class-appropriate combat preparations from native stat changes; no spell-name or faction-ID guesses. */
final class PlayerBotCombatBuffs {
	private static final Set<StatEnum> ATTACK_STATS=Set.of(StatEnum.PHYSICAL_ATTACK,StatEnum.PHYSICAL_CRITICAL,
		StatEnum.PHYSICAL_ACCURACY,StatEnum.ATTACK_SPEED,StatEnum.BOOST_MAGICAL_SKILL,StatEnum.BOOST_SPELL_ATTACK,
		StatEnum.MAGICAL_CRITICAL,StatEnum.MAGICAL_ACCURACY,StatEnum.BOOST_CASTING_TIME,
		StatEnum.BOOST_CASTING_TIME_SKILL,StatEnum.BOOST_CASTING_TIME_ATTACK);
	/** CombatStrategy burst purpose: an attack-only effect that expires before its
	 * own cooldown is not a standing maintenance buff. Native mixed/recovery,
	 * movement, lasting and toggle preparations retain noncombat admission. */
	static boolean nonCombatUseful(SkillTemplate skill) {
		if(skill==null)return false;
		if(skill.isToggle() || skill.getCooldown()<=0 || skill.getEffects()==null || skill.getEffects().getEffects().isEmpty())return true;
		return !skill.getEffects().getEffects().stream().allMatch(e->e.getDuration2()>0 && e.getDuration2()<skill.getCooldown()*100L
			&& e.getChange()!=null && !e.getChange().isEmpty() && e.getChange().stream().allMatch(c->ATTACK_STATS.contains(c.getStat())
				&& (c.getStat().getSign()<0 ? c.getValue()<0 : c.getValue()>0)));
	}
	static boolean useful(PlayerClass pc, Role role, SkillTemplate skill, double health, double mana) {
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
