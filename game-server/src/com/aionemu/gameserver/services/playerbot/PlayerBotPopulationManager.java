/* RandomPlayerbotMgr::{ProcessBot,Randomize,RandomizeFirst} and RandomBotLevelMgr,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotPopulationConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.AutoGroupService;
import com.aionemu.gameserver.world.World;

/** Bounded planning only; the service executes one native logout/factory/login at a time.
 * Reset intent and refresh/played-time events join the character's SQL checkpoint.
 * The actual rebuild is unpublished, so failed preparation cannot expose a partial actor. */
final class PlayerBotPopulationManager {
 enum Action { REBUILD, REVIVE, QUEUE_JOIN, QUEUE_ENTER, QUEUE_LEAVE, LEGION }
 record Work(PlayerBotSession session,int level,boolean full,String reason,Action action,int mask) {
  Work(PlayerBotSession session,int level,boolean full,String reason){this(session,level,full,reason,level==0?Action.REVIVE:Action.REBUILD,0);}
 }
 private static final class State {
  final Properties values;long observed;boolean login=true;
  State(Properties values,long now,int level){this.values=values;observed=now;}
  long number(String key){return Long.parseLong(values.getProperty(key,"0"));}
  void number(String key,long value){values.setProperty(key,Long.toString(value));}
 }
 private final Map<Integer,State> states=new java.util.concurrent.ConcurrentHashMap<>();
 private final Map<Integer,Integer> pending=new java.util.concurrent.ConcurrentSkipListMap<>();
 private final Random random=new Random();
 private final PlayerBotLegions legions=new PlayerBotLegions();
 private final PlayerBotPopulationLevelSync levelSync=new PlayerBotPopulationLevelSync();
 int playerLevelLimit(long now){return levelSync.limit(now,PlayerBotConfig.POPULATION_STARTING_LEVEL,World.getInstance().getAllPlayers());}
 private record LevelChange(int oldLevel,int newLevel) {}
 private final Map<Integer,java.util.concurrent.ConcurrentLinkedQueue<LevelChange>> levelChanges=new java.util.concurrent.ConcurrentHashMap<>();
 private List<PlayerBotPopulationLevels.Band> elyos=List.of(),asmodians=List.of();
 private Set<Integer> friends=Set.of(),humanLegions=Set.of();
 private long nextCensus,nextPending,nextProtection,loginAllowedAt,lastHuman,emptySince;
 private int pendingBudget;private String configuration="";
 private long nextQueueCensus,nextQueueAdmission;private volatile List<AutoGroupService.PopulationQueue> queues=List.of();
 private record QueueAttempt(int character,int mask) {}
 private final Map<QueueAttempt,Long> queueRetries=new HashMap<>();

