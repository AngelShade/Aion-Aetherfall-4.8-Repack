package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

public final class PlayerBotConfig {
	@Property(key = "gameserver.playerbots.enable", defaultValue = "true")
	public static boolean ENABLED;
	@Property(key = "gameserver.playerbots.summon.enable", defaultValue = "true")
	public static boolean SUMMON_ENABLED;
	@Property(key = "gameserver.playerbots.max_per_owner", defaultValue = "5")
	public static int MAX_PER_OWNER;
	@Property(key = "gameserver.playerbots.max_active", defaultValue = "100")
	public static int MAX_ACTIVE;
	@Property(key = "gameserver.playerbots.tick_ms", defaultValue = "400")
	public static int TICK_MS;
	@Property(key = "gameserver.playerbots.generated.enable", defaultValue = "true")
	public static boolean GENERATED_ENABLED;
	@Property(key = "gameserver.playerbots.generated.max_per_account", defaultValue = "20")
	public static int GENERATED_MAX_PER_ACCOUNT;
	@Property(key = "gameserver.playerbots.level_difference", defaultValue = "10")
	public static int LEVEL_DIFFERENCE;
	@Property(key = "gameserver.playerbots.save_seconds", defaultValue = "120")
	public static int SAVE_SECONDS;
	@Property(key = "gameserver.playerbots.instance_follow", defaultValue = "false")
	public static boolean INSTANCE_FOLLOW;
	@Property(key = "gameserver.playerbots.allowed_instance_maps", defaultValue = "")
	public static java.util.Set<Integer> ALLOWED_INSTANCE_MAPS;
	@Property(key = "gameserver.playerbots.mission_distance", defaultValue = "180")
	public static int MISSION_DISTANCE;
}
