package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.*;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.XMLQuests;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.questEngine.handlers.models.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Actual property loader/annotations and native quest state objects; no services, World, IDs or SQL. */
public final class PlayerBotQuestFeatureConfigCheck {
 private static int checks;
 private static final List<String> FIELDS=List.of("QUESTS_ENABLED","QUEST_ITEM_STARTS","QUEST_CUSTOM","QUEST_CRAFTING","QUEST_MATERIALS","QUEST_EXCHANGE","QUEST_FLIGHT");
 private static final List<String> KEYS=List.of("enable","item_starts","custom","crafting","materials","exchange","flight");
 private static final String PREFIX="gameserver.playerbots.quests.";
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static java.util.Properties load(Path root)throws Exception {
  var method=Config.class.getDeclaredMethod("loadProperties",Path.class);method.setAccessible(true);
  return (java.util.Properties)method.invoke(null,root);
 }
 private static Path directory(Path root,boolean features)throws Exception {
  for(var name:List.of("administration","main","network"))Files.createDirectories(root.resolve("config/"+name));
  if(features)Files.createDirectories(root.resolve("config/playerbots/media"));return root;
 }
 private static String switches(boolean value) {
  var text=new StringBuilder();for(var key:KEYS)text.append(PREFIX).append(key).append(" = ").append(value).append('\n');return text.toString();
 }
 private static boolean field(int index)throws Exception{return PlayerBotConfig.class.getField(FIELDS.get(index)).getBoolean(null);}
 private static java.util.Properties allEnabled(){var properties=new java.util.Properties();for(var key:KEYS)properties.setProperty(PREFIX+key,"true");return properties;}
 private static XMLQuest model(XMLQuest model,int id)throws Exception {
  var field=XMLQuest.class.getDeclaredField("id");field.setAccessible(true);field.setInt(model,id);return model;
 }
 private static final class MarkedHandler extends AbstractQuestHandler {
  boolean crafting,gathering;MarkedHandler(){super(6);}
  @Override public void register(){throw new AssertionError("No native engine registration lifecycle");}
  @Override public boolean isBotCraftingQuest(){return crafting;}
  @Override public boolean isBotGatheringQuest(){return gathering;}
  @Override public List<QuestBotAction> getPlayerBotActions(Player bot){return List.of(new QuestBotAction(QuestBotAction.Kind.CONVERSATION,1,0,0,Map.of(123,2L),"Native held prerequisite"));}
 }
 private static final class CustodyActor extends Player {
  QuestStateList states;CustodyActor(){super(null,null);}
  @Override public QuestStateList getQuestStateList(){return states;}
 }
 private record SavedQuest(int id,QuestStatus status,int vars,int flags,int count,Timestamp next,Integer reward,Timestamp complete,PersistentState persistence) {
  static SavedQuest of(QuestState state){return new SavedQuest(state.getQuestId(),state.getStatus(),state.getQuestVars().getQuestVars(),state.getFlags(),state.getCompleteCount(),state.getNextRepeatTime(),state.getRewardGroup(),state.getLastCompleteTime(),state.getPersistentState());}
 }
 public static void main(String[] args)throws Exception {
  var originals=new LinkedHashMap<Field,Object>();
  for(var field:PlayerBotConfig.class.getDeclaredFields())if(Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))originals.put(field,field.get(null));
  var originalXml=DataManager.XML_QUESTS;var originalQuests=DataManager.QUEST_DATA;Path temporary=Files.createTempDirectory("playerbot-feature-config-");
  var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=unsafeField.get(null);
  var engine=QuestEngine.getInstance();var handlersField=QuestEngine.class.getDeclaredField("questHandlers");handlersField.setAccessible(true);
  @SuppressWarnings("unchecked") var handlers=(Map<Integer,AbstractQuestHandler>)handlersField.get(engine);var oldHandlers=new HashMap<>(handlers);
  try {
   for(int i=0;i<FIELDS.size();i++) {
    var annotation=PlayerBotConfig.class.getField(FIELDS.get(i)).getAnnotation(Property.class);
    check(annotation!=null && annotation.key().equals(PREFIX+KEYS.get(i)),"Real feature annotation key "+FIELDS.get(i));
    check(annotation.defaultValue().equals("true"),"Feature preserves default enabled behavior "+FIELDS.get(i));
   }
   var root=directory(temporary.resolve("complete"),true);
   Files.writeString(root.resolve("config/main/playerbots.properties"),switches(true)+"gameserver.playerbots.population.max=123\n");
   Files.writeString(root.resolve("config/playerbots/features.properties"),switches(false));
   Files.writeString(root.resolve("config/playerbots/media/ignored.properties"),switches(true)+"nested.media.marker=unexpected\n");
   Files.writeString(root.resolve("config/playerbots/care-character-1.properties"),switches(true)+"account=123\ncharacter=456\nenchant=true\n");
   Files.writeString(root.resolve("config/playerbots/readme.txt"),"non.properties.marker=unexpected\n");
   Files.writeString(root.resolve("config/network/test.properties"),"fixture.unknown.network=retained\n");
   var properties=load(root);var unused=ConfigurableProcessor.process(properties,PlayerBotConfig.class);
   check(PlayerBotConfig.POPULATION_MAX==123,"Existing main population configuration remains loaded");
   check(unused.equals(Set.of("fixture.unknown.network")),"Actual parser recognizes every feature key and retains unknown keys");
   check(properties.getProperty("nested.media.marker")==null,"Native loader ignores nested media properties");
   check(properties.getProperty("account")==null && properties.getProperty("character")==null && properties.getProperty("enchant")==null,"Native loader ignores sibling per-character care metadata");
   check(properties.getProperty("non.properties.marker")==null,"Native loader ignores non-properties files");
   for(int i=0;i<FIELDS.size();i++)check(!field(i),"Feature directory overrides main value "+FIELDS.get(i));
   for(int override=0;override<FIELDS.size();override++) {
    Files.writeString(root.resolve("config/mygs.properties"),PREFIX+KEYS.get(override)+"=true\n");
    properties=load(root);unused=ConfigurableProcessor.process(properties,PlayerBotConfig.class);
    for(int i=0;i<FIELDS.size();i++)check(field(i)==(i==override),"mygs is the final native override "+override+"/"+i);
    check(unused.equals(Set.of("fixture.unknown.network")),"Override keys remain known to native parser "+override);
    check(PlayerBotConfig.POPULATION_MAX==123,"Feature override preserves main population "+override);
   }
   var legacy=directory(temporary.resolve("optional-missing"),false);
   Files.writeString(legacy.resolve("config/main/playerbots.properties"),"gameserver.playerbots.population.max=321\n");
   properties=load(legacy);unused=ConfigurableProcessor.process(properties,PlayerBotConfig.class);
   check(unused.isEmpty() && PlayerBotConfig.POPULATION_MAX==321,"Native loader accepts absent optional playerbots directory and mygs file");
   for(int i=0;i<FIELDS.size();i++)check(field(i),"Absent optional feature file restores annotation default "+FIELDS.get(i));
   for(int toggle=0;toggle<FIELDS.size();toggle++)for(boolean value:List.of(false,true)) {
    properties=allEnabled();properties.setProperty(PREFIX+KEYS.get(toggle),Boolean.toString(value));
    unused=ConfigurableProcessor.process(properties,PlayerBotConfig.class);
    check(unused.isEmpty(),"Each feature boolean is a recognized native key "+toggle+"/"+value);
    for(int i=0;i<FIELDS.size();i++)check(field(i)==(i==toggle?value:true),"Native boolean parsing independently toggles "+toggle+"/"+value+"/"+i);
   }
   var xml=new XMLQuests();xml.setData(List.of(model(new ReportToData(),1),model(new WorkOrdersData(),2),model(new CraftingRewardsData(),3),model(new RelicRewardsData(),4),model(new FountainRewardsData(),5)));
   DataManager.XML_QUESTS=xml;
   for(int id=6;id<=8;id++) {
    var handler=(MarkedHandler)unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,MarkedHandler.class);handler.crafting=id==7;handler.gathering=id==8;handlers.put(id,handler);
    check(engine.isPlayerBotCraftingQuest(id)==(id==7) && engine.isPlayerBotGatheringQuest(id)==(id==8),"Actual registered handler supplies explicit native profession family "+id);
   }
   var states=new ArrayList<QuestState>();for(var status:QuestStatus.values()) {
    var state=new QuestState(2,status,0x12345,42,3,new Timestamp(23456),7,new Timestamp(12345));state.setPersistentState(PersistentState.NOACTION);states.add(state);
   }
   var snapshots=states.stream().map(SavedQuest::of).toList();
   var bot=(CustodyActor)unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,CustodyActor.class);bot.states=new QuestStateList();
   bot.states.addQuest(7,new QuestState(7,QuestStatus.START));DataManager.QUEST_DATA=new QuestsData();
   var materialNeed=new PlayerBotQuestCraftingDriver.Need(PlayerBotQuestCrafting.Stage.MATERIALS,7,123,2,40008,Set.of(500),null,"Native vendor material");
   var craftNeed=new PlayerBotQuestCraftingDriver.Need(PlayerBotQuestCrafting.Stage.CRAFT,7,124,1,40008,Set.of(),null,"Native crafting");
   ConfigurableProcessor.process(allEnabled(),PlayerBotConfig.class);
   check(PlayerBotQuestCraftingDriver.admitted(materialNeed) && PlayerBotQuestCraftingDriver.admitted(craftNeed),"Enabled native material vendor and craft requests are admitted");
   check(PlayerBotQuestCraftingDriver.requirements(bot,7).equals(Map.of(123,2L)),"Raw actual registered-handler custody owns full native prerequisites");
   for(int toggle=0;toggle<FIELDS.size();toggle++) {
    properties=allEnabled();properties.setProperty(PREFIX+KEYS.get(toggle),"false");ConfigurableProcessor.process(properties,PlayerBotConfig.class);
    for(int id=1;id<=8;id++) {
     boolean expected=toggle!=0 && !(toggle==2 && id>=6) && !(toggle==3 && (id==2 || id==3 || id==7)) && !(toggle==4 && id==8) && !(toggle==5 && (id==4 || id==5));
     check(PlayerBotQuestFeatures.enabled(id)==expected,"Disabled feature classifies native quest family "+toggle+"/"+id);
     check(PlayerBotQuestFeatures.disabled(id).isEmpty()==expected,"Specific disabled reason agrees with admission "+toggle+"/"+id);
     var acquisition=new HashMap<Integer,Long>();var custody=Map.of(id,2L);PlayerBotQuestMaterials.acquireRequirements(acquisition,custody,PlayerBotQuestFeatures.enabled(id));
     check(acquisition.equals(expected?custody:Map.of()) && custody.get(id)==2L,"Disabled native family removes acquisition targets while preserving exact custody "+toggle+"/"+id);
     if(!expected)check(PlayerBotQuestFeatures.disabled(id).contains(PREFIX+KEYS.get(toggle)),"Disabled reason names the applicable owner switch "+toggle+"/"+id);
    }
    check(states.stream().map(SavedQuest::of).toList().equals(snapshots),"Disabled admission preserves all native statuses, vars, flags, counts, timers, reward and persistence "+toggle);
    check(PlayerBotQuestCraftingDriver.admitted(materialNeed)==(toggle!=0 && toggle!=2 && toggle!=3 && toggle!=4),"Actual native material/vendor request gate honors master/custom/crafting/material switches "+toggle);
    check(PlayerBotQuestCraftingDriver.admitted(craftNeed)==(toggle!=0 && toggle!=2 && toggle!=3),"Materials switch independently preserves craft request admission "+toggle);
    check(PlayerBotQuestCraftingDriver.requirements(bot,7).equals(Map.of(123,2L)),"Disabled decisions preserve actual registered native custody requirements "+toggle);
   }
   DataManager.XML_QUESTS=null;properties=allEnabled();properties.setProperty(PREFIX+"custom","false");ConfigurableProcessor.process(properties,PlayerBotConfig.class);
   check(!PlayerBotQuestFeatures.enabled(6),"Missing XML metadata follows custom/campaign admission without service initialization");
   check(states.stream().map(SavedQuest::of).toList().equals(snapshots),"Missing metadata cannot mutate saved native quest state");
   System.out.println("OK: "+checks+" native quest feature configuration/admission checks; no World/services/SQL/IDs");
  }finally {
   DataManager.XML_QUESTS=originalXml;DataManager.QUEST_DATA=originalQuests;handlers.clear();handlers.putAll(oldHandlers);for(var original:originals.entrySet())original.getKey().set(null,original.getValue());
   try(var paths=Files.walk(temporary)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
  }
 }
 private PlayerBotQuestFeatureConfigCheck(){}
}
