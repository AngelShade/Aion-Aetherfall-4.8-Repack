package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Bounded local A* over native ground probes. This is not a world travel graph or a navmesh. */
final class PlayerBotPathfinder {
	interface Ground { Point step(Point from, float x, float y); }
	private record Cell(int x, int y, int height) {}
	private record Node(Cell cell, Point point, Node parent, double cost, double estimate, long sequence) {}
	static List<Point> find(Point start, Point goal, Ground ground, int budget, BooleanSupplier expired) {
		if (!finite(start) || !finite(goal) || budget <= 0) return List.of();
		var open = new PriorityQueue<Node>(Comparator.comparingDouble(Node::estimate).thenComparingLong(Node::sequence));
		var best = new HashMap<Cell, Double>();
		var startCell = new Cell(0, 0, Math.round(start.z()));
		long sequence = 0;
		open.add(new Node(startCell, start, null, 0, distance(start, goal), sequence++)); best.put(startCell, 0.0);
		int visited = 0;
		while (!open.isEmpty() && visited < budget && !expired.getAsBoolean()) {
			Node current = open.remove();
			if (current.cost() > best.getOrDefault(current.cell(), Double.POSITIVE_INFINITY)) continue;
			visited++;
			if (distance(current.point(), goal) <= 3) {
				Point end = ground.step(current.point(), goal.x(), goal.y());
				if (validEdge(current.point(), end, goal.x(), goal.y()) && distance(end, goal) < 1) {
					var route = new ArrayList<Point>(); route.add(end);
					for (Node n = current; n.parent() != null; n = n.parent()) route.add(n.point());
					Collections.reverse(route); return List.copyOf(route);
				}
			}
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0 || expired.getAsBoolean()) continue;
				int cx = current.cell().x() + dx, cy = current.cell().y() + dy;
				if (Math.abs(cx) > 12 || Math.abs(cy) > 12) continue;
				float x = start.x() + cx * 2, y = start.y() + cy * 2;
				Point point = ground.step(current.point(), x, y);
				if (!validEdge(current.point(), point, x, y)) continue;
				Cell cell = new Cell(cx, cy, Math.round(point.z()));
				double cost = current.cost() + distance(current.point(), point);
				if (cost >= best.getOrDefault(cell, Double.POSITIVE_INFINITY)) continue;
				best.put(cell, cost);
				open.add(new Node(cell, point, current, cost, cost + distance(point, goal), sequence++));
			}
		}
		return List.of();
	}
	private static boolean validEdge(Point from, Point point, float x, float y) {
		return finite(point) && Math.hypot(point.x() - x, point.y() - y) <= 0.3
			&& Math.abs(point.z() - from.z()) <= Math.hypot(x - from.x(), y - from.y()) + 0.5;
	}
	private static boolean finite(Point p) { return p != null && Float.isFinite(p.x()) && Float.isFinite(p.y()) && Float.isFinite(p.z()); }
	private static double distance(Point a, Point b) { return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2)); }
	private PlayerBotPathfinder() {}
}
