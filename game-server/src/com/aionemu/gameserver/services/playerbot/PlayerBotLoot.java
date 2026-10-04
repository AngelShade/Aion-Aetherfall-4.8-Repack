package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.actions.PlayerMode;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.drop.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Normal loot rights and inventory rules; never spends a character's money on bids. */
final class PlayerBotLoot {
	private final Map<Integer, Long> attempted = new HashMap<>();
	void passRoll(Player bot) {
		if (bot.isInPlayerMode(PlayerMode.IN_ROLL) && bot.inRoll != null) {
			var roll = bot.inRoll;
			boolean useful = roll.getRollType() == 2 && PlayerBotGearPolicy.roll(bot, DataManager.ITEM_DATA.getItemTemplate(roll.getItemId()));
			DropDistributionService.getInstance().handleRollOrBid(bot, roll.getRollType(), useful ? 1 : 0, 0,
				roll.getItemId(), roll.getNpcId(), roll.getIndex());
		}
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
