package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.ItemEligibility;

/** Appearance-only items never qualify as automatic combat gear, even when transmog is disabled. */
final class PlayerBotCombatGear {
 static boolean skinOnly(ItemTemplate template) {
  // Owned or earned equipment may carry a remodel action as well as real stats.
  // Keep the deliberate shop/acquisition classification in ItemEligibility.
  if(template!=null && !template.isAppearanceItem() && (template.isWeapon() || template.isArmor())) {
   if(template.getModifiers()!=null && template.getModifiers().stream().anyMatch(stat->stat.getValue()!=0))return false;
   var weapon=template.getWeaponStats();
   if(weapon!=null && (weapon.getMinDamage()!=0 || weapon.getMaxDamage()!=0 || weapon.getBoostMagicalSkill()!=0
    || weapon.getMagicalAccuracy()!=0 || weapon.getPhysicalAccuracy()!=0 || weapon.getCritical()!=0 || weapon.getParry()!=0))return false;
  }
  return ItemEligibility.isAppearanceOnly(template);
 }
 static boolean usable(ItemTemplate template){return ItemEligibility.isPlayerItem(template) && !skinOnly(template);}
 private PlayerBotCombatGear(){}
}
