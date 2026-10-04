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

	/** Re-resolve retained action keys from fresh, state-specific strategies on each tick. */
	public boolean tick(State newState, List<Strategy> strategies, List<Multiplier> multipliers, int budget) {
		state = newState;
		var result = PlayerBotArbitration.run(this, newState, strategies, multipliers, budget);
		attempts = result.attempts(); lastAction = result.action();
		return result.executed();
	}

	public String getLastAction() { return lastAction; }
	public State getState() { return state; }
	public int getAttempts() { return attempts; }
}
