package com.aionemu.gameserver.services.trade;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.RatesConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.enchants.EnchantmentStone;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.actions.TamperingAction;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.CentralMarketRules;
import com.aionemu.gameserver.services.EnchantService;
import com.aionemu.gameserver.services.ItemEligibility;

/** Expected cost to reach at least a level, including critical jumps, lost levels and destroyed gear. */
final class EnhancementAppraisal {
 private record Stone(EnchantmentStone grade,long price) {}
 private record Chances(float base,float amplified,float tempering) {}
 private static volatile List<Stone> stones;
 private static final Map<String,long[]> ENCHANT=new ConcurrentHashMap<>(),TEMPER=new ConcurrentHashMap<>();
 private EnhancementAppraisal() {}
 static void invalidate(){stones=null;ENCHANT.clear();TEMPER.clear();}
 static long clamp(double cost){return (long)Math.max(0,Math.min(CentralMarketRules.MAX_KINAH,Math.ceil(cost)));}
 static float rate(float[] rates){return rates==null || rates.length==0?65:rates[0];}
 private static List<Stone> stones() {
  var found=stones;if(found!=null)return found;
  synchronized(EnhancementAppraisal.class) {
   if(stones!=null)return stones;
   var prices=new EnumMap<EnchantmentStone,Long>(EnchantmentStone.class);
   for(var t:DataManager.ITEM_DATA.getItemTemplates())if(t.getItemGroup()==ItemGroup.ENCHANTMENT && ItemEligibility.isPlayerItem(t)
    && t.getExpireTime()==0 && t.getActions()!=null && t.getActions().getEnchantAction()!=null && (t.getAcquisition()==null || t.getAcquisition().getRequiredAp()==0)) {
    try{prices.merge(EnchantmentStone.getByItemId(t.getTemplateId()),EconomyPrices.anchor(t),Math::min);}catch(IllegalArgumentException ignored){}
   }
   stones=prices.entrySet().stream().map(e->new Stone(e.getKey(),e.getValue())).toList();return stones;
  }
 }
 static long enhanced(ItemTemplate t,int enchant,int tempering) {
  return enhanced(t,enchant,tempering,rate(RatesConfig.ENCHANTMENT_STONE_BASE_CHANCES),rate(RatesConfig.ENCHANTMENT_STONE_AMPLIFIED_CHANCES),rate(RatesConfig.TEMPERING_CHANCES));
 }
 static long enhanced(ItemTemplate t,int enchant,int tempering,float baseChance,float amplifiedChance,float temperingChance) {
  var chances=new Chances(baseChance,amplifiedChance,temperingChance);
  long plain=Math.min(CentralMarketRules.MAX_KINAH,EconomyPrices.anchor(t));
  int e=t.isNoEnchant()?0:Math.clamp(enchant,0,Math.min(255,t.getMaxEnchantLevel()));
  int temp=Math.clamp(tempering,0,Math.min(255,t.getMaxTampering()));
  double cost=plain;
  if(e>0) {
   boolean amplified=t.getEnchantType()==1;long replacement=t.getEnchantType()>0?plain:0;
   String key=t.getLevel()+":"+t.getItemQuality()+":"+t.getMaxEnchantLevel()+":"+t.getEnchantType()+":"+replacement+":"+chances.base()+":"+chances.amplified();
   long[] values=ENCHANT.computeIfAbsent(key,k->enchantCosts(t,replacement,amplified,chances));cost+=values[e];
  }
  if(temp>0) {
   String key=t.getItemGroup()+":"+t.getMaxTampering()+":"+(t.getItemGroup()==ItemGroup.PLUME?plain:0)+":"+chances.tempering();
   cost+=TEMPER.computeIfAbsent(key,k->temperingCosts(t,plain,chances))[temp];
  }
  return clamp(cost);
 }
 private static double probability(ItemTemplate t,int level,boolean amplified,Stone s,Chances c) {
  return Math.clamp(EnchantService.enchantChance(t,level,amplified,s.grade(),c.base(),c.amplified())/100d,0,1);
 }
 private static long[] enchantCosts(ItemTemplate t,long replacement,boolean amplified,Chances c) {
  int max=Math.min(255,t.getMaxEnchantLevel());long[] result=new long[max+1];
  var candidates=stones().stream().filter(s->amplified?s.grade()==EnchantmentStone.OMEGA:s.grade()!=EnchantmentStone.OMEGA).toList();
  if(candidates.isEmpty())candidates=stones();
  for(int goal=1;goal<=max;goal++) {
   var policy=new Stone[goal];
   for(int level=0;level<goal;level++) {final int l=level;policy[level]=candidates.stream().filter(s->probability(t,l,amplified,s,c)>0).min(Comparator.comparingDouble(s->s.price()/probability(t,l,amplified,s,c))).orElse(null);}
   if(Arrays.stream(policy).anyMatch(Objects::isNull)){result[goal]=CentralMarketRules.MAX_KINAH;continue;}
   double[] values=null;
   for(int iteration=0;iteration<64;iteration++) {
    double[][] matrix=new double[goal][goal+1];
    for(int level=0;level<goal;level++) {
     matrix[level][level]=1;double p=probability(t,level,amplified,policy[level],c);
     matrix[level][goal]=policy[level].price()+(1-p)*replacement;
     int failure=replacement>0?0:EnchantService.enchantFailureLevel(level,max,amplified);
     if(failure<goal)matrix[level][failure]-=1-p;
     for(int addition=1;addition<=3;addition++) {
      double weight=level<max?(addition==1?.9:.05):(addition==1?1:0);
      int next=EnchantService.enchantSuccessLevel(level,max,amplified,addition);
      if(next<goal)matrix[level][next]-=p*weight;
     }
    }
    values=solve(matrix);if(values==null)break;
    boolean changed=false;
    for(int level=0;level<goal;level++) {
     Stone best=policy[level];double bestCost=Double.POSITIVE_INFINITY;
     for(var s:candidates) {
      double p=probability(t,level,amplified,s,c);if(p<=0)continue;
      int failure=replacement>0?0:EnchantService.enchantFailureLevel(level,max,amplified);
      double cost=s.price()+(1-p)*(replacement+(failure<goal?values[failure]:0));
      for(int a=1;a<=3;a++){int next=EnchantService.enchantSuccessLevel(level,max,amplified,a);cost+=p*(level<max?(a==1?.9:.05):(a==1?1:0))*(next<goal?values[next]:0);}
      if(cost<bestCost-0.000001){best=s;bestCost=cost;}
     }
     if(best!=policy[level]){policy[level]=best;changed=true;}
    }
    if(!changed)break;
   }
   result[goal]=Math.max(result[goal-1],values==null?CentralMarketRules.MAX_KINAH:clamp(values[0]));
  }
  return result;
 }
 private static double[] solve(double[][] a) {
  int n=a.length;
  for(int col=0;col<n;col++) {
   int pivot=col;for(int row=col+1;row<n;row++)if(Math.abs(a[row][col])>Math.abs(a[pivot][col]))pivot=row;
   if(Math.abs(a[pivot][col])<1e-12)return null;
   var swap=a[col];a[col]=a[pivot];a[pivot]=swap;double d=a[col][col];for(int j=col;j<=n;j++)a[col][j]/=d;
   for(int row=0;row<n;row++)if(row!=col){double factor=a[row][col];for(int j=col;j<=n;j++)a[row][j]-=factor*a[col][j];}
  }
  double[] result=new double[n];for(int i=0;i<n;i++)result[i]=a[i][n];return result;
 }
 private static long[] temperingCosts(ItemTemplate t,long plain,Chances c) {
  long solution=DataManager.ITEM_DATA.getItemTemplates().stream().filter(s->s.getItemGroup()==ItemGroup.TAMPERING && ItemEligibility.isPlayerItem(s)
   && s.getExpireTime()==0 && s.getActions()!=null && s.getActions().getItemActions().stream().anyMatch(a->a instanceof TamperingAction)
   && (s.getAcquisition()==null || s.getAcquisition().getRequiredAp()==0)).mapToLong(EconomyPrices::anchor).min().orElse(CentralMarketRules.MAX_KINAH);
  long[] result=new long[Math.min(255,t.getMaxTampering())+1];
  for(int level=0;level<result.length-1;level++) {
   double p=Math.clamp(TamperingAction.temperingChance(t.getItemGroup(),level,c.tempering())/100d,0,1);
   result[level+1]=p==0?CentralMarketRules.MAX_KINAH:clamp((result[level]+(double)solution+(1-p)*(t.getItemGroup()==ItemGroup.PLUME?plain:0))/p);
  }
  return result;
 }
}
