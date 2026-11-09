package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotMarketUpgrades.*;

/** Phase 3: world bots shop for gear they can wear now. Pure ranking and retry rules plus wiring; no World/SQL/tasks. */
public final class PlayerBotMarketUpgradesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  ranking();
  retries();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" market upgrade checks; no World/SQL/tasks");
 }

 static List<Integer> items(List<Pick> picks){return picks.stream().map(Pick::item).toList();}
 static void ranking() {
  var picks=List.of(new Pick(13,1,2.0),new Pick(11,1,4.0),new Pick(10,1,5.0),new Pick(12,1,3.0),new Pick(21,2,1.5),new Pick(20,2,6.0));
  check(items(spread(picks,3)).equals(List.of(20,10,11,21,12)),"Every slot's best comes first, then each slot's next best: "+items(spread(picks,3)));
  check(items(spread(picks,1)).equals(List.of(20,10)),"One per slot keeps only each slot's best");
  check(!items(spread(picks,3)).contains(13),"A slot keeps at most its few best candidates");
  var ring=List.of(new Pick(30,64,2.5),new Pick(30,128,2.5),new Pick(31,128,2.0));
  check(items(spread(ring,3)).equals(List.of(30,31)),"An item that fits two slots (rings, earrings) is listed once");
  check(items(spread(List.of(new Pick(41,4,3.0),new Pick(40,8,3.0)),3)).equals(List.of(40,41)),"Equal gains keep template order");
  check(spread(List.of(),3).isEmpty(),"No upgrade, nothing to shop for");
  check(LEVEL_SPAN>=5 && LEVEL_SPAN<=15,"Only items near the bot's level are scored");
  check(PER_SLOT>=2,"An unlisted favourite does not hide the slot's next candidate");
  check(CACHE_MS>=10*60*1000L && CACHE_MS<=60*60*1000L,"Picks are cached for minutes, like upgrade hunts, and recomputed when gear changes");
  check(!market(null),"No template, no market item");
 }

 static void retries() {
  long now=100*3600000L,six=6*3600000L;
  check(fresh(Map.of(),7,now,six),"An item never tried is priced");
  check(!fresh(Map.of(7,now-3600000L),7,now,six),"An item tried an hour ago waits");
  check(fresh(Map.of(7,now-six),7,now,six),"After the retry window it is priced again");
  check(fresh(Map.of(8,now),7,now,six),"Another item's attempt does not block this one");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var market=Files.readString(src.resolve("PlayerBotMarket.java"));
  var upgrades=Files.readString(src.resolve("PlayerBotMarketUpgrades.java"));
  var policy=Files.readString(src.resolve("PlayerBotGearPolicy.java"));
  var manager=Files.readString(src.resolve("PlayerBotPopulationManager.java"));
  var economy=Files.readString(root.resolve("src/com/aionemu/gameserver/configs/main/PlayerBotEconomyConfig.java"));
  var config=Files.readString(root.resolve("config/playerbots/economy.properties"));
  check(market.contains("if(!PlayerBotMarketUpgrades.enabled() || !session.autonomous())return List.of();"),"Only world bots shop for upgrades; companions keep their owner's goals and budget");
  check(market.contains("planned.addAll(upgradeNeeds(session,ledger,planned));"),"Upgrades come after goals, stones and trade inputs, with their own per-visit limit");
  check(market.contains("if(need.upgrade())PlayerBotMarketUpgrades.tried(bot,need.item(),System.currentTimeMillis());"),"Each candidate is marked before pricing, so it is not re-quoted every visit");
  check(market.contains("boolean post=resting<=0 && !need.equipment() && session.autonomous();"),"Gear is bought only from a live fair ask, never as a resting buy order");
  check(market.contains("if(need.upgrade())PlayerBotMarketUpgrades.bought(session);") && market.contains("PlayerBotMarketUpgrades.stats()"),"Purchases are counted and reported with the market line");
  check(market.contains("for(var order:ledger.orders())if(order.side().equals(\"B\") && (order.live() || order.uncollected()>0))skip.add(order.itemId());"),"Nothing already ordered or awaiting collection is bought twice");
  check(upgrades.contains("!PlayerBotLoadouts.listed(t,key,level) || !PlayerBotGearPolicy.eligible(bot,t) || !PlayerBotGearPolicy.allowedWeapon(t,settings,role)")
   && upgrades.contains("PlayerBotLoadouts.fits(t,slot,key,weapon)") && upgrades.contains("PlayerBotGearPolicy.gain(bot,t,score,mask,role)") && upgrades.contains("if(best>1)"),
   "Candidates use the same admission and gain as upgrade hunts and equipping");
  check(policy.contains("!eligible(bot,t) || !allowedWeapon(t,s,role)") && policy.contains("if(gain>1)result.add(new PlayerBotEquipment.Upgrade(item,slot,gain));"),
   "Equipping accepts exactly what was bought (same eligibility, weapon rule and threshold), so it is worn, not sold as surplus");
  check(upgrades.contains("bot.getInventory().getItemCountByItemId(p.item())>0 || !bot.getEquipment().getEquippedItemsByItemId(p.item()).isEmpty()"),"Held or worn items are not bought again");
  check(upgrades.contains("worker.setPriority(Thread.MIN_PRIORITY)") && upgrades.contains("private record Inputs(long catalogue,"),"The template scan runs off the decision thread and caches compare a build id, not the catalogue");
  check(upgrades.contains("PICKER.execute(()->{") && upgrades.contains("private static void compute(Player bot,Catalogue ready,"),"A pick is computed on the low-priority worker, never on the decision thread (live: avg 19.6 ms, worst 110 ms there)");
  check(upgrades.contains("if(QUEUED.add(id)) {") && upgrades.contains("finally{QUEUED.remove(id);}") && upgrades.contains("return cached==null?List.of():cached.picks();"),"One queued pick per bot; meanwhile the previous picks (filtered on use) or none");
  check(manager.contains("PlayerBotMarketUpgrades.forget(id);"),"A closed bot's picks and attempts are dropped");
  for(var key:List.of("upgrades","upgrades_per_visit","upgrade_retry_hours")) {
   check(economy.contains("\"gameserver.playerbots.market."+key+"\""),"Compiled setting "+key);
   check(config.contains("gameserver.playerbots.market."+key+" = "),"Documented setting "+key);
  }
 }
 private PlayerBotMarketUpgradesCheck(){}
}
