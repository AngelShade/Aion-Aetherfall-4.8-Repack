package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.concurrent.PriorityThreadFactory;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.findGroup.*;
import com.aionemu.gameserver.model.gameobjects.player.CustomPlayerState;
import com.aionemu.gameserver.model.gameobjects.player.DeniedStatus;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.chatserver.ChatServer;
import com.aionemu.gameserver.services.findgroup.FindGroupService;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native Find Group registrations and bounded social work, independent of AI/terrain/SQL lanes.
 * Registrations and conversations have the same login lifetime as human listings.
 * There is no second roster, saved party, quest progress or persistent timer store. */
public final class PlayerBotFindGroup {
 enum Tab { RECRUIT, APPLY, INSTANCE }
 enum Intent { JOIN, START, WAIT, HELP, LEAD, LEAVE, HELLO, TRADE, ITEMS, FOLLOW, STAY, ATTACK, STATUS, THANKS, TELEPORT, UNKNOWN }
 private static final Pattern TRADE = Pattern.compile("\\b(trade|exchange|swap)\\b");
 private static final Pattern ITEMS = Pattern.compile("\\b(items?|inventory|what do you have|selling|sell|buy|shop|spare)\\b");
 private static final Pattern ATTACK = Pattern.compile("\\b(attack|kill (?:it|that|this|my target)|assist me|help me fight)\\b");
 private static final Pattern STATUS = Pattern.compile("\\b(how are you|how's it going|status|where are you|you ok|your hp|your mana)\\b");
 private static final Pattern JOIN = Pattern.compile("\\b(inv(?:ite)?|join|group|party|apply)\\b");
 private static final Pattern START = Pattern.compile("\\b(ready|start|go|begin|let's go|lets go|rdy)\\b");
 private static final Pattern WAIT = Pattern.compile("\\b(wait|hold|not ready|not yet|don't start|dont start|do not start|stop|brb|afk)\\b");
 private static final Pattern HELP = Pattern.compile("\\b(help|how|where|what|quest|objective|details|status|need|role|tank|heal(?:er|ing)?|dps)\\b");
 private static final Pattern LEAVE = Pattern.compile("^(?:bye|goodbye|leave|cancel|no thanks|never mind|nevermind)[.! ]*$");
 private static final Pattern NEGATIVE = Pattern.compile("\\b(no|not|don't|dont|never|can't|cant|cannot)\\b");
 private static final Map<Integer, Post> POSTS = new ConcurrentHashMap<>();
 private static final java.util.concurrent.atomic.AtomicLong GOAL_HELP_OPENED=new java.util.concurrent.atomic.AtomicLong();
 static long goalHelpRequestsOpened(){return GOAL_HELP_OPENED.get();}
 private static final Set<Integer> PENDING_GOAL_HELP=ConcurrentHashMap.newKeySet();
 /** Nominate only; the existing bounded social worker owns native posts and invitation custody. */
 static void requestGoalHelp(Player bot) {
  int id=bot.getObjectId();
  if(!enabled() || !PENDING_GOAL_HELP.add(id))return;
  if(!REQUESTS.offer(()->{try{publishGoalHelp(bot);}finally{PENDING_GOAL_HELP.remove(id);}}))PENDING_GOAL_HELP.remove(id);
 }
 static boolean goalHelpRoom(boolean existing,boolean helperOffer,long recruits,int limit) {
  return (!existing || helperOffer) && recruits<Math.clamp(limit,1,30);
 }
 private static void publishGoalHelp(Player bot) {
  if(!enabled() || !free(bot))return;
  var need=PlayerBotGroupNeeds.gear(bot);if(need==null)return;
  var old=POSTS.get(bot.getObjectId());
  long recruits=POSTS.values().stream().filter(p->p.entry!=null && p.tab==Tab.RECRUIT && p.bot.getRace()==bot.getRace()).count();
  if(!goalHelpRoom(old!=null,old!=null && old.tab==Tab.APPLY,recruits,PlayerBotConfig.FIND_GROUP_POSTS_PER_TAB))return;
  // A real need may replace this free bot's helper offer; preserve existing recruitment and instance runs.
  if(old!=null){withdraw(old);POSTS.remove(bot.getObjectId(),old);}
  var post=new Post(bot,Tab.RECRUIT,need);POSTS.put(bot.getObjectId(),post);register(post);
  if(post.entry==null)POSTS.remove(bot.getObjectId(),post);
 }
 private static final class FreeHelp {
  final Post post;final Player human;volatile boolean ended;
  /** After the final boss the party stays together this long: a goodbye, an emote, then a normal leave. */
  volatile long graceUntil;
  /** When a dungeon run's bots first found their human offline (relog hold). */
  volatile long offlineSince;
  final Set<Integer> members=ConcurrentHashMap.newKeySet();
  FreeHelp(Post post,Player human){this.post=post;this.human=human;}
 }
 private static final Map<Integer,FreeHelp> FREE_HELP=new ConcurrentHashMap<>();
 /** Transient rendezvous in the current native party. No saved membership or timer. */
 static final class EntranceMeeting {
  final Player bot,human;final ServerWideGroup group;final PlayerBotWorldTravel.Place entrance;final int channel;
  volatile boolean accepted,arrived;
  // The player pressed Summon: this bot follows them now and enters with the party transfer.
  volatile boolean summoned;
  /** When the waiting human went offline (relog). */
  volatile long offlineSince;
  EntranceMeeting(Player bot,Player human,ServerWideGroup group,PlayerBotWorldTravel.Place entrance,int channel){this.bot=bot;this.human=human;this.group=group;this.entrance=entrance;this.channel=channel;}
 }
 static final Map<Integer,EntranceMeeting> MEETINGS=new ConcurrentHashMap<>();
 private static Iterator<EntranceMeeting> meetingCursor=Collections.emptyIterator();
 private static final ArrayBlockingQueue<Runnable> REQUESTS = new ArrayBlockingQueue<>(128);
 private static final ScheduledThreadPoolExecutor SOCIAL = socialExecutor();
 // Delayed invitation follow-ups (up to 120 s) must not hold server shutdown: REQUESTS is cleared by then anyway.
 private static ScheduledThreadPoolExecutor socialExecutor() {
  var executor=new ScheduledThreadPoolExecutor(1,new PriorityThreadFactory("PlayerbotFindGroup", Thread.NORM_PRIORITY));
  executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
  return executor;
 }
 private static volatile boolean stopped;
 private static Future<?> task;
 private static Collection<PlayerBotSession> actors = List.of();
 private static Iterator<PlayerBotSession> cursor = Collections.emptyIterator();
 private static Iterator<Post> postCursor = Collections.emptyIterator();
 private static long nextReport,observed,available,withoutNeed,published,publishedCandidates,joined,alreadyPosted,hireDisabled,bracketFull,tabFull,registrationFailed,gatheringPlanned;
 private static final Map<String,Long> COVERAGE=new TreeMap<>();
 private static final Map<com.aionemu.gameserver.model.Race,Long> NEXT_ANNOUNCEMENT = new EnumMap<>(com.aionemu.gameserver.model.Race.class);

 private static final long ROLE_PATIENCE=45_000;
 private static final class Post {
  volatile long lastJoin=System.currentTimeMillis();
  final Player bot;
  final Tab tab;
  final PlayerBotGroupNeeds.Need need;
  final PlayerBotWorldTravel.Place gathering;
  final long created = System.currentTimeMillis();
  volatile FindGroupEntry entry;
  volatile Player controller;
  volatile long joiningUntil;
  boolean greeted, finished, prepared,goalHelpCounted;
  long refreshedAt;
  volatile boolean started;
  long lastReply, lastAnnouncement;
  volatile boolean assembling,expired;
  // Teleport offer to a human who joined from far away: offers made, current offer, second-chance window.
  volatile int tpOffers,tpSerial;
  volatile long tpGraceUntil;
  // The human who led this field party relogged: their character id and since when the party waits for them.
  volatile int away;
  volatile long awaySince;
  Post(Player bot, Tab tab, PlayerBotGroupNeeds.Need need) { this(bot,tab,need,null); }
  Post(Player bot, Tab tab, PlayerBotGroupNeeds.Need need,PlayerBotWorldTravel.Place gathering) { this.bot=bot; this.tab=tab; this.need=need; this.gathering=gathering; }
 }

 static synchronized void start(Collection<PlayerBotSession> sessions) {
  if (stopped || task != null) return;
  actors = sessions; // ConcurrentHashMap values: a bounded, weakly consistent cursor, never a population lock.
  task = SOCIAL.scheduleWithFixedDelay(PlayerBotFindGroup::pulse, 2, 1, TimeUnit.SECONDS);
  LoggerFactory.getLogger(PlayerBotFindGroup.class).info("Playerbot Find Group social worker started; enabled={}, world={}, findgroup={}, posts per faction/tab={}",PlayerBotConfig.ENABLED,PlayerBotConfig.WORLD_ENABLED,PlayerBotConfig.FIND_GROUP_ENABLED,PlayerBotConfig.FIND_GROUP_POSTS_PER_TAB);
 }

 static void shutdown() {
  stopped=true;
  synchronized (PlayerBotFindGroup.class) { if (task != null) task.cancel(false); }
  REQUESTS.clear();
  PENDING_GOAL_HELP.clear();
  SOCIAL.shutdown();
  MEETINGS.clear();
  boolean interrupted=false;
  for (;;) try { if (SOCIAL.awaitTermination(1, TimeUnit.SECONDS)) break; } catch (InterruptedException e) { interrupted=true; }
  for (var post : POSTS.values()) withdraw(post);
  POSTS.clear();
  if (interrupted) Thread.currentThread().interrupt();
 }

 private static boolean enabled() { return !stopped && PlayerBotConfig.ENABLED && PlayerBotConfig.WORLD_ENABLED && PlayerBotConfig.FIND_GROUP_ENABLED; }
 private static boolean resident(Player bot) {
  var session=PlayerBotService.getInstance().currentActor(bot);
  return session != null && session.world() && !bot.isDead() && bot.isSpawned();
 }
 private static boolean free(Player bot) {
  var session=PlayerBotService.getInstance().currentActor(bot);
  return session != null && session.autonomous() && !PlayerBotRoadDuty.assigned(bot) && resident(bot) && !bot.isInTeam() && !bot.isTrading() && bot.getFlightPath()==null;
 }
 private static boolean forming(Post post) {
  var team=post.bot.getPlayerGroup();
  return post.tab!=Tab.APPLY && !post.expired && !post.finished && team!=null && !team.isFull() && team.size()<post.need.members()
   && (post.controller==null ? team.isLeader(post.bot) : post.controller.isOnline() && PlayerBotTeams.same(post.bot,post.controller)
    && leadsFor(team.getLeaderObject(),post));
 }
 /** The party leader admits recruits for this post: its poster, its human, or a companion the human gave the lead. */
 private static boolean leadsFor(Player leader,Post post) {
  if(leader==null)return false;
  if(leader==post.bot || leader==post.controller)return true;
  var actor=leader.isWorldBot()?PlayerBotService.getInstance().currentActor(leader):null;
  return actor!=null && actor.owner()==post.controller;
 }
 /** An autonomous poster forms its party between its own fights. A human-led party keeps recruiting through its fights
  * and deaths (owner 8 Nov: once the lead passed to the human and a fight began, no more bots came; a player's aggro
  * list only clears at full HP). Only the native inviter, the party leader, has to be alive. */
 private static boolean posterReady(Post post,Player leader) {
  if(post.controller==null)return socialIdle(leader);
  var team=leader.getPlayerGroup();var inviter=team==null?leader:team.getLeaderObject();
  return inviterReady(inviter!=null && inviter.isSpawned(),inviter!=null && inviter.isDead());
 }
 static boolean inviterReady(boolean spawned,boolean dead){return spawned && !dead;}
 /** A human-led party outlasts its poster's death or loading screen: the poster revives with the party it posted. */
 private static boolean heldByHuman(Post post) {
  var session=PlayerBotService.getInstance().currentActor(post.bot);var human=post.controller;
  return heldThrough(session!=null && session.world(),human!=null && human.isOnline(),human!=null && PlayerBotTeams.same(post.bot,human))
   || session!=null && session.world() && post.awaySince>0;
 }
 static boolean heldThrough(boolean actorInWorld,boolean humanOnline,boolean sameParty){return actorInWorld && humanOnline && sameParty;}

 private static void pulse() {
  try {
   for (int i=0; i<8; i++) { var request=REQUESTS.poll(); if (request==null) break; try { request.run(); } catch (RuntimeException e) { LoggerFactory.getLogger(PlayerBotFindGroup.class).warn("Playerbot Find Group request failed",e); } }
   // Even expired/departed entries are retired incrementally. No full roster work
   // runs in chat packets, native team locks, combat or companion scheduling.
   if (!postCursor.hasNext()) postCursor=POSTS.values().iterator();
   for (int i=0; i<16 && postCursor.hasNext(); i++) maintain(postCursor.next());
   if(!meetingCursor.hasNext())meetingCursor=MEETINGS.values().iterator();
   for(int i=0;i<16 && meetingCursor.hasNext();i++)maintainMeeting(meetingCursor.next());
   if (!enabled()) { report(); return; }
   if (!cursor.hasNext()) cursor=actors.iterator();
   plansThisPulse=0;
   for (int i=0; i<16 && cursor.hasNext(); i++) {
    var session=cursor.next(); var bot=session.bot();
    observed++;
    if (!PlayerBotService.getInstance().current(session) || !free(bot)) continue;
    available++;
    COVERAGE.merge(bot.getRace()+"/"+bracket(bot.getLevel()),1L,Long::sum);
    if(tryJoin(bot))continue;
    if(POSTS.containsKey(bot.getObjectId())){alreadyPosted++;continue;}
    publish(bot);
   }
   announce();
   if (enabled()) offers(System.currentTimeMillis());
   report();
  } catch (RuntimeException e) { LoggerFactory.getLogger(PlayerBotFindGroup.class).warn("Playerbot Find Group maintenance failed; normal bot AI continues",e); }
 }

