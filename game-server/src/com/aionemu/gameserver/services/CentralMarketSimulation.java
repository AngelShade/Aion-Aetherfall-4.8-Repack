package com.aionemu.gameserver.services;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.aionemu.gameserver.configs.main.CentralMarketSimulationConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.trade.EconomyPrices;
import static com.aionemu.gameserver.services.CentralMarketService.*;

/** Persistent virtual quotes; real items are generated only when a funded player buys. */
final class CentralMarketSimulation {
	static final int ACCOUNT = 0;
	private static final Map<Integer,ItemTemplate> REQUESTS=new java.util.concurrent.ConcurrentHashMap<>();
	private static final Set<Integer> CREATED=java.util.concurrent.ConcurrentHashMap.newKeySet();
	private CentralMarketSimulation() {}
	static boolean supplyEligible(ItemTemplate t){return eligible(t) && com.aionemu.gameserver.services.trade.MarketPolicy.generated(t) && (t.getAcquisition()==null || t.getAcquisition().getRequiredAp()==0);}
	/** The enchant and tempering a generated sale of this variant carries, or null when generated supply cannot cover it. */
	static int[] virtualGrade(ItemTemplate t,String key) {
		if(key.equals(t.getTemplateId()+":0:0"))return new int[]{0,0};
		for(int[] v:variants(t))if(key.equals(t.getTemplateId()+":"+v[0]+":"+v[1]))return v;
		return null;
	}
	static void request(ItemTemplate t){if(CentralMarketSimulationConfig.ENABLED && !t.isStackable() && supplyEligible(t) && !CREATED.contains(t.getTemplateId()) && REQUESTS.size()<1024)REQUESTS.putIfAbsent(t.getTemplateId(),t);}
	/** One requested item per maintenance pass, at most 255 variants of each kind; no browse-time book mutation. */
	static void requested(Connection c,Set<String> keys)throws SQLException {
		var t=REQUESTS.values().stream().findFirst().orElse(null);if(t==null)return;
		for(int[] v:variants(t)){String key=t.getTemplateId()+":"+v[0]+":"+v[1];catalog(c,t,key,v[0],v[1]);keys.add(key);}
		CREATED.add(t.getTemplateId());REQUESTS.remove(t.getTemplateId());
	}
	static List<int[]> variants(ItemTemplate t) {
		if(t.isStackable() || !supplyEligible(t))return List.of();
		var result=new ArrayList<int[]>();
		if(!t.isNoEnchant() && t.getTemplateId()/1000000<120)for(int e=1;e<=Math.clamp(CentralMarketSimulationConfig.ENHANCED_MAX_ENCHANT,0,Math.min(255,t.getMaxEnchantLevel()));e++)result.add(new int[]{e,0});
		for(int temp=1;temp<=Math.clamp(CentralMarketSimulationConfig.ENHANCED_MAX_TEMPERING,0,Math.min(255,t.getMaxTampering()));temp++)result.add(new int[]{0,temp});
		return result;
	}
	static int depthChance(int level) {
		if(level<=0)return 100;int[] chances=CentralMarketSimulationConfig.ENHANCED_CHANCE_PER_LEVEL;
		return chances==null || chances.length==0?0:Math.clamp(chances[Math.min(level,chances.length)-1],0,100);
	}
	/** Cheapest repeatable plain acquisition plus the cost to enhance it; a funded bid above this would pay for buying generated stock and enhancing it. */
	static long bidCeiling(ItemTemplate t,int enchant,int tempering) {
		if(enchant==0 && tempering==0)return EconomyPrices.demandCeiling(t);
		long plain=Math.min(EconomyPrices.replacement(t),EconomyPrices.generatedFloor(t));
		return EconomyPrices.demandCeiling(Math.min(CentralMarketRules.MAX_KINAH,plain+EconomyPrices.enhancementCost(t,enchant,tempering)));
	}
	static long supplyFloor(ItemTemplate t,int enchant,int tempering){return enchant==0 && tempering==0?EconomyPrices.generatedFloor(t):EconomyPrices.enhanced(t,enchant,tempering);}
	static List<Long> supplyPrices(ItemTemplate t,String key,int enchant,int tempering,List<Long> levels,java.util.function.IntSupplier roll) {
		boolean plain=key.equals(t.getTemplateId()+":0:0");
		if(!supplyEligible(t) || !plain && (enchant>0 && tempering>0 || variants(t).stream().noneMatch(v->v[0]==enchant && v[1]==tempering) || !key.equals(t.getTemplateId()+":"+enchant+":"+tempering)))return List.of();
		var asks=levels.stream().filter(p->p>=supplyFloor(t,enchant,tempering)).toList();
		int depth=Math.min(asks.size(),Math.clamp(CentralMarketSimulationConfig.PRICE_LEVELS,2,31));var prices=new ArrayList<Long>();
		if(!plain) {
			int chance=depthChance(Math.max(enchant,tempering));if(roll.getAsInt()>=chance)return List.of();
			depth=Math.min(depth,Math.max(1,(depth*chance+99)/100));
		}
		for(int i=0;i<depth;i++) {
			int index=depth==1?0:(int)Math.round(i*(asks.size()-1.0)/(depth-1));prices.add(asks.get(index));
		}
		return prices;
	}

