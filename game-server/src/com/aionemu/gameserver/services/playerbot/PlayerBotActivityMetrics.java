package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;

/**
 * Phase 0 of the activity plan (project-notes/playerbots-activity-lod-plan-20261107.md): where awake world bots are
 * relative to real players, what each distance band costs per decision, and what bots achieve awake versus dormant.
 * Read-only measurement; nothing here changes behaviour.
 */
final class PlayerBotActivityMetrics {
	enum Band {
		VISIBLE("<150m"), NEAR("150-600m"), MAP("same map >600m"), DORMANT("no player on map");

		final String label;

		Band(String label) {
			this.label = label;
		}
	}

	enum Outcome { LEVEL, TURN_IN, KILL, GATHER, CRAFT, SALE }

	static final int VISIBLE_M = 150, NEAR_M = 600;
	/** log2 microsecond buckets: bucket i holds [2^i, 2^(i+1)) µs. */
	private static final int BUCKETS = 32;

	private record Human(int map, int instance, float x, float y, float z) {}
	private static volatile List<Human> humans = List.of();
	private static volatile long humansAt;

	private static final class Window {
		final LongAdder[] count = adders(), cpu = adders(), wall = adders();
		final LongAdder[][] histogram = new LongAdder[Band.values().length][BUCKETS];
		{
			for (var row : histogram)
				for (int i = 0; i < BUCKETS; i++)
					row[i] = new LongAdder();
		}
	}

	private static LongAdder[] adders() {
		var a = new LongAdder[Band.values().length];
		for (int i = 0; i < a.length; i++)
			a[i] = new LongAdder();
		return a;
	}

	private static volatile Window window = new Window();
	private static final LongAdder[][] OUTCOMES = new LongAdder[2][Outcome.values().length];
	static {
		for (var row : OUTCOMES)
			for (int i = 0; i < row.length; i++)
				row[i] = new LongAdder();
	}
	static final LongAdder PRESENCE_IN = new LongAdder(), FOCUS_MOVES = new LongAdder(), LEFT_PLAYER_MAPS = new LongAdder();
	private record Seen(int map, boolean occupied) {}
	private static final Map<Integer, Seen> LAST_SEEN = new ConcurrentHashMap<>();

	/** Real players in the world, refreshed at most every 2 s so a decision never walks the whole player list. */
	private static List<Human> humans(long now) {
		if (now - humansAt < 2000)
			return humans;
		var next = new ArrayList<Human>();
		for (var p : World.getInstance().getAllPlayers())
			if (!p.isPlayerBot() && p.isSpawned())
				next.add(new Human(p.getWorldId(), p.getInstanceId(), p.getX(), p.getY(), p.getZ()));
		humans = List.copyOf(next);
		humansAt = now;
		return humans;
	}

	static Band band(Player bot, long now) {
		return band(bot.getWorldId(), bot.getInstanceId(), bot.getX(), bot.getY(), bot.getZ(), now);
	}

	static Band band(int map, int instance, float x, float y, float z, long now) {
		double best = Double.MAX_VALUE;
		boolean sameMap = false;
		for (var h : humans(now))
			if (h.map() == map && h.instance() == instance) {
				sameMap = true;
				best = Math.min(best, PositionUtil.getDistance(x, y, z, h.x(), h.y(), h.z()));
			}
		return !sameMap ? Band.DORMANT : best < VISIBLE_M ? Band.VISIBLE : best < NEAR_M ? Band.NEAR : Band.MAP;
	}

	static void decision(Player bot, long cpuNanos, long wallNanos) {
		try {
			if (bot.isWorldBot() && bot.getPosition() != null)
				record(band(bot, System.currentTimeMillis()), cpuNanos, wallNanos);
		} catch (RuntimeException ignored) {
			// A measurement failure must not fail the decision runner.
		}
	}

	static void record(Band band, long cpuNanos, long wallNanos) {
		var w = window;
		int b = band.ordinal();
		w.count[b].increment();
		w.wall[b].add(Math.max(0, wallNanos));
		if (cpuNanos >= 0) {
			w.cpu[b].add(cpuNanos);
			long micros = Math.max(1, cpuNanos / 1000);
			w.histogram[b][Math.min(BUCKETS - 1, 63 - Long.numberOfLeadingZeros(micros))].increment();
		}
	}

