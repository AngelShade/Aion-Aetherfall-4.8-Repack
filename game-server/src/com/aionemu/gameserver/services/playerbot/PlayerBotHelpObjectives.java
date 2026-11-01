package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** A human's accepted help with a world bot's concrete need. It lives as long as their party, which a restart ends;
 * what must survive is each recipient's claim, kept in that bot's own checkpoint by PlayerBotGrowth.learnHelp.
 * The helper sees it as the one reusable quest HELP_QUEST, abandoned rather than completed so it pays and counts nothing;
 * Aetherfall Core reads title() for the name just before the client builds that quest's rows. */
public final class PlayerBotHelpObjectives {
 public static final int HELP_QUEST=14996;
 private static final long SWEEP=30000;
 static final class Binding {
  final Player requester,helper;final int account,quest,npc,round;final String title;
  final Set<Integer> participants=ConcurrentHashMap.newKeySet();
  int defeated;
  volatile boolean shown,finished;
  Binding(Player requester,Player helper,int quest,int npc,int round,String title) {
   this.requester=requester;this.helper=helper;account=helper.getAccount().getId();this.quest=quest;this.npc=npc;this.round=round;this.title=title;
  }
  /** Abandoning the quest in the journal ends the help. A finished target stays until every member has reported its death. */
  boolean active(){return PlayerBotTeams.same(requester,helper) && (!shown || finished || helping(helper));}
  boolean same(Player bot,int quest,int npc,int round){return requester==bot && this.quest==quest && this.npc==npc && this.round==round;}
 }
 private static final Map<Integer,Binding> BY_TEAM=new ConcurrentHashMap<>();
 private static volatile boolean sweeping;

 private static boolean helping(Player helper) {
  var state=helper.getQuestStateList().getQuestState(HELP_QUEST);return state!=null && state.getStatus()==QuestStatus.START;
 }
 private static void show(Binding binding) {
  var quests=DataManager.QUEST_DATA;if(quests==null || quests.getQuestById(HELP_QUEST)==null)return;
  binding.shown=QuestService.startQuest(new QuestEnv(null,binding.helper,HELP_QUEST),QuestStatus.START,false);
  if(binding.shown && !sweeping){sweeping=true;ThreadPoolManager.getInstance().scheduleAtFixedRate(PlayerBotHelpObjectives::sweep,SWEEP,SWEEP);}
 }
 private static void hide(Binding binding,String notice) {
  if(!binding.shown || !binding.helper.isOnline())return;
  if(QuestService.abandonQuest(binding.helper,HELP_QUEST))PacketSendUtility.sendMessage(binding.helper,notice);
 }
 private static void sweep() {
  for(var entry:BY_TEAM.entrySet()) {
   var binding=entry.getValue();
   if(binding.active() && !binding.finished)continue;
   if(BY_TEAM.remove(entry.getKey(),binding) && !binding.finished)hide(binding,"Your help request ended.");
  }
 }
 /** The helper's current quest title, for the client, or empty when it has none. */
 public static String title(Player helper) {
  for(var binding:BY_TEAM.values())if(binding.helper==helper && binding.active())return binding.title;
  return "";
 }
 /** A saved help quest whose party ended while its helper was away. */
 public static void entered(Player helper) {
  if(helping(helper) && title(helper).isEmpty())QuestService.abandonQuest(helper,HELP_QUEST);
 }

 /** Called when the human accepts the bot's native invitation. Offers, levelling and instance runs have no objective here. */
 static void accept(Player bot,Player human,PlayerBotGroupNeeds.Need need) {
  if(need.instance() || PlayerBotGroupNeeds.helping(need) || need.quest()<=0 && need.npc()<=0 || bot.getCurrentTeam()==null)return;
  BY_TEAM.values().removeIf(b->!b.active());
  int round=0;
  if(need.quest()>0) {
   var state=bot.getQuestStateList().getQuestState(need.quest());
   if(state==null || state.getStatus()!=QuestStatus.START)return;
   round=state.getCompleteCount();
  }
  int quest=need.quest(),npc=need.npc(),count=round,team=bot.getCurrentTeam().getTeamId();
  // A reopened post is the same objective, and an account helps with one at a time.
  if(BY_TEAM.values().stream().anyMatch(b->b.account==human.getAccount().getId() || b.same(bot,quest,npc,count)))return;
  String title="Help "+bot.getName()+": "+need.title();
  var binding=new Binding(bot,human,quest,npc,round,title.length()>96 ? title.substring(0,96) : title);
  if(BY_TEAM.putIfAbsent(team,binding)==null)show(binding);
 }
 private static Binding bound(Player member) {
  if(BY_TEAM.isEmpty() || member.getCurrentTeam()==null)return null;
  int team=member.getCurrentTeam().getTeamId();var binding=BY_TEAM.get(team);
  if(binding!=null && !binding.active()){if(BY_TEAM.remove(team,binding))hide(binding,"Your help request ended.");return null;}
  return binding;
 }
 static boolean committed(Player member) { return bound(member) != null; }
 private static boolean objective(int quest,Npc npc) {
  return QuestEngine.getInstance().getQuestNpc(npc.getNpcId()).getOnKillEvent().contains(quest)
   || QuestService.getQuestDrop(npc.getNpcId()).stream().anyMatch(drop->drop.getQuestId()==quest);
 }
 /** A kill that already earned practice. A quest pays at the turn-in; a requested target pays as it dies. */
 static void kill(Player bot,Npc npc) {
  var binding=bound(bot);if(binding==null)return;
  if(binding.quest>0) {
   if(npc.getNpcId()==binding.npc || objective(binding.quest,npc))binding.participants.add(bot.getObjectId());
   return;
  }
  if(npc.getNpcId()!=binding.npc)return;
  synchronized(binding) {
   // Each eligible party member reports the same death; another spawn is not this objective.
   if(binding.defeated==0){binding.defeated=npc.getObjectId();binding.finished=true;hide(binding,binding.title+" is complete.");PlayerBotFindGroup.helpCompleted(binding.requester);}
   else if(binding.defeated!=npc.getObjectId())return;
  }
  PlayerBotGrowth.learnHelp(bot,"n"+binding.requester.getObjectId()+":"+npc.getNpcId()+":"+npc.getObjectId());
 }
 /** The requesting bot's own native turn-in. True when it was paid 50, which replaces its ordinary 25. */
 static boolean completed(Player bot,int quest) {
  var binding=bound(bot);
  if(binding==null || binding.requester!=bot || binding.quest!=quest)return false;
  BY_TEAM.remove(bot.getCurrentTeam().getTeamId(),binding);
  hide(binding,binding.title+" is complete.");
  PlayerBotFindGroup.helpCompleted(binding.requester);
  String claim="q"+bot.getObjectId()+":"+quest+":"+binding.round;
  boolean paid=false;
  for(var member:PlayerBotTeams.members(bot))
   if(member.isPlayerBot() && binding.participants.contains(member.getObjectId()))
    paid|=PlayerBotGrowth.learnHelp(member,claim) && member==bot;
  return paid;
 }
 private PlayerBotHelpObjectives() {}
}
