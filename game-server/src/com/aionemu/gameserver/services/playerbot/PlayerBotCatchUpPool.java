package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.ItemType;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;

/** Bounded per-class catalogue for generated catch-up gear. COMMON and RARE stay admitted as ordinary world-drop grades;
 * higher grades need a NORMAL shop, a quest reward or a recipe, which keeps boss-exclusive and balance-test templates out.
 * Only generation is limited: earned, gifted or bought gear is never checked against this pool. */
final class PlayerBotCatchUpPool {
 private record Index(Object recipes,Object shops,Object goods,Object quests,Object items,Set<Integer> sourced,Map<PlayerClass,List<ItemTemplate>> classes) {}
 private static volatile Index INDEX;

 private static Index index() {
  var current=INDEX;if(current!=null && current.recipes()==DataManager.RECIPE_DATA && current.shops()==DataManager.TRADE_LIST_DATA && current.goods()==DataManager.GOODSLIST_DATA && current.quests()==DataManager.QUEST_DATA && current.items()==DataManager.ITEM_DATA)return current;
  synchronized(PlayerBotCatchUpPool.class) {
   current=INDEX;if(current!=null && current.recipes()==DataManager.RECIPE_DATA && current.shops()==DataManager.TRADE_LIST_DATA && current.goods()==DataManager.GOODSLIST_DATA && current.quests()==DataManager.QUEST_DATA && current.items()==DataManager.ITEM_DATA)return current;
   var sourced=new HashSet<Integer>();
   if(DataManager.RECIPE_DATA!=null)for(var recipe:DataManager.RECIPE_DATA.getRecipeTemplates()) {
    sourced.add(recipe.getProductId());
    for(int i=1;i<=recipe.getComboProductSize();i++)sourced.add(recipe.getComboProduct(i));
   }
   if(DataManager.TRADE_LIST_DATA!=null && DataManager.GOODSLIST_DATA!=null)for(var shop:DataManager.TRADE_LIST_DATA.getTradeListTemplate().values())if(shop.getTradeNpcType()==TradeNpcType.NORMAL)
    for(var tab:shop.getTradeTablist()){var goods=DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());if(goods!=null && goods.getLegionLevel()==0 && goods.getItemIdList()!=null)sourced.addAll(goods.getItemIdList());}
   if(DataManager.QUEST_DATA!=null)for(var quest:DataManager.QUEST_DATA.getQuestTemplates()) {
    for(var rewards:quest.getRewards()){add(sourced,rewards.getRewardItem());add(sourced,rewards.getSelectableRewardItem());}
    var extended=quest.getExtendedRewards();if(extended!=null){add(sourced,extended.getRewardItem());add(sourced,extended.getSelectableRewardItem());}
    for(var pc:PlayerClass.values())add(sourced,quest.getSelectableRewardByClass(pc));
   }
   sourced.remove(null);
   return INDEX=new Index(DataManager.RECIPE_DATA,DataManager.TRADE_LIST_DATA,DataManager.GOODSLIST_DATA,DataManager.QUEST_DATA,DataManager.ITEM_DATA,Set.copyOf(sourced),new ConcurrentHashMap<>());
  }
 }
 private static void add(Set<Integer> sourced,List<QuestItems> items){for(var item:items)sourced.add(item.getItemId());}
 private static boolean admitted(Index index,ItemTemplate t){return t.getItemQuality()!=null && t.getItemQuality().getQualityId()<=ItemQuality.RARE.getQualityId() || index.sourced().contains(t.getTemplateId());}
 static boolean admitted(ItemTemplate t){return admitted(index(),t);}
 /** Class-legal gear this pool admits. Level, race and role admission stay with PlayerBotGearPolicy.generatedTemplate. */
 static List<ItemTemplate> forClass(PlayerClass pc) {
  var index=index();
  return index.classes().computeIfAbsent(pc,c->DataManager.ITEM_DATA.getItemTemplates().stream().filter(t->(t.isWeapon() || t.isArmor()) && !t.isTestItem() && t.isClassSpecific(c)
   && t.getItemType()==ItemType.NORMAL && t.getAcquisition()==null && t.getExpireTime()==0 && admitted(index,t)).toList());
 }
 private PlayerBotCatchUpPool() {}
}
