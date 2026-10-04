package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;

/** Exact authoritative quest state and real NPC coordinates for the owner. */
public final class PlayerBotQuestJournal {
 public static String name(int quest){var t=DataManager.QUEST_DATA.getQuestById(quest);return t==null ? "Quest "+quest : t.getName();}
 public static Map<String,Object> describe(Player owner,Player bot,QuestState quest) {
  var template=DataManager.QUEST_DATA.getQuestById(quest.getQuestId());
  Map<String,Object> row=new LinkedHashMap<>();
  row.put("id",quest.getQuestId());row.put("name",template==null ? "Quest "+quest.getQuestId() : template.getName());
  row.put("status",quest.getStatus().name());row.put("progress",quest.getQuestVars().getQuestVars());
  boolean ready=PlayerBotQuestMetadata.ready(bot,quest.getQuestId());
  row.put("ready",ready);row.put("missionEligible",PlayerBotMission.eligible(bot,quest.getQuestId()));
  List<Map<String,Object>> objectives=new ArrayList<>();
  var model=DataManager.XML_QUESTS==null ? null : DataManager.XML_QUESTS.getQuest(quest.getQuestId());
  if(template!=null && model!=null && model.getClass()==com.aionemu.gameserver.questEngine.handlers.models.MonsterHuntData.class)
   for(var kill:template.getQuestKill()) {
    if(template.isDataDriven() && kill.getQuestStep()!=quest.getQuestVarById(0) && !ready)continue;
    int variable=kill.getSequenceNumber()>0 ? kill.getSequenceNumber() : kill.getVar();
    int total=count(quest,variable,kill.getKillCount());
    String names=kill.getNpcIds().stream().map(DataManager.NPC_DATA::getNpcTemplate).filter(Objects::nonNull).map(n->n.getName()).distinct().collect(java.util.stream.Collectors.joining(" / "));
    objectives.add(Map.of("name",names.isBlank() ? "Quest objective" : names,"current",ready ? kill.getKillCount() : total,"required",kill.getKillCount()));
   }
  row.put("objectives",objectives);
  if(template!=null && template.getCollectItems()!=null)for(var item:template.getCollectItems().getCollectItem()) {
   var t=DataManager.ITEM_DATA.getItemTemplate(item.getItemId());
   objectives.add(Map.of("name",t==null ? "Quest item "+item.getItemId() : t.getName(),"current",ready ? item.getCount().longValue() : Math.min(item.getCount().longValue(),PlayerBotQuestMirror.held(bot,item.getItemId())),"required",item.getCount()));
  }
  Set<Integer> ids=new TreeSet<>(ready ? PlayerBotQuestMetadata.rewardNpcIds(bot,quest.getQuestId()) : QuestEngine.getInstance().getRequiredKillNpcIds(bot,quest.getQuestId()));
  if(!ready)ids.addAll(PlayerBotQuestObjects.objectiveIds(bot,quest.getQuestId()));
  var conversations=ready ? Set.<Integer>of() : PlayerBotQuestConversations.objectiveIds(owner,bot,quest.getQuestId());ids.addAll(conversations);
  row.put("conversation",!conversations.isEmpty());
  List<Map<String,Object>> destinations=new ArrayList<>();
  for(int id:new TreeSet<>(ids)) {
   var npc=DataManager.NPC_DATA.getNpcTemplate(id);if(npc==null)continue;
   // Instance NPC locations must come from the current instance, not static
   // open-world coordinates reused by a different copy of a dungeon.
   if(bot.isInInstance())owner.getKnownList().forEachNpc(actual->{if(actual.getNpcId()==id && actual.getInstanceId()==owner.getInstanceId())destinations.add(destination(owner,id,npc.getName(),actual.getX(),actual.getY(),actual.getZ()));});
   else if(DataManager.SPAWNS_DATA!=null)for(var group:DataManager.SPAWNS_DATA.getSpawnsForNpc(bot.getWorldId(),id)) {
    if(group.isTemporarySpawn() || group.getHandlerType()!=null)continue;
    for(var spawn:group.getSpawnTemplates())if(destinations.size()<32)destinations.add(destination(owner,id,npc.getName(),spawn.getX(),spawn.getY(),spawn.getZ()));
   }
  }
  destinations.sort(Comparator.comparingDouble(d->((Number)d.get("distance")).doubleValue()));
  row.put("destinations",destinations.stream().limit(5).toList());
  row.put("guidance",ids.isEmpty() ? "This quest needs your guidance or a custom story dialog." : ready ? "Lead me near a listed NPC; I will turn it in and select rewards for my class and equipment build." : !conversations.isEmpty() ? "Lead me near the conversation NPC. I will advance this objective, then report my next step." : template!=null && template.getCollectItems()!=null ? "Lead me near these quest objects or item sources. I will collect the required items through normal interaction and loot." : "These are my current quest objective NPCs.");
  return row;
 }
 static int count(QuestState quest,int variable,int required) {
  if(variable<0 || variable>5)return 0;
  int total=0,shift=0,remaining=required;
  do {total+=quest.getQuestVarById(variable++)<<shift;shift+=6;remaining>>=6;}while(remaining>0 && shift<30 && variable<6);
  return Math.min(Math.max(0,total),Math.max(0,required));
 }
 private static Map<String,Object> destination(Player owner,int id,String name,float x,float y,float z) {
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(owner.getWorldId());
  return Map.of("npc",id,"name",name,"map",owner.getWorldId(),"mapName",map==null ? "Map "+owner.getWorldId() : map.getName(),"x",x,"y",y,"z",z,"distance",Math.round(PositionUtil.getDistance(owner,x,y,z)));
 }
 private PlayerBotQuestJournal() {}
}
