package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotPopulationConfig;
import com.aionemu.gameserver.model.Race;

/**
 * 7 Nov live-defect fixes (Phase 0/0B of project-notes/playerbots-activity-lod-plan-20261107.md): camp death memory,
 * aggressive-monster standoff landings, revive/move without a reload, the save lock-race retry, and the activity bands.
 * Pure logic plus source wiring; no World, SQL or tasks.
 */
public final class PlayerBotCampSafetyCheck {
	private static int checks;
	private static final int MAP = 210050000;

	private static void check(boolean value, String why) {
		checks++;
		if (!value)
			throw new AssertionError(why);
	}

	private static final class Flat implements PlayerBotCampSafety.Ground {
		final java.util.function.Predicate<float[]> valid;

		Flat(java.util.function.Predicate<float[]> valid) {
			this.valid = valid;
		}

		public float height(float x, float y, float sourceZ) {
			return sourceZ;
		}

		public boolean valid(float x, float y, float z) {
			return valid.test(new float[] { x, y, z });
		}
	}

	public static void main(String[] args) throws Exception {
		PlayerBotPopulationConfig.CAMP_SAFETY = true;
		PlayerBotPopulationConfig.CAMP_SAFETY_MARGIN = 10;
		PlayerBotPopulationConfig.CAMP_SAFETY_LEVEL_BELOW = 3;
		PlayerBotPopulationConfig.CAMP_DEATH_LIMIT = 2;
		PlayerBotPopulationConfig.CAMP_DEATH_WINDOW = 3600;
		PlayerBotPopulationConfig.CAMP_DEATH_AVOID = 86400;
		deaths();
		danger();
		standoff();
		saveRetry();
		parkedAttacks();
		metrics();
		wiring(args.length > 0 ? Path.of(args[0]) : Path.of("."));
		System.out.println("OK: " + checks + " camp death memory/standoff landing/in-place revival/save retry/activity band checks; no World/SQL/tasks");
	}

	private static void deaths() {
		PlayerBotCampSafety.reset();
		long t = 1_000_000_000L;
		check(!PlayerBotCampSafety.recordDeath(MAP, 668.8f, 1148.8f, 47, true, t), "One death does not close a camp");
		check(!PlayerBotCampSafety.avoided(MAP, 668.8f, 1148.8f, 47, t), "A camp with one death stays open");
		check(PlayerBotCampSafety.recordDeath(MAP, 669.5f, 1149.2f, 47, true, t + 60_000), "A second death in the same cell within an hour closes it");
		check(PlayerBotCampSafety.avoided(MAP, 668.8f, 1148.8f, 47, t + 120_000), "Valalathin's death spot is skipped for its band");
		check(PlayerBotCampSafety.avoided(MAP, 668.8f, 1148.8f, 49, t + 120_000), "Levels in the same 5-level band are skipped too");
		check(!PlayerBotCampSafety.avoided(MAP, 668.8f, 1148.8f, 52, t + 120_000), "Another level band may still use the camp");
		check(!PlayerBotCampSafety.avoided(MAP, 760f, 1148.8f, 47, t + 120_000), "A camp far from the closed cell is unaffected");
		check(!PlayerBotCampSafety.avoided(MAP + 1, 668.8f, 1148.8f, 47, t + 120_000), "Another map is unaffected");
		check(PlayerBotCampSafety.avoided(MAP, 655f, 1148.8f, 47, t + 120_000), "A landing shifted into the neighbouring cell next to the closed one is still skipped");
		check(!PlayerBotCampSafety.avoided(MAP, 668.8f, 1148.8f, 47, t + 60_000 + 86_400_001L), "The closure expires after avoid_seconds");
		PlayerBotCampSafety.reset();
		PlayerBotCampSafety.recordDeath(MAP, 1063f, 1314f, 63, false, t);
		check(!PlayerBotCampSafety.recordDeath(MAP, 1063f, 1314f, 63, false, t + 3_600_001L), "Deaths more than an hour apart do not close a camp");
		PlayerBotPopulationConfig.CAMP_SAFETY = false;
		PlayerBotCampSafety.recordDeath(MAP, 1063f, 1314f, 63, false, t + 3_600_002L);
		check(!PlayerBotCampSafety.avoided(MAP, 1063f, 1314f, 63, t + 3_600_003L), "Disabled camp safety never skips a camp");
		PlayerBotPopulationConfig.CAMP_SAFETY = true;
		long before = PlayerBotCampSafety.DEATHS_AWAKE.get();
		PlayerBotCampSafety.recordDeath(MAP, 0, 0, 10, false, t);
		check(PlayerBotCampSafety.DEATHS_AWAKE.get() == before + 1, "Deaths are split into awake and dormant counters");
	}

