/* Current native objectives adapt pinned QuestAction / ChooseTravelTargetAction
 * at mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * See third-party/playerbots/AUTHORS.md. Handler discovery is not authorization. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Shared current-stage discovery for local actions and complete world travel. */
final class PlayerBotQuestActions {
 private static final Map<Integer,Map<String,Long>> RETRIES=new ConcurrentHashMap<>();
 static List<QuestBotAction> actions(Player bot,int quest) {
  if(!PlayerBotQuestFeatures.enabled(quest))return List.of();
  int required=PlayerBotQuestSync.requiredReward(bot,quest);
  return required==-2 ? List.of() : QuestEngine.getInstance().getPlayerBotActions(bot,quest,required);
 }
 /** Read-only custody requirements remain protected when AI admission is disabled. */
 static List<QuestBotAction> custody(Player bot,int quest) {return QuestEngine.getInstance().getPlayerBotActions(bot,quest);}
 static boolean items(Player bot,QuestBotAction action) {
  return action.requiredItems().entrySet().stream().allMatch(e->bot.getInventory().getItemCountByItemId(e.getKey())>=e.getValue());
 }
 static Set<Integer> ids(Player bot,int quest,QuestBotAction.Kind kind) {
  var result=new TreeSet<Integer>();for(var action:actions(bot,quest))
   if(action.kind()==kind && action.targetId()>0 && items(bot,action))result.add(action.targetId());
  return Set.copyOf(result);
 }
 static Set<Integer> scriptedNpcs(Player bot,int quest) {
  var result=new TreeSet<Integer>();for(var action:actions(bot,quest))
   if((action.kind()==QuestBotAction.Kind.EVENT || action.kind()==QuestBotAction.Kind.FLIGHT && com.aionemu.gameserver.configs.main.PlayerBotConfig.QUEST_FLIGHT)
    && action.targetId()>0 && action.dialogAction()>0 && items(bot,action))result.add(action.targetId());
  return Set.copyOf(result);
 }
 static boolean object(Player bot,int quest,int npc) {
  return actions(bot,quest).stream().anyMatch(a->a.kind()==QuestBotAction.Kind.OBJECT && a.targetId()==npc && items(bot,a));
 }
 static Set<Integer> skills(Player owner,Player bot) {
  var result=new HashSet<Integer>();
  for(var state:bot.getQuestStateList().getUncompletedQuests())if(PlayerBotQuestSync.wanted(owner,bot,state.getQuestId()))
   result.addAll(ids(bot,state.getQuestId(),QuestBotAction.Kind.SKILL));
  return Set.copyOf(result);
 }
 static boolean tickObjects(PlayerBotSession session,PlayerBotNavigation navigation,boolean allowed) {
  if(!allowed)return false;var bot=session.bot();var owner=session.owner();long now=System.currentTimeMillis();
  var retry=RETRIES.computeIfAbsent(bot.getObjectId(),id->new HashMap<>());retry.entrySet().removeIf(e->e.getValue()<=now);
  for(var state:bot.getQuestStateList().getUncompletedQuests()) {
   int quest=state.getQuestId();if(!PlayerBotQuestSync.wanted(owner,bot,quest))continue;
   for(var action:actions(bot,quest)) {
    String key=quest+":"+action.targetId();
    boolean scripted=(action.kind()==QuestBotAction.Kind.EVENT || action.kind()==QuestBotAction.Kind.FLIGHT && com.aionemu.gameserver.configs.main.PlayerBotConfig.QUEST_FLIGHT)
     && action.targetId()>0 && action.dialogAction()>0;
    if(action.kind()!=QuestBotAction.Kind.OBJECT && !scripted || !items(bot,action) || retry.containsKey(key))continue;
    // If the required summoned creature is already present, fight it instead
    // of repeatedly activating a trigger and duplicating native spawns.
    Set<Integer> hunts=ids(bot,quest,QuestBotAction.Kind.HUNT);boolean[] live={false};
    bot.getKnownList().forEachNpc(n->{if(hunts.contains(n.getNpcId()) && n.isSpawned() && !n.isDead())live[0]=true;});
    if(live[0])continue;
    List<Npc> candidates=new ArrayList<>();
    owner.getKnownList().forEachNpc(n->{if(n.getNpcId()==action.targetId() && n.isSpawned() && !n.isDead()
     && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && !bot.isEnemy(n)
     && owner.getKnownList().sees(n) && bot.getKnownList().sees(n) && PositionUtil.isInRange(owner,n,25)
     && DialogService.isInteractionAllowed(bot,n) && PlayerBotQuestObjectives.matches(bot,scripted?PlayerBotQuestRoutes.Kind.CONVERSATION:PlayerBotQuestRoutes.Kind.OBJECT,quest,n))candidates.add(n);});
    var npc=candidates.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
    if(npc==null)continue;
    // Timed quest-use objects must finish through their native AI; it also
    // registers earned quest drops. The shared collector owns that task.
    if("quest_use_item".equals(npc.getAi().getName()))continue;
    if(!PositionUtil.isInTalkRange(bot,npc) || !GeoService.getInstance().canSee(bot,npc))return navigation.approach(npc,2);
    // Re-query before dispatch; discovery cannot authorize a stale objective.
    if(!actions(bot,quest).contains(action) || !items(bot,action))continue;
    navigation.stop();
    try(var dialog=PlayerBotQuestDialog.open(bot.getObjectId(),npc.getObjectId(),quest)) {
     int variables=state.getQuestVars().getQuestVars();var status=state.getStatus();
     npc.getController().onDialogSelect(scripted ? action.dialogAction() : DialogAction.USE_OBJECT,0,bot,quest,0);
     if(!scripted && action.dialogAction()>0 && state.getStatus()==status && state.getQuestVars().getQuestVars()==variables) {
      if(action.page()<=0 || !dialog.offered(action.page())) { retry.put(key,now+5000);PlayerBotQuestObjectives.attempted(bot,false);return false; }
      dialog.clearPage();npc.getController().onDialogSelect(action.dialogAction(),action.page(),bot,quest,0);
      for(var continuation:action.continuation()) {
       if(state.getStatus()!=status || state.getQuestVars().getQuestVars()!=variables)break;
       if(!dialog.offered(continuation.page()) || npc.getWorldId()!=bot.getWorldId() || npc.getInstanceId()!=bot.getInstanceId()
        || !PositionUtil.isInTalkRange(bot,npc) || !GeoService.getInstance().canSee(bot,npc) || !DialogService.isInteractionAllowed(bot,npc))return false;
       dialog.clearPage();npc.getController().onDialogSelect(continuation.action(),continuation.page(),bot,quest,0);
      }
     }
     retry.put(key,now+30000);
     boolean[] spawned={false};bot.getKnownList().forEachNpc(n->{if(hunts.contains(n.getNpcId()) && n.isSpawned() && !n.isDead())spawned[0]=true;});
     PlayerBotQuestObjectives.attempted(bot,spawned[0] || state.getStatus()!=status || state.getQuestVars().getQuestVars()!=variables
      || bot.isUsingFlightTransporterOrWindstream() || !bot.isSpawned()
      || bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress());return true;
    } finally { DialogService.onCloseDialog(bot,npc); }
   }
  }
  return false;
 }
 static void close(Player bot){RETRIES.remove(bot.getObjectId());}
 private PlayerBotQuestActions() {}
}
