package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.utils.PositionUtil;

/** Generates the same animation timing normally supplied by the client; native validation stays enabled. */
public final class PlayerBotSkillTiming {
	public static void prepare(Player bot, Skill skill) {
		var template = skill.getSkillTemplate();
		int time = Math.round(DataManager.MOTION_DATA.calculateAnimationTimeUntilFirstHit(bot, skill));
		if (template.getMotion() != null) time += template.getMotion().getDelay();
		if (template.getAmmoSpeed() > 0 && skill.getFirstTarget() != null)
			time += (int) (PositionUtil.getDistance(bot, skill.getFirstTarget()) / template.getAmmoSpeed() * 1000);
		skill.setClientHitTime(Math.max(0, time));
	}
	static long remainingLock(Player bot, Skill skill) {
		var animation = DataManager.MOTION_DATA.calculateAnimationTimesAfterLastHit(bot, skill);
		return Math.max(400, skill.getCastDuration() + Math.max(skill.getHitTime(), animation == null ? 0 : animation.lastHitMillis()));
	}
	private PlayerBotSkillTiming() {}
}
