/* Item-use ordering and budgeted vendor purchases adapted from mod-playerbots
 * ItemUsageValue.cpp and BuyAction.cpp, revision 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later. Attribution: third-party/playerbots/AUTHORS.md.
 * Aion extraction/enchantment actions and the protective policy are local adapters. */
package com.aionemu.gameserver.services.playerbot;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.enchants.EnchantmentStone;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.actions.*;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;
import com.aionemu.gameserver.model.trade.TradeList;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.TradeService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

final class PlayerBotCare {
 record Purchase(Npc npc,int item,long price) {}
 record Pending(Item target,int level,boolean extraction) {}
 private static final Map<Integer,Pending> PENDING=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 private static final Map<Integer,Map<Integer,Integer>> ATTEMPTS=new ConcurrentHashMap<>();
 static void close(PlayerBotSession session){PENDING.remove(session.bot().getObjectId());NEXT.remove(session.bot().getObjectId());ATTEMPTS.remove(session.bot().getObjectId());}
 static long allowance(long money,long reserve,long budget,long spent) {
  if(money<0 || reserve<0 || budget<0 || spent<0)return 0;
  return Math.max(0,Math.min(money-Math.max(reserve,money/4),Math.max(0,budget-spent)));
 }
 static boolean disposable(Item item,boolean protectedItem,boolean upgrade,boolean questItem,boolean futureItem) {
  ItemTemplate t=item.getItemTemplate();
  return !protectedItem && !upgrade && !questItem && !futureItem && !item.isEquipped() && (t.isArmor() || t.isWeapon())
   && t.getItemQuality()!=null && t.getItemQuality().getQualityId()>=ItemQuality.COMMON.getQualityId()
   && t.getItemQuality().getQualityId()<=ItemQuality.LEGEND.getQualityId() && item.getEnchantLevel()==0 && item.getTempering()==0
   && item.getFusionedItemId()==0 && item.getItemStonesSize()==0 && item.getGodStoneId()==0 && item.getIdianStone()==null
   && !item.isAmplified() && item.isIdentified() && !item.isSkinnedItem();
 }
 static boolean target(Item item) {
  var t=item.getItemTemplate();return item.isEquipped() && t.isArmor() && !item.isAmplified() && t.getEnchantType()==0
   && !t.isNoEnchant() && item.getEnchantLevel()<Math.min(5,item.getMaxEnchantLevel());
 }
 static boolean stone(ItemTemplate template,Item target) {
  int id=template.getTemplateId();if(id<166000191 || id>166000195 || template.getItemGroup()!=ItemGroup.ENCHANTMENT)return false;
  var grade=EnchantmentStone.getByItemId(id);
  return grade.getBaseLevel()>=Math.min(65,target.getItemTemplate().getLevel())
   && grade.getBaseQuality().getQualityId()>=target.getItemTemplate().getItemQuality().getQualityId();
 }
 static AbstractItemAction extract(ItemTemplate template) {
  if(template.getActions()==null)return null;
  return template.getActions().getItemActions().stream().filter(a->a instanceof ExtractAction).findFirst().orElse(null);
 }
 static Trigger trigger(PlayerBotSession session,PlayerBotNavigation navigation) {
  Action action=new Action(){
   public String name(){return "equipment care";}
   public boolean isUseful(){return available(session);}
   public boolean isPossible(){return available(session);}
   public boolean execute(){return perform(session,navigation);}
  };
  return new Trigger(()->available(session),action,()->DEFAULT+2);
 }
 private static boolean available(PlayerBotSession session) {
  var bot=session.bot();var owner=session.owner();var s=PlayerBotQuestSync.state(session);
  return (s.enchant || s.salvage) && !session.closing() && !bot.isDead() && !bot.isFlying() && !owner.isFlying()
   && !bot.isCasting() && !bot.isLooting() && !bot.getController().hasTask(TaskId.ITEM_USE)
   && !bot.getController().isInCombat() && !owner.getController().isInCombat() && !owner.getMoveController().isInMove()
   && !PlayerBotQuestSync.returning(session) && System.currentTimeMillis()>=NEXT.getOrDefault(bot.getObjectId(),0L)
   && PlayerBotEquipment.upgrades(bot,session.combatRole()).isEmpty();
 }
 static void observe(PlayerBotSession session) {
  Player bot=session.bot();var pending=PENDING.get(bot.getObjectId());
  if(pending==null || bot.getController().hasTask(TaskId.ITEM_USE))return;
  PENDING.remove(bot.getObjectId());
  if(pending.extraction()) {
   boolean removed=bot.getInventory().getItemByObjId(pending.target().getObjectId())==null;
   PlayerBotQuestSync.notice(bot,removed ? "I extracted unused "+ChatUtil.item(pending.target().getItemId())+" into enchantment stones." : "Extraction was interrupted; I kept the item.","care-result",0);
  }else PlayerBotQuestSync.notice(bot,"Enchantment finished for "+ChatUtil.item(pending.target().getItemId())+": +"+pending.target().getEnchantLevel()
   +(pending.target().getEnchantLevel()>pending.level() ? "." : " (the native attempt failed or was interrupted)."),"care-result",0);
 }
 private static boolean perform(PlayerBotSession session,PlayerBotNavigation navigation) {
  if(!available(session))return false;
  Player bot=session.bot();var s=PlayerBotQuestSync.state(session);
  String day=LocalDate.now().toString();if(!s.spentDay.equals(day)){s.spentDay=day;s.spent=0;ATTEMPTS.remove(bot.getObjectId());s.save();}
  var attempts=ATTEMPTS.computeIfAbsent(bot.getObjectId(),id->new HashMap<>());
  Item armor=s.enchant ? bot.getEquipment().getEquippedItems().stream().filter(PlayerBotCare::target)
   .filter(i->attempts.getOrDefault(i.getObjectId(),0)<3).min(Comparator.comparingInt(Item::getEnchantLevel)).orElse(null) : null;
  if(armor!=null) {
   Item stone=bot.getInventory().getItems().stream().filter(i->stone(i.getItemTemplate(),armor)).min(Comparator.comparingInt(Item::getItemId)).orElse(null);
   if(stone!=null) {
    var action=stone.getItemTemplate().getActions()==null ? null : stone.getItemTemplate().getActions().getEnchantAction();
    if(action!=null && PlayerRestrictions.canUseItem(bot,stone) && action.canAct(bot,stone,armor)) {
     navigation.stop();bot.getObserveController().notifyItemuseObservers(stone);
     PENDING.put(bot.getObjectId(),new Pending(armor,armor.getEnchantLevel(),false));attempts.merge(armor.getObjectId(),1,Integer::sum);
     NEXT.put(bot.getObjectId(),System.currentTimeMillis()+10000);action.act(bot,stone,armor);
     PlayerBotQuestSync.notice(bot,"I am enchanting "+ChatUtil.item(armor.getItemId())+" with my own stone. Native success/failure rules apply.","care-start",0);return true;
    }
   }
  }
  Item unused=s.salvage ? bot.getInventory().getItems().stream().filter(i->disposable(i,s.protectedItems.contains(i.getObjectId()) || PlayerBotGearPolicy.generated(bot,i),false,
   questItem(bot,i.getItemId()),i.getItemTemplate().getRequiredLevel(bot.getPlayerClass())>bot.getLevel())).findFirst().orElse(null) : null;
  if(unused!=null) {
   Item tool=bot.getInventory().getItems().stream().filter(i->extract(i.getItemTemplate())!=null).findFirst().orElse(null);
   if(tool!=null) {
    var action=extract(tool.getItemTemplate());
    if(PlayerRestrictions.canUseItem(bot,tool) && action.canAct(bot,tool,unused)) {
     navigation.stop();bot.getObserveController().notifyItemuseObservers(tool);PENDING.put(bot.getObjectId(),new Pending(unused,0,true));
     NEXT.put(bot.getObjectId(),System.currentTimeMillis()+10000);action.act(bot,tool,unused);return true;
    }
   }
  }
  Item neededArmor=armor;
  Purchase purchase=findPurchase(session,neededArmor,unused!=null);
  if(purchase!=null) {
   if(!PositionUtil.isInTalkRange(bot,purchase.npc()) || !GeoService.getInstance().canSee(bot,purchase.npc()))return navigation.approach(purchase.npc(),2);
   if(!available(session) || !DialogService.isInteractionAllowed(bot,purchase.npc()))return false;
   long balance=bot.getInventory().getKinah(),allowed=allowance(balance,s.reserve,s.dailyBudget,s.spent);
   if(purchase.price()<=0 || purchase.price()>allowed)return false;
   TradeList list=new TradeList(purchase.npc().getObjectId());list.addItem(purchase.item(),1);
   var template=DataManager.TRADE_LIST_DATA.getTradeListTemplate(purchase.npc().getNpcId());
   if(!list.calculateBuyListPrice(bot,template.getSellPriceRate()) || list.getRequiredKinah()>allowed || list.getRequiredKinah()!=purchase.price())return false;
   s.spent+=purchase.price();s.save(); // book the bounded spend before changing inventory
   boolean success=false;
   try{success=TradeService.performBuyFromShop(purchase.npc(),bot,list);return success;}
   finally {
    s.spent-=purchase.price();s.spent+=Math.max(0,balance-bot.getInventory().getKinah());s.save();NEXT.put(bot.getObjectId(),System.currentTimeMillis()+10000);
    if(success){PlayerBotQuestSync.notice(bot,"I bought "+ChatUtil.item(purchase.item())+" for "+purchase.price()+" of my own Kinah.","care-buy",0);PlayerBotQuestSync.returnToOwner(bot);}
   }
  }
  NEXT.put(bot.getObjectId(),System.currentTimeMillis()+15000);return false;
 }
 private static Purchase findPurchase(PlayerBotSession session,Item armor,boolean needTool) {
  Player owner=session.owner(),bot=session.bot();var s=PlayerBotQuestSync.state(session);List<Purchase> choices=new ArrayList<>();
  long allowed=allowance(bot.getInventory().getKinah(),s.reserve,s.dailyBudget,s.spent);
  owner.getKnownList().forEachNpc(npc->{
   if(npc.isDead() || !npc.isSpawned() || bot.isEnemy(npc) || npc.getWorldId()!=bot.getWorldId() || npc.getInstanceId()!=bot.getInstanceId()
    || !PositionUtil.isInRange(owner,npc,40) || !DialogService.isInteractionAllowed(bot,npc))return;
   var catalog=DataManager.TRADE_LIST_DATA.getTradeListTemplate(npc.getNpcId());if(catalog==null || catalog.getTradeNpcType()!=TradeNpcType.NORMAL)return;
   for(var tab:catalog.getTradeTablist()) {
    var goods=DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());
    if(goods==null || goods.getItemIdList()==null || goods.getLegionLevel()>(bot.getLegion()==null ? 0 : bot.getLegion().getLegionLevel()))continue;
    for(int id:goods.getItemIdList()) {
     var template=DataManager.ITEM_DATA.getItemTemplate(id);
     if(template==null || template.getAcquisition()!=null || !(armor!=null && stone(template,armor) || needTool && extract(template)!=null)
      || bot.getInventory().getItemCountByItemId(id)>0)continue;
     TradeList list=new TradeList(npc.getObjectId());list.addItem(id,1);
     if(list.calculateBuyListPrice(bot,catalog.getSellPriceRate()) && list.getRequiredKinah()>0 && list.getRequiredKinah()<=allowed)choices.add(new Purchase(npc,id,list.getRequiredKinah()));
    }
   }
  });
  return choices.stream().min(Comparator.comparingLong(Purchase::price).thenComparingDouble(p->PositionUtil.getDistance(owner,p.npc()))).orElse(null);
 }
 private static boolean questItem(Player bot,int item) {
  for(var quest:bot.getQuestStateList().getUncompletedQuests()) {
   var template=DataManager.QUEST_DATA.getQuestById(quest.getQuestId());
   if(template!=null && template.getQuestWorkItems()!=null && template.getQuestWorkItems().getQuestWorkItem().stream().anyMatch(i->i.getItemId()==item))return true;
   if(template!=null && template.getCollectItems()!=null && template.getCollectItems().getCollectItem().stream().anyMatch(i->i.getItemId()==item))return true;
   if(template!=null && template.getInventoryItems()!=null && template.getInventoryItems().getInventoryItems().stream().anyMatch(i->i.getItemId()==item))return true;
  }
  return false;
 }
 private PlayerBotCare() {}
}
