package com.aionemu.gameserver.controllers.observer;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.aionemu.gameserver.skillengine.model.Effect;

public class ShieldHitCountHelper {

	private static final Map<AttackShieldObserver, Integer> REMAINING_HITS = Collections.synchronizedMap(new WeakHashMap<>());

	public static boolean onDamageAbsorbed(AttackShieldObserver observer, Effect effect) {
		if (observer == null || effect == null || effect.getSkillTemplate() == null) {
			return false;
		}

		boolean shouldEndEffect;
		synchronized (REMAINING_HITS) {
			Integer remaining = REMAINING_HITS.get(observer);
			if (remaining == null) {
				remaining = parseHitCount(effect);
				if (remaining <= 0)
					return false;
			}
			if (remaining <= 0)
				return true;

			remaining--;
			REMAINING_HITS.put(observer, remaining);
			shouldEndEffect = remaining == 0;
		}

		// Keep effect teardown outside the map lock. The zero entry remains associated
		// with this observer until its weak key is collected, preventing a late,
		// already-queued hit callback from restarting the counter.
		if (shouldEndEffect)
			effect.endEffect();
		return shouldEndEffect;
	}

	public static int parseHitCount(Effect effect) {
		if (effect == null || effect.getSkillTemplate() == null) {
			return 0;
		}
		String stack = effect.getSkillTemplate().getStack();
		if (stack != null) {
			int idx = stack.indexOf("COUNT");
			if (idx != -1) {
				int start = idx + 5;
				int end = start;
				while (end < stack.length() && Character.isDigit(stack.charAt(end))) {
					end++;
				}
				if (end > start) {
					try {
						return Integer.parseInt(stack.substring(start, end));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return 0;
	}

	public static int getRemainingHits(AttackShieldObserver observer) {
		if (observer == null)
			return 0;
		synchronized (REMAINING_HITS) {
			Integer remaining = REMAINING_HITS.get(observer);
			return remaining == null ? 0 : remaining;
		}
	}

	public static void clear(AttackShieldObserver observer) {
		if (observer != null)
			REMAINING_HITS.remove(observer);
	}
}
