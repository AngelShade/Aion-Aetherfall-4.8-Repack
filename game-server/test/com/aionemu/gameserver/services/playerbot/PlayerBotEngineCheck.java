package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;
import com.aionemu.gameserver.model.stats.container.StatEnum;

/** Offline deterministic checks: no live characters, network clients or database writes. */
public final class PlayerBotEngineCheck {
	private static int checks;
	private static void check(boolean success, String message) {
		checks++;
		if (!success) throw new AssertionError("FAIL: " + message);
	}
	private record TestAction(String name, BooleanSupplier useful, BooleanSupplier possible, BooleanSupplier execution,
		List<Action> prerequisites, List<Action> alternatives) implements Action {
		@Override public boolean isUseful() { return useful.getAsBoolean(); }
		@Override public boolean isPossible() { return possible.getAsBoolean(); }
		@Override public boolean execute() { return execution.getAsBoolean(); }
	}
	private static Action action(String name, List<String> trace) {
		return new TestAction(name, () -> true, () -> true, () -> { trace.add(name); return true; }, List.of(), List.of());
	}
	private static Trigger trigger(Action action, double relevance) { return new Trigger(() -> true, action, () -> relevance); }
	private static boolean tick(PlayerBotEngine engine, Trigger... triggers) {
		return engine.tick(State.COMBAT, List.of(new Strategy("check", List.of(triggers))), List.of(), 64);
	}
	public static void main(String[] args) throws Exception {
		List<String> trace = new ArrayList<>();
		PlayerBotEngine engine = new PlayerBotEngine();
		Action heal = action("heal", trace), damage = action("damage", trace), interrupt = action("interrupt", trace);
		check(tick(engine, trigger(damage, NORMAL), trigger(heal, healPriority(12, true)), trigger(interrupt, INTERRUPT)), "decision executes");
		check(trace.equals(List.of("heal")), "lethal healing outranks interruption and damage; one action per tick");
		trace.clear();
		check(tick(engine, trigger(damage, NORMAL), trigger(interrupt, INTERRUPT)), "interruption executes");
		check(trace.equals(List.of("interrupt")), "interrupt outranks normal damage");
		trace.clear();
		check(tick(engine, trigger(action("first", trace), 10), trigger(action("second", trace), 10)), "tied action executes");
		check(trace.equals(List.of("first")), "stable equal-priority ordering");
		trace.clear();
		check(engine.tick(State.COMBAT, List.of(new Strategy("policy", List.of(trigger(damage, 100), trigger(heal, 20)))),
			List.of(a -> a == damage ? 0 : 1), 64), "suppressed action falls through");
		check(trace.equals(List.of("heal")), "multiplier suppresses damage");
		trace.clear();
		boolean[] reached = { false };
		Action reach = new TestAction("reach", () -> !reached[0], () -> true,
			() -> { reached[0] = true; trace.add("reach"); return true; }, List.of(), List.of());
		Action cast = new TestAction("cast", () -> true, () -> true,
			() -> { trace.add("cast"); return true; }, List.of(reach), List.of());
		check(tick(engine, trigger(cast, EMERGENCY)), "movement prerequisite executes");
		check(trace.equals(List.of("reach")), "movement precedes cast");
		check(tick(engine, trigger(cast, EMERGENCY)), "cast after prerequisite");
		check(trace.equals(List.of("reach", "cast")), "fresh snapshot permits completed prerequisite");
		trace.clear();
		Action blocked = new TestAction("blocked reach", () -> true, () -> false, () -> { throw new AssertionError("unreachable movement executed"); }, List.of(), List.of());
		Action unreachableCast = new TestAction("unreachable cast", () -> true, () -> true,
			() -> { throw new AssertionError("cast bypassed failed movement"); }, List.of(blocked), List.of());
		check(tick(engine, trigger(unreachableCast, 100), trigger(damage, 10)), "failed movement falls through");
		check(trace.equals(List.of("damage")), "a failed prerequisite cannot authorize its action");
		trace.clear();
		Action fallback = new TestAction("unavailable", () -> true, () -> false, () -> false, List.of(), List.of(heal));
		check(tick(engine, trigger(fallback, 100)), "alternative executes");
		check(trace.equals(List.of("heal")), "fallback chooses available alternative");
		trace.clear();
		check(!engine.tick(State.COMBAT, List.of(new Strategy("blocked alternative", List.of(trigger(fallback, 100)))),
			List.of(a -> a == heal ? 0 : 1), 8), "alternative respects policy suppression");
		Action cycle = new Action() {
			public String name() { return "cycle"; }
			public boolean isUseful() { return true; }
			public boolean isPossible() { return false; }
			public boolean execute() { throw new AssertionError(); }
			public List<Action> alternatives() { return List.of(this); }
		};
		check(!engine.tick(State.COMBAT, List.of(new Strategy("cycle", List.of(trigger(cycle, 10)))), List.of(), 3), "cyclic fallback terminates");
		check(engine.getAttempts() <= 3, "expansion obeys exact attempt budget");
		check(!engine.tick(State.DEAD, List.of(), List.of(), 10) && engine.getState() == State.DEAD && engine.getLastAction().equals("idle"), "death clears queued work");
		check(!tick(engine, trigger(damage, Double.NaN), trigger(heal, Double.POSITIVE_INFINITY), trigger(interrupt, -1)), "non-finite/negative priorities cannot execute");
		check(!engine.tick(State.NON_COMBAT, List.of(new Strategy("no budget", List.of(trigger(damage, 10)))), List.of(), 0) && engine.getAttempts() == 0, "zero budget executes nothing");
		check(healPriority(15, false) > healPriority(45, true) && healPriority(45, false) > healPriority(75, true), "health urgency ordering");
		check(healPriority(0, true) == 0 && healPriority(85, true) == 0 && healPriority(Double.NaN, true) == 0, "do not heal dead/full/invalid recipients");
		check(roleFor(PlayerClass.TEMPLAR) == Role.TANK && roleFor(PlayerClass.CLERIC) == Role.HEALER && roleFor(PlayerClass.BARD) == Role.SUPPORT, "class role defaults");
		for (var pc : PlayerClass.values()) check(roleFor(pc) != null, "role coverage " + pc);
		check(canRecruit(true, true, false, false, false, false, -10, 10), "eligible offline owned character");
		check(!canRecruit(false, true, false, false, false, false, 0, 10), "reject other account");
		check(!canRecruit(true, false, false, false, false, false, 0, 10), "reject other faction");
		check(!canRecruit(true, true, true, false, false, false, 0, 10), "reject online character");
		check(!canRecruit(true, true, false, true, false, false, 0, 10), "reject banned character");
		check(!canRecruit(true, true, false, false, true, false, 0, 10), "reject pending deletion");
		check(!canRecruit(true, true, false, false, false, true, 0, 10), "reject prisoner");
		check(!canRecruit(true, true, false, false, false, false, 11, 10), "respect level gap");
		check(canAttack(true, true, true, true, false, false, false, 30), "assist engaged PvE enemy");
		check(!canAttack(false, true, true, true, true, false, false, 10), "never attack player");
		check(!canAttack(true, true, true, false, false, false, false, 10), "never pull neutral engagement");
		check(!canAttack(true, true, true, true, true, true, false, 10), "protect crowd control");
		check(!canAttack(true, true, true, true, true, false, true, 10), "passive blocks attacks");
		check(!canAttack(true, true, true, true, true, false, false, 46), "owner leash");
		check(!canAttack(true, true, true, true, true, false, false, Double.NaN), "invalid distance rejected");
		leaseChecks();
		check(shouldRest(60, 80, false, false, false, 4), "injured companion rests near stationary owner");
		check(shouldRest(90, 90, true, false, false, 4), "resting hysteresis avoids repeated standing and sitting");
		check(!shouldRest(96, 96, true, false, false, 4), "stand after recovery");
		check(!shouldRest(20, 20, false, true, false, 4), "never sit during combat");
		check(!shouldRest(20, 20, false, false, true, 4), "follow moving owner instead of sitting");
		check(!shouldRest(20, 20, false, false, false, 9), "catch up before resting");
		check(!shouldRest(Double.NaN, 20, false, false, false, 4), "invalid health never triggers rest");
		var hazard = new PlayerBotTactics.Hazard(10, 20, 30, 5);
		var escape = PlayerBotTactics.escape(10, 20, 30, hazard, (byte) 0);
		check(Math.hypot(escape.x() - 10, escape.y() - 20) >= 7 && escape.z() == 30, "escape remains finite at hazard centre and clears its radius");
		var escapeEdge = PlayerBotTactics.escape(12, 20, 30, hazard, (byte) 60);
		check(escapeEdge.x() > 12 && escapeEdge.y() == 20, "escape moves away from an area hazard");
		check(PlayerBotTactics.healFit(100, 100, 1000, false) > PlayerBotTactics.healFit(100, 1000, 1000, false), "prefer useful healing over overhealing");
		check(PlayerBotTactics.healFit(100, 100, 0, true) > PlayerBotTactics.healFit(100, 100, 3000, true), "urgent healing favours a fast cast");
		check(PlayerBotTactics.healFit(100, Double.NaN, 0, true) == 0, "invalid heal estimates do not rank skills");
		check(!PlayerBotClassCombat.shouldBurst(0, 5, 10, 1000), "never waste an Assassin burst without runes");
		check(!PlayerBotClassCombat.shouldBurst(2, 5, 90, 8000), "build runes before bursting a healthy target");
		check(PlayerBotClassCombat.shouldBurst(5, 5, 90, 8000), "burst complete runes");
		check(PlayerBotClassCombat.shouldBurst(2, 5, 20, 8000), "burst early to finish a low-health target");
		check(PlayerBotClassCombat.shouldBurst(2, 5, 90, 1500), "use runes before expiration");
		pathChecks();
		preferenceChecks();
		var appearance = new PlayerAppearance(); appearance.setHair(4);
		var copied = appearance.copy(); copied.setHair(9);
		check(appearance.getHair() == 4 && copied.getHair() == 9, "generated appearance does not mutate owner");
		check(PlayerBotEquipment.weight(StatEnum.HEAL_BOOST, Role.HEALER, true) > PlayerBotEquipment.weight(StatEnum.HEAL_BOOST, Role.MELEE, false), "gear scoring values healing for healers");
		check(PlayerBotEquipment.weight(StatEnum.MAXHP, Role.TANK, false) > PlayerBotEquipment.weight(StatEnum.MAXHP, Role.RANGED, true), "gear scoring values tank survivability");
		System.out.println("OK: " + checks + " playerbot engine, policy, lease, preference and appearance checks");
	}

