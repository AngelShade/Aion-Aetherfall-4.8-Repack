package com.aionemu.gameserver.services.playerbot;

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
 }
 static boolean draw(Player bot) { return transition(view(bot),true); }
 static boolean idle(Player bot,long actionReadyAt) { return transition(view(bot),false,System.currentTimeMillis(),actionReadyAt); }
 static boolean transition(View bot,boolean draw) { return transition(bot,draw,System.currentTimeMillis(),0); }
 static boolean transition(View bot,boolean draw,long now,long actionReadyAt) {
  if(!bot.spawned() || bot.dead())return false;
  // Native instant spells clear casting before their result/hit animation ends.
  // Follow may reset the AI deadline, so native action deadlines remain authoritative.
  if(!draw && (bot.casting() || bot.combat() || now<actionReadyAt || now<bot.skillReadyAt() || bot.animationPending(now)))return false;
  int before=bot.state(),flag=CreatureState.WEAPON_EQUIPPED.getId();
  int after=draw?before|flag:before&~flag;
  if(before==after)return false;
  bot.state(after);
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
   public void notify(EmotionType emotion){PacketSendUtility.broadcastToSightedPlayers(bot,new SM_EMOTION(bot,emotion),true);}
  };
 }
 private PlayerBotWeaponStance(){}
}
