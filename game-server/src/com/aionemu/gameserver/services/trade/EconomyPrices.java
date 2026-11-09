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
	/** NPCs that exist in the world; null means unknown (offline tools without spawn data), so every shop counts. */
	private static volatile java.util.Set<Integer> liveTraders;

	private EconomyPrices() {}

	/** Called only after a complete, valid storefront reload. Prices are per item. */
	public static void cashPrices(Map<Integer, Long> prices) {
		cash = Map.copyOf(prices);
		BoxAppraisal.invalidate();
		EnhancementAppraisal.invalidate();
		CraftAppraisal.invalidate();
	}

	/** Offline audits pass the spawned NPC IDs they read; the server reads them from its spawn data. */
	public static synchronized void liveTraders(java.util.Set<Integer> npcIds) {
		liveTraders = java.util.Set.copyOf(npcIds);
		purchaseRates = null;
		CraftAppraisal.invalidate();
		EnhancementAppraisal.invalidate();
	}

	private static java.util.Set<Integer> spawnedNpcs() {
		if (liveTraders != null || DataManager.SPAWNS_DATA == null) return liveTraders;
		var ids = new java.util.HashSet<Integer>();
		DataManager.SPAWNS_DATA.addAllNpcIdsToSet(ids);
		if (DataManager.TOWN_SPAWNS_DATA != null) DataManager.TOWN_SPAWNS_DATA.addAllNpcIdsToSet(ids);
		if (DataManager.EVENT_DATA != null) DataManager.EVENT_DATA.addAllNpcIdsToSet(ids);
		return liveTraders = java.util.Set.copyOf(ids);
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
			// A shop whose NPC never spawns sells nothing: 926 market items were capped at such a list's token price
			// (level 60 manastones at 13 Kinah, which also pinned what players could list them for).
			var spawned = spawnedNpcs();
			for (var shop : DataManager.TRADE_LIST_DATA.getTradeListTemplate().values()) {
				if (shop.getTradeNpcType() != TradeNpcType.NORMAL) continue;
				if (spawned != null && !spawned.contains(shop.getNpcId())) continue;
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
		return demandCeiling(supply);
	}

	public static long demandCeiling(long supply) {
		return Math.max(1, java.math.BigInteger.valueOf(supply).multiply(java.math.BigInteger.valueOf(10_000))
			.divide(java.math.BigInteger.valueOf(com.aionemu.gameserver.services.AetherfallBoonService.maximumMarketReturn()))
			.min(java.math.BigInteger.valueOf(com.aionemu.gameserver.services.CentralMarketRules.MAX_KINAH)).longValueExact() - 1);
	}

	public static long cashPrice(ItemTemplate item) {
		return cash.getOrDefault(item.getTemplateId(), 0L);
	}

	/** Native item-specific value, with existing market tier floors for token-price templates. */
	public static long reference(ItemTemplate item) {
		long value = BoxAppraisal.reference(item);
		// Black Cloud prices anchor their own goods; everything else crafted is worth at least its materials.
		return cashPrice(item) > 0 ? value : Math.max(value, CraftAppraisal.floor(item));
	}

	/** A crafting material's cost: its appraisal, or a live NPC's price when that is cheaper. Never the x10 factor. */
	static long materialValue(ItemTemplate item) {
		initialize();
		return npcCapped(item, reference(item));
	}

	private static long npcCapped(ItemTemplate item, long value) {
		Integer rate = npcRates.get(item.getTemplateId());
		if (rate != null && rate > 0 && item.getAcquisition() == null) {
			long nativePrice = Math.min(PricesService.getBuyPrice(item.getPrice(), Race.ELYOS), PricesService.getBuyPrice(item.getPrice(), Race.ASMODIANS));
			value = Math.min(value, Math.max(1, nativePrice * rate / 100));
		}
		return value;
	}

	/** The previous appraisal, retained for the offline before/after audit and recursion leaves. */
	public static long nativeReference(ItemTemplate item) {
		ItemTemplate original = MarketPolicy.original(item);
		if (original != null) return nativeReference(original);
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

	/** Plain anchor plus the expected native production cost, without supplements or AP conversion. */
	public static long enhanced(ItemTemplate item,int enchant,int tempering) {
		return EnhancementAppraisal.enhanced(item,enchant,tempering);
	}

	public static long enhancementCost(ItemTemplate item,int enchant,int tempering) {
		return Math.max(0,enhanced(item,enchant,tempering)-Math.min(com.aionemu.gameserver.services.CentralMarketRules.MAX_KINAH,anchor(item)));
	}

	/** Actor-specific production alternative, sharing the deterministic memoised native model. */
	public static long enhancementCost(ItemTemplate item,int enchant,int tempering,float baseChance,float amplifiedChance,float temperingChance) {
		return Math.max(0,EnhancementAppraisal.enhanced(item,enchant,tempering,baseChance,amplifiedChance,temperingChance)-Math.min(com.aionemu.gameserver.services.CentralMarketRules.MAX_KINAH,anchor(item)));
	}

	public static boolean contentsAppraised(ItemTemplate item) { return BoxAppraisal.isBox(item.getTemplateId()); }
	public static boolean sourceAppraised(ItemTemplate item) { return BoxAppraisal.isContent(item.getTemplateId()); }

	/** Lowest currently repeatable NPC/cash/generated retail; no invented AP conversion. */
	public static long replacement(ItemTemplate item) {
		initialize();
		long value = npcCapped(item, reference(item));
		// Owner feedback 9 Nov: ten times for level 65 mythic gear. It lifts the plain appraisal; a crafted piece whose
		// materials cost more keeps that cost instead of multiplying it again (2.6 billion for a Legendary Katalium Sword).
		double factor = MarketPolicy.factor(item);
		if (factor == 1) return value;
		long scaled = (long) Math.max(1, Math.min(com.aionemu.gameserver.services.CentralMarketRules.MAX_KINAH, Math.round(npcCapped(item, BoxAppraisal.reference(item)) * factor)));
		return Math.max(value, scaled);
	}
}
