/* WoW QuestAction::CompleteQuest fills objectives/items then uses normal reward
 * selection. Leader turn-in mirroring is explicitly requested Aion behavior.
 * GPL-2.0-or-later; pinned revision and attribution in third-party/playerbots. */
package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemId;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.utils.*;

public final class PlayerBotPartyCompletion {
 record Completion(int owner,int quest,int count,int npc,int map,int instance,int variables,Set<Integer> bots,long time) {}
 private static final Map<Long,Completion> EVENTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Map<Integer,Integer>> REWARD_NPCS=new ConcurrentHashMap<>();
 private static final Map<Integer,Set<Long>> APPLIED=new ConcurrentHashMap<>();
 private static long key(int owner,int quest){return (long)owner<<32 | quest&0xffffffffL;}
 /** Called only after the human's native finishQuest succeeds, before vars reset. */
 public static void ownerCompleted(QuestEnv env) {
  try {
  Player owner=env.getPlayer();if(owner.isPlayerBot() || !com.aionemu.gameserver.configs.main.PlayerBotConfig.ENABLED || owner.getPlayerGroup()==null)return;
  var q=owner.getQuestStateList().getQuestState(env.getQuestId());if(q==null || q.getStatus()!=QuestStatus.COMPLETE || !(env.getVisibleObject() instanceof Npc npc))return;
  Set<Integer> bots=new HashSet<>();for(Player p:owner.getPlayerGroup().getMembers())if(p.isPlayerBot() && p.getPlayerBotOwnerId()==owner.getObjectId()) {
   var owned=p.getQuestStateList().getQuestState(q.getQuestId());if(owned!=null && (owned.getStatus()==QuestStatus.START || owned.getStatus()==QuestStatus.REWARD))bots.add(p.getObjectId());
  }
  if(bots.isEmpty())return;
  long now=System.currentTimeMillis();EVENTS.values().removeIf(e->now-e.time()>600000);
  EVENTS.put(key(owner.getObjectId(),q.getQuestId()),new Completion(owner.getObjectId(),q.getQuestId(),q.getCompleteCount(),npc.getNpcId(),owner.getWorldId(),owner.getInstanceId(),q.getQuestVars().getQuestVars(),Set.copyOf(bots),now));
  }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(PlayerBotPartyCompletion.class).error("Companion completion observation failed; the player's native quest rewards were retained",error);}
 }
 static void close(Player bot){APPLIED.remove(bot.getObjectId());REWARD_NPCS.remove(bot.getObjectId());}
 static java.nio.file.Path path(Player bot){return java.nio.file.Path.of("config/playerbots/party-rewards-character-"+bot.getObjectId()+".properties");}
 static void remember(Player bot,int quest,int npc) {
  var ids=REWARD_NPCS.computeIfAbsent(bot.getObjectId(),id->load(bot));ids.put(quest,npc);
  try {
   var p=new Properties();p.setProperty("account",Integer.toString(bot.getAccount().getId()));p.setProperty("character",Integer.toString(bot.getObjectId()));
   for(var e:ids.entrySet()){var state=bot.getQuestStateList().getQuestState(e.getKey());if(state!=null && state.getStatus()==QuestStatus.REWARD)p.setProperty("npc."+e.getKey(),Integer.toString(e.getValue()));}
   PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"party-rewards",p);
  }catch(java.io.IOException error){throw new IllegalStateException("Cannot preserve companion reward NPC",error);}
 }
 static Map<Integer,Integer> load(Player bot) {
  Map<Integer,Integer> ids=new ConcurrentHashMap<>();
  try {
   var p=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"party-rewards",path(bot));
   for(String key:p.stringPropertyNames())if(key.startsWith("npc.")){int q=Integer.parseInt(key.substring(4));var state=bot.getQuestStateList().getQuestState(q);if(state!=null && state.getStatus()==QuestStatus.REWARD)ids.put(q,Integer.parseInt(p.getProperty(key)));}
   return ids;
  }catch(Exception error){throw new IllegalStateException("Cannot load companion reward NPCs",error);}
 }
 static Set<Integer> rewardNpcIds(Player bot,int quest){Integer id=REWARD_NPCS.computeIfAbsent(bot.getObjectId(),n->load(bot)).get(quest);return id==null ? Set.of() : Set.of(id);}
 static boolean eligible(Completion event,int owner,int bot,boolean enabled,boolean skipped,QuestStatus status,long now){return event.owner()==owner && event.bots().contains(bot) && enabled && !skipped && (status==QuestStatus.START || status==QuestStatus.REWARD) && now-event.time()>=0 && now-event.time()<600000;}
 static boolean prepare(Player bot,QuestState quest,Completion completion) {
  var t=DataManager.QUEST_DATA.getQuestById(quest.getQuestId());if(t==null)return false;
  if(quest.getStatus()==QuestStatus.REWARD){remember(bot,quest.getQuestId(),completion.npc());return true;}
  Map<Integer,Long> items=new TreeMap<>();
  if(t.getQuestWorkItems()!=null)for(var i:t.getQuestWorkItems().getQuestWorkItem())items.merge(i.getItemId(),i.getCount(),Math::max);
  if(t.getCollectItems()!=null)for(var i:t.getCollectItems().getCollectItem())items.merge(i.getItemId(),i.getCount().longValue(),Math::max);
  else if(t.getInventoryItems()!=null)for(var i:t.getInventoryItems().getInventoryItems())if(i.getCount()!=null)items.merge(i.getItemId(),i.getCount().longValue(),Math::max);
  long slots=0;for(var e:items.entrySet())if(e.getValue()>PlayerBotQuestMirror.held(bot,e.getKey())){if(e.getKey()==ItemId.KINAH || DataManager.ITEM_DATA.getItemTemplate(e.getKey())==null)return false;slots++;}
  if(slots>bot.getInventory().getFreeSlots())return false;
  for(var e:items.entrySet()){long missing=e.getValue()-PlayerBotQuestMirror.held(bot,e.getKey());if(missing>0)ItemService.addItem(bot,e.getKey(),missing);if(PlayerBotQuestMirror.held(bot,e.getKey())<e.getValue())return false;}
  // Standard collecting handlers consume on CHECK_USER_HAS_QUEST_ITEM. Remove
  // exactly once before changing START to REWARD; custom handlers own their work items.
  if(quest.getStatus()==QuestStatus.START) {
   var model=DataManager.XML_QUESTS==null ? null : DataManager.XML_QUESTS.getQuest(quest.getQuestId());
   if(model!=null && model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.ItemCollectingData.class && !QuestService.collectItemCheck(new QuestEnv(null,bot,quest.getQuestId()),true))return false;
   quest.setQuestVar(completion.variables());quest.setStatus(QuestStatus.REWARD);
   PacketSendUtility.sendPacket(bot,new com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION(com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION.ActionType.UPDATE,quest));
  }
  remember(bot,quest.getQuestId(),completion.npc());return true;
 }
 static void tick(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();var settings=PlayerBotQuestSync.state(session);long now=System.currentTimeMillis();
  if(bot.isDead() || owner.isDead() || bot.getController().isInCombat() || owner.getController().isInCombat())return;
  for(var quest:bot.getQuestStateList().getUncompletedQuests()) {
   Completion event=EVENTS.get(key(owner.getObjectId(),quest.getQuestId()));
   if(event==null || !eligible(event,owner.getObjectId(),bot.getObjectId(),settings.partySync,settings.skipped.contains(quest.getQuestId()),quest.getStatus(),now))continue;
   long token=key(quest.getQuestId(),event.count());Set<Long> done=APPLIED.computeIfAbsent(bot.getObjectId(),id->ConcurrentHashMap.newKeySet());if(done.contains(token))continue;
   if(prepare(bot,quest,event)) {
    settings.managed.add(quest.getQuestId());settings.ownerCompletions.put(quest.getQuestId(),event.count()-1);settings.prompts.remove(quest.getQuestId());settings.save();done.add(token);
    PlayerBotQuestSync.notice(bot,"You completed "+ChatUtil.quest(quest.getQuestId())+". I will turn mine in and choose rewards for my class and equipment build.","party-finish:"+quest.getQuestId(),1000);
   }else PlayerBotQuestSync.notice(bot,"I cannot turn in "+ChatUtil.quest(quest.getQuestId())+" yet; I need inventory space or my own required Kinah.","party-finish-blocked:"+quest.getQuestId(),30000);
  }
 }
 private PlayerBotPartyCompletion(){}
}
