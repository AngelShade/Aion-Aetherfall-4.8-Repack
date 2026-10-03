/*
 * Adapted on 2026-10-03 from mod-playerbots (AzerothCore), commit
 * 037c01418b5d01506917a3db9b44fd56ac5f965c: Engine.cpp, Action.h, Trigger.h,
 * Strategy.h. Copyright: upstream contributors, see third-party/playerbots/AUTHORS.md.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Java adaptation: fresh snapshot queue, stable ties and bounded expansion.
 */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** Strategy / trigger / action arbitration, independent of Aion objects and networking. */
public final class PlayerBotEngine {

	public static final double DEFAULT = 5, NORMAL = 10, HIGH = 20, MOVE = 30,
		INTERRUPT = 40, DISPEL = 50, ENCOUNTER = 60, EMERGENCY = 90;

	public enum State { NON_COMBAT, COMBAT, DEAD }

	public interface Action {
		String name();
		boolean isUseful();
		boolean isPossible();
		boolean execute();
		default List<Action> prerequisites() { return List.of(); }
		default List<Action> alternatives() { return List.of(); }
	}

	public record Trigger(BooleanSupplier active, Action action, DoubleSupplier relevance) {}
	public record Strategy(String name, List<Trigger> triggers) {}
	@FunctionalInterface
	public interface Multiplier { double value(Action action); }
	private record Basket(Action action, double relevance, long order) {}

	private String lastAction = "idle";
	private State state = State.NON_COMBAT;
	private int attempts;

	/** A queue lasts one snapshot; a previous target or heal must never survive a state change. */
	public boolean tick(State newState, List<Strategy> strategies, List<Multiplier> multipliers, int budget) {
		state = newState;
		attempts = 0;
		PriorityQueue<Basket> queue = new PriorityQueue<>(Comparator
			.comparingDouble(Basket::relevance).reversed().thenComparingLong(Basket::order));
		long order = 0;
		for (Strategy strategy : strategies)
			for (Trigger trigger : strategy.triggers())
				if (trigger.active().getAsBoolean()) {
					double relevance = trigger.relevance().getAsDouble();
					for (Multiplier multiplier : multipliers)
						relevance *= multiplier.value(trigger.action());
					if (Double.isFinite(relevance) && relevance > 0)
						queue.add(new Basket(trigger.action(), relevance, order++));
				}
		Set<String> expanded = new HashSet<>();
		while (!queue.isEmpty() && attempts < Math.max(0, budget)) {
			attempts++;
			Basket basket = queue.remove();
			Action action = basket.action();
			// Suppression policies apply to expanded prerequisites/alternatives as well as seeds.
			boolean suppressed = false;
			for (Multiplier multiplier : multipliers) {
				double value = multiplier.value(action);
				if (!Double.isFinite(value) || value <= 0) { suppressed = true; break; }
			}
			if (suppressed) continue;
			if (!action.isUseful())
				continue;
			if (action.isPossible()) {
				List<Action> prerequisites = action.prerequisites().stream().filter(Action::isUseful).toList();
				if (!prerequisites.isEmpty()) {
					if (expanded.add("prerequisite:" + action.name())) {
						queue.add(new Basket(action, basket.relevance() + 0.001, order++));
						for (Action prerequisite : prerequisites)
							queue.add(new Basket(prerequisite, basket.relevance() + 0.002, order++));
						continue;
					}
				} else if (action.execute()) {
					lastAction = action.name();
					return true;
				}
			}
			if (expanded.add("alternative:" + action.name()))
				for (Action alternative : action.alternatives())
					queue.add(new Basket(alternative, basket.relevance() + 0.003, order++));
		}
		lastAction = "idle";
		return false;
	}

	public String getLastAction() { return lastAction; }
	public State getState() { return state; }
	public int getAttempts() { return attempts; }
}
