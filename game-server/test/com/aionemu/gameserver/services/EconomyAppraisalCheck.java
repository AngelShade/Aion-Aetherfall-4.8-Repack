package com.aionemu.gameserver.services;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.trade.EconomyPrices;

/** Offline audit and real-template assertions. Run from game-server against normal builder classes. */
public final class EconomyAppraisalCheck {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static ItemTemplate item(int id){return Objects.requireNonNull(DataManager.ITEM_DATA.getItemTemplate(id));}
 private static long ref(int id){return EconomyPrices.reference(item(id));}
 private static long leaf(int id){return EconomyPrices.nativeReference(item(id));}
 private static boolean generic(ItemTemplate t,long value) {
  long l=Math.max(1,t.getLevel());int q=t.getItemQuality()==null?1:t.getItemQuality().getQualityId();
  return t.getPrice()<=5 && EconomyPrices.cashPrice(t)==0 && value==100+l*l*(2+q);
 }
 public static void main(String[] args)throws Exception {
  EconomyStaticData.load();
  var random=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(188051099);
  check(random!=null && !random.isSelectable(),"Real Shugo stone pouch is random");
  long expected=(long)Math.ceil(leaf(166000195)*(.3334+2*.3333+3*.3333));
  check(ref(188051099)==Math.max(EconomyPrices.retailFloor(item(188051099)),expected),"Weighted quantities use the actual 33.34/33.33/33.33 native reward table");
  var select=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(188051091);
  check(select!=null && select.isSelectable(),"Real Frozen Box is selectable");
  long best=select.getSets().getFirst().getItems().stream().mapToLong(i->leaf(i.getItemId())*i.getMinimumCount()).max().orElseThrow();
  check(ref(188051091)==Math.max(EconomyPrices.retailFloor(item(188051091)),Math.max(best,EconomyPrices.cashPrice(item(188051091)))),"Selectable box uses the best option, never the sum of mutually exclusive choices");
  var nested=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(188051360);
  check(nested.getSets().getFirst().getItems().stream().allMatch(i->DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(i.getItemId())!=null),"Real Kobushka bundle contains nested boxes");
  check(ref(188051360)==Math.max(EconomyPrices.retailFloor(item(188051360)),(long)Math.ceil(.5*ref(188051361)+.5*ref(188051362))),"Nested random boxes recurse into actual child appraisals");
  check(ref(188052096)==Math.max(EconomyPrices.retailFloor(item(188052096)),leaf(141000001)+1500000),"Fixed reward branches add up, and Kinah is valued at face value");
  var mixed=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(188052086);double mixedValue=0;
  for(var reward:mixed.getSets().getFirst().getRewards()) {
   double value=0;for(var child:reward.getItems())value+=(child.getMinimumCount()+(double)child.getMaximumCount())/2*(child.getItemId()==182400001?1:DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(child.getItemId())!=null?ref(child.getItemId()):leaf(child.getItemId()));
   mixedValue+=reward.getChance()/100d*value;
  }
  long mixedExpected=Math.max(EconomyPrices.cashPrice(item(188052086)),Math.max(EconomyPrices.retailFloor(item(188052086)),(long)Math.ceil(mixedValue)));
  check(ref(188052086)==mixedExpected,"Random bundles sum their contents and mean quantities, retaining the cash floor: "+ref(188052086)+" != "+mixedExpected);
  for(int id:new int[]{188054700,188054702,188054704}) {
   var child=DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(id).getSets().getFirst().getItems().getFirst();
   check(ref(id)==Math.max(EconomyPrices.retailFloor(item(id)),Math.max(leaf(child.getItemId()),EconomyPrices.cashPrice(item(id)))),"Real permanent mount box uses its content value: "+id);
   check(ref(id)>107,"Level-one mount boxes no longer share the 107 generic value: "+id);
  }
  var counts=new TreeMap<String,long[]>();var changed=new ArrayList<ItemTemplate>();int eligible=0;
  for(var t:DataManager.ITEM_DATA.getItemTemplates())if(CentralMarketService.eligible(t)) {
   eligible++;long before=EconomyPrices.nativeReference(t),after=EconomyPrices.reference(t);String type=CentralMarketBrowse.type(t),category=CentralMarketBrowse.browseCategory(t,type);
   var c=counts.computeIfAbsent(category,k->new long[4]);if(generic(t,before))c[0]++;
   if(generic(t,after) && !EconomyPrices.contentsAppraised(t) && !EconomyPrices.sourceAppraised(t))c[1]++;
   if(EconomyPrices.contentsAppraised(t))c[2]++;if(EconomyPrices.sourceAppraised(t))c[3]++;
   check(after>=EconomyPrices.resale(t) && after>=EconomyPrices.retailFloor(t),"No eligible appraisal crosses a native resale/native-price floor: "+t.getTemplateId());
   if(after!=before)changed.add(t);
  }
  check(counts.values().stream().mapToLong(c->c[2]).sum()>100,"Audit covers real eligible decomposable items");
  check(counts.values().stream().mapToLong(c->c[3]).sum()>100,"Real unpriced contents acquire a box-source appraisal");
  check(ref(169620062)<1_000_000 && ref(190020222)<1_000_000,"A rare box drop is not priced as the whole box divided by its chance: "+ref(169620062)+", "+ref(190020222));
  // Economy audit, 9 Nov 2026.
  check(EconomyPrices.replacement(item(167000543))>=3500,"A shop whose NPC never spawns no longer caps a level 60 manastone at 13 Kinah: "+EconomyPrices.replacement(item(167000543)));
  check(ref(170040000)>1_000_000,"Craft-only decor is worth at least its NPC materials, not 105 Kinah: "+ref(170040000));
  check(ref(152012591)==leaf(152012591),"A droppable material keeps its supply price despite an expensive alternative recipe: "+ref(152012591));
  check(!CentralMarketService.eligible(item(170170047)) && !ItemEligibility.isPlayerItem(item(190010003)) && !ItemEligibility.isPlayerItem(item(188052083)),"Untranslated placeholders, Unused and GM items are not market goods");
  System.out.println("Eligible items: "+eligible+"; category: generic before, generic after, contents boxes, box-sourced contents");
  counts.forEach((name,c)->System.out.println(name+": "+Arrays.toString(c)));
  changed.sort(Comparator.<ItemTemplate>comparingLong(t->Math.abs(EconomyPrices.reference(t)-EconomyPrices.nativeReference(t))).reversed().thenComparingInt(ItemTemplate::getTemplateId));
  System.out.println("Ten largest changes:");for(var t:changed.subList(0,Math.min(10,changed.size())))System.out.println(t.getTemplateId()+" "+t.getName()+": "+EconomyPrices.nativeReference(t)+" -> "+EconomyPrices.reference(t));
  System.out.println("OK: "+checks+" native box/content appraisal and resale-floor checks");
 }
}
