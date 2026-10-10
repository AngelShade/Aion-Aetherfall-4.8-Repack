package com.aionemu.gameserver.world.container;

import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.exceptions.DuplicateAionObjectException;

/**
 * Container for storing Players by objectId and name.
 * 
 * @author -Nemesiss-, Neon
 */
public class PlayerContainer implements Iterable<Player> {

	private final ConcurrentHashMap<Integer, Player> playersById = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Player> playersByName = new ConcurrentHashMap<>();
	// Index keys are independent of display names and the host's default locale.
	// Mutations are serialized across both indexes; reads remain lock-free.
	private final Map<Integer, String> indexedNames = new HashMap<>();

	public synchronized void add(Player player) {
		String key = nameKey(player.getName());
		Player existing = playersById.get(player.getObjectId());
		if (existing == null) existing = playersByName.get(key);
		if (existing != null)
			throw new DuplicateAionObjectException(player, existing);
		playersById.put(player.getObjectId(), player);
		playersByName.put(key, player);
		indexedNames.put(player.getObjectId(), key);
	}

	public synchronized void remove(Player player) {
		if (playersById.remove(player.getObjectId(), player)) {
			String key = indexedNames.remove(player.getObjectId());
			if (key != null) playersByName.remove(key, player);
		}
	}

	public Player get(int objectId) {
		return playersById.get(objectId);
	}

	public Player get(String name) {
		return name == null || name.isEmpty() ? null : playersByName.get(nameKey(name));
	}

	@Override
	public Iterator<Player> iterator() {
		return playersById.values().iterator();
	}

	public Collection<Player> getAllPlayers() {
		return playersById.values().stream().filter(p -> p != null).collect(Collectors.toList()); // ensure there are no null values (due to concurrent object removal)
	}

	public synchronized void updateCachedPlayerName(String oldName, Player player) {
		// A late rename notification must not register an offline/replaced actor.
		if (playersById.get(player.getObjectId()) != player) return;
		String key = nameKey(player.getName());
		Player existing = playersByName.get(key);
		if (existing != null && existing != player)
			throw new DuplicateAionObjectException(player, existing);
		String previous = indexedNames.get(player.getObjectId());
		playersByName.put(key, player);
		if (!key.equals(previous)) playersByName.remove(previous, player);
		indexedNames.put(player.getObjectId(), key);
	}

	private static String nameKey(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
