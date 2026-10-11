package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.XMLQuests;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.handlers.models.QuestSkillData;
import com.aionemu.gameserver.questEngine.handlers.template.SkillUse;
import com.aionemu.gameserver.questEngine.model.*;

/** Native event counters and dialog authorization, without World, awards or casts. */
public final class PlayerBotQuestSkillCounterCheck {
 private static int checks;
 private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 private static final class Handler extends SkillUse {
  int page,updates;
  Handler(List<QuestSkillData> skills){super(900143,List.of(203001),List.of(203002),skills);}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
  @Override public void updateQuestStatus(QuestEnv env){updates++;}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
  var bot=(PlayerBotConversationCheck.Bot)unsafe.allocateInstance(PlayerBotConversationCheck.Bot.class);
  bot.quests=new com.aionemu.gameserver.model.gameobjects.player.QuestStateList();
  var npc=(PlayerBotConversationCheck.Contact)unsafe.allocateInstance(PlayerBotConversationCheck.Contact.class);npc.id=203002;
  var xml=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader(
   "<quest_scripts><skill_use id='900143' start_npc_ids='203001' end_npc_ids='203002'><skill ids='9912' end_var='100'/><skill ids='1699 4646' var_num='2' end_var='2'/></skill_use></quest_scripts>"));
  var requirements=(List<QuestSkillData>)(List<?>)PlayerBotQuestConversations.list(xml.getQuest(900143),"skills");
  var before=com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA;
  com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA=(com.aionemu.gameserver.dataholders.QuestsData)JAXBContext.newInstance(com.aionemu.gameserver.dataholders.QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests><quest id='900143' name='Counter fixture' category='QUEST'><rewards exp='10'/></quest></quests>"));
  try {
  var handler=new Handler(requirements);var state=new QuestState(900143,QuestStatus.START);bot.quests.addQuest(900143,state);
  var env=new QuestEnv(npc,bot,900143,DialogAction.SELECT_QUEST_REWARD);
  check(!handler.onDialogEvent(env),"Reward dialog cannot bypass skill requirements");
  check(state.getStatus()==QuestStatus.START && handler.page==0,"Rejected reward cannot mutate stage or offer a reward");
  for(int use=1;use<=100;use++) {
   check(handler.onUseSkillEvent(env,9912),"Real native skill event advances its declared counter");
   check(SkillUse.currentCount(state,requirements.getFirst())==use,"Native six-bit count includes carries above 63");
   check(state.getStatus()==QuestStatus.START,"One completed requirement cannot finish another missing requirement");
  }
  check(!handler.onUseSkillEvent(env,9912),"Completed counter cannot overflow");
  check(!handler.onDialogEvent(env),"Partially satisfied multiple requirements cannot turn in");
  check(handler.getPlayerBotActions(bot).stream().noneMatch(a->a.targetId()==9912),"Completed skill is removed from current intent");
  check(handler.getPlayerBotActions(bot).stream().map(QuestBotAction::targetId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(1699,4646)),"Remaining alternative skills preserve the actual native requirement");
  check(handler.onUseSkillEvent(env,1699) && state.getStatus()==QuestStatus.START,"First alternate use remains incomplete");
  check(handler.onUseSkillEvent(env,4646) && state.getStatus()==QuestStatus.REWARD,"Combined alternative uses finish all requirements natively");
  check(state.getCompleteCount()==0,"Native reward readiness does not grant completion or rewards");
  check(SkillUse.currentCount(state,requirements.getFirst())==100,"Other counters remain intact on reward transition");
  check(!handler.onUseSkillEvent(env,1699),"Reward stage cannot replay skill credit");
  System.out.println("OK: "+checks+" native skill-counter, carry, multi-requirement and reward-admission checks; no World/DB/IDs/casts");
  } finally { com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA=before; }
 }
}
