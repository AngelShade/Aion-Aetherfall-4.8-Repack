package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.questEngine.model.*;

/** Actual native quest custody and travel/service admission on inert shells;
 * no World, IDs, SQL, tasks, transport or profession changes. */
public final class PlayerBotTownServiceIntegrationCheck {
 static int checks;
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var bot=(PlayerBotFailedTravelCheck.Actor)u.allocateInstance(PlayerBotFailedTravelCheck.Actor.class);
  bot.id=1999999392;bot.world=197;bot.instance=1;bot.setQuestStateList(new QuestStateList());
  var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);
  PlayerBotFailedTravelCheck.set(session,"bot",bot);PlayerBotFailedTravelCheck.set(session,"owner",bot);
  var type=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var state=ctor.newInstance();
  f=PlayerBotWorldActivities.class.getDeclaredField("STATES");f.setAccessible(true);@SuppressWarnings("unchecked") var states=(Map<Integer,Object>)f.get(null);states.put(bot.id,state);
  var writer=PlayerBotWorldActivities.class.getDeclaredMethod("goal",type,PlayerBotWorldActivities.Goal.class);writer.setAccessible(true);
  var need=new PlayerBotQuestCraftingDriver.Need(PlayerBotQuestCrafting.Stage.TRAIN,991989,0,0,40010,Set.of(),null,"native practice");
  var goal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(197,101,50,0,0),50);
  try {
   check(PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Uncommitted autonomous actor retains optional profession preparation");
   writer.invoke(null,state,goal);
   check(!PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Actual committed journey rejects unrelated pre-acceptance profession detour");
   var quest=new QuestState(need.quest(),QuestStatus.START);bot.getQuestStateList().addQuest(quest.getQuestId(),quest);
   check(PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Actual active quest retains its native training/material dependency");
   quest.setStatus(QuestStatus.COMPLETE);
   check(!PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Completed quest cannot supply stale dependency rights");
   bot.x=46;
   check(PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Native arrival returns local optional preparation to eligibility");bot.x=0;
   var owner=(PlayerBotFailedTravelCheck.Actor)u.allocateInstance(PlayerBotFailedTravelCheck.Actor.class);PlayerBotFailedTravelCheck.set(session,"owner",owner);
   check(PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Private companions retain original owner-directed service behavior");PlayerBotFailedTravelCheck.set(session,"owner",bot);
   writer.invoke(null,state,null);PlayerBotFailedTravelCheck.set(state,"vendor",goal.place());
   check(PlayerBotQuestCraftingDriver.preparationUseful(session,need),"Native vendor service does not veto its own service work");
   check(bot.getQuestStateList().getQuestState(need.quest()).getStatus()==QuestStatus.COMPLETE,"Admission probes never accept/reward/advance a quest");
  } finally {PlayerBotWorldActivities.close(bot);}
  System.out.println("OK: "+checks+" actual quest/travel/service admission and preserved native custody checks");
 }
 private PlayerBotTownServiceIntegrationCheck(){}
}
