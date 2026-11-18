package com.aionemu.gameserver.services.dynamicevents;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.DynamicEventConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.World;

/**
 * What an event's outcome leaves on the road (Guild Wars 2: "if monsters are successful in raiding an area, they may become
 * strong enough to occupy" it, and it stays so until players act). A failed chain leaves raiders holding the road where it
 * began: the beat's Warden falls back to the far end, merchants turn around before it, and the next player of the road's
 * faction who comes near starts the retake. Held in memory, so a restart clears it, as a Guild Wars 2 server reset does.
 */
public final class EventWorld {

	private static final Logger log = LoggerFactory.getLogger(EventWorld.class);
	/** Metres around a hold that merchants avoid and that counts as the held stretch. */
	public static final float BLOCK_RADIUS = 60;

	static final class Hold {
		final DynamicEvent.Anchor anchor;
		final EventSpawns.Pool pool;
		final Set<Integer> holders = ConcurrentHashMap.newKeySet();
		final long until;
		volatile boolean retaking;

		Hold(DynamicEvent.Anchor anchor, EventSpawns.Pool pool, long until) {
			this.anchor = anchor;
			this.pool = pool;
			this.until = until;
		}
	}

	private static final Map<String, Hold> HOLDS = new ConcurrentHashMap<>();

	public static boolean held(String beat) {
		return HOLDS.containsKey(beat);
	}

	static Hold hold(String beat) {
		return HOLDS.get(beat);
	}

	static int count() {
		return HOLDS.size();
	}

	/** Where the raiders hold this beat, or null. */
	public static DynamicEvent.Spot heldSpot(String beat) {
		Hold h = HOLDS.get(beat);
		return h == null ? null : h.anchor.start();
	}

	/** Whether a point lies on a held stretch of road (merchants turn before it). Holds are few; no world scan. */
	public static boolean blocked(int map, int instance, float x, float y, float z) {
		for (Hold h : HOLDS.values())
			if (h.anchor.map() == map && h.anchor.instance() == instance && h.anchor.start().distance(x, y, z) <= BLOCK_RADIUS)
				return true;
		return false;
	}

	/** A failed chain: the raiders take the road where it began and post guards on it. */
	static void occupy(DynamicEvent e, long now) {
		free(e.anchor.beat(), "renewed");
		var hold = new Hold(e.anchor, e.pool, now + Math.max(1, DynamicEventConfig.OCCUPATION_MINUTES) * 60_000L);
		var random = new Random(e.key);
		var spots = e.anchor.roadSpots().isEmpty() ? List.of(e.anchor.start()) : e.anchor.roadSpots();
		for (int i = 0; i < Math.max(1, DynamicEventConfig.OCCUPATION_HOLDERS); i++) {
			var template = e.pool.normal().get(random.nextInt(e.pool.normal().size()));
			Npc npc = EventSpawns.spawn(e.anchor, template, spots.get(random.nextInt(spots.size())), 5, null);
			if (npc != null)
				hold.holders.add(npc.getObjectId());
		}
		HOLDS.put(e.anchor.beat(), hold);
		log.info("Dynamic events: raiders hold {} on map {} with {} guards for {} minutes", e.anchor.road(), e.anchor.map(), hold.holders.size(),
			DynamicEventConfig.OCCUPATION_MINUTES);
	}

	/** The road is free again: remaining guards leave the field. */
	static void free(String beat, String why) {
		Hold h = HOLDS.remove(beat);
		if (h == null)
			return;
		for (int id : h.holders)
			if (World.getInstance().findVisibleObject(id) instanceof Npc npc && npc.isSpawned() && !npc.isDead())
				npc.getController().delete();
		log.info("Dynamic events: {} is no longer held ({})", h.anchor.road(), why);
	}

	/** Guards still standing, handed to the retake event as its first monsters. */
	static List<Integer> standing(Hold h) {
		var alive = new ArrayList<Integer>();
		for (int id : h.holders)
			if (World.getInstance().findVisibleObject(id) instanceof Npc npc && npc.isSpawned() && !npc.isDead())
				alive.add(id);
		return alive;
	}

	/** Each second: an expired hold dissolves, and a player of the road's faction near a hold starts its retake. */
	static void tick(long now) {
		for (var entry : HOLDS.entrySet()) {
			Hold h = entry.getValue();
			if (h.retaking)
				continue;
			if (now >= h.until) {
				free(entry.getKey(), "expired");
				continue;
			}
			var guards = standing(h);
			if (guards.isEmpty()) {
				free(entry.getKey(), "every guard fell");
				continue;
			}
			Player near = null;
			if (World.getInstance().findVisibleObject(guards.getFirst()) instanceof Npc eye) {
				Player[] found = { null };
				eye.getKnownList().forEachPlayer(p -> {
					if (found[0] == null && !p.isPlayerBot() && p.isOnline() && !p.isDead() && p.getRace() == h.anchor.race()
						&& h.anchor.start().distance(p.getX(), p.getY(), p.getZ()) <= DynamicEventConfig.RADIUS)
						found[0] = p;
				});
				near = found[0];
			}
			if (near != null && DynamicEventService.retake(h))
				h.retaking = true;
		}
	}

	/** The retake ended. Won: the road is free. Lost: the hold stands again with fresh guards (DynamicEventService occupies). */
	static void retaken(String beat, boolean won) {
		Hold h = HOLDS.get(beat);
		if (h == null)
			return;
		if (won)
			free(beat, "retaken");
		else
			h.retaking = false;
	}

	private EventWorld() {
	}
}
