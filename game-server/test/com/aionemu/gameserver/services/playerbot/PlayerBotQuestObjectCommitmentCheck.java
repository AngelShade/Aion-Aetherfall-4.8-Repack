package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.model.templates.VisibleObjectTemplate;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.*;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Actual object adapter/native timer and page operations on inert actors.
 * No World, IDs, scheduler, SQL, registration lifecycle or allocated item awards. */
public final class PlayerBotQuestObjectCommitmentCheck {
 private static final int QUEST=900451, ITEM=182200215;
 private static sun.misc.Unsafe unsafe;private static int checks;
 private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 private static <T>T allocate(Class<T> type)throws Exception{return type.cast(unsafe.allocateInstance(type));}
 private static Field field(Class<?> type,String name)throws Exception{for(;type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 @SuppressWarnings("unchecked") private static <K,V>Map<K,V> map(Class<?> type,String name)throws Exception{return (Map<K,V>)field(type,name).get(null);}
 private static final class Seen extends KnownList {
  final List<Npc> objects=new ArrayList<>();Seen(){super(null);}
  @Override public boolean sees(VisibleObject object){return objects.contains(object);}
  @Override public void forEachNpc(Consumer<Npc> consumer){objects.forEach(consumer);}
  @Override public void forEachPlayer(Consumer<Player> consumer){} // No sockets or world broadcasts.
 }
 private static final class Inventory extends PlayerStorage {
  long count=1;Inventory(Player owner){super(owner,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int id){return id==ITEM?count:0;}
 }
 private static final class Statistics extends PlayerGameStats {
  Statistics(){super(null);}
  @Override public Stat2 getAttackSpeed(){return new AdditionStat(StatEnum.ATTACK_SPEED,2000,null);}
  @Override public float getMovementSpeedFloat(){return 6;}
 }
 private static final class Controller extends PlayerController {
  boolean combat;int blindCancels;
  @Override public boolean isInCombat(){return combat;}
  @Override public Future<?> cancelTask(TaskId id){blindCancels++;return super.cancelTask(id);}
 }
 private static final class Actor extends Player {
  int id,world=210010000,instance=1;boolean dead,spawned=true;float x;
  Seen seen;Inventory inventory;QuestStateList quests;Controller controller;ObserveController observers;Statistics stats;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}@Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public boolean isPlayerBot(){return true;}@Override public boolean isOnline(){return false;}@Override public boolean isPlaying(){return !dead;}
  @Override public boolean isLooting(){return false;}@Override public boolean isCasting(){return false;}@Override public boolean isEnemy(Creature other){return false;}
  @Override public VisibleObjectTemplate getObjectTemplate(){return new NpcTemplate();}
  @Override public KnownList getKnownList(){return seen;}@Override public PlayerStorage getInventory(){return inventory;}
  @Override public QuestStateList getQuestStateList(){return quests;}@Override public PlayerController getController(){return controller;}
  @Override public ObserveController getObserveController(){return observers;}@Override public PlayerGameStats getGameStats(){return stats;}
 }
 private static final class QuietAI extends NpcAI {QuietAI(Npc npc){super(npc);}}
 private static final class Contact extends Npc {
  int id;boolean spawned=true,dead;int world=210010000,instance=1;float x;
  NpcController controller;NpcAI ai;
  Contact(){super(null,null,null);}
  @Override public int getObjectId(){return id;}@Override public int getNpcId(){return 700001;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public NpcTemplate getObjectTemplate(){return new NpcTemplate(){@Override public int getTemplateId(){return 700001;}};}
  @Override public NpcController getController(){return controller;}@Override public NpcAI getAi(){return ai;}
 }
 private static final class Intent extends AbstractQuestHandler {
  final QuestBotAction action=new QuestBotAction(QuestBotAction.Kind.OBJECT,700001,DialogAction.SETPRO1,1352,Map.of(ITEM,1L),"").then(DialogAction.SETPRO2,1353);
  boolean admitted=true;Intent(){super(QUEST);}
  @Override public void register(){throw new AssertionError("No native registration lifecycle");}
  @Override public List<QuestBotAction> getPlayerBotActions(Player bot){var q=bot.getQuestStateList().getQuestState(QUEST);return admitted && q!=null && q.getStatus()==QuestStatus.START && q.getQuestVarById(0)==0?List.of(action):List.of();}
 }
 private static final class DialogController extends NpcController {
  Actor owner,bot;Contact npc;Intent intent;int first,second,mode;
  @Override public void onDialogSelect(int action,int page,Player player,int quest,int extended){
   try {
    if(action==DialogAction.SETPRO1){first++;switch(mode){case 1->PlayerBotConfig.QUESTS_ENABLED=false;case 2->intent.admitted=false;case 3->bot.inventory.count=0;case 4->owner.quests.getQuestState(QUEST).setStatus(QuestStatus.COMPLETE);case 5->npc.instance++;}
     if(mode!=6)emit(bot,npc,1353);
    }else if(action==DialogAction.SETPRO2){second++;var operation=new SetQuestVarOperation();field(SetQuestVarOperation.class,"value").setInt(operation,1);operation.doOperate(new QuestEnv(npc,bot,quest,action));}
   }catch(Exception error){throw new IllegalStateException(error);}
  }
 }
 private static final class Timer extends ActionItemUseOperation {
  Runnable queued;int delay;FutureTask<Void> scheduled;
  Timer(QuestOperations operations){finish=operations;}
  @Override protected boolean interactionPresent(Player player){return player.isSpawned() && !player.isDead();}
  @Override protected Future<?> schedule(Runnable completion,int delay){queued=completion;this.delay=delay;scheduled=new FutureTask<>(()->null);return scheduled;}
 }
 private static Actor actor(int id)throws Exception{var actor=allocate(Actor.class);actor.id=id;actor.world=210010000;actor.instance=1;actor.spawned=true;actor.seen=new Seen();actor.inventory=new Inventory(actor);actor.quests=new QuestStateList();actor.quests.addQuest(QUEST,new QuestState(QUEST,QuestStatus.START));actor.controller=new Controller();actor.controller.setOwner(actor);actor.observers=new ObserveController();actor.stats=allocate(Statistics.class);return actor;}
 private static PlayerBotSession session(Actor owner,Actor bot)throws Exception{var session=allocate(PlayerBotSession.class);field(PlayerBotSession.class,"owner").set(session,owner);field(PlayerBotSession.class,"bot").set(session,bot);return session;}
 private static void emit(Actor bot,Contact npc,int page)throws Exception{var operation=new NpcDialogOperation();field(NpcDialogOperation.class,"id").setInt(operation,page);operation.doOperate(new QuestEnv(npc,bot,QUEST));}
 private static void pending(Actor bot,Contact npc)throws Exception{map(PlayerBotQuestObjects.class,"PENDING").put(bot.id,npc);map(PlayerBotQuestObjects.class,"CLAIMS").put(npc.id,new PlayerBotQuestObjects.Claim(bot.id,Long.MAX_VALUE));}
 private static void offer(Actor bot,Contact npc,Intent intent,int page)throws Exception{var offer=new PlayerBotQuestObjects.Offer(bot,npc,bot.quests.getQuestState(QUEST),intent.action,System.currentTimeMillis()+30000);map(PlayerBotQuestObjects.class,"OFFERS").put(bot.id,Map.of(QUEST,offer));emit(bot,npc,page);check(Objects.equals(offer.page,page),"Actual native NPC dialog operation records stored object page");}
 private static boolean tick(PlayerBotSession session,boolean admit,boolean continuing){return PlayerBotQuestObjects.tick(session,null,admit,continuing);}
 private static void reset(Actor owner,Actor bot,Contact npc,Intent intent)throws Exception{PlayerBotQuestObjects.close(bot);owner.dead=bot.dead=npc.dead=false;owner.spawned=bot.spawned=npc.spawned=true;owner.world=bot.world=npc.world=210010000;owner.instance=bot.instance=npc.instance=1;owner.x=bot.x=npc.x=0;owner.controller.combat=bot.controller.combat=false;owner.seen.objects.clear();owner.seen.objects.add(npc);bot.seen.objects.clear();bot.seen.objects.add(npc);owner.quests.getQuestState(QUEST).setStatus(QuestStatus.START);bot.quests.getQuestState(QUEST).setStatus(QuestStatus.START);bot.quests.getQuestState(QUEST).setQuestVar(0);bot.inventory.count=1;intent.admitted=true;PlayerBotConfig.QUESTS_ENABLED=PlayerBotConfig.QUEST_CUSTOM=true;}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);
  boolean oldGeo=GeoDataConfig.CANSEE_ENABLE,oldEnabled=PlayerBotConfig.QUESTS_ENABLED,oldCustom=PlayerBotConfig.QUEST_CUSTOM;
  var oldQuests=DataManager.QUEST_DATA;
  DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests><quest id='900451' name='Object commitment' category='QUEST'><rewards exp='0'/></quest></quests>"));
  var engine=QuestEngine.getInstance();var handlers=(Map<Integer,AbstractQuestHandler>)field(QuestEngine.class,"questHandlers").get(engine);var previous=handlers.get(QUEST);var intent=new Intent();
  var owner=actor(1999999451);var bot=actor(1999999452);var session=session(owner,bot);var npc=allocate(Contact.class);npc.id=1999999453;npc.world=210010000;npc.instance=1;npc.spawned=true;npc.ai=new QuietAI(npc);
  var controller=new DialogController();npc.controller=controller;controller.setOwner(npc);controller.owner=owner;controller.bot=bot;controller.npc=npc;controller.intent=intent;
  try {
   GeoDataConfig.CANSEE_ENABLE=false;handlers.put(QUEST,intent);reset(owner,bot,npc,intent);
   var phase=new FutureTask<Void>(()->null);bot.controller.addTask(TaskId.ACTION_ITEM_NPC,phase);pending(bot,npc);PlayerBotConfig.QUESTS_ENABLED=false;
   check(tick(session,false,true),"Admission disabled still waits valid first native phase");check(PlayerBotQuestObjects.pending(bot) && !phase.isCancelled(),"Disabled admission retains existing phase and object claim");phase.run();
   check(tick(session,false,true),"Completed native phase is observed with admission disabled");check(!PlayerBotQuestObjects.pending(bot) && bot.controller.hasTask(TaskId.ACTION_ITEM_NPC),"Completion clears object ownership without removing completed native task slot");
   check(!tick(session,false,true),"Disabled admission cannot start another object");
   reset(owner,bot,npc,intent);
   final Timer[] currentTimer={null};
   var operations=new QuestOperations(){@Override public boolean operate(QuestEnv env){check(ActionItemUseOperation.isInProgress(bot),"Native committing seam remains visible after controller claim");check(!bot.controller.hasScheduledTask(TaskId.ACTION_ITEM_NPC),"Native claim has removed controller task before operations finish");currentTimer[0].queued.run();check(ActionItemUseOperation.isInProgress(bot),"Duplicate callback during native commitment cannot release its running predecessor");check(tick(session,false,true) && PlayerBotQuestObjects.pending(bot),"Actual collector waits through native commit with admission disabled");bot.quests.getQuestState(QUEST).setQuestVarById(0,1);return true;}};
   var timer=new Timer(operations);currentTimer[0]=timer;pending(bot,npc);timer.doOperate(new QuestEnv(npc,bot,QUEST,DialogAction.USE_OBJECT));check(timer.delay==3000 && ActionItemUseOperation.isInProgress(bot),"Native XML owns its actual second three-second phase");PlayerBotConfig.QUESTS_ENABLED=false;
   check(tick(session,false,true),"Disabled admission waits tracked second native phase");timer.queued.run();check(!ActionItemUseOperation.isInProgress(bot) && bot.quests.getQuestState(QUEST).getQuestVarById(0)==1,"Actual native completion commits once then releases seam");check(tick(session,false,true) && !PlayerBotQuestObjects.pending(bot),"Collector observes committed state after second phase finishes");
   for(int mode=0;mode<6;mode++){
    reset(owner,bot,npc,intent);final int[] completed={0};var canceled=new Timer(new QuestOperations(){@Override public boolean operate(QuestEnv env){completed[0]++;return true;}});pending(bot,npc);canceled.doOperate(new QuestEnv(npc,bot,QUEST,DialogAction.USE_OBJECT));
    switch(mode){case 1->owner.dead=true;case 2->bot.dead=true;case 3->npc.spawned=false;case 4->bot.instance++;case 5->bot.controller.combat=true;}
    check(!tick(session,true,mode!=0),"Manual/unsafe pending operation cancels through native observer "+mode);canceled.queued.run();check(completed[0]==0 && !ActionItemUseOperation.isInProgress(bot) && canceled.scheduled.isCancelled(),"Late canceled native callback cannot grant progress "+mode);
   }
   reset(owner,bot,npc,intent);final int[] oldGrant={0};var replaced=new Timer(new QuestOperations(){@Override public boolean operate(QuestEnv env){oldGrant[0]++;return true;}});pending(bot,npc);replaced.doOperate(new QuestEnv(npc,bot,QUEST,DialogAction.USE_OBJECT));var successor=new FutureTask<Void>(()->null);bot.controller.addTask(TaskId.ACTION_ITEM_NPC,successor);
   check(!tick(session,true,false),"Manual pending cleanup aborts its old native intent");replaced.queued.run();check(oldGrant[0]==0 && !successor.isCancelled() && bot.controller.hasScheduledTask(TaskId.ACTION_ITEM_NPC),"Stale callback and cleanup never remove or cancel successor task");successor.run();
   for(int mode=0;mode<7;mode++){
    reset(owner,bot,npc,intent);controller.first=controller.second=0;controller.mode=mode;pending(bot,npc);offer(bot,npc,intent,1352);check(tick(session,true,true),"Stored native offer is processed "+mode);check(controller.first==1 && controller.second==(mode==0?1:0),"Each new selection rechecks feature/action/custody/owner/location/fresh page "+mode);check(bot.quests.getQuestState(QUEST).getQuestVarById(0)==(mode==0?1:0),"Only authorized fresh continuation earns native variable progress "+mode);
   }
   for(int mode=0;mode<5;mode++){
    reset(owner,bot,npc,intent);controller.first=controller.second=0;controller.mode=0;pending(bot,npc);offer(bot,npc,intent,mode==0?1351:1352);switch(mode){case 1->PlayerBotConfig.QUESTS_ENABLED=false;case 2->intent.admitted=false;case 3->bot.inventory.count=0;case 4->owner.quests.getQuestState(QUEST).setStatus(QuestStatus.COMPLETE);}
    check(tick(session,true,true),"Invalid stored offer is safely retired "+mode);check(controller.first==0 && controller.second==0 && bot.quests.getQuestState(QUEST).getQuestVarById(0)==0,"Stale initial page/policy/intent/custody cannot dispatch any choice "+mode);
   }
   check(bot.controller.blindCancels==0,"Object adapter never blindly removes another native task");
   System.out.println("OK: "+checks+" actual object commitment, native timer/commit custody, cancellation, successor-task and native page/policy checks; no World/IDs/scheduler/SQL/item allocation");
  }finally{PlayerBotQuestObjects.close(bot);bot.observers.notifyMoveObservers();if(previous==null)handlers.remove(QUEST);else handlers.put(QUEST,previous);DataManager.QUEST_DATA=oldQuests;GeoDataConfig.CANSEE_ENABLE=oldGeo;PlayerBotConfig.QUESTS_ENABLED=oldEnabled;PlayerBotConfig.QUEST_CUSTOM=oldCustom;}
 }
 private PlayerBotQuestObjectCommitmentCheck(){}
}
