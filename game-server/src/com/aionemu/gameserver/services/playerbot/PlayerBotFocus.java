package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.craft.Profession;
import com.aionemu.gameserver.model.gameobjects.Gatherable;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.Plan;
import com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.Stage;
import com.aionemu.gameserver.utils.PositionUtil;

/** Selected priorities feed the existing native action owners; no separate scheduler, production or reward loop. */
public final class PlayerBotFocus {
 public enum Focus { BALANCED, GATHERING, CRAFTING, PVP, INSTANCE_PVE, WORLD_PVE }
 private static final class State {
  final PlayerBotSession session;Focus focus=Focus.BALANCED;int profession;long nextGather,nextCraft;
  Map<Integer,Long> gather=Map.of();Plan craft;String reason="Follow your party; optional work stays within the owner's nearby area.";
  String recipeKey="";List<com.aionemu.gameserver.model.templates.recipe.RecipeTemplate> recipes=List.of();
  State(PlayerBotSession session) {
   this.session=session;
   try {
    var p=PlayerBotMetadata.load(session.owner().getAccount().getId(),session.bot().getObjectId(),"focus",null);
    if(!p.isEmpty()){focus=Focus.valueOf(p.getProperty("focus"));profession=Integer.parseInt(p.getProperty("profession"));}
   }catch(IOException|RuntimeException error){throw new IllegalStateException("Cannot read companion focus",error);}
  }
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 public static void validate(Map<String,String> values) {
  if(!values.keySet().equals(Set.of("version","account","character","focus","profession")) || !"1".equals(values.get("version")))throw new IllegalArgumentException("Invalid companion focus fields/version");
  var focus=Focus.valueOf(values.get("focus"));int skill=Integer.parseInt(values.get("profession"));
  var profession=Profession.getBySkillId(skill);
  if(skill!=0 && (profession==null || focus==Focus.GATHERING && profession.isCrafting() || focus==Focus.CRAFTING && !profession.isCrafting()))
   throw new IllegalArgumentException("Choose a native profession matching the selected focus");
 }
 private static boolean owned(PlayerBotSession session){return session!=null && session.recruit() && PlayerBotWarehouse.privateOwner(session);}
 private static State state(PlayerBotSession session){return STATES.computeIfAbsent(session.bot().getObjectId(),id->new State(session));}
 static Focus chosen(PlayerBotSession session){return owned(session)?state(session).focus:Focus.BALANCED;}
 /** A focus setting never interrupts paid work, a quest or a native craft already in progress. */
 public static String select(Player owner,String name,String selected,int profession) {
  var service=PlayerBotService.getInstance();var session=service.find(owner,name);
  if(!service.claimTransfer(session))throw new IllegalArgumentException("This companion is busy; wait and retry.");
  try{synchronized(session) {
   if(!service.current(session) || !owned(session) || session.owner()!=owner)throw new IllegalArgumentException("Choose your own active ticket recruit. Normal alts and borrowed world bots keep their existing behavior.");
   var focus=Focus.valueOf(selected);var p=new Properties();p.setProperty("version","1");p.setProperty("account",Integer.toString(owner.getAccount().getId()));
   p.setProperty("character",Integer.toString(session.bot().getObjectId()));p.setProperty("focus",focus.name());p.setProperty("profession",Integer.toString(profession));
   validate(PlayerBotMetadata.values(p));var s=state(session);
   try{PlayerBotMetadata.save(owner.getAccount().getId(),session.bot().getObjectId(),"focus",p);}
   catch(IOException error){throw new IllegalStateException("Cannot save companion focus",error);}
   s.focus=focus;s.profession=profession;s.nextGather=s.nextCraft=0;s.gather=Map.of();s.craft=null;
   PlayerBotQuestCraftingDriver.invalidate(session.bot());PlayerBotQuestMaterials.invalidate(session.bot());
   return session.bot().getName()+": "+label(focus)+" focus selected. Current party orders, accepted objectives and native tasks keep priority.";
  }}finally{service.completeTransfer(session);}
 }
 private static String label(Focus focus){return switch(focus){case INSTANCE_PVE->"Instance PvE";case WORLD_PVE->"World PvE";case PVP->"PvP";default->focus.name().substring(0,1)+focus.name().substring(1).toLowerCase(Locale.ROOT);};}
 /** Small relevance changes cannot outrank recovery, combat, hazards or committed objectives. */
 static double priority(PlayerBotSession session,String activity,double base,boolean committed) {
  if(committed)return base;
  return base+switch(chosen(session)) {
   case GATHERING->activity.equals("gather")?4:0;
   case CRAFTING->activity.equals("craft")?4:0;
   case INSTANCE_PVE->activity.equals("gear")?6:0;
   case WORLD_PVE->activity.equals("quest")?4:0;
   case PVP->activity.equals("gear")?3:0;
   default->0;
  };
 }
 static boolean pvp(PlayerBotSession session){return chosen(session)==Focus.PVP;}
 private static int profession(State state,boolean crafting) {
  if(state.profession!=0)return state.profession;
  var bot=state.session.bot();return Arrays.stream(Profession.values()).filter(p->p.isCrafting()==crafting && bot.getSkillList().isSkillPresent(p.getSkillId()))
   .max(Comparator.comparingInt(p->bot.getSkillList().getSkillLevel(p.getSkillId()))).map(Profession::getSkillId).orElse(0);
 }
 /** Nearby XP-bearing real nodes add one native output goal, never an invented gathering award. */
 static Map<Integer,Long> gatheringGoals(PlayerBotSession session) {
  if(chosen(session)!=Focus.GATHERING)return Map.of();var s=state(session);var bot=session.bot();long now=System.currentTimeMillis();
  if(now<s.nextGather)return s.gather;s.nextGather=now+5000;s.gather=Map.of();
  if(bot.getInventory().isFull() || bot.isInInstance())return s.gather;
  int skill=profession(s,false);if(skill==0){s.reason="Choose Essence- or Aethertapping to learn, or use a profession already learned.";return s.gather;}
  int level=bot.getSkillList().isSkillPresent(skill)?bot.getSkillList().getSkillLevel(skill):0;
  var nodes=new ArrayList<Gatherable>();bot.getKnownList().forEachObject(o->{if(o instanceof Gatherable node && node.isSpawned()
   && node.getWorldId()==bot.getWorldId() && node.getInstanceId()==bot.getInstanceId() && bot.getKnownList().sees(node)
   && PositionUtil.isInRange(session.owner(),node,25) && node.getController().getGatheringPlayerId()==0)nodes.add(node);});
  nodes.sort(Comparator.comparingDouble(node->PositionUtil.getDistance(bot,node)));
  for(var node:nodes) {
   var source=PlayerBotQuestMaterials.gatherSource(node.getObjectTemplate());
   if(source.skill()!=skill || bot.getLevel()<Math.max(10,source.level()) || source.points()>level && Profession.getBySkillId(skill).getUpgradeCost(level)==null)continue;
   if(source.points()<=level && !PlayerBotQuestMaterials.practiceAllowed(skill,level,source.points(),com.aionemu.gameserver.configs.main.CraftConfig.DISABLE_AETHER_AND_ESSENCE_TAPPING_CAP)
    && Profession.getBySkillId(skill).getUpgradeCost(level)==null)continue;
   var outputs=source.outputs(source.required()>0 && !bot.getEquipment().getEquippedItemsByItemId(source.required()).isEmpty(),source.required()>0?bot.getInventory().getItemCountByItemId(source.required()):0);
   int item=outputs.stream().filter(id->DataManager.ITEM_DATA.getItemTemplate(id)!=null).min(Integer::compareTo).orElse(0);
   if(item==0)continue;long held=bot.getInventory().getItemCountByItemId(item);if(held>=100)continue;
   s.gather=Map.of(item,held+1);s.reason="Gather a nearby native node for actual profession progress and owned materials.";return s.gather;
  }
  s.reason="No nearby legal XP-bearing node for this profession; following the party until one is available.";return s.gather;
 }
 /** Existing recipe resolution owns training, designs, dependencies, budgets, tools and actual native consumption. */
 static Plan craftingPlan(PlayerBotSession session) {
  if(chosen(session)!=Focus.CRAFTING)return null;var s=state(session);long now=System.currentTimeMillis();
  if(now<s.nextCraft)return s.craft;s.nextCraft=now+5000;s.craft=null;
  var bot=session.bot();int skill=profession(s,true);if(skill==0){s.reason="Choose a crafting profession to learn, or use one already learned.";return null;}
  int level=bot.getSkillList().isSkillPresent(skill)?bot.getSkillList().getSkillLevel(skill):0;
  if(Profession.getBySkillId(skill).getUpgradeCost(level)!=null) {
   s.craft=new Plan(Stage.TRAIN,0,0,skill,0,Map.of(),Map.of(),"Train the selected native profession using this bot's own Kinah and normal profession limits");return s.craft;
  }
  for(var goal:new TreeMap<>(PlayerBotGearPolicy.state(session).goals).values()) {
   var parts=goal.split(":");if(parts.length!=3)continue;int item=Integer.parseInt(parts[0]);
   var plan=PlayerBotQuestCrafting.productPlan(bot,0,item,1);
   if(plan.skillId()==skill && plan.stage()!=Stage.NONE){s.craft=plan;s.reason="Craft an actual missing item for the current native equipment loadout.";return plan;}
  }
  String key=skill+":"+level+":"+bot.getRace()+":"+bot.getRecipeList().size();
  if(!key.equals(s.recipeKey)) {
   s.recipes=PlayerBotQuestCrafting.professionRecipes(skill).stream().filter(r->r.getSkillpoint()<=level && level-r.getSkillpoint()<=40
   && r.getMaxProductionCount()==null && r.getRace()!=null && (r.getRace()==bot.getRace() || r.getRace()==com.aionemu.gameserver.model.Race.PC_ALL))
   .filter(r->{var product=DataManager.ITEM_DATA.getItemTemplate(r.getProductId());return product!=null && PlayerBotSupplies.available(bot,product);})
   .sorted(Comparator.<com.aionemu.gameserver.model.templates.recipe.RecipeTemplate>comparingInt(r->bot.getRecipeList().isRecipePresent(r.getId())?0:1)
    .thenComparing(Comparator.comparingInt(com.aionemu.gameserver.model.templates.recipe.RecipeTemplate::getSkillpoint).reversed()).thenComparingInt(com.aionemu.gameserver.model.templates.recipe.RecipeTemplate::getId)).limit(24).toList();
   s.recipeKey=key;
  }
  for(var recipe:s.recipes) {
   ItemTemplate product=DataManager.ITEM_DATA.getItemTemplate(recipe.getProductId());
   if(product==null || !PlayerBotSupplies.available(bot,product) || bot.getInventory().getItemCountByItemId(recipe.getProductId())>=20)continue;
   var plan=PlayerBotQuestCrafting.recipePlan(bot,0,recipe);if(plan.stage()==Stage.BLOCKED || plan.stage()==Stage.NONE)continue;
   s.craft=plan;s.reason="Produce a small native consumable reserve with an actual recipe and owned materials.";return plan;
  }
  s.reason="No useful craftable loadout item or XP-bearing supply recipe is available; keeping earned materials and following the party.";return null;
 }
 public static Map<String,Object> snapshot(PlayerBotSession session) {
  if(!owned(session))return Map.of("available",false);var s=state(session);var bot=session.bot();var professions=new ArrayList<Map<String,Object>>();
  for(var p:Profession.values())professions.add(Map.of("id",p.getSkillId(),"name",p.name(),"crafting",p.isCrafting(),"level",bot.getSkillList().isSkillPresent(p.getSkillId())?bot.getSkillList().getSkillLevel(p.getSkillId()):0));
  return Map.of("available",true,"selected",s.focus.name(),"profession",s.profession,"choices",Arrays.stream(Focus.values()).map(f->Map.of("id",f.name(),"name",label(f))).toList(),
   "professions",professions,"knownRecipes",bot.getRecipeList().size(),"reason",s.reason,"scope","Optional nearby work while your party is idle. Follow, combat, accepted objectives and paid commitments keep priority; no independent expeditions or queue submission.");
 }
 static void close(PlayerBotSession session){STATES.remove(session.bot().getObjectId());}
 static void invalidateCraft(Player bot){var s=STATES.get(bot.getObjectId());if(s!=null)s.nextCraft=0;}
 private PlayerBotFocus() {}
}