	static long quantity(ItemTemplate t, String category) {
		if (!t.isStackable()) return 1;
		long cap = Math.max(1, Math.min(1000, Math.min(t.getMaxStackCount(), CentralMarketSimulationConfig.MAX_STACK)));
		long activity = Math.max(1, CentralMarketSimulationConfig.POPULATION / 30);
		if (!Set.of("Materials", "Consumables", "Enhancement").contains(category)) activity = Math.max(1, activity / 10);
		long max = Math.min(cap, activity);
		return ThreadLocalRandom.current().nextLong(Math.max(1, max / 3), max + 1);
	}

	static void refresh(Connection c, Map<String,Object> entry, long now) throws SQLException {
		if (!CentralMarketSimulationConfig.ENABLED || CentralMarketSimulationConfig.POPULATION <= 0) return;
		String key = str(entry,"variant");
		Map<String,Object> due = row(c,"SELECT next_refresh FROM central_market_simulation WHERE variant=?",key);
		if (due != null && lng(due,"next_refresh") > now) return;
		ItemTemplate t = DataManager.ITEM_DATA.getItemTemplate((int)lng(entry,"item_id"));
		// Owner 9 Nov: generated quotes (asks and bids) never cover Black Cloud Marketplace goods; players still trade them.
		if (t == null || !eligible(t) || !com.aionemu.gameserver.services.trade.MarketPolicy.generated(t)) {
			update(c,"UPDATE central_market_orders SET state='CANCELLED' WHERE account_id=0 AND variant=? AND state IN ('OPEN','QUEUED')",key);
			update(c,"INSERT INTO central_market_simulation VALUES(?,?) ON DUPLICATE KEY UPDATE next_refresh=VALUES(next_refresh)",key,now+86400000);
			return;
		}
		List<Long> levels = CentralMarketRules.ladder(lng(entry,"base_price"),lng(entry,"floor_price"),lng(entry,"ceiling_price"));
		var random = ThreadLocalRandom.current();
		var existing=rows(c,"SELECT id FROM central_market_orders WHERE account_id=0 AND variant=? AND state IN ('OPEN','QUEUED') ORDER BY id",key);
		update(c,"UPDATE central_market_orders SET state='CANCELLED' WHERE account_id=0 AND variant=? AND state IN ('OPEN','QUEUED')",key);
		var quotes=new ArrayList<Quote>();
		int enchant=(int)lng(entry,"enchant"),tempering=(int)lng(entry,"tempering");
		long cap=bidCeiling(t,enchant,tempering);
		int bidIndex=CentralMarketRules.demandIndex(levels.size(),random.nextInt(10_000),CentralMarketSimulationConfig.LOW_PRICE_DEMAND,CentralMarketSimulationConfig.HIGH_PRICE_DEMAND);
		if(bidIndex>=0) {
			long bid=levels.get(bidIndex);
			if(bid<=cap) quotes.add(new Quote("B",bid,quantity(t,category(t))));
		}
		{
			var asks=supplyPrices(t,key,enchant,tempering,levels,()->random.nextInt(100));
			int depth=asks.size();
			long stock=quantity(t,category(t));
			if(t.isStackable())depth=(int)Math.min(depth,stock);
			// The endpoints and spaced interior prices all remain visible. Quantities vary at each refresh.
			for(int i=0;i<depth;i++) {
				int index=depth==1?0:(int)Math.round(i*(asks.size()-1.0)/(depth-1));
				long count=t.isStackable()?Math.max(1,stock/depth+(i<stock%depth?1:0)):1;
				quotes.add(new Quote("S",asks.get(index),count));
			}
		}
		// Bounded batch; reuse active virtual rows without touching human escrow or filled history.
		try(var insert=c.prepareStatement("INSERT INTO central_market_orders(account_id,variant,side,price,quantity,remaining,state,created_at,available_at) VALUES(0,?,?,?,?,?,'OPEN',?,?)");
			var reuse=c.prepareStatement("UPDATE central_market_orders SET side=?,price=?,quantity=?,remaining=?,state='OPEN',created_at=?,available_at=? WHERE id=? AND account_id=0")) {
			for(int i=0;i<quotes.size();i++) {
				var quote=quotes.get(i);
				if(i<existing.size()) { bind(reuse,quote.side,quote.price,quote.count,quote.count,now,now,lng(existing.get(i),"id"));reuse.addBatch(); }
				else { bind(insert,key,quote.side,quote.price,quote.count,quote.count,now,now);insert.addBatch(); }
			}
			reuse.executeBatch();insert.executeBatch();
		}
		long min = Math.max(5,CentralMarketSimulationConfig.REFRESH_MIN_SECONDS);
		long max = Math.max(min,Math.min(86400,CentralMarketSimulationConfig.REFRESH_MAX_SECONDS));
		min = Math.min(min,max);
		long delay = random.nextLong(min,max+1) * 1000L * 3000 / Math.max(300,Math.min(30000,CentralMarketSimulationConfig.POPULATION));
		update(c,"INSERT INTO central_market_simulation VALUES(?,?) ON DUPLICATE KEY UPDATE next_refresh=VALUES(next_refresh)",key,now+Math.max(5000,delay));
	}

	private record Quote(String side,long price,long count) {}

	static void disable(Connection c) throws SQLException {
		update(c,"UPDATE central_market_orders SET state='CANCELLED' WHERE account_id=0 AND state IN ('OPEN','QUEUED')");
		// Runs at every server start. Leaving these rows behind grew the table by a full market per restart.
		update(c,"DELETE FROM central_market_orders WHERE account_id=0 AND state='CANCELLED' AND id NOT IN (SELECT buy_order FROM central_market_trades) AND id NOT IN (SELECT sell_order FROM central_market_trades)");
		update(c,"DELETE FROM central_market_simulation");
	}
}
