package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind;
import com.aionemu.gameserver.skillengine.effect.EffectType;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute;

/** Native RIDER preparations and resource decisions, shared by both bot lifecycles.
 * Skill groups are the families declared in skill_templates.xml, not foreign rotations.
 * No effect calculation, cast, resource payment or stat adjustment occurs here. */
final class PlayerBotAethertech {
	static boolean applies(PlayerClass pc) { return pc == PlayerClass.RIDER; }

	private static boolean selfMechSkill(SkillTemplate skill) {
		return skill != null && !skill.isPassive() && !skill.isProvoked()
			&& skill.getRideRobotCondition() != null && skill.getProperties() != null
			&& skill.getProperties().getFirstTarget() == FirstTargetAttribute.ME
			&& skill.getProperties().getTargetRelation() == TargetRelationAttribute.FRIEND;
	}

	static boolean chargingActivation(SkillTemplate skill) {
		// This PROVOKER installs the native attack observer whose proc heals MP.
		// Leeching Steel also uses PROVOKER, but attacks enemy MP and is not this buff.
		return selfMechSkill(skill) && "RI_ACTIVATEGENERATOR".equals(skill.getGroup())
			&& skill.hasAnyEffect(EffectType.PROVOKER);
	}

	private static boolean mobility(SkillTemplate skill) {
		return selfMechSkill(skill) && skill.isToggle() && "RI_SPEEDOVERDRIVE".equals(skill.getGroup());
	}

	private static boolean stability(SkillTemplate skill) {
		return selfMechSkill(skill) && skill.isToggle() && "RI_POWEROVERDRIVE".equals(skill.getGroup());
	}

	static boolean nonCombatAllowed(SkillTemplate skill) {
		// Stability consumes 4% MP every four seconds; Mounting Frustration adds hate.
		return !stability(skill) && !(selfMechSkill(skill) && "RI_WARNINGALARM".equals(skill.getGroup()));
	}

	/** Travel is the caller's continuing intent, not a momentary mover pause for casting. */
	static boolean buffAllowed(Player bot, Role role, SkillTemplate skill, boolean combat, boolean travelling) {
		return toggleAllowed(bot, role, skill, combat, travelling, 40);
	}

	private static boolean toggleAllowed(Player bot, Role role, SkillTemplate skill, boolean combat, boolean travelling, int manaFloor) {
		if (!applies(bot.getPlayerClass()) || !selfMechSkill(skill)) return true;
		double mana = PlayerBotDefense.mana(bot);
		if (mobility(skill)) return !combat && travelling && mana >= manaFloor;
		if (stability(skill)) return combat && mana >= manaFloor;
		if ("RI_WARNINGALARM".equals(skill.getGroup())) return combat && role == Role.TANK;
		return true;
	}

	/** Null leaves other classes and preparations on the installed generic policy. */
	static Boolean combatBuffUseful(PlayerClass pc, Role role, SkillTemplate skill, double health, double mana) {
		if (!applies(pc) || !selfMechSkill(skill)) return null;
		if (mobility(skill)) return false; // its speed comes with a physical-defense penalty
		if (stability(skill)) return health > 0 && mana >= 40;
		if (chargingActivation(skill)) return health > 0; // native MP admission still checks the cost
		if ("RI_WARNINGALARM".equals(skill.getGroup())) return role == Role.TANK && health > 0;
		return null;
	}

	/** Return only an obsolete native toggle. The session owns the ordinary cancellation action. */
	static Effect unwantedToggle(Player bot, Role role, boolean combat, boolean travelling) {
		if (!applies(bot.getPlayerClass())) return null;
		return bot.getEffectController().getAllEffects().stream().filter(e -> !e.isPassive())
			.filter(e -> mobility(e.getSkillTemplate()) || stability(e.getSkillTemplate())
				|| selfMechSkill(e.getSkillTemplate()) && e.getSkillTemplate().isToggle()
					&& "RI_WARNINGALARM".equals(e.getSkillTemplate().getGroup()))
			// Leave a reserve for native recovery without toggling straight back off
			// after the activation cost takes MP below the 40% admission threshold.
			.filter(e -> !toggleAllowed(bot, role, e.getSkillTemplate(), combat, travelling, 20))
			.findFirst().orElse(null);
	}

	static double recoveryPriority(Player bot, PlayerBotSkills.Entry entry, Creature target) {
		if (!applies(bot.getPlayerClass()) || entry.kind() != SkillKind.HEAL || target != bot
			|| !selfMechSkill(entry.template()) || !entry.template().hasAnyEffect(EffectType.MPHEALINSTANT)) return 0;
		// Recharge retains its native HP usefulness and now also responds to the same
		// <40% MP threshold as pure mana recovery, even when HP is already full.
		return !bot.isDead() && bot.isInRobotMode() && PlayerBotDefense.mana(bot) < 40 ? PlayerBotEngine.HIGH + 1 : 0;
	}


	private PlayerBotAethertech() {}
}
