package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayDeque;
import java.util.Deque;

import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Owner breadcrumbs plus short, collision-tested steps; never teleports to escape combat obstacles. */
final class PlayerBotNavigation {
	record Point(float x, float y, float z) {}
	private final Creature bot;
	private final Deque<Point> trail = new ArrayDeque<>();
	private Point lastOwner;
	private final Deque<Point> route = new ArrayDeque<>();
	private Point routeGoal;
	private double closestGoalDistance;
	private long lastProgress, nextSearch;
	private int routeWorld, routeInstance;
	private static final java.util.concurrent.atomic.AtomicLong LAST_SEARCH = new java.util.concurrent.atomic.AtomicLong();
	private long nextMove;
	private int worldId, instanceId;
	private String status = "ready";
	private java.util.List<PlayerBotHazards.Area> hazards = java.util.List.of();

	PlayerBotNavigation(Creature bot) { this.bot = bot; }
	void hazards(java.util.List<PlayerBotHazards.Area> areas) { hazards = java.util.List.copyOf(areas); }
	boolean escapeHazards() {
		Point destination = PlayerBotHazards.escape(new Point(bot.getX(), bot.getY(), bot.getZ()), hazards, bot.getHeading());
		return destination != null && move(destination.x(), destination.y(), destination.z());
	}

	void record(Player owner) {
		if (owner.isFlying() || bot.isFlying()) { trail.clear(); lastOwner = null; return; }
		if (worldId != owner.getWorldId() || instanceId != owner.getInstanceId()) {
			trail.clear();
			lastOwner = null;
			worldId = owner.getWorldId();
			instanceId = owner.getInstanceId();
		}
		if (lastOwner == null || PositionUtil.getDistance(owner, lastOwner.x(), lastOwner.y(), lastOwner.z()) > 2) {
			lastOwner = new Point(owner.getX(), owner.getY(), owner.getZ());
			trail.addLast(lastOwner);
			while (trail.size() > 128) trail.removeFirst();
		}
	}

	boolean follow(Player owner, int formationSlot) {
		PlayerBotFormation.following(owner, (Player)bot, formationSlot);
		Point formation = PlayerBotFormation.destination(owner, (Player)bot, formationSlot);
		if (bot.isFlying()) {
			// Airborne follow uses the leader's altitude, never a ground breadcrumb.
			if (!PlayerBotFormation.needsFollow(owner,(Player)bot,formationSlot)) { stop(); return false; }
			boolean moved=move(formation.x(),formation.y(),formation.z());
			if(moved)PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,formation,hazards);
			return moved;
		}
		PlayerBotNavigationTrail.consume(trail, new Point(bot.getX(),bot.getY(),bot.getZ()));
		if (!PlayerBotFormation.needsFollow(owner,(Player)bot,formationSlot)) {
			stop();
			return false;
		}
		// Reached breadcrumbs have been consumed across the whole prefix, not only
		// at its head. An old unvisited formation point cannot pin the bot to its
		// current position while an opened doorway occludes newer owner samples.
		Point destination = trail.peekFirst();
		for (Point point : trail)
			if (PositionUtil.getDistance(bot,point.x(),point.y(),point.z())>=3 && GeoService.getInstance().canSee(bot, point.x(), point.y(), point.z(),
				com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) destination = point;
		if (GeoService.getInstance().canSee(bot, formation.x(),formation.y(),formation.z(),com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) destination=formation;
		if (destination == null) destination = new Point(owner.getX(), owner.getY(), owner.getZ());
		boolean moved=move(destination.x(), destination.y(), destination.z());
		if(moved)PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,formation,hazards);
		return moved;
	}

