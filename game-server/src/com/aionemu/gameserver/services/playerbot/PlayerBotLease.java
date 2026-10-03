package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds a character through loading, play and final save; shared by normal logins and bots. */
public final class PlayerBotLease implements AutoCloseable {
	private static final Map<Integer, PlayerBotLease> leases = new ConcurrentHashMap<>();
	private final int characterId;
	private PlayerBotLease(int characterId) { this.characterId = characterId; }

	public static PlayerBotLease acquire(int characterId) {
		PlayerBotLease lease = new PlayerBotLease(characterId);
		return leases.putIfAbsent(characterId, lease) == null ? lease : null;
	}

	public static boolean isReserved(int characterId) { return leases.containsKey(characterId); }

	@Override
	public void close() { leases.remove(characterId, this); }
}
