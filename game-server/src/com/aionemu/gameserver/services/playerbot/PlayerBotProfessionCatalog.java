package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.gather.GatherableTemplate;
import com.aionemu.gameserver.model.templates.recipe.RecipeTemplate;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;

/** Shared native supply indexes, prepared before actors are published. */
final class PlayerBotProfessionCatalog {
 record Seller(int npc,int rate) {}
 private record Key(Race race,int skill) {}
 private record Shop(Race race,int item) {}
 private static final Map<Key,NavigableMap<Integer,List<GatherableTemplate>>> NODES=new HashMap<>();
 private static final Map<Key,NavigableMap<Integer,List<RecipeTemplate>>> RECIPES=new HashMap<>();
 private static final Map<Shop,List<Seller>> SELLERS=new HashMap<>();
 private static final Map<Integer,List<RecipeTemplate>> PRODUCTS=new HashMap<>();
 private static final Set<Integer> WORK_ORDERS=new HashSet<>(),QUALIFICATIONS=new HashSet<>(),CRAFT_ITEMS=new HashSet<>(),SUPPLIED=new HashSet<>();
 private static final Map<Shop,List<GatherableTemplate>> OUTPUTS=new HashMap<>();
 private static boolean prepared;
 static synchronized void prepare() {
  if(prepared)return;PlayerBotQuestCrafting.prepare();
  var seen=new HashSet<String>();var shopRaces=new HashMap<Integer,Race>();
  for(var map:DataManager.WORLD_MAPS_DATA)if(!map.isInstance()) {
   int id=map.getMapId();Race race=progression(id);boolean town=id==110010000 || id==120010000 || id==700010000 || id==710010000;
   for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(id))if(!group.isTemporarySpawn() && group.getHandlerType()==null && !group.getSpawnTemplates().isEmpty()) {
    int npc=group.getNpcId();var node=DataManager.GATHERABLE_DATA.getGatherableTemplate(npc);
    if(race!=null && node!=null && (node.getHarvestSkill()==30002 || node.getHarvestSkill()==30003) && seen.add(race+":"+npc)) {
     NODES.computeIfAbsent(new Key(race,node.getHarvestSkill()),k->new TreeMap<>()).computeIfAbsent(node.getSkillLevel(),k->new ArrayList<>()).add(node);
     if(node.getMaterials()!=null)for(var material:node.getMaterials().getMaterial())if(material.getRate()>0)OUTPUTS.computeIfAbsent(new Shop(race,material.getItemId()),k->new ArrayList<>()).add(node);
    }
    if(town)shopRaces.put(npc,id==110010000 || id==700010000?Race.ELYOS:Race.ASMODIANS);
   }
  }
  for(var shop:DataManager.TRADE_LIST_DATA.getTradeListTemplate().values())if(shop.getTradeNpcType()==TradeNpcType.NORMAL && shopRaces.containsKey(shop.getNpcId()))
   for(var tab:shop.getTradeTablist()){var goods=DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());
    if(goods==null || goods.getLegionLevel()!=0 || goods.getItemIdList()==null)continue;
    var limited=new HashSet<Integer>();for(var item:goods.getLimitedItems())limited.add(item.getItemId());
    for(int item:goods.getItemIdList())if(!limited.contains(item))SELLERS.computeIfAbsent(new Shop(shopRaces.get(shop.getNpcId()),item),k->new ArrayList<>()).add(new Seller(shop.getNpcId(),shop.getSellPriceRate()));
   }
  for(var entry:DataManager.QUEST_DATA.getQuestTemplates()) {
   int id=entry.getId();var model=DataManager.XML_QUESTS.getQuest(id);
   if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.WorkOrdersData work) {
    WORK_ORDERS.add(work.getRecipeId());
    if(entry.getQuestWorkItems()!=null)for(var supplied:entry.getQuestWorkItems().getQuestWorkItem())SUPPLIED.add(supplied.getItemId());
   }
   if(entry.getCombineSkill()>0 && (entry.getCombineSkillPoint()==399 || entry.getCombineSkillPoint()==499))QUALIFICATIONS.add(id);
  }
  for(var recipe:DataManager.RECIPE_DATA.getRecipeTemplates()) {
   // Limited and work-order recipes run on quest-supplied stock, which must not count as earned trade goods.
   if(recipe.isMorph() || recipe.getMaxProductionCount()!=null || WORK_ORDERS.contains(recipe.getId()))continue;
   PRODUCTS.computeIfAbsent(recipe.getProductId(),k->new ArrayList<>()).add(recipe);CRAFT_ITEMS.add(recipe.getProductId());
   for(int i=1;i<=recipe.getComboProductSize();i++){var product=recipe.getComboProduct(i);if(product!=null){PRODUCTS.computeIfAbsent(product,k->new ArrayList<>()).add(recipe);CRAFT_ITEMS.add(product);}}
   for(var group:recipe.getComponents())for(var item:group.getComponent())CRAFT_ITEMS.add(item.getItemId());
   for(var race:List.of(Race.ELYOS,Race.ASMODIANS))if(recipe.getRace()==race || recipe.getRace()==Race.PC_ALL)
    RECIPES.computeIfAbsent(new Key(race,recipe.getSkillId()),k->new TreeMap<>()).computeIfAbsent(recipe.getSkillpoint(),k->new ArrayList<>()).add(recipe);
  }
  NODES.values().forEach(m->m.replaceAll((k,v)->v.stream().sorted(Comparator.comparingInt(GatherableTemplate::getTemplateId)).toList()));
  RECIPES.values().forEach(m->m.replaceAll((k,v)->v.stream().sorted(Comparator.comparingInt(RecipeTemplate::getId)).toList()));
  OUTPUTS.replaceAll((k,v)->v.stream().sorted(Comparator.comparingInt(GatherableTemplate::getSkillLevel).thenComparingInt(GatherableTemplate::getTemplateId)).toList());prepared=true;
 }
 static Race progression(int map){return map>=210020000 && map<=210070000?Race.ELYOS:map>=220020000 && map<=220080000?Race.ASMODIANS:null;}
 static List<GatherableTemplate> nodes(Player bot,int skill,int limit,int cursor,boolean practice) {
  var index=NODES.get(new Key(bot.getRace(),skill));if(index==null)return List.of();int points=points(bot,skill);var out=new ArrayList<GatherableTemplate>();
  var eligible=index.subMap(practice?Math.max(1,points-40):1,true,Math.max(1,points),true).descendingMap();
  for(var list:eligible.values())for(int i=0;i<list.size() && out.size()<limit;i++){var node=list.get(Math.floorMod(cursor+i,list.size()));if(bot.getLevel()>=node.getLevelLimit())out.add(node);}return List.copyOf(out);
 }
 /** An ingredient may come from any node the bot's skill and level allow, however far below its own skill. */
 static List<GatherableTemplate> nodesFor(Player bot,int item) {
  return OUTPUTS.getOrDefault(new Shop(bot.getRace(),item),List.of()).stream().filter(n->bot.getLevel()>=n.getLevelLimit() && points(bot,n.getHarvestSkill())>=n.getSkillLevel() && n.getCheckType()!=2).limit(8).toList();
 }
 static List<RecipeTemplate> recipes(Player bot,int skill,int limit,int cursor) {
  var index=RECIPES.get(new Key(bot.getRace(),skill));if(index==null)return List.of();int points=points(bot,skill);var out=new ArrayList<RecipeTemplate>();
  for(var list:index.subMap(Math.max(1,points-40),true,Math.max(1,points),true).descendingMap().values())
   for(int i=0;i<list.size() && out.size()<limit;i++)out.add(list.get(Math.floorMod(cursor+i,list.size())));return List.copyOf(out);
 }
 static int points(Player bot,int skill){return bot.getSkillList().isSkillPresent(skill)?bot.getSkillList().getSkillLevel(skill):0;}
 static List<Seller> sellers(Race race,int item){return SELLERS.getOrDefault(new Shop(race,item),List.of());}
 static List<RecipeTemplate> products(int item){return PRODUCTS.getOrDefault(item,List.of());}
 static boolean keepRecipe(int recipe){var r=DataManager.RECIPE_DATA.getRecipeTemplateById(recipe);return r!=null && !r.isMorph() && r.getMaxProductionCount()==null && !WORK_ORDERS.contains(recipe);}
 static boolean qualification(int quest){return QUALIFICATIONS.contains(quest);}
 static boolean craftItem(int item){return CRAFT_ITEMS.contains(item);}
 static boolean supplied(int item){return SUPPLIED.contains(item);}
 static boolean trainer(Race race,int skill){return PlayerBotQuestCrafting.trainers(skill).stream().anyMatch(n->PlayerBotWorldTravel.places(n).stream().anyMatch(p->race==Race.ELYOS?(p.map()==110010000 || p.map()==700010000):(p.map()==120010000 || p.map()==710010000)));}
 private PlayerBotProfessionCatalog(){}
}
