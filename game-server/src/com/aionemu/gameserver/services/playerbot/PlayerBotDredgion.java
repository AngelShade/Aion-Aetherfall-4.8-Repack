package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.instancescore.PvpInstanceScore;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * PvP-3a Dredgion objective play, a hook of its own (pvp.battlegrounds.dredgion), beside the generic battleground
 * hook. Inside Baranath, Chantra or Terath Dredgion the bots that do not lean PvP (those keep hunting players through
 * PlayerBotPvpSeek) play the map: the Surkana that scores best for the walk (one a same-race player is already
 * attacking counts double, so bots back the player up), hatches and the officers that open the captain's deck, point
 * officers, and the captain only when killing it wins the run for their side. The tables mirror the map handlers
 * (data/handlers/instance/dredgion), which keep all scoring; PlayerBotDredgionCheck pins them to that source.
 * Each new objective is called out once in team chat.
 */
final class PlayerBotDredgion {
 /** One Dredgion: scored targets (npc id -> points), path openers, the captain and who wins on its death. */
 record Ship(int map,String name,Map<Integer,Integer> points,Set<Integer> openers,int captain,boolean killerWins) {}
 static final Ship BARANATH=new Ship(300110000,"Baranath Dredgion",Map.ofEntries(
  Map.entry(700485,500),Map.entry(700486,500),Map.entry(700495,500),Map.entry(700496,500),Map.entry(700490,600),Map.entry(700491,600),
  Map.entry(700497,700),Map.entry(700498,700),Map.entry(700492,800),Map.entry(700493,800),Map.entry(700487,900),Map.entry(700488,1080),Map.entry(700489,1080)),
  Set.of(700506,700508),214823,true);
 static final Ship CHANTRA=new Ship(300210000,"Chantra Dredgion",Map.ofEntries(
  Map.entry(700838,400),Map.entry(700839,400),Map.entry(700840,700),Map.entry(700848,700),Map.entry(700849,700),Map.entry(700850,700),Map.entry(700851,700),
  Map.entry(700845,800),Map.entry(700846,800),Map.entry(700847,900),Map.entry(700841,1000),Map.entry(700842,1000),Map.entry(700843,1100),Map.entry(700844,1100),
  Map.entry(216941,1000),Map.entry(700836,100)),
  Set.of(730349,730350,730351,730352,216882),216886,false);
 static final Ship TERATH=new Ship(300440000,"Terath Dredgion",Map.ofEntries(
  Map.entry(701441,400),Map.entry(701442,400),Map.entry(701443,700),Map.entry(701451,700),Map.entry(701452,700),Map.entry(701453,700),Map.entry(701454,700),
  Map.entry(701448,800),Map.entry(701449,800),Map.entry(701450,900),Map.entry(701444,1000),Map.entry(701445,1000),Map.entry(701446,1100),Map.entry(701447,1100),
  Map.entry(233377,1000),Map.entry(233370,500),Map.entry(701439,100)),
  Set.of(730570,730571,730572,730573,233378),233371,false);
 static final List<Ship> SHIPS=List.of(BARANATH,CHANTRA,TERATH);
 /** A hatch or deck officer is worth this much to a bot: below most Surkanas, so the near rooms go first. */
 static final int OPENER_VALUE=300,CAPTAIN_VALUE=2000;
 static final float RANGE=45,ARRIVED=15;
 static final long UNREACHABLE_MS=60000,CALL_MS=60000,REPICK_MS=5000;
 static final AtomicLong TARGETED=new AtomicLong(),PICKS=new AtomicLong(),ARRIVALS=new AtomicLong(),UNREACHABLE=new AtomicLong(),CALLS=new AtomicLong(),
  SURKANAS=new AtomicLong(),CAPTAINS=new AtomicLong();
 private record Goal(int npc,long since) {}
 private static final Map<Integer,Goal> GOALS=new ConcurrentHashMap<>();
 private static final Map<Long,Long> SKIP=new ConcurrentHashMap<>(),CALLED=new ConcurrentHashMap<>();
 private static final Set<Integer> SEEN=ConcurrentHashMap.newKeySet();

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_DREDGION;}
 static Ship ship(int map){for(var s:SHIPS)if(s.map()==map)return s;return null;}
 /** What an objective is worth to a bot right now; 0 = not an objective (or a captain that would hand the enemy the win). */
 static int value(Ship ship,int npcId,boolean ownSideLeads) {
  if(ship==null)return 0;
  if(npcId==ship.captain())return ship.killerWins() || ownSideLeads?CAPTAIN_VALUE:0;
  if(ship.openers().contains(npcId))return OPENER_VALUE;
  return ship.points().getOrDefault(npcId,0);
 }
 /** Points for the walk; a same-race player already on it doubles it (bots back the player up). */
 static double score(int value,double distance,boolean playerOnIt){return value<=0?0:value/(Math.max(0,distance)+20)*(playerOnIt?2:1);}
 /** A room's Surkana: a 700xxx/701xxx device worth 400+ (the 100-point items and named officers are not). */
 static boolean surkana(Ship ship,int npcId){return npcId>=700000 && npcId<702000 && ship.points().getOrDefault(npcId,0)>=400;}
 /** Non-PvP bots play the objectives; PvP-leaning bots keep hunting players. */
 static boolean runner(boolean pvpLeaning){return !pvpLeaning;}
 static boolean leading(Player bot) {
  var handler=bot.getPosition().getWorldMapInstance().getInstanceHandler();
  return handler!=null && handler.getInstanceScore() instanceof PvpInstanceScore<?> score && score.getRaceWithHighestPoints()==bot.getRace()
   && score.getPointsByRace(bot.getRace())>score.getPointsByRace(bot.getRace()==Race.ELYOS?Race.ASMODIANS:Race.ELYOS);
 }
 static boolean started(Player bot) {
  var handler=bot.getPosition().getWorldMapInstance().getInstanceHandler();
  return handler!=null && handler.getInstanceScore()!=null && handler.getInstanceScore().getInstanceProgressionType()!=null
   && handler.getInstanceScore().getInstanceProgressionType().isStartProgress();
 }
 private static boolean playerOnIt(Player bot,Npc npc) {
  for(var p:bot.getPosition().getWorldMapInstance().getPlayersInside())
   if(!p.isPlayerBot() && p.getRace()==bot.getRace() && !p.isDead() && (p.getTarget()==npc || npc.getAggroList().isHating(p)))return true;
  return false;
 }
 /** In a Dredgion: Surkanas, openers, point officers and a winning captain within reach and in sight are fair targets. */
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party) {
  // Position first: bots without one (fixtures, map transfer) leave here safely, and only Dredgion maps continue.
  if(!enabled() || bot.getPosition()==null || !bot.isInInstance())return false;
  var ship=ship(bot.getWorldId());if(ship==null || npc.isDead())return false;
  boolean yes=value(ship,npc.getNpcId(),npc.getNpcId()==ship.captain() && !ship.killerWins() && leading(bot))>0
   && npc.getWorldMapInstance()==bot.getWorldMapInstance() && PositionUtil.isInRange(bot,npc,RANGE) && PlayerBotTargetEligibility.visible(bot,npc);
  if(yes && SEEN.add(npc.getObjectId())){TARGETED.incrementAndGet();if(SEEN.size()>20000)SEEN.clear();}
  return yes;
 }
 private static long key(int a,int b){return ((long)a<<32)|(b&0xffffffffL);}
 private static Npc pick(Player bot,Ship ship,long now) {
  boolean leads=!ship.killerWins() && leading(bot);
  Npc best=null;double top=0;
  for(var npc:bot.getPosition().getWorldMapInstance().getNpcs()) {
   if(npc.isDead() || npc.getLifeStats().isAboutToDie())continue;
   int value=value(ship,npc.getNpcId(),leads);if(value<=0)continue;
   Long skip=SKIP.get(key(bot.getObjectId(),npc.getObjectId()));if(skip!=null && now<skip)continue;
   double s=score(value,PositionUtil.getDistance(bot,npc),playerOnIt(bot,npc));
   if(s>top){top=s;best=npc;}
  }
  return best;
 }
 /** One call-out per objective per minute for the whole team, so five bots do not all shout. */
 private static void callOut(Player bot,Npc npc,Ship ship,long now) {
  var team=bot.getCurrentTeam();if(team==null)return;
  long k=key(team.getTeamId(),npc.getObjectId());Long last=CALLED.get(k);if(last!=null && now-last<CALL_MS)return;
  CALLED.put(k,now);if(CALLED.size()>5000)CALLED.clear();CALLS.incrementAndGet();
  int points=ship.points().getOrDefault(npc.getNpcId(),0);
  String what=npc.getNpcId()==ship.captain()?"the captain - let's finish this!":ship.openers().contains(npc.getNpcId())?npc.getName()+" to open the way!"
   :npc.getName()+(points>0?" ("+points+" points)!":"!");
  for(var member:team.getMembers())if(!member.isPlayerBot() && member.isOnline())PacketSendUtility.sendPacket(member,new SM_MESSAGE(bot,"Heading for "+what,ChatType.GROUP));
 }
 /** The bot's own step inside a Dredgion: walk to the best objective; within reach the normal fight takes over. */
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();int id=bot.getObjectId();
  if(!enabled() || !session.autonomous() || bot.getPosition()==null || !bot.isInInstance()){GOALS.remove(id);return false;}
  var ship=ship(bot.getWorldId());
  if(ship==null || !runner(PlayerBotLeanings.pvp(bot)) || !started(bot)){GOALS.remove(id);return false;}
  var goal=GOALS.get(id);Npc npc=null;
  if(goal!=null)for(var n:bot.getPosition().getWorldMapInstance().getNpcs())if(n.getObjectId()==goal.npc()){npc=n;break;}
  if(npc!=null && (npc.isDead() || value(ship,npc.getNpcId(),!ship.killerWins() && leading(bot))<=0)){
   if(npc.isDead() && surkana(ship,npc.getNpcId()))SURKANAS.incrementAndGet();
   if(npc.isDead() && npc.getNpcId()==ship.captain())CAPTAINS.incrementAndGet();
   npc=null;
  }
  // Re-weigh now and then: a player may have started on a better target.
  if(npc==null || now-goal.since()>REPICK_MS) {
   var best=pick(bot,ship,now);
   if(best==null){GOALS.remove(id);return false;}
   if(npc==null || best!=npc){npc=best;GOALS.put(id,new Goal(npc.getObjectId(),now));PICKS.incrementAndGet();callOut(bot,npc,ship,now);}
   else GOALS.put(id,new Goal(npc.getObjectId(),now));
  }
  if(PositionUtil.isInRange(bot,npc,ARRIVED)) {
   ARRIVALS.incrementAndGet();navigation.stop();
   // Not attackable for this bot (the game says no): set it aside for a minute instead of hovering beside it.
   if(!bot.isEnemy(npc)){SKIP.put(key(id,npc.getObjectId()),now+UNREACHABLE_MS);GOALS.remove(id);return false;}
   bot.setTarget(npc);return false;
  }
  if(!navigation.approach(npc,3) && navigation.routeFailed()) {
   // Behind a closed hatch or off the navmesh: try another objective for a minute.
   SKIP.put(key(id,npc.getObjectId()),now+UNREACHABLE_MS);if(SKIP.size()>20000)SKIP.clear();
   GOALS.remove(id);UNREACHABLE.incrementAndGet();return false;
  }
  return true;
 }
 static void forget(int bot){GOALS.remove(bot);}
 static String stats(){return "enabled="+enabled()+", objective targets="+TARGETED.get()+", picks="+PICKS.get()+" arrived="+ARRIVALS.get()+" unreachable="+UNREACHABLE.get()
  +", call-outs="+CALLS.get()+", Surkanas down="+SURKANAS.get()+", captains down="+CAPTAINS.get();}
 private PlayerBotDredgion(){}
}
