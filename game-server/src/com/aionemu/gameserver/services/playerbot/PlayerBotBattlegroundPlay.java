package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.instancescore.PvpInstanceScore;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * Shared mechanics for the per-map battleground hooks (PlayerBotKamar, PlayerBotOphidanBridge, PlayerBotIronWall,
 * PlayerBotIdgelDome). Each map keeps its own table, switch, target line, travel step and report line; this only holds
 * what they do alike: pick the objective worth most for the walk (double when a same-race player is on it), fight
 * kill targets natively, use capture objects through the native dialog/cast bar, and take the boss only when its points
 * win the run (these maps end on the boss and give the win to the side with more points). The map handlers keep all
 * scoring. PvP-leaning bots keep hunting players (PlayerBotPvpSeek).
 */
final class PlayerBotBattlegroundPlay {
 /** One map: kill targets and use targets (npc id -> points), the boss that ends the run and its points (0 = none),
  * and whether use targets are capture flags a side cannot take from itself. */
 record Field(int map,String name,Map<Integer,Integer> kills,Map<Integer,Integer> uses,int boss,int bossPoints,boolean captureFlags) {}
 /** Counters of one map, for its report line. */
 static final class Stats {
  final AtomicLong targeted=new AtomicLong(),picks=new AtomicLong(),arrived=new AtomicLong(),unreachable=new AtomicLong(),uses=new AtomicLong(),calls=new AtomicLong(),bosses=new AtomicLong();
  final Set<Integer> seen=ConcurrentHashMap.newKeySet();
  String line(boolean enabled){return "enabled="+enabled+", objective targets="+targeted.get()+", picks="+picks.get()+" arrived="+arrived.get()+" unreachable="+unreachable.get()
   +", uses started="+uses.get()+", call-outs="+calls.get()+", bosses down="+bosses.get();}
 }
 static final float RANGE=45,ARRIVED=15;
 static final long UNREACHABLE_MS=60000,CALL_MS=60000,REPICK_MS=5000;
 private record Goal(int npc,long since) {}
 private static final Map<Integer,Goal> GOALS=new ConcurrentHashMap<>();
 private static final Map<Long,Long> SKIP=new ConcurrentHashMap<>(),CALLED=new ConcurrentHashMap<>(),ASKED=new ConcurrentHashMap<>();
 static final long USE_START_MS=3000,USE_RETRY_MS=20000;

