package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.geoEngine.bounding.BoundingBox;
import com.aionemu.gameserver.geoEngine.collision.*;
import com.aionemu.gameserver.geoEngine.math.*;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.*;
import com.aionemu.gameserver.geoEngine.scene.DespawnableNode.DespawnableType;

/** Bot-only ground probes. Door state is read on every probe, never inferred from combat or a map ID. */
public final class PlayerBotGroundNavigation {
	private record DoorPair(DespawnableNode closed, DespawnableNode opened) {}
	private static final Map<GeoMap, Map<Geometry, DoorPair>> DOORS = new ConcurrentHashMap<>();
	private static final float STEP = 0.35f;

	public static Vector3f walk(GeoMap map, int instance, float x, float y, float z, float targetX, float targetY) {
		return walk(map, instance, x, y, z, targetX, targetY, z);
	}

	public static Vector3f walk(GeoMap map, int instance, float x, float y, float z, float targetX, float targetY, float targetZ) {
		Vector3f start = new Vector3f(x, y, z);
		if (map == null || !finite(start) || !Float.isFinite(targetX) || !Float.isFinite(targetY) || !Float.isFinite(targetZ)) return start;
		double distance = Math.hypot(targetX - x, targetY - y);
		// Native GeoMap movement accepts support down to z-1.5 at the origin.
		// Recruitment/transfer copies a client position before headless gravity runs.
		float floor = PlayerBotGroundSupport.floor(map, instance, x, y, z + 0.6f, z - 1.5f);
		if (!Float.isFinite(floor) && targetZ < z - 1.25f) {
			// A headless actor can inherit an elevated spawn/transfer position without
			// receiving client gravity updates. Use the movement intent's altitude to
			// find its nearest support below, instead of discarding Z and retrying XY
			// forever. This applies only at the starting position; walking edges retain
			// their narrow step/drop windows and cannot cross cliffs or missing floors.
			floor = PlayerBotGroundSupport.floor(map, instance, x, y, z + 0.6f, targetZ - 1.25f);
			if (Float.isFinite(floor)) {
				// Do not skip a steep/closed physical surface rejected as a floor, or
				// pull the actor through an intervening ceiling, wall or door.
				if (!clear(map, instance, new Vector3f(x, y, z), new Vector3f(x, y, floor + 0.05f))) return start;
				for (float height : new float[]{0.75f, 1.5f})
					if (!clear(map, instance, new Vector3f(x, y, z + height), new Vector3f(x, y, floor + height))) return start;
				start.z = floor;
			}
		}
		// A center ray alone can report success through a missing floor or beside
		// an object touching the actor's body. Validate the walking strip below.
		if (!Float.isFinite(floor)) return start;
		Vector3f current = new Vector3f(x, y, floor);
		// Vertical correction is a walking destination, not a relocation. Reject a body obstruction during it.
		for(float height:new float[]{0.75f,1.5f})
			if (!clear(map, instance, new Vector3f(x, y, z + height), new Vector3f(x, y, floor + height))) return start;
		if (distance < 0.001) return current;
		float dx = (float)((targetX - x) / distance), dy = (float)((targetY - y) / distance);
		int steps = Math.min(128, (int)Math.ceil(distance / STEP));
		for (int i = 1; i <= steps; i++) {
			float travelled = (float)Math.min(distance, i * STEP);
			float nextX = x + dx * travelled, nextY = y + dy * travelled;
			float ground = PlayerBotGroundSupport.floor(map, instance, nextX, nextY, current.z + 0.6f, current.z - 0.85f);
			float horizontal = (float)Math.hypot(nextX - current.x, nextY - current.y);
			if (!Float.isFinite(ground) || ground - current.z > horizontal + 0.15f || current.z - ground > horizontal + 0.3f) break;
			Vector3f next = new Vector3f(nextX, nextY, ground);
			boolean pass = true;
			for (float side : new float[]{-0.3f, 0, 0.3f}) for (float height : new float[]{0.75f, 1.5f})
				if (!clear(map, instance, new Vector3f(current.x-dy*side, current.y+dx*side, current.z + height), new Vector3f(next.x-dy*side, next.y+dx*side, next.z + height))
					|| !clear(map, instance, new Vector3f(next.x,next.y,next.z+height),new Vector3f(next.x-dy*side,next.y+dx*side,next.z+height))) { pass = false; break; }
			if (!pass) break;
			current = next;
		}
		return current;
	}

