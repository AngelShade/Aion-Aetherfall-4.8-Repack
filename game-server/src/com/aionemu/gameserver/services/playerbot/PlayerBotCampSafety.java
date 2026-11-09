package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotPopulationConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;

/**
 * Where a world bot may land or sleep without being killed on arrival.
 * <p>
 * Native grind camps are monster spawn points, so a bot teleported onto one lands inside the pack.
 * Two rules keep landings and dormant parking alive:
 * <ul>
 * <li><b>Danger:</b> no aggressive native spawn whose level is at least the bot's level minus
 * {@code camp_safety.level_below} (elite and stronger count {@link #ELITE_LEVELS} higher) within its
 * aggro range plus {@code camp_safety.margin} metres. Built once per map and faction from static spawns.</li>
 * <li><b>Death memory:</b> {@code camp_deaths.limit} deaths in one 20 m cell and 5-level band within
 * {@code camp_deaths.window_seconds} close that cell to the band for {@code camp_deaths.avoid_seconds}.
 * RAM only; a restart starts clean.</li>
 * </ul>
 */
final class PlayerBotCampSafety {
	static final int CELL = 20, BAND = 5, ELITE_LEVELS = 5, GRID = 64;
	/** Standoff rings around a dangerous camp anchor: far enough to leave aggro, close enough to hunt it. */
	static final int[] RINGS = { 15, 25, 35, 45 };

	record Threat(float x, float y, float z, int level, float radius) {}
	private record Index(Map<Long, List<Threat>> grid, int threats) {}
	private record Cell(int map, int x, int y, int band) {}
	private static final class Deaths {
		final ArrayDeque<Long> times = new ArrayDeque<>();
		long avoidUntil;
	}

	private static final Map<String, Index> INDEX = new ConcurrentHashMap<>();
	private static final Map<Cell, Deaths> DEATHS = new ConcurrentHashMap<>();
	static final AtomicLong DEATHS_AWAKE = new AtomicLong(), DEATHS_DORMANT = new AtomicLong(), CELLS_CLOSED = new AtomicLong(),
		CELL_SKIPS = new AtomicLong(), DANGER_SKIPS = new AtomicLong(), STANDOFFS = new AtomicLong(), DORMANT_MOVES = new AtomicLong(), HUB_PARKS = new AtomicLong(), PARKED_SAMPLES = new AtomicLong(),
		DORMANT_NO_SPOT = new AtomicLong(), REVIVED_IN_PLACE = new AtomicLong(), MOVED_IN_PLACE = new AtomicLong(),
		PARKED_ATTACKS = new AtomicLong(), PARKED_DEATHS = new AtomicLong();
	private static final Map<Integer, Long> NEXT_DORMANT_CHECK = new ConcurrentHashMap<>();
	/** Parked bots that monsters attacked since they last went to sleep. */
	private static final Set<Integer> PARKED_FIGHT = ConcurrentHashMap.newKeySet();
	private static final long STARTED = System.currentTimeMillis();

	static boolean enabled() {
		return PlayerBotPopulationConfig.CAMP_SAFETY;
	}

	// ---------------------------------------------------------------- death memory

	private static Cell cell(int map, float x, float y, int level) {
		return new Cell(map, Math.floorDiv((int) Math.floor(x), CELL), Math.floorDiv((int) Math.floor(y), CELL), Math.max(0, level - 1) / BAND);
	}

	/** Records one death. Returns true when this death closed the cell for the bot's band. */
	static boolean recordDeath(int map, float x, float y, int level, boolean dormant, long now) {
		(dormant ? DEATHS_DORMANT : DEATHS_AWAKE).incrementAndGet();
		if (!enabled() || PlayerBotPopulationConfig.CAMP_DEATH_LIMIT <= 0)
			return false;
		var deaths = DEATHS.computeIfAbsent(cell(map, x, y, level), k -> new Deaths());
		synchronized (deaths) {
			long window = 1000L * PlayerBotPopulationConfig.CAMP_DEATH_WINDOW;
			while (!deaths.times.isEmpty() && now - deaths.times.peekFirst() > window)
				deaths.times.pollFirst();
			deaths.times.addLast(now);
			if (deaths.times.size() >= PlayerBotPopulationConfig.CAMP_DEATH_LIMIT && deaths.avoidUntil <= now) {
				deaths.avoidUntil = now + 1000L * PlayerBotPopulationConfig.CAMP_DEATH_AVOID;
				deaths.times.clear();
				CELLS_CLOSED.incrementAndGet();
				return true;
			}
		}
		if (DEATHS.size() > 50_000)
			DEATHS.entrySet().removeIf(e -> e.getValue().avoidUntil <= now && e.getValue().times.isEmpty());
		return false;
	}

