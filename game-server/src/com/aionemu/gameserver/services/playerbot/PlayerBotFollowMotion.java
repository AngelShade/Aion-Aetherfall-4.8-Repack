/* Purpose of mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c
 * MovementActions.cpp::Follow / FollowActions.cpp: attach one persistent
 * MoveFollow generator and let the movement system chase the moving leader.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Aion steering is local. */
package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayDeque;

import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * Companion following owned by the native mover, not renewed by AI decisions.
 * The session attaches it once; every movement step then steers toward the live
 * role slot. Steering tries a direct walk to the slot, then to the owner, then
 * to the owner's recent footsteps. Only when all of them are blocked does the
 * session hand over to full navigation (routes, jumps, lifts) and, after the
 * configured stuck delay, to native regroup placement.
 */
final class PlayerBotFollowMotion implements PlayerBotMoveController.FollowSource {
	/** Stop within this distance of the slot while walking. */
	static final float ARRIVE = .8f;
	/** Start again only beyond this distance while the owner stands still. */
	static final float REST = 1.8f;
	private static final int TRAIL = 48, TRAIL_PROBES = 6;
	private final Player owner, bot;
	private final ArrayDeque<Point> trail = new ArrayDeque<>(); // guarded by itself
	private int trailWorld, trailInstance;
	private volatile long blockedSince;
	// Mover-thread steering cache.
	private Point steering;
	private long nextProbe;

	PlayerBotFollowMotion(Player owner, Player bot) {
		this.owner = owner;
		this.bot = bot;
	}

	/** AI thread: remember the owner's walked positions as fallback corners. */
	void record() {
		synchronized (trail) {
			if (owner.isFlying() || owner.isInGlidingState() || owner.getWorldId() != trailWorld || owner.getInstanceId() != trailInstance) {
				trail.clear();
				trailWorld = owner.getWorldId();
				trailInstance = owner.getInstanceId();
				if (owner.isFlying() || owner.isInGlidingState())
					return;
			}
			Point last = trail.peekLast();
			Point now = new Point(owner.getX(), owner.getY(), owner.getZ());
			if (last != null && distance(last, now) > 15)
				trail.clear(); // teleport or transfer: old footsteps are not a path
			if (last == null || distance(last, now) >= 1) {
				trail.addLast(now);
				while (trail.size() > TRAIL)
					trail.removeFirst();
			}
		}
	}

	/** One rule for the session and the mover: is following movement wanted now? */
	boolean needed(Point goal) {
		if (owner.getMoveController().isInMove())
			return true;
		double planar = Math.hypot(bot.getX() - goal.x(), bot.getY() - goal.y());
		return Math.abs(bot.getZ() - goal.z()) > 2.5 || planar > (bot.getMoveController().isInMove() ? ARRIVE : REST);
	}

	boolean attached() {
		return bot.getMoveController() instanceof PlayerBotMoveController mover && mover.following(this);
	}

	boolean attach() {
		if (attached())
			return true;
		blockedSince = 0;
		steering = null;
		nextProbe = 0;
		return bot.getMoveController() instanceof PlayerBotMoveController mover && mover.startFollow(this);
	}

	/** How long every direct candidate has been blocked; 0 while steering works. */
	long blockedFor(long now) {
		long since = blockedSince;
		return since == 0 ? 0 : Math.max(1, now - since);
	}

	/** AI thread: cheap check used to leave the navigation fallback again. */
	boolean directlyReachable(Point goal) {
		return reach(goal, true) != null || reach(ownerPoint(), false) != null;
	}

	@Override
	public PlayerBotMoveController.FollowStep steer(long now) {
		if (!owner.isOnline() || !owner.isSpawned() || owner.isDead() || owner.getWorldId() != bot.getWorldId()
			|| owner.getInstanceId() != bot.getInstanceId())
			return null;
		Point goal = PlayerBotFollowIntent.goal(bot);
		if (goal == null)
			goal = ownerPoint();
		boolean leaderMoving = owner.getMoveController().isInMove();
		double planar = Math.hypot(bot.getX() - goal.x(), bot.getY() - goal.y());
		boolean walking = bot.getMoveController().isInMove();
		if (!leaderMoving && planar <= (walking ? ARRIVE : REST) && Math.abs(bot.getZ() - goal.z()) <= 2.5) {
			blockedSince = 0;
			steering = null;
			return null;
		}
		// Re-probe five times a second while walking; back off while blocked.
		if (steering != null && now < nextProbe && distance(steering, goal) < 1)
			return step(steering);
		nextProbe = now + (blockedSince != 0 ? 600 : 200);
		Point target = reach(goal, true);
		if (target == null) {
			Point leader = ownerPoint();
			if (Math.hypot(bot.getX() - leader.x(), bot.getY() - leader.y()) <= 2.2 && Math.abs(bot.getZ() - leader.z()) <= 2) {
				blockedSince = 0;
				steering = null;
				return null; // slot unreachable, but already beside the owner
			}
			target = reach(leader, false);
		}
		if (target == null)
			target = trailTarget();
		if (target == null) {
			if (blockedSince == 0)
				blockedSince = now;
			steering = null;
			return null;
		}
		blockedSince = 0;
		steering = target;
		return step(target);
	}

	@Override
	public void blocked(long now) {
		if (blockedSince == 0)
			blockedSince = now;
		steering = null;
		nextProbe = now + 600;
	}

	private Point ownerPoint() {
		return new Point(owner.getX(), owner.getY(), owner.getZ());
	}

	/** Native straight-line ground walk; slots are preferences clipped to the owner's floor. */
	private Point reach(Point goal, boolean slot) {
		Vector3f reached = GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(), bot.getInstanceId(), bot.getX(), bot.getY(),
			bot.getZ(), goal.x(), goal.y(), goal.z());
		if (reached == null || !Float.isFinite(reached.x) || !Float.isFinite(reached.y) || !Float.isFinite(reached.z))
			return null;
		if (Math.hypot(reached.x - goal.x(), reached.y - goal.y()) > .35 || Math.abs(reached.z - goal.z()) > 2)
			return null;
		if (slot && Math.abs(reached.z - owner.getZ()) > 3)
			return null; // a slot over a ledge is not the owner's floor
		return new Point(reached.x, reached.y, reached.z);
	}

	/** Newest walked owner position that is directly reachable from here. */
	private Point trailTarget() {
		Point[] candidates;
		synchronized (trail) {
			candidates = trail.toArray(Point[]::new);
		}
		int probes = 0;
		for (int i = candidates.length - 1; i >= 0 && probes < TRAIL_PROBES; i -= 2) {
			Point point = candidates[i];
			if (Math.hypot(point.x() - bot.getX(), point.y() - bot.getY()) < 1.2)
				break; // older footsteps lie behind us
			probes++;
			Point reached = reach(point, false);
			if (reached != null)
				return reached;
		}
		return null;
	}

	private static PlayerBotMoveController.FollowStep step(Point point) {
		return new PlayerBotMoveController.FollowStep(point.x(), point.y(), point.z());
	}

	private static double distance(Point a, Point b) {
		double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
