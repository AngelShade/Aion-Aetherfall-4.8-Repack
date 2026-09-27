package com.aionemu.gameserver.services.custom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.BrokerMarketConfig;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dao.BrokerDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.broker.BrokerItemMask;
import com.aionemu.gameserver.model.broker.BrokerRace;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.BrokerService;
import com.aionemu.gameserver.services.trade.PricesService;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Supplies a small, varied set of real, tradeable templates in every broker leaf category. */
public class AuctionHouseSimulator {

	private static final Logger log = LoggerFactory.getLogger(AuctionHouseSimulator.class);
	private static final int[] ELYOS_SELLERS = { 999901, 999902, 999903 };
	private static final int[] ASMODIAN_SELLERS = { 999904, 999905, 999906 };
	private final Map<Race, List<MarketEntry>> catalog = new EnumMap<>(Race.class);
	private final Map<Race, int[]> sellers = new EnumMap<>(Race.class);
	private boolean initialized;

	private record MarketEntry(ItemTemplate template) {}

	public static AuctionHouseSimulator getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder {
		private static final AuctionHouseSimulator INSTANCE = new AuctionHouseSimulator();
	}

	private AuctionHouseSimulator() {}

	public static boolean isSimulatedSeller(int sellerId) {
		return sellerId >= ELYOS_SELLERS[0] && sellerId <= ASMODIAN_SELLERS[ASMODIAN_SELLERS.length - 1];
	}

	public synchronized void init() {
		if (initialized || !BrokerMarketConfig.ENABLED)
			return;
		initialized = true;
		sellers.put(Race.ELYOS, availableSellers(ELYOS_SELLERS));
		sellers.put(Race.ASMODIANS, availableSellers(ASMODIAN_SELLERS));
		for (Race race : List.of(Race.ELYOS, Race.ASMODIANS)) {
			catalog.put(race, buildCatalog(race));
			log.info("Broker market: {} templates for {}, {} seller accounts", catalog.get(race).size(), race, sellers.get(race).length);
		}
		populateAll(true);
		scheduleNext();
	}

	private int[] availableSellers(int[] ids) {
		Set<Integer> found = BrokerDAO.findExistingPlayerIds(ids);
		if (found.isEmpty())
			log.warn("Broker market seller characters are missing. Create the reserved seller characters before enabling market seeding.");
		return java.util.Arrays.stream(ids).filter(found::contains).toArray();
	}

	private List<MarketEntry> buildCatalog(Race race) {
		List<ItemTemplate> templates = DataManager.ITEM_DATA.getItemTemplates().stream()
			.filter(t -> eligible(t, race)).toList();
		List<MarketEntry> result = new ArrayList<>();
		Set<Integer> selected = new HashSet<>();
		int missing = 0;
		int target = Math.max(1, Math.min(10, BrokerMarketConfig.ITEMS_PER_CATEGORY));
		for (BrokerItemMask category : BrokerItemMask.values()) {
			if (category == BrokerItemMask.UNKNOWN || category.hasChildren())
				continue;
			List<ItemTemplate> choices = templates.stream().filter(category::matchesTemplate)
				.sorted(Comparator.comparingInt(ItemTemplate::getLevel).thenComparingLong(ItemTemplate::getPrice)).toList();
			int added = 0;
			for (int slot = 0; slot < target && !choices.isEmpty(); slot++) {
				int center = Math.min(choices.size() - 1, (int) ((long) (slot + 1) * choices.size() / (target + 1)));
				for (int offset = 0; offset < choices.size(); offset++) {
					ItemTemplate choice = choices.get((center + offset) % choices.size());
					if (selected.add(choice.getTemplateId())) {
						result.add(new MarketEntry(choice));
						added++;
						break;
					}
				}
			}
			if (added == 0) {
				missing++;
				log.debug("Broker market: no eligible {} item for {}", category, race);
			}
		}
		log.info("Broker market: {} empty leaf categories for {} after trade and template filtering", missing, race);
		return result;
	}

	private boolean eligible(ItemTemplate t, Race race) {
		if (!t.isTradeable() || t.getPrice() <= 0 ||
			((t.isWeapon() || t.isArmor() || t.isStigma()) && t.getLevel() > GSConfig.PLAYER_MAX_LEVEL) ||
			(t.getRace() != Race.PC_ALL && t.getRace() != race) || t.isKinah())
			return false;
		String name = t.getName().toLowerCase(java.util.Locale.ROOT);
		if (name.isBlank() || name.startsWith("[") || name.contains("test") || name.contains("npc") ||
			name.contains("debug") || name.contains("quest") || name.contains("prototype") || name.contains("dummy") ||
			name.contains("event") || name.contains("placeholder"))
			return false;
		// A value of 5 is used by many internal weapon/armor templates.
		return !(t.isWeapon() || t.isArmor()) || t.getPrice() >= 1000;
	}

