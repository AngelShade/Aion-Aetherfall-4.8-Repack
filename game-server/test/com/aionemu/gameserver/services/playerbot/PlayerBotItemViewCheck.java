package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.stats.calc.functions.*;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.condition.Conditions;

/** Actual native Items, no World, SQL, native IDs or game actors. */
public final class PlayerBotItemViewCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var template=new ItemTemplate(){
   @Override public int getTemplateId(){return 100000096;}
   @Override public String getName(){return "Fixture sword";}
   @Override public long getItemSlot(){return ItemSlot.MAIN_OR_SUB.getSlotIdMask();}
   @Override public ItemQuality getItemQuality(){return ItemQuality.UNIQUE;}
   @Override public int getLevel(){return 43;}
  };
  var item=new Item(812,template,17,false,0);
  item.setEquipmentSlot(ItemSlot.MAIN_HAND.getSlotIdMask());item.setEquipped(true);
  item.setEnchantLevel(9);item.setTempering(3);
  var original=Map.<String,Object>of("id",812,"bankable",false,"mailable",false,"slots",List.of("MAIN_HAND","SUB_HAND"));
  var view=PlayerBotItemView.decorate(original,item);
  check(view.get("id").equals(812),"Exact instance ID retained");
  check(view.get("bankable").equals(false),"Custody eligibility retained");
  check(view.get("mailable").equals(false),"Native mailing eligibility retained");
  check(view.get("slots").equals(List.of("MAIN_HAND","SUB_HAND")),"Eligible slots remain distinct from equipped slot");
  check(view.get("equippedSlots").equals(List.of("MAIN_HAND")),"Actual current hand, not eligible alternate hand");
  check(view.get("tooltip").equals("item=100000096&count=17&enchant_count=9&authorize_count=3"),"Installed native hover contract uses real count/enchant/tempering");
  check(view.get("enchant").equals(9),"Actual enchant selected detail");
  check(view.get("tempering").equals(3),"Actual tempering selected detail");
  check(view.get("quality").equals("UNIQUE"),"Native quality");
  check(view.get("itemLevel").equals(43),"Native item level");
  check(view.get("manastones").equals(List.of()),"Empty native sockets remain empty");
  check(view.get("fusionStones").equals(List.of()),"No invented fusion stones");
  check(!original.containsKey("tooltip"),"Original projection not mutated");
  check(item.getEquipmentSlot()==ItemSlot.MAIN_HAND.getSlotIdMask() && item.getEnchantLevel()==9 && item.getItemCount()==17,"Native instance remains unchanged");
  item.setEquipmentSlot(ItemSlot.MAIN_OR_SUB.getSlotIdMask());
  check(PlayerBotItemView.decorate(original,item).get("equippedSlots").equals(List.of("MAIN_HAND","SUB_HAND")),"Two-hand actual slot mask represented");
  item.setEquipped(false);
  check(PlayerBotItemView.decorate(original,item).get("equippedSlots").equals(List.of()),"Cube location mask cannot masquerade as worn slot");
  var stored=PlayerBotItemView.stored(item);
  check(stored.get("id").equals(812) && stored.get("itemId").equals(100000096),"Storage preserves native object/template identity");
  check(stored.get("name").equals("Fixture sword") && stored.get("count").equals(17L),"Storage retains name/count");
  check(stored.get("tooltip").equals(view.get("tooltip")),"Storage shares exact native tooltip metadata");
  check(stored.get("fusionItemId").equals(0) && stored.get("godstoneId").equals(0) && stored.get("bonusStatsId").equals(0),"No fabricated instance extras");
  var mods=PlayerBotItemView.modifiers(List.of(new StatAddFunction(StatEnum.MAXHP,120,false),new StatSubFunction(){{stat=StatEnum.MAXMP;value=5;}},new StatRateFunction(StatEnum.MAXHP,10,false).withConditions(new Conditions())));
  check(mods.get(0).get("name").equals("MAXHP") && mods.get(0).get("value").equals(120),"Native stat name/value retained");
  check(mods.get(0).get("operation").equals("add") && mods.get(1).get("operation").equals("subtract"),"Native add/subtract retained");
  check(mods.get(2).get("operation").equals("rate") && mods.get(2).get("conditional").equals(true),"Percentage and conditional marker retained, never unconditional stat claim");
  check(PlayerBotItemView.modifiers(null).equals(List.of()),"Missing native bonus data fails gracefully");
  check(stored.get("fusionName").equals("") && stored.get("godstoneName").equals(""),"No invented fusion/godstone names");
  var previous=com.aionemu.gameserver.dataholders.DataManager.ITEM_RANDOM_BONUSES;
  try {
   var xml="<random_bonuses><random_bonus id=\"72\" type=\"INVENTORY\"><modifiers><add name=\"MAXHP\" value=\"321\"/></modifiers><modifiers><add name=\"MAXMP\" value=\"456\"/></modifiers></random_bonus></random_bonuses>";
   var data=(com.aionemu.gameserver.dataholders.ItemRandomBonusData)javax.xml.bind.JAXBContext.newInstance(com.aionemu.gameserver.dataholders.ItemRandomBonusData.class).createUnmarshaller().unmarshal(new java.io.StringReader(xml));
   com.aionemu.gameserver.dataholders.DataManager.ITEM_RANDOM_BONUSES=data;
   var bonusTemplate=new ItemTemplate(){@Override public int getStatBonusSetId(){return 72;} @Override public String getName(){return "Native fused sword";}};
   var bonusItem=new Item(813,bonusTemplate,1,false,0);bonusItem.setBonusStats(2,false);
   var actual=PlayerBotItemView.decorate(Map.of(),bonusItem);
   check(actual.get("bonusModifiers").equals(List.of(Map.of("name","MAXMP","value",456,"conditional",false,"operation","add"))),"Native template set plus saved second roll resolved, never first roll or reroll");
   bonusItem.setFusionedItem(bonusTemplate,1,0);
   actual=PlayerBotItemView.decorate(Map.of(),bonusItem);
   check(actual.get("fusionName").equals("Native fused sword"),"Fusion template name resolved");
   check(actual.get("fusionModifiers").equals(List.of(Map.of("name","MAXHP","value",321,"conditional",false,"operation","add"))),"Fusion uses its own saved roll");
   check(bonusItem.getBonusStatsId()==2 && bonusItem.getFusionedItemBonusStatsId()==1,"Projection cannot reroll stored bonuses");
  } finally {com.aionemu.gameserver.dataholders.DataManager.ITEM_RANDOM_BONUSES=previous;}
  System.out.println("OK: "+checks+" actual native item projection/slot/hover/custody/identity checks; no World/SQL/IDs");
 }
}