 static boolean global(Player bot) {
  return bot!=null && bot.isWorldBot() && PlayerBotPopulationPolicy.serverAccount(bot.getAccount().getId());
 }
 private static boolean named(String names,Player bot) {
  return Arrays.stream(names.split(",")).map(String::trim).anyMatch(bot.getName()::equalsIgnoreCase);
 }
 private boolean humanParty(Player bot) {
  return PlayerBotTeams.members(bot).stream().anyMatch(p->p!=null && !p.isPlayerBot());
 }
 private boolean realLegion(Player bot) {return bot.getLegion()!=null && humanLegions.contains(bot.getLegion().getLegionId());}
 private boolean queueSafe(PlayerBotSession session) {
  var bot=session.bot();return global(bot) && session.autonomous() && !session.closing() && bot.isSpawned() && !bot.isDead()
   && !bot.isInInstance() && !bot.isInTeam() && !session.worldExcursion() && !bot.isCasting() && !bot.isTrading() && !bot.isLooting()
   && !bot.getController().isInCombat() && !bot.isFlying() && bot.getFlightPath()==null && bot.canPerformMove()
   && !PlayerBotFindGroup.committed(bot) && !(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController move
    && (move.ridingPlatform() || move.traversingHeight()));
 }
 private boolean excluded(PlayerBotSession session,boolean brackets) {
  Player bot=session.bot();return !global(bot) || !session.autonomous() || session.closing() || humanParty(bot)
   || named(brackets?PlayerBotPopulationConfig.BRACKET_EXCLUDE:PlayerBotPopulationConfig.RESET_EXCLUDE,bot)
   || (brackets?PlayerBotPopulationConfig.LEGIONS:PlayerBotPopulationConfig.RESET_LEGIONS) && realLegion(bot)
   || brackets && PlayerBotPopulationConfig.FRIENDS && friends.contains(bot.getObjectId());
 }
 boolean safe(PlayerBotSession session) {
  Player bot=session.bot();
  return global(bot) && session.autonomous() && !session.closing() && bot.isSpawned() && !bot.isDead()
   && !bot.isInInstance() && !session.worldExcursion() && !humanParty(bot) && !bot.isCasting() && !bot.isTrading() && !bot.isLooting()
   && !bot.isFlying() && bot.getFlightPath()==null && !bot.isUsingFlightTransporterOrWindstream() && bot.canPerformMove()
   && !bot.getController().isInCombat() && bot.getAggroList().stream().findAny().isEmpty()
   && !AutoGroupService.getInstance().isLookingForInstance(bot)
   && !(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController move
    && (move.ridingPlatform() || move.traversingHeight()))
   && !PlayerBotWorldPlacement.nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ())
   && bot.getKnownList().streamPlayers().noneMatch(p->!p.isPlayerBot() && p.getKnownList().sees(bot));
 }
 private State state(Player bot,long now)throws IOException {
  var known=states.get(bot.getObjectId());if(known!=null)return known;
  var values=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"population",null);
  if(values.isEmpty()) {
   values.setProperty("account",Integer.toString(bot.getAccount().getId()));values.setProperty("character",Integer.toString(bot.getObjectId()));
   values.setProperty("lastLevel",Integer.toString(bot.getLevel()));values.setProperty("levelPlayedMillis","0");
   // Donor login defers the initial refresh instead of doing a factory burst in the login callback.
   values.setProperty("randomizeAt",Long.toString(now+1000L*PlayerBotPopulationPolicy.between(random,3,Math.max(4,PlayerBotConfig.POPULATION_UPDATE_INTERVAL_SECONDS*2/5))));
  }
  known=new State(values,now,bot.getLevel());states.put(bot.getObjectId(),known);return known;
 }
 private static void queueMetadata(Player bot,State state)throws IOException {
  PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"population",state.values);
 }
 private void configure() {
  PlayerBotPopulationConfig.validate();var settings=PlayerBotPopulationPolicy.settings();
  String signature=PlayerBotPopulationConfig.ELYOS+"|"+PlayerBotPopulationConfig.ASMODIANS+"|"+settings.minLevel()+"|"+settings.maxLevel();
  if(configuration.equals(signature))return;
  elyos=PlayerBotPopulationLevels.parse(PlayerBotPopulationConfig.ELYOS,settings.minLevel(),settings.maxLevel());
  asmodians=PlayerBotPopulationLevels.parse(PlayerBotPopulationConfig.ASMODIANS,settings.minLevel(),settings.maxLevel());
  if(elyos.size()!=asmodians.size())throw new IllegalArgumentException("Both population factions require the same bracket count");
  configuration=signature;pending.clear();nextCensus=0;
 }
 private List<PlayerBotPopulationLevels.Band> bands(Race race){return race==Race.ELYOS?elyos:asmodians;}
 private void protection(long now)throws SQLException {
  if(now<nextProtection)return;
  var nextFriends=new HashSet<Integer>();var nextLegions=new HashSet<Integer>();
  try(var c=DatabaseFactory.getConnection()) {
   c.setReadOnly(true);
   try(var s=c.prepareStatement("SELECT DISTINCT f.friend FROM friends f JOIN playerbot_roster b ON b.player_id=f.friend LEFT JOIN playerbot_roster owner_bot ON owner_bot.player_id=f.player WHERE b.account_id BETWEEN -1023999 AND -1000000 AND owner_bot.player_id IS NULL");var rows=s.executeQuery()) {
    while(rows.next())nextFriends.add(rows.getInt(1));
   }
   try(var s=c.prepareStatement("SELECT DISTINCT lm.legion_id FROM legion_members lm LEFT JOIN playerbot_roster b ON b.player_id=lm.player_id WHERE b.player_id IS NULL");var rows=s.executeQuery()) {
    while(rows.next())nextLegions.add(rows.getInt(1));
   }
  }
  // Failure keeps prior protections and aborts this cycle; never interpret SQL failure as no friends.
  friends=Set.copyOf(nextFriends);humanLegions=Set.copyOf(nextLegions);nextProtection=now+15000;
 }
 private int resetTarget(State state,int current,boolean login,boolean natural) {
  if(!PlayerBotPopulationConfig.RESET)return 0;
  if(!login && !natural)return 0;
  if(natural && current<=1)return 0;
  if(!login && PlayerBotPopulationConfig.SKIP_FROM>0 && current==PlayerBotPopulationConfig.SKIP_FROM)return PlayerBotPopulationConfig.SKIP_TO;
  int max=PlayerBotPopulationConfig.RESET_MAX;
  if(max>0 && current>max)return PlayerBotPopulationConfig.RESET_TO;
  boolean timeAllows=!PlayerBotPopulationConfig.RESTRICT_PLAYED || current!=max
   || login && state.number("levelPlayedMillis")>=1000L*PlayerBotPopulationConfig.MIN_PLAYED;
  if(max>0 && timeAllows && (current==max || !login && PlayerBotPopulationConfig.SCALED)
   && random.nextInt(100)<PlayerBotPopulationLevels.resetChance(current,max,PlayerBotPopulationConfig.RESET_CHANCE,PlayerBotPopulationConfig.SCALED))return PlayerBotPopulationConfig.RESET_TO;
  return login && PlayerBotPopulationConfig.SKIP_FROM>0 && current==PlayerBotPopulationConfig.SKIP_FROM?PlayerBotPopulationConfig.SKIP_TO:0;
 }
 private void census(List<PlayerBotSession> actors) {
  List<PlayerBotPopulationLevels.Band> e=elyos,a=asmodians;
  if(PlayerBotPopulationConfig.DYNAMIC) {
   int[] ec=new int[e.size()],ac=new int[a.size()];
   for(var p:World.getInstance().getAllPlayers())if(!p.isPlayerBot() && p.isSpawned()) {
    var table=p.getRace()==Race.ELYOS?e:a;int index=PlayerBotPopulationLevels.range(table,p.getLevel());
    if(index>=0)(p.getRace()==Race.ELYOS?ec:ac)[index]++;
   }
   if(PlayerBotPopulationConfig.SYNC_FACTIONS)for(int i=0;i<ec.length;i++){ec[i]+=ac[i];ac[i]=ec[i];}
   e=PlayerBotPopulationLevels.dynamic(e,ec,PlayerBotPopulationConfig.PLAYER_WEIGHT);
   a=PlayerBotPopulationLevels.dynamic(a,ac,PlayerBotPopulationConfig.PLAYER_WEIGHT);
  }
  for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
   var eligible=actors.stream().filter(s->s.bot().getRace()==race && !excluded(s,true))
    .map(s->new PlayerBotPopulationLevels.Actor(s.bot().getObjectId(),s.bot().getLevel(),safe(s))).toList();
   var table=race==Race.ELYOS?e:a;
   PlayerBotPopulationLevels.plan(table,eligible,pending).forEach(pending::putIfAbsent);
  }
 }
 Work poll(Collection<PlayerBotSession> sessions,long now)throws IOException,SQLException {
  configure();protection(now);
  playerLevelLimit(now);
  if(now>=nextQueueCensus){queues=AutoGroupService.getInstance().populationQueues();nextQueueCensus=now+30000;}
  var actors=sessions.stream().filter(s->global(s.bot()) && !s.closing()).toList();
  var ids=new HashSet<Integer>();actors.forEach(s->ids.add(s.bot().getObjectId()));pending.keySet().retainAll(ids);states.keySet().retainAll(ids);
  if(PlayerBotPopulationConfig.NATIVE_QUEUES)for(var session:actors)if(queueSafe(session)) {
   int mask=AutoGroupService.getInstance().populationInvitationMask(session.bot());
   if(mask>0)return new Work(session,0,false,"native matched queue invitation",Action.QUEUE_ENTER,mask);
  }
  // LfgLeaveAction retains deliberate queues while enabled, even if humans
  // withdraw. Only an explicit disable releases unmatched generated entries.
  if(!PlayerBotPopulationConfig.NATIVE_QUEUES)for(var session:actors)if(queueSafe(session)) {
   int mask=AutoGroupService.getInstance().populationQueuedMask(session.bot());
   if(mask>0)return new Work(session,0,false,"native population queues disabled",Action.QUEUE_LEAVE,mask);
  }
  if(PlayerBotPopulationConfig.BRACKETS && now>=nextCensus){nextCensus=now+1000L*PlayerBotPopulationConfig.BRACKET_CHECK;census(actors);}
  if(now>=nextPending){nextPending=now+1000L*PlayerBotPopulationConfig.PENDING_CHECK;pendingBudget=PlayerBotPopulationConfig.PROCESS_LIMIT==0?120:PlayerBotPopulationConfig.PROCESS_LIMIT;}
  int cold=0;Work selected=null;
  for(var session:actors) {
   if(!states.containsKey(session.bot().getObjectId()) && ++cold>Math.min(120,PlayerBotConfig.POPULATION_PER_INTERVAL))continue;
   Player bot=session.bot();
   synchronized(session) {
    if(session.closing() || !PlayerBotService.getInstance().current(session))continue;
    var state=state(bot,now);
    bot.getCommonData().setNoExp(PlayerBotPopulationConfig.FIXED_LEVELS);
    int last=(int)state.number("lastLevel"),current=bot.getLevel();boolean natural=current==last+1;
    if(last!=current){state.number("lastLevel",current);state.number("levelPlayedMillis",0);}
    else state.number("levelPlayedMillis",Math.addExact(state.number("levelPlayedMillis"),Math.max(0,now-state.observed)));
    state.observed=now;boolean login=state.login;state.login=false;
    int target=excluded(session,false)?0:resetTarget(state,current,login,false);
    var changes=levelChanges.get(bot.getObjectId());boolean hookObserved=false;
    if(changes!=null)for(LevelChange change;(change=changes.poll())!=null;) {
     hookObserved=true;
     if(target==0 && !excluded(session,false))target=resetTarget(state,change.newLevel(),false,change.newLevel()==change.oldLevel()+1);
    }
    // The first poll can observe a level change that predates hook publication.
    if(target==0 && natural && !hookObserved && !excluded(session,false))target=resetTarget(state,current,false,true);
    boolean playedDue=now>=state.number("playedCheckAt");
    if(playedDue)state.number("playedCheckAt",now+1000L*PlayerBotPopulationConfig.PLAYED_CHECK);
    if(target==0 && PlayerBotPopulationConfig.RESET && PlayerBotPopulationConfig.RESTRICT_PLAYED && playedDue
     && PlayerBotPopulationConfig.RESET_MAX>0 && current>=PlayerBotPopulationConfig.RESET_MAX && !excluded(session,false))target=resetTarget(state,current,true,false);
    if(target>0 && state.number("pendingLevel")==0){state.number("pendingLevel",target);state.values.setProperty("pendingFull","true");state.values.setProperty("pendingReason","reset");}
    queueMetadata(bot,state);
    if(reviveAllowed(session) && reviveDue(bot,now) && selected==null)selected=new Work(session,0,false,"scheduled revival");
    if(!bot.isDead() && state.number("deadUntil")!=0)revived(bot);
    if(state.number("pendingLevel")>0) {
     boolean bracketIntent=state.values.getProperty("pendingReason","").equals("bracket");
     String reason=state.values.getProperty("pendingReason","");
     if(excluded(session,bracketIntent) || bracketIntent && !PlayerBotPopulationConfig.BRACKETS
      || reason.equals("reset") && !PlayerBotPopulationConfig.RESET || Set.of("refresh","teleport").contains(reason) && !PlayerBotPopulationConfig.REFRESH) {
      state.number("pendingLevel",0);state.values.setProperty("pendingReason","");state.values.setProperty("pendingField","false");queueMetadata(bot,state);
     }else if(selected==null && safe(session))selected=new Work(session,(int)state.number("pendingLevel"),Boolean.parseBoolean(state.values.getProperty("pendingFull","true")),"level reset/retry");
    }
    if(selected!=null || state.number("pendingLevel")>0)continue;
    if(PlayerBotPopulationConfig.BRACKETS && pendingBudget>0 && pending.containsKey(bot.getObjectId())) {
     if(excluded(session,true)){pending.remove(bot.getObjectId());continue;}
     int index=pending.get(bot.getObjectId());var table=bands(bot.getRace());
     if(index>=table.size() || !table.get(index).valid()){pending.remove(bot.getObjectId());continue;}
     if(safe(session)){var band=table.get(index);selected=new Work(session,PlayerBotPopulationPolicy.between(random,band.lower(),band.upper()),true,"level bracket "+index);continue;}
    }
    if(PlayerBotPopulationConfig.REFRESH && state.number("randomizeAt")<=now && !excluded(session,false) && safe(session)
     && bot.getPlayerGroup()==null && !bot.isInAlliance() && PlayerBotWorldActivities.idle(bot)) {
     var settings=PlayerBotPopulationPolicy.settings();boolean full=current<3 || PlayerBotPopulationConfig.DOWNGRADE_MAX && current>=settings.maxLevel();
     int level=PlayerBotPopulationLevels.randomizeLevel(settings,current,playerLevelLimit(now),PlayerBotPopulationConfig.DOWNGRADE_MAX,random);
     selected=new Work(session,level,full,"periodic factory refresh");
    }
   }
  }
  if(selected!=null)return selected;
  if(PlayerBotPopulationConfig.NATIVE_QUEUES && now>=nextQueueAdmission) {
   nextQueueAdmission=now+1000;
   queueRetries.entrySet().removeIf(entry->entry.getValue()<=now || !ids.contains(entry.getKey().character()));
   for(var queue:queues)if(queue.humanDemand())for(var session:actors) {
    var bot=session.bot();if(!queueSafe(session) || AutoGroupService.getInstance().isLookingForInstance(bot)
     || bot.getLevel()<queue.minimumLevel() || bot.getLevel()>queue.maximumLevel()
     || queueRetries.containsKey(new QueueAttempt(bot.getObjectId(),queue.maskId())))continue;
    int occupants=bot.getRace()==Race.ELYOS?queue.elyosPlayers()+queue.elyosBots():queue.asmodianPlayers()+queue.asmodianBots();
    int capacity=com.aionemu.gameserver.dataholders.DataManager.INSTANCE_COOLTIME_DATA.getMaxMemberCount(queue.mapId(),bot.getRace());
    capacity=(queue.elyosPlayers()+queue.asmodianPlayers()>0?capacity:0)+(bot.getRace()==Race.ELYOS?queue.elyosOpenSeats():queue.asmodianOpenSeats());
    if(capacity>0 && occupants<capacity)return new Work(session,0,false,"native queue population demand",Action.QUEUE_JOIN,queue.maskId());
   }
  }
  var legionActor=legions.candidate(actors.stream().filter(this::queueSafe).filter(s->!AutoGroupService.getInstance().isLookingForInstance(s.bot())).toList(),now);
  return legionActor==null?null:new Work(legionActor,0,false,"native generated legion membership",Action.LEGION,0);
 }
 void loaded(Player bot)throws SQLException,IOException {legions.assign(bot);}
 boolean legionAction(PlayerBotSession session)throws SQLException,IOException {
  if(!queueSafe(session) || AutoGroupService.getInstance().isLookingForInstance(session.bot()))return false;
  legions.assign(session.bot());return session.bot().isLegionMember();
 }
 boolean queueAction(Work work) {
  if(!queueSafe(work.session()) || work.action()!=Action.QUEUE_LEAVE && !PlayerBotPopulationConfig.NATIVE_QUEUES)return false;
  var bot=work.session().bot();var service=AutoGroupService.getInstance();
  if(work.action()==Action.QUEUE_ENTER) {
   if(service.populationInvitationMask(bot)!=work.mask())return false;
   work.session().preparePopulationPlacement();service.pressEnter(bot,work.mask());
  } else if(work.action()==Action.QUEUE_LEAVE) {
   if(PlayerBotPopulationConfig.NATIVE_QUEUES || !service.leavePopulationQueue(bot,work.mask()))return false;
  } else {
   // Ticket, cooldown or changed match eligibility must not starve other actors.
   queueRetries.put(new QueueAttempt(bot.getObjectId(),work.mask()),System.currentTimeMillis()+30000);
   if(!service.joinPopulationQueue(bot,work.mask()))return false;
  }
  nextQueueCensus=0;return true;
 }
 /** Called under the current actor's monitor after native execution admission. */
 void stage(Work work,long now)throws IOException {
  protectionNow();
  var bot=work.session().bot();var state=state(bot,now);
  boolean bracket=work.reason().startsWith("level bracket") || state.values.getProperty("pendingReason","").equals("bracket");
  if(excluded(work.session(),bracket) || !safe(work.session()))throw new IllegalStateException("Population rebuild became protected");
  if(state.number("pendingLevel")==0)state.values.setProperty("pendingReason",bracket?"bracket":work.reason().startsWith("periodic")?"refresh":"reset");
  state.number("pendingLevel",work.level());state.values.setProperty("pendingFull",Boolean.toString(work.full()));queueMetadata(bot,state);
 }
 private void protectionNow()throws IOException {try{nextProtection=0;protection(System.currentTimeMillis());}catch(SQLException e){throw new IOException("Cannot verify native population protections",e);}}
 void started(Work work){if(pending.remove(work.session().bot().getObjectId())!=null)pendingBudget=Math.max(0,pendingBudget-1);}
 void closed(Player bot){closedId(bot.getObjectId());}
 void closedId(int id){states.remove(id);pending.remove(id);levelChanges.remove(id);legions.closed(id);}
 void levelChanged(Player bot,int oldLevel,int newLevel) {
  if(global(bot) && bot.isSpawned() && newLevel==oldLevel+1)
   levelChanges.computeIfAbsent(bot.getObjectId(),id->new java.util.concurrent.ConcurrentLinkedQueue<>()).add(new LevelChange(oldLevel,newLevel));
 }
 boolean reviveAllowed(PlayerBotSession session) {
  var bot=session.bot();return global(bot) && session.autonomous() && !session.closing() && bot.isDead()
   && !humanParty(bot) && !bot.isInInstance() && !AutoGroupService.getInstance().isLookingForInstance(bot);
 }
 void placed(Player bot)throws IOException {
  if(!global(bot) || !PlayerBotPopulationConfig.REFRESH)return;
  var state=state(bot,System.currentTimeMillis());
  if(state.number("pendingLevel")==0) {
   state.number("pendingLevel",bot.getLevel());state.values.setProperty("pendingFull","false");state.values.setProperty("pendingField","false");state.values.setProperty("pendingReason","teleport");queueMetadata(bot,state);
  }
 }
 boolean reviveDue(Player bot,long now)throws IOException {
  if(!global(bot))return false;var state=states.get(bot.getObjectId());if(state==null)return false;
  if(state.number("deadUntil")==0 || now>=state.number("deadUntil")) {
   state.number("deadUntil",now+1000L*Math.max(PlayerBotConfig.POPULATION_PRESENCE_MAX,PlayerBotPopulationConfig.REVIVE_MAX+1L));
   state.number("reviveAt",PlayerBotPopulationPolicy.deadline(now,random,PlayerBotPopulationConfig.REVIVE_MIN,PlayerBotPopulationConfig.REVIVE_MAX));queueMetadata(bot,state);
  }
  return now>=state.number("reviveAt");
 }
 void revived(Player bot)throws IOException {
  var state=states.get(bot.getObjectId());if(state!=null){
   state.number("deadUntil",0);state.number("reviveAt",0);
   if(state.number("pendingLevel")==0){state.number("pendingLevel",bot.getLevel());state.values.setProperty("pendingFull","false");state.values.setProperty("pendingField","true");state.values.setProperty("pendingReason","revive");}
   queueMetadata(bot,state);
  }
 }
 void initialize(){configure();}
 /** Rebuild only after normal logout saved a durable intent and released the old actor. */
 boolean prepareLoaded(Player bot)throws Exception {
  configure();var state=state(bot,System.currentTimeMillis());int level=(int)state.number("pendingLevel");
  bot.getCommonData().setNoExp(PlayerBotPopulationConfig.FIXED_LEVELS);
  // Native login hook runs before publication, including max-level/skip rules.
  if(level==0 && PlayerBotPopulationConfig.RESET) {
   protectionNow();
   if(!named(PlayerBotPopulationConfig.RESET_EXCLUDE,bot) && !(PlayerBotPopulationConfig.RESET_LEGIONS && realLegion(bot))) {
    level=resetTarget(state,bot.getLevel(),true,false);
    if(level>0){state.number("pendingLevel",level);state.values.setProperty("pendingFull","true");state.values.setProperty("pendingReason","reset");}
   }
  }
  state.login=false;
  if(level==0)return false;
  // SQL friendships/legion membership and live configuration can change after
  // intent staging. Resolve exclusions again on the fresh unpublished character.
  protectionNow();String reason=state.values.getProperty("pendingReason","reset");boolean bracket=reason.equals("bracket");
  boolean cancelled=bracket && !PlayerBotPopulationConfig.BRACKETS || reason.equals("reset") && !PlayerBotPopulationConfig.RESET
   || Set.of("refresh","teleport").contains(reason) && !PlayerBotPopulationConfig.REFRESH
   || !reason.equals("revive") && (named(bracket?PlayerBotPopulationConfig.BRACKET_EXCLUDE:PlayerBotPopulationConfig.RESET_EXCLUDE,bot)
    || (bracket?PlayerBotPopulationConfig.LEGIONS:PlayerBotPopulationConfig.RESET_LEGIONS) && realLegion(bot)
    || bracket && PlayerBotPopulationConfig.FRIENDS && friends.contains(bot.getObjectId()));
  if(cancelled){state.number("pendingLevel",0);state.values.setProperty("pendingReason","");state.values.setProperty("pendingField","false");queueMetadata(bot,state);return false;}
  if(bot.isDead()){queueMetadata(bot,state);return false;}
  boolean full=Boolean.parseBoolean(state.values.getProperty("pendingFull","true"));
  var home=PlayerBotPersistence.Home.of(bot);
  if(full || Boolean.parseBoolean(state.values.getProperty("pendingField","false"))) {
   var selection=Boolean.parseBoolean(state.values.getProperty("pendingField","false"))
    ?PlayerBotWorldPlacement.chooseGrindDestination(bot.getRace(),level,random)
    :PlayerBotWorldPlacement.chooseReserveDestination(bot.getRace(),level,PlayerBotConfig.POPULATION_CITY_PERCENT,random);
   var place=selection.location();if(place==null)throw new IllegalStateException("Population factory awaits native level "+level+" landing: "+selection.reason());
   home=new PlayerBotPersistence.Home(place.map(),place.x(),place.y(),place.z(),(byte)0,0);
  }
  var chosen=PlayerBotService.getInstance().plannedPopulationClass(bot);
  PlayerBotFactoryRecipes.prepare(bot,full);
  if(full){bot.getCommonData().setPlayerClass(level>=10?chosen:chosen.getStartingClass());bot.getCommonData().setDaeva(level>=10);bot.getCommonData().setLevel(level);}
  bot.getGameStats().updateStatsTemplate();
  PlayerBotTemporary.rebuildWorld(bot,PlayerBotRules.roleFor(level>=10?chosen:chosen.getStartingClass()),full);
  if(!World.getInstance().setPosition(bot,home.map(),1,home.x(),home.y(),home.z(),home.heading()))throw new IllegalStateException("Native population reset position refused");
  state.number("pendingLevel",0);state.values.setProperty("pendingFull","false");state.values.setProperty("pendingField","false");state.values.setProperty("pendingReason","");state.number("lastLevel",bot.getLevel());
  if(full)state.number("levelPlayedMillis",0);
  if(full || reason.equals("refresh") || state.number("randomizeAt")==0)
   state.number("randomizeAt",PlayerBotPopulationPolicy.deadline(System.currentTimeMillis(),random,PlayerBotPopulationConfig.REFRESH_MIN,PlayerBotPopulationConfig.REFRESH_MAX));
  queueMetadata(bot,state);PlayerBotPersistence.save(bot,PlayerBotPersistence.Home.of(bot));
  states.remove(bot.getObjectId());return true;
 }
 boolean allowLogin(long now) {
  if(!PlayerBotPopulationConfig.REQUIRE_PLAYERS)return true;
  boolean human=World.getInstance().getAllPlayers().stream().anyMatch(p->!p.isPlayerBot() && p.isOnline());
  if(human){lastHuman=now;emptySince=0;if(loginAllowedAt==0)loginAllowedAt=now+1000L*PlayerBotPopulationConfig.LOGIN_DELAY;return now>=loginAllowedAt;}
  loginAllowedAt=0;if(emptySince==0)emptySince=now;return false;
 }
 boolean logoutForNoPlayers(long now){return PlayerBotPopulationConfig.REQUIRE_PLAYERS && emptySince>0 && now-Math.max(lastHuman,emptySince)>=1000L*PlayerBotPopulationConfig.LOGOUT_DELAY;}
 String summary(){return "Population management: brackets="+PlayerBotPopulationConfig.BRACKETS+", pending bracket resets="+pending.size()+", level resets="+PlayerBotPopulationConfig.RESET+", factory refresh="+PlayerBotPopulationConfig.REFRESH+", upstream placement="+PlayerBotPopulationConfig.UPSTREAM_PLACEMENT+"; "+legions.summary()+"; native queue census="+queues;}
}