	boolean approach(Creature target, float range) {
		if (PositionUtil.isInRange(bot,target,range,false) && GeoService.getInstance().canSee(bot, target)) {
			stop();
			return false;
		}
		var point = PlayerBotCombatPosition.point(bot,target,range);
		if (!GeoService.getInstance().canSee(bot,target)) {
			if (range<8) return move(target.getX(),target.getY(),target.getZ());
			// Find a walkable firing position, preferring the normal range shell. A stale
			// opened-door sight mesh must not veto walking to the other side of the door.
			// Native sight from the target at the destination remains mandatory; casting is unchanged.
			long deadline = System.nanoTime()+6_000_000;
			for (double factor : bot.isFlying() ? new double[]{1} : new double[]{1,.85,.7,.55}) for (double angle : new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2}) {
				if (System.nanoTime()>=deadline) break;
				double dx=(point.x()-target.getX())*factor,dy=(point.y()-target.getY())*factor;
				float x=target.getX()+(float)(Math.cos(angle)*dx-Math.sin(angle)*dy);
				float y=target.getY()+(float)(Math.sin(angle)*dx+Math.cos(angle)*dy);
				Vector3f ground=bot.isFlying()
					? (GeoService.getInstance().canSee(bot,x,y,point.z(),com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE) ? new Vector3f(x,y,point.z()) : null)
					: GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),x,y);
				if (ground!=null && Float.isFinite(ground.z) && Math.hypot(ground.x-x,ground.y-y)<.3
					&& GeoService.getInstance().canSee(target,ground.x,ground.y,ground.z,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) return move(ground.x,ground.y,ground.z);
			}
		}
		return move(point.x(),point.y(),point.z());
	}

	boolean retreat(Creature target) {
		float dx = bot.getX() - target.getX(), dy = bot.getY() - target.getY();
		double distance = Math.max(0.01, Math.hypot(dx, dy));
		return move(bot.getX() + (float) (dx / distance * 5), bot.getY() + (float) (dy / distance * 5), bot.getZ());
	}

	boolean escape(PlayerBotTactics.Hazard hazard) {
		Point point = PlayerBotTactics.escape(bot.getX(), bot.getY(), bot.getZ(), hazard, bot.getHeading());
		return move(point.x(), point.y(), point.z());
	}

	boolean move(float targetX, float targetY, float targetZ) {
		if (bot.isCasting() || !bot.canPerformMove() || bot.isDead()) return false;
		long now = System.currentTimeMillis();
		if (now < nextMove) return bot.getMoveController().isInMove();
		nextMove = now + 200;
		if(bot instanceof Player player)PlayerBotFollowIntent.clear(player);
		if (bot.isFlying()) {
			route.clear();
			boolean moved = PlayerBotFlight.move(bot, targetX, targetY, targetZ, hazards);
			status = moved ? "flying" : "air route blocked";
			return moved;
		}
		Point goal = new Point(targetX, targetY, targetZ);
		if (!Float.isFinite(targetX) || !Float.isFinite(targetY) || !Float.isFinite(targetZ)) { stop(); return false; }
		double goalDistance = PositionUtil.getDistance(bot, targetX, targetY, targetZ);
		if (goalDistance < 0.15) { stop(); status="at navigation waypoint"; return false; }
		if (routeGoal == null || routeWorld != bot.getWorldId() || routeInstance != bot.getInstanceId()
			|| PositionUtil.getDistance(routeGoal.x(), routeGoal.y(), routeGoal.z(), targetX, targetY, targetZ) > 4) {
			route.clear(); routeGoal = goal; routeWorld = bot.getWorldId(); routeInstance = bot.getInstanceId();
			closestGoalDistance = goalDistance; lastProgress = now; nextSearch = 0;
		}
		if (goalDistance < closestGoalDistance - 0.5) { closestGoalDistance = goalDistance; lastProgress = now; }
		if (!route.isEmpty() && now - lastProgress > 4000) { route.clear(); lastProgress = now; }
		while (!route.isEmpty() && PositionUtil.getDistance(bot, route.peekFirst().x(), route.peekFirst().y(), route.peekFirst().z()) < 0.6) route.removeFirst();
		if (route.isEmpty() && goalDistance > 2 && now - lastProgress >= 1800 && now >= nextSearch) {
			long previous = LAST_SEARCH.get();
			// Share a search quota across players and their summons; bound each probe pass too.
			if (now - previous >= 100 && LAST_SEARCH.compareAndSet(previous, now)) {
				nextSearch = now + 3000;
				long deadline = System.nanoTime() + 6_000_000;
				Point start = new Point(bot.getX(), bot.getY(), bot.getZ());
				route.addAll(PlayerBotPathfinder.find(start, goal, (from, x, y) -> {
					Vector3f result = GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(), bot.getInstanceId(), from.x(), from.y(), from.z(), x, y);
					Point point = result == null ? null : new Point(result.getX(), result.getY(), result.getZ());
					return point != null && PlayerBotHazards.safePath(from, point, hazards) ? point : null;
				}, 128, () -> System.nanoTime() >= deadline));
			}
		}
		if (!route.isEmpty()) { Point waypoint = route.peekFirst(); targetX = waypoint.x(); targetY = waypoint.y(); targetZ = waypoint.z(); }
		double angle = Math.toDegrees(Math.atan2(targetY - bot.getY(), targetX - bot.getX()));
		float distance = (float) Math.min(Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2), PositionUtil.getDistance(bot, targetX, targetY, targetZ));
		Point best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int offset : new int[] { 0, 30, -30, 60, -60, 90, -90 }) {
			double radians = Math.toRadians(angle + offset);
			float x = bot.getX() + (float) Math.cos(radians) * distance;
			float y = bot.getY() + (float) Math.sin(radians) * distance;
			Vector3f collision = GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(), bot.getInstanceId(), bot.getX(), bot.getY(), bot.getZ(), x, y);
			float z;
			if (collision != null) {
				x = collision.getX(); y = collision.getY(); z = collision.getZ();
			} else {
				z = GeoService.getInstance().getZ(bot.getWorldId(), x, y, bot.getZ(), bot.getInstanceId());
				if (!Float.isFinite(z)) z = bot.getZ();
			}
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
				|| Math.abs(z - bot.getZ()) > distance + 0.5 || Math.hypot(x - bot.getX(), y - bot.getY()) < 0.15) continue;
			Point candidate = new Point(x, y, z);
			if (!PlayerBotHazards.safePath(new Point(bot.getX(), bot.getY(), bot.getZ()), candidate, hazards)) continue;
			double score = -PositionUtil.getDistance(x, y, z, targetX, targetY, targetZ) - Math.abs(offset) * 0.015;
			if (score > bestScore) { bestScore = score; best = new Point(x, y, z); }
		}
		if (best == null) { route.clear(); status = "blocked by geometry"; stop(); return false; }
		status = route.isEmpty() ? "moving" : "following local route";
		byte heading = (byte) Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(best.y() - bot.getY(), best.x() - bot.getX())) / 3), 120);
		synchronized(bot.getMoveController()) {
			if(bot instanceof Player player)PlayerBotFollowIntent.clear(player);
			bot.getMoveController().setNewDirection(best.x(), best.y(), best.z(), heading);
			bot.getMoveController().startMovingToDestination();
		}
		return true;
	}

	void stop() { if (bot.getMoveController().isInMove()) bot.getMoveController().abortMove(); }
	String status() { return status; }
}
