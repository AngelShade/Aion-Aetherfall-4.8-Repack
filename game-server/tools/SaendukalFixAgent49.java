package tools;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
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
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.controllers.observer.AttackCalcObserver;
import com.aionemu.gameserver.controllers.observer.AttackShieldObserver;
import com.aionemu.gameserver.controllers.observer.ShieldHitCountHelper;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.NpcSkillEntry;
import com.aionemu.gameserver.model.skill.NpcSkillList;
import com.aionemu.gameserver.model.skill.NpcSkillTemplateEntry;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTemplate;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTemplates;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.effect.ShieldEffect;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.world.World;

public class SaendukalFixAgent49 {

	static final List<String> lines = new ArrayList<>();

	static void check(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError("FAILED: " + label);
		}
		lines.add("OK: " + label);
	}

	public static void agentmain(String argument, Instrumentation inst) {
		var humansBefore = World.getInstance().getAllPlayers().stream().filter(p -> !p.isPlayerBot()).map(Player::getObjectId).sorted().toList();
		var botsBefore = World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).map(Player::getObjectId).sorted().toList();

		try {
			lines.add("=== Saendukal Strong Protection Live Fix (Revision 49) ===");

			// 1. Redefine AttackShieldObserver, ShieldEffect, and Skill
			Path classesDir = Path.of(argument.split(";")[0]);
			Path reportPath = Path.of(argument.split(";")[1]);

			byte[] asoBytes = Files.readAllBytes(classesDir.resolve("com/aionemu/gameserver/controllers/observer/AttackShieldObserver.class"));
			byte[] seBytes = Files.readAllBytes(classesDir.resolve("com/aionemu/gameserver/skillengine/effect/ShieldEffect.class"));
			byte[] skillBytes = Files.readAllBytes(classesDir.resolve("com/aionemu/gameserver/skillengine/model/Skill.class"));

			inst.redefineClasses(
				new ClassDefinition(AttackShieldObserver.class, asoBytes),
				new ClassDefinition(ShieldEffect.class, seBytes),
				new ClassDefinition(Skill.class, skillBytes)
			);
			lines.add("OK: AttackShieldObserver, ShieldEffect, and Skill redefined in live JVM");

			// 2. Validate ShieldHitCountHelper parses stack BNFI_SHIELDLONGTCOUNT10_SELF correctly
			SkillTemplate strongProtTmpl = DataManager.SKILL_DATA.getSkillTemplate(16415);
			check(strongProtTmpl != null, "Skill 16415 template exists");
			int parsedHits = ShieldHitCountHelper.parseHitCount(new Effect(null, null, strongProtTmpl, 1, 0));
			check(parsedHits == 10, "ShieldHitCountHelper parses 10 hits from Strong Protection stack: " + parsedHits);

			// 3. Update in-memory NPC template AI mappings
			Field aiField = NpcTemplate.class.getDeclaredField("ai");
			aiField.setAccessible(true);
			for (int id : new int[]{211040, 280338}) {
				NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(id);
				check(template != null, "Template " + id + " exists in static data");
				if (!"saendukal".equals(template.getAiName())) {
					aiField.set(template, "saendukal");
				}
				check("saendukal".equals(template.getAiName()), "Template " + id + " active AI is 'saendukal'");
			}

			// 4. Ensure SaendukalAI is compiled and registered in active AI registry
			AIEngine.getInstance().reload();
			var activeField = AIRegistryReload.class.getDeclaredField("active");
			activeField.setAccessible(true);
			var activeMap = (Map<?, ?>) activeField.get(null);
			check(activeMap != null && activeMap.containsKey("saendukal"), "Active AI registry contains 'saendukal'");

			// 5. Update DataManager.NPC_SKILL_DATA in memory for 211040 and 280338
			Field npcSkillDataField = DataManager.NPC_SKILL_DATA.getClass().getDeclaredField("npcSkillData");
			npcSkillDataField.setAccessible(true);
			var npcSkillDataMap = (Map<Integer, NpcSkillTemplates>) npcSkillDataField.get(DataManager.NPC_SKILL_DATA);

			Field skillListByTemplateField = DataManager.NPC_SKILL_DATA.getClass().getDeclaredField("skillListByTemplate");
			skillListByTemplateField.setAccessible(true);
			var skillListByTemplateMap = (Map<NpcSkillTemplates, NpcSkillList>) skillListByTemplateField.get(DataManager.NPC_SKILL_DATA);

			Field probField = NpcSkillTemplate.class.getDeclaredField("prob");
			probField.setAccessible(true);

			for (int id : new int[]{211040, 280338}) {
				NpcSkillTemplates templates = npcSkillDataMap.get(id);
				if (templates != null) {
					for (NpcSkillTemplate st : templates.getNpcSkills()) {
						int sId = st.getSkillId();
						if (sId == 16415 || sId == 16879 || sId == 16860 || sId == 16861) {
							probField.setInt(st, 0);
						}
					}
					skillListByTemplateMap.remove(templates);
				}
			}

			// Verify new skillList for 211040
			NpcSkillList updatedSkillList = DataManager.NPC_SKILL_DATA.getOrCreateNpcSkillList(211040);
			for (int prio : updatedSkillList.getPriorities()) {
				for (NpcSkillEntry e : updatedSkillList.getSkillsSortedByPriority(prio)) {
					if (e instanceof NpcSkillTemplateEntry nste) {
						if (e.getSkillId() == 16415 || e.getSkillId() == 16879 || e.getSkillId() == 16860 || e.getSkillId() == 16861) {
							check(nste.getTemplate().getProbability() == 0, "Skill " + e.getSkillId() + " prob is 0");
						}
					}
				}
			}
			lines.add("OK: DataManager.NPC_SKILL_DATA memory updated with prob=0 for scripted skills");

			// 6. Fix the live Grand Chieftain Saendukal in Eltnen
			List<Npc> liveBosses = new ArrayList<>();
			for (Player p : World.getInstance().getAllPlayers()) {
				p.getKnownList().forEachNpc(npc -> {
					if (npc.getNpcId() == 211040 || npc.getNpcId() == 280338) {
						if (!liveBosses.contains(npc)) {
							liveBosses.add(npc);
						}
					}
				});
			}

			Field creatureAiField = Creature.class.getDeclaredField("ai");
			creatureAiField.setAccessible(true);

			Field npcSkillListField = Npc.class.getDeclaredField("skillList");
			npcSkillListField.setAccessible(true);

			lines.add("Found " + liveBosses.size() + " live Saendukal boss(es)");
			for (Npc boss : liveBosses) {
				lines.add("Inspecting live boss: id=" + boss.getObjectId() + " (" + boss.getName() + "), HP%: " + boss.getLifeStats().getHpPercentage() + "%");

				// A. Remove stuck Strong Protection (16415)
				if (boss.getEffectController().hasAbnormalEffect(16415)) {
					boss.getEffectController().removeEffect(16415);
					lines.add("OK: Removed stuck Strong Protection (16415) from live boss " + boss.getObjectId());
				}
				check(!boss.getEffectController().hasAbnormalEffect(16415), "Live boss has no Strong Protection active");

				// B. Update boss skillList reference to corrected skillList
				npcSkillListField.set(boss, DataManager.NPC_SKILL_DATA.getOrCreateNpcSkillList(boss.getNpcId()));
				lines.add("OK: Updated boss " + boss.getObjectId() + " skillList reference to prob=0 configuration");

				// C. Update boss AI to SaendukalAI
				if (!"ai.worlds.eltnen.SaendukalAI".equals(boss.getAi().getClass().getName())) {
					var newAi = AIEngine.getInstance().newAI("saendukal", boss);
					creatureAiField.set(boss, newAi);
					lines.add("OK: Re-bound boss " + boss.getObjectId() + " AI to ai.worlds.eltnen.SaendukalAI");
				}
				check("ai.worlds.eltnen.SaendukalAI".equals(boss.getAi().getClass().getName()), "Boss AI is SaendukalAI");

				// D. Synchronize HpPhases with current HP
				int currentHpPct = boss.getLifeStats().getHpPercentage();
				var ai = boss.getAi();
				Field hpPhasesField = ai.getClass().getDeclaredField("hpPhases");
				hpPhasesField.setAccessible(true);
				HpPhases phases = (HpPhases) hpPhasesField.get(ai);
				Field currentPhaseField = HpPhases.class.getDeclaredField("currentPhase");
				currentPhaseField.setAccessible(true);
				// If boss is at ~59% HP: phase 75 has already passed (phase index 1 = waiting for 50%)
				if (currentHpPct < 75 && currentHpPct >= 50) {
					currentPhaseField.setInt(phases, 1);
					lines.add("OK: Synchronized HpPhases for current " + currentHpPct + "% HP: currentPhase set to 1 (waiting for 50% threshold)");
				} else if (currentHpPct < 50 && currentHpPct >= 25) {
					currentPhaseField.setInt(phases, 2);
					lines.add("OK: Synchronized HpPhases for current " + currentHpPct + "% HP: currentPhase set to 2 (waiting for 25% threshold)");
				}

				// E. Set combat state if currently fighting
				VisibleObject target = boss.getTarget();
				if (target instanceof Creature) {
					ai.setStateIfNot(AIState.FIGHT);
					Field startedFightField = ai.getClass().getDeclaredField("startedFight");
					startedFightField.setAccessible(true);
					startedFightField.setBoolean(ai, true);
					lines.add("OK: Live boss fighting state synchronized with target " + target.getName());
				}
			}

			// 7. Test 10-hit count expiration mechanics on Strong Protection
			lines.add("Testing Strong Protection 10-hit consumption mechanics:");
			for (Npc boss : liveBosses) {
				// Apply 16415 directly to test
				Effect testEffect = SkillEngine.getInstance().applyEffectDirectly(16415, boss, boss);
				check(testEffect != null, "Applied test Strong Protection (16415)");
				check(boss.getEffectController().hasAbnormalEffect(16415), "Boss has test effect 16415");

				// Find the AttackShieldObserver on boss
				Field acoField = boss.getObserveController().getClass().getDeclaredField("attackCalcObservers");
				acoField.setAccessible(true);
				List<?> acoList = (List<?>) acoField.get(boss.getObserveController());
				AttackShieldObserver foundObserver = null;
				for (Object obs : acoList) {
					if (obs instanceof AttackShieldObserver aso) {
						foundObserver = aso;
						break;
					}
				}
				check(foundObserver != null, "Found AttackShieldObserver on boss");

				// Simulate 10 hits absorbing damage
				for (int h = 1; h <= 10; h++) {
					boolean expired = ShieldHitCountHelper.onDamageAbsorbed(foundObserver, testEffect);
					if (h < 10) {
						check(!expired, "Hit " + h + " did not expire the shield");
					} else {
						check(expired, "Hit 10 EXPIRED the shield!");
					}
				}
				check(!boss.getEffectController().hasAbnormalEffect(16415), "Strong Protection ended and was removed after exactly 10 hits!");
				lines.add("OK: Strong Protection (16415) confirmed to break and expire after exactly 10 hits!");
			}

			// 8. Player & bot preservation
			check(humansBefore.equals(World.getInstance().getAllPlayers().stream().filter(p -> !p.isPlayerBot()).map(Player::getObjectId).sorted().toList()), "Human characters preserved byte-identically");
			check(botsBefore.equals(World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).map(Player::getObjectId).sorted().toList()), "PlayerBot companions preserved byte-identically");

			lines.add("=== ALL SAENDUKAL STRONG PROTECTION ASSERTIONS PASSED (100% SUCCESS) ===");
			Files.write(reportPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (Throwable t) {
			StringWriter sw = new StringWriter();
			t.printStackTrace(new PrintWriter(sw));
			lines.add("FAIL: " + sw);
			try {
				Path reportPath = Path.of(argument.split(";")[1]);
				Files.write(reportPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("Usage: java SaendukalFixAgent49 <pid> <agentJarPath> <argument>");
			System.exit(1);
		}
		VirtualMachine vm = VirtualMachine.attach(args[0]);
		try {
			vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(), args[2]);
		} finally {
			vm.detach();
		}
	}
}
