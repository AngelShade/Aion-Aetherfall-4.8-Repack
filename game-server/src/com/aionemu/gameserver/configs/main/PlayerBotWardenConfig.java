package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** Road Warden services; see config/playerbots/road-wardens.properties. */
public final class PlayerBotWardenConfig {
 @Property(key="gameserver.playerbots.wardens.enable",defaultValue="true") public static boolean ENABLED;
 @Property(key="gameserver.playerbots.wardens.walk",defaultValue="true") public static boolean WALK;
 @Property(key="gameserver.playerbots.wardens.invite_range",defaultValue="30") public static int INVITE_RANGE;
 @Property(key="gameserver.playerbots.wardens.question_seconds",defaultValue="60") public static int QUESTION_SECONDS;
 @Property(key="gameserver.playerbots.wardens.escort_minutes",defaultValue="15") public static int ESCORT_MINUTES;
 @Property(key="gameserver.playerbots.wardens.escort_cooldown_minutes",defaultValue="10") public static int ESCORT_COOLDOWN;
 @Property(key="gameserver.playerbots.wardens.leash",defaultValue="150") public static int LEASH;
 @Property(key="gameserver.playerbots.wardens.leash_warning",defaultValue="125") public static int LEASH_WARNING;
 @Property(key="gameserver.playerbots.wardens.fair_damage_percent",defaultValue="20") public static int FAIR_DAMAGE;
 @Property(key="gameserver.playerbots.wardens.return_run_distance",defaultValue="40") public static int RETURN_RUN;
 @Property(key="gameserver.playerbots.wardens.spacing",defaultValue="40") public static int SPACING;
 @Property(key="gameserver.playerbots.wardens.beat_capacity_metres",defaultValue="150") public static int BEAT_CAPACITY_METRES;
 @Property(key="gameserver.playerbots.wardens.stop_min_seconds",defaultValue="20") public static int STOP_MIN;
 @Property(key="gameserver.playerbots.wardens.stop_max_seconds",defaultValue="60") public static int STOP_MAX;
 @Property(key="gameserver.playerbots.wardens.distress_range",defaultValue="60") public static int DISTRESS_RANGE;
 @Property(key="gameserver.playerbots.wardens.distress_health_percent",defaultValue="60") public static int DISTRESS_HP;
 @Property(key="gameserver.playerbots.wardens.distress_max",defaultValue="2") public static int DISTRESS_MAX;
 @Property(key="gameserver.playerbots.wardens.distress_cooldown_seconds",defaultValue="30") public static int DISTRESS_COOLDOWN;
 @Property(key="gameserver.playerbots.wardens.rescue_range",defaultValue="60") public static int RESCUE_RANGE;
 @Property(key="gameserver.playerbots.wardens.rescue_hostile_range",defaultValue="30") public static int RESCUE_HOSTILE_RANGE;
 @Property(key="gameserver.playerbots.wardens.rescue_delay_seconds",defaultValue="6") public static int RESCUE_DELAY;
 @Property(key="gameserver.playerbots.wardens.rescue_cooldown_minutes",defaultValue="15") public static int RESCUE_COOLDOWN;
 @Property(key="gameserver.playerbots.wardens.report_seconds",defaultValue="60") public static int REPORT_SECONDS;
 @Property(key="gameserver.playerbots.wardens.ward_skill",defaultValue="273") public static int WARD_SKILL;
 @Property(key="gameserver.playerbots.wardens.ward_range",defaultValue="15") public static int WARD_RANGE;
 @Property(key="gameserver.playerbots.wardens.ward_seconds",defaultValue="60") public static int WARD_SECONDS;
 @Property(key="gameserver.playerbots.wardens.company_size",defaultValue="120") public static int COMPANY_SIZE;
 @Property(key="gameserver.playerbots.wardens.title.elyos",defaultValue="115") public static int TITLE_ELYOS;
 @Property(key="gameserver.playerbots.wardens.title.asmodians",defaultValue="134") public static int TITLE_ASMODIANS;
 @Property(key="gameserver.playerbots.wardens.bounty_kills",defaultValue="15") public static int BOUNTY_KILLS;
 @Property(key="gameserver.playerbots.wardens.bounty_corridor",defaultValue="100") public static int BOUNTY_CORRIDOR;
 @Property(key="gameserver.playerbots.wardens.bounty_ticket_every",defaultValue="3") public static int BOUNTY_TICKET_EVERY;
 @Property(key="gameserver.playerbots.wardens.bounty_tickets",defaultValue="1") public static int BOUNTY_TICKETS;
 @Property(key="gameserver.playerbots.wardens.bounty_xp_percent",defaultValue="100") public static int BOUNTY_XP_PERCENT;
 @Property(key="gameserver.playerbots.wardens.bounty_kinah_percent",defaultValue="100") public static int BOUNTY_KINAH_PERCENT;
 private PlayerBotWardenConfig(){}
}
