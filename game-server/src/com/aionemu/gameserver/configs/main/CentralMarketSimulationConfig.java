package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** Economic activity in Central Market, independent of the NPC Broker. */
public class CentralMarketSimulationConfig {
	@Property(key="gameserver.central.market.simulation.enhanced_max_enchant", defaultValue="10")
	public static int ENHANCED_MAX_ENCHANT = 10;
	@Property(key="gameserver.central.market.simulation.enhanced_max_tempering", defaultValue="5")
	public static int ENHANCED_MAX_TEMPERING = 5;
	@Property(key="gameserver.central.market.simulation.enhanced_chance_per_level", defaultValue="100,95,85,60,40,20,8,4,2,1")
	public static int[] ENHANCED_CHANCE_PER_LEVEL = {100,95,85,60,40,20,8,4,2,1};
	@Property(key="gameserver.central.market.simulation.enabled", defaultValue="true")
	public static boolean ENABLED;
	@Property(key="gameserver.central.market.simulation.population", defaultValue="3000")
	public static int POPULATION;
	@Property(key="gameserver.central.market.simulation.tick_seconds", defaultValue="5")
	public static int TICK_SECONDS;
	@Property(key="gameserver.central.market.simulation.catalog_batch", defaultValue="2000")
	public static int CATALOG_BATCH;
	@Property(key="gameserver.central.market.simulation.refresh_min_seconds", defaultValue="120")
	public static int REFRESH_MIN_SECONDS;
	@Property(key="gameserver.central.market.simulation.refresh_max_seconds", defaultValue="480")
	public static int REFRESH_MAX_SECONDS;
	@Property(key="gameserver.central.market.simulation.max_stack", defaultValue="100")
	public static int MAX_STACK;
	@Property(key="gameserver.central.market.simulation.price_levels", defaultValue="7")
	public static int PRICE_LEVELS;
	@Property(key="gameserver.central.market.simulation.low_price_demand_percent", defaultValue="80")
	public static int LOW_PRICE_DEMAND;
	@Property(key="gameserver.central.market.simulation.high_price_demand_percent", defaultValue="10")
	public static int HIGH_PRICE_DEMAND;
}
