package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;

import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Per-character settings, independent of game progress tables and shared account storage. */
final class PlayerBotPreferences {
	record Values(Role role, boolean area, boolean supplies, boolean gear, boolean loot, boolean questing) {
		Values { Objects.requireNonNull(role); }
		Values(Role role, boolean area, boolean supplies, boolean gear, boolean loot) { this(role, area, supplies, gear, loot, false); }
	}
	private final Path directory;
	PlayerBotPreferences(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

	Values load(int account, int character, Role defaultRole) throws IOException {
		Path path = path(account, character);
		Properties properties = PlayerBotMetadata.load(account, character, "preferences", path);
		if (properties.isEmpty()) return new Values(defaultRole, false, true, true, false, true);
		try {
			if (Integer.parseInt(properties.getProperty("account")) != account
				|| Integer.parseInt(properties.getProperty("character")) != character) throw new IllegalArgumentException("Settings owner mismatch");
			properties.putIfAbsent("loot", "false");
			properties.putIfAbsent("questing", "false");
			return new Values(Role.valueOf(properties.getProperty("role")), flag(properties, "area"), flag(properties, "supplies"), flag(properties, "gear"), flag(properties, "loot"), flag(properties, "questing"));
		} catch (RuntimeException e) { throw new IOException("Invalid companion settings: " + path.getFileName(), e); }
	}

	void save(int account, int character, Values values) throws IOException {
		Path path = path(account, character);
		Properties properties = new Properties();
		properties.setProperty("account", Integer.toString(account)); properties.setProperty("character", Integer.toString(character));
		properties.setProperty("role", values.role().name()); properties.setProperty("area", Boolean.toString(values.area()));
		properties.setProperty("supplies", Boolean.toString(values.supplies())); properties.setProperty("gear", Boolean.toString(values.gear()));
		properties.setProperty("loot", Boolean.toString(values.loot()));
		properties.setProperty("questing", Boolean.toString(values.questing()));
		PlayerBotMetadata.save(account, character, "preferences", properties);
	}

	private Path path(int account, int character) {
		if (account <= 0 || character <= 0) throw new IllegalArgumentException("Invalid companion owner or character ID");
		return directory.resolve("character-" + character + ".properties");
	}
	private static boolean flag(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("Invalid " + key);
		return Boolean.parseBoolean(value);
	}
}
