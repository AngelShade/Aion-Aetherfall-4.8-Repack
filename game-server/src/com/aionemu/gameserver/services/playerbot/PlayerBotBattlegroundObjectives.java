package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * PvP-3 battleground objectives, a hook of its own (pvp.battlegrounds.objectives): inside a battleground or arena, a
 * hostile NPC within reach (Dredgion crew, Kamar guards, Ophidan and Iron Wall objectives...) is a fair target; each map
 * scores those kills natively. Hunting players stays with PlayerBotPvpSeek; nothing is shared with sieges or bases.
 * Per-map refinements (a Dredgion officer first, a Kamar flag) belong in their own classes, not here.
 */
final class PlayerBotBattlegroundObjectives {
 static final float RANGE=45;
 static final AtomicLong TARGETED=new AtomicLong();
 private static final Set<Integer> SEEN=ConcurrentHashMap.newKeySet();

 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_BATTLEGROUND_OBJECTIVES;}
 static boolean objective(boolean enabled,boolean battleground,boolean ownNpc,boolean enemy,boolean alive,boolean inReach) {
  return enabled && battleground && ownNpc && enemy && alive && inReach;
 }
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party) {
  // battleground() checks the position first, so bots without one (fixtures, map transfer) leave here safely.
  if(!enabled() || !PlayerBotPvpSeek.battleground(bot))return false;
  boolean yes=objective(true,true,npc.getMaster()==npc,bot.isEnemy(npc),!npc.isDead(),
   npc.getWorldMapInstance()==bot.getWorldMapInstance() && PositionUtil.isInRange(bot,npc,RANGE) && PlayerBotTargetEligibility.visible(bot,npc));
  if(yes && SEEN.add(npc.getObjectId())){TARGETED.incrementAndGet();if(SEEN.size()>20000)SEEN.clear();}
  return yes;
 }
 static String stats(){return "enabled="+enabled()+", objective targets="+TARGETED.get();}
 private PlayerBotBattlegroundObjectives(){}
}
