/* Item-use ordering and budgeted vendor purchases adapted from mod-playerbots
 * ItemUsageValue.cpp and BuyAction.cpp, revision 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later. Attribution: third-party/playerbots/AUTHORS.md.
 * Aion extraction/enchantment actions and the protective policy are local adapters. */
package com.aionemu.gameserver.services.playerbot;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.configs.main.RatesConfig;
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
import com.aionemu.gameserver.services.EnchantService;
import com.aionemu.gameserver.services.trade.EconomyPrices;
import com.aionemu.gameserver.model.gameobjects.player.Rates;
import com.aionemu.gameserver.services.TradeService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

final class PlayerBotCare {
 record Purchase(Npc npc,int item,long price) {}
 record Pending(Item target,int level,boolean extraction,Item stone,long count) {}
 private static final Map<Integer,Pending> PENDING=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 private static final Set<Integer> BACKGROUND_ADMITTED=ConcurrentHashMap.newKeySet();
 private static final java.util.concurrent.atomic.AtomicLong ENHANCE_ATTEMPTS=new java.util.concurrent.atomic.AtomicLong(),SUCCESSES=new java.util.concurrent.atomic.AtomicLong(),BOUGHT=new java.util.concurrent.atomic.AtomicLong();
 private static long backgroundSecond;private static int backgroundTurns;
 static String stats(){return "enhance attempts="+ENHANCE_ATTEMPTS.get()+", successes="+SUCCESSES.get()+", stones bought="+BOUGHT.get();}
 static void bought(long count){BOUGHT.addAndGet(count);}
 static boolean enabled(PlayerBotSession session){return PlayerBotEconomyConfig.ENHANCE_ENABLED && (session.autonomous()?PlayerBotEconomyConfig.ECONOMY_ENABLED && PlayerBotEconomyConfig.ENHANCE_WORLD:PlayerBotQuestSync.state(session).enchant);}
 static long stoneAllowance(PlayerBotSession session) {
  var s=PlayerBotQuestSync.state(session);String day=LocalDate.now().toString();
  if(!s.enhanceDay.equals(day)) {s.enhanceDay=day;s.enhanceSpent=0;s.enhanceBudget=PlayerBotMarket.budget(session.bot().getInventory().getKinah(),Math.max(s.reserve,PlayerBotMarket.reserve(session.bot().getLevel(),PlayerBotEconomyConfig.MARKET_RESERVE_KINAH)),PlayerBotEconomyConfig.ENHANCE_BUDGET_PERCENT,Long.MAX_VALUE);s.save();}
  return Math.max(0,s.enhanceBudget-s.enhanceSpent);
 }
 static void stoneSpent(PlayerBotSession session,long cost){var s=PlayerBotQuestSync.state(session);s.enhanceSpent=Math.max(0,s.enhanceSpent+cost);s.save();}
 static boolean dormantDue(PlayerBotSession session,long now){
  if(!session.autonomous() || !PlayerBotEconomyConfig.ECONOMY_ENABLED || !PlayerBotEconomyConfig.ENHANCE_ENABLED || !PlayerBotEconomyConfig.ENHANCE_WORLD)return false;
  int id=session.bot().getObjectId();if(BACKGROUND_ADMITTED.contains(id))return true;
  Long next=NEXT.get(id);if(next==null){NEXT.put(id,now+Math.max(60,PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_INTERVAL_SECONDS)*1000L+Math.floorMod(id,60000));return false;} // first check waits one spread interval
  if(now<next || !backgroundSlot(now))return false;BACKGROUND_ADMITTED.add(id);return true;
 }
 static synchronized boolean backgroundSlot(long now){if(backgroundSecond!=now/1000){backgroundSecond=now/1000;backgroundTurns=0;}return backgroundTurns++<Math.max(1,PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_TURNS_PER_SECOND);}
 static boolean background(PlayerBotSession session,long now) {
  if(!dormantDue(session,now) || !BACKGROUND_ADMITTED.remove(session.bot().getObjectId()))return false;
  NEXT.put(session.bot().getObjectId(),now+Math.max(60,PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_INTERVAL_SECONDS)*1000L);
  var bot=session.bot();if(!enabled(session) || session.closing() || bot.isDead() || bot.isCasting() || bot.isLooting() || bot.getController().isInCombat() || bot.getController().hasScheduledTask(TaskId.ITEM_USE))return false;
  Item target=enhancementTarget(session);if(target==null)return false;
  Item held=bestStone(bot,target);if(held!=null)return backgroundAttempt(bot,held,target);
  // Use the same bounded native extraction path offscreen, without scheduling an animation.
  Item unused=unused(session);
  if(unused!=null)for(var tool:bot.getInventory().getItems()) {var action=extract(tool.getItemTemplate());if(action!=null && PlayerRestrictions.canUseItem(bot,tool) && action.canAct(bot,tool,unused))return EnchantService.breakItem(bot,unused,tool);}
  return PlayerBotMarket.idle(session,true);
 }
 /** Native final gate, roll, consumption and outcome; only the offscreen animation is omitted. */
 static boolean backgroundAttempt(Player bot,Item stone,Item target) {
  var action=stone.getItemTemplate().getActions()==null?null:stone.getItemTemplate().getActions().getEnchantAction();
  if(!stone(stone.getItemTemplate(),target) || action==null || !PlayerRestrictions.canUseItem(bot,stone) || !action.canAct(bot,stone,target)
   || bot.getInventory().getItemByObjId(stone.getObjectId())!=stone || !target.isEquipped() && bot.getInventory().getItemByObjId(target.getObjectId())!=target)return false;
  int level=target.getEnchantLevel();long count=stone.getItemCount();boolean success=EnchantService.enchantItem(bot,stone,target,null);
  bot.startCooldown(stone);EnchantService.enchantItemAct(bot,stone,target,null,level,success);
  if(stone.getItemCount()<count || bot.getInventory().getItemByObjId(stone.getObjectId())==null){ENHANCE_ATTEMPTS.incrementAndGet();if(success)SUCCESSES.incrementAndGet();return true;}return false;
 }
 static void close(PlayerBotSession session){PENDING.remove(session.bot().getObjectId());NEXT.remove(session.bot().getObjectId());BACKGROUND_ADMITTED.remove(session.bot().getObjectId());}
 static long allowance(long money,long reserve,long budget,long spent) {
  if(money<0 || reserve<0 || budget<0 || spent<0)return 0;
  return Math.max(0,Math.min(money-Math.max(reserve,money/4),Math.max(0,budget-spent)));
 }
 static boolean disposable(Item item,boolean protectedItem,boolean upgrade,boolean questItem,boolean futureItem) {
  ItemTemplate t=item.getItemTemplate();
  return !protectedItem && !upgrade && !questItem && !futureItem && !item.isEquipped() && (t.isArmor() || t.isWeapon())
   && t.getItemQuality()!=null && t.getItemQuality().getQualityId()>=ItemQuality.COMMON.getQualityId()
   && t.getItemQuality().getQualityId()<=ItemQuality.LEGEND.getQualityId()
   && item.getFusionedItemId()==0 && item.getItemStonesSize()==0 && item.getGodStoneId()==0 && item.getIdianStone()==null
   && !item.isAmplified() && item.isIdentified() && !item.isSkinnedItem();
 }
 static boolean target(Item item) {
  var t=item.getItemTemplate();return item.isEquipped() && !t.isNoEnchant() && t.getTemplateId()/1000000<120
   && item.getEnchantLevel()<Math.min(Math.clamp(PlayerBotEconomyConfig.ENHANCE_TARGET_LEVEL,0,255),item.isAmplified()?255:item.getMaxEnchantLevel())
   && (t.getMaxEnchantLevel()>0 || t.canExceedEnchant());
 }
 static boolean stone(ItemTemplate template,Item target) {
  if(template==null || template.getItemGroup()!=ItemGroup.ENCHANTMENT)return false;
  try{var grade=EnchantmentStone.getByItemId(template.getTemplateId());return !target.isAmplified() || grade==EnchantmentStone.OMEGA;}
  catch(IllegalArgumentException unknown){return false;}
 }
 static double stoneEfficiency(Player bot,ItemTemplate stone,Item target) {
  return Math.max(0,EnchantService.enchantChance(target.getItemTemplate(),target.getEnchantLevel(),target.isAmplified(),EnchantmentStone.getByItemId(stone.getTemplateId()),Rates.get(bot,RatesConfig.ENCHANTMENT_STONE_BASE_CHANCES),Rates.get(bot,RatesConfig.ENCHANTMENT_STONE_AMPLIFIED_CHANCES)))/Math.max(1,EconomyPrices.anchor(stone));
 }
 static Item bestStone(Player bot,Item target) {
  var held=bot.getInventory().getItems().stream().filter(i->stone(i.getItemTemplate(),target) && i.getItemTemplate().getActions()!=null && i.getItemTemplate().getActions().getEnchantAction()!=null)
   .filter(i->PlayerRestrictions.canUseItem(bot,i) && i.getItemTemplate().getActions().getEnchantAction().canAct(bot,i,target)).toList();
  boolean normal=held.stream().anyMatch(i->EnchantmentStone.getByItemId(i.getItemId())!=EnchantmentStone.OMEGA);
  return held.stream().filter(i->!normal || EnchantmentStone.getByItemId(i.getItemId())!=EnchantmentStone.OMEGA)
   .max(Comparator.<Item>comparingDouble(i->stoneEfficiency(bot,i.getItemTemplate(),target)).thenComparingInt(i->-i.getItemId())).orElse(null);
 }
 private static volatile List<ItemTemplate> MARKET_STONES;
 static ItemTemplate marketStone(Player bot,Item target) {
  var all=MARKET_STONES;
  if(all==null){all=DataManager.ITEM_DATA.getItemTemplates().stream().filter(t->t.getItemGroup()==ItemGroup.ENCHANTMENT && com.aionemu.gameserver.services.CentralMarketService.eligible(t) && t.getActions()!=null && t.getActions().getEnchantAction()!=null).toList();MARKET_STONES=all;}
  var choices=all.stream().filter(t->stone(t,target)).toList();boolean normal=choices.stream().anyMatch(t->EnchantmentStone.getByItemId(t.getTemplateId())!=EnchantmentStone.OMEGA);
  return choices.stream().filter(t->!normal || EnchantmentStone.getByItemId(t.getTemplateId())!=EnchantmentStone.OMEGA)
   .max(Comparator.<ItemTemplate>comparingDouble(t->stoneEfficiency(bot,t,target)).thenComparingInt(t->-t.getTemplateId())).orElse(null);
 }
 static Item enhancementTarget(PlayerBotSession session){return enabled(session)?session.bot().getEquipment().getEquippedItems().stream().filter(PlayerBotCare::target).min(Comparator.comparingInt(Item::getEnchantLevel).thenComparingInt(Item::getObjectId)).orElse(null):null;}
 private static Item unused(PlayerBotSession session) {var bot=session.bot();var s=PlayerBotQuestSync.state(session);
  Set<Integer> upgrades=new HashSet<>();for(var upgrade:PlayerBotEquipment.upgrades(bot,session.combatRole()))upgrades.add(upgrade.item().getObjectId());
  return bot.getInventory().getItems().stream().filter(i->i.getEnchantLevel()==0 && i.getTempering()==0 && !PlayerBotGearPolicy.goal(bot,i.getItemTemplate()) && !PlayerBotQuestMaterials.protectedItem(session,i.getItemId())
   && disposable(i,s.protectedItems.contains(i.getObjectId()) || PlayerBotGearPolicy.generated(bot,i) || PlayerBotGearPolicy.referenced(bot,i) || PlayerBotRoadMerchants.merchandise(bot,i),upgrades.contains(i.getObjectId()),questItem(bot,i.getItemId()),i.getItemTemplate().getRequiredLevel(bot.getPlayerClass())>bot.getLevel())).findFirst().orElse(null);
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
  return (enabled(session) || s.salvage) && !session.closing() && !bot.isDead() && !bot.isFlying() && !owner.isFlying()
   && !bot.isCasting() && !bot.isLooting() && !bot.getController().hasScheduledTask(TaskId.ITEM_USE)
   && !bot.getController().isInCombat() && !owner.getController().isInCombat() && !owner.getMoveController().isInMove()
   && !PlayerBotQuestSync.returning(session) && System.currentTimeMillis()>=NEXT.getOrDefault(bot.getObjectId(),0L)
   && PlayerBotEquipment.upgrades(bot,session.combatRole()).isEmpty();
 }
 static void observe(PlayerBotSession session) {
  Player bot=session.bot();var pending=PENDING.get(bot.getObjectId());
  if(pending==null || bot.getController().hasScheduledTask(TaskId.ITEM_USE))return;
  PENDING.remove(bot.getObjectId());
  if(!pending.extraction() && session.autonomous() && (pending.stone().getItemCount()<pending.count() || bot.getInventory().getItemByObjId(pending.stone().getObjectId())==null)){ENHANCE_ATTEMPTS.incrementAndGet();if(pending.target().getEnchantLevel()>pending.level())SUCCESSES.incrementAndGet();}
  if(pending.extraction()) {
   boolean removed=bot.getInventory().getItemByObjId(pending.target().getObjectId())==null;
   PlayerBotQuestSync.notice(bot,removed ? "I extracted unused "+ChatUtil.item(pending.target().getItemId())+" into enchantment stones." : "Extraction was interrupted; I kept the item.","care-result",0);
  }else PlayerBotQuestSync.notice(bot,"Enchantment finished for "+ChatUtil.item(pending.target().getItemId())+": +"+pending.target().getEnchantLevel()
   +(pending.target().getEnchantLevel()>pending.level() ? "." : " (the native attempt failed or was interrupted)."),"care-result",0);
 }
 private static boolean perform(PlayerBotSession session,PlayerBotNavigation navigation) {
  if(!available(session))return false;
  Player bot=session.bot();var s=PlayerBotQuestSync.state(session);
  String day=LocalDate.now().toString();if(!s.spentDay.equals(day)){s.spentDay=day;s.spent=0;s.save();}
  Item armor=enhancementTarget(session);
  if(armor!=null) {
   Item stone=bestStone(bot,armor);
   if(stone!=null) {
    var action=stone.getItemTemplate().getActions()==null ? null : stone.getItemTemplate().getActions().getEnchantAction();
    if(action!=null && PlayerRestrictions.canUseItem(bot,stone) && action.canAct(bot,stone,armor)) {
     navigation.stop();bot.getObserveController().notifyItemuseObservers(stone);
     PENDING.put(bot.getObjectId(),new Pending(armor,armor.getEnchantLevel(),false,stone,stone.getItemCount()));
     NEXT.put(bot.getObjectId(),System.currentTimeMillis()+10000);action.act(bot,stone,armor);
     PlayerBotQuestSync.notice(bot,"I am enchanting "+ChatUtil.item(armor.getItemId())+" with my own stone. Native success/failure rules apply.","care-start",0);return true;
    }
   }
  }
  Item unused=s.salvage || session.autonomous() && armor!=null ? unused(session) : null;
  if(unused!=null) {
   Item tool=bot.getInventory().getItems().stream().filter(i->extract(i.getItemTemplate())!=null).findFirst().orElse(null);
   if(tool!=null) {
    var action=extract(tool.getItemTemplate());
    if(PlayerRestrictions.canUseItem(bot,tool) && action.canAct(bot,tool,unused)) {
     navigation.stop();bot.getObserveController().notifyItemuseObservers(tool);PENDING.put(bot.getObjectId(),new Pending(unused,0,true,null,0));
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
   boolean buyingStone=armor!=null && stone(DataManager.ITEM_DATA.getItemTemplate(purchase.item()),armor);
   if(buyingStone)allowed=Math.min(allowed,stoneAllowance(session));
   if(purchase.price()<=0 || purchase.price()>allowed)return false;
   TradeList list=new TradeList(purchase.npc().getObjectId());list.addItem(purchase.item(),1);
   var template=DataManager.TRADE_LIST_DATA.getTradeListTemplate(purchase.npc().getNpcId());
   if(!list.calculateBuyListPrice(bot,template.getSellPriceRate()) || list.getRequiredKinah()>allowed || list.getRequiredKinah()!=purchase.price())return false;
   if(buyingStone)stoneSpent(session,purchase.price());
   s.spent+=purchase.price();s.save(); // book the bounded spend before changing inventory
   boolean success=false;
   try{success=TradeService.performBuyFromShop(purchase.npc(),bot,list);return success;}
   finally {
    s.spent-=purchase.price();s.spent+=Math.max(0,balance-bot.getInventory().getKinah());s.save();NEXT.put(bot.getObjectId(),System.currentTimeMillis()+10000);
    if(buyingStone){if(success)bought(1);else stoneSpent(session,-purchase.price());}
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
     if(list.calculateBuyListPrice(bot,catalog.getSellPriceRate()) && list.getRequiredKinah()>0 && list.getRequiredKinah()<=allowed && (armor==null || !stone(template,armor) || list.getRequiredKinah()<=stoneAllowance(session)))choices.add(new Purchase(npc,id,list.getRequiredKinah()));
    }
   }
  });
  boolean normal=armor!=null && choices.stream().anyMatch(p->stone(DataManager.ITEM_DATA.getItemTemplate(p.item()),armor) && EnchantmentStone.getByItemId(p.item())!=EnchantmentStone.OMEGA);
  return choices.stream().filter(p->!normal || !stone(DataManager.ITEM_DATA.getItemTemplate(p.item()),armor) || EnchantmentStone.getByItemId(p.item())!=EnchantmentStone.OMEGA)
   .min(Comparator.<Purchase>comparingDouble(p->armor!=null && stone(DataManager.ITEM_DATA.getItemTemplate(p.item()),armor)?-stoneEfficiency(bot,DataManager.ITEM_DATA.getItemTemplate(p.item()),armor):0).thenComparingLong(Purchase::price).thenComparingDouble(p->PositionUtil.getDistance(owner,p.npc()))).orElse(null);
 }
 static boolean worldQuestItem(Player bot,int item){return questItem(bot,item);}
 private static boolean questItem(Player bot,int item) {
  var active=PlayerBotService.getInstance().currentActor(bot);
  if(active!=null && PlayerBotQuestMaterials.protectedItem(active,item))return true;
  for(var quest:bot.getQuestStateList().getUncompletedQuests()) {
   if(PlayerBotQuestActions.custody(bot,quest.getQuestId()).stream().anyMatch(a->a.requiredItems().containsKey(item) || a.kind()==com.aionemu.gameserver.questEngine.model.QuestBotAction.Kind.ITEM && a.targetId()==item))return true;
   var template=DataManager.QUEST_DATA.getQuestById(quest.getQuestId());
   if(template!=null && template.getQuestWorkItems()!=null && template.getQuestWorkItems().getQuestWorkItem().stream().anyMatch(i->i.getItemId()==item))return true;
   if(template!=null && template.getCollectItems()!=null && template.getCollectItems().getCollectItem().stream().anyMatch(i->i.getItemId()==item))return true;
   if(template!=null && template.getInventoryItems()!=null && template.getInventoryItems().getInventoryItems().stream().anyMatch(i->i.getItemId()==item))return true;
  }
  return false;
 }
 private PlayerBotCare() {}
}
