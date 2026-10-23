package quest.beluslan;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Installed 4.8 dialog: Bestla grants the native booster, Horu installs it, Nerita rewards. */
public class _24150InteriorLandscaping extends AbstractQuestHandler {
	private static final int NERITA = 204702, BESTLA = 204733, HORU = 204734, BOOSTER = 182215460;

	public _24150InteriorLandscaping() {
		super(24150);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(NERITA).addOnQuestStart(questId);
		for (int npc : new int[] { NERITA, BESTLA, HORU })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, NERITA);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD)
			return npc == NERITA && sendQuestEndDialog(env);
		if (state.getStatus() != QuestStatus.START)
			return false;
		int stage = state.getQuestVarById(0);
		if (stage == 0 && npc == BESTLA) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, 1352);
			if (action == SELECT2_1)
				return sendQuestDialog(env, action);
			if (action == SETPRO1)
				return defaultCloseDialog(env, 0, 1, BOOSTER, 1);
		} else if (stage == 1 && npc == HORU) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, 1693);
			if (action == SELECT3_1)
				return sendQuestDialog(env, action);
			if (action == SETPRO2 && removeQuestItem(env, BOOSTER, 1))
				return defaultCloseDialog(env, 1, 2);
		} else if (stage == 2 && npc == NERITA) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, 2375);
			if (action == SELECT_QUEST_REWARD)
				return defaultCloseDialog(env, 2, 2, true, true);
		}
		return false;
	}
}
