package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.questEngine.handlers.template.*;
import com.aionemu.gameserver.questEngine.model.*;

/** Real native exchange consumption/page/reward handoff, without World/IDs/awards. */
public final class PlayerBotQuestExchangeCheck {
 static int checks;static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static final class Bot extends PlayerBotConversationCheck.Bot {
  Inventory cube;Bot(){super();}@Override public PlayerStorage getInventory(){return cube;}
 }
 static final class Inventory extends PlayerStorage {
  final Map<Integer,Long> held=new HashMap<>();int removals;
  Inventory(Player owner){super(owner,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int id){return held.getOrDefault(id,0L);}
  @Override public boolean decreaseByItemId(int id,long count){return decreaseByItemId(id,count,QuestStatus.START);}
  @Override public boolean decreaseByItemId(int id,long count,QuestStatus status){if(count<=0 || getItemCountByItemId(id)<count)return false;held.put(id,getItemCountByItemId(id)-count);removals++;return true;}
 }
 static final class Relic extends RelicRewards {
  int page,ends;Relic(){super(900201,List.of(203201));}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
  @Override public void updateQuestStatus(QuestEnv env){}
  @Override public boolean sendQuestEndDialog(QuestEnv env){ends++;return true;}
 }
 static final class Fountain extends FountainRewards {
  int page,ends;Fountain(){super(900202,List.of(203202));}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
  @Override public boolean sendQuestEndDialog(QuestEnv env){if(env.getDialogActionId()==DialogAction.USE_OBJECT)return super.sendQuestEndDialog(env);ends++;return true;}
 }
 public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(sun.misc.Unsafe)f.get(null);
  var previous=DataManager.QUEST_DATA;var previousXml=DataManager.XML_QUESTS;
  try {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader(
    "<quests><quest id='900201' name='Relic' category='QUEST'><collect_items><collect_item item_id='182200001' count='2'/><collect_item item_id='182200002' count='3'/><collect_item item_id='182200003' count='4'/><collect_item item_id='182200004' count='5'/></collect_items><rewards exp='10'/><rewards exp='20'/><rewards exp='30'/><rewards exp='40'/></quest><quest id='900202' name='Fountain' category='QUEST'><collect_items><collect_item item_id='182200005' count='2'/></collect_items><rewards exp='10'/></quest></quests>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader(
    "<quest_scripts><relic_rewards id='900201' start_npc_ids='203201'/><fountain_rewards id='900202' start_npc_ids='203202'/></quest_scripts>"));
   var bot=(Bot)u.allocateInstance(Bot.class);bot.quests=new QuestStateList();bot.cube=new Inventory(bot);
   var npc=(PlayerBotConversationCheck.Contact)u.allocateInstance(PlayerBotConversationCheck.Contact.class);npc.id=203201;
   var template=DataManager.QUEST_DATA.getQuestById(900201);
   check(PlayerBotQuestExchange.relicChoice(template,bot.cube::getItemCountByItemId)==-1,"No owned alternative means no planned offering");
   for(int i=0;i<4;i++) {
    bot.quests=new QuestStateList();
    bot.cube.held.clear();bot.cube.removals=0;bot.cube.held.put(182200001+i,20L);
    var state=new QuestState(900201,QuestStatus.START);bot.quests.addQuest(900201,state);var handler=new Relic();
    check(PlayerBotQuestExchange.relicChoice(template,bot.cube::getItemCountByItemId)==i,"Actual owned alternative selects exact reward category");
    check(PlayerBotQuestExchange.ready(bot,900201),"Accepted relic with owned offering is eligible for native selection");
    var env=new QuestEnv(npc,bot,900201,DialogAction.USE_OBJECT);
    check(handler.onDialogEvent(env) && handler.page==1011,"Real native handler opens selection page");
    int action=switch(i){case 0->DialogAction.SELECT1;case 1->DialogAction.SELECT2;case 2->DialogAction.SELECT3;default->DialogAction.SELECT4;};
    env.setDialogActionId(action);check(handler.onDialogEvent(env),"Exact native choice admitted");
    check(state.getStatus()==QuestStatus.REWARD && Objects.equals(state.getRewardGroup(),i),"Native transition owns status and reward category");
    check(bot.cube.getItemCountByItemId(182200001+i)==20-(i+2) && bot.cube.removals==1,"Only selected owned offering consumed once");
    check(handler.page==i+5,"Native reward page matches selected category");
    env.setDialogActionId(DialogAction.SELECTED_QUEST_NOREWARD);check(handler.onDialogEvent(env) && handler.ends==1,"Native final action hands off to real reward service");
    check(bot.cube.removals==1,"Reward handoff does not charge relic again");
    check(!PlayerBotQuestExchange.ready(bot,900201),"Reward stage cannot repeat offering selection");
   }
   npc.id=203202;var state=new QuestState(900202,QuestStatus.REWARD);bot.quests.addQuest(900202,state);bot.cube.held.put(182200005,3L);
   var fountain=new Fountain();var env=new QuestEnv(npc,bot,900202,DialogAction.USE_OBJECT);
   check(fountain.onDialogEvent(env) && fountain.page==5,"Reopening native fountain reward offers actual page instead of abandonment");
   check(state.getStatus()==QuestStatus.REWARD && bot.cube.getItemCountByItemId(182200005)==3,"Reopen preserves saved offering and progress");
   check(PlayerBotQuestMetadata.rewardAction(bot,900202)==DialogAction.USE_OBJECT,"Shared reward planner uses safe fountain reopening");
   check(PlayerBotQuestExchange.eligible(bot,900202),"Fountain admission checks actual full owned collection");
   int before=bot.cube.removals;env.setDialogActionId(DialogAction.SELECTED_QUEST_NOREWARD);
   check(fountain.onDialogEvent(env) && fountain.ends==1,"Native fountain final action owns reward handoff");
   check(bot.cube.getItemCountByItemId(182200005)==1 && bot.cube.removals==before+1,"Native fountain charges exact collection once");
   check(!PlayerBotQuestExchange.eligible(bot,900202),"Consumed offering cannot be reused for another acceptance");
   System.out.println("OK: "+checks+" native exchange category, owned cost, reward reopening and handoff checks; no World/DB/IDs/reward awards");
  } finally {DataManager.QUEST_DATA=previous;DataManager.XML_QUESTS=previousXml;}
 }
}
