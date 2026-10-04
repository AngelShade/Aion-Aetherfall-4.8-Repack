package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.properties.*;
import com.aionemu.gameserver.utils.PositionUtil;

final class PlayerBotCoordination {
	static PlayerBotNavigation.Point spread(Player bot, List<Player> party, List<Npc> enemies) {
		for (var enemy : enemies) {
			var cast = enemy.getCastingSkill();
			if (cast == null || cast.getFirstTarget() != bot) continue;
			// A targeted cast travels with its victim. Running the active tank away
			// drags the enemy and repeats on every cast; ground hazards are handled
			// separately by PlayerBotHazards. Other roles still spread normally.
			if (enemy.getTarget() == bot && PlayerBotService.getInstance().combatRole(bot) == PlayerBotRules.Role.TANK) continue;
			var p = cast.getSkillTemplate().getProperties();
			if (p == null || p.getFirstTarget() != FirstTargetAttribute.TARGET || p.getTargetRelation() != TargetRelationAttribute.ENEMY
				|| p.getTargetType() != TargetRangeAttribute.AREA || p.getEffectiveDist() > 0 || p.getEffectiveRange() <= 0) continue;
			var allies = party.stream().filter(a -> a != bot && !a.isDead() && PositionUtil.isInRange(bot, a, Math.min(15, p.getEffectiveRange() + 1)))
				.map(a -> new PlayerBotNavigation.Point(a.getX(), a.getY(), a.getZ())).toList();
			if (!allies.isEmpty()) return away(new PlayerBotNavigation.Point(bot.getX(), bot.getY(), bot.getZ()), allies, 6, bot.getHeading());
		}
		return null;
	}
	static PlayerBotNavigation.Point tankFacing(Player bot, Npc target, List<Player> party) {
		// Do not translate facing into a destination beyond a moving enemy.
		// The enemy follows its tank, so recomputing that point creates an
		// unbounded pull. Native attacks face the target; reach and real hazard
		// avoidance remain the combat movement authorities.
		return null;
	}
	static PlayerBotNavigation.Point away(PlayerBotNavigation.Point center, List<PlayerBotNavigation.Point> allies, float radius, byte heading) {
		double x = allies.stream().mapToDouble(PlayerBotNavigation.Point::x).average().orElse(center.x());
		double y = allies.stream().mapToDouble(PlayerBotNavigation.Point::y).average().orElse(center.y());
		double dx = center.x() - x, dy = center.y() - y, length = Math.hypot(dx, dy);
		if (length < .01) { dx = Math.cos(Math.toRadians(heading * 3)); dy = Math.sin(Math.toRadians(heading * 3)); length = 1; }
		return new PlayerBotNavigation.Point(center.x() + (float) (dx / length * radius), center.y() + (float) (dy / length * radius), center.z());
	}
	private PlayerBotCoordination() {}
}
