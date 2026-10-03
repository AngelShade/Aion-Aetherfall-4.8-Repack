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
		if (target == null || target.getTarget() != bot || target.isCasting()) return null;
		var allies = party.stream().filter(a -> a != bot && !a.isDead() && PlayerBotService.getInstance().combatRole(a) != PlayerBotRules.Role.TANK
			&& PositionUtil.isInRange(target, a, 15)).map(a -> new PlayerBotNavigation.Point(a.getX(), a.getY(), a.getZ())).toList();
		if (allies.isEmpty()) return null;
		return away(new PlayerBotNavigation.Point(target.getX(), target.getY(), target.getZ()), allies,
			Math.max(2, target.getObjectTemplate().getBoundRadius().getFront() + 1.5f), target.getHeading());
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
