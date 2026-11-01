package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/** Road merchant module settings, documented in config/playerbots/road-merchants.properties. */
public final class PlayerBotMerchantConfig {
 @Property(key="gameserver.playerbots.merchants.price.scale_percent",defaultValue="100") public static int PRICE_SCALE;
 @Property(key="gameserver.playerbots.merchants.price.level_divisor",defaultValue="6") public static int PRICE_LEVEL_DIVISOR;
 @Property(key="gameserver.playerbots.merchants.price.minimum",defaultValue="1000") public static long PRICE_MINIMUM;
 @Property(key="gameserver.playerbots.merchants.price.maximum",defaultValue="999999999999") public static long PRICE_MAXIMUM;
 @Property(key="gameserver.playerbots.merchants.price.house_base",defaultValue="10000") public static long PRICE_HOUSE_BASE;
 @Property(key="gameserver.playerbots.merchants.price.quality.common",defaultValue="5") public static int QUALITY_COMMON;
 @Property(key="gameserver.playerbots.merchants.price.quality.green",defaultValue="10") public static int QUALITY_GREEN;
 @Property(key="gameserver.playerbots.merchants.price.quality.blue",defaultValue="20") public static int QUALITY_BLUE;
 @Property(key="gameserver.playerbots.merchants.price.quality.gold",defaultValue="40") public static int QUALITY_GOLD;
 @Property(key="gameserver.playerbots.merchants.price.quality.orange",defaultValue="80") public static int QUALITY_ORANGE;
 @Property(key="gameserver.playerbots.merchants.price.quality.purple",defaultValue="150") public static int QUALITY_PURPLE;
 @Property(key="gameserver.playerbots.merchants.price.rank.peddler_percent",defaultValue="100") public static int PRICE_PEDDLER;
 @Property(key="gameserver.playerbots.merchants.price.rank.trader_percent",defaultValue="110") public static int PRICE_TRADER;
 @Property(key="gameserver.playerbots.merchants.price.rank.specialist_percent",defaultValue="120") public static int PRICE_SPECIALIST;
 @Property(key="gameserver.playerbots.merchants.price.rank.master_percent",defaultValue="135") public static int PRICE_MASTER;
 @Property(key="gameserver.playerbots.merchants.price.rank.legendary_percent",defaultValue="150") public static int PRICE_LEGENDARY;
 @Property(key="gameserver.playerbots.merchants.rank.max_quality.peddler",defaultValue="2") public static int MAX_QUALITY_PEDDLER;
 @Property(key="gameserver.playerbots.merchants.rank.max_quality.trader",defaultValue="3") public static int MAX_QUALITY_TRADER;
 @Property(key="gameserver.playerbots.merchants.rank.max_quality.specialist",defaultValue="4") public static int MAX_QUALITY_SPECIALIST;
 @Property(key="gameserver.playerbots.merchants.rank.max_quality.master",defaultValue="5") public static int MAX_QUALITY_MASTER;
 @Property(key="gameserver.playerbots.merchants.rank.max_quality.legendary",defaultValue="6") public static int MAX_QUALITY_LEGENDARY;
 @Property(key="gameserver.playerbots.merchants.rank.chance.peddler",defaultValue="6000") public static int CHANCE_PEDDLER;
 @Property(key="gameserver.playerbots.merchants.rank.chance.trader",defaultValue="2500") public static int CHANCE_TRADER;
 @Property(key="gameserver.playerbots.merchants.rank.chance.specialist",defaultValue="1000") public static int CHANCE_SPECIALIST;
 @Property(key="gameserver.playerbots.merchants.rank.chance.master",defaultValue="400") public static int CHANCE_MASTER;
 @Property(key="gameserver.playerbots.merchants.rank.chance.legendary",defaultValue="100") public static int CHANCE_LEGENDARY;
 @Property(key="gameserver.playerbots.merchants.stock.base",defaultValue="20") public static int STOCK_BASE;
 @Property(key="gameserver.playerbots.merchants.stock.per_rank",defaultValue="1") public static int STOCK_PER_RANK;
 @Property(key="gameserver.playerbots.merchants.stock.restock_hours",defaultValue="6") public static int RESTOCK_HOURS;
 @Property(key="gameserver.playerbots.merchants.stock.restock_items",defaultValue="6") public static int RESTOCK_ITEMS;
 @Property(key="gameserver.playerbots.merchants.shop.max_listed",defaultValue="10") public static int SHOP_MAX_LISTED;
 @Property(key="gameserver.playerbots.merchants.shop.open_range",defaultValue="8") public static int SHOP_OPEN_RANGE;
 @Property(key="gameserver.playerbots.merchants.shop.close_range",defaultValue="15") public static int SHOP_CLOSE_RANGE;
 private PlayerBotMerchantConfig(){}
}
