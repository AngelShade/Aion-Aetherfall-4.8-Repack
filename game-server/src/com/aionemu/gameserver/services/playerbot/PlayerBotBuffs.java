package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.skillengine.model.*;

/** Conservatively retains the current buff set using EffectController's native slot capacities. */
final class PlayerBotBuffs {
	static boolean canAdd(PlayerClass casterClass, SkillTemplate next, List<SkillTemplate> active) {
		for (var current : active) {
			if (current.getSkillId() == next.getSkillId()
				|| next.getStack() != null && !next.getStack().isEmpty() && next.getStack().equals(current.getStack())
				|| next.getConflictId() != 0 && next.getConflictId() == current.getConflictId()) return false;
			if (next.getDispelCategory() == DispelCategoryType.EXTRA && current.getDispelCategory() == DispelCategoryType.EXTRA) return false;
			if (next.getTargetSlot() == current.getTargetSlot() && next.getEffects() != null && current.getEffects() != null)
				for (var effect : next.getEffects().getEffects())
					if (effect.getEffectId() != 0 && current.getEffects().getEffects().stream().anyMatch(a -> a.getEffectId() == effect.getEffectId())) return false;
		}
		if (noShowToggle(next)) {
			boolean chant = next.getSubType() == SkillSubType.CHANT;
			int limit = casterClass == PlayerClass.RANGER || casterClass == PlayerClass.RIDER ? 2 : chant ? 3 : 1;
			return active.stream().filter(PlayerBotBuffs::noShowToggle).filter(t -> (t.getSubType() == SkillSubType.CHANT) == chant).count() < limit;
		}
		if (next.getTargetSlot() == SkillTargetSlot.CHANT
			&& active.stream().filter(t -> !noShowToggle(t) && t.getTargetSlot() == SkillTargetSlot.CHANT).count() >= 4) return false;
		if (next.getTargetSlot() == SkillTargetSlot.NOSHOW || next.getCooldownId() == 1) return true;
		int cooldown = next.getCooldownId();
		int limit = cooldown == 273 || cooldown == 353 ? 2 : cooldown == 6 ? 10 : 1;
		if (active.stream().filter(t -> !noShowToggle(t) && t.getTargetSlot() == next.getTargetSlot() && t.getCooldownId() == cooldown).count() >= limit) return false;
		return !rangerBuff(cooldown) || active.stream().filter(t -> rangerBuff(t.getCooldownId())).count() < 2;
	}
	private static boolean noShowToggle(SkillTemplate t) { return t.getTargetSlot() == SkillTargetSlot.NOSHOW && t.isToggle(); }
	private static boolean rangerBuff(int id) { return id == 2020 || id == 2022 || id == 2024 || id == 2026 || id == 2028 || id == 2030; }
	private PlayerBotBuffs() {}
}
