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
 /** One native wrapper quest per help slot, so several accepted requests show side by side in the journal and tracker. */
 public static final int[] HELP_QUESTS={14996,14998,14999};
 /** How many requests one account may help at once (social.findgroup.help_max_active, at most one per wrapper quest). */
 public static int maxActive(){return Math.max(1,Math.min(HELP_QUESTS.length,com.aionemu.gameserver.configs.main.PlayerBotConfig.HELP_MAX_ACTIVE));}
 public static boolean helpQuest(int quest){return slotOf(quest)>=0;}
 static int slotOf(int quest){for(int i=0;i<HELP_QUESTS.length;i++)if(HELP_QUESTS[i]==quest)return i;return -1;}
 /** The lowest slot not in use, or -1 when every allowed slot is taken. */
 static int freeSlot(Set<Integer> used,int max){for(int i=0;i<Math.min(max,HELP_QUESTS.length);i++)if(!used.contains(i))return i;return -1;}
 private static int questOf(Binding b){return HELP_QUESTS[Math.max(0,Math.min(HELP_QUESTS.length-1,b.slot))];}
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
  /** Which wrapper quest shows it (0 = 14996); requests saved before slots read back as 0. */
  public int slot;
  public List<Counter> objectives=new ArrayList<>();
  public Set<Integer> participants=new TreeSet<>();
  public Contract() {}
 }
 static final class Binding {
  volatile Player requester,helper;
  final int account,quest,npc,round,slot;
  final String title;
  final Set<Integer> participants=ConcurrentHashMap.newKeySet();
  volatile int defeated;
  volatile boolean shown,finished,helperParticipated;
  /** The helper was in the requester's party at the native hand-in itself (set before anything can break the party up). */
  volatile boolean handedInTogether;
  /** Finished in place 10 s after the last objective (the bot hands the quest in later, on its own). */
  volatile boolean remoteDone;
  /** The helper was in the requester's party when the last objective filled. */
  volatile boolean doneTogether;
  volatile Contract committed;
  volatile Row row;
  Binding(Player requester,Player helper,int quest,int npc,int round,String title) {
   this.requester=requester;this.helper=helper;account=helper.getAccount().getId();this.quest=quest;this.npc=npc;this.round=round;this.title=title;slot=0;
  }
  Binding(Row row) {
   this.row=row;committed=Json.read(row.payload(),Contract.class);
   if(committed.version!=1 || committed.account!=row.account() || committed.bot!=row.bot() || committed.helper!=row.helper())throw new IllegalStateException("Unsupported help contract");
   committed.status=row.status();account=row.account();quest=committed.quest;npc=committed.npc;round=committed.round;slot=Math.max(0,committed.slot);
   title="Help "+committed.botName+": "+shownNeed(committed);
   participants.addAll(committed.participants);defeated=committed.defeated;helperParticipated=committed.helperParticipated;finished=!row.status().equals("ACTIVE");
  }
  boolean active(){return (row==null || row.status().equals("ACTIVE")) && requester!=null && helper!=null && PlayerBotTeams.same(requester,helper);}
  String request(){return "h"+account+":"+(committed==null?requester.getObjectId():committed.bot)+":"+quest+":"+npc;}
 }
 /** account -> slot -> active request; requesting bot -> request; the latest finished request per account (web window). */
 private static final Map<Integer,Map<Integer,Binding>> BY_ACCOUNT=new ConcurrentHashMap<>();
 private static final Map<Integer,Binding> BY_REQUESTER=new ConcurrentHashMap<>(),LAST=new ConcurrentHashMap<>();
 /** Accounts already restored from SQL (absent ones too: title polling must not query again). */
 private static final Set<Integer> RESTORED=ConcurrentHashMap.newKeySet();
 private static void activate(Binding b){BY_ACCOUNT.computeIfAbsent(b.account,a->new java.util.concurrent.ConcurrentSkipListMap<>()).put(b.slot,b);BY_REQUESTER.put(b.committed.bot,b);}
 private static void retire(Binding b){var m=BY_ACCOUNT.get(b.account);if(m!=null)m.remove(b.slot,b);BY_REQUESTER.remove(b.committed.bot,b);LAST.put(b.account,b);}
 private static volatile boolean prepared;
 public static synchronized void initialize() {
  if(prepared)return;
  try(var c=DatabaseFactory.getConnection()){PlayerBotHelpDAO.prepare(c);prepared=true;}
  catch(SQLException e){throw new IllegalStateException("Cannot prepare native help contracts",e);}
 }
 private static Binding remember(Row row) {
  if(row==null)return null;
  var known=BY_REQUESTER.get(row.bot());
  if(known!=null && known.row!=null && known.row.key().equals(row.key()) && known.row.revision()>=row.revision())return known;
  var b=new Binding(row);
  var slots=BY_ACCOUNT.get(row.account());var old=slots==null?null:slots.get(b.slot);
  if(old!=null && old.row!=null && old.row.key().equals(row.key()) && old.row.revision()>=row.revision())return old;
  if(!Set.of("CLAIMED","ABANDONED").contains(row.status()))activate(b);else LAST.put(row.account(),b);
  return b;
 }
 /** Every active request of the account from SQL, once per account (and the latest finished one when none is active). */
 private static void restore(int account) {
  if(RESTORED.contains(account))return;
  boolean inRam=!BY_ACCOUNT.getOrDefault(account,Map.of()).isEmpty();
  try {
   initialize();
   try(var c=DatabaseFactory.getConnection()) {
    for(var row:PlayerBotHelpDAO.active(c,account))remember(row);
    if(BY_ACCOUNT.getOrDefault(account,Map.of()).isEmpty())remember(PlayerBotHelpDAO.latest(c,account));
   }
  }catch(SQLException|RuntimeException e) {
   // A request already in RAM (its bot's session loaded first) keeps working; otherwise this is a real failure.
   if(!inRam)throw new IllegalStateException("Cannot restore help requests",e);
   log.warn("Help requests for account {} kept from RAM: {}",account,e.toString());
  }
  RESTORED.add(account);
 }
 /** The account's active requests in slot order, the helper attached. */
 private static List<Binding> bindings(Player human) {
  if(human.isPlayerBot())return List.of();
  int account=human.getAccount().getId();
  // SQL only for an account with nothing in RAM, or once after a bot's session brought one request in first.
  if(BY_ACCOUNT.getOrDefault(account,Map.of()).isEmpty() || !RESTORED.contains(account) && prepared)restore(account);
  var slots=BY_ACCOUNT.get(account);if(slots==null)return List.of();
  var list=new ArrayList<Binding>();
  for(var b:slots.values()){if(b.committed.helper==human.getObjectId())b.helper=human;list.add(b);}
  return list;
 }
 private static Binding binding(Player human,int slot){for(var b:bindings(human))if(b.slot==slot)return b;return null;}
 private static Binding byKey(Player human,String key){for(var b:bindings(human))if(b.row!=null && b.row.key().equals(key))return b;return null;}
 /** The first active request, else the latest finished one: what the single-request callers mean. */
 private static Binding current(Player human) {
  var list=bindings(human);if(!list.isEmpty())return list.getFirst();
  if(human.isPlayerBot())return null;
  var last=LAST.get(human.getAccount().getId());if(last!=null && last.committed.helper==human.getObjectId())last.helper=human;
  return last;
 }
 /** One indexed restore when the bot's real native session loads. */
 static void loaded(Player bot) {
  initialize();try(var c=DatabaseFactory.getConnection()){var b=remember(PlayerBotHelpDAO.requester(c,bot.getObjectId()));if(b!=null)b.requester=bot;}
  catch(SQLException e){throw new IllegalStateException("Cannot load bot help contract",e);}
 }
 private static boolean helping(Player human,int quest) {var q=human.getQuestStateList().getQuestState(quest);return q!=null && q.getStatus()==QuestStatus.START;}
 private static void show(Binding b) {
  var human=b.helper;if(human==null || !human.isOnline() || b.committed!=null && human.getObjectId()!=b.committed.helper)return;
  int quest=questOf(b);
  if(DataManager.QUEST_DATA==null || DataManager.QUEST_DATA.getQuestById(quest)==null)return;
  b.shown=helping(human,quest) || QuestService.startQuest(new QuestEnv(null,human,quest),QuestStatus.START,false);
  // Keep native stage variable zero for the single-step wrapper; higher bits
  // identify a changed committed projection, never substitute gameplay progress.
  if(b.shown && b.row!=null){var q=human.getQuestStateList().getQuestState(quest);int revision=((int)(b.row.revision()%0x7fffffe)+1)<<4;if(q.getQuestVars().getQuestVars()!=revision){q.setQuestVar(revision);PacketSendUtility.sendPacket(human,new SM_QUEST_ACTION(ActionType.UPDATE,q));}}
 }
 private static void hide(Binding b){hide(b,false);}
 /** A finished request closes its journal entry as completed, with the UPDATE that QuestService.finishQuest sends; the native
  * ABANDON packet made the client report the quest failed right before "Request complete!" (owner, 9 Nov). The wrapper quests
  * repeat without limit (max_repeat_count 255), so counting the completion never blocks a later request. */
 private static void hide(Binding b,boolean completed) {
  var human=b.helper;int quest=questOf(b);
  if(human!=null && human.isOnline() && helping(human,quest)) {
   if(completed) {
    var q=human.getQuestStateList().getQuestState(quest);q.setStatus(QuestStatus.COMPLETE);q.setQuestVar(0);q.setFlags(0);
    PacketSendUtility.sendPacket(human,new SM_QUEST_ACTION(ActionType.UPDATE,q));human.getController().updateNearbyQuests();
   }
   else QuestService.abandonQuest(human,quest);
  }
  b.shown=false;
 }
 public static String title(Player human){return title(human,0);}
 public static String title(Player human,int slot) {
  if(human.isPlayerBot())return "";
  var b=binding(human,slot);if(b==null || b.committed.helper!=human.getObjectId() || Set.of("ABANDONED","CLAIMED").contains(b.row.status()))return "";
  return b.title.length()>96?b.title.substring(0,96):b.title;
 }
 public static String progress(Player human){return progress(human,0);}
 public static String progress(Player human,int slot) {
  if(human.isPlayerBot())return "";
  var b=binding(human,slot);if(b==null || b.committed.helper!=human.getObjectId() || Set.of("ABANDONED","CLAIMED").contains(b.row.status()))return "";
  var text=step(b.committed,b.row.status());
  return text.length()>1500?text.substring(0,1500):text;
 }
 /** The one native step line the tracker shows: each counter, e.g. "Alluring Temptress 3/5, Garasa hands in the quest 0/1". */
 static String step(Contract c,String status) {
  var counters=new ArrayList<String>();
  for(var n:c.objectives)if(listed(n))counters.add(goal(c,n)+" "+n.current()+(n.required()<0?"":"/"+n.required()));
  String text=counters.isEmpty()?shownNeed(c):String.join(", ",counters);
  return status.equals("COMPLETE")?text+" - reward ready":text;
 }
 /** A counter's name in the journal and tracker; the turn-in names the companion who hands it in. */
 private static String goal(Contract c,Counter n){return n.kind().equals("TURNIN")?c.botName+" hands in the quest":String.valueOf(shownCounter(n).get("name"));}
 /** What this request pays, said the same way in chat and in the journal. helps = requests finished before this one. */
 static String rewardPreview(long helps,int needed,long amount) {
  if(needed<=0 || amount<=0)return "Help rewards are switched off on this server.";
  String tickets=amount+" Recruitment Ticket"+(amount==1?"":"s");
  if(needed==1)return tickets;
  long next=helps%needed+1;
  return next==needed?tickets+" (help "+next+"/"+needed+")":"Help "+next+"/"+needed+" toward "+tickets;
 }
 /** The native client's journal card: title, tracker step, "STATUS map x y z", then the details page HTML. */
 public static String card(Player human){return card(human,0);}
 public static String card(Player human,int slot) {
  String title=title(human,slot);if(title.isEmpty())return "";
  var b=binding(human,slot);if(b==null)return "";
  var c=b.committed;String status=b.row.status();var progress=tickets(b.account);
  var map=DataManager.WORLD_MAPS_DATA==null?null:DataManager.WORLD_MAPS_DATA.getTemplate(c.map);
  String place=(map==null?"":map.getName());
  return title.replace('\n',' ')+"\n"+progress(human,slot).replace('\n',' ')+"\n"+status+" "+c.map+" "+c.x+" "+c.y+" "+c.z+"\n"
   +page(c,status,place,progress,PlayerBotEconomyConfig.TICKET_HELP_REQUESTS,PlayerBotEconomyConfig.TICKET_HELP_AMOUNT);
 }
 /** card2 (AetherfallCore 1.3.12+): the card with the tracker lines between the state line and the page. */
 public static String card2(Player human,int slot) {
  String card=card(human,slot);if(card.isEmpty())return "";
  var b=binding(human,slot);if(b==null)return "";
  int state=card.indexOf('\n',card.indexOf('\n',card.indexOf('\n')+1)+1);
  return state<0?"":card.substring(0,state+1)+tracker(b.committed,b.row.status())+card.substring(state);
 }
 /** The help row's lines under its title in the native tracker: each counter on its own line, the count in yellow. */
 static String tracker(Contract c,String status) {
  var lines=new ArrayList<String>();
  for(var n:c.objectives)if(listed(n))lines.add(escape(goal(c,n))+" <font color=\"#ffd84a\">("+n.current()+(n.required()<0?"":"/"+n.required())+")</font>");
  if(lines.isEmpty())lines.add(escape(shownNeed(c)));
  if(status.equals("COMPLETE"))lines.add("<font color=\"#8ee06a\">Reward ready</font>");
  else if(c.quest>0 && objectivesDone(c.objectives))lines.add("<font color=\"#8ee06a\">All done - completing</font>");
  return String.join("<br>",lines);
 }
 /** The details page, in the native quest summary's own font. No dictionary tokens: the client draws it as written. */
 static String page(Contract c,String status,String place,long[] progress,int needed,long amount) {
  var html=new StringBuilder("<html><body>");
  html.append(line("Help "+c.botName+": "+shownNeed(c)));
  html.append(line("Objectives"));
  boolean any=false;
  for(var n:c.objectives){
   if(!listed(n))continue;any=true;
   boolean done=n.required()>=0 && n.current()>=n.required();
   html.append(line("- "+goal(c,n)+" "+n.current()+(n.required()<0?"":"/"+n.required())+(done?" (done)":"")));
  }
  if(!any)html.append(line("- "+shownNeed(c)));
  html.append("<br>");
  html.append(line("Where: "+(place.isEmpty()?"this map":place)+" near ("+(int)c.x+", "+(int)c.y+"). Show location marks it on the map."));
  String state=switch(status){
   case "ACTIVE"->c.quest>0 && objectivesDone(c.objectives)?"All objectives done. Stay in the party; it completes in a few seconds."
    :c.quest>0?"In progress. It completes a few seconds after the last objective, so stay in the party until then."
    :"In progress. It finishes when the target is defeated with you nearby.";
   case "COMPLETE"->"Complete. Press Collect reward to claim it.";
   default->status.toLowerCase(Locale.ROOT)+".";
  };
  html.append(line("Status: "+state));
  html.append("<br>");
  html.append(line("Reward: "+rewardPreview(progress==null?0:progress[0],needed,amount)+(progress==null?"":" (you have "+progress[1]+")")));
  html.append(line("Companions who take part earn 50 Learning XP. Help near the objective for it to count."));
  html.append(line("Track pins this request to the quest tracker."));
  return html.append("</body></html>").toString();
 }
 private static String line(String text){return "<p><font font_xml=\"quest_summary\">"+escape(text)+"</font></p>";}
 /** Plain text for the native HTML view: markup characters and its "[%" template tokens never pass through. */
 static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("[%","[ %").replace('\n',' ').replace('\r',' ');}
 /** The journal's own buttons (Track, Collect reward): the same controls as the Help requests window, answered in chat. */
 public static String journal(Player human,String operation){return journal(human,operation,0);}
 public static String journal(Player human,String operation,int slot) {
  try {
   var b=binding(human,slot);if(b==null)throw new IllegalArgumentException("That help request is no longer active.");
   switch(operation) {
    case "track"->{action(human,"track",b.row.key());tell(human,"Tracking "+b.committed.botName+"'s request in the quest tracker.",false);return "ok";}
    case "claim"->{
     action(human,"claim",b.row.key());
     var progress=b==null?null:tickets(b.account);
     tell(human,progress==null?"Help reward collected.":rewardLine(progress[0],PlayerBotEconomyConfig.TICKET_HELP_REQUESTS,PlayerBotEconomyConfig.TICKET_HELP_AMOUNT,progress[1]),true);
     return "ok";
    }
    default->throw new IllegalArgumentException("Choose a help-request control.");
   }
  }catch(IllegalArgumentException e){tell(human,e.getMessage(),false);return e.getMessage();}
 }
 /** Login runs once: PlayerEnterWorldService and every wrapper quest's handler call this. */
 private static final Map<Integer,Long> ENTERED=new ConcurrentHashMap<>();
 static final long ENTER_ONCE_MS=10000;
 static boolean firstEntry(Long last,long now){return last==null || now-last>=ENTER_ONCE_MS;}
 public static void entered(Player human) {
  if(human.isPlayerBot())return;
  long now=System.currentTimeMillis();
  if(!firstEntry(ENTERED.put(human.getObjectId(),now),now))return;
  var shownQuests=new HashSet<Integer>();
  for(var b:bindings(human)) {
   if(b.committed.helper!=human.getObjectId() || Set.of("ABANDONED","CLAIMED").contains(b.row.status()))continue;
   shownQuests.add(questOf(b));show(b);
   if(!b.row.status().equals("ACTIVE"))settle(b);
   else if(b.quest>0 && objectivesDone(b.committed.objectives)){
    final var restored=b;
    com.aionemu.gameserver.utils.ThreadPoolManager.getInstance().schedule(()->finishInPlace(restored,true),LOGIN_FINISH_MS);
   }
  }
  // A wrapper quest with no request behind it is stale.
  for(int quest:HELP_QUESTS)if(!shownQuests.contains(quest) && helping(human,quest))QuestService.abandonQuest(human,quest);
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
   String key="h"+human.getAccount().getId()+":"+bot.getObjectId()+":"+need.quest()+":"+need.npc();
   var active=bindings(human);
   for(var existing:active)if(existing.row.key().equals(key)){existing.helper=human;existing.requester=bot;show(existing);return;}
   var used=new HashSet<Integer>();for(var b:active)used.add(b.slot);
   int slot=freeSlot(used,maxActive());
   if(slot<0)throw new IllegalArgumentException("You are already helping "+active.size()+" companions. Finish or abandon one in Help requests first.");
   // Initial accepted counters must be a committed native baseline as well.
   try{service.checkpointForOwnerAction(actor);}catch(SQLException e){throw new IllegalStateException("Native progress could not be confirmed; the help request was not accepted.",e);}
   var contract=terms(bot,human,need);contract.slot=slot;
   try(var c=DatabaseFactory.getConnection()) {
    c.setAutoCommit(false);
    try{var row=PlayerBotHelpDAO.accept(c,new Row(key,contract.account,contract.helper,contract.bot,0,"ACTIVE",Json.write(contract)),maxActive());c.commit();var b=remember(row);b.helper=human;b.requester=bot;show(b);introduce(b,bot,human);}
    catch(SQLException|RuntimeException e){c.rollback();throw e;}
   }catch(SQLException e){throw new IllegalStateException("Cannot accept help request",e);}
  }}}finally{service.completeTransfer(actor);}
 }
 static void joined(Player bot,Player human) {
  Binding b=null;for(var a:bindings(human))if(a.committed.bot==bot.getObjectId())b=a;
  if(b!=null && b.committed.helper==human.getObjectId()){b.requester=bot;b.helper=human;show(b);}
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
  // QuestService calls this synchronously at REWARD -> COMPLETE, while the party is still whole. The checkpoint
  // that decides the reward runs later, after control may already have ended, so it reads this record.
  b.handedInTogether=together(b.helper,b.requester,PlayerBotTeams.same(bot,b.helper));
  if(b.handedInTogether)PlayerBotFindGroup.handedIn(bot);
  b.finished=true;String claim="q"+bot.getObjectId()+":"+quest+":"+b.round;boolean paid=false;
  for(var member:PlayerBotTeams.members(bot))if(member.isPlayerBot() && b.participants.contains(member.getObjectId()))paid|=PlayerBotGrowth.learnHelp(member,claim) && member==bot;
  return paid;
 }
 /** The kick's last check: this helper, a quest request still open, every counter full, not yet finished. */
 static boolean awaitingHandIn(Player bot,Player human) {
  var b=BY_REQUESTER.get(bot.getObjectId());
  return b!=null && b.helper==human && !b.finished && !b.remoteDone && b.quest>0 && b.row!=null && b.row.status().equals("ACTIVE") && objectivesDone(b.committed.objectives);
 }
 /** Shown as a counter in the tracker and journal: hunts and items, not the internal stage or the bot's own hand-in. */
 static boolean listed(Counter n){return shown(n) && !n.kind().equals("TURNIN");}
 static final long FINISH_DELAY_MS=10_000;
 /** 10 s after the last objective: with the helper still in the party (and not kicked) the help is finished in place,
  * the companions' Learning XP is paid, and one checkpoint commits it, so the reward lands now. The quest giver is
  * then released after the farewell window and hands its quest in on its own. */
 static void finishInPlace(Binding b){finishInPlace(b,false);}
 /** A login finds the objectives already done (before a relog, or with the bot gone): nothing of the bot is needed. */
 static final long LOGIN_FINISH_MS=3000;
 static void finishInPlace(Binding b,boolean atLogin) {
  var bot=b.requester;var human=b.helper;
  if(!finishable(human!=null && human.isOnline(),b.finished || b.remoteDone,b.quest,b.row!=null && b.row.status().equals("ACTIVE"),objectivesDone(b.committed.objectives),
   human!=null && PlayerBotHelpKick.kicked(b.committed.bot,human.getObjectId()),atLogin || b.doneTogether || b.active()))return;
  b.handedInTogether=true;b.remoteDone=true;
  if(bot!=null) {
   String claim="q"+bot.getObjectId()+":"+b.quest+":"+b.round;
   for(var member:PlayerBotTeams.members(bot))if(member.isPlayerBot() && b.participants.contains(member.getObjectId()))PlayerBotGrowth.learnHelp(member,claim);
   PlayerBotFindGroup.handedIn(bot);
  }
  commitFinished(b);
 }
 /** Commits the finish from the helper's side, so it needs neither the bot online nor its next checkpoint. A later
  * bot checkpoint adopts the newer revision (PlayerBotHelpDAO.store) and finds nothing ACTIVE to publish. */
 private static void commitFinished(Binding b) {
  Pending p;
  synchronized(b) {
   if(b.row==null || !b.row.status().equals("ACTIVE"))return;
   var c=Json.read(Json.write(b.committed),Contract.class);
   c.helperParticipated=b.helperParticipated;c.participants=new TreeSet<>(b.participants);
   c.status=earned(b.helperParticipated,b.handedInTogether)?"COMPLETE":"RESOLVED";
   p=new Pending(b,b.row,c);
  }
  try(var con=DatabaseFactory.getConnection()) {
   con.setAutoCommit(false);
   try{var saved=store(con,p);con.commit();committed(p,saved);}
   catch(SQLException|RuntimeException e){con.rollback();throw e;}
  }catch(SQLException e){log.warn("Help finish for account {} deferred to the next checkpoint: {}",b.account,e.toString());}
 }
 /** Pure gate for {@link #finishInPlace}: still open, a quest request, every counter full, not kicked, helper in the party. */
 static boolean finishable(boolean present,boolean finished,int quest,boolean active,boolean objectives,boolean kicked,boolean together) {
  return present && !finished && quest>0 && active && objectives && !kicked && together;
 }
 /** Both characters exist and share the party. */
 static boolean together(Player helper,Player requester,boolean sameParty){return helper!=null && requester!=null && sameParty;}
 /** The reward needs real help near the objective and the helper's presence when it was finished (the hand-in for quests). */
 static boolean earned(boolean participated,boolean present){return participated && present;}
 record Pending(Binding binding,Row before,Contract contract) {}
 static Pending pending(Player bot) {
  var b=BY_REQUESTER.get(bot.getObjectId());if(b==null || b.row==null || !b.row.status().equals("ACTIVE"))return null;
  var q=b.quest>0?bot.getQuestStateList().getQuestState(b.quest):null;boolean turnedIn=q!=null && q.getStatus()==QuestStatus.COMPLETE;
  boolean done=turnedIn || b.remoteDone;
  var c=project(bot,b.committed,done);c.participants=new TreeSet<>(b.participants);c.helperParticipated=b.helperParticipated;c.defeated=b.defeated;if(c.quest==0)c=project(bot,c,false);
  if(turnedIn && q.getCompleteCount()!=c.round+1)c.status="REVISED";
  else if(done || c.quest==0 && b.defeated!=0)c.status=earned(b.helperParticipated,done?b.handedInTogether:together(b.helper,b.requester,PlayerBotTeams.same(b.requester,b.helper)))?"COMPLETE":"RESOLVED";
  else if(c.quest>0 && (q==null || q.getStatus()==QuestStatus.LOCKED))c.status="CANCELLED";
  else if(c.quest>0 && q.getCompleteCount()!=c.round || bot.getLevel()<c.minLevel || bot.getLevel()>c.maxLevel)c.status="REVISED";
  return new Pending(b,b.row,c);
 }
 static Row store(Connection c,Pending p)throws SQLException{return p==null?null:PlayerBotHelpDAO.store(c,p.before,p.contract.status,Json.write(p.contract));}
 static void committed(Pending p,Row saved) {
  if(p==null || saved==p.before)return;
  var b=p.binding;List<Counter> previous;
  synchronized(b){if(b.row.revision()>saved.revision())return;previous=b.committed.objectives;b.row=saved;b.committed=Json.read(saved.payload(),Contract.class);b.committed.status=saved.status();b.finished=!saved.status().equals("ACTIVE");}
  if(Set.of("CLAIMED","ABANDONED").contains(saved.status())){retire(b);hide(b,saved.status().equals("CLAIMED"));return;}
  try{
   show(b);
   if(saved.status().equals("ACTIVE"))announce(b,previous);
   else {if(b.requester!=null)PlayerBotFindGroup.helpCompleted(b.requester);settle(b);}
  }
  catch(RuntimeException e){log.warn("Committed help presentation awaits reopen: {}",e.toString());}
 }
 private static String close(Binding b,boolean claim) {
  if(b==null)throw new IllegalArgumentException("Your account has no accepted help request.");
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
   retire(b);b.committed.status=b.row.status();b.finished=true;hide(b,claim);
  }
  return claim?"Help reward collected once. Your help-contract ticket counter has been credited.":"Help request abandoned. Previous committed credit is retained; no partial reward is paid.";
 }
 // --- What the helper sees in chat: start, each step, the end and the reward (owner, 8 Nov). ---
 /** One line in the helper's chat; the important moments also as a native centre-screen notice. */
 private static void tell(Player human,String text,boolean centre) {
  if(human==null || !human.isOnline())return;
  PacketSendUtility.sendMessage(human,"[Help] "+text);
  if(centre)PacketSendUtility.sendMessage(human,text,com.aionemu.gameserver.model.ChatType.BRIGHT_YELLOW_CENTER);
 }
 /** The bot speaking in party chat, as a player would. */
 private static void say(Player bot,String text) {
  if(bot==null || !bot.isSpawned())return;
  for(var member:PlayerBotTeams.members(bot))if(!member.isPlayerBot())
   PacketSendUtility.sendPacket(member,new com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE(bot,text,com.aionemu.gameserver.model.ChatType.GROUP));
 }
 static boolean shown(Counter n){return !n.kind().equals("STAGE");}
 static String counter(Counter n){return shownCounter(n).get("name")+" "+n.current()+(n.required()<0?"":"/"+n.required());}
 /** Only the counters that moved, e.g. "Wriggling Clodworm 4/10". Empty when nothing visible changed. */
 static String progressLine(List<Counter> before,List<Counter> after) {
  var old=new HashMap<String,Long>();if(before!=null)for(var n:before)old.put(n.key(),n.current());
  var moved=new ArrayList<String>();
  for(var n:after)if(shown(n) && !Objects.equals(old.get(n.key()),n.current()))moved.add(counter(n));
  return String.join(", ",moved);
 }
 /** Every hunt and item counter is full; only the bot's own turn-in remains. */
 static boolean objectivesDone(List<Counter> counters) {
  boolean any=false;
  for(var n:counters)if(!n.kind().equals("STAGE") && !n.kind().equals("TURNIN")){any=true;if(n.required()>=0 && n.current()<n.required())return false;}
  return any;
 }
 /** helps = finished requests including this one; tickets = balance after the credit. */
 static String rewardLine(long helps,int needed,long amount,long tickets) {
  if(needed<=0 || amount<=0)return "Help rewards are switched off on this server.";
  if(helps>0 && helps%needed==0)return "You earned "+amount+" Recruitment Ticket"+(amount==1?"":"s")+"! Tickets now: "+tickets+".";
  return "Help "+(helps%needed)+"/"+needed+" toward your next Recruitment Ticket. Tickets now: "+tickets+".";
 }
 private static long[] tickets(int account) {
  initialize();
  try(var c=DatabaseFactory.getConnection()){return new long[]{com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.helps(c,account),com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.wallet(c,account).tickets()};}
  catch(SQLException e){log.warn("Ticket progress for account {} unavailable: {}",account,e.toString());return null;}
 }
 private static String where(Contract c) {
  var map=DataManager.WORLD_MAPS_DATA==null?null:DataManager.WORLD_MAPS_DATA.getTemplate(c.map);
  return (map==null?"this map":map.getName())+" near ("+(int)c.x+", "+(int)c.y+")";
 }
 private static void introduce(Binding b,Player bot,Player human) {
  // The contract is already committed; telling the helper about it must never turn that into an error.
  try{introduceNow(b,bot,human);}catch(RuntimeException e){log.warn("Help request introduction skipped: {}",e.toString());}
 }
 private static void introduceNow(Binding b,Player bot,Player human) {
  var c=b.committed;var goals=c.objectives.stream().filter(n->shown(n) && !n.kind().equals("TURNIN")).map(PlayerBotHelpObjectives::counter).toList();
  var progress=tickets(c.account);int needed=PlayerBotEconomyConfig.TICKET_HELP_REQUESTS;
  String reward=progress==null || needed<=0?"":" Reward: "+rewardPreview(progress[0],needed,PlayerBotEconomyConfig.TICKET_HELP_AMOUNT)+".";
  String finish=c.quest>0?" It completes a few seconds after the last objective, so stay in the party until then.":" It finishes when the target is defeated with you nearby.";
  tell(human,"You are helping "+c.botName+": "+shownNeed(c)+". To do: "+(goals.isEmpty()?shownNeed(c):String.join(", ",goals))+". Where: "+where(c)+"."+finish+reward,false);
  tell(human,"Helping "+c.botName+": "+shownNeed(c),true);
  say(bot,"Thanks for coming! "+(goals.isEmpty()?shownNeed(c):goals.getFirst())+", then I'll hand it in.");
 }
 private static void announce(Binding b,List<Counter> previous) {
  var now=b.committed.objectives;String line=progressLine(previous,now);
  if(!line.isEmpty())tell(b.helper,b.committed.botName+": "+line,false);
  if(b.committed.quest>0 && objectivesDone(now) && (previous==null || !objectivesDone(previous)))
  {b.doneTogether=b.active();
   tell(b.helper,"All objectives done! Stay in "+b.committed.botName+"'s party a few seconds to finish.",true);
   PlayerBotHelpKick.objectivesDone(b.requester,b.helper);
   com.aionemu.gameserver.utils.ThreadPoolManager.getInstance().schedule(()->finishInPlace(b),FINISH_DELAY_MS);}
 }
 /** The helper left the bot's party: the bots carry on, and rejoining before the hand-in keeps the reward. */
 static void left(Player human,Player bot) {
  // Runs while the service releases control: RAM only, no SQL restore.
  var b=human==null || human.isPlayerBot()?null:BY_REQUESTER.get(bot.getObjectId());
  if(b==null || b.account!=human.getAccount().getId() || b.committed.bot!=bot.getObjectId() || !b.row.status().equals("ACTIVE"))return;
  if(PlayerBotHelpKick.kicked(bot.getObjectId(),human.getObjectId()))return;
  tell(human,"You left "+b.committed.botName+"'s party. They keep working on it; rejoin through Find Group before they hand it in to get your reward.",false);
 }
 /** A finished request settles itself: a completed one pays its reward at once, anything else closes with the reason. */
 private static void settle(Binding b) {
  // Also runs at login: a failure here is logged and retried, never thrown into entering the world.
  try{settleNow(b);}catch(RuntimeException e){log.warn("Help request settlement deferred: {}",e.toString());}
 }
 private static void settleNow(Binding b) {
  var human=b.helper;if(human==null || !human.isOnline())return; // retried when the helper next enters the world
  String status=b.row.status(),bot=b.committed.botName;
  switch(status) {
   case "COMPLETE"->{
    try{close(b,true);}catch(IllegalArgumentException e){tell(human,"Request complete, but "+e.getMessage(),false);return;}
    var progress=tickets(b.account);
    String reward=progress==null?"Your help-contract ticket counter has been credited.":rewardLine(progress[0],PlayerBotEconomyConfig.TICKET_HELP_REQUESTS,PlayerBotEconomyConfig.TICKET_HELP_AMOUNT,progress[1]);
    tell(human,"Request complete! "+bot+" handed it in. "+reward,false);tell(human,reward,true);
    var requester=b.requester;
    if(requester!=null && requester.isSpawned() && !requester.isDead()) {
     say(requester,"Thank you, "+human.getName()+"! That was a big help.");
     PacketSendUtility.broadcastToSightedPlayers(requester,new com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION(requester,com.aionemu.gameserver.model.EmotionType.EMOTE,
      com.aionemu.gameserver.model.EmotionId.THANK.id(),requester.getX(),requester.getY(),requester.getZ(),requester.getHeading(),human.getObjectId()),true);
    }
   }
   case "RESOLVED"->{
    if(PlayerBotHelpKick.kicked(b.committed.bot,human.getObjectId()))tell(human,bot+" dropped you right before the hand-in and kept the credit. Some players are like that. No reward this time.",true);
    else tell(human,bot+" handed it in while you were not in the party or not near enough, so it does not count. No reward this time.",false);
    close(b,false);
   }
   case "CANCELLED"->{tell(human,bot+"'s quest is no longer available, so this help request is closed.",false);close(b,false);}
   case "REVISED"->{tell(human,bot+"'s quest changed (its level or round), so this help request is closed without a reward.",false);close(b,false);}
   default->{}
  }
 }
 static Map<String,Object> details(Player human,Player bot,PlayerBotGroupNeeds.Need need){var v=view(terms(bot,human,need),"",0,human);v.put("botLevel",bot.getLevel());v.put("botClass",bot.getPlayerClass().name());return v;}
 /** Quest needs carry their real title; hunt needs carry a template key. Saved contracts keep what they froze, so this applies when shown. */
 private static String shownNeed(Contract c){return c.quest>0?c.need:"Defeat "+PlayerBotGroupNeeds.display(c.need);}
 private static Map<String,Object> shownCounter(Counter n) {
  String name=switch(n.kind()){case "STAGE"->"Quest step";case "TURNIN"->"Companion turns in the quest";case "ITEM"->n.name();default->n.name().equals("Native hunt")?"Monsters to hunt":PlayerBotGroupNeeds.display(n.name());};
  return Map.of("name",name,"current",n.current(),"required",n.required());
 }
 private static Map<String,Object> view(Contract c,String key,long revision,Player human) {
  Map<String,Object> v=new LinkedHashMap<>();v.put("key",key);v.put("revision",revision);v.put("slot",c.slot);v.put("bot",c.bot);v.put("botName",c.botName);v.put("rarity",c.rarity);v.put("need",shownNeed(c));v.put("status",c.status);v.put("objectives",c.objectives.stream().filter(PlayerBotHelpObjectives::listed).map(PlayerBotHelpObjectives::shownCounter).toList());v.put("map",c.map);var map=DataManager.WORLD_MAPS_DATA==null?null:DataManager.WORLD_MAPS_DATA.getTemplate(c.map);v.put("mapName",map==null?"":map.getName());v.put("x",c.x);v.put("y",c.y);v.put("z",c.z);v.put("npc",c.npc);v.put("quest",c.quest);v.put("helper",c.helper);v.put("myCharacter",c.helper==human.getObjectId());v.put("participated",c.helperParticipated);v.put("minLevel",c.minLevel);v.put("maxLevel",c.maxLevel);v.put("level",human.getLevel());v.put("eligible",PlayerBotGroupNeeds.level(human.getLevel(),c.minLevel,c.maxLevel));
  v.put("reward","Companions who take part earn 50 Learning XP. Complete and collect "+PlayerBotEconomyConfig.TICKET_HELP_REQUESTS+" different requests for "+PlayerBotEconomyConfig.TICKET_HELP_AMOUNT+" Recruitment Ticket(s). You must help near the objective. A quest request completes a few seconds after its last objective while you are in the party; the companion hands the quest in by itself.");return v;
 }
 public static Map<String,Object> snapshot(Player human){var b=current(human);return b==null?Map.of():view(b.committed,b.row.key(),b.row.revision(),human);}
 /** Every active request for the Help requests window (the latest finished one when none is active). */
 public static List<Map<String,Object>> snapshots(Player human) {
  var list=new ArrayList<Map<String,Object>>();
  for(var b:bindings(human))list.add(view(b.committed,b.row.key(),b.row.revision(),human));
  if(list.isEmpty()){var b=current(human);if(b!=null)list.add(view(b.committed,b.row.key(),b.row.revision(),human));}
  return list;
 }
 public static void abandonFromJournal(Player human){abandonFromJournal(human,HELP_QUEST);}
 /** Native Abandon on one wrapper quest closes that slot's request. */
 public static void abandonFromJournal(Player human,int quest) {
  var b=binding(human,slotOf(quest));
  if(b==null){QuestService.abandonQuest(human,quest);return;}
  close(b,false);
 }
 public static String action(Player human,String operation){return action(human,operation,"");}
 /** Help requests window controls; key picks the request (empty = the first, for older pages). */
 public static String action(Player human,String operation,String key) {
  var b=key==null || key.isEmpty()?current(human):byKey(human,key);
  if(b==null)throw new IllegalArgumentException("That help request is no longer active. Refresh Help requests.");
  if(operation.equals("abandon"))return close(b,false);
  if(operation.equals("claim"))return close(b,true);
  if(operation.equals("track")){if(b.committed.helper!=human.getObjectId() || Set.of("CLAIMED","ABANDONED").contains(b.row.status()))throw new IllegalArgumentException("Track the active request on the character that accepted it.");show(b);return "Help request restored to your native journal and tracker.";}
  throw new IllegalArgumentException("Choose a help-request control.");
 }
 private PlayerBotHelpObjectives() {}
}
