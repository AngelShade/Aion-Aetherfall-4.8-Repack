package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import sun.misc.Unsafe;

/** Pure pricing and spending boundaries; no actors, database, native ID allocation or game lifecycle. */
public final class PlayerBotEconomyCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void refused(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  PlayerBotGearPolicy.validateLoadouts(java.util.Map.of("account","-1000000","character","5","goalsFor","world.60","goal.TORSO","110000001:npc:212345"));
  check(true,"World bot goals are valid stored loadout data");
  PlayerBotEconomyConfig.HIRING_MINIMUM_KINAH=1000;PlayerBotEconomyConfig.HIRING_LEVEL_KINAH=50;
  PlayerBotEconomyConfig.HIRING_RANK_PERCENT="100,110,125,150,200";
  check(PlayerBotHiring.price(1,0)==1000,"Tutorial term retains its minimum");
  check(PlayerBotHiring.price(10,0)==5000,"Level ten term scales to native progression");
  check(PlayerBotHiring.price(30,0)==45000,"Level thirty base price");
  check(PlayerBotHiring.price(65,0)==211250 && PlayerBotHiring.price(65,4)==422500,"Late-game term has a meaningful rank premium");
  check(PlayerBotHiring.price(65,-1)==211250,"Unranked world helpers use ordinary base terms");
  refused(()->PlayerBotHiring.price(66,0),"Invalid level cannot produce a quote");
  refused(()->PlayerBotHiring.price(65,5),"Invalid rank cannot produce a quote");
  // Steel Sword 100000189: native price 12,150, ordinary sell reward 2,430.
  check(PlayerBotWorldTrade.quote(2430,11542,1,true)==11542,"A useful real item is sold above its native replacement/resale floor");
  check(PlayerBotWorldTrade.quote(2430,11542,3,false)==7290,"A bot buying three real units pays ordinary native vendor value only");
  check(PlayerBotWorldTrade.quote(2430,3000,1,true)==4860,"The existing double-vendor asking minimum remains");
  check(PlayerBotWorldTrade.quote(3359128,35690743,1,true)>28552594,"Specialist resale cannot exceed a bot's asking price if that equipment becomes surplus-eligible");
  refused(()->PlayerBotWorldTrade.quote(5,10,0,true),"Zero count cannot produce a quote");
  refused(()->PlayerBotWorldTrade.quote(5,Long.MAX_VALUE,2,true),"Retail stack overflow is refused");
  PlayerBotEconomyConfig.PASS_RESERVE_KINAH=200000;PlayerBotEconomyConfig.PASS_BUDGET_PERCENT=25;
  check(PlayerBotPass.worldAllowance(65,6200000,10000)==1500000,"A tier needs six million spendable Kinah while retaining the level-65 reserve");
  check(PlayerBotPass.worldAllowance(65,6199999,10000)<1500000,"One Kinah below the affordability boundary refuses the tier");
  check(PlayerBotPass.worldAllowance(1,10000,10000)==0,"The saved care reserve protects a new bot's supplies");
  check(PlayerBotPass.worldAllowance(65,8000000,3000000)==1250000,"A higher saved reserve takes precedence over automatic Pass spending");
  check(PlayerBotPass.worldAllowance(30,6042603,10000)==1500000,"Pass reserve scales to level rather than using one flat wallet floor");
  check(PlayerBotPass.worldAllowance(65,Long.MAX_VALUE,0)>0,"Large native balances do not overflow percentage arithmetic");
  check(PlayerBotSupplies.affordableCount(450,20,50000)==20,"Twenty Lesser Life Potions cost 9,000 Kinah rather than appearing free");
  check(PlayerBotSupplies.affordableCount(1500,20,10000)==6,"Supply refills shrink to what the bot can actually afford");
  check(PlayerBotSupplies.affordableCount(1500,20,1499)==0,"A refill cannot breach reserve or remaining daily budget");
  check(PlayerBotSupplies.affordableCount(Long.MAX_VALUE,20,Long.MAX_VALUE)==1,"Supply affordability does not overflow on a stack");
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  Item money=(Item)((Unsafe)field.get(null)).allocateInstance(Item.class);money.setPersistentState(PersistentState.UPDATED);
  PlayerBotSupplies.supplyBalance(money,41000);
  check(money.getItemCount()==41000 && money.getPersistentState()==PersistentState.UPDATE_REQUIRED,"Paid refill leaves the actual native wallet dirty so its debit is checkpointed");
  money.setPersistentState(PersistentState.NEW);PlayerBotSupplies.supplyBalance(money,32000);
  check(money.getPersistentState()==PersistentState.NEW,"A newly created wallet retains native insert semantics");
  System.out.println("OK: "+checks+" Playerbots hiring, trade and Pass economy checks; no actors or database");
 }
 private PlayerBotEconomyCheck(){}
}
