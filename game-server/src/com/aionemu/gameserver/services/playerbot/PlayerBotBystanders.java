package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * A free world bot passing a human of its faction in a hard fight joins in until that fight ends.
 * Road Wardens on duty answer through their own distress call. Bots hired or in a group already fight for their party.
 */
public final class PlayerBotBystanders {
 private record Answer(Player human,Npc enemy) {}
 private static final Map<Integer,Answer> ANSWERS=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 private static final AtomicLong HELPS=new AtomicLong();
 private static final long CHECK_MS=1000;
 /** The bot stays with a fight that drifts a little beyond the distance that made it stop. */
 private static final int STAY_FACTOR=2;
 private static final int MINIMUM_BOT_HEALTH=50;

 static long helps(){return HELPS.get();}
 /** Whether this bot's hits are public aid, which leaves the kill and its rewards with the human. */
 public static boolean answering(Player bot){return ANSWERS.containsKey(bot.getObjectId());}
 static boolean helping(Player bot,Npc npc){var answer=ANSWERS.get(bot.getObjectId());return answer!=null && answer.enemy()==npc;}
 static void forget(Player bot){ANSWERS.remove(bot.getObjectId());NEXT.remove(bot.getObjectId());}
 static boolean calls(boolean elite,int humanHealth,int healthPercent){return elite || humanHealth<healthPercent;}
 static boolean room(long answering,int maximum){return answering<maximum;}

 /** Runs at most once a second per bot and reads only the bot's own known list, like the Warden distress check. */
 static void observe(PlayerBotSession session) {
  Player bot=session.bot();
  if(!PlayerBotConfig.BYSTANDERS_ENABLED || !session.autonomous()){forget(bot);return;}
  long now=System.currentTimeMillis();int id=bot.getObjectId();
  if(now<NEXT.getOrDefault(id,0L))return;
  NEXT.put(id,now+CHECK_MS);
  var answer=ANSWERS.get(id);
  if(answer!=null) {
   if(current(bot,answer))return;
   ANSWERS.remove(id);
  }
  if(!free(bot))return;
  Answer[] found={null};
  bot.getKnownList().forEachPlayer(human->{
   if(found[0]==null && callingHuman(bot,human) && room(helpersOf(human),PlayerBotConfig.BYSTANDERS_MAX_PER_PLAYER)) {
    var enemy=enemy(bot,human);
    if(enemy!=null)found[0]=new Answer(human,enemy);
   }
  });
  if(found[0]!=null){ANSWERS.put(id,found[0]);HELPS.incrementAndGet();}
 }
 private static long helpersOf(Player human){return ANSWERS.values().stream().filter(a->a.human()==human).count();}
 private static boolean free(Player bot) {
  return !bot.isDead() && PlayerBotPopulationManager.global(bot) && !bot.isInTeam() && !PlayerBotRoadDuty.onDuty(bot) && !PlayerBotFindGroup.committed(bot)
   && !bot.getController().isInCombat() && !bot.isTrading() && !bot.isFlying() && bot.getFlightPath()==null
   && bot.getLifeStats().getHpPercentage()>=MINIMUM_BOT_HEALTH;
 }
 private static boolean callingHuman(Player bot,Player human) {
  return !human.isPlayerBot() && human.isOnline() && !human.isDead() && human.getRace()==bot.getRace() && !bot.isEnemy(human) && !human.isEnemy(bot)
   && human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId() && !human.isInInstance()
   && human.getController().isInCombat() && PositionUtil.isInRange(bot,human,PlayerBotConfig.BYSTANDERS_RANGE);
 }
 private static boolean hates(Npc npc,Player human) {
  return npc.getAggroList().isHating(human) || human.getSummon()!=null && npc.getAggroList().isHating(human.getSummon());
 }
 private static Npc enemy(Player bot,Player human) {
  Npc[] nearest={null};int health=human.getLifeStats().getHpPercentage();
  human.getKnownList().forEachNpc(npc->{
   if(npc.isDead() || !npc.isSpawned() || npc.getMaster()!=npc || npc.isFlag() || !hates(npc,human) || !bot.isEnemy(npc)
    || !PlayerBotWorldActivities.levelSuitable(bot.getLevel(),npc.getLevel()) || PlayerBotHunting.training(npc.getTribe())
    || !calls(elite(npc),health,PlayerBotConfig.BYSTANDERS_HEALTH_PERCENT)
    || !PositionUtil.isInRange(bot,npc,PlayerBotConfig.BYSTANDERS_RANGE) || !PlayerBotTargetEligibility.visible(bot,npc))return;
   if((nearest[0]==null || PositionUtil.getDistance(bot,npc)<PositionUtil.getDistance(bot,nearest[0])) && GeoService.getInstance().canSee(bot,npc))nearest[0]=npc;
  });
  return nearest[0];
 }
 private static boolean elite(Npc npc){var rating=npc.getObjectTemplate().getRating();return rating!=NpcRating.NORMAL && rating!=NpcRating.JUNK;}
 private static boolean current(Player bot,Answer answer) {
  var human=answer.human();var enemy=answer.enemy();
  return !bot.isDead() && !bot.isInTeam() && human.isOnline() && !human.isDead() && !enemy.isDead() && enemy.isSpawned()
   && human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId() && enemy.getWorldId()==bot.getWorldId() && enemy.getInstanceId()==bot.getInstanceId()
   && hates(enemy,human) && PositionUtil.isInRange(bot,human,PlayerBotConfig.BYSTANDERS_RANGE*STAY_FACTOR);
 }
 private PlayerBotBystanders(){}
}
