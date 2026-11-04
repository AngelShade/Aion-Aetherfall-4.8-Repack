package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.services.CentralMarketRules;
import com.aionemu.gameserver.services.AetherfallBoonService;
import com.aionemu.gameserver.services.CentralMarketService.CompanionPrice;
import com.aionemu.gameserver.services.CentralMarketService.CompanionQuote;

/** Pricing decisions only: no server, database, player inventories or generated rewards. */
public final class PlayerBotMarketPricingCheck {
 private static int checks;
 private static void expect(boolean value,String what){checks++;if(!value)throw new AssertionError(what);}
 private static CompanionPrice price(int owner,long value){return new CompanionPrice(owner,value,10,false);}
 private static CompanionQuote quote(List<CompanionPrice> trades,List<CompanionPrice> asks,List<CompanionPrice> bids){return new CompanionQuote(1000,500,2000,trades,asks,bids);}
 public static void main(String[] args) {
  expect(PlayerBotMarket.reserve(1,50000)==1000 && PlayerBotMarket.reserve(10,50000)==1183,"Early progression does not inherit a level-65 reserve");
  expect(PlayerBotMarket.reserve(30,50000)==10650 && PlayerBotMarket.reserve(65,50000)==50000,"Reserve scales to the configured level-65 reference");
  expect(PlayerBotMarket.reserve(99,50000)==50000 && PlayerBotMarket.reserve(-1,-1)==1000,"Reserve clamps invalid level/settings safely");
  expect(PlayerBotMarket.budget(150001,50000,25,100000)==25000,"Percentage budget keeps fractional hundreds without exceeding spare funds");
  expect(PlayerBotMarket.budget(150000,50000,25,9000)==9000,"Existing Care daily allowance remains authoritative");
  expect(PlayerBotMarket.budget(40000,50000,25,50000)==0 && PlayerBotMarket.budget(150000,50000,0,50000)==0,"Reserve and disabled spending are respected");

  var inflated=List.of(price(10,2000),price(10,1900),price(10,1800));
  expect(PlayerBotMarket.median(inflated)==0,"Repeated orders by one account are not independent price evidence");
  expect(PlayerBotMarket.median(List.of(price(10,900),price(20,1000),price(30,1100)))==1000,"Independent parties use the median rather than trade volume");
  expect(PlayerBotMarket.median(List.of(price(10,900),price(20,1000),new CompanionPrice(0,2000,1000000,true)))==0,"Virtual volume cannot complete the evidence quorum");
  expect(PlayerBotMarket.fair(quote(List.of(),inflated,List.of()),1000)==1000,"One manipulator's asks cannot lift native willingness to pay");
  expect(PlayerBotMarket.fair(quote(List.of(price(10,900),price(20,920),price(30,940)),List.of(),List.of()),1000)==920,"Recent real trades can lower the appraisal");
  expect(PlayerBotMarket.fair(quote(List.of(price(10,999999),price(20,999999),price(30,999999)),List.of(),List.of()),1000)==1250,"Even coordinated trade inflation is bounded by native alternatives");
  expect(PlayerBotMarket.fair(quote(List.of(price(10,1),price(20,1),price(30,1)),List.of(),List.of()),1000)==750,"Wash-priced cheap history cannot collapse the appraisal");

  expect(PlayerBotMarket.purchasePrice(quote(List.of(),List.of(price(10,999)),List.of()),1000)==1000,"Purchases use the next selectable ladder price above the ask");
  expect(PlayerBotMarket.purchasePrice(quote(List.of(),List.of(price(10,1001)),List.of()),1000)==0,"Purchases refuse asks above actual willingness");
  expect(PlayerBotMarket.purchasePrice(quote(List.of(),List.of(new CompanionPrice(0,1000,1,true)),List.of()),950)==0,"Virtual supply cannot bypass the alternative-price cap");
  expect(PlayerBotMarket.purchasePrice(quote(List.of(),List.of(),List.of()),1000)==0,"An empty book does not create speculative buy orders");
  expect(PlayerBotMarket.salePrice(quote(List.of(),List.of(price(10,1000)),List.of()),1000,950)==997,"Surplus undercuts by one selectable price");
  expect(PlayerBotMarket.salePrice(quote(List.of(),List.of(),List.of(price(10,1030))),1000,950)==1030,"A funded fair bid can sell surplus immediately");
  expect(PlayerBotMarket.salePrice(quote(List.of(),List.of(price(10,900)),List.of(price(20,800))),1000,950)==0,"A bad book leaves goods to native vendor/care instead of a loss");
  expect(PlayerBotMarket.salePrice(quote(List.of(),List.of(),List.of()),1000,1050)==0,"A resale floor above the current ladder cannot be undercut");
  for(long supply:List.of(100L,1000L,100000L,1000000000L)) {
   int returned=AetherfallBoonService.maximumMarketReturn();long virtualBid=supply*10000/returned-1;
   expect(CentralMarketRules.collect(virtualBid,returned)<supply,"Even the best real market return cannot profit from a synthetic supply/demand round trip");
  }
  System.out.println("OK: "+checks+" Playerbot market pricing checks");
 }
}
