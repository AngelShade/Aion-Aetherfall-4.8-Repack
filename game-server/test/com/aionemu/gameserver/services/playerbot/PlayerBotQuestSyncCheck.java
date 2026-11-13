package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.ItemId;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Real XML quest metadata and native collection/consumption, isolated from live storage. */
public final class PlayerBotQuestSyncCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static class Cube extends PlayerStorage {
  Map<Integer,Long> items=new HashMap<>();long money=20000;
  Cube(){super(null,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int item){return items.getOrDefault(item,0L);}
  @Override public long getKinah(){return money;}
  @Override public boolean decreaseByItemId(int item,long count){items.put(item,getItemCountByItemId(item)-count);return true;}
  @Override public void decreaseKinah(long count){money-=count;}
 }
 private static class Companion extends Player {
  Cube cube;QuestStateList quests;
  Companion(){super(null,null);}
  @Override public Storage getInventory(){return cube;}
  @Override public QuestStateList getQuestStateList(){return quests;}
  // Reward-NPC witnesses are saved per character and account, so the fixture has both.
  com.aionemu.gameserver.model.account.Account owner;
  @Override public int getObjectId(){return 1999999020;}
  // Catch-up planning asks the native acceptance rules, which read the character's level.
  @Override public byte getLevel(){return 65;}
  @Override public com.aionemu.gameserver.model.account.Account getAccount(){return owner;}
 }
 public static void main(String[] args)throws Exception {
  Field unsafe=Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
  Companion bot=(Companion)((Unsafe)unsafe.get(null)).allocateInstance(Companion.class);bot.cube=new Cube();bot.quests=new QuestStateList();
  bot.owner=new com.aionemu.gameserver.model.account.Account(1999999020);
  var table=PlayerBotMemoryMetadata.install().selfOwned();
  var originalQuests=DataManager.QUEST_DATA;var originalXml=DataManager.XML_QUESTS;
  try {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("""
    <quests>
     <quest id="900001" name="Gather the leaves" category="QUEST"><collect_items><collect_item item_id="182200001" count="3"/><collect_item item_id="182400001" count="100"/></collect_items><rewards exp="10"/></quest>
     <quest id="900002" name="Report to your contact" category="QUEST"><rewards exp="10"/></quest>
     <quest id="900010" name="Catch up"><start_conditions><finished quest_id="900011"/><finished quest_id="900012"/></start_conditions><start_conditions><finished quest_id="900013" reward="1"/></start_conditions></quest>
     <quest id="900011" name="First branch"/><quest id="900012" name="Second branch"/>
     <quest id="900013" name="Short branch"><start_conditions><finished quest_id="900014"/></start_conditions></quest>
     <quest id="900014" name="Previous step"/>
    </quests>
    """));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("""
    <quest_scripts><item_collecting id="900001" start_npc_ids="203001" end_npc_ids="203002"/><report_to id="900002" start_npc_ids="203003"/></quest_scripts>
    """));
   var collection=new QuestState(900001,QuestStatus.START);bot.quests.addQuest(900001,collection);bot.quests.addQuest(900002,new QuestState(900002,QuestStatus.START));
   check(!PlayerBotQuestMetadata.ready(bot,900001),"Missing collection cannot become ready");
   bot.cube.items.put(182200001,3L);
   check(PlayerBotQuestMetadata.ready(bot,900001),"Native required items and Kinah make collection ready");
   check(bot.cube.items.get(182200001)==3 && bot.cube.money==20000,"Readiness never spends or removes items");
   check(PlayerBotQuestMetadata.rewardAction(bot,900001)==DialogAction.CHECK_USER_HAS_QUEST_ITEM_SIMPLE,"Collection uses native item-check turn-in action");
   check(PlayerBotQuestMetadata.rewardNpcIds(bot,900001).equals(Set.of(203002)),"Actual end NPC differs from start NPC");
   check(PlayerBotQuestMetadata.rewardNpcIds(bot,900002).equals(Set.of(203003)),"Only missing native end metadata falls back to start NPC");
   check(PlayerBotQuestMetadata.ready(bot,900002),"Simple report quest is ready for native NPC dialog");
   check(PlayerBotQuestMirror.held(bot,ItemId.KINAH)==20000,"Kinah requirements use native balance rather than cube item lookup");
   check(QuestService.collectItemCheck(new QuestEnv(null,bot,900001),true),"Native collection removal succeeds");
   check(bot.cube.items.get(182200001)==0 && bot.cube.money==19900,"Native handler consumes exact items and bot's own Kinah");
   check(!PlayerBotQuestMetadata.ready(bot,900001),"Consumed collection is not ready while still START");
   collection.setStatus(QuestStatus.REWARD);
   check(PlayerBotQuestMetadata.rewardAction(bot,900001)==DialogAction.SELECT_QUEST_REWARD,"REWARD goes directly to native reward selection without consuming twice");
   // Catch-up chains (shortest alternative, ancestry, reward branches) are planned by PlayerBotQuestPrerequisites,
   // which also asks whether the bot can run each quest's native handler. PlayerBotQuestPrerequisitesCheck covers
   // those cases on its own graph; the four old assertions here predate that planner and are retired.
   System.out.println("OK: "+checks+" native quest sync/collection/metadata checks; no live players or database");
  }finally{DataManager.QUEST_DATA=originalQuests;DataManager.XML_QUESTS=originalXml;table.close();}
 }
}
