package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.EmotionId;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.services.DuelService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;

/**
 * PvP-6: duels with bots through the native duel flow only. A bot answers a duel question after a short pause
 * (salute and accept, or shake its head and decline), fights its opponent as an explicit target (PlayerBotPvp), and
 * after the duel the winner celebrates or the loser thanks. Now and then an idle bot beside a human asks for a duel.
 */
public final class PlayerBotDuels {
 static final AtomicLong ASKED=new AtomicLong(),ACCEPTED=new AtomicLong(),DECLINED=new AtomicLong(),CHALLENGES=new AtomicLong(),WON=new AtomicLong(),LOST=new AtomicLong();
 static final long PLAN_MS=10000;
 private static final Map<Integer,Long> HUMAN_NEXT=new ConcurrentHashMap<>();
 /** A challenge is walked, not shouted: bot -> (human, give-up time). The native question follows face to face. */
 private record Challenge(int human,long until) {}
 private static final Map<Integer,Challenge> CHALLENGES_PENDING=new ConcurrentHashMap<>();
 /** Asked from arm's length (owner: "2-3 feet away"): it walks to WALK_TO and asks within ASK_RANGE. */
 static final float ASK_RANGE=1.5f,WALK_TO=0.8f,SCAN_RANGE=30,LEAVE_RANGE=15;
 static final long WALK_MS=30000,ANSWER_MS=30000;
 /** After asking: bot -> (human, give-up time). It stands there facing them and does nothing else. */
 private record Waiting(int human,long until) {}
 private static final Map<Integer,Waiting> AWAITING=new ConcurrentHashMap<>();
 /** Keep waiting while the player is there, near and has not answered (the duel starting ends the wait too). */
 static boolean waiting(long now,long until,boolean present,double distance,boolean dueling){return !dueling && present && now<until && distance<=LEAVE_RANGE;}
 /** Close enough, in plain sight, to ask: the player sees who is asking before the duel window opens. */
 static boolean faceToFace(double distance,boolean sight){return sight && distance<=ASK_RANGE;}
 private static volatile long nextPlan;

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_DUELS;}
 /** Free, healthy and not mid-action: what a player would want before saying yes. */
 static boolean free(boolean dead,boolean combat,boolean busy,double hp,boolean dueling){return !dead && !combat && !busy && hp>=70 && !dueling;}
 static boolean accepts(boolean free,boolean pvpLeaning,int roll,int percent){return free && (pvpLeaning || roll<Math.max(0,Math.min(100,percent)));}
 private static boolean busy(Player bot){return bot.isCasting() || bot.isTrading() || bot.isLooting() || bot.getFlightPath()!=null || bot.isUsingFlightTransporterOrWindstream();}
 private static void emote(Player bot,EmotionId emotion,Player toward) {
  if(bot.isSpawned() && !bot.isDead())PacketSendUtility.broadcastToSightedPlayers(bot,new SM_EMOTION(bot,EmotionType.EMOTE,emotion.id(),bot.getX(),bot.getY(),bot.getZ(),bot.getHeading(),toward==null?0:toward.getObjectId()),true);
 }

 /** DuelService has put its native question to a bot: answer it like a player would, after a moment. */
 public static void requested(Player requester,Player bot) {
  if(!bot.isPlayerBot() || requester.isPlayerBot())return;
  ASKED.incrementAndGet();
  long think=1500+ThreadLocalRandom.current().nextInt(2000);
  ThreadPoolManager.getInstance().schedule(()->answer(requester,bot),think);
 }
 private static void answer(Player requester,Player bot) {
  var session=PlayerBotService.getInstance().currentActor(bot);
  if(session==null){bot.getResponseRequester().respond(SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_ACCEPT_REQUEST,0);return;}
  synchronized(session) {
   var duels=DuelService.getInstance();
   boolean ready=enabled() && free(bot.isDead(),bot.getController().isInCombat(),busy(bot),PlayerBotPvp.hp(bot),duels.isDueling(bot)) && requester.isOnline() && !duels.isDueling(requester);
   boolean yes=accepts(ready,PlayerBotLeanings.pvp(bot),ThreadLocalRandom.current().nextInt(100),PlayerBotConfig.PVP_DUEL_ACCEPT);
   if(!yes){emote(bot,EmotionId.NO,requester);bot.getResponseRequester().respond(SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_ACCEPT_REQUEST,0);DECLINED.incrementAndGet();return;}
   bot.setTarget(requester);emote(bot,EmotionId.SALUTE,requester);
  }
  // Accept a beat after the salute, so the gesture is seen before the countdown.
  ThreadPoolManager.getInstance().schedule(()->{
   if(bot.getResponseRequester().respond(SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_ACCEPT_REQUEST,1) && DuelService.getInstance().isDueling(bot,requester))ACCEPTED.incrementAndGet();
  },800);
 }

 static final AtomicLong REWARDS=new AtomicLong(),REWARDS_REFUSED=new AtomicLong();
 /** Same bot pays the same account at most once per this long. */
 static final long SAME_BOT_MS=30*60000L;
 private record Tally(String day,int count) {}
 private static final Map<Integer,Tally> DAILY=new ConcurrentHashMap<>();
 private static final Map<String,Long> LAST_FROM_BOT=new ConcurrentHashMap<>();
 static long purse(int level,int perLevel){return (long)Math.max(1,level)*Math.max(0,perLevel);}
 static boolean paid(int todayCount,int perDay,long lastFromBot,long now){return todayCount<Math.max(0,perDay) && now-lastFromBot>=SAME_BOT_MS;}
 /** A human beat a world bot: a small native Kinah purse and AP gain, at most rewards_per_day, the same bot once per 30 min. */
 private static void reward(Player human,Player bot) {
  if(!enabled() || human.getAccount()==null)return;
  int account=human.getAccount().getId();long now=System.currentTimeMillis();String day=java.time.LocalDate.now().toString();
  var tally=DAILY.compute(account,(k,v)->v==null || !v.day().equals(day)?new Tally(day,0):v);
  String pair=account+":"+bot.getObjectId();
  if(!paid(tally.count(),PlayerBotConfig.PVP_DUEL_REWARDS_PER_DAY,LAST_FROM_BOT.getOrDefault(pair,0L),now)) {
   REWARDS_REFUSED.incrementAndGet();
   PacketSendUtility.sendMessage(human,"[Duel] You beat "+bot.getName()+". "+(tally.count()>=PlayerBotConfig.PVP_DUEL_REWARDS_PER_DAY
    ?"Today's duel rewards are used up.":bot.getName()+" paid you a short while ago; duel someone else for a reward."));
   return;
  }
  DAILY.put(account,new Tally(day,tally.count()+1));LAST_FROM_BOT.put(pair,now);
  long kinah=purse(human.getLevel(),PlayerBotConfig.PVP_DUEL_REWARD_KINAH_PER_LEVEL);int ap=Math.max(0,PlayerBotConfig.PVP_DUEL_REWARD_AP);
  if(kinah>0)human.getInventory().increaseKinah(kinah);
  if(ap>0)com.aionemu.gameserver.services.abyss.AbyssPointsService.addAp(human,ap);
  String line="You beat "+bot.getName()+": +"+String.format("%,d",kinah)+" Kinah, +"+ap+" Abyss Points.";
  PacketSendUtility.sendMessage(human,"[Duel] "+line);
  PacketSendUtility.sendMessage(human,line,com.aionemu.gameserver.model.ChatType.BRIGHT_YELLOW_CENTER);
  REWARDS.incrementAndGet();
 }
 /** The duel ended with a winner: the bot celebrates a win or thanks for the fight; a human who won is paid. */
 public static void ended(Player winner,Player loser) {
  if(winner!=null && loser!=null && !winner.isPlayerBot() && loser.isWorldBot())reward(winner,loser);
  if(winner!=null && winner.isPlayerBot() && loser!=null){WON.incrementAndGet();ThreadPoolManager.getInstance().schedule(()->emote(winner,EmotionId.VICTORY,loser),1500);}
  if(loser!=null && loser.isPlayerBot() && winner!=null){LOST.incrementAndGet();ThreadPoolManager.getInstance().schedule(()->emote(loser,EmotionId.THANK,winner),2000);}
 }

 /** At most one challenge per human every challenge_minutes, from an idle autonomous bot standing beside them. */
 static void plan(Collection<PlayerBotSession> sessions,long now) {
  if(!enabled() || PlayerBotConfig.PVP_DUEL_CHALLENGE_MINUTES<=0 || now<nextPlan)return;nextPlan=now+PLAN_MS;
  var duels=DuelService.getInstance();
  for(var human:World.getInstance().getAllPlayers()) {
   if(human.isPlayerBot() || !human.isOnline() || !human.isSpawned() || human.isDead() || human.isInInstance() || human.getController().isInCombat()
    || duels.isDueling(human) || PlayerBotPvp.hp(human)<90 || now<HUMAN_NEXT.getOrDefault(human.getObjectId(),0L))continue;
   for(var s:sessions) {
    var bot=s.bot();
    if(!s.autonomous() || s.closing() || bot.getWorldId()!=human.getWorldId() || bot.getInstanceId()!=human.getInstanceId()
     || !PositionUtil.isInRange(bot,human,SCAN_RANGE) || bot.getRace()!=human.getRace() || CHALLENGES_PENDING.containsKey(bot.getObjectId())
     || !free(bot.isDead(),bot.getController().isInCombat(),busy(bot),PlayerBotPvp.hp(bot),duels.isDueling(bot)) || PlayerBotPvp.hp(bot)<90
     || PlayerBotRoadDuty.assigned(bot) || bot.getCurrentTeam()!=null)continue;
    // Bots that like a fight ask more readily; an ordinary bot asks only now and then.
    if(!PlayerBotLeanings.pvp(bot) && ThreadLocalRandom.current().nextInt(100)>=20)continue;
    HUMAN_NEXT.put(human.getObjectId(),now+PlayerBotConfig.PVP_DUEL_CHALLENGE_MINUTES*60000L);
    // The bot walks over first (approach below, in its own decision); the question comes when it stands in front.
    CHALLENGES_PENDING.put(bot.getObjectId(),new Challenge(human.getObjectId(),now+WALK_MS));break;
   }
  }
 }
 /** The bot's own decision: walk to the player it chose, then point, say so and send the native duel request;
  * then wait in front of them for the answer before doing anything else. */
 static boolean approach(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  var bot=session.bot();
  var asked=AWAITING.get(bot.getObjectId());
  if(asked!=null)return await(bot,asked,navigation,now);
  var pending=CHALLENGES_PENDING.get(bot.getObjectId());if(pending==null)return false;
  var human=World.getInstance().getPlayer(pending.human());var duels=DuelService.getInstance();
  if(!enabled() || now>pending.until() || human==null || !human.isOnline() || human.isDead() || human.getWorldId()!=bot.getWorldId()
   || human.getInstanceId()!=bot.getInstanceId() || duels.isDueling(human) || duels.isDueling(bot) || human.getController().isInCombat()){
   CHALLENGES_PENDING.remove(bot.getObjectId());navigation.stop();return false;
  }
  double distance=PositionUtil.getDistance(bot,human);
  if(!faceToFace(distance,com.aionemu.gameserver.world.geo.GeoService.getInstance().canSee(bot,human))){navigation.approach(human,WALK_TO);return true;}
  CHALLENGES_PENDING.remove(bot.getObjectId());navigation.stop();bot.setTarget(human);
  emote(bot,EmotionId.POINT,human);
  PacketSendUtility.broadcastToSightedPlayers(bot,new com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE(bot,human.getName()+", fancy a duel?",com.aionemu.gameserver.model.ChatType.NORMAL),true);
  face(bot,human);
  duels.onDuelRequest(bot,human);CHALLENGES.incrementAndGet();
  AWAITING.put(bot.getObjectId(),new Waiting(human.getObjectId(),now+ANSWER_MS));
  return true;
 }
 private static void face(Player bot,Player human){bot.getPosition().setH(PositionUtil.getHeadingTowards(bot,human));}
 /** Standing in front of the player until they answer. A duel that starts goes on through PlayerBotPvp. */
 private static boolean await(Player bot,Waiting asked,PlayerBotNavigation navigation,long now) {
  var human=World.getInstance().getPlayer(asked.human());var duels=DuelService.getInstance();
  boolean present=human!=null && human.isOnline() && !human.isDead() && human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId();
  if(waiting(now,asked.until(),present,present?PositionUtil.getDistance(bot,human):Double.MAX_VALUE,duels.isDueling(bot))) {
   navigation.stop();bot.setTarget(human);face(bot,human);return true;
  }
  AWAITING.remove(bot.getObjectId());
  // No answer: withdraw through the native question, which closes the player's duel window.
  if(!duels.isDueling(bot))bot.getResponseRequester().respond(com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_WITHDRAW_REQUEST,1);
  return false;
 }
 /** DuelService: the player said no. The bot shrugs it off and goes back to what it was doing. */
 public static void declined(Player requester,Player responder) {
  if(requester==null || !requester.isPlayerBot() || AWAITING.remove(requester.getObjectId())==null)return;
  ThreadPoolManager.getInstance().schedule(()->emote(requester,EmotionId.NO,responder),700);
 }
 /** For the decision status line. */
 static boolean asking(Player bot){return AWAITING.containsKey(bot.getObjectId());}
 static void forget(int bot){CHALLENGES_PENDING.remove(bot);AWAITING.remove(bot);}
 static String stats(){return "duel requests to bots="+ASKED.get()+" accepted="+ACCEPTED.get()+" declined="+DECLINED.get()+", bot challenges="+CHALLENGES.get()+", bot duels won/lost="+WON.get()+"/"+LOST.get()+", duel rewards paid="+REWARDS.get()+" capped="+REWARDS_REFUSED.get();}
 private PlayerBotDuels(){}
}
