package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.nio.file.*;
import java.io.StringReader;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.questEngine.handlers.template.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.model.templates.recipe.*;

/** Real recipe/component objects and native-rule boundaries, without World, DB or IDs. */
public final class PlayerBotQuestCraftingCheck {
 private static int checks;
 private static final Map<String,String> XML=new HashMap<>();
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void set(Object target,String field,Object value)throws Exception {var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
 private static Component component(int item,int count)throws Exception {var c=new Component();set(c,"itemid",item);set(c,"quantity",count);return c;}
 private static ComponentsData group(Component... components)throws Exception {var g=new ComponentsData();set(g,"component",List.of(components));return g;}
 private static RecipeTemplate recipe(ComponentsData... groups)throws Exception {var r=new RecipeTemplate();set(r,"componentsData",List.of(groups));return r;}
 static final class Bot extends Player {
  QuestStateList quests;PlayerSkillList skills;RecipeList recipes;Inventory cube;Race race;PlayerCommonData common;Cooldowns cooldowns;
  Bot(){super(null,null);}
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public PlayerSkillList getSkillList(){return skills;}
  @Override public RecipeList getRecipeList(){return recipes;}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public Race getRace(){return race;}
  @Override public PlayerCommonData getCommonData(){return common;}
  @Override public Cooldowns getCraftCooldowns(){return cooldowns;}
 }
 static final class Inventory extends PlayerStorage {
  Map<Integer,Long> counts=new HashMap<>();boolean full;
  Inventory(Player bot){super(bot,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int item){return counts.getOrDefault(item,0L);}
  @Override public boolean isFull(){return full;}
  @Override public boolean decreaseByItemId(int id,long count){return decreaseByItemId(id,count,QuestStatus.START);}
  @Override public boolean decreaseByItemId(int id,long count,QuestStatus status){if(getItemCountByItemId(id)<count)return false;counts.put(id,getItemCountByItemId(id)-count);return true;}
 }
 static final class Work extends WorkOrders {
  int page;Work(){super(5000,List.of(203788,830062),List.of(),155004001);}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
  @Override public void updateQuestStatus(QuestEnv env){}
 }
 static final class Qualification extends CraftingRewards {
  int page;Qualification(){super(1941,203788,40002,400,203700,93);}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
 }
 static final class Contact extends com.aionemu.gameserver.model.gameobjects.Npc {
  int id;Contact(){super(null,null,null);}
  @Override public int getNpcId(){return id;}
  @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate(){return new com.aionemu.gameserver.model.templates.npc.NpcTemplate(){@Override public int getTemplateId(){return id;}};}
 }
 private static String nativeEntry(String path,String tag,int id)throws Exception {
  var xml=XML.get(path);if(xml==null){xml=Files.readString(Path.of(path));XML.put(path,xml);}var matcher=java.util.regex.Pattern.compile("<"+tag+"\\s+id=\""+id+"\"[^>]*>.*?</"+tag+">",java.util.regex.Pattern.DOTALL).matcher(xml);
  if(!matcher.find())throw new AssertionError("Missing native XML "+tag+" "+id);return matcher.group();
 }
 private static void nativePlans()throws Exception {
  var unsafeClass=Class.forName("sun.misc.Unsafe");var field=unsafeClass.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=field.get(null);
  var allocate=unsafeClass.getMethod("allocateInstance",Class.class);var bot=(Bot)allocate.invoke(unsafe,Bot.class);
  bot.quests=new QuestStateList();bot.skills=new PlayerSkillList();bot.recipes=new RecipeList();bot.cube=new Inventory(bot);bot.race=Race.ELYOS;bot.cooldowns=new Cooldowns();bot.common=(PlayerCommonData)allocate.invoke(unsafe,PlayerCommonData.class);
  var oldR=DataManager.RECIPE_DATA;var oldI=DataManager.ITEM_DATA;var oldQ=DataManager.QUEST_DATA;var oldX=DataManager.XML_QUESTS;
  int oldExperts=CraftConfig.MAX_EXPERT_CRAFTING_SKILLS,oldMasters=CraftConfig.MAX_MASTER_CRAFTING_SKILLS;
  try {
   CraftConfig.MAX_EXPERT_CRAFTING_SKILLS=2;CraftConfig.MAX_MASTER_CRAFTING_SKILLS=1;
   var recipeXml=nativeEntry("game-server/data/static_data/recipe/recipe_templates.xml","recipe_template",155004001);
   var questXml=nativeEntry("game-server/data/static_data/quest_data/quest_data.xml","quest",5000);
   var workXml=nativeEntry("game-server/data/static_data/quest_script_data/work_order.xml","work_order",5000);
   var designRecipeXml=nativeEntry("game-server/data/static_data/recipe/recipe_templates.xml","recipe_template",155001001);
   var designItemXml=nativeEntry("game-server/data/static_data/items/item_templates.xml","item_template",152201001);
   DataManager.RECIPE_DATA=(RecipeData)JAXBContext.newInstance(RecipeData.class).createUnmarshaller().unmarshal(new StringReader("<recipe_templates>"+recipeXml+designRecipeXml+"</recipe_templates>"));
   DataManager.ITEM_DATA=(ItemData)JAXBContext.newInstance(ItemData.class).createUnmarshaller().unmarshal(new StringReader("<item_templates>"+designItemXml+"</item_templates>"));
   var qualificationXml=nativeEntry("game-server/data/static_data/quest_data/quest_data.xml","quest",1941);
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests>"+questXml+qualificationXml+"</quests>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts>"+workXml+"</quest_scripts>"));
   check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.TRAIN,"Missing genuine profession produces training prerequisite");
   check(bot.recipes.size()==0 && bot.skills.getAllSkills().isEmpty() && bot.quests.getAllQuestState().isEmpty(),"Preparation discovery grants no recipe/skill/quest");
   bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40002,1,0,PersistentState.UPDATED)));
   var accept=PlayerBotQuestCrafting.plan(bot,5000);check(accept.stage()==PlayerBotQuestCrafting.Stage.ACCEPT,"Actual native recipe permits acceptance at learned skill one");
   check(accept.recipeId()==155004001 && accept.toolId()==150000011,"Work-order intent identifies the actual native recipe/tool");
   var work=new Work();var offered=work.getPlayerBotActions(bot);check(offered.size()==2 && offered.stream().allMatch(a->a.dialogAction()==DialogAction.QUEST_ACCEPT_1 && a.page()==DialogPage.ASK_QUEST_ACCEPT_WINDOW.id()),"Both actual native work-order masters declare their precise acceptance action/page");
   var state=new QuestState(5000,QuestStatus.START);bot.quests.addQuest(5000,state);
   check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.BLOCKED,"Accepted order with absent unpurchasable native recipe is explicit; no invented design ID zero");
   bot.recipes.getRecipeList().add(155004001);
   var missing=PlayerBotQuestCrafting.plan(bot,5000);check(missing.stage()==PlayerBotQuestCrafting.Stage.MATERIALS && missing.missing().equals(Map.of(182290000,1L)),"Missing material is the exact actual native supplied component");
   bot.cube.counts.put(182290000,4L);var craft=PlayerBotQuestCrafting.plan(bot,5000);
   check(craft.stage()==PlayerBotQuestCrafting.Stage.CRAFT && craft.materials().equals(Map.of(182290000,1L)),"Attempt consumes one recipe batch, never all supplied components");
   var resolve=PlayerBotQuestCraftingDriver.class.getDeclaredMethod("resolve",Player.class,PlayerBotQuestCrafting.Plan.class,Set.class,Set.class);resolve.setAccessible(true);
   var protection=new TreeSet<Integer>();var need=(PlayerBotQuestCraftingDriver.Need)resolve.invoke(null,bot,craft,new HashSet<Integer>(),protection);
   check(protection.equals(Set.of(182290000)),"Already owned native crafting ingredients remain protected even with no missing material");
   check(need.stage()==PlayerBotQuestCrafting.Stage.CRAFT && need.plan().equals(craft),"Custody protection preserves the actual native craft plan");
   check(bot.cube.counts.get(182290000)==4,"Recursive custody protection never consumes or fabricates ingredients");
   check(bot.cube.counts.get(182290000)==4 && state.getStatus()==QuestStatus.START && state.getQuestVars().getQuestVars()==0,"Native craft planning preserves custody/status/variables");
   bot.cube.full=true;check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.BLOCKED,"Native full-inventory restriction is retained");bot.cube.full=false;
   bot.cube.counts.put(182290317,2L);check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.CRAFT,"Partial earned products remain short of actual three-product requirement");
   bot.cube.counts.put(182290317,3L);check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.REWARD,"Actual collection readiness requests native reward transition");
   check(state.getStatus()==QuestStatus.START && bot.cube.counts.get(182290317)==3,"Ready discovery never completes or consumes the work order");
   var ready=work.getPlayerBotActions(bot);check(ready.size()==2 && ready.stream().allMatch(a->a.kind()==QuestBotAction.Kind.REWARD && a.requiredItems().equals(Map.of(182290317,3L))),"Ready native work order exposes exact reward NPCs and real collection custody");
   var contact=(Contact)allocate.invoke(unsafe,Contact.class);contact.id=203788;
   check(work.onDialogEvent(new QuestEnv(contact,bot,5000,DialogAction.QUEST_SELECT)),"Actual native handler admits collection-complete QUEST_SELECT");
   check(state.getStatus()==QuestStatus.REWARD && work.page==DialogPage.SELECT_QUEST_REWARD_WINDOW1.id(),"Only native handler transitions real collected work order to reward and emits its reward page");
   check(state.getCompleteCount()==0 && bot.cube.counts.get(182290317)==3,"Reward readiness does not award completion or consume finished products prematurely");
   check(bot.cube.counts.get(182290000)==0,"Native handler alone removes supplied quest work components at reward transition");
   state.setStatus(QuestStatus.REWARD);check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.REWARD,"Actual reward status remains a native hand-in");
   bot.recipes=new RecipeList();
   check(work.onDialogEvent(new QuestEnv(contact,bot,5000,DialogAction.QUEST_SELECT)),"Native REWARD QUEST_SELECT opens the ordinary end-dialog flow");
   check(bot.cube.counts.get(182290317)==0,"Actual native two-argument custody removal clears finished work products");
   check(state.getStatus()==QuestStatus.REWARD && state.getCompleteCount()==0 && work.page==DialogPage.getRewardPageByIndex(state.getRewardGroup()).id(),"Native REWARD page opening waits for actual reward selection before completion");
   bot.recipes.getRecipeList().add(155004001);
   state.setStatus(QuestStatus.START);bot.cube.counts.put(182290317,0L);bot.cube.counts.put(182290000,4L);
   check(PlayerBotQuestCrafting.productPlan(bot,5000,182290317,3).recipeId()==155004001,"Crafted dependency index selects the real learned native product recipe");
   check(PlayerBotQuestCrafting.productPlan(bot,5000,182290317,0).stage()==PlayerBotQuestCrafting.Stage.NONE,"No craft planned after requirement is absent");
   bot.quests=new QuestStateList();bot.recipes=new RecipeList();bot.race=Race.ASMODIANS;
   check(PlayerBotQuestCrafting.plan(bot,5000).stage()==PlayerBotQuestCrafting.Stage.BLOCKED,"Wrong native recipe race cannot be learned or accepted");
   check(bot.recipes.size()==0 && bot.skills.getSkillLevel(40002)==1 && bot.cube.counts.get(182290000)==4,"All native planning cases preserve learned skills and accepted components");
   bot.race=Race.ELYOS;bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40002,399,0,PersistentState.UPDATED)));
   var qualification=new Qualification();var qualificationStart=qualification.getPlayerBotActions(bot);
   check(qualificationStart.size()==1 && qualificationStart.getFirst().targetId()==203788 && qualificationStart.getFirst().dialogAction()==DialogAction.QUEST_ACCEPT_1,"Native qualification acceptance uses actual master and start action");
   var qstate=new QuestState(1941,QuestStatus.START);bot.quests.addQuest(1941,qstate);var qualificationStep=qualification.getPlayerBotActions(bot).getFirst();
   check(qualificationStep.targetId()==203700 && qualificationStep.dialogAction()==DialogAction.SELECT_QUEST_REWARD,"Native qualification reports to its actual designated end NPC");
   contact.id=203700;check(qualification.onDialogEvent(new QuestEnv(contact,bot,1941,DialogAction.QUEST_SELECT)),"Actual qualification handler authorizes its native START report page");
   check(qualification.page==qualificationStep.page(),"Qualification progression waits for exactly the page actually emitted by its handler");
   check(qstate.getStatus()==QuestStatus.START && bot.skills.getSkillLevel(40002)==399,"Qualification discovery/page opening grants no skill rank or reward");
   qstate.setStatus(QuestStatus.REWARD);check(qualification.getPlayerBotActions(bot).getFirst().kind()==QuestBotAction.Kind.REWARD,"Earned native qualification reward is available even after filling profession capacity");
   bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40008,110,0,PersistentState.UPDATED)));
   var learn=PlayerBotQuestCrafting.productPlan(bot,1976,152020079,1);
   check(DataManager.RECIPE_DATA.getRecipeTemplateById(155001001).getItemId()==0,"Actual maintained recipe XML omits design itemid");
   check(learn.stage()==PlayerBotQuestCrafting.Stage.LEARN_RECIPE && learn.missing().equals(Map.of(152201001,1L)),"Actual native CraftLearnAction supplies the correct obtainable design dependency");
   protection.clear();var learnNeed=(PlayerBotQuestCraftingDriver.Need)resolve.invoke(null,bot,learn,new HashSet<Integer>(),protection);
   check(learnNeed.item()==152201001 && protection.contains(152201001) && protection.containsAll(learn.materials().keySet()),"Waiting real design and all already-owned parent materials remain protected");
   check(bot.recipes.size()==0 && bot.skills.getSkillLevel(40008)==110,"Design discovery does not grant a recipe or profession XP");
   var qualificationModels=new ArrayList<com.aionemu.gameserver.questEngine.handlers.models.CraftingRewardsData>();var qualificationTemplates=new StringBuilder();
   for(var capital:List.of("sanctum","pandaemonium")) {
    var xml=Files.readString(Path.of("game-server/data/static_data/quest_script_data/"+capital+".xml"));var matcher=java.util.regex.Pattern.compile("<crafting_rewards\\b[^>]*/>").matcher(xml);
    while(matcher.find()) {
     var model=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts>"+matcher.group()+"</quest_scripts>"));
     var idMatcher=java.util.regex.Pattern.compile(" id=\"(\\d+)\"").matcher(matcher.group());if(!idMatcher.find())throw new AssertionError("Native qualification ID absent");
     int id=Integer.parseInt(idMatcher.group(1));qualificationModels.add((com.aionemu.gameserver.questEngine.handlers.models.CraftingRewardsData)model.getQuest(id));
     qualificationTemplates.append(nativeEntry("game-server/data/static_data/quest_data/quest_data.xml","quest",id));
    }
   }
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests>"+qualificationTemplates+"</quests>"));
   check(qualificationModels.size()==42,"All maintained native capital crafting qualification XML declarations are covered");
   bot.skills=new PlayerSkillList();
   for(var model:qualificationModels) {
    int id=PlayerBotQuestConversations.number(model,"id"),start=PlayerBotQuestConversations.number(model,"startNpcId"),end=PlayerBotQuestConversations.number(model,"endNpcId");if(end==0)end=start;
    int skill=PlayerBotQuestConversations.number(model,"skillId"),reward=PlayerBotQuestConversations.number(model,"levelReward"),movie=PlayerBotQuestConversations.number(model,"questMovie");
    var handler=new CraftingRewards(id,start,skill,reward,end,movie);bot.quests=new QuestStateList();
    var declaration=handler.getPlayerBotActions(bot).getFirst();boolean driven=DataManager.QUEST_DATA.getQuestById(id).isDataDriven();
    check(declaration.targetId()==start && declaration.dialogAction()==DialogAction.QUEST_ACCEPT_1 && declaration.page()==(driven?4762:1011),"Native qualification "+id+" preserves actual acceptance NPC/page");
    var active=new QuestState(id,QuestStatus.START);bot.quests.addQuest(id,active);declaration=handler.getPlayerBotActions(bot).getFirst();
    check(declaration.targetId()==end && declaration.dialogAction()==DialogAction.SELECT_QUEST_REWARD && declaration.page()==(driven?1011:2375),"Native qualification "+id+" preserves actual end NPC/report page");
    active.setStatus(QuestStatus.REWARD);declaration=handler.getPlayerBotActions(bot).getFirst();
    check(declaration.kind()==QuestBotAction.Kind.REWARD && declaration.targetId()==end,"Native qualification "+id+" declares its earned reward endpoint");
    check(active.getCompleteCount()==0 && bot.skills.getAllSkills().isEmpty(),"Native qualification "+id+" readonly declarations grant no rank or completion");
   }
  } finally {DataManager.RECIPE_DATA=oldR;DataManager.ITEM_DATA=oldI;DataManager.QUEST_DATA=oldQ;DataManager.XML_QUESTS=oldX;CraftConfig.MAX_EXPERT_CRAFTING_SKILLS=oldExperts;CraftConfig.MAX_MASTER_CRAFTING_SKILLS=oldMasters;}
 }
 public static void main(String[] args)throws Exception {
  var required=Map.of(100,4L,200,2L);
  check(PlayerBotQuestCrafting.missing(required,id->0).equals(required),"No owned materials cannot become virtual custody");
  check(PlayerBotQuestCrafting.missing(required,id->id==100?3:2).equals(Map.of(100,1L)),"Count only exact remaining custody");
  check(PlayerBotQuestCrafting.missing(required,id->100).isEmpty(),"Owned surplus does not need another purchase");
  var duplicate=recipe(group(component(100,2),component(100,3),component(200,1)));
  check(PlayerBotQuestCrafting.materials(duplicate,id->0).equals(Map.of(100,5L,200,1L)),"Duplicate component rows must aggregate before native consumption");
  check(PlayerBotQuestCrafting.missing(PlayerBotQuestCrafting.materials(duplicate,id->4),id->4).equals(Map.of(100,1L)),"Native duplicate rows cannot undercharge materials");
  var alternatives=recipe(group(component(100,5),component(200,1)),group(component(300,2),component(400,2)));
  check(PlayerBotQuestCrafting.materials(alternatives,id->id>=300?2:0).equals(Map.of(300,2L,400,2L)),"Select the actually held alternative component group");
  check(PlayerBotQuestCrafting.materials(alternatives,id->0).equals(Map.of(300,2L,400,2L)),"Dependency planning prefers fewer missing real components");
  var ambiguous=recipe(group(component(100,5),component(200,1)),group(component(100,1),component(300,1)));
  check(PlayerBotQuestCrafting.materials(ambiguous,id->1).equals(Map.of(100,5L,200,1L)),"Shared selectors retain native FIRST group semantics");
  var overlap=recipe(group(component(100,5),component(200,1)),group(component(300,1),component(100,1)));
  check(PlayerBotQuestCrafting.materials(overlap,id->100).equals(Map.of(100,5L,200,1L)),"Earlier selector present as later ingredient cannot evade native selection");
  check(PlayerBotQuestCrafting.materials(recipe(),id->100).isEmpty(),"Absent recipe components never imply free production");
  var frozen=PlayerBotQuestCrafting.materials(duplicate,id->0);boolean immutable=false;try{frozen.put(900,1L);}catch(UnsupportedOperationException expected){immutable=true;}
  check(immutable,"Intent custody snapshots are immutable");
  int[] skills={40001,40002,40003,40004,40007,40008,40010},tools={150000009,150000011,150000010,150000015,150000013,150000012,150000023};
  for(int i=0;i<skills.length;i++)check(PlayerBotQuestCrafting.toolId(skills[i])==tools[i],"Correct actual native profession tool "+skills[i]);
  check(PlayerBotQuestCrafting.toolId(40009)==0,"Morphing is not advertised as a supported static-tool task");
  check(PlayerBotQuestCrafting.toolId(30002)==0,"Essencetapping cannot use a crafting station");
  for(int experts=0;experts<4;experts++)for(int masters=0;masters<3;masters++) {
   check(PlayerBotQuestCrafting.qualificationAllowed(400,experts,masters,2,1)==(experts+masters<2),"Expert qualification includes existing master slots");
   check(PlayerBotQuestCrafting.qualificationAllowed(500,experts,masters,2,1)==(masters<1),"Master qualification obeys native master cap");
  }
  check(!PlayerBotQuestCrafting.qualificationAllowed(450,0,0,2,1),"No invented qualification rank");
  check(!PlayerBotQuestCrafting.qualificationAllowed(400,0,0,0,1),"Disabled native expert capacity respected");
  check(!PlayerBotQuestCrafting.qualificationAllowed(500,0,0,2,0),"Disabled native master capacity respected");
  var materials=new HashMap<>(Map.of(100,1L));var p=new PlayerBotQuestCrafting.Plan(PlayerBotQuestCrafting.Stage.CRAFT,5000,155004001,40002,150000011,materials,Map.of(),"Native task");
  materials.put(100,99L);check(p.materials().get(100)==1,"Later inventory planning cannot mutate an already selected intent");
  check(PlayerBotQuestCrafting.recoveryAllowed(true,true,true,true,true,false,false,1,0),"Observed failed autonomous work order can recover through native abandonment");
  for(int refused=0;refused<7;refused++)check(!PlayerBotQuestCrafting.recoveryAllowed(refused!=0,refused!=1,refused!=2,refused!=3,refused!=4,refused==5,refused==6,1,0),"Recovery requires every observed/native permission gate "+refused);
  check(!PlayerBotQuestCrafting.recoveryAllowed(true,true,true,true,true,false,false,60001,0),"Old inventory loss cannot become failure evidence");
  check(!PlayerBotQuestCrafting.recoveryAllowed(true,true,true,true,true,false,false,-1,0),"Clock rollback cannot reset quest progress");
  check(!PlayerBotQuestCrafting.recoveryAllowed(true,true,true,true,true,false,false,1,3),"At most three native work-order resets per hour");
  nativePlans();
  System.out.println("OK: "+checks+" native recipe/material/qualification/tool checks; no World/DB/IDs");
 }
}
