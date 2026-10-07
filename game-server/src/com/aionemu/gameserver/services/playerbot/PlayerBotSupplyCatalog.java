package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;

/** Generated stocks come from real normal shops or native crafting products, never test templates. */
public final class PlayerBotSupplyCatalog {
 private static final Set<Integer> ORDINARY=ordinary();
 private static Set<Integer> ordinary() {
  var ids=new HashSet<Integer>();
  for(var shop:DataManager.TRADE_LIST_DATA.getTradeListTemplate().values())if(shop.getTradeNpcType()==TradeNpcType.NORMAL)for(var tab:shop.getTradeTablist()) {
   var goods=DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());if(goods!=null && goods.getItemIdList()!=null && goods.getLegionLevel()==0)ids.addAll(goods.getItemIdList());
  }
  for(var recipe:DataManager.RECIPE_DATA.getRecipeTemplates()) {
   ids.add(recipe.getProductId());for(int combo=1;combo<=recipe.getComboProductSize();combo++){var product=recipe.getComboProduct(combo);if(product!=null)ids.add(product);}
  }
  return Set.copyOf(ids);
 }
 public static boolean ordinary(ItemTemplate t) {
  return ORDINARY.contains(t.getTemplateId()) && !t.getName().startsWith("[") && !t.getName().toLowerCase(Locale.ROOT).contains("test") && !t.getName().contains("FX Retaining");
 }
 static void record(Player bot,Item item) {
  var owner=com.aionemu.gameserver.world.World.getInstance().getPlayer(bot.getPlayerBotOwnerId());if(owner==null)return;
  for(var session:PlayerBotService.getInstance().companions(owner))if(session.bot()==bot && session.generated()) {
   var policy=PlayerBotGearPolicy.state(session);policy.generated.add(item.getObjectId());policy.save();
   bot.getInventory().setPersistentState(com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.UPDATE_REQUIRED);return;
  }
 }
 private PlayerBotSupplyCatalog() {}
}
