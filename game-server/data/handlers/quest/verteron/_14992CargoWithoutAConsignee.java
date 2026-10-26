package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/** The Unbroken Field: Cargo Without a Consignee. Native quest persistence; no core overrides. */
public class _14992CargoWithoutAConsignee extends AbstractQuestHandler {

	public _14992CargoWithoutAConsignee() {
		super(14992);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(203147).addOnQuestStart(questId);
		for (int npc : new int[] { 203146, 203147, 203166, 203172, 700283 })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		qe.registerOnGetItem(182299984, questId);
		qe.registerOnEnterWorld(questId);
		qe.registerCanAct(questId, 700283);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		if (env.getPlayer().getWorldId() != 210030000 || !(env.getVisibleObject() instanceof Npc))
			return false;
		QuestState previous = env.getPlayer().getQuestStateList().getQuestState(14991);
		if (previous == null || previous.getStatus() != QuestStatus.COMPLETE)
			return false;
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, 203147);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != 203172)
				return false;
			boolean handled = sendQuestEndDialog(env);
			// Native completion may succeed before a follow-up dialogue declines.
			// Consume that confirmation instead of opening its action ID as HTML.
			if (!handled && state.getStatus() == QuestStatus.COMPLETE)
				return sendQuestSelectionDialog(env);
			return handled;
		}
		// A late/repeated confirmation must not reward again or fall through to page 23.
		if (state.getStatus() == QuestStatus.COMPLETE && npc == 203172
			&& action >= SELECTED_QUEST_REWARD1 && action <= SELECTED_QUEST_NOREWARD)
			return sendQuestSelectionDialog(env);
		if (state.getStatus() != QuestStatus.START)
			return false;
		int step = state.getQuestVarById(0);
		if (step == 0 && npc == 203146) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT2);
			if (action == SETPRO1)
				return defaultCloseDialog(env, 0, 1);
		}
		if (step == 1 && npc == 203166) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT3);
			if (action == SETPRO1)
				return defaultCloseDialog(env, 1, 2);
		}
		if (step == 2 && npc == 700283)
			return action == USE_OBJECT;

		if (step == 3 && npc == 203172 && !QuestService.collectItemCheck(env, false)) {
			changeQuestStep(env, 3, 2);
			return sendQuestDialog(env, SELECT6);
		}
		if (step == 3 && npc == 203172) {
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
	public boolean onCanAct(QuestEnv env, QuestActionType type, Object... objects) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (type != QuestActionType.ACTION_ITEM_USE || env.getPlayer().getWorldId() != 210030000
			|| state == null || state.getStatus() != QuestStatus.START)
			return false;
		return (state.getQuestVarById(0) == 2 && env.getTargetId() == 700283);
	}

}