	private static void danger() {
		PlayerBotCampSafety.reset();
		// A level 50 pack with aggro range 15 at (1000,1000); margin 10 => 25 m danger radius.
		PlayerBotCampSafety.threatsForTest(MAP, Race.ELYOS, List.of(new PlayerBotCampSafety.Threat(1000, 1000, 400, 50, 25)));
		check(PlayerBotCampSafety.dangerous(MAP, 1000, 1000, 400, Race.ELYOS, 47), "A bot on a stronger aggressive pack's spawn point is in danger");
		check(PlayerBotCampSafety.dangerous(MAP, 1020, 1000, 400, Race.ELYOS, 47), "Inside aggro range plus margin is danger");
		check(!PlayerBotCampSafety.dangerous(MAP, 1030, 1000, 400, Race.ELYOS, 47), "Outside aggro range plus margin is safe");
		check(PlayerBotCampSafety.dangerous(MAP, 1000, 1000, 400, Race.ELYOS, 53), "A monster up to level_below under the bot still counts");
		check(!PlayerBotCampSafety.dangerous(MAP, 1000, 1000, 400, Race.ELYOS, 54), "A monster far below the bot's level does not");
		check(!PlayerBotCampSafety.dangerous(MAP, 1000, 1000, 430, Race.ELYOS, 47), "A floor 30 m above or below is a different place");
		check(!PlayerBotCampSafety.dangerous(MAP, 1000, 1000, 400, Race.ASMODIANS, 47), "Threats are per faction");
		check(PlayerBotCampSafety.dangerous(MAP, 1062, 1000, 400, Race.ELYOS, 47) == false, "Grid neighbours are searched without false hits");
		PlayerBotCampSafety.threatsForTest(MAP, Race.ELYOS, List.of(new PlayerBotCampSafety.Threat(1060, 1000, 400, 50, 25)));
		check(PlayerBotCampSafety.dangerous(MAP, 1050, 1000, 400, Race.ELYOS, 47), "A threat across a 64 m grid border is found");
	}

	private static void standoff() {
		PlayerBotCampSafety.reset();
		PlayerBotCampSafety.threatsForTest(MAP, Race.ELYOS, List.of(new PlayerBotCampSafety.Threat(1000, 1000, 400, 50, 25)));
		var all = new Flat(p -> true);
		var point = PlayerBotCampSafety.standoff(MAP, 1000, 1000, 400, Race.ELYOS, 47, all, 0);
		check(point != null, "A dangerous camp anchor gets a standoff landing");
		double d = Math.hypot(point.x() - 1000, point.y() - 1000);
		check(d >= 25 && d <= 46, "The standoff lands outside aggro range but near enough to hunt the camp: " + d);
		check(!PlayerBotCampSafety.dangerous(MAP, point.x(), point.y(), point.z(), Race.ELYOS, 47), "The standoff point itself is safe");
		check(PlayerBotCampSafety.standoff(MAP, 1000, 1000, 400, Race.ELYOS, 47, new Flat(p -> false), 0) == null, "Native landing guards still apply to standoff points");
		PlayerBotCampSafety.threatsForTest(MAP, Race.ELYOS, List.of(new PlayerBotCampSafety.Threat(1000, 1000, 400, 50, 60)));
		check(PlayerBotCampSafety.standoff(MAP, 1000, 1000, 400, Race.ELYOS, 47, all, 0) == null, "A camp surrounded by danger has no standoff and is skipped");
		PlayerBotCampSafety.threatsForTest(MAP, Race.ELYOS, List.of(new PlayerBotCampSafety.Threat(1000, 1000, 400, 50, 25)));
		var cliff = new PlayerBotCampSafety.Ground() {
			public float height(float x, float y, float sourceZ) {
				return x > 1000 ? sourceZ + 20 : sourceZ;
			}

			public boolean valid(float x, float y, float z) {
				return true;
			}
		};
		var low = PlayerBotCampSafety.standoff(MAP, 1000, 1000, 400, Race.ELYOS, 47, cliff, 0);
		check(low != null && low.x() <= 1000.01f, "A standoff never jumps onto a ledge more than 8 m above or below the camp");
		PlayerBotCampSafety.recordDeath(MAP, 1015, 1000, 47, true, 0);
		PlayerBotCampSafety.recordDeath(MAP, 1015, 1000, 47, true, 1);
		var avoiding = PlayerBotCampSafety.standoff(MAP, 1000, 1000, 400, Race.ELYOS, 47, all, 2);
		check(avoiding != null && !PlayerBotCampSafety.avoided(MAP, avoiding.x(), avoiding.y(), 47, 2), "A standoff also skips closed death cells");
	}

