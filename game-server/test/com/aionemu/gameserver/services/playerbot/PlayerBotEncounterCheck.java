package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.services.playerbot.PlayerBotHazards.Area;
import com.aionemu.gameserver.services.playerbot.PlayerBotHazards.Shape;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Geometry, shield lifetime and priority regressions; no live encounter completion claim. */
public final class PlayerBotEncounterCheck {
	private static int checks;
	private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError("FAIL: " + message); }
	private static Point point(float x, float y) { return new Point(x, y, 0); }
	public static void main(String[] args) throws Exception {
		var circle = new Area(0, 0, 0, 5, 0, 0, 0, 0, 1, Shape.CIRCLE);
		check(circle.risk(point(0, 0)) > 0 && circle.risk(point(7, 0)) == 0, "circle includes center but excludes distant ground");
		check(circle.risk(new Point(0, 0, 4)) == 0, "separate floor is excluded");
		check(!PlayerBotHazards.safePath(point(-8, 0), point(8, 0), List.of(circle)), "safe endpoints cannot route through a damaging field");
		check(PlayerBotHazards.safePath(point(2, 0), point(8, 0), List.of(circle)), "inside actor can escape toward decreasing danger");
		check(!PlayerBotHazards.safePath(point(2, 0), point(-8, 0), List.of(circle)), "escape cannot first move toward hazard center");
		var adjacent = new Area(9, 0, 0, 2, 0, 0, 0, 0, 1, Shape.CIRCLE);
		check(!PlayerBotHazards.safePath(point(2, 0), point(9, 0), List.of(circle, adjacent)), "leaving one field cannot enter another even when total danger falls");
		var exit = PlayerBotHazards.escape(point(2, 0), List.of(circle, adjacent), (byte) 0);
		check(exit != null && PlayerBotHazards.risk(exit, List.of(circle, adjacent)) == 0, "escape chooses an available safe direction");
		check(PlayerBotHazards.escape(point(7, 0), List.of(circle), (byte) 0) == null, "safe actor needs no escape");
		var opposing = new Area(4, 0, 0, 5, 0, 0, 0, 0, 1, Shape.CIRCLE);
		var confined = PlayerBotHazards.escape(point(2, 0), List.of(circle, opposing), (byte) 0);
		check(confined == null || PlayerBotHazards.safePath(point(2, 0), confined, List.of(circle, opposing)), "overlap never invents an unsafe escape");
		check(!PlayerBotHazards.safePath(point(Float.NaN, 0), point(8, 0), List.of(circle)), "invalid coordinates cannot authorize motion");
		var donut = new Area(0, 0, 0, 12, 5, 0, 0, 0, 1, Shape.DONUT);
		check(donut.risk(point(0, 0)) == 0 && donut.risk(point(8, 0)) > 0, "donut preserves its safe central pocket");
		var data = (SkillData) JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
			<skill_data>
			<skill_template skill_id="1"><properties first_target="ME" target_type="AREA" effective_dist="10" effective_angle="90" direction="FRONT"/></skill_template>
			<skill_template skill_id="2"><properties first_target="ME" target_type="AREA" effective_dist="10" effective_angle="90" direction="BACK"/></skill_template>
			<skill_template skill_id="3"><properties first_target="ME" target_type="AREA" effective_dist="12" effective_range="4"/></skill_template>
			<skill_template skill_id="4"><properties first_target="POINT" target_type="AREA" effective_range="6" ineffective_range="4" effective_dist="10" effective_angle="90"/></skill_template>
			<skill_template skill_id="5"><properties first_target="POINT" target_type="POINT" target_distance="9"/></skill_template>
			</skill_data>
			"""));
		var front = PlayerBotHazards.from(data.getSkillTemplate(1).getProperties(), 0, 0, 0, 0, 0);
		var back = PlayerBotHazards.from(data.getSkillTemplate(2).getProperties(), 0, 0, 0, 0, 0);
		check(front.risk(point(5, 0)) > 0 && front.risk(point(-5, 0)) == 0 && front.risk(point(0, 5)) == 0, "front cone respects heading");
		check(back.risk(point(-5, 0)) > 0 && back.risk(point(5, 0)) == 0, "back cone reverses direction");
		var wrapped = PlayerBotHazards.from(data.getSkillTemplate(1).getProperties(), 0, 0, 0, 360, 0);
		check(wrapped.risk(point(5, 0)) == front.risk(point(5, 0)), "heading wraps through full turn");
		var line = PlayerBotHazards.from(data.getSkillTemplate(3).getProperties(), 0, 0, 0, 0, 0);
		check(line.risk(point(8, 0)) > 0 && line.risk(point(8, 5)) == 0 && line.risk(point(-4, 0)) == 0, "line distinguishes length, width and backward ground");
		var ground = PlayerBotHazards.from(data.getSkillTemplate(4).getProperties(), 0, 0, 0, 0, 0);
		check(ground.shape() == Shape.CIRCLE && ground.risk(point(-3, 0)) > 0, "native point-centered area ignores directional and inner range metadata");
		var sphere = PlayerBotHazards.from(data.getSkillTemplate(5).getProperties(), 0, 0, 0, 0, 0);
		check(sphere.risk(new Point(0, 0, 5)) > 0 && sphere.risk(new Point(9, 0, 9)) == 0,
			"native POINT range uses spatial radius rather than default AREA altitude");
		check(PlayerBotEncounters.requiredProtection(false, false) == 0, "ordinary boss state requires no special protection");
		check(PlayerBotEncounters.requiredProtection(true, false) == 20535 && PlayerBotEncounters.requiredProtection(false, true) == 20536, "native blue and red shields choose matching protection");
		check(PlayerBotEncounters.requiredProtection(true, true) < 0, "ambiguous shield state blocks new damage");
		check(PlayerBotEncounters.matchingFlame(282997, 20535) && PlayerBotEncounters.matchingFlame(282999, 20535)
			&& PlayerBotEncounters.matchingFlame(282998, 20536) && !PlayerBotEncounters.matchingFlame(282998, 20535), "flame choices never apply the opposite elemental weakness");
		check(!PlayerBotEncounters.protectedFor(20535, 500, 1000) && PlayerBotEncounters.protectedFor(20535, 2000, 1000), "protection must last through the intended cast commitment");
		check(!PlayerBotEncounters.protectedFor(-1, 16000, 0) && PlayerBotEncounters.protectedFor(0, 0, 1000), "unknown shield is refused while unshielded attacks remain allowed");
		var engine = new PlayerBotEngine(); var performed = new ArrayList<String>();
		engine.tick(PlayerBotEngine.State.COMBAT, List.of(new PlayerBotEngine.Strategy("blocked hazard", List.of(
			trigger("escape", PlayerBotEngine.ENCOUNTER + 20, false, performed), trigger("heal", PlayerBotRules.healPriority(40, true), true, performed)))), List.of(), 8);
		check(performed.equals(List.of("heal")), "blocked escape cannot starve a valid recovery action");
		performed.clear();
		engine.tick(PlayerBotEngine.State.COMBAT, List.of(new PlayerBotEngine.Strategy("urgent recovery", List.of(
			trigger("escape", PlayerBotEngine.ENCOUNTER + 20, true, performed), trigger("heal", PlayerBotRules.healPriority(10, true), true, performed)))), List.of(), 8);
		check(performed.equals(List.of("heal")), "critical healing precedes generic hazard movement");
		performed.clear();
		engine.tick(PlayerBotEngine.State.COMBAT, List.of(new PlayerBotEngine.Strategy("rank fallback", List.of(
			trigger("expensive rank", 20, false, performed), trigger("affordable rank", 19, true, performed)))), List.of(), 8);
		check(performed.equals(List.of("affordable rank")), "unavailable stronger skill permits the cheaper candidate");
		System.out.println("OK: " + checks + " encounter geometry, protection and recovery arbitration checks");
	}
	private static PlayerBotEngine.Trigger trigger(String name, double relevance, boolean success, List<String> performed) {
		return new PlayerBotEngine.Trigger(() -> true, new PlayerBotEngine.Action() {
			public String name() { return name; }
			public boolean isUseful() { return true; }
			public boolean isPossible() { return true; }
			public boolean execute() { if (success) performed.add(name); return success; }
		}, () -> relevance);
	}
}
