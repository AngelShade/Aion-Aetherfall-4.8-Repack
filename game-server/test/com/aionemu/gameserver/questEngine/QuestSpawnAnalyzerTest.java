package com.aionemu.gameserver.questEngine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aionemu.gameserver.configs.main.AIConfig;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.configs.main.InstanceConfig;
import com.aionemu.gameserver.dataholders.XMLQuests;
import com.aionemu.gameserver.questEngine.handlers.models.KillSpawnedData;
import com.aionemu.gameserver.utils.xml.JAXBUtil;

class QuestSpawnAnalyzerTest {

	@TempDir
	Path sourceDir;

	@Test
	void detectsWalkerSpawnsAndSelectedBossesWithoutCountingInactiveSpawns() throws Exception {
		Files.writeString(sourceDir.resolve("Instance.java"), """
			spawnWithWalker(230744, 1, 2, 3);
			spawnAndSetRespawn(230745, 1, 2, 3);
			spawn(elyos ? 230749 : 230750, 1, 2, 3);
			int questNpcId = switch (way) {
				case 0 -> asmodians ? 206395 : 206378;
				case 1 -> asmodians ? 206396 : 206379;
				default -> asmodians ? 206397 : 206380;
			};
			spawn(questNpcId, 1, 2, 3);
			int unrelated = switch (way) { default -> 299999; };
			// spawn(217609, 1, 2, 3);
			/* sp(217599, 1, 2, 3); */
			""");
		File oldInstances = InstanceConfig.HANDLER_DIRECTORY;
		File oldQuests = GSConfig.QUEST_HANDLER_DIRECTORY;
		File oldAi = AIConfig.HANDLER_DIRECTORY;
		try {
			InstanceConfig.HANDLER_DIRECTORY = sourceDir.toFile();
			GSConfig.QUEST_HANDLER_DIRECTORY = sourceDir.toFile();
			AIConfig.HANDLER_DIRECTORY = sourceDir.toFile();
			assertEquals(Set.of(230744, 230745, 230749, 230750, 206395, 206396, 206397, 206378, 206379, 206380),
				QuestSpawnAnalyzer.loadNpcIdsSpawnedByHandlers());
		} finally {
			InstanceConfig.HANDLER_DIRECTORY = oldInstances;
			GSConfig.QUEST_HANDLER_DIRECTORY = oldQuests;
			AIConfig.HANDLER_DIRECTORY = oldAi;
		}
	}

	@Test
	void questSummonsRequireAnAvailableTriggerAndOnlySpawnTheFirstAlternative() {
		XMLQuests quests = JAXBUtil.deserialize("""
			<quest_scripts><kill_spawned id="46504">
				<monster spawner_object_id="700772" npc_ids="216640 216641"/>
			</kill_spawned></quest_scripts>
			""", XMLQuests.class);
		KillSpawnedData quest = (KillSpawnedData) quests.getQuest(46504);
		assertEquals(Set.of(216640), quest.getSpawnedNpcIds(Set.of(700772)));
		assertTrue(quest.getSpawnedNpcIds(Set.of(700773)).isEmpty());
		assertEquals(Set.of(216640), quest.getAlternativeNpcs(216641));
	}
}
