package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.templates.item.*;

/** Native production XML catalog and build choices without server/database or grants. */
public final class PlayerBotSuppliesCatalogCheck {
 static class Bot extends Player {
  PlayerClass pc;int level;Equipment outfit=new Equipment(null);
  Bot(){super(null,null);}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public byte getLevel(){return (byte)level;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public Gender getGender(){return Gender.FEMALE;}
  @Override public Equipment getEquipment(){return outfit;}
 }
 static <T>T read(Class<T> type,Path file)throws Exception{return type.cast(JAXBContext.newInstance(type).createUnmarshaller().unmarshal(file.toFile()));}
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);DataManager.SKILL_DATA=read(SkillData.class,root.resolve("skills/skill_templates.xml"));DataManager.RECIPE_DATA=read(RecipeData.class,root.resolve("recipe/recipe_templates.xml"));DataManager.TRADE_LIST_DATA=read(TradeListData.class,root.resolve("npc_trade_list.xml"));DataManager.GOODSLIST_DATA=read(GoodsListData.class,root.resolve("goodslists/goodslists.xml"));DataManager.ITEM_DATA=read(ItemData.class,root.resolve("items/item_templates.xml"));
  for(int id:new int[]{162000029,160005055,164002168})if(PlayerBotSupplyCatalog.ordinary(DataManager.ITEM_DATA.getItemTemplate(id)))throw new AssertionError("Special/test template admitted to generated stock: "+id);
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);int cases=0;
  for(var pc:PlayerClass.values())for(int level:pc.isStartingClass() ? new int[]{1,9} : new int[]{20,39,65}) {
   var bot=(Bot)unsafe.allocateInstance(Bot.class);bot.pc=pc;bot.level=level;bot.outfit=new Equipment(null);var best=new TreeMap<String,ItemTemplate>();
   for(var t:DataManager.ITEM_DATA.getItemTemplates()) {
    if(!PlayerBotSupplyCatalog.ordinary(t) || t.getItemType()!=ItemType.NORMAL || t.getItemSlot()!=0 || t.getAcquisition()!=null || t.hasAreaRestriction() || t.hasWorldRestrictions()
     || t.getExpireTime()!=0 || t.getExtraInventoryId()>0 || t.getLevel()>level || t.getUseLimits().getMinRank()>1 || t.getUseLimits().getGuildLevelPermitted()>0
     || t.getActions()==null || t.getActions().getSkillUseAction()==null || t.getActions().getSkillUseAction().getMapId()!=0)continue;
    String category=PlayerBotSupplies.category(bot,t);if(category.isEmpty())continue;var previous=best.get(category);
    if(previous==null || PlayerBotSupplies.potency(bot,t)>PlayerBotSupplies.potency(bot,previous))best.put(category,t);
   }
   if(level>=20 && (!best.containsKey("hp") || !best.containsKey("mp") || best.keySet().stream().noneMatch(c->c.startsWith("buff:"))))throw new AssertionError("Missing ordinary stock: "+pc+" level "+level+" "+best.keySet());
   if(level>=20 && (!best.containsKey("hp-regen") || !best.containsKey("mp-regen")))throw new AssertionError("Missing native regeneration stocks: "+pc+" "+best.keySet());
   if(level==39)System.out.println("OK: "+pc+" ordinary native build choices "+best.entrySet().stream().map(e->e.getKey()+"="+e.getValue().getName()).toList());cases++;
  }
  System.out.println("OK: "+cases+" native class/level catalog cases; test/full-recovery/event templates excluded from generated stock; no database or live actors.");
 }
}


