package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.enchants.EnchantmentStone;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.model.stats.container.PlayerGameStats;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.services.*;

/** Actual offscreen bot entry and native roll/outcome/storage on inert actors. No scheduler, SQL or game server. */
public final class PlayerBotEnhanceCheck {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static final class Life extends PlayerLifeStats {Life(){super(null);}@Override public boolean isAboutToDie(){return false;}}
 private static final class Stats extends PlayerGameStats {Stats(){super(null);}@Override public void updateStatsVisually(){}}
 private static final class Outfit extends Equipment {List<Item> contents=List.of();Outfit(Player bot){super(bot);}@Override public List<Item> getEquippedItems(){return contents;}}
 private static final class Bot extends Player {
  PlayerStorage cube;PlayerCommonData common;PlayerEffectController effects;TransformModel transform;Life life;Stats stats;Outfit outfit;PlayerController controller;
  Bot(){super(null,null);}
  @Override public Account getAccount(){return new Account(7);}
  @Override public int getObjectId(){return 7101;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isSpawned(){return true;}
  @Override public boolean isOnline(){return false;}
  @Override public boolean isDead(){return false;}
  @Override public boolean isInPrison(){return false;}
  @Override public boolean isStaff(){return false;}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public PlayerCommonData getCommonData(){return common;}
  @Override public PlayerEffectController getEffectController(){return effects;}
  @Override public TransformModel getTransformModel(){return transform;}
  @Override public PlayerLifeStats getLifeStats(){return life;}
  @Override public PlayerGameStats getGameStats(){return stats;}
  @Override public Equipment getEquipment(){return outfit;}
  @Override public PlayerController getController(){return controller;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.GLADIATOR;}
  @Override public byte getLevel(){return 65;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public Gender getGender(){return Gender.MALE;}
  @Override public boolean hasCooldown(Item item){return false;}
 }
 public static void main(String[] args)throws Exception {
  EconomyStaticData.load();var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var bot=(Bot)unsafe.allocateInstance(Bot.class);bot.cube=new PlayerStorage(bot,StorageType.CUBE);bot.cube.setLimit(30);
  bot.common=new PlayerCommonData(7101);bot.common.setPlayerClass(PlayerClass.GLADIATOR);bot.effects=new PlayerEffectController(bot);bot.transform=new TransformModel(bot);bot.life=(Life)unsafe.allocateInstance(Life.class);
  bot.stats=(Stats)unsafe.allocateInstance(Stats.class);bot.outfit=new Outfit(bot);bot.controller=new PlayerController();bot.controller.setOwner(bot);
  var weapon=new Item(8101,DataManager.ITEM_DATA.getItemTemplate(100000096),1,true,1);
  check(weapon.getItemTemplate().isWeapon() && PlayerBotCare.target(weapon),"An equipped native weapon is an enhancement target");
  weapon.setEnchantLevel(9);check(PlayerBotCare.target(weapon),"The configured +10 target replaces the old +5 cap");
  weapon.setEnchantLevel(10);check(!PlayerBotCare.target(weapon),"Desired level is respected");weapon.setEnchantLevel(0);
  for(int id=166000001;id<=166000201;id++)check(PlayerBotCare.stone(DataManager.ITEM_DATA.getItemTemplate(id),weapon),"Every recognized old/new native stone is admitted, including 196-201: "+id);
  for(int id=166020000;id<=166020003;id++)check(PlayerBotCare.stone(DataManager.ITEM_DATA.getItemTemplate(id),weapon),"Native Omega aliases are recognized: "+id);
  weapon.setAmplified(true);
  check(!PlayerBotCare.stone(DataManager.ITEM_DATA.getItemTemplate(166000201),weapon),"Amplified target requires native Omega");
  check(PlayerBotCare.stone(DataManager.ITEM_DATA.getItemTemplate(166020000),weapon),"Amplified target accepts Omega");weapon.setAmplified(false);
  var normal=new Item(8102,DataManager.ITEM_DATA.getItemTemplate(166000194),4,false,0);var omega=new Item(8103,DataManager.ITEM_DATA.getItemTemplate(166020000),4,false,0);
  bot.cube.onLoadHandler(normal);bot.cube.onLoadHandler(omega);
  check(PlayerBotCare.bestStone(bot,weapon)==normal,"Held ordinary stone is preferred over Omega on an ordinary item");
  var cheap=new Item(8104,DataManager.ITEM_DATA.getItemTemplate(166000001),4,false,0);bot.cube.onLoadHandler(cheap);
  Item selected=PlayerBotCare.bestStone(bot,weapon);
  check(PlayerBotCare.stoneEfficiency(bot,selected.getItemTemplate(),weapon)>=PlayerBotCare.stoneEfficiency(bot,normal.getItemTemplate(),weapon) && PlayerBotCare.stoneEfficiency(bot,selected.getItemTemplate(),weapon)>=PlayerBotCare.stoneEfficiency(bot,cheap.getItemTemplate(),weapon),"Held stone choice maximizes native success chance per appraised Kinah");bot.cube.remove(cheap);
  bot.cube.remove(normal);check(PlayerBotCare.bestStone(bot,weapon)==omega,"Omega is used when it is the only held stone");
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  for(var entry:Map.of("bot",bot,"owner",bot,"worldDormant",true).entrySet()){var field=PlayerBotSession.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(session,entry.getValue());}
  bot.outfit.contents=List.of(weapon);weapon.setEnchantLevel(weapon.getMaxEnchantLevel());weapon.setAmplified(true);
  // Only visual stat presentation is inert; the real background scheduler entry, item action,
  // success roll, native equipped-item outcome and native storage debit all execute.
  DataManager.ENCHANT_DATA=new com.aionemu.gameserver.dataholders.EnchantData();PlayerBotEconomyConfig.ENHANCE_TARGET_LEVEL=20;
  long now=System.currentTimeMillis();long cadence=Math.max(60,PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_INTERVAL_SECONDS)*1000L+1000;
  RatesConfig.ENCHANTMENT_STONE_AMPLIFIED_CHANCES=new float[]{100,100};int level=weapon.getEnchantLevel();
  // Falling asleep schedules the first check one spread interval out, so it never wakes the scheduler at once.
  check(!PlayerBotCare.dormantDue(session,now),"A newly dormant bot waits one spread interval before its first enhancement check");now+=cadence+60000;
  check(PlayerBotCare.dormantDue(session,now),"A dormant autonomous session is admitted through bounded background scheduling");
  check(PlayerBotCare.background(session,now),"Offscreen bot executes the real background enhancement entry point");
  check(omega.getItemCount()==3 && weapon.getEnchantLevel()==level+1,"Native certain amplified success consumes exactly one real stored stone and raises one level");
  RatesConfig.ENCHANTMENT_STONE_AMPLIFIED_CHANCES=new float[]{0,0};
  check(!PlayerBotCare.dormantDue(session,now+1000),"The same dormant bot cannot consume another stone before its background cadence");
  check(PlayerBotCare.background(session,now+cadence),"Offscreen failure still completes a native attempt");
  check(omega.getItemCount()==2 && weapon.getEnchantLevel()==weapon.getMaxEnchantLevel() && !weapon.isAmplified(),"Native failed amplification consumes a stone and removes amplification at the native maximum");
  RatesConfig.ENCHANTMENT_STONE_BASE_CHANCES=new float[]{-200,-200};
  check(!PlayerBotCare.background(session,now+cadence*2),"A native-capped ordinary item is no longer targeted or charged another stone");
  weapon.setEnchantLevel(9);check(PlayerBotCare.background(session,now+cadence*3),"Native zero-chance ordinary background attempt completes");
  check(omega.getItemCount()==1 && weapon.getEnchantLevel()==8,"Ordinary failure consumes one stone and loses a level");
  check(PlayerBotCare.stats().contains("enhance attempts=3, successes=1"),"Actual world attempts and successful native rolls reach worldstats");
  check(EnchantService.enchantFailureLevel(14,15,false)==10 && EnchantService.enchantFailureLevel(10,15,false)==9,"Native high-level and ordinary failure transitions are shared with appraisal");
  check(EnchantService.enchantSuccessLevel(14,15,false,3)==15,"Native critical increase respects the native cap");
  check(PlayerBotMarket.betterEnhanced(50,1100,10,1000,500),"A fairly priced enhanced upgrade can beat plain gear plus own enhancement cost");
  check(!PlayerBotMarket.betterEnhanced(50,1600,10,1000,500),"Enhanced gear above its own production alternative is refused");
  check(!PlayerBotMarket.betterEnhanced(5,1100,10,1000,500),"Poor gear-score gain per Kinah is refused");
  var state=(PlayerBotQuestSync.State)unsafe.allocateInstance(PlayerBotQuestSync.State.class);state.loadEnhance(new Properties());
  check(!state.enchant && state.enhanceSpent==0 && state.enhanceBudget==0,"Existing companion toggle stays off and older saved spending metadata remains readable");
  var saved=new Properties();saved.setProperty("enhanceDay","2026-11-07");saved.setProperty("enhanceBudget","10000");saved.setProperty("enhanceSpent","2500");state.loadEnhance(saved);
  check(state.enhanceDay.equals("2026-11-07") && state.enhanceBudget-state.enhanceSpent==7500,"Durable native Care metadata reloads the remaining daily stone allowance");
  weapon.setEquipped(false);weapon.setEnchantLevel(8);weapon.setAmplified(false);weapon.setTuneCount(0);
  check(PlayerBotCare.disposable(weapon,false,false,false,false),"Unused enhanced gear passes the shared surplus shape guard");
  check(!PlayerBotCare.disposable(weapon,true,false,false,false) && !PlayerBotCare.disposable(weapon,false,true,false,false) && !PlayerBotCare.disposable(weapon,false,false,true,false) && !PlayerBotCare.disposable(weapon,false,false,false,true),"Protected, upgrade, quest and future gear stay excluded even when enhanced");
  System.out.println("OK: "+checks+" bot stone, weapon, native offscreen consumption/roll and variant purchase checks");
 }
}
