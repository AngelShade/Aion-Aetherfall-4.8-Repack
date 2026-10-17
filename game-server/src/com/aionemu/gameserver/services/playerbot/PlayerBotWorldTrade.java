/* Public trade valuation/acceptance purpose: pinned TradeAction, TradeStatusAction,
 * ItemUsageValue; mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.trade.Exchange;
import com.aionemu.gameserver.network.aion.serverpackets.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.services.ExchangeService;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.services.trade.PricesService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Public fixed-price goods trades; group control never grants inventory ownership. */
public final class PlayerBotWorldTrade {
 private static final class Deal {
  final Player human,bot;final boolean selling;final long started=System.currentTimeMillis();long price;
  Deal(Player human,Player bot,boolean selling,long price){this.human=human;this.bot=bot;this.selling=selling;this.price=price;}
 }
 private static final Map<Integer,Deal> DEALS=new ConcurrentHashMap<>();
 private static final ThreadLocal<Boolean> INSIDE=ThreadLocal.withInitial(()->false);
 private static final ThreadLocal<Player> FINISHING=new ThreadLocal<>();
 static boolean inside(){return INSIDE.get();}
 static boolean finishing(Player p){return p!=null && FINISHING.get()==p;}
 static Deal deal(Player p){if(p==null)return null;var own=DEALS.get(p.getObjectId());if(own!=null)return own;
  var peer=ExchangeService.getInstance().getCurrentParnterExchange(p);return peer==null?null:DEALS.get(peer.getActiveplayer().getObjectId());}
 static boolean handles(Player p){var e=p==null?null:ExchangeService.getInstance().getCurrentParnterExchange(p);return p!=null && (p.isWorldBot() || e!=null && e.getActiveplayer().isWorldBot());}
 static String refusal(Player human,Player bot) {
  if(human==null || bot==null || human.isPlayerBot() || !bot.isWorldBot())return "Choose an active world bot to trade with.";
  if(!human.isOnline() || !human.isSpawned() || !bot.isSpawned())return "Wait until both characters have entered the world.";
  if(human.isDead() || bot.isDead())return "Both characters must be alive to trade.";
  if(human.isInAnyHide() || bot.isInAnyHide())return "Leave hide mode before trading.";
  if(human.getRace()!=bot.getRace())return "Trade with a world bot of your own faction.";
  if(human.getWorldId()!=bot.getWorldId() || human.getInstanceId()!=bot.getInstanceId())return "Meet the bot on the same map and instance.";
  if(!PositionUtil.isInRange(human,bot,5))return "Move within 5 metres of the bot to trade.";
  if(human.getController().isInCombat())return "Your combat timer is still active; wait for combat to end before trading.";
  if(bot.getController().isInCombat())return bot.getName()+" is still in combat; wait for its combat timer to end.";
  if(PlayerBotService.getInstance().worldSession(bot)==null)return "The world bot is entering, leaving or waiting for a save; retry when it is ready.";
  if(GameServer.isShuttingDownSoon())return "Trading is unavailable while the server is shutting down.";
  return null;
 }
 static boolean ready(Player human,Player bot){return refusal(human,bot)==null;}
 static boolean participants(Player a,Player b){Player bot=a.isWorldBot()?a:b,human=a.isWorldBot()?b:a;return ready(human,bot) && !a.isTrading() && !b.isTrading();}
 static long price(long unit,long count,boolean sale){if(unit<=0 || count<=0)throw new IllegalArgumentException("Item has no trade value");
  try{return Math.multiplyExact(sale?Math.multiplyExact(unit,2):unit,count);}catch(ArithmeticException e){throw new IllegalArgumentException("Trade price is too large",e);}}
 private static long value(Item item,long count,boolean sale){return price(PricesService.getSellReward(item.getItemTemplate().getPrice(),PricesService.getVendorSellModifier()),count,sale);}
 private static Player find(Player human,String name){List<Player> bots=new ArrayList<>();human.getKnownList().forEachPlayer(p->{if(p.isWorldBot() && p.getName().equalsIgnoreCase(name))bots.add(p);});
  if(bots.size()!=1 || !ready(human,bots.getFirst()))throw new IllegalArgumentException("Stand within 5m of that world bot, outside combat.");return bots.getFirst();}
 public static void shop(Player human,String name){Player bot=find(human,name);var s=PlayerBotService.getInstance().worldSession(bot);if(s==null)throw new IllegalArgumentException("World bot is unavailable.");synchronized(s){if(!PlayerBotService.getInstance().current(s))throw new IllegalArgumentException("World bot control changed; retry.");int count=0;
  for(Item i:bot.getInventory().getItems())if(PlayerBotTrade.rights(bot,human,i) && PlayerBotWorldActivities.surplus(s,i)) {
   try{PacketSendUtility.sendMessage(human,i.getObjectId()+": "+i.getItemName()+" x"+i.getItemCount()+" — "+value(i,1,true)+" Kinah each");count++;}catch(IllegalArgumentException ignored){}
  }
  PacketSendUtility.sendMessage(human,count==0?bot.getName()+" has no surplus equipment for sale.":"Use .bot buy "+bot.getName()+" <object ID> [count]. Native trade lets you review and confirm.");}}
 public static void buy(Player human,String name,int object,long count){Player bot=find(human,name);var s=PlayerBotService.getInstance().worldSession(bot);if(s==null)throw new IllegalArgumentException("World bot is unavailable.");synchronized(s){if(!PlayerBotService.getInstance().current(s))throw new IllegalArgumentException("World bot control changed; retry.");
  Item item=bot.getInventory().getItemByObjId(object);
  if(!PlayerBotTrade.rights(bot,human,item) || count<=0 || count>item.getItemCount() || !PlayerBotWorldActivities.surplus(s,item))throw new IllegalArgumentException("That stock is unavailable.");
  long price=value(item,count,true);if(price>human.getInventory().getKinah())throw new IllegalArgumentException("Not enough Kinah for this quote.");
  if(!open(human,bot,true,price))return;
  INSIDE.set(true);try{ExchangeService.getInstance().addItem(bot,object,count);}finally{INSIDE.remove();}
  PacketSendUtility.sendMessage(human,"Price: "+price+" Kinah. Add exactly that amount, then lock and confirm; cancelling spends nothing.");}}
 static boolean request(Player human,Player bot){var s=PlayerBotService.getInstance().worldSession(bot);if(s==null){PacketSendUtility.sendMessage(human,"World bot is unavailable.");return true;}synchronized(s){if(!PlayerBotService.getInstance().current(s))return true;if(open(human,bot,false,0))
  PacketSendUtility.sendMessage(human,"Offer usable equipment upgrades or recovery supplies. Lock to see the bot's Kinah quote, then confirm or cancel.");return true;}}
 private static boolean open(Player human,Player bot,boolean sale,long price) {
  String why=refusal(human,bot);
  if(why==null && human.isTrading())why="Finish or cancel your current trade first.";
  if(why==null && bot.isTrading())why=bot.getName()+" is already trading with another player.";
  if(why==null && bot.isCasting())why=bot.getName()+" is casting; try again when the cast finishes.";
  if(why==null && bot.isLooting())why=bot.getName()+" is looting; try again when it finishes.";
  if(why!=null){PacketSendUtility.sendMessage(human,why);return false;}
  bot.getMoveController().abortMove();ExchangeService.getInstance().registerExchange(human,bot);
  if(!bot.isTrading())return false;DEALS.put(bot.getObjectId(),new Deal(human,bot,sale,price));return true;
 }
 static boolean useful(PlayerBotSession s,Item item,long count) {
  if(item==null || count<=0 || count>item.getItemCount() || PlayerBotCare.worldQuestItem(s.bot(),item.getItemId()))return false;
  var t=item.getItemTemplate();Player bot=s.bot();
  if(PlayerBotSupplies.available(bot,t))return count<=10 && bot.getInventory().getItemCountByItemId(item.getItemId())+count<=20;
  return count==1 && PlayerBotGearPolicy.eligible(bot,t) && PlayerBotGearPolicy.slots(bot,t).stream().anyMatch(slot->
   PlayerBotGearPolicy.gain(bot,t,PlayerBotGearPolicy.score(bot,item,s.combatRole()),slot,s.combatRole())>0);
 }
 static boolean guard(Player p,String operation,int object,long count) {
  if(inside())return false;
  {Deal d=deal(p);var actor=d==null?null:PlayerBotService.getInstance().worldSession(d.bot);synchronized(actor==null?DEALS:actor){INSIDE.set(true);
   try{var service=ExchangeService.getInstance();if(d==null || actor==null || !PlayerBotService.getInstance().current(actor) || !operation.equals("cancel") && !ready(d.human,d.bot)){service.cancelExchange(p);return true;}
    switch(operation){case "item"->service.addItem(p,object,count);case "kinah"->service.addKinah(p,count);case "lock"->service.lockExchange(p);case "confirm"->service.confirmExchange(p);case "cancel"->service.cancelExchange(p);default->throw new IllegalArgumentException(operation);}
   }finally{INSIDE.remove();}return true;}}
 }
 static void locked(Player p){Deal d=deal(p);if(d==null || p!=d.human)return;var service=ExchangeService.getInstance();
  Exchange human=service.getCurrentParnterExchange(d.bot),bot=service.getCurrentParnterExchange(p);if(human==null || bot==null)return;
  try {
   if(d.selling){if(!human.getItems().isEmpty() || human.getKinahCount()!=d.price || bot.getItems().isEmpty())throw new IllegalArgumentException("Add exactly the quoted Kinah, with no items.");}
   else {
    if(human.getItems().isEmpty() || human.getKinahCount()!=0)throw new IllegalArgumentException("Offer goods only; the bot pays the quote.");
    long quote=0;var s=PlayerBotService.getInstance().worldSession(d.bot);Set<Integer> templates=new HashSet<>();
    for(var e:human.getItems().values()){Item i=p.getInventory().getItemByObjId(e.getItemObjId());
     if(!PlayerBotTrade.rights(p,d.bot,i) || !useful(s,i,e.getItemCount()) || !templates.add(i.getItemId()))throw new IllegalArgumentException("The bot does not need one of these items.");
     quote=Math.addExact(quote,value(i,e.getItemCount(),false));}
    if(quote>d.bot.getInventory().getKinah()/2)throw new IllegalArgumentException("The bot cannot afford this trade while keeping its travel reserve.");
    d.price=quote;service.addKinah(d.bot,quote);if(bot.getKinahCount()!=quote)throw new IllegalArgumentException("The bot's funds changed.");
   }
   service.lockExchange(d.bot);PacketSendUtility.sendMessage(p,"World bot quote: "+d.price+" Kinah. Review the trade window before confirming.");
  }catch(RuntimeException e){PacketSendUtility.sendMessage(p,e.getMessage());service.cancelExchange(p);}
 }
 static boolean confirm(Player p){Deal d=deal(p);if(d==null || p!=d.human)return true;var service=ExchangeService.getInstance();
  Exchange human=service.getCurrentParnterExchange(d.bot),bot=service.getCurrentParnterExchange(p);
  if(human==null || bot==null || !human.isLocked() || !bot.isLocked())return true;
  Player seller=d.selling?d.bot:p,buyer=d.selling?p:d.bot;Exchange goods=d.selling?bot:human,payment=d.selling?human:bot;
  if(!ready(p,d.bot) || d.price<=0 || payment.getKinahCount()!=d.price || !payment.getItems().isEmpty() || goods.getKinahCount()!=0 || goods.getItems().isEmpty()
   || goods.getItems().size()>buyer.getInventory().getFreeSlots()){service.cancelExchange(p);return true;}
  var s=PlayerBotService.getInstance().worldSession(d.bot);
  for(var e:goods.getItems().values()){var real=seller.getInventory().getItemByObjId(e.getItemObjId());
   if(!PlayerBotTrade.rights(seller,buyer,real) || (d.selling?!PlayerBotWorldActivities.surplus(s,real):!useful(s,real,e.getItemCount()))){service.cancelExchange(p);return true;}}
  try{PlayerBotTradeStore.purchase(seller,buyer,d.bot,goods,d.price);}
  catch(PlayerBotTradeStore.Indeterminate e){hold(d,e);return true;}
  catch(Exception e){org.slf4j.LoggerFactory.getLogger(PlayerBotWorldTrade.class).error("World trade refused before custody transfer",e);service.cancelExchange(p);return true;}
  List<Item> received=new ArrayList<>();
  try {
   for(var e:goods.getItems().values()){Item real=seller.getInventory().getItemByObjId(e.getItemObjId()),given=e.getItem();
    if(real.getItemCount()==e.getItemCount()){if(given!=real)IDFactory.getInstance().releaseId(given.getObjectId());given=real;seller.getInventory().remove(real);e.setItem(real);}
    else{real.setItemCount(real.getItemCount()-e.getItemCount());real.setPersistentState(PersistentState.UPDATED);}
    given.setEquipmentSlot(0);if(given.getPackCount()>0)given.setPackCount(-given.getPackCount());
    if(buyer.getInventory().add_CharacterTransfer(given)==null)throw new IllegalStateException("Committed world trade projection failed");given.setPersistentState(PersistentState.UPDATED);received.add(given);
   }
   Item from=buyer.getInventory().getKinahItem(),to=seller.getInventory().getKinahItem();from.setItemCount(from.getItemCount()-d.price);to.setItemCount(to.getItemCount()+d.price);
   from.setPersistentState(PersistentState.UPDATED);to.setPersistentState(PersistentState.UPDATED);
  }catch(RuntimeException e){hold(d,e);return true;}
  FINISHING.set(p);try{service.cancelExchange(p);}finally{FINISHING.remove();DEALS.remove(d.bot.getObjectId());}
  PacketSendUtility.sendPacket(p,new SM_EXCHANGE_CONFIRMATION(SM_EXCHANGE_CONFIRMATION.Action.COMPLETED));
  try {
   if(buyer==p)PacketSendUtility.sendPacket(p,new SM_INVENTORY_ADD_ITEM(received,p,ItemPacketService.ItemAddType.PLAYER_EXCHANGE_GET_BACK));
   else for(var e:goods.getItems().values()){Item remaining=p.getInventory().getItemByObjId(e.getItemObjId());if(remaining!=null)PacketSendUtility.sendPacket(p,new SM_INVENTORY_UPDATE_ITEM(p,remaining,ItemPacketService.ItemUpdateType.INC_PLAYER_EXCHANGE_GET_BACK));}
   PacketSendUtility.sendPacket(p,new SM_INVENTORY_UPDATE_ITEM(p,p.getInventory().getKinahItem(),ItemPacketService.ItemUpdateType.INC_PLAYER_EXCHANGE_GET_BACK));
   for(Item item:received)QuestEngine.getInstance().onItemGet(buyer,item.getItemId());
   PacketSendUtility.sendMessage(p,"World bot trade completed: "+d.price+" Kinah.");
  }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(PlayerBotWorldTrade.class).error("Committed world trade notification failed",e);}
  return true;
 }
 private static void hold(Deal d,Exception e){InventoryDAO.quarantineMarketInventory(d.human.getObjectId());InventoryDAO.quarantineMarketInventory(d.bot.getObjectId());
  FINISHING.set(d.human);try{ExchangeService.getInstance().cancelExchange(d.human);}finally{FINISHING.remove();DEALS.remove(d.bot.getObjectId());}
  var s=PlayerBotService.getInstance().worldSession(d.bot);if(s!=null)s.markClosing();
  org.slf4j.LoggerFactory.getLogger(PlayerBotWorldTrade.class).error("World trade held for custody verification; IDs retained",e);
  PacketSendUtility.sendMessage(d.human,"Trade persistence requires custody verification; reconnect after the server log is reviewed.");
  if(d.human.getClientConnection()!=null)d.human.getClientConnection().close();}
 static void closed(Player p){DEALS.remove(p.getObjectId());}
 static boolean tick(PlayerBotSession s){Deal d=DEALS.get(s.bot().getObjectId());if(!s.bot().isTrading())return false;
  if(d==null || !ready(d.human,d.bot) || System.currentTimeMillis()-d.started>120000)PlayerBotTrade.cancelFromAi(s.bot());return s.bot().isTrading();}
 private PlayerBotWorldTrade(){}
}
