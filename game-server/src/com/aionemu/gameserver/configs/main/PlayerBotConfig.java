package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

public final class PlayerBotConfig {
 // Tactical deployments share a minimum interval; native cooldowns and lifetimes can only lengthen it.
 @Property(key = "gameserver.playerbots.skills.deploy_interval_ms", defaultValue = "10000")
 public static long SKILL_DEPLOY_INTERVAL_MS = 10000;
 // Save long cooldowns for durable enemies, players or emergencies. Native data uses 100 ms units.
 @Property(key = "gameserver.playerbots.skills.long_cooldown_seconds", defaultValue = "180")
 public static int SKILL_LONG_COOLDOWN_SECONDS = 180;
 // A free world bot passing a human of its faction joins a hard fight. The kill and its rewards stay the human's.
 @Property(key = "gameserver.playerbots.bystanders.enable", defaultValue = "true")
 public static boolean BYSTANDERS_ENABLED = true;
 @Property(key = "gameserver.playerbots.bystanders.range", defaultValue = "30")
 public static int BYSTANDERS_RANGE = 30;
 @Property(key = "gameserver.playerbots.bystanders.health_percent", defaultValue = "70")
 public static int BYSTANDERS_HEALTH_PERCENT = 70;
 @Property(key = "gameserver.playerbots.bystanders.max_per_player", defaultValue = "2")
 public static int BYSTANDERS_MAX_PER_PLAYER = 2;
 @Property(key = "gameserver.playerbots.collection.renown.enable", defaultValue = "true")
 public static boolean COLLECTION_RENOWN_ENABLED = true;
 @Property(key = "gameserver.playerbots.collection.renown.rarity_weights", defaultValue = "10,25,50,100,200")
 public static String COLLECTION_RENOWN_RARITY_WEIGHTS = "10,25,50,100,200";
 @Property(key = "gameserver.playerbots.tickets.enable", defaultValue = "false")
 public static boolean TICKETS_ENABLED = false;
 @Property(key = "gameserver.playerbots.tickets.starting", defaultValue = "50")
 public static long TICKET_STARTING = 50;
 @Property(key = "gameserver.playerbots.tickets.challenge_one", defaultValue = "0.60")
 public static double TICKET_CHALLENGE_ONE = 0.60;
 @Property(key = "gameserver.playerbots.tickets.challenge_two", defaultValue = "1.10")
 public static double TICKET_CHALLENGE_TWO = 1.10;
 @Property(key = "gameserver.playerbots.tickets.challenge_three", defaultValue = "1.60")
 public static double TICKET_CHALLENGE_THREE = 1.60;
 @Property(key = "gameserver.playerbots.tickets.pvp_win", defaultValue = "1")
 public static int TICKET_PVP_WIN = 1;
 @Property(key = "gameserver.playerbots.world.trait_weights", defaultValue = "4354,2854,1854,554,384")
 public static String WORLD_TRAIT_WEIGHTS = "4354,2854,1854,554,384";
 @Property(key = "gameserver.playerbots.tickets.level_step", defaultValue = "0.1")
 public static double TICKET_LEVEL_STEP = 0.1;
 @Property(key = "gameserver.playerbots.tickets.level_min", defaultValue = "0.25")
 public static double TICKET_LEVEL_MIN = 0.25;
 @Property(key = "gameserver.playerbots.tickets.level_max", defaultValue = "2.0")
 public static double TICKET_LEVEL_MAX = 2.0;
 @Property(key = "gameserver.playerbots.tickets.gear_min", defaultValue = "0.5")
 public static double TICKET_GEAR_MIN = 0.5;
 @Property(key = "gameserver.playerbots.tickets.gear_max", defaultValue = "1.5")
 public static double TICKET_GEAR_MAX = 1.5;
 @Property(key = "gameserver.playerbots.journey.tickets_goal", defaultValue = "1")
 public static int JOURNEY_TICKETS_GOAL = 1;
 @Property(key = "gameserver.playerbots.journey.runs_goal", defaultValue = "5")
 public static int JOURNEY_RUNS_GOAL = 5;
 @Property(key = "gameserver.playerbots.journey.help_goal", defaultValue = "1")
 public static int JOURNEY_HELP_GOAL = 1;
 @Property(key = "gameserver.playerbots.journey.midpoint_title", defaultValue = "101")
 public static int JOURNEY_MIDPOINT_TITLE = 101;
 @Property(key = "gameserver.playerbots.journey.final_title", defaultValue = "102")
 public static int JOURNEY_FINAL_TITLE = 102;
 @Property(key = "gameserver.playerbots.road_duty.enable", defaultValue = "true")
 public static boolean ROAD_DUTY = true;
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
  if(PVP_BASE_TRIP_MINUTES<1 || PVP_BASE_TRIP_MINUTES>1440)
   throw new IllegalArgumentException("Playerbots pvp.properties: use bases.trip_minutes 1..1440.");
  if(PVP_DUEL_REWARD_KINAH_PER_LEVEL<0 || PVP_DUEL_REWARD_KINAH_PER_LEVEL>100000 || PVP_DUEL_REWARD_AP<0 || PVP_DUEL_REWARD_AP>30000 || PVP_DUEL_REWARDS_PER_DAY<0 || PVP_DUEL_REWARDS_PER_DAY>1000)
   throw new IllegalArgumentException("Playerbots pvp.properties: use duels.reward_kinah_per_level 0..100000, duels.reward_ap 0..30000, duels.rewards_per_day 0..1000.");
  if(PVP_DUEL_ACCEPT<0 || PVP_DUEL_ACCEPT>100 || PVP_DUEL_CHALLENGE_MINUTES<0 || PVP_DUEL_CHALLENGE_MINUTES>1440)
   throw new IllegalArgumentException("Playerbots pvp.properties: use duels.accept_percent 0..100 and duels.challenge_minutes 0..1440.");
  if(PVP_LEANING_FOCUS_PERCENT<10 || PVP_LEANING_FOCUS_PERCENT>100 || PVP_LEANING_MIN_LEVEL<1 || PVP_LEANING_MIN_LEVEL>65 || PVP_SHARED_ENEMIES<0 || PVP_SHARED_ENEMIES>30 || PVP_SHARED_LEVEL_RANGE<0 || PVP_SHARED_LEVEL_RANGE>20
   || !Float.isFinite(PVP_SEEK_DISTANCE) || PVP_SEEK_DISTANCE<50 || PVP_SEEK_DISTANCE>3000 || PVP_RIFT_INVADERS<0 || PVP_RIFT_INVADERS>24 || PVP_VICTORY_EMOTE<0 || PVP_VICTORY_EMOTE>100)
   throw new IllegalArgumentException("Playerbots pvp.properties: use leaning_focus_percent 10..100, leaning_min_level 1..65, shared_enemies_per_human 0..30, shared_level_range 0..20, seek_distance 50..3000, rifts.invaders_per_rift 0..24, victory_emote_percent 0..100.");
  com.aionemu.gameserver.services.playerbot.PlayerBotLeanings.weights(PVP_LEANING_WEIGHTS);
 }
 @Property(key = "gameserver.playerbots.pvp.enable", defaultValue = "true")
 public static boolean PVP_ENABLED = true;
 /** Bots count as real players for PvP kill credit, as killer or victim: Abyss kills, kill quests, AP/DP/XP and kill rewards. */
 @Property(key = "gameserver.playerbots.pvp.count_like_players", defaultValue = "true")
 public static boolean PVP_COUNT_LIKE_PLAYERS = true;
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
 @Property(key = "gameserver.playerbots.pvp.leaning_weights", defaultValue = "QUESTER:35,HUNTER:20,TRADE:15,TRADER:10,PVP:20")
 public static String PVP_LEANING_WEIGHTS = "QUESTER:35,HUNTER:20,TRADE:15,TRADER:10,PVP:20";
 @Property(key = "gameserver.playerbots.pvp.leaning_min_level", defaultValue = "20")
 public static int PVP_LEANING_MIN_LEVEL = 20;
 @Property(key = "gameserver.playerbots.pvp.leaning_focus_percent", defaultValue = "40")
 public static int PVP_LEANING_FOCUS_PERCENT = 40;
 @Property(key = "gameserver.playerbots.pvp.shared_enemies_per_human", defaultValue = "6")
 public static int PVP_SHARED_ENEMIES = 6;
 @Property(key = "gameserver.playerbots.pvp.shared_level_range", defaultValue = "5")
 public static int PVP_SHARED_LEVEL_RANGE = 5;
 @Property(key = "gameserver.playerbots.pvp.seek_distance", defaultValue = "1000")
 public static float PVP_SEEK_DISTANCE = 1000;
 @Property(key = "gameserver.playerbots.pvp.rifts.invaders_per_rift", defaultValue = "4")
 public static int PVP_RIFT_INVADERS = 4;
 @Property(key = "gameserver.playerbots.pvp.victory_emote_percent", defaultValue = "40")
 public static int PVP_VICTORY_EMOTE = 40;
 @Property(key = "gameserver.playerbots.pvp.duels.enable", defaultValue = "true")
 public static boolean PVP_DUELS = true;
 @Property(key = "gameserver.playerbots.pvp.duels.accept_percent", defaultValue = "70")
 public static int PVP_DUEL_ACCEPT = 70;
 @Property(key = "gameserver.playerbots.pvp.duels.challenge_minutes", defaultValue = "30")
 public static int PVP_DUEL_CHALLENGE_MINUTES = 30;
 @Property(key = "gameserver.playerbots.pvp.duels.reward_kinah_per_level", defaultValue = "150")
 public static int PVP_DUEL_REWARD_KINAH_PER_LEVEL = 150;
 @Property(key = "gameserver.playerbots.pvp.duels.reward_ap", defaultValue = "25")
 public static int PVP_DUEL_REWARD_AP = 25;
 @Property(key = "gameserver.playerbots.pvp.duels.rewards_per_day", defaultValue = "10")
 public static int PVP_DUEL_REWARDS_PER_DAY = 10;
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.enable", defaultValue = "true")
 public static boolean PVP_BATTLEGROUNDS = true;
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.objectives", defaultValue = "true")
 public static boolean PVP_BATTLEGROUND_OBJECTIVES = true;
 /** PvP-3a: inside a Dredgion, non-PvP bots play its Surkanas, hatches, officers and (when it wins) the captain. */
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.dredgion", defaultValue = "true")
 public static boolean PVP_DREDGION = true;
 /** PvP-3: Kamar Battlefield objective play (its own hook). */
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.kamar", defaultValue = "true")
 public static boolean PVP_KAMAR = true;
 /** PvP-3: Engulfed Ophidan Bridge objective play (its own hook). */
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.ophidan", defaultValue = "true")
 public static boolean PVP_OPHIDAN = true;
 /** PvP-3: Iron Wall Warfront objective play (its own hook). */
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.iron_wall", defaultValue = "true")
 public static boolean PVP_IRON_WALL = true;
 /** PvP-3: Idgel Dome objective play (its own hook). */
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.idgel", defaultValue = "true")
 public static boolean PVP_IDGEL = true;
 /** PvP-4: arena point objects and monsters (its own hook). */
 @Property(key = "gameserver.playerbots.pvp.battlegrounds.arenas", defaultValue = "true")
 public static boolean PVP_ARENAS = true;
 /** PvP-5: a running Agent fight draws every bot on its map, not only PvP-leaning ones. */
 @Property(key = "gameserver.playerbots.pvp.sieges.agent_all_bots", defaultValue = "true")
 public static boolean PVP_SIEGE_AGENT_ALL = true;
 @Property(key = "gameserver.playerbots.pvp.sieges.enable", defaultValue = "true")
 public static boolean PVP_SIEGES = true;
 @Property(key = "gameserver.playerbots.pvp.bases.enable", defaultValue = "true")
 public static boolean PVP_BASES = true;
 @Property(key = "gameserver.playerbots.pvp.bases.trip_minutes", defaultValue = "30")
 public static int PVP_BASE_TRIP_MINUTES = 30;
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
 @Property(key = "gameserver.playerbots.social.findgroup.run_briefing", defaultValue = "true")
 public static boolean FIND_GROUP_RUN_BRIEFING = true;
 /** Help requests one account may have at once (1..3, one native wrapper quest each). */
 @Property(key = "gameserver.playerbots.social.findgroup.help_max_active", defaultValue = "3")
 public static int HELP_MAX_ACTIVE = 3;
 /** Share of world bots (percent, lifelong per character) that are the type to drop a helper before the hand-in. */
 @Property(key = "gameserver.playerbots.social.findgroup.help_kick_scoundrel_percent", defaultValue = "15")
 public static int HELP_KICK_SCOUNDREL_PERCENT = 15;
 /** Chance (percent) that such a bot actually does it when its quest objectives fill. 0 = never. */
 @Property(key = "gameserver.playerbots.social.findgroup.help_kick_percent", defaultValue = "40")
 public static int HELP_KICK_PERCENT = 40;
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
 /** Let nearby players hear short updates about what companions and world bots are doing. */
 @Property(key = "gameserver.playerbots.chat.activity.enable", defaultValue = "true")
 public static boolean ACTIVITY_CHAT_ENABLED = true;
 /** Minimum seconds between a bot's activity updates. */
 @Property(key = "gameserver.playerbots.chat.activity.cooldown_seconds", defaultValue = "90")
 public static int ACTIVITY_CHAT_COOLDOWN_SECONDS = 90;
 /** Seconds before a bot can mention the same ongoing activity again. */
 @Property(key = "gameserver.playerbots.chat.activity.reminder_seconds", defaultValue = "180")
 public static int ACTIVITY_CHAT_REMINDER_SECONDS = 180;
 /** Maximum activity updates each player hears from all bots in any sixty seconds. */
 @Property(key = "gameserver.playerbots.chat.activity.human_cap_per_minute", defaultValue = "4")
 public static int ACTIVITY_CHAT_HUMAN_CAP_PER_MINUTE = 4;
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
 @Property(key = "gameserver.playerbots.navigation.trace_moves", defaultValue = "")
 public static String NAVIGATION_TRACE_MOVES = "";
 /** Diagnosis only: trace the bot a staff player has targeted. */
 @Property(key = "gameserver.playerbots.navigation.trace_targeted", defaultValue = "false")
 public static boolean NAVIGATION_TRACE_TARGETED = false;
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
	@Property(key = "gameserver.playerbots.world.detail_tiers", defaultValue = "true")
	public static boolean WORLD_DETAIL_TIERS = true;
	@Property(key = "gameserver.playerbots.world.detail.visible_radius", defaultValue = "200")
	public static int WORLD_DETAIL_VISIBLE_RADIUS = 200;
	@Property(key = "gameserver.playerbots.world.detail.visible_leave_radius", defaultValue = "250")
	public static int WORLD_DETAIL_VISIBLE_LEAVE_RADIUS = 250;
	@Property(key = "gameserver.playerbots.world.detail.visible_per_human", defaultValue = "30")
	public static int WORLD_DETAIL_VISIBLE_PER_HUMAN = 30;
	@Property(key = "gameserver.playerbots.world.detail.near_reaction_multiplier", defaultValue = "3")
	public static int WORLD_DETAIL_NEAR_MULTIPLIER = 3;
	@Property(key = "gameserver.playerbots.world.detail.near_moving_ms", defaultValue = "600")
	public static int WORLD_DETAIL_NEAR_MOVING_MS = 600;
	@Property(key = "gameserver.playerbots.world.detail.rotation_percent", defaultValue = "10")
	public static int WORLD_DETAIL_ROTATION_PERCENT = 10;
	@Property(key = "gameserver.playerbots.world.detail.rotation_seconds", defaultValue = "30")
	public static int WORLD_DETAIL_ROTATION_SECONDS = 30;
	@Property(key = "gameserver.playerbots.world.detail.smartscale_floor_ms", defaultValue = "50")
	public static int WORLD_DETAIL_SMARTSCALE_FLOOR_MS = 50;
	@Property(key = "gameserver.playerbots.world.detail.smartscale_ceiling_ms", defaultValue = "200")
	public static int WORLD_DETAIL_SMARTSCALE_CEILING_MS = 200;
	@Property(key = "gameserver.playerbots.world.detail.legion_force", defaultValue = "true")
	public static boolean WORLD_DETAIL_LEGION_FORCE = true;
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
	/** Parallel world-bot login workers (RandomPlayerbotMgr batch login with async DB loads). 0 = one per CPU core. */
	@Property(key = "gameserver.playerbots.population.login_workers", defaultValue = "0")
	public static int POPULATION_LOGIN_WORKERS;
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
	@Property(key = "gameserver.playerbots.generated.enable", defaultValue = "false")
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
