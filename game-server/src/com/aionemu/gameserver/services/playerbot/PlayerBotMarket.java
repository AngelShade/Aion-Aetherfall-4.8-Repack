package com.aionemu.gameserver.services.playerbot;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.CentralMarketRules;
import com.aionemu.gameserver.services.CentralMarketService;
import com.aionemu.gameserver.services.CentralMarketService.CompanionLedger;
import com.aionemu.gameserver.services.CentralMarketService.CompanionOrder;
import com.aionemu.gameserver.services.CentralMarketService.CompanionQuote;
import com.aionemu.gameserver.services.CentralMarketService.CompanionPrice;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.trade.EconomyPrices;
import com.aionemu.gameserver.services.trade.PricesService;

/** Bots use the real market with their own goods and Kinah. Each visit settles first, then makes one purchase or sale. */
final class PlayerBotMarket {
 private record Need(int item,long quantity,boolean equipment) {}
 private record View(long at,Map<String,Object> value) {}
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 private static final Map<Integer,Boolean> WINDING=new ConcurrentHashMap<>();
 private static final Map<Integer,View> VIEWS=new ConcurrentHashMap<>();
 private static final AtomicLong WINDOW=new AtomicLong();
 private static final AtomicInteger USED=new AtomicInteger();
 private static final AtomicLong VISITS=new AtomicLong(),BOUGHT=new AtomicLong(),LISTED=new AtomicLong();
 private static final long VIEW_MILLIS=30000;
 private static final int RETURN_BASIS_POINTS=6500;
 private PlayerBotMarket() {}

