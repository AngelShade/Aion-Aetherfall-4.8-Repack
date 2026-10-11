package quest.ishalgen;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;

/**
 * @author vlog, Majka
 */
public class _2001ThinkingAhead extends AbstractQuestHandler {

	private int[] mobs = { 210369, 210368 };

	public _2001ThinkingAhead() {
		super(2001);
	}

	@Override
	public java.util.List<QuestBotAction> getPlayerBotActions(Player player) {
		var qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null) return java.util.List.of();
		if (qs.getStatus() == QuestStatus.REWARD) return java.util.List.of(QuestBotAction.reward(203518));
		if (qs.getStatus() != QuestStatus.START) return java.util.List.of();
		int stage = qs.getQuestVarById(0);
		if (stage >= 3 && stage <= 8) return QuestBotAction.hunts(mobs);
		return switch (stage) {
			case 0 -> java.util.List.of(QuestBotAction.dialog(203518, SETPRO1, 1011));
			case 1 -> java.util.List.of(QuestBotAction.object(700093), QuestBotAction.dialog(203518, CHECK_USER_HAS_QUEST_ITEM, 1352)
				.requiringCollection(com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA.getQuestById(questId).getCollectItems()));
			case 2 -> java.util.List.of(QuestBotAction.dialog(203518, SETPRO3, 1694));
			default -> java.util.List.of(QuestBotAction.event("Unexpected native Thinking Ahead stage; no transition declared"));
		};
	}

	@Override
	public void register() {
		qe.registerOnQuestCompleted(questId);
		qe.registerOnLevelChanged(questId);
		qe.registerQuestNpc(203518).addOnTalkEvent(questId);
		qe.registerQuestNpc(700093).addOnTalkEvent(questId);
		for (int mob : mobs) {
			qe.registerQuestNpc(mob).addOnKillEvent(questId);
		}
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;
		int var = qs.getQuestVarById(0);
		int targetId = env.getTargetId();
		int dialogActionId = env.getDialogActionId();

		if (qs.getStatus() == QuestStatus.START) {
			if (targetId == 203518) { // Boromer
				switch (dialogActionId) {
					case QUEST_SELECT:
						if (var == 0) {
							return sendQuestDialog(env, 1011);
						} else if (var == 1) {
							return sendQuestDialog(env, 1352);
						} else if (var == 2) {
							return sendQuestDialog(env, 1694);
						}
						break;
					case SELECT1_1:
						playQuestMovie(env, 51);
						return sendQuestDialog(env, 1012);
					case SETPRO1:
						return defaultCloseDialog(env, 0, 1); // 1
					case SETPRO3:
						return defaultCloseDialog(env, 2, 3); // 3
					case CHECK_USER_HAS_QUEST_ITEM:
						return checkQuestItems(env, 1, 2, false, 1694, 1693);
					case FINISH_DIALOG:
						return sendQuestSelectionDialog(env);
				}
			} else if (targetId == 700093) {
				return true; // just give quest drop on use
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203518) { // Boromer
				if (dialogActionId == USE_OBJECT) {
					return sendQuestDialog(env, 2034);
				} else {
					return sendQuestEndDialog(env);
				}
			}
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;
		int var = qs.getQuestVarById(0);
		if (var >= 3 && var < 8) {
			return defaultOnKillEvent(env, mobs, 3, 8); // 3 - 8
		} else if (var == 8) {
			return defaultOnKillEvent(env, mobs, 8, true); // reward
		}
		return false;
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		defaultOnQuestCompletedEvent(env, 2100);
	}

	@Override
	public void onLevelChangedEvent(Player player) {
		defaultOnLevelChangedEvent(player, 2100);
	}
}
