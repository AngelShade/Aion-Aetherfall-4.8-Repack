package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.dao.PlayerBotHelpDAO;
import com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION.ActionType;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native accepted contracts. Only the requesting bot's committed checkpoint publishes
 * counters/completion. The reusable journal wrapper never completes or pays quest rewards. */
public final class PlayerBotHelpObjectives {
 public static final int HELP_QUEST=14996;
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PlayerBotHelpObjectives.class);
 public record Counter(String key,String name,String kind,int variable,int step,long current,long required) {
  Counter at(long n){return new Counter(key,name,kind,variable,step,n,required);}
 }
 /** Versioned projection in native SQL, never a separate quest engine. */
 public static final class Contract {
  public int version=1,account,helper,bot,quest,npc,round,minLevel,maxLevel,map,stage,defeated;
  public float x,y,z;
  public String botName="",rarity="",need="",status="ACTIVE";
  public boolean helperParticipated;
  public List<Counter> objectives=new ArrayList<>();
  public Set<Integer> participants=new TreeSet<>();
  public Contract() {}
 }
 static final class Binding {
  volatile Player requester,helper;
  final int account,quest,npc,round;
  final String title;
  final Set<Integer> participants=ConcurrentHashMap.newKeySet();
  volatile int defeated;
  volatile boolean shown,finished,helperParticipated;
  volatile Contract committed;
  volatile Row row;
  Binding(Player requester,Player helper,int quest,int npc,int round,String title) {
   this.requester=requester;this.helper=helper;account=helper.getAccount().getId();this.quest=quest;this.npc=npc;this.round=round;this.title=title;
  }
  Binding(Row row) {
   this.row=row;committed=Json.read(row.payload(),Contract.class);
   if(committed.version!=1 || committed.account!=row.account() || committed.bot!=row.bot() || committed.helper!=row.helper())throw new IllegalStateException("Unsupported help contract");
   committed.status=row.status();account=row.account();quest=committed.quest;npc=committed.npc;round=committed.round;
   title="Help "+committed.botName+": "+shownNeed(committed);
   participants.addAll(committed.participants);defeated=committed.defeated;helperParticipated=committed.helperParticipated;finished=!row.status().equals("ACTIVE");
  }
  boolean active(){return (row==null || row.status().equals("ACTIVE")) && requester!=null && helper!=null && PlayerBotTeams.same(requester,helper);}
  String request(){return "h"+account+":"+(committed==null?requester.getObjectId():committed.bot)+":"+quest+":"+npc;}
 }
 private static final Map<Integer,Binding> BY_ACCOUNT=new ConcurrentHashMap<>(),BY_REQUESTER=new ConcurrentHashMap<>();
  private static final Map<Integer,Optional<Binding>> RESTORED=new ConcurrentHashMap<>();
 private static volatile boolean prepared;
 public static synchronized void initialize() {
  if(prepared)return;
  try(var c=DatabaseFactory.getConnection()){PlayerBotHelpDAO.prepare(c);prepared=true;}
  catch(SQLException e){throw new IllegalStateException("Cannot prepare native help contracts",e);}
 }
 private static Binding remember(Row row) {
  if(row==null)return null;
  var old=BY_ACCOUNT.get(row.account());
  if(old!=null && old.row!=null && old.row.key().equals(row.key()) && old.row.revision()>=row.revision())return old;
  var b=new Binding(row);
   RESTORED.put(row.account(),Optional.of(b));
  if(!Set.of("CLAIMED","ABANDONED").contains(row.status())){BY_ACCOUNT.put(row.account(),b);BY_REQUESTER.put(row.bot(),b);}
  return b;
 }
 private static Binding current(Player human) {
   if(human.isPlayerBot())return null;
   int account=human.getAccount().getId();
   var b=BY_ACCOUNT.get(account);
   if(b==null) {
    var restored=RESTORED.get(account);
    if(restored==null) {
     initialize();
     try(var c=DatabaseFactory.getConnection()) {
      b=remember(PlayerBotHelpDAO.latest(c,account));
      // Cache absent and terminal requests too: journal/title polling must not
      // query SQL again for accounts without an active contract.
      RESTORED.putIfAbsent(account,Optional.ofNullable(b));
     }catch(SQLException e){throw new IllegalStateException("Cannot restore help request",e);}
     restored=RESTORED.get(account);
    }
    b=restored.orElse(null);
   }
  if(b!=null && b.committed.helper==human.getObjectId())b.helper=human;
  return b;
 }
 /** One indexed restore when the bot's real native session loads. */
 static void loaded(Player bot) {
  initialize();try(var c=DatabaseFactory.getConnection()){var b=remember(PlayerBotHelpDAO.requester(c,bot.getObjectId()));if(b!=null)b.requester=bot;}
  catch(SQLException e){throw new IllegalStateException("Cannot load bot help contract",e);}
 }
 private static boolean helping(Player human) {var q=human.getQuestStateList().getQuestState(HELP_QUEST);return q!=null && q.getStatus()==QuestStatus.START;}
 private static void show(Binding b) {
  var human=b.helper;if(human==null || !human.isOnline() || b.committed!=null && human.getObjectId()!=b.committed.helper)return;
  if(DataManager.QUEST_DATA==null || DataManager.QUEST_DATA.getQuestById(HELP_QUEST)==null)return;
  b.shown=helping(human) || QuestService.startQuest(new QuestEnv(null,human,HELP_QUEST),QuestStatus.START,false);
  // Keep native stage variable zero for the single-step wrapper; higher bits
  // identify a changed committed projection, never substitute gameplay progress.
  if(b.shown && b.row!=null){var q=human.getQuestStateList().getQuestState(HELP_QUEST);int revision=((int)(b.row.revision()%0x7fffffe)+1)<<4;if(q.getQuestVars().getQuestVars()!=revision){q.setQuestVar(revision);PacketSendUtility.sendPacket(human,new SM_QUEST_ACTION(ActionType.UPDATE,q));}}
 }
 private static void hide(Binding b){if(b.helper!=null && b.helper.isOnline() && helping(b.helper))QuestService.abandonQuest(b.helper,HELP_QUEST);b.shown=false;}
 public static String title(Player human) {
  if(human.isPlayerBot())return "";
  var b=current(human);if(b==null || b.committed.helper!=human.getObjectId() || Set.of("ABANDONED","CLAIMED").contains(b.row.status()))return "";
  return b.title.length()>96?b.title.substring(0,96):b.title;
 }
 public static String progress(Player human) {
  if(human.isPlayerBot())return "";
  var b=current(human);if(b==null || b.committed.helper!=human.getObjectId() || Set.of("ABANDONED","CLAIMED").contains(b.row.status()))return "";
  var text=new StringBuilder(b.title);
  for(var n:b.committed.objectives)text.append(" | ").append(n.name()).append(' ').append(n.current()).append(n.required()<0?"":"/"+n.required());
  text.append(" | ").append(b.row.status().equals("ACTIVE")?"In progress":b.row.status().equals("COMPLETE")?"Reward ready":b.row.status().toLowerCase(Locale.ROOT));
  return text.length()>1500?text.substring(0,1500):text.toString();
 }
 public static void entered(Player human) {
   if(human.isPlayerBot())return;
  var b=current(human);
  if(b!=null && b.committed.helper==human.getObjectId() && !Set.of("ABANDONED","CLAIMED").contains(b.row.status()))show(b);
  else if(helping(human))QuestService.abandonQuest(human,HELP_QUEST);
 }
 private static Contract terms(Player bot,Player human,PlayerBotGroupNeeds.Need need) {
  Contract c=new Contract();c.account=human.getAccount().getId();c.helper=human.getObjectId();c.bot=bot.getObjectId();c.botName=bot.getName();c.quest=need.quest();c.npc=need.npc();c.need=need.title();c.minLevel=need.minLevel();c.maxLevel=need.maxLevel();c.map=need.map();c.x=bot.getX();c.y=bot.getY();c.z=bot.getZ();
  var rarity=PlayerBotGrowth.rarity(bot);c.rarity=rarity==null?"Unassigned":rarity.name();
  var destination=PlayerBotWorldTravel.places(c.npc).stream().filter(p->p.map()==c.map).min(Comparator.comparingDouble(p->PlayerBotWorldTravel.distance(bot,p))).orElse(null);
  if(destination!=null){c.x=destination.x();c.y=destination.y();c.z=destination.z();}
  if(c.quest>0) {
   var q=bot.getQuestStateList().getQuestState(c.quest);if(q==null || q.getStatus()!=QuestStatus.START)throw new IllegalArgumentException("This native quest is no longer requesting help.");c.round=q.getCompleteCount();
   var t=DataManager.QUEST_DATA==null?null:DataManager.QUEST_DATA.getQuestById(c.quest);var model=DataManager.XML_QUESTS==null?null:DataManager.XML_QUESTS.getQuest(c.quest);
   if(t!=null && model!=null && model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData.class)for(var k:t.getQuestKill()) {
    int variable=k.getSequenceNumber()>0?k.getSequenceNumber():k.getVar();String name=k.getNpcIds().stream().map(DataManager.NPC_DATA::getNpcTemplate).filter(Objects::nonNull).map(n->n.getName()).distinct().collect(java.util.stream.Collectors.joining(" / "));
    c.objectives.add(new Counter("kill:"+variable+":"+k.getQuestStep(),name.isBlank()?"Native hunt":name,t.isDataDriven()?"STEP_KILL":"KILL",variable,k.getQuestStep(),0,k.getKillCount()));
   }
   if(t!=null && t.getCollectItems()!=null)for(var item:t.getCollectItems().getCollectItem()){var it=DataManager.ITEM_DATA.getItemTemplate(item.getItemId());c.objectives.add(new Counter("item:"+item.getItemId(),it==null?"Quest item "+item.getItemId():it.getName(),"ITEM",item.getItemId(),0,0,item.getCount().longValue()));}
   c.objectives.add(new Counter("stage","Native stage","STAGE",0,0,0,-1));c.objectives.add(new Counter("turnin","Bot turn-in","TURNIN",0,0,0,1));
  }else c.objectives.add(new Counter("target","Defeat "+need.title(),"TARGET",0,0,0,1));
  return project(bot,c,false);
 }
 /** Definitions stay frozen; counters are captured with the native inventory/quest checkpoint. */
 private static Contract project(Player bot,Contract base,boolean turnedIn) {
  Contract c=Json.read(Json.write(base),Contract.class);var q=c.quest>0?bot.getQuestStateList().getQuestState(c.quest):null;if(q!=null)c.stage=q.getQuestVarById(0);
  boolean ready=q!=null && (q.getStatus()==QuestStatus.REWARD || turnedIn);List<Counter> counts=new ArrayList<>();
  for(var n:c.objectives) {
   long count=switch(n.kind()) {
    case "TARGET"->c.defeated==0?0:1;
    case "TURNIN"->turnedIn?1:0;
    case "STAGE"->c.stage;
    case "ITEM"->ready?n.required():Math.min(n.required(),PlayerBotQuestMirror.held(bot,n.variable()));
    case "STEP_KILL"->ready || c.stage>n.step()?n.required():c.stage<n.step()?0:q==null?n.current():PlayerBotQuestJournal.count(q,n.variable(),(int)n.required());
    case "KILL"->ready?n.required():q==null?n.current():PlayerBotQuestJournal.count(q,n.variable(),(int)n.required());
    default->n.current();
   };counts.add(n.at(count));
  }c.objectives=counts;return c;
 }
 static void accept(Player bot,Player human,PlayerBotGroupNeeds.Need need) {
  if(need.instance() || PlayerBotGroupNeeds.helping(need) || need.quest()<=0 && need.npc()<=0)return;
  initialize();var service=PlayerBotService.getInstance();var actor=service.worldSession(bot);
  if(actor==null || !service.claimTransfer(actor))throw new IllegalArgumentException("This world companion is busy. Retry when its current action finishes.");
  try {synchronized(actor) {synchronized(bot) {
   if(!service.current(actor))throw new IllegalArgumentException("This world companion changed activity. Refresh its request.");
   var existing=current(human);String key="h"+human.getAccount().getId()+":"+bot.getObjectId()+":"+need.quest()+":"+need.npc();
   if(existing!=null && existing.row.key().equals(key) && !Set.of("ABANDONED","CLAIMED").contains(existing.row.status())){existing.helper=human;existing.requester=bot;show(existing);return;}
   // Initial accepted counters must be a committed native baseline as well.
   try{service.checkpointForOwnerAction(actor);}catch(SQLException e){throw new IllegalStateException("Native progress could not be confirmed; the help request was not accepted.",e);}
   var contract=terms(bot,human,need);
   try(var c=DatabaseFactory.getConnection()) {
    c.setAutoCommit(false);
    try{var row=PlayerBotHelpDAO.accept(c,new Row(key,contract.account,contract.helper,contract.bot,0,"ACTIVE",Json.write(contract)));c.commit();var b=remember(row);b.helper=human;b.requester=bot;show(b);}
    catch(SQLException|RuntimeException e){c.rollback();throw e;}
   }catch(SQLException e){throw new IllegalStateException("Cannot accept help request",e);}
  }}}finally{service.completeTransfer(actor);}
 }
 static void joined(Player bot,Player human) {
  var b=current(human);
  if(b!=null && b.committed.bot==bot.getObjectId() && b.committed.helper==human.getObjectId()){b.requester=bot;b.helper=human;show(b);}
  else PacketSendUtility.sendMessage(human,"Open Help requests in Find Group to review and accept this bot's objective.");
 }
 private static Binding bound(Player member) {
  if(member.getCurrentTeam()==null)return null;
  var direct=BY_REQUESTER.get(member.getObjectId());if(direct!=null && direct.active())return direct;
  for(var ally:PlayerBotTeams.members(member)){var b=BY_REQUESTER.get(ally.getObjectId());if(b!=null && b.active())return b;}return null;
 }
 static boolean committed(Player member){return bound(member)!=null;}
 private static boolean objective(int quest,Npc npc){return QuestEngine.getInstance().getQuestNpc(npc.getNpcId()).getOnKillEvent().contains(quest) || QuestService.getQuestDrop(npc.getNpcId()).stream().anyMatch(d->d.getQuestId()==quest);}
 static void kill(Player bot,Npc npc) {
  var b=bound(bot);if(b==null)return;
  if((npc.getNpcId()==b.npc || b.quest>0 && objective(b.quest,npc)) && b.helper.isOnline() && b.helper.getWorldMapInstance()==npc.getWorldMapInstance() && PositionUtil.isInRange(b.helper,npc,100) && PlayerBotRunRewards.participated(npc,b.helper))b.helperParticipated=true;
  if(b.quest>0){if(npc.getNpcId()==b.npc || objective(b.quest,npc))b.participants.add(bot.getObjectId());return;}
  if(npc.getNpcId()!=b.npc)return;
  synchronized(b){if(b.defeated==0){b.defeated=npc.getObjectId();b.finished=true;}else if(b.defeated!=npc.getObjectId())return;}
  PlayerBotGrowth.learnHelp(bot,"n"+b.requester.getObjectId()+":"+npc.getNpcId()+":"+npc.getObjectId());
 }
 static boolean completed(Player bot,int quest) {
  var b=BY_REQUESTER.get(bot.getObjectId());if(b==null || b.quest!=quest || b.finished || !b.active())return false;
  var state=bot.getQuestStateList().getQuestState(quest);
  if(state==null || state.getStatus()!=QuestStatus.COMPLETE || state.getCompleteCount()!=b.round+1)return false;
  b.finished=true;String claim="q"+bot.getObjectId()+":"+quest+":"+b.round;boolean paid=false;
  for(var member:PlayerBotTeams.members(bot))if(member.isPlayerBot() && b.participants.contains(member.getObjectId()))paid|=PlayerBotGrowth.learnHelp(member,claim) && member==bot;
  return paid;
 }
 record Pending(Binding binding,Row before,Contract contract) {}
 static Pending pending(Player bot) {
  var b=BY_REQUESTER.get(bot.getObjectId());if(b==null || b.row==null || !b.row.status().equals("ACTIVE"))return null;
  var q=b.quest>0?bot.getQuestStateList().getQuestState(b.quest):null;boolean turnedIn=q!=null && q.getStatus()==QuestStatus.COMPLETE;
  var c=project(bot,b.committed,turnedIn);c.participants=new TreeSet<>(b.participants);c.helperParticipated=b.helperParticipated;c.defeated=b.defeated;if(c.quest==0)c=project(bot,c,false);
  if(turnedIn && q.getCompleteCount()!=c.round+1)c.status="REVISED";
  else if(turnedIn || c.quest==0 && b.defeated!=0)c.status=b.helperParticipated && b.helper!=null && b.requester!=null && PlayerBotTeams.same(b.requester,b.helper)?"COMPLETE":"RESOLVED";
  else if(c.quest>0 && (q==null || q.getStatus()==QuestStatus.LOCKED))c.status="CANCELLED";
  else if(c.quest>0 && q.getCompleteCount()!=c.round || bot.getLevel()<c.minLevel || bot.getLevel()>c.maxLevel)c.status="REVISED";
  return new Pending(b,b.row,c);
 }
 static Row store(Connection c,Pending p)throws SQLException{return p==null?null:PlayerBotHelpDAO.store(c,p.before,p.contract.status,Json.write(p.contract));}
 static void committed(Pending p,Row saved) {
  if(p==null || saved==p.before)return;
  var b=p.binding;synchronized(b){if(b.row.revision()>saved.revision())return;b.row=saved;b.committed=Json.read(saved.payload(),Contract.class);b.committed.status=saved.status();b.finished=!saved.status().equals("ACTIVE");}
  if(Set.of("CLAIMED","ABANDONED").contains(saved.status())){BY_REQUESTER.remove(b.committed.bot,b);BY_ACCOUNT.remove(b.account,b);hide(b);return;}
  try{show(b);if(!saved.status().equals("ACTIVE") && b.requester!=null)PlayerBotFindGroup.helpCompleted(b.requester);}
  catch(RuntimeException e){log.warn("Committed help presentation awaits reopen: {}",e.toString());}
 }
 private static String close(Player human,boolean claim) {
  var b=current(human);if(b==null)throw new IllegalArgumentException("Your account has no accepted help request.");
  if(claim && PlayerBotPopulationPolicy.serverAccount(b.account))throw new IllegalArgumentException("Help rewards belong to human accounts.");
  if(claim && (!PlayerBotEconomyConfig.ECONOMY_ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotConfig.TICKETS_ENABLED || PlayerBotEconomyConfig.TICKET_HELP_REQUESTS<=0 || PlayerBotEconomyConfig.TICKET_HELP_AMOUNT<=0))throw new IllegalArgumentException("Help rewards are disabled; your completed request is retained.");
  synchronized(b) {
   try(var c=DatabaseFactory.getConnection()) {
    c.setAutoCommit(false);
    try {
     if(claim)com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.starter(c,b.account,com.aionemu.gameserver.configs.main.PlayerBotConfig.TICKET_STARTING);
     var saved=PlayerBotHelpDAO.close(c,b.account,b.row.key(),claim,PlayerBotEconomyConfig.TICKET_HELP_REQUESTS,PlayerBotEconomyConfig.TICKET_HELP_AMOUNT);c.commit();b.row=saved;
    }catch(SQLException|RuntimeException e){c.rollback();throw e;}
   }catch(SQLException e){throw new IllegalStateException("Cannot save help action",e);}
   BY_REQUESTER.remove(b.committed.bot,b);BY_ACCOUNT.remove(b.account,b);b.committed.status=b.row.status();b.finished=true;hide(b);
  }
  return claim?"Help reward collected once. Your help-contract ticket counter has been credited.":"Help request abandoned. Previous committed credit is retained; no partial reward is paid.";
 }
 static Map<String,Object> details(Player human,Player bot,PlayerBotGroupNeeds.Need need){var v=view(terms(bot,human,need),"",0,human);v.put("botLevel",bot.getLevel());v.put("botClass",bot.getPlayerClass().name());return v;}
 /** Quest needs carry their real title; hunt needs carry a template key. Saved contracts keep what they froze, so this applies when shown. */
 private static String shownNeed(Contract c){return c.quest>0?c.need:"Defeat "+PlayerBotGroupNeeds.display(c.need);}
 private static Map<String,Object> shownCounter(Counter n) {
  String name=switch(n.kind()){case "STAGE"->"Quest step";case "TURNIN"->"Companion turns in the quest";case "ITEM"->n.name();default->n.name().equals("Native hunt")?"Monsters to hunt":PlayerBotGroupNeeds.display(n.name());};
  return Map.of("name",name,"current",n.current(),"required",n.required());
 }
 private static Map<String,Object> view(Contract c,String key,long revision,Player human) {
  Map<String,Object> v=new LinkedHashMap<>();v.put("key",key);v.put("revision",revision);v.put("bot",c.bot);v.put("botName",c.botName);v.put("rarity",c.rarity);v.put("need",shownNeed(c));v.put("status",c.status);v.put("objectives",c.objectives.stream().map(PlayerBotHelpObjectives::shownCounter).toList());v.put("map",c.map);var map=DataManager.WORLD_MAPS_DATA==null?null:DataManager.WORLD_MAPS_DATA.getTemplate(c.map);v.put("mapName",map==null?"":map.getName());v.put("x",c.x);v.put("y",c.y);v.put("z",c.z);v.put("npc",c.npc);v.put("quest",c.quest);v.put("helper",c.helper);v.put("myCharacter",c.helper==human.getObjectId());v.put("participated",c.helperParticipated);v.put("minLevel",c.minLevel);v.put("maxLevel",c.maxLevel);v.put("level",human.getLevel());v.put("eligible",PlayerBotGroupNeeds.level(human.getLevel(),c.minLevel,c.maxLevel));
  v.put("reward","Companions who take part earn 50 Learning XP. Complete and collect "+PlayerBotEconomyConfig.TICKET_HELP_REQUESTS+" different requests for "+PlayerBotEconomyConfig.TICKET_HELP_AMOUNT+" Recruitment Ticket(s). You must help near the objective, and a quest request finishes when the companion turns in its quest.");return v;
 }
 public static Map<String,Object> snapshot(Player human){var b=current(human);return b==null?Map.of():view(b.committed,b.row.key(),b.row.revision(),human);}
 public static void abandonFromJournal(Player human) {
  if(current(human)==null){QuestService.abandonQuest(human,HELP_QUEST);return;}
  close(human,false);
 }
 public static String action(Player human,String operation) {
  if(operation.equals("abandon"))return close(human,false);
  if(operation.equals("claim"))return close(human,true);
  if(operation.equals("track")){var b=current(human);if(b==null || b.committed.helper!=human.getObjectId() || Set.of("CLAIMED","ABANDONED").contains(b.row.status()))throw new IllegalArgumentException("Track the active request on the character that accepted it.");show(b);return "Help request restored to your native journal and tracker.";}
  throw new IllegalArgumentException("Choose a help-request control.");
 }
 private PlayerBotHelpObjectives() {}
}
