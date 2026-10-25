package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/** The Unbroken Field: An Honest Seal. Native quest persistence; no core overrides. */
public class _14983AnHonestSeal extends AbstractQuestHandler {

	public _14983AnHonestSeal() {
		super(14983);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(203126).addOnQuestStart(questId);
		for (int npc : new int[] { 203126, 203128, 700117 })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		qe.registerOnGetItem(182299980, questId);
		qe.registerOnEnterWorld(questId);
		qe.registerCanAct(questId, 700117);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		if (env.getPlayer().getWorldId() != 210030000 || !(env.getVisibleObject() instanceof Npc))
			return false;
		QuestState previous = env.getPlayer().getQuestStateList().getQuestState(14982);
		if (previous == null || previous.getStatus() != QuestStatus.COMPLETE)
			return false;
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, 203126);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != 203128)
				return false;
			boolean handled = sendQuestEndDialog(env);
			// Native completion may succeed before a follow-up dialogue declines.
			// Consume that confirmation instead of opening its action ID as HTML.
			if (!handled && state.getStatus() == QuestStatus.COMPLETE)
				return sendQuestSelectionDialog(env);
			return handled;
		}
		// A late/repeated confirmation must not reward again or fall through to page 23.
		if (state.getStatus() == QuestStatus.COMPLETE && npc == 203128
			&& action >= SELECTED_QUEST_REWARD1 && action <= SELECTED_QUEST_NOREWARD)
			return sendQuestSelectionDialog(env);
		if (state.getStatus() != QuestStatus.START)
			return false;
		int step = state.getQuestVarById(0);
		if (step == 0 && npc == 700117)
			return action == USE_OBJECT;

		if (step == 1 && npc == 203128 && !QuestService.collectItemCheck(env, false)) {
			changeQuestStep(env, 1, 0);
			return sendQuestDialog(env, SELECT6);
		}
		if (step == 1 && npc == 203128) {
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
		if (state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 0)
			return false;
		return QuestService.collectItemCheck(env, false) && changeQuestStep(env, 0, 1);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START)
			return false;
		boolean collected = QuestService.collectItemCheck(env, false);
		int step = state.getQuestVarById(0);
		if (step == 0 && collected)
			return changeQuestStep(env, 0, 1);
		if (step == 1 && !collected)
			return changeQuestStep(env, 1, 0);
		return false;
	}

	@Override
	public boolean onCanAct(QuestEnv env, QuestActionType type, Object... objects) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (type != QuestActionType.ACTION_ITEM_USE || env.getPlayer().getWorldId() != 210030000
			|| state == null || state.getStatus() != QuestStatus.START)
			return false;
		return (state.getQuestVarById(0) == 0 && env.getTargetId() == 700117);
	}

}