	static void outcome(Player bot, Outcome kind, long amount) {
		// Measurement must never break the outcome it counts: an unpositioned or half-built character counts as dormant.
		if (bot == null || amount <= 0)
			return;
		try {
			if (!bot.isWorldBot())
				return;
			OUTCOMES[bot.getPosition() != null && PlayerBotWorldActivity.occupied(bot) ? 0 : 1][kind.ordinal()].add(amount);
		} catch (RuntimeException e) {
			OUTCOMES[1][kind.ordinal()].add(amount);
		}
	}

	static void backgroundOutcome(Outcome kind) {
		OUTCOMES[1][kind.ordinal()].increment();
	}

	private static long p95(LongAdder[] histogram) {
		long total = 0;
		for (var a : histogram)
			total += a.sum();
		if (total == 0)
			return 0;
		long seen = 0, target = (long) Math.ceil(total * 0.95);
		for (int i = 0; i < histogram.length; i++) {
			seen += histogram[i].sum();
			if (seen >= target)
				return 1L << (i + 1); // upper bound of the bucket
		}
		return 1L << BUCKETS;
	}

	/** One report line. Counts bots per band now, closes the 30 s decision window, and tracks departures from player maps. */
	static String report(Collection<PlayerBotSession> active, boolean drain) {
		long now = System.currentTimeMillis();
		int[] bots = new int[Band.values().length];
		for (var s : active) {
			var bot = s.bot();
			if (!s.autonomous() || bot.getPosition() == null || bot.isDead())
				continue;
			var band = band(bot, now);
			bots[band.ordinal()]++;
			if (!drain)
				continue;
			var previous = LAST_SEEN.put(bot.getObjectId(), new Seen(bot.getWorldId(), band != Band.DORMANT));
			if (previous != null && previous.occupied() && previous.map() != bot.getWorldId())
				LEFT_PLAYER_MAPS.increment();
		}
		if (LAST_SEEN.size() > 4 * Math.max(1000, active.size()))
			LAST_SEEN.clear();
		var w = window;
		if (drain)
			window = new Window();
		var text = new StringBuilder("Activity bands: bots");
		for (var b : Band.values())
			text.append(b == Band.VISIBLE ? " " : ", ").append(b.label).append('=').append(bots[b.ordinal()]);
		text.append("; decisions last window (count, avg/p95 CPU us, avg wall us)");
		for (var b : Band.values()) {
			int i = b.ordinal();
			long n = w.count[i].sum();
			text.append(b == Band.VISIBLE ? " " : ", ").append(b.label).append('=').append(n);
			if (n > 0)
				text.append(" (").append(w.cpu[i].sum() / n / 1000).append('/').append(p95(w.histogram[i])).append(", ").append(w.wall[i].sum() / n / 1000).append(')');
		}
		text.append("; moves in: presence=").append(PRESENCE_IN.sum()).append(", focus=").append(FOCUS_MOVES.sum())
			.append(", left player maps=").append(LEFT_PLAYER_MAPS.sum());
		for (int awake = 0; awake < 2; awake++) {
			text.append(awake == 0 ? "; outcomes awake " : "; dormant ");
			var row = OUTCOMES[awake];
			for (var o : Outcome.values())
				text.append(o == Outcome.LEVEL ? "" : ", ").append(o.name().toLowerCase(Locale.ROOT).replace('_', '-')).append('=').append(row[o.ordinal()].sum());
		}
		return text.toString();
	}

	/** Test hook. */
	static void reset() {
		window = new Window();
		for (var row : OUTCOMES)
			for (var a : row)
				a.reset();
		PRESENCE_IN.reset();
		FOCUS_MOVES.reset();
		LEFT_PLAYER_MAPS.reset();
		LAST_SEEN.clear();
		humans = List.of();
		humansAt = 0;
	}

	/** Test hook: a fixed set of real-player positions. */
	static void humansForTest(List<float[]> positions, int map, long now) {
		var next = new ArrayList<Human>();
		for (var p : positions)
			next.add(new Human(map, 1, p[0], p[1], p[2]));
		humans = List.copyOf(next);
		humansAt = now;
	}

	private PlayerBotActivityMetrics() {}
}
