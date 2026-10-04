package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;

/** Exercise the actual panel inventory mapper with every native item group. */
public final class PlayerBotInventoryCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Method mapper=PlayerBotSession.class.getDeclaredMethod("lambda$snapshot$0",Item.class);mapper.setAccessible(true);
  List<Map<?,?>> inventory=new ArrayList<>();
  int id=1,ordinary=0,equipment=0;
  for(ItemGroup group:ItemGroup.values()) {
   ItemTemplate template=new ItemTemplate(){
    @Override public long getItemSlot(){return group.getValidEquipmentSlots();}
    @Override public String getName(){return group.name();}
    @Override public int getTemplateId(){return 100000+group.ordinal();}
   };
   for(boolean equipped:new boolean[]{false,true}) {
    // Only native equipment groups can legitimately be equipped.
    if(equipped && template.getItemSlot()==0)continue;
    Item item=new Item(id++,template,7,equipped,equipped ? Long.lowestOneBit(template.getItemSlot()) : 0);
    Map<?,?> row;
    try{row=(Map<?,?>)mapper.invoke(null,item);}
    catch(InvocationTargetException error){throw new AssertionError("Panel cannot serialize "+group+" equipped="+equipped,error.getCause());}
    List<String> expected=template.getItemSlot()==0 ? List.of() : Arrays.stream(ItemSlot.getSlotsFor(template.getItemSlot())).map(Enum::name).toList();
    check(row.get("slots").equals(expected),"Wrong equipment choices for "+group);
    check(row.get("id").equals(item.getObjectId()) && row.get("itemId").equals(item.getItemId()) && row.get("name").equals(group.name())
     && row.get("count").equals(7L) && row.get("equipped").equals(equipped),"Inventory metadata lost for "+group);
    inventory.add(row);
    if(template.getItemSlot()==0)ordinary++;else equipment++;
   }
  }
  check(ordinary>0 && equipment>0,"Exercise both ordinary items and equipment");
  var decoded=JSON.parseObject(JSON.toJSONString(Map.of("inventory",inventory)));
  check(decoded.getJSONArray("inventory").size()==inventory.size(),"JSON response must retain every inventory item");
  try{ItemSlot.getSlotsFor(0);throw new AssertionError("Native slot validation was weakened");}
  catch(IllegalArgumentException expected){check(true,"Native slot validation remains strict");}
  System.out.println("OK: "+checks+" panel inventory checks; "+ordinary+" ordinary items and "+equipment+" equipment rows, including unequipped/multiple-slot gear and JSON serialization");
 }
}
