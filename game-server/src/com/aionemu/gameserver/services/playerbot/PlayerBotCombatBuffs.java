package com.aionemu.gameserver.services.playerbot;

import java.util.Set;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Class-appropriate combat preparations from native stat changes; no spell-name or faction-ID guesses. */
final class PlayerBotCombatBuffs {
	static boolean useful(PlayerClass pc, Role role, SkillTemplate skill, double health, double mana) {
		if (skill == null || skill.getEffects() == null || health < 55 || mana < 35 || skill.getDuration() > 1500 || skill.isToggle()) return false;
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
