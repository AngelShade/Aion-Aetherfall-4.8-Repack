package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** The Unbroken Field: What the Water Carries. Native quest persistence; no core overrides. */
public class _14987WhatTheWaterCarries extends AbstractQuestHandler {

	public _14987WhatTheWaterCarries() {
		super(14987);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(203149).addOnQuestStart(questId);
		for (int npc : new int[] { 203148, 203149, 700113 })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		for (int npc : new int[] { 210121 })
			qe.registerQuestNpc(npc).addOnKillEvent(questId);
		qe.registerCanAct(questId, 700113);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		if (env.getPlayer().getWorldId() != 210030000 || !(env.getVisibleObject() instanceof Npc))
			return false;
		QuestState previous = env.getPlayer().getQuestStateList().getQuestState(14986);
		if (previous == null || previous.getStatus() != QuestStatus.COMPLETE)
			return false;
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, 203149);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != 203148)
				return false;
			boolean handled = sendQuestEndDialog(env);
			// Native completion may succeed before a follow-up dialogue declines.
			// Consume that confirmation instead of opening its action ID as HTML.
			if (!handled && state.getStatus() == QuestStatus.COMPLETE)
				return sendQuestSelectionDialog(env);
			return handled;
		}
		// A late/repeated confirmation must not reward again or fall through to page 23.
		if (state.getStatus() == QuestStatus.COMPLETE && npc == 203148
			&& action >= SELECTED_QUEST_REWARD1 && action <= SELECTED_QUEST_NOREWARD)
			return sendQuestSelectionDialog(env);
		if (state.getStatus() != QuestStatus.START)
			return false;
		int step = state.getQuestVarById(0);
		if (step == 1 && npc == 700113) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT3);
			if (action == SETPRO1)
				return defaultCloseDialog(env, 1, 2);
		}
		if (step == 2 && npc == 203148) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT5);
			if (action == SELECT_QUEST_REWARD) {
				changeQuestStep(env, step, step, true);
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (env.getPlayer().getWorldId() != 210030000 || state == null || state.getStatus() != QuestStatus.START)
			return false;
		int npc = env.getTargetId();
		if (state.getQuestVarById(0) == 0 && (npc == 210121)) {
			int count = state.getQuestVarById(1) + 1;
			if (count >= 4) {
				state.setQuestVarById(1, 0);
				return changeQuestStep(env, 0, 1);
			}
			state.setQuestVarById(1, count);
			updateQuestStatus(env);
			return true;
		}
		return false;
	}

	@Override
	public boolean onCanAct(QuestEnv env, QuestActionType type, Object... objects) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (type != QuestActionType.ACTION_ITEM_USE || env.getPlayer().getWorldId() != 210030000
			|| state == null || state.getStatus() != QuestStatus.START)
			return false;
		return (state.getQuestVarById(0) == 1 && env.getTargetId() == 700113);
	}

}
