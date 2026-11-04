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
	private CentralMarketSimulation() {}

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
		if (t == null || !eligible(t)) {
			update(c,"UPDATE central_market_orders SET state='CANCELLED' WHERE account_id=0 AND variant=? AND state IN ('OPEN','QUEUED')",key);
			update(c,"INSERT INTO central_market_simulation VALUES(?,?) ON DUPLICATE KEY UPDATE next_refresh=VALUES(next_refresh)",key,now+86400000);
			return;
		}
		List<Long> levels = CentralMarketRules.ladder(lng(entry,"base_price"),lng(entry,"floor_price"),lng(entry,"ceiling_price"));
		var random = ThreadLocalRandom.current();
		var existing=rows(c,"SELECT id FROM central_market_orders WHERE account_id=0 AND variant=? AND state IN ('OPEN','QUEUED') ORDER BY id",key);
		update(c,"UPDATE central_market_orders SET state='CANCELLED' WHERE account_id=0 AND variant=? AND state IN ('OPEN','QUEUED')",key);
		var quotes=new ArrayList<Quote>();
		boolean plain=key.equals(t.getTemplateId()+":0:0");
		long cap=EconomyPrices.demandCeiling(t);
		// Modified gear retains the existing enchant/tempering valuation; never invent its supply.
		if(!plain) cap=Math.min(CentralMarketRules.MAX_KINAH,cap*(1L+lng(entry,"enchant")*lng(entry,"enchant")+2*lng(entry,"tempering")*lng(entry,"tempering")));
		int bidIndex=CentralMarketRules.demandIndex(levels.size(),random.nextInt(10_000),CentralMarketSimulationConfig.LOW_PRICE_DEMAND,CentralMarketSimulationConfig.HIGH_PRICE_DEMAND);
		if(bidIndex>=0) {
			long bid=levels.get(bidIndex);
			if(bid<=cap) quotes.add(new Quote("B",bid,quantity(t,category(t))));
		}
		if(plain && (t.getAcquisition()==null || t.getAcquisition().getRequiredAp()==0)) {
			var asks=levels.stream().filter(p->p>=EconomyPrices.generatedFloor(t)).toList();
			int depth=Math.min(asks.size(),Math.clamp(CentralMarketSimulationConfig.PRICE_LEVELS,2,31));
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
		update(c,"DELETE FROM central_market_simulation");
	}
}
