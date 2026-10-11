package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilderFactory;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.skill.NpcSkillTemplateEntry;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTemplate;
import instance.KromedesTrialInstance;
import ai.instance.kromedesTrial.KaligaAI;

/** Calls actual encounter/AI methods with recording boundaries; no World/ID/native spawn or cast. */
public class KaligaEncounterCheck {
	private static int checks;
	private static void check(boolean ok, String message) {
		checks++;
		if (!ok) throw new AssertionError(message);
	}
	private static void set(Object object, Class<?> owner, String name, Object value) throws Exception {
		var field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(object, value);
	}
	private static class Encounter extends KromedesTrialInstance {
		List<Integer> spawned;
		Encounter() { super(null); } // bypassed only by the isolated recording fixture
		void enter() { spawnFinalEncounter(); }
		@Override protected VisibleObject spawn(int id, float x, float y, float z, byte heading) {
			spawned.add(id); return null;
		}
	}
	private static class Boss extends KaligaAI {
		Set<Integer> alive, removed;
		int waves;
		Boss() { super(null); }
		@Override protected boolean hasLivingRelic(int id) { return alive.contains(id); }
		@Override protected void removeRelicEffect(int id) { removed.add(id); }
		@Override protected void spawnGolemWave() { waves++; }
		void health(int hp) { checkGolemWaves(hp); }
	}
	public static void main(String[] args) throws Exception {
		var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		var unsafe = (sun.misc.Unsafe) field.get(null);
		int[] originals = {217000, 217002, 216982}, weakened = {217001, 217003, 217004};
		for (int mask = 0; mask < 8; mask++) {
			Encounter encounter = (Encounter) unsafe.allocateInstance(Encounter.class);
			encounter.spawned = new ArrayList<>();
			Set<Integer> defeated = new HashSet<>();
			List<Integer> expected = new ArrayList<>(List.of(mask == 0 ? 217006 : 217005));
			for (int i = 0; i < 3; i++) if ((mask & (1 << i)) != 0) { defeated.add(originals[i]); expected.add(weakened[i]); }
			set(encounter, KromedesTrialInstance.class, "isBossSpawned", new AtomicBoolean());
			set(encounter, KromedesTrialInstance.class, "defeatedSubordinates", defeated);
			encounter.enter();
			check(encounter.spawned.equals(expected), "Wrong final boss/subordinates for mask " + mask);
			encounter.enter();
			check(encounter.spawned.equals(expected), "Duplicate final encounter");
		}
		for (int mask = 0; mask < 4; mask++) {
			Boss boss = (Boss) unsafe.allocateInstance(Boss.class);
			boss.alive = new HashSet<>(); boss.removed = new HashSet<>();
			if ((mask & 1) != 0) boss.alive.add(282095);
			if ((mask & 2) != 0) boss.alive.add(282093);
			boss.onEndUseSkill(null, 27);
			check(boss.removed.contains(19247) == ((mask & 1) == 0), "Strength relic cast/death race");
			check(boss.removed.contains(19248) == ((mask & 2) == 0), "Mana relic cast/death race");
			for (int id : new int[] {19247, 19248, 16523}) {
				boss.removed.clear();
				var effect = (com.aionemu.gameserver.skillengine.model.Effect) unsafe.allocateInstance(com.aionemu.gameserver.skillengine.model.Effect.class);
				var template = new com.aionemu.gameserver.skillengine.model.SkillTemplate();
				set(template, com.aionemu.gameserver.skillengine.model.SkillTemplate.class, "skillId", id);
				set(effect, com.aionemu.gameserver.skillengine.model.Effect.class, "skillTemplate", template);
				boss.onEffectApplied(effect);
				check(boss.removed.contains(19247) == (id != 16523 && (mask & 1) == 0), "Delayed Strength impact after relic death");
				check(boss.removed.contains(19248) == (id != 16523 && (mask & 2) == 0), "Delayed Mana impact after relic death");
			}
		}
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of(args[0], "data/static_data/npc_skills/npc_skills.xml").toFile());
		var groups = doc.getElementsByTagName("npc_skills");
		for (int i = 0; i < groups.getLength(); i++) {
			var group = (org.w3c.dom.Element) groups.item(i);
			if (!group.getAttribute("npc_ids").equals("217006")) continue;
			var skills = group.getElementsByTagName("npc_skill");
			for (int j = 0; j < skills.getLength(); j++) {
				var skill = (org.w3c.dom.Element) skills.item(j);
				int id = Integer.parseInt(skill.getAttribute("id"));
				if (id == 19247 || id == 19248) {
					check(skill.getAttribute("target").equals("ME"), "Buff must target Kaliga");
					var cond = (org.w3c.dom.Element) skill.getElementsByTagName("cond").item(0);
					check(cond.getAttribute("cond_type").equals("NPC_IS_ALIVE") && cond.getAttribute("npc_id").equals(id == 19247 ? "282095" : "282093"), "Buff must depend on correct living relic");
				} else if (id == 19246) {
					NpcSkillTemplate template = new NpcSkillTemplate();
					set(template, NpcSkillTemplate.class, "prob", Integer.parseInt(skill.getAttribute("prob")));
					set(template, NpcSkillTemplate.class, "prio", Integer.parseInt(skill.getAttribute("prio")));
					set(template, NpcSkillTemplate.class, "maxTime", Integer.parseInt(skill.getAttribute("max_time")));
					var entry = new NpcSkillTemplateEntry(template);
					check(entry.isReady(100, 0) && entry.getPriority() > 90, "Opening judgment must precede buffs");
					check(!entry.isReady(50, 10001), "Opening window must expire");
				}
			}
		}
		Boss waveBoss = (Boss) unsafe.allocateInstance(Boss.class);
		for (int[] point : new int[][] {{100,0},{76,0},{75,1},{74,1},{100,1},{51,1},{50,2},{49,2},{25,3},{24,3},{1,3},{100,3}}) {
			waveBoss.health(point[0]);
			check(waveBoss.waves == point[1], "Repeated/skipped/healing golem threshold");
		}
		Boss burstBoss = (Boss) unsafe.allocateInstance(Boss.class);
		burstBoss.health(20);
		check(burstBoss.waves == 3, "Burst damage must retain all crossed waves");
		check(checks == 67, "Missing encounter/template coverage");
		System.out.println("KALIGA ENCOUNTER CHECK PASSED: " + checks + " checks");
		System.out.println("OK: " + checks + " Kaliga encounter checks");
	}
}
