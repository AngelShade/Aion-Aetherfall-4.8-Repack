package com.aionemu.gameserver.services;

import java.util.*;
import com.aionemu.gameserver.configs.main.CentralMarketSimulationConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.trade.EconomyPrices;

/** The actual quote generator, with deterministic depth rolls; no market database or assets. */
public final class CentralMarketEnhancedCheck {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  EconomyStaticData.load();
  var weapon=DataManager.ITEM_DATA.getItemTemplate(100000096);
  var one=new ItemTemplate();for(var entry:Map.of("itemId",100000096,"name","One-step native cost check","level",weapon.getLevel(),"price",(int)weapon.getPrice(),"itemQuality",weapon.getItemQuality(),"maxEnchant",1).entrySet()){var f=ItemTemplate.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(one,entry.getValue());}
  double cheapest=Double.POSITIVE_INFINITY;
  for(var stone:DataManager.ITEM_DATA.getItemTemplates())if(stone.getItemGroup()==com.aionemu.gameserver.model.templates.item.enums.ItemGroup.ENCHANTMENT && ItemEligibility.isPlayerItem(stone) && stone.getExpireTime()==0 && stone.getActions()!=null && stone.getActions().getEnchantAction()!=null && (stone.getAcquisition()==null || stone.getAcquisition().getRequiredAp()==0)) {
   try{var grade=com.aionemu.gameserver.model.enchants.EnchantmentStone.getByItemId(stone.getTemplateId());if(grade==com.aionemu.gameserver.model.enchants.EnchantmentStone.OMEGA)continue;
    double chance=Math.clamp(EnchantService.enchantChance(one,0,false,grade,65,61)/100d,0,1);if(chance>0)cheapest=Math.min(cheapest,EconomyPrices.anchor(stone)/chance);
   }catch(IllegalArgumentException ignored){}
  }
  check(EconomyPrices.enhanced(one,1,0)==EconomyPrices.anchor(one)+(long)Math.ceil(cheapest),"Independent one-step geometric expectation equals the exact native production appraisal");
  check(EconomyPrices.enhancementCost(one,1,0,30,61,65)>EconomyPrices.enhancementCost(one,1,0),"A bot's lower actual native chance raises its own expected production alternative");
  check(EconomyPrices.enhancementCost(one,1,0,100,61,65)<=EconomyPrices.enhancementCost(one,1,0),"Improved membership chance cannot raise a bot's expected production cost");
  var accessory=DataManager.ITEM_DATA.getItemTemplates().stream().filter(t->CentralMarketSimulation.supplyEligible(t) && !t.isStackable() && t.getMaxTampering()>=5).min(Comparator.comparingInt(ItemTemplate::getTemplateId)).orElseThrow();
  for(var t:List.of(weapon,accessory)) {
   long plain=EconomyPrices.anchor(t),previous=plain;
   for(var v:CentralMarketSimulation.variants(t)) {
    int e=v[0],temp=v[1];long appraisal=EconomyPrices.enhanced(t,e,temp),cost=EconomyPrices.enhancementCost(t,e,temp);
    check(appraisal>=plain && appraisal>=Math.min(CentralMarketRules.MAX_KINAH,plain+cost),"Enhanced appraisal covers its plain anchor and expected native cost");
    check(appraisal>plain,"Real native production consumes appraised materials");
    if(temp==0){check(appraisal>=previous,"Enchantment appraisal is monotonic");previous=appraisal;}
    var ladder=CentralMarketRules.ladder(appraisal,Math.max(1,appraisal/2),Math.min(CentralMarketRules.MAX_KINAH,appraisal*2));String key=t.getTemplateId()+":"+e+":"+temp;
    var asks=CentralMarketSimulation.supplyPrices(t,key,e,temp,ladder,()->0);
    long fullDepth=Math.min(CentralMarketSimulationConfig.PRICE_LEVELS,ladder.stream().filter(p->p>=appraisal).count());
    check(asks.size()==Math.max(1,(fullDepth*CentralMarketSimulation.depthChance(Math.max(e,temp))+99)/100),"Configured quote depth thins with level when the variant's stock roll passes");
    check(asks.stream().allMatch(p->p>=appraisal && ladder.contains(p)),"Every enhanced ask uses the normal ladder and covers production cost");
    check(CentralMarketSimulation.supplyPrices(t,key,e,temp,ladder,()->100).isEmpty(),"A failed stock roll produces no enhanced supply");
    long ceiling=EconomyPrices.demandCeiling(appraisal);
    check(asks.stream().allMatch(ask->CentralMarketRules.collect(Math.min(CentralMarketRules.MAX_KINAH,ceiling),AetherfallBoonService.maximumMarketReturn())<ask),"Buying any generated enhanced ask then selling to a capped virtual bid loses Kinah even at the highest return");
    long route=Math.min(CentralMarketRules.MAX_KINAH,Math.min(EconomyPrices.replacement(t),EconomyPrices.generatedFloor(t))+cost);
    check(CentralMarketRules.collect(CentralMarketSimulation.bidCeiling(t,e,temp),AetherfallBoonService.maximumMarketReturn())<=route,"Buying generated plain stock and enhancing it cannot profit from a capped virtual bid");
    check(CentralMarketSimulation.supplyPrices(t,key+":custom",e,temp,ladder,()->0).isEmpty(),"Custom attributes never acquire invented stock");
   }
   check(CentralMarketSimulation.supplyPrices(t,t.getTemplateId()+":1:1",1,1,List.of(1000L),()->0).isEmpty(),"Enchant/tempering combinations have no generated supply");
  }
  check(CentralMarketSimulation.depthChance(1)==100 && CentralMarketSimulation.depthChance(3)==85 && CentralMarketSimulation.depthChance(7)==8,"Default low levels are common and high levels rare");
  CentralMarketSimulationConfig.ENHANCED_CHANCE_PER_LEVEL=new int[]{100,0};
  check(CentralMarketSimulation.depthChance(1)==100 && CentralMarketSimulation.depthChance(2)==0 && CentralMarketSimulation.depthChance(20)==0,"Configured level chances and trailing level policy apply");
  CentralMarketSimulationConfig.ENHANCED_MAX_ENCHANT=2;CentralMarketSimulationConfig.ENHANCED_MAX_TEMPERING=1;
  check(CentralMarketSimulation.variants(weapon).stream().allMatch(v->v[0]<=2 && v[1]<=1),"Configured variant maxima cap lazy catalog rows");
  var ap=DataManager.ITEM_DATA.getItemTemplates().stream().filter(CentralMarketService::eligible).filter(t->t.getAcquisition()!=null && t.getAcquisition().getRequiredAp()>0).findFirst().orElseThrow();
  check(CentralMarketSimulation.variants(ap).isEmpty(),"AP acquisition is never bypassed by enhanced generated supply");
  System.out.println("OK: "+checks+" native enhanced appraisal, generated depth, floor and tax-spread checks");
 }
}
