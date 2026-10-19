package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.autogroup.AutoGroup;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.portal.PortalPath;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;

/** Read-only reasons to group. Native quests, entrances and earned XP supply the need. */
final class PlayerBotGroupNeeds {
 record Need(int quest, int npc, int map, int mask, int members, int minLevel, int maxLevel, String title) {
  boolean instance() { return mask != 0; }
 }
 record InstancePlan(Need need,PlayerBotWorldTravel.Place entrance) {}

 static boolean level(int level, int min, int max) { return level >= min && (max == 0 || level <= max); }

 static Need field(Player bot) {
  if (bot.isInInstance()) return null;
  // A chosen movement destination is useful context, not a prerequisite for
  // wanting company. Town visitors and dormant residents can still seek XP.
  var activity = PlayerBotWorldActivities.activity(bot);
  if (Boolean.TRUE.equals(activity.get("selected"))) {
   int quest = ((Number) activity.get("quest")).intValue();
   int npc = ((Number) activity.get("npc")).intValue();
   int map = ((Number) activity.get("map")).intValue();
   if (map == bot.getWorldId()) {
    if (quest > 0 && PlayerBotQuestRoutes.ids(bot,bot,quest,PlayerBotQuestRoutes.Kind.HUNT).contains(npc)) {
     var need=quest(bot,quest,npc);if(need!=null)return need;
    }
    if (quest == 0) {
     var target=DataManager.NPC_DATA.getNpcTemplate(npc);
     if(bot.getLevel()<65 && target!=null && PlayerBotHunting.template(target)
      && PlayerBotWorldActivities.levelSuitable(bot.getLevel(),target.getLevel()))
      return new Need(0,npc,map,0,6,Math.max(1,bot.getLevel()-5),Math.min(65,bot.getLevel()+5),"XP and "+target.getName());
    }
   }
  }
  // Advertise another genuine unfinished local objective even when the bot's
  // current AI action is talking, preparing or waiting for its movement budget.
  for(var state:bot.getQuestStateList().getUncompletedQuests().stream().limit(40).toList()) {
   if(state.getStatus()!=QuestStatus.START)continue;
   for(int npc:PlayerBotQuestRoutes.ids(bot,bot,state.getQuestId(),PlayerBotQuestRoutes.Kind.HUNT))
    if(DataManager.SPAWNS_DATA.getSpawnsForNpc(bot.getWorldId(),npc).stream().anyMatch(g->!g.isTemporarySpawn() && g.getHandlerType()==null)) {
     var need=quest(bot,state.getQuestId(),npc);if(need!=null)return need;
    }
  }
  return leveling(bot.getLevel(),bot.getWorldId());
 }

 private static Need quest(Player bot,int quest,int npc) {
  var template=DataManager.QUEST_DATA.getQuestById(quest);
  var state=bot.getQuestStateList().getQuestState(quest);
  if(template==null || state==null || state.getStatus()!=QuestStatus.START || PlayerBotQuestMetadata.ready(bot,quest)
   || template.getTarget()==com.aionemu.gameserver.model.templates.quest.QuestTarget.ALLIANCE
   || template.getTarget()==com.aionemu.gameserver.model.templates.quest.QuestTarget.LEAGUE)return null;
  return new Need(quest,npc,bot.getWorldId(),0,6,Math.max(1,bot.getLevel()-5),Math.min(65,bot.getLevel()+5),template.getName());
 }

 static Need leveling(int level,int map) {
  if(level<1 || level>=65 || map<=0)return null;
  return new Need(0,0,map,0,6,Math.max(1,level-5),Math.min(65,level+5),"Lv"+level+" leveling group");
 }

 static Need instance(Player bot) {
  var plan=instancePlan(bot);
  return plan!=null && entrance(bot,DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(plan.need().mask())) ? plan.need() : null;
 }