	private static void saveRetry() {
		check(PlayerBotPersistence.lockRace(new SQLException("Deadlock found when trying to get lock", "40001", 1213)), "A deadlock is a lock race");
		check(PlayerBotPersistence.lockRace(new SQLException("Lock wait timeout exceeded", "HY000", 1205)), "A lock wait timeout is a lock race");
		check(PlayerBotPersistence.lockRace(new java.sql.SQLTransactionRollbackException("rollback")), "A transaction rollback is a lock race");
		check(PlayerBotPersistence.lockRace(new SQLException("Companion inventory transaction failed")), "The companion batch failure, whose driver error was logged and swallowed, is retried");
		check(PlayerBotPersistence.lockRace(new SQLException("outer", new SQLException("inner", "40001"))), "A lock race nested as a cause is found");
		check(!PlayerBotPersistence.lockRace(new SQLException("Companion attempted to save shared inventory")), "A custody refusal is never retried");
		check(!PlayerBotPersistence.lockRace(new SQLException("Companion item custody mismatch: 5")), "A custody mismatch is never retried");
	}

	private static void metrics() {
		PlayerBotActivityMetrics.reset();
		long now = System.currentTimeMillis();
		PlayerBotActivityMetrics.humansForTest(List.of(new float[] { 0, 0, 0 }, new float[] { 2000, 0, 0 }), MAP, now);
		check(PlayerBotActivityMetrics.band(MAP, 1, 100, 0, 0, now) == PlayerBotActivityMetrics.Band.VISIBLE, "Under 150 m of a player is the visible band");
		check(PlayerBotActivityMetrics.band(MAP, 1, 400, 0, 0, now) == PlayerBotActivityMetrics.Band.NEAR, "150-600 m is the near band");
		check(PlayerBotActivityMetrics.band(MAP, 1, 1000, 0, 0, now) == PlayerBotActivityMetrics.Band.MAP, "Further away on the same map is the map band");
		check(PlayerBotActivityMetrics.band(MAP, 1, 1900, 0, 0, now) == PlayerBotActivityMetrics.Band.VISIBLE, "The nearest player decides the band");
		check(PlayerBotActivityMetrics.band(MAP + 1, 1, 0, 0, 0, now) == PlayerBotActivityMetrics.Band.DORMANT, "A map without players is the dormant band");
		check(PlayerBotActivityMetrics.band(MAP, 2, 0, 0, 0, now) == PlayerBotActivityMetrics.Band.DORMANT, "Another instance of the map is not the player's");
		for (int i = 0; i < 19; i++)
			PlayerBotActivityMetrics.record(PlayerBotActivityMetrics.Band.VISIBLE, 100_000, 200_000);
		PlayerBotActivityMetrics.record(PlayerBotActivityMetrics.Band.VISIBLE, 50_000_000, 60_000_000);
		String line = PlayerBotActivityMetrics.report(List.of(), false);
		check(line.contains("<150m=20 (2595/128, 3190)"), "Window shows count, average CPU, p95 bucket and average wall: " + line);
		check(line.equals(PlayerBotActivityMetrics.report(List.of(), true)), "A UI read does not drain the window");
		check(PlayerBotActivityMetrics.report(List.of(), true).contains("avg wall us) <150m=0,"), "The periodic report drains the window");
		check(line.contains("outcomes awake level=0, turn-in=0, kill=0, gather=0, craft=0, sale=0; dormant level=0"), "Outcomes are split awake versus dormant: " + line);
		check(line.contains("moves in: presence=0, focus=0, left player maps=0"), "Moves in and departures are reported");
	}

	private static void parkedAttacks() {
		PlayerBotCampSafety.reset();
		long attacks = PlayerBotCampSafety.PARKED_ATTACKS.get(), deaths = PlayerBotCampSafety.PARKED_DEATHS.get();
		check(!PlayerBotCampSafety.takeParkedAttack(7), "No attack, no forced move");
		PlayerBotCampSafety.attackedWhileParked(7);
		PlayerBotCampSafety.attackedWhileParked(7);
		check(PlayerBotCampSafety.PARKED_ATTACKS.get() == attacks + 1, "One fight counts once, however many ticks it settles");
		check(PlayerBotCampSafety.takeParkedAttack(7), "A survivor moves once before sleeping");
		check(!PlayerBotCampSafety.takeParkedAttack(7), "...and only once");
		PlayerBotCampSafety.attackedWhileParked(8);
		PlayerBotCampSafety.died(8, true);
		check(PlayerBotCampSafety.PARKED_DEATHS.get() == deaths + 1 && !PlayerBotCampSafety.takeParkedAttack(8), "A death after a parked attack is counted and clears the mark");
		PlayerBotCampSafety.died(9, true);
		check(PlayerBotCampSafety.PARKED_DEATHS.get() == deaths + 1, "A dormant-map death without a parked attack stays in the other share");
		PlayerBotCampSafety.attackedWhileParked(10);
		PlayerBotCampSafety.died(10, false);
		check(PlayerBotCampSafety.PARKED_DEATHS.get() == deaths + 1 && !PlayerBotCampSafety.takeParkedAttack(10), "A death on a map a human reached clears the mark without counting");
		PlayerBotCampSafety.attackedWhileParked(11);
		PlayerBotCampSafety.closed(11);
		check(!PlayerBotCampSafety.takeParkedAttack(11), "A closed bot leaves no mark behind");
		check(PlayerBotCampSafety.stats(System.currentTimeMillis()).contains("attacked while parked="), "The report shows parked attacks");
	}

