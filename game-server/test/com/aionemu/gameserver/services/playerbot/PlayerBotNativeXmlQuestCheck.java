package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.bind.JAXBContext;
import javax.xml.transform.stream.StreamSource;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.models.XmlQuestData;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.*;
import com.aionemu.gameserver.questEngine.handlers.template.XmlQuest;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.item.ItemPacketService.*;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Actual maintained 1127 operations and native custody; manual callbacks, no World/SQL/IDs/scheduler. */
public final class PlayerBotNativeXmlQuestCheck {
 private static int checks;private static sun.misc.Unsafe unsafe;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static Object get(Object object,String name)throws Exception{for(Class<?> t=object.getClass();t!=null;t=t.getSuperclass())try{var f=t.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
 private static void set(Object object,String name,Object value)throws Exception{for(Class<?> t=object.getClass();t!=null;t=t.getSuperclass())try{var f=t.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
 private static <T> T inert(Class<T> type)throws Exception{return type.cast(unsafe.allocateInstance(type));}
 private static String entry(String path,String tag,String key,int id)throws Exception{var p=java.util.regex.Pattern.compile("<"+tag+"\\s+"+key+"=\""+id+"\"[^>]*(?:/>|>.*?</"+tag+">)",java.util.regex.Pattern.DOTALL).matcher(Files.readString(Path.of(path)));if(!p.find())throw new AssertionError("Missing native "+tag+" "+id);return p.group();}
 private static final class TimerRecord implements Future<Object> {
  boolean cancelled;public boolean cancel(boolean interrupt){check(!interrupt,"Native timer cancellation does not interrupt custody work");cancelled=true;return true;}
  public boolean isCancelled(){return cancelled;}public boolean isDone(){return cancelled;}public Object get(){throw new AssertionError("No blocking fixture");}public Object get(long n,TimeUnit u){throw new AssertionError("No blocking fixture");}
 }
 private static final class Cube extends PlayerStorage {
  final Item owned;long count=1,kinah;int additions,collections,workRemovals;
  Cube(Actor actor,ItemTemplate template){super(actor,StorageType.CUBE);owned=new Item(1999976402,template);owned.setItemCount(1);}
  @Override public long getItemCountByItemId(int item){return item==182200215?count:0;}
  @Override public List<Item> getItemsByItemId(int item){check(item==182200215,"Native give operation uses only Ancient Cube");return List.of(owned);}
  @Override public long increaseItemCount(Item item,long n,ItemUpdateType type){check(item==owned && type==ItemUpdateType.INC_ITEM_COLLECT,"Actual ItemService updates the existing owned recording stack");count+=n;owned.setItemCount(count);additions++;return 0;}
  @Override public boolean decreaseByItemId(int item,long n){if(item!=182200215 || count<n)return false;count-=n;owned.setItemCount(count);collections++;return true;}
  @Override public boolean decreaseByItemId(int item,long n,QuestStatus status){check(status==QuestStatus.REWARD,"Native reward work-item cleanup preserves actual status");boolean result=decreaseByItemId(item,n);workRemovals++;return result;}
  @Override public Item add(Item item,ItemAddType type){throw new AssertionError("No ItemFactory allocation allowed");}
  @Override public void increaseKinah(long n,ItemUpdateType type){check(type==ItemUpdateType.INC_KINAH_QUEST,"Native earned currency uses quest reward endpoint");kinah+=n;}
  @Override public int size(){return count>0?1:0;}
  @Override public boolean isFull(){return false;}
 }
 private static final class Common extends PlayerCommonData {
  long awarded;Common(){super(1999976401);}
  @Override public com.aionemu.gameserver.model.templates.BoundRadius getBoundRadius(){return com.aionemu.gameserver.model.templates.BoundRadius.DEFAULT;}
  @Override public void addExp(long n,Rates rates,String name){check(rates==Rates.XP_QUEST && n==4015,"Actual quest finish delivers maintained Ancient Cube XP");awarded+=n;}
 }
 private static final class Stats extends PlayerGameStats {
  Stats(Player actor){super(actor);}
  @Override public int getBaseAttackSpeed(){return 1500;}
  @Override public float getMovementSpeedFloat(){return 5;}
  @Override protected Stat2 getStat(StatEnum stat,float base,Set<com.aionemu.gameserver.utils.stats.CalculationType> types){return new AdditionStat(stat,base,null);}
 }
 private static final class Controller extends PlayerController {int updates;@Override public void updateNearbyQuests(){updates++;}}
 private static final class Sight extends KnownList {boolean visible=true;Sight(Player p){super(p);}@Override public boolean sees(VisibleObject object){return visible;}}
 private static final class Actor extends Player {
  boolean spawned=true,dead,playing=true,bot=true,online;int world=210010000,instance=1;float x;QuestStateList quests;Cube cube;Controller controller;Common common;Stats stats;Sight known;ObserveController observe;Account account;
  Actor(){super(null,null);}@Override public int getObjectId(){return 1999976401;}@Override public boolean isPlayerBot(){return bot;}@Override public boolean isPlaying(){return playing;}@Override public boolean isOnline(){return online;}@Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}@Override public float getX(){return x;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public PlayerStorage getInventory(){return cube;}@Override public QuestStateList getQuestStateList(){return quests;}@Override public PlayerController getController(){return controller;}@Override public KnownList getKnownList(){return known;}@Override public ObserveController getObserveController(){return observe;}
  @Override public PlayerCommonData getCommonData(){return common;}@Override public PlayerCommonData getObjectTemplate(){return common;}@Override public PlayerGameStats getGameStats(){return stats;}@Override public Account getAccount(){return account;}
  @Override public byte getLevel(){return 5;}@Override public Race getRace(){return Race.ELYOS;}@Override public PlayerClass getPlayerClass(){return PlayerClass.WARRIOR;}@Override public Gender getGender(){return Gender.MALE;}
  @Override public boolean isInPlayerMode(com.aionemu.gameserver.model.actions.PlayerMode mode){return false;}@Override public boolean isInAnyHide(){return false;}
 }
 private static final class Contact extends Npc {
  boolean spawned=true,dead;int world=210010000,instance=1;NpcTemplate template;Contact(){super(null,null,null);}
  @Override public int getObjectId(){return getNpcId();}@Override public NpcTemplate getObjectTemplate(){return template;}@Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}@Override public float getX(){return 0;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
 }
 private static final class NativeTimer extends ActionItemUseOperation {
  final PlayerBotService service;Runnable callback;TimerRecord timer;int delay,schedules;
  NativeTimer(PlayerBotService service,QuestOperations operations)throws Exception{this.service=service;set(this,"finish",operations);}
  @Override protected Future<?> schedule(Runnable callback,int delay){this.callback=callback;this.delay=delay;schedules++;return timer=new TimerRecord();}
  @Override protected boolean interactionPresent(Player p){return p.isPlayerBot()?service.allowsHeadlessInteraction(p):super.interactionPresent(p);}
  boolean present(Player p){return interactionPresent(p);}
 }
 private static final class NativeAi extends ai.quests.QuestItemNpcAI {
  NativeAi(Npc npc){super(npc);}void firstPhaseFinished(Player p){super.handleUseItemFinish(p);}
 }
 private static List<?> list(Object object,String name)throws Exception{return (List<?>)get(object,name);}
 private static void reset(Actor bot,Contact cube,PlayerBotService service,PlayerBotSession session,Map<Integer,PlayerBotSession> sessions)throws Exception {
  bot.controller.cancelTask(TaskId.ACTION_ITEM_NPC);bot.quests=new QuestStateList();bot.quests.addQuest(1127,new QuestState(1127,QuestStatus.START));bot.spawned=bot.playing=true;bot.dead=false;bot.bot=true;bot.online=false;bot.world=cube.world=210010000;bot.instance=cube.instance=1;bot.x=0;bot.known.visible=true;cube.spawned=true;cube.dead=false;set(session,"bot",bot);set(session,"closing",false);set(service,"shuttingDown",false);sessions.put(bot.getObjectId(),session);
 }
 public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
  var oldQuests=DataManager.QUEST_DATA;var oldItems=DataManager.ITEM_DATA;var oldNpcs=DataManager.NPC_DATA;boolean oldSee=GeoDataConfig.CANSEE_ENABLE,oldLog=LoggingConfig.LOG_ITEM,oldAi=PlayerBotConfig.QUESTS_ENABLED,oldUnified=CustomConfig.UNIFIED_INVENTORY;int oldLimit=CustomConfig.CUBE_EXPANSION_LIMIT,oldQuestLimit=CustomConfig.BASIC_QUEST_SIZE_LIMIT;var oldKinah=RatesConfig.QUEST_KINAH_RATES;
  var engine=QuestEngine.getInstance();@SuppressWarnings("unchecked") var handlers=(Map<Integer,com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler>)get(engine,"questHandlers");var oldHandler=handlers.get(1127);
  try {
   GeoDataConfig.CANSEE_ENABLE=false;LoggingConfig.LOG_ITEM=false;CustomConfig.UNIFIED_INVENTORY=false;CustomConfig.CUBE_EXPANSION_LIMIT=10;CustomConfig.BASIC_QUEST_SIZE_LIMIT=30;RatesConfig.QUEST_KINAH_RATES=new float[]{1};PlayerBotConfig.QUESTS_ENABLED=true;
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests>"+entry("game-server/data/static_data/quest_data/quest_data.xml","quest","id",1127)+"</quests>"));
   var model=JAXBContext.newInstance(XmlQuestData.class).createUnmarshaller().unmarshal(new StreamSource(new StringReader(entry("game-server/data/static_data/quest_script_data/poeta.xml","xml_quest","id",1127))),XmlQuestData.class).getValue();
   var npcData=new NpcData();@SuppressWarnings("unchecked") var templates=(Map<Integer,NpcTemplate>)get(npcData,"npcData");
   var cubeTemplate=(NpcTemplate)JAXBContext.newInstance(NpcTemplate.class).createUnmarshaller().unmarshal(new StringReader(entry("game-server/data/static_data/npcs/npc_templates.xml","npc_template","npc_id",700001)));templates.put(700001,cubeTemplate);
   var reportTemplate=(NpcTemplate)JAXBContext.newInstance(NpcTemplate.class).createUnmarshaller().unmarshal(new StringReader(entry("game-server/data/static_data/npcs/npc_templates.xml","npc_template","npc_id",798008)));templates.put(798008,reportTemplate);DataManager.NPC_DATA=npcData;
   var item=JAXBContext.newInstance(ItemTemplate.class).createUnmarshaller().unmarshal(new StreamSource(new StringReader(entry("game-server/data/static_data/items/item_templates.xml","item_template","id",182200215))),ItemTemplate.class).getValue();
   check(!item.isStackable(),"Maintained Ancient Cube is nonstackable; new-ID allocation is deliberately outside this fixture");
   // Only an inert recording container's capacity is adapted: native operation
   // dispatch and item identity remain actual. No missing item/ID is synthesized.
   set(item,"maxStackCount",9999);var itemData=new ItemData();@SuppressWarnings("unchecked") var itemMap=(Map<Integer,ItemTemplate>)get(itemData,"items");itemMap.put(182200215,item);DataManager.ITEM_DATA=itemData;
   var bot=inert(Actor.class);bot.quests=new QuestStateList();bot.controller=new Controller();bot.controller.setOwner(bot);bot.common=new Common();bot.common.setPlayerClass(PlayerClass.WARRIOR);bot.stats=inert(Stats.class);bot.account=new Account(0);bot.cube=new Cube(bot,item);bot.known=new Sight(bot);bot.observe=new ObserveController();bot.spawned=bot.playing=true;bot.world=210010000;bot.instance=1;bot.bot=true;
   var cube=inert(Contact.class);cube.template=cubeTemplate;var report=inert(Contact.class);report.template=reportTemplate;report.spawned=true;report.world=210010000;report.instance=1;
   var service=inert(PlayerBotService.class);Map<Integer,PlayerBotSession> sessions=new ConcurrentHashMap<>();set(service,"sessions",sessions);var session=inert(PlayerBotSession.class);set(session,"bot",bot);sessions.put(bot.getObjectId(),session);
   var event=list(model,"onTalkEvents").getFirst();var variable=list(event,"var").getFirst();var object=list(variable,"npc").getFirst();var dialog=list(object,"dialog").getFirst();var operations=(QuestOperations)get(dialog,"operations");var original=(ActionItemUseOperation)list(operations,"operations").getFirst();var nativeTimer=new NativeTimer(service,(QuestOperations)get(original,"finish"));set(operations,"operations",List.of(nativeTimer));
   var handler=new XmlQuest(1127,(List<Integer>)get(model,"startNpcIds"),(List<Integer>)get(model,"endNpcIds"),(List<com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.events.OnTalkEvent>)get(model,"onTalkEvents"),null);handlers.put(1127,handler);handler.register();
   check(cubeTemplate.getTalkDelay()==3 && handler.getPlayerBotActions(bot).equals(List.of(QuestBotAction.dialog(798008,DialogAction.QUEST_ACCEPT,4))),"Actual XML starter and first native AI three-second phase are declared");
   try(var lease=PlayerBotLease.acquire(bot.getObjectId())) {
    check(lease!=null && service.allowsHeadlessInteraction(bot),"Actual registered headless actor and character lease authorize native presence");
    var accept=new QuestEnv(report,bot,1127,DialogAction.QUEST_ACCEPT);check(handler.onDialogEvent(accept) && bot.quests.getQuestState(1127).getStatus()==QuestStatus.START,"Actual XML generic acceptance uses native QuestService.startQuest");
    check(handler.getPlayerBotActions(bot).equals(List.of(QuestBotAction.object(700001))),"Actual var0 discovers Ancient Cube only");reset(bot,cube,service,session,sessions);
    var ai=new NativeAi(cube);long before=bot.cube.count;ai.firstPhaseFinished(bot);
    check(nativeTimer.delay==3000 && nativeTimer.schedules==1 && bot.controller.hasScheduledTask(TaskId.ACTION_ITEM_NPC) && ActionItemUseOperation.isInProgress(bot),"First native AI completion schedules and retains a distinct second three-second XML task");
    check(bot.cube.count==before && bot.quests.getQuestState(1127).getQuestVarById(0)==0,"Neither native timer admission nor discovery awards early progress");
    handler.onDialogEvent(new QuestEnv(cube,bot,1127,DialogAction.USE_OBJECT));check(nativeTimer.schedules==1,"Repeated object dialog cannot duplicate pending native operation");
    PlayerBotConfig.QUESTS_ENABLED=false;nativeTimer.callback.run();check(!PlayerBotConfig.QUESTS_ENABLED && bot.cube.count==before+1 && bot.quests.getQuestState(1127).getQuestVarById(0)==1,"Actual native GiveItemOperation and SetQuestVar complete committed work despite disabled new AI");PlayerBotConfig.QUESTS_ENABLED=true;
    check(!ActionItemUseOperation.isInProgress(bot) && !bot.controller.hasScheduledTask(TaskId.ACTION_ITEM_NPC) && nativeTimer.timer.cancelled,"Native completion removes only its timer/observer custody");nativeTimer.callback.run();check(bot.cube.count==before+1,"Repeated callback cannot grant twice");
    var reportAction=handler.getPlayerBotActions(bot).getFirst();check(reportAction.kind()==QuestBotAction.Kind.CONVERSATION && reportAction.targetId()==798008 && reportAction.dialogAction()==39 && reportAction.page()==2375 && reportAction.requiredItems().equals(Map.of(182200215,1L)),"Actual var1 declaration requires owned Ancient Cube and exact native report action/page");
    var reportEnv=new QuestEnv(report,bot,1127,DialogAction.QUEST_SELECT);try(var offered=PlayerBotQuestDialog.open(bot.getObjectId(),report.getObjectId(),1127)){check(handler.onDialogEvent(reportEnv) && offered.offered(2375),"Actual XML NpcDialogOperation emits captured report page2375");}
    long missing=bot.cube.count;bot.cube.count=0;reportEnv.setDialogActionId(39);handler.onDialogEvent(reportEnv);check(bot.quests.getQuestState(1127).getStatus()==QuestStatus.START,"Missing actual owned cube cannot reach reward");bot.cube.count=missing;
    check(handler.onDialogEvent(reportEnv) && bot.cube.count==missing-1 && bot.quests.getQuestState(1127).getStatus()==QuestStatus.REWARD,"Actual native collection consumes one owned cube before REWARD");check(handler.getPlayerBotActions(bot).equals(List.of(QuestBotAction.reward(798008))),"Only actual earned reward state exposes the native endpoint");
    reportEnv.setDialogActionId(DialogAction.SELECT_QUEST_REWARD);try(var offered=PlayerBotQuestDialog.open(bot.getObjectId(),report.getObjectId(),1127)){check(handler.onDialogEvent(reportEnv) && offered.offered(5),"Actual earned native handler opens authorized reward page5");}
    reportEnv.setDialogActionId(DialogAction.SELECTED_QUEST_NOREWARD);check(handler.onDialogEvent(reportEnv) && bot.quests.getQuestState(1127).getStatus()==QuestStatus.COMPLETE,"Actual native reward selection completes the XML chain");check(bot.common.awarded==4015 && bot.cube.kinah==2400 && bot.getQuestExpands()==1 && bot.quests.getQuestState(1127).getQuestVarById(5)==1,"Native turn-in alone earns maintained XP/Kinah/cube expansion and duplicate protection marker");
    long earned=bot.cube.kinah;handler.onDialogEvent(reportEnv);check(bot.cube.kinah==earned && bot.getQuestExpands()==1,"Completed quest cannot duplicate native rewards or expansion");
    for(int invalid=0;invalid<15;invalid++) {
     reset(bot,cube,service,session,sessions);var env=new QuestEnv(cube,bot,1127,DialogAction.USE_OBJECT);handler.onDialogEvent(env);long held=bot.cube.count;var saved=bot.quests.getQuestState(1127);TimerRecord successor=null;
     switch(invalid){case 0->bot.dead=true;case 1->bot.spawned=false;case 2->bot.playing=false;case 3->bot.world++;case 4->bot.instance++;case 5->cube.spawned=false;case 6->cube.dead=true;case 7->bot.known.visible=false;case 8->bot.x=100;case 9->saved.setQuestVarById(0,2);case 10->saved.setStatus(QuestStatus.REWARD);case 11->bot.quests=new QuestStateList();case 12->set(session,"bot",inert(Actor.class));case 13->bot.getObserveController().notifyMoveObservers();default->{successor=new TimerRecord();bot.controller.addTask(TaskId.ACTION_ITEM_NPC,successor);}}
     nativeTimer.callback.run();check(bot.cube.count==held && saved.getQuestVarById(0)==(invalid==9?2:0),"Invalid actor/object/map/state/identity/movement/current-task prevents actual late item/progress grant "+invalid);check(!ActionItemUseOperation.isInProgress(bot),"Invalid XML timer releases its own commitment "+invalid);if(successor!=null)check(!successor.cancelled && bot.controller.hasScheduledTask(TaskId.ACTION_ITEM_NPC),"Stale XML callback preserves successor task identity");
    }
    reset(bot,cube,service,session,sessions);var mutable=new QuestEnv(cube,bot,1127,DialogAction.USE_OBJECT);handler.onDialogEvent(mutable);mutable.setQuestId(999999);mutable.setPlayer(inert(Actor.class));mutable.setVisibleObject(report);nativeTimer.callback.run();check(bot.quests.getQuestState(1127).getQuestVarById(0)==1,"Native timer uses captured immutable env fields rather than reused QuestEngine envelope");
    reset(bot,cube,service,session,sessions);bot.bot=false;bot.online=true;check(nativeTimer.present(bot),"Human connected/playing presence remains admitted by native predicate");bot.online=false;check(!nativeTimer.present(bot),"Human disconnected presence cannot complete native task");bot.bot=true;
   }
   check(!service.allowsHeadlessInteraction(bot),"Released native actor lease denies future completion");System.out.println("OK: "+checks+" actual native XML1127 timing/operations/custody/reward/presence checks; no scheduler/World/SQL/IDFactory; inert existing-stack capacity seam only");
  } finally {DataManager.QUEST_DATA=oldQuests;DataManager.ITEM_DATA=oldItems;DataManager.NPC_DATA=oldNpcs;GeoDataConfig.CANSEE_ENABLE=oldSee;LoggingConfig.LOG_ITEM=oldLog;PlayerBotConfig.QUESTS_ENABLED=oldAi;CustomConfig.UNIFIED_INVENTORY=oldUnified;CustomConfig.CUBE_EXPANSION_LIMIT=oldLimit;CustomConfig.BASIC_QUEST_SIZE_LIMIT=oldQuestLimit;RatesConfig.QUEST_KINAH_RATES=oldKinah;if(oldHandler==null)handlers.remove(1127);else handlers.put(1127,oldHandler);}
 }
 private PlayerBotNativeXmlQuestCheck(){}
}
