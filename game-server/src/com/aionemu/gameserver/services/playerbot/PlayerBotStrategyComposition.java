/*
 * Adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Engine.cpp/Action.h/Strategy.h and AiFactory.cpp. Upstream contributors:
 * third-party/playerbots/AUTHORS.md. SPDX-License-Identifier: GPL-2.0-or-later
 * Aion keeps fresh actor snapshots; only action keys survive a decision.
 */
package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** State-specific composition without changing an existing character's build. */
public final class PlayerBotStrategyComposition {
	private static final java.lang.reflect.Field PRE_CATEGORY;
	static {
		try {
			PRE_CATEGORY = com.aionemu.gameserver.skillengine.condition.ChainCondition.class.getDeclaredField("preCategory");
			PRE_CATEGORY.setAccessible(true);
		} catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
	}

	public interface ContinuingAction extends Action {
		default String key() { return name(); }
		default List<String> continuers() { return List.of(); }
	}

	public static final class Plan {
		private final State state;
		private final Map<String, List<Trigger>> groups = new LinkedHashMap<>();
		private final Set<String> disabled = new HashSet<>();
		private final List<Multiplier> multipliers = new ArrayList<>();
		public Plan(State state) { this.state = state; }
		public List<Trigger> triggers(String name, State... states) {
			if (Arrays.stream(states).noneMatch(s -> s == state)) return new ArrayList<>();
			return groups.computeIfAbsent(name, ignored -> new ArrayList<>());
		}
		public void enable(String name, boolean enabled) {
			if (enabled) disabled.remove(name); else disabled.add(name);
		}
		public void multiplier(Multiplier multiplier) { multipliers.add(multiplier); }
		public void defaults(String name, Action action, double relevance, State... states) {
			triggers(name, states).add(new Trigger(() -> true, action, () -> relevance));
		}
		public List<Strategy> strategies() {
			return groups.entrySet().stream().filter(e -> !disabled.contains(e.getKey()))
				.map(e -> new Strategy(e.getKey(), List.copyOf(e.getValue()))).toList();
		}
		public boolean tick(PlayerBotEngine engine, int budget) {
			return engine.tick(state, strategies(), List.copyOf(multipliers), budget);
		}
	}

	/** The key includes native identity; equally named NPCs are never interchangeable. */
	static String skillKey(PlayerBotSkills.Entry entry, Creature recipient) {
		return "skill:" + entry.template().getSkillId() + ":" + recipient.getObjectId()
			+ ":" + recipient.getWorldId() + ":" + recipient.getInstanceId();
	}

	static Action skill(Action action, Player bot, PlayerBotSkills.Entry entry, Creature recipient,
		List<PlayerBotSkills.Entry> learned) {
		// Potential successors are only native learned follow-ups. Availability, role,
		// target, resources and cooldowns are re-evaluated by the next fresh session.
		return new LinkedSkill(action, skillKey(entry, recipient), bot, entry, recipient, learned);
	}

	private record LinkedSkill(Action delegate, String key, Player bot, PlayerBotSkills.Entry entry,
		Creature recipient, List<PlayerBotSkills.Entry> learned) implements ContinuingAction {
		public String name() { return delegate.name(); }
		public boolean isUseful() { return delegate.isUseful(); }
		public boolean isPossible() { return delegate.isPossible(); }
		public boolean execute() { return delegate.execute(); }
		public List<Action> prerequisites() { return delegate.prerequisites(); }
		public List<Action> alternatives() { return delegate.alternatives(); }
		public List<String> continuers() {
			var chain = entry.template().getChainCondition();
			if (chain == null) return List.of();
			// A native cast may finish asynchronously. Retain the template's exact
			// successor keys now; the next admitted snapshot still requires the actual
			// proc, activation count and unexpired native chain before it can cast.
			return learned.stream().filter(e -> e.kind() == entry.kind() && PlayerBotSkills.followUp(e)
				&& e.template().getSkillId() != entry.template().getSkillId()
				&& follows(chain.getCategory(), e)).map(e -> skillKey(e, recipient)).limit(16).toList();
		}
	}
	private static boolean follows(String category, PlayerBotSkills.Entry successor) {
		try { return Objects.equals(category, PRE_CATEGORY.get(successor.template().getChainCondition())); }
		catch (IllegalAccessException error) { throw new IllegalStateException("Native chain metadata unavailable", error); }
	}

	static String key(Action action) {
		return action instanceof ContinuingAction linked ? linked.key() : action.name();
	}
	/** Upstream ThreatMultiplier purpose; retain Aion's existing emergency interrupt exception. */
	static Multiplier threat(boolean withheld) {
		return action -> {
			if (!withheld || !(action instanceof LinkedSkill skill)) return 1;
			if (skill.entry().kind() != PlayerBotRules.SkillKind.DAMAGE) return 1;
			return skill.recipient().isCasting() && PlayerBotSkills.canInterrupt(skill.entry().template()) ? 1 : 0;
		};
	}
	private PlayerBotStrategyComposition() {}
}
