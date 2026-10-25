package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** RandomPlayerbotMgr / RandomBotLevelMgr controls, separate from owner population counts. */
public final class PlayerBotPopulationConfig {
 @Property(key="gameserver.playerbots.management.legions.enable",defaultValue="true") public static boolean BOT_LEGIONS;
 @Property(key="gameserver.playerbots.management.legions.count",defaultValue="20") public static int BOT_LEGION_COUNT;
 @Property(key="gameserver.playerbots.management.legions.max_members",defaultValue="15") public static int BOT_LEGION_SIZE;
 @Property(key="gameserver.playerbots.management.upstream_placement",defaultValue="true") public static boolean UPSTREAM_PLACEMENT;
 @Property(key="gameserver.playerbots.management.upstream_admission",defaultValue="true") public static boolean UPSTREAM_ADMISSION;
 @Property(key="gameserver.playerbots.management.refresh.enable",defaultValue="true") public static boolean REFRESH;
 @Property(key="gameserver.playerbots.management.refresh.min_seconds",defaultValue="7200") public static int REFRESH_MIN;
 @Property(key="gameserver.playerbots.management.refresh.max_seconds",defaultValue="1209600") public static int REFRESH_MAX;
 @Property(key="gameserver.playerbots.management.downgrade_max_level",defaultValue="false") public static boolean DOWNGRADE_MAX;
 @Property(key="gameserver.playerbots.management.fixed_levels",defaultValue="false") public static boolean FIXED_LEVELS;
 @Property(key="gameserver.playerbots.management.native_queues.enable",defaultValue="true") public static boolean NATIVE_QUEUES;
 @Property(key="gameserver.playerbots.management.revive.min_seconds",defaultValue="60") public static int REVIVE_MIN;
 @Property(key="gameserver.playerbots.management.revive.max_seconds",defaultValue="300") public static int REVIVE_MAX;
 @Property(key="gameserver.playerbots.management.disabled_without_players",defaultValue="false") public static boolean REQUIRE_PLAYERS;
 @Property(key="gameserver.playerbots.management.player_login_delay_seconds",defaultValue="30") public static int LOGIN_DELAY;
 @Property(key="gameserver.playerbots.management.player_logout_delay_seconds",defaultValue="300") public static int LOGOUT_DELAY;
 @Property(key="gameserver.playerbots.management.brackets.enable",defaultValue="false") public static boolean BRACKETS;
 @Property(key="gameserver.playerbots.management.brackets.check_seconds",defaultValue="300") public static int BRACKET_CHECK;
 @Property(key="gameserver.playerbots.management.brackets.pending_check_seconds",defaultValue="15") public static int PENDING_CHECK;
 @Property(key="gameserver.playerbots.management.brackets.process_limit",defaultValue="5") public static int PROCESS_LIMIT;
 // WoW's default bands are clamped/rebalanced to native 1..65.
 @Property(key="gameserver.playerbots.management.brackets.elyos",defaultValue="1-9:12,10-19:11,20-29:11,30-39:11,40-49:11,50-59:11,60-69:11,70-79:11,80-80:11") public static String ELYOS;
 @Property(key="gameserver.playerbots.management.brackets.asmodians",defaultValue="1-9:12,10-19:11,20-29:11,30-39:11,40-49:11,50-59:11,60-69:11,70-79:11,80-80:11") public static String ASMODIANS;
 @Property(key="gameserver.playerbots.management.brackets.dynamic",defaultValue="false") public static boolean DYNAMIC;
 @Property(key="gameserver.playerbots.management.brackets.sync_factions",defaultValue="false") public static boolean SYNC_FACTIONS;
 @Property(key="gameserver.playerbots.management.brackets.real_player_weight",defaultValue="1.0") public static double PLAYER_WEIGHT;
 @Property(key="gameserver.playerbots.management.brackets.exclude_names",defaultValue="") public static String BRACKET_EXCLUDE;
 @Property(key="gameserver.playerbots.management.brackets.protect_friends",defaultValue="true") public static boolean FRIENDS;
 @Property(key="gameserver.playerbots.management.brackets.protect_legions",defaultValue="true") public static boolean LEGIONS;
 @Property(key="gameserver.playerbots.management.reset.enable",defaultValue="false") public static boolean RESET;
 @Property(key="gameserver.playerbots.management.reset.max_level",defaultValue="65") public static int RESET_MAX;
 @Property(key="gameserver.playerbots.management.reset.to_level",defaultValue="1") public static int RESET_TO;
 @Property(key="gameserver.playerbots.management.reset.skip_from_level",defaultValue="0") public static int SKIP_FROM;
 @Property(key="gameserver.playerbots.management.reset.skip_to_level",defaultValue="1") public static int SKIP_TO;
 @Property(key="gameserver.playerbots.management.reset.chance",defaultValue="100") public static int RESET_CHANCE;
 @Property(key="gameserver.playerbots.management.reset.scaled_chance",defaultValue="false") public static boolean SCALED;
 @Property(key="gameserver.playerbots.management.reset.restrict_played_time",defaultValue="false") public static boolean RESTRICT_PLAYED;
 @Property(key="gameserver.playerbots.management.reset.min_played_seconds",defaultValue="86400") public static int MIN_PLAYED;
 @Property(key="gameserver.playerbots.management.reset.played_check_seconds",defaultValue="864") public static int PLAYED_CHECK;
 @Property(key="gameserver.playerbots.management.reset.protect_legions",defaultValue="false") public static boolean RESET_LEGIONS;
 @Property(key="gameserver.playerbots.management.reset.exclude_names",defaultValue="") public static String RESET_EXCLUDE;
 public static void validate() {
  if(BOT_LEGION_COUNT<0 || BOT_LEGION_COUNT>100 || BOT_LEGION_SIZE<1 || BOT_LEGION_SIZE>240)throw new IllegalArgumentException("Invalid generated legion population bounds");
  if(REFRESH_MIN<1 || REFRESH_MAX<REFRESH_MIN || REVIVE_MIN<3 || REVIVE_MAX<REVIVE_MIN || BRACKET_CHECK<1 || PENDING_CHECK<1 || PROCESS_LIMIT<0 || PROCESS_LIMIT>120
   || LOGIN_DELAY<0 || LOGOUT_DELAY<0 || !Double.isFinite(PLAYER_WEIGHT) || PLAYER_WEIGHT<0
   || RESET_MAX<0 || RESET_MAX>65 || RESET_TO<1 || RESET_TO>65 || SKIP_FROM<0 || SKIP_FROM>65 || SKIP_TO<1 || SKIP_TO>65
   || RESET_CHANCE<0 || RESET_CHANCE>100 || MIN_PLAYED<0 || PLAYED_CHECK<1)
   throw new IllegalArgumentException("Invalid Playerbots population-management timing, level or percentage");
 }
 private PlayerBotPopulationConfig() {}
}
