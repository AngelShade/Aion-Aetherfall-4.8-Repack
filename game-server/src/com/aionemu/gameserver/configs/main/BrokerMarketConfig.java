package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** Controls the optional broker supply for small/private servers. */
public class BrokerMarketConfig {

	@Property(key = "gameserver.broker.market.enabled", defaultValue = "true")
	public static boolean ENABLED;

	@Property(key = "gameserver.broker.market.max_items_per_refill", defaultValue = "2000")
	public static int MAX_ITEMS_PER_REFILL;

	@Property(key = "gameserver.broker.market.catchup_interval_seconds", defaultValue = "15")
	public static int CATCHUP_INTERVAL_SECONDS;

	@Property(key = "gameserver.broker.market.max_stack", defaultValue = "100")
	public static int MAX_STACK;

	@Property(key = "gameserver.broker.market.price_multiplier", defaultValue = "1.0")
	public static double PRICE_MULTIPLIER;

	@Property(key = "gameserver.broker.market.refill_min_minutes", defaultValue = "30")
	public static int REFILL_MIN_MINUTES;

	@Property(key = "gameserver.broker.market.refill_max_minutes", defaultValue = "90")
	public static int REFILL_MAX_MINUTES;
}
