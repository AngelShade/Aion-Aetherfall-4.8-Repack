package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.*;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.skill.PlayerSkillList;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.actions.SkillUseAction;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.models.QuestSkillData;
import com.aionemu.gameserver.questEngine.handlers.template.SkillUse;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Maintained item/skill/quest templates and real native admission on inert actors.
 * Cast execution is a recording endpoint: no World, IDs, SQL, scheduler or effects. */
public final class PlayerBotQuestItemTargetCheck {
 private static int checks;private static sun.misc.Unsafe unsafe;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Field field(Class<?> type,String name)throws Exception{for(;type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static String entry(Path file,String tag,String key,int id)throws Exception{var m=java.util.regex.Pattern.compile("<"+tag+"\\s+"+key+"=\""+id+"\"[^>]*>.*?</"+tag+">",java.util.regex.Pattern.DOTALL).matcher(Files.readString(file));if(!m.find())throw new AssertionError("Missing native template "+id);return m.group();}
 private static class Life extends PlayerBotSorcererCheck.Life {Life(){super();}@Override public boolean isAboutToDie(){return false;}}
 private static class Actor extends PlayerBotSupportCooperationCheck.Actor {
  QuestStateList quests;Cube cube;PlayerCommonData common;ObserveController observe;TransformModel transform;boolean cooling,visible=true;
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public PlayerCommonData getCommonData(){return common;}
  @Override public ObserveController getObserveController(){return observe;}
  @Override public TransformModel getTransformModel(){return transform;}
  @Override public byte getLevel(){return 40;}
  @Override public boolean isInPrison(){return false;}
  @Override public boolean hasCooldown(Item item){return cooling;}
  @Override public boolean canSee(VisibleObject target){return visible;}
 }
 private static class Cube extends PlayerStorage {
  List<Item> items=new ArrayList<>();Cube(Player player){super(player,StorageType.CUBE);}
  @Override public List<Item> getItems(){return List.copyOf(items);}
  @Override public Item getItemByObjId(int id){return items.stream().filter(i->i.getObjectId()==id).findFirst().orElse(null);}
 }
 private static class Owned extends Item {ItemTemplate template;Owned(){super(1,null);}@Override public int getObjectId(){return 1999999451;}@Override public ItemTemplate getItemTemplate(){return template;}@Override public int getItemId(){return template.getTemplateId();}@Override public long getItemCount(){return 1;}}
 private static class Recording extends SkillUseAction {
  int casts;VisibleObject selected;boolean admitted=true;
  Recording(SkillUseAction original){skillid=original.getSkillId();level=original.getLevel();}
  @Override public boolean actOn(Player player,Item item,Item targetItem,VisibleObject target){casts++;selected=target;return admitted;}
 }
 private static class Handler extends SkillUse {
  Handler(int quest,List<QuestSkillData> requirements){super(quest,List.of(203707),null,requirements);}
  @Override public void updateQuestStatus(QuestEnv env){}
 }
 private static Actor actor(int id)throws Exception{
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.pc=PlayerClass.CLERIC;a.race=Race.ELYOS;a.playing=a.spawned=a.attack=a.worldBot=a.visible=true;a.world=a.instance=1;a.nearby=new ArrayList<>();
  a.life=(Life)unsafe.allocateInstance(Life.class);a.life.hp=a.life.mp=1000;a.stats=(PlayerBotClericCheck.Stats)unsafe.allocateInstance(PlayerBotClericCheck.Stats.class);a.effects=new PlayerBotSorcererCheck.Effects(a);a.move=new PlayerBotSorcererCheck.Move(a);a.chains=new ChainSkills();a.control=new PlayerBotFieldCombatCheck.Clock();a.learned=new PlayerSkillList();
  field(Creature.class,"aggroList").set(a,new AggroList(a));a.common=new PlayerCommonData(id);a.common.setPlayerClass(a.pc);a.quests=new QuestStateList();a.cube=new Cube(a);a.observe=new ObserveController();a.transform=new TransformModel(a);
  a.seen=new KnownList(a){@Override public void forEachPlayer(java.util.function.Consumer<Player> c){List.copyOf(a.nearby).forEach(c);}@Override public boolean sees(VisibleObject other){return a.nearby.contains(other);}};
  return a;
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);PlayerBotSupportCooperationCheck.unsafe=unsafe;Path root=Path.of(args[0]);
  var oldSkills=DataManager.SKILL_DATA;var oldItems=DataManager.ITEM_DATA;var oldQuests=DataManager.QUEST_DATA;var oldXml=DataManager.XML_QUESTS;var oldMaterials=DataManager.MATERIAL_DATA;
  boolean enabled=PlayerBotConfig.QUESTS_ENABLED,custom=PlayerBotConfig.QUEST_CUSTOM;
  var handlers=(Map<Integer,AbstractQuestHandler>)field(QuestEngine.class,"questHandlers").get(QuestEngine.getInstance());var previous=new HashMap<>(handlers);
  var sessions=(Map<Integer,PlayerBotSession>)field(PlayerBotService.class,"sessions").get(PlayerBotService.getInstance());Actor bot=null;
  try{
   PlayerBotConfig.QUESTS_ENABLED=PlayerBotConfig.QUEST_CUSTOM=true;
   DataManager.MATERIAL_DATA=new MaterialData();
   DataManager.SKILL_DATA=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("<skill_data>"+entry(root.resolve("data/static_data/skills/skill_templates.xml"),"skill_template","skill_id",9912)+entry(root.resolve("data/static_data/skills/skill_templates.xml"),"skill_template","skill_id",1699)+"</skill_data>"));
   DataManager.ITEM_DATA=(ItemData)JAXBContext.newInstance(ItemData.class).createUnmarshaller().unmarshal(new StringReader("<item_templates>"+entry(root.resolve("data/static_data/items/item_templates.xml"),"item_template","id",161000005)+entry(root.resolve("data/static_data/items/item_templates.xml"),"item_template","id",164006008)+"</item_templates>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts>"+entry(root.resolve("data/static_data/quest_script_data/sanctum.xml"),"skill_use","id",3910)+entry(root.resolve("data/static_data/quest_script_data/sanctum.xml"),"skill_use","id",3912)+"</quest_scripts>"));
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests>"+entry(root.resolve("data/static_data/quest_data/quest_data.xml"),"quest","id",3910)+entry(root.resolve("data/static_data/quest_data/quest_data.xml"),"quest","id",3912)+"</quests>"));
   bot=actor(1999999452);var dead=actor(1999999453);dead.dead=true;dead.x=4;bot.nearby.add(dead);var session=PlayerBotSupportCooperationCheck.session(bot,bot);check(sessions.putIfAbsent(bot.id,session)==null,"Cold inert service slot only");
   for(int itemId:List.of(161000005,164006008)){
    int quest=itemId==161000005?3910:3912;var requirements=(List<QuestSkillData>)(List<?>)PlayerBotQuestConversations.list(DataManager.XML_QUESTS.getQuest(quest),"skills");var handler=new Handler(quest,requirements);handlers.put(quest,handler);
    var state=new QuestState(quest,QuestStatus.START);bot.quests.addQuest(quest,state);var owned=(Owned)unsafe.allocateInstance(Owned.class);owned.template=DataManager.ITEM_DATA.getItemTemplate(itemId);var original=(SkillUseAction)owned.template.getActions().getItemActions().getFirst();var action=new Recording(original);owned.template.getActions().getItemActions().set(0,action);bot.cube.items.add(owned);
    check(original.getSkillId()==(itemId==161000005?9912:1699),"Actual owned native item supplies required skill");
    check(!original.canAct(bot,owned,null),"Old selected-target path reproduces rejection with no selected target");
    check(original.canActOn(bot,owned,null,dead),"Actual native explicit item-skill admission accepts legal dead friend");
    check(PlayerBotQuestItems.skillTarget(bot,owned,action,null)==dead,"Planner discovers nearby legal corpse without enemy or learned spell");
    var chosen=PlayerBotQuestItems.choose(bot,bot,System.currentTimeMillis()+10000);check(chosen!=null && chosen.target()==dead && chosen.item()==owned,"Actual owned quest-item chooser binds explicit recipient");
    check(bot.getTarget()==null && owned.getItemCount()==1 && state.getQuestVars().getQuestVars()==0 && bot.life.mp==1000,"Planning preserves target, item, resources and native progress");
    check(PlayerBotQuestItems.skillItemRequirement(bot,original.getSkillId()).contains("visible dead allied player"),"Diagnostics explain owned item's actual recipient requirement");
    bot.cube.items.clear();check(!PlayerBotQuestItems.use(bot,bot,chosen),"A transferred owned item cannot be used from cached plan");bot.cube.items.add(owned);
    PlayerBotConfig.QUESTS_ENABLED=false;check(!PlayerBotQuestItems.use(bot,bot,chosen) && owned.getItemCount()==1,"Owner feature switch stops admission without removing owned item");PlayerBotConfig.QUESTS_ENABLED=true;
    bot.visible=false;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Native visibility recheck rejects cached target");bot.visible=true;
    dead.hostile=true;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Hostile actor never receives quest-item resurrection");dead.hostile=false;
    if(original.getSkillId()==1699){bot.life.mp=99;check(PlayerBotQuestItems.skillTarget(bot,owned,action,null)==null && bot.life.mp==99,"Actual native ten-percent MP cost rejects unaffordable item without spending");bot.life.mp=1000;}
    dead.dead=false;check(!PlayerBotQuestItems.use(bot,bot,chosen) && action.casts==0,"Cached plan rechecks newly living target");dead.dead=true;
    dead.world=2;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Map transfer invalidates bound corpse");dead.world=1;
    dead.instance=2;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Instance transfer invalidates bound corpse");dead.instance=1;
    dead.pvp=true;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Public assistance retains native PvP boundary");dead.pvp=false;
    dead.x=30;check(PlayerBotQuestItems.skillTarget(bot,owned,action,null)==null,"Full native item range rejects distant corpse");dead.x=4;
    dead.spawned=false;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Despawn rejects cached corpse");dead.spawned=true;
    bot.cooling=true;check(!PlayerBotQuestItems.use(bot,bot,chosen),"Actual native owned-item cooldown remains");bot.cooling=false;
    bot.nearby.clear();check(!PlayerBotQuestItems.use(bot,bot,chosen),"Known-list loss rejects cached corpse");bot.nearby.add(dead);
    var other=actor(1999999454);bot.nearby.add(other);other.casting=SkillEngine.getInstance().getSkill(other,original.getSkillId(),1,dead,owned.template);check(!PlayerBotQuestItems.use(bot,bot,chosen),"A newly competing native resurrection invalidates cached plan");other.casting=null;bot.nearby.remove(other);
    action.admitted=false;check(!PlayerBotQuestItems.use(bot,bot,chosen) && action.casts==1 && state.getQuestVars().getQuestVars()==0,"Native failed admission cannot claim completion or invent credit");action.admitted=true;
    PlayerBotQuestItems.close(bot);check(PlayerBotQuestItems.use(bot,bot,chosen) && action.casts==2 && action.selected==dead,"Execution passes captured target to native cast endpoint");
    check(state.getQuestVars().getQuestVars()==0 && owned.getItemCount()==1 && bot.getTarget()==null,"Recording cast admission cannot fake native event credit or item consumption");
    check(handler.onUseSkillEvent(new QuestEnv(null,bot,quest),original.getSkillId()) && state.getQuestVarById(0)==1,"Only actual registered native skill-use event earns one requirement");
    PlayerBotQuestItems.close(bot);bot.cube.items.clear();bot.quests=new QuestStateList();
   }
   System.out.println("OK: "+checks+" maintained owned skill-item target/native admission, custody, stale-plan and skill-event checks; no World/IDs/SQL/scheduler/casts");
  }finally{if(bot!=null){PlayerBotQuestItems.close(bot);sessions.remove(bot.id);}handlers.clear();handlers.putAll(previous);DataManager.SKILL_DATA=oldSkills;DataManager.ITEM_DATA=oldItems;DataManager.QUEST_DATA=oldQuests;DataManager.XML_QUESTS=oldXml;DataManager.MATERIAL_DATA=oldMaterials;PlayerBotConfig.QUESTS_ENABLED=enabled;PlayerBotConfig.QUEST_CUSTOM=custom;}
 }
 private PlayerBotQuestItemTargetCheck(){}
}
