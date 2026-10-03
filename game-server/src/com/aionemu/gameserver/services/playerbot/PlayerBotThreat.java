package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Short damage pauses use actual native hate; they never rewrite threat or suppress hits already committed. */
final class PlayerBotThreat {
	private int target;
	private long holdUntil, retryAfter;
	boolean hold(Player bot, Role role, Npc enemy, List<Player> party, long now) {
		if (enemy == null) return decide(0, 0, 0, false, role == Role.TANK, now);
		int tankHate = party.stream().filter(p -> p != bot && !p.isDead() && PlayerBotService.getInstance().combatRole(p) == Role.TANK)
			.mapToInt(p -> enemy.getAggroList().getHate(p)).max().orElse(0);
		int ownHate = enemy.getAggroList().getHate(bot);
		if (bot.getSummon() != null) ownHate = (int) Math.min(Integer.MAX_VALUE, (long) ownHate + enemy.getAggroList().getHate(bot.getSummon()));
		return decide(enemy.getObjectId(), ownHate, tankHate, enemy.getLifeStats().getHpPercentage() > 10, role == Role.TANK, now);
	}
	boolean decide(int enemy, int ownHate, int tankHate, boolean durableTarget, boolean tank, long now) {
		if (enemy != target) { target = enemy; holdUntil = retryAfter = 0; }
		if (enemy == 0 || tank || tankHate <= 0 || !durableTarget || ownHate < tankHate * .7) { holdUntil = 0; return false; }
		if (holdUntil > now) return true;
		if (holdUntil != 0) { holdUntil = 0; retryAfter = now + 1500; return false; }
		if (now >= retryAfter && ownHate >= tankHate * .85) { holdUntil = now + 2500; return true; }
		return false;
	}
}
