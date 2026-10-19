package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.concurrent.PriorityThreadFactory;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
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
 enum Intent { JOIN, START, WAIT, HELP, LEAD, LEAVE, HELLO, UNKNOWN }
 private static final Pattern JOIN = Pattern.compile("\\b(inv(?:ite)?|join|group|party|apply)\\b");
 private static final Pattern START = Pattern.compile("\\b(ready|start|go|begin|let's go|lets go|rdy)\\b");
 private static final Pattern WAIT = Pattern.compile("\\b(wait|hold|not ready|not yet|don't start|dont start|do not start|stop|brb|afk)\\b");
 private static final Pattern HELP = Pattern.compile("\\b(help|how|where|what|quest|objective|details|status|need|role|tank|heal(?:er|ing)?|dps)\\b");
 private static final Pattern LEAVE = Pattern.compile("^(?:bye|goodbye|leave|cancel|no thanks|never mind|nevermind)[.! ]*$");
 private static final Pattern NEGATIVE = Pattern.compile("\\b(no|not|don't|dont|never|can't|cant|cannot)\\b");
 private static final Map<Integer, Post> POSTS = new ConcurrentHashMap<>();
 private static final ArrayBlockingQueue<Runnable> REQUESTS = new ArrayBlockingQueue<>(128);
 private static final ScheduledExecutorService SOCIAL = Executors.newSingleThreadScheduledExecutor(new PriorityThreadFactory("PlayerbotFindGroup", Thread.NORM_PRIORITY));
 private static volatile boolean stopped;
 private static Future<?> task;
 private static Collection<PlayerBotSession> actors = List.of();
 private static Iterator<PlayerBotSession> cursor = Collections.emptyIterator();
 private static Iterator<Post> postCursor = Collections.emptyIterator();
 private static long nextReport,observed,available,withoutNeed,published,joined;
 private static final Map<com.aionemu.gameserver.model.Race,Long> NEXT_ANNOUNCEMENT = new EnumMap<>(com.aionemu.gameserver.model.Race.class);

 private static final class Post {
  final Player bot;
  final Tab tab;
  final PlayerBotGroupNeeds.Need need;
  final PlayerBotWorldTravel.Place gathering;
  final long created = System.currentTimeMillis();
  volatile FindGroupEntry entry;
  volatile Player controller;
  volatile long joiningUntil;
  boolean greeted, finished;
  volatile boolean started;
  long lastReply, lastAnnouncement;
  volatile boolean assembling,expired;
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
  SOCIAL.shutdown();
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
  return session != null && session.autonomous() && resident(bot) && !bot.isInTeam() && !bot.isTrading() && bot.getFlightPath()==null;
 }
 private static boolean forming(Post post) {
  var team=post.bot.getCurrentTeam();
  return post.tab!=Tab.APPLY && !post.expired && !post.started && team!=null && !team.isFull() && team.size()<post.need.members()
   && team.isLeader(post.bot) && (post.controller==null || PlayerBotTeams.same(post.bot,post.controller));
 }

 private static void pulse() {
  try {
   for (int i=0; i<8; i++) { var request=REQUESTS.poll(); if (request==null) break; try { request.run(); } catch (RuntimeException e) { LoggerFactory.getLogger(PlayerBotFindGroup.class).warn("Playerbot Find Group request failed",e); } }
   // Even expired/departed entries are retired incrementally. No full roster work
   // runs in chat packets, native team locks, combat or companion scheduling.
   if (!postCursor.hasNext()) postCursor=POSTS.values().iterator();
   for (int i=0; i<16 && postCursor.hasNext(); i++) maintain(postCursor.next());
   if (!enabled()) { report(); return; }
   if (!cursor.hasNext()) cursor=actors.iterator();
   for (int i=0; i<16 && cursor.hasNext(); i++) {
    var session=cursor.next(); var bot=session.bot();
    observed++;
    if (!PlayerBotService.getInstance().current(session) || !free(bot)) continue;
    available++;
    if(tryJoin(bot))continue;
    if(POSTS.containsKey(bot.getObjectId()))continue;
    publish(bot);
   }
   announce();
   report();
  } catch (RuntimeException e) { LoggerFactory.getLogger(PlayerBotFindGroup.class).warn("Playerbot Find Group maintenance failed; normal bot AI continues",e); }
 }

 /** Use the actual subscribed LFG channel, with one offer per faction every 20 s.
  * Staggered renewal avoids a population-sized wall of chat at login or regroup. */
 private static void announce() {
  long now=System.currentTimeMillis();
  for(var race:List.of(com.aionemu.gameserver.model.Race.ELYOS,com.aionemu.gameserver.model.Race.ASMODIANS)) {
   if(now<NEXT_ANNOUNCEMENT.getOrDefault(race,0L))continue;
   var post=POSTS.values().stream().filter(p->announcementDue(p,now) && p.bot.getRace()==race)
    .min(Comparator.comparingLong((Post p)->p.lastAnnouncement).thenComparingLong(p->p.created)).orElse(null);
   if(post==null)continue;
   if(ChatServer.getInstance().sendBotFindGroup(post.bot,announcement(post.entry))) {
    post.lastAnnouncement=now;
    NEXT_ANNOUNCEMENT.put(race,now+20000);
   }
  }
 }

 private static boolean announcementDue(Post post,long now) {
  return post.entry!=null && visible(post.entry) && now-post.created>=10000
   && now-post.lastAnnouncement>=180000+Math.floorMod(post.bot.getObjectId(),120)*1000L;
 }

 static String announcement(FindGroupEntry entry) {
  if(entry instanceof GroupRecruitment r)return r.getMessage()+" ("+r.getSize()+"/6)";
  if(entry instanceof GroupApplication a)return a.getMessage();
  if(entry instanceof ServerWideGroup g)return g.getMessage()+" ("+g.getMembers().size()+"/"+g.getMinMembers()+") Whisper for invite.";
  throw new IllegalArgumentException("Unknown Find Group entry");
 }

 private static void report() {
  long now=System.currentTimeMillis();if(now<nextReport)return;nextReport=now+30000;
  long recruit=0,apply=0,instance=0,gathering=0,parties=0,members=0;
  for(var post:POSTS.values()) {
   if(post.entry!=null)switch(post.tab){case RECRUIT->recruit++;case APPLY->apply++;case INSTANCE->instance++;}
   else if(post.gathering!=null && post.controller==null)gathering++;
   if(post.bot.getPlayerGroup()!=null && post.bot.getPlayerGroup().isLeader(post.bot)){parties++;members+=post.bot.getPlayerGroup().size();}
  }
  LoggerFactory.getLogger(PlayerBotFindGroup.class).info("Playerbot Find Group: enabled={}; observed={}, available={}, without need={}, published={}, joined={}; current recruit/apply/instance={}/{}/{}; gathering={}, parties={}, party members={}; chat queue={}",enabled(),observed,available,withoutNeed,published,joined,recruit,apply,instance,gathering,parties,members,REQUESTS.size());
  observed=available=withoutNeed=published=joined=0;
 }

 private static void publish(Player bot) {
  PlayerBotGroupNeeds.Need need=null;
  PlayerBotWorldTravel.Place gathering=null;
  Tab tab;
  // Different people prefer to lead or seek an invitation. A character makes
  // one registration, rather than cloning its name across all three tabs.
  if (POSTS.values().stream().filter(p->p.tab==Tab.INSTANCE && p.bot.getRace()==bot.getRace()).count()<Math.min(4,Math.clamp(PlayerBotConfig.FIND_GROUP_POSTS_PER_TAB,1,30))
   && PlayerBotWorldActivity.occupied(bot)) {
   var plan=PlayerBotGroupNeeds.instancePlan(bot);
   if(plan!=null){need=plan.need();gathering=plan.entrance();}
  }
  if (need != null) tab=Tab.INSTANCE;
  else { need=PlayerBotGroupNeeds.field(bot); tab=(bot.getObjectId()&1)==0 ? Tab.RECRUIT : Tab.APPLY; }
  if (need==null) { withoutNeed++; return; }
  final Tab target=tab;
  long count=POSTS.values().stream().filter(p->p.entry!=null && p.tab==target && p.bot.getRace()==bot.getRace()).count();
  if (count>=Math.clamp(PlayerBotConfig.FIND_GROUP_POSTS_PER_TAB,1,30)) return;
  var post=new Post(bot,tab,need,gathering);
  POSTS.put(bot.getObjectId(),post);
  register(post);
 }

 private static void register(Post post) {
  var bot=post.bot;var tab=post.tab;var need=post.need;
  if(post.expired || post.entry!=null || post.tab==Tab.INSTANCE && !PlayerBotGroupNeeds.entrance(bot,com.aionemu.gameserver.dataholders.DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(need.mask())))return;
  String role=role(bot);
  String message=switch (tab) {
   case RECRUIT -> variant(bot, "LFM "+need.title()+" - "+role+" here, looking for company. Whisper for invite.", "Anyone need "+need.title()+"? "+role+" making a quest/XP group, all welcome.");
   case APPLY -> variant(bot, role+" LFG for "+need.title()+". Still need it, happy to help.", "Looking for a group: "+need.title()+". Lv"+bot.getLevel()+" "+role+", invite me.");
   case INSTANCE -> need.title()+(need.quest()>0?" quest run - ":" XP run - ")+role+" forming, looking for company. Meet at entrance.";
  };
  message=message.length()>120 ? message.substring(0,117)+"..." : message;
  post.entry=switch (tab) {
   case RECRUIT -> new GroupRecruitment(bot.getCurrentTeam()==null?bot:bot.getCurrentTeam(),message,0);
   case APPLY -> new GroupApplication(bot,message,0,bot.getPlayerClass().getClassId(),bot.getLevel());
   case INSTANCE -> new ServerWideGroup(bot,need.mask(),need.members(),message);
  };
  if (!FindGroupService.getInstance().registerBotEntry(post.entry) || !(free(bot) || forming(post))) { withdraw(post); }
  else published++;
 }

 private static String variant(Player bot,String first,String second) { return (bot.getObjectId()&2)==0 ? first : second; }
 /** Reserve human space rather than filling every native party with bots. */
 static int botSeats(boolean instance,int capacity) { return Math.max(1,Math.min(instance?5:3,capacity-1)); }
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
  return enabled() && (groupPost(bot)!=null || post!=null && !post.expired && post.controller==null && post.gathering!=null);
 }

 /** Called only by the actor's normal decision while native actions/support are safe. */
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation) {
  if(!enabled() || !session.autonomous())return false;
  var bot=session.bot();var post=groupPost(bot);
  if(post!=null && post.bot!=bot) {
   var leader=post.bot;
   if(!leader.isSpawned() || leader.isDead() || leader.getWorldId()!=bot.getWorldId() || leader.getInstanceId()!=bot.getInstanceId())return true;
   double distance=PositionUtil.getDistance(bot,leader);
   if(distance>7)navigation.follow(leader,Math.max(1,leader.getPlayerGroup().getMembers().indexOf(bot)));
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
  if(!enabled() || post==null || post.expired || post.tab==Tab.APPLY || post.controller!=null || post.started
   || leader==member || !free(member) || !(free(leader) || forming(post)) || !socialIdle(leader) || !socialIdle(member)
   || leader.getRace()!=member.getRace() || leader.getWorldId()!=member.getWorldId() || leader.getInstanceId()!=member.getInstanceId()
   || PositionUtil.getDistance(leader,member)>(post.need.instance()?900:250)
   || own!=null && own.gathering!=null || !PlayerBotGroupNeeds.useful(member,post.need)
   || leader.getBlockList().contains(member.getObjectId()) || member.getBlockList().contains(leader.getObjectId())
   || member.getPlayerSettings().isInDeniedStatus(DeniedStatus.GROUP))return false;
  var team=leader.getPlayerGroup();var members=team==null?List.of(leader):team.getMembers();
  if(members.stream().anyMatch(p->!p.isWorldBot()) || members.size()>=botSeats(post.need.instance(),post.need.members()))return false;
  long tanks=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.TANK).count();
  long healers=members.stream().filter(p->PlayerBotRules.roleFor(p.getPlayerClass())==PlayerBotRules.Role.HEALER).count();
  return partyRoleFits(PlayerBotRules.roleFor(member.getPlayerClass()),tanks,healers,members.size(),botSeats(post.need.instance(),post.need.members()));
 }

 private static boolean tryJoin(Player bot) {
  var own=POSTS.get(bot.getObjectId());
  if(own!=null && (own.gathering!=null || System.currentTimeMillis()-own.created<10000))return false;
  // A finite registration set, not a population scan; one native invitation per
  // candidate. Prefer instance work and older recruiters to form stable groups.
  var offer=POSTS.values().stream().filter(p->p.bot!=bot && p.tab!=Tab.APPLY && !p.expired && p.controller==null
   && (own==null || p.created<own.created) && canJoin(p.bot,bot))
   .min(Comparator.comparing((Post p)->!p.need.instance()).thenComparingLong(p->p.created)).orElse(null);
  if(offer==null)return false;
  offer.assembling=true;
  try {
   if(!PlayerBotService.getInstance().joinFindGroupBots(offer.bot,bot))return false;
   if(own!=null){withdraw(own);POSTS.remove(bot.getObjectId(),own);}
   offer.joiningUntil=0;joined++;
   register(offer);
   return true;
  }finally{offer.assembling=false;}
 }

 private static void retire(Post post) {
  post.expired=true;withdraw(post);
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
  if (!enabled() || !resident(post.bot) || post.expired) { retire(post); return; }
  if (post.controller != null) {
   if (!PlayerBotTeams.same(post.controller,post.bot) || !post.controller.isOnline()) { withdraw(post); POSTS.remove(post.bot.getObjectId(),post); return; }
   if (!post.greeted) {
    post.greeted=true;
    say(post,post.controller,"Hi "+post.controller.getName()+"! Thanks for joining for "+post.need.title()+". Want to start, wait for more, or hear how you can help?"+(forming(post)?" I'll keep recruiting until we start; you can ask for lead.":" I'll follow your lead."),true);
   }
   if (!post.finished && !PlayerBotGroupNeeds.unfinished(post.bot,post.need)) {
    post.finished=true;
    say(post,post.controller,"Thanks, that's what I needed! I'll stay with the party while you finish up; say 'leave' when you're done with my help.",true);
    withdraw(post);
   }
   if (post.entry!=null && (!forming(post) || System.currentTimeMillis()-post.created>600000)) withdraw(post);
   return;
  }
  if(post.assembling)return;
  if (post.bot.isInTeam() && System.currentTimeMillis()<post.joiningUntil) return;
  if (!(free(post.bot) || forming(post)) || !PlayerBotGroupNeeds.unfinished(post.bot,post.need) || System.currentTimeMillis()-post.created>900000
   || post.need.instance() && !PlayerBotGroupNeeds.applicant(post.bot,post.need)) { retire(post); return; }
  register(post);
 }

 private static void withdraw(Post post) {
  var entry=post.entry;
  if (entry!=null) { post.entry=null; FindGroupService.getInstance().removeBotEntry(entry); }
 }

 /** Native registry cleanup only; never acquire an actor/service monitor in a team notification. */
 public static void teamJoined(Player player) { var post=POSTS.get(player.getObjectId()); if (post!=null) { post.joiningUntil=System.currentTimeMillis()+30000; if(post.tab!=Tab.INSTANCE)withdraw(post); } }

 /** Called after native control publication. Conversation is delivered by the social lane. */
 static void controlled(Player bot,Player controller) {
  var post=POSTS.get(bot.getObjectId());
  if (post==null) return;
  if (post.tab!=Tab.INSTANCE || controller==bot || bot.getCurrentTeam()==null || !bot.getCurrentTeam().isLeader(bot)) withdraw(post);
  if (controller==bot) { POSTS.remove(bot.getObjectId(),post); return; }
  post.controller=controller;
 }

 public static boolean recruiting(Player bot) { var post=POSTS.get(bot.getObjectId()); return enabled() && post!=null && !post.expired && resident(bot) && (post.controller==null || forming(post)); }

 /** A bot invitation was accepted through the real native question window. */
 public static void accepted(Player inviter,Player invited) {
  if (!inviter.isWorldBot() || invited.isPlayerBot() || !PlayerBotTeams.same(inviter,invited)) return;
  var post=POSTS.get(inviter.getObjectId());
  if (post==null) return;
  if (!enabled() || !PlayerBotGroupNeeds.unfinished(inviter,post.need) || !PlayerBotGroupNeeds.applicant(invited,post.need)) { PlayerGroupService.removePlayer(invited); return; }
  if (post.controller!=null) {
   REQUESTS.offer(()->say(post,invited,"Welcome, "+invited.getName()+"! We're gathering for "+post.need.title()+". Let me know if you need the details.",true));
  } else if (PlayerBotService.getInstance().controlFindGroupBot(invited,inviter)) {
   if (post.tab!=Tab.INSTANCE) PlayerGroupService.changeLeader(invited);
  } else PlayerGroupService.removePlayer(invited);
 }

 public static boolean apply(Player applicant,Player recruiter) {
  var post=POSTS.get(recruiter.getObjectId());
  if (post==null || post.tab!=Tab.INSTANCE || !recruiting(recruiter)) return false;
  REQUESTS.offer(()->invite(post,applicant));
  return true;
 }

 /** Filter stale bot ads immediately on refresh, even before the incremental cleanup reaches them. */
 public static boolean visible(FindGroupEntry entry) {
  Player bot=entry instanceof GroupRecruitment r && r.getObject() instanceof Player p ? p
   : entry instanceof GroupRecruitment r && r.getObject() instanceof com.aionemu.gameserver.model.team.TemporaryPlayerTeam<?> t ? t.getLeaderObject()
   : entry instanceof GroupApplication a ? a.getPlayer() : entry instanceof ServerWideGroup g ? g.getRecruiter() : null;
  if (bot==null || !bot.isWorldBot()) return true;
  var post=POSTS.get(bot.getObjectId());
  return enabled() && post!=null && !post.expired && post.entry==entry && (free(bot) || forming(post)) && PlayerBotGroupNeeds.unfinished(bot,post.need)
   && (post.need.instance() || bot.getWorldId()==post.need.map());
 }

 static Intent intent(String message) {
  if (message==null || message.length()>512) return Intent.UNKNOWN;
  String text=message.toLowerCase(Locale.ROOT).replace('\u2019','\'').replaceAll("\\s+"," ").strip();
  if (LEAVE.matcher(text).matches()) return Intent.LEAVE;
  if (text.matches("(?:yes|yeah|yep|sure|ok|okay)[!. ]*")) return Intent.START;
  if (text.matches("(?:no|nope)[!. ]*")) return Intent.WAIT;
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
   var members=POSTS.values().stream().filter(p->p.controller!=null && PlayerBotTeams.same(sender,p.bot)).sorted(Comparator.comparingInt(p->p.bot.getObjectId())).toList();
   post=members.stream().filter(p->addressed(message,p.bot.getName())).findFirst().orElse(members.isEmpty()?null:members.get(0));
  }
  if (post==null || !canTalk(post.bot,sender) || type==ChatType.WHISPER && sender.isInCustomState(CustomPlayerState.NO_WHISPERS_MODE)) return;
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
  if (!party) {
   if (post.controller!=null && !forming(post)) { say(post,sender,"I'm already helping a party. Good luck finding a group!",false); return; }
   if (action==Intent.JOIN || action==Intent.START) { invite(post,sender); return; }
   say(post,sender,PlayerBotGroupNeeds.guidance(post.bot,post.need)+(post.tab==Tab.APPLY ? " Invite me if your party needs a "+role(post.bot)+"." : " Want an invite?"),false);
   return;
  }
  switch (action) {
   case START -> {
    if (sender!=post.controller) { say(post,sender,"Let's let "+post.controller.getName()+" decide when we start.",true); break; }
    var session=PlayerBotService.getInstance().currentActor(post.bot);
    if (session==null || session.owner()!=sender) break;
    if (post.bot.getController().isInCombat() || post.bot.isCasting() || post.bot.isTrading()) { say(post,sender,"Give me a moment to finish what I'm doing, then let's go.",true); break; }
    session.order(PlayerBotRules.Order.FOLLOW);
    post.started=true; withdraw(post);
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
     PlayerGroupService.changeLeader(sender); withdraw(post);
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
  if (!enabled() || POSTS.get(post.bot.getObjectId())!=post || !canTalk(post.bot,player) || !(free(post.bot) || forming(post))) return;
  if (player.isInTeam()) { say(post,player,"You're already in a party. Your leader can invite me if there's room.",false); return; }
  if(post.bot.getPlayerGroup()!=null && post.bot.getPlayerGroup().size()>=post.need.members()) { say(post,player,"We've filled the available places for this run. Thanks for asking!",false);return; }
  if (!PlayerBotGroupNeeds.unfinished(post.bot,post.need)) { say(post,player,"I've just finished that objective. Thanks for offering!",false); withdraw(post); return; }
  if (!PlayerBotGroupNeeds.applicant(player,post.need)) { say(post,player,"This run needs levels "+post.need.minLevel()+"-"+post.need.maxLevel()+(post.need.instance()?" and an available, unlocked entrance.":". Let's find a better match for you."),false); return; }
  if (player.getPlayerSettings().isInDeniedStatus(DeniedStatus.GROUP)) { say(post,player,"Your settings are blocking group invitations.",false); return; }
  // Native invitations are allowed during combat. The controller's combat
  // timer is not an invitation restriction, including its post-hit grace.
  say(post,player,"Sure, sending an invite for "+post.need.title()+"."+(post.controller==null?" You can lead when we start.":" We're still gathering the party."),false);
  PlayerGroupService.inviteToGroup(post.bot,player);
 }

 private static void say(Post post,Player player,String message,boolean party) {
  if (!canTalk(post.bot,player)) return;
  if (party && PlayerBotTeams.same(post.bot,player)) {
   for (var member : post.bot.getCurrentTeam().getMembers()) if (canTalk(post.bot,member))
    com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(member,new SM_MESSAGE(post.bot,message,ChatType.GROUP));
  } else if (!player.isInCustomState(CustomPlayerState.NO_WHISPERS_MODE)) com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(player,new SM_MESSAGE(post.bot,message,ChatType.WHISPER));
 }

 private PlayerBotFindGroup() {}
}
