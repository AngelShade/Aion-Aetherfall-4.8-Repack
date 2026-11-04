package com.aionemu.gameserver.services;

import com.aionemu.gameserver.model.templates.item.ItemTemplate;

/** Native item classifications shared by generated shop stock and automatic combat equipment. */
public final class ItemEligibility {
	private ItemEligibility() {}

	/** NPC-summoning consumables remain player items; the NPC flag only covers equipment. */
	public static boolean isPlayerItem(ItemTemplate template) {
		return template != null && !template.getName().isBlank() && !template.isTestItem() && !template.isNonPlayerEquipment();
	}

	/** Appearance families are not combat upgrades, even when early skins have nominal stats. */
	public static boolean isAppearanceOnly(ItemTemplate template) {
		if (template == null || !(template.isWeapon() || template.isArmor())) return false;
		var remodel = template.getActions() == null ? null : template.getActions().getRemodelAction();
		if (template.isAppearanceItem() || remodel != null && remodel.getExtractType() == 2) return true;
		if (template.getModifiers() != null && template.getModifiers().stream().anyMatch(stat -> stat.getValue() != 0)) return false;
		var weapon = template.getWeaponStats();
		if (weapon != null && (weapon.getMinDamage() != 0 || weapon.getMaxDamage() != 0 || weapon.getBoostMagicalSkill() != 0
			|| weapon.getMagicalAccuracy() != 0 || weapon.getPhysicalAccuracy() != 0 || weapon.getCritical() != 0 || weapon.getParry() != 0)) return false;
		return template.getStatBonusSetId() == 0 && WardrobeRules.category(template).equals("Costumes");
	}
}
