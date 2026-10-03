package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.item.ItemFactory;

/** Common-quality, character-bound starting equipment for the dedicated roster. */
final class PlayerBotGenerated {
	static ItemGroup weapon(PlayerClass pc) {
		return switch (pc) {
			case GLADIATOR -> ItemGroup.POLEARM;
			case SCOUT, ASSASSIN -> ItemGroup.DAGGER;
			case RANGER -> ItemGroup.BOW;
			case MAGE, SPIRIT_MASTER -> ItemGroup.SPELLBOOK;
			case SORCERER -> ItemGroup.ORB;
			case PRIEST, CLERIC -> ItemGroup.MACE;
			case CHANTER -> ItemGroup.STAFF;
			case ENGINEER, GUNNER -> ItemGroup.GUN;
			case RIDER -> ItemGroup.KEYBLADE;
			case ARTIST, BARD -> ItemGroup.HARP;
			default -> ItemGroup.SWORD;
		};
	}

	static boolean eligible(ItemTemplate template, Player bot) {
		return eligible(template, bot.getPlayerClass(), bot.getRace(), bot.getGender(), bot.getLevel(), bot.getSkillList()::isSkillPresent);
	}

	static boolean eligible(ItemTemplate template, PlayerClass pc, Race race, com.aionemu.gameserver.model.Gender gender,
		int level, java.util.function.IntPredicate learned) {
		int required = template.getRequiredLevel(pc);
		var limits = template.getUseLimits();
		var mastery = DataManager.SKILL_DATA.getMasterySkills(template.getItemGroup());
		int maxLevel = template.getMaxLevelRestrict(pc);
		return template.getItemQuality() == ItemQuality.COMMON && template.getItemSlot() != 0 && template.getMaxTuneCount() == 0
			&& template.isClassSpecific(pc) && required >= 0 && required <= level && template.getLevel() <= level
			&& (maxLevel == 0 || maxLevel >= level) && !template.hasAreaRestriction()
			&& (template.getRace() == Race.PC_ALL || template.getRace() == race)
			&& (limits.getGenderPermitted() == null || limits.getGenderPermitted() == gender)
			&& limits.getMinRank() <= 1 && limits.getMaxRank() >= 1
			&& (mastery.isEmpty() || mastery.stream().anyMatch(id -> learned.test(id)));
	}

	static void equipStarterSet(Player bot) {
		List<ItemTemplate> choices = DataManager.ITEM_DATA.getItemTemplates().stream().filter(t -> eligible(t, bot))
			.sorted(Comparator.comparingInt(ItemTemplate::getLevel).reversed().thenComparingInt(ItemTemplate::getTemplateId)).toList();
		ItemTemplate weapon = choices.stream().filter(t -> t.getItemGroup() == weapon(bot.getPlayerClass())).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No compatible common-quality weapon template for " + bot.getPlayerClass()));
		addEquipped(bot, weapon, weapon.isTwoHandWeapon() ? ItemSlot.MAIN_OR_SUB.getSlotIdMask() : ItemSlot.MAIN_HAND.getSlotIdMask());
		List<ItemSlot> slots = new ArrayList<>(List.of(ItemSlot.HELMET, ItemSlot.TORSO, ItemSlot.SHOULDER, ItemSlot.GLOVES,
			ItemSlot.PANTS, ItemSlot.BOOTS, ItemSlot.NECKLACE, ItemSlot.WAIST, ItemSlot.EARRINGS_LEFT, ItemSlot.EARRINGS_RIGHT, ItemSlot.RING_LEFT, ItemSlot.RING_RIGHT));
		if (bot.getPlayerClass() == PlayerClass.TEMPLAR || bot.getPlayerClass() == PlayerClass.CLERIC) slots.add(ItemSlot.SUB_HAND);
		for (var slot : slots) {
			ItemTemplate selected = choices.stream().filter(t -> !t.isWeapon() && !t.isStigma()
				&& (t.getItemSlot() & slot.getSlotIdMask()) != 0 && (Long.bitCount(t.getItemSlot()) == 1
					|| t.getItemGroup() == ItemGroup.RING || t.getItemGroup() == ItemGroup.EARRING)).findFirst().orElse(null);
			if (selected != null) addEquipped(bot, selected, slot.getSlotIdMask());
		}
		bot.getEquipment().setPersistentState(com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.UPDATE_REQUIRED);
	}

	private static void addEquipped(Player bot, ItemTemplate template, long slot) {
		var item = ItemFactory.newItem(template.getTemplateId(), 1);
		if (item == null || !item.isIdentified()) throw new IllegalArgumentException("Generated equipment template is not usable: " + template.getTemplateId());
		item.setSoulBound(true); item.setEquipped(true); item.setEquipmentSlot(slot);
		bot.getInventory().onLoadHandler(item);
	}
	private PlayerBotGenerated() {}
}
