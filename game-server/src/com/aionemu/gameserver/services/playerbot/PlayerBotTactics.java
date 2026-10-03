package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute;
import com.aionemu.gameserver.utils.PositionUtil;

/** Decisions derived from visible enemy casts; encounter-specific scripts can add their own hazards later. */
final class PlayerBotTactics {
	record Hazard(float x, float y, float z, float radius) {}
	static Hazard dangerousCast(Creature bot, List<Npc> enemies) {
		for (Npc enemy : enemies) {
			var cast = enemy.getCastingSkill();
			if (cast == null) continue;
			var properties = cast.getSkillTemplate().getProperties();
			if (properties == null || properties.getTargetRelation() != com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute.ENEMY
				|| properties.getTargetType() != TargetRangeAttribute.AREA && properties.getTargetType() != TargetRangeAttribute.POINT) continue;
			float radius = Math.max(properties.getEffectiveRange(), properties.getTargetDistance());
			if (radius <= 0 || radius > 30) continue;
			float x, y, z;
			if (properties.getFirstTarget() == FirstTargetAttribute.ME) { x = enemy.getX(); y = enemy.getY(); z = enemy.getZ(); }
			else if (properties.getFirstTarget() == FirstTargetAttribute.POINT) { x = cast.getX(); y = cast.getY(); z = cast.getZ(); }
			else if (cast.getFirstTarget() != null) {
				// Moving away cannot evade an area centred on this companion itself.
				if (cast.getFirstTarget() == bot) continue;
				x = cast.getFirstTarget().getX(); y = cast.getFirstTarget().getY(); z = cast.getFirstTarget().getZ();
			} else continue;
			if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && PositionUtil.getDistance(bot, x, y, z) <= radius + 1)
				return new Hazard(x, y, z, radius);
		}
		return null;
	}
	static PlayerBotNavigation.Point escape(float x, float y, float z, Hazard hazard, byte heading) {
		double dx = x - hazard.x(), dy = y - hazard.y(), distance = Math.hypot(dx, dy);
		if (distance < 0.01) { dx = Math.cos(Math.toRadians(heading * 3)); dy = Math.sin(Math.toRadians(heading * 3)); distance = 1; }
		return new PlayerBotNavigation.Point(hazard.x() + (float) (dx / distance * (hazard.radius() + 2)),
			hazard.y() + (float) (dy / distance * (hazard.radius() + 2)), z);
	}
	static double healFit(double missing, double heal, int castMillis, boolean urgent) {
		if (!Double.isFinite(missing) || !Double.isFinite(heal) || missing <= 0 || heal <= 0) return 0;
		double used = Math.min(missing, heal) / missing;
		double waste = Math.max(0, heal - missing) / heal;
		return Math.clamp(used * 2 - waste - Math.max(0, castMillis) / (urgent ? 1000.0 : 4000.0), -3, 2);
	}
}
