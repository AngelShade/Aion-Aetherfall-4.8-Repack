package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.questEngine.model.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Real Decorative Weapons metadata/consumption and party synchronization fixtures. */
public final class PlayerBotPartyBehaviorCheck {
 private static int checks;
 private static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
 private static void field(Object o,String name,Object v)throws Exception{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 private static class Cube extends PlayerStorage {
  Map<Integer,Long> items=new HashMap<>();Cube(){super(null,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int id){return items.getOrDefault(id,0L);}
  @Override public int getFreeSlots(){return 0;}
  @Override public boolean decreaseByItemId(int id,long amount){items.put(id,getItemCountByItemId(id)-amount);return true;}
 }
 private static class Companion extends Player {
  Cube cube;QuestStateList quests;Account account;
  Companion(){super(null,null);}
  @Override public int getObjectId(){return 1999999010;}
  @Override public int getWorldId(){return 210030000;}
  @Override public boolean isInInstance(){return false;}
  @Override public Storage getInventory(){return cube;}
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public Account getAccount(){return account;}
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  Companion bot=(Companion)unsafe.allocateInstance(Companion.class);bot.cube=new Cube();bot.quests=new QuestStateList();bot.account=(Account)unsafe.allocateInstance(Account.class);field(bot.account,"id",1999999010);
  PlayerBotSession session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(session,"owner",bot);field(session,"bot",bot);
  Path behavior=Path.of("config/playerbots/behavior-character-1999999010.properties"),reward=PlayerBotPartyCompletion.path(bot);
  check(!Files.exists(behavior) && !Files.exists(reward),"Fixtures never replace real companion preferences");
  var originalQuests=DataManager.QUEST_DATA;var originalXml=DataManager.XML_QUESTS;var originalItems=DataManager.ITEM_DATA;var originalNpcs=DataManager.NPC_DATA;
  try {
   for(Order order:Order.values()) {
    check(PlayerBotPartyBehavior.catchUpNeeded(61,false,order)==(order==Order.FOLLOW || order==Order.PASSIVE),"60m catch-up respects explicit orders");
    check(PlayerBotPartyBehavior.catchUpNeeded(19,true,order)==(order==Order.FOLLOW || order==Order.PASSIVE),"18m fight-start catch-up respects orders");
    check(!PlayerBotPartyBehavior.catchUpNeeded(18,true,order),"No teleport inside combat formation range");
   }
   check(!PlayerBotPartyBehavior.catchUpNeeded(Double.NaN,true,Order.FOLLOW),"Nonfinite positions cannot teleport");
   check(PlayerBotPartyBehavior.catchUpSpeed(6,6)==1.1,"Bounded catch-up assistance over equal speeds");
   check(PlayerBotPartyBehavior.catchUpSpeed(4,12)==2,"Catch-up speed cap");
   check(PlayerBotPartyBehavior.catchUpSpeed(0,6)==1,"Invalid speed cannot amplify movement");
   for(Role role:Role.values()) {
    check(PlayerBotPartyBehavior.canInitiate(role,true)==(role==Role.TANK),"Tank leads quest pull");
    check(PlayerBotPartyBehavior.canInitiate(role,false)==(role==Role.TANK || role==Role.MELEE || role==Role.RANGED),"Healer/support never initiate untouched quest target");
   }
   PlayerBotPartyBehavior.update(session,Role.TANK,Order.FOLLOW);check(Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(session).get("questCombat")),"Quest combat defaults On");
   PlayerBotPartyBehavior.configure(session,false);PlayerBotPartyBehavior.close(session);
   check(Boolean.FALSE.equals(PlayerBotPartyBehavior.snapshot(session).get("questCombat")),"Off persists across recruitment lifecycle");
   PlayerBotPartyBehavior.configure(session,true);PlayerBotPartyBehavior.close(session);check(Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(session).get("questCombat")),"On persists");
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/quest_data/quest_data.xml").toFile());
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/quest_script_data/verteron.xml").toFile());
   DataManager.ITEM_DATA=new ItemData();DataManager.NPC_DATA=new NpcData();
   QuestState q=new QuestState(1136,QuestStatus.START);bot.quests.addQuest(1136,q);
   var template=DataManager.QUEST_DATA.getQuestById(1136);var drop=template.getQuestDrop().getFirst();
   check(PlayerBotQuests.ordinary(template),"IMPORTANT collection quests are supported");
   check(drop.getNpcId()==700116 && drop.getItemId()==182200511,"Actual Decorative Weapons quest object and item");
   check(PlayerBotQuestObjects.needs(bot,q,drop),"Missing native collection identifies object target");
   check(PlayerBotQuestObjects.objectiveIds(bot,1136).equals(Set.of(700116)),"Journal finds actual collection object");
   check(PlayerBotQuestMetadata.rewardNpcIds(bot,1136).equals(Set.of(203100)),"Actual decorative quest turn-in NPC");
   var journal=PlayerBotQuestJournal.describe(bot,bot,q);
   check(((String)journal.get("guidance")).contains("quest objects"),"Collection guidance replaces custom-dialog fallback");
   var objectives=(List<Map<String,Object>>)journal.get("objectives");
   check(objectives.size()==1 && ((Number)objectives.getFirst().get("required")).intValue()==5,"Journal shows five required decorative weapons");
   bot.cube.items.put(182200511,4L);check(PlayerBotQuestObjects.needs(bot,q,drop),"Four of five still needs object");
   bot.cube.items.put(182200511,5L);check(!PlayerBotQuestObjects.needs(bot,q,drop),"Completed collection stops object looting");
   check(PlayerBotQuestMetadata.ready(bot,1136),"Native five-item collecting handler ready");
   long now=System.currentTimeMillis();var event=new PlayerBotPartyCompletion.Completion(42,1136,1,203100,210030000,1,0,Set.of(bot.getObjectId()),now);
   check(PlayerBotPartyCompletion.eligible(event,42,bot.getObjectId(),true,false,QuestStatus.START,now),"Active party quest can follow successful human turn-in without managed-only restriction");
   check(!PlayerBotPartyCompletion.eligible(event,43,bot.getObjectId(),true,false,QuestStatus.START,now),"Wrong owner event denied");
   check(!PlayerBotPartyCompletion.eligible(event,42,7,true,false,QuestStatus.START,now),"Bots without the quest at turn-in denied");
   check(!PlayerBotPartyCompletion.eligible(event,42,bot.getObjectId(),false,false,QuestStatus.START,now),"Party sync Off denied");
   check(!PlayerBotPartyCompletion.eligible(event,42,bot.getObjectId(),true,true,QuestStatus.START,now),"Declined quest remains skipped");
   check(!PlayerBotPartyCompletion.eligible(event,42,bot.getObjectId(),true,false,QuestStatus.COMPLETE,now),"No duplicated completed quest rewards");
   check(!PlayerBotPartyCompletion.eligible(event,42,bot.getObjectId(),true,false,QuestStatus.START,now+600001),"Expired owner event denied");
   check(PlayerBotPartyCompletion.prepare(bot,q,event),"Native collecting completion preparation");
   check(q.getStatus()==QuestStatus.REWARD && bot.cube.getItemCountByItemId(182200511)==0,"Native handler consumes exactly five items before reward");
   check(PlayerBotPartyCompletion.prepare(bot,q,event) && bot.cube.getItemCountByItemId(182200511)==0,"Already-ready repeat never grants consumed items again");
   PlayerBotPartyCompletion.close(bot);check(PlayerBotPartyCompletion.rewardNpcIds(bot,1136).equals(Set.of(203100)),"Verified turn-in NPC persists across dismissal/restart");
   q.setStatus(QuestStatus.COMPLETE);PlayerBotPartyCompletion.close(bot);check(PlayerBotPartyCompletion.rewardNpcIds(bot,1136).isEmpty(),"Completed reward witness cannot authorize repeat reward");
   System.out.println("OK: "+checks+" party catch-up, role/pull/toggle, actual Decorative Weapons objective/journal/native consumption and completion-witness checks; no live players or database");
  }finally{DataManager.QUEST_DATA=originalQuests;DataManager.XML_QUESTS=originalXml;DataManager.ITEM_DATA=originalItems;DataManager.NPC_DATA=originalNpcs;PlayerBotPartyBehavior.close(session);Files.deleteIfExists(behavior);Files.deleteIfExists(reward);}
 }
}
