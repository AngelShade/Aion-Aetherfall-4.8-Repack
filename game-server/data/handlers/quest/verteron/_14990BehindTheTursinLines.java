package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/** The Unbroken Field: Behind the Tursin Lines. Native quest persistence; no core overrides. */
public class _14990BehindTheTursinLines extends AbstractQuestHandler {

	public _14990BehindTheTursinLines() {
		super(14990);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(203178).addOnQuestStart(questId);
		for (int npc : new int[] { 203147, 203178, 700115 })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		for (int npc : new int[] { 210183, 210184 })
			qe.registerQuestNpc(npc).addOnKillEvent(questId);
		qe.registerOnGetItem(182299983, questId);
		qe.registerOnEnterWorld(questId);
		qe.registerCanAct(questId, 700115);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		if (env.getPlayer().getWorldId() != 210030000 || !(env.getVisibleObject() instanceof Npc))
			return false;
		QuestState previous = env.getPlayer().getQuestStateList().getQuestState(14989);
		if (previous == null || previous.getStatus() != QuestStatus.COMPLETE)
			return false;
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, 203178);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != 203147)
				return false;
			boolean handled = sendQuestEndDialog(env);
			// Native completion may succeed before a follow-up dialogue declines.
			// Consume that confirmation instead of opening its action ID as HTML.
			if (!handled && state.getStatus() == QuestStatus.COMPLETE)
				return sendQuestSelectionDialog(env);
			return handled;
		}
		// A late/repeated confirmation must not reward again or fall through to page 23.
		if (state.getStatus() == QuestStatus.COMPLETE && npc == 203147
			&& action >= SELECTED_QUEST_REWARD1 && action <= SELECTED_QUEST_NOREWARD)
			return sendQuestSelectionDialog(env);
		if (state.getStatus() != QuestStatus.START)
			return false;
		int step = state.getQuestVarById(0);
		if (step == 0 && npc == 203147) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT2);
			if (action == SETPRO1)
				return defaultCloseDialog(env, 0, 1);
		}
		if (step == 2 && npc == 700115)
			return action == USE_OBJECT;

		if (step == 3 && npc == 203147 && !QuestService.collectItemCheck(env, false)) {
			changeQuestStep(env, 3, 2);
			return sendQuestDialog(env, SELECT6);
		}
		if (step == 3 && npc == 203147) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT5);
			if (action == SELECT_QUEST_REWARD) {
				if (!QuestService.collectItemCheck(env, true))
					return false;
				changeQuestStep(env, step, step, true);
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public boolean onGetItemEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 2)
			return false;
		return QuestService.collectItemCheck(env, false) && changeQuestStep(env, 2, 3);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START)
			return false;
		boolean collected = QuestService.collectItemCheck(env, false);
		int step = state.getQuestVarById(0);
		if (step == 2 && collected)
			return changeQuestStep(env, 2, 3);
		if (step == 3 && !collected)
			return changeQuestStep(env, 3, 2);
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (env.getPlayer().getWorldId() != 210030000 || state == null || state.getStatus() != QuestStatus.START)
			return false;
		int npc = env.getTargetId();
		if (state.getQuestVarById(0) == 1 && (npc == 210183 || npc == 210184)) {
			int count = state.getQuestVarById(1) + 1;
			if (count >= 4) {
				state.setQuestVarById(1, 0);
				return changeQuestStep(env, 1, 2);
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
		return (state.getQuestVarById(0) == 2 && env.getTargetId() == 700115);
	}

}
