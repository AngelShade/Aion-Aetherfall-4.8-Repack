package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.RVController;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.rift.RiftManager;
import com.aionemu.gameserver.utils.PositionUtil;

/** Optional native rift excursions. Character/checkpoint SQL owns the durable timers. */
final class PlayerBotRifts {
 private Properties saved;
 private volatile Npc portal;
 private volatile Npc rallyPortal;
 private volatile long rallyUntil;
 private long walkStarted;
 private volatile long visitUntil;
 private boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_RIFTS;}
 boolean inProgress(){return enabled() && (portal!=null || System.currentTimeMillis()<visitUntil);}
 private void load(Player bot,long now) {
  if(saved!=null)return;
  try {
   saved=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"pvp",null);
   visitUntil=Long.parseLong(saved.getProperty("visitUntil","0"));
   if(saved.isEmpty()){saved.setProperty("account",""+bot.getAccount().getId());saved.setProperty("character",""+bot.getObjectId());rest(bot,now);}
  }catch(java.io.IOException e){throw new IllegalStateException("Cannot load world-bot rift timers from SQL",e);}
 }
 private void save(Player bot) {
  try{PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"pvp",saved);}
  catch(java.io.IOException e){throw new IllegalStateException("Cannot queue world-bot rift timers for native checkpoint",e);}
 }
 private void rest(Player bot,long now) {
  long minimum=PlayerBotConfig.PVP_RIFT_REST_MINUTES*60000L;
  saved.setProperty("nextRift",""+(now+minimum+ThreadLocalRandom.current().nextLong(Math.max(1,minimum))));save(bot);
 }
 /** Called even during ordinary work so saved visit custody is restored before population checks. */
 void observe(PlayerBotSession session,long now) {
  if(!session.autonomous() || !enabled())return;
  load(session.bot(),now);
  if(visitUntil>0 && (now>=visitUntil || session.bot().isDead())) {
   visitUntil=0;saved.setProperty("visitUntil","0");save(session.bot());
  }
 }
 boolean tick(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  Player bot=session.bot();
  if(!enabled() || !session.autonomous()){portal=null;return false;}
  observe(session,now);
  var team=bot.getCurrentTeam();
  if(visitUntil>now || bot.isInInstance() || team!=null && team.getMembers().stream().anyMatch(p->!p.isWorldBot() || p.getPlayerBotOwnerId()!=0)
   || bot.isDead() || bot.isFlying()
   || bot.getFlightPath()!=null || bot.isTrading() || bot.isLooting() || bot.isCasting()
   || bot.getController().isInCombat() || !bot.canPerformMove()
   || bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress())return false;
  if(portal==null) {
   // An independent all-bot party follows its leader to the same physical rift.
   // Every member still walks, waits for combat to end and consumes its own entry.
   var leader=team==null || team.getLeaderObject()==bot?null:PlayerBotService.getInstance().currentActor(team.getLeaderObject());
   if(leader!=null && leader.autonomous() && leader.riftRallyUntil()>now && eligible(bot,leader.riftRally())) {
    portal=leader.riftRally();walkStarted=now;
   } else {
   if(leader!=null)return false;
   if(now<Long.parseLong(saved.getProperty("nextRift","0")))return false;
   rest(bot,now);
   if(ThreadLocalRandom.current().nextInt(100)>=PlayerBotConfig.PVP_RIFT_PARTICIPANTS || PlayerBotPvp.hp(bot)<85
    || PlayerBotTerritory.homeland(bot.getWorldId())!=bot.getRace())return false;
   portal=RiftManager.getSpawnedRifts(bot.getWorldId()).stream().filter(n->eligible(bot,n))
    .filter(n->PositionUtil.isInRange(bot,n,PlayerBotConfig.PVP_RIFT_SEARCH_DISTANCE))
    .min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
   if(portal==null)return false;
   walkStarted=now;rallyUntil=now+PlayerBotConfig.PVP_RIFT_TRAVEL_SECONDS*1000L;rallyPortal=portal;
   }
   PlayerBotWorldActivities.close(bot);PlayerBotQuestRoutes.close(bot);
  }
  if(!eligible(bot,portal) || now-walkStarted>PlayerBotConfig.PVP_RIFT_TRAVEL_SECONDS*1000L){portal=null;navigation.stop();return false;}
  if(!PositionUtil.isInRange(bot,portal,4)){navigation.approach(portal,3);return true;}
  var rift=(RVController)portal.getController();
  navigation.stop();
  // Native controller rechecks the portal, level, race, range and capacity at entry.
  boolean crossed=rift.enterPlayerBot(bot);portal=null;
  if(crossed){visitUntil=now+PlayerBotConfig.PVP_RIFT_VISIT_MINUTES*60000L;saved.setProperty("visitUntil",""+visitUntil);rest(bot,now);}
  return crossed;
 }
 Npc rally(){return rallyPortal;}
 long rallyUntil(){return rallyUntil;}
 private static boolean eligible(Player bot,Npc portal){return portal!=null && portal.isSpawned() && !portal.isDead()
  && portal.getWorldId()==bot.getWorldId() && portal.getInstanceId()==bot.getInstanceId()
  && portal.getController() instanceof RVController rift && rift.canPlayerBotEnter(bot);}
}
