package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

public final class PlayerBotConfig {
 public static void validatePvp() {
  if(PVP_AGGRESSION<0 || PVP_AGGRESSION>100 || PVP_RIFT_PARTICIPANTS<0 || PVP_RIFT_PARTICIPANTS>100)
   throw new IllegalArgumentException("Playerbots pvp.properties: percentages must be between 0 and 100.");
  if(!Float.isFinite(PVP_NOTICE_DISTANCE) || PVP_NOTICE_DISTANCE<1 || PVP_NOTICE_DISTANCE>60
   || !Float.isFinite(PVP_CHASE_DISTANCE) || PVP_CHASE_DISTANCE<PVP_NOTICE_DISTANCE || PVP_CHASE_DISTANCE>150
   || PVP_CHASE_SECONDS<1 || PVP_CHASE_SECONDS>120 || PVP_LEVEL_DIFFERENCE<0 || PVP_LEVEL_DIFFERENCE>64
   || !Float.isFinite(PVP_STRENGTH_LIMIT) || PVP_STRENGTH_LIMIT<.1f || PVP_STRENGTH_LIMIT>10
   || PVP_ATTACKERS_PER_TARGET<1 || PVP_ATTACKERS_PER_TARGET>24 || PVP_RETREAT_HEALTH<1 || PVP_RETREAT_HEALTH>90)
   throw new IllegalArgumentException("Playerbots pvp.properties: use notice_distance 1..60, chase_distance notice..150, chase_seconds 1..120, level_difference 0..64, enemy_strength_limit 0.1..10, attackers_per_target 1..24, retreat_health_percent 1..90.");
  if(PVP_RIFT_REST_MINUTES<1 || PVP_RIFT_REST_MINUTES>1440 || PVP_RIFT_VISIT_MINUTES<1 || PVP_RIFT_VISIT_MINUTES>120
   || !Float.isFinite(PVP_RIFT_SEARCH_DISTANCE) || PVP_RIFT_SEARCH_DISTANCE<5 || PVP_RIFT_SEARCH_DISTANCE>3000
   || PVP_RIFT_TRAVEL_SECONDS<10 || PVP_RIFT_TRAVEL_SECONDS>900 || !Float.isFinite(POPULATION_UNSEEN_DISTANCE)
   || POPULATION_UNSEEN_DISTANCE<150 || POPULATION_UNSEEN_DISTANCE>1000 || !Float.isFinite(POPULATION_ENEMY_CAMP_DISTANCE)
   || POPULATION_ENEMY_CAMP_DISTANCE<0 || POPULATION_ENEMY_CAMP_DISTANCE>1000)
   throw new IllegalArgumentException("Playerbots pvp.properties: use rest_minutes 1..1440, visit_minutes 1..120, search_distance 5..3000, travel_seconds 10..900, unseen_spawn_distance 150..1000, enemy_camp_distance 0..1000.");
 }
 @Property(key = "gameserver.playerbots.pvp.enable", defaultValue = "true")
 public static boolean PVP_ENABLED = true;
 @Property(key = "gameserver.playerbots.pvp.start_fights_percent", defaultValue = "35")
 public static int PVP_AGGRESSION = 35;
 @Property(key = "gameserver.playerbots.pvp.notice_distance", defaultValue = "24")
 public static float PVP_NOTICE_DISTANCE = 24;
 @Property(key = "gameserver.playerbots.pvp.chase_distance", defaultValue = "60")
 public static float PVP_CHASE_DISTANCE = 60;
 @Property(key = "gameserver.playerbots.pvp.chase_seconds", defaultValue = "25")
 public static int PVP_CHASE_SECONDS = 25;
 @Property(key = "gameserver.playerbots.pvp.level_difference", defaultValue = "8")
 public static int PVP_LEVEL_DIFFERENCE = 8;
 @Property(key = "gameserver.playerbots.pvp.enemy_strength_limit", defaultValue = "1.5")
 public static float PVP_STRENGTH_LIMIT = 1.5f;
 @Property(key = "gameserver.playerbots.pvp.attackers_per_target", defaultValue = "3")
 public static int PVP_ATTACKERS_PER_TARGET = 3;
 @Property(key = "gameserver.playerbots.pvp.retreat_health_percent", defaultValue = "25")
 public static int PVP_RETREAT_HEALTH = 25;
 @Property(key = "gameserver.playerbots.pvp.rifts.enable", defaultValue = "true")
 public static boolean PVP_RIFTS = true;
 @Property(key = "gameserver.playerbots.pvp.rifts.participants_percent", defaultValue = "15")
 public static int PVP_RIFT_PARTICIPANTS = 15;
 @Property(key = "gameserver.playerbots.pvp.rifts.rest_minutes", defaultValue = "20")
 public static int PVP_RIFT_REST_MINUTES = 20;
 @Property(key = "gameserver.playerbots.pvp.rifts.visit_minutes", defaultValue = "10")
 public static int PVP_RIFT_VISIT_MINUTES = 10;
 @Property(key = "gameserver.playerbots.pvp.rifts.search_distance", defaultValue = "1200")
 public static float PVP_RIFT_SEARCH_DISTANCE = 1200;
 @Property(key = "gameserver.playerbots.pvp.rifts.travel_seconds", defaultValue = "180")
 public static int PVP_RIFT_TRAVEL_SECONDS = 180;
 @Property(key = "gameserver.playerbots.pvp.rifts.keep_home_map_awake", defaultValue = "true")
 public static boolean PVP_RIFT_HOME_ACTIVITY = true;
 @Property(key = "gameserver.playerbots.population.faction_home_spawns", defaultValue = "true")
 public static boolean POPULATION_FACTION_HOME = true;
 @Property(key = "gameserver.playerbots.population.unseen_spawn_distance", defaultValue = "200")
 public static float POPULATION_UNSEEN_DISTANCE = 200;
 @Property(key = "gameserver.playerbots.population.enemy_camp_distance", defaultValue = "150")
 public static float POPULATION_ENEMY_CAMP_DISTANCE = 150;
 @Property(key = "gameserver.playerbots.social.findgroup", defaultValue = "true")
 public static boolean FIND_GROUP_ENABLED = true;
 @Property(key = "gameserver.playerbots.social.findgroup.posts_per_tab", defaultValue = "12")
 public static int FIND_GROUP_POSTS_PER_TAB = 12;
 /** Dungeon (Instance Groups tab) posts per faction at the same time. */
 @Property(key = "gameserver.playerbots.social.findgroup.instance_posts", defaultValue = "20")
 public static int FIND_GROUP_INSTANCE_POSTS = 20;
 /** Bots on any map post dungeon runs; they are summoned to the human who joins. */
 @Property(key = "gameserver.playerbots.social.findgroup.remote_instances", defaultValue = "true")
 public static boolean FIND_GROUP_REMOTE_INSTANCES = true;
 /** Bots whisper human Find Group posts: the role a party lacks, or an invite to a forming bot group. */
 @Property(key = "gameserver.playerbots.social.findgroup.offers", defaultValue = "true")
 public static boolean FIND_GROUP_OFFERS = true;
 /** Custom catch-up while a companion owns an admitted follow movement. */
 @Property(key = "gameserver.playerbots.companion.catchup_multiplier", defaultValue = "4")
 public static float COMPANION_CATCHUP_MULTIPLIER = 4;
 /** Nearby follow recovery measures physical progress, not published path requests. */
 @Property(key = "gameserver.playerbots.companion.stuck_recall_ms", defaultValue = "5000")
 public static long COMPANION_STUCK_RECALL_MS = 5000;
 @Property(key = "gameserver.playerbots.social.legion_invites", defaultValue = "true")
 public static boolean CHAT_LEGION_INVITES = true;
 @Property(key = "gameserver.playerbots.navigation.platform.queue_limit", defaultValue = "8")
 public static int PLATFORM_QUEUE_LIMIT = 8;
 @Property(key = "gameserver.playerbots.navigation.platform.passengers", defaultValue = "4")
 public static int PLATFORM_PASSENGERS = 4;
	@Property(key = "gameserver.playerbots.companion.glide_follow", defaultValue = "true")
	public static boolean COMPANION_GLIDE_FOLLOW = true;
 @Property(key = "gameserver.playerbots.navigation.mesh.enable", defaultValue = "true")
 public static boolean NAVMESH_ENABLED = true;
 @Property(key = "gameserver.playerbots.navigation.mesh.memory_mb", defaultValue = "0")
 public static long NAVMESH_MEMORY_MB = 0;
 @Property(key = "gameserver.playerbots.navigation.mesh.heap_percent", defaultValue = "25")
 public static int NAVMESH_HEAP_PERCENT = 25;
 @Property(key = "gameserver.playerbots.navigation.mesh.max_queries", defaultValue = "64")
 public static int NAVMESH_MAX_QUERIES = 64;
 /** Reusable directed road itineraries, charged inside each loaded map's allowance. */
 @Property(key = "gameserver.playerbots.navigation.mesh.route_cache_kb", defaultValue = "512")
 public static long NAVMESH_ROUTE_CACHE_KB = 512;
 /** Navmesh accuracy diagnostics: per-check counts in the population log and the
  * per-area log/navmesh_areas.csv report. Off stops both recording and writing. */
 @Property(key = "gameserver.playerbots.navigation.mesh.diagnostics", defaultValue = "true")
 public static boolean NAVMESH_DIAGNOSTICS = true;
 /** Quick Detour line check on loaded meshes before the native straight-walk probe. */
 @Property(key = "gameserver.playerbots.navigation.mesh.line_check", defaultValue = "true")
 public static boolean NAVMESH_LINE_CHECK = true;
 /** Learn spots where prepared corridors fail the native ground check and route around them. */
 @Property(key = "gameserver.playerbots.navigation.mesh.learn_blocks", defaultValue = "true")
 public static boolean NAVMESH_LEARN_BLOCKS = true;
 /** Tank pull command: the tank tags the selected monster and brings it back to the owner. */
 @Property(key = "gameserver.playerbots.tank.pull.enable", defaultValue = "true")
 public static boolean TANK_PULL = true;
 /** Skills at least this long (metres) count as ranged pulls; shorter ones wait while one is ready. */
 @Property(key = "gameserver.playerbots.tank.pull.ranged_range", defaultValue = "10")
 public static float TANK_PULL_RANGE = 10;
 /** Give up a pull that was not tagged within this time. */
 @Property(key = "gameserver.playerbots.tank.pull.reach_ms", defaultValue = "25000")
 public static long TANK_PULL_REACH_MS = 25000;
 /** Longest walk back to the group after tagging. */
 @Property(key = "gameserver.playerbots.tank.pull.return_ms", defaultValue = "8000")
 public static long TANK_PULL_RETURN_MS = 8000;
 /** Tanks use area threat on loose enemies even when area attacks are off. */
 @Property(key = "gameserver.playerbots.tank.area_threat.enable", defaultValue = "true")
 public static boolean TANK_AREA_THREAT = true;
 /** Loose enemies (in the fight, not on the tank) needed before area threat is preferred. */
 @Property(key = "gameserver.playerbots.tank.area_threat.min_loose", defaultValue = "2")
 public static int TANK_AREA_THREAT_MIN = 2;
 /** Owner kill/crowd-control marks for companions. */
 @Property(key = "gameserver.playerbots.marks.enable", defaultValue = "true")
 public static boolean MARKS = true;
 /** Read numbered attack marks and the sleep mark from the native party/alliance. */
 @Property(key = "gameserver.playerbots.marks.native.enable", defaultValue = "true")
 public static boolean NATIVE_MARKS = true;
 /** A crowd-control mark left uncontrolled this long while hostile is released. */
 @Property(key = "gameserver.playerbots.marks.cc_release_ms", defaultValue = "8000")
 public static long MARKS_CC_RELEASE_MS = 8000;
 /** Each actor plans from shared map memory without population-wide terrain/query admission. */
 @Property(key = "gameserver.playerbots.navigation.independent", defaultValue = "true")
 public static boolean NAVIGATION_INDEPENDENT = true;
 /** Prepared/recorded paths are proposals; the native mover validates actual steps. */
 @Property(key = "gameserver.playerbots.navigation.memory_first", defaultValue = "true")
 public static boolean NAVIGATION_MEMORY_FIRST = true;
 /** Compatibility allowance when independent planning is disabled. */
 @Property(key = "gameserver.playerbots.navigation.budget_ms_per_second", defaultValue = "500")
 public static int TERRAIN_BUDGET_MS = 500;
 @Property(key = "gameserver.playerbots.navigation.slice_ms", defaultValue = "3")
 public static int TERRAIN_SLICE_MS = 3;
 @Property(key = "gameserver.playerbots.navigation.max_workers", defaultValue = "2")
 public static int TERRAIN_WORKERS = 2;
 /** Reserved companion planning allowance, independent of world-bot debt and queues. */
 @Property(key = "gameserver.playerbots.companions.navigation.budget_ms_per_second", defaultValue = "250")
 public static int COMPANION_TERRAIN_BUDGET_MS = 250;
 @Property(key = "gameserver.playerbots.companions.navigation.max_workers", defaultValue = "1")
 public static int COMPANION_TERRAIN_WORKERS = 1;
 @Property(key = "gameserver.playerbots.companions.navigation.mesh.max_queries", defaultValue = "8")
 public static int COMPANION_NAVMESH_MAX_QUERIES = 8;
 @Property(key = "gameserver.playerbots.companions.navigation.mesh.memory_mb", defaultValue = "128")
 public static long COMPANION_NAVMESH_MEMORY_MB = 128;
 @Property(key = "gameserver.playerbots.navigation.segment_metres", defaultValue = "24")
 public static float TERRAIN_SEGMENT_METRES = 24;
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
	@Property(key = "gameserver.playerbots.world.only_occupied_maps", defaultValue = "true")
	public static boolean WORLD_ONLY_OCCUPIED_MAPS = true;
	@Property(key = "gameserver.playerbots.world.presence_refresh_ms", defaultValue = "1000")
	public static int WORLD_PRESENCE_REFRESH_MS = 1000;
	@Property(key = "gameserver.playerbots.world.rpg.enable", defaultValue = "true")
	public static boolean WORLD_RPG_ENABLED = true;
	@Property(key = "gameserver.playerbots.world.rpg_pause_ms", defaultValue = "10000")
	public static int WORLD_RPG_PAUSE_MS = 10000;
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
	/** Pinned RandomBotUpdateInterval; startup and shortage use the faster manager cadence. */
	@Property(key = "gameserver.playerbots.population.update_interval_seconds", defaultValue = "20")
	public static int POPULATION_UPDATE_INTERVAL_SECONDS = 20;
	/** Optional Aion extension; the pinned early expiry return does not assign an offline timer. */
	@Property(key = "gameserver.playerbots.population.offline_cooldown.enable", defaultValue = "false")
	public static boolean POPULATION_OFFLINE_COOLDOWN = false;
	/** Elyos share of the global population (0..100%). Asmodians receive the remainder: 100 - this value.
	 * 50 = 50% Elyos / 50% Asmodians; 30 = 30% Elyos / 70% Asmodians; 0 = all Asmodians; 100 = all Elyos.
	 * This is the desired balance; admissions can use the other faction when eligible candidates are unavailable. */
	@Property(key = "gameserver.playerbots.population.elyos_percent", defaultValue = "50")
	public static int POPULATION_ELYOS_PERCENT;
	@Property(key = "gameserver.playerbots.population.min_level", defaultValue = "1")
	public static int POPULATION_MIN_LEVEL;
	@Property(key = "gameserver.playerbots.population.max_level", defaultValue = "65")
	public static int POPULATION_MAX_LEVEL;
	@Property(key = "gameserver.playerbots.population.min_level_percent", defaultValue = "0")
	public static int POPULATION_MIN_LEVEL_PERCENT;
	@Property(key = "gameserver.playerbots.population.max_level_percent", defaultValue = "0")
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
	/** Additive nearby residency; the pinned worldwide manager and its target remain authoritative. */
	@Property(key = "gameserver.playerbots.population.player_presence.enable", defaultValue = "false")
	public static boolean POPULATION_PLAYER_PRESENCE;
	@Property(key = "gameserver.playerbots.population.player_presence.per_area", defaultValue = "40")
	public static int POPULATION_PRESENCE_PER_AREA;
	@Property(key = "gameserver.playerbots.population.player_presence.radius", defaultValue = "600")
	public static int POPULATION_PRESENCE_RADIUS;
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
	@Property(key = "gameserver.playerbots.generated.random_looks", defaultValue = "true")
	public static boolean GENERATED_RANDOM_LOOKS;
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
