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
 private record Need(int item,long quantity,boolean equipment,boolean stone,boolean upgrade) {}
 private record Choice(String variant,CompanionQuote quote,long price,double gain) {}
 private record View(long at,Map<String,Object> value) {}
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 private static final Map<Integer,Boolean> WINDING=new ConcurrentHashMap<>();
 private static final Map<Integer,View> VIEWS=new ConcurrentHashMap<>();
 private static final AtomicLong WINDOW=new AtomicLong();
 private static final AtomicInteger USED=new AtomicInteger();
 private static final AtomicLong VISITS=new AtomicLong(),BOUGHT=new AtomicLong(),LISTED=new AtomicLong(),POSTED=new AtomicLong();
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
 static boolean idle(PlayerBotSession session){return idle(session,false);}
 /** A background visit trades for a dormant bot. Pass rewards still wait until it is awake. */
 static boolean idle(PlayerBotSession session,boolean background) {
  Player bot=session.bot();long now=System.currentTimeMillis();
  if(!eligible(session) || now<NEXT.getOrDefault(bot.getObjectId(),0L) || !background && !PlayerBotPass.rewardIdle(session) || !PlayerBotWarehouse.idle(bot))return false;
  // Phase 4: a TRADER-leaning bot visits the market more often.
  NEXT.put(bot.getObjectId(),now+(long)(Math.max(60,PlayerBotEconomyConfig.MARKET_INTERVAL_SECONDS)*1000L*PlayerBotLeanings.pace(bot,PlayerBotLeanings.Activity.MARKET))+bot.getObjectId()%60000);
  try {
   PlayerBotGearPolicy.state(session);
   PlayerBotService.getInstance().checkpointForOwnerAction(session);
   var ledger=CentralMarketService.companionLedger(bot);PlayerBotProfessions.ledger(bot,ledger);
   boolean settling=settle(session,ledger,now);
   if(settling){NEXT.put(bot.getObjectId(),now+5000);return true;}
   if(WINDING.remove(bot.getObjectId())!=null)return false;
   if(ledger.orders().stream().filter(CompanionOrder::live).count()>=Math.max(0,PlayerBotEconomyConfig.MARKET_OPEN_ORDERS))return false;
   VISITS.incrementAndGet();PlayerBotLeanings.did(bot,PlayerBotLeanings.Activity.MARKET);
   if(buy(session,ledger))return true;
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
  for(var order:ledger.orders())if(order.uncollected()>0 || order.gross()>0) {
   perform(bot,Map.of("action","collect","order",Long.toString(order.id())));
   if(order.side().equals("S") && order.gross()>0 && order.price()>0){PlayerBotProfessions.sold(bot,order.itemId(),order.gross()/order.price());PlayerBotWorldOutcomes.sold(bot,order.gross()/order.price());}
   return true;
  }
  long expiry=WINDING.containsKey(bot.getObjectId())?0:Math.max(1,PlayerBotEconomyConfig.MARKET_ORDER_HOURS)*3600000L;
  for(var order:ledger.orders())if(order.live() && now-order.created()>=expiry){perform(bot,Map.of("action","cancel","order",Long.toString(order.id())));return true;}
  for(var stock:ledger.stock()) {
   perform(bot,Map.of("action","transfer","source",Integer.toString(com.aionemu.gameserver.model.items.storage.StorageType.MARKET_WAREHOUSE.getId()),"target","0","object",Integer.toString(stock.object()),"quantity",Long.toString(stock.count())));
   return true;
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
   if(!ordered.contains(item) && bot.getInventory().getItemCountByItemId(item)==0 && bot.getEquipment().getEquippedItemsByItemId(item).isEmpty())needs.add(new Need(item,1,true,false,false));
   if(needs.size()>=PlayerBotEconomyConfig.MARKET_NEEDS_PER_VISIT)return needs;
  }
  Item target=PlayerBotCare.enhancementTarget(session);
  if(target!=null && PlayerBotCare.bestStone(bot,target)==null && PlayerBotCare.stoneAllowance(session)>0) {
   var stone=PlayerBotCare.marketStone(bot,target);
   if(stone!=null && !ordered.stream().anyMatch(id->{var t=DataManager.ITEM_DATA.getItemTemplate(id);return t!=null && PlayerBotCare.stone(t,target);}))needs.add(new Need(stone.getTemplateId(),1,false,true,false));
   if(needs.size()>=PlayerBotEconomyConfig.MARKET_NEEDS_PER_VISIT)return needs;
  }
  // A world bot's trade inputs reach the market even outside its awake work turn; that is what makes the market its first stop.
  var craft=session.autonomous()?null:PlayerBotFocus.craftingPlan(session);
  var missing=session.autonomous()?PlayerBotProfessions.inputs(bot):craft==null?Map.<Integer,Long>of():craft.missing();
  for(var entry:new TreeMap<>(missing).entrySet()) {
   if(!ordered.contains(entry.getKey()) && entry.getValue()>0)needs.add(new Need(entry.getKey(),entry.getValue(),false,false,false));
   if(needs.size()>=PlayerBotEconomyConfig.MARKET_NEEDS_PER_VISIT)break;
  }
  return needs;
 }
 /** Phase 3: wearable gear on sale now, after goals, stones and trade inputs, with its own per-visit limit.
  * Awake and dormant bots share this path, so offscreen purchases happen at the awake rate.
  * Companions keep the owner's goals and Care budget only. */
 private static List<Need> upgradeNeeds(PlayerBotSession session,CompanionLedger ledger,List<Need> planned) {
  if(!PlayerBotMarketUpgrades.enabled() || !session.autonomous())return List.of();
  var skip=new HashSet<Integer>();for(var need:planned)skip.add(need.item());
  for(var order:ledger.orders())if(order.side().equals("B") && (order.live() || order.uncollected()>0))skip.add(order.itemId());
  var result=new ArrayList<Need>();
  for(int item:PlayerBotMarketUpgrades.picks(session,skip,PlayerBotEconomyConfig.MARKET_UPGRADES_PER_VISIT,System.currentTimeMillis()))result.add(new Need(item,1,true,false,true));
  return result;
 }
 /** Configured reserve is the level-65 reference; native progression needs smaller working balances early on. */
 /** Bot market work since start, for worldstats. */
 static String stats(){return "market visits="+VISITS.get()+", bought="+BOUGHT.get()+", buy orders="+POSTED.get()+", listed="+LISTED.get()+", "+PlayerBotMarketUpgrades.stats();}
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
 /** Highest selectable price a waiting buy order may offer without passing the bot's limit. */
 static long orderPrice(CompanionQuote quote,long limit){return orderPrice(CentralMarketRules.ladder(quote.base(),quote.floor(),quote.ceiling()),limit);}
 static long orderPrice(List<Long> ladder,long limit){return ladder.stream().filter(p->p>0 && p<=limit).reduce((a,b)->b).orElse(0L);}
 static long purchasePrice(CompanionQuote quote,long limit) {
  long ask=quote.asks().stream().filter(p->p.quantity()>0).mapToLong(CompanionPrice::price).min().orElse(0);
  if(ask<=0)return 0;
  return CentralMarketRules.ladder(quote.base(),quote.floor(),quote.ceiling()).stream().filter(p->p>=ask && p<=limit).findFirst().orElse(0L);
 }
 private static double improvement(PlayerBotSession session,ItemTemplate template) {
  return improvement(session,template,0,0);
 }
 private static double improvement(PlayerBotSession session,ItemTemplate template,int enchant,int tempering) {
  Player bot=session.bot();if(!PlayerBotGearPolicy.purchasable(bot,template))return 0;
  var role=PlayerBotGearPolicy.role(bot,session.combatRole());double score=PlayerBotGearPolicy.variantScore(bot,template,role,enchant,tempering);
  double gain=PlayerBotGearPolicy.slots(bot,template).stream().mapToDouble(slot->PlayerBotGearPolicy.gain(bot,template,score,slot,role)).max().orElse(0);
  return gain>1?Math.min(1,gain/Math.max(1,score)):0;
 }
 private static boolean buy(PlayerBotSession session,CompanionLedger ledger)throws Exception {
  Player bot=session.bot();var care=PlayerBotQuestSync.state(session);String day=java.time.LocalDate.now().toString();
  if(!care.spentDay.equals(day)){care.spentDay=day;care.spent=0;care.save();}
  long reserved=Math.max(care.reserve,reserve(bot.getLevel(),PlayerBotEconomyConfig.MARKET_RESERVE_KINAH));
  long budget=budget(bot.getInventory().getKinah(),reserved,PlayerBotEconomyConfig.MARKET_BUDGET_PERCENT,dailyAllowance(session.autonomous(),bot.getLevel(),care.dailyBudget,care.spent));if(budget<=0)return false;
  var planned=new ArrayList<>(needs(session,ledger));planned.addAll(upgradeNeeds(session,ledger,planned));
  for(var need:planned) {
   if(need.upgrade())PlayerBotMarketUpgrades.tried(bot,need.item(),System.currentTimeMillis());
   var template=DataManager.ITEM_DATA.getItemTemplate(need.item());
   if(template==null || !CentralMarketService.eligible(template)
    || !com.aionemu.gameserver.services.ItemEligibility.isPlayerItem(template) || com.aionemu.gameserver.services.ItemEligibility.isAppearanceOnly(template)
    || (template.isWeapon() || template.isArmor()) && !PlayerBotCombatGear.usable(template))continue;
   double gain=need.equipment()?improvement(session,template):1;if(gain<=0)continue;
   if(need.equipment() && PlayerBotQuestCrafting.productPlan(bot,0,need.item(),1).stage()==PlayerBotQuestCrafting.Stage.CRAFT)continue;
   var quote=CentralMarketService.companionQuote(bot,need.item());String variant=need.item()+":0:0";long alternative=Math.min(EconomyPrices.replacement(template),EconomyPrices.generatedFloor(template));
   long value=fair(quote,alternative);if(value<=0)continue;
   long limit=Math.min(alternative,value+value*PlayerBotEconomyConfig.MARKET_OVERPAY_PERCENT/100);
   if(PlayerBotQuestMaterials.hasSource(bot,need.item()))limit=Math.min(limit,value*PlayerBotEconomyConfig.MARKET_DROP_SOURCE_PERCENT/100);
   long allowance=(long)(budget*Math.min(1,Math.max(0.1,gain*4)));
   // Trade inputs also stay inside the trade's own daily budget.
   if(need.stone())allowance=Math.min(allowance,PlayerBotCare.stoneAllowance(session));
   else if(!need.equipment() && session.autonomous())allowance=Math.min(allowance,PlayerBotProfessions.allowance(session,false));
   long resting=purchasePrice(quote,Math.min(limit,allowance));
   if(need.equipment()) {
    Choice enhanced=enhancedChoice(session,template,resting,allowance);
    if(enhanced!=null){variant=enhanced.variant();quote=enhanced.quote();resting=enhanced.price();}
   }
   // Nothing fairly priced is on sale: a world crafter posts a real funded order for gatherers to sell into.
   boolean post=resting<=0 && !need.equipment() && session.autonomous();
   long ask=post?orderPrice(quote,Math.min(limit,allowance)):resting;if(ask<=0)continue;
   long available=post?need.quantity():quote.asks().stream().filter(p->p.price()<=ask).mapToLong(CompanionPrice::quantity).sum();
   long quantity=Math.min(Math.min(need.quantity(),available),Math.min(allowance/ask,CentralMarketRules.maxQuantity(template.isStackable(),template.getMaxStackCount())));if(quantity<=0)continue;
   long cost=CentralMarketRules.total(ask,quantity);
   // Reserve the daily allowance durably before creating escrow. A crash can defer spending,
   // but cannot reset the budget and spend that same day's allowance again.
   if(need.stone())PlayerBotCare.stoneSpent(session,cost);
   care.spent+=cost;care.save();PlayerBotService.getInstance().checkpointForOwnerAction(session);
   // A busy market changed nothing, so the reserved allowance is returned.
   try{perform(bot,Map.of("action","kinah","source","0","direction","deposit","quantity",Long.toString(cost)));}
   catch(CentralMarketService.CompanionBusy busy){care.spent-=cost;care.save();if(need.stone())PlayerBotCare.stoneSpent(session,-cost);throw busy;}
   try{perform(bot,Map.of("action","buy","variant",variant,"quantity",Long.toString(quantity),"price",Long.toString(ask)));}
   finally{NEXT.put(bot.getObjectId(),System.currentTimeMillis()+5000);}
   if(!need.stone() && !need.equipment() && session.autonomous())PlayerBotProfessions.marketSpent(bot,cost);
   // Count stones only when bought from a live ask; a posted order may never fill, and goods coming home may be unsold listings.
   if(need.stone() && !post)PlayerBotCare.bought(quantity);
   if(need.upgrade())PlayerBotMarketUpgrades.bought(session);
   (post?POSTED:BOUGHT).incrementAndGet();return true;
  }
  return false;
 }
 static boolean betterEnhanced(double gain,long price,double plainGain,long plainPrice,long productionCost) {
  return gain>0 && price>0 && plainPrice>0 && price<Math.min(CentralMarketRules.MAX_KINAH,plainPrice+productionCost)
   && gain/price>plainGain/plainPrice;
 }
 private static double gearGain(PlayerBotSession session,ItemTemplate template,int enchant,int tempering) {
  Player bot=session.bot();if(!PlayerBotGearPolicy.purchasable(bot,template))return 0;
  var role=PlayerBotGearPolicy.role(bot,session.combatRole());double score=PlayerBotGearPolicy.variantScore(bot,template,role,enchant,tempering);
  return PlayerBotGearPolicy.slots(bot,template).stream().mapToDouble(slot->PlayerBotGearPolicy.gain(bot,template,score,slot,role)).max().orElse(0);
 }
 private static Choice enhancedChoice(PlayerBotSession session,ItemTemplate template,long plainAsk,long allowance)throws SQLException {
  long replacement=plainAsk>0?plainAsk:Math.min(EconomyPrices.replacement(template),EconomyPrices.generatedFloor(template));Choice best=null;
  double plainGain=Math.max(0,gearGain(session,template,0,0));
  int maxE=Math.min(template.getMaxEnchantLevel(),Math.max(0,com.aionemu.gameserver.configs.main.CentralMarketSimulationConfig.ENHANCED_MAX_ENCHANT));
  int maxT=Math.min(template.getMaxTampering(),Math.max(0,com.aionemu.gameserver.configs.main.CentralMarketSimulationConfig.ENHANCED_MAX_TEMPERING));
  for(int n=1;n<=maxE+maxT;n++) {
   int e=n<=maxE?n:0,temp=n<=maxE?0:n-maxE;String key=template.getTemplateId()+":"+e+":"+temp;
   var q=CentralMarketService.companionQuote(session.bot(),key);long anchor=EconomyPrices.enhanced(template,e,temp),value=fair(q,anchor);
   long price=purchasePrice(q,Math.min(allowance,Math.min(anchor,value+value*PlayerBotEconomyConfig.MARKET_OVERPAY_PERCENT/100)));
   double gain=gearGain(session,template,e,temp);Player bot=session.bot();
   long cost=EconomyPrices.enhancementCost(template,e,temp,com.aionemu.gameserver.model.gameobjects.player.Rates.get(bot,com.aionemu.gameserver.configs.main.RatesConfig.ENCHANTMENT_STONE_BASE_CHANCES),com.aionemu.gameserver.model.gameobjects.player.Rates.get(bot,com.aionemu.gameserver.configs.main.RatesConfig.ENCHANTMENT_STONE_AMPLIFIED_CHANCES),com.aionemu.gameserver.model.gameobjects.player.Rates.get(bot,com.aionemu.gameserver.configs.main.RatesConfig.TEMPERING_CHANCES));
   if(betterEnhanced(gain,price,plainGain,replacement,cost) && (best==null || gain/price>best.gain()/best.price()))best=new Choice(key,q,price,gain);
  }
  return best;
 }
 /** Equipment follows the care rules for unwanted gear; materials count only above the working reserve. */
 private static long surplus(PlayerBotSession session,Item item,Set<Integer> upgrades) {
  Player bot=session.bot();var t=item.getItemTemplate();
  if(item.isEquipped() || PlayerBotQuestSync.state(session).protectedItems.contains(item.getObjectId()) || !item.isTradeable() || item.getExpireTime()!=0 || !CentralMarketService.eligible(t) || PlayerBotGearPolicy.referenced(bot,item)
   || PlayerBotGearPolicy.goal(bot,t) || PlayerBotGearPolicy.generated(bot,item) || PlayerBotRoadMerchants.merchandise(bot,item) || PlayerBotCare.worldQuestItem(bot,t.getTemplateId())
   || PlayerBotQuestMaterials.protectedItem(session,t.getTemplateId()))return 0;
  // A world bot's trade goods sell by its own style. This replaces the single material reserve for them.
  long trade=session.autonomous()?PlayerBotProfessions.saleQuantity(bot,item):-1;if(trade>=0)return trade;
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
   long appraisal=EconomyPrices.enhanced(template,item.getEnchantLevel(),item.getTempering());
   long floor=Math.max(Math.max(item.getEnchantLevel()>0 || item.getTempering()>0?appraisal:EconomyPrices.generatedFloor(template),vendor*10000/RETURN_BASIS_POINTS+1),PlayerBotProfessions.saleFloor(bot,item.getItemId()));
   long price=salePrice(quote,appraisal,floor);if(price<=0)continue;
   if(CentralMarketRules.collect(CentralMarketRules.total(price,quantity),RETURN_BASIS_POINTS)<vendor*quantity)continue;
   int itemId=item.getItemId();int market=com.aionemu.gameserver.model.items.storage.StorageType.MARKET_WAREHOUSE.getId();
   perform(bot,Map.of("action","transfer","source","0","target",Integer.toString(market),"object",Integer.toString(item.getObjectId()),"quantity",Long.toString(quantity)));
   // A partial stack moved under a new object ID; anything unsold comes home on the next visit.
   var stock=CentralMarketService.companionLedger(bot).stock().stream().filter(s->s.itemId()==itemId).findFirst().orElse(null);
   if(stock!=null){long listed=Math.min(quantity,stock.count());perform(bot,Map.of("action","sell","object",Integer.toString(stock.object()),"quantity",Long.toString(listed),"price",Long.toString(price)));PlayerBotProfessions.offered(bot,itemId,listed);}
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
