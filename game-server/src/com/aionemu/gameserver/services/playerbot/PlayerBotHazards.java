package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.*;
import com.aionemu.gameserver.skillengine.properties.Properties;

/** Visible cast shapes and explicitly supported environmental emitters. No hidden world-object queries. */
final class PlayerBotHazards {
	enum Shape { CIRCLE, SPHERE, DONUT, CONE, LINE }
	record Area(float x, float y, float z, float radius, float inner, float heading, float angle, float width, float altitude, Shape shape) {
		boolean valid() {
			return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(radius) && radius > 0 && radius <= 35
				&& Float.isFinite(inner) && inner >= 0 && inner < radius && Float.isFinite(heading) && Float.isFinite(angle)
				&& Float.isFinite(width) && width >= 0 && Float.isFinite(altitude) && altitude > 0 && shape != null;
		}
		double risk(PlayerBotNavigation.Point point) {
			if (!valid() || Math.abs(point.z() - z) > altitude + 1) return 0;
			double dx = point.x() - x, dy = point.y() - y;
			double distance = shape == Shape.SPHERE ? Math.sqrt(dx * dx + dy * dy + Math.pow(point.z() - z, 2)) : Math.hypot(dx, dy);
			double penetration = radius + 1 - distance;
			if (shape == Shape.DONUT) penetration = Math.min(penetration, distance - Math.max(0, inner - 1));
			if (shape == Shape.CONE) {
				double difference = Math.abs(Math.IEEEremainder(Math.toDegrees(Math.atan2(dy, dx)) - heading, 360));
				if (difference > angle / 2 + Math.toDegrees(Math.atan2(1, Math.max(1, distance)))) return 0;
				penetration = Math.min(penetration, Math.max(0, distance * Math.sin(Math.toRadians(angle / 2 - difference))) + 1);
			}
			if (shape == Shape.LINE) {
				double radians = Math.toRadians(heading), along = dx * Math.cos(radians) + dy * Math.sin(radians);
				double across = Math.abs(-dx * Math.sin(radians) + dy * Math.cos(radians));
				penetration = Math.min(Math.min(along + 1, radius + 1 - along), width + 1 - across);
			}
			return penetration >= 0 ? 1 + penetration : 0;
		}
	}
	static final Map<String, Integer> EMITTERS = Map.of("spilled_oil", 19658, "bladestorm", 20748, "malicious_ice_storm", 21180, "tahabatafirestorm", 20759);
	static Area scripted(String ai, float x, float y, float z, float heading) {
		return switch (ai == null ? "" : ai) {
			case "drakenspire_dimensional_wave" -> new Area(x, y, z, 29, 0, heading - 90, 180, 0, 29, Shape.CONE);
			case "drakenspire_dimensional_wave_small" -> new Area(x, y, z, 22, 0, 0, 0, 0, 22, Shape.SPHERE);
			default -> null;
		};
	}

