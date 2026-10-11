package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.instance.handlers.InstanceHandler;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.gather.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;
import com.aionemu.gameserver.skillengine.task.*;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Exact native GatheringTask callbacks with inert endpoint records; no scheduler/World/SQL/IDFactory. */
public final class PlayerBotNativeGatheringTaskCheck {
 static int checks;
 static Object unsafe;
 static <T> T allocate(Class<T> type)throws Exception{return type.cast(unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type));}
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void set(Object target,String name,Object value)throws Exception {
  Class<?> type=target.getClass();while(type!=null){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
  throw new NoSuchFieldException(name);
 }
 static Object get(Object target,String name)throws Exception {Class<?> type=target.getClass();while(type!=null){try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException missing){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
 static final class ScheduledRecord implements Future<Object> {
  boolean cancelled;int cancels;
  public boolean cancel(boolean interrupt){check(!interrupt,"Native gathering cancellation never interrupts endpoint custody");cancelled=true;cancels++;return true;}
  public boolean isCancelled(){return cancelled;}public boolean isDone(){return cancelled;}
  public Object get(){throw new UnsupportedOperationException();}public Object get(long timeout,TimeUnit unit){throw new UnsupportedOperationException();}
 }
 static final class Inventory extends PlayerStorage {
  Item stack;long held=9,added;int consumed,updates;
  Inventory(){super(null,StorageType.CUBE);}
  @Override public boolean decreaseByItemId(int id,long count){check(id==200 && count==2,"Exact native consumed material prerequisite");held-=count;consumed++;return true;}
  @Override public boolean decreaseByItemId(int id,long count,QuestStatus status){throw new AssertionError("Unexpected quest credit/removal");}
  @Override public List<Item> getItemsByItemId(int id){check(id==100,"Exact native gather output reaches ItemService existing-stack path");return List.of(stack);}
  @Override public long increaseItemCount(Item item,long count,ItemUpdateType type){check(item==stack && count==1 && type==ItemUpdateType.INC_ITEM_COLLECT,"Actual ItemService records owned stack increase");updates++;added+=count;return 0;}
  @Override public boolean isFull(){return false;}
 }
 static final class Actor extends Player {
  boolean spawned,dead,playing;int map,instance;Inventory inventory;KnownList known;ObserveController observers;WorldPosition position;Account account;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return 1999976141;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isOnline(){return false;}
  @Override public boolean isPlaying(){return playing;}
  @Override public Account getAccount(){return account;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isDead(){return dead;}
  @Override public int getWorldId(){return map;}
  @Override public int getInstanceId(){return instance;}
  @Override public Storage getInventory(){return inventory;}
  @Override public KnownList getKnownList(){return known;}
  @Override public ObserveController getObserveController(){return observers;}
  @Override public WorldPosition getPosition(){return position;}
 }
 static final class Node extends Gatherable {
  GatherableTemplate template;NodeController controller;
  Node(){super(null,null);}
  @Override public GatherableTemplate getObjectTemplate(){return template;}
  @Override public NodeController getController(){return controller;}
  @Override public int getObjectId(){return 1999976142;}
 }
 static final class NodeController extends GatherableController {
  int rewards,finishes;
  @Override public void rewardPlayer(Player actor){rewards++;}
  @Override public void completeInteraction(){finishes++;super.completeInteraction();}
 }
 static final class InertInstance extends WorldMapInstance {
  InertInstance(){super(null,0,0,i->null);}
  @Override public MapRegion getRegion(float x,float y,float z){throw new AssertionError("No real World region is allowed");}
  @Override protected void initMapRegions(){throw new AssertionError("No native World construction is allowed");}
  @Override public boolean isPersonal(){return false;}
  @Override public int getOwnerId(){return 0;}
 }
 static final class Position extends WorldPosition {
  WorldMapInstance instance;Position(WorldMapInstance instance){super(1);this.instance=instance;}
  @Override public WorldMapInstance getWorldMapInstance(){return instance;}
 }
 static final class Work extends GatheringTask {
  final PlayerBotService service;int ticks,updates,success,failed,aborted,finished;final ScheduledRecord schedule=new ScheduledRecord();
  Work(Actor actor,Node node,Material material,int difference,PlayerBotService service){super(actor,node,material,difference);this.service=service;}
  void begin()throws Exception {requester.setInteractionTask(this);set(((Node)responder).controller,"gatheringTask",this);onInteractionStart();set(this,"task",schedule);check(((Node)responder).controller.getGatheringPlayerId()==requester.getObjectId(),"Actual native node custody holds the current task requester");}
  void tick(){ticks++;runInteraction();}
  int progress(){return currentSuccessValue;}int failure(){return currentFailureValue;}
  @Override protected boolean interactionPresent(){return service.allowsHeadlessInteraction(requester);}
  @Override protected void sendInteractionUpdate(){updates++;super.sendInteractionUpdate();}
  @Override protected boolean onSuccessFinish(){success++;return super.onSuccessFinish();}
  @Override protected void onFailureFinish(){failed++;super.onFailureFinish();}
  @Override protected void onInteractionAbort(){aborted++;super.onInteractionAbort();}
  @Override protected void onInteractionFinish(){finished++;super.onInteractionFinish();}
 }
 public static void main(String[] args)throws Exception {
  var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);unsafe=unsafeField.get(null);
  var originalItems=DataManager.ITEM_DATA;var originalRates=RatesConfig.GATHERING_COUNT_RATES;int originalFailure=CraftConfig.MAX_GATHER_FAILURE_CHANCE;boolean originalLog=LoggingConfig.LOG_ITEM;
  try {
   RatesConfig.GATHERING_COUNT_RATES=new float[]{1};LoggingConfig.LOG_ITEM=false;
   var template=new GatherableTemplate();set(template,"id",400001);set(template,"harvestSkill",30002);set(template,"skillLevel",50);set(template,"harvestCount",1000);set(template,"reqItem",200);set(template,"eraseValue",2);
   var material=new Material();set(material,"itemid",100);
   var itemTemplate=new ItemTemplate();set(itemTemplate,"itemId",100);set(itemTemplate,"maxStackCount",9999);
   var itemData=new ItemData();@SuppressWarnings("unchecked") var itemMap=(Map<Integer,ItemTemplate>)get(itemData,"items");itemMap.put(100,itemTemplate);DataManager.ITEM_DATA=itemData;
   var actor=allocate(Actor.class);actor.spawned=actor.playing=true;actor.account=new Account(0);actor.map=actor.instance=1;actor.inventory=new Inventory();actor.inventory.stack=new Item(1999976143,itemTemplate);actor.known=new KnownList(actor);actor.observers=new ObserveController();
   var node=allocate(Node.class);node.template=template;node.controller=new NodeController();node.controller.setOwner(node);
   int[] gathers={0};var instance=allocate(InertInstance.class);
   var handler=(InstanceHandler)Proxy.newProxyInstance(InstanceHandler.class.getClassLoader(),new Class<?>[]{InstanceHandler.class},(proxy,method,arguments)->{if(method.getName().equals("onGather")){check(arguments[0]==actor && arguments[1]==node,"Native instance gather callback preserves actual task actors");gathers[0]++;return null;}throw new AssertionError("Unexpected instance service "+method.getName());});
   set(instance,"instanceHandler",handler);actor.position=new Position(instance);
   var service=allocate(PlayerBotService.class);Map<Integer,PlayerBotSession> sessions=new ConcurrentHashMap<>();set(service,"sessions",sessions);
   var session=allocate(PlayerBotSession.class);set(session,"bot",actor);sessions.put(actor.getObjectId(),session);
   try(var lease=PlayerBotLease.acquire(actor.getObjectId())) {
    check(lease!=null && !actor.isOnline() && service.allowsHeadlessInteraction(actor),"Actual registration/lease permits inert headless native gathering");
    for(int difference:new int[]{41,0}) {
     CraftConfig.MAX_GATHER_FAILURE_CHANCE=0;var work=new Work(actor,node,material,difference,service);work.begin();
     int finished=node.controller.finishes,rewards=node.controller.rewards,instances=gathers[0],consumed=actor.inventory.consumed;long added=actor.inventory.added;
     check(work.isInProgress() && actor.getInteractionTask()==work,"Native gathering task owns current scheduled interaction");
     work.tick();check(work.progress()==1000 && work.updates==1 && work.success==0 && work.finished==0,"First exact scheduled native tick calculates gathering success, without premature awarding");
     check(actor.inventory.consumed==consumed && actor.inventory.added==added,"Gather analysis preserves prerequisite/output custody");
     work.tick();check(work.success==1 && work.finished==1 && work.aborted==0 && !work.isInProgress() && actor.getInteractionTask()==null,"Second exact scheduled tick owns native success/finalization");
     check(actor.inventory.consumed==consumed+1 && actor.inventory.added==added+1,"Only native gathering success consumes/updates actual recorded holdings");
     check(node.controller.rewards==rewards+1 && node.controller.finishes==finished+1 && gathers[0]==instances+1,"Native success reaches reward/instance/complete endpoints exactly once");
     check(work.schedule.cancels==1 && node.controller.getGatheringPlayerId()==0 && ((List<?>)get(actor.observers,"observers")).isEmpty(),"Native finish releases node custody, cancels scheduled handle and removes gather observer");
    }
    for(int difference:new int[]{-1,0}) {
     CraftConfig.MAX_GATHER_FAILURE_CHANCE=Integer.MAX_VALUE;var work=new Work(actor,node,material,difference,service);work.begin();long held=actor.inventory.held,added=actor.inventory.added;int rewards=node.controller.rewards;
     work.tick();check(work.failure()==1000 && work.failed==0 && work.finished==0,"First native failure tick analyzes before completion");
     work.tick();check(work.failed==1 && work.success==0 && work.finished==1 && work.aborted==0,"Second native tick completes actual failed GatheringTask");
     check(actor.inventory.held==held && actor.inventory.added==added && node.controller.rewards==rewards,"Native failure preserves owned tools/materials and awards nothing");
    }
    for(int invalid=0;invalid<10;invalid++) {
     actor.spawned=actor.playing=true;actor.dead=false;actor.map=actor.instance=1;set(session,"closing",false);set(service,"shuttingDown",false);set(session,"bot",actor);sessions.put(actor.getObjectId(),session);
     var work=new Work(actor,node,material,41,service);work.begin();long held=actor.inventory.held,added=actor.inventory.added;int rewards=node.controller.rewards;
     switch(invalid){case 0->actor.dead=true;case 1->actor.spawned=false;case 2->actor.map=2;case 3->actor.instance=2;case 4->set(session,"closing",true);case 5->set(service,"shuttingDown",true);case 6->sessions.clear();case 7->set(session,"bot",allocate(Actor.class));case 8->actor.setInteractionTask(new Work(actor,node,material,41,service));default->actor.playing=false;}
     var replacement=actor.getInteractionTask();work.tick();
     check(work.progress()==0 && work.updates==0 && work.success==0 && work.failed==0 && work.aborted==1 && work.finished==1,"Stale/dead/despawn/map/instance/session/shutdown/current-task guard aborts actual gathering before calculation "+invalid);
     check(actor.inventory.held==held && actor.inventory.added==added && node.controller.rewards==rewards,"Guard abort preserves native material custody "+invalid);
     check(work.schedule.cancels==1 && node.controller.getGatheringPlayerId()==0 && !work.isInProgress() && ((List<?>)get(actor.observers,"observers")).isEmpty(),"Guard abort releases actual node custody and clears task schedule/observer "+invalid);
     check(actor.getInteractionTask()==(invalid==8?replacement:null),"Stale task abort cannot clear successor native custody "+invalid);
    }
    actor.spawned=actor.playing=true;actor.dead=false;actor.map=actor.instance=1;set(session,"closing",false);set(service,"shuttingDown",false);set(session,"bot",actor);sessions.put(actor.getObjectId(),session);
    var work=new Work(actor,node,material,41,service);work.begin();long held=actor.inventory.held,added=actor.inventory.added;
    actor.observers.notifyMoveObservers();check(work.aborted==1 && work.finished==1 && !work.isInProgress(),"Actual native gather move observer aborts scheduled task");
    check(actor.inventory.held==held && actor.inventory.added==added,"Movement abort retains actual owned gathering prerequisites");
   }
   check(!service.allowsHeadlessInteraction(actor),"Released lease prevents further native gathering admission");
   System.out.println("OK: "+checks+" actual native GatheringTask progression/custody/presence/abort checks; no scheduler/World/SQL/IDFactory");
  }finally {DataManager.ITEM_DATA=originalItems;RatesConfig.GATHERING_COUNT_RATES=originalRates;CraftConfig.MAX_GATHER_FAILURE_CHANCE=originalFailure;LoggingConfig.LOG_ITEM=originalLog;}
 }
 private PlayerBotNativeGatheringTaskCheck(){}
}
