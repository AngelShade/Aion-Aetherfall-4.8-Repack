package quest.rentus_base;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * @author zhkchi
 */
public class _30500Desperation extends AbstractQuestHandler {

	public _30500Desperation() {
		super(30500);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(205842).addOnQuestStart(questId); // Ancanus
		qe.registerQuestNpc(205842).addOnTalkEvent(questId);
		qe.registerQuestNpc(804876).addOnQuestStart(questId); // Monodia, who the client marks as this quest's giver
		qe.registerQuestNpc(804876).addOnTalkEvent(questId);
		qe.registerQuestNpc(804879).addOnTalkEvent(questId); // Garnon
		qe.registerQuestNpc(799592).addOnTalkEvent(questId); // Ariana at the Rentus Base entrance
		qe.registerQuestNpc(799670).addOnTalkEvent(questId); // Ariana after the final battle
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int dialogActionId = env.getDialogActionId();
		int targetId = env.getTargetId();

		if (qs == null || qs.isStartable()) {
			if (targetId == 205842 || targetId == 804876) {
				switch (dialogActionId) {
					case QUEST_SELECT:
						return sendQuestDialog(env, 1011);
					default:
						return sendQuestStartDialog(env);
				}
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			if (targetId == 804879) {
				switch (dialogActionId) {
					case QUEST_SELECT:
						return sendQuestDialog(env, 1352);
					case SETPRO1:
						qs.setQuestVarById(0, 1);
						qs.setStatus(QuestStatus.REWARD);
						updateQuestStatus(env);
						return closeDialogWindow(env);
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 799592 || targetId == 799670) {
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}
}
