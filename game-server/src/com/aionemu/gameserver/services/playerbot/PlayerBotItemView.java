package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.items.ManaStone;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.bonuses.StatBonusType;
import com.aionemu.gameserver.model.stats.calc.functions.*;

/** Read-only item projection for the installed native hover bridge and companion cube. */
final class PlayerBotItemView {
 static Map<String,Object> stored(Item item) {
  return decorate(Map.of("id",item.getObjectId(),"itemId",item.getItemId(),"name",item.getItemTemplate().getName(),"count",item.getItemCount()),item);
 }
 static Map<String,Object> decorate(Map<String,Object> original,Item item) {
  var view=new LinkedHashMap<String,Object>(original);
  long mask=item.isEquipped()?item.getEquipmentSlot():0;
  view.put("equippedSlots",mask==0?List.of():Arrays.stream(ItemSlot.getSlotsFor(mask)).map(Enum::name).toList());
  view.put("tooltip","item="+item.getItemId()+"&count="+item.getItemCount()+"&enchant_count="+item.getEnchantLevel()+"&authorize_count="+item.getTempering());
  view.put("enchant",item.getEnchantLevel());view.put("tempering",item.getTempering());
  view.put("quality",item.getItemTemplate().getItemQuality()==null?"":item.getItemTemplate().getItemQuality().name());
  view.put("itemLevel",item.getItemTemplate().getLevel());view.put("bonusStatsId",item.getBonusStatsId());
  view.put("fusionItemId",item.getFusionedItemId());view.put("fusionBonusStatsId",item.getFusionedItemBonusStatsId());
  view.put("bonusModifiers",bonuses(item.getItemTemplate(),item.getBonusStatsId()));
  view.put("fusionName",item.getFusionedItemTemplate()==null?"":item.getFusionedItemTemplate().getName());
  view.put("fusionModifiers",bonuses(item.getFusionedItemTemplate(),item.getFusionedItemBonusStatsId()));
  view.put("godstoneId",item.getGodStoneId());
  view.put("godstoneName",item.getGodStone()==null?"":item.getGodStone().getItemTemplate().getName());
  view.put("manastones",stones(item.getItemStones()));view.put("fusionStones",stones(item.getFusionStones()));
  return view;
 }
 private static List<Map<String,Object>> bonuses(ItemTemplate template,int roll) {
  if(template==null || roll<=0 || template.getStatBonusSetId()==0 || DataManager.ITEM_RANDOM_BONUSES==null)return List.of();
  try {
   var nativeRoll=DataManager.ITEM_RANDOM_BONUSES.getTemplate(StatBonusType.INVENTORY,template.getStatBonusSetId(),roll);
   return nativeRoll==null?List.of():modifiers(nativeRoll.getModifiers());
  } catch(IndexOutOfBoundsException ex) { return List.of(); }
 }
 static List<Map<String,Object>> modifiers(List<StatFunction> nativeStats) {
  if(nativeStats==null)return List.of();
  return nativeStats.stream().filter(s->s.getName()!=null).map(s->Map.<String,Object>of(
   "name",s.getName().name(),"value",s.getValue(),"conditional",s.hasConditions(),
   "operation",s instanceof StatRateFunction?"rate":s instanceof StatSubFunction?"subtract":s instanceof StatSetFunction?"set":s instanceof StatAbsFunction?"absolute":"add")).toList();
 }
 private static List<Map<String,Object>> stones(Set<ManaStone> stones) {
  return stones.stream().sorted(Comparator.comparingInt(ManaStone::getSlot)).map(s->Map.<String,Object>of("slot",s.getSlot(),"itemId",s.getItemId(),"name",s.getItemTemplate().getName())).toList();
 }
 private PlayerBotItemView(){}
}
