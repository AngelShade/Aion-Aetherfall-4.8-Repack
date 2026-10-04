/*
 * Adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c,
 * Engine.cpp DoNextAction/MultiplyAndPush/PushAgain. Upstream contributors:
 * third-party/playerbots/AUTHORS.md. SPDX-License-Identifier: GPL-2.0-or-later
 */
package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotStrategyComposition.key;
import java.util.*;

/** Bounded upstream action-node semantics, with fresh Aion actor resolution. */
public final class PlayerBotArbitration {
	private static final int MAX_PENDING = 16, MAX_QUEUE = 512;
	private static final long LIFETIME = 10000;
	private static final Map<PlayerBotEngine, Memory> MEMORIES = Collections.synchronizedMap(new WeakHashMap<>());
	private static final class Memory {
		State state;
		Object context;
		final Map<String, Pending> pending = new LinkedHashMap<>();
	}
	private record Pending(String key, double relevance, long expires) {}
	private record Basket(Action action, double relevance, long order, List<Pending> parents) {}
	public record Result(boolean executed, String action, int attempts) {}

	private static Memory memory(PlayerBotEngine engine) {
		synchronized (MEMORIES) { return MEMORIES.computeIfAbsent(engine, ignored -> new Memory()); }
	}
	/** Call before early exits too: no continuation can cross native lifecycle/context changes. */
	public static void context(PlayerBotEngine engine, Object context) {
		Memory memory = memory(engine);
		synchronized (memory) {
			if (!Objects.equals(memory.context, context)) memory.pending.clear();
			memory.context = context;
		}
	}
	public static void clear(PlayerBotEngine engine) {
		synchronized (MEMORIES) { MEMORIES.remove(engine); }
	}

	public static Result run(PlayerBotEngine engine, State state, List<Strategy> strategies,
		List<Multiplier> multipliers, int budget) {
		Memory memory = memory(engine);
		synchronized (memory) {
			long now = System.currentTimeMillis();
			if (state != memory.state || state == State.DEAD) memory.pending.clear();
			memory.state = state;
			var queue = new PriorityQueue<Basket>(Comparator.comparingDouble(Basket::relevance)
				.reversed().thenComparingLong(Basket::order));
			Map<String, Trigger> fresh = new LinkedHashMap<>();
			Map<String, Double> priorities = new LinkedHashMap<>();
			for (Strategy strategy : strategies) for (Trigger trigger : strategy.triggers()) {
				if (!trigger.active().getAsBoolean()) continue;
				double relevance = trigger.relevance().getAsDouble();
				if (!Double.isFinite(relevance) || relevance <= 0) continue;
				String id = key(trigger.action());
				if (!fresh.containsKey(id) || relevance > priorities.get(id)) {
					fresh.put(id, trigger); priorities.put(id, relevance);
				}
			}
			// Name resolution uses this snapshot's admitted, active strategy actions only.
			memory.pending.values().removeIf(p -> p.expires() <= now || !fresh.containsKey(p.key()));
			long order = 0;
			for (var seed : fresh.entrySet()) {
				Pending pending = memory.pending.get(seed.getKey());
				double relevance = priorities.get(seed.getKey());
				if (pending != null) relevance = Math.max(relevance, pending.relevance());
				push(queue, new Basket(seed.getValue().action(), relevance, order++, List.of()));
			}
			Set<String> expanded = new HashSet<>();
			int attempts = 0;
			while (!queue.isEmpty() && attempts < Math.max(0, budget)) {
				attempts++;
				Basket basket = queue.remove(); Action action = basket.action(); String id = key(action);
				if (!action.isUseful()) { memory.pending.remove(id); continue; }
				// Upstream evaluates multipliers at pop, for every action, not at seed creation.
				double relevance = basket.relevance();
				for (Multiplier multiplier : multipliers) {
					double value = multiplier.value(action);
					if (!Double.isFinite(value) || value <= 0) { relevance = 0; break; }
					relevance *= value;
				}
				if (!Double.isFinite(relevance) || relevance <= 0) { memory.pending.remove(id); continue; }
				if (action.isPossible()) {
					List<Action> prerequisites = action.prerequisites().stream().filter(Action::isUseful).toList();
					if (!prerequisites.isEmpty()) {
						if (expanded.add("prerequisite:" + id)) {
							var parents = new ArrayList<>(basket.parents());
							parents.add(new Pending(id, relevance + .001, now + LIFETIME));
							push(queue, new Basket(action, relevance + .001, order++, basket.parents()));
							for (Action prerequisite : prerequisites)
								push(queue, new Basket(prerequisite, relevance + .002, order++, List.copyOf(parents)));
							continue;
						}
						// Unlike skipPrerequisites in WoW, failed native movement cannot authorize a cast.
					} else if (action.execute()) {
						memory.pending.remove(id);
						for (Pending parent : basket.parents()) remember(memory, parent);
						if (action instanceof PlayerBotStrategyComposition.ContinuingAction linked)
							for (String next : linked.continuers())
								remember(memory, new Pending(next, relevance, now + LIFETIME));
						return new Result(true, action.name(), attempts);
					}
				}
				memory.pending.remove(id);
				if (expanded.add("alternative:" + id)) for (Action alternative : action.alternatives())
					push(queue, new Basket(alternative, relevance + .003, order++, basket.parents()));
			}
			return new Result(false, "idle", attempts);
		}
	}
	private static void remember(Memory memory, Pending pending) {
		if (pending.key() == null || pending.key().isBlank()) return;
		Pending before = memory.pending.get(pending.key());
		if (before == null || pending.relevance() >= before.relevance()) memory.pending.put(pending.key(), pending);
		while (memory.pending.size() > MAX_PENDING) memory.pending.remove(memory.pending.keySet().iterator().next());
	}
	private static void push(PriorityQueue<Basket> queue, Basket basket) {
		queue.add(basket);
		if (queue.size() > MAX_QUEUE) {
			Basket worst = queue.stream().min(Comparator.comparingDouble(Basket::relevance)
				.thenComparing(Comparator.comparingLong(Basket::order).reversed())).orElseThrow();
			queue.remove(worst);
		}
	}
	private PlayerBotArbitration() {}
}
