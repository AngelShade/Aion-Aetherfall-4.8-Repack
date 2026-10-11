/* Owned quest-item selection and native acceptance adapted from UseItemAction.cpp /
 * UseRandomQuestItem at mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Aion completes its native
 * item cast before offering acceptance; no client packet or progress is invented. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.actions.*;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute;

/** One owned item and its actual asynchronous native offer, isolated per actor. */
final class PlayerBotQuestItems {
 record Candidate(Item item, AbstractItemAction action, int quest, boolean starting, Creature target) {
  Candidate(Item item,AbstractItemAction action,int quest,boolean starting){this(item,action,quest,starting,null);}
  Candidate(Item item,QuestStartAction action){this(item,action,action.getQuestId(),true);}
 }
 static final class Request {
  final int player,item,quest,world,instance,variables;final QuestStatus status;final boolean starting; final long deadline;
  volatile Integer page;
  Request(Player bot,Candidate candidate,long now) {
   player=bot.getObjectId();item=candidate.item().getObjectId();quest=candidate.quest();
   world=bot.getWorldId();instance=bot.getInstanceId();
   var state=bot.getQuestStateList()==null?null:bot.getQuestStateList().getQuestState(quest);
   status=state==null?null:state.getStatus();variables=state==null?0:state.getQuestVars().getQuestVars();starting=candidate.starting();
   deadline=now+Math.max(30000L, candidate.item().getItemTemplate().getCastingDelay()+10000L);
  }
  boolean matches(int player,int npc,int quest,long now) {
   return this.player==player && npc==0 && this.quest==quest && now<deadline;
  }
 }
 private static final class State {
  Request request; long nextScan;
  final Map<Integer,Long> retry=new HashMap<>();
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 private static final Map<Integer,Request> OFFERS=new ConcurrentHashMap<>();
 static void observe(int player,int npc,int page,int quest) {
  Request request=OFFERS.get(player);
  if(request!=null && request.matches(player,npc,quest,System.currentTimeMillis()))request.page=page;
 }
 static void close(Player bot) { STATES.remove(bot.getObjectId());OFFERS.remove(bot.getObjectId()); }
 static boolean startable(Player owner,Player bot,int quest) {
  if(!com.aionemu.gameserver.configs.main.PlayerBotConfig.QUEST_ITEM_STARTS || !PlayerBotQuestFeatures.enabled(quest))return false;
  var current=bot.getQuestStateList().getQuestState(quest);
  return quest>0 && (current==null || current.isStartable())
   && PlayerBotQuestSync.wanted(owner,bot,quest)
   && !QuestEngine.getInstance().getPlayerBotHandlerType(quest).isEmpty()
   && QuestService.checkStartConditions(bot,quest,false);
 }
 static Candidate choose(Player owner,Player bot,long now) {
  return choose(owner,bot,now,false);
 }
 static boolean active(Player owner,Player bot,Candidate candidate) {
  if(!PlayerBotQuestSync.wanted(owner,bot,candidate.quest()))return false;
  return PlayerBotQuestActions.actions(bot,candidate.quest()).stream().anyMatch(a->PlayerBotQuestActions.items(bot,a)
   && (a.kind()==QuestBotAction.Kind.ITEM && a.targetId()==candidate.item().getItemId()
    || a.kind()==QuestBotAction.Kind.SKILL && candidate.action() instanceof SkillUseAction skill && a.targetId()==skill.getSkillId()));
 }
 static boolean eligible(Player owner,Player bot,Candidate candidate){return candidate.starting()?startable(owner,bot,candidate.quest()):active(owner,bot,candidate);}
 static Candidate choose(Player owner,Player bot,long now,boolean combat) {
  return choose(owner,bot,now,combat,bot.getTarget() instanceof Creature target?target:null);
 }
 static Candidate choose(Player owner,Player bot,long now,boolean combat,Creature currentEnemy) {
  State state=STATES.computeIfAbsent(bot.getObjectId(),id->new State());
  if(state.request!=null || now<state.nextScan)return null;
  state.nextScan=now+2000;state.retry.entrySet().removeIf(entry->entry.getValue()<=now);
  for(Item item:bot.getInventory().getItems()) {
   if(item.getItemCount()<=0 || bot.hasCooldown(item))continue;
   var actions=item.getItemTemplate().getActions()==null?List.<AbstractItemAction>of():item.getItemTemplate().getActions().getItemActions();
   if(!combat)for(var action:actions)if(action instanceof QuestStartAction start) {
    PlayerBotQuestSync.nearby(owner,bot,start.getQuestId());
    if(state.retry.getOrDefault(start.getQuestId(),0L)>now || !startable(owner,bot,start.getQuestId()))continue;
    if(PlayerRestrictions.canUseItem(bot,item) && start.canAct(bot,item,null))return new Candidate(item,start);
   }
   for(var quest:bot.getQuestStateList().getUncompletedQuests()) {
    if(state.retry.getOrDefault(quest.getQuestId(),0L)>now || !PlayerBotQuestSync.wanted(owner,bot,quest.getQuestId()))continue;
    for(var intent:PlayerBotQuestActions.actions(bot,quest.getQuestId())) {
     if(!PlayerBotQuestActions.items(bot,intent))continue;
     AbstractItemAction selected=null;
     if(intent.kind()==QuestBotAction.Kind.ITEM && intent.targetId()==item.getItemId() && !combat) {
      selected=actions.stream().filter(a->!(a instanceof QuestStartAction)).findFirst().orElse(null);
     } else if(intent.kind()==QuestBotAction.Kind.SKILL) {
      selected=actions.stream().filter(a->a instanceof SkillUseAction skill && skill.getSkillId()==intent.targetId()).findFirst().orElse(null);
      if(selected==null)continue;
     } else continue;
     if(actions.stream().anyMatch(a->a instanceof QuestStartAction))continue;
     if(!PlayerRestrictions.canUseItem(bot,item))continue;
     if(selected instanceof SkillUseAction skill) {
      Creature target=skillTarget(bot,item,skill,currentEnemy);
      if(target!=null)return new Candidate(item,selected,quest.getQuestId(),false,target);
     }else if(selected==null || selected.canAct(bot,item,null))return new Candidate(item,selected,quest.getQuestId(),false);
    }
   }
  }
  return null;
 }
 /** UseItemAction's explicit unit target is adapted through native item skill
  * properties. Planning never changes the actor's selected target or starts a cast. */
 static Creature skillTarget(Player bot,Item item,SkillUseAction action,Creature currentEnemy) {
  var template=DataManager.SKILL_DATA==null?null:DataManager.SKILL_DATA.getSkillTemplate(action.getSkillId());
  if(template==null || template.getProperties()==null)return null;
  var props=template.getProperties();var first=props.getFirstTarget();
  List<? extends Creature> targets;
  if(first==FirstTargetAttribute.ME || first==FirstTargetAttribute.PASSIVE)targets=List.of(bot);
  else if(first==FirstTargetAttribute.MYPET)targets=bot.getSummon()==null?List.of():List.of(bot.getSummon());
  else if(first==FirstTargetAttribute.TARGET || first==FirstTargetAttribute.TARGETORME) {
   if(template.hasResurrectEffect()) {
    var allies=PlayerBotSupport.members(bot);
    targets=allies.stream().filter(p->p!=bot && p.isDead() && !p.getResStatus()
     && !PlayerBotService.getInstance().isReserved(bot,p,PlayerBotRules.SkillKind.RESURRECT)
     && allies.stream().noneMatch(a->{var cast=a.getCastingSkill();return a!=bot && cast!=null
      && cast.getSkillTemplate().hasResurrectEffect() && (cast.getFirstTarget()==p || cast.getEffectedList().contains(p));}))
     .sorted(Comparator.<Player>comparingDouble(p->com.aionemu.gameserver.utils.PositionUtil.getDistance(bot,p)).thenComparingInt(Player::getObjectId)).toList();
   }else if(props.getTargetRelation()==TargetRelationAttribute.ENEMY || props.getTargetRelation()==TargetRelationAttribute.ALL)
    targets=currentEnemy instanceof Npc && PlayerBotService.allowsTarget(bot,currentEnemy)?List.of(currentEnemy):List.of();
   else targets=PlayerBotSupport.members(bot);
  }else return null; // Point/remote-party item use needs an explicit native choice.
  for(var target:targets)if(targetEligible(bot,action,target) && action.canActOn(bot,item,null,target))return target;
  return null;
 }
 static boolean targetEligible(Player bot,SkillUseAction action,Creature target) {
  var template=DataManager.SKILL_DATA==null?null:DataManager.SKILL_DATA.getSkillTemplate(action.getSkillId());
  if(template==null || target==null || !target.isSpawned() || target.getWorldId()!=bot.getWorldId() || target.getInstanceId()!=bot.getInstanceId())return false;
  if(target!=bot && (bot.getKnownList()==null || !bot.getKnownList().sees(target)))return false;
  if(template.hasResurrectEffect())return target instanceof Player player && PlayerBotSupport.eligible(bot,player)
   && player!=bot && player.isDead() && !player.getResStatus()
   && !PlayerBotService.getInstance().isReserved(bot,player,PlayerBotRules.SkillKind.RESURRECT);
  if(target.isDead())return false;
  if(target==bot || target==bot.getSummon())return true;
  if(target instanceof Player player)return PlayerBotSupport.eligible(bot,player);
  return target instanceof Npc && PlayerBotService.allowsTarget(bot,target);
 }
 private static boolean canAct(Player bot,Candidate candidate,AbstractItemAction action) {
  if(action instanceof SkillUseAction skill && candidate.target()!=null)
   return targetEligible(bot,skill,candidate.target()) && skill.canActOn(bot,candidate.item(),null,candidate.target());
  return action.canAct(bot,candidate.item(),null);
 }
 static boolean use(Player owner,Player bot,Candidate candidate) {
  long now=System.currentTimeMillis();State state=STATES.computeIfAbsent(bot.getObjectId(),id->new State());
  if(state.request!=null || bot.isCasting() || bot.isLooting() || bot.getController().hasScheduledTask(TaskId.ITEM_USE)
   || bot.getInventory().getItemByObjId(candidate.item().getObjectId())!=candidate.item()
   || !eligible(owner,bot,candidate) || !PlayerRestrictions.canUseItem(bot,candidate.item())
   || candidate.action()!=null && !canAct(bot,candidate,candidate.action()))return false;
  Request request=new Request(bot,candidate,now);state.request=request;OFFERS.put(bot.getObjectId(),request);
  state.retry.put(candidate.quest(),candidate.starting()?request.deadline+30000:now+2000);
  try {
   // Match native CM_USE_ITEM: observers, the item-use handler (except delayed
   // starts), then every admitted action in its original declared order.
   bot.getObserveController().notifyItemuseObservers(candidate.item());
   var declared=candidate.item().getItemTemplate().getActions()==null?List.<AbstractItemAction>of():candidate.item().getItemTemplate().getActions().getItemActions();
   var result=declared.stream().anyMatch(a->a instanceof QuestStartAction)?com.aionemu.gameserver.questEngine.handlers.HandlerResult.UNKNOWN
    :QuestEngine.getInstance().onItemUseEvent(new QuestEnv(null,bot,0),candidate.item());
   var admitted=new ArrayList<AbstractItemAction>();
   for(var action:declared) {
    // These native operations require an explicit design/target/instance
    // choice. A quest-item request supplies none and must not guess one.
    if(action instanceof DyeAction || action instanceof MultiReturnAction || action instanceof InstanceTimeClear)continue;
    if(canAct(bot,candidate,action))admitted.add(action);
   }
   if(admitted.isEmpty() && result!=com.aionemu.gameserver.questEngine.handlers.HandlerResult.SUCCESS){clear(bot,state);return false;}
   for(var action:admitted) {
    if(action instanceof SkillUseAction skill && candidate.target()!=null) {
     if(!eligible(owner,bot,candidate) || !canAct(bot,candidate,skill)
      || !skill.actOn(bot,candidate.item(),null,candidate.target())){clear(bot,state);return false;}
     var template=DataManager.SKILL_DATA.getSkillTemplate(skill.getSkillId());
     if(template.hasResurrectEffect())PlayerBotService.getInstance().reserve(bot,candidate.target(),PlayerBotRules.SkillKind.RESURRECT,
      now+Math.max(candidate.item().getItemTemplate().getCastingDelay(),template.getDuration())+2000L);
    }else action.act(bot,candidate.item(),null);
   }
   return true;
  }
  catch(RuntimeException | Error error) { clear(bot,state);throw error; }
 }
 static boolean finish(Player owner,Player bot,boolean allowed) {
  State state=STATES.get(bot.getObjectId());if(state==null || state.request==null)return false;
  Request request=state.request;long now=System.currentTimeMillis();
  if(bot.isDead() || bot.getWorldId()!=request.world || bot.getInstanceId()!=request.instance || now>=request.deadline) {
   clear(bot,state);return false;
  }
  if(!allowed || bot.getController().hasScheduledTask(TaskId.ITEM_USE))return false;
  if(!request.starting) {
   var current=bot.getQuestStateList().getQuestState(request.quest);
   boolean progressed=current!=null && (current.getStatus()!=request.status || current.getQuestVars().getQuestVars()!=request.variables);
   try {
    if(!progressed && current!=null && request.page!=null && PlayerBotQuestFeatures.enabled(request.quest)
     && PlayerBotQuestSync.wanted(owner,bot,request.quest))for(var action:PlayerBotQuestActions.actions(bot,request.quest))
     if(action.kind()==QuestBotAction.Kind.ITEM && action.dialogAction()>0 && action.page()>0 && Objects.equals(request.page,action.page())
      && PlayerBotQuestActions.items(bot,action)) {
      var item=bot.getInventory().getItemByObjId(request.item);if(item==null || action.targetId()!=item.getItemId())continue;
      try(var dialog=PlayerBotQuestDialog.open(bot.getObjectId(),0,request.quest)) {
       request.page=null;QuestEngine.getInstance().onDialog(new QuestEnv(null,bot,request.quest,action.dialogAction()));
       for(var continuation:action.continuation()) {
        if(!dialog.offered(continuation.page()) || bot.isDead() || bot.getWorldId()!=request.world || bot.getInstanceId()!=request.instance
         || !PlayerBotQuestFeatures.enabled(request.quest) || !PlayerBotQuestSync.wanted(owner,bot,request.quest))break;
        dialog.clearPage();QuestEngine.getInstance().onDialog(new QuestEnv(null,bot,request.quest,continuation.action()));
       }
      }
      progressed=current.getStatus()!=request.status || current.getQuestVars().getQuestVars()!=request.variables;break;
     }
    return progressed;
   } finally {clear(bot,state);}
  }
  // Accept only the offer the native item completion actually emitted. A custom
  // story page belongs to the handler action contract, never a guessed accept.
  if(!Objects.equals(request.page,4)) { clear(bot,state);return false; }
  Item item=bot.getInventory().getItemByObjId(request.item);
  try {
   if(item==null || item.getItemCount()<=0 || !startable(owner,bot,request.quest))return false;
   QuestEngine.getInstance().onDialog(new QuestEnv(null,bot,request.quest,DialogAction.QUEST_ACCEPT));
   var current=bot.getQuestStateList().getQuestState(request.quest);
   boolean accepted=current!=null && (current.getStatus()==QuestStatus.START || current.getStatus()==QuestStatus.REWARD);
   if(accepted)PlayerBotQuestSync.accepted(owner,bot,request.quest,false);
   return accepted;
  } finally { clear(bot,state); }
 }
 private static void clear(Player bot,State state) {
  OFFERS.remove(bot.getObjectId(),state.request);state.request=null;
 }
 private PlayerBotQuestItems() {}
}