 private static boolean eligible(PlayerBotSession session) {
  // Winding down still runs with the market switched off, so nothing stays stranded inside it.
  return (PlayerBotEconomyConfig.ECONOMY_ENABLED && PlayerBotEconomyConfig.MARKET_ENABLED || WINDING.containsKey(session.bot().getObjectId())) && (session.generated() || session.autonomous()) && !session.borrowed() && !session.closing();
 }
 /** Every market transaction takes the shared market lock, so all bots together share one budget. */
 private static synchronized boolean admit() {
  long minute=System.currentTimeMillis()/60000;
  if(WINDOW.getAndSet(minute)!=minute)USED.set(0);
  return USED.incrementAndGet()<=Math.max(1,PlayerBotEconomyConfig.MARKET_OPERATIONS_PER_MINUTE);
 }
 private static String perform(Player bot,Map<String,String> args)throws Exception {
  if(!admit())throw new IllegalArgumentException("Companion market work is busy; the goods remain safe for the next visit.");
  var result=CentralMarketService.companionAction(bot,args,UUID.randomUUID().toString());
  VIEWS.remove(bot.getObjectId());return result;
 }
 /** Runs under the actor decision lease and session lock, like the other idle reward work. */
 static boolean idle(PlayerBotSession session) {
  Player bot=session.bot();long now=System.currentTimeMillis();
  if(!eligible(session) || now<NEXT.getOrDefault(bot.getObjectId(),0L) || !PlayerBotPass.rewardIdle(session) || !PlayerBotWarehouse.idle(bot))return false;
  NEXT.put(bot.getObjectId(),now+Math.max(60,PlayerBotEconomyConfig.MARKET_INTERVAL_SECONDS)*1000L+bot.getObjectId()%60000);
  try {
   PlayerBotGearPolicy.state(session);
   PlayerBotService.getInstance().checkpointForOwnerAction(session);
   var ledger=CentralMarketService.companionLedger(bot);
   boolean settling=settle(session,ledger,now);
   if(settling){NEXT.put(bot.getObjectId(),now+5000);return true;}
   if(WINDING.remove(bot.getObjectId())!=null)return false;
   if(ledger.orders().stream().filter(CompanionOrder::live).count()>=Math.max(0,PlayerBotEconomyConfig.MARKET_OPEN_ORDERS))return false;
   VISITS.incrementAndGet();
   if(buy(session,ledger)){BOUGHT.incrementAndGet();return true;}
   if(sell(session)){LISTED.incrementAndGet();return true;}
   return false;
  }catch(CentralMarketService.CompanionHeld error) {
   InventoryDAO.quarantineMarketInventory(bot.getObjectId());session.markClosing();
   LoggerFactory.getLogger(PlayerBotMarket.class).error("Market custody held for bot {}",bot.getObjectId(),error);
  }catch(CentralMarketService.CompanionBusy busy) {
   NEXT.put(bot.getObjectId(),System.currentTimeMillis()+5000);
  }catch(IllegalArgumentException unavailable) {
   // Capacity, prices and trade rules are rechecked on the next visit.
  }catch(Exception error) {
   LoggerFactory.getLogger(PlayerBotMarket.class).warn("Market work deferred for bot {}",bot.getObjectId(),error);
  }
  return false;
 }
 /** One step per call: collect, cancel what expired, bring goods home, then withdraw Kinah. */
 private static boolean settle(PlayerBotSession session,CompanionLedger ledger,long now)throws Exception {
  Player bot=session.bot();
  for(var order:ledger.orders())if(order.uncollected()>0 || order.gross()>0){perform(bot,Map.of("action","collect","order",Long.toString(order.id())));return true;}
  long expiry=WINDING.containsKey(bot.getObjectId())?0:Math.max(1,PlayerBotEconomyConfig.MARKET_ORDER_HOURS)*3600000L;
  for(var order:ledger.orders())if(order.live() && now-order.created()>=expiry){perform(bot,Map.of("action","cancel","order",Long.toString(order.id())));return true;}
  for(var stock:ledger.stock()) {
   perform(bot,Map.of("action","transfer","source",Integer.toString(com.aionemu.gameserver.model.items.storage.StorageType.MARKET_WAREHOUSE.getId()),"target","0","object",Integer.toString(stock.object()),"quantity",Long.toString(stock.count())));return true;
  }
  if(ledger.kinah()>0){perform(bot,Map.of("action","kinah","source","0","direction","withdraw","quantity",Long.toString(ledger.kinah())));return true;}
  return false;
 }
 /** Saved equipment goals first, then what the selected craft is short of. Held goods and live orders are not bought twice. */
 private static List<Need> needs(PlayerBotSession session,CompanionLedger ledger) {
  Player bot=session.bot();var needs=new ArrayList<Need>();var ordered=new HashSet<Integer>();
  for(var order:ledger.orders())if(order.side().equals("B") && (order.live() || order.uncollected()>0))ordered.add(order.itemId());
  for(var goal:new TreeMap<>(PlayerBotGearPolicy.state(session).goals).values()) {
   var parts=goal.split(":");if(parts.length!=3)continue;int item=Integer.parseInt(parts[0]);
   if(!ordered.contains(item) && bot.getInventory().getItemCountByItemId(item)==0 && bot.getEquipment().getEquippedItemsByItemId(item).isEmpty())needs.add(new Need(item,1,true));
   if(needs.size()>=PlayerBotEconomyConfig.MARKET_NEEDS_PER_VISIT)return needs;
  }
  var craft=PlayerBotFocus.craftingPlan(session);
  if(craft!=null)for(var entry:new TreeMap<>(craft.missing()).entrySet()) {
   if(!ordered.contains(entry.getKey()) && entry.getValue()>0)needs.add(new Need(entry.getKey(),entry.getValue(),false));
   if(needs.size()>=PlayerBotEconomyConfig.MARKET_NEEDS_PER_VISIT)break;
  }
  return needs;
 }
 /** Configured reserve is the level-65 reference; native progression needs smaller working balances early on. */
 /** Bot market work since start, for worldstats. */
 static String stats(){return "market visits="+VISITS.get()+", bought="+BOUGHT.get()+", listed="+LISTED.get();}
 static long reserve(int level,long configured) {
  long l=Math.max(1,Math.min(65,level)),value=Math.max(0,Math.min(CentralMarketRules.MAX_KINAH,configured));
  return Math.max(PlayerBotEconomyConfig.MARKET_RESERVE_MINIMUM,value/4225*l*l+value%4225*l*l/4225);
 }
 /** The owner sets a companion's budget. A world bot has no owner, so its budget grows with level to match what gear costs. */
 static long dailyAllowance(boolean world,int level,long careBudget,long spent){return Math.max(0,(world?reserve(level,PlayerBotEconomyConfig.MARKET_WORLD_DAILY_KINAH):careBudget)-spent);}
 static long budget(long wealth,long reserve,int percent,long allowance) {
  long spare=Math.max(0,wealth-Math.max(0,reserve));int p=Math.max(0,Math.min(100,percent));
  return Math.min(Math.max(0,allowance),spare/100*p+spare%100*p/100);
 }
 /** One account cannot increase its influence by splitting a stack or posting many orders. */
 static long median(List<CompanionPrice> prices) {
  var parties=new LinkedHashMap<Integer,Long>();
  for(var price:prices)if(!price.simulated() && price.price()>0)parties.putIfAbsent(price.owner(),price.price());
  if(parties.size()<Math.max(1,PlayerBotEconomyConfig.MARKET_EVIDENCE_OWNERS))return 0;
  var sorted=parties.values().stream().sorted().toList();return sorted.get((sorted.size()-1)/2);
 }
 static long fair(CompanionQuote quote,long anchor) {
  if(anchor<=0 || quote.base()<=0)return 0;
  long trade=median(quote.trades()),ask=median(quote.asks()),bid=median(quote.bids());
  // Sparse books use native replacement, never one manipulator's ask. All evidence is bounded by alternatives.
  long value=trade>0?trade:anchor;
  if(ask>0 && bid>0 && bid<=ask)value=(value*2+ask+bid)/4;
  return Math.clamp(value,Math.max(1,anchor*PlayerBotEconomyConfig.MARKET_FAIR_MINIMUM_PERCENT/100),Math.min(CentralMarketRules.MAX_KINAH,anchor*PlayerBotEconomyConfig.MARKET_FAIR_MAXIMUM_PERCENT/100));
 }
 static long purchasePrice(CompanionQuote quote,long limit) {
  long ask=quote.asks().stream().filter(p->p.quantity()>0).mapToLong(CompanionPrice::price).min().orElse(0);
  if(ask<=0)return 0;
  return CentralMarketRules.ladder(quote.base(),quote.floor(),quote.ceiling()).stream().filter(p->p>=ask && p<=limit).findFirst().orElse(0L);
 }
 private static double improvement(PlayerBotSession session,ItemTemplate template) {
  Player bot=session.bot();if(!PlayerBotGearPolicy.purchasable(bot,template))return 0;
  var role=PlayerBotGearPolicy.role(bot,session.combatRole());double score=PlayerBotGearPolicy.score(bot,template,role);
  double gain=PlayerBotGearPolicy.slots(bot,template).stream().mapToDouble(slot->PlayerBotGearPolicy.gain(bot,template,score,slot,role)).max().orElse(0);
  return gain>1?Math.min(1,gain/Math.max(1,score)):0;
 }
 private static boolean buy(PlayerBotSession session,CompanionLedger ledger)throws Exception {
  Player bot=session.bot();var care=PlayerBotQuestSync.state(session);String day=java.time.LocalDate.now().toString();
  if(!care.spentDay.equals(day)){care.spentDay=day;care.spent=0;care.save();}
  long reserved=Math.max(care.reserve,reserve(bot.getLevel(),PlayerBotEconomyConfig.MARKET_RESERVE_KINAH));
  long budget=budget(bot.getInventory().getKinah(),reserved,PlayerBotEconomyConfig.MARKET_BUDGET_PERCENT,dailyAllowance(session.autonomous(),bot.getLevel(),care.dailyBudget,care.spent));if(budget<=0)return false;
  for(var need:needs(session,ledger)) {
   var template=DataManager.ITEM_DATA.getItemTemplate(need.item());
   if(template==null || !CentralMarketService.eligible(template)
    || !com.aionemu.gameserver.services.ItemEligibility.isPlayerItem(template) || com.aionemu.gameserver.services.ItemEligibility.isAppearanceOnly(template)
    || (template.isWeapon() || template.isArmor()) && !PlayerBotCombatGear.usable(template))continue;
   double gain=need.equipment()?improvement(session,template):1;if(gain<=0)continue;
   if(need.equipment() && PlayerBotQuestCrafting.productPlan(bot,0,need.item(),1).stage()==PlayerBotQuestCrafting.Stage.CRAFT)continue;
   var quote=CentralMarketService.companionQuote(bot,need.item());long alternative=Math.min(EconomyPrices.replacement(template),EconomyPrices.generatedFloor(template));
   long value=fair(quote,alternative);if(value<=0)continue;
   long limit=Math.min(alternative,value+value*PlayerBotEconomyConfig.MARKET_OVERPAY_PERCENT/100);
   if(PlayerBotQuestMaterials.hasSource(bot,need.item()))limit=Math.min(limit,value*PlayerBotEconomyConfig.MARKET_DROP_SOURCE_PERCENT/100);
   long allowance=(long)(budget*Math.min(1,Math.max(0.1,gain*4)));
   long ask=purchasePrice(quote,Math.min(limit,allowance));if(ask<=0)continue;
   long available=quote.asks().stream().filter(p->p.price()<=ask).mapToLong(CompanionPrice::quantity).sum();
   long quantity=Math.min(Math.min(need.quantity(),available),Math.min(allowance/ask,CentralMarketRules.maxQuantity(template.isStackable(),template.getMaxStackCount())));if(quantity<=0)continue;
   long cost=CentralMarketRules.total(ask,quantity);
   // Reserve the daily allowance durably before creating escrow. A crash can defer spending,
   // but cannot reset the budget and spend that same day's allowance again.
   care.spent+=cost;care.save();PlayerBotService.getInstance().checkpointForOwnerAction(session);
   // A busy market changed nothing, so the reserved allowance is returned.
   try{perform(bot,Map.of("action","kinah","source","0","direction","deposit","quantity",Long.toString(cost)));}
   catch(CentralMarketService.CompanionBusy busy){care.spent-=cost;care.save();throw busy;}
   try{perform(bot,Map.of("action","buy","variant",need.item()+":0:0","quantity",Long.toString(quantity),"price",Long.toString(ask)));}
   finally{NEXT.put(bot.getObjectId(),System.currentTimeMillis()+5000);}
   return true;
  }
  return false;
 }
 /** Equipment follows the care rules for unwanted gear; materials count only above the working reserve. */
 private static long surplus(PlayerBotSession session,Item item,Set<Integer> upgrades) {
  Player bot=session.bot();var t=item.getItemTemplate();
  if(item.isEquipped() || !item.isTradeable() || item.getExpireTime()!=0 || !CentralMarketService.eligible(t) || PlayerBotGearPolicy.referenced(bot,item)
   || PlayerBotGearPolicy.goal(bot,t) || PlayerBotGearPolicy.generated(bot,item) || PlayerBotRoadMerchants.merchandise(bot,item) || PlayerBotCare.worldQuestItem(bot,t.getTemplateId())
   || PlayerBotQuestMaterials.protectedItem(session,t.getTemplateId()))return 0;
  if(PlayerBotCare.disposable(item,false,upgrades.contains(item.getObjectId()),false,t.getRequiredLevel(bot.getPlayerClass())>bot.getLevel()))return item.getItemCount();
  if(!t.isStackable() || !CentralMarketService.material(t))return 0;
  var craft=PlayerBotFocus.craftingPlan(session);
  if(craft!=null && craft.materials().containsKey(t.getTemplateId()))return 0;
  return Math.max(0,item.getItemCount()-Math.max(0,PlayerBotEconomyConfig.MARKET_MATERIAL_RESERVE));
 }
 private static boolean sell(PlayerBotSession session)throws Exception {
  Player bot=session.bot();int evaluated=0;
  Set<Integer> upgrades=new HashSet<>();for(var upgrade:PlayerBotEquipment.upgrades(bot,session.combatRole()))upgrades.add(upgrade.item().getObjectId());
  for(Item item:bot.getInventory().getItems()) {
   long quantity=surplus(session,item,upgrades);if(quantity<=0)continue;
   if(++evaluated>PlayerBotEconomyConfig.MARKET_SALES_PER_VISIT)break;
   var template=item.getItemTemplate();quantity=Math.min(quantity,CentralMarketRules.maxQuantity(template.isStackable(),template.getMaxStackCount()));
   var quote=CentralMarketService.companionQuote(bot,item);
   long vendor=PricesService.getSellReward(template.getPrice(),PricesService.getVendorSellModifier());
   long floor=Math.max(EconomyPrices.generatedFloor(template),vendor*10000/RETURN_BASIS_POINTS+1);
   long price=salePrice(quote,EconomyPrices.anchor(template),floor);if(price<=0)continue;
   if(CentralMarketRules.collect(CentralMarketRules.total(price,quantity),RETURN_BASIS_POINTS)<vendor*quantity)continue;
   int itemId=item.getItemId();int market=com.aionemu.gameserver.model.items.storage.StorageType.MARKET_WAREHOUSE.getId();
   perform(bot,Map.of("action","transfer","source","0","target",Integer.toString(market),"object",Integer.toString(item.getObjectId()),"quantity",Long.toString(quantity)));
   // A partial stack moved under a new object ID; anything unsold comes home on the next visit.
   var stock=CentralMarketService.companionLedger(bot).stock().stream().filter(s->s.itemId()==itemId).findFirst().orElse(null);
   if(stock!=null)perform(bot,Map.of("action","sell","object",Integer.toString(stock.object()),"quantity",Long.toString(Math.min(quantity,stock.count())),"price",Long.toString(price)));
   return true;
  }
  return false;
 }
 static long salePrice(CompanionQuote quote,long anchor,long floor) {
  long value=fair(quote,anchor);if(value<=0)return 0;
  var ladder=CentralMarketRules.ladder(quote.base(),quote.floor(),quote.ceiling());
  long ask=quote.asks().stream().filter(p->p.quantity()>0).mapToLong(CompanionPrice::price).min().orElse(0);
  long bid=quote.bids().stream().filter(p->p.quantity()>0).mapToLong(CompanionPrice::price).max().orElse(0);
  // Take a fair funded bid; otherwise undercut by one selectable price, with a
  // native resale floor. If the whole book is worse, leave the goods to normal care/vendor work.
  if(bid>=floor)return ladder.stream().filter(p->p>=floor && p<=bid && p>=value*PlayerBotEconomyConfig.MARKET_FAIR_BID_PERCENT/100).reduce((a,b)->b).orElse(0L);
  long target=ask>0?Math.min(value,ask-1):value;
  return ladder.stream().filter(p->p>=floor && p<=target).reduce((a,b)->b).orElse(0L);
 }
 /** Recycling archives the bot, so nothing may stay behind in the market. */
 static void requireSettled(PlayerBotSession session) {
  try {
   session.bot().getInventory().increaseKinah(0);
   PlayerBotService.getInstance().checkpointForOwnerAction(session);
   CentralMarketService.closeCompanionPosition(session.bot());
   int id=session.bot().getObjectId();VIEWS.remove(id);NEXT.remove(id);WINDING.remove(id);
  }catch(CentralMarketService.CompanionHeld error) {
   InventoryDAO.quarantineMarketInventory(session.bot().getObjectId());session.markClosing();
   throw new IllegalStateException("This companion remains unrecycled. Its saved market goods need a fresh activation before recycling.",error);
  }catch(SQLException error){throw new IllegalStateException("The market could not close safely. This companion remains unrecycled and its goods are retained.",error);}
 }
 private static String name(int item) {
  var template=DataManager.ITEM_DATA.getItemTemplate(item);return template==null?"Item "+item:template.getName();
 }
 private static long unit(long quantity,long price) {return CentralMarketRules.total(price,quantity);}
 static Map<String,Object> snapshot(PlayerBotSession session) {
  Player bot=session.bot();
  if(!eligible(session))return Map.of("available",false);
  var cached=VIEWS.get(bot.getObjectId());long now=System.currentTimeMillis();
  if(cached!=null && now-cached.at()<VIEW_MILLIS)return cached.value();
  try {
   var ledger=CentralMarketService.companionLedger(bot);var orders=new ArrayList<Map<String,Object>>();
   for(var order:ledger.orders())if(order.live())orders.add(Map.of("text",(order.side().equals("B")?"Buying ":"Listed ")+order.remaining()+" x "+name(order.itemId())+" at "+order.price()+" Kinah each"));
   var trades=new ArrayList<Map<String,Object>>();
   for(var trade:CentralMarketService.companionTrades(bot,6))trades.add(Map.of("text",(trade.bought()?"Bought ":"Sold ")+trade.quantity()+" x "+name(trade.itemId())+" for "+unit(trade.quantity(),trade.price())+" Kinah"));
   var value=Map.<String,Object>of("available",true,"held",ledger.kinah(),"orders",orders,"trades",trades,
    "scope","Uses its own Kinah and real surplus. Compares recent trades, independent sellers, native prices and actual upgrades. Keeps a level-based reserve and your Care spending limit; collects goods and proceeds before spending again. Reprices stale listings. Kinah stays with the companion.");
   VIEWS.put(bot.getObjectId(),new View(now,value));return value;
  }catch(SQLException error){return Map.of("available",false);}
 }
 static void close(PlayerBotSession session) {
  int id=session.bot().getObjectId();NEXT.remove(id);VIEWS.remove(id);
 }
}
