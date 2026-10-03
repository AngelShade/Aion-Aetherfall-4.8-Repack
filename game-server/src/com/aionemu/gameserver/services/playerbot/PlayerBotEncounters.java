package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;

/** Mechanics grounded in the installed source handlers and skill data; does not enable instance admission. */
final class PlayerBotEncounters {
	static int requiredProtection(Npc enemy) {
		if (!"brigade_general_vasharti".equals(enemy.getObjectTemplate().getAiName())) return 0;
		return requiredProtection(enemy.getEffectController().hasAbnormalEffect(20530), enemy.getEffectController().hasAbnormalEffect(20531));
	}
	static int requiredProtection(boolean blue, boolean red) { return blue && red ? -1 : blue ? 20535 : red ? 20536 : 0; }
	static boolean allowsAttack(Creature actor, Npc enemy) {
		return allowsAttack(actor, enemy, 750);
	}
	static boolean allowsAttack(Creature actor, Npc enemy, long commitmentMillis) {
		int required = requiredProtection(enemy);
		var protection = required > 0 ? actor.getEffectController().getAbnormalEffects().stream().filter(e -> e.getSkillId() == required).findFirst().orElse(null) : null;
		return protectedFor(required, protection == null ? 0 : protection.getRemainingTimeMillis(), commitmentMillis);
	}
	static boolean protectedFor(int required, long remaining, long commitment) {
		return required == 0 || required > 0 && remaining > Math.max(0, commitment);
	}
	static int visibleProtection(Creature actor) {
		int[] required = { 0 };
		actor.getKnownList().forEachNpc(npc -> {
			if (npc.isSpawned() && !npc.isDead() && npc.getWorldId() == actor.getWorldId() && npc.getInstanceId() == actor.getInstanceId()) {
				int value = requiredProtection(npc);
				if (value != 0) required[0] = required[0] == 0 || required[0] == value ? value : -1;
			}
		});
		return required[0];
	}
	static boolean matchingFlame(int npc, int protection) { return protection == 20536 ? npc == 282998 : protection == 20535 && (npc == 282997 || npc == 282999); }
	static Npc protection(Player owner, Player bot, List<Npc> engaged) {
		int required = engaged.stream().mapToInt(PlayerBotEncounters::requiredProtection).filter(id -> id != 0).findFirst().orElse(0);
		var current = required > 0 ? bot.getEffectController().getAbnormalEffects().stream().filter(e -> e.getSkillId() == required).findFirst().orElse(null) : null;
		if (required <= 0 || current != null && current.getRemainingTimeMillis() > 4000) return null;
		List<Npc> flames = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			if (npc.isSpawned() && !npc.isDead() && npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId()
				&& "dancing_flame".equals(npc.getObjectTemplate().getAiName()) && matchingFlame(npc.getNpcId(), required)
				&& PositionUtil.isInRange(owner, npc, 35)) flames.add(npc);
		});
		return flames.stream().min(Comparator.comparingDouble(n -> PositionUtil.getDistance(bot, n))).orElse(null);
	}
	private PlayerBotEncounters() {}
}