	private static void leaseChecks() throws Exception {
		int id = 2147483500;
		var first = PlayerBotLease.acquire(id);
		check(first != null && PlayerBotLease.acquire(id) == null, "exclusive character lease");
		first.close();
		var second = PlayerBotLease.acquire(id); first.close();
		check(second != null && PlayerBotLease.isReserved(id), "stale close cannot release another owner's lease"); second.close();
		try (var executor = Executors.newFixedThreadPool(8)) {
			var start = new CountDownLatch(1); var acquired = new CountDownLatch(8); var release = new CountDownLatch(1);
			var winners = new AtomicInteger(); var futures = new ArrayList<Future<?>>();
			for (int n = 0; n < 8; n++) futures.add(executor.submit(() -> {
				try {
					start.await(); var lease = PlayerBotLease.acquire(id); if (lease != null) winners.incrementAndGet();
					acquired.countDown(); release.await(); if (lease != null) lease.close();
				} catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
			}));
			start.countDown(); boolean completed = acquired.await(10, TimeUnit.SECONDS); release.countDown();
			check(completed && winners.get() == 1, "simultaneous login/recruit race has one winner");
			for (var future : futures) future.get(10, TimeUnit.SECONDS);
			check(!PlayerBotLease.isReserved(id), "winner releases reservation after save");
		}
	}
	private static void pathChecks() {
		var start = new PlayerBotNavigation.Point(0, 0, 0);
		var goal = new PlayerBotNavigation.Point(10, 0, 0);
		PlayerBotPathfinder.Ground wall = (from, x, y) -> {
			for (int sample = 1; sample <= 20; sample++) {
				float sx = from.x() + (x - from.x()) * sample / 20, sy = from.y() + (y - from.y()) * sample / 20;
				if (sx >= 3 && sx <= 5 && sy >= -4 && sy <= 4) return null;
			}
			return new PlayerBotNavigation.Point(x, y, 0);
		};
		var detour = PlayerBotPathfinder.find(start, goal, wall, 128, () -> false);
		check(!detour.isEmpty() && detour.getLast().equals(goal), "local search finds a route around a wall");
		check(detour.stream().anyMatch(p -> Math.abs(p.y()) > 4), "route takes a real detour instead of passing through geometry");
		var previous = start;
		for (var point : detour) {
			check(wall.step(previous, point.x(), point.y()) != null, "every detour edge is collision-probed");
			previous = point;
		}
		check(PlayerBotPathfinder.find(start, goal, (from, x, y) -> null, 128, () -> false).isEmpty(), "blocked ground does not invent a route");
		check(PlayerBotPathfinder.find(start, goal, (from, x, y) -> new PlayerBotNavigation.Point(x, y, 20), 128, () -> false).isEmpty(), "local search rejects cliff jumps");
		var probes = new AtomicInteger();
		check(PlayerBotPathfinder.find(start, goal, (from, x, y) -> { probes.incrementAndGet(); return new PlayerBotNavigation.Point(x, y, 0); },
			128, () -> true).isEmpty() && probes.get() == 0, "expired search budget performs no geometry work");
		check(PlayerBotPathfinder.find(start, goal, wall, 1, () -> false).isEmpty(), "node budget stops incomplete searches");
		check(PlayerBotPathfinder.find(start, new PlayerBotNavigation.Point(Float.NaN, 0, 0), wall, 128, () -> false).isEmpty(), "non-finite destinations are rejected");
	}
	private static void preferenceChecks() throws Exception {
		Path directory = Files.createTempDirectory(Path.of("target", "playerbots-validation"), "preferences-");
		try {
			var preferences = new PlayerBotPreferences(directory);
			var defaults = preferences.load(1, 2, Role.TANK);
			check(defaults.role() == Role.TANK && !defaults.area() && defaults.supplies() && !defaults.gear(), "safe defaults");
			var values = new PlayerBotPreferences.Values(Role.HEALER, true, false, true, true, true);
			preferences.save(1, 2, values);
			check(preferences.load(1, 2, Role.TANK).equals(values), "settings persist across re-recruitment");
			try { preferences.load(3, 2, Role.MELEE); throw new AssertionError("wrong account settings accepted"); }
			catch (java.io.IOException expected) { check(true, "settings account ownership"); }
			Files.writeString(directory.resolve("character-2.properties"), "account=1\ncharacter=2\nrole=TANK\narea=false\nsupplies=true\ngear=false\nloot=true\n");
			check(!preferences.load(1, 2, Role.TANK).questing() && preferences.load(1, 2, Role.TANK).loot(), "older preferences keep loot choice and default quest automation off");
			Files.writeString(directory.resolve("character-2.properties"), "account=1\ncharacter=2\nrole=TANK\narea=yes\nsupplies=true\ngear=false\n");
			try { preferences.load(1, 2, Role.TANK); throw new AssertionError("corrupt flag accepted"); }
			catch (java.io.IOException expected) { check(true, "corrupt settings fail visibly"); }
		} finally {
			try (var paths = Files.list(directory)) { for (var path : paths.toList()) Files.delete(path); }
			Files.delete(directory);
		}
	}
}
