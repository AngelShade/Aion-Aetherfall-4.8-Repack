package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.model.DialogAction.*;

/** Exact actions from native _1006Ascension/_2008Ascension. Handlers own every quest mutation/reward. */
final class PlayerBotWorldAscension {
 record Step(int npc,int action) {}
 private static final Map<Integer,Long> RETRY=new ConcurrentHashMap<>();
 static Step step(boolean elyos,int stage) {
  if(elyos)return switch(stage){case 0->new Step(790001,SETPRO1);case 2->new Step(730008,SETPRO2);case 3->new Step(790001,SETPRO3);case 99->new Step(205000,QUEST_SELECT);case 5->new Step(790001,SETPRO4);default->null;};
  return switch(stage){case 0->new Step(203550,SETPRO1);case 1->new Step(790003,SETPRO2);case 2->new Step(790002,SETPRO3);case 3->new Step(203546,SETPRO4);case 4->new Step(203550,SETPRO5);case 99->new Step(205020,QUEST_SELECT);case 6->new Step(203550,SETPRO6);default->null;};
 }
 static int choice(boolean elyos,PlayerClass pc){return switch(pc){
  case GLADIATOR->elyos?SETPRO5:SETPRO7;case TEMPLAR->elyos?SETPRO6:SETPRO8;case ASSASSIN->elyos?SETPRO7:SETPRO9;case RANGER->elyos?SETPRO8:SETPRO10;
  case SORCERER->elyos?SETPRO9:SETPRO11;case SPIRIT_MASTER->elyos?SETPRO10:SETPRO12;case CLERIC->elyos?SETPRO11:SETPRO14;case CHANTER->elyos?SETPRO12:SETPRO13;
  case GUNNER->elyos?SETPRO13:SETPRO15;case BARD->elyos?SETPRO14:SETPRO16;case RIDER->elyos?SETPRO15:SETPRO17;default->0;};}
 static PlayerClass specialization(Player bot) {
  var chosen=PlayerBotService.getInstance().plannedPopulationClass(bot);if(chosen.getStartingClass()==bot.getPlayerClass())return chosen;
  return Arrays.stream(PlayerClass.values()).filter(c->!c.isStartingClass() && c.getStartingClass()==bot.getPlayerClass()).findFirst().orElse(bot.getPlayerClass());
 }
 static boolean pending(Player bot){if(!bot.getPlayerClass().isStartingClass() && bot.getCommonData().isDaeva())return false;int id=bot.getRace()==Race.ELYOS?1006:2008;var q=bot.getQuestStateList().getQuestState(id);return q!=null && (q.getStatus()==QuestStatus.START || q.getStatus()==QuestStatus.REWARD);}
 static void close(Player bot){RETRY.remove(bot.getObjectId());}
 static boolean tick(PlayerBotSession session,PlayerBotNavigation navigation) {
  if(!session.autonomous())return false;Player bot=session.bot();boolean elyos=bot.getRace()==Race.ELYOS;int id=elyos?1006:2008;
  var q=bot.getQuestStateList().getQuestState(id);if(q==null || !pending(bot) || bot.isDead() || bot.getController().isInCombat())return false;
  long now=System.currentTimeMillis();if(now<RETRY.getOrDefault(bot.getObjectId(),0L))return true;
  int stage=q.getQuestVarById(0);
  if(elyos && stage==1 && q.getStatus()==QuestStatus.START) {
   if(!bot.isInsideItemUseZone("LF1_ITEMUSEAREA_Q1006"))return navigation.move(640,1060,100);
   var bottle=bot.getInventory().getFirstItemByItemId(182200007);if(bottle==null)return false;
   navigation.stop();if(!bot.hasCooldown(bottle) && PlayerRestrictions.canUseItem(bot,bottle)) {
    bot.getObserveController().notifyItemuseObservers(bottle);QuestEngine.getInstance().onItemUseEvent(new QuestEnv(null,bot,0),bottle);
    RETRY.put(bot.getObjectId(),now+4000);return true;
   }
   return false;
  }
  Step step=q.getStatus()==QuestStatus.REWARD?new Step(elyos?790001:203550,SELECTED_QUEST_NOREWARD):step(elyos,stage);
  if(step==null)return false;
  List<Npc> npcs=new ArrayList<>();bot.getKnownList().forEachNpc(n->{if(n.getNpcId()==step.npc() && n.isSpawned() && !n.isDead() && !bot.isEnemy(n)
   && n.getWorldId()==bot.getWorldId() && n.getInstanceId()==bot.getInstanceId() && DialogService.isInteractionAllowed(bot,n))npcs.add(n);});
  Npc npc=npcs.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).orElse(null);
  if(npc==null) {
   var place=PlayerBotWorldTravel.places(step.npc()).stream().filter(p->p.map()==bot.getWorldId()).min(Comparator.comparingDouble(p->PlayerBotWorldTravel.distance(bot,p))).orElse(null);
   return place!=null && PlayerBotWorldTravel.move(bot,place,navigation);
  }
  if(!PositionUtil.isInTalkRange(bot,npc) || !GeoService.getInstance().canSee(bot,npc))return navigation.approach(npc,2);
  navigation.stop();RETRY.put(bot.getObjectId(),now+3000);
  try(var dialog=PlayerBotQuestDialog.open(bot.getObjectId(),npc.getObjectId(),id)) {
   npc.getController().onDialogSelect(QUEST_SELECT,0,bot,id,0);
   if(stage==99) {
    if(q.getQuestVarById(0)==50)PlayerBotHeadlessFlight.begin(bot,DataManager.FLY_PATH.getPathTemplate(elyos?1:3));
    return true;
   }
   Object page=PlayerBotQuestConversations.field(dialog,"page");if(!(page instanceof Integer offered) || offered<=0)return false;
   if(!elyos && stage==4 && q.getStatus()==QuestStatus.START)npc.getController().onDialogSelect(SELECT5_1,offered,bot,id,0);
   npc.getController().onDialogSelect(step.action(),offered,bot,id,0);
   if(q.getStatus()==QuestStatus.START && stage==(elyos?5:6)) {
    Object selection=PlayerBotQuestConversations.field(dialog,"page");
    int expected=com.aionemu.gameserver.services.ClassChangeService.getClassSelectionDialogPageId(bot.getRace(),bot.getPlayerClass());
    if(selection instanceof Integer selected && selected==expected && expected!=0)npc.getController().onDialogSelect(choice(elyos,specialization(bot)),selected,bot,id,0);
   }
   return true;
  }finally{DialogService.onCloseDialog(bot,npc);}
 }
 private PlayerBotWorldAscension(){}
}
