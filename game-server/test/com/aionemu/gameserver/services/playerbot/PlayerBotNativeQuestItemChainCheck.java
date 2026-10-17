package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.*;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.actions.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.handlers.template.ItemOrders;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.world.knownlist.KnownList;
import quest.eltnen._1038TheShadowsCommand;

/** Actual native item actions, handlers, acceptance and reward completion on inert
 * recording actors. No World, IDFactory, scheduler, components, SQL or item awards. */
public final class PlayerBotNativeQuestItemChainCheck {
 private static int checks;
 private static sun.misc.Unsafe unsafe;
 private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 private static <T> T allocate(Class<T> type)throws Exception{return type.cast(unsafe.allocateInstance(type));}
 private static final int ORDER=900301,NEXT=900302,CONT=900303,TOKEN=182200001,CONT_ITEM=182200003;
 private static final class Bot extends Player {
  int id,world=210020000,instance=1,cooldowns;boolean dead,cooling;QuestStateList quests;Inventory cube;
  RecordingController controller;KnownList known;ObserveController observe;PlayerEffectController effects;
  TransformModel transform;Life life;PlayerCommonData common;
  Bot(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public int getWorldId(){return world;}
  @Override public int getInstanceId(){return instance;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isPlaying(){return !dead;}
  @Override public boolean isOnline(){return false;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isInPrison(){return false;}
  @Override public boolean isCasting(){return false;}
  @Override public boolean isLooting(){return false;}
  @Override public boolean hasCooldown(Item item){return cooling;}
  @Override public void startCooldown(Item item){cooldowns++;}
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public PlayerController getController(){return controller;}
  @Override public KnownList getKnownList(){return known;}
  @Override public ObserveController getObserveController(){return observe;}
  @Override public PlayerEffectController getEffectController(){return effects;}
  @Override public TransformModel getTransformModel(){return transform;}
  @Override public PlayerLifeStats getLifeStats(){return life;}
  @Override public PlayerCommonData getCommonData(){return common;}
  @Override public byte getLevel(){return 40;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.SORCERER;}
  @Override public Gender getGender(){return Gender.FEMALE;}
 }
 private static final class Life extends PlayerLifeStats {
  Life(){super(null);}@Override public boolean isAboutToDie(){return false;}
 }
 private static final class RecordingController extends PlayerController {
  int refresh;boolean using;
  @Override public void updateNearbyQuests(){refresh++;}
  @Override public boolean hasScheduledTask(TaskId id){return using && id==TaskId.ITEM_USE;}
 }
 private static final class Inventory extends PlayerStorage {
  final Map<Integer,Owned> objects=new LinkedHashMap<>();int removals;
  Inventory(Player owner){super(owner,StorageType.CUBE);}
  @Override public List<Item> getItems(){return new ArrayList<>(objects.values());}
  @Override public Item getItemByObjId(int id){return objects.get(id);}
  @Override public long getItemCountByItemId(int id){return objects.values().stream().filter(i->i.getItemId()==id).mapToLong(i->i.count).sum();}
  @Override public boolean decreaseByItemId(int id,long count,QuestStatus status){
   if(count<=0 || getItemCountByItemId(id)<count)return false;
   for(var item:objects.values())if(item.getItemId()==id){long used=Math.min(count,item.count);item.count-=used;count-=used;if(count==0)break;}
   removals++;return true;
  }
 }
 private static final class Owned extends Item {
  int id;long count;ItemTemplate template;Owned(){super(1,null);}
  @Override public int getObjectId(){return id;}
  @Override public ItemTemplate getItemTemplate(){return template;}
  @Override public int getL10nId(){return template.getL10nId();}
  @Override public int getItemId(){return template.getTemplateId();}
  @Override public long getItemCount(){return count;}
 }
 private static final class NativeOrders extends ItemOrders {
  NativeOrders(int quest){super(quest,203301,203302,203302);}
  NativeOrders(int quest,int first,int second,int end){super(quest,first,second,end);}
 }
 private static final class Diary extends _1038TheShadowsCommand {
  @Override public void register(){qe.registerQuestItem(182201007,1038);}
 }
 /** Fixture handler deliberately records each dispatch so stale admission is
  * observable. The real shared adapter must authorize native offered pages. */
 private static final class Continuation extends AbstractQuestHandler {
  int first,second;boolean fresh=true,transfer,disable;
  Continuation(){super(CONT);}
  @Override public void register(){qe.registerQuestItem(CONT_ITEM,CONT);}
  @Override public List<QuestBotAction> getPlayerBotActions(Player player){
   var q=player.getQuestStateList().getQuestState(CONT);
   return q!=null && q.getStatus()==QuestStatus.START && q.getQuestVarById(0)==0
    ?List.of(new QuestBotAction(QuestBotAction.Kind.ITEM,CONT_ITEM,DialogAction.SETPRO1,1352,Map.of(CONT_ITEM,1L),"").then(DialogAction.SETPRO2,1353)):List.of();
  }
  @Override public HandlerResult onItemUseEvent(QuestEnv env,Item item){return HandlerResult.fromBoolean(sendQuestDialog(new QuestEnv(null,env.getPlayer(),CONT),1352));}
  @Override public boolean onDialogEvent(QuestEnv env){
   if(env.getDialogActionId()==DialogAction.SETPRO1){first++;if(transfer)((Bot)env.getPlayer()).world++;if(disable)PlayerBotConfig.QUESTS_ENABLED=false;return !fresh || sendQuestDialog(env,1353);}
   if(env.getDialogActionId()==DialogAction.SETPRO2){second++;return changeQuestStep(env,0,1);}
   return false;
  }
 }
 private static Bot bot(int id)throws Exception{
  var bot=allocate(Bot.class);bot.id=id;bot.world=210020000;bot.instance=1;bot.quests=new QuestStateList();bot.cube=new Inventory(bot);
  bot.controller=new RecordingController();bot.controller.setOwner(bot);bot.known=new KnownList(bot);bot.observe=new ObserveController();
  bot.effects=new PlayerEffectController(bot);bot.transform=new TransformModel(bot);bot.life=allocate(Life.class);bot.common=new PlayerCommonData(id);bot.common.setPlayerClass(PlayerClass.SORCERER);
  return bot;
 }
 private static Owned own(Bot bot,int item,int id,long count)throws Exception{
  var owned=allocate(Owned.class);owned.id=id;owned.count=count;owned.template=DataManager.ITEM_DATA.getItemTemplate(item);bot.cube.objects.put(id,owned);return owned;
 }
 private static void pending(Bot bot,Owned item,int quest)throws Exception{
  PlayerBotQuestItems.close(bot);var state=bot.quests.getQuestState(quest);if(state==null){state=new QuestState(quest,QuestStatus.START);bot.quests.addQuest(quest,state);}else{state.setStatus(QuestStatus.START);state.setQuestVar(0);}
  var candidate=PlayerBotQuestItems.choose(bot,bot,System.currentTimeMillis());check(candidate!=null && candidate.item()==item && !candidate.starting(),"Owned current-stage item selected");
  check(PlayerBotQuestItems.use(bot,bot,candidate),"Native item callback emitted its real continuation page");
 }
 private static QuestState orderState(Bot bot){bot.quests=new QuestStateList();var q=new QuestState(ORDER,QuestStatus.START);bot.quests.addQuest(ORDER,q);return q;}
 private static QuestEnv contact(Bot bot,int npc,int action)throws Exception{var target=allocate(PlayerBotConversationCheck.Contact.class);target.id=npc;return new QuestEnv(target,bot,ORDER,action);}
 private static void installedAdmissionBefore(Bot bot)throws Exception{
  // Matching native Heiron1514 contacts, evaluated on the exact extracted installed class.
  var handler=new NativeOrders(ORDER,204582,204505,203831);var q=orderState(bot);
  var first=contact(bot,204582,DialogAction.SETPRO1);check(handler.onDialogEvent(first) && q.getQuestVarById(0)==1,"Installed first legitimate contact advances");
  check(handler.onDialogEvent(first) && q.getStatus()==QuestStatus.REWARD,"REPRO: installed repeated first contact skips second contact and reaches reward");
  q=orderState(bot);check(handler.onDialogEvent(contact(bot,204505,DialogAction.SETPRO1)) && q.getQuestVarById(0)==1,"REPRO: installed second contact can run before first");
  q=orderState(bot);check(handler.onDialogEvent(contact(bot,203831,DialogAction.SELECT_QUEST_REWARD)) && q.getStatus()==QuestStatus.REWARD,"REPRO: installed end contact can skip both required contacts");
  try(var bytes=ItemOrders.class.getResourceAsStream("ItemOrders.class")){
   String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
   check(hash.equals("c4a53cd52f564925e2dc692ab5e225364b58fdb2d56546d9033481531fffa0b0"),"Reproduction is bound to exact installed native class bytes");
   System.out.println("REPRODUCED: repeated-first, early-second and early-end native defects; installed ItemOrders SHA256="+hash+"; class source="+ItemOrders.class.getProtectionDomain().getCodeSource().getLocation());
  }
 }
 private static void orderVariants(Bot bot)throws Exception{
  int[][] variants={{204582,204505,203831},{203932,0,203830},{0,0,203933},{0,203932,203830},
   {203932,203932,203830},{203932,0,203932},{203932,203830,203830}};
  for(var ids:variants){
   int first=ids[0]!=0?ids[0]:ids[1],second=ids[0]!=0?ids[1]:0,end=ids[2];
   var handler=new NativeOrders(ORDER,ids[0],ids[1],end);var q=orderState(bot);q.setQuestVarById(1,9);
   check(!handler.onDialogEvent(new QuestEnv(null,bot,ORDER,DialogAction.SETPRO1)),"Unset contact never treats item-owned NPC zero as a report objective");
   if(first>0){
    if(second>0 && second!=first){
     check(!handler.onDialogEvent(contact(bot,second,DialogAction.QUEST_SELECT)),"Second contact cannot offer first-stage report page");
     check(!handler.onDialogEvent(contact(bot,second,DialogAction.SETPRO1)) && q.getQuestVarById(0)==0,"Second contact cannot skip first report");
    }
    check(!handler.onDialogEvent(contact(bot,end,DialogAction.SELECT_QUEST_REWARD)) && q.getStatus()==QuestStatus.START,"Early end report cannot skip declared contact objectives");
    var env=contact(bot,first,DialogAction.QUEST_SELECT);
    try(var dialog=PlayerBotQuestDialog.open(bot.id,env.getVisibleObject().getObjectId(),ORDER)){check(handler.onDialogEvent(env) && dialog.offered(1352),"Current real first contact retains native page1352");}
    env.setDialogActionId(DialogAction.SETPRO1);check(handler.onDialogEvent(env) && q.getQuestVarById(0)==1,"Current real first report advances exactly once");
    check(q.getQuestVarById(1)==9,"Report preserves other saved native variables");
    if(second>0){
     check(q.getStatus()==QuestStatus.START,"Two-contact native declaration still requires second report");
     if(first!=second){
      check(!handler.onDialogEvent(contact(bot,first,DialogAction.QUEST_SELECT)),"Stale first contact cannot reopen completed-stage report page");
      check(!handler.onDialogEvent(contact(bot,first,DialogAction.SETPRO1)) && q.getStatus()==QuestStatus.START && q.getQuestVarById(0)==1,"Repeated first report cannot skip second report");
     }
     check(!handler.onDialogEvent(contact(bot,end,DialogAction.SELECT_QUEST_REWARD)) && q.getStatus()==QuestStatus.START,"End report cannot skip remaining second contact");
     env=contact(bot,second,DialogAction.QUEST_SELECT);
     try(var dialog=PlayerBotQuestDialog.open(bot.id,env.getVisibleObject().getObjectId(),ORDER)){check(handler.onDialogEvent(env) && dialog.offered(1352),"Current second contact retains actual native page1352");}
     env.setDialogActionId(DialogAction.SETPRO1);check(handler.onDialogEvent(env) && q.getQuestVarById(0)==2,"Second report advances exact final contact");
    }
    check(q.getStatus()==QuestStatus.REWARD,"Last actual declared contact alone reaches native reward");
   }else{
    var env=contact(bot,end,DialogAction.QUEST_SELECT);
    try(var dialog=PlayerBotQuestDialog.open(bot.id,env.getVisibleObject().getObjectId(),ORDER)){check(handler.onDialogEvent(env) && dialog.offered(2375),"Zero-contact native order retains direct end report page2375");}
    env.setDialogActionId(DialogAction.SELECT_QUEST_REWARD);check(handler.onDialogEvent(env) && q.getStatus()==QuestStatus.REWARD && q.getQuestVarById(0)==1,"Zero-contact order retains actual native report-to-reward transition");
   }
   var reward=contact(bot,end,DialogAction.USE_OBJECT);
   try(var dialog=PlayerBotQuestDialog.open(bot.id,reward.getVisibleObject().getObjectId(),ORDER)){check(handler.onDialogEvent(reward) && dialog.offered(2375),"Earned native order reward can reopen actual end page");}
   check(q.getQuestVarById(1)==9,"Reward opening preserves unrelated saved variables");
   q.setStatus(QuestStatus.START);q.setQuestVarById(0,3);int saved=q.getQuestVars().getQuestVars();
   check(!handler.onDialogEvent(contact(bot,first,DialogAction.SETPRO1)) && q.getQuestVars().getQuestVars()==saved && q.getStatus()==QuestStatus.START,"Unsupported saved stage is retained without invented contact advancement");
   check(!handler.onDialogEvent(contact(bot,end,DialogAction.SELECT_QUEST_REWARD)) && q.getStatus()==QuestStatus.START,"Unsupported saved stage cannot bypass to reward");
  }
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);
  var oldQuests=DataManager.QUEST_DATA;var oldXml=DataManager.XML_QUESTS;var oldItems=DataManager.ITEM_DATA;var oldNpcs=DataManager.NPC_DATA;
  boolean oldEnable=PlayerBotConfig.QUESTS_ENABLED,oldItemsEnable=PlayerBotConfig.QUEST_ITEM_STARTS,oldCustom=PlayerBotConfig.QUEST_CUSTOM;
  int oldLimit=CustomConfig.BASIC_QUEST_SIZE_LIMIT;
  var engine=QuestEngine.getInstance();Map<Integer,AbstractQuestHandler> handlers;
  var hf=QuestEngine.class.getDeclaredField("questHandlers");hf.setAccessible(true);handlers=(Map<Integer,AbstractQuestHandler>)hf.get(engine);var oldHandlers=new HashMap<>(handlers);
  var itemsField=QuestEngine.class.getDeclaredField("questItemRelated");itemsField.setAccessible(true);var registrations=(Map<Integer,List<Integer>>)itemsField.get(engine);var oldRegistrations=new HashMap<>(registrations);
  var npcField=QuestEngine.class.getDeclaredField("questNpcs");npcField.setAccessible(true);var npcs=(Map<Integer,com.aionemu.gameserver.model.templates.quest.QuestNpc>)npcField.get(engine);var oldNpcsIndex=new HashMap<>(npcs);
  var completedField=QuestEngine.class.getDeclaredField("questOnCompleted");completedField.setAccessible(true);
  check(oldHandlers.isEmpty() && ((List<?>)completedField.get(engine)).isEmpty(),"Fixture requires cold standalone native engine; real registered completion callbacks must never run");
  Bot bot=null;
  try{
   PlayerBotConfig.QUESTS_ENABLED=true;PlayerBotConfig.QUEST_ITEM_STARTS=true;PlayerBotConfig.QUEST_CUSTOM=true;CustomConfig.BASIC_QUEST_SIZE_LIMIT=50;
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader(
    "<quests><quest id='900301' name='Owned native orders' category='QUEST'><quest_work_items><quest_work_item item_id='182200001' count='1'/></quest_work_items><rewards/></quest>"
    +"<quest id='900302' name='Followup owned orders' category='QUEST'><quest_work_items><quest_work_item item_id='182200002' count='1'/></quest_work_items><start_conditions><finished quest_id='900301'/></start_conditions><rewards/></quest>"
    +"<quest id='900303' name='Native item continuation' category='QUEST'><rewards/></quest><quest id='1038' name='Actual Shadows diary' category='MISSION'><rewards/></quest></quests>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader(
    "<quest_scripts><item_order id='900301' talk_npc_id1='203301' talk_npc_id2='203302' end_npc_id='203302'/><item_order id='900302' talk_npc_id1='203301' talk_npc_id2='203302' end_npc_id='203302'/></quest_scripts>"));
   DataManager.ITEM_DATA=(ItemData)JAXBContext.newInstance(ItemData.class).createUnmarshaller().unmarshal(new StringReader(
    "<item_templates>"+item(TOKEN,"<queststart questid='900301'/><read/>")+item(182200002,"<queststart questid='900302'/>")+item(CONT_ITEM,"")+item(182201007,"<read/>")+"</item_templates>"));
   DataManager.NPC_DATA=new NpcData(){@Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getNpcTemplate(int id){return new com.aionemu.gameserver.model.templates.npc.NpcTemplate();}};
   handlers.clear();registrations.clear();npcs.clear();engine.addQuestHandler(new NativeOrders(ORDER));engine.addQuestHandler(new NativeOrders(NEXT));engine.addQuestHandler(new Diary());var continuation=new Continuation();engine.addQuestHandler(continuation);
   bot=bot(1970301);var token=own(bot,TOKEN,1970311,3);own(bot,182200002,1970312,1);
   if(args.length>0 && args[0].equals("--installed-admission-before")){installedAdmissionBefore(bot);return;}
   check(DataManager.XML_QUESTS.getQuest(ORDER) instanceof com.aionemu.gameserver.questEngine.handlers.models.ItemOrdersData,"Actual native item_order XML model recognized");
   orderVariants(bot);bot.quests=new QuestStateList();
   check(!QuestService.checkStartConditions(bot,NEXT,false),"Native prerequisite rejects next quest before actual completion");
   var candidate=PlayerBotQuestItems.choose(bot,bot,System.currentTimeMillis());check(candidate!=null && candidate.starting() && candidate.quest()==ORDER,"Native owned eligible start selected before locked followup");
   check(PlayerBotQuestItems.use(bot,bot,candidate),"Actual QuestStartAction completes and emits native page four");
   check(bot.cooldowns==1 && bot.quests.getQuestState(ORDER)==null,"Native item completion applies cooldown without fabricating acceptance");
   check(PlayerBotQuestItems.finish(bot,bot,true),"Captured native item offer routed through actual ItemOrders and QuestService.startQuest");
   var orders=bot.quests.getQuestState(ORDER);check(orders.getStatus()==QuestStatus.START && token.count==3,"Acceptance retains original work token until native reward");
   check(!PlayerBotQuestItems.finish(bot,bot,true),"Consumed acceptance cannot repeat");
   var npc=allocate(PlayerBotConversationCheck.Contact.class);npc.id=203301;var env=new QuestEnv(npc,bot,ORDER,DialogAction.QUEST_SELECT);
   try(var dialog=PlayerBotQuestDialog.open(bot.id,npc.getObjectId(),ORDER)){
    check(engine.onDialog(env) && dialog.offered(1352),"Native first objective opens actual report page");env.setDialogActionId(DialogAction.SETPRO1);check(engine.onDialog(env),"Actual report advances first native objective");
   }
   check(orders.getQuestVarById(0)==1 && orders.getStatus()==QuestStatus.START,"First objective preserves second objective");
   npc.id=203302;env.setDialogActionId(DialogAction.QUEST_SELECT);check(engine.onDialog(env),"Native second report opens");env.setDialogActionId(DialogAction.SETPRO1);check(engine.onDialog(env),"Native second report advances");
   check(orders.getStatus()==QuestStatus.REWARD && orders.getQuestVarById(0)==2 && token.count==3,"Only earned objective chain reaches reward; token not prematurely consumed");
   env.setDialogActionId(DialogAction.USE_OBJECT);try(var dialog=PlayerBotQuestDialog.open(bot.id,npc.getObjectId(),ORDER)){check(engine.onDialog(env) && dialog.offered(2375),"Native reward opens exact end page");}
   env.setDialogActionId(DialogAction.SELECTED_QUEST_NOREWARD);check(engine.onDialog(env),"Actual native reward service completes quest without item allocation");
   check(orders.getStatus()==QuestStatus.COMPLETE && orders.getCompleteCount()==1 && orders.getQuestVars().getQuestVars()==0,"Native reward owns completion count and variable reset");
   check(token.count==2 && bot.cube.removals==1,"Native reward consumes exact one token and preserves surplus");
   check(!engine.onDialog(env) && token.count==2 && bot.cube.removals==1,"Completed native reward cannot be charged or completed twice");
   check(QuestService.checkStartConditions(bot,NEXT,false),"Actual completion unlocks native followup prerequisite");
   check(PlayerBotQuestItems.startable(bot,bot,NEXT),"AI next activity observes native unlocked followup");
   check(!PlayerBotQuestItems.startable(bot,bot,ORDER),"Completed nonrepeatable item quest cannot be started again");
   PlayerBotQuestItems.close(bot);candidate=PlayerBotQuestItems.choose(bot,bot,System.currentTimeMillis());check(candidate!=null && candidate.quest()==NEXT,"AI selects actual newly unlocked owned followup");check(PlayerBotQuestItems.use(bot,bot,candidate) && PlayerBotQuestItems.finish(bot,bot,true),"Actual native followup acceptance closes full chain handoff");
   var diary=own(bot,182201007,1970313,1);var shadow=new QuestState(1038,QuestStatus.START);shadow.setQuestVarById(0,2);bot.quests.addQuest(1038,shadow);PlayerBotQuestItems.close(bot);
   candidate=PlayerBotQuestItems.choose(bot,bot,System.currentTimeMillis());check(candidate!=null && candidate.item()==diary && !candidate.starting(),"Actual handler declares owned saved diary objective");
   check(PlayerBotQuestItems.use(bot,bot,candidate),"Actual native diary use and ReadAction run through shared adapter");check(shadow.getQuestVarById(0)==3 && diary.count==1,"Actual Shadows acquisition continuation advances and retains diary");check(PlayerBotQuestItems.finish(bot,bot,true),"Native progress observed after active item action");
   bot.cube.objects.clear();var objective=own(bot,CONT_ITEM,1970314,1);pending(bot,objective,CONT);check(PlayerBotQuestItems.finish(bot,bot,true),"Exact native item page and fresh continuation execute");check(continuation.first==1 && continuation.second==1,"Both admitted native actions execute once");
   continuation.first=0;continuation.second=0;continuation.fresh=false;pending(bot,objective,CONT);check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.second==0,"Missing fresh native continuation page prevents second action");continuation.fresh=true;
   continuation.first=0;continuation.second=0;pending(bot,objective,CONT);PlayerBotConfig.QUESTS_ENABLED=false;check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Disabling automation blocks initial stored-page dispatch");PlayerBotConfig.QUESTS_ENABLED=true;
   pending(bot,objective,CONT);bot.controller.using=true;check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Native item task must finish before item-dialog continuation");bot.controller.using=false;check(PlayerBotQuestItems.finish(bot,bot,true),"Finished native item task allows real continuation");
   continuation.first=0;continuation.second=0;continuation.transfer=true;pending(bot,objective,CONT);check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==1 && continuation.second==0,"Map transfer during native first action prevents stale continuation");continuation.transfer=false;
   continuation.first=0;continuation.second=0;continuation.disable=true;pending(bot,objective,CONT);check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.second==0,"Config change during native first action prevents new continuation");continuation.disable=false;PlayerBotConfig.QUESTS_ENABLED=true;
   continuation.first=0;continuation.second=0;pending(bot,objective,CONT);bot.dead=true;check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Death cancels pending native page without credit");bot.dead=false;
   pending(bot,objective,CONT);PlayerBotQuestItems.close(bot);check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Closing actor discards pending page authorization");
   pending(bot,objective,CONT);bot.cube.objects.remove(objective.id);check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Transferred owned item cannot authorize stored-page dispatch");bot.cube.objects.put(objective.id,objective);
   pending(bot,objective,CONT);objective.count=0;check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Exhausted owned item cannot authorize stored-page dispatch");objective.count=1;
   pending(bot,objective,CONT);bot.instance++;check(!PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Instance transfer cancels pending item page");
   pending(bot,objective,CONT);check(!PlayerBotQuestItems.finish(bot,bot,false) && continuation.first==0,"Noncombat arbitration deferral cannot dispatch item dialog");check(PlayerBotQuestItems.finish(bot,bot,true),"Later native-safe arbitration resumes authorized item dialog");
   continuation.first=0;continuation.second=0;pending(bot,objective,CONT);var heldState=bot.quests.getQuestState(CONT);heldState.setQuestVarById(0,1);PlayerBotConfig.QUESTS_ENABLED=false;
   check(PlayerBotQuestItems.finish(bot,bot,true) && continuation.first==0,"Disabled automation still observes objective actually completed by native event");PlayerBotConfig.QUESTS_ENABLED=true;
   for(int mode=0;mode<5;mode++){
    PlayerBotQuestItems.close(bot);bot.quests=new QuestStateList();bot.cube.objects.clear();var startToken=own(bot,TOKEN,1970390+mode,1);
    candidate=PlayerBotQuestItems.choose(bot,bot,System.currentTimeMillis());check(candidate!=null && PlayerBotQuestItems.use(bot,bot,candidate),"Native start offer captured before late guard "+mode);
    switch(mode){case 0->bot.cube.objects.remove(startToken.id);case 1->startToken.count=0;case 2->PlayerBotConfig.QUEST_ITEM_STARTS=false;case 3->bot.instance++;case 4->PlayerBotQuestItems.close(bot);}
    check(!PlayerBotQuestItems.finish(bot,bot,true) && bot.quests.getQuestState(ORDER)==null,"Late ownership/config/instance/cancellation guard preserves unaccepted state "+mode);PlayerBotConfig.QUEST_ITEM_STARTS=true;
   }
   System.out.println("OK: "+checks+" actual native owned-item acceptance/objectives/reward/followup and stored-page guard checks; no World/IDs/scheduler/SQL/award allocation");
  }finally{
   if(bot!=null)PlayerBotQuestItems.close(bot);handlers.clear();handlers.putAll(oldHandlers);registrations.clear();registrations.putAll(oldRegistrations);npcs.clear();npcs.putAll(oldNpcsIndex);
   DataManager.QUEST_DATA=oldQuests;DataManager.XML_QUESTS=oldXml;DataManager.ITEM_DATA=oldItems;DataManager.NPC_DATA=oldNpcs;PlayerBotConfig.QUESTS_ENABLED=oldEnable;PlayerBotConfig.QUEST_ITEM_STARTS=oldItemsEnable;PlayerBotConfig.QUEST_CUSTOM=oldCustom;CustomConfig.BASIC_QUEST_SIZE_LIMIT=oldLimit;
  }
 }
 private static String item(int id,String actions){return "<item_template id='"+id+"' name='Inert owned quest item' level='1' mask='0' item_group='QUEST' quality='COMMON' price='0' desc='1' casting_delay='0'><actions>"+actions+"</actions><uselimits/></item_template>";}
}
