package com.aionemu.gameserver.services.dynamicevents;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.DynamicEventConfig;
import com.aionemu.gameserver.dao.DynamicEventDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.Rates;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * Event rewards as Guild Wars 2 pays them: every finished part pays XP and Kinah by contribution tier (Gold 100%, Silver 85%,
 * Bronze 75%); a failed part pays half; players ten or more levels from the event earn nothing. A defeated leader also opens
 * a daily chest per character and a daily bonus chest per account. Bots only count toward the shares.
 */
final class EventRewards {

	private static final Logger log = LoggerFactory.getLogger(EventRewards.class);

	enum Tier {
		GOLD, SILVER, BRONZE, NONE;

		String label() {
			return name().charAt(0) + name().substring(1).toLowerCase();
		}
	}

	/** Share of the top contributor: Gold from goldPercent, Silver from silverPercent, Bronze above zero. */
	static Tier tier(double points, double top, int goldPercent, int silverPercent) {
		if (points <= 0 || top <= 0)
			return Tier.NONE;
		double share = points * 100 / top;
		return share >= goldPercent ? Tier.GOLD : share >= silverPercent ? Tier.SILVER : Tier.BRONZE;
	}

	/** Percent of the Gold reward a tier pays. */
	static int tierPercent(Tier tier, int silverPercent, int bronzePercent) {
		return switch (tier) {
			case GOLD -> 100;
			case SILVER -> Math.clamp(silverPercent, 0, 100);
			case BRONZE -> Math.clamp(bronzePercent, 0, 100);
			case NONE -> 0;
		};
	}

	/** Players this many levels or more from the event take no part in its rewards. */
	static boolean eligible(int playerLevel, int eventLevel, int gap) {
		return Math.abs(playerLevel - eventLevel) < Math.max(1, gap);
	}

	/** At or below the event's level the reward follows the event's level; above it, the player's own. */
	static int rewardLevel(int playerLevel, int eventLevel) {
		return Math.max(playerLevel, eventLevel);
	}

	/** permille of a level's XP, by tier, halved (failedPercent) for a failed part. */
	static long xp(long levelNeed, int permille, int tierPercent, boolean success, int failedPercent) {
		return Math.max(0, levelNeed) * Math.max(0, permille) / 1000 * tierPercent / 100 * (success ? 100 : Math.clamp(failedPercent, 0, 100)) / 100;
	}

	static long kinah(int level, int perLevel, int tierPercent, boolean success, int failedPercent) {
		return (long) Math.max(1, level) * Math.max(0, perLevel) * tierPercent / 100 * (success ? 100 : Math.clamp(failedPercent, 0, 100)) / 100;
	}

	/** The XP between this level and the next; 0 at the last level. */
	static long levelXp(int level) {
		var table = DataManager.PLAYER_EXPERIENCE_TABLE;
		if (level >= table.getMaxLevel())
			return 0;
		return table.getStartExpForLevel(level + 1) - table.getStartExpForLevel(level);
	}

	/** One finished part (won or lost), at the event's level. */
	static void stage(Player player, String title, Tier tier, boolean success, int eventLevel) {
		if (tier == Tier.NONE)
			return;
		if (!eligible(player.getLevel(), eventLevel, DynamicEventConfig.REWARD_LEVEL_GAP)) {
			PacketSendUtility.sendMessage(player, "[Event] " + title + ": you are too far from the event's level " + eventLevel + " for a reward.");
			return;
		}
		int percent = tierPercent(tier, DynamicEventConfig.SILVER_REWARD, DynamicEventConfig.BRONZE_REWARD);
		int level = rewardLevel(player.getLevel(), eventLevel);
		long xp = player.getLevel() >= DataManager.PLAYER_EXPERIENCE_TABLE.getMaxLevel() ? 0
			: xp(levelXp(level), DynamicEventConfig.STAGE_XP_PERMILLE, percent, success, DynamicEventConfig.FAILED_REWARD);
		long kinah = kinah(level, DynamicEventConfig.KINAH_PER_LEVEL, percent, success, DynamicEventConfig.FAILED_REWARD);
		if (xp > 0)
			player.getCommonData().addExp(xp, Rates.XP_QUEST, title);
		if (kinah > 0)
			player.getInventory().increaseKinah(kinah);
		PacketSendUtility.sendMessage(player, "[Event] " + title + (success ? " complete" : " failed") + ": " + tier.label() + " contribution. "
			+ (xp > 0 ? xp + " XP, " : "") + kinah + " Kinah.");
	}

	/** Rolls of the leader's native drop for the bonus chest's guaranteed gear (Guild Wars 2: a rare piece once a day). */
	static final int GEAR_ROLLS = 40;