	private static void wiring(Path root) throws Exception {
		var src = root.resolve("src/com/aionemu/gameserver/services/playerbot");
		var service = Files.readString(src.resolve("PlayerBotService.java"));
		var manager = Files.readString(src.resolve("PlayerBotPopulationManager.java"));
		var placement = Files.readString(src.resolve("PlayerBotWorldPlacement.java"));
		var session = Files.readString(src.resolve("PlayerBotSession.java"));
		var dao = Files.readString(root.resolve("src/com/aionemu/gameserver/dao/InventoryDAO.java"));
		check(service.contains("if(PlayerBotPopulationConfig.MOVE_IN_PLACE)reviveInPlace(work.session());") && service.contains("else populationManager.revived(work.session().bot());"),
			"Scheduled revival revives and relocates the live bot; the reload path stays behind the switch");
		int placed = manager.indexOf(" void placed(Player bot)");
		check(placed > 0 && manager.indexOf("MOVE_IN_PLACE", placed) < manager.indexOf("\"teleport\"", placed), "A move refills supplies in place before any teleport rebuild intent could be queued");
		check(manager.contains("PlayerBotCampSafety.recordDeath(bot.getWorldId(),bot.getX(),bot.getY(),bot.getLevel(),dormant,now);")
			&& manager.contains("boolean dormant=!PlayerBotWorldActivity.occupied(bot);"), "Deaths are recorded where the body lies");
		check(manager.contains("PlayerBotCampSafety.died(bot.getObjectId(),dormant);"), "Each death settles its parked-attack mark");
		check(session.contains("if(autonomous()){PlayerBotCampSafety.attackedWhileParked(bot.getObjectId());PlayerBotCampSafety.sampleParkedAttack(bot);}")
			&& session.indexOf("attackedWhileParked") < session.indexOf("worldDormant=false;status=\"settling native combat before map dormancy\""), "A parked bot that wakes to fight marks its spot");
		check(session.contains("boolean attacked=autonomous() && !bot.isDead() && PlayerBotCampSafety.takeParkedAttack(bot.getObjectId());")
			&& session.contains("(attacked || PlayerBotCampSafety.dormantCheckDue(bot,System.currentTimeMillis())") && session.contains("(hubs?!PlayerBotWorldPlacement.atHub(bot):PlayerBotCampSafety.dangerNow(bot))"),
			"A survivor of a parked attack moves before sleeping, without waiting for the recheck timer; a dead bot keeps its mark for the death count");
		var metadata = Files.readString(src.resolve("PlayerBotMetadata.java"));
		check(metadata.contains("\"revive\",\"teleport\"") && manager.contains("rebuildLabel(reason)"), "Saved revive/teleport intents stay valid and still run the old path once");
		check(placement.contains("PlayerBotCampSafety.avoided(p.map(),p.x(),p.y(),level,now)") && placement.contains("!PlayerBotCampSafety.unsafe(p.map(),point.x(),point.y(),point.z(),race,level,now)"),
			"Both camp scans skip closed and dangerous cells");
		check(placement.contains("\"validated native camp standoff\""), "Dangerous anchors land at a standoff");
		check(session.indexOf("PlayerBotCampSafety.dangerNow(bot)") > 0 && session.indexOf("PlayerBotCampSafety.dangerNow(bot)") < session.indexOf("worldDormant=true;status=\"dormant: no human player on this map/instance\";"),
			"A bot about to sleep leaves dangerous ground first");
		check(dao.contains("lockOrder.sort(java.util.Comparator.comparingInt(Item::getObjectId));"), "Companion item rows are locked in one global order");
		check(service.contains("tick {}ms; dismiss {}ms; blocked {}ms; waited {}ms; GC {}ms"), "The slow-decision warning names what the bot waited on");
	}
}
