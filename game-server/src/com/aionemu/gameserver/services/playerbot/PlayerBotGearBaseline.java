package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Expected equipment score by class and level, built once from native item templates: the median ordinary item of each slot, per level. */
final class PlayerBotGearBaseline {
 private static final int MAX_LEVEL=65,WINDOW=3;
 private static final Map<PlayerClass,double[]> TABLE=new ConcurrentHashMap<>();

 static double expected(PlayerClass playerClass,int level) {
  return TABLE.computeIfAbsent(playerClass,PlayerBotGearBaseline::build)[Math.max(1,Math.min(MAX_LEVEL,level))];
 }
 static double actual(Player player) {
  var role=PlayerBotRules.roleFor(player.getPlayerClass());double total=0;
  for(var item:player.getEquipment().getEquippedItemsWithoutStigma())total+=PlayerBotEquipment.score(item,role,player.getPlayerClass());
  return total;
 }
 private static double[] build(PlayerClass playerClass) {
  var role=PlayerBotRules.roleFor(playerClass);var slots=new HashMap<Long,List<List<Double>>>();var paired=new HashSet<Long>();
  for(var t:DataManager.ITEM_DATA.getItemTemplates()) {
   if(!PlayerBotCombatGear.usable(t) || com.aionemu.gameserver.services.ItemEligibility.isAppearanceOnly(t) || !t.isWeapon() && !t.isArmor() || t.getItemSlot()==0 || !t.isClassSpecific(playerClass) || t.getLevel()<1 || t.getLevel()>MAX_LEVEL)continue;
   if(!Set.of("COMMON","RARE").contains(t.getItemQuality().name()))continue;
   var levels=slots.computeIfAbsent(t.getItemSlot(),s->{var list=new ArrayList<List<Double>>();for(int i=0;i<=MAX_LEVEL;i++)list.add(new ArrayList<>());return list;});
   levels.get(t.getLevel()).add(PlayerBotEquipment.score(t,role,playerClass));
   if(!t.isWeapon() && Long.bitCount(t.getItemSlot())>1)paired.add(t.getItemSlot());
  }
  var table=new double[MAX_LEVEL+1];
  for(var slot:slots.entrySet()) {
   double carried=0;int count=paired.contains(slot.getKey())?2:1;
   for(int level=1;level<=MAX_LEVEL;level++) {
    var near=new ArrayList<Double>();
    for(int from=Math.max(1,level-WINDOW);from<=level;from++)near.addAll(slot.getValue().get(from));
    if(!near.isEmpty()) {Collections.sort(near);carried=near.get(near.size()/2);}
    table[level]+=carried*count;
   }
  }
  return table;
 }
 private PlayerBotGearBaseline() {}
}
