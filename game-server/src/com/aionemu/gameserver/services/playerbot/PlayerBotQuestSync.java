/* Quest eligibility, leader notification and inventory-use ordering adapted from
 * mod-playerbots AcceptQuestAction.cpp, QueryQuestAction.cpp and ItemUsageValue.cpp,
 * pinned revision 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Attribution: third-party/playerbots/AUTHORS.md. Catch-up consent is an Aion policy. */
package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;

public final class PlayerBotQuestSync {
 record Prompt(int quest, List<Integer> prerequisites, String reason) {}
 static final class State {
  final PlayerBotSession session;
  final int account, character;
  final Set<Integer> skipped = new HashSet<>(), approved = new HashSet<>(), managed = new HashSet<>(), protectedItems = new HashSet<>();
  final Map<Integer,Prompt> prompts = new LinkedHashMap<>();
  final Map<String,Long> announcements = new HashMap<>();
  final Map<Integer,Integer> ownerCompletions = new HashMap<>();
  final Deque<String> messages = new ArrayDeque<>();
  long nextScan, returnUntil;
  boolean enchant, salvage, partySync = true;
  long reserve = 10000, dailyBudget = 50000, spent;
  String spentDay = "";
  State(PlayerBotSession session) {
   this.session=session;
   account=session.owner().getAccount().getId();character=session.bot().getObjectId();
   session.bot().getInventory().getItems().forEach(i->protectedItems.add(i.getObjectId()));
   session.bot().getEquipment().getEquippedItems().forEach(i->protectedItems.add(i.getObjectId()));
   for(var q:session.owner().getQuestStateList().getAllQuestState())ownerCompletions.put(q.getQuestId(),q.getCompleteCount());
   for(var q:session.bot().getQuestStateList().getUncompletedQuests()) {
    var owned=session.owner().getQuestStateList().getQuestState(q.getQuestId());
    if(owned!=null && (owned.getStatus()==QuestStatus.START || owned.getStatus()==QuestStatus.REWARD))managed.add(q.getQuestId());
   }
   try {
    Properties p=PlayerBotMetadata.load(account,character,"care",path());
    if(!p.isEmpty()) {
    if(Integer.parseInt(p.getProperty("account"))!=account || Integer.parseInt(p.getProperty("character"))!=character)throw new IOException("Companion care settings owner mismatch");
    enchant=Boolean.parseBoolean(p.getProperty("enchant","false"));salvage=Boolean.parseBoolean(p.getProperty("salvage","false"));
    partySync=Boolean.parseBoolean(p.getProperty("partySync","true"));
    reserve=Math.max(10000,Long.parseLong(p.getProperty("reserve","10000")));dailyBudget=Math.max(0,Long.parseLong(p.getProperty("dailyBudget","50000")));
    spent=Math.max(0,Long.parseLong(p.getProperty("spent","0")));spentDay=p.getProperty("spentDay","");
    for(String id:p.getProperty("skipped","").split(","))if(!id.isBlank())skipped.add(Integer.parseInt(id));
    for(String id:p.getProperty("approved","").split(","))if(!id.isBlank())approved.add(Integer.parseInt(id));
    for(String key:p.stringPropertyNames())if(key.startsWith("together.")){int id=Integer.parseInt(key.substring(9));managed.add(id);ownerCompletions.put(id,Integer.parseInt(p.getProperty(key)));}
    }
   }catch(Exception error){throw new IllegalStateException("Cannot load companion care settings",error);}
  }
  Path path(){return Path.of("config","playerbots","care-character-"+character+".properties");}
  void save() {
   Properties p=new Properties();p.setProperty("account",Integer.toString(account));p.setProperty("character",Integer.toString(character));
   p.setProperty("enchant",Boolean.toString(enchant));p.setProperty("salvage",Boolean.toString(salvage));p.setProperty("reserve",Long.toString(reserve));
   p.setProperty("partySync",Boolean.toString(partySync));
   p.setProperty("dailyBudget",Long.toString(dailyBudget));p.setProperty("spent",Long.toString(spent));p.setProperty("spentDay",spentDay);
   p.setProperty("skipped",skipped.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   p.setProperty("approved",approved.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   for(int id:managed)p.setProperty("together."+id,Integer.toString(ownerCompletions.getOrDefault(id,0)));
   try {PlayerBotMetadata.save(account,character,"care",p);}
   catch(IOException error){throw new IllegalStateException("Cannot queue companion care metadata",error);}
  }
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 static State state(PlayerBotSession session){return STATES.computeIfAbsent(session.bot().getObjectId(),id->new State(session));}
 static void close(PlayerBotSession session){STATES.remove(session.bot().getObjectId());PlayerBotCare.close(session);PlayerBotTravel.close(session);PlayerBotGearPolicy.close(session);PlayerBotPartyBehavior.close(session);}
 static boolean skipped(Player bot,int quest){var s=STATES.get(bot.getObjectId());return s!=null && s.skipped.contains(quest);}
 static boolean returning(PlayerBotSession session) {
  var s=state(session);
  if(s.returnUntil!=0 && (s.returnUntil<System.currentTimeMillis() || PositionUtil.isInRange(session.owner(),session.bot(),6)))s.returnUntil=0;
  return s.returnUntil!=0;
 }
 static void returnToOwner(Player bot){var s=STATES.get(bot.getObjectId());if(s!=null)s.returnUntil=System.currentTimeMillis()+60000;}
 static void notice(Player bot,String text,String key,long interval) {
  var s=STATES.get(bot.getObjectId());if(s==null)return;
  long now=System.currentTimeMillis();if(s.announcements.getOrDefault(key,0L)>now)return;s.announcements.put(key,now+interval);
  if(s.session.owner().isOnline())PacketSendUtility.sendMessage(s.session.owner(),bot.getName()+": "+text);
  String readable=java.util.regex.Pattern.compile("\\[(quest|item):([0-9]+)\\]").matcher(text).replaceAll(match->{
   int id=Integer.parseInt(match.group(2));String name;
   if(match.group(1).equals("quest"))name=PlayerBotQuestJournal.name(id);
   else {var item=DataManager.ITEM_DATA.getItemTemplate(id);name=item==null ? "Item "+id : item.getName();}
   return java.util.regex.Matcher.quoteReplacement(name);
  });
  s.messages.addLast(readable);while(s.messages.size()>8)s.messages.removeFirst();
 }
 static void accepted(Player owner,Player bot,int quest,boolean turnIn) {
  var s=STATES.get(bot.getObjectId());if(s!=null && !turnIn){s.managed.add(quest);s.prompts.remove(quest);var owned=owner.getQuestStateList().getQuestState(quest);s.ownerCompletions.put(quest,owned==null ? 0 : owned.getCompleteCount());s.save();}
  notice(bot,(turnIn ? "I completed " : "I accepted ")+ChatUtil.quest(quest)+". Returning to you.","quest:"+quest+":"+turnIn,1000);
  returnToOwner(bot);
 }
 static boolean wanted(Player owner,Player bot,int quest) {
  var s=STATES.get(bot.getObjectId());if(s!=null && s.skipped.contains(quest))return false;
  var owned=owner.getQuestStateList().getQuestState(quest);
  if(owned==null || owned.getStatus()!=QuestStatus.COMPLETE)return true;
  var completed=bot.getQuestStateList().getQuestState(quest);
  if(completed!=null && completed.getStatus()==QuestStatus.COMPLETE)return false;
  return s!=null && (s.approved.contains(quest) || s.managed.contains(quest) && s.ownerCompletions.getOrDefault(quest,Integer.MAX_VALUE)<owned.getCompleteCount()
   || s.approved.stream().anyMatch(root->chain(bot,root).contains(quest)));
 }
 static List<Integer> missing(Player bot,int quest) {
  List<Integer> missing=new ArrayList<>();QuestTemplate template=DataManager.QUEST_DATA.getQuestById(quest);
  if(template==null)return missing;
  // Native finished-condition groups are alternatives: choose the shortest
  // unfinished branch, and never demand all alternative quest chains.
  List<Integer> best=null;
  for(var condition:template.getXMLStartConditions())if(condition.getFinishedPreconditions()!=null) {
   List<Integer> branch=new ArrayList<>();
   for(var prerequisite:condition.getFinishedPreconditions()) {
    var qs=bot.getQuestStateList().getQuestState(prerequisite.getQuestId());
    if(qs==null || qs.getStatus()!=QuestStatus.COMPLETE || prerequisite.getReward()>=0 && !Objects.equals(qs.getRewardGroup(),prerequisite.getReward()))branch.add(prerequisite.getQuestId());
   }
   if(branch.isEmpty())return List.of();
   if(best==null || branch.size()<best.size())best=branch;
  }
  if(best!=null)missing.addAll(best);
  return List.copyOf(missing);
 }
 static List<Integer> chain(Player bot,int quest) {
  Set<Integer> result=new LinkedHashSet<>();Deque<Integer> pending=new ArrayDeque<>(missing(bot,quest));
  while(!pending.isEmpty() && result.size()<32){int id=pending.removeFirst();if(id==quest || !result.add(id))continue;pending.addAll(missing(bot,id));}
  return List.copyOf(result);
 }
 static void tick(PlayerBotSession session,boolean enabled) {
  State s=state(session);Player owner=session.owner(),bot=session.bot();long now=System.currentTimeMillis();
  if(!enabled || now<s.nextScan || owner.isDead() || bot.isDead())return;s.nextScan=now+3000;
  s.prompts.entrySet().removeIf(e->{var q=bot.getQuestStateList().getQuestState(e.getKey());return q!=null && q.getStatus()==QuestStatus.COMPLETE;});
  for(var owned:owner.getQuestStateList().getUncompletedQuests()) {
   int id=owned.getQuestId();if(s.skipped.contains(id))continue;
   var current=bot.getQuestStateList().getQuestState(id);if(current!=null && current.getStatus()!=QuestStatus.LOCKED && !current.isStartable())continue;
   var template=DataManager.QUEST_DATA.getQuestById(id);if(template==null)continue;
   if(!QuestService.checkStartConditions(bot,id,false)) {
    List<Integer> prerequisites=chain(bot,id);
    if(!prerequisites.isEmpty())prompt(session,new Prompt(id,prerequisites,"I have not completed the prerequisite quests. Will you help me catch up?"));
    else notice(bot,"I cannot accept "+ChatUtil.quest(id)+" yet: my level, class or another native quest requirement is not met.","blocked:"+id,120000);
    continue;
   }
   if(!bot.getController().isInCombat() && !owner.getController().isInCombat() && !template.isCannotShare())PlayerBotService.getInstance().acceptSharedQuest(owner,bot,id);
  }
  for(var current:bot.getQuestStateList().getUncompletedQuests()) {
   var owned=owner.getQuestStateList().getQuestState(current.getQuestId());
   if(owned!=null && PlayerBotQuestMirror.shouldComplete(s.partySync,s.managed.contains(current.getQuestId()),s.skipped.contains(current.getQuestId()),
    s.ownerCompletions.getOrDefault(current.getQuestId(),owned.getCompleteCount()),owned.getCompleteCount(),owned.getStatus(),current.getStatus())) {
    if(!PlayerBotQuestMirror.ready(bot,current))notice(bot,"I cannot synchronize this quest's custom completion yet: "+ChatUtil.quest(current.getQuestId())+". Its native handler needs guidance.","mirror-blocked:"+current.getQuestId(),120000);
   }
   if(owned!=null && owned.getStatus()==QuestStatus.COMPLETE && !s.skipped.contains(current.getQuestId()) && !s.approved.contains(current.getQuestId()))
    if(!s.managed.contains(current.getQuestId()) || s.ownerCompletions.getOrDefault(current.getQuestId(),Integer.MAX_VALUE)>=owned.getCompleteCount())
     prompt(session,new Prompt(current.getQuestId(),List.of(),"You already completed this quest, but I have not. Will you help me finish it?"));
  }
 }
 private static void prompt(PlayerBotSession session,Prompt prompt) {
  State s=state(session);if(s.approved.contains(prompt.quest()) || s.prompts.containsKey(prompt.quest()))return;
  s.prompts.put(prompt.quest(),prompt);
  notice(session.bot(),prompt.reason()+" "+ChatUtil.quest(prompt.quest())+" Reply in Companions with Yes/No, or .bot answer "+session.bot().getName()+" "+prompt.quest()+" yes|no.","prompt:"+prompt.quest(),Long.MAX_VALUE-System.currentTimeMillis());
 }
 static void nearby(Player owner,Player bot,int quest) {
  var s=STATES.get(bot.getObjectId());if(s==null || s.skipped.contains(quest) || s.approved.contains(quest))return;
  var owned=owner.getQuestStateList().getQuestState(quest);var current=bot.getQuestStateList().getQuestState(quest);
  if(owned==null || owned.getStatus()!=QuestStatus.COMPLETE || current!=null && current.getStatus()!=QuestStatus.LOCKED)return;
  if(!QuestService.checkStartConditions(bot,quest,false) && chain(bot,quest).isEmpty())return;
  prompt(s.session,new Prompt(quest,chain(bot,quest),"This nearby quest is behind your progress. Will you help me catch up?"));
 }
 public static void answer(PlayerBotSession session,int quest,boolean help) {
  synchronized(session) {
   if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");
   State s=state(session);Prompt prompt=s.prompts.get(quest);if(prompt==null)throw new IllegalArgumentException("That catch-up question is no longer pending. Refresh Companions.");
   if(help){s.approved.add(quest);s.skipped.remove(quest);s.prompts.remove(quest);s.save();notice(session.bot(),"Thank you. I will work on "+ChatUtil.quest(quest)+" and its prerequisites near you.","answer:"+quest,0);}
   else {
    // Only the selected quest and prerequisites started by this companion AI
    // are abandoned. Native un-abandonable campaign rules stay authoritative.
    boolean retained=false;Set<Integer> cancel=new HashSet<>(prompt.prerequisites());cancel.add(quest);
    for(int id:cancel)if(id==quest || s.managed.contains(id)) {
     if(id!=quest && s.approved.stream().filter(root->root!=quest).anyMatch(root->chain(session.bot(),root).contains(id)))continue;
     var q=session.bot().getQuestStateList().getQuestState(id);
     if(q!=null && (q.getStatus()==QuestStatus.START || q.getStatus()==QuestStatus.REWARD)) {
      if(!QuestService.abandonQuest(session.bot(),id))retained=true;
      else if(id!=quest)s.skipped.add(id);
     }
    }
    s.skipped.add(quest);s.approved.remove(quest);s.prompts.remove(quest);s.save();
    if(Objects.equals(session.snapshot().get("mission"),quest))session.mission(0);
    notice(session.bot(),retained ? "I stopped pursuing that quest. The game does not allow abandoning part of this quest chain." : "I canceled that catch-up request and any associated quests the game allows me to abandon.","answer:"+quest,0);
   }
  }
 }
 public static void setCare(PlayerBotSession session,String key,boolean enabled) {
  if(!session.generated() && enabled && !key.equals("partySync"))throw new IllegalArgumentException("Automatic item care applies only to Temporary Bots; your alt's items are preserved.");
  synchronized(session){State s=state(session);if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");
   boolean before=switch(key){case "enchant"->s.enchant;case "salvage"->s.salvage;case "partySync"->s.partySync;default->throw new IllegalArgumentException("Unknown companion setting");};
   switch(key){case "enchant"->s.enchant=enabled;case "salvage"->s.salvage=enabled;case "partySync"->s.partySync=enabled;}
   try{s.save();}catch(RuntimeException error){switch(key){case "enchant"->s.enchant=before;case "salvage"->s.salvage=before;case "partySync"->s.partySync=before;}throw error;}
  }
 }
 public static void setBudget(PlayerBotSession session,long reserve,long dailyBudget) {
  if(reserve<10000 || reserve>1000000000000L || dailyBudget<0 || dailyBudget>1000000000000L)throw new IllegalArgumentException("Use a reserve of at least 10,000 Kinah and a nonnegative daily budget.");
  synchronized(session){if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");State s=state(session);long oldReserve=s.reserve,oldBudget=s.dailyBudget;
   s.reserve=reserve;s.dailyBudget=dailyBudget;try{s.save();}catch(RuntimeException error){s.reserve=oldReserve;s.dailyBudget=oldBudget;throw error;}}
 }
 static Map<String,Object> snapshot(PlayerBotSession session) {
  State s=state(session);
  Map<String,Object> result=new LinkedHashMap<>(Map.of("enchant",s.enchant,"salvage",s.salvage,"partySync",s.partySync,"reserve",s.reserve,"dailyBudget",s.dailyBudget,"spent",s.spent,
   "questions",s.prompts.values().stream().map(p->Map.of("quest",p.quest(),"name",PlayerBotQuestJournal.name(p.quest()),"prerequisites",p.prerequisites().stream().map(id->Map.of("id",id,"name",PlayerBotQuestJournal.name(id))).toList(),"reason",p.reason())).toList(),"announcements",List.copyOf(s.messages)));
  result.putAll(PlayerBotPartyBehavior.snapshot(session));return result;
 }
 private PlayerBotQuestSync() {}
}
