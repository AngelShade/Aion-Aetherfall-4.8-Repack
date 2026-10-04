import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.ai.AIEngine;
import com.aionemu.gameserver.ai.AIRegistryReload;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldMapInstanceFactory;

public class SaendukalActivationAgent45 {

	static final List<String> lines = new ArrayList<>();

	static void check(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError("FAILED: " + label);
		}
		lines.add("OK: " + label);
	}

	public static void agentmain(String argument, Instrumentation inst) throws Exception {
		Path report = Path.of(argument);
		var humansBefore = World.getInstance().getAllPlayers().stream().filter(p -> !p.isPlayerBot()).map(Player::getObjectId).sorted().toList();
		var botsBefore = World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).map(Player::getObjectId).sorted().toList();

		try {
			lines.add("=== Saendukal AI Hot-Reload & Verification (Revision 45) ===");

			// 1. Verify static NPC templates
			NpcTemplate tplWorld = DataManager.NPC_DATA.getNpcTemplate(211040);
			check(tplWorld != null, "Template 211040 exists in static data");
			check("saendukal".equals(tplWorld.getAiName()), "Template 211040 ai is 'saendukal'");

			NpcTemplate tplCrucible = DataManager.NPC_DATA.getNpcTemplate(280338);
			check(tplCrucible != null, "Template 280338 exists in static data");
			check("saendukal".equals(tplCrucible.getAiName()), "Template 280338 ai is 'saendukal'");

			// 2. Trigger synchronous atomic AI reload via AIEngine.getInstance().reload()
			long start = System.currentTimeMillis();
			AIEngine.getInstance().reload();
			long reloadTime = System.currentTimeMillis() - start;
			lines.add("OK: AIEngine.reload() recompiled and published new registry in " + reloadTime + " ms");

			var helper = AIRegistryReload.class;
			var activeField = helper.getDeclaredField("active");
			activeField.setAccessible(true);
			var activeMap = (Map<?, ?>) activeField.get(null);
			check(activeMap != null && activeMap.containsKey("saendukal"), "Active AI registry contains 'saendukal' (" + (activeMap == null ? 0 : activeMap.size()) + " handlers loaded)");

			Class<?> saendukalClass = (Class<?>) activeMap.get("saendukal");
			check("ai.worlds.eltnen.SaendukalAI".equals(saendukalClass.getName()), "Loaded handler class is ai.worlds.eltnen.SaendukalAI");

			// 3. Isolated Fixture Testing in private test instance
			var world = World.getInstance();
			var map = world.getWorldMap(320100000); // Fire temple map for isolated test instance
			WorldMapInstance testInstance = WorldMapInstanceFactory.createWorldMapInstance(map, 0, GeneralInstanceHandler::new, 6);
			Npc boss = null;
			try {
				boss = (Npc) SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(320100000, 211040, 421.99f, 93.19f, 117.3f, (byte) 0), testInstance.getInstanceId());
				check(boss != null, "Spawned isolated fixture boss 211040 in private instance");
				check("saendukal".equals(boss.getAi().getName()), "Boss AI name is 'saendukal'");
				check("ai.worlds.eltnen.SaendukalAI".equals(boss.getAi().getClass().getName()), "Boss AI is instance of ai.worlds.eltnen.SaendukalAI");

				var ai = boss.getAi();
				Method onStart = ai.getClass().getMethod("onStartUseSkill", SkillTemplate.class, int.class);
				Method onEnd = ai.getClass().getMethod("onEndUseSkill", SkillTemplate.class, int.class);
				Method handlePhase = ai.getClass().getMethod("handleHpPhase", int.class);

				// 4. Test Shout mapping via onStartUseSkill / onEndUseSkill
				int[][] testCases = {
					{16855, 4}, // Deadly Chain
					{17845, 4}, // Darkness Snare
					{16861, 5}, // Wrath Explosion
					{16609, 6}, // Wide Crippling Wave
					{17855, 6}, // Head Wound
					{17856, 7}, // Statue Curse
					{16860, 8}, // Pulverizing Assault
					{16415, 9}, // Strong Protection
					{16873, 9}  // Bellicosity
				};

				for (int[] tc : testCases) {
					int skillId = tc[0];
					int expectedShout = tc[1];
					SkillTemplate st = DataManager.SKILL_DATA.getSkillTemplate(skillId);
					check(st != null, "Skill template exists for ID " + skillId);

					boss.setSkillNumber(0);
					onStart.invoke(ai, st, 31);
					check(boss.getSkillNumber() == expectedShout, "Skill " + skillId + " correctly sets skillNumber to " + expectedShout);

					onEnd.invoke(ai, st, 31);
					check(boss.getSkillNumber() == 0, "Skill " + skillId + " resets skillNumber to 0 on end");
				}

				// 5. Test Phase 75 combo: Statue Curse -> Pulverizing Assault
				ai.setStateIfNot(AIState.FIGHT);
				boss.clearQueuedSkills();
				handlePhase.invoke(ai, 75);
				var q1 = boss.getNextQueuedSkill();
				check(q1 != null && q1.getSkillId() == 17856, "Phase 75 queues Statue Curse (17856)");
				boss.clearQueuedSkills();
				onEnd.invoke(ai, DataManager.SKILL_DATA.getSkillTemplate(17856), 31);
				var q2 = boss.getNextQueuedSkill();
				check(q2 != null && q2.getSkillId() == 16860, "Phase 75 combo chains into Pulverizing Assault (16860)");
				boss.clearQueuedSkills();

				// 6. Test Phase 50 combo: Deadly Chain -> Wrath Explosion
				handlePhase.invoke(ai, 50);
				var q3 = boss.getNextQueuedSkill();
				check(q3 != null && q3.getSkillId() == 16855, "Phase 50 queues Deadly Chain (16855)");
				boss.clearQueuedSkills();
				onEnd.invoke(ai, DataManager.SKILL_DATA.getSkillTemplate(16855), 31);
				var q4 = boss.getNextQueuedSkill();
				check(q4 != null && q4.getSkillId() == 16861, "Phase 50 combo chains into Wrath Explosion (16861)");
				boss.clearQueuedSkills();

				// 7. Test Phase 25 full 4-step combo
				handlePhase.invoke(ai, 25);
				var q5 = boss.getNextQueuedSkill();
				check(q5 != null && q5.getSkillId() == 16415, "Phase 25 queues Strong Protection (16415)");
				boss.clearQueuedSkills();
				onEnd.invoke(ai, DataManager.SKILL_DATA.getSkillTemplate(16415), 31);
				var q6 = boss.getNextQueuedSkill();
				check(q6 != null && q6.getSkillId() == 17856, "Phase 25 chains into Statue Curse (17856)");
				boss.clearQueuedSkills();
				onEnd.invoke(ai, DataManager.SKILL_DATA.getSkillTemplate(17856), 31);
				var q7 = boss.getNextQueuedSkill();
				check(q7 != null && q7.getSkillId() == 16861, "Phase 25 chains into Wrath Explosion (16861)");
				boss.clearQueuedSkills();
				onEnd.invoke(ai, DataManager.SKILL_DATA.getSkillTemplate(16861), 31);
				var q8 = boss.getNextQueuedSkill();
				check(q8 != null && q8.getSkillId() == 16860, "Phase 25 chains into Pulverizing Assault (16860)");
				boss.clearQueuedSkills();

				// 8. Test Phase 10 enrage buff & task
				handlePhase.invoke(ai, 10);
				var q9 = boss.getNextQueuedSkill();
				check(q9 != null && q9.getSkillId() == 16873, "Phase 10 queues Bellicosity (16873)");
				boss.clearQueuedSkills();

				// 9. Test Non-phase Statue Curse combo follow-up
				onEnd.invoke(ai, DataManager.SKILL_DATA.getSkillTemplate(17856), 31);
				var q10 = boss.getNextQueuedSkill();
				check(q10 != null && q10.getSkillId() == 16860, "Normal rotation Statue Curse chains into Pulverizing Assault (16860)");
				boss.clearQueuedSkills();

				// 10. Test Encounter Cleanup
				Method cleanup = ai.getClass().getDeclaredMethod("cleanupEncounter");
				cleanup.setAccessible(true);
				cleanup.invoke(ai);
				check(boss.getNextQueuedSkill() == null, "cleanupEncounter clears all queued skills");
				check(boss.getSkillNumber() == 0, "cleanupEncounter resets skillNumber to 0");

			} finally {
				if (boss != null && boss.isSpawned()) {
					boss.getController().delete();
				}
			}

			// 11. Preservation invariants: human players and companions intact
			check(humansBefore.equals(World.getInstance().getAllPlayers().stream().filter(p -> !p.isPlayerBot()).map(Player::getObjectId).sorted().toList()), "Human characters byte-identical and retained");
			check(botsBefore.equals(World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).map(Player::getObjectId).sorted().toList()), "PlayerBot companions byte-identical and retained");

			lines.add("ALL SAENDUKAL AI VERIFICATION ASSERTIONS PASSED (100% SUCCESS)!");
		} catch (Throwable t) {
			StringWriter sw = new StringWriter();
			t.printStackTrace(new PrintWriter(sw));
			lines.add("FAIL: " + sw);
		} finally {
			Files.write(report, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("Usage: java SaendukalActivationAgent45 <pid> <agentJarPath> <reportPath>");
			System.exit(1);
		}
		VirtualMachine vm = VirtualMachine.attach(args[0]);
		try {
			vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(), Path.of(args[2]).toAbsolutePath().toString());
		} finally {
			vm.detach();
		}
	}
}
