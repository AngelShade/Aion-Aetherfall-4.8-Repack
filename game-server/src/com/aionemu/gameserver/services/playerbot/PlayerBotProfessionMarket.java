package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.services.CentralMarketService;
import com.aionemu.gameserver.services.CentralMarketService.CompanionQuote;

/** One bounded quote queue, drained by the profession worker, never by a decision callback. */
final class PlayerBotProfessionMarket {
 private record View(long until,CompanionQuote quote) {}
 private record Claim(int actor,long until) {}
 private static final Map<Integer,View> CACHE=new LinkedHashMap<>();
 private static final Set<Integer> PENDING=new LinkedHashSet<>();
 private static final Map<Integer,Claim> CLAIMS=new HashMap<>();
 private static final Map<Integer,Long> OFFERED=new HashMap<>();
 private static long window;private static int reads;private static String day="";
 static synchronized CompanionQuote quote(int item,long now) {
  var view=CACHE.get(item);if(view!=null && now<view.until())return view.quote();
  if(PENDING.size()<PlayerBotEconomyConfig.PROFESSIONS_MARKET_CACHE_ENTRIES)PENDING.add(item);return null;
 }
 static void refresh(long now) throws java.sql.SQLException {
  int item;
  synchronized(PlayerBotProfessionMarket.class){if(window!=now/60000){window=now/60000;reads=0;}
   if(reads>=PlayerBotEconomyConfig.PROFESSIONS_MARKET_VARIANTS_PER_MINUTE || PENDING.isEmpty())return;
   item=PENDING.iterator().next();PENDING.remove(item);reads++;}
  var quote=CentralMarketService.productionQuote(item);
  synchronized(PlayerBotProfessionMarket.class){while(CACHE.size()>=PlayerBotEconomyConfig.PROFESSIONS_MARKET_CACHE_ENTRIES)CACHE.remove(CACHE.keySet().iterator().next());
   CACHE.put(item,new View(now+PlayerBotEconomyConfig.PROFESSIONS_MARKET_SNAPSHOT_SECONDS*1000L,quote));}
 }
 static long demand(CompanionQuote quote,int actor,int owner,int item) {
  if(quote==null)return 0;long quantity=0;
  for(var bid:quote.bids())if(!bid.simulated() && bid.owner()!=-actor && bid.owner()!=owner && (bid.owner()>0 || PlayerBotProfessions.consumer(-bid.owner(),item)))quantity=Math.addExact(quantity,bid.quantity());
  return quantity;
 }
 static synchronized boolean claim(int actor,int item,long quantity,long demand,long now) {
  var claim=CLAIMS.get(item);if(claim!=null && claim.actor()!=actor && claim.until()>now)return false;
  if(quantity<=0 || quantity>Math.min(demand,PlayerBotEconomyConfig.PROFESSIONS_WORLD_ITEM_UNITS))return false;
  CLAIMS.put(item,new Claim(actor,now+PlayerBotEconomyConfig.PROFESSIONS_WORK_SLICE_SECONDS*1000L));return true;
 }
 static synchronized long remaining(int item){String today=java.time.LocalDate.now().toString();if(!today.equals(day)){day=today;OFFERED.clear();}return Math.max(0,PlayerBotEconomyConfig.PROFESSIONS_WORLD_ITEM_UNITS-OFFERED.getOrDefault(item,0L));}
 static synchronized void offered(int item,long quantity){remaining(item);OFFERED.merge(item,quantity,Math::addExact);}
 static synchronized void restore(int item,long quantity){remaining(item);OFFERED.merge(item,quantity,Math::addExact);}
 static synchronized void release(int actor){CLAIMS.entrySet().removeIf(e->e.getValue().actor()==actor);}
 private PlayerBotProfessionMarket(){}
}
