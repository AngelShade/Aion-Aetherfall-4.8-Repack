package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** Controls the optional broker supply for small/private servers. */
public class BrokerMarketConfig {

	@Property(key = "gameserver.broker.market.enabled", defaultValue = "true")
	public static boolean ENABLED;

	@Property(key = "gameserver.broker.market.items_per_category", defaultValue = "3")
	public static int ITEMS_PER_CATEGORY;

	@Property(key = "gameserver.broker.market.max_items_per_refill", defaultValue = "40")
	public static int MAX_ITEMS_PER_REFILL;

	@Property(key = "gameserver.broker.market.max_stack", defaultValue = "30")
	public static int MAX_STACK;

	@Property(key = "gameserver.broker.market.price_multiplier", defaultValue = "1.0")
	public static double PRICE_MULTIPLIER;

	@Property(key = "gameserver.broker.market.refill_min_minutes", defaultValue = "30")
	public static int REFILL_MIN_MINUTES;

	@Property(key = "gameserver.broker.market.refill_max_minutes", defaultValue = "90")
	public static int REFILL_MAX_MINUTES;
}