	/** A closed cell is skipped for that level band, including the 8 neighbours a 20 m landing shift can reach. */
	static boolean avoided(int map, float x, float y, int level, long now) {
		if (!enabled() || DEATHS.isEmpty())
			return false;
		var centre = cell(map, x, y, level);
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++) {
				var deaths = DEATHS.get(new Cell(map, centre.x() + dx, centre.y() + dy, centre.band()));
				if (deaths != null && deaths.avoidUntil > now) {
					// Only the exact cell is closed; neighbours count only when the point is within one cell of its centre.
					if (dx != 0 || dy != 0) {
						float cx = (centre.x() + dx) * CELL + CELL / 2f, cy = (centre.y() + dy) * CELL + CELL / 2f;
						if (Math.hypot(cx - x, cy - y) > CELL)
							continue;
					}
					CELL_SKIPS.incrementAndGet();
					return true;
				}
			}
		return false;
	}

	// ---------------------------------------------------------------- static danger

	static boolean aggressiveTo(NpcTemplate npc, Race race) {
		if (npc == null || npc.getTribe() == null || npc.getAggroRange() <= 0)
			return false;
		TribeClass pc = race == Race.ELYOS ? TribeClass.PC : TribeClass.PC_DARK;
		try {
			if (DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(npc.getTribe(), pc))
				return true;
			var base = DataManager.TRIBE_RELATIONS_DATA.getBaseTribe(npc.getTribe());
			// TribeRelationService's guard rules, which tribe data alone does not carry.
			return base == TribeClass.GUARD_DRAGON || race == Race.ELYOS && base == TribeClass.GUARD_DARK || race == Race.ASMODIANS && base == TribeClass.GUARD;
		} catch (RuntimeException e) {
			return false;
		}
	}

	static int effectiveLevel(NpcTemplate npc) {
		var rating = npc.getRating();
		return npc.getLevel() + (rating == NpcRating.ELITE || rating == NpcRating.HERO || rating == NpcRating.LEGENDARY ? ELITE_LEVELS : 0);
	}

	private static long key(int gx, int gy) {
		return ((long) gx << 32) ^ (gy & 0xffffffffL);
	}

	private static Index index(int map, Race race) {
		return INDEX.computeIfAbsent(race + ":" + map, k -> build(map, race));
	}

	private static Index build(int map, Race race) {
		var grid = new HashMap<Long, List<Threat>>();
		int count = 0;
		var groups = DataManager.SPAWNS_DATA == null ? null : DataManager.SPAWNS_DATA.getSpawnsByWorldId(map);
		if (groups != null)
			for (var group : groups) {
				if (group.isTemporarySpawn() || group.getHandlerType() != null)
					continue;
				var npc = DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
				if (!aggressiveTo(npc, race))
					continue;
				float radius = npc.getAggroRange() + Math.max(0, PlayerBotPopulationConfig.CAMP_SAFETY_MARGIN);
				int level = effectiveLevel(npc);
				for (var spawn : group.getSpawnTemplates()) {
					var threat = new Threat(spawn.getX(), spawn.getY(), spawn.getZ(), level, radius);
					grid.computeIfAbsent(key(Math.floorDiv((int) spawn.getX(), GRID), Math.floorDiv((int) spawn.getY(), GRID)), g -> new ArrayList<>()).add(threat);
					count++;
				}
			}
		grid.replaceAll((k, v) -> List.copyOf(v));
		return new Index(Map.copyOf(grid), count);
	}

	/** True when a native aggressive spawn strong enough to matter would aggro a bot standing here. */
	static boolean dangerous(int map, float x, float y, float z, Race race, int level) {
		if (!enabled())
			return false;
		var index = index(map, race);
		int floor = level - Math.max(0, PlayerBotPopulationConfig.CAMP_SAFETY_LEVEL_BELOW);
		int gx = Math.floorDiv((int) x, GRID), gy = Math.floorDiv((int) y, GRID);
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++) {
				var threats = index.grid().get(key(gx + dx, gy + dy));
				if (threats != null)
					for (var t : threats)
						if (t.level() >= floor && Math.abs(t.z() - z) < 20 && Math.hypot(t.x() - x, t.y() - y) < t.radius())
							return true;
			}
		return false;
	}

	/** Live check at the bot's own position, which also catches walkers and monsters the static data places elsewhere. */
	static boolean dangerNow(Player bot) {
		if (!enabled() || bot.getKnownList() == null)
			return false;
		int floor = bot.getLevel() - Math.max(0, PlayerBotPopulationConfig.CAMP_SAFETY_LEVEL_BELOW);
		boolean[] found = { false };
		bot.getKnownList().forEachNpc(npc -> {
			if (found[0] || npc.isDead() || !npc.isSpawned() || npc.getObjectTemplate() == null)
				return;
			if (effectiveLevel(npc.getObjectTemplate()) < floor || !aggressiveTo(npc.getObjectTemplate(), bot.getRace()))
				return;
			double radius = npc.getAggroRange() + Math.max(0, PlayerBotPopulationConfig.CAMP_SAFETY_MARGIN);
			if (Math.abs(npc.getZ() - bot.getZ()) < 20 && Math.hypot(npc.getX() - bot.getX(), npc.getY() - bot.getY()) < radius)
				found[0] = true;
		});
		return found[0] || dangerous(bot.getWorldId(), bot.getX(), bot.getY(), bot.getZ(), bot.getRace(), bot.getLevel());
	}

	static boolean unsafe(int map, float x, float y, float z, Race race, int level, long now) {
		if (avoided(map, x, y, level, now))
			return true;
		if (dangerous(map, x, y, z, race, level)) {
			DANGER_SKIPS.incrementAndGet();
			return true;
		}
		return false;
	}

	interface Ground {
		/** Native floor height near sourceZ, or NaN. */
		float height(float x, float y, float sourceZ);

		/** Every native landing guard the caller applies to an ordinary landing point. */
		boolean valid(float x, float y, float z);
	}

	/**
	 * A landing near a camp anchor outside every aggro radius that matters. Tries rings of {@link #RINGS} metres
	 * in 8 directions and keeps the first point the caller's own landing guards accept. Null when none exists.
	 */
	static PlayerBotNavigation.Point standoff(int map, float x, float y, float z, Race race, int level, Ground ground, long now) {
		for (int ring : RINGS)
			for (int direction = 0; direction < 8; direction++) {
				double angle = direction * Math.PI / 4 + (ring / 15 % 2) * Math.PI / 8;
				float px = x + ring * (float) Math.cos(angle), py = y + ring * (float) Math.sin(angle);
				float pz = ground.height(px, py, z);
				if (!Float.isFinite(pz) || Math.abs(pz - z) > 8 || unsafe(map, px, py, pz, race, level, now) || !ground.valid(px, py, pz))
					continue;
				STANDOFFS.incrementAndGet();
				return new PlayerBotNavigation.Point(px, py, pz);
			}
		return null;
	}

	/** Dormant parking is checked at most this often per bot, so a crowded map cannot loop teleports. */
	static final long DORMANT_RECHECK_MS = 60 * 1000L;

	static boolean dormantCheckDue(Player bot, long now) {
		if (!enabled())
			return false;
		var next = NEXT_DORMANT_CHECK.get(bot.getObjectId());
		if (next != null && now < next)
			return false;
		NEXT_DORMANT_CHECK.put(bot.getObjectId(), now + DORMANT_RECHECK_MS);
		if (NEXT_DORMANT_CHECK.size() > 20_000)
			NEXT_DORMANT_CHECK.entrySet().removeIf(e -> e.getValue() < now);
		return true;
	}

	/** A parked bot woke to fight: something reached its spot, so the spot is unsafe whatever the spawn data says. */
	/** Diagnosis: the first parked attacks of a run, with the attacker, so the next run shows what still reaches sleepers. */
	static void sampleParkedAttack(Player bot) {
		if (!enabled() || PARKED_SAMPLES.get() >= 40)
			return;
		var top = bot.getAggroList().stream().filter(a -> a.getHate() > 0 && a.getAttacker() instanceof com.aionemu.gameserver.model.gameobjects.Npc && !a.getAttacker().isDead())
			.max(java.util.Comparator.comparingInt(a -> a.getHate())).map(a -> a.getAttacker()).orElse(null);
		if (!(top instanceof com.aionemu.gameserver.model.gameobjects.Npc npc) || PARKED_SAMPLES.incrementAndGet() > 40)
			return;
		org.slf4j.LoggerFactory.getLogger(PlayerBotCampSafety.class).info("Parked bot attacked: {} lv{} at map {} ({}, {}) {}% HP by {} ({}) lv{} {} aggro {} m at {} m; at own service point={}",
			bot.getName(), bot.getLevel(), bot.getWorldId(), (int) bot.getX(), (int) bot.getY(), (int) PlayerBotDefense.health(bot), npc.getName(), npc.getNpcId(), npc.getLevel(),
			npc.getObjectTemplate().getRating(), (int) npc.getAggroRange(), (int) com.aionemu.gameserver.utils.PositionUtil.getDistance(bot, npc), PlayerBotWorldPlacement.atHub(bot));
	}

	static void attackedWhileParked(int bot) {
		if (enabled() && PARKED_FIGHT.add(bot))
			PARKED_ATTACKS.incrementAndGet();
	}

	/** True once after a parked attack the bot survived: it moves to a safe camp before sleeping again. */
	static boolean takeParkedAttack(int bot) {
		return PARKED_FIGHT.remove(bot);
	}

	/** Splits dormant-map deaths: those after a parked attack, and the rest (bots still active when the map emptied). */
	static void died(int bot, boolean dormant) {
		if (PARKED_FIGHT.remove(bot) && dormant)
			PARKED_DEATHS.incrementAndGet();
	}

	static void closed(int bot) {
		NEXT_DORMANT_CHECK.remove(bot);
		PARKED_FIGHT.remove(bot);
	}

	static String stats(long now) {
		long minutes = Math.max(1, (now - STARTED) / 60_000L);
		long open = DEATHS.values().stream().filter(d -> d.avoidUntil > now).count();
		return "Playerbots: Camp safety: enabled=" + enabled() + ", deaths awake=" + DEATHS_AWAKE.get() + ", dormant=" + DEATHS_DORMANT.get()
			+ " (per hour " + String.format(Locale.ROOT, "%.1f/%.1f", DEATHS_AWAKE.get() * 60.0 / minutes, DEATHS_DORMANT.get() * 60.0 / minutes) + ")"
			+ ", cells closed=" + CELLS_CLOSED.get() + " (closed now " + open + "), closed-cell skips=" + CELL_SKIPS.get()
			+ ", danger skips=" + DANGER_SKIPS.get() + ", standoff landings=" + STANDOFFS.get() + ", dormant safe moves=" + DORMANT_MOVES.get() + " (to own service points " + HUB_PARKS.get() + ")"
			+ ", dormant no safe spot=" + DORMANT_NO_SPOT.get() + ", attacked while parked=" + PARKED_ATTACKS.get()
			+ " (deaths after " + PARKED_DEATHS.get() + ", other dormant-map deaths " + Math.max(0, DEATHS_DORMANT.get() - PARKED_DEATHS.get()) + ")"
			+ ", revived in place=" + REVIVED_IN_PLACE.get() + ", moved in place=" + MOVED_IN_PLACE.get()
			+ ", save retries=" + PlayerBotPersistence.SAVE_RETRIES.get() + " (recovered " + PlayerBotPersistence.SAVE_RETRIES_RECOVERED.get() + ")"
			+ ", danger index maps=" + INDEX.size();
	}

	/** Test hook: a fixed set of threats for one map and faction. */
	static void threatsForTest(int map, Race race, List<Threat> threats) {
		var grid = new HashMap<Long, List<Threat>>();
		for (var t : threats)
			grid.computeIfAbsent(key(Math.floorDiv((int) t.x(), GRID), Math.floorDiv((int) t.y(), GRID)), g -> new ArrayList<>()).add(t);
		INDEX.put(race + ":" + map, new Index(Map.copyOf(grid), threats.size()));
	}

	/** Test hook: forget all RAM state. */
	static void reset() {
		INDEX.clear();
		DEATHS.clear();
		NEXT_DORMANT_CHECK.clear();
		PARKED_FIGHT.clear();
	}

	private PlayerBotCampSafety() {}
}
