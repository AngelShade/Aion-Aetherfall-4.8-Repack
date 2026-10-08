package com.aionemu.gameserver.services.playerbot;

import java.util.Deque;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Consume the latest reached breadcrumb even if a formation never visited the old trail head. */
final class PlayerBotNavigationTrail {
	static void consume(Deque<Point> trail, Point position) {
		Point reached = null;
		for (var points = trail.descendingIterator(); points.hasNext();) {
			Point point = points.next();
			if (Math.sqrt(Math.pow(point.x()-position.x(),2)+Math.pow(point.y()-position.y(),2)+Math.pow(point.z()-position.z(),2)) < 3) {
				reached = point; break;
			}
		}
		if (reached != null) while (!trail.isEmpty()) if (trail.removeFirst() == reached) break;
	}
	private PlayerBotNavigationTrail() {}
}
