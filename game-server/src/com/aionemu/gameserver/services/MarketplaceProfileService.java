package com.aionemu.gameserver.services;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Character-specific shop data. Atomic files survive GameServer restarts. */
public final class MarketplaceProfileService {

	private static final Path DIRECTORY = Path.of("data/marketplace");
	private static final int HISTORY_LIMIT = 200;
	private static final int FAVORITES_LIMIT = 500;
	private static final Map<Integer, CachedProfile> cache = new LinkedHashMap<>(32, .75f, true);

	private MarketplaceProfileService() {
	}

	public static synchronized Snapshot snapshot(int playerId) throws IOException {
		if (playerId <= 0) throw new IllegalArgumentException("Invalid character ID");
		Path file = DIRECTORY.resolve(playerId + ".properties");
		long modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
		long size = modified < 0 ? 0 : Files.size(file);
		CachedProfile cached = cache.get(playerId);
		if (cached != null && cached.modified() == modified && cached.size() == size) return cached.snapshot();
		Properties data = load(playerId);
		Set<Integer> favorites = new HashSet<>();
		for (String id : data.getProperty("favorites", "").split(",")) {
			if (!id.isBlank()) favorites.add(Integer.parseInt(id));
		}
		List<Purchase> purchases = new ArrayList<>();
		int count = Math.min(HISTORY_LIMIT, Integer.parseInt(data.getProperty("history.count", "0")));
		for (int i = 0; i < count; i++) {
			String prefix = "history." + i + ".";
			purchases.add(new Purchase(data.getProperty(prefix + "id"), Long.parseLong(data.getProperty(prefix + "time")),
				Integer.parseInt(data.getProperty(prefix + "item")), data.getProperty(prefix + "name"),
				Long.parseLong(data.getProperty(prefix + "quantity")), Long.parseLong(data.getProperty(prefix + "price"))));
		}
		Snapshot snapshot = new Snapshot(Set.copyOf(favorites), List.copyOf(purchases));
		cache.put(playerId, new CachedProfile(modified, size, snapshot));
		if (cache.size() > 256) cache.remove(cache.keySet().iterator().next());
		return snapshot;
	}

	public static synchronized void setFavorite(int playerId, int itemId, boolean selected) throws IOException {
		Snapshot current = snapshot(playerId);
		Set<Integer> favorites = new HashSet<>(current.favorites());
		if (selected) {
			if (favorites.size() >= FAVORITES_LIMIT && !favorites.contains(itemId))
				throw new IOException("Favorites limit reached");
			favorites.add(itemId);
		} else favorites.remove(itemId);
		save(playerId, new Snapshot(favorites, current.purchases()));
	}

	public static synchronized void recordPurchase(int playerId, Purchase purchase) throws IOException {
		Snapshot current = snapshot(playerId);
		if (current.purchases().stream().anyMatch(entry -> entry.id().equals(purchase.id()))) return;
		List<Purchase> purchases = new ArrayList<>(current.purchases());
		purchases.add(purchase);
		purchases.sort(java.util.Comparator.comparingLong(Purchase::time).reversed());
		if (purchases.size() > HISTORY_LIMIT) purchases = new ArrayList<>(purchases.subList(0, HISTORY_LIMIT));
		save(playerId, new Snapshot(current.favorites(), purchases));
	}

	/** Import successful purchases recorded before history was added, once. */
	public static synchronized void importLoggedPurchases(Path log, java.util.function.IntFunction<String> itemName) throws IOException {
		Path marker = DIRECTORY.resolve(".history-import-v1");
		if (Files.exists(marker) || !Files.isRegularFile(log)) return;
		var pattern = java.util.regex.Pattern.compile("^(\\S+) .*MarketplaceService - Marketplace: Player \\[id=(\\d+), name=.*?\\] bought item (\\d+) x(\\d+) for (\\d+) Kinah$");
		for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
			var match = pattern.matcher(line);
			if (!match.matches()) continue;
			int playerId = Integer.parseInt(match.group(2));
			int itemId = Integer.parseInt(match.group(3));
			long time = java.time.OffsetDateTime.parse(match.group(1).replace(',', '.')).toInstant().toEpochMilli();
			recordPurchase(playerId, new Purchase("log:" + match.group(1) + ":" + itemId, time,
				itemId, itemName.apply(itemId), Long.parseLong(match.group(4)), Long.parseLong(match.group(5))));
		}
		Files.createDirectories(DIRECTORY);
		Files.writeString(marker, "Imported successful purchases from " + log.getFileName(), StandardCharsets.UTF_8);
	}

	private static Properties load(int playerId) throws IOException {
		if (playerId <= 0) throw new IllegalArgumentException("Invalid character ID");
		Properties data = new Properties();
		Path file = DIRECTORY.resolve(playerId + ".properties");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { data.load(reader); }
		}
		return data;
	}

	private static void save(int playerId, Snapshot profile) throws IOException {
		Properties data = new Properties();
		data.setProperty("favorites", profile.favorites().stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
		data.setProperty("history.count", String.valueOf(profile.purchases().size()));
		for (int i = 0; i < profile.purchases().size(); i++) {
			Purchase purchase = profile.purchases().get(i);
			String prefix = "history." + i + ".";
			data.setProperty(prefix + "id", purchase.id());
			data.setProperty(prefix + "time", String.valueOf(purchase.time()));
			data.setProperty(prefix + "item", String.valueOf(purchase.itemId()));
			data.setProperty(prefix + "name", purchase.name());
			data.setProperty(prefix + "quantity", String.valueOf(purchase.quantity()));
			data.setProperty(prefix + "price", String.valueOf(purchase.price()));
		}
		Files.createDirectories(DIRECTORY);
		Path temporary = DIRECTORY.resolve(playerId + "-" + UUID.randomUUID() + ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { data.store(writer, "Marketplace character data"); }
			try { Files.move(temporary, DIRECTORY.resolve(playerId + ".properties"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException e) { Files.move(temporary, DIRECTORY.resolve(playerId + ".properties"), StandardCopyOption.REPLACE_EXISTING); }
			cache.remove(playerId);
		} finally { Files.deleteIfExists(temporary); }
	}

	public record Snapshot(Set<Integer> favorites, List<Purchase> purchases) {
	}

	public record Purchase(String id, long time, int itemId, String name, long quantity, long price) {
	}
	private record CachedProfile(long modified, long size, Snapshot snapshot) {
	}
}