	public synchronized void populateAll(boolean force) {
		if (!BrokerMarketConfig.ENABLED || !initialized)
			return;
		populateRace(Race.ELYOS, BrokerRace.ELYOS, force);
		populateRace(Race.ASMODIANS, BrokerRace.ASMODIAN, force);
	}

	private void populateRace(Race race, BrokerRace brokerRace, boolean force) {
		int[] raceSellers = sellers.get(race);
		if (raceSellers == null || raceSellers.length == 0)
			return;
		BrokerService broker = BrokerService.getInstance();
		Set<Integer> listed = broker.getActiveItemIds(race);
		int added = 0;
		int limit = force ? Integer.MAX_VALUE : Math.max(1, BrokerMarketConfig.MAX_ITEMS_PER_REFILL);
		List<MarketEntry> candidates = new ArrayList<>(catalog.get(race));
		if (!force)
			java.util.Collections.shuffle(candidates);
		for (MarketEntry entry : candidates) {
			ItemTemplate template = entry.template();
			if (listed.contains(template.getTemplateId()) || added >= limit)
				continue;
			long count = stackCount(template);
			long price = unitPrice(template, race);
			int seller = raceSellers[ThreadLocalRandom.current().nextInt(raceSellers.length)];
			if (broker.addSimulatedItem(template.getTemplateId(), count, price, seller, brokerRace)) {
				listed.add(template.getTemplateId());
				added++;
			}
		}
		if (added > 0)
			log.info("Broker market restocked {} listings for {}; active listings: {}", added, race, broker.getRaceItemCount(race));
	}

	private long stackCount(ItemTemplate template) {
		if (!template.isStackable())
			return 1;
		long max = Math.min(template.getMaxStackCount(), Math.max(1, BrokerMarketConfig.MAX_STACK));
		return 1 + ThreadLocalRandom.current().nextLong(max);
	}

	private long unitPrice(ItemTemplate template, Race race) {
		double vendorPrice = PricesService.getBuyPrice(template.getPrice(), race);
		int level = Math.max(1, template.getLevel());
		int quality = template.getItemQuality() == null ? 1 : template.getItemQuality().getQualityId();
		// Many otherwise valid templates use a token value of 1 or 5 Kinah.
		double floor = 100 + level * level * 2.0 * (1 + quality * 0.2);
		if (template.getItemGroup() == ItemGroup.MANASTONE || template.getItemGroup() == ItemGroup.SPECIAL_MANASTONE)
			floor = 500 + level * level * 8.0 * (1 + quality * 0.25);
		else if (template.getItemGroup() == ItemGroup.ENCHANTMENT)
			floor = 2000 + level * level * 30.0 * (1 + quality * 0.25);
		else if (template.getItemGroup() == ItemGroup.TAMPERING)
			floor = 100000 + level * level * 100.0 * (1 + quality * 0.25);
		else if (template.getItemGroup() == ItemGroup.FLUX || template.getItemGroup() == ItemGroup.GATHERABLE ||
			template.getItemGroup() == ItemGroup.BALIC_MATERIAL || template.getItemGroup() == ItemGroup.DROP_MATERIAL)
			floor = 100 + level * level * 3.0;
		double multiplier = Math.max(0.1, BrokerMarketConfig.PRICE_MULTIPLIER);
		double variance = 0.9 + ThreadLocalRandom.current().nextDouble() * 0.2;
		return Math.max(1, Math.min(999_999_999L, Math.round(Math.max(floor, vendorPrice) * multiplier * variance)));
	}

	private void scheduleNext() {
		long min = Math.max(1, BrokerMarketConfig.REFILL_MIN_MINUTES);
		long max = Math.max(min, BrokerMarketConfig.REFILL_MAX_MINUTES);
		long delay = ThreadLocalRandom.current().nextLong(min, max + 1);
		ThreadPoolManager.getInstance().schedule(() -> {
			try {
				populateAll(false);
			} catch (Exception e) {
				log.error("Broker market refill failed", e);
			} finally {
				scheduleNext();
			}
		}, TimeUnit.MINUTES.toMillis(delay));
	}
}
