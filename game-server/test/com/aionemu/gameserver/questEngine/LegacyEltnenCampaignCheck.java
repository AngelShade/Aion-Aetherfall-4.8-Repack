package com.aionemu.gameserver.questEngine;

import java.util.*;
import java.nio.file.*;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.templates.quest.QuestNpc;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.world.WorldPosition;

/** Offline checks; compile alongside the restored data handlers and run from game-server. */
public class LegacyEltnenCampaignCheck {
	private static int checks;
	private static void check(boolean condition, String message) {
		checks++;
		if (!condition) throw new AssertionError(message);
	}
	private static Player player(Race race, int level) throws Exception {
		PlayerCommonData common = new PlayerCommonData(900000000 + checks);
		common.setName("OfflineEltnenCheck");
		common.setRace(race);
		common.setGender(Gender.MALE);
		common.setPlayerClass(PlayerClass.GLADIATOR);
		// Set fixture level without invoking player services, world registration or the database.
		var levelField = PlayerCommonData.class.getDeclaredField("level");
		levelField.setAccessible(true);
		levelField.setInt(common, level);
		PlayerAppearance appearance = new PlayerAppearance();
		appearance.setHeight(1);
		// Player's normal constructor loads pets from the database. Availability tests
		// need only common data and quest states, so create an isolated, offline fixture.
		var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		Player player = (Player) unsafe.allocateInstance(Player.class);
		var accountField = Player.class.getDeclaredField("playerAccountData");
		accountField.setAccessible(true);
		accountField.set(player, new PlayerAccountData(common, appearance));
		player.setQuestStateList(new QuestStateList());
		var positionField = com.aionemu.gameserver.model.gameobjects.VisibleObject.class.getDeclaredField("position");
		positionField.setAccessible(true);
		positionField.set(player, new WorldPosition(210020000));
		return player;
	}
	private static void complete(Player player, int questId) {
		QuestState existing = player.getQuestStateList().getQuestState(questId);
		if (existing == null)
			player.getQuestStateList().addQuest(questId, new QuestState(questId, QuestStatus.COMPLETE));
		else
			existing.setStatus(QuestStatus.COMPLETE);
	}
	private static QuestStatus status(Player player, int questId) {
		QuestState state = player.getQuestStateList().getQuestState(questId);
		return state == null ? null : state.getStatus();
	}
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		try {
			com.aionemu.commons.configuration.transformers.PropertyTransformers.register(new com.aionemu.gameserver.services.cron.CronExpressionTransformer());
			Config.load();
			DataManager.getInstance();
			DataManager.waitForValidationToFinishAndShutdownOnFail();
			QuestEngine engine = QuestEngine.getInstance();
			Map<Integer, AbstractQuestHandler> restored = new TreeMap<>();
			try (var paths = Files.list(Path.of("data/handlers/quest/eltnen"))) {
				for (Path path : paths.filter(p -> p.getFileName().toString().matches("_(103[1-9]|104[0-3]|1300)\\D.*\\.java")).toList()) {
					String name = path.getFileName().toString().replace(".java", "");
					AbstractQuestHandler handler = (AbstractQuestHandler) Class.forName("quest.eltnen." + name).getDeclaredConstructor().newInstance();
					restored.put(handler.getQuestId(), handler);
					engine.addQuestHandler(handler);
				}
			}
			check(restored.size() == 14, "All 14 campaigns must load");
			var npcsField = QuestEngine.class.getDeclaredField("questNpcs");
			npcsField.setAccessible(true);
			Collection<QuestNpc> npcs = ((Map<Integer, QuestNpc>) npcsField.get(engine)).values();
			Set<Integer> spawns = QuestSpawnAnalyzer.loadNpcIdsSpawnedByHandlers();
			DataManager.SPAWNS_DATA.addAllNpcIdsToSet(spawns);
			DataManager.TOWN_SPAWNS_DATA.addAllNpcIdsToSet(spawns);
			spawns.add(213575); // Balaur Conspiracy's two dynamic waves, passed through a constant.
			for (QuestNpc npc : npcs)
				check(spawns.contains(npc.getNpcId()), "Missing NPC spawn " + npc.getNpcId());
			for (int questId : restored.keySet()) {
				Map<String, List<Integer>> sources = new HashMap<>();
				for (var drop : DataManager.QUEST_DATA.getQuestById(questId).getQuestDrop())
					sources.computeIfAbsent(drop.getItemId() + ":" + drop.getCollectingStep(), key -> new ArrayList<>()).add(drop.getNpcId());
				for (var source : sources.entrySet())
					check(source.getValue().stream().anyMatch(spawns::contains), "Missing quest-drop source " + questId + ":" + source);
			}
			for (AbstractQuestHandler handler : restored.values()) {
				if (handler.getQuestId() == 1300) continue;
				Player fresh = player(Race.ELYOS, 37);
				handler.onLevelChangedEvent(fresh);
				check(status(fresh, handler.getQuestId()) == null, "Campaign bypassed Orders " + handler.getQuestId());
				Player asmodian = player(Race.ASMODIANS, 37);
				complete(asmodian, 1300);
				handler.onLevelChangedEvent(asmodian);
				check(status(asmodian, handler.getQuestId()) == null, "Wrong race unlocked " + handler.getQuestId());
				for (QuestStatus saved : List.of(QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE)) {
					Player existing = player(Race.ELYOS, 37);
					complete(existing, 1300);
					QuestState state = new QuestState(handler.getQuestId(), saved);
					state.setQuestVar(12345);
					existing.getQuestStateList().addQuest(handler.getQuestId(), state);
					handler.onEnterWorldEvent(new QuestEnv(null, existing, handler.getQuestId()));
					check(status(existing, handler.getQuestId()) == saved && state.getQuestVars().getQuestVars() == 12345,
						"Login changed existing progress " + handler.getQuestId());
				}
			}
			Player elyos = player(Race.ELYOS, 37);
			complete(elyos, 1300);
			for (var handler : restored.values()) handler.onQuestCompletedEvent(new QuestEnv(null, elyos, 1300));
			for (int id : List.of(1031,1032,1033,1034,1035,1036,1037,1038,1043))
				check(status(elyos, id) == QuestStatus.START, "Starter missing " + id);
			for (int id : List.of(1039,1040,1041,1042))
				check(status(elyos, id) == QuestStatus.LOCKED, "Prerequisite skipped " + id);
			complete(elyos, 1036);
			restored.get(1040).onQuestCompletedEvent(new QuestEnv(null, elyos, 1036));
			check(status(elyos, 1040) == QuestStatus.START, "1036 does not unlock 1040");
			for (int verteron : List.of(1016, 14016)) {
				Player water = player(Race.ELYOS, 37);
				complete(water,1300); complete(water,1035); complete(water,verteron);
				restored.get(1039).onLevelChangedEvent(water);
				check(status(water,1039) == QuestStatus.START, "Verteron alternative rejected " + verteron);
			}
			Player modern = player(Race.ELYOS,37);
			complete(modern,1300); complete(modern,14020);
			for (int id=14021; id<=14026; id++)
				check(QuestService.checkStartConditions(modern,id,false), "Restoration blocks modern quest " + id);
			System.out.println("LEGACY ELTNEN CHECK PASSED: " + checks + " checks, 14 handlers, " + npcs.size() + " registered NPCs");
			System.exit(0);
		} catch (Throwable failure) {
			failure.printStackTrace(); System.exit(1);
		}
	}
}
