package quest.theobomos;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Aetherfall continuation of The Red Journal, using native persisted quest progress. */
public class _3999TheUnfinishedEntry extends AbstractQuestHandler {

	public _3999TheUnfinishedEntry() {
		super(3999);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(798193).addOnQuestStart(questId); // Nagrunerk
		qe.registerQuestNpc(798193).addOnTalkEvent(questId);
		qe.registerQuestNpc(798190).addOnTalkEvent(questId); // Rosina
		qe.registerQuestNpc(798192).addOnTalkEvent(questId); // Bodyguard
		qe.registerQuestNpc(214577).addOnKillEvent(questId); // Titirunerk
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		QuestState previous = env.getPlayer().getQuestStateList().getQuestState(3060);
		if (previous == null || previous.getStatus() != QuestStatus.COMPLETE)
			return false;
		QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
		int target = env.getTargetId();
		int action = env.getDialogActionId();
		if (qs == null || qs.isStartable())
			return sendQuestNoneDialog(env, 798193);
		if (qs.getStatus() == QuestStatus.REWARD)
			return target == 798190 && sendQuestEndDialog(env);
		if (qs.getStatus() != QuestStatus.START)
			return false;

		int step = qs.getQuestVarById(0);
		if (target == 798190 && step == 0) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 1352);
			if (action == SETPRO1)
				return defaultCloseDialog(env, 0, 1);
		} else if (target == 798192 && step == 1) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 1693);
			if (action == SETPRO2)
				return defaultCloseDialog(env, 1, 2);
		} else if (target == 798193 && step == 3) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 2034);
			if (action == SETPRO3)
				return defaultCloseDialog(env, 3, 4);
		} else if (target == 798190 && step == 4) {
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 2375);
			if (action == SELECT_QUEST_REWARD) {
				changeQuestStep(env, 4, 4, true);
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		// Only the active investigation records recovery of the stolen notes.
		// Quest 3059 keeps its independent safe retrieval and reward.
		return defaultOnKillEvent(env, 214577, 2, 3);
	}
}
