package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotPopulationConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;

/**
 * Phase 1.2: a sleeping bot turns in a finished ordinary quest without the cross-map walk. Nobody can see either end
 * (no human on the bot's map or the reward NPC's map), so it moves unseen beside the NPC, then the next turn uses the
 * native reward dialog exactly as an awake bot does: real reward choice, real rewards, the handler's own checks.
 */
final class PlayerBotOffscreenTurnIns {
 /** Per bot: how often a sleeping bot looks for a finished quest, and the short wait between moving and talking. */
 static final long INTERVAL_MS=10*60*1000L,TALK_DELAY_MS=5000L;
 /** Server-wide: each turn scans one map's NPCs, so admissions are rationed. */
 static final int TURNS_PER_SECOND=2;
 static final AtomicLong MOVES=new AtomicLong(),TURN_INS=new AtomicLong(),REFUSED=new AtomicLong();
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 private static long second;private static int turns;

 static boolean enabled(){return PlayerBotPopulationConfig.OFFSCREEN_TURN_INS;}

 /** True when a sleeping bot should get one decision for this. The first check waits a spread interval. */
 static boolean due(PlayerBotSession session,long now) {
  if(!enabled() || !session.autonomous() || !session.dormant())return false;
  int id=session.bot().getObjectId();Long next=NEXT.get(id);
  if(next==null){NEXT.put(id,now+INTERVAL_MS/2+Math.floorMod(id,(int)(INTERVAL_MS/2)));return false;}
  return now>=next;
 }
 /** Moved beside a reward NPC and about to talk: nothing else may move it meanwhile. */
 static boolean talking(int bot,long now){Long next=NEXT.get(bot);return next!=null && next>now && next-now<=TALK_DELAY_MS;}
 static synchronized boolean slot(long now){if(second!=now/1000){second=now/1000;turns=0;}return turns++<TURNS_PER_SECOND;}

 /** One bounded step: talk if already beside a reward NPC, otherwise move unseen next to one. */
 static boolean turn(PlayerBotSession session,long now) {
  if(!due(session,now) || !slot(now))return false;
  var bot=session.bot();int id=bot.getObjectId();NEXT.put(id,now+INTERVAL_MS);
  if(!PlayerBotWorldPlacement.idle(session) || PlayerBotWorldActivity.allowed(session) || PlayerBotPlayerPresence.resident(bot)
   || PlayerBotFindGroup.committed(bot) || PlayerBotRoadDuty.assigned(bot))return false;
  for(var state:bot.getQuestStateList().getAllQuestState()) {
   if(state.getStatus()!=QuestStatus.REWARD)continue;
   int quest=state.getQuestId();var template=DataManager.QUEST_DATA.getQuestById(quest);
   if(!PlayerBotQuests.ordinary(template) || !PlayerBotQuests.autonomous(bot,quest) || PlayerBotQuestExchange.exchange(quest)
    || !PlayerBotQuestMetadata.ready(bot,quest))continue;
   for(int npcId:new TreeSet<>(PlayerBotQuestMetadata.rewardNpcIds(bot,quest))) {
    var npc=beside(bot,npcId);
    if(npc!=null) {
     boolean done=session.turnInOffscreen(npc,quest);
     (done?TURN_INS:REFUSED).incrementAndGet();
     return done;
    }
   }
   for(int npcId:new TreeSet<>(PlayerBotQuestMetadata.rewardNpcIds(bot,quest))) {
    var npc=live(npcId);if(npc==null || PlayerBotWorldActivity.occupiedMap(npc.getWorldId()))continue;
    if(moveBeside(session,npc)){MOVES.incrementAndGet();NEXT.put(id,now+TALK_DELAY_MS);return true;}
   }
  }
  return false;
 }

 /** The reward NPC this bot already stands beside, on its own channel. */
 private static Npc beside(Player bot,int npcId) {
  Npc[] found={null};
  bot.getKnownList().forEachNpc(npc->{if(found[0]==null && npc.getNpcId()==npcId && usable(npc) && npc.getInstanceId()==bot.getInstanceId()
   && PositionUtil.isInTalkRange(bot,npc))found[0]=npc;});
  return found[0];
 }
 private static boolean usable(Npc npc){return npc!=null && npc.isSpawned() && !npc.isDead() && npc.getMaster()==npc;}

 /** A live, spawned reward NPC on an open-world map from its native permanent spawns. */
 private static Npc live(int npcId) {
  for(int map:PlayerBotWorldTravel.places(npcId).stream().map(PlayerBotWorldTravel.Place::map).distinct().sorted().toList()) {
   var world=World.getInstance().getWorldMap(map);if(world==null)continue;
   var npc=world.getMainWorldMapInstance().getNpc(npcId);
   if(usable(npc) && !npc.isInInstance())return npc;
  }
  return null;
 }

 /** Lands a step in front of the NPC through the normal placement guards (no human near either end, free landing). */
 /** Also stages PvP rift invaders beside a rift on their own empty homeland. */
 static boolean moveBeside(PlayerBotSession session,Npc npc) {
  var bot=session.bot();
  // A bot that fell asleep sitting must stand before the idle placement guard admits it; nobody is on the map to see.
  session.standUp();
  for(int attempt=0;attempt<4;attempt++) {
   double angle=npc.getHeading()*Math.PI/60+attempt*Math.PI/2;
   float x=npc.getX()+(float)(Math.cos(angle)*2),y=npc.getY()+(float)(Math.sin(angle)*2);
   // Native floor at the step, close to the NPC's own height: never a ledge, roof or table top.
   var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();
   float z=geo.getZ(npc.getWorldId(),x,y,npc.getZ()+2,npc.getZ()-4,1);
   if(!Float.isFinite(z) || Math.abs(z-npc.getZ())>1.5f || !geo.isPlayerBotLanding(npc.getWorldId(),1,x,y,z))continue;
   var landing=new PlayerBotWorldPlacement.Location(bot.getRace(),npc.getWorldId(),x,y,z,1,65,false,false);
   if(PlayerBotWorldPlacement.placeAt(session,landing))return true;
  }
  return false;
 }

 static void closed(int bot){NEXT.remove(bot);}
 static String stats(){return "offscreen turn-ins="+TURN_INS.get()+" (moves "+MOVES.get()+", refused "+REFUSED.get()+")";}
 private PlayerBotOffscreenTurnIns(){}
}
