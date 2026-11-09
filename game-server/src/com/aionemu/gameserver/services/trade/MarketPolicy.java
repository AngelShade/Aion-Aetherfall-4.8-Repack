package com.aionemu.gameserver.services.trade;

import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;

/**
 * Central Market policy from player feedback (owner, 9 Nov 2026). The other feedback removals and price changes apply to
 * the Black Cloud Marketplace catalogue (config/ingameshop/rebalance_catalog.py), not here.
 */
public final class MarketPolicy {
	/** All Altered State Dispelling Potion: clears every ailment at once, a GM-strength item. Black Cloud does not sell it. */
	static final int ALL_ALTERED_STATE_POTION = 162000028;

	private MarketPolicy() {}

	/** Players keep any they hold; the market no longer lists it. */
	public static boolean excluded(ItemTemplate t) {
		return excluded(t.getTemplateId());
	}

	public static boolean excluded(int id) {
		return id == ALL_ALTERED_STATE_POTION;
	}

	public static double factor(ItemTemplate t) {
		return factor(t.isWeapon() || t.isArmor(), t.getItemQuality(), t.getLevel());
	}

	/** Level 65 mythic gear is end-game crafting: ten times. */
	public static double factor(boolean gear, ItemQuality quality, int level) {
		return gear && quality == ItemQuality.MYTHIC && level >= 65 ? 10 : 1;
	}

	/**
	 * Bootleg gear copies its original's stats but carries a placeholder native price, so it fell to the bare level floor
	 * (339,000 Kinah for level 65 mythic gear). It is appraised as its original (matched by client name).
	 */
	public static ItemTemplate original(ItemTemplate t) {
		ItemTemplate original = t.getBootlegOriginal();
		return original == null || original.getTemplateId() == t.getTemplateId() ? null : original;
	}

	/** Generated ("ghost") quotes never cover Black Cloud Marketplace goods. */
	public static boolean generated(ItemTemplate t) {
		return generated(EconomyPrices.cashPrice(t));
	}

	public static boolean generated(long cashPrice) {
		return cashPrice <= 0;
	}
}
