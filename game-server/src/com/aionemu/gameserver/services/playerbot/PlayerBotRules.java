/*
 * Health priority bands adapted on 2026-10-03 from mod-playerbots, commit
 * 037c01418b5d01506917a3db9b44fd56ac5f965c, HealPriestStrategy.cpp and HealthTriggers.cpp.
 * Upstream contributors: third-party/playerbots/AUTHORS.md. SPDX-License-Identifier: GPL-2.0-or-later
 * Aion role mapping, lease eligibility and combat policy are local adaptations.
 */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;

public final class PlayerBotRules {
	public enum Role { TANK, HEALER, SUPPORT, MELEE, RANGED }
	public enum Order { FOLLOW, STAY, GUARD, PASSIVE }
	public enum SkillKind { HEAL, MANA, CLEANSE, RESURRECT, RECOVERY, MODE, SUMMON, PET_ORDER, DEFENSE, BUFF, TAUNT, CONTROL, DAMAGE, UNSUPPORTED }

	public static boolean shouldRest(double health, double mana, boolean resting, boolean combat, boolean ownerMoving, double ownerDistance) {
		return Double.isFinite(health) && Double.isFinite(mana) && Double.isFinite(ownerDistance)
			&& health > 0 && !combat && !ownerMoving && ownerDistance <= 8
			&& (resting ? health < 95 || mana < 95 : health < 70 || mana < 40);
	}

	public static Role roleFor(PlayerClass pc) {
		return switch (pc) {
			case TEMPLAR -> Role.TANK;
			case PRIEST, CLERIC -> Role.HEALER;
			case CHANTER, BARD, ARTIST -> Role.SUPPORT;
			case WARRIOR, GLADIATOR, SCOUT, ASSASSIN, RIDER -> Role.MELEE;
			default -> Role.RANGED;
		};
	}

	public static double healPriority(double health, boolean underAttack) {
		if (!Double.isFinite(health) || health <= 0 || health >= 85)
			return 0;
		if (health < 30)
			return PlayerBotEngine.EMERGENCY + 5 + (30 - health) / 10;
		if (health < 55)
			return PlayerBotEngine.MOVE + 5 + (55 - health) / 20;
		return PlayerBotEngine.HIGH + (85 - health) / 20 + (underAttack ? 2 : 0);
	}

	public static boolean canRecruit(boolean owned, boolean sameRace, boolean online, boolean banned,
		boolean deleting, boolean imprisoned, int levelDifference, int maximumDifference) {
		return owned && sameRace && !online && !banned && !deleting && !imprisoned
			&& Math.abs(levelDifference) <= Math.max(0, maximumDifference);
	}

	public static boolean canAttack(boolean npc, boolean hostile, boolean alive, boolean engaged,
		boolean explicitAttack, boolean controlled, boolean passive, double ownerDistance) {
		return canAttack(npc, hostile, alive, engaged, explicitAttack, controlled, passive, ownerDistance, 45);
	}
	public static boolean canAttack(boolean npc, boolean hostile, boolean alive, boolean engaged,
		boolean explicitAttack, boolean controlled, boolean passive, double ownerDistance, double maximumDistance) {
		return npc && hostile && alive && !controlled && !passive && (engaged || explicitAttack)
			&& Double.isFinite(ownerDistance) && Double.isFinite(maximumDistance) && maximumDistance >= 0 && ownerDistance >= 0 && ownerDistance <= maximumDistance;
	}

	private PlayerBotRules() {}
}