 static InstancePlan instancePlan(Player bot) {
  if (DataManager.AUTO_GROUP == null || DataManager.PORTAL2_DATA == null) return null;
  var quests=new LinkedHashMap<Integer,Set<Integer>>();
  for (var state : bot.getQuestStateList().getUncompletedQuests().stream().limit(40).toList())
   if (state.getStatus()==QuestStatus.START && !PlayerBotQuestMetadata.ready(bot,state.getQuestId()))
    quests.put(state.getQuestId(),QuestEngine.getInstance().getRequiredKillNpcIds(bot,state.getQuestId()));
  // Native order is stable. Pick a level-appropriate, genuinely accessible run,
  // rather than a famous dungeon name unrelated to this character.
  for (int mask : DataManager.AUTO_GROUP.getRecruitableInstanceMaskIds().stream().sorted().toList()) {
   var group = DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(mask);
   int map = group.getInstanceMapId();
   int members = DataManager.INSTANCE_COOLTIME_DATA.getMaxMemberCount(map, bot.getRace());
   int quest = instanceQuest(quests, map);
   if (members < 2 || members > 6 || !level(bot.getLevel(), group.getMinLvl(), group.getMaxLvl())
    || quest == 0 && (bot.getLevel() > group.getMinLvl()+8 || bot.getLevel() >= 65) || blocked(bot,map)) continue;
   var portal=plannedEntrance(bot,group);
   if(portal==null)continue;
   var world = DataManager.WORLD_MAPS_DATA.getTemplate(map);
   if (world != null) return new InstancePlan(new Need(quest, 0, map, mask, members, group.getMinLvl(), quest>0 ? group.getMaxLvl() : Math.min(group.getMaxLvl(), group.getMinLvl()+8), world.getName()),portal);
  }
  return null;
 }

 private static PlayerBotWorldTravel.Place plannedEntrance(Player bot,AutoGroup group) {
  PlayerBotWorldTravel.Place best=null;double distance=Double.POSITIVE_INFINITY;
  for(int npc:group.getNpcIds()) {
   if(DataManager.PORTAL2_DATA.getPortalPaths(npc).stream().noneMatch(p->portal(bot,p,group.getInstanceMapId())))continue;
   for(var spawn:DataManager.SPAWNS_DATA.getSpawnsForNpc(bot.getWorldId(),npc)) {
    if(spawn.isTemporarySpawn() || spawn.getHandlerType()!=null)continue;
    for(var point:spawn.getSpawnTemplates())if(!point.isAerialSpawn()) {
     double next=PositionUtil.getDistance(bot,point.getX(),point.getY(),point.getZ());
     if(next<distance){distance=next;best=new PlayerBotWorldTravel.Place(bot.getWorldId(),npc,point.getX(),point.getY(),point.getZ());}
    }
   }
  }
  return best;
 }

 /** Joining another bot's offer needs useful work for this character too. */
 static boolean useful(Player bot,Need need) {
  if(!applicant(bot,need))return false;
  if(need.quest()>0) {
   var own=bot.getQuestStateList().getQuestState(need.quest());
   if(own!=null && own.getStatus()==QuestStatus.START && !PlayerBotQuestMetadata.ready(bot,need.quest()))return true;
  }
  if(bot.getLevel()>=65)return false;
  if(need.instance())return bot.getLevel()<=DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(need.mask()).getMinLvl()+8;
  if(need.npc()==0)return true;
  var target=DataManager.NPC_DATA.getNpcTemplate(need.npc());
  return target!=null && PlayerBotWorldActivities.levelSuitable(bot.getLevel(),target.getLevel());
 }

 private static int instanceQuest(Map<Integer,Set<Integer>> quests, int map) {
  for (var quest : quests.entrySet()) {
   for (int npc : quest.getValue())
    if (!DataManager.SPAWNS_DATA.getSpawnsForNpc(map,npc).isEmpty()) return quest.getKey();
  }
  return 0;
 }

 static boolean entrance(Player player, AutoGroup group) {
  if (group == null) return false;
  if (blocked(player,group.getInstanceMapId())) return false;
  for (int npc : group.getNpcIds()) {
   boolean eligible = DataManager.PORTAL2_DATA.getPortalPaths(npc).stream().anyMatch(path -> portal(player, path, group.getInstanceMapId()));
   if (!eligible) continue;
   if (GroupConfig.FORM_INSTANCE_GROUP_ANYWHERE || player.getWorldId() == group.getInstanceMapId()) return true;
   // 4.8 registration is made at an entrance, unless the owner's existing
   // form-anywhere option explicitly enables the later-version behavior.
   for (var spawn : DataManager.SPAWNS_DATA.getSpawnsForNpc(player.getWorldId(), npc)) {
    if (spawn.isTemporarySpawn() || spawn.getHandlerType() != null) continue;
    for (var point : spawn.getSpawnTemplates())
     if (!point.isAerialSpawn() && PositionUtil.getDistance(player, point.getX(), point.getY(), point.getZ()) <= 30) return true;
   }
  }
  return false;
 }

