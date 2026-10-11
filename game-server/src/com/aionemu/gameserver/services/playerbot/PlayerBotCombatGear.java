package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.WardrobeRules;

/** Appearance-only items never qualify as automatic combat gear, even when transmog is disabled. */
final class PlayerBotCombatGear {
 static boolean skinOnly(ItemTemplate template) {
  if(template==null || !(template.isWeapon() || template.isArmor()))return false;
  if(template.getModifiers()!=null && template.getModifiers().stream().anyMatch(stat->stat.getValue()!=0))return false;
  var weapon=template.getWeaponStats();
  if(weapon!=null && (weapon.getMinDamage()!=0 || weapon.getMaxDamage()!=0 || weapon.getBoostMagicalSkill()!=0
   || weapon.getMagicalAccuracy()!=0 || weapon.getPhysicalAccuracy()!=0 || weapon.getCritical()!=0 || weapon.getParry()!=0))return false;
  if(template.getStatBonusSetId()!=0)return false;
  var remodel=template.getActions()==null ? null : template.getActions().getRemodelAction();
  return WardrobeRules.category(template).equals("Costumes") || remodel!=null && remodel.getExtractType()==2;
 }
 private PlayerBotCombatGear(){}
}
