package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.recipe.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.craft.CraftService;
import com.aionemu.gameserver.services.item.ItemPacketService.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.task.*;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Actual native crafting analysis/finalization on owned stacks; no scheduler/World/SQL/IDFactory. */
public final class PlayerBotNativeCraftingTaskCheck {
 static int checks;static sun.misc.Unsafe unsafe;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Object get(Object target,String name)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
 static void set(Object target,String name,Object value)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
 static final class ScheduledRecord implements Future<Object> {
  boolean cancelled;int cancels;public boolean cancel(boolean interrupt){check(!interrupt,"Native craft cancellation preserves task custody");cancelled=true;cancels++;return true;}
  public boolean isCancelled(){return cancelled;}public boolean isDone(){return cancelled;}public Object get(){throw new AssertionError();}public Object get(long time,TimeUnit unit){throw new AssertionError();}
 }
 static final class Inventory extends PlayerStorage {
  final Map<Integer,Item> stacks=new HashMap<>();final Map<Integer,Long> held=new HashMap<>();int updates,consumed;long added;
  Inventory(){super(null,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int id){return held.getOrDefault(id,0L);}
  @Override public boolean decreaseByItemId(int id,long count){if(getItemCountByItemId(id)<count)return false;held.put(id,getItemCountByItemId(id)-count);consumed++;return true;}
  @Override public List<Item> getItemsByItemId(int id){var stack=stacks.get(id);check(stack!=null,"Actual native ItemService must use an existing owned product stack, no ItemFactory allocation");return List.of(stack);}
  @Override public long increaseItemCount(Item item,long count,ItemUpdateType type){check(stacks.get(item.getItemId())==item && type==ItemUpdateType.INC_ITEM_COLLECT,"Native craft outcome updates actual owned stack with native custody type");held.merge(item.getItemId(),count,Long::sum);item.setItemCount(held.get(item.getItemId()));updates++;added+=count;return 0;}
  @Override public Item add(Item item,ItemAddType type){throw new AssertionError("No native new item or ID allocation allowed");}
  @Override public boolean isFull(){return false;}
 }
 static final class Common extends PlayerCommonData {
  long xp;Common(){super(1999976151);}
  @Override public com.aionemu.gameserver.model.templates.BoundRadius getBoundRadius(){return com.aionemu.gameserver.model.templates.BoundRadius.DEFAULT;}
  @Override public void addExp(long value,Rates rates){check(rates==Rates.XP_CRAFTING && value>0,"Actual native craft profession XP reaches character crafting XP endpoint");xp+=value;}
 }
 static final class Stats extends PlayerGameStats {
  Stats(Player owner){super(owner);}
  @Override protected Stat2 getStat(StatEnum stat,float base,Set<com.aionemu.gameserver.utils.stats.CalculationType> types){return new AdditionStat(stat,base,null);}
 }
 static final class Recipes extends RecipeList {
  int deleted;
  @Override public boolean deleteRecipe(Player player,int id){check(getRecipeList().remove(id),"Native finite-production recipe removal owns the actual learned recipe");deleted++;return true;}
 }
 static final class Actor extends Player {
  boolean spawned,dead,playing;int map,instance;Inventory cube;KnownList known;Common common;Stats stats;Account account;PlayerSkillList skills;Recipes recipes;QuestStateList quests;Cooldowns cooldown;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return 1999976151;}@Override public boolean isPlayerBot(){return true;}@Override public boolean isOnline(){return false;}@Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public boolean isPlaying(){return playing;}
  @Override public int getWorldId(){return map;}@Override public int getInstanceId(){return instance;}@Override public float getX(){return 0;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public PlayerStorage getInventory(){return cube;}@Override public KnownList getKnownList(){return known;}@Override public PlayerCommonData getCommonData(){return common;}@Override public PlayerGameStats getGameStats(){return stats;}@Override public Account getAccount(){return account;}
  @Override public PlayerCommonData getObjectTemplate(){return common;}
  @Override public PlayerSkillList getSkillList(){return skills;}@Override public RecipeList getRecipeList(){return recipes;}@Override public QuestStateList getQuestStateList(){return quests;}@Override public Cooldowns getCraftCooldowns(){return cooldown;}
  @Override public boolean isInPlayerMode(com.aionemu.gameserver.model.actions.PlayerMode mode){return false;}@Override public boolean isInAnyHide(){return false;}
  @Override public com.aionemu.gameserver.model.house.House getActiveHouse(){return null;}
 }
 static final class Tool extends StaticObject {
  Tool(){super(null,null,null);}@Override public int getObjectId(){return 1999976152;}@Override public float getX(){return 0;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}@Override public int getWorldId(){return 1;}@Override public int getInstanceId(){return 1;}
  @Override public com.aionemu.gameserver.model.templates.VisibleObjectTemplate getObjectTemplate(){return new com.aionemu.gameserver.model.templates.VisibleObjectTemplate(){public int getTemplateId(){return 150000012;}public String getName(){return "Native fixture tool";}public int getL10nId(){return 0;}};}
 }
 static final class Work extends CraftingTask {
  final PlayerBotService service;int updates,success,failed,aborted,finished;final ScheduledRecord schedule=new ScheduledRecord();
  Work(Actor actor,Tool tool,RecipeTemplate recipe,int difference,PlayerBotService service){super(actor,tool,recipe,difference,0);this.service=service;}
  void begin()throws Exception{requester.setInteractionTask(this);onInteractionStart();set(this,"task",schedule);}
  void tick(){runInteraction();}int progress(){return currentSuccessValue;}int failure(){return currentFailureValue;}
  @Override protected boolean interactionPresent(){return service.allowsHeadlessInteraction(requester);}
  @Override protected void sendInteractionUpdate(){updates++;super.sendInteractionUpdate();}
  @Override protected boolean onSuccessFinish(){success++;return super.onSuccessFinish();}
  @Override protected void onFailureFinish(){failed++;super.onFailureFinish();}
  @Override protected void onInteractionAbort(){aborted++;super.onInteractionAbort();}
  @Override protected void onInteractionFinish(){finished++;super.onInteractionFinish();}
 }
 static final class Cooking extends quest.crafting._19038MasterCooksPotential {
  int failures,updates;
  @Override public boolean onFailCraftEvent(QuestEnv env,int item){failures++;return super.onFailCraftEvent(env,item);}
  @Override public void updateQuestStatus(QuestEnv env){updates++;}
 }
 static String nativeEntry(String path,String tag,int id)throws Exception{var matcher=java.util.regex.Pattern.compile("<"+tag+"\\s+id=\""+id+"\"[^>]*>.*?</"+tag+">",java.util.regex.Pattern.DOTALL).matcher(Files.readString(Path.of(path)));if(!matcher.find())throw new AssertionError("Missing maintained native "+id);return matcher.group();}
 static RecipeTemplate ordinary()throws Exception {
  var recipe=new RecipeTemplate();set(recipe,"id",155001001);set(recipe,"skillid",40008);set(recipe,"skillpoint",440);set(recipe,"race",Race.ELYOS);set(recipe,"productid",152020079);set(recipe,"quantity",1);
  var component=new Component();set(component,"itemid",152000100);set(component,"quantity",2);var group=new ComponentsData();set(group,"component",List.of(component));set(recipe,"componentsData",List.of(group));return recipe;
 }
 static boolean admit(Actor bot,Tool tool,RecipeTemplate recipe)throws Exception {
  var method=CraftService.class.getDeclaredMethod("checkCraft",Player.class,RecipeTemplate.class,int.class,VisibleObject.class,ItemTemplate.class,int.class,Map.class);method.setAccessible(true);
  var sent=new HashMap<Integer,Long>();for(var component:recipe.getComponents().getFirst().getComponent())sent.put(component.getItemId(),(long)component.getQuantity());
  return (boolean)method.invoke(null,bot,recipe,recipe.getSkillId(),tool,DataManager.ITEM_DATA.getItemTemplate(recipe.getProductId()),0,sent);
 }
 static void addTemplate(ItemData data,Actor actor,int id)throws Exception {
  var template=new ItemTemplate();set(template,"itemId",id);set(template,"maxStackCount",9999);set(template,"itemQuality",ItemQuality.COMMON);
  @SuppressWarnings("unchecked") var items=(Map<Integer,ItemTemplate>)get(data,"items");items.put(id,template);
  var stack=new Item(1999976153+actor.cube.stacks.size(),template);stack.setItemCount(1);actor.cube.stacks.put(id,stack);actor.cube.held.put(id,1L);
 }
 static void reset(Actor actor,PlayerBotService service,PlayerBotSession session,Map<Integer,PlayerBotSession> sessions)throws Exception{
  actor.spawned=true;actor.dead=false;actor.playing=true;actor.map=actor.instance=1;set(session,"closing",false);set(service,"shuttingDown",false);set(session,"bot",actor);sessions.put(actor.getObjectId(),session);actor.setInteractionTask(null);
 }
 public static void main(String[] args)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);
  var oldItems=DataManager.ITEM_DATA;var oldRecipes=DataManager.RECIPE_DATA;var oldSkills=DataManager.SKILL_DATA;var oldQuests=DataManager.QUEST_DATA;
  var oldRate=RatesConfig.SKILL_XP_CRAFTING_RATES;var oldCrit=RatesConfig.CRAFT_CRIT_CHANCES;var oldCombo=RatesConfig.CRAFT_COMBO_CHANCES;int oldFailure=CraftConfig.MAX_CRAFT_FAILURE_CHANCE;boolean oldItemLog=LoggingConfig.LOG_ITEM,oldCraftLog=LoggingConfig.LOG_CRAFT;
  boolean oldQuestAi=PlayerBotConfig.QUESTS_ENABLED,oldCraftAi=PlayerBotConfig.QUEST_CRAFTING;
  var engine=QuestEngine.getInstance();@SuppressWarnings("unchecked") var handlers=(Map<Integer,com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler>)get(engine,"questHandlers");@SuppressWarnings("unchecked") var failHooks=(Map<Integer,Integer>)get(engine,"questOnFailCraft");var oldHandler=handlers.get(19038);var oldHook=failHooks.get(182206773);
  try {
   RatesConfig.SKILL_XP_CRAFTING_RATES=new float[]{1};RatesConfig.CRAFT_CRIT_CHANCES=new float[]{0};RatesConfig.CRAFT_COMBO_CHANCES=new float[]{0};CraftConfig.MAX_CRAFT_FAILURE_CHANCE=0;LoggingConfig.LOG_ITEM=LoggingConfig.LOG_CRAFT=false;
   var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.cube=new Inventory();actor.known=new KnownList(actor);actor.common=(Common)unsafe.allocateInstance(Common.class);actor.stats=(Stats)unsafe.allocateInstance(Stats.class);actor.account=new Account(0);actor.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40008,440,0,Persistable.PersistentState.UPDATED)));actor.recipes=new Recipes();actor.quests=new QuestStateList();actor.cooldown=new Cooldowns();
   var recipe=ordinary();var items=new ItemData();addTemplate(items,actor,recipe.getProductId());addTemplate(items,actor,152000100);DataManager.ITEM_DATA=items;
   var recipeData=new RecipeData();@SuppressWarnings("unchecked") var recipeMap=(Map<Integer,RecipeTemplate>)get(recipeData,"recipeData");recipeMap.put(recipe.getId(),recipe);DataManager.RECIPE_DATA=recipeData;
   var skillData=new SkillData();@SuppressWarnings("unchecked") var skillMap=(Map<Integer,SkillTemplate>)get(skillData,"skillTemplateById");for(int id:List.of(40008,40001)){var skill=new SkillTemplate();set(skill,"skillId",id);skillMap.put(id,skill);}DataManager.SKILL_DATA=skillData;
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests>"+nativeEntry("game-server/data/static_data/quest_data/quest_data.xml","quest",19038)+"</quests>"));
   var service=(PlayerBotService)unsafe.allocateInstance(PlayerBotService.class);Map<Integer,PlayerBotSession> sessions=new ConcurrentHashMap<>();set(service,"sessions",sessions);var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);set(session,"bot",actor);var tool=(Tool)unsafe.allocateInstance(Tool.class);reset(actor,service,session,sessions);
   try(var lease=PlayerBotLease.acquire(actor.getObjectId())) {
    check(lease!=null && !actor.isOnline() && service.allowsHeadlessInteraction(actor),"Actual registered actor/lease admits headless native crafting");
    actor.recipes.getRecipeList().add(recipe.getId());actor.cube.held.put(152000100,10L);int consumed=actor.cube.consumed;
    check(admit(actor,tool,recipe) && actor.cube.consumed==consumed+1 && actor.cube.held.get(152000100)==8,"Actual native CraftService.checkCraft consumes owned ingredients before starting real task");
    var work=new Work(actor,tool,recipe,0,service);work.begin();long product=actor.cube.held.get(recipe.getProductId());int xp=actor.skills.getSkillEntry(40008).getCurrentXp();int ticks=0;
    boolean noPrematureAward=true;while(work.progress()<1000 && ticks++<100){work.tick();noPrematureAward &= actor.cube.held.get(recipe.getProductId())==product && actor.skills.getSkillEntry(40008).getCurrentXp()==xp;}
    check(noPrematureAward,"Every actual native analysis tick preserves unfinished product/XP custody");
    check(ticks>1 && work.updates==ticks && work.success==0 && work.isInProgress(),"Actual native crafting uses multiple runInteraction analysis ticks before finalization");
    work.tick();check(work.success==1 && work.finished==1 && work.aborted==0 && actor.getInteractionTask()==null && !work.isInProgress(),"Only actual native completion finalizes headless crafting task");
    check(actor.cube.held.get(recipe.getProductId())==product+1 && actor.cube.updates==1 && actor.skills.getSkillEntry(40008).getCurrentXp()>xp && actor.common.xp>0,"Native CraftingTask -> finishCrafting -> ItemService and real skill list earn owned product/profession XP");
    check(actor.cube.held.get(152000100)==8 && actor.cube.consumed==consumed+1,"Native completion never duplicates ingredient consumption");
    // AI admission disables future work; a committed native task keeps its earned completion.
    reset(actor,service,session,sessions);actor.recipes.getRecipeList().add(recipe.getId());check(admit(actor,tool,recipe),"Second real attempt consumes actual owned components");work=new Work(actor,tool,recipe,0,service);work.begin();PlayerBotConfig.QUESTS_ENABLED=PlayerBotConfig.QUEST_CRAFTING=false;
    product=actor.cube.held.get(recipe.getProductId());while(work.progress()<1000)work.tick();work.tick();check(!PlayerBotConfig.QUESTS_ENABLED && !PlayerBotConfig.QUEST_CRAFTING && actor.cube.held.get(recipe.getProductId())==product+1,"Disabling actual new quest/crafting AI configuration preserves an already committed native craft completion");
    reset(actor,service,session,sessions);CraftConfig.MAX_CRAFT_FAILURE_CHANCE=100;work=new Work(actor,tool,recipe,0,service);work.begin();product=actor.cube.held.get(recipe.getProductId());xp=actor.skills.getSkillEntry(40008).getCurrentXp();while(work.failure()<1000)work.tick();work.tick();
    check(work.failed==1 && work.success==0 && work.finished==1 && actor.cube.held.get(recipe.getProductId())==product && actor.skills.getSkillEntry(40008).getCurrentXp()==xp,"Actual native analysis failure awards no product or profession XP");
    for(int invalid=0;invalid<10;invalid++) {
     reset(actor,service,session,sessions);work=new Work(actor,tool,recipe,41,service);work.begin();product=actor.cube.held.get(recipe.getProductId());xp=actor.skills.getSkillEntry(40008).getCurrentXp();
     switch(invalid){case 0->actor.dead=true;case 1->actor.spawned=false;case 2->actor.map=2;case 3->actor.instance=2;case 4->set(session,"closing",true);case 5->set(service,"shuttingDown",true);case 6->sessions.clear();case 7->set(session,"bot",unsafe.allocateInstance(Actor.class));case 8->actor.setInteractionTask(new Work(actor,tool,recipe,41,service));default->actor.playing=false;}
     var successor=actor.getInteractionTask();work.tick();check(work.progress()==0 && work.aborted==1 && work.finished==1 && work.schedule.cancels==1,"Native stale/dead/despawn/map/instance/closing/shutdown/identity/current-task abort precedes actual craft calculation "+invalid);
     check(actor.cube.held.get(recipe.getProductId())==product && actor.skills.getSkillEntry(40008).getCurrentXp()==xp && actor.getInteractionTask()==(invalid==8?successor:null),"Invalid task cannot grant products/XP or clear successor custody "+invalid);
    }
    reset(actor,service,session,sessions);var cooking=(RecipeData)JAXBContext.newInstance(RecipeData.class).createUnmarshaller().unmarshal(new StringReader("<recipe_templates>"+nativeEntry("game-server/data/static_data/recipe/recipe_templates.xml","recipe_template",155002239)+"</recipe_templates>"));var cook=cooking.getRecipeTemplateById(155002239);DataManager.RECIPE_DATA=cooking;
    addTemplate(items,actor,cook.getProductId());addTemplate(items,actor,cook.getComboProduct(1));actor.cube.held.put(cook.getComboProduct(1),0L);actor.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40001,499,0,Persistable.PersistentState.UPDATED)));actor.recipes.getRecipeList().add(cook.getId());actor.quests.addQuest(19038,new QuestState(19038,QuestStatus.START));actor.quests.getQuestState(19038).setQuestVarById(0,2);
    var handler=new Cooking();handlers.put(19038,handler);failHooks.put(cook.getComboProduct(1),19038);CraftConfig.MAX_CRAFT_FAILURE_CHANCE=0;work=new Work(actor,tool,cook,0,service);work.begin();product=actor.cube.held.get(cook.getProductId());while(work.progress()<1000)work.tick();work.tick();
    check(work.success==1 && actor.cube.held.get(cook.getProductId())==product+1 && actor.cube.held.get(cook.getComboProduct(1))==0,"Actual finite cooking craft produces its native noncritical outcome only");
    check(handler.failures==1 && handler.updates==1 && actor.quests.getQuestState(19038).getQuestVarById(0)==1 && actor.recipes.deleted==1,"Native finishCrafting dispatches real cooking onFailCraft to native retry stage and removes its finite recipe");
    check(actor.skills.getSkillLevel(40001)==499 && actor.skills.getSkillEntry(40001).getCurrentXp()==0 && actor.quests.getQuestState(19038).getStatus()==QuestStatus.START,"Native qualification cap and cooking retry preserve rank/completion authority");
   }
   check(!service.allowsHeadlessInteraction(actor),"Released character lease denies further native crafting progress");
   System.out.println("OK: "+checks+" actual native CraftingTask product/XP/custody/cooking/presence checks; no scheduler/World/SQL/IDFactory");
  }finally{DataManager.ITEM_DATA=oldItems;DataManager.RECIPE_DATA=oldRecipes;DataManager.SKILL_DATA=oldSkills;DataManager.QUEST_DATA=oldQuests;RatesConfig.SKILL_XP_CRAFTING_RATES=oldRate;RatesConfig.CRAFT_CRIT_CHANCES=oldCrit;RatesConfig.CRAFT_COMBO_CHANCES=oldCombo;CraftConfig.MAX_CRAFT_FAILURE_CHANCE=oldFailure;LoggingConfig.LOG_ITEM=oldItemLog;LoggingConfig.LOG_CRAFT=oldCraftLog;PlayerBotConfig.QUESTS_ENABLED=oldQuestAi;PlayerBotConfig.QUEST_CRAFTING=oldCraftAi;if(oldHandler==null)handlers.remove(19038);else handlers.put(19038,oldHandler);if(oldHook==null)failHooks.remove(182206773);else failHooks.put(182206773,oldHook);}
 }
 private PlayerBotNativeCraftingTaskCheck(){}
}
