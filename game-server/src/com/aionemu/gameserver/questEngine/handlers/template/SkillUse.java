package com.aionemu.gameserver.questEngine.handlers.template;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.models.QuestSkillData;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * @author vlog, Bobobear, Pad
 */
public class SkillUse extends AbstractTemplateQuestHandler {

	private final Set<Integer> startNpcIds = new HashSet<>();
	private final Set<Integer> endNpcIds = new HashSet<>();
	private final List<QuestSkillData> qsd;

	public SkillUse(int questId, List<Integer> startNpcIds, List<Integer> endNpcIds, List<QuestSkillData> qsd) {
		super(questId);
		if (startNpcIds != null)
			this.startNpcIds.addAll(startNpcIds);
		if (endNpcIds != null)
			this.endNpcIds.addAll(endNpcIds);
		else
			this.endNpcIds.addAll(this.startNpcIds);
		this.qsd = qsd == null ? Collections.emptyList() : qsd;
	}

	@Override
	public void register() {
		for (Integer startNpcId : startNpcIds) {
			qe.registerQuestNpc(startNpcId).addOnQuestStart(questId);
			qe.registerQuestNpc(startNpcId).addOnTalkEvent(questId);
		}
		if (!endNpcIds.equals(startNpcIds)) {
			for (Integer endNpcId : endNpcIds)
				qe.registerQuestNpc(endNpcId).addOnTalkEvent(questId);
		}
		for (QuestSkillData questSkillData : qsd) {
			for (Integer skillId : questSkillData.getSkillIds())
				qe.registerQuestSkill(skillId, questId);
		}
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int dialogActionId = env.getDialogActionId();
		int targetId = env.getTargetId();

		if (qs == null || qs.isStartable()) {
			if (startNpcIds.isEmpty() || startNpcIds.contains(targetId)) {
				if (dialogActionId == QUEST_SELECT)
					return sendQuestDialog(env, 4762);
				else
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			if (!requirementsMet(qs))
				return false;
			int var = qs.getQuestVarById(0);
			if (endNpcIds.contains(targetId)) {
				if (dialogActionId == QUEST_SELECT) {
					return sendQuestDialog(env, 10002);
				} else if (dialogActionId == SELECT_QUEST_REWARD) {
					changeQuestStep(env, var, var, true); // reward
					return sendQuestDialog(env, 5);
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (endNpcIds.contains(targetId)) {
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public boolean onUseSkillEvent(QuestEnv env, int skillId) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs != null && qs.getStatus() == QuestStatus.START) {
			boolean success = false;
			for (QuestSkillData qd : qsd) {
				if (qd.getSkillIds().contains(skillId)) {
					int endVar = qd.getEndVar();
					int varId = qd.getVarNum();
					int total = 0;
					do {
						int currentVar = qs.getQuestVarById(varId);
						total += currentVar << ((varId - qd.getVarNum()) * 6);
						endVar >>= 6;
						varId++;
					} while (endVar > 0);
					total += 1;
					if (total <= qd.getEndVar()) {
						for (int varsUsed = qd.getVarNum(); varsUsed < varId; varsUsed++) {
							int value = total & 0x3F;
							total >>= 6;
							qs.setQuestVarById(varsUsed, value);
						}
						updateQuestStatus(env);
						success = true;
					}
				}
			}
			if (success && requirementsMet(qs)) {
				if (qs.getQuestVarById(0) == 0)
					qs.setQuestVarById(0, 1);
				qs.setStatus(QuestStatus.REWARD);
				updateQuestStatus(env);
			}
			return success;
		}
		return false;
	}

	/** Native counters span successive six-bit quest variables for counts above 63. */
	public static long currentCount(QuestState state, QuestSkillData requirement) {
		long total = 0;
		int remaining = requirement.getEndVar(), variable = requirement.getVarNum(), shift = 0;
		do {
			total |= (long) state.getQuestVarById(variable++) << shift;
			remaining >>>= 6;
			shift += 6;
		} while (remaining > 0);
		return total;
	}

	private boolean requirementsMet(QuestState state) {
		return !qsd.isEmpty() && qsd.stream().allMatch(q -> currentCount(state, q) >= q.getEndVar());
	}

	@Override
	public java.util.List<com.aionemu.gameserver.questEngine.model.QuestBotAction> getPlayerBotActions(Player player) {
		var state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START) return java.util.List.of();
		if (requirementsMet(state)) return endNpcIds.stream().map(com.aionemu.gameserver.questEngine.model.QuestBotAction::reward).toList();
		return qsd.stream().filter(q -> currentCount(state, q) < q.getEndVar())
			.flatMap(q -> q.getSkillIds().stream()).distinct()
			.map(id -> new com.aionemu.gameserver.questEngine.model.QuestBotAction(
				com.aionemu.gameserver.questEngine.model.QuestBotAction.Kind.SKILL, id, 0, 0, java.util.Map.of(),
				"Complete the remaining native skill-use counter with a legal cast or owned item"))
			.toList();
	}
}
