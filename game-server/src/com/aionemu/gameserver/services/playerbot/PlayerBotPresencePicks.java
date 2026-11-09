package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * Phase 1.3: among bots eligible to move near a player, prefer ones with open quests whose NPCs or monsters are in that
 * area, so they stay busy there instead of walking off. Level fit is unchanged: placement still picks the camp.
 */
final class PlayerBotPresencePicks {
 /** Eligible bots scored per placement; bounds the work on the service thread. */
 static final int SAMPLE=16;
 private static final int QUESTS=40;
 static final AtomicLong PICKS=new AtomicLong(),LOCAL=new AtomicLong();

 /** Open quests (in progress or finished) with a kill target or reward NPC inside the area. */
 static int localWork(Player bot,PlayerBotPopulationFocus.Snapshot area) {
  if(area==null || !area.active())return 0;
  int score=0;List<com.aionemu.gameserver.questEngine.model.QuestState> open;
  // Read from the service thread: a quest list changing under the copy only costs this bot its preference.
  try{open=bot.getQuestStateList().getUncompletedQuests().stream().limit(QUESTS).toList();}catch(RuntimeException e){return 0;}
  for(var state:open) {
   Set<Integer> npcs=state.getStatus()==QuestStatus.REWARD ? PlayerBotQuestMetadata.rewardNpcIds(bot,state.getQuestId())
    : state.getStatus()==QuestStatus.START ? QuestEngine.getInstance().getRequiredKillNpcIds(bot,state.getQuestId()) : Set.of();
   if(inArea(npcs,area))score++;
  }
  return score;
 }
 private static boolean inArea(Set<Integer> npcs,PlayerBotPopulationFocus.Snapshot area) {
  for(int npc:npcs)for(var place:PlayerBotWorldTravel.places(npc))if(area.nearby(place.map(),place.x(),place.y(),place.z()))return true;
  return false;
 }
 /** Phase 4: a quester's local quest work counts double; a trader also scores for a town in the area. */
 static int fit(int work,PlayerBotLeanings.Leaning leaning,int level,boolean town) {
  if(leaning==null || level<10)return work;
  return switch(leaning){case QUESTER->work*2;case TRADER->work+(town?2:0);default->work;};
 }
 static int fit(Player bot,PlayerBotPopulationFocus.Snapshot area) {
  int work=localWork(bot,area);var leaning=PlayerBotLeanings.of(bot);
  return fit(work,leaning,bot.getLevel(),leaning==PlayerBotLeanings.Leaning.TRADER && town(bot,area));
 }
 private static boolean town(Player bot,PlayerBotPopulationFocus.Snapshot area) {
  if(area==null || !area.active())return false;
  for(int map:area.maps())for(var p:PlayerBotWorldPlacement.hubs(bot.getRace(),map))if(p.city() && area.nearby(p.map(),p.x(),p.y(),p.z()))return true;
  return false;
 }
 /** Highest local work first; equal scores keep the caller's (shuffled) order. */
 static <T> List<T> rank(List<T> eligible,java.util.function.ToIntFunction<T> score) {
  var scored=new ArrayList<Map.Entry<T,Integer>>();
  for(var e:eligible)scored.add(Map.entry(e,score.applyAsInt(e)));
  scored.sort(Map.Entry.<T,Integer>comparingByValue().reversed());
  return scored.stream().map(Map.Entry::getKey).toList();
 }
 static void picked(boolean local){PICKS.incrementAndGet();if(local)LOCAL.incrementAndGet();}
 static String stats(){return LOCAL.get()+"/"+PICKS.get();}
 private PlayerBotPresencePicks(){}
}
