package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Conservative upgrades from the companion's own cube, using the normal equipment rules. */
final class PlayerBotEquipment {
	record Upgrade(Item item, long slot, double gain) {}

	static List<Upgrade> upgrades(Player bot, Role role) {
		List<Upgrade> result = new ArrayList<>();
		for (Item item : bot.getInventory().getItems()) {
			var template = item.getItemTemplate();
			long mask = template.getItemSlot();
			if (mask == 0 || ItemSlot.isStigma(mask) || !item.isIdentified() || item.isEquipped()
				|| template.isSoulBound() && !item.isSoulBound() || !template.isClassSpecific(bot.getPlayerClass())
				|| template.getRequiredLevel(bot.getPlayerClass()) < 0 || template.getRequiredLevel(bot.getPlayerClass()) > bot.getLevel()) continue;
			var mastery = DataManager.SKILL_DATA.getMasterySkills(template.getItemGroup());
			if (!mastery.isEmpty() && mastery.stream().noneMatch(bot.getSkillList()::isSkillPresent)) continue;
			// Do not replace the user's weapon style, merge dual weapons or remove a tank's shield.
			if (template.isWeapon()) {
				var current = bot.getEquipment().getEquippedItems().stream().filter(i -> (i.getEquipmentSlot() & ItemSlot.MAIN_HAND.getSlotIdMask()) != 0).findFirst().orElse(null);
				if (current == null || current.getItemTemplate().getItemGroup() != template.getItemGroup()) continue;
				long slot = template.isTwoHandWeapon() ? ItemSlot.MAIN_OR_SUB.getSlotIdMask() : ItemSlot.MAIN_HAND.getSlotIdMask();
				result.add(new Upgrade(item, slot, score(item, role, bot.getPlayerClass()) - score(current, role, bot.getPlayerClass())));
				continue;
			}
			for (var slot : ItemSlot.getSlotsFor(mask)) {
				if (slot == ItemSlot.POWER_SHARD_LEFT || slot == ItemSlot.POWER_SHARD_RIGHT || slot == ItemSlot.MAIN_OFF_HAND || slot == ItemSlot.SUB_OFF_HAND) continue;
				var current = bot.getEquipment().getEquippedItems().stream().filter(i -> (i.getEquipmentSlot() & slot.getSlotIdMask()) != 0).findFirst().orElse(null);
				// Avoid taking apart costumes/sets that occupy multiple slots with one item.
				if (current != null && Long.bitCount(current.getEquipmentSlot()) > 1) continue;
				result.add(new Upgrade(item, slot.getSlotIdMask(), score(item, role, bot.getPlayerClass()) - (current == null ? 0 : score(current, role, bot.getPlayerClass()))));
			}
		}
		return result.stream().filter(u -> u.gain() > 1).sorted(Comparator.comparingDouble(Upgrade::gain).reversed()).toList();
	}

	static double score(Item item, Role role, com.aionemu.gameserver.model.PlayerClass playerClass) {
		return score(item.getItemTemplate(), role, playerClass) + item.getEnchantLevel() * 2;
	}
	static double score(com.aionemu.gameserver.model.templates.item.ItemTemplate template, Role role, com.aionemu.gameserver.model.PlayerClass playerClass) {
		var weapon = template.getWeaponStats();
		double result = template.getLevel() * 0.1;
		boolean magical = role == Role.HEALER || switch (playerClass) {
			case MAGE, SORCERER, SPIRIT_MASTER, ENGINEER, GUNNER, RIDER, ARTIST, BARD -> true;
			default -> false;
		};
		if (template.isWeapon()) result += magical ? weapon.getBoostMagicalSkill() * 0.7 + weapon.getMagicalAccuracy() * 0.15
			: weapon.getMeanDamage() * 3 + weapon.getCritical() * 0.3 + weapon.getPhysicalAccuracy() * 0.15;
		if (template.getModifiers() != null)
			for (var modifier : template.getModifiers()) {
				if (modifier.hasConditions()) continue;
				result += modifier.getValue() * weight(modifier.getName(), role, magical);
			}
		return result;
	}

	static double weight(StatEnum stat, Role role, boolean magical) {
		return switch (stat) {
			case MAXHP -> role == Role.TANK ? 0.5 : 0.15;
			case MAXMP -> role == Role.HEALER ? 0.15 : 0.05;
			case HEAL_BOOST -> role == Role.HEALER ? 2 : role == Role.SUPPORT ? 1 : 0;
			case BLOCK, PARRY, PHYSICAL_DEFENSE, MAGICAL_RESIST, MAGICAL_DEFEND -> role == Role.TANK ? 1 : 0.25;
			case PHYSICAL_ATTACK, MAX_DAMAGES, MIN_DAMAGES -> magical ? 0 : 2;
			case BOOST_MAGICAL_SKILL, MAGICAL_ATTACK -> magical ? 1 : 0;
			case PHYSICAL_CRITICAL -> magical ? 0 : 0.75;
			case MAGICAL_CRITICAL -> magical ? 1 : 0;
			case PHYSICAL_ACCURACY, MAGICAL_ACCURACY -> 0.15;
			case ATTACK_SPEED, BOOST_CASTING_TIME -> 4;
			default -> 0;
		};
	}
	private PlayerBotEquipment() {}
}
