package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.template.ItemOrders;
import com.aionemu.gameserver.questEngine.model.*;

/** Cold native handler authority and accepted item-order stages; no World, awards or SQL. */
public final class PlayerBotQuestCapabilityCheck {
 static int checks;static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 static class Order extends ItemOrders {
  int page;Order(int first,int second){super(900105,first,second,203003);}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
  @Override public boolean closeDialogWindow(QuestEnv env){return true;}
  @Override public boolean sendQuestEndDialog(QuestEnv env){page=5;return true;}
  @Override public void updateQuestStatus(QuestEnv env){} // packet projection excluded; native handler still owns variables
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(sun.misc.Unsafe)f.get(null);
  var bot=(PlayerBotConversationCheck.Bot)u.allocateInstance(PlayerBotConversationCheck.Bot.class);bot.quests=new com.aionemu.gameserver.model.gameobjects.player.QuestStateList();
  var npc=(PlayerBotConversationCheck.Contact)u.allocateInstance(PlayerBotConversationCheck.Contact.class);
  var engine=QuestEngine.getInstance();Field handlers=QuestEngine.class.getDeclaredField("questHandlers");handlers.setAccessible(true);
  var registered=(Map<Integer,AbstractQuestHandler>)handlers.get(engine);var before=registered.get(900105);
  var oldQ=DataManager.QUEST_DATA;var oldX=DataManager.XML_QUESTS;
  try(var metadata=PlayerBotMetadataCheck.isolated(7)) {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests><quest id='900105' name='Native item order' category='QUEST'><quest_work_items><quest_work_item item_id='182200001'/></quest_work_items><rewards exp='10'/></quest></quests>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts><item_order id='900105' talk_npc_id1='203001' talk_npc_id2='203002' end_npc_id='203003'/></quest_scripts>"));
   var model=DataManager.XML_QUESTS.getQuest(900105);
   registered.remove(900105);
   check(engine.getPlayerBotHandlerType(900105).isEmpty(),"Missing actual registration is visible");
   check(Boolean.FALSE.equals(PlayerBotQuestCapabilities.describe(bot,900105,model).get("registered")),"Metadata alone cannot authorize a quest");
   var nativeHandler=new Order(203001,203002);registered.put(900105,nativeHandler);
   check(engine.getPlayerBotHandlerType(900105).equals(Order.class.getName()),"Actual registered native class is reported");
   check(PlayerBotQuestCapabilities.describe(bot,900105,model).get("action").equals("NOT_ACTIVE"),"Handler presence does not fabricate acceptance");
   var q=new QuestState(900105,QuestStatus.START);bot.quests.addQuest(900105,q);
   check(!PlayerBotQuestMetadata.ready(bot,900105),"Conversation cannot be skipped for reward");
   check(PlayerBotWorldActivities.activity(bot).get("selected").equals(false),"No invented fallback destination is reported");
   for(int stage=0;stage<2;stage++) {
    var step=PlayerBotQuestConversations.steps(null,bot,900105).getFirst();
    check(step.npc()==203001+stage,"Stage selects exact native order NPC");
    check(PlayerBotQuestCapabilities.describe(bot,900105,model).get("action").equals("CONVERSATION"),"Current action rather than whole-chain support is reported");
    npc.id=step.npc();var env=new QuestEnv(npc,bot,900105);env.setDialogActionId(DialogAction.QUEST_SELECT);
    check(nativeHandler.onDialogEvent(env) && nativeHandler.page==step.page(),"Actual native handler offers the planned page");
    env.setDialogActionId(step.action());check(nativeHandler.onDialogEvent(env),"Actual native order action executes");
    check(q.getQuestVarById(0)==stage+1,"Only native handler changes progress");
    check(q.getStatus()==(stage==0?QuestStatus.START:QuestStatus.REWARD),"Final conversation reaches native reward state");
    check(!PlayerBotQuestConversations.interact(bot,npc,step),"Previous planned step cannot replay after native progress");
   }
   check(PlayerBotQuestConversations.steps(null,bot,900105).isEmpty(),"Reward state has no repeated intermediate action");
   check(PlayerBotQuestMetadata.rewardNpcIds(bot,900105).equals(Set.of(203003)),"Singular native order reward NPC is retained");
   check(PlayerBotQuestCapabilities.describe(bot,900105,model).get("action").equals("REWARD"),"Native reward destination is exposed");
   check(PlayerBotQuestMetadata.coverage(model).contains("native owned-item acceptance"),"Item-start path is identified without claiming whole-chain acceptance");
   registered.remove(900105);
   check(Boolean.FALSE.equals(PlayerBotQuestCapabilities.describe(bot,900105,model).get("registered")),"A saved reward state cannot substitute a missing handler");
   check(PlayerBotQuestCapabilities.missing(new com.aionemu.gameserver.questEngine.handlers.models.KillSpawnedData()).contains("spawner"),"Specific missing spawned-object action is identified");
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts><item_order id='900105' talk_npc_id1='203001' end_npc_id='203003'/></quest_scripts>"));
   q.setStatus(QuestStatus.START);q.setQuestVarById(0,0);nativeHandler=new Order(203001,0);registered.put(900105,nativeHandler);
   check(!PlayerBotQuestMetadata.ready(bot,900105),"Single contact remains required before reward");
   var single=PlayerBotQuestConversations.steps(null,bot,900105).getFirst();check(single.npc()==203001,"Single-contact order selects its real NPC");
   npc.id=single.npc();var env=new QuestEnv(npc,bot,900105);env.setDialogActionId(DialogAction.QUEST_SELECT);
   check(nativeHandler.onDialogEvent(env) && nativeHandler.page==1352,"Single-contact page is authorized natively");
   env.setDialogActionId(single.action());check(nativeHandler.onDialogEvent(env),"Single-contact action reaches native handler");
   check(q.getStatus()==QuestStatus.REWARD,"Single-contact reward status is set only by native handler");
   check(PlayerBotQuestConversations.steps(null,bot,900105).isEmpty(),"Single-contact reward never repeats conversation");
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts><item_order id='900105' end_npc_id='203003'/></quest_scripts>"));
   q.setStatus(QuestStatus.START);q.setQuestVarById(0,0);nativeHandler=new Order(0,0);registered.put(900105,nativeHandler);
   check(PlayerBotQuestMetadata.ready(bot,900105),"Native direct-report order exposes its normal turn-in");
   check(PlayerBotQuestRoutes.ids(bot,bot,900105,PlayerBotQuestRoutes.Kind.REWARD).equals(Set.of(203003)),"Direct-report travel uses exact reward NPC");
   npc.id=203003;env=new QuestEnv(npc,bot,900105);env.setDialogActionId(DialogAction.SELECT_QUEST_REWARD);
   check(nativeHandler.onDialogEvent(env),"Direct-report native handler authorizes reward transition");
   check(q.getStatus()==QuestStatus.REWARD,"Direct-report quest progress is native");
   check(nativeHandler.page==5,"Normal native reward dialog is required");
   check(q.getCompleteCount()==0,"Planning and reward readiness do not grant rewards/completion");
   var missingEnd=DataManager.XML_QUESTS.getQuest(900105);var end=missingEnd.getClass().getDeclaredField("endNpcId");end.setAccessible(true);end.setInt(missingEnd,0);
   check(PlayerBotQuestMetadata.rewardNpcIds(bot,900105).isEmpty(),"Invalid reward NPC is never replaced by a guessed NPC");
   check(PlayerBotQuestCapabilities.describe(bot,900105,missingEnd).get("reason").toString().contains("NPC adapter is missing"),"Reward stage without a native destination is explicit");
   System.out.println("OK: "+checks+" native handler availability and item-order transitions; no World/DB/IDs/rewards");
  }finally{if(before==null)registered.remove(900105);else registered.put(900105,before);DataManager.QUEST_DATA=oldQ;DataManager.XML_QUESTS=oldX;}
 }
}