 /** Use the actual subscribed LFG channel, with one offer per faction every 20 s.
  * Staggered renewal avoids a population-sized wall of chat at login or regroup. */
 private static void announce() {
  long now=System.currentTimeMillis();
  for(var race:List.of(com.aionemu.gameserver.model.Race.ELYOS,com.aionemu.gameserver.model.Race.ASMODIANS)) {
   if(now<NEXT_ANNOUNCEMENT.getOrDefault(race,0L))continue;
   // Dungeon runs get two of every three LFG chat announcements.
   boolean dungeonTurn=(now/20000)%3!=0;
   var post=POSTS.values().stream().filter(p->announcementDue(p,now) && p.bot.getRace()==race)
    .min(Comparator.comparing((Post p)->dungeonTurn && p.tab!=Tab.INSTANCE).thenComparingLong(p->p.lastAnnouncement).thenComparingLong(p->p.created)).orElse(null);
   if(post==null)continue;
   if(ChatServer.getInstance().sendBotFindGroup(post.bot,announcement(post.entry))) {
    post.lastAnnouncement=now;
    NEXT_ANNOUNCEMENT.put(race,now+20000);
   }
  }
 }

 private static final Map<com.aionemu.gameserver.model.Race,Set<Integer>> WANTED = new ConcurrentHashMap<>();
 private static final Map<com.aionemu.gameserver.model.Race,Long> WANTED_AT = new ConcurrentHashMap<>();
 /** Dungeons whose level range fits an online player of this faction (refreshed every 30 s). */
 private static Set<Integer> wantedDungeons(com.aionemu.gameserver.model.Race race,long now) {
  if (now-WANTED_AT.getOrDefault(race,0L)<30_000) return WANTED.getOrDefault(race,Set.of());
  var masks=new HashSet<Integer>();var data=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP;
  var levels=new HashSet<Integer>();
  com.aionemu.gameserver.world.World.getInstance().forEachPlayer(p->{if(!p.isPlayerBot() && p.isOnline() && p.getRace()==race)levels.add((int)p.getLevel());});
  for (int mask : data.getRecruitableInstanceMaskIds()) {
   var group=data.getTemplateByInstanceMaskId(mask);
   if (group!=null && levels.stream().anyMatch(l->PlayerBotGroupNeeds.level(l,group.getMinLvl(),group.getMaxLvl()))) masks.add(mask);
  }
  WANTED.put(race,Set.copyOf(masks));WANTED_AT.put(race,now);
  return masks;
 }
 private static int instanceLimit(){return Math.clamp(PlayerBotConfig.FIND_GROUP_INSTANCE_POSTS,1,60);}
 private static boolean announcementDue(Post post,long now) {
  return post.entry!=null && visible(post.entry) && now-post.created>=10000
   && now-post.lastAnnouncement>=180000+Math.floorMod(post.bot.getObjectId(),120)*1000L;
 }

 /* The native client prefixes its own LFG posts with [cmd:<name>;<code>]. The
  * code captured on 20 October rendered as an empty "<>" link when reused with a
  * bot's name (tested in game), so it is bound to the original poster. Bot posts
  * stay plain text until the client's code format is decoded. */

 static String announcement(FindGroupEntry entry) {
  if(entry instanceof GroupRecruitment r)return r.getMessage()+" ("+r.getSize()+"/6)";
  if(entry instanceof GroupApplication a)return a.getMessage();
  if(entry instanceof ServerWideGroup g)return g.getMessage()+" ("+g.getMembers().size()+"/"+g.getMinMembers()+") Whisper for invite.";
  throw new IllegalArgumentException("Unknown Find Group entry");
 }

 /** Client limit for a Find Group text: game.pak party_match_offer/seek/instance_offer_dialog.xml, desc max_length="50". */
 static final int MAX_POST=50;
 /** Every bot post goes through here: whole words, never over the client limit. */
 static String fit(String text) {
  text=text.strip();
  if (text.length()<=MAX_POST) return text;
  int cut=text.lastIndexOf(' ',MAX_POST-3);
  return (cut>MAX_POST/2 ? text.substring(0,cut) : text.substring(0,MAX_POST-3)).strip()+"...";
 }
 private static void report() {
  long now=System.currentTimeMillis();if(now<nextReport)return;nextReport=now+30000;
  long recruit=0,apply=0,instance=0,gathering=0,parties=0,members=0;
  var liveCoverage=new TreeMap<String,Long>();
  for(var post:POSTS.values()) {
   if(post.entry!=null && visible(post.entry))liveCoverage.merge(post.bot.getRace()+"/"+bracket(post.bot.getLevel())+"/"+post.tab,1L,Long::sum);
   if(post.entry!=null && visible(post.entry))switch(post.tab){case RECRUIT->recruit++;case APPLY->apply++;case INSTANCE->instance++;}
   else if(post.gathering!=null && post.controller==null)gathering++;
   if(post.bot.getPlayerGroup()!=null && post.bot.getPlayerGroup().isLeader(post.bot)){parties++;members+=post.bot.getPlayerGroup().size();}
  }
  LoggerFactory.getLogger(PlayerBotFindGroup.class).info("Playerbot Find Group: enabled={}; interval visits observed={}, available={}, without need={}, newly published={}, joined={}; available publication successes={}, already posted={}, hiring disabled={}, bracket full={}, tab full={}, registration failed={}, gathering planned={}; live recruit/apply/instance={}/{}/{}; gathering={}, parties={}, party members={}; live visible race/bracket/tab coverage={}; available visit race/bracket coverage={} (brackets 1-20/21-35/36-50/51-60/61-65); chat queue={}",enabled(),observed,available,withoutNeed,published,joined,publishedCandidates,alreadyPosted,hireDisabled,bracketFull,tabFull,registrationFailed,gatheringPlanned,recruit,apply,instance,gathering,parties,members,liveCoverage,COVERAGE,REQUESTS.size());
  observed=available=withoutNeed=published=publishedCandidates=joined=alreadyPosted=hireDisabled=bracketFull=tabFull=registrationFailed=gatheringPlanned=0;COVERAGE.clear();
 }

 private static void publish(Player bot) {
  PlayerBotGroupNeeds.Need need=null;
  PlayerBotWorldTravel.Place gathering=null;
  Tab tab;
  // Different people prefer to lead or seek an invitation. A character makes
  // one registration, rather than cloning its name across all three tabs.
  long now=System.currentTimeMillis();
  if (plansThisPulse<2 && now>=NEXT_PLAN.getOrDefault(bot.getObjectId(),0L)
   && (PlayerBotConfig.FIND_GROUP_REMOTE_INSTANCES || PlayerBotWorldActivity.occupied(bot))
   && !wantedDungeons(bot.getRace(),now).isEmpty()
   && POSTS.values().stream().filter(p->p.tab==Tab.INSTANCE && p.bot.getRace()==bot.getRace()).count()<instanceLimit()) {
   plansThisPulse++;
   // Dungeon planning reads portal data: at most once per bot every few minutes.
   NEXT_PLAN.put(bot.getObjectId(),now+60_000+Math.floorMod(bot.getObjectId(),30)*1000L);
   var race=bot.getRace();
   var plan=PlayerBotGroupNeeds.instancePlan(bot,wantedDungeons(race,now),mask->(int)POSTS.values().stream()
    .filter(p->p.tab==Tab.INSTANCE && !p.expired && p.bot.getRace()==race && p.need.mask()==mask).count());
   if(plan!=null){need=plan.need();gathering=plan.entrance();}
  }
  if (need != null) tab=Tab.INSTANCE;
  else {
   // A genuinely hard target recruits help; otherwise the bot may offer its own help.
   need=PlayerBotGroupNeeds.field(bot); tab=Tab.RECRUIT;
   if(need==null){need=PlayerBotGroupNeeds.helper(bot);tab=Tab.APPLY;}
  }
  if (need==null) { withoutNeed++; return; }
  final Tab target=tab;
  if (target==Tab.APPLY && !PlayerBotEconomyConfig.HIRING_ENABLED) {hireDisabled++;return;}
  if (target==Tab.APPLY && !bracketRoom(bot)) {bracketFull++;return;}
  long count=POSTS.values().stream().filter(p->p.entry!=null && p.tab==target && p.bot.getRace()==bot.getRace()).count();
  if (count>=(target==Tab.INSTANCE?instanceLimit():Math.clamp(PlayerBotConfig.FIND_GROUP_POSTS_PER_TAB,1,30))) {tabFull++;return;}
  var post=new Post(bot,tab,need,gathering);
  POSTS.put(bot.getObjectId(),post);
  register(post);
  if(post.entry==null){if(post.gathering!=null)gatheringPlanned++;else registrationFailed++;}else publishedCandidates++;
 }

 private static void register(Post post) {
  var bot=post.bot;var tab=post.tab;var need=post.need;
  if(post.expired || post.entry!=null || post.tab==Tab.INSTANCE && post.controller==null && (post.gathering==null
   ? !PlayerBotGroupNeeds.applicant(bot,need)
   : !PlayerBotGroupNeeds.entrance(bot,com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(need.mask()))))return;
  String role=role(bot);
  String message=switch (tab) {
   // The Find Group window rejects a whole list when one entry's text is too
   // long (bot posts of 80+ characters emptied Apply and Instance). Keep each
   // post short; the details are in the whispered guidance.
   case RECRUIT, INSTANCE -> text(post);
   case APPLY -> PlayerBotHiring.offerText(bot);
  };
  message=fit(message);
  post.entry=switch (tab) {
   case RECRUIT -> new GroupRecruitment(bot.getCurrentTeam()==null?bot:bot.getCurrentTeam(),message,0);
   case APPLY -> new GroupApplication(bot,message,0,bot.getPlayerClass().getClassId(),bot.getLevel());
   case INSTANCE -> new ServerWideGroup(bot,need.mask(),need.members(),message);
  };
  if (!FindGroupService.getInstance().registerBotEntry(post.entry) || !(free(bot) || forming(post))) { withdraw(post); }
  else {
   published++;
   if(post.need.gear() && !post.goalHelpCounted){post.goalHelpCounted=true;GOAL_HELP_OPENED.incrementAndGet();}
  }
 }

 private static String variant(Player bot,String first,String second) { return (bot.getObjectId()&2)==0 ? first : second; }
 /** Reserve human space rather than filling every native party with bots. */
 static int botSeats(boolean instance,int capacity) { return Math.max(1,Math.min(instance?5:3,capacity-1)); }
 static int seatLimit(boolean humanJoined,boolean instance,int capacity) { return humanJoined?Math.min(6,capacity):botSeats(instance,capacity); }
 static boolean roleFits(PlayerBotRules.Role role,long tanks,long healers) {
  return (role!=PlayerBotRules.Role.TANK || tanks==0) && (role!=PlayerBotRules.Role.HEALER || healers==0);
 }
 static boolean partyRoleFits(PlayerBotRules.Role role,long tanks,long healers,int count,int seats) {
  if(!roleFits(role,tanks,healers) || count>=seats)return false;
  long missing=(tanks==0 && role!=PlayerBotRules.Role.TANK?1:0)+(healers==0 && role!=PlayerBotRules.Role.HEALER?1:0);
  return missing<=seats-count-1+(seats==2?1:0);
 }
 private static Post groupPost(Player bot) {
  var team=bot.getPlayerGroup();
  if(team==null)return null;
  var post=POSTS.get(team.getLeaderObject().getObjectId());
  return post!=null && !post.expired && post.controller==null && team.getMembers().stream().allMatch(Player::isWorldBot) ? post : null;
 }
 static boolean cooperative(Player bot) { return groupPost(bot)!=null; }
 static boolean committed(Player bot) {
  var post=POSTS.get(bot.getObjectId());
  return enabled() && (MEETINGS.containsKey(bot.getObjectId()) || groupPost(bot)!=null || post!=null && !post.expired && post.controller==null && post.gathering!=null);
 }

