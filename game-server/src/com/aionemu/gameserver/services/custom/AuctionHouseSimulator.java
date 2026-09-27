package com.aionemu.gameserver.services.custom;

import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.broker.BrokerRace;
import com.aionemu.gameserver.services.BrokerService;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * Auction House Simulator for Solo Play
 * Automatically populates the Broker with realistic consumables, manastones,
 * enchantment stones, and crafting materials for both Elyos and Asmodians.
 */
public class AuctionHouseSimulator {

	private static final Logger log = LoggerFactory.getLogger(AuctionHouseSimulator.class);
	private static final Random RND = new Random();

	private static final int[] ELYOS_SELLERS = { 999901, 999902, 999903 };
	private static final int[] ASMODIAN_SELLERS = { 999904, 999905, 999906 };

	private record MarketTemplate(int itemId, int minCount, int maxCount, long basePrice) {}

	private static final List<MarketTemplate> MARKET_CATALOG = List.of(
		// Potions & Serums
		new MarketTemplate(162000015, 50, 100, 1500),   // Greater Life Serum
		new MarketTemplate(162000018, 50, 100, 1500),   // Lesser Mana Serum
		new MarketTemplate(162000021, 30, 60, 3500),    // Major Mana Serum
		new MarketTemplate(162000022, 20, 50, 6000),    // Lesser Healing Potion
		new MarketTemplate(162000027, 20, 50, 3000),    // Major Wind Serum

		// Scrolls
		new MarketTemplate(164000076, 50, 100, 1800),   // Greater Running Scroll
		new MarketTemplate(164000073, 50, 100, 1800),   // Greater Courage Scroll
		new MarketTemplate(164000134, 50, 100, 1800),   // Greater Awakening Scroll
		new MarketTemplate(164000079, 30, 60, 2000),    // Greater Raging Wind Scroll
		new MarketTemplate(164000070, 20, 50, 2500),    // Major Anti-Shock Scroll

		// Food (DP Jelly)
		new MarketTemplate(160001274, 10, 20, 22000),   // Perer Aether Jelly (Elyos DP)
		new MarketTemplate(160002274, 10, 20, 22000),   // Cippo Aether Jelly (Asmo DP)

		// Enchantment & Tempering
		new MarketTemplate(166020000, 2, 5, 350000),    // Omega Enchantment Stone
		new MarketTemplate(166000194, 5, 10, 110000),   // Delta Enchantment Stone
		new MarketTemplate(166000195, 5, 10, 175000),   // Epsilon Enchantment Stone
		new MarketTemplate(166030005, 2, 4, 1250000),   // Tempering Solution

		// Manastones (Level 60 Rare)
		new MarketTemplate(167000558, 10, 20, 35000),   // Crit Strike +17
		new MarketTemplate(167000518, 10, 20, 40000),   // Attack +5
		new MarketTemplate(167000555, 10, 20, 35000),   // Magic Boost +27
		new MarketTemplate(167000551, 10, 20, 20000),   // HP +95
		new MarketTemplate(167000560, 10, 20, 25000),   // Magical Accuracy +14
		new MarketTemplate(167000553, 10, 20, 20000),   // Accuracy +27

		// Manastones (Level 70 Rare - End-Game)
		new MarketTemplate(167000773, 5, 10, 95000),    // Crit Strike +19
		new MarketTemplate(167000770, 5, 10, 95000),    // Magic Boost +28
		new MarketTemplate(167000766, 5, 10, 85000),    // HP +100
		new MarketTemplate(167000775, 5, 10, 70000),    // Magical Accuracy +16
		new MarketTemplate(167000768, 5, 10, 60000),    // Accuracy +29
		new MarketTemplate(167000776, 5, 10, 65000),    // Resist Magic +16

		// Crafting Fluxes & Aether & Ores
		new MarketTemplate(152011003, 20, 40, 4500),    // Weapon Flux
		new MarketTemplate(152011004, 15, 30, 9000),    // Greater Weapon Flux
		new MarketTemplate(152011005, 10, 20, 20000),   // Major Weapon Flux
		new MarketTemplate(152011006, 10, 20, 40000),   // Fine Weapon Flux
		new MarketTemplate(152011023, 20, 40, 3500),    // Armor Flux
		new MarketTemplate(152011024, 15, 30, 6500),    // Greater Armor Flux
		new MarketTemplate(152011025, 10, 20, 15000),   // Major Armor Flux
		new MarketTemplate(152011026, 10, 20, 30000),   // Fine Armor Flux
		new MarketTemplate(152000908, 30, 60, 4000),    // Greater Aether
		new MarketTemplate(152000909, 30, 60, 8500),    // Pure Aether
		new MarketTemplate(152000910, 20, 40, 19000),   // Brilliant Aether
		new MarketTemplate(152000202, 30, 60, 2500),    // Titanium Ore
		new MarketTemplate(152000104, 30, 60, 5000),    // Platinum Ore
		new MarketTemplate(152000102, 30, 60, 8000)     // Gold Ore
	);

	public static AuctionHouseSimulator getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder {
		private static final AuctionHouseSimulator INSTANCE = new AuctionHouseSimulator();
	}

	private AuctionHouseSimulator() {
	}

	public void init() {
		log.info("[AuctionHouseSimulator] Initializing Solo Auction House market...");
		populateAll(false);
		// Periodically check and restock every 30 minutes
		ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> populateAll(false), 30 * 60 * 1000L, 30 * 60 * 1000L);
	}

	public void populateAll(boolean force) {
		populateRace(Race.ELYOS, BrokerRace.ELYOS, ELYOS_SELLERS, force);
		populateRace(Race.ASMODIANS, BrokerRace.ASMODIAN, ASMODIAN_SELLERS, force);
	}

	private void populateRace(Race race, BrokerRace brokerRace, int[] sellers, boolean force) {
		BrokerService bs = BrokerService.getInstance();
		int currentCount = bs.getRaceItemCount(race);

		if (!force && currentCount >= MARKET_CATALOG.size()) {
			return;
		}

		int added = 0;
		for (MarketTemplate mt : MARKET_CATALOG) {
			if (!force && bs.hasItem(race, mt.itemId())) {
				continue;
			}
			int count = mt.minCount() + RND.nextInt(Math.max(1, mt.maxCount() - mt.minCount() + 1));
			// Fluctuate price slightly (+-10%) for realism
			double priceVariance = 0.9 + (RND.nextDouble() * 0.2);
			long unitPrice = Math.max(1, Math.round(mt.basePrice() * priceVariance));
			int seller = sellers[RND.nextInt(sellers.length)];

			bs.addSimulatedItem(mt.itemId(), count, unitPrice, seller, brokerRace);
			added++;
		}

		log.info("[AuctionHouseSimulator] Restocked {} items for race {}. Total active listings: {}",
			added, race, bs.getRaceItemCount(race));
	}
}
