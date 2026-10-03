package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.List;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.PlayerBotHttpService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

public final class PlayerBotPlanningCheck {
	private static int checks;
	private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError("FAIL: " + message); }
	public static void main(String[] args) throws Exception {
		var threat = new PlayerBotThreat();
		check(!threat.decide(1, 50, 100, true, false, 10000), "healthy tank threat margin allows damage");
		check(threat.decide(1, 90, 100, true, false, 10100), "high native hate starts a bounded damage pause");
		check(threat.decide(1, 80, 100, true, false, 11000), "hysteresis prevents oscillation at the start threshold");
		check(!threat.decide(1, 90, 100, true, false, 12600), "pause ends even if tank cannot catch up");
		check(!threat.decide(1, 95, 100, true, false, 13000), "resume interval permits useful damage between pauses");
		check(threat.decide(1, 95, 100, true, false, 14200), "later high threat can pause again");
		check(!threat.decide(1, 60, 100, true, false, 14300), "recovered threat margin releases pause immediately");
		check(!threat.decide(2, 200, 0, true, false, 15000), "solo/no-tank encounters cannot throttle forever");
		check(!threat.decide(2, 200, 100, true, true, 15100), "tank damage is never held for another tank");
		check(!threat.decide(2, 200, 100, false, false, 15200), "finishing a nearly defeated target takes precedence");
		var data = (SkillData) JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
			<skill_data>
			<skill_template skill_id="1" activation="ACTIVE" duration="0"><effects><statup><change stat="PHYSICAL_ATTACK" func="ADD" value="20"/></statup></effects></skill_template>
			<skill_template skill_id="2" activation="ACTIVE" duration="0"><effects><statup><change stat="BOOST_MAGICAL_SKILL" func="ADD" value="200"/></statup></effects></skill_template>
			<skill_template skill_id="3" activation="ACTIVE" duration="0"><effects><statup><change stat="BLOCK" func="ADD" value="200"/></statup></effects></skill_template>
			<skill_template skill_id="4" activation="ACTIVE" duration="4000"><effects><statup><change stat="PHYSICAL_ATTACK" func="ADD" value="20"/></statup></effects></skill_template>
			</skill_data>
			"""));
		check(PlayerBotCombatBuffs.useful(PlayerClass.ASSASSIN, Role.MELEE, data.getSkillTemplate(1), 100, 100), "Assassin prepares physical damage");
		check(!PlayerBotCombatBuffs.useful(PlayerClass.SORCERER, Role.RANGED, data.getSkillTemplate(1), 100, 100), "caster avoids irrelevant physical preparation");
		check(PlayerBotCombatBuffs.useful(PlayerClass.GUNNER, Role.RANGED, data.getSkillTemplate(2), 100, 100), "Gunner uses magical preparation");
		check(PlayerBotCombatBuffs.useful(PlayerClass.TEMPLAR, Role.TANK, data.getSkillTemplate(3), 100, 100), "tank can prepare native block enhancement");
		check(!PlayerBotCombatBuffs.useful(PlayerClass.GLADIATOR, Role.MELEE, data.getSkillTemplate(4), 100, 100), "long preparation cannot monopolize combat casts");
		check(!PlayerBotCombatBuffs.useful(PlayerClass.ASSASSIN, Role.MELEE, data.getSkillTemplate(1), 30, 20), "recovery takes precedence over preparation");
		for (var pc : PlayerClass.values()) check(PlayerBotCombatBuffs.useful(pc, PlayerBotRules.roleFor(pc), data.getSkillTemplate(1), 100, 100)
			|| PlayerBotCombatBuffs.useful(pc, PlayerBotRules.roleFor(pc), data.getSkillTemplate(2), 100, 100), "all native classes have a compatible combat preparation policy: " + pc);
		var origin = new PlayerBotNavigation.Point(0, 0, 0);
		var away = PlayerBotCoordination.away(origin, List.of(new PlayerBotNavigation.Point(5, 0, 0)), 3, (byte) 0);
		check(away.x() == -3 && away.y() == 0, "tank anchor faces the enemy away from party center");
		check(Float.isFinite(PlayerBotCoordination.away(origin, List.of(origin), 6, (byte) 0).x()), "stacked party coordinates still yield a finite spread goal");
		PlayerBotConfig.MISSION_DISTANCE = 900;
		check(PlayerBotMission.range() == 500, "mission config cannot remove the owner distance cap");
		PlayerBotConfig.MISSION_DISTANCE = 180;
		check(!PlayerBotRules.canAttack(true, true, true, false, true, false, false, 100)
			&& PlayerBotRules.canAttack(true, true, true, false, true, false, false, 100, PlayerBotMission.range()), "only explicit mission policy expands the normal owner leash");
		check(!PlayerBotRules.canAttack(false, true, true, false, true, false, false, 100, 180), "mission leash never admits human PvP targets");
		var close = new PlayerBotMission.Destination(1, false, new PlayerBotNavigation.Point(3, 0, 0));
		var distant = new PlayerBotMission.Destination(2, false, new PlayerBotNavigation.Point(10, 0, 0));
		var invalid = new PlayerBotMission.Destination(3, false, new PlayerBotNavigation.Point(Float.NaN, 0, 0));
		check(PlayerBotMission.nearest(origin, List.of(distant, invalid, close)).equals(close), "travel chooses nearest finite native spawn candidate");
		Object player = new Object(), connection = new Object();
		check(PlayerBotHttpService.validTicket(player, player, connection, connection, 200, 100), "current connection may use its unexpired form");
		check(!PlayerBotHttpService.validTicket(player, new Object(), connection, connection, 200, 100), "another player cannot replay a companion form");
		check(!PlayerBotHttpService.validTicket(player, player, connection, new Object(), 200, 100), "re-login invalidates the old connection form");
		check(!PlayerBotHttpService.validTicket(player, player, connection, connection, 100, 100), "expired form cannot mutate recruitment");
		check(PlayerBotHttpService.parse("name=My+Cleric&slot=MAIN_HAND").get("name").equals("My Cleric"), "panel form decoding preserves text");
		boolean rejected = false;
		try { PlayerBotHttpService.parse("action=recruit&action=dismiss"); } catch (IllegalArgumentException expected) { rejected = true; }
		check(rejected, "duplicate mutation fields are rejected rather than ambiguously interpreted");
		var wave = PlayerBotHazards.scripted("drakenspire_dimensional_wave", 0, 0, 0, 90);
		check(wave.risk(new PlayerBotNavigation.Point(10, 0, 0)) > 0 && wave.risk(new PlayerBotNavigation.Point(-10, 0, 0)) == 0, "Drakenspire half-wave follows native heading-max minus 180 degrees");
		var small = PlayerBotHazards.scripted("drakenspire_dimensional_wave_small", 0, 0, 0, 0);
		check(small.risk(new PlayerBotNavigation.Point(10, 0, 10)) > 0 && small.risk(new PlayerBotNavigation.Point(30, 0, 0)) == 0, "small dimensional wave follows the native 22m spatial radius");
		System.out.println("OK: " + checks + " class preparation, threat, coordination, mission and panel policy checks");
	}
}
