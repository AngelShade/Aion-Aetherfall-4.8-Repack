package com.aionemu.gameserver.configs.main;

import java.util.Set;

import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.model.templates.item.ItemQuality;

/**
 * @author Tiger0319, Neon
 */
public class DropConfig {

	/**
	 * Announce when a player drops an item with the configured minimum item quality
	 * 
	 * @see ItemQuality
	 */
	@Property(key = "gameserver.drop.announce_quality")
	public static ItemQuality MIN_ANNOUNCE_QUALITY;

	/**
	 * Disable range checks for specified maps
	 */
	@Property(key = "gameserver.drop.disable_range_check_maps")
	public static Set<Integer> DISABLE_RANGE_CHECK_MAPS;

	/**
	 * Number of quest items generated per successful NPC quest drop.
	 */
	@Property(key = "gameserver.drop.quest_item_count", defaultValue = "1")
	public static int QUEST_ITEM_COUNT;
}
