package com.aionemu.gameserver.services.trade;

import java.util.HashMap;
import java.util.Map;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.pet.PetFunctionType;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;

/** Shared appraisal for server-generated retail; native player resale remains unchanged. */
public final class EconomyPrices {
	private static volatile Map<Integer, Long> cash = Map.of();
	private static volatile Map<Integer, Integer> purchaseRates;
	private static Map<Integer, Integer> npcRates;
	private static int merchantRate;

	private EconomyPrices() {}

	/** Called only after a complete, valid storefront reload. Prices are per item. */
	public static void cashPrices(Map<Integer, Long> prices) {
		cash = Map.copyOf(prices);
	}

	private static void initialize() {
		if (purchaseRates != null) return;
		synchronized (EconomyPrices.class) {
			if (purchaseRates != null) return;
			merchantRate = Math.max(0, PricesService.getVendorSellModifier());
			for (int id : DataManager.PET_DATA.getPetIds()) {
				var function = DataManager.PET_DATA.getPetTemplate(id).getPetFunction(PetFunctionType.MERCHANT);
				if (function != null) merchantRate = Math.max(merchantRate, function.getRatePrice());
			}
			var purchases = new HashMap<Integer, Integer>();
			for (var shop : DataManager.TRADE_LIST_DATA.getPurchaseTemplates()) {
				if (shop.getTradeNpcType() == TradeNpcType.ABYSS) continue; // these return AP, not Kinah
				for (var tab : shop.getTradeTablist()) {
					var goods = DataManager.GOODSLIST_DATA.getGoodsPurchaseListById(tab.getId());
					if (goods != null) for (int id : goods.getItemIdList()) purchases.merge(id, shop.getBuyPriceRate(), Math::max);
				}
			}
			var normal = new HashMap<Integer, Integer>();
			for (var shop : DataManager.TRADE_LIST_DATA.getTradeListTemplate().values()) {
				if (shop.getTradeNpcType() != TradeNpcType.NORMAL) continue;
				for (var tab : shop.getTradeTablist()) {
					var goods = DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());
					if (goods != null) for (int id : goods.getItemIdList()) normal.merge(id, shop.getSellPriceRate(), Math::min);
				}
			}
			npcRates = Map.copyOf(normal);
			purchaseRates = Map.copyOf(purchases); // publish the complete index last
		}
	}

	public static long resale(ItemTemplate item) {
		initialize();
		int rate = (item.getMask() & ItemMask.SELLABLE) != 0 ? merchantRate : 0;
		rate = Math.max(rate, purchaseRates.getOrDefault(item.getTemplateId(), 0));
		return PricesService.getSellReward(Math.max(0, item.getPrice()), rate);
	}

	/** Keep a margin even against specialist buyers and the best merchant pet. */
	public static long retailFloor(ItemTemplate item) {
		long sale = resale(item);
		return Math.max(Math.max(1, item.getPrice()), sale + sale / 4 + 1);
	}

	public static long anchor(ItemTemplate item) {
		long sale = resale(item);
		return Math.max(sale + sale / 4 + 1, replacement(item));
	}

	public static long generatedFloor(ItemTemplate item) {
		long sale = resale(item);
		return Math.max(sale + sale / 4 + 1, anchor(item) * 95 / 100);
	}

	public static long demandCeiling(ItemTemplate item) {
		// Even the best account boon must not turn repeatable external shop stock into free Kinah.
		long supply = Math.min(replacement(item), generatedFloor(item));
		return Math.max(1, java.math.BigInteger.valueOf(supply).multiply(java.math.BigInteger.valueOf(10_000))
			.divide(java.math.BigInteger.valueOf(com.aionemu.gameserver.services.AetherfallBoonService.maximumMarketReturn()))
			.min(java.math.BigInteger.valueOf(com.aionemu.gameserver.services.CentralMarketRules.MAX_KINAH)).longValueExact() - 1);
	}

	public static long cashPrice(ItemTemplate item) {
		return cash.getOrDefault(item.getTemplateId(), 0L);
	}

	/** Native item-specific value, with existing market tier floors for token-price templates. */
	public static long reference(ItemTemplate item) {
		long level = Math.max(1, item.getLevel());
		int quality = item.getItemQuality() == null ? 1 : item.getItemQuality().getQualityId();
		long floor = 100 + level * level * (2 + quality);
		switch (item.getItemGroup()) {
			case MANASTONE, SPECIAL_MANASTONE -> floor = 500 + level * level * 8;
			case ENCHANTMENT -> floor = 2000 + level * level * 30;
			case TAMPERING -> floor = 100000 + level * level * 100;
			default -> {}
		}
		long quoted = cashPrice(item);
		return Math.max(retailFloor(item), quoted > 0 ? quoted : Math.max(item.getPrice(), floor));
	}

	/** Lowest currently repeatable NPC/cash/generated retail; no invented AP conversion. */
	public static long replacement(ItemTemplate item) {
		initialize();
		long value = reference(item);
		Integer rate = npcRates.get(item.getTemplateId());
		if (rate != null && rate > 0 && item.getAcquisition() == null) {
			long nativePrice = Math.min(PricesService.getBuyPrice(item.getPrice(), Race.ELYOS), PricesService.getBuyPrice(item.getPrice(), Race.ASMODIANS));
			value = Math.min(value, Math.max(1, nativePrice * rate / 100));
		}
		return value;
	}
}