	/** Bonus-chest gear: equipment of Superior grade or better that this player's race may wear. */
	static boolean rareGear(ItemTemplate t, Race race) {
		return t != null && (t.isArmor() || t.isWeapon()) && t.getItemQuality().getQualityId() >= ItemQuality.RARE.getQualityId()
			&& (t.getRace() == null || t.getRace() == Race.PC_ALL || t.getRace() == race);
	}

	/** The first such piece in up to GEAR_ROLLS personal rolls of the leader's native drop, or null. */
	static ItemTemplate rollGear(com.aionemu.gameserver.model.gameobjects.Npc leader, Player player) {
		try {
			for (int roll = 0; roll < GEAR_ROLLS; roll++)
				for (var item : com.aionemu.gameserver.services.drop.DropRegistrationService.getInstance().personalDrop(leader, player)) {
					ItemTemplate t = DataManager.ITEM_DATA.getItemTemplate(item.getDropTemplate().getItemId());
					if (rareGear(t, player.getRace()))
						return t;
				}
		} catch (RuntimeException e) {
			log.warn("Dynamic event bonus gear roll for {} fell back to tickets: {}", player.getObjectId(), e.toString());
		}
		return null;
	}

	/** The defeated leader's chests: once per character per chain per day, and a bonus once per account per day. Off the tick. */
	static void chest(Player player, String chainId, String chainTitle, com.aionemu.gameserver.model.gameobjects.Npc leader, Tier tier, int eventLevel) {
		if (tier == Tier.NONE || player.getAccount() == null || player.getAccount().getId() <= 0
			|| !eligible(player.getLevel(), eventLevel, DynamicEventConfig.REWARD_LEVEL_GAP))
			return;
		ThreadPoolManager.getInstance().execute(() -> {
			LocalDate day = LocalDate.now();
			int account = player.getAccount().getId();
			boolean chest, bonus;
			try (var c = DatabaseFactory.getConnection()) {
				chest = DynamicEventDAO.claimChest(c, player.getObjectId(), chainId, day);
				bonus = chest && DynamicEventDAO.claimBonus(c, account, day);
			} catch (SQLException e) {
				log.warn("Dynamic event chest for player {} awaits storage: {}", player.getObjectId(), e.toString());
				PacketSendUtility.sendMessage(player, "[Event] The chest could not be opened right now; defeat the leader again later today.");
				return;
			}
			if (!chest) {
				PacketSendUtility.sendMessage(player, "[Event] You already opened " + chainTitle + "'s chest today.");
				return;
			}
			long kinah = (long) Math.max(1, player.getLevel()) * Math.max(0, DynamicEventConfig.CHEST_KINAH_PER_LEVEL);
			if (kinah > 0)
				player.getInventory().increaseKinah(kinah);
			int tickets = Math.max(0, DynamicEventConfig.CHEST_TICKETS);
			if (tickets > 0)
				com.aionemu.gameserver.services.playerbot.PlayerBotTickets.award(account, "dynamic-event-chest:" + player.getObjectId() + ":" + chainId + ":" + day, tickets);
			PacketSendUtility.sendMessage(player, "[Event] " + chainTitle + " chest: " + kinah + " Kinah" + (tickets > 0 ? ", " + tickets + " Recruitment Ticket"
				+ (tickets == 1 ? "" : "s") : "") + ". It opens once a day.");
			if (!bonus)
				return;
			ItemTemplate item = rollGear(leader, player);
			if (item != null) {
				give(player, item, "Bonus chest");
				PacketSendUtility.sendMessage(player, "[Event] Bonus chest (once a day per account): " + item.getName() + ".");
			} else {
				int extra = Math.max(0, DynamicEventConfig.BONUS_CHEST_TICKETS);
				if (extra > 0)
					com.aionemu.gameserver.services.playerbot.PlayerBotTickets.award(account, "dynamic-event-bonus:" + account + ":" + day, extra);
				PacketSendUtility.sendMessage(player, "[Event] Bonus chest (once a day per account): " + extra + " Recruitment Ticket" + (extra == 1 ? "" : "s") + ".");
			}
		});
	}

	/** Into the bag; what does not fit arrives by system mail. */
	static void give(Player player, ItemTemplate item, String title) {
		long left = ItemService.addItem(player, item.getTemplateId(), 1);
		if (left > 0)
			SystemMailService.sendMail("World Event", player.getName(), title, "Your bag was full, so your reward came by mail.", item.getTemplateId(), left, 0,
				LetterType.NORMAL);
	}

	private EventRewards() {
	}
}
