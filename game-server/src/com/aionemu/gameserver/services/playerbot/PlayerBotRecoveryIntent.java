package com.aionemu.gameserver.services.playerbot;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;

/** Transient recovery intent survives standing to cast; it never replaces native rest or resource state. */
final class PlayerBotRecoveryIntent {
 private static final Set<Integer> RECOVERING=ConcurrentHashMap.newKeySet();
 static boolean active(Player bot){return RECOVERING.contains(bot.getObjectId()) || bot.isInState(CreatureState.RESTING);}
 static void start(Player bot){RECOVERING.add(bot.getObjectId());}
 static void clear(Player bot){RECOVERING.remove(bot.getObjectId());}
 static void update(Player bot,boolean engaged,boolean leaderMoving) {
  if(bot.isDead() || engaged || leaderMoving || PlayerBotHealing.health(bot)>=PlayerBotRecoveryPolicy.RECOVERED && PlayerBotDefense.mana(bot)>=PlayerBotRecoveryPolicy.RECOVERED)clear(bot);
  else if(bot.isInState(CreatureState.RESTING))start(bot);
 }
 /** Wait standing only while native periodic effects cover every resource still being recovered. */
 static boolean regenerating(Player bot) {
  boolean health=PlayerBotHealing.health(bot)>=PlayerBotRecoveryPolicy.RECOVERED;
  boolean mana=PlayerBotDefense.mana(bot)>=PlayerBotRecoveryPolicy.RECOVERED;
  for(var effect:bot.getEffectController().getAllEffects()) {
   if(effect.isPassive() || effect.getRemainingTimeMillis()<=0)continue;
   for(var template:effect.getSuccessEffects()) {
    var tick=effect.getReserveds(template.getPosition());if(tick==null || tick.getValue()<=0)continue;
    if(template instanceof com.aionemu.gameserver.skillengine.effect.HealEffect && !bot.getEffectController().isAbnormalSet(com.aionemu.gameserver.skillengine.effect.AbnormalState.DISEASE))health=true;
    if(template instanceof com.aionemu.gameserver.skillengine.effect.MPHealEffect)mana=true;
   }
  }
  return health && mana;
 }
 private PlayerBotRecoveryIntent(){}
}
