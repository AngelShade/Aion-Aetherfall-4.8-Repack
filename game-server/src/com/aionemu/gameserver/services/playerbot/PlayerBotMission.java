package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;

/** Explicitly assigned ordinary hunt/turn-in mission. Native quest handlers alone award progress. */
final class PlayerBotMission {
	record Destination(int npc, boolean turnIn, PlayerBotNavigation.Point point) {}
	final int quest;
	private final long deadline;
	private final Map<Destination, Long> excluded = new HashMap<>();
	private Destination current;
	private long lastProgress, nextPlan;
	private double bestDistance = Double.POSITIVE_INFINITY;
	private String status = "planning quest route";
	PlayerBotMission(Player bot, int quest) {
		this.quest = quest;
		if (!eligible(bot, quest)) throw new IllegalArgumentException("Select an active ordinary MonsterHunt quest with a supported objective or reward NPC.");
		deadline = System.currentTimeMillis() + 600000;
	}
	static boolean eligible(Player bot, int quest) {
		var template = DataManager.QUEST_DATA.getQuestById(quest);
		var state = bot.getQuestStateList().getQuestState(quest);
		return PlayerBotQuests.ordinary(template) && template.getExtendedRewards() == null && state != null
			&& (state.getStatus() == QuestStatus.START && !QuestEngine.getInstance().getRequiredKillNpcIds(bot, quest).isEmpty()
				|| state.getStatus() == QuestStatus.REWARD && !QuestEngine.getInstance().getPlayerBotRewardNpcIds(bot, quest).isEmpty());
	}
	private Set<Integer> ids(Player bot) {
		var engine = QuestEngine.getInstance();
		var state = bot.getQuestStateList().getQuestState(quest);
		return state != null && state.getStatus() == QuestStatus.REWARD ? engine.getPlayerBotRewardNpcIds(bot, quest) : engine.getRequiredKillNpcIds(bot, quest);
	}
	boolean ended(Player bot, long now) {
		var state = bot.getQuestStateList().getQuestState(quest);
		return now >= deadline || state == null || state.getStatus() == QuestStatus.COMPLETE;
	}
	String status() { return status; }
	void status(String value) { status = value; }
	static float range() { return Math.clamp(PlayerBotConfig.MISSION_DISTANCE, 45, 500); }
	Destination destination(Player owner, Player bot, long now) {
		Set<Integer> required = ids(bot);
		boolean reward = bot.getQuestStateList().getQuestState(quest).getStatus() == QuestStatus.REWARD;
		if (current != null) {
			double distance = PositionUtil.getDistance(bot, current.point().x(), current.point().y(), current.point().z());
			if (distance < bestDistance - 1) { bestDistance = distance; lastProgress = now; }
			if (required.contains(current.npc()) && current.turnIn() == reward && inRange(owner, current.point()) && now - lastProgress < 12000) return current;
			excluded.put(current, now + 60000); current = null;
		}
		if (now < nextPlan) return null;
		nextPlan = now + 2000; excluded.entrySet().removeIf(e -> e.getValue() <= now);
		List<Destination> choices = new ArrayList<>();
		int scanned = 0;
		if (DataManager.SPAWNS_DATA == null || owner.isInInstance()) { status = "mission travel requires an open-world map"; return null; }
		search: for (int id : new TreeSet<>(required)) {
			var npc = DataManager.NPC_DATA.getNpcTemplate(id);
			if (npc == null || !reward && (npc.getLevel() > bot.getLevel() + 2 || npc.getRating() != NpcRating.NORMAL && npc.getRating() != NpcRating.JUNK)) continue;
			for (var group : DataManager.SPAWNS_DATA.getSpawnsForNpc(bot.getWorldId(), id)) {
				if (++scanned > 2048) break search;
				if (group.isTemporarySpawn() || group.getHandlerType() != null) continue;
				for (var spawn : group.getSpawnTemplates()) {
					if (++scanned > 2048 || choices.size() >= 256) break search;
					var point = new PlayerBotNavigation.Point(spawn.getX(), spawn.getY(), spawn.getZ());
					var choice = new Destination(id, reward, point);
					if (!spawn.isAerialSpawn() && inRange(owner, point) && !excluded.containsKey(choice)) choices.add(choice);
				}
			}
		}
		current = nearest(new PlayerBotNavigation.Point(bot.getX(), bot.getY(), bot.getZ()), choices);
		bestDistance = current == null ? Double.POSITIVE_INFINITY : PositionUtil.getDistance(bot, current.point().x(), current.point().y(), current.point().z());
		lastProgress = now; status = current == null ? "no eligible quest destination within owner range" : reward ? "traveling to quest reward NPC" : "traveling to quest objective";
		return current;
	}
	private static boolean inRange(Player owner, PlayerBotNavigation.Point point) {
		return Float.isFinite(point.x()) && Float.isFinite(point.y()) && Float.isFinite(point.z()) && PositionUtil.getDistance(owner, point.x(), point.y(), point.z()) <= range();
	}
	static Destination nearest(PlayerBotNavigation.Point start, List<Destination> choices) {
		return choices.stream().filter(d -> Float.isFinite(d.point().x()) && Float.isFinite(d.point().y()) && Float.isFinite(d.point().z()))
			.min(Comparator.comparingDouble((Destination d) -> PositionUtil.getDistance(start.x(), start.y(), start.z(), d.point().x(), d.point().y(), d.point().z()))
				.thenComparingInt(Destination::npc)).orElse(null);
	}
}
