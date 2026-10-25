package com.aionemu.gameserver.services.playerbot;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Transient corpse candidates, adapted from mod-playerbots LootObjectStack at
 * commit 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * The donor's GUID deduplication, 30-second lifetime and 200-candidate reset
 * behavior are retained. World and instance ownership belong to the caller.
 */
final class PlayerBotLootMemory<T> {

    private static final int MAX_ENTRIES = 200;
    private static final long LIFETIME_MILLIS = 30_000;

    record Entry<T>(int id, T value, long added) { }

    private final LinkedHashMap<Integer, Entry<T>> candidates = new LinkedHashMap<>();

    void add(int id, T value, long now) {
        if (candidates.size() >= MAX_ENTRIES) {
            prune(now);
            if (candidates.size() >= MAX_ENTRIES)
                candidates.clear();
        }
        if (candidates.containsKey(id))
            return;
        candidates.put(id, new Entry<>(id, value, now));
    }

    void prune(long now) {
        candidates.values().removeIf(entry -> now - entry.added() >= LIFETIME_MILLIS);
    }

    List<Entry<T>> entries(long now) {
        prune(now);
        return List.copyOf(candidates.values());
    }

    void remove(int id) {
        candidates.remove(id);
    }

    void clear() {
        candidates.clear();
    }

    int size() {
        return candidates.size();
    }
}
