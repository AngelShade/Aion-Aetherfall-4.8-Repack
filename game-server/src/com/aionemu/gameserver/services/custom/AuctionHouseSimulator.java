package com.aionemu.gameserver.services.custom;

import java.util.ArrayList;
import java.util.Collections;
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

/** Supplies all eligible, tradeable templates to both brokers in paced batches. */
public class AuctionHouseSimulator {

	private static final Logger log = LoggerFactory.getLogger(AuctionHouseSimulator.class);
	private static final int[] ELYOS_SELLERS = { 999901, 999902, 999903 };
	private static final int[] ASMODIAN_SELLERS = { 999904, 999905, 999906 };
	private final Map<Race, List<ItemTemplate>> catalog = new EnumMap<>(Race.class);
	private final Map<Race, int[]> sellers = new EnumMap<>(Race.class);
	private final Map<Race, Integer> nextIndex = new EnumMap<>(Race.class);
	private boolean initialized;

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
		sellers.put(Race.ELYOS, availableSellers(ELYOS_SELLERS, Race.ELYOS));
		sellers.put(Race.ASMODIANS, availableSellers(ASMODIAN_SELLERS, Race.ASMODIANS));
		for (Race race : List.of(Race.ELYOS, Race.ASMODIANS)) {
			catalog.put(race, buildCatalog(race));
			nextIndex.put(race, 0);
			log.info("Broker market: {} eligible templates for {}, {} seller characters", catalog.get(race).size(), race, sellers.get(race).length);
		}
		ThreadPoolManager.getInstance().schedule(this::refillSafely, 1000);
	}

	private int[] availableSellers(int[] ids, Race race) {
		Set<Integer> found = BrokerDAO.findExistingPlayerIds(ids, race);
		if (found.isEmpty())
			log.warn("Broker market seller characters are missing. Create the reserved seller characters before enabling market seeding.");
		return java.util.Arrays.stream(ids).filter(found::contains).toArray();
	}

	private List<ItemTemplate> buildCatalog(Race race) {
		List<ItemTemplate> templates = DataManager.ITEM_DATA.getItemTemplates().stream()
			.filter(t -> eligible(t, race)).toList();
		List<List<ItemTemplate>> categories = new ArrayList<>();
		// Leaf categories first; parent masks then catch templates that have no leaf.
		for (boolean parent : new boolean[] { false, true }) {
			for (BrokerItemMask category : BrokerItemMask.values()) {
				if (category == BrokerItemMask.UNKNOWN || category.hasChildren() != parent)
					continue;
				List<ItemTemplate> choices = new ArrayList<>();
				for (ItemTemplate template : templates) {
					if (category.matchesTemplate(template))
						choices.add(template);
				}
				Collections.shuffle(choices);
				if (!choices.isEmpty())
					categories.add(choices);
			}
		}
		List<ItemTemplate> result = new ArrayList<>(templates.size());
		Set<Integer> selected = new HashSet<>();
		for (int offset = 0;; offset++) {
			boolean hasMore = false;
			for (List<ItemTemplate> choices : categories) {
				if (offset < choices.size()) {
					hasMore = true;
					ItemTemplate template = choices.get(offset);
					if (selected.add(template.getTemplateId()))
						result.add(template);
				}
			}
			if (!hasMore)
				break;
		}
		log.info("Broker market catalog for {}: {} eligible, {} displayable, {} without a broker mask", race,
			templates.size(), result.size(), templates.size() - result.size());
		return result;
	}

	private boolean eligible(ItemTemplate t, Race race) {
		if (!t.isTradeable() || t.getPrice() < 0 ||
			((t.isWeapon() || t.isArmor() || t.isStigma()) && t.getLevel() > GSConfig.PLAYER_MAX_LEVEL) ||
			(t.getRace() != Race.PC_ALL && t.getRace() != race) || t.isKinah())
			return false;
		String name = t.getName().toLowerCase(java.util.Locale.ROOT);
		if (name.isBlank() || name.contains("test") || name.contains("npc") || name.contains("debug") ||
			name.contains("prototype") || name.contains("dummy") || name.contains("placeholder") ||
			name.startsWith("[temp"))
			return false;
		// A value of 5 is used by thousands of internal weapon and armor templates.
		return !(t.isWeapon() || t.isArmor()) || t.getPrice() != 5;
	}

	private void refillSafely() {
		boolean needsCatchup = true;
		try {
			needsCatchup = populateAll();
		} catch (Exception e) {
			log.error("Broker market refill failed", e);
		} finally {
			scheduleNext(needsCatchup);
		}
	}

	private synchronized boolean populateAll() {
		if (!BrokerMarketConfig.ENABLED || !initialized)
			return false;
		boolean elyosMissing = populateRace(Race.ELYOS, BrokerRace.ELYOS);
		boolean asmodianMissing = populateRace(Race.ASMODIANS, BrokerRace.ASMODIAN);
		return elyosMissing || asmodianMissing;
	}

	private boolean populateRace(Race race, BrokerRace brokerRace) {
		int[] raceSellers = sellers.get(race);
		if (raceSellers == null || raceSellers.length == 0)
			return false;
		List<ItemTemplate> entries = catalog.get(race);
		if (entries == null || entries.isEmpty())
			return false;
		BrokerService broker = BrokerService.getInstance();
		Set<Integer> listed = broker.getActiveItemIds(race);
		int added = 0;
		int failures = 0;
		int limit = Math.max(1, Math.min(10000, BrokerMarketConfig.MAX_ITEMS_PER_REFILL));
		int start = nextIndex.get(race);
		int scanned = 0;
		boolean missing = false;
		for (; scanned < entries.size(); scanned++) {
			ItemTemplate template = entries.get((start + scanned) % entries.size());
			if (listed.contains(template.getTemplateId()))
				continue;
			if (added >= limit || failures >= 10) {
				missing = true;
				break;
			}
			long count = stackCount(template);
			long price = unitPrice(template, race);
			int seller = raceSellers[ThreadLocalRandom.current().nextInt(raceSellers.length)];
			if (broker.addSimulatedItem(template.getTemplateId(), count, price, seller, brokerRace)) {
				listed.add(template.getTemplateId());
				added++;
			} else
				failures++;
		}
		nextIndex.put(race, (start + scanned) % entries.size());
		if (added > 0)
			log.info("Broker market added {} listings for {}; active listings: {} of {} catalog items", added, race,
				broker.getRaceItemCount(race), entries.size());
		return missing || failures > 0;
	}

	private long stackCount(ItemTemplate template) {
		if (!template.isStackable())
			return 1;
		long max = Math.min(template.getMaxStackCount(), Math.max(1, BrokerMarketConfig.MAX_STACK));
		long min = Math.max(1, max / 4);
		return ThreadLocalRandom.current().nextLong(min, max + 1);
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

	private void scheduleNext(boolean needsCatchup) {
		if (!BrokerMarketConfig.ENABLED)
			return;
		if (needsCatchup) {
			long seconds = Math.max(1, BrokerMarketConfig.CATCHUP_INTERVAL_SECONDS);
			ThreadPoolManager.getInstance().schedule(this::refillSafely, TimeUnit.SECONDS.toMillis(seconds));
			return;
		}
		long min = Math.max(1, BrokerMarketConfig.REFILL_MIN_MINUTES);
		long max = Math.max(min, BrokerMarketConfig.REFILL_MAX_MINUTES);
		long delay = ThreadLocalRandom.current().nextLong(min, max + 1);
		log.info("Broker market is stocked; next refill check in {} minutes", delay);
		ThreadPoolManager.getInstance().schedule(this::refillSafely, TimeUnit.MINUTES.toMillis(delay));
	}
}