	/** Population landing requires supported feet, body clearance and a walkable exit. */
	public static boolean landing(GeoMap map,int instance,float x,float y,float z) {
		if(map==null || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))return false;
		float floor=PlayerBotGroundSupport.floor(map,instance,x,y,z+0.2f,z-0.3f);
		if(!Float.isFinite(floor) || Math.abs(floor-z)>0.3f)return false;
		for(int direction=0;direction<8;direction++) {
			float dx=(float)Math.cos(direction*Math.PI/4),dy=(float)Math.sin(direction*Math.PI/4);
			for(float height:new float[]{0.75f,1.5f})if(!clear(map,instance,new Vector3f(x,y,z+height),new Vector3f(x+dx*0.4f,y+dy*0.4f,z+height)))return false;
		}
		for(int direction=0;direction<8;direction++) {
			float tx=x+2*(float)Math.cos(direction*Math.PI/4),ty=y+2*(float)Math.sin(direction*Math.PI/4);
			var end=walk(map,instance,x,y,z,tx,ty);
			if(Math.hypot(end.x-tx,end.y-ty)<0.3)return true;
		}
		return false;
	}

	private static boolean clear(GeoMap map, int instance, Vector3f from, Vector3f to) {
		if (from.distance(to) < 0.001f) return true;
		for (CollisionResult hit : map.getPlayerBotCollisions(from, to.x, to.y, to.z, instance, CollisionIntention.DEFAULT_COLLISIONS.getId(), IgnoreProperties.ANY_RACE)) {
			DoorPair pair = DOORS.computeIfAbsent(map, PlayerBotGroundNavigation::doors).get(hit.getGeometry());
			if (pair == null || pair.closed().isActive(instance) || !pair.opened().isActive(instance)) return false;
		}
		return true;
	}

	private static Map<Geometry, DoorPair> doors(GeoMap map) {
		Map<Integer, DespawnableNode[]> states = new HashMap<>();
		collect(map, states);
		Map<Geometry, DoorPair> result = new IdentityHashMap<>();
		for (DespawnableNode[] pair : states.values()) {
			if (pair[0] == null || pair[1] == null) continue;
			List<Geometry> closed = geometries(pair[0]), opened = geometries(pair[1]);
			for (Geometry open : opened) for (Geometry shut : closed)
				// Equal mesh triangles AND transform prove the exporter aliased closed/open collision.
				// A correctly moved opened leaf, a door frame or a bridge remains an obstacle.
				if (open.getWorldMatrix().equals(shut.getWorldMatrix()) && leaf(open.getMesh()) && sameMesh(open.getMesh(), shut.getMesh()))
					result.put(open, new DoorPair(pair[0], pair[1]));
		}
		return Collections.unmodifiableMap(result);
	}
	private static boolean sameMesh(Mesh a, Mesh b) {
		if (a == b) return true;
		if (a.getTriangleCount() != b.getTriangleCount() || a.getTriangleCount() > 2048) return false;
		return triangles(a).equals(triangles(b));
	}
	private static List<String> triangles(Mesh mesh) {
		List<String> result = new ArrayList<>();
		Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
		for (int i=0; i<mesh.getTriangleCount(); i++) {
			mesh.getTriangle(i,a,b,c);
			List<String> points = new ArrayList<>();
			for (Vector3f point : new Vector3f[]{a,b,c}) points.add(Float.toHexString(point.x == 0 ? 0 : point.x)+","+Float.toHexString(point.y == 0 ? 0 : point.y)+","+Float.toHexString(point.z == 0 ? 0 : point.z));
			Collections.sort(points); result.add(String.join(";",points));
		}
		Collections.sort(result); return result;
	}

	private static void collect(Node node, Map<Integer, DespawnableNode[]> states) {
		if (node instanceof DespawnableNode door && (door.type == DespawnableType.DOOR_STATE1 || door.type == DespawnableType.DOOR_STATE2))
			states.computeIfAbsent(door.id, key -> new DespawnableNode[2])[door.type == DespawnableType.DOOR_STATE1 ? 0 : 1] = door;
		for (Spatial child : node.getChildren()) if (child instanceof Node nested) collect(nested, states);
	}
	private static List<Geometry> geometries(Node node) {
		List<Geometry> result = new ArrayList<>();
		for (Spatial child : node.getChildren()) {
			if (child instanceof Geometry geometry) result.add(geometry);
			else if (child instanceof Node nested) result.addAll(geometries(nested));
		}
		return result;
	}
	private static boolean leaf(Mesh mesh) {
		if (!(mesh.getBound() instanceof BoundingBox box)) return false;
		boolean thinX = box.getXExtent() < box.getYExtent();
		float thin = thinX ? box.getXExtent() : box.getYExtent(), wide = thinX ? box.getYExtent() : box.getXExtent();
		if (thin > 0.25f || wide < 0.4f || box.getZExtent() < 0.6f) return false;
		// A frame can share both states legitimately. Require solid leaf coverage across its middle.
		for (float offset : new float[]{-0.5f, 0, 0.5f}) {
			Vector3f origin = box.getCenter().clone(), direction = new Vector3f(thinX ? 1 : 0, thinX ? 0 : 1, 0);
			if (thinX) { origin.x -= thin + 0.1f; origin.y += offset * wide; }
			else { origin.y -= thin + 0.1f; origin.x += offset * wide; }
			Ray ray = new Ray(origin, direction); ray.setLimit(2 * thin + 0.2f);
			CollisionResults hits = new CollisionResults(CollisionIntention.PHYSICAL.getId(), 0);
			if (mesh.collideWith(ray, new Matrix4f(), box, hits) == 0) return false;
		}
		return true;
	}
	private static boolean finite(Vector3f point) { return point != null && Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z); }
	private PlayerBotGroundNavigation() {}
}
