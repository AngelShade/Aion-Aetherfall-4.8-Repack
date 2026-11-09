package com.aionemu.gameserver.services.trade;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.services.CentralMarketRules;
import com.aionemu.gameserver.services.ItemEligibility;

/** Immutable static-data index. Forward contents first, then cheapest acquisition per expected unit. */
final class BoxAppraisal {
 private record Index(Map<Integer,Long> boxes,Map<Integer,Long> contents) {}
 private static volatile Index index;
 private static final int DEPTH=16;
 private BoxAppraisal() {}
 static void invalidate(){index=null;}
 static long reference(ItemTemplate t){var i=index();return i.boxes().getOrDefault(t.getTemplateId(),i.contents().getOrDefault(t.getTemplateId(),EconomyPrices.nativeReference(t)));}
 static boolean isBox(int id){return index().boxes().containsKey(id);}
 static boolean isContent(int id){return index().contents().containsKey(id);}
 private static boolean sourceEligible(ItemTemplate t){return t.getPrice()<=5 && EconomyPrices.cashPrice(t)==0 && ItemEligibility.isPlayerItem(t)
  && !ItemEligibility.isAppearanceOnly(t) && (t.getAcquisition()==null || t.getAcquisition().getRequiredAp()==0);}
 private static Index index() {
  var found=index;if(found!=null)return found;
  synchronized(BoxAppraisal.class) {
   if(index!=null)return index;
   if(DataManager.DECOMPOSABLE_ITEMS_DATA==null)return new Index(Map.of(),Map.of());
   var boxes=new TreeMap<Integer,Long>();var contents=new TreeMap<Integer,Long>();
   var templates=DataManager.ITEM_DATA.getItemTemplates().stream().sorted(Comparator.comparingInt(ItemTemplate::getTemplateId)).toList();
   for(var t:templates)if(DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(t.getTemplateId())!=null)value(t,boxes,new HashSet<>(),0,Map.of());
   for(var t:templates) {
    var info=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(t.getTemplateId());if(info==null)continue;
    var counts=counts(info);
    for(var e:counts.entrySet()) {
     var child=DataManager.ITEM_DATA.getItemTemplate(e.getKey());
     // Only a guaranteed yield attributes the box's value to one content. A rare drop shares the box with every other
     // reward, and dividing the whole box by a 0.3% chance priced charms and eggs in the tens of millions.
     if(child==null || child.isKinah() || boxes.containsKey(e.getKey()) || !sourceEligible(child) || e.getValue()<1)continue;
     long cost=clamp(boxes.getOrDefault(t.getTemplateId(),EconomyPrices.nativeReference(t))/e.getValue());
     contents.merge(e.getKey(),Math.max(EconomyPrices.retailFloor(child),cost),Math::min);
    }
   }
   // Second pass (owner 9 Nov): a box's contents count at the value a guaranteed source gives them. Otherwise a random
   // pack holding the cabinets of a cash-shop bundle priced them at their token native value and sold for ~100 Kinah.
   // Pass-one contents are fixed here, so this pass cannot feed back into itself.
   var known=Map.copyOf(contents);var revalued=new TreeMap<Integer,Long>();
   for(var t:templates)if(DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(t.getTemplateId())!=null)value(t,revalued,new HashSet<>(),0,known);
   index=new Index(Map.copyOf(revalued),known);return index;
  }
 }
 private static long clamp(double value){return (long)Math.max(1,Math.min(CentralMarketRules.MAX_KINAH,Math.ceil(value)));}
 private static long value(ItemTemplate t,Map<Integer,Long> memo,Set<Integer> visiting,int depth,Map<Integer,Long> known) {
  if(t.isKinah())return 1;
  int id=t.getTemplateId();long base=known.getOrDefault(id,EconomyPrices.nativeReference(t));
  if(memo.containsKey(id))return memo.get(id);
  var info=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(id);
  if(info==null || depth>=DEPTH || !visiting.add(id))return base;
  double best=0;
  for(var profile:profiles(info)) {
   double total=0,remaining=1;
   for(var set:info.getSets()) {
    if(!set.isApplicable(profile.race(),profile.pc(),profile.level()))continue;
    double branch=0;
    if(info.isSelectable()) {
     for(var reward:set.getRewards())for(var child:reward.getItems()) {
      var template=DataManager.ITEM_DATA.getItemTemplate(child.getItemId());if(template!=null)branch=Math.max(branch,mean(child)*value(template,memo,visiting,depth+1,known));
     }
     total=branch;break; // native selection uses the first applicable set
    }
    double roll=0;
    for(var reward:set.getRewards()) {
     double probability=Math.max(0,Math.min(1-roll,Math.round(reward.getChance()*100)/10000d));roll+=probability;
     double bundle=0;
     for(var child:reward.getItems()) {var template=DataManager.ITEM_DATA.getItemTemplate(child.getItemId());if(template!=null)bundle+=mean(child)*value(template,memo,visiting,depth+1,known);}
     branch+=probability*bundle;
    }
    double chance=Math.clamp(Math.round(set.getChance()*10)/1000d,0,1);
    total+=remaining*chance*branch;if(info.isOnlyOne())remaining*=1-chance;
   }
   best=Math.max(best,total);
  }
  visiting.remove(id);long result=Math.max(EconomyPrices.retailFloor(t),clamp(best));
  // A cash-shop price remains an acquisition anchor even when contents are inexpensive.
  result=Math.max(result,EconomyPrices.cashPrice(t));memo.put(id,result);return result;
 }
 private record Profile(Race race,PlayerClass pc,int level) {}
 private static List<Profile> profiles(DecomposableItemInfo info) {
  var levels=new TreeSet<Integer>();levels.add(1);for(var s:info.getSets())levels.add(s.getMinimumLevel());
  var profiles=new ArrayList<Profile>();for(var race:List.of(Race.ELYOS,Race.ASMODIANS))for(var pc:PlayerClass.values())for(int level:levels)profiles.add(new Profile(race,pc,level));return profiles;
 }
 private static double mean(DecomposedItem i){return (i.getMinimumCount()+(double)i.getMaximumCount())/2;}
 /** Maximum yield of a particular item for a legal opener; selectable choices count only that choice. */
 private static Map<Integer,Double> counts(DecomposableItemInfo info) {
  var result=new HashMap<Integer,Double>();
  for(var p:profiles(info)) {
   var totals=new HashMap<Integer,Double>();double remaining=1;
   for(var s:info.getSets()) {
    if(!s.isApplicable(p.race(),p.pc(),p.level()))continue;
    double chance=Math.clamp(Math.round(s.getChance()*10)/1000d,0,1),roll=0;
    for(var reward:s.getRewards()) {
     double probability=info.isSelectable()?1:Math.max(0,Math.min(1-roll,Math.round(reward.getChance()*100)/10000d));roll+=probability;
     for(var child:reward.getItems())totals.merge(child.getItemId(),mean(child)*(info.isSelectable()?1:remaining*chance*probability),info.isSelectable()?Math::max:Double::sum);
    }
    if(info.isSelectable())break;if(info.isOnlyOne())remaining*=1-chance;
   }
   totals.forEach((id,count)->result.merge(id,count,Math::max));
  }
  return result;
 }
}
