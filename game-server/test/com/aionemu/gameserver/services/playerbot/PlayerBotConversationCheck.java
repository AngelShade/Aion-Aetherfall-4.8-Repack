package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.questEngine.handlers.models.NpcInfos;
import com.aionemu.gameserver.questEngine.handlers.template.ReportToMany;

/** Exercise actual intermediate native handler transitions, not reward shortcuts. */
public final class PlayerBotConversationCheck {
 static int checks;static void check(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
 static class Bot extends Player {QuestStateList quests;Bot(){super(null,null);}@Override public QuestStateList getQuestStateList(){return quests;}@Override public boolean isPlayerBot(){return true;}}
 static class Contact extends Npc {int id;Contact(){super(null,null,null);}@Override public int getNpcId(){return id;}@Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate(){return new com.aionemu.gameserver.model.templates.npc.NpcTemplate(){@Override public int getTemplateId(){return id;}};}}
 static class Report extends ReportToMany {
  int page;Report(List<NpcInfos> infos){super(900101,0,List.of(203000),infos,0,false);}
  @Override public boolean closeDialogWindow(QuestEnv env){return true;}
  @Override public boolean sendQuestDialog(QuestEnv env,int page){this.page=page;return true;}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);Bot bot=(Bot)u.allocateInstance(Bot.class);bot.quests=new QuestStateList();Contact npc=(Contact)u.allocateInstance(Contact.class);
  var oldQuests=DataManager.QUEST_DATA;var oldXml=DataManager.XML_QUESTS;
  try {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests><quest id='900101' name='Three conversations' category='QUEST'><rewards exp='10'/></quest><quest id='900102' name='Talk before collecting' category='QUEST'><rewards exp='10'/></quest></quests>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts><report_to_many id='900101' start_npc_ids='203000'><npc_infos npc_ids='203001'/><npc_infos npc_ids='203002'/><npc_infos npc_ids='203003'/></report_to_many><item_collecting id='900102' start_npc_ids='203000' next_npc_id='203010' end_npc_ids='203011'/></quest_scripts>"));
   var q=new QuestState(900101,QuestStatus.START);bot.quests.addQuest(900101,q);
   var infos=(List<NpcInfos>)(List<?>)PlayerBotQuestConversations.list(DataManager.XML_QUESTS.getQuest(900101),"npcInfos");var nativeHandler=new Report(infos);
   for(int stage=0;stage<2;stage++) {
    check(!PlayerBotQuestMetadata.ready(bot,900101),"Intermediate conversation is not a reward objective");
    var step=PlayerBotQuestConversations.steps(null,bot,900101).getFirst();check(step.npc()==203001+stage,"Correct NPC for current objective");npc.id=step.npc();
    var env=new QuestEnv(npc,bot,900101);env.setDialogActionId(DialogAction.QUEST_SELECT);check(nativeHandler.onDialogEvent(env) && nativeHandler.page==step.page(),"Native handler authorizes exact intermediate page");
    env.setDialogActionId(step.action());check(nativeHandler.onDialogEvent(env),"Native conversation action succeeds");
    check(q.getQuestVarById(0)==stage+1 && q.getStatus()==QuestStatus.START,"Conversation advances one objective and does not complete the quest");
    check(!nativeHandler.onDialogEvent(env),"Previous objective cannot be advanced twice at old NPC");
   }
   check(PlayerBotQuestConversations.steps(null,bot,900101).isEmpty(),"Reward NPC is excluded from intermediate action list");check(PlayerBotQuestMetadata.ready(bot,900101),"Final native conversation reaches normal turn-in path");
   check(PlayerBotQuestMetadata.rewardNpcIds(bot,900101).equals(Set.of(203003)),"Reward NPC differs from start/intermediate NPCs");
   var collecting=new QuestState(900102,QuestStatus.START);bot.quests.addQuest(900102,collecting);
   check(!PlayerBotQuestMetadata.ready(bot,900102),"Collection cannot skip required intermediate contact");check(PlayerBotQuestConversations.objectiveIds(null,bot,900102).equals(Set.of(203010)),"Collection journal identifies required next contact");
   collecting.setQuestVarById(0,1);check(PlayerBotQuestConversations.steps(null,bot,900102).isEmpty(),"Completed contact is not repeated");
   System.out.println("OK: "+checks+" actual native conversation transitions, next NPC, repeat protection and reward separation checks; no player/database changes");
  }finally{DataManager.QUEST_DATA=oldQuests;DataManager.XML_QUESTS=oldXml;}
 }
}