	static List<Area> visible(Creature actor) {
		List<Area> result = new ArrayList<>();
		int protection = PlayerBotEncounters.visibleProtection(actor);
		actor.getKnownList().forEachNpc(npc -> {
			if (result.size() >= 64 || npc.isDead() || !npc.isSpawned() || npc.getWorldId() != actor.getWorldId()
				|| npc.getInstanceId() != actor.getInstanceId() || npc.getMaster() != npc) return;
			String ai = npc.getObjectTemplate().getAiName();
			Area scripted = scripted(ai, npc.getX(), npc.getY(), npc.getZ(), npc.getHeading() * 3f);
			if (scripted != null) {
				if (actor instanceof com.aionemu.gameserver.model.gameobjects.player.Player && Math.hypot(actor.getX() - npc.getX(), actor.getY() - npc.getY()) <= scripted.radius() + 18) result.add(scripted);
				return;
			}
			// Avoid pulses that would apply the wrong resistance penalty; walk into the matching flame normally.
			if ("dancing_flame".equals(ai)) {
				if (protection > 0 && !PlayerBotEncounters.matchingFlame(npc.getNpcId(), protection)) {
					var flame = DataManager.SKILL_DATA.getSkillTemplate(npc.getNpcId() == 282998 ? 20536 : 20535);
					if (flame != null) {
						Area area = from(flame.getProperties(), npc.getX(), npc.getY(), npc.getZ(), 0, actor.getObjectTemplate().getBoundRadius().getFront());
						if (area != null) result.add(area);
					}
				}
				return;
			}
			Integer id = EMITTERS.get(ai);
			// Native KromedeTrapAI detonates 17050 after 5.5s: its visible object is already a warning, before casting starts.
			if ("kromede_trap".equals(ai)) id = 17050;
			if ("tahabatafirestorm".equals(ai) && npc.getNpcId() == 283102) id = 20753;
			var cast = npc.getCastingSkill();
			SkillTemplate template = id == null ? cast == null ? null : cast.getSkillTemplate() : DataManager.SKILL_DATA.getSkillTemplate(id);
			if (template == null || template.getProperties() == null || id == null && !actor.isEnemy(npc)) return;
			var p = template.getProperties();
			if (p.getTargetRelation() != TargetRelationAttribute.ENEMY && p.getTargetRelation() != TargetRelationAttribute.ALL) return;
			if (p.getTargetType() != TargetRangeAttribute.AREA && p.getTargetType() != TargetRangeAttribute.POINT) return;
			// KromedeTrapAI explicitly casts at its own actor, despite the template's TARGET first-target metadata.
			Creature center = p.getFirstTarget() == FirstTargetAttribute.ME || "kromede_trap".equals(ai) ? npc : cast == null ? null : cast.getFirstTarget();
			if (center == actor && p.getEffectiveDist() == 0) return; // targeted area follows this actor: spreading is a separate mechanic
			float x, y, z;
			if (p.getFirstTarget() == FirstTargetAttribute.POINT && cast != null) { x = cast.getX(); y = cast.getY(); z = cast.getZ(); }
			else if (p.getEffectiveDist() > 0) { x = npc.getX(); y = npc.getY(); z = npc.getZ(); }
			else if (center != null) { x = center.getX(); y = center.getY(); z = center.getZ(); }
			else return;
			Area area = from(p, x, y, z, npc.getHeading() * 3f, npc.getObjectTemplate().getBoundRadius().getFront()
				+ actor.getObjectTemplate().getBoundRadius().getFront());
			if (area != null && Math.hypot(actor.getX() - x, actor.getY() - y) <= area.radius() + 18) result.add(area);
		});
		return List.copyOf(result);
	}
	static Area from(Properties p, float x, float y, float z, float heading, float bounds) {
		if (p == null) return null;
		Shape shape = p.getTargetType() == TargetRangeAttribute.POINT ? Shape.SPHERE : Shape.CIRCLE;
		float radius = p.getTargetType() == TargetRangeAttribute.POINT ? p.getTargetDistance() + 1 : p.getEffectiveRange();
		float inner = p.getIneffectiveRange(), width = 0;
		if (p.getEffectiveDist() > 0 && p.getFirstTarget() != FirstTargetAttribute.POINT && p.getTargetType() != TargetRangeAttribute.POINT) {
			radius = p.getEffectiveDist();
			shape = p.getEffectiveAngle() == 0 ? Shape.LINE : p.getEffectiveAngle() < 360 ? Shape.CONE : Shape.CIRCLE;
			width = p.getEffectiveRange() / 2f + Math.max(0, bounds);
			if (p.getDirection() == AreaDirections.BACK) heading += 180;
		} else if (inner > 0 && p.getFirstTarget() != FirstTargetAttribute.POINT) shape = Shape.DONUT;
		Area area = new Area(x, y, z, radius + Math.max(0, bounds), inner, heading, p.getEffectiveAngle(), width,
			shape == Shape.SPHERE ? radius + Math.max(0, bounds) : p.getEffectiveAltitude() == 0 ? 1 : p.getEffectiveAltitude(), shape);
		return radius > 0 && area.valid() ? area : null;
	}
	static double risk(PlayerBotNavigation.Point point, List<Area> areas) { return areas.stream().mapToDouble(a -> a.risk(point)).sum(); }
	static boolean safePath(PlayerBotNavigation.Point start, PlayerBotNavigation.Point end, List<Area> areas) {
		if (areas.isEmpty()) return true;
		if (!finite(start) || !finite(end)) return false;
		double[] previous = areas.stream().mapToDouble(a -> a.risk(start)).toArray();
		int samples = Math.min(32, Math.max(1, (int) Math.ceil(Math.hypot(end.x() - start.x(), end.y() - start.y()) * 2)));
		for (int index = 1; index <= samples; index++) {
			float ratio = (float) index / samples;
			var point = new PlayerBotNavigation.Point(start.x() + (end.x() - start.x()) * ratio,
				start.y() + (end.y() - start.y()) * ratio, start.z() + (end.z() - start.z()) * ratio);
			for (int area = 0; area < areas.size(); area++) {
				double current = areas.get(area).risk(point);
				if (current > previous[area] + 0.01 || previous[area] == 0 && current > 0) return false;
				previous[area] = current;
			}
		}
		return true;
	}
	private static boolean finite(PlayerBotNavigation.Point p) { return Float.isFinite(p.x()) && Float.isFinite(p.y()) && Float.isFinite(p.z()); }
	static PlayerBotNavigation.Point escape(PlayerBotNavigation.Point start, List<Area> areas, byte heading) {
		double initial = risk(start, areas);
		if (initial == 0) return null;
		PlayerBotNavigation.Point best = null; double bestScore = Double.POSITIVE_INFINITY;
		for (int distance : new int[] { 4, 8, 12, 16 }) for (int offset = 0; offset < 360; offset += 15) {
			double angle = Math.toRadians(heading * 3 + offset);
			var candidate = new PlayerBotNavigation.Point(start.x() + (float) Math.cos(angle) * distance,
				start.y() + (float) Math.sin(angle) * distance, start.z());
			if (!safePath(start, candidate, areas)) continue;
			double danger = risk(candidate, areas), score = danger * 100 + distance;
			if (danger < initial && score < bestScore) { best = candidate; bestScore = score; }
		}
		return best;
	}
	private PlayerBotHazards() {}
}
