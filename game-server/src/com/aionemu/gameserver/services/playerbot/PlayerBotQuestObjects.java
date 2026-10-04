/* Native Aion adapter for WoW OpenLootAction's quest-gated game-object handling.
 * Pinned source: mod-playerbots LootAction.cpp, GPL-2.0-or-later. */
package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.quest.QuestDrop;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.*;
import com.aionemu.gameserver.services.drop.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

public final class PlayerBotQuestObjects {
 private static final Map<Integer,Npc> PENDING=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> RETRY=new ConcurrentHashMap<>();
 record Claim(int bot,long until) {}
 private static final Map<Integer,Claim> CLAIMS=new ConcurrentHashMap<>();
 static void close(Player bot){PENDING.remove(bot.getObjectId());RETRY.remove(bot.getObjectId());CLAIMS.values().removeIf(c->c.bot()==bot.getObjectId());}
 static boolean needs(Player bot,QuestState q,QuestDrop drop) {
  if(q==null || q.getStatus()!=QuestStatus.START || PlayerBotQuestSync.skipped(bot,q.getQuestId()) || drop.getCollectingStep()!=0 && drop.getCollectingStep()!=q.getQuestVarById(0))return false;
  var t=DataManager.QUEST_DATA.getQuestById(q.getQuestId());if(t==null)return false;
  if(drop instanceof com.aionemu.gameserver.model.templates.quest.HandlerSideDrop h)return bot.getInventory().getItemCountByItemId(drop.getItemId())<h.getNeededAmount();
  if(t.getCollectItems()!=null)return t.getCollectItems().getCollectItem().stream().anyMatch(i->Objects.equals(i.getItemId(),drop.getItemId()) && bot.getInventory().getItemCountByItemId(i.getItemId())<i.getCount());
  if(t.getInventoryItems()!=null)return t.getInventoryItems().getInventoryItems().stream().anyMatch(i->Objects.equals(i.getItemId(),drop.getItemId()) && i.getCount()!=null && bot.getInventory().getItemCountByItemId(i.getItemId())<i.getCount());
  if(t.getQuestWorkItems()!=null)return t.getQuestWorkItems().getQuestWorkItem().stream().anyMatch(i->Objects.equals(i.getItemId(),drop.getItemId()) && bot.getInventory().getItemCountByItemId(i.getItemId())<i.getCount());
  return false;
 }
 static Set<Integer> objectiveIds(Player bot,int quest) {
  var q=bot.getQuestStateList().getQuestState(quest);var t=DataManager.QUEST_DATA.getQuestById(quest);Set<Integer> ids=new TreeSet<>();
  if(t!=null)for(var d:t.getQuestDrop())if(needs(bot,q,d))ids.add(d.getNpcId());return ids;
 }
 static boolean needed(Player bot,Npc npc){return QuestService.getQuestDrop(npc.getNpcId()).stream().anyMatch(d->needs(bot,bot.getQuestStateList().getQuestState(d.getQuestId()),d));}
 static boolean safe(Player owner,Player bot,Npc npc){return owner.isSpawned() && !owner.isDead() && npc.isSpawned() && bot.getWorldId()==owner.getWorldId() && bot.getInstanceId()==owner.getInstanceId()
  && npc.getWorldId()==owner.getWorldId() && npc.getInstanceId()==owner.getInstanceId() && owner.getKnownList().sees(npc) && PositionUtil.isInRange(owner,npc,25)
  && !owner.getController().isInCombat() && !bot.getController().isInCombat() && PlayerBotPartyBehavior.isolated(owner,bot,npc);}
 static boolean tick(PlayerBotSession session,PlayerBotNavigation navigation,boolean enabled) {
  Player bot=session.bot(),owner=session.owner();Npc pending=PENDING.get(bot.getObjectId());
  if(pending!=null) {
   if(!enabled || !safe(owner,bot,pending)) {bot.getObserveController().notifyMoveObservers();if(bot.isLooting())DropService.getInstance().closeDropList(bot,bot.getLootingNpcOid());close(bot);return false;}
   if(bot.getController().hasScheduledTask(TaskId.ACTION_ITEM_NPC))return true;
   bot.getController().cancelTask(TaskId.ACTION_ITEM_NPC);
   // The native object AI registers normal per-member loot and opens its list.
   if(bot.isLooting() && bot.getLootingNpcOid()==pending.getObjectId()) {
    var set=DropRegistrationService.getInstance().getCurrentDropMap().get(pending.getObjectId());
    if(set!=null)for(var d:new ArrayList<>(set))if(d.canViewDropItem(bot.getObjectId()) && QuestService.getQuestDrop(pending.getNpcId()).stream().anyMatch(q->q.getItemId()==d.getDropTemplate().getItemId() && needs(bot,bot.getQuestStateList().getQuestState(q.getQuestId()),q)))
     DropService.getInstance().requestDropItem(bot,pending.getObjectId(),d.getIndex(),true);
    DropService.getInstance().closeDropList(bot,pending.getObjectId());
    PlayerBotQuestSync.notice(bot,"I collected quest items from "+pending.getName()+". Returning to you.","object:"+pending.getObjectId(),1000);PlayerBotQuestSync.returnToOwner(bot);
   }
   PlayerBotQuestObjectives.objectFinished(bot);PENDING.remove(bot.getObjectId());CLAIMS.remove(pending.getObjectId());RETRY.put(bot.getObjectId(),System.currentTimeMillis()+1500);return true;
  }
  if(!enabled || bot.isCasting() || bot.isLooting() || System.currentTimeMillis()<RETRY.getOrDefault(bot.getObjectId(),0L))return false;
  CLAIMS.values().removeIf(c->c.until()<System.currentTimeMillis());
  List<Npc> objects=new ArrayList<>();owner.getKnownList().forEachNpc(npc->{
   if(CLAIMS.containsKey(npc.getObjectId()))return;
   var registration=DropRegistrationService.getInstance().getDropRegistrationMap().get(npc.getObjectId());
   boolean available=!npc.isDead() || registration!=null && registration.isAllowedToLoot(bot) && !registration.isBeingLooted();
   if(available && npc.getAi().getName().equals("quest_use_item") && safe(owner,bot,npc) && needed(bot,npc) && bot.getKnownList().sees(npc) && (npc.isDead() || DialogService.isInteractionAllowed(bot,npc))
    && PlayerBotQuestObjectives.objectMatch(bot,npc))objects.add(npc);
  });
  Npc object=objects.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(owner,n))).orElse(null);if(object==null)return false;
  if(!PositionUtil.isInTalkRange(bot,object) || !GeoService.getInstance().canSee(bot,object))return navigation.approach(object,2);
  if(CLAIMS.putIfAbsent(object.getObjectId(),new Claim(bot.getObjectId(),System.currentTimeMillis()+Math.max(5000,object.getObjectTemplate().getTalkDelay()*1000L+5000)))!=null)return false;
  navigation.stop();PlayerBotQuestObjectives.objectStarted(bot,object);PENDING.put(bot.getObjectId(),object);if(object.isDead())DropService.getInstance().requestDropList(bot,object.getObjectId());else object.getController().onDialogRequest(bot);return true;
 }
 private PlayerBotQuestObjects(){}
}