 /** Called only by the actor's normal decision while native actions/support are safe. */
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation) {
  if(!enabled() || !session.autonomous())return false;
  var bot=session.bot();var post=groupPost(bot);
  if(post!=null && post.bot!=bot) {
   var leader=post.bot;
   if(!leader.isSpawned() || leader.isDead() || leader.getWorldId()!=bot.getWorldId() || leader.getInstanceId()!=bot.getInstanceId())return true;
   // Companion formation/follow is keyed to a human owner's roster: for a bot
   // leader every member resolved to the same front slot and inherited the
   // companion catch-up speed. Keep members in a loose column behind the leader.
   int index=Math.max(1,leader.getPlayerGroup().getMembers().indexOf(bot));
   double heading=leader.getHeading()*Math.PI/60,back=-(2.5+1.5*((index-1)/2)),side=(index%2==0?-1:1)*1.8;
   float x=leader.getX()+(float)(Math.cos(heading)*back-Math.sin(heading)*side),y=leader.getY()+(float)(Math.sin(heading)*back+Math.cos(heading)*side);
   if(PositionUtil.getDistance(bot,leader)>6 || Math.hypot(bot.getX()-x,bot.getY()-y)>3)navigation.move(x,y,leader.getZ());
   else navigation.stop();
   return true;
  }
  if(post==null)post=POSTS.get(bot.getObjectId());
  if(post==null || post.expired || post.controller!=null || post.gathering==null || post.started)return false;
  var destination=post.gathering;
  if(bot.getWorldId()!=destination.map()) { post.expired=true;return false; }
  if(PositionUtil.getDistance(bot,destination.x(),destination.y(),destination.z())>8) {
   // Planned entrances are on this map. Use the native ground navigator
   // directly; no transport graph or cross-map relocation is needed.
   navigation.move(destination.x(),destination.y(),destination.z());
   if(navigation.routeFailed())post.expired=true;
  } else navigation.stop();
  return true;
 }

 private static boolean socialIdle(Player bot) {
  return resident(bot) && !bot.isCasting() && !bot.isTrading() && !bot.isLooting() && bot.getFlightPath()==null
   && !bot.isFlying() && !bot.getController().isInCombat() && bot.getAggroList().stream().findAny().isEmpty()
   && !bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ITEM_USE)
   && !bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ACTION_ITEM_NPC)
   && (bot.getInteractionTask()==null || !bot.getInteractionTask().isInProgress());
 }

 static boolean canJoin(Player leader,Player member) {
  var post=POSTS.get(leader.getObjectId());var own=POSTS.get(member.getObjectId());
  if(!enabled() || post==null || post.expired || post.tab==Tab.APPLY
   || leader==member || !free(member) || !(free(leader) || forming(post)) || !posterReady(post,leader) || !socialIdle(member)
   || leader.getRace()!=member.getRace() || post.controller==null && (leader.getWorldId()!=member.getWorldId() || leader.getInstanceId()!=member.getInstanceId()
    || PositionUtil.getDistance(leader,member)>(post.need.instance()?900:250))
   || !post.need.instance() && leader.getWorldId()!=post.need.map() || !PlayerBotGroupNeeds.unfinished(leader,post.need)
   || own!=null && own.gathering!=null || !PlayerBotGroupNeeds.useful(member,post.need)
   || leader.getBlockList().contains(member.getObjectId()) || member.getBlockList().contains(leader.getObjectId())
   || member.getPlayerSettings().isInDeniedStatus(DeniedStatus.GROUP))return false;
  var team=leader.getPlayerGroup();var members=team==null?List.of(leader):team.getMembers();
  // Reserve a human seat only while no human has joined. After that, count the
  // human's role and fill the actual run, through its current native leader.
  boolean human=members.stream().anyMatch(p->!p.isWorldBot());
  if(post.controller==null && human && !post.need.instance())return false;
  int seats=seatLimit(post.controller!=null || human,post.need.instance(),post.need.members());
  if(members.size()>=seats)return false;
  long tanks=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.TANK).count();
  long healers=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.HEALER).count();
  // A role is a preference, not a lock: a human's party must not wait forever for
  // a class nobody free can fill. Only the human-led party may stop waiting.
  if((post.controller!=null || human) && System.currentTimeMillis()-post.lastJoin>ROLE_PATIENCE)return true;
  return partyRoleFits(PlayerBotRules.roleFor(member.getPlayerClass()),tanks,healers,members.size(),seats);
 }

 /** Current human controller for a mixed recruitment; native membership stays authoritative. */
 static Player recruitmentController(Player bot) {
  var post=POSTS.get(bot.getObjectId());
  return post!=null && forming(post) ? post.controller : null;
 }

 private static boolean tryJoin(Player bot) {
  var own=POSTS.get(bot.getObjectId());
  if(own!=null && (own.gathering!=null || System.currentTimeMillis()-own.created<10000))return false;
  // A finite registration set, not a population scan; one native invitation per
  // candidate. Prefer instance work and older recruiters to form stable groups.
  var offer=POSTS.values().stream().filter(p->p.bot!=bot && p.tab!=Tab.APPLY && !p.expired
   && p.bot.getRace()==bot.getRace() && (p.controller!=null || own==null || p.created<own.created) && canJoin(p.bot,bot))
   .min(Comparator.comparing((Post p)->p.controller==null).thenComparing(p->!p.need.instance()).thenComparingLong(p->p.created)).orElse(null);
  if(offer==null)return false;
  offer.assembling=true;
  try {
   if(!PlayerBotService.getInstance().joinFindGroupBots(offer.bot,bot))return false;
   if(own!=null){withdraw(own);POSTS.remove(bot.getObjectId(),own);}
   offer.joiningUntil=0;offer.lastJoin=System.currentTimeMillis();joined++;
   register(offer);
   var human=offer.controller!=null?offer.controller:offer.need.instance()?humanIn(offer.bot):null;
   if(human!=null)arrived(human,bot);
   return true;
  }finally{offer.assembling=false;}
 }

 private static Player humanIn(Player bot) {
  for(var member:PlayerBotTeams.members(bot))if(!member.isWorldBot())return member;
  return null;
 }

 private static void retire(Post post) {
  post.expired=true;withdraw(post);
  var scope=FREE_HELP.get(post.bot.getObjectId());
  if(scope!=null && scope.post==post){scope.ended=true;for(int id:scope.members)FREE_HELP.remove(id,scope);}
  var team=post.bot.getPlayerGroup();
  if(post.controller==null && team!=null && team.getLeaderObject()==post.bot && team.getMembers().stream().allMatch(Player::isWorldBot)) {
   for(var member:team.getMembers())if(member!=post.bot && !PlayerBotService.getInstance().leaveFindGroup(member))return;
   if(!PlayerBotService.getInstance().leaveFindGroup(post.bot))return;
  }
  POSTS.remove(post.bot.getObjectId(),post);
 }

 private static String role(Player bot) { return switch (PlayerBotRules.roleFor(bot.getPlayerClass())) { case TANK -> "tank"; case HEALER -> "healer"; case SUPPORT -> "support"; default -> "DPS"; }; }

 private static void maintain(Post post) {
  if (POSTS.get(post.bot.getObjectId())!=post) return;
  if (!enabled() || post.expired || !resident(post.bot) && !runUnderway(post) && !heldByHuman(post)) { retire(post); return; }
  if (post.controller != null) {
   if (!PlayerBotTeams.same(post.controller,post.bot) || !post.controller.isOnline()) {
    withdraw(post);
    // Disconnected, still a member: the release (waitsFor) or the relog (relogged) takes the post from here.
    if(!post.controller.isOnline() && member(post,post.controller.getObjectId()))return;
    POSTS.remove(post.bot.getObjectId(),post); return;
   }
   if (!post.greeted) {
    post.greeted=true;
    say(post,post.controller,PlayerBotGroupNeeds.helping(post.need)
     ? "Hi "+post.controller.getName()+"! Thanks for inviting me. Tell me what you need help with; I'll follow your lead. You can also ask me to trade, follow, stay, attack your target or how I'm doing."
     : "Hi "+post.controller.getName()+"! Thanks for joining for "+post.need.title()+". Want to start, wait for more, or hear how you can help?"+(forming(post)?" I'll keep recruiting for our missing roles until the party is full; you can ask for lead.":" I'll follow your lead.")
      +" You can also ask me to trade, follow, stay or attack your target.",true);
   }
   if (!post.finished && !requestOpen(post)) {
    post.finished=true;
    say(post,post.controller,"Thanks, that's what I needed! Our reciprocal help ends with this objective. I'll leave safely after the current fight.",true);
    withdraw(post);
   }
   if (forming(post) && !post.finished) { register(post); refresh(post); }
   else withdraw(post);
   var team=post.controller.getCurrentTeam();
   if (post.need.instance() && !post.prepared && team!=null && team.isLeader(post.controller)
    && team.getMembers().size()>=Math.min(post.need.members(),6)) {
    post.prepared=true;
    FindGroupService.getInstance().prepareEntry(post.controller,new ServerWideGroup(post.controller,post.need.mask(),post.need.members(),fit(post.need.title())));
   }
   return;
  }
  if(post.assembling)return;
  if(post.awaySince>0) {
   // Reserved for the relogging human: no listing; after the wait the bots leave the party and the post ends.
   withdraw(post);
   if(relogWaitOver(post.awaySince,System.currentTimeMillis()) && leaveParty(post)){post.away=0;post.awaySince=0;retire(post);}
   return;
  }
  if(MEETINGS.containsKey(post.bot.getObjectId())){refresh(post);return;}
  if (post.bot.isInTeam() && System.currentTimeMillis()<post.joiningUntil) return;
  refresh(post);
  if (!(free(post.bot) || forming(post)) || !PlayerBotGroupNeeds.unfinished(post.bot,post.need) || System.currentTimeMillis()-post.created>900000
   || post.need.instance() && !PlayerBotGroupNeeds.applicant(post.bot,post.need)) { retire(post); return; }
  register(post);
 }

 private static final int[] BRACKETS={20,35,50,60,65};
 private static int bracket(int level){for(int i=0;i<BRACKETS.length;i++)if(level<=BRACKETS[i])return i;return BRACKETS.length-1;}
 /**
  * Help offers are shared across level brackets (1-20, 21-35, 36-50, 51-60,
  * 61-65), so a low-level player also finds helpers, and each bracket keeps
  * its first two seats for a tank and a healer before more damage dealers.
  */
 private static boolean bracketRoom(Player bot) {
  int b=bracket(bot.getLevel());
  var same=POSTS.values().stream().filter(p->p.tab==Tab.APPLY && p.entry!=null && !p.expired && p.bot.getRace()==bot.getRace() && bracket(p.bot.getLevel())==b).toList();
  int cap=Math.clamp(PlayerBotConfig.FIND_GROUP_POSTS_PER_TAB,1,30);
  int share=bracketShare(bot.getLevel(),cap);
  if (same.size()>=share) return false;
  var role=PlayerBotRules.roleFor(bot.getPlayerClass());
  boolean tank=same.stream().anyMatch(p->PlayerBotRules.roleFor(p.bot.getPlayerClass())==PlayerBotRules.Role.TANK);
  boolean healer=same.stream().anyMatch(p->PlayerBotRules.roleFor(p.bot.getPlayerClass())==PlayerBotRules.Role.HEALER);
  int reserved=(tank?0:1)+(healer?0:1);
  if (role==PlayerBotRules.Role.TANK) return !tank;
  if (role==PlayerBotRules.Role.HEALER) return !healer;
  // Never leave a bracket invisible while waiting for classes which do not
  // exist below Ascension or whose free actors are currently unavailable.
  // Reserve one slot where there is room, without reserving the entire share.
  return same.size()<Math.max(1,share-Math.min(reserved,share-1));
 }
 static int bracketShare(int level,int cap) {
  int b=bracket(level);
  return cap/BRACKETS.length+(b<cap%BRACKETS.length?1:0);
 }

 /** Post text for Recruit/Instance: where it is and what is still missing (client limit 50). */
 private static String text(Post post) {
  var bot=post.bot;var need=post.need;
  var team=bot.getCurrentTeam();var members=team==null?List.of(bot):team.getMembers();
  long tanks=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.TANK).count();
  long healers=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.HEALER).count();
  String missing=tanks==0?"tank":healers==0?"heal":"dps";
  String count=members.size()+"/"+Math.min(6,need.members());
  // Put live recruitment status first so a long dungeon/map title cannot
  // truncate the count or missing role at the native 50-character limit.
  if (post.tab==Tab.INSTANCE)
   return fit(count+" need "+missing+": "+need.title()+" "+(need.quest()>0?"quest":bot.getLevel()>=65?"gear":"XP"));
  var map=com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA.getTemplate(bot.getWorldId());
  return fit(count+" need "+missing+" ["+(map==null?"?":map.getName())+"]: "+PlayerBotGroupNeeds.difficulty(need.npc()));
 }

 /** Keep a live post current: new member counts in its text, and a fresh timestamp every 10 minutes. */
 private static void refresh(Post post) {
  var entry=post.entry;if(entry==null)return;
  long now=System.currentTimeMillis();
  boolean stale=now-post.refreshedAt>600_000;
  if (post.tab!=Tab.APPLY) {
   String text=text(post);
   if (entry instanceof GroupRecruitment r && (stale || !text.equals(r.getMessage()))) { r.setMessage(text); r.updateLastUpdate(); post.refreshedAt=now; }
   else if (entry instanceof ServerWideGroup g && (stale || !text.equals(g.getMessage()))) { g.setMessage(text); g.setLastUpdate(); post.refreshedAt=now; }
  } else if (entry instanceof GroupApplication a) {
   String text=fit(PlayerBotHiring.offerText(post.bot));
   if(stale || !text.equals(a.getMessage())){a.setMessage(text);a.updateLastUpdate();post.refreshedAt=now;}
  }
 }

 /** The leader pressed Enter in the native window: enter through the normal portal rules. */
 public static boolean enterInstance(Player member,ServerWideGroup group) {
  if(member.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.TELEPORT))return false;
  var template=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(group.getInstanceMaskId());
  if (template==null) return false;
  int map=template.getInstanceMapId();
  for (int npc:template.getNpcIds())
   for (var path:com.aionemu.gameserver.dataholders.DataManager.PORTAL2_DATA.getPortalPaths(npc))
    if (PlayerBotGroupNeeds.portal(member,path,map)) {
     // Requirements were checked above, so the native portal never needs an NPC dialog for a refusal.
      try {
       com.aionemu.gameserver.services.teleport.PortalService.port(path,member,null);
       // Human FADE_OUT_BEAM stores a native TELEPORT FutureTask until
       // CM_TELEPORT_ANIMATION_DONE; headless bots spawn synchronously.
       return member.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.TELEPORT)
        || member.getWorldId()==map && member.isInInstance();
      }
     catch (RuntimeException e) { LoggerFactory.getLogger(PlayerBotFindGroup.class).warn("Find Group entry for {} failed",member.getName(),e); return false; }
    }
  com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(member,"You don't meet this dungeon's entry requirements yet (level, quest or item).");
  return false;
 }

 private static final Map<Integer,Map<Integer,Long>> NOTIFIED = new ConcurrentHashMap<>();
 /**
  * "Automatic search when the window is closed": the client asks every 50 s.
  * Tell the player once about each new dungeon group in their level range.
  */
 public static void searchWhileClosed(Player player) {
  if (!enabled() || player.isPlayerBot()) return;
  long now=System.currentTimeMillis();
  var seen=NOTIFIED.computeIfAbsent(player.getObjectId(),k->new ConcurrentHashMap<>());
  seen.values().removeIf(t->now-t>3_600_000);
  if (NOTIFIED.size()>1024) NOTIFIED.keySet().removeIf(id->com.aionemu.gameserver.world.World.getInstance().getPlayer(id)==null);
  var fresh=new ArrayList<String>();
  for (var post:POSTS.values()) {
   if (!(post.entry instanceof ServerWideGroup g) || post.bot.getRace()!=player.getRace() || !visible(g)) continue;
   if (player.getLevel()<g.getMinLevel() || player.getLevel()>g.getMaxLevel() || seen.containsKey(g.getId())) continue;
   seen.put(g.getId(),now);
   if (fresh.size()<2) fresh.add(post.need.title());
  }
  if (!fresh.isEmpty())
   com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(player,"Find Group: "+String.join(" and ",fresh)+(fresh.size()>1?" groups":" group")+" in your level range just opened.");
 }

 private static void withdraw(Post post) {
  var entry=post.entry;
  if (entry!=null) { post.entry=null; FindGroupService.getInstance().removeBotEntry(entry); }
 }

 /** Native registry cleanup only; never acquire an actor/service monitor in a team notification. */
 public static void teamJoined(Player player) { var post=POSTS.get(player.getObjectId()); if (post!=null) { post.joiningUntil=System.currentTimeMillis()+30000; if(post.tab!=Tab.INSTANCE)withdraw(post); } }

 /** Called after native control publication. Conversation is delivered by the social lane. */
 static void controlled(Player bot,Player controller) {
  if(controller==bot) {
   var scope=FREE_HELP.remove(bot.getObjectId());
   if(scope!=null && scope.post.bot==bot)scope.ended=true;
  }
  var post=POSTS.get(bot.getObjectId());
  if (post==null) return;
  // The human left a field help party that stays together: keep the listing open so they can rejoin, and the
  // reattached contract can still be completed with them.
  if (controller==bot) {
   var previous=post.controller;
   if (previous!=null && waitsFor(post,previous)) {
    post.away=previous.getObjectId();post.awaySince=System.currentTimeMillis();post.controller=null;withdraw(post);
    LoggerFactory.getLogger(PlayerBotFindGroup.class).info("Find Group party of {} waits for {} to relog",bot.getName(),previous.getName());
    // Already back on a new login (the bots let go late): take them back now, the login hook has run.
    for(var m:PlayerBotTeams.members(bot))if(m!=previous && m.getObjectId()==previous.getObjectId() && m.isOnline())
     SOCIAL.schedule(()->REQUESTS.offer(()->rejoin(post,m,0)),1,TimeUnit.SECONDS);
    return;
   }
  }
  if (controller==bot && reopens(post)) { post.controller=null; return; }
  if (post.tab==Tab.APPLY || controller==bot || !PlayerBotTeams.same(bot,controller)) withdraw(post);
  if (controller==bot) { POSTS.remove(bot.getObjectId(),post); return; }
  post.controller=controller;
 }

 public static boolean recruiting(Player bot) { var post=POSTS.get(bot.getObjectId()); return enabled() && post!=null && !post.expired && resident(bot) && (post.controller==null || forming(post)); }

 /** A bot invitation was accepted through the real native question window. */
 public static void accepted(Player inviter,Player invited) {
  if (!inviter.isWorldBot() || invited.isPlayerBot() || !PlayerBotTeams.same(inviter,invited)) return;
  var post=POSTS.get(inviter.getObjectId());
  if (post==null) return;
  if(PlayerBotHelpKick.kicked(inviter.getObjectId(),invited.getObjectId())){PlayerGroupService.removePlayer(invited);return;}
  if(post.tab==Tab.APPLY || PlayerBotGroupNeeds.helping(post.need)) {
   // An offering helper cannot obtain unpaid control by inviting the human instead.
   PlayerGroupService.removePlayer(invited);PlayerBotHiring.offer(invited,inviter);return;
  }
  if (!enabled() || !PlayerBotGroupNeeds.unfinished(inviter,post.need) || !PlayerBotGroupNeeds.applicant(invited,post.need)) { PlayerGroupService.removePlayer(invited); return; }
  // A dungeon run says what it is, who stands at the end and what it can pay (owner play test, 8 Nov).
  if(post.need.instance())PlayerBotRunBriefing.joined(inviter,invited,post.need);
  // Applying to a field help post is consent to its objective: accept the tracked contract now (journal, chat stages,
  // reward) instead of asking for a second step in Help requests. A refusal (e.g. another active request) says why.
  if(!post.need.instance() && (post.need.quest()>0 || post.need.npc()>0)) {
   try{PlayerBotHelpObjectives.accept(inviter,invited,post.need);}
   catch(IllegalArgumentException|IllegalStateException refused){com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(invited,"[Help] Not tracked: "+refused.getMessage());}
  }
  // Shows the tracked request, or points to Help requests when it could not be accepted automatically.
  PlayerBotHelpObjectives.joined(inviter,invited);
  var previous=FREE_HELP.get(inviter.getObjectId());
  if(post.controller==null && (previous==null || previous.ended || previous.post!=post || !previous.human.isOnline() || !PlayerBotTeams.same(previous.human,inviter))) {
   if(previous!=null){previous.ended=true;for(int id:previous.members)FREE_HELP.remove(id,previous);}
   var scope=new FreeHelp(post,invited);
   for(var member:PlayerBotTeams.members(inviter))if(member.isWorldBot()){scope.members.add(member.getObjectId());FREE_HELP.put(member.getObjectId(),scope);}
  }
  if (post.controller!=null) {
   REQUESTS.offer(()->say(post,invited,"Welcome, "+invited.getName()+"! We're gathering for "+post.need.title()+". Let me know if you need the details.",true));
  } else REQUESTS.offer(()->offerTeleport(post,invited)); // the human travels to the bots
 }
 /** Intercept the paid helper route before native group membership is changed. */
 public static boolean paidInvitation(Player inviter,Player invited) {
  if(!inviter.isWorldBot() || invited.isPlayerBot())return false;
  var post=POSTS.get(inviter.getObjectId());
  if(post==null || post.tab!=Tab.APPLY && !PlayerBotGroupNeeds.helping(post.need))return false;
  PlayerBotHiring.offer(invited,inviter);return true;
 }

 /**
  * A dungeon party the human leads is committed to the run once inside it: the
  * poster dying or finishing its own quest objective mid-run no longer ends the
  * party's term. The run's final boss (completeRun) or the human leaving does.
  */
 static boolean holdsRun(boolean dungeonPost,boolean humanLeads,boolean sameParty,boolean insideRun,boolean finished) {
  return dungeonPost && humanLeads && sameParty && insideRun && !finished;
 }
 private static boolean runUnderway(Post post) {
  var bot=post.bot;var human=post.controller;
  return holdsRun(post.need.instance(),human!=null && human.isOnline(),PlayerBotTeams.same(bot,human),
   bot.isSpawned() && bot.isInInstance() && bot.getWorldId()==post.need.map(),post.finished);
 }
 private static boolean requestOpen(Post post) {
  if(post.tab==Tab.APPLY)return true;
  if(post.finished || post.expired)return false;
  if(runUnderway(post))return true;
  if(post.need.quest()>0) {
   var state=post.bot.getQuestStateList().getQuestState(post.need.quest());
   return state!=null && (state.getStatus()==com.aionemu.gameserver.questEngine.model.QuestStatus.START
    || state.getStatus()==com.aionemu.gameserver.questEngine.model.QuestStatus.REWARD);
  }
  return true; // Concrete target/run completion closes the scope through the native completion adapters below.
 }
 /** Exact reciprocal request scope, bounded by this six-person native party and its actual completion. */
 /** A bot freed from field help stays with the other bots of its party, which carries on with the objective and
  * can be rejoined through Find Group. Instance runs and paid hires keep their own ending. */
 static boolean reopens(Post post) {
  return post.tab==Tab.RECRUIT && post.entry!=null && !post.expired && !post.finished && !post.need.instance()
   && post.bot.getCurrentTeam()!=null && PlayerBotTeams.members(post.bot).stream().noneMatch(m->!m.isPlayerBot() && m.isOnline());
 }
 static boolean keepsParty(Player bot) {
  var scope=FREE_HELP.get(bot.getObjectId());
  if(scope==null || scope.post.need.instance() || bot.isInInstance() || bot.getCurrentTeam()==null)return false;
  var members=PlayerBotTeams.members(bot);int human=scope.human.getObjectId();
  return keeps(members.stream().anyMatch(m->!m.isPlayerBot() && m.isOnline() && m.getObjectId()!=human),
   // Owner 8 Nov: only an OFFLINE helper (a relog) is waited for. Once the help ended with them online, the bots
   // leave their party (keeping them made a ghost party once world population rebuilt those bots).
   !scope.human.isOnline() && members.stream().anyMatch(m->m.getObjectId()==human),
   members.stream().anyMatch(m->!m.isPlayerBot()),members.stream().anyMatch(m->m!=bot && m.isWorldBot()));
 }
 /** Bots stay together after control ends: for their relogging human (even one bot), or, with no human left, to carry on. */
 static boolean keeps(boolean otherHumanOnline,boolean humanRelogging,boolean anyHuman,boolean otherBot) {
  if(otherHumanOnline)return false;
  if(humanRelogging)return true;
  return !anyHuman && otherBot;
 }
 static final long RELOG_WAIT_MS=300_000;
 private static boolean memberOf(Player bot,int character){return PlayerBotTeams.members(bot).stream().anyMatch(m->m.getObjectId()==character);}
 /** The same character's new, online login in this bot's party, if it relogged. */
 private static Player newLogin(Player bot,Player old) {
  for(var m:PlayerBotTeams.members(bot))if(m!=old && m.getObjectId()==old.getObjectId() && m.isOnline() && !m.isPlayerBot())return m;
  return null;
 }
 /** A dungeon run's bots keep their place while their human relogs: no release for RELOG_WAIT_MS (they keep fighting
  * back in the native settling mode), then the normal ending. The quick-relog handoff gives them to the new login. */
 static boolean holdsForRelog(Player owner,Player bot,long now) {
  if(owner==null || owner.isPlayerBot() || owner.isOnline())return false;
  var scope=FREE_HELP.get(bot.getObjectId());
  if(scope==null || scope.ended || scope.human!=owner)return false;
  if(scope.offlineSince==0)scope.offlineSince=now;
  return holdsRelog(scope.post.need.instance(),bot.isInInstance(),scope.post.finished || scope.post.expired,memberOf(bot,owner.getObjectId()),scope.offlineSince,now);
 }
 static boolean holdsRelog(boolean dungeon,boolean inside,boolean over,boolean stillMember,long since,long now) {
  return dungeon && inside && !over && stillMember && !relogWaitOver(since,now);
 }
 static boolean relogWaitOver(long since,long now){return since>0 && now-since>RELOG_WAIT_MS;}
 private static boolean member(Post post,int character) {
  return PlayerBotTeams.members(post.bot).stream().anyMatch(m->m.getObjectId()==character);
 }
 /** A field help party keeps its disconnected leader's place: still a member, no other human online. */
 private static boolean waitsFor(Post post,Player human) {
  return waits(post.tab==Tab.RECRUIT && !post.need.instance() && !post.bot.isInInstance(),post.expired || post.finished,human.isOnline(),
   post.bot.getCurrentTeam()!=null && member(post,human.getObjectId()),
   PlayerBotTeams.members(post.bot).stream().anyMatch(m->!m.isPlayerBot() && m.isOnline() && m.getObjectId()!=human.getObjectId()));
 }
 static boolean waits(boolean fieldParty,boolean over,boolean online,boolean stillMember,boolean otherHuman) {
  return fieldParty && !over && !online && stillMember && !otherHuman;
 }
 private static boolean leaveParty(Post post) {
  for(var member:PlayerBotTeams.members(post.bot))if(member.isWorldBot() && member!=post.bot && !PlayerBotService.getInstance().leaveFindGroup(member))return false;
  return !post.bot.isInTeam() || PlayerBotService.getInstance().leaveFindGroup(post.bot);
 }
 /** Quick relog, before the bots let go: the same character's new login takes over its help scope (decision lane). */
 static Player relogged(Player owner,Player bot) {
  if(owner==null || owner.isPlayerBot() || owner.isOnline())return null;
  Player back=null;
  for(var m:PlayerBotTeams.members(bot))if(m!=owner && m.getObjectId()==owner.getObjectId() && m.isOnline() && !m.isPlayerBot())back=m;
  if(back==null)return null;
  var scope=FREE_HELP.get(bot.getObjectId());
  if(scope==null || scope.ended || scope.human.getObjectId()!=back.getObjectId())return null;
  if(scope.human==back)return back;
  var fresh=new FreeHelp(scope.post,back);fresh.graceUntil=scope.graceUntil;fresh.members.addAll(scope.members);
  for(int id:scope.members)FREE_HELP.replace(id,scope,fresh);
  scope.ended=true;
  final Player human=back;
  SOCIAL.schedule(()->REQUESTS.offer(()->welcomeBack(fresh.post,human,true)),1,TimeUnit.SECONDS);
  return back;
 }
 /** Slow relog: the bots let go and waited; the returning human gets them back like a fresh application. */
 public static void reconnected(Player human) {
  if(!enabled() || human.isPlayerBot())return;
  for(var post:POSTS.values())if(post.away==human.getObjectId())
   SOCIAL.schedule(()->REQUESTS.offer(()->rejoin(post,human,0)),5,TimeUnit.SECONDS);
 }
 private static void rejoin(Post post,Player human,int attempt) {
  if(POSTS.get(post.bot.getObjectId())!=post || post.away!=human.getObjectId() || post.expired || !human.isOnline())return;
  if(!human.isSpawned()) {
   if(attempt<24)SOCIAL.schedule(()->REQUESTS.offer(()->rejoin(post,human,attempt+1)),5,TimeUnit.SECONDS);
   return;
  }
  post.away=0;post.awaySince=0;
  if(!PlayerBotTeams.same(human,post.bot) || post.finished)return;
  var previous=FREE_HELP.get(post.bot.getObjectId());
  if(previous!=null){previous.ended=true;for(int id:previous.members)FREE_HELP.remove(id,previous);}
  var scope=new FreeHelp(post,human);
  for(var member:PlayerBotTeams.members(post.bot))if(member.isWorldBot()){scope.members.add(member.getObjectId());FREE_HELP.put(member.getObjectId(),scope);}
  post.tpOffers=0;post.greeted=true;
  welcomeBack(post,human,false); // adopt gives the lead back
  offerTeleport(post,human);
 }
 private static void welcomeBack(Post post,Player human,boolean lead) {
  if(!human.isOnline() || !PlayerBotTeams.same(human,post.bot))return;
  var team=human.getPlayerGroup();
  if(lead && team!=null && !team.isLeader(human) && team.getLeaderObject().isPlayerBot())PlayerGroupService.changeLeader(human);
  say(post,human,"Welcome back, "+human.getName()+"! We kept your spot.",true);
  PlayerBotHelpObjectives.joined(post.bot,human);
 }
 static boolean freeHelpControl(Player human,Player bot) {
  if(human==null || human.isPlayerBot() || !human.isOnline() || !PlayerBotTeams.same(human,bot))return false;
  var scope=FREE_HELP.get(bot.getObjectId());
  if(scope==null && bot.getPlayerGroup()!=null) {
   for(var member:bot.getPlayerGroup().getMembers()) {
    var candidate=FREE_HELP.get(member.getObjectId());
    if(candidate!=null && candidate.human==human && !candidate.ended){scope=candidate;break;}
   }
   if(scope!=null){scope.members.add(bot.getObjectId());FREE_HELP.putIfAbsent(bot.getObjectId(),scope);}
  }
  if(scope!=null && scope.human==human && System.currentTimeMillis()<scope.graceUntil && PlayerBotTeams.same(scope.post.bot,human))return true;
  return scope!=null && scope.human==human && !scope.ended && requestOpen(scope.post)
   && (scope.post.controller==null || scope.post.controller==human) && PlayerBotTeams.same(scope.post.bot,human);
 }
 /** This bot posted a request for its own quest, so that quest is the job it is in the party for. */
 static boolean questRequest(Player bot) {
  var scope=FREE_HELP.get(bot.getObjectId());
  return scope!=null && !scope.ended && scope.post.bot==bot && scope.post.need.quest()>0;
 }
 static int requestedQuest(Player bot) {
  var scope=FREE_HELP.get(bot.getObjectId());
  return scope!=null && !scope.ended && scope.post.bot==bot ? scope.post.need.quest() : 0;
 }
 /** The requesting bot handed its quest in with the helper in the party: keep free-help control (and so the party)
  * for the farewell window, so the thanks and the reward land before the bots go their way. */
 static void handedIn(Player requester) {
  var scope=FREE_HELP.get(requester.getObjectId());
  if(scope!=null && scope.post.bot==requester)scope.graceUntil=System.currentTimeMillis()+RUN_FAREWELL_MS;
 }
 /** PlayerBotHelpKick: the requesting bot drops its helper just before the hand-in. If it leads, the native kick
  * ("kicked by"); otherwise it leaves alone and hands in solo. Last-moment checks on the social lane. */
 static void ninja(Player bot,Player human,long delayMs,String line) {
  var scope=FREE_HELP.get(bot.getObjectId());
  if(scope==null || scope.post.bot!=bot || scope.human!=human)return;
  SOCIAL.schedule(()->REQUESTS.offer(()->{
   var group=bot.getPlayerGroup();
   if(group==null || !human.isOnline() || !bot.isSpawned() || bot.isDead() || !PlayerBotTeams.same(bot,human)
    || !PlayerBotHelpObjectives.awaitingHandIn(bot,human))return;
   PlayerBotHelpKick.remember(bot.getObjectId(),human.getObjectId(),System.currentTimeMillis());
   say(scope.post,human,line,true);
   if(group.isLeader(bot))PlayerGroupService.banPlayer(human,bot);
   else PlayerGroupService.removePlayer(bot);
  }),delayMs,TimeUnit.MILLISECONDS);
 }
 /** Native help target death / requesting bot turn-in, never acceptance or a UI counter. */
 public static void helpCompleted(Player requester) {
  var scope=FREE_HELP.get(requester.getObjectId());if(scope!=null && scope.post.bot==requester){scope.ended=true;scope.post.finished=true;}
 }
 /** Existing verified completeRun() adapters close only the exact joined native run. */
 public static void runCompleted(int map,int instance) {
  var finished=Collections.newSetFromMap(new IdentityHashMap<FreeHelp,Boolean>());
  for(var scope:FREE_HELP.values())if(!scope.ended && scope.post.need.instance() && scope.post.need.map()==map
   && scope.post.bot.getWorldId()==map && scope.post.bot.getInstanceId()==instance){
   scope.graceUntil=System.currentTimeMillis()+RUN_FAREWELL_MS;scope.ended=true;scope.post.finished=true;finished.add(scope);
  }
  for(var scope:finished)REQUESTS.offer(()->farewell(scope));
 }
 static final long RUN_FAREWELL_MS=12_000;
 /** Native emotes a pickup group uses after a clear: victory, clap, thank, salute. */
 static final int[] FAREWELL_EMOTES={com.aionemu.gameserver.model.EmotionId.VICTORY.id(),com.aionemu.gameserver.model.EmotionId.CLAP.id(),
  com.aionemu.gameserver.model.EmotionId.THANK.id(),com.aionemu.gameserver.model.EmotionId.SALUTE.id()};
 static int farewellEmote(int bot){return FAREWELL_EMOTES[Math.floorMod(bot,FAREWELL_EMOTES.length)];}
 /** Real players say goodbye before they drop group: one party line from the poster, an emote from every bot. */
 private static void farewell(FreeHelp scope) {
  var human=scope.human;if(!human.isOnline() || !PlayerBotTeams.same(scope.post.bot,human))return;
  say(scope.post,human,variant(scope.post.bot,"Good run, everyone! Thanks for the group.","gg! Thanks for leading, that was a clean run."),true);
  int delay=0;
  for(var member:PlayerBotTeams.members(human)) {
   if(!member.isWorldBot() || member.isDead() || !member.isSpawned())continue;
   int emote=farewellEmote(member.getObjectId());delay+=700;
   SOCIAL.schedule(()->{if(member.isSpawned() && !member.isDead() && PlayerBotTeams.same(member,human))
    com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(member,new com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION(
     member,com.aionemu.gameserver.model.EmotionType.EMOTE,emote,member.getX(),member.getY(),member.getZ(),member.getHeading(),human.getObjectId()),true);},delay,TimeUnit.MILLISECONDS);
  }
 }

 /** Hand the whole bot party to the human as companions; null when the leader cannot be adopted. */
 private static List<Player> adopt(Post post,Player human) {
  var inviter=post.bot;
  if (!PlayerBotTeams.same(inviter,human) || !PlayerBotService.getInstance().controlFindGroupBot(human,inviter)) return null;
  // The bots that already gathered in this party come with it: all of them
  // follow and fight with the human, instead of wandering off on their own.
  var team=inviter.getCurrentTeam();var adopted=new ArrayList<Player>(List.of(inviter));
  if (team!=null) for (var member : List.copyOf(team.getMembers()))
   if (member!=inviter && member!=human && member.isWorldBot()) {
    if (PlayerBotService.getInstance().adoptFindGroupMember(human,member)) adopted.add(member);
    else {
     REQUESTS.offer(()->say(post,human,member.getName()+" can't come along too (companion limit), so they'll head off. Good luck!",true));
     PlayerGroupService.removePlayer(member);
    }
   }
  // The human leads: enters the dungeon or guides the hunt.
  PlayerGroupService.changeLeader(human);
  return adopted;
 }

 public static boolean apply(Player applicant,Player recruiter) {
  var post=POSTS.get(recruiter.getObjectId());
  if (post==null || post.tab!=Tab.INSTANCE || !recruiting(recruiter)) return false;
  if(!REQUESTS.offer(()->invite(post,applicant)))com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(applicant,"Find Group is busy. Press Apply again shortly.");
  return true;
 }
 /** The Recruit tab's native Invite reverses to the bot requester's invitation,
  * preserving its existing party and reciprocal objective instead of selling it. */
 public static boolean requestInvitation(Player human,Player bot) {
  var post=POSTS.get(bot.getObjectId());
  if(post==null || post.tab==Tab.APPLY || PlayerBotGroupNeeds.helping(post.need))return false;
  if(!REQUESTS.offer(()->invite(post,human)))com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(human,"Find Group is busy. Press Invite again shortly.");
  return true;
 }

 /** Resolve by registration identity, including a bot's post after the human takes lead. */
 private static Post entryPost(FindGroupEntry entry) {
  if(entry instanceof GroupRecruitment r && r.getObject() instanceof com.aionemu.gameserver.model.team.TemporaryPlayerTeam<?> team) {
   for(var member:team.getMembers()) {
    var post=POSTS.get(member.getObjectId());
    if(post!=null && post.entry==entry)return post;
   }
   return null;
  }
  Player bot=entry instanceof GroupRecruitment r && r.getObject() instanceof Player p ? p
   : entry instanceof GroupApplication a ? a.getPlayer() : entry instanceof ServerWideGroup g ? g.getRecruiter() : null;
  var post=bot==null?null:POSTS.get(bot.getObjectId());
  return post!=null && post.entry==entry ? post : null;
 }

 public static boolean manages(FindGroupEntry entry) { return entryPost(entry)!=null; }

 /** Concrete help contracts from the same native LFG posts, not another planner. */
 public static List<Map<String,Object>> helpRequests(Player human) {
  record Open(Post post,PlayerBotGroupNeeds.Need need,boolean helpable,boolean here,String name) {}
  var open=new ArrayList<Open>();
  for(var post:POSTS.values()) {
   if(post.bot.getRace()!=human.getRace() || post.expired || post.entry==null || !visible(post.entry)
    || post.need.instance() || PlayerBotGroupNeeds.helping(post.need) || post.need.quest()<=0 && post.need.npc()<=0)continue;
   open.add(new Open(post,post.need,PlayerBotGroupNeeds.applicant(human,post.need),post.need.map()==human.getWorldId(),post.bot.getName()));
  }
  // Requests the player can help with first, then nearby ones. Only the rows shown build their full terms.
  open.sort(Comparator.comparing(Open::helpable).thenComparing(Open::here).reversed().thenComparing(Open::name));
  var rows=new ArrayList<Map<String,Object>>();
  for(var row:open) {
   if(rows.size()>=60)break;
   try{rows.add(PlayerBotHelpObjectives.details(human,row.post().bot,row.need()));}
   catch(IllegalArgumentException stale){/* Native objective changed before the incremental post cleanup. */}
  }
  return rows;
 }
 public static String acceptHelp(Player human,int botId) {
  var post=POSTS.get(botId);
  if(post==null || post.expired || post.entry==null || !visible(post.entry) || post.bot.getRace()!=human.getRace()
   || post.need.instance() || PlayerBotGroupNeeds.helping(post.need) || post.need.quest()<=0 && post.need.npc()<=0
   || !PlayerBotGroupNeeds.applicant(human,post.need) || !PlayerBotGroupNeeds.unfinished(post.bot,post.need))throw new IllegalArgumentException("This LFG need changed or you are not eligible. Refresh its details.");
  PlayerBotHelpObjectives.accept(post.bot,human,post.need);
  return "Help request accepted. Join this bot through the native Find Group invitation; your accepted objective is saved.";
 }

 /** Filter stale bot ads immediately on refresh, even before the incremental cleanup reaches them. */
 public static boolean visible(FindGroupEntry entry) {
  var post=entryPost(entry);
  if(post==null) {
   Player owner=owner(entry);
   return owner==null || !owner.isWorldBot();
  }
  var bot=post.bot;
  return enabled() && !post.expired && (free(bot) || forming(post)) && PlayerBotGroupNeeds.unfinished(bot,post.need)
   && (post.need.instance() || bot.getWorldId()==post.need.map());
 }

 static Intent intent(String message) {
  if (message==null || message.length()>512) return Intent.UNKNOWN;
  String text=message.toLowerCase(Locale.ROOT).replace('\u2019','\'').replaceAll("\\s+"," ").strip();
  if (LEAVE.matcher(text).matches()) return Intent.LEAVE;
  if (text.matches("(?:yes|yeah|yep|sure|ok|okay)[!. ]*")) return Intent.START;
  if (text.matches("(?:no|nope)[!. ]*")) return Intent.WAIT;
  if (text.matches("(?:thanks|thank you|ty|thx)[!. ]*")) return Intent.THANKS;
  if (text.matches("(?:tp|teleport(?: me)?|port me|summon me|bring me(?: there)?)[!.? ]*")) return Intent.TELEPORT;
  if (text.matches("(?:follow(?: me)?|come(?: here| with me)?|with me)[!. ]*")) return Intent.FOLLOW;
  if (text.matches("(?:stay|stay here|wait here|hold here|stop here)[!. ]*")) return Intent.STAY;
  if (TRADE.matcher(text).find()) return Intent.TRADE;
  if (ITEMS.matcher(text).find()) return Intent.ITEMS;
  if (ATTACK.matcher(text).find()) return Intent.ATTACK;
  if (STATUS.matcher(text).find()) return Intent.STATUS;
  if (WAIT.matcher(text).find()) return Intent.WAIT;
  if (text.matches("(?:lead|leader|pass (?:me )?lead|give me lead|let me lead)[?!. ]*")) return Intent.LEAD;
  if (HELP.matcher(text).find()) return Intent.HELP;
  if (NEGATIVE.matcher(text).find()) return Intent.UNKNOWN;
  if (START.matcher(text).find()) return Intent.START;
  if (JOIN.matcher(text).find()) return Intent.JOIN;
  if (text.matches("(?:hi|hey|hello|thanks|thank you)[!. ]*")) return Intent.HELLO;
  return Intent.UNKNOWN;
 }

 public static void heard(Player sender,ChatType type,String message,Player receiver) {
  if (!enabled() || sender==null || sender.isPlayerBot() || message==null || message.length()>512) return;
  if (Pattern.compile("(?i)\\b(legion|guild)\\b").matcher(message).find()) return;
  Post post=null;
  if (type==ChatType.WHISPER && receiver!=null) post=POSTS.get(receiver.getObjectId());
  else if (type==ChatType.GROUP || type==ChatType.GROUP_LEADER) {
   // One spokesman for an unaddressed party line, even if several former
   // applicants are now together. Distant party members can still converse.
   var members=POSTS.values().stream().filter(p->(p.controller!=null || p.tpSerial>0) && PlayerBotTeams.same(sender,p.bot)).sorted(Comparator.comparingInt(p->p.bot.getObjectId())).toList();
   post=members.stream().filter(p->addressed(message,p.bot.getName())).findFirst().orElse(members.isEmpty()?null:members.get(0));
  }
  if (post==null) {
   Player bot=type==ChatType.WHISPER && receiver!=null && receiver.isWorldBot() ? receiver : null;
   if (bot==null && (type==ChatType.GROUP || type==ChatType.GROUP_LEADER) && sender.getCurrentTeam()!=null)
    bot=sender.getCurrentTeam().getMembers().stream().filter(p->p.isWorldBot() && addressed(message,p.getName())).findFirst().orElse(null);
   if (bot==null || !canTalk(bot,sender)) return;
   final Player speaker=bot;boolean party=type!=ChatType.WHISPER;
   REQUESTS.offer(()->{
    long now=System.currentTimeMillis();
    if (now-LAST_TALK.getOrDefault(speaker.getObjectId(),0L)<2500) return;
    LAST_TALK.put(speaker.getObjectId(),now);
    if (LAST_TALK.size()>4096) LAST_TALK.entrySet().removeIf(e->now-e.getValue()>60000);
    Intent action=intent(message.replaceAll("(?i)(?<![\\p{L}\\p{N}_])"+Pattern.quote(speaker.getName())+"(?![\\p{L}\\p{N}_])", " ").replaceFirst("^[ ,:]+", ""));
    if (!talk(speaker,sender,action,party) && (action==Intent.HELLO || action==Intent.HELP || action==Intent.UNKNOWN && !party))
     speak(speaker,sender,"Hi! I can trade, show what items I can spare, follow, stay, attack your target, or tell you how I'm doing. Just ask.",party);
   });
   return;
  }
  if (!canTalk(post.bot,sender) || type==ChatType.WHISPER && sender.isInCustomState(CustomPlayerState.NO_WHISPERS_MODE)) return;
  var selected=post;
  REQUESTS.offer(()->answer(selected,sender,type,message));
 }

 private static boolean addressed(String message,String name) { return Pattern.compile("(?i)(?<![\\p{L}\\p{N}_])"+Pattern.quote(name)+"(?![\\p{L}\\p{N}_])").matcher(message).find(); }
 private static boolean canTalk(Player bot,Player player) {
  return resident(bot) && !player.isPlayerBot() && player.isOnline() && player.isSpawned() && bot.getRace()==player.getRace()
   && !bot.getBlockList().contains(player.getObjectId()) && !player.getBlockList().contains(bot.getObjectId());
 }

 private static void answer(Post post,Player sender,ChatType type,String message) {
  if (!enabled() || POSTS.get(post.bot.getObjectId())!=post || !canTalk(post.bot,sender)) return;
  boolean party=PlayerBotTeams.same(sender,post.bot);
  if (type!=ChatType.WHISPER && !party) return;
  long now=System.currentTimeMillis();
  if (now-post.lastReply<2500) return;
  post.lastReply=now;
  Intent action=intent(message.replaceAll("(?i)(?<![\\p{L}\\p{N}_])"+Pattern.quote(post.bot.getName())+"(?![\\p{L}\\p{N}_])", " ").replaceFirst("^[ ,:]+", ""));
  if (talk(post.bot,sender,action,party)) return;
  if (!party) {
   if (post.controller!=null && !forming(post)) { say(post,sender,"I'm already helping a party. Good luck finding a group!",false); return; }
   if (post.tab==Tab.APPLY || PlayerBotGroupNeeds.helping(post.need)) {
    if(action==Intent.JOIN || action==Intent.START)PlayerBotHiring.offer(sender,post.bot);
    else say(post,sender,"Select me in Find Group and press Invite to see my price.",false);
    return;
   }
   if (action==Intent.JOIN || action==Intent.START) { invite(post,sender); return; }
   say(post,sender,PlayerBotGroupNeeds.guidance(post.bot,post.need)+(post.tab==Tab.APPLY ? " Invite me if your party needs a "+role(post.bot)+"." : " Want an invite?"),false);
   return;
  }
  if (post.controller==null) {
   if (action==Intent.TELEPORT && now<post.tpGraceUntil) offerTeleport(post,sender);
   else if (now<post.tpGraceUntil) say(post,sender,"Say 'tp' and I'll send the teleport window again.",true);
   return;
  }
  switch (action) {
   case TELEPORT -> say(post,sender,"Use the dungeon entry window when the group is ready; we will join through the normal entrance.",true);
   case START -> {
    if (sender!=post.controller) { say(post,sender,"Let's let "+post.controller.getName()+" decide when we start.",true); break; }
    var session=PlayerBotService.getInstance().currentActor(post.bot);
    if (session==null || session.owner()!=sender) break;
    if (post.bot.getController().isInCombat() || post.bot.isCasting() || post.bot.isTrading()) { say(post,sender,"Give me a moment to finish what I'm doing, then let's go.",true); break; }
    session.order(PlayerBotRules.Order.FOLLOW);
    post.started=true;
    if (post.bot.getCurrentTeam()!=null && post.bot.getCurrentTeam().isLeader(post.bot)) PlayerGroupService.changeLeader(sender);
    say(post,sender,"Ready! "+PlayerBotGroupNeeds.guidance(post.bot,post.need),true);
   }
   case WAIT -> {
    if (sender==post.controller) {
     var session=PlayerBotService.getInstance().currentActor(post.bot);
     if (session!=null && session.owner()==sender && !post.bot.getController().isInCombat() && !post.bot.isCasting() && !post.bot.isTrading())
      session.order(PlayerBotRules.Order.PASSIVE);
    }
    say(post,sender,"Sure, take your time. I'll follow while we get ready; tell me when to start.",true);
   }
   case HELP, JOIN -> say(post,sender,PlayerBotGroupNeeds.guidance(post.bot,post.need)+" I'm playing "+role(post.bot)+".",true);
   case LEAD -> {
    if (sender==post.controller && post.bot.getPlayerGroup()!=null && post.bot.getPlayerGroup().isLeader(post.bot)) {
     PlayerGroupService.changeLeader(sender);
     say(post,sender,"You've got lead. I'll follow your call; tell me when we're ready to start.",true);
    }
   }
   case LEAVE -> {
    if (sender!=post.controller) { say(post,sender,"I'll check with "+post.controller.getName()+" before leaving.",true); break; }
    say(post,sender,"Thanks for the group! Good luck out there.",true);
    PlayerGroupService.removePlayer(post.bot); withdraw(post); POSTS.remove(post.bot.getObjectId(),post);
   }
   case HELLO -> say(post,sender,"Hey! Let me know when you're ready or if you need the details.",true);
   case UNKNOWN -> { if (type==ChatType.WHISPER || addressed(message,post.bot.getName())) say(post,sender,"Want to start, wait, or hear how to help with "+post.need.title()+"?",party); }
  }
 }

 private static void invite(Post post,Player player) {
  if(post.tab==Tab.APPLY || PlayerBotGroupNeeds.helping(post.need)){PlayerBotHiring.offer(player,post.bot);return;}
  if (!enabled() || POSTS.get(post.bot.getObjectId())!=post || !canTalk(post.bot,player) || !(free(post.bot) || forming(post))) return;
  if (PlayerBotHelpKick.kicked(post.bot.getObjectId(),player.getObjectId())) { say(post,player,"Nah, I'm good. Find another group :P",false); return; }
  if (player.isInTeam()) { say(post,player,"You're already in a party. Your leader can invite me if there's room.",false); return; }
  if(post.bot.getPlayerGroup()!=null && post.bot.getPlayerGroup().size()>=post.need.members()) { say(post,player,"We've filled the available places for this run. Thanks for asking!",false);return; }
  if (!PlayerBotGroupNeeds.unfinished(post.bot,post.need)) { say(post,player,"I've just finished that objective. Thanks for offering!",false); withdraw(post); return; }
  if (!PlayerBotGroupNeeds.applicant(player,post.need)) { say(post,player,"This run needs levels "+post.need.minLevel()+"-"+post.need.maxLevel()+(post.need.instance()?" and an available, unlocked entrance.":". Let's find a better match for you."),false); return; }
  if (player.getPlayerSettings().isInDeniedStatus(DeniedStatus.GROUP)) { say(post,player,"Your settings are blocking group invitations.",false); return; }
  // Native invitations are allowed during combat. The controller's combat
  // timer is not an invitation restriction, including its post-hit grace.
  say(post,player,"Sure, sending an invite for "+post.need.title()+"."+(post.controller==null?" You can lead when we start.":" We're still gathering the party."),false);
  var team=post.bot.getPlayerGroup();
  PlayerGroupService.inviteToGroup(team==null?post.bot:team.getLeaderObject(),player);
 }

 private static void say(Post post,Player player,String message,boolean party) {
  if (!canTalk(post.bot,player)) return;
  if (party && PlayerBotTeams.same(post.bot,player)) {
   for (var member : post.bot.getCurrentTeam().getMembers()) if (canTalk(post.bot,member))
    com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(member,new SM_MESSAGE(post.bot,message,ChatType.GROUP));
  } else if (!player.isInCustomState(CustomPlayerState.NO_WHISPERS_MODE)) com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(player,new SM_MESSAGE(post.bot,message,ChatType.WHISPER));
 }

 private static final Map<Integer,Long> LAST_TALK = new ConcurrentHashMap<>();
 private static void speak(Player bot,Player human,String message,boolean party) {
  if (!canTalk(bot,human)) return;
  if (party && PlayerBotTeams.same(bot,human)) {
   for (var member : bot.getCurrentTeam().getMembers()) if (canTalk(bot,member))
    com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(member,new SM_MESSAGE(bot,message,ChatType.GROUP));
  } else whisper(bot,human,message);
 }

 /** Everyday requests any Find Group bot understands; false leaves the message to the post conversation. */
 private static boolean talk(Player bot,Player human,Intent action,boolean party) {
  var session=PlayerBotService.getInstance().currentActor(bot);
  if (session==null) return false;
  boolean mine=session.owner()==human && !session.autonomous();
  switch (action) {
   case TRADE -> {
    if (!PositionUtil.isInRange(bot,human,5) || bot.getWorldId()!=human.getWorldId()) { speak(bot,human,"Come within 5 m and say 'trade' again; I'll open a trade.",party); return true; }
    PlayerBotWorldTrade.request(human,bot);
    // The native trade explains a refusal itself (busy, combat, distance).
    if (bot.isTrading()) speak(bot,human,"Trade's open. Offer what you like; lock to see my quote, then confirm or cancel.",party);
    return true;
   }
   case ITEMS -> {
    if (!PositionUtil.isInRange(bot,human,5) || bot.getWorldId()!=human.getWorldId()) { speak(bot,human,"Come within 5 m and ask again; I'll list what I can spare.",party); return true; }
    try { PlayerBotWorldTrade.shop(human,bot.getName()); speak(bot,human,"I've sent you the list of what I can spare.",party); }
    catch (IllegalArgumentException e) { speak(bot,human,e.getMessage(),party); }
    return true;
   }
   case FOLLOW, STAY, ATTACK -> {
    if (!mine) { speak(bot,human,"I'm not in your party as your companion, so I can't take orders yet. Invite me first!",party); return true; }
    try {
     if (action==Intent.ATTACK) { session.attackSelectedTarget(); speak(bot,human,"On it!",party); }
     else { session.command(action==Intent.FOLLOW ? PlayerBotRules.Order.FOLLOW : PlayerBotRules.Order.STAY); speak(bot,human,action==Intent.FOLLOW?"Right behind you.":"I'll hold here.",party); }
    } catch (IllegalArgumentException e) { speak(bot,human,e.getMessage(),party); }
    return true;
   }
   case STATUS -> {
    var map=com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA.getTemplate(bot.getWorldId());
    String where=bot.getWorldId()==human.getWorldId() && bot.getInstanceId()==human.getInstanceId()
     ? "about "+Math.round(PositionUtil.getDistance(bot,human))+" m from you" : "in "+(map==null?"another area":map.getName());
    speak(bot,human,"I'm at "+Math.round(PlayerBotDefense.health(bot))+"% HP and "+Math.round(PlayerBotDefense.mana(bot))+"% MP, "+where+".",party);
    return true;
   }
   case THANKS -> { speak(bot,human,"Anytime!",party); return true; }
   default -> { return false; }
  }
 }

 /**
  * The human joined bots that are elsewhere: offer to take them there (native
  * question window). The bots become companions only once the human is with
  * them; a decline leaves the party and the bots keep recruiting.
  */
 private static void offerTeleport(Post post,Player human) {
  if (!human.isOnline() || human.isDead() || !PlayerBotTeams.same(human,post.bot)) return;
  if(post.need.instance()) {
   var template=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(post.need.mask());
   var place=post.gathering!=null ? post.gathering : template==null ? null : PlayerBotGroupNeeds.entranceFor(human,template);
   if(place==null && template!=null)place=PlayerBotGroupNeeds.entranceFor(post.bot,template);
   if(place==null){say(post,human,"This dungeon has no available entrance for our group. Please choose another run.",true);PlayerGroupService.removePlayer(human);return;}
   int channel=post.bot.getWorldId()==place.map()?post.bot.getInstanceId():human.getWorldId()==place.map()?human.getInstanceId():1;
   var group=new ServerWideGroup(post.bot,post.need.mask(),post.need.members(),fit(post.need.title()));
   for(var member:PlayerBotTeams.members(post.bot))if(member.isWorldBot())MEETINGS.putIfAbsent(member.getObjectId(),new EntranceMeeting(member,human,group,place,channel));
   offerEntrance(MEETINGS.get(post.bot.getObjectId()));return;
  }
  var bot=post.bot;var spot=PlayerBotGroupNeeds.meetingPoint(bot,post.need);
  float x=spot==null?bot.getX():spot[0],y=spot==null?bot.getY():spot[1],z=spot==null?bot.getZ():spot[2];
  if(human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId() && PositionUtil.getDistance(human,x,y,z)<=80) {
   if(adopt(post,human)==null)PlayerGroupService.removePlayer(human);return;
  }
  final int serial=++post.tpSerial;post.tpOffers++;post.tpGraceUntil=0;
  var handler=new com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler<com.aionemu.gameserver.model.gameobjects.Creature>(null) {
   @Override public void acceptRequest(com.aionemu.gameserver.model.gameobjects.Creature requester,Player responder) {
    REQUESTS.offer(()->{
     if(post.tpSerial!=serial || post.expired || !PlayerBotTeams.same(responder,bot))return;
     if(responder.isDead() || responder.getController().isInCombat()){busy(post,responder,serial);return;}
     com.aionemu.gameserver.services.teleport.TeleportService.teleportTo(responder,bot.getWorldId(),bot.getInstanceId(),x,y,z);
     // Physical arrival is checked on the social lane before control publication.
     SOCIAL.schedule(()->REQUESTS.offer(()->{
      if(post.tpSerial==serial && !post.expired && PlayerBotTeams.same(responder,bot) && responder.isSpawned()
       && responder.getWorldId()==bot.getWorldId() && responder.getInstanceId()==bot.getInstanceId() && PositionUtil.getDistance(responder,x,y,z)<=80) {
       if(adopt(post,responder)==null)PlayerGroupService.removePlayer(responder);
      }
     }),3,TimeUnit.SECONDS);
    });
   }
   @Override public void denyRequest(com.aionemu.gameserver.model.gameobjects.Creature requester,Player responder){REQUESTS.offer(()->declined(post,responder,serial));}
  };
  if(!human.getResponseRequester().putRequest(905097,handler)){say(post,human,"Answer your open travel window first; I will keep waiting here.",true);return;}
  com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(human,new com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW(905097,0,0,post.need.title()));
  SOCIAL.schedule(()->REQUESTS.offer(()->{if(post.controller==null && post.tpSerial==serial && post.tpGraceUntil==0 && PlayerBotTeams.same(human,bot)){human.getResponseRequester().remove(905097);declined(post,human,serial);}}),60,TimeUnit.SECONDS);
  say(post,human,"Accept the travel window to meet us at the objective.",true);
 }

 /** The central relocation guard stops automatic recall and another companion's group transfer; only an explicit Summon releases it. */
 public static boolean waitingAtEntrance(PlayerBotSession session) {
  var meeting=MEETINGS.get(session.bot().getObjectId());
  return meeting!=null && !meeting.summoned;
 }
 static boolean releaseFromEntranceWait(PlayerBotSession session) {
  var meeting=MEETINGS.get(session.bot().getObjectId());
  if(meeting==null || meeting.summoned)return false;
  meeting.summoned=true;return true;
 }
 static void restoreEntranceWait(PlayerBotSession session) {
  var meeting=MEETINGS.get(session.bot().getObjectId());
  if(meeting!=null)meeting.summoned=false;
 }
 /** Bots of a dungeon party hold their meeting until this human reaches the entrance and takes control. */
 static boolean awaitingHuman(Player human) {
  return MEETINGS.values().stream().anyMatch(meeting->meeting.human==human && PlayerBotTeams.same(human,meeting.bot));
 }
 /** The dungeon run this human's party already agreed on, if a bot joins it late. */
 static EntranceMeeting partyRun(Player human,Player bot) {
  for(var meeting:MEETINGS.values())if(meeting.human==human && meeting.bot!=bot && PlayerBotTeams.same(human,meeting.bot))return meeting;
  return null;
 }
 static EntranceMeeting joinRun(EntranceMeeting run,Player bot) {
  var meeting=new EntranceMeeting(bot,run.human,run.group,run.entrance,run.channel);
  meeting.accepted=run.accepted;meeting.arrived=run.arrived;
  return meeting;
 }
 private static boolean atEntrance(EntranceMeeting meeting) {
  var human=meeting.human;var place=meeting.entrance;
  return human.isOnline() && human.isSpawned() && !human.isDead() && human.getWorldId()==place.map() && human.getInstanceId()==meeting.channel
   && PositionUtil.getDistance(human,place.x(),place.y(),place.z())<=80;
 }
 private static void offerEntrance(EntranceMeeting meeting) {
  if(meeting==null)return;
  var human=meeting.human;
  if(atEntrance(meeting)){meeting.accepted=true;return;}
  var handler=new com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler<com.aionemu.gameserver.model.gameobjects.Creature>(null) {
   @Override public void acceptRequest(com.aionemu.gameserver.model.gameobjects.Creature requester,Player responder){REQUESTS.offer(()->{
    if(MEETINGS.get(meeting.bot.getObjectId())!=meeting || !PlayerBotTeams.same(responder,meeting.bot) || !responder.isOnline())return;
    if(responder.isDead() || responder.getController().isInCombat()){whisper(meeting.bot,responder,"Travel is unavailable during combat or death. We will wait at our meeting point.");return;}
    for(var other:MEETINGS.values())if(other.human==responder && other.group.getInstanceMaskId()==meeting.group.getInstanceMaskId())other.accepted=true;
    var p=meeting.entrance;
    com.aionemu.gameserver.services.teleport.TeleportService.teleportTo(responder,p.map(),meeting.channel,p.x(),p.y(),p.z());
   });}
   @Override public void denyRequest(com.aionemu.gameserver.model.gameobjects.Creature requester,Player responder){whisper(meeting.bot,responder,"We will wait here. Travel to the entrance to continue, or leave the party to cancel.");}
  };
  if(!human.getResponseRequester().putRequest(905097,handler)){whisper(meeting.bot,human,"Answer your open travel window first. We will keep waiting; travel to the entrance to continue.");return;}
  com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(human,new com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW(905097,0,0,meeting.group.getMessage()+" entrance"));
  whisper(meeting.bot,human,"Accept the travel window to reach the dungeon entrance. We stay here until you arrive, then enter through the normal group window.");
  SOCIAL.schedule(()->REQUESTS.offer(()->{if(MEETINGS.get(meeting.bot.getObjectId())==meeting && !meeting.arrived)human.getResponseRequester().remove(905097);}),60,TimeUnit.SECONDS);
 }
 private static void maintainMeeting(EntranceMeeting meeting) {
  if(MEETINGS.get(meeting.bot.getObjectId())!=meeting)return;
  var human=meeting.human;var bot=meeting.bot;
  // The bots wait for as long as the human stays in the party; leaving is how a player cancels.
  // A relog is not leaving (owner 8 Nov): the wait moves to the new login, or waits for it.
  if(!human.isOnline() && memberOf(bot,human.getObjectId())) {
   var back=newLogin(bot,human);
   if(back!=null) {
    var moved=new EntranceMeeting(bot,back,meeting.group,meeting.entrance,meeting.channel);
    moved.accepted=meeting.accepted;moved.arrived=meeting.arrived;moved.summoned=meeting.summoned;
    if(MEETINGS.replace(bot.getObjectId(),meeting,moved))REQUESTS.offer(()->whisper(bot,back,"Welcome back! We are still waiting at the "+meeting.group.getMessage()+" entrance."));
    return;
   }
   long now=System.currentTimeMillis();
   if(meeting.offlineSince==0)meeting.offlineSince=now;
   if(!relogWaitOver(meeting.offlineSince,now))return;
  }
  if(!human.isOnline() || !PlayerBotTeams.same(human,bot)) {
   LoggerFactory.getLogger(PlayerBotFindGroup.class).info("Find Group entrance wait ended for {}: human {}",bot.getName(),human.isOnline()?"left the party":"went offline");
   if(MEETINGS.remove(bot.getObjectId(),meeting) && PlayerBotTeams.same(human,bot)) {
    var post=POSTS.get(bot.getObjectId());
    if(post!=null && post.controller==null)PlayerGroupService.removePlayer(human);else PlayerGroupService.removePlayer(bot);
   }
   return;
  }
  if(bot.isDead() || human.isDead())return;
  if(atEntrance(meeting)) {
   // Walking to the exact entrance is an explicit travel choice too. Merely
   // accepting a window or beginning a loading screen never publishes control.
   meeting.accepted=meeting.arrived=true;
   var post=POSTS.get(bot.getObjectId());
   if(post!=null && post.controller==null && adopt(post,human)==null){PlayerGroupService.removePlayer(human);return;}
  }
  if(!meeting.accepted || !meeting.arrived || !human.isSpawned())return;
  var template=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(meeting.group.getInstanceMaskId());
  if(template==null || human.getWorldId()!=template.getInstanceMapId() || !human.isInInstance())return;
  if(bot.getWorldId()==human.getWorldId() && bot.getInstanceId()==human.getInstanceId()){MEETINGS.remove(bot.getObjectId(),meeting);return;}
  if(meeting.summoned)return; // Already following its owner: the native party transfer brings it in.
  // Never pull a waiting bot onto the human. It enters through the same native
  // portal admission and team instance selection only after the human entered.
  if(enterInstance(bot,meeting.group) && bot.getWorldId()==human.getWorldId() && bot.getInstanceId()==human.getInstanceId())MEETINGS.remove(bot.getObjectId(),meeting);
 }
 public static boolean canEnterPrepared(Player leader,ServerWideGroup group) {
  var template=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(group.getInstanceMaskId());
  if(template!=null && leader.getCurrentTeam()!=null)for(var bot:leader.getCurrentTeam().getMembers()) {
   if(bot.isWorldBot() && !MEETINGS.containsKey(bot.getObjectId()) && (bot.getWorldId()!=template.getInstanceMapId() || bot.getInstanceId()!=leader.getInstanceId())) {
    // Covers a last-member response before its committed hire arrival callback,
    // and an existing field helper selected for a new native dungeon offer.
    arrived(leader,bot);
    com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(leader,"Wait for your helpers' entrance travel window before pressing Enter.");return false;
   }
  }
  for(var meeting:MEETINGS.values())if(meeting.human==leader && meeting.group.getInstanceMaskId()==group.getInstanceMaskId() && PlayerBotTeams.same(leader,meeting.bot)) {
   if(!meeting.arrived || !atEntrance(meeting)){com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(leader,"Reach the dungeon entrance on the offered channel before pressing Enter.");return false;}
  }
  return true;
 }
 public static void validateHire(Player human,Player bot) {
  var offer=FindGroupService.getInstance().instanceOffer(human);
  if(offer==null)return;
  var template=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(offer.getInstanceMaskId());
  if(template==null || human.getRace()!=bot.getRace() || !PlayerBotGroupNeeds.level(bot.getLevel(),template.getMinLvl(),template.getMaxLvl())
   || !PlayerBotGroupNeeds.eligibleEntrance(bot,template) || !PlayerBotGroupNeeds.eligibleEntrance(human,template)
   || PlayerBotGroupNeeds.entranceFor(human,template)==null)
   throw new IllegalArgumentException("This helper or your character does not meet the posted dungeon's level or entrance requirements.");
 }
 /**
  * A cancelled (or unanswered) teleport is not final at once: the first time,
  * the human keeps the seat for two minutes and can say 'tp' to get the window
  * again (summoning-stone style second chance). A second decline, or silence,
  * leaves the party and the bots keep their post up for others.
  */
 /** Accepting the travel window during a fight or while dead keeps the seat: the offer waits for 'tp' after the fight
  * and is not counted as a refusal (it used to remove the human from the party). */
 private static void busy(Post post,Player human,int serial) {
  if (post.controller!=null || post.tpSerial!=serial || !PlayerBotTeams.same(human,post.bot)) return;
  post.tpOffers=Math.max(0,post.tpOffers-1);
  grace(post,human,serial,"You're in a fight right now. Finish it, then say 'tp' in party chat within 2 minutes and I'll send the travel window again.");
 }
 private static void declined(Post post,Player human,int serial) {
  if (post.controller!=null || post.tpSerial!=serial || !PlayerBotTeams.same(human,post.bot)) return;
  if (post.tpOffers<2) {
   grace(post,human,serial,"No problem. Changed your mind? Say 'tp' in party chat within 2 minutes and I'll offer the teleport again.");
   return;
  }
  say(post,human,"No problem! We'll keep looking for others.",false);
  PlayerGroupService.removePlayer(human);
 }
 private static void grace(Post post,Player human,int serial,String line) {
  post.tpGraceUntil=System.currentTimeMillis()+120_000;
  say(post,human,line,true);
  SOCIAL.schedule(()->REQUESTS.offer(()->{
   if (post.controller!=null || post.tpSerial!=serial || !PlayerBotTeams.same(human,post.bot)) return;
   say(post,human,"We'll keep looking for others. Join again anytime!",false);
   PlayerGroupService.removePlayer(human);
  }),120,TimeUnit.SECONDS);
 }

 // ---- Bringing bots to the human, and answering human Find Group posts ----
 private static final Map<Integer,Long> NEXT_PLAN = new ConcurrentHashMap<>();
 private static int plansThisPulse;
 private static final Map<String,Long> OFFERED = new ConcurrentHashMap<>();
 private static long nextOfferScan;

 /** Bots that joined a human from far away are summoned to them (native companion recall). */
 static void bringToHuman(Player human,List<Player> bots) {
  REQUESTS.offer(()->{
   var far=new ArrayList<PlayerBotSession>();
   for(var bot:bots){
    var session=PlayerBotService.getInstance().currentActor(bot);
    if(session!=null && session.owner()==human && (bot.getWorldId()!=human.getWorldId() || bot.getInstanceId()!=human.getInstanceId()
     || PositionUtil.getDistance(bot,human)>80))far.add(session);
   }
   if(far.isEmpty())return;
   try{PlayerBotTravel.summonAll(far);}
   catch(RuntimeException e){
    com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(human,"Your new party members are far away. Use .bot summon when you are out of combat to bring them to you.");
   }
  });
 }
 /** A human invited a world bot directly (for example after its offer). */
 public static void arrived(Player human,Player bot) {
  if(!enabled() || human.isPlayerBot())return;
  var offer=FindGroupService.getInstance().instanceOffer(human);
  if(offer!=null) {
   var template=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(offer.getInstanceMaskId());
   var place=template==null?null:PlayerBotGroupNeeds.entranceFor(human,template);
   if(place==null){com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(human,"This dungeon has no available entrance. Leave the party to end the term.");return;}
   int channel=human.getWorldId()==place.map()?human.getInstanceId():1;
   var meeting=new EntranceMeeting(bot,human,offer,place,channel);
   MEETINGS.put(bot.getObjectId(),meeting);
   REQUESTS.offer(()->offerEntrance(meeting));return;
  }
  var run=partyRun(human,bot);
  if(run!=null) {
   MEETINGS.putIfAbsent(bot.getObjectId(),joinRun(run,bot));
   REQUESTS.offer(()->speak(bot,human,line(bot,"joined-run",null,null),true));return;
  }
  REQUESTS.offer(()->speak(bot,human,line(bot,"joined",null,null),true));
  bringToHuman(human,List.of(bot));
 }

 private static Player owner(FindGroupEntry entry) {
  if(entry instanceof GroupRecruitment r)return r.getObject() instanceof Player p ? p
   : r.getObject() instanceof com.aionemu.gameserver.model.team.TemporaryPlayerTeam<?> t ? t.getLeaderObject() : null;
  if(entry instanceof GroupApplication a)return a.getPlayer();
  if(entry instanceof ServerWideGroup g)return g.getRecruiter();
  return null;
 }
 /** A hire advertisement is available to answer humans; a concrete bot request
  * or entrance gathering keeps its own job and party. */
 private static boolean canOffer(Player bot) {
  var post=POSTS.get(bot.getObjectId());
  return post==null || post.tab==Tab.APPLY && !post.expired && post.controller==null;
 }
 private static int rolePriority(Player bot,long tanks,long healers) {
  var role=PlayerBotRules.roleFor(bot.getPlayerClass());
  if(role==PlayerBotRules.Role.TANK)return tanks==0?0:3;
  if(role==PlayerBotRules.Role.HEALER)return healers==0?1:3;
  return 2;
 }

 /** A native reply only consumes an actual, unexpired bot application. */
 public static boolean instanceApplicationReply(Player human,Player applicant,byte reply) {
  if(!applicant.isWorldBot())return false;
  var until=APPLIED.remove(human.getObjectId()+":"+applicant.getObjectId());
  if(until==null || until<System.currentTimeMillis()) {
   com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(human,"That helper application expired. Wait for a new application or select a helper in Find Group.");return true;
  }
  if(reply!=1){APPLIED.put(human.getObjectId()+":"+applicant.getObjectId(),-(System.currentTimeMillis()+120_000));return true;}
  try{validateHire(human,applicant);}catch(IllegalArgumentException error){com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(human,error.getMessage());return true;}
  return false;
 }
 private static void whisper(Player bot,Player human,String message) {
  if(canTalk(bot,human) && !human.isInCustomState(CustomPlayerState.NO_WHISPERS_MODE))
   com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(human,new SM_MESSAGE(bot,message,ChatType.WHISPER));
 }

 /** WoW playerbots answer LFG requests. Every 10 s, at most three human posts get one offer each, each human at most every 3 min. */
 private static void offers(long now) {
  if(!PlayerBotConfig.FIND_GROUP_OFFERS || now<nextOfferScan)return;
  nextOfferScan=now+10_000;
  if(OFFERED.size()>2048)OFFERED.entrySet().removeIf(e->e.getValue()<now);
  int made=0;var posted=new java.util.HashSet<Integer>();
  for(var entry:FindGroupService.getInstance().humanEntries()) {
   Player human=owner(entry);
   if(human!=null)posted.add(human.getObjectId());
   if(made>=3)continue;
   if(human==null || human.isPlayerBot() || !human.isOnline() || !human.isSpawned() || human.isDead())continue;
   // A human Instance Group offer is filled with native applications, role by role, until complete.
   if(entry instanceof ServerWideGroup g){if(PlayerBotEconomyConfig.HIRING_ENABLED)fillInstanceGroup(g,human,now);continue;}
   if(OFFERED.getOrDefault(human.getObjectId()+":"+entry.getClass().getSimpleName(),0L)>now)continue;
   boolean offered=entry instanceof GroupApplication ? offerInvite(human,now) : offerMember(entry,human);
   if(offered){made++;OFFERED.put(human.getObjectId()+":"+entry.getClass().getSimpleName(),now+180_000);}
  }
  fillPlayerParties(now,posted);
 }

 private static final Map<String,Long> APPLIED = new ConcurrentHashMap<>();
 /**
  * The human posted an Instance Group offer (party or alliance size chosen in
  * the native window): suitable free bots send native applications, which the
  * human accepts in the window like any applicant. Roles fill first (tank,
  * healer), then damage/support, up to the chosen size and the companion limit.
  * At most two applications per offer every 10 s; each lasts two minutes.
  */
 private static void fillInstanceGroup(ServerWideGroup offer,Player human,long now) {
  var team=human.getCurrentTeam();
  if(team!=null && !team.isLeader(human))return;
  var group=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(offer.getInstanceMaskId());
  if(group==null)return;
  int max=com.aionemu.gameserver.dataholders.DataManager.INSTANCE_COOLTIME_DATA.getMaxMemberCount(group.getInstanceMapId(),human.getRace());
  int size=Math.min(Math.max(2,offer.getMinMembers()),max>0?max:offer.getMinMembers());
  var members=team==null?List.of(human):team.getMembers();
  String prefix=human.getObjectId()+":";
  if(APPLIED.size()>4096)APPLIED.entrySet().removeIf(e->Math.abs(e.getValue())<now);
  var pending=new ArrayList<Player>();
  for(var e:APPLIED.entrySet())if(e.getKey().startsWith(prefix) && e.getValue()>now){
   var bot=com.aionemu.gameserver.world.World.getInstance().getPlayer(Integer.parseInt(e.getKey().substring(prefix.length())));
   if(bot!=null && !PlayerBotTeams.same(bot,human))pending.add(bot);
  }
  int companionLimit=size>6?PlayerBotRaidPolicy.limit():Math.min(5,PlayerBotRaidPolicy.limit());
  int free=Math.min(size-members.size()-pending.size(),companionLimit-PlayerBotService.getInstance().companions(human).size()-pending.size());
  if(free<=0)return;
  var all=new ArrayList<Player>(members);all.addAll(pending);
  long tanks=all.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.TANK).count();
  long healers=all.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.HEALER).count();
  int wanted=(size+5)/6;
  var world=com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA.getTemplate(group.getInstanceMapId());
  var need=new PlayerBotGroupNeeds.Need(0,0,group.getInstanceMapId(),offer.getInstanceMaskId(),size,group.getMinLvl(),group.getMaxLvl(),world==null?"the dungeon":world.getName());
  for(int sent=0;sent<Math.min(2,free);sent++) {
   final boolean needTank=tanks<wanted,needHealer=healers<wanted;
   Player best=null;double bestDistance=Double.POSITIVE_INFINITY;
   for(var session:actors) {
    var bot=session.bot();
    if(bot.getRace()!=human.getRace() || !session.autonomous() || !canOffer(bot)
     || Math.abs(APPLIED.getOrDefault(prefix+bot.getObjectId(),0L))>now)continue;
    var role=PlayerBotRules.roleFor(bot.getPlayerClass());

    if(!free(bot) || !PlayerBotGroupNeeds.joinable(bot,need))continue;
    if(bot.getBlockList().contains(human.getObjectId()) || human.getBlockList().contains(bot.getObjectId()))continue;
    double distance=rolePriority(bot,needTank?0:1,needHealer?0:1)*10_000_000D+(bot.getWorldId()==human.getWorldId() ? PositionUtil.getDistance(bot,human) : 1_000_000+bot.getObjectId()%1000);
    if(distance<bestDistance){bestDistance=distance;best=bot;}
   }
   if(best==null){quiet(human,"instance offer for "+need.title(),"no free Lv"+need.minLevel()+"-"+need.maxLevel()+" "+(needTank?"tank":needHealer?"healer":"damage dealer")+" of your faction",now);return;}
   APPLIED.put(prefix+best.getObjectId(),now+120_000);
   var role=PlayerBotRules.roleFor(best.getPlayerClass());
   if(role==PlayerBotRules.Role.TANK)tanks++;else if(role==PlayerBotRules.Role.HEALER)healers++;
   // Native instance-group application: the human accepts or declines it in the window.
   com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(human,new com.aionemu.gameserver.network.aion.serverpackets.SM_FIND_GROUP(best));
   whisper(best,human,PlayerBotHiring.offerText(best)+". Accepting this application opens the Hire confirmation; no payment until you accept Hire.");
  }
 }

 /** The human recruits: one free bot of the missing role whispers that it can join. */
 private static boolean offerMember(FindGroupEntry entry,Player human) {
  if(!PlayerBotEconomyConfig.HIRING_ENABLED)return false;
  var team=human.getCurrentTeam();
  if(team!=null && !team.isLeader(human))return false;
  var members=team==null?List.of(human):team.getMembers();
  PlayerBotGroupNeeds.Need need=null;int capacity=6;
  if(entry instanceof ServerWideGroup g) {
   var group=com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(g.getInstanceMaskId());
   if(group==null)return false;
   capacity=Math.min(6,com.aionemu.gameserver.dataholders.DataManager.INSTANCE_COOLTIME_DATA.getMaxMemberCount(group.getInstanceMapId(),human.getRace()));
   var world=com.aionemu.gameserver.dataholders.DataManager.WORLD_MAPS_DATA.getTemplate(group.getInstanceMapId());
   need=new PlayerBotGroupNeeds.Need(0,0,group.getInstanceMapId(),g.getInstanceMaskId(),capacity,group.getMinLvl(),group.getMaxLvl(),world==null?"the dungeon":world.getName());
  }
  if(members.size()>=capacity || PlayerBotService.getInstance().companions(human).size()>=Math.min(5,PlayerBotRaidPolicy.limit()))return false;
  long tanks=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.TANK).count();
  long healers=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.HEALER).count();
  int level=human.getLevel();final var instanceNeed=need;
  Player best=null;double bestDistance=Double.POSITIVE_INFINITY;
  for(var session:actors) {
   var bot=session.bot();
   if(bot.getRace()!=human.getRace() || !session.autonomous() || !canOffer(bot))continue;
   var role=PlayerBotRules.roleFor(bot.getPlayerClass());

   if(instanceNeed==null && Math.abs(bot.getLevel()-level)>5 || !free(bot))continue;
   if(instanceNeed!=null ? !PlayerBotGroupNeeds.joinable(bot,instanceNeed) : Math.abs(bot.getLevel()-level)>5)continue;
   if(bot.getBlockList().contains(human.getObjectId()) || human.getBlockList().contains(bot.getObjectId()))continue;
   double distance=rolePriority(bot,tanks,healers)*10_000_000D+(bot.getWorldId()==human.getWorldId() ? PositionUtil.getDistance(bot,human) : 1_000_000+bot.getObjectId()%1000);
   if(distance<bestDistance){bestDistance=distance;best=bot;}
  }
  String what=tanks==0?"tank":healers==0?"heal":"bring damage";
  if(best==null){quiet(human,"group post","no free "+what+" bot within 5 levels of Lv"+level+" on your faction",System.currentTimeMillis());return false;}
  whisper(best,human,PlayerBotHiring.offerText(best)+". Invite to select my quote, then review the Hire confirmation. No payment until you accept.");
  return true;
 }

 // ---- Natural, player-style lines ----
 private static final java.util.Random TALK = new java.util.Random();
 private static String pick(String... options){return options[TALK.nextInt(options.length)];}
 /** What a bot says in Find Group moments: varied, casual, never command syntax. */
 static String line(Player bot,String moment,String run,String what) {
  String lvl="Lv"+bot.getLevel()+" "+role(bot);
  String place=run==null?"your group":run;
  return switch(moment) {
   case "offer" -> pick("Hey! Saw your post. I'm a "+lvl+" and can "+what+" for "+place+" if you still need someone.",
    "Hi there, still looking for people? "+lvl+" here, happy to "+what+". Just invite me.",
    "Yo, need a hand with "+place+"? I can "+what+". Throw me an invite and I'll head over.");
   case "applied" -> pick("Hey, I just applied to your "+place+" group. "+lvl+", I can "+what+".",
    "Sent you an application for "+place+"! "+lvl+" ready to "+what+".",
    "Hi! Applied for "+place+". I'm a "+lvl+", hope that helps.");
   case "joined" -> pick("Thanks for the invite! On my way.","Hey all! Heading to you now.","Got it, coming over. Happy to help!",
    "Thanks! Give me a sec to get there.");
   case "joined-run" -> pick("Count me in! I'll join you when you go in.","Thanks! I'll be ready when you enter the dungeon.",
    "Happy to help. Head in when you're ready and I'll come along.");
   case "fill" -> pick("Hey, noticed your party has a free spot. "+lvl+" here, I can "+what+" if you want an extra hand.",
    "Hi! Need one more? I'm a "+lvl+" and can "+what+". Invite me anytime.",
    "Looks like you're short a "+what.replace("bring ","")+". "+lvl+" here, glad to join!");
   default -> "Hey!";
  };
 }
 private static final Map<String,Long> QUIET = new ConcurrentHashMap<>();
 /** When no bot can answer a human's post, say why in the server log (once per 5 min per player and post). */
 private static void quiet(Player human,String post,String reason,long now) {
  String key=human.getObjectId()+":"+post;
  if(QUIET.getOrDefault(key,0L)>now)return;
  if(QUIET.size()>1024)QUIET.entrySet().removeIf(e->e.getValue()<now);
  QUIET.put(key,now+300_000);
  LoggerFactory.getLogger(PlayerBotFindGroup.class).info("Playerbot Find Group: no bot could answer {}'s {}: {}",human.getName(),post,reason);
 }

 /**
  * A party of real players with free seats gets one natural offer to its
  * leader (WoW bots joining a forming group). Only parties with at least two
  * humans; at most once per party every 5 minutes; skipped when the leader
  * already has a Find Group post (those are answered above).
  */
 private static void fillPlayerParties(long now,java.util.Set<Integer> posted) {
  if(!PlayerBotEconomyConfig.HIRING_ENABLED)return;
  var leaders=new ArrayList<Player>();
  com.aionemu.gameserver.world.World.getInstance().forEachPlayer(p->{
   if(p.isPlayerBot() || !p.isOnline() || !p.isSpawned() || p.isDead())return;
   var group=p.getPlayerGroup();
   if(group==null || !group.isLeader(p) || group.isFull() || posted.contains(p.getObjectId()))return;
   if(group.getMembers().stream().filter(m->!m.isPlayerBot()).count()<2)return;
   leaders.add(p);
  });
  for(var leader:leaders) {
   String key=leader.getObjectId()+":party";
   if(OFFERED.getOrDefault(key,0L)>now)continue;
   OFFERED.put(key,now+300_000);
   var team=leader.getPlayerGroup();
   long tanks=team.getMembers().stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.TANK).count();
   long healers=team.getMembers().stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.HEALER).count();
   int level=leader.getLevel();Player best=null;double bestDistance=Double.POSITIVE_INFINITY;
   for(var session:actors) {
    var bot=session.bot();
    if(bot.getRace()!=leader.getRace() || !session.autonomous() || !canOffer(bot) || Math.abs(bot.getLevel()-level)>5)continue;
    var role=PlayerBotRules.roleFor(bot.getPlayerClass());

    if(!free(bot) || bot.getBlockList().contains(leader.getObjectId()) || leader.getBlockList().contains(bot.getObjectId()))continue;
    double distance=rolePriority(bot,tanks,healers)*10_000_000D+(bot.getWorldId()==leader.getWorldId() ? PositionUtil.getDistance(bot,leader) : 1_000_000+bot.getObjectId()%1000);
    if(distance<bestDistance){bestDistance=distance;best=bot;}
   }
   String what=tanks==0?"tank":healers==0?"heal":"bring damage";
   if(best==null){quiet(leader,"party","no free "+what+" bot within 5 levels of Lv"+level,now);continue;}
   whisper(best,leader,PlayerBotHiring.offerText(best)+". Invite to select my quote, then review the Hire confirmation. No payment until you accept.");
   return; // one party per scan
  }
 }

 /** The human looks for a group: a forming bot group with room whispers an invitation offer. */
 private static boolean offerInvite(Player human,long now) {
  if(human.isInTeam())return false;
  var post=POSTS.values().stream().filter(p->p.tab!=Tab.APPLY && !p.expired && !p.started && p.controller==null && p.entry!=null
   && p.bot.getRace()==human.getRace() && PlayerBotGroupNeeds.applicant(human,p.need) && PlayerBotGroupNeeds.unfinished(p.bot,p.need)
   && (p.bot.getPlayerGroup()==null || p.bot.getPlayerGroup().size()<p.need.members()))
   .min(Comparator.comparing((Post p)->p.bot.getWorldId()!=human.getWorldId()).thenComparingLong(p->p.created)).orElse(null);
  if(post==null)return false;
  int size=post.bot.getPlayerGroup()==null?1:post.bot.getPlayerGroup().size();
  whisper(post.bot,human,pick("Hey! We're putting together "+post.need.title()+" ("+size+"/"+post.need.members()+"). Want in? Just say 'invite'.",
   "Hi, saw you're looking for a group. We've got room for "+post.need.title()+" ("+size+"/"+post.need.members()+"), say 'invite' if you're up for it!"));
  return true;
 }

 private PlayerBotFindGroup() {}
}
