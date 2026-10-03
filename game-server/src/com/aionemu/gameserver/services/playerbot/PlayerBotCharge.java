package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.skillengine.model.Skill;

/** Selects a native release window. Actual release/costs/charged skill selection stay in CreatureController. */
final class PlayerBotCharge {
	static long releaseDelay(Skill start, double targetHealth) {
		var condition = start.getSkillTemplate().getSkillChargeCondition();
		var charge = condition == null ? null : DataManager.SKILL_CHARGE_DATA.getChargedSkillEntry(condition.getValue());
		if (charge == null) return -1;
		return releaseDelay(charge.getSkills().stream().map(s -> s.getTime()).toList(), charge.getMinTime(),
			start.getCastSpeedForAnimationBoostAndChargeSkills(), start.getCastDuration(), targetHealth < 25);
	}
	static long releaseDelay(List<Integer> times, int minimum, float speed, int maximum, boolean finishQuickly) {
		if (times == null || times.isEmpty() || !Float.isFinite(speed) || speed <= 0 || minimum < 0 || maximum <= 100
			|| times.stream().anyMatch(t -> t == null || t <= 0)) return -1;
		long beforeFinal = 0;
		if (!finishQuickly) for (int index = 0; index < times.size() - 1; index++) beforeFinal += (int) (times.get(index) * speed);
		long delay = Math.max((long) Math.ceil(minimum * speed) + 25, beforeFinal + 25);
		return delay < maximum - 50 ? delay : -1;
	}
	private PlayerBotCharge() {}
}
