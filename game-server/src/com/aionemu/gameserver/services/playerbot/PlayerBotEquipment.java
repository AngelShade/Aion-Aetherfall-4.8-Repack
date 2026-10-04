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
		return PlayerBotGearPolicy.upgrades(bot, role);
	}

	static double score(Item item, Role role, com.aionemu.gameserver.model.PlayerClass playerClass) {
		return score(item.getItemTemplate(), role, playerClass) + item.getEnchantLevel() * 2;
	}
	static double score(com.aionemu.gameserver.model.templates.item.ItemTemplate template, Role role, com.aionemu.gameserver.model.PlayerClass playerClass) {
		var weapon = template.getWeaponStats();
		double result = template.getLevel() * 0.1;
		boolean magical = role == Role.HEALER || Set.of(com.aionemu.gameserver.model.PlayerClass.PRIEST,com.aionemu.gameserver.model.PlayerClass.CLERIC,com.aionemu.gameserver.model.PlayerClass.MAGE,com.aionemu.gameserver.model.PlayerClass.SORCERER,com.aionemu.gameserver.model.PlayerClass.SPIRIT_MASTER,com.aionemu.gameserver.model.PlayerClass.ENGINEER,com.aionemu.gameserver.model.PlayerClass.GUNNER,com.aionemu.gameserver.model.PlayerClass.RIDER,com.aionemu.gameserver.model.PlayerClass.ARTIST,com.aionemu.gameserver.model.PlayerClass.BARD).contains(playerClass);
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
		// String dispatch also keeps bounded live updates independent of cached enum-switch arrays.
		return switch (stat.name()) {
			case "MAXHP" -> role == Role.TANK ? 0.5 : 0.15;
			case "MAXMP" -> role == Role.HEALER ? 0.15 : 0.05;
			case "BOOST_HATE" -> role == Role.TANK ? 5 : -1;
			case "HEAL_BOOST" -> role == Role.HEALER ? 2 : role == Role.SUPPORT ? 1 : 0;
			case "BLOCK", "PARRY", "PHYSICAL_DEFENSE", "MAGICAL_RESIST", "MAGICAL_DEFEND" -> role == Role.TANK ? 1 : 0.25;
			case "PHYSICAL_ATTACK", "MAX_DAMAGES", "MIN_DAMAGES" -> magical ? 0 : 2;
			case "BOOST_MAGICAL_SKILL", "MAGICAL_ATTACK" -> magical ? 1 : 0;
			case "PHYSICAL_CRITICAL" -> magical ? 0 : 0.75;
			case "MAGICAL_CRITICAL" -> magical ? 1 : 0;
			case "PHYSICAL_ACCURACY" -> magical ? 0 : 0.3;
			case "MAGICAL_ACCURACY" -> magical ? 0.3 : 0;
			case "ATTACK_SPEED" -> 4;
			case "BOOST_CASTING_TIME" -> magical ? 4 : 0;
			default -> 0;
		};
	}
	private PlayerBotEquipment() {}
}
