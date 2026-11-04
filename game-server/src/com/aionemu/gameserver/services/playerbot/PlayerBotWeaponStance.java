package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Supplies the native draw/put-away transition normally initiated by CM_EMOTION. */
final class PlayerBotWeaponStance {
 interface View {
  int state();
  boolean spawned();
  boolean dead();
  boolean moving();
  boolean casting();
  boolean combat();
  long skillReadyAt();
  boolean animationPending(long now);
  void state(int value);
  void notify(EmotionType emotion);
  long drawnUntil();
  void drawnUntil(long value);
 }
 /** A player keeps the weapon out between buffs, heals and pulls; putting it away after every cast reads as flicker. */
 static final long HOLD_MS=8000;
 private static final Map<Integer,Long> DRAWN_UNTIL=new ConcurrentHashMap<>();
 static boolean draw(Player bot) { return transition(view(bot),true); }
 static boolean idle(Player bot,long actionReadyAt) { return transition(view(bot),false,System.currentTimeMillis(),actionReadyAt); }
 static boolean transition(View bot,boolean draw) { return transition(bot,draw,System.currentTimeMillis(),0); }
 static boolean transition(View bot,boolean draw,long now,long actionReadyAt) {
  if(!bot.spawned() || bot.dead())return false;
  // Every reason to hold the weapon extends the hold, even when it is already out.
  if(draw)bot.drawnUntil(now+HOLD_MS);
  // Native instant spells clear casting before their result/hit animation ends.
  // Follow may reset the AI deadline, so native action deadlines remain authoritative.
  if(!draw && (bot.casting() || bot.combat() || now<actionReadyAt || now<bot.skillReadyAt() || bot.animationPending(now) || now<bot.drawnUntil()))return false;
  int before=bot.state(),flag=CreatureState.WEAPON_EQUIPPED.getId();
  int after=draw?before|flag:before&~flag;
  if(before==after)return false;
  bot.state(after);
  if(!draw)bot.drawnUntil(0);
  bot.notify(draw?(bot.moving()?EmotionType.ATTACKMODE_IN_MOVE:EmotionType.ATTACKMODE_IN_STANDING)
   :(bot.moving()?EmotionType.NEUTRALMODE_IN_MOVE:EmotionType.NEUTRALMODE_IN_STANDING));
  return true;
 }
 private static View view(Player bot) {
  return new View() {
   public int state(){return bot.getState();}
   public boolean spawned(){return bot.isSpawned();}
   public boolean dead(){return bot.isDead();}
   public boolean moving(){return bot.getMoveController().isInMove();}
   public boolean casting(){return bot.isCasting();}
   public boolean combat(){return bot.getController().isInCombat() || bot.getAggroList().stream().findAny().isPresent();}
   public long skillReadyAt(){return bot.getNextSkillUse();}
   public boolean animationPending(long now){return bot.isHitTimeBoosted(now);}
   public void state(int value){
    if((value & CreatureState.WEAPON_EQUIPPED.getId())!=0)bot.setState(CreatureState.WEAPON_EQUIPPED);
    else bot.unsetState(CreatureState.WEAPON_EQUIPPED);
   }
   public long drawnUntil(){return DRAWN_UNTIL.getOrDefault(bot.getObjectId(),0L);}
   public void drawnUntil(long value){if(value<=0)DRAWN_UNTIL.remove(bot.getObjectId());else DRAWN_UNTIL.put(bot.getObjectId(),value);}
   public void notify(EmotionType emotion){PacketSendUtility.broadcastToSightedPlayers(bot,new SM_EMOTION(bot,emotion),true);}
  };
 }
 private PlayerBotWeaponStance(){}
}
