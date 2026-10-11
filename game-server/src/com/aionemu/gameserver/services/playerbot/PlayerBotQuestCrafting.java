/* Native profession/quest adapter informed by pinned TrainerAction.cpp,
 * RpgSubActions.cpp and QuestAction.cpp, 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.craft.Profession;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.recipe.RecipeTemplate;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;
import com.aionemu.gameserver.model.trade.TradeList;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.questEngine.handlers.models.WorkOrdersData;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.TradeService;
import com.aionemu.gameserver.services.craft.CraftService;
import com.aionemu.gameserver.services.craft.CraftSkillUpdateService;
import com.aionemu.gameserver.skillengine.task.CraftingTask;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Plans from actual owned items and native recipes; native services alone consume/produce/advance. */
public final class PlayerBotQuestCrafting {
 private record Catalog(Object recipes,Object items,Object shops,Object goods,Map<Integer,List<RecipeTemplate>> products,
                        Map<Integer,List<RecipeTemplate>> professions,Map<Integer,Set<Integer>> sellers,Map<Integer,Set<Integer>> designs) {}
 private static volatile Catalog CATALOG;
 private static Catalog catalog() {
  var current=CATALOG;if(current!=null && current.recipes()==DataManager.RECIPE_DATA && current.items()==DataManager.ITEM_DATA && current.shops()==DataManager.TRADE_LIST_DATA && current.goods()==DataManager.GOODSLIST_DATA)return current;
  synchronized(PlayerBotQuestCrafting.class) {
   current=CATALOG;if(current!=null && current.recipes()==DataManager.RECIPE_DATA && current.items()==DataManager.ITEM_DATA && current.shops()==DataManager.TRADE_LIST_DATA && current.goods()==DataManager.GOODSLIST_DATA)return current;
   var products=new HashMap<Integer,List<RecipeTemplate>>();var professions=new HashMap<Integer,List<RecipeTemplate>>();var sellers=new HashMap<Integer,Set<Integer>>();var designs=new HashMap<Integer,Set<Integer>>();
   for(var recipe:DataManager.RECIPE_DATA.getRecipeTemplates()) {
    professions.computeIfAbsent(recipe.getSkillId(),k->new ArrayList<>()).add(recipe);var outputs=new HashSet<Integer>();outputs.add(recipe.getProductId());
    for(int i=1;i<=recipe.getComboProductSize();i++){var product=recipe.getComboProduct(i);if(product!=null)outputs.add(product);}
    for(int product:outputs)products.computeIfAbsent(product,k->new ArrayList<>()).add(recipe);
   }
   // Recipe XML normally omits itemid. The actual consumable design is declared
   // by its ItemTemplate CraftLearnAction, and multiple designs may teach a recipe.
   if(DataManager.ITEM_DATA!=null)for(var item:DataManager.ITEM_DATA.getItemTemplates()) {
    var action=item.getActions()==null?null:item.getActions().getCraftLearnAction();
    if(action!=null && DataManager.RECIPE_DATA.getRecipeTemplateById(action.getRecipeId())!=null)
     designs.computeIfAbsent(action.getRecipeId(),k->new TreeSet<>()).add(item.getTemplateId());
   }
   if(DataManager.TRADE_LIST_DATA!=null && DataManager.GOODSLIST_DATA!=null)for(var shop:DataManager.TRADE_LIST_DATA.getTradeListTemplate().values())if(shop.getTradeNpcType()==TradeNpcType.NORMAL)
    for(var tab:shop.getTradeTablist()){var goods=DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());if(goods!=null && goods.getLegionLevel()==0 && goods.getItemIdList()!=null)
     for(int item:goods.getItemIdList())sellers.computeIfAbsent(item,k->new TreeSet<>()).add(shop.getNpcId());}
   products.replaceAll((k,v)->List.copyOf(v));professions.replaceAll((k,v)->List.copyOf(v));sellers.replaceAll((k,v)->Set.copyOf(v));designs.replaceAll((k,v)->Set.copyOf(v));
   return CATALOG=new Catalog(DataManager.RECIPE_DATA,DataManager.ITEM_DATA,DataManager.TRADE_LIST_DATA,DataManager.GOODSLIST_DATA,Map.copyOf(products),Map.copyOf(professions),Map.copyOf(sellers),Map.copyOf(designs));
  }
 }
 static List<RecipeTemplate> professionRecipes(int skill){return catalog().professions().getOrDefault(skill,List.of());}
 static Set<Integer> designs(int recipe){return catalog().designs().getOrDefault(recipe,Set.of());}
 static int designItem(Player bot,int recipe){return designs(recipe).stream().sorted(Comparator.<Integer>comparingInt(id->bot.getInventory().getItemCountByItemId(id)>0?0:sellers(id).isEmpty()?2:1).thenComparingInt(Integer::intValue)).findFirst().orElse(0);}
 public enum Stage { NONE, ACCEPT, REWARD, TRAIN, LEARN_RECIPE, MATERIALS, CRAFT, WAIT, BLOCKED }
 public record Plan(Stage stage,int questId,int recipeId,int skillId,int toolId,
                    Map<Integer,Long> materials,Map<Integer,Long> missing,String reason) {
  public Plan { materials=Map.copyOf(materials);missing=Map.copyOf(missing); }
 }
 /** Native client tool item IDs, corroborated by both capital STATIC spawn files. */
 public static int toolId(int skill) {
  return switch(skill) {case 40001->150000009;case 40002->150000011;case 40003->150000010;
   case 40004->150000015;case 40007->150000013;case 40008->150000012;case 40010->150000023;default->0;};
 }
 public static boolean qualificationAllowed(int reward,int experts,int masters,int expertCap,int masterCap) {
  return reward==400 ? experts+masters<expertCap : reward==500 && masters<masterCap;
 }
 public static boolean recoveryAllowed(boolean world,boolean observedConsumption,boolean noProduct,boolean noSource,boolean start,
                                       boolean cannotGiveup,boolean timer,long age,int retries) {
  return world && observedConsumption && noProduct && noSource && start && !cannotGiveup && !timer && age>=0 && age<=60000 && retries<3;
 }
 /** Pure read-only equivalent of native profession-slot admission; no message/state side effects. */
 public static boolean qualificationAllowed(Player bot,int reward) {
  int experts=0,masters=0;
  for(var p:Profession.values())if(p.isCrafting() && bot.getSkillList().isSkillPresent(p.getSkillId())) {
   int level=bot.getSkillList().getSkillLevel(p.getSkillId());if(level>499)masters++;else if(level>399)experts++;
  }
  return qualificationAllowed(reward,experts,masters,CraftConfig.MAX_EXPERT_CRAFTING_SKILLS,CraftConfig.MAX_MASTER_CRAFTING_SKILLS);
 }
 public static boolean recipeLearnable(Player bot,RecipeTemplate recipe) {
  return recipe!=null && (recipe.getRace()==Race.PC_ALL || recipe.getRace()==bot.getRace())
   && bot.getRecipeList().size()<1600 && !bot.getRecipeList().isRecipePresent(recipe.getId())
   && bot.getSkillList().isSkillPresent(recipe.getSkillId())
   && bot.getSkillList().getSkillLevel(recipe.getSkillId())>=recipe.getSkillpoint();
 }
 /** Aggregate duplicate components before comparing custody, avoiding a native double-consumption shortfall. */
 public static Map<Integer,Long> missing(Map<Integer,Long> required,java.util.function.IntToLongFunction held) {
  var out=new TreeMap<Integer,Long>();required.forEach((id,count)->{long have=held.applyAsLong(id);if(have<count)out.put(id,count-have);});return Map.copyOf(out);
 }
 public static Map<Integer,Long> materials(RecipeTemplate recipe,java.util.function.IntToLongFunction held) {
  Map<Integer,Long> best=Map.of();long score=Long.MAX_VALUE;
  for(var group:recipe.getComponents()) {
   if(group.getComponent().isEmpty())continue;
   var requirements=new TreeMap<Integer,Long>();for(var c:group.getComponent())requirements.merge(c.getItemId(),(long)c.getQuantity(),Math::addExact);
   // CraftService chooses the FIRST group whose first component is in the sent map.
   // A later alternative sharing any earlier selector cannot be submitted safely.
   var selected=recipe.getComponents().stream().filter(g->!g.getComponent().isEmpty() && requirements.containsKey(g.getComponent().getFirst().getItemId())).findFirst().orElse(null);
   if(selected!=group)continue;
   long absent=missing(requirements,held).values().stream().reduce(0L,Math::addExact);
   if(absent<score){best=Map.copyOf(requirements);score=absent;}
  }
  return best;
 }
 public static Plan plan(Player bot,int quest) {
  var model=DataManager.XML_QUESTS.getQuest(quest);var state=bot.getQuestStateList().getQuestState(quest);
  if(!(model instanceof WorkOrdersData work))return none(quest);
  if(state!=null && state.getStatus()==QuestStatus.REWARD)return result(Stage.REWARD,quest,null,Map.of(),Map.of(),"Native work-order reward is ready");
  if(state!=null && !state.isStartable() && state.getStatus()!=QuestStatus.START)return none(quest);
  var recipe=DataManager.RECIPE_DATA.getRecipeTemplateById(work.getRecipeId());
  if(recipe==null)return result(Stage.BLOCKED,quest,null,Map.of(),Map.of(),"Native work-order recipe "+work.getRecipeId()+" is unavailable");
  if(state==null || state.isStartable()) {
   if(recipeLearnable(bot,recipe))return result(Stage.ACCEPT,quest,recipe,Map.of(),Map.of(),"Native acceptance must supply its recipe and work components");
   if(!bot.getSkillList().isSkillPresent(recipe.getSkillId()) || bot.getSkillList().getSkillLevel(recipe.getSkillId())<recipe.getSkillpoint())
    return result(Stage.TRAIN,quest,recipe,Map.of(),Map.of(),"Native profession "+recipe.getSkillId()+" requires "+recipe.getSkillpoint()+" points before acceptance");
   return result(Stage.BLOCKED,quest,recipe,Map.of(),Map.of(),"Native recipe acceptance rejects race, recipe capacity or already learned recipe");
  }
  var template=DataManager.QUEST_DATA.getQuestById(quest);var collection=template.getCollectItems();
  if(collection!=null && !collection.getCollectItem().isEmpty() && collection.getCollectItem().stream().allMatch(c->bot.getInventory().getItemCountByItemId(c.getItemId())>=c.getCount()))
   return result(Stage.REWARD,quest,recipe,Map.of(),Map.of(),"Native QUEST_SELECT must verify collection and transition to reward");
  return recipePlan(bot,quest,recipe);
 }
 /** Supports actual crafted collection dependencies, including custom qualification quests. */
 public static Plan productPlan(Player bot,int quest,int product,long needed) {
  if(needed<=0 || bot.getInventory().getItemCountByItemId(product)>=needed)return none(quest);
  var recipes=catalog().products().getOrDefault(product,List.of()).stream()
   .filter(r->r.getRace()==Race.PC_ALL || r.getRace()==bot.getRace()).sorted(Comparator.<RecipeTemplate>comparingInt(r->bot.getRecipeList().isRecipePresent(r.getId())?0:1).thenComparingInt(RecipeTemplate::getSkillpoint).thenComparingInt(RecipeTemplate::getId)).toList();
  Plan fallback=none(quest);
  for(var recipe:recipes){var p=recipePlan(bot,quest,recipe);if(p.stage()==Stage.CRAFT || p.stage()==Stage.MATERIALS)return p;if(fallback.stage()==Stage.NONE)fallback=p;}
  return fallback;
 }
 static Plan recipePlan(Player bot,int quest,RecipeTemplate recipe) {
  var materials=materials(recipe,bot.getInventory()::getItemCountByItemId);var missing=missing(materials,bot.getInventory()::getItemCountByItemId);
  if(!bot.getSkillList().isSkillPresent(recipe.getSkillId()) || bot.getSkillList().getSkillLevel(recipe.getSkillId())<recipe.getSkillpoint())
   return result(Stage.TRAIN,quest,recipe,materials,missing,"Native profession "+recipe.getSkillId()+" requires "+recipe.getSkillpoint()+" points");
  if(!bot.getRecipeList().isRecipePresent(recipe.getId())){int design=designItem(bot,recipe.getId());return result(design>0?Stage.LEARN_RECIPE:Stage.BLOCKED,quest,recipe,materials,design>0?Map.of(design,1L):Map.of(),"Learn native recipe "+recipe.getId()+" through an owned design item; work-order designs come only from acceptance");}
  if(bot.getInteractionTask() instanceof CraftingTask task && task.isInProgress())return result(Stage.WAIT,quest,recipe,materials,missing,"Native crafting task is in progress");
  if(bot.getInventory().isFull())return result(Stage.BLOCKED,quest,recipe,materials,missing,"Native inventory is full");
  if(recipe.getCraftDelayId()!=null && bot.getCraftCooldowns().hasCooldown(recipe.getCraftDelayId()))return result(Stage.WAIT,quest,recipe,materials,missing,"Native recipe cooldown "+recipe.getCraftDelayId()+" is active");
  if(recipe.getDp()!=null && bot.getCommonData().getDp()<recipe.getDp())return result(Stage.BLOCKED,quest,recipe,materials,missing,"Native recipe requires "+recipe.getDp()+" DP");
  if(recipe.getComponents().isEmpty() || materials.isEmpty())return result(Stage.BLOCKED,quest,recipe,materials,missing,"Recipe has no declared native material set; no empty-material craft substitute");
  if(toolId(recipe.getSkillId())==0)return result(Stage.BLOCKED,quest,recipe,materials,missing,"No supported native crafting tool for profession "+recipe.getSkillId());
  return result(missing.isEmpty()?Stage.CRAFT:Stage.MATERIALS,quest,recipe,materials,missing,missing.isEmpty()?"Use the real native crafting task at tool "+toolId(recipe.getSkillId()):"Acquire missing native materials "+missing);
 }
 private static Plan result(Stage stage,int quest,RecipeTemplate recipe,Map<Integer,Long> materials,Map<Integer,Long> missing,String reason) {
  return new Plan(stage,quest,recipe==null?0:recipe.getId(),recipe==null?0:recipe.getSkillId(),recipe==null?0:toolId(recipe.getSkillId()),materials,missing,reason);
 }
 private static Plan none(int quest){return result(Stage.NONE,quest,null,Map.of(),Map.of(),"");}
 /** Revalidate immediately before consuming. Success means a real task began, never that production succeeded. */
 public static boolean start(Player bot,Plan intent,StaticObject tool) {
  if(!idle(bot) || intent.stage()!=Stage.CRAFT || tool==null || !tool.isSpawned() || !bot.getKnownList().sees(tool)
   || tool.getWorldId()!=bot.getWorldId() || tool.getInstanceId()!=bot.getInstanceId() || tool.getObjectTemplate().getTemplateId()!=intent.toolId()
   || !PositionUtil.isInRange(bot,tool,5,false) || !GeoService.getInstance().canSee(bot,tool))return false;
  var recipe=DataManager.RECIPE_DATA.getRecipeTemplateById(intent.recipeId());if(recipe==null || !recipePlan(bot,intent.questId(),recipe).equals(intent))return false;
  bot.getMoveController().abortMove();CraftService.startCrafting(bot,recipe.getId(),tool.getObjectId(),0,intent.materials());
  return bot.getInteractionTask() instanceof CraftingTask task && task.isInProgress();
 }
 private static boolean idle(Player bot){return !GameServer.isShuttingDownSoon() && bot.isSpawned() && !bot.isDead() && !bot.isCasting() && !bot.isTrading() && !bot.isLooting()
  && !bot.isUsingFlightTransporterOrWindstream() && !bot.getController().isUnderStance() && !bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ITEM_USE)
  && !bot.getController().isInCombat() && (bot.getInteractionTask()==null || !bot.getInteractionTask().isInProgress());}
 public static boolean train(Player bot,Npc npc,int skill) {
  if(!idle(bot) || !legalNpc(bot,npc))return false;var service=CraftSkillUpdateService.getInstance();var profession=service.getProfessionByNpc(npc);
  if(profession==null || profession.getSkillId()!=skill)return false;int level=bot.getSkillList().isSkillPresent(skill)?bot.getSkillList().getSkillLevel(skill):0;Integer cost=profession.getUpgradeCost(level);
  if(cost==null || cost>bot.getInventory().getKinah() || bot.getLevel()<10)return false;
  // Clear this bot's prior profession confirmation, then accept only the newly requested operation.
  bot.getResponseRequester().remove(SM_QUESTION_WINDOW.STR_CRAFT_ADDSKILL_CONFIRM);service.learnSkill(bot,npc);
  bot.getResponseRequester().respond(SM_QUESTION_WINDOW.STR_CRAFT_ADDSKILL_CONFIRM,1);return bot.getSkillList().getSkillLevel(skill)>level;
 }
 public static boolean learnRecipe(Player bot,int recipe) {
  if(!idle(bot))return false;
  for(var item:bot.getInventory().getItems()) {var actions=item.getItemTemplate().getActions();var action=actions==null?null:actions.getCraftLearnAction();
   if(action==null || action.getRecipeId()!=recipe || item.getItemCount()<1 || !PlayerRestrictions.canUseItem(bot,item) || !action.canAct(bot,item,null))continue;
   action.act(bot,item,null);return bot.getRecipeList().isRecipePresent(recipe);
  }return false;
 }
 public static Set<Integer> sellers(int item) {
  return catalog().sellers().getOrDefault(item,Set.of());
 }
 /** Read the existing native trainer registry rather than maintaining a second NPC policy. */
 static Set<Integer> trainers(int skill) {
  try {
   CraftSkillUpdateService.getInstance();var field=CraftSkillUpdateService.class.getDeclaredField("professionByNpc");field.setAccessible(true);
   var out=new TreeSet<Integer>();for(var entry:((Map<?,?>)field.get(null)).entrySet())
    if(entry.getKey() instanceof Integer id && entry.getValue() instanceof Profession p && p.getSkillId()==skill)out.add(id);
   return Set.copyOf(out);
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native crafting trainer registry is unavailable",failure);}
 }
 /** Native finite purchase at the actual authorized merchant, priced from the real trade list. */
 public static boolean buy(Player bot,Npc npc,int item,long count,long allowance) {
  if(count<=0 || !idle(bot) || !legalNpc(bot,npc) || !sellers(item).contains(npc.getNpcId()))return false;
  var shop=DataManager.TRADE_LIST_DATA.getTradeListTemplate(npc.getNpcId());var list=new TradeList(npc.getObjectId());list.addItem(item,count);
  if(!list.calculateBuyListPrice(bot,shop.getSellPriceRate()) || list.getRequiredKinah()<0 || list.getRequiredKinah()>Math.min(allowance,bot.getInventory().getKinah()))return false;
  return TradeService.performBuyFromShop(npc,bot,list);
 }
 private static boolean legalNpc(Player bot,Npc npc){return npc!=null && npc.isSpawned() && !npc.isDead() && !bot.isEnemy(npc) && bot.getKnownList().sees(npc)
  && npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId() && PositionUtil.isInTalkRange(bot,npc)
  && GeoService.getInstance().canSee(bot,npc) && DialogService.isInteractionAllowed(bot,npc);}
 private PlayerBotQuestCrafting() {}
}
