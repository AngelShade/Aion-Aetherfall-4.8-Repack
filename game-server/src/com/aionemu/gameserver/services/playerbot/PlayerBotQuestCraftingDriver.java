package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.craft.Profession;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.spawnengine.SpawnHandlerType;
import com.aionemu.gameserver.questEngine.handlers.models.WorkOrdersData;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.skillengine.task.CraftingTask;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.*;

/** Earns crafted quest dependencies with native custody; the caller owns all quest/mission pauses. */
final class PlayerBotQuestCraftingDriver {
 private record Observed(int quest,CraftingTask task,Map<Integer,Long> products,long started,boolean consumed) {}
 private record Failed(int quest,long ended) {}
 private record Retry(long since,int count,long next) {}
 private static final Map<Integer,Observed> OBSERVED=new ConcurrentHashMap<>();
 private static final Map<Integer,Failed> FAILED=new ConcurrentHashMap<>();
 private static final Map<Integer,Retry> RETRIES=new ConcurrentHashMap<>();
 private record Snapshot(long next,long fingerprint,List<Need> needs,Set<Integer> protectedItems) {}
 private static final Map<Integer,Snapshot> SNAPSHOTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Map<String,Long>> EXCLUDED=new ConcurrentHashMap<>();
 private static final Map<Integer,List<PlayerBotWorldTravel.Place>> TOOLS=new ConcurrentHashMap<>();
 private record Route(String need,PlayerBotWorldTravel.Place destination,long progress,double best) {}
 private static final Map<Integer,Route> ROUTES=new ConcurrentHashMap<>();
 static void invalidate(Player bot){SNAPSHOTS.remove(bot.getObjectId());}
 private static long fingerprint(Player bot) {
  long result=1;for(var state:bot.getQuestStateList().getUncompletedQuests())result=31*result+state.getQuestId()+state.getStatus().ordinal()+state.getQuestVars().getQuestVars();
  for(var item:bot.getDirtyItemsToUpdate())result=31*result+item.getItemId()+item.getItemCount();
  for(var profession:Profession.values())if(profession.isCrafting())result=31*result+(bot.getSkillList().isSkillPresent(profession.getSkillId())?bot.getSkillList().getSkillLevel(profession.getSkillId()):0);
  return result;
 }
 private static String key(Need need){return need.quest()+":"+need.stage()+":"+need.plan().recipeId()+":"+need.item();}
 private static boolean excluded(Player bot,Need need){return EXCLUDED.getOrDefault(bot.getObjectId(),Map.of()).getOrDefault(key(need),0L)>System.currentTimeMillis();}
 private static void exclude(Player bot,Need need){long now=System.currentTimeMillis();var exclusions=EXCLUDED.computeIfAbsent(bot.getObjectId(),id->new ConcurrentHashMap<>());
  exclusions.entrySet().removeIf(e->e.getValue()<=now);if(exclusions.size()>=32)exclusions.entrySet().stream().min(Map.Entry.comparingByValue()).ifPresent(e->exclusions.remove(e.getKey(),e.getValue()));
  exclusions.put(key(need),now+30000);
 }
 private static void observe(Player bot) {
  var observation=OBSERVED.get(bot.getObjectId());if(observation==null || observation.task().isInProgress())return;
  OBSERVED.remove(bot.getObjectId());invalidate(bot);boolean produced=observation.products().entrySet().stream().anyMatch(e->bot.getInventory().getItemCountByItemId(e.getKey())>e.getValue());
  long now=System.currentTimeMillis();
  if(observation.consumed() && !produced && now>=observation.started() && now-observation.started()<=60000)FAILED.put(bot.getObjectId(),new Failed(observation.quest(),now));
  else FAILED.remove(bot.getObjectId());
 }
 private static boolean recover(PlayerBotSession session) {
  var bot=session.bot();var failure=FAILED.get(bot.getObjectId());if(failure==null)return false;long now=System.currentTimeMillis();
  var state=bot.getQuestStateList().getQuestState(failure.quest());var model=DataManager.XML_QUESTS.getQuest(failure.quest());
  var template=DataManager.QUEST_DATA.getQuestById(failure.quest());if(!(model instanceof WorkOrdersData) || template==null)return false;
  var retry=RETRIES.getOrDefault(bot.getObjectId(),new Retry(now,0,0));if(now-retry.since()>3600000)retry=new Retry(now,0,0);
  if(now<retry.next())return false;var plan=plan(bot,failure.quest());
  var supplied=PlayerBotQuestConversations.list(model,"giveComponents").stream().map(o->((com.aionemu.gameserver.model.templates.quest.QuestItems)o).getItemId()).collect(java.util.stream.Collectors.toSet());
  boolean exhausted=plan.stage()==Stage.MATERIALS && plan.missing().keySet().stream().anyMatch(id->supplied.contains(id) && sellers(id).isEmpty() && !PlayerBotQuestMaterials.hasDeclaredSource(id)
   && productPlan(bot,failure.quest(),id,plan.missing().get(id)+bot.getInventory().getItemCountByItemId(id)).stage()==Stage.NONE);
  if(!recoveryAllowed(session.world(),true,true,exhausted,state!=null && state.getStatus()==QuestStatus.START,
   template.isCannotGiveup(),bot.getController().hasTask(TaskId.QUEST_TIMER),now-failure.ended(),retry.count()))return false;
  RETRIES.put(bot.getObjectId(),new Retry(retry.since(),retry.count()+1,now+60000));
  if(!QuestService.abandonQuest(bot,failure.quest()))return false;
  FAILED.remove(bot.getObjectId());invalidate(bot);PlayerBotQuestSync.notice(bot,"A native crafting attempt produced no required product and exhausted the supplied components. I abandoned work order "+failure.quest()+" through the native service and will request it again.","work-order-craft-failure",failure.quest());
  return true;
 }
 record Need(Stage stage,int quest,int item,long count,int skill,Set<Integer> npcs,Plan plan,String reason) {
  Need {npccheck(npcs);npcs=Set.copyOf(npcs);}
  private static void npccheck(Set<Integer> ids){if(ids.stream().anyMatch(i->i<=0))throw new IllegalArgumentException("Invalid native service NPC");}
 }
 static List<Need> requests(PlayerBotSession session) {
  var bot=session.bot();long now=System.currentTimeMillis(),fingerprint=fingerprint(bot);var snapshot=SNAPSHOTS.get(bot.getObjectId());
  if(snapshot!=null && now<snapshot.next() && snapshot.fingerprint()==fingerprint)return snapshot.needs();
  var result=new ArrayList<Need>();var protection=new TreeSet<Integer>();
  for(var state:bot.getQuestStateList().getUncompletedQuests()) {
   int quest=state.getQuestId();if(state.getStatus()!=QuestStatus.START || !PlayerBotQuestSync.wanted(session.owner(),bot,quest))continue;
   if(DataManager.XML_QUESTS.getQuest(quest) instanceof WorkOrdersData) {
    var plan=plan(bot,quest);if(plan.stage()!=Stage.REWARD && plan.stage()!=Stage.NONE && plan.stage()!=Stage.ACCEPT)result.add(resolve(bot,plan,new HashSet<>(),protection));
   }else for(var entry:requirements(bot,quest).entrySet()) {
    long absent=entry.getValue()-bot.getInventory().getItemCountByItemId(entry.getKey());if(absent<=0)continue;
    protection.add(entry.getKey());var plan=productPlan(bot,quest,entry.getKey(),entry.getValue());if(plan.stage()!=Stage.NONE)result.add(resolve(bot,plan,new HashSet<>(),protection));
   }
  }
  if(session.world() && result.isEmpty())bot.getKnownList().forEachNpc(npc->{
   if(!npc.isSpawned() || npc.isDead() || bot.isEnemy(npc) || !bot.getKnownList().sees(npc) || !PositionUtil.isInRange(bot,npc,40))return;
   var registered=QuestEngine.getInstance().getQuestNpc(npc.getNpcId());if(registered==null)return;
   for(int quest:registered.getOnQuestStart())if(DataManager.XML_QUESTS.getQuest(quest) instanceof WorkOrdersData && PlayerBotQuestSync.wanted(session.owner(),bot,quest) && prequest(bot,quest)) {
    var plan=plan(bot,quest);if(plan.stage()==Stage.TRAIN)result.add(resolve(bot,plan,new HashSet<>(),protection));
   }
  });
  var frozen=List.copyOf(result);SNAPSHOTS.put(bot.getObjectId(),new Snapshot(now+2000,fingerprint,frozen,Set.copyOf(protection)));return frozen;
 }
 /** Current native stage requirements, preserving alternative exchanges and collecting-step gates. */
 static Map<Integer,Long> requirements(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);if(state==null || state.getStatus()!=QuestStatus.START)return Map.of();
  var required=new TreeMap<Integer,Long>();for(var action:PlayerBotQuestActions.custody(bot,quest))action.requiredItems().forEach((id,count)->required.merge(id,count,Math::max));
  var template=DataManager.QUEST_DATA.getQuestById(quest);if(template==null)return Map.copyOf(required);
  var model=DataManager.XML_QUESTS.getQuest(quest);
  if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.RelicRewardsData || model instanceof com.aionemu.gameserver.questEngine.handlers.models.FountainRewardsData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData && PlayerBotQuestConversations.number(model,"nextNpcId")>0 && state.getQuestVarById(0)==0)return Map.copyOf(required);
  boolean ordinary=model instanceof com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData
   || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToData || model instanceof com.aionemu.gameserver.questEngine.handlers.models.ReportToManyData;
  java.util.function.IntPredicate current=id->{var drops=template.getQuestDrop().stream().filter(d->d.getItemId()==id).toList();
   return drops.isEmpty()?ordinary:drops.stream().anyMatch(d->d.getCollectingStep()==0 || d.getCollectingStep()==state.getQuestVarById(0));};
  if(template.getCollectItems()!=null)for(var item:template.getCollectItems().getCollectItem())if(current.test(item.getItemId()))required.merge(item.getItemId(),item.getCount().longValue(),Math::max);
  if(template.getInventoryItems()!=null)for(var item:template.getInventoryItems().getInventoryItems())if(item.getCount()!=null && current.test(item.getItemId()))required.merge(item.getItemId(),item.getCount().longValue(),Math::max);
  if(template.getQuestWorkItems()!=null)for(var item:template.getQuestWorkItems().getQuestWorkItem())if(current.test(item.getItemId()))required.merge(item.getItemId(),item.getCount(),Math::max);
  return Map.copyOf(required);
 }
 /** All native non-profession acceptance requirements still govern preparation; acceptance rechecks everything. */
 private static boolean prequest(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);if(state!=null && !state.isStartable())return false;
  var template=DataManager.QUEST_DATA.getQuestById(quest);if(template==null || bot.getLevel()<template.getMinlevelPermitted() || template.getMaxlevelPermitted()!=0 && bot.getLevel()>template.getMaxlevelPermitted()
   || template.getRacePermitted()!=null && template.getRacePermitted()!=com.aionemu.gameserver.model.Race.PC_ALL && template.getRacePermitted()!=bot.getRace()
   || !template.getClassPermitted().isEmpty() && !template.getClassPermitted().contains(bot.getPlayerClass())
   || template.getGenderPermitted()!=null && template.getGenderPermitted()!=bot.getGender()
   || template.getRequiredRank()!=0 && bot.getAbyssRank().getRank().getId()<template.getRequiredRank())return false;
  if(template.getXMLStartConditions().stream().filter(c->c.check(bot,false)).count()<template.getRequiredConditionCount()
   || !QuestService.inventoryItemCheck(new com.aionemu.gameserver.questEngine.model.QuestEnv(null,bot,quest),false))return false;
  if(template.getNpcFactionId()!=0){var faction=bot.getNpcFactions().getFactionById(template.getNpcFactionId());if(faction==null || !faction.isActive()
   || !template.isTimeBased() && !bot.getNpcFactions().canStartQuest(template))return false;}
  return true;
 }
 static Need request(PlayerBotSession session) {
  var requests=requests(session);return requests.stream().filter(n->admitted(n) && n.stage()!=Stage.BLOCKED && n.stage()!=Stage.WAIT && !excluded(session.bot(),n)).findFirst().orElse(null);
 }
 static boolean admitted(Need need) {
  return PlayerBotQuestFeatures.enabled(need.quest()) && (need.stage()!=Stage.MATERIALS || com.aionemu.gameserver.configs.main.PlayerBotConfig.QUEST_MATERIALS);
 }
 /** Root gathering/drop routing can earn these exact terminal dependencies normally. */
 static Map<Integer,Long> neededMaterials(PlayerBotSession session) {
  var result=new TreeMap<Integer,Long>();for(var need:requests(session))if(PlayerBotQuestFeatures.enabled(need.quest()) && (need.stage()==Stage.MATERIALS || need.stage()==Stage.LEARN_RECIPE) && need.item()>0 && need.npcs().isEmpty())result.merge(need.item(),need.count(),Math::max);return Map.copyOf(result);
 }
 static Need training(Player bot,int quest,int skill,int points){return resolve(bot,new Plan(Stage.TRAIN,quest,0,skill,0,Map.of(),Map.of(),"Native profession "+skill+" requires "+points+" points"),new HashSet<>());}
 /** Includes owned parent ingredients and designs, not just the currently missing terminal component. */
 static Set<Integer> protectedItems(PlayerBotSession session){requests(session);var snapshot=SNAPSHOTS.get(session.bot().getObjectId());return snapshot==null?Set.of():snapshot.protectedItems();}
 static boolean protectedItem(PlayerBotSession session,int item){return protectedItems(session).contains(item);}
 private static Need resolve(Player bot,Plan plan,Set<Integer> visited) {
  return resolve(bot,plan,visited,new HashSet<>());
 }
 private static Need resolve(Player bot,Plan plan,Set<Integer> visited,Set<Integer> protection) {
  protection.addAll(plan.materials().keySet());
  if(plan.stage()==Stage.LEARN_RECIPE)protection.addAll(plan.missing().keySet());
  if(plan.recipeId()>0 && (!visited.add(plan.recipeId()) || visited.size()>12))return need(Stage.BLOCKED,plan,0,0,Set.of(),"Native recipe dependency cycle or depth exceeds twelve: "+visited);
  if(plan.stage()==Stage.MATERIALS) {
   // A recipe only consumes the current attempt's materials. Failed/critical crafts
   // re-enter this fresh plan; no projected outputs or duplicate material credit.
   for(var entry:new TreeMap<>(plan.missing()).entrySet()) {
    var sellers=sellers(entry.getKey());if(!sellers.isEmpty())return need(Stage.MATERIALS,plan,entry.getKey(),entry.getValue(),sellers,plan.reason());
    var component=productPlan(bot,plan.questId(),entry.getKey(),bot.getInventory().getItemCountByItemId(entry.getKey())+entry.getValue());
    if(component.stage()!=Stage.NONE)return resolve(bot,component,visited,protection);
    return need(Stage.MATERIALS,plan,entry.getKey(),entry.getValue(),Set.of(),"Earn native material "+entry.getKey()+" x"+entry.getValue()+" through its real drop/gathering source; no native shop or recipe exists");
   }
  }
  if(plan.stage()==Stage.LEARN_RECIPE) {
   int item=designItem(bot,plan.recipeId());
   return need(Stage.LEARN_RECIPE,plan,item,1,item>0?sellers(item):Set.of(),plan.reason());
  }
  if(plan.stage()==Stage.TRAIN) {
   var profession=Profession.getBySkillId(plan.skillId());int level=bot.getSkillList().isSkillPresent(plan.skillId())?bot.getSkillList().getSkillLevel(plan.skillId()):0;
   if(profession!=null && profession.getUpgradeCost(level)!=null)return need(Stage.TRAIN,plan,0,0,trainers(plan.skillId()),plan.reason());
   if(level>=549)return need(Stage.BLOCKED,plan,0,0,Set.of(),"Native profession "+plan.skillId()+" is at its final native crafting XP cap "+level+"; no higher native qualification is declared");
   if(Set.of(399,499).contains(level))return need(Stage.BLOCKED,plan,0,0,Set.of(),"Native profession "+plan.skillId()+" is at qualification cap "+level+"; complete its eligible qualification quest");
   // Between native rank boundaries, earn actual native crafting XP from a
   // learned useful recipe. Native XP refuses recipes more than forty points low.
   var practice=professionRecipes(plan.skillId()).stream().filter(r->r.getMaxProductionCount()==null
    && r.getSkillpoint()<=level && level-r.getSkillpoint()<=40 && bot.getRecipeList().isRecipePresent(r.getId()))
    .sorted(Comparator.<com.aionemu.gameserver.model.templates.recipe.RecipeTemplate>comparingInt(r->materials(r,bot.getInventory()::getItemCountByItemId).isEmpty()?1:0)
     .thenComparing(Comparator.comparingInt(com.aionemu.gameserver.model.templates.recipe.RecipeTemplate::getSkillpoint).reversed()).thenComparingInt(com.aionemu.gameserver.model.templates.recipe.RecipeTemplate::getId)).findFirst().orElse(null);
   if(practice!=null)return resolve(bot,recipePlan(bot,plan.questId(),practice),visited,protection);
   var learnable=professionRecipes(plan.skillId()).stream().filter(r->r.getMaxProductionCount()==null
    && !designs(r.getId()).isEmpty() && r.getSkillpoint()<=level && level-r.getSkillpoint()<=40 && recipeLearnable(bot,r))
    .sorted(Comparator.comparingInt(com.aionemu.gameserver.model.templates.recipe.RecipeTemplate::getSkillpoint).reversed()).findFirst().orElse(null);
   if(learnable!=null)return resolve(bot,recipePlan(bot,plan.questId(),learnable),visited,protection);
   return need(Stage.BLOCKED,plan,0,0,Set.of(),"Native profession "+plan.skillId()+" at "+level+" needs an owned learnable practice recipe within forty skill points; no learned XP-bearing recipe is available");
  }
  return need(plan.stage(),plan,0,0,plan.toolId()>0?Set.of(plan.toolId()):Set.of(),plan.reason());
 }
 private static Need need(Stage stage,Plan plan,int item,long count,Set<Integer> npcs,String reason){return new Need(stage,plan.questId(),item,count,plan.skillId(),npcs,plan,reason);}
 static List<PlayerBotWorldTravel.Place> places(Need need) {
  if(need.stage()!=Stage.CRAFT)return need.npcs().stream().sorted().flatMap(n->PlayerBotWorldTravel.places(n).stream()).toList();
  return TOOLS.computeIfAbsent(need.plan().toolId(),toolId->toolPlaces(toolId));
 }
 private static List<PlayerBotWorldTravel.Place> toolPlaces(int tool) {
  var result=new ArrayList<PlayerBotWorldTravel.Place>();for(var map:DataManager.WORLD_MAPS_DATA)if(!map.isInstance())
   for(var group:DataManager.SPAWNS_DATA.getSpawnsForNpc(map.getMapId(),tool))if(group.getHandlerType()==SpawnHandlerType.STATIC && !group.isTemporarySpawn())
    for(var spot:group.getSpawnTemplates())if(!spot.isAerialSpawn())result.add(new PlayerBotWorldTravel.Place(map.getMapId(),tool,spot.getX(),spot.getY(),spot.getZ()));
  return PlayerBotWorldTravel.catalog(result);
 }
 static boolean tick(PlayerBotSession session,PlayerBotNavigation navigation,boolean allowed) {
  var bot=session.bot();if(!allowed || !com.aionemu.gameserver.configs.main.PlayerBotConfig.QUESTS_ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotConfig.QUEST_CRAFTING
   || !bot.isSpawned() || bot.isDead() || bot.isCasting() || bot.isTrading() || bot.isLooting()
   || bot.isUsingFlightTransporterOrWindstream() || bot.getController().isUnderStance() || bot.getController().isInCombat() || bot.getController().hasScheduledTask(TaskId.ITEM_USE))return false;
  observe(bot);if(bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress())return false;if(recover(session))return true;
  var wanted=request(session);if(wanted==null || wanted.stage()==Stage.BLOCKED || wanted.stage()==Stage.WAIT)return false;
  if(wanted.stage()==Stage.LEARN_RECIPE && current(session,wanted) && learnRecipe(bot,wanted.plan().recipeId())){invalidate(bot);return true;}
  if(wanted.stage()==Stage.CRAFT) {
   var tools=new ArrayList<StaticObject>();bot.getKnownList().forEachObject(o->{if(o instanceof StaticObject tool && wanted.npcs().contains(tool.getObjectTemplate().getTemplateId()) && tool.isSpawned()
    && tool.getWorldId()==bot.getWorldId() && tool.getInstanceId()==bot.getInstanceId() && bot.getKnownList().sees(tool))tools.add(tool);});
   var tool=tools.stream().min(Comparator.comparingDouble(t->PositionUtil.getDistance(bot,t))).orElse(null);
   if(tool!=null) {
    if(!PositionUtil.isInRange(bot,tool,4,false) || !GeoService.getInstance().canSee(bot,tool)){boolean moved=navigation.move(tool.getX(),tool.getY(),tool.getZ());if(!moved)exclude(bot,wanted);return moved;}
    navigation.stop();if(!current(session,wanted))return false;var template=DataManager.QUEST_DATA.getQuestById(wanted.quest());var products=new TreeMap<Integer,Long>();
    if(template!=null && template.getCollectItems()!=null)for(var item:template.getCollectItems().getCollectItem())products.put(item.getItemId(),bot.getInventory().getItemCountByItemId(item.getItemId()));
    var before=new TreeMap<Integer,Long>();wanted.plan().materials().keySet().forEach(id->before.put(id,bot.getInventory().getItemCountByItemId(id)));
    boolean started=start(bot,wanted.plan(),tool);
    if(started && DataManager.XML_QUESTS.getQuest(wanted.quest()) instanceof WorkOrdersData) {
     boolean consumed=before.entrySet().stream().anyMatch(e->bot.getInventory().getItemCountByItemId(e.getKey())<e.getValue());
     OBSERVED.put(bot.getObjectId(),new Observed(wanted.quest(),(CraftingTask)bot.getInteractionTask(),Map.copyOf(products),System.currentTimeMillis(),consumed));
    }
    if(started)invalidate(bot);else exclude(bot,wanted);return started;
   }
  }else {
   var candidates=new ArrayList<Npc>();bot.getKnownList().forEachNpc(n->{if(wanted.npcs().contains(n.getNpcId()) && !n.isDead() && n.isSpawned() && !bot.isEnemy(n)
    && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && bot.getKnownList().sees(n))candidates.add(n);});
   var npc=candidates.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
   if(npc!=null) {
    if(!PositionUtil.isInTalkRange(bot,npc) || !GeoService.getInstance().canSee(bot,npc)){boolean moved=navigation.approach(npc,2);if(!moved)exclude(bot,wanted);return moved;}
    navigation.stop();if(!current(session,wanted))return false;var care=PlayerBotQuestSync.state(session);String day=LocalDate.now().toString();if(!care.spentDay.equals(day)){care.spentDay=day;care.spent=0;care.save();}
    long before=bot.getInventory().getKinah(),allowance=PlayerBotCare.allowance(before,care.reserve,care.dailyBudget,care.spent);boolean result=false;
    try {
     if(wanted.stage()==Stage.TRAIN){var profession=Profession.getBySkillId(wanted.skill());int level=bot.getSkillList().isSkillPresent(wanted.skill())?bot.getSkillList().getSkillLevel(wanted.skill()):0;
      Integer cost=profession==null?null:profession.getUpgradeCost(level);if(cost!=null && cost<=allowance)result=train(bot,npc,wanted.skill());
     }else if(wanted.item()>0 && allowance>0)result=buy(bot,npc,wanted.item(),wanted.count(),allowance);
     if(result)invalidate(bot);else exclude(bot,wanted);return result;
    }finally {long spent=Math.max(0,before-bot.getInventory().getKinah());if(spent>0){care.spent+=spent;care.save();}}
   }
  }
  // Companions respect the owner scope; only autonomous actors travel across maps.
  if(!session.world())return false;
  var destination=places(wanted).stream().filter(p->Double.isFinite(PlayerBotWorldTravel.distance(bot,p))).min(Comparator.comparingDouble(p->PlayerBotWorldTravel.distance(bot,p))).orElse(null);
  if(destination!=null){long now=System.currentTimeMillis();double distance=PlayerBotWorldTravel.distance(bot,destination);var route=ROUTES.get(bot.getObjectId());
   if(route==null || !route.need().equals(key(wanted)) || !route.destination().equals(destination))route=new Route(key(wanted),destination,now,distance);
   if(distance+1<route.best())route=new Route(route.need(),route.destination(),now,distance);ROUTES.put(bot.getObjectId(),route);
   if(distance<=4 || now-route.progress()>30000){exclude(bot,wanted);ROUTES.remove(bot.getObjectId());return false;}
  }
  boolean moved=destination!=null && PlayerBotWorldTravel.move(bot,destination,navigation);if(!moved)exclude(bot,wanted);return moved;
 }
 private static boolean current(PlayerBotSession session,Need need){invalidate(session.bot());return admitted(need) && requests(session).contains(need);}
 static void close(Player bot){OBSERVED.remove(bot.getObjectId());FAILED.remove(bot.getObjectId());RETRIES.remove(bot.getObjectId());SNAPSHOTS.remove(bot.getObjectId());EXCLUDED.remove(bot.getObjectId());ROUTES.remove(bot.getObjectId());}
 private PlayerBotQuestCraftingDriver() {}
}
