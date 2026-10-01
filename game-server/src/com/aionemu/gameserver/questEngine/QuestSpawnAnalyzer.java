package com.aionemu.gameserver.questEngine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.AIConfig;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.configs.main.InstanceConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.factions.NpcFactionTemplate;
import com.aionemu.gameserver.model.templates.quest.QuestCategory;
import com.aionemu.gameserver.model.templates.quest.QuestNpc;
import com.aionemu.gameserver.model.templates.quest.XMLStartCondition;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.models.KillSpawnedData;
import com.aionemu.gameserver.questEngine.handlers.models.XMLQuest;

public class QuestSpawnAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(QuestSpawnAnalyzer.class);

	private QuestSpawnAnalyzer() {
	}

	static void run(Collection<AbstractQuestHandler> questHandlers, Collection<QuestNpc> questNpcs, boolean ignoreEventQuests) {
		log.info("Analyzing quest handlers (ignoreEventQuests=" + ignoreEventQuests + ")...");
		long timeMillis = System.currentTimeMillis();
		Set<Integer> unobtainableQuests = new HashSet<>();
		Set<Integer> factionIds = new HashSet<>();
		Set<Integer> allSpawns = loadNpcIdsSpawnedByHandlers();
		DataManager.SPAWNS_DATA.addAllNpcIdsToSet(allSpawns);
		DataManager.TOWN_SPAWNS_DATA.addAllNpcIdsToSet(allSpawns);
		DataManager.EVENT_DATA.addAllNpcIdsToSet(allSpawns);
		for (XMLQuest quest : DataManager.XML_QUESTS.getAllQuests()) {
			if (quest instanceof KillSpawnedData killSpawned)
				allSpawns.addAll(killSpawned.getSpawnedNpcIds(allSpawns));
		}
		// Housing managers are spawned for individual houses, not by the world spawn templates.
		DataManager.HOUSE_DATA.getLands().forEach(land -> allSpawns.add(land.getManagerNpcId()));
		for (NpcFactionTemplate nft : DataManager.NPC_FACTIONS_DATA.getNpcFactionsData()) {
			if (nft.getNpcIds() == null || nft.getNpcIds().stream().anyMatch(allSpawns::contains))
				factionIds.add(nft.getId());
		}
		for (AbstractQuestHandler qh : questHandlers) {
			QuestTemplate qt = DataManager.QUEST_DATA.getQuestById(qh.getQuestId());
			if (qt.getMinlevelPermitted() == 99 || qt.getNpcFactionId() > 0 && !factionIds.contains(qt.getNpcFactionId()))
				unobtainableQuests.add(qh.getQuestId()); // players can still have these quests from before an update
		}
		Map<Set<Integer>, List<Integer>> missingSpawnsByQuests = new HashMap<>();
		for (QuestNpc npc : questNpcs) {
			if (allSpawns.contains(npc.getNpcId()))
				continue;
			Set<Integer> questIds = npc.findAllRegisteredQuestIds(id -> (!ignoreEventQuests || id < 80000 && DataManager.QUEST_DATA.getQuestById(id).getCategory() != QuestCategory.EVENT)
				&& !isUnobtainable(id, unobtainableQuests) && !existsSpawnDataForAnyAlternativeNpc(id, npc.getNpcId(), allSpawns));
			if (questIds.isEmpty())
				continue;
			missingSpawnsByQuests.computeIfAbsent(questIds, _ -> new ArrayList<>()).add(npc.getNpcId());
		}
		timeMillis = System.currentTimeMillis() - timeMillis;
		if (missingSpawnsByQuests.isEmpty()) {
			log.info("Quest handler analysis finished in {} ms without errors", timeMillis);
		} else {
			String missingSpawns = missingSpawnsByQuests.entrySet().stream()
				.map(e -> "\n\tNpc " + e.getValue().stream().sorted().map(String::valueOf).collect(Collectors.joining("/")) + " (quests: " + e.getKey().stream().sorted().map(String::valueOf).collect(Collectors.joining(", ")) + ")")
				.sorted()
				.collect(Collectors.joining());
			log.warn("Quest handler analysis finished in {} ms. Found {} missing quest npc spawns:{}", timeMillis, missingSpawnsByQuests.size(), missingSpawns);
		}
	}

	private static boolean isUnobtainable(int questId, Set<Integer> unobtainableQuests) {
		if (unobtainableQuests.contains(questId))
			return true;
		QuestTemplate qt = DataManager.QUEST_DATA.getQuestById(questId);
		for (XMLStartCondition startCondition : qt.getXMLStartConditions()) {
			if (startCondition.getFinishedPreconditions() == null)
				continue;
			if (startCondition.getFinishedPreconditions().stream().allMatch(fpc -> isUnobtainable(fpc.getQuestId(), unobtainableQuests)))
				return true;
		}
		return false;
	}

	/**
	 * @return True, if alternative npc ids, which are valid for this quest, appear in spawn templates (e.g. mobs for quest kills or talk npcs)
	 */
	private static boolean existsSpawnDataForAnyAlternativeNpc(int questId, int npcId, Set<Integer> allSpawns) {
		XMLQuest quest = DataManager.XML_QUESTS.getQuest(questId);
		if (quest == null)
			return true; // no way to get alternative npcs from non-xml based handlers, so assume the quest spawns work (lol)
		Set<Integer> alternativeNpcs = quest.getAlternativeNpcs(npcId);
		if (alternativeNpcs == null)
			return false;
		return alternativeNpcs.stream().anyMatch(allSpawns::contains);
	}

	public static Set<Integer> loadNpcIdsSpawnedByHandlers() {
		Set<Integer> npcIds = new HashSet<>();
		Pattern pattern = Pattern.compile("\\bsp(?:awn)?(?:ForFiveMinutes|Temporarily|WithWalker|AndSetRespawn)?\\([^,\\d]*(\\d{6})(?:\\s*:\\s*(\\d{6}))?");
		Set<String> spawnIdMethods = new HashSet<>();
		Set<String> incrementedSpawnIdMethods = new HashSet<>();
		Map<String, Set<Integer>> returnedNpcIds = new HashMap<>();
		parseSpawnNpcIds(InstanceConfig.HANDLER_DIRECTORY, pattern, npcIds, spawnIdMethods, incrementedSpawnIdMethods, returnedNpcIds);
		parseSpawnNpcIds(GSConfig.QUEST_HANDLER_DIRECTORY, pattern, npcIds, spawnIdMethods, incrementedSpawnIdMethods, returnedNpcIds);
		parseSpawnNpcIds(AIConfig.HANDLER_DIRECTORY, pattern, npcIds, spawnIdMethods, incrementedSpawnIdMethods, returnedNpcIds);
		for (String method : spawnIdMethods)
			npcIds.addAll(returnedNpcIds.getOrDefault(method, Set.of()));
		for (String method : incrementedSpawnIdMethods)
			for (int id : returnedNpcIds.getOrDefault(method, Set.of()))
				npcIds.add(id + 1);
		return npcIds;
	}

	private static void parseSpawnNpcIds(File sourceDir, Pattern pattern, Set<Integer> npcIds, Set<String> spawnIdMethods, Set<String> incrementedSpawnIdMethods, Map<String, Set<Integer>> returnedNpcIds) {
		Pattern ternarySpawn = Pattern.compile("\\bspawn\\s*\\(\\s*\\([^;]*?\\?\\s*(\\d{6})\\s*:\\s*(\\d{6})\\s*\\)");
		Pattern spawnMethod = Pattern.compile("\\bspawn\\s*\\(\\s*(get\\w+Id)\\(\\)(\\s*\\+\\s*1)?");
		Pattern returnedId = Pattern.compile("\\bint\\s+(get\\w+Id)\\(\\)\\s*\\{\\s*return\\s+(\\d{6})\\s*;");
		Pattern variableTernary = Pattern.compile("\\b(\\w+)\\s*=\\s*[^;?]+\\?\\s*(\\d{6})\\s*:\\s*(\\d{6})\\s*;");
		try (Stream<Path> stream = Files.walk(sourceDir.toPath())) {
			for (Path path : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
				String source = Files.readString(path).replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
				Matcher matcher = pattern.matcher(source);
				while (matcher.find()) {
					for (int i = 1; i <= matcher.groupCount(); i++) {
						String group = matcher.group(i);
						if (group != null)
							npcIds.add(Integer.parseInt(group));
					}
				}
				Matcher ternary = ternarySpawn.matcher(source);
				while (ternary.find()) {
					npcIds.add(Integer.parseInt(ternary.group(1)));
					npcIds.add(Integer.parseInt(ternary.group(2)));
				}
				Matcher methodCall = spawnMethod.matcher(source);
				while (methodCall.find()) {
					if (methodCall.group(2) == null)
						spawnIdMethods.add(methodCall.group(1));
					else
						incrementedSpawnIdMethods.add(methodCall.group(1));
				}
				Matcher methodReturn = returnedId.matcher(source);
				while (methodReturn.find())
					returnedNpcIds.computeIfAbsent(methodReturn.group(1), _ -> new HashSet<>()).add(Integer.parseInt(methodReturn.group(2)));
				Matcher ternaryVariable = variableTernary.matcher(source);
				while (ternaryVariable.find()) {
					if (Pattern.compile("\\bspawn\\s*\\(\\s*" + Pattern.quote(ternaryVariable.group(1)) + "\\s*,").matcher(source).find()) {
						npcIds.add(Integer.parseInt(ternaryVariable.group(2)));
						npcIds.add(Integer.parseInt(ternaryVariable.group(3)));
					}
				}
				// Some instance handlers select a boss ID before passing it to spawn(id, ...).
				Matcher switchResult = Pattern.compile("\\bint\\s+(\\w+)\\s*=\\s*switch\\s*\\([\\s\\S]*?\\)\\s*\\{([\\s\\S]*?)\\};").matcher(source);
				while (switchResult.find()) {
					String variable = switchResult.group(1);
					if (!Pattern.compile("\\bspawn\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*,").matcher(source).find())
						continue;
					Matcher npcId = Pattern.compile("\\b(\\d{6})\\b").matcher(switchResult.group(2));
					while (npcId.find())
						npcIds.add(Integer.parseInt(npcId.group(1)));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
