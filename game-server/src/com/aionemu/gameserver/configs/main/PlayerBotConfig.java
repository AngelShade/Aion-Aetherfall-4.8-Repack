package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

public final class PlayerBotConfig {
	/** Owner-facing switches are documented together in config/playerbots/features.properties. */
	@Property(key = "gameserver.playerbots.quests.enable", defaultValue = "true")
	public static boolean QUESTS_ENABLED = true;
	@Property(key = "gameserver.playerbots.quests.item_starts", defaultValue = "true")
	public static boolean QUEST_ITEM_STARTS = true;
	@Property(key = "gameserver.playerbots.quests.custom", defaultValue = "true")
	public static boolean QUEST_CUSTOM = true;
	@Property(key = "gameserver.playerbots.quests.crafting", defaultValue = "true")
	public static boolean QUEST_CRAFTING = true;
	@Property(key = "gameserver.playerbots.quests.materials", defaultValue = "true")
	public static boolean QUEST_MATERIALS = true;
	@Property(key = "gameserver.playerbots.quests.exchange", defaultValue = "true")
	public static boolean QUEST_EXCHANGE = true;
	@Property(key = "gameserver.playerbots.quests.flight", defaultValue = "true")
	public static boolean QUEST_FLIGHT = true;
	@Property(key = "gameserver.playerbots.world.enable", defaultValue = "true")
	public static boolean WORLD_ENABLED;
	@Property(key = "gameserver.playerbots.world.max_active", defaultValue = "3000")
	public static int WORLD_MAX_ACTIVE;
	@Property(key = "gameserver.playerbots.population.enable", defaultValue = "true")
	public static boolean POPULATION_ENABLED;
	@Property(key = "gameserver.playerbots.population.min", defaultValue = "2800")
	public static int POPULATION_MIN;
	@Property(key = "gameserver.playerbots.population.max", defaultValue = "3000")
	public static int POPULATION_MAX;
	@Property(key = "gameserver.playerbots.population.per_interval", defaultValue = "120")
	public static int POPULATION_PER_INTERVAL;
	/** Elyos share of the global population (0..100%). Asmodians receive the remainder: 100 - this value.
	 * 50 = 50% Elyos / 50% Asmodians; 30 = 30% Elyos / 70% Asmodians; 0 = all Asmodians; 100 = all Elyos.
	 * This is the desired balance; admissions can use the other faction when eligible candidates are unavailable. */
	@Property(key = "gameserver.playerbots.population.elyos_percent", defaultValue = "50")
	public static int POPULATION_ELYOS_PERCENT;
	@Property(key = "gameserver.playerbots.population.min_level", defaultValue = "1")
	public static int POPULATION_MIN_LEVEL;
	@Property(key = "gameserver.playerbots.population.max_level", defaultValue = "65")
	public static int POPULATION_MAX_LEVEL;
	@Property(key = "gameserver.playerbots.population.min_level_percent", defaultValue = "10")
	public static int POPULATION_MIN_LEVEL_PERCENT;
	@Property(key = "gameserver.playerbots.population.max_level_percent", defaultValue = "65")
	public static int POPULATION_MAX_LEVEL_PERCENT;
	@Property(key = "gameserver.playerbots.population.random_levels", defaultValue = "true")
	public static boolean POPULATION_RANDOM_LEVELS;
	@Property(key = "gameserver.playerbots.population.starting_level", defaultValue = "1")
	public static int POPULATION_STARTING_LEVEL;
	@Property(key = "gameserver.playerbots.population.sync_player_levels", defaultValue = "false")
	public static boolean POPULATION_SYNC_LEVELS;
	@Property(key = "gameserver.playerbots.population.periodic_presence", defaultValue = "true")
	public static boolean POPULATION_PERIODIC_PRESENCE;
	/** WoW PeriodicOnlineOfflineRatio: persistent pool size, independent of active residents. */
	@Property(key = "gameserver.playerbots.population.rotation_ratio", defaultValue = "2.0")
	public static float POPULATION_ROTATION_RATIO;
	@Property(key = "gameserver.playerbots.population.presence_min_seconds", defaultValue = "600")
	public static int POPULATION_PRESENCE_MIN;
	@Property(key = "gameserver.playerbots.population.presence_max_seconds", defaultValue = "28800")
	public static int POPULATION_PRESENCE_MAX;
	@Property(key = "gameserver.playerbots.population.offline_min_seconds", defaultValue = "600")
	public static int POPULATION_OFFLINE_MIN;
	@Property(key = "gameserver.playerbots.population.offline_max_seconds", defaultValue = "28800")
	public static int POPULATION_OFFLINE_MAX;
	@Property(key = "gameserver.playerbots.population.target_min_seconds", defaultValue = "1800")
	public static int POPULATION_TARGET_MIN;
	@Property(key = "gameserver.playerbots.population.target_max_seconds", defaultValue = "7200")
	public static int POPULATION_TARGET_MAX;
	@Property(key = "gameserver.playerbots.population.teleport_min_seconds", defaultValue = "3600")
	public static int POPULATION_TELEPORT_MIN;
	@Property(key = "gameserver.playerbots.population.teleport_max_seconds", defaultValue = "18000")
	public static int POPULATION_TELEPORT_MAX;
	@Property(key = "gameserver.playerbots.population.city_percent", defaultValue = "0")
	public static int POPULATION_CITY_PERCENT;
	/** WoW RandomBotConcentrateInPlayerZone: prefer occupied native geographic zones.
	 * Enabled by user request; level/faction eligibility and worldwide fallback remain. */
	@Property(key = "gameserver.playerbots.population.concentrate_player_maps", defaultValue = "true")
	public static boolean POPULATION_CONCENTRATE;
	/** User-requested responsive residency around real players; no saved character rerolls. */
	@Property(key = "gameserver.playerbots.population.player_focus.enable", defaultValue = "true")
	public static boolean POPULATION_PLAYER_FOCUS;
	/** Retired quota: retained only so existing configuration files remain compatible.
	 * Player focus distributes the configured global target and never caps it. */
	@Property(key = "gameserver.playerbots.population.player_focus.per_map", defaultValue = "0")
	public static int POPULATION_FOCUS_PER_MAP;
	@Property(key = "gameserver.playerbots.population.player_focus.radius", defaultValue = "900")
	public static int POPULATION_FOCUS_RADIUS;
	/** Empty means every native open-world map with a suitable validated destination. */
	@Property(key = "gameserver.playerbots.population.maps", defaultValue = "")
	public static java.util.Set<Integer> POPULATION_MAPS;
	/** Unfinished companion outfit feature remains source-only until explicitly released. */
	@Property(key = "gameserver.playerbots.appearance_enabled", defaultValue = "false")
	public static boolean APPEARANCE_ENABLED;
	@Property(key = "gameserver.playerbots.enable", defaultValue = "true")
	public static boolean ENABLED;
	@Property(key = "gameserver.playerbots.summon.enable", defaultValue = "true")
	public static boolean SUMMON_ENABLED;
	/** Active controlled bots, bounded by native alliance capacity (23 plus the controller). */
	@Property(key = "gameserver.playerbots.max_per_owner", defaultValue = "23")
	public static int MAX_PER_OWNER;
	@Property(key = "gameserver.playerbots.max_active", defaultValue = "100")
	public static int MAX_ACTIVE;
	@Property(key = "gameserver.playerbots.tick_ms", defaultValue = "400")
	public static int TICK_MS;
	@Property(key = "gameserver.playerbots.generated.enable", defaultValue = "true")
	public static boolean GENERATED_ENABLED;
	/** Saved Temporary Bot capacity, independent of the active native party quota. */
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
