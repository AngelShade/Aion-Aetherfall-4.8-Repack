package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Order;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * Local Aion stranded-follower repair alongside upstream FollowAction's formation
 * movement contract. The cached MovementActions.cpp teleport fallback is commented
 * out; this helper does not claim that fallback as active upstream parity.
 * Native short-range portals such as Steel Rake's Drana
 * Generator Chamber access door (2.8m horizontal) and the Brig entrance move the owner
 * through walls/keyed gates without exceeding the long-jump or 60m catch-up thresholds,
 * so ground followers remained behind geometry. A follower that is actively trying to
 * follow, out of combat, but makes neither displacement nor owner-distance progress for
 * {@link #STALL_MS} is relocated through the native {@link PlayerBotService#relocate} path.
 */
final class PlayerBotFollowRecovery {
	static final long STALL_MS = 6000, GAP_MS = 1500;
	static final double MOVED = 3, CLOSER = 1.5, MIN_DISTANCE = 6;

	static final class State {
		float x, y, z;
		double best;
		long mark, last;
	}

	private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

	/** Called from follow() while the follower still needs to move toward the owner. */
	static void observe(Player bot, Player owner) {
		long now = System.currentTimeMillis();
		State s = STATES.computeIfAbsent(bot.getObjectId(), id -> new State());
		double distance = PositionUtil.getDistance(bot, owner);
		synchronized (s) {
			if (s.mark == 0 || now - s.last > GAP_MS || !Double.isFinite(s.best)
				|| PositionUtil.getDistance(bot, s.x, s.y, s.z) >= MOVED || distance <= s.best - CLOSER) {
				s.x = bot.getX(); s.y = bot.getY(); s.z = bot.getZ();
				s.best = distance; s.mark = now;
			}
			s.last = now;
		}
	}

	/** Called when no follow movement is needed (in formation) or follow is not running. */
	static void reset(Player bot) { STATES.remove(bot.getObjectId()); }

	static boolean stalled(State s, long now, double distance) {
		return stalled(s,now,distance,false);
	}

	static boolean stalled(State s, long now, double distance, boolean blocked) {
		return s != null && s.mark != 0 && now >= s.last && now - s.last <= GAP_MS && now - s.mark >= STALL_MS
			&& Double.isFinite(distance) && (distance > MIN_DISTANCE || blocked && distance > 1.6);
	}

	static boolean follows(Order order) { return order == Order.FOLLOW || order == Order.PASSIVE; }

	/** Relocate a stranded out-of-combat follower next to the owner through native relocation. */
	static boolean recover(PlayerBotSession session) {
		Player owner = session.owner(), bot = session.bot();
		State s = STATES.get(bot.getObjectId());
		if (s == null || session.closing()) return false;
		// An order change must cancel even a freshly observed follow stall.
		if (!follows(PlayerBotPartyBehavior.state(session).order)) { reset(bot); return false; }
		long now = System.currentTimeMillis();
		synchronized (s) {
			// Movement can complete between the last follow observation and this tick.
			double distance=PositionUtil.getDistance(bot, owner);
			if (PositionUtil.getDistance(bot, s.x, s.y, s.z) >= MOVED || distance <= s.best - CLOSER) { reset(bot); return false; }
			// A 2.8m portal may place the leader behind a wall without meeting the 6m gate.
			// Check native sight only once the observation's time/distance gates can pass.
			if (!stalled(s, now, distance, true)) return false;
			if (distance <= MIN_DISTANCE && GeoService.getInstance().canSee(bot,owner)) return false;
		}
		if (bot.getWorldId() != owner.getWorldId() || bot.getInstanceId() != owner.getInstanceId() || owner.isFlying() || bot.isFlying()
			|| bot.isCasting() || bot.isLooting() || bot.isTrading() || bot.getController().hasTask(TaskId.ITEM_USE)
			|| !bot.canPerformMove() || owner.getMoveController().isInMove()
			|| !PlayerBotPartyBehavior.canRelocate(session)) return false;
		if (!PlayerBotService.getInstance().relocate(session)) return false;
		STATES.remove(bot.getObjectId());
		PlayerBotQuestSync.returnToOwner(bot);
		return true;
	}

	static void close(Player bot) { STATES.remove(bot.getObjectId()); }

	private PlayerBotFollowRecovery() {}
}
