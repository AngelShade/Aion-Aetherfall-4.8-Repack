package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.ExchangeService;
import com.aionemu.gameserver.services.drop.DropService;
import com.aionemu.gameserver.utils.PositionUtil;

/** User-requested distance recall. Cancel native activity, retain custody/builds, resume FOLLOW. */
public final class PlayerBotRecall {
 static final double DISTANCE=60;
 private static final ThreadLocal<PlayerBotSession> CURRENT=new ThreadLocal<>();
 private static final Map<Integer,Long> RETRY=new ConcurrentHashMap<>();
 static boolean eligible(PlayerBotSession s) {
  Player owner=s.owner(),bot=s.bot();
  return !s.closing() && owner.isOnline() && owner.isSpawned() && !owner.isDead() && owner.getWorldMapInstance()!=null
   && bot.isPlayerBot() && bot.getPlayerBotOwnerId()==owner.getObjectId()
   && owner.getPlayerGroup()!=null && bot.getPlayerGroup()==owner.getPlayerGroup();
 }
 static boolean needed(double distance,boolean differentMap) { return differentMap || Double.isFinite(distance) && distance>DISTANCE; }
 static boolean recalling(PlayerBotSession s) { return CURRENT.get()==s; }
 public static boolean recalling(Player bot) { var s=CURRENT.get();return s!=null && s.bot()==bot; }
 static boolean scoped(PlayerBotSession s,BooleanSupplier transition) {
  if(!eligible(s))return false;
  var previous=CURRENT.get();CURRENT.set(s);
  try{return transition.getAsBoolean();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
 }
 static boolean recall(PlayerBotSession s) {
  return scoped(s,()->{
   Player bot=s.bot();
   if(bot.isDead())PlayerBotRecovery.revive(s);
   // Native exchange cancellation returns offers through existing custody guards.
   if(bot.isTrading())ExchangeService.getInstance().cancelExchange(bot);
   if(bot.isTrading())throw new IllegalArgumentException("The companion trade is still committing; retry Summon.");
   if(!eligible(s))return false;
   bot.getController().cancelCurrentSkill(null);bot.getController().cancelUseItem();
   bot.getObserveController().notifyMoveObservers();
   if(bot.isLooting())DropService.getInstance().closeDropList(bot,bot.getLootingNpcOid());
   s.order(PlayerBotRules.Order.FOLLOW);
   if(!PlayerBotService.getInstance().relocate(s))return false;
   PlayerBotTravel.close(s);PlayerBotQuestSync.returnToOwner(bot);
   return true;
  });
 }
 static boolean automatic(PlayerBotSession s) {
  if(!eligible(s) || s.bot().isDead())return false;
  Player owner=s.owner(),bot=s.bot();
  if(!needed(PositionUtil.getDistance(owner,bot),PlayerBotTransfers.different(owner,bot)) && !RETRY.containsKey(bot.getObjectId()))return false;
  long now=System.currentTimeMillis();if(now<RETRY.getOrDefault(bot.getObjectId(),0L))return true;
  try {
   boolean moved=recall(s);if(moved)RETRY.remove(bot.getObjectId());else RETRY.put(bot.getObjectId(),now+2000);return true;
  } catch(RuntimeException error) {
   RETRY.put(bot.getObjectId(),now+3000);
   org.slf4j.LoggerFactory.getLogger(PlayerBotRecall.class).error("Companion {} recall retained for retry",bot.getName(),error);
   return true;
  }
 }
 static void close(Player bot){RETRY.remove(bot.getObjectId());}
 private PlayerBotRecall(){}
}
