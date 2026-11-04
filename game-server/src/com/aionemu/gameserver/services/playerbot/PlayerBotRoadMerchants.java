package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PrivateStore;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.actions.PlayerMode;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.model.trade.Exchange;
import com.aionemu.gameserver.model.trade.ExchangeItem;
import com.aionemu.gameserver.model.trade.TradePSItem;
import com.aionemu.gameserver.services.LootCodexService;
import com.aionemu.gameserver.services.ItemEligibility;
import com.aionemu.gameserver.services.trade.EconomyPrices;
import com.aionemu.gameserver.services.PrivateStoreService;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.WorldMapType;

/** Finite, provenance-backed regional merchandise, checkpointed with native inventory and road career. */
public final class PlayerBotRoadMerchants {
 public static void refuse(Player seller,Player buyer,Object item,String reason) {
  org.slf4j.LoggerFactory.getLogger(PlayerBotRoadMerchants.class).warn("Merchant sale refused: merchant={} ({}) buyer={} ({}) item={} reason={}",seller.getName(),seller.getObjectId(),buyer.getName(),buyer.getObjectId(),item,reason);
  PacketSendUtility.sendMessage(buyer,reason);
 }
 private record Pool(int map,Race race,int level,int rank) {}
 /** Everything a merchant can stock, and which of it comes from this map's instances. */
 private record Supply(List<LootCodexService.RegionalGoods> goods,Set<Integer> instance) {}
 private static final Map<Pool,Supply> POOLS=new ConcurrentHashMap<>();
 private static int capacity(PlayerBotRoadDuty.Rank rank){return PlayerBotMerchantConfig.STOCK_BASE+rank.ordinal()*PlayerBotMerchantConfig.STOCK_PER_RANK;}
 private static int ceiling(PlayerBotRoadDuty.Rank rank){return switch(rank){case PEDDLER->PlayerBotMerchantConfig.MAX_QUALITY_PEDDLER;case TRADER->PlayerBotMerchantConfig.MAX_QUALITY_TRADER;case SPECIALIST->PlayerBotMerchantConfig.MAX_QUALITY_SPECIALIST;case MASTER->PlayerBotMerchantConfig.MAX_QUALITY_MASTER;case LEGENDARY->PlayerBotMerchantConfig.MAX_QUALITY_LEGENDARY;};}
 private static int rankPercent(PlayerBotRoadDuty.Rank rank){return switch(rank){case PEDDLER->PlayerBotMerchantConfig.PRICE_PEDDLER;case TRADER->PlayerBotMerchantConfig.PRICE_TRADER;case SPECIALIST->PlayerBotMerchantConfig.PRICE_SPECIALIST;case MASTER->PlayerBotMerchantConfig.PRICE_MASTER;case LEGENDARY->PlayerBotMerchantConfig.PRICE_LEGENDARY;};}
 private static int qualityMultiplier(ItemTemplate item){return switch(item.getItemQuality().getQualityId()){case 2->PlayerBotMerchantConfig.QUALITY_GREEN;case 3->PlayerBotMerchantConfig.QUALITY_BLUE;case 4->PlayerBotMerchantConfig.QUALITY_GOLD;case 5->PlayerBotMerchantConfig.QUALITY_ORANGE;case 6->PlayerBotMerchantConfig.QUALITY_PURPLE;default->PlayerBotMerchantConfig.QUALITY_COMMON;};}
 /** Housing zones have no monsters or instances, so their merchants carry house goods instead. */
 private static boolean housing(int map){return map==WorldMapType.ORIEL.getId() || map==WorldMapType.PERNON.getId();}
 private static boolean houseGoods(ItemTemplate item) {
  // Underscored names are unfinished placeholder templates.
  return item.getActions()!=null && (item.getActions().getHouseObjectAction()!=null || item.getActions().getDecorateAction()!=null)
   && item.isTradeable() && item.getName()!=null && !item.getName().contains("_");
 }
 private static boolean eligible(ItemTemplate item,PlayerBotRoadDuty.State s) {
  return ItemEligibility.isPlayerItem(item) && !ItemEligibility.isAppearanceOnly(item) && item.getItemQuality()!=null && !item.isKinah() && item.getItemGroup()!=ItemGroup.QUEST
   && (housing(s.route.map())?houseGoods(item):item.isWeapon() || item.isArmor() || item.isStigma()) && item.getItemQuality().getQualityId()<=ceiling(s.rank)
   && (item.getRace()==Race.PC_ALL || item.getRace()==s.route.race()) && item.getUseLimits().getMinRank()==1
   && item.getUseLimits().getGuildLevelPermitted()==0 && !item.hasAreaRestriction();
 }
 private static Map<Integer,LootCodexService.RegionalGoods> usable(List<LootCodexService.RegionalGoods> goods,PlayerBotRoadDuty.State s) {
  var out=new TreeMap<Integer,LootCodexService.RegionalGoods>();
  for(var g:goods)if(eligible(DataManager.ITEM_DATA.getItemTemplate(g.item()),s))out.putIfAbsent(g.item(),g);
  return out;
 }
 /** Rank sets the quality of this map's instance drops; only a map short of those adds its own monsters' drops. */
 private static Supply pool(PlayerBotRoadDuty.State s) {
  return POOLS.computeIfAbsent(new Pool(s.route.map(),s.route.race(),s.route.level(),s.rank.ordinal()),key->{
   if(housing(key.map)) {
    var goods=new TreeMap<Integer,LootCodexService.RegionalGoods>();
    for(var item:DataManager.ITEM_DATA.getItemTemplates())if(eligible(item,s))goods.put(item.getTemplateId(),new LootCodexService.RegionalGoods(item.getTemplateId(),s.route.npc(),key.map,""));
    return new Supply(List.copyOf(goods.values()),Set.copyOf(goods.keySet()));
   }
   var instance=usable(LootCodexService.instanceGoods(key.map,key.race),s);
   var all=new TreeMap<>(instance);
   if(instance.size()<capacity(s.rank))usable(LootCodexService.regionalGoods(key.map,key.race),s).forEach(all::putIfAbsent);
   return new Supply(List.copyOf(all.values()),Set.copyOf(instance.keySet()));
  });
 }
 static boolean merchandise(Player bot,Item item) {
  var s=PlayerBotRoadDuty.state(bot);
  return PlayerBotRoadDuty.merchant(bot) && item!=null && !item.isEquipped() && item.getItemLocation()==0
   && s.values.containsKey("stock."+item.getObjectId()) && eligible(item.getItemTemplate(),s);
 }
 static long price(Player bot,Item item,long count) {
  var s=PlayerBotRoadDuty.state(bot);if(!merchandise(bot,item) || count!=1)throw new IllegalArgumentException("Choose one available merchant item.");
  // Native per-item value retains slot/crafting differences; rarity is a modest retail premium.
  var template=item.getItemTemplate();
  long quoted=EconomyPrices.cashPrice(template);
  double base=quoted>0?quoted:EconomyPrices.reference(template)*Math.max(1,qualityMultiplier(template)/10.0);
  // House goods are all level 1, so quality and their native price set the value.
  if(houseGoods(item.getItemTemplate()))base=Math.max(item.getItemTemplate().getPrice(),PlayerBotMerchantConfig.PRICE_HOUSE_BASE*qualityMultiplier(item.getItemTemplate()));
  double value=base*rankPercent(s.rank)/100*PlayerBotMerchantConfig.PRICE_SCALE/100;
  return Math.max(EconomyPrices.generatedFloor(template),Math.max(PlayerBotMerchantConfig.PRICE_MINIMUM,Math.min((long)value,PlayerBotMerchantConfig.PRICE_MAXIMUM)));
 }
 static void maintenance(PlayerBotRoadDuty.State s) {
  long now=System.currentTimeMillis();if(now<s.next || !s.session.autonomous())return;
  var bot=s.session.bot();if(bot.hasStore() || bot.isDead() || bot.isTrading() || bot.isCasting() || bot.isLooting() || bot.getController().isInCombat())return;
  long restock=Long.parseLong(s.values.getProperty("restockAt","0"));
  // A raised stock size fills once at the next stop, rather than after up to a full restock wait.
  int capacity=capacity(s.rank);boolean grown=capacity>Integer.parseInt(s.values.getProperty("stockCapacity","0"));
  if(now<restock && !grown)return;
  var supply=pool(s);var choices=supply.goods();if(choices.isEmpty()){s.values.setProperty("restockAt",Long.toString(now+3600000));s.save();return;}
  // An offline/restarted actor replays the same cycle instead of fishing for a different rare roll.
  long cycle=restock==0?0:restock/3600000;
  var random=new Random(((long)bot.getObjectId()<<32)^cycle^0x52b9ad4cL);
  var used=new HashSet<Integer>();int count=0;
  for(String key:List.copyOf(s.values.stringPropertyNames()))if(key.startsWith("stock.")) {
   var item=bot.getInventory().getItemByObjId(Integer.parseInt(key.substring(6)));
   if(item==null)s.values.remove(key);else{used.add(item.getItemId());count++;}
  }
  var remaining=new ArrayList<>(choices);Collections.shuffle(remaining,random);
  // Each rank guarantees a slot from its highest locally obtainable quality when available.
  int highest=remaining.stream().filter(g->supply.instance().contains(g.item())).mapToInt(g->DataManager.ITEM_DATA.getItemTemplate(g.item()).getItemQuality().getQualityId()).max().orElse(0);
  remaining.sort(Comparator.comparingInt(g->!supply.instance().contains(g.item())?2:DataManager.ITEM_DATA.getItemTemplate(g.item()).getItemQuality().getQualityId()==highest?0:1));
  int created=0;
  for(var goods:remaining) {
   if(count>=capacity || bot.getInventory().isFull() || created>=PlayerBotMerchantConfig.RESTOCK_ITEMS)break;
   if(!used.add(goods.item()))continue;
   Item item=ItemFactory.newItem(goods.item(),1);if(item==null)throw new IllegalStateException("Regional merchant item template disappeared");
   bot.getInventory().onLoadHandler(item);
   s.values.setProperty("stock."+item.getObjectId(),goods.sourceNpc()+":"+goods.sourceMap());count++;created++;
  }
  // Bounded work per decision; the restock timer starts only when the cycle is filled.
  if(count>=capacity || used.size()>=choices.size() || bot.getInventory().isFull()) {
   s.values.setProperty("restockAt",Long.toString(now+PlayerBotMerchantConfig.RESTOCK_HOURS*3600000L));
   s.values.setProperty("stockCapacity",Integer.toString(capacity));
  }
  s.save();
 }
 private static List<Item> stock(PlayerBotRoadDuty.State s) {
  var bot=s.session.bot();var goods=new ArrayList<Item>();
  for(var item:bot.getInventory().getItems())if(merchandise(bot,item))goods.add(item);
  return goods;
 }
 static boolean stocked(PlayerBotRoadDuty.State s){return !stock(s).isEmpty();}
 private static String sign(PlayerBotRoadDuty.State s){var name=s.rank.name();return name.charAt(0)+name.substring(1).toLowerCase(Locale.ROOT)+(housing(s.route.map())?" - house goods":" - regional goods");}
 /** Native personal shop while a traveller attends this merchant. The client refuses to show a shop over its
  * native item limit, so each opening lists the next stretch of stock, rarest first. */
 static void open(PlayerBotRoadDuty.State s) {
  var bot=s.session.bot();
  if(bot.hasStore() || bot.getMoveController().isInMove() || bot.isFlying() || bot.isDead() || bot.isTrading() || bot.isCasting()
   || bot.isInPlayerMode(PlayerMode.RIDE) || bot.isInState(CreatureState.CHAIR))return;
  var goods=stock(s);if(goods.isEmpty())return;
  goods.sort(Comparator.comparingLong((Item item)->price(bot,item,1)).reversed());
  var store=new PrivateStore(bot);
  int listed=Math.clamp(PlayerBotMerchantConfig.SHOP_MAX_LISTED,1,goods.size()),start=s.shopOffset%goods.size();
  for(int i=0;i<listed;i++) {
   var item=goods.get((start+i)%goods.size());
   store.addItemToSell(item.getObjectId(),new TradePSItem(item.getObjectId(),item.getItemId(),1,price(bot,item,1)));
  }
  s.shopOffset=start+listed;
  PrivateStoreService.openPlayerBotStore(bot,store,sign(s));
 }
 static void close(Player bot){if(bot.hasStore())PrivateStoreService.closePrivateStore(bot);}
 /** Native shop purchase through the world-trade custody commit; merchandise is never a plain inventory hand-off. */
 public static void sell(Player seller,Player buyer,PrivateStore store,List<TradePSItem> bought,long price) {
  var items=bought.stream().map(TradePSItem::getItemObjId).toList();
  var session=PlayerBotService.getInstance().worldSession(seller);if(session==null){refuse(seller,buyer,items,"This merchant is unavailable. Try again when they are ready.");return;}
  synchronized(session) {
   if(!PlayerBotService.getInstance().current(session) || !session.autonomous() || seller.getStore()!=store){refuse(seller,buyer,items,"This shop has changed or closed. Browse it again.");return;}
   if(buyer.isDead() || seller.isDead()){refuse(seller,buyer,items,"Both of you must be alive to buy.");return;}
   if(buyer.getWorldId()!=seller.getWorldId() || buyer.getInstanceId()!=seller.getInstanceId() || !PositionUtil.isInRange(buyer,seller,PlayerBotMerchantConfig.SHOP_CLOSE_RANGE)) {
    refuse(seller,buyer,items,"Stay beside "+seller.getName()+" to buy from their shop.");return;
   }
   if(buyer.getInventory().getKinah()<price){refuse(seller,buyer,items,"Not enough Kinah for this purchase.");return;}
   var goods=new Exchange(seller,buyer);
   for(var entry:bought) {
    var item=seller.getInventory().getItemByObjId(entry.getItemObjId());
    if(!merchandise(seller,item) || entry.getCount()!=1){refuse(seller,buyer,entry.getItemObjId(),"That merchant item is no longer available. Browse the shop again.");return;}
    goods.addItem(item.getObjectId(),new ExchangeItem(item.getObjectId(),1,item));
   }
   if(!PlayerBotWorldTrade.shopSale(seller,buyer,goods,price))return;
   for(var entry:bought)store.removeItem(entry.getItemObjId());
   if(store.getSoldItems().isEmpty())close(seller);
  }
 }
 private PlayerBotRoadMerchants(){}
}
