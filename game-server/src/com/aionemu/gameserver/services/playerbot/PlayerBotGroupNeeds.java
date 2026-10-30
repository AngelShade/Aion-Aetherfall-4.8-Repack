package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
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
    if (quest > 0 && hard(bot,npc) && PlayerBotQuestRoutes.ids(bot,bot,quest,PlayerBotQuestRoutes.Kind.HUNT).contains(npc)) {
     var need=quest(bot,quest,npc);if(need!=null)return need;
    }
    if (quest == 0 && hard(bot,npc)) {
     var target=DataManager.NPC_DATA.getNpcTemplate(npc);
     if(bot.getLevel()<65 && target!=null && PlayerBotHunting.template(target))
      return new Need(0,npc,map,0,6,Math.max(1,bot.getLevel()-5),Math.min(65,bot.getLevel()+5),target.getName());
    }
   }
  }
  // Advertise another genuine unfinished local objective even when the bot's
  // current AI action is talking, preparing or waiting for its movement budget.
  for(var state:bot.getQuestStateList().getUncompletedQuests().stream().limit(40).toList()) {
   if(state.getStatus()!=QuestStatus.START)continue;
   for(int npc:PlayerBotQuestRoutes.ids(bot,bot,state.getQuestId(),PlayerBotQuestRoutes.Kind.HUNT))
    if(hard(bot,npc) && DataManager.SPAWNS_DATA.getSpawnsForNpc(bot.getWorldId(),npc).stream().anyMatch(g->!g.isTemporarySpawn() && g.getHandlerType()==null)) {
     var need=quest(bot,state.getQuestId(),npc);if(need!=null)return need;
    }
  }
  // Chore quests and ordinary levelling are solo work: no help request.
  return null;
 }

 /** Worth asking for help: an elite/hero/legendary monster, or one clearly above the bot's level. */
 static boolean hard(Player bot,int npc) {
  var target=DataManager.NPC_DATA.getNpcTemplate(npc);
  if(target==null || target.getLevel()<=0 || target.getRace()==bot.getRace()
   || target.getNpcTemplateType()==com.aionemu.gameserver.model.templates.npc.NpcTemplateType.FLAG)return false;
  // Quest target lists also hold friendly NPCs (e.g. Elyos 'andu'): only a monster hostile to the bot's faction counts.
  var own=bot.getRace()==Race.ELYOS?com.aionemu.gameserver.model.TribeClass.PC:com.aionemu.gameserver.model.TribeClass.PC_DARK;
  if(DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(target.getTribe(),own)
   || !DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(target.getTribe(),own) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(target.getTribe(),own)
    && !target.getTribe().name().contains("MONSTER"))return false;
  var rating=target.getRating();
  return rating==com.aionemu.gameserver.model.templates.npc.NpcRating.ELITE || rating==com.aionemu.gameserver.model.templates.npc.NpcRating.HERO
   || rating==com.aionemu.gameserver.model.templates.npc.NpcRating.LEGENDARY || target.getLevel()>=bot.getLevel()+3;
 }
 /** Why the target is hard, for the post and dialogue. */
 static String difficulty(int npc) {
  var target=DataManager.NPC_DATA.getNpcTemplate(npc);
  if(target==null)return "";
  String rating=switch(target.getRating()){case ELITE->"elite ";case HERO->"hero ";case LEGENDARY->"legendary ";default->"";};
  return display(target.getName())+" ("+rating+"Lv"+target.getLevel()+")";
 }
 /** Native template names are lower-case keys (e.g. "hagiaphilia"); show them as names. */
 static String display(String name) {
  var words=name.replace('_',' ').trim().split(" +");var out=new StringBuilder();
  for(var w:words)if(!w.isEmpty())out.append(out.length()==0?"":" ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
  return out.toString();
 }
 /** A bot with no hard need of its own offers its help (npc -1 marks the offer). */
 static Need helper(Player bot) {
  if (bot.isInInstance() || bot.getLevel() < 10) return null;
  return new Need(0,-1,bot.getWorldId(),0,6,Math.max(1,bot.getLevel()-5),Math.min(65,bot.getLevel()+5),"help offer");
 }
 static boolean helping(Need need){return need.npc()==-1;}
 /** The entrance for this run that a player may use, on any map. */
 static PlayerBotWorldTravel.Place entranceFor(Player player,AutoGroup group) {
  for(int npc:group.getNpcIds()) {
   if(DataManager.PORTAL2_DATA.getPortalPaths(npc).stream().noneMatch(p->portal(player,p,group.getInstanceMapId())))continue;
   for(var map:DataManager.WORLD_MAPS_DATA)
    for(var spawn:DataManager.SPAWNS_DATA.getSpawnsForNpc(map.getMapId(),npc)) {
     if(spawn.isTemporarySpawn() || spawn.getHandlerType()!=null)continue;
     for(var point:spawn.getSpawnTemplates())
      if(!point.isAerialSpawn())return new PlayerBotWorldTravel.Place(map.getMapId(),npc,point.getX(),point.getY(),point.getZ());
    }
  }
  return null;
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

 static InstancePlan instancePlan(Player bot) { return instancePlan(bot, Set.of(), mask -> 0); }
 /**
  * wanted: dungeons inside some online player's level range; posted: current
  * posts per dungeon. Spreads posts over the dungeons players can pick from,
  * at most two per dungeon, instead of many bots posting the same run.
  */
 static InstancePlan instancePlan(Player bot, Set<Integer> wanted, java.util.function.ToIntFunction<Integer> posted) {
  if (DataManager.AUTO_GROUP == null || DataManager.PORTAL2_DATA == null) return null;
  var quests=new LinkedHashMap<Integer,Set<Integer>>();
  for (var state : bot.getQuestStateList().getUncompletedQuests().stream().limit(40).toList())
   if (state.getStatus()==QuestStatus.START && !PlayerBotQuestMetadata.ready(bot,state.getQuestId()))
    quests.put(state.getQuestId(),QuestEngine.getInstance().getRequiredKillNpcIds(bot,state.getQuestId()));
  // Pick a level-appropriate, genuinely accessible run, rather than a famous
  // dungeon name unrelated to this character. Quest runs come first; otherwise
  // each bot rotates through its eligible runs so the list is not one dungeon.
  var plans=new ArrayList<InstancePlan>();
  for (int mask : DataManager.AUTO_GROUP.getRecruitableInstanceMaskIds().stream().sorted().toList()) {
   var group = DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(mask);
   int map = group.getInstanceMapId();
   int members = DataManager.INSTANCE_COOLTIME_DATA.getMaxMemberCount(map, bot.getRace());
   int quest = instanceQuest(quests, map);
   if (members < 2 || members > 6 || !level(bot.getLevel(), group.getMinLvl(), group.getMaxLvl())
    || quest == 0 && !worthRunning(bot.getLevel(), group) || blocked(bot,map)) continue;
   var portal=plannedEntrance(bot,group);
   // An entrance on another map: the bot posts from here and is summoned to the
   // human who joins (they meet at the entrance), instead of never posting.
   if(portal==null && !(PlayerBotConfig.FIND_GROUP_REMOTE_INSTANCES && eligibleEntrance(bot,group)))continue;
   var world = DataManager.WORLD_MAPS_DATA.getTemplate(map);
   if (world == null) continue;
   var plan=new InstancePlan(new Need(quest, 0, map, mask, members, group.getMinLvl(), quest>0 || gearRun(bot.getLevel(),group) ? group.getMaxLvl() : Math.min(group.getMaxLvl(), group.getMinLvl()+8), world.getName()),portal);
   if (quest > 0) return plan;
   plans.add(plan);
  }
  plans.removeIf(p->posted.applyAsInt(p.need().mask())>=2);
  if (plans.isEmpty()) return null;
  var forPlayers=plans.stream().filter(p->wanted.contains(p.need().mask())).toList();
  if (!forPlayers.isEmpty()) plans=new ArrayList<>(forPlayers);
  // Least-posted dungeon first; random among equals so the list stays varied.
  Collections.shuffle(plans);
  return plans.stream().min(Comparator.comparingInt((InstancePlan p)->posted.applyAsInt(p.need().mask()))
   .thenComparing(p->p.entrance()==null)).orElse(null);
 }
 /** Native portal admission (level, race, quests, items, cooldown) without standing at the entrance. */
 static boolean eligibleEntrance(Player bot,AutoGroup group) {
  return !blocked(bot,group.getInstanceMapId()) && group.getNpcIds().stream()
   .anyMatch(npc->DataManager.PORTAL2_DATA.getPortalPaths(npc).stream().anyMatch(p->portal(bot,p,group.getInstanceMapId())));
 }
 /** Max-level characters run endgame dungeons for gear/AP even without a quest. */
 static boolean gearRun(int level,AutoGroup group) { return level >= 65 && group.getMinLvl() >= 60; }
 /** Below max level a run is worth it for XP near its entry level; at max level, endgame runs for gear. */
 static boolean worthRunning(int level,AutoGroup group) { return gearRun(level, group) || level < 65 && level <= group.getMinLvl() + 8; }

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
  if(helping(need))return false;
  if(need.instance())return worthRunning(bot.getLevel(),DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(need.mask()));
  if(bot.getLevel()>=65)return false;
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
  if (helping(need)) return true;
  if (need.quest() > 0) {
   var state = bot.getQuestStateList().getQuestState(need.quest());
   return state != null && state.getStatus() == QuestStatus.START && !PlayerBotQuestMetadata.ready(bot, need.quest());
  }
  if (need.instance()) {
   var group = DataManager.AUTO_GROUP.getTemplateByInstanceMaskId(need.mask());
   // Entry consumes the run's cooldown; it must not retire recruitment for the
   // very instance the organizer is currently playing.
   return group != null && level(bot.getLevel(), need.minLevel(), need.maxLevel()) && worthRunning(bot.getLevel(), group)
    && (bot.isInInstance() && bot.getWorldId()==need.map() || !blocked(bot, need.map()));
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

 /**
  * Joining a human's run: the human leads the entry, so level range and
  * re-entry cooldown decide. Entry quests/items are checked by the native
  * portal when the party enters; requiring them here left no bot to offer.
  */
 static boolean joinable(Player player, Need need) {
  return level(player.getLevel(), need.minLevel(), need.maxLevel()) && (!need.instance() || !blocked(player, need.map()));
 }

 static String guidance(Player bot, Need need) {
  if (need.instance()) return (need.quest()>0 ? "I still need " + PlayerBotQuestJournal.name(need.quest()) + " in " : "I'm looking for an XP run in ") + need.title() + ". We need a tank, healing and damage for a " + need.members() + "-person party. Meet at the entrance; we enter normally when everyone is ready.";
  if(helping(need))return "I'm offering my help as a "+switch(PlayerBotRules.roleFor(bot.getPlayerClass())){case TANK->"tank";case HEALER->"healer";case SUPPORT->"support";default->"damage dealer";}
   +", Lv"+bot.getLevel()+". Invite me and tell me what you need: a tough monster, a group quest or a dungeon. I'll come to you.";
  if(need.quest()==0 && need.npc()==0)return "I'm level "+bot.getLevel()+" and looking for company to earn XP. Let's head to a hunting area around our level. I'll follow your lead and help with normal combat and loot.";
  var target = DataManager.NPC_DATA.getNpcTemplate(need.npc());
  String objective = target == null ? "the quest objectives" : target.getName();
  var map = DataManager.WORLD_MAPS_DATA.getTemplate(need.map());
  String place = map == null ? "my current area" : map.getName();
  String hard = difficulty(need.npc());
  return (need.quest() == 0 ? "I can't beat " + hard + " alone" : "I can't beat " + hard + " alone for " + need.title())
   + " in " + place + "." + where(bot, need) + " Join me and I'll offer to bring you here; we fight them together.";
 }

 /** Nearest native spawn of the objective, so the human can lead the party to it. */
 /** The objective's nearest native spawn on the bot's map: {x, y, z}, or null. */
 static float[] objective(Player bot, Need need) {
  if (need.npc() <= 0 || bot.getWorldId() != need.map()) return null;
  double best = Double.POSITIVE_INFINITY; float[] spot = null;
  for (var spawn : DataManager.SPAWNS_DATA.getSpawnsForNpc(need.map(), need.npc())) {
   if (spawn.isTemporarySpawn() || spawn.getHandlerType() != null) continue;
   for (var point : spawn.getSpawnTemplates()) {
    double d = PositionUtil.getDistance(bot, point.getX(), point.getY(), point.getZ());
    if (d < best) { best = d; spot = new float[]{point.getX(), point.getY(), point.getZ()}; }
   }
  }
  return spot;
 }
 /**
  * Where a helper should arrive: on the ground 15 m short of the objective,
  * on the bot's side, so nobody lands on top of the monster.
  */
 static float[] meetingPoint(Player bot, Need need) {
  float[] spot = objective(bot, need);
  if (spot == null) return null;
  double dx = bot.getX() - spot[0], dy = bot.getY() - spot[1], d = Math.hypot(dx, dy);
  float x = d < 1 ? spot[0] : spot[0] + (float) (dx / d * Math.min(15, d));
  float y = d < 1 ? spot[1] : spot[1] + (float) (dy / d * Math.min(15, d));
  float z = com.aionemu.gameserver.world.geo.GeoService.getInstance().getZ(need.map(), x, y, spot[2] + 10, spot[2] - 20, bot.getInstanceId());
  return new float[]{x, y, Float.isFinite(z) ? z : spot[2]};
 }

 static String where(Player bot, Need need) {
  float[] spot = objective(bot, need);
  if (spot == null) return "";
  double best = PositionUtil.getDistance(bot, spot[0], spot[1], spot[2]); float bx = spot[0], by = spot[1];
  if (best < 30) return " They're right around here.";
  String[] names = {"east", "north-east", "north", "north-west", "west", "south-west", "south", "south-east"};
  int octant = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(by - bot.getY(), bx - bot.getX())) / 45), 8L);
  return " The nearest ones are about " + Math.round(best / 10) * 10 + " m " + names[octant] + " of me (X " + Math.round(bx) + ", Y " + Math.round(by) + ").";
 }

 private PlayerBotGroupNeeds() {}
}
