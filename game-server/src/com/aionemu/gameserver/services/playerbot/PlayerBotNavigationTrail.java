package com.aionemu.gameserver.services.playerbot;

import java.util.Deque;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Ordered leader footsteps; seeing an upper floor is not reaching it. */
final class PlayerBotNavigationTrail {
	static void consume(Deque<Point> trail, Point position) {
		Point reached = null;
		for (var points = trail.descendingIterator(); points.hasNext();) {
			Point point = points.next();
			if (Math.hypot(point.x()-position.x(),point.y()-position.y()) < 1
				&& Math.abs(point.z()-position.z()) < .75) {
				reached = point; break;
			}
		}
		if (reached != null) while (!trail.isEmpty()) if (trail.removeFirst() == reached) break;
	}
	static Point target(Deque<Point> trail,Point bot,Point formation,boolean formationVisible) {
		// Restore normal formation only on its floor and with a clear approach.
		// In corridors/structures, retain the actual ordered entrance/turn samples.
		if(trail.isEmpty() || formationVisible && Math.abs(bot.z()-formation.z())<=1.75)return formation;
		return trail.peekFirst();
	}
	/** Recorded footsteps are candidate corners, not geometry proof. The native
	 * mover must prove each handoff before consuming its remaining time allowance. */
	static Point append(Deque<Point> corridor, Deque<Point> trail, Point after, double horizon) {
		if (corridor.isEmpty() || after == null) return after;
		double distance = 0;
		Point previous = null;
		for (Point point : corridor) {
			if (previous != null) distance += distance(previous, point);
			previous = point;
		}
		boolean found = false;
		for (Point point : trail) {
			if (!found) { found = point.equals(after); continue; }
			double edge = distance(previous, point);
			if (corridor.size() >= 16 || distance + edge > horizon) break;
			corridor.addLast(point); previous = after = point; distance += edge;
		}
		return after;
	}
	private static double distance(Point a, Point b) {
		double dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();
		return Math.sqrt(dx*dx+dy*dy+dz*dz);
	}
	private PlayerBotNavigationTrail() {}
}
