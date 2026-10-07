package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.util.List;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.stats.calc.functions.StatFunction;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.WardrobeRules;

/** Offline native-item regressions; no server, database, packet or ID allocation. */
public final class PlayerBotAppearanceCheck {
 private static int checks;
 private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 private static void field(Object object,String name,Object value)throws Exception {
  for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 private static ItemTemplate template(int id,ItemGroup group,int defense)throws Exception {
  ItemTemplate t=new ItemTemplate(){@Override public List<StatFunction> getModifiers(){return defense==0 ? List.of() : List.of(new StatFunction(StatEnum.PHYSICAL_DEFENSE,defense,false));}};
  field(t,"itemId",id);field(t,"itemGroup",group);field(t,"name","Appearance regression item");field(t,"maxTuneCount",0);return t;
 }
 public static void main(String[] args)throws Exception {
  ItemTemplate skin=template(110100150,ItemGroup.CL_TORSO,0),armor=template(110600017,ItemGroup.PL_TORSO,100),statClothes=template(110000018,ItemGroup.CL_TORSO,234);
  check(PlayerBotAppearance.costume(skin),"Zero-stat costume cannot replace combat armor automatically");
  check(!PlayerBotAppearance.costume(armor),"Normal stat armor retained");
  check(!PlayerBotAppearance.costume(statClothes),"Stat-bearing clothes are not appearance-only");
  check(PlayerBotAppearance.targetEligible(statClothes),"Stat-bearing clothes can receive transmog");
  check(!PlayerBotAppearance.targetEligible(skin),"Statless costume is not offered as combat target");
  check(WardrobeRules.compatible(armor,skin),"Clothing skin fits plate chest");
  check(!WardrobeRules.compatible(template(1,ItemGroup.PL_PANTS,100),skin),"Chest skin cannot replace pants appearance");
  Item equipped=new Item(1999999101,armor,1,false,0);equipped.setEquipmentSlot(ItemSlot.TORSO.getSlotIdMask());equipped.setPersistentState(PersistentState.UPDATED);
  equipped.setEnchantLevel(5);equipped.setPersistentState(PersistentState.UPDATED);
  PlayerBotAppearance.applySkin(equipped,skin);
  check(equipped.getPersistentState()==PersistentState.UPDATE_REQUIRED,"Stored skin remains pending database update");
  check(equipped.getItemTemplate()==armor && equipped.getItemId()==110600017 && equipped.getEnchantLevel()==5,"Transmog preserves base item and enchantment");
  check(equipped.getEquipmentSlot()==ItemSlot.TORSO.getSlotIdMask(),"Transmog preserves equipped slot");
  check(equipped.getItemSkinTemplate()==skin,"Native skin installed");
  PlayerBotAppearance.applySkin(equipped,armor);
  check(equipped.getPersistentState()==PersistentState.UPDATE_REQUIRED && !equipped.isSkinnedItem(),"Reset remains pending persistence");
  Item fresh=new Item(1999999102,armor,1,false,0);PlayerBotAppearance.applySkin(fresh,skin);
  check(fresh.getPersistentState()==PersistentState.NEW,"Generated gear keeps its pending insert");
  Item source=new Item(1999999103,skin,1,false,0);
  check(PlayerBotAppearance.sourceEligible(source),"Permanent cube outfit is available");
  source.setEquipmentSlot(ItemSlot.TORSO.getSlotIdMask());field(source,"isEquipped",true);
  check(!PlayerBotAppearance.sourceEligible(source),"Equipped source is unavailable");
  field(source,"isEquipped",false);field(source,"expireTime",1);
  check(!PlayerBotAppearance.sourceEligible(source),"Instance-expiring source is unavailable even with a permanent template");
  System.out.println("OK: "+checks+" native appearance/persistence/stat-preservation checks; no live writes");
 }
}