 /** Worth to a bot now; 0 = not an objective. A boss counts only when its points put the bot's side ahead. */
 static int value(Field f,int npcId,int own,int enemy,boolean ownCapture) {
  if(f==null)return 0;
  if(npcId==f.boss() && f.boss()!=0)return own+f.bossPoints()>enemy?f.bossPoints():0;
  Integer use=f.uses().get(npcId);
  if(use!=null)return f.captureFlags() && ownCapture?0:use;
  return f.kills().getOrDefault(npcId,0);
 }
 static double score(int value,double distance,boolean playerOnIt){return value<=0?0:value/(Math.max(0,distance)+20)*(playerOnIt?2:1);}
 static boolean runner(boolean pvpLeaning){return !pvpLeaning;}
 static boolean use(Field f,int npcId){return f.uses().containsKey(npcId);}
 private static PvpInstanceScore<?> score(Player bot) {
  var handler=bot.getPosition().getWorldMapInstance().getInstanceHandler();
  return handler!=null && handler.getInstanceScore() instanceof PvpInstanceScore<?> s?s:null;
 }
 private static int points(PvpInstanceScore<?> s,Race race){return s==null?0:s.getPointsByRace(race);}
 private static Race enemy(Race race){return race==Race.ELYOS?Race.ASMODIANS:Race.ELYOS;}
 static boolean started(Player bot) {
  var handler=bot.getPosition().getWorldMapInstance().getInstanceHandler();
  return handler!=null && handler.getInstanceScore()!=null && handler.getInstanceScore().getInstanceProgressionType()!=null
   && handler.getInstanceScore().getInstanceProgressionType().isStartProgress();
 }
 private static int value(Field f,Player bot,Npc npc) {
  var s=score(bot);
  return value(f,npc.getNpcId(),points(s,bot.getRace()),points(s,enemy(bot.getRace())),npc.getRace()==bot.getRace());
 }
 private static boolean playerOnIt(Player bot,Npc npc) {
  for(var p:bot.getPosition().getWorldMapInstance().getPlayersInside())
   if(!p.isPlayerBot() && p.getRace()==bot.getRace() && !p.isDead() && (p.getTarget()==npc || npc.getAggroList().isHating(p)))return true;
  return false;
 }
 /** Kill targets (and a winning boss) within reach and in sight are fair targets. Use targets are not fought. */
 static boolean mechanicEnemy(Field f,Stats stats,Player bot,Npc npc) {
  if(bot.getPosition()==null || !bot.isInInstance() || bot.getWorldId()!=f.map() || npc.isDead() || use(f,npc.getNpcId()))return false;
  boolean yes=value(f,bot,npc)>0 && npc.getWorldMapInstance()==bot.getWorldMapInstance() && PositionUtil.isInRange(bot,npc,RANGE) && PlayerBotTargetEligibility.visible(bot,npc);
  if(yes && stats.seen.add(npc.getObjectId())){stats.targeted.incrementAndGet();if(stats.seen.size()>20000)stats.seen.clear();}
  return yes;
 }
 private static long key(int a,int b){return ((long)a<<32)|(b&0xffffffffL);}
 /** Every set-aside goes through here, so the table stays bounded whichever branch filled it. */
 private static void skip(int bot,Npc npc,long until){if(SKIP.size()>20000)SKIP.clear();SKIP.put(key(bot,npc.getObjectId()),until);}
 private static Npc pick(Field f,Player bot,long now) {
  Npc best=null;double top=0;
  for(var npc:bot.getPosition().getWorldMapInstance().getNpcs()) {
   if(npc.isDead() || npc.getLifeStats().isAboutToDie())continue;
   int value=value(f,bot,npc);if(value<=0)continue;
   Long skip=SKIP.get(key(bot.getObjectId(),npc.getObjectId()));if(skip!=null && now<skip)continue;
   double s=score(value,PositionUtil.getDistance(bot,npc),playerOnIt(bot,npc));
   if(s>top){top=s;best=npc;}
  }
  return best;
 }
 private static void callOut(Field f,Stats stats,Player bot,Npc npc,long now) {
  var team=bot.getCurrentTeam();if(team==null)return;
  long k=key(team.getTeamId(),npc.getObjectId());Long last=CALLED.get(k);if(last!=null && now-last<CALL_MS)return;
  CALLED.put(k,now);if(CALLED.size()>5000)CALLED.clear();stats.calls.incrementAndGet();
  String what=npc.getNpcId()==f.boss()?"the "+npc.getName()+" - this wins it!":use(f,npc.getNpcId())?"the "+npc.getName()+" to take it!":npc.getName()+"!";
  for(var member:team.getMembers())if(!member.isPlayerBot() && member.isOnline())PacketSendUtility.sendPacket(member,new SM_MESSAGE(bot,"Heading for "+what,ChatType.GROUP));
 }
 private static boolean usingNow(Player bot){return bot.getController().hasScheduledTask(TaskId.ACTION_ITEM_NPC);}
 /** The bot's own step inside this map: walk to the best objective; kill targets go to the normal fight, use targets
  * are used through the native dialog (cast bar, standing still until it completes). */
 static boolean travel(Field f,Stats stats,boolean enabled,PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();int id=bot.getObjectId();
  if(!enabled || !session.autonomous() || bot.getPosition()==null || !bot.isInInstance() || bot.getWorldId()!=f.map()){GOALS.remove(id);return false;}
  // A use already under way finishes before anything else (moving would cancel it natively).
  if(usingNow(bot)){navigation.stop();return true;}
  if(!runner(PlayerBotLeanings.pvp(bot)) || !started(bot)){GOALS.remove(id);return false;}
  var goal=GOALS.get(id);Npc npc=null;
  if(goal!=null)for(var n:bot.getPosition().getWorldMapInstance().getNpcs())if(n.getObjectId()==goal.npc()){npc=n;break;}
  if(npc!=null && (npc.isDead() || value(f,bot,npc)<=0)){if(npc.isDead() && npc.getNpcId()==f.boss())stats.bosses.incrementAndGet();npc=null;}
  if(npc==null || now-goal.since()>REPICK_MS) {
   var best=pick(f,bot,now);
   if(best==null){GOALS.remove(id);return false;}
   if(npc==null || best!=npc){npc=best;GOALS.put(id,new Goal(npc.getObjectId(),now));stats.picks.incrementAndGet();callOut(f,stats,bot,npc,now);}
   else GOALS.put(id,new Goal(npc.getObjectId(),now));
  }
  if(use(f,npc.getNpcId())) {
   if(PositionUtil.isInTalkRange(bot,npc) && com.aionemu.gameserver.world.geo.GeoService.getInstance().canSee(bot,npc)) {
    navigation.stop();stats.arrived.incrementAndGet();
    if(!DialogService.isInteractionAllowed(bot,npc)){skip(id,npc,now+UNREACHABLE_MS);GOALS.remove(id);return false;}
    // Asked a moment ago and still not using it (someone else is, or the game refused): try something else for a while.
    Long asked=ASKED.get(key(id,npc.getObjectId()));
    if(asked!=null && now-asked<USE_START_MS+REPICK_MS){if(now-asked>=USE_START_MS){skip(id,npc,now+USE_RETRY_MS);ASKED.remove(key(id,npc.getObjectId()));GOALS.remove(id);return false;}return true;}
    ASKED.put(key(id,npc.getObjectId()),now);if(ASKED.size()>20000)ASKED.clear();
    bot.setTarget(npc);npc.getController().onDialogRequest(bot);stats.uses.incrementAndGet();
    return true;
   }
  } else if(PositionUtil.isInRange(bot,npc,ARRIVED)) {
   stats.arrived.incrementAndGet();navigation.stop();
   if(!bot.isEnemy(npc)){skip(id,npc,now+UNREACHABLE_MS);GOALS.remove(id);return false;}
   bot.setTarget(npc);return false;
  }
  if(!navigation.approach(npc,use(f,npc.getNpcId())?1.5f:3) && navigation.routeFailed()) {
   skip(id,npc,now+UNREACHABLE_MS);
   GOALS.remove(id);stats.unreachable.incrementAndGet();return false;
  }
  return true;
 }
 static void forget(int bot){GOALS.remove(bot);}
 private PlayerBotBattlegroundPlay(){}
}
