package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.services.WardrobeRules;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_UPDATE_PLAYER_APPEARANCE;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** PB-CUSTOM-APPEARANCE-001: owned appearance sources, separate from combat gear. */
public final class PlayerBotAppearance {
 private record Selection(int account, Map<Long,Integer> skins) {}
 private static final Map<Integer,Selection> SELECTED=new ConcurrentHashMap<>();
 public static boolean costume(ItemTemplate t) {
  // CLOTHES/ALL_ARMOR also contain real stat gear (e.g. Forest Denku's Frillycoat).
  return t!=null && WardrobeRules.category(t).equals("Costumes")
   && (t.getModifiers()==null || t.getModifiers().stream().noneMatch(stat->stat.getValue()!=0));
 }
 static boolean targetEligible(ItemTemplate t) { return WardrobeRules.eligible(t) && !costume(t); }
 static boolean sourceEligible(Item item) {
  return item!=null && !item.isEquipped() && item.getExpireTime()==0
   && WardrobeRules.eligible(item.getItemTemplate()) && WardrobeRules.eligible(item.getItemSkinTemplate());
 }
 static void applySkin(Item item,ItemTemplate skin) {
  // The native setter keeps NEW items pending insert and marks stored items UPDATE_REQUIRED.
  item.setItemSkinTemplate(skin);
 }
 private static Path path(PlayerBotSession s) { return Path.of("config/playerbots/appearance-character-"+s.bot().getObjectId()+".properties"); }
 private static Selection selection(PlayerBotSession s) {
  int account=s.owner().getAccount().getId();
  Selection result=SELECTED.computeIfAbsent(s.bot().getObjectId(),id->{
   Map<Long,Integer> skins=new LinkedHashMap<>();Path file=path(s);
   if(Files.exists(file))try(var in=Files.newInputStream(file)) {
    Properties p=new Properties();p.load(in);
    if(Integer.parseInt(p.getProperty("account"))!=account || Integer.parseInt(p.getProperty("character"))!=id)throw new IOException("Appearance owner mismatch");
    for(String key:p.stringPropertyNames())if(key.startsWith("slot.")) {
     long slot=ItemSlot.valueOf(key.substring(5)).getSlotIdMask();int skin=Integer.parseInt(p.getProperty(key));
     if(skin<0)throw new IOException("Invalid appearance");skins.put(slot,skin);
    }
   }catch(Exception error){throw new IllegalStateException("Cannot load companion appearances",error);}
   return new Selection(account,Map.copyOf(skins));
  });
  if(result.account()!=account)throw new IllegalArgumentException("Companion appearance owner mismatch.");return result;
 }
 private static void save(PlayerBotSession s,Map<Long,Integer> skins) {
  Properties p=new Properties();p.setProperty("account",Integer.toString(s.owner().getAccount().getId()));p.setProperty("character",Integer.toString(s.bot().getObjectId()));
  for(var e:skins.entrySet())p.setProperty("slot."+Arrays.stream(ItemSlot.values()).filter(slot->slot.getSlotIdMask()==e.getKey()).findFirst().orElseThrow().name(),Integer.toString(e.getValue()));
  Path file=path(s);
  try {
   Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"companion-appearance-",".tmp");
   try {try(var out=Files.newOutputStream(tmp)){p.store(out,"Companion appearance only; native gear stats retained");}PlayerBotSettingsFiles.replace(tmp,file);}
   finally{Files.deleteIfExists(tmp);}
  }catch(IOException error){throw new IllegalArgumentException("Companion appearance could not be saved.",error);}
  SELECTED.put(s.bot().getObjectId(),new Selection(s.owner().getAccount().getId(),Map.copyOf(skins)));
 }
 private static boolean ready(PlayerBotSession s) {
  return !s.closing() && !s.bot().isDead() && !s.bot().isTrading() && !s.bot().isCasting() && !s.bot().isLooting()
   && !s.bot().getController().hasTask(TaskId.ITEM_USE) && !s.bot().getController().isInCombat() && !s.owner().getController().isInCombat();
 }
 private static Item target(PlayerBotSession s,int object) {
  for(Item item:s.bot().getEquipment().getEquippedItems())if(item.getObjectId()==object && targetEligible(item.getItemTemplate()))return item;
  throw new IllegalArgumentException("Equip your combat armor first, then choose it as the transmog target.");
 }
 public static String configure(PlayerBotSession s,int sourceObject,int targetObject,boolean reset) {
  synchronized(s) {
   if(!ready(s))throw new IllegalArgumentException("Change appearances while your companion is alive and out of combat, after trade or item use finishes.");
   Item target=target(s,targetObject);int skinId=0;
   if(!reset) {
    Item source=s.bot().getInventory().getItemByObjId(sourceObject);
    if(source==null || source.isEquipped() || source==target)throw new IllegalArgumentException("Keep the appearance item in this companion's cube.");
    ItemTemplate skin=source.getItemSkinTemplate();
    if(!sourceEligible(source) || !WardrobeRules.permitted(skin,s.bot().getRace(),s.bot().getGender()) || !WardrobeRules.compatible(target.getItemTemplate(),skin))
     throw new IllegalArgumentException("Choose a permanent appearance compatible with this companion and equipment slot.");
    skinId=skin.getTemplateId();
   }
   Map<Long,Integer> skins=new LinkedHashMap<>(selection(s).skins());
   if(reset)skins.remove(target.getEquipmentSlot());else skins.put(target.getEquipmentSlot(),skinId);
   save(s,skins);
   if(reset) {
    applySkin(target,target.getItemTemplate());ItemPacketService.updateItemAfterInfoChange(s.bot(),target);
    PacketSendUtility.broadcastPacket(s.bot(),new SM_UPDATE_PLAYER_APPEARANCE(s.bot().getObjectId(),s.bot().getEquipment().getEquippedForAppearance()),true);
   } else tick(s);
   return reset ? "Original appearance restored. Combat equipment kept." : "Transmog applied. Combat stats and the appearance item are kept; this slot keeps its look through gear upgrades.";
  }
 }
 static void tick(PlayerBotSession s) {
  if(!ready(s))return;
  Selection selected=selection(s);boolean changed=false;
  for(Item item:s.bot().getEquipment().getEquippedItems()) {
   Integer id=selected.skins().get(item.getEquipmentSlot());if(id==null || !targetEligible(item.getItemTemplate()))continue;
   ItemTemplate skin=id==0 ? item.getItemTemplate() : DataManager.ITEM_DATA.getItemTemplate(id);
   if(skin==null || !WardrobeRules.eligible(skin) || !WardrobeRules.permitted(skin,s.bot().getRace(),s.bot().getGender()) || !WardrobeRules.compatible(item.getItemTemplate(),skin) || item.getItemSkinTemplate().getTemplateId()==skin.getTemplateId())continue;
   applySkin(item,skin);ItemPacketService.updateItemAfterInfoChange(s.bot(),item);changed=true;
  }
  if(changed)PacketSendUtility.broadcastPacket(s.bot(),new SM_UPDATE_PLAYER_APPEARANCE(s.bot().getObjectId(),s.bot().getEquipment().getEquippedForAppearance()),true);
 }
 static Map<String,Object> itemView(PlayerBotSession s,Item item) {
  Map<String,Object> view=new LinkedHashMap<>();long mask=item.getItemTemplate().getItemSlot();
  view.put("id",item.getObjectId());view.put("itemId",item.getItemId());view.put("name",item.getItemName());view.put("count",item.getItemCount());view.put("equipped",item.isEquipped());
  view.put("slots",mask==0 ? List.of() : Arrays.stream(ItemSlot.getSlotsFor(mask)).map(Enum::name).toList());
  view.put("skinName",item.getItemSkinTemplate().getName());
  view.put("appearanceSet",targetEligible(item.getItemTemplate()) && (selection(s).skins().getOrDefault(item.getEquipmentSlot(),0)>0 || item.isSkinnedItem()));
  List<Map<String,Object>> targets=new ArrayList<>();
  if(sourceEligible(item) && WardrobeRules.permitted(item.getItemSkinTemplate(),s.bot().getRace(),s.bot().getGender()))
   for(Item equipped:s.bot().getEquipment().getEquippedItems())if(targetEligible(equipped.getItemTemplate()) && WardrobeRules.compatible(equipped.getItemTemplate(),item.getItemSkinTemplate()))
    targets.add(Map.of("id",equipped.getObjectId(),"name",equipped.getItemName()));
  view.put("appearanceTargets",targets);return view;
 }
 static List<Map<String,Object>> inventory(PlayerBotSession s,List<Map<String,Object>> original) {
  List<Map<String,Object>> result=new ArrayList<>();Map<Integer,Item> items=new HashMap<>();
  for(Item item:s.bot().getEquipment().getEquippedItems())items.put(item.getObjectId(),item);
  for(Item item:s.bot().getInventory().getItems())items.put(item.getObjectId(),item);
  for(var row:original) {Item item=items.get(((Number)row.get("id")).intValue());if(item!=null)result.add(itemView(s,item));}
  return result;
 }
 static void close(PlayerBotSession s){SELECTED.remove(s.bot().getObjectId());}
 private PlayerBotAppearance() {}
}