 private static boolean blocked(Player player,int map) {
  var cooldown=player.getPortalCooldownList().getPortalCooldown(map);
  return cooldown!=null && cooldown.getReuseTime()>=System.currentTimeMillis()
   && cooldown.getEnterCount()>=DataManager.INSTANCE_COOLTIME_DATA.getInstanceMaxCountByWorldId(map);
 }

 static boolean portal(Player player, PortalPath path, int map) {
  var destination = DataManager.PORTAL_LOC_DATA.getPortalLoc(path.getLocId());
  var limits = DataManager.INSTANCE_COOLTIME_DATA.getInstanceCooltimeByWorldId(map);
  if (destination == null || destination.getWorldId() != map || limits == null || path.getSiegeId() != 0
   || path.getRace() != Race.PC_ALL && path.getRace() != player.getRace()
   || limits.getRace() != null && limits.getRace() != Race.PC_ALL && limits.getRace() != player.getRace()
   || player.getAbyssRank().getRank().getId() < path.getMinRank()
   || path.getTitleId() != 0 && path.getTitleId() != player.getCommonData().getTitleId()
   || player.getInventory().getKinah() < path.getKinah()) return false;
  int min = path.getMinLevel() > 0 ? path.getMinLevel() : player.getRace() == Race.ELYOS ? limits.getEnterMinLevelLight() : limits.getEnterMinLevelDark();
  int max = player.getRace() == Race.ELYOS ? limits.getEnterMaxLevelLight() : limits.getEnterMaxLevelDark();
  if (!level(player.getLevel(), min, max)) return false;
  if (path.getQuestReq() != null && path.getQuestReq().stream().noneMatch(req -> {
   var state = player.getQuestStateList().getQuestState(req.getQuestId());
   return state != null && (state.getStatus() == QuestStatus.COMPLETE || req.getQuestStep() > 0 && state.getQuestVarById(0) >= req.getQuestStep());
  })) return false;
  return path.getItemReq() == null || path.getItemReq().stream().allMatch(req -> player.getInventory().getItemCountByItemId(req.getItemId()) >= req.getItemCount());
 }

 static boolean unfinished(Player bot, Need need) {
  if (need.quest() > 0) {
   var state = bot.getQuestStateList().getQuestState(need.quest());
   return state != null && state.getStatus() == QuestStatus.START && !PlayerBotQuestMetadata.ready(bot, need.quest());
  }
  return bot.getLevel() < 65 && level(bot.getLevel(), need.minLevel(), need.maxLevel());
 }

 static boolean applicant(Player player, Need need) {
  if (!level(player.getLevel(), need.minLevel(), need.maxLevel())) return false;
  if (!need.instance()) return true;
  var group = DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(need.mask());
  return group != null && !blocked(player,need.map())
   && group.getNpcIds().stream().anyMatch(npc -> DataManager.PORTAL2_DATA.getPortalPaths(npc).stream().anyMatch(path -> portal(player, path, need.map())));
 }

 static String guidance(Player bot, Need need) {
  if (need.instance()) return (need.quest()>0 ? "I still need " + PlayerBotQuestJournal.name(need.quest()) + " in " : "I'm looking for an XP run in ") + need.title() + ". We need a tank, healing and damage for a " + need.members() + "-person party. Meet at the entrance; we enter normally when everyone is ready.";
  if(need.quest()==0 && need.npc()==0)return "I'm level "+bot.getLevel()+" and looking for company to earn XP. Let's head to a hunting area around our level. I'll follow your lead and help with normal combat and loot.";
  var target = DataManager.NPC_DATA.getNpcTemplate(need.npc());
  String objective = target == null ? "the quest objectives" : target.getName();
  var map = DataManager.WORLD_MAPS_DATA.getTemplate(need.map());
  String place = map == null ? "my current area" : map.getName();
  return (need.quest() == 0 ? "I'm earning XP against " : "I still need " + objective + " for " + need.title() + ". Help me with ")
   + (need.quest() == 0 ? objective : "those objectives") + " in " + place + ". I'll follow your lead and use my normal combat and quest actions.";
 }

 private PlayerBotGroupNeeds() {}
}
