package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.util.List;

import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.configs.main.RatesConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.ItemData;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.model.skill.PlayerSkillList;
import com.aionemu.gameserver.model.gameobjects.Persistable;
import com.aionemu.gameserver.model.templates.gather.GatherableTemplate;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.recipe.RecipeTemplate;
import com.aionemu.gameserver.skillengine.task.CraftingTask;
import com.aionemu.gameserver.skillengine.task.GatheringTask;

/** The native gather and craft rolls run with no world object, scheduler or client, as a dormant bot's background turn needs. */
public final class PlayerBotProfessionTaskCheck {
 static int checks;
 static sun.misc.Unsafe unsafe;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void set(Object target,String name,Object value)throws Exception {
  Class<?> type=target.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
  throw new NoSuchFieldException(name);
 }
 static PlayerSkillList skills(int gather,int craft){return new PlayerSkillList(List.of(new PlayerSkillEntry(30002,gather,0,Persistable.PersistentState.UPDATED),new PlayerSkillEntry(40008,craft,0,Persistable.PersistentState.UPDATED)));}
 static final class Actor extends Player {
  PlayerSkillList skills;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return 1999976171;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isOnline(){return false;}
  @Override public int getWorldId(){return 1;}
  @Override public int getInstanceId(){return 1;}
  @Override public PlayerSkillList getSkillList(){return skills;}
  @Override public Account getAccount(){return new Account(0);}
  @Override public com.aionemu.gameserver.model.house.House getActiveHouse(){return null;}
 }
 public static void main(String[] args)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);
  int oldGather=CraftConfig.MAX_GATHER_FAILURE_CHANCE,oldCraft=CraftConfig.MAX_CRAFT_FAILURE_CHANCE;var oldItems=DataManager.ITEM_DATA;var oldCrit=RatesConfig.CRAFT_CRIT_CHANCES;var oldCombo=RatesConfig.CRAFT_COMBO_CHANCES;
  try {
   var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.skills=skills(50,50);
   var node=new GatherableTemplate();set(node,"id",400001);set(node,"harvestSkill",30002);set(node,"skillLevel",50);
   // Gathering: same rolls, no node, timer or observer. Finishes in one call whatever the skill gap.
   CraftConfig.MAX_GATHER_FAILURE_CHANCE=0;actor.skills=skills(50,50);
   for(int i=0;i<200;i++)check(GatheringTask.backgroundSuccess(actor,node),"a skilled bot with no failure chance always gathers");
   actor.skills=skills(91,50);CraftConfig.MAX_GATHER_FAILURE_CHANCE=100;
   for(int i=0;i<50;i++)check(GatheringTask.backgroundSuccess(actor,node),"41 points above the node is a native automatic success");
   actor.skills=skills(49,50);CraftConfig.MAX_GATHER_FAILURE_CHANCE=0;
   for(int i=0;i<50;i++)check(!GatheringTask.backgroundSuccess(actor,node),"a node above the bot's skill is a native automatic failure");
   actor.skills=skills(50,50);CraftConfig.MAX_GATHER_FAILURE_CHANCE=100;int failed=0;
   for(int i=0;i<200;i++)if(!GatheringTask.backgroundSuccess(actor,node))failed++;
   check(failed>0,"failure rolls still happen for a bot at the node's level");
   // Crafting: native rolls with native elapsed time; a failed attempt is reported as failed.
   var product=new ItemTemplate();set(product,"itemId",152020079);set(product,"itemQuality",ItemQuality.COMMON);
   var items=new ItemData();Field map=ItemData.class.getDeclaredField("items");map.setAccessible(true);((java.util.Map<Integer,ItemTemplate>)map.get(items)).put(152020079,product);DataManager.ITEM_DATA=items;
   var recipe=new RecipeTemplate();set(recipe,"id",155001001);set(recipe,"skillid",40008);set(recipe,"skillpoint",50);set(recipe,"race",Race.ELYOS);set(recipe,"productid",152020079);set(recipe,"quantity",1);
   RatesConfig.CRAFT_CRIT_CHANCES=new float[]{0};RatesConfig.CRAFT_COMBO_CHANCES=new float[]{0};
   CraftConfig.MAX_CRAFT_FAILURE_CHANCE=0;actor.skills=skills(50,50);
   for(int i=0;i<100;i++){var result=CraftingTask.backgroundOutcome(actor,recipe);check(result.success() && result.critical()==0 && result.millis()>0,"a certain craft succeeds and takes real time");}
   CraftConfig.MAX_CRAFT_FAILURE_CHANCE=100;int craftFailed=0;long slowest=0;
   for(int i=0;i<100;i++){var result=CraftingTask.backgroundOutcome(actor,recipe);if(!result.success())craftFailed++;slowest=Math.max(slowest,result.millis());}
   check(craftFailed>0,"craft failures still happen");
   check(slowest<600000,"one attempt cannot take longer than a background interval");
   check(CraftingTask.interval(product,0)==2500 && CraftingTask.interval(product,1000)==1200,"native step times keep their floor");
   check(List.of(GatheringTask.class,CraftingTask.class).size()==2,"task classes loaded");
   System.out.println("OK: "+checks+" background gather and craft roll checks");
  } finally {CraftConfig.MAX_GATHER_FAILURE_CHANCE=oldGather;CraftConfig.MAX_CRAFT_FAILURE_CHANCE=oldCraft;DataManager.ITEM_DATA=oldItems;RatesConfig.CRAFT_CRIT_CHANCES=oldCrit;RatesConfig.CRAFT_COMBO_CHANCES=oldCombo;}
 }
 private PlayerBotProfessionTaskCheck(){}
}
