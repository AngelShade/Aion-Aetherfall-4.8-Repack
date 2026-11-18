package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** Dynamic event chains started by Road Wardens; see config/dynamic-events/dynamic-events.properties and config/dynamic-events/README.md. */
public final class DynamicEventConfig {
	@Property(key = "gameserver.dynamicevents.enable", defaultValue = "true")
	public static boolean ENABLED;
	@Property(key = "gameserver.dynamicevents.trigger_chance_percent", defaultValue = "20")
	public static int TRIGGER_CHANCE;
	@Property(key = "gameserver.dynamicevents.trigger_check_seconds", defaultValue = "30")
	public static int TRIGGER_CHECK_SECONDS;
	@Property(key = "gameserver.dynamicevents.max_running", defaultValue = "4")
	public static int MAX_RUNNING;
	@Property(key = "gameserver.dynamicevents.max_per_map", defaultValue = "1")
	public static int MAX_PER_MAP;
	@Property(key = "gameserver.dynamicevents.beat_cooldown_minutes", defaultValue = "45")
	public static int BEAT_COOLDOWN_MINUTES;
	@Property(key = "gameserver.dynamicevents.radius", defaultValue = "60")
	public static int RADIUS;
	@Property(key = "gameserver.dynamicevents.bot_weight_percent", defaultValue = "50")
	public static int BOT_WEIGHT_PERCENT;
	@Property(key = "gameserver.dynamicevents.bot_recruit_range", defaultValue = "120")
	public static int BOT_RECRUIT_RANGE;
	@Property(key = "gameserver.dynamicevents.bot_recruit_max", defaultValue = "4")
	public static int BOT_RECRUIT_MAX;
	@Property(key = "gameserver.dynamicevents.scale_cap_players", defaultValue = "10")
	public static int SCALE_CAP;
	@Property(key = "gameserver.dynamicevents.group_scale_cap_players", defaultValue = "40")
	public static int GROUP_SCALE_CAP;
	@Property(key = "gameserver.dynamicevents.elite_from_players", defaultValue = "5")
	public static int ELITE_FROM;
	@Property(key = "gameserver.dynamicevents.boss_hp_per_participant_percent", defaultValue = "35")
	public static int BOSS_HP_PER_PARTICIPANT;
	@Property(key = "gameserver.dynamicevents.boss_hp_max_percent", defaultValue = "500")
	public static int BOSS_HP_MAX;
	@Property(key = "gameserver.dynamicevents.gold_share_percent", defaultValue = "50")
	public static int GOLD_SHARE;
	@Property(key = "gameserver.dynamicevents.silver_share_percent", defaultValue = "20")
	public static int SILVER_SHARE;
	@Property(key = "gameserver.dynamicevents.stage_xp_permille", defaultValue = "70")
	public static int STAGE_XP_PERMILLE;
	@Property(key = "gameserver.dynamicevents.kinah_per_level", defaultValue = "120")
	public static int KINAH_PER_LEVEL;
	@Property(key = "gameserver.dynamicevents.silver_reward_percent", defaultValue = "85")
	public static int SILVER_REWARD;
	@Property(key = "gameserver.dynamicevents.bronze_reward_percent", defaultValue = "75")
	public static int BRONZE_REWARD;
	@Property(key = "gameserver.dynamicevents.failed_reward_percent", defaultValue = "50")
	public static int FAILED_REWARD;
	@Property(key = "gameserver.dynamicevents.reward_level_gap", defaultValue = "10")
	public static int REWARD_LEVEL_GAP;
	@Property(key = "gameserver.dynamicevents.chest_kinah_per_level", defaultValue = "1000")
	public static int CHEST_KINAH_PER_LEVEL;
	@Property(key = "gameserver.dynamicevents.chest_tickets", defaultValue = "1")
	public static int CHEST_TICKETS;
	@Property(key = "gameserver.dynamicevents.bonus_chest_tickets", defaultValue = "2")
	public static int BONUS_CHEST_TICKETS;
	@Property(key = "gameserver.dynamicevents.occupation_minutes", defaultValue = "240")
	public static int OCCUPATION_MINUTES;
	@Property(key = "gameserver.dynamicevents.occupation_holders", defaultValue = "5")
	public static int OCCUPATION_HOLDERS;
	@Property(key = "gameserver.dynamicevents.shop_minutes", defaultValue = "10")
	public static int SHOP_MINUTES;

	private DynamicEventConfig() {
	}
}
