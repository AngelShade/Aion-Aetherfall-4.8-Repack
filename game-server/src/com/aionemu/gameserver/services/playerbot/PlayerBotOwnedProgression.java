package com.aionemu.gameserver.services.playerbot;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.ClassChangeService;
import com.aionemu.gameserver.utils.PositionUtil;

/** Owner-directed ascension for real account characters, through the native class owner. */
public final class PlayerBotOwnedProgression {
 private static final ThreadLocal<Boolean> MATCHING=ThreadLocal.withInitial(()->false);
 public static boolean matching(){return MATCHING.get();}
 static boolean recruitmentMatches(int account,int character) {
  try{return Boolean.parseBoolean(PlayerBotMetadata.load(account,character,"care",null).getProperty("levelTogether","true"));}
  catch(java.io.IOException error){throw new IllegalArgumentException("Cannot load that character's progression preference.",error);}
 }
 private static boolean owned(PlayerBotSession session) {
  return !session.world() && !session.generated() && session.owner()!=session.bot()
   && !session.owner().isPlayerBot() && session.bot().getPlayerBotOwnerId()==session.owner().getObjectId()
   && session.owner().getAccount().getId()==session.bot().getAccount().getId();
 }
 static List<String> choices(PlayerBotSession session) {
  if(!owned(session) || !session.bot().getPlayerClass().isStartingClass())return List.of();
  var starting=session.bot().getPlayerClass();
  return Arrays.stream(PlayerClass.values()).filter(c->!c.isStartingClass() && c.getStartingClass()==starting)
   .map(Enum::name).toList();
 }
 static Map<String,Object> snapshot(PlayerBotSession session) {
  return Map.of("ascensionChoices",choices(session),"ascensionReady",owned(session) && session.bot().getLevel()>=9,
   "ownedProgression",owned(session),"levelTogether",owned(session) && PlayerBotQuestSync.state(session).levelTogether);
 }
 public static void levelTogether(PlayerBotSession session,boolean enabled) {
  synchronized(session) {
   if(!PlayerBotService.getInstance().current(session) || !owned(session))throw new IllegalArgumentException("Choose your recruited account character.");
   var state=PlayerBotQuestSync.state(session);boolean previous=state.levelTogether;state.levelTogether=enabled;
   try{state.save();}catch(RuntimeException error){state.levelTogether=previous;throw error;}
  }
 }
 static void tick(PlayerBotSession session) {
  if(!owned(session) || !PlayerBotQuestSync.state(session).levelTogether)return;
  var state=PlayerBotQuestSync.state(session);long now=System.currentTimeMillis();
  if(now<state.nextLevelMatch)return;state.nextLevelMatch=now+2000;
  var bot=session.bot();var owner=session.owner();
  if(!PlayerBotTeams.same(owner,bot) || owner.isDead() || bot.isDead() || !owner.isSpawned() || !bot.isSpawned()
   || bot.getWorldId()!=owner.getWorldId() || bot.getInstanceId()!=owner.getInstanceId()
   || bot.getCommonData().getNoExp() || !PositionUtil.isInRange(owner,bot,100)
   || bot.isCasting() || bot.isLooting() || bot.isTrading() || bot.getController().isInCombat()
   || bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress())return;
  long target=owner.getCommonData().getExp();
  if(bot.getCommonData().getExp()>=target)return;
  // Native non-Daeva ceiling remains authoritative until the owner chooses a class.
  if(bot.getPlayerClass().isStartingClass())target=Math.min(target,
   com.aionemu.gameserver.dataholders.DataManager.PLAYER_EXPERIENCE_TABLE.getStartExpForLevel(10));
  if(bot.getCommonData().getExp()<target) {
   MATCHING.set(true);
   try{bot.getCommonData().setExp(target);}finally{MATCHING.remove();}
  }
 }
 public static void ascend(PlayerBotSession session,String selected) {
  synchronized(session) {
   if(!PlayerBotService.getInstance().current(session) || !owned(session))
    throw new IllegalArgumentException("Choose your recruited account character.");
   var bot=session.bot();
   if(!choices(session).contains(selected))throw new IllegalArgumentException("Choose an advanced class belonging to this character's starting class.");
   if(bot.getLevel()<9)throw new IllegalArgumentException("Ascension is available from level 9.");
   if(bot.isDead() || bot.isCasting() || bot.isTrading() || bot.isLooting() || bot.getController().isInCombat()
    || bot.getInteractionTask()!=null && bot.getInteractionTask().isInProgress())
    throw new IllegalArgumentException("Wait until this character is alive and outside combat or interactions.");
   if(!ClassChangeService.setClass(bot,PlayerClass.valueOf(selected),true,true))
    throw new IllegalArgumentException("Native class selection refused this choice.");
   session.setRole(PlayerBotRules.roleFor(bot.getPlayerClass()));
   PlayerBotQuestSync.notice(bot,"Ascended as "+selected.replace('_',' ')+". My equipment and earned progress are preserved.","owned-ascension",0);
  }
 }
 private PlayerBotOwnedProgression() {}
}
