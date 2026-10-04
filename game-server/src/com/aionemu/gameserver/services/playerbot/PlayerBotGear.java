/* Inventory equipment selection adapted from mod-playerbots EquipAction.cpp and
 * ItemUsageValue.cpp, revision 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later. Attribution: third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

final class PlayerBotGear {
 static List<PlayerBotEquipment.Upgrade> upgrades(Player bot,Role role) {
  List<PlayerBotEquipment.Upgrade> choices=new ArrayList<>();
  for(Item item:bot.getInventory().getItems()) {
   var t=item.getItemTemplate();long mask=t.getItemSlot();int level=t.getRequiredLevel(bot.getPlayerClass());
   if(mask==0 || ItemSlot.isStigma(mask) || !item.isIdentified() || item.isEquipped() || t.isSoulBound() && !item.isSoulBound()
    || !t.isClassSpecific(bot.getPlayerClass()) || level<0 || level>bot.getLevel())continue;
   var mastery=DataManager.SKILL_DATA.getMasterySkills(t.getItemGroup());
   if(!mastery.isEmpty() && mastery.stream().noneMatch(bot.getSkillList()::isSkillPresent))continue;
   if(t.isWeapon()) {
    long slot=t.isTwoHandWeapon() ? ItemSlot.MAIN_OR_SUB.getSlotIdMask() : ItemSlot.MAIN_HAND.getSlotIdMask();
    var replaced=bot.getEquipment().getEquippedItems().stream().filter(i->(i.getEquipmentSlot() & slot)!=0).toList();
    if(role==Role.TANK && t.isTwoHandWeapon() && replaced.stream().anyMatch(i->i.getItemTemplate().getItemGroup()==com.aionemu.gameserver.model.templates.item.enums.ItemGroup.SHIELD))continue;
    // Compare the whole displaced hand setup, including off-hand equipment.
    double current=replaced.stream().mapToDouble(i->PlayerBotEquipment.score(i,role,bot.getPlayerClass())).sum();
    choices.add(new PlayerBotEquipment.Upgrade(item,slot,PlayerBotEquipment.score(item,role,bot.getPlayerClass())-current));
   }else for(var slot:ItemSlot.getSlotsFor(mask)) {
    if(slot==ItemSlot.POWER_SHARD_LEFT || slot==ItemSlot.POWER_SHARD_RIGHT || slot==ItemSlot.MAIN_OFF_HAND || slot==ItemSlot.SUB_OFF_HAND)continue;
    var replaced=bot.getEquipment().getEquippedItems().stream().filter(i->(i.getEquipmentSlot() & slot.getSlotIdMask())!=0).toList();
    if(replaced.stream().anyMatch(i->Long.bitCount(i.getEquipmentSlot())>1))continue;
    double current=replaced.stream().mapToDouble(i->PlayerBotEquipment.score(i,role,bot.getPlayerClass())).sum();
    choices.add(new PlayerBotEquipment.Upgrade(item,slot.getSlotIdMask(),PlayerBotEquipment.score(item,role,bot.getPlayerClass())-current));
   }
  }
  return choices.stream().filter(u->u.gain()>1).sorted(Comparator.comparingDouble(PlayerBotEquipment.Upgrade::gain).reversed()
   .thenComparingInt(u->u.item().getObjectId()).thenComparingLong(PlayerBotEquipment.Upgrade::slot)).toList();
 }
 private PlayerBotGear() {}
}
