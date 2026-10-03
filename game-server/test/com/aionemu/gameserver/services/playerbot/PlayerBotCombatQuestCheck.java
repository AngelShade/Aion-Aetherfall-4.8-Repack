package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.List;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.questEngine.handlers.models.Monster;
import com.aionemu.gameserver.questEngine.handlers.template.MonsterHunt;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Regression scenarios for release windows, offensive decisions and native quest objective metadata. */
public final class PlayerBotCombatQuestCheck {
	private static int checks;
	private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError("FAIL: " + message); }
	public static void main(String[] args) throws Exception {
		check(PlayerBotCharge.releaseDelay(List.of(1600, 1600, 5000), 400, 1f, 8200, false) == 3225, "full release enters final native stage before cancellation");
		check(PlayerBotCharge.releaseDelay(List.of(1600, 1600, 5000), 400, 1f, 8200, true) == 425, "finisher uses earliest native stage");
		check(PlayerBotCharge.releaseDelay(List.of(1600, 1600, 5000), 400, 0.75f, 6150, false) == 2425, "charge thresholds match native cast speed");
		check(PlayerBotCharge.releaseDelay(List.of(80, 20), 80, 1f, 100, false) < 0, "unsafe cancellation deadline rejected");
		check(PlayerBotCharge.releaseDelay(List.of(1600), 400, Float.NaN, 2000, false) < 0, "invalid speed rejected");
		try (var dialog = PlayerBotQuestDialog.open(1, 2, 3)) {
			PlayerBotQuestDialog.record(9, 2, 5, 3);
			PlayerBotQuestDialog.record(1, 9, 5, 3);
			PlayerBotQuestDialog.record(1, 2, 5, 9);
			check(!dialog.offered(5), "unrelated player, NPC and chained quest cannot authorize a turn-in");
			PlayerBotQuestDialog.record(1, 2, 4, 3);
			check(dialog.offered(4) && !dialog.offered(5), "acceptance page cannot authorize reward submission");
			Thread unrelated = new Thread(() -> PlayerBotQuestDialog.record(1, 2, 5, 3));
			unrelated.start(); unrelated.join();
			check(!dialog.offered(5), "concurrent dialog cannot change synchronous offer");
			PlayerBotQuestDialog.record(1, 2, 5, 3);
			check(dialog.offered(5), "matching native reward dialog authorizes next action");
		}
		try (var dialog = PlayerBotQuestDialog.open(1, 2, 3)) { check(!dialog.offered(5), "closed offers cannot be replayed"); }
		DataManager.QUEST_DATA = (QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("""
			<quests><quest id="900001" category="QUEST" data_driven="true"/><quest id="900002" category="MISSION"/></quests>
			"""));
		check(PlayerBotQuests.ordinary(DataManager.QUEST_DATA.getQuestById(900001)), "ordinary quests eligible for automation");
		check(!PlayerBotQuests.ordinary(DataManager.QUEST_DATA.getQuestById(900002)), "campaign and class-choice stories require human control");
		Monster current = new Monster(); current.setVar(1); current.setEndVar(70); current.setStep(2); current.addNpcIds(List.of(100001));
		Monster later = new Monster(); later.setVar(1); later.setEndVar(5); later.setStep(3); later.addNpcIds(List.of(100002));
		var handler = new MonsterHunt(900001, List.of(200001), List.of(200002), List.of(current, later), 0, 0, List.of(), 0, null, 0, true, false);
		var state = new QuestState(900001, QuestStatus.START);
		state.setQuestVarById(0, 2); state.setQuestVarById(1, 5); state.setQuestVarById(2, 1); // 69 kills in native 6-bit fields
		int before = state.getQuestVars().getQuestVars();
		check(handler.getRequiredKillNpcIds(state).equals(java.util.Set.of(100001)), "only current quest step and incomplete packed counter are targeted");
		check(state.getQuestVars().getQuestVars() == before && state.getStatus() == QuestStatus.START, "objective planning never grants kill credit");
		state.setQuestVarById(1, 6);
		check(handler.getRequiredKillNpcIds(state).isEmpty(), "completed counter is excluded before next native step");
		state.setQuestVarById(0, 3); state.setQuestVarById(1, 0); state.setQuestVarById(2, 0);
		check(handler.getRequiredKillNpcIds(state).equals(java.util.Set.of(100002)), "native quest step changes update target set");
		check(handler.getRewardNpcIds(state).isEmpty(), "active hunt cannot plan early rewards");
		state.setStatus(QuestStatus.REWARD);
		check(handler.getRequiredKillNpcIds(state).isEmpty(), "reward-ready quests never request more kills");
		check(handler.getRewardNpcIds(state).equals(java.util.Set.of(200002)), "travel uses native end NPC rather than the acceptance NPC");
		check(state.getStatus() == QuestStatus.REWARD && state.getCompleteCount() == 0, "reward destination query cannot complete or reward a quest");
		var skills = (SkillData) JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
			<skill_data>
			<skill_template skill_id="1" stack="STRIKE" lvl="1" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK"><effects><skillatk value="100"/></effects></skill_template>
			<skill_template skill_id="2" stack="STRIKE" lvl="2" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK"><effects><skillatk value="300"/></effects></skill_template>
			<skill_template skill_id="3" duration="4000" activation="ACTIVE" skilltype="PHYSICAL" skillsubtype="ATTACK"><effects><skillatk value="300"/></effects></skill_template>
			<skill_template skill_id="4" activation="ACTIVE" skilltype="MAGICAL" skillsubtype="ATTACK"><effects><poison value="100" duration2="12000" checktime="2000"/></effects></skill_template>
			</skill_data>
			"""));
		var healthy = new PlayerBotRotation.Context(PlayerClass.GLADIATOR, Role.MELEE, 100, 100, 100, 10000, 100, 1, false, false);
		var lowTarget = new PlayerBotRotation.Context(PlayerClass.GLADIATOR, Role.MELEE, 100, 100, 10, 10000, 100, 1, false, false);
		check(PlayerBotRotation.score(skills.getSkillTemplate(2), 1, healthy) > PlayerBotRotation.score(skills.getSkillTemplate(1), 1, healthy), "larger useful damage outranks arbitrary skill ID order");
		check(PlayerBotRotation.score(skills.getSkillTemplate(2), 1, healthy) > PlayerBotRotation.score(skills.getSkillTemplate(3), 1, healthy), "long casts lose to faster equally damaging skills");
		check(PlayerBotRotation.score(skills.getSkillTemplate(4), 1, lowTarget) < PlayerBotRotation.score(skills.getSkillTemplate(4), 1, healthy), "near-dead target penalizes slow periodic damage");
		var lower = new PlayerBotSkills.Entry(skills.getSkillTemplate(1), 1, PlayerBotRules.SkillKind.DAMAGE, 5);
		var higher = new PlayerBotSkills.Entry(skills.getSkillTemplate(2), 1, PlayerBotRules.SkillKind.DAMAGE, 5);
		check(PlayerBotRotation.rankedSkills(List.of(lower, higher)).equals(List.of(higher, lower)), "higher rank wins ties but lower rank remains for native resource/cooldown fallback");
		System.out.println("OK: " + checks + " charged combat, offensive ranking and quest dialogue/objective checks");
	}
}
