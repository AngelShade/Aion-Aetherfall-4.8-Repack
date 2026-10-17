package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.drop.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Normal loot rights and inventory rules; never spends a character's money on bids. */
public final class PlayerBotLoot {
	private final Map<Integer, Long> attempted = new HashMap<>();
	/** Read-only native current-stage objective requirements, also used to permit
	 * human Need votes for quest items despite ordinary class/equipment limits. */
	public static boolean needsObjective(Player player, int itemId) {
		long owned = player.getInventory().getItemCountByItemId(itemId);
		for (var quest : player.getQuestStateList().getUncompletedQuests())
			if (PlayerBotQuestCraftingDriver.requirements(player, quest.getQuestId()).getOrDefault(itemId, 0L) > owned) return true;
		return false;
	}
	void passRoll(Player bot) {
		var distribution = DropDistributionService.getInstance();
		for (var roll : distribution.getPendingRolls(bot)) {
			int vote = roll.getRollType() == 2 ? vote(bot, DataManager.ITEM_DATA.getItemTemplate(roll.getItemId())) : DropDistributionService.PASS;
			distribution.handleRollOrBid(bot, roll.getRollType(), vote, 0,
				roll.getItemId(), roll.getNpcId(), roll.getIndex());
		}
	}
	/** LootRollAction/ItemUsageValue at pinned 037c014: usable upgrades Need,
	 * useful supplies/vendor items Greed, unusable/unique/full-bag items Pass.
	 * User-requested differences: required native quest objectives also Need;
	 * Greed is enabled here rather than copying WoW's default disabled option. */
	private int vote(Player bot, ItemTemplate template) {
		if (template == null || PlayerBotGearPolicy.settings(bot).rolls() == PlayerBotGearPolicy.Rolls.PASS
			|| bot.getInventory().isFull(template.getExtraInventoryId())) return DropDistributionService.PASS;
		int id = template.getTemplateId();
		if (template.hasLimitOne() && (bot.getInventory().getItemCountByItemId(id) > 0
			|| bot.getEquipment().getEquippedItems().stream().anyMatch(i -> i.getItemId() == id)
			|| bot.getStorage(StorageType.REGULAR_WAREHOUSE.getId()).getFirstItemByItemId(id) != null)) return DropDistributionService.PASS;
		long owned = bot.getInventory().getItemCountByItemId(id);
		if (needsObjective(bot, id)) return DropDistributionService.NEED;
		if (PlayerBotGearPolicy.roll(bot, template)) return DropDistributionService.NEED;
		boolean supplies = PlayerBotSupplies.available(bot, template) && owned < Math.max(1, template.getMaxStackCount());
		boolean vendor = (template.getMask() & ItemMask.SELLABLE) != 0 && template.getPrice() > 0;
		return supplies || vendor || PlayerBotGearPolicy.settings(bot).rolls() == PlayerBotGearPolicy.Rolls.ALL
			? DropDistributionService.GREED : DropDistributionService.PASS;
	}
	Npc choose(Player owner, Player bot) {
		long now = System.currentTimeMillis(); attempted.values().removeIf(time -> time < now);
		List<Npc> candidates = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			if (npc.isDead() && npc.isSpawned() && npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId()
				&& PositionUtil.isInRange(owner, npc, 15) && !attempted.containsKey(npc.getObjectId()) && !eligible(bot, npc).isEmpty())
				candidates.add(npc);
		});
		return candidates.stream().min(Comparator.comparingDouble(npc -> PositionUtil.getDistance(bot, npc))).orElse(null);
	}
	boolean collect(Player bot, Npc npc) {
		if (bot.isDead() || bot.isCasting() || !npc.isDead() || !npc.isSpawned() || !PositionUtil.isInRange(bot, npc, 3)
			|| !GeoService.getInstance().canSee(bot, npc)) return false;
		List<DropItem> items = eligible(bot, npc);
		if (items.isEmpty()) return false;
		attempted.put(npc.getObjectId(), System.currentTimeMillis() + 10000);
		var service = DropService.getInstance();
		service.requestDropList(bot, npc.getObjectId());
		if (!bot.isLooting() || bot.getLootingNpcOid() != npc.getObjectId()) return false;
		try {
			for (DropItem item : items)
				if (item.canViewDropItem(bot.getObjectId())) service.requestDropItem(bot, npc.getObjectId(), item.getIndex(), true);
		} finally { service.closeDropList(bot, npc.getObjectId()); }
		return true;
	}
	private List<DropItem> eligible(Player bot, Npc npc) {
		var registrations = DropRegistrationService.getInstance();
		var drop = registrations.getDropRegistrationMap().get(npc.getObjectId());
		var items = registrations.getCurrentDropMap().get(npc.getObjectId());
		if (drop == null || items == null || !drop.isAllowedToLoot(bot) || drop.isBeingLooted()
			|| !drop.getInRangePlayers().contains(bot)) return List.of();
		List<DropItem> result = new ArrayList<>();
		synchronized (items) {
			for (DropItem item : items) {
				if (!item.canViewDropItem(bot.getObjectId()) || item.getCount() <= 0 || item.isDistributeItem()) continue;
				var template = DataManager.ITEM_DATA.getItemTemplate(item.getDropTemplate().getItemId());
				if (template == null || bot.getInventory().isFull(template.getExtraInventoryId())) continue;
				var rules = drop.getLootGroupRules();
				if (!item.isFreeForAll() && rules != null
					&& (rules.containDropItem(item) || rules.getAutodistributionId() > 1 && rules.getQualityRule(template.getItemQuality()))) continue;
				result.add(item);
			}
		}
		return result;
	}
}
