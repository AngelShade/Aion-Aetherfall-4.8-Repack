package quest.poeta;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;

/**
 * @author MrPoke, Majka
 */
public class _1005BarringtheGate extends AbstractQuestHandler {

	public _1005BarringtheGate() {
		super(1005);
	}

	@Override
	public java.util.List<QuestBotAction> getPlayerBotActions(Player player) {
		var qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null) return java.util.List.of();
		if (qs.getStatus() == QuestStatus.REWARD) return java.util.List.of(QuestBotAction.reward(203067));
		if (qs.getStatus() != QuestStatus.START) return java.util.List.of();
		return switch (qs.getQuestVarById(0)) {
			case 0 -> java.util.List.of(QuestBotAction.dialog(203067, SETPRO1, 1011));
			case 1 -> java.util.List.of(QuestBotAction.dialog(203081, SETPRO2, 1352));
			case 2 -> java.util.List.of(QuestBotAction.dialog(790001, SETPRO3, 1693));
			case 3 -> java.util.List.of(QuestBotAction.dialog(203085, SETPRO4, 2034));
			case 4 -> java.util.List.of(QuestBotAction.dialog(203086, SETPRO5, 2375));
			case 5 -> java.util.List.of(QuestBotAction.object(700081));
			case 6 -> java.util.List.of(QuestBotAction.object(700082));
			case 7 -> java.util.List.of(QuestBotAction.object(700083));
			case 8 -> java.util.List.of(QuestBotAction.object(700080));
			default -> java.util.List.of(QuestBotAction.event("Unexpected native Barring the Gate stage; no transition declared"));
		};
	}

	@Override
	public void register() {
		int[] talkNpcs = { 203067, 203081, 790001, 203085, 203086, 700080, 700081, 700082, 700083 };
		qe.registerOnQuestCompleted(questId);
		qe.registerOnLevelChanged(questId);
		for (int id : talkNpcs)
			qe.registerQuestNpc(id).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(final QuestEnv env) {
		final Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;

		int var = qs.getQuestVarById(0);
		int targetId = 0;
		if (env.getVisibleObject() instanceof Npc)
			targetId = ((Npc) env.getVisibleObject()).getNpcId();

		if (qs.getStatus() == QuestStatus.START) {
			if (targetId == 203067) {
				switch (env.getDialogActionId()) {
					case QUEST_SELECT:
						if (var == 0)
							return sendQuestDialog(env, 1011);
						return false;
					case SETPRO1:
						if (var == 0) {
							qs.setQuestVarById(0, var + 1);
							updateQuestStatus(env);
							sendQuestSelectionDialog(env);
							return true;
						}
				}
			} else if (targetId == 203081) {
				switch (env.getDialogActionId()) {
					case QUEST_SELECT:
						if (var == 1)
							return sendQuestDialog(env, 1352);
						return false;
					case SETPRO2:
						if (var == 1) {
							qs.setQuestVarById(0, var + 1);
							updateQuestStatus(env);
							sendQuestSelectionDialog(env);
							return true;
						}
				}
			} else if (targetId == 790001) {
				switch (env.getDialogActionId()) {
					case QUEST_SELECT:
						if (var == 2)
							return sendQuestDialog(env, 1693);
						return false;
					case SETPRO3:
						if (var == 2) {
							qs.setQuestVarById(0, var + 1);
							updateQuestStatus(env);
							sendQuestSelectionDialog(env);
							return true;
						}
				}
			} else if (targetId == 203085) {
				switch (env.getDialogActionId()) {
					case QUEST_SELECT:
						if (var == 3)
							return sendQuestDialog(env, 2034);
						return false;
					case SETPRO4:
						if (var == 3) {
							qs.setQuestVarById(0, var + 1);
							updateQuestStatus(env);
							sendQuestSelectionDialog(env);
							return true;
						}
				}
			} else if (targetId == 203086) {
				switch (env.getDialogActionId()) {
					case QUEST_SELECT:
						if (var == 4)
							return sendQuestDialog(env, 2375);
						return false;
					case SETPRO5:
						if (var == 4) {
							qs.setQuestVarById(0, var + 1);
							updateQuestStatus(env);
							sendQuestSelectionDialog(env);
							return true;
						}
				}
			} else if (targetId == 700081) {
				if (var == 5) {
					destroy(6, env);
					return false;
				}
			} else if (targetId == 700082) {
				if (var == 6) {
					destroy(7, env);
					return false;
				}
			} else if (targetId == 700083) {
				if (var == 7) {
					destroy(8, env);
					return false;
				}
			} else if (targetId == 700080) {
				if (var == 8) {
					destroy(-1, env);
					return false;
				}
			}

		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203067) {
				if (env.getDialogActionId() == USE_OBJECT) {
					playQuestMovie(env, 171);
					return sendQuestDialog(env, 2716);
				} else
					return sendQuestEndDialog(env);

			}
		}
		return false;
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		int[] quests = { 1100, 1004, 1003, 1002, 1001 };
		defaultOnQuestCompletedEvent(env, quests);
	}

	@Override
	public void onLevelChangedEvent(Player player) {
		int[] quests = { 1100, 1004, 1003, 1002, 1001 };
		defaultOnLevelChangedEvent(player, quests);
	}

	private void destroy(final int var, final QuestEnv env) {
		final Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (var != -1)
			qs.setQuestVarById(0, var);
		else {
			playQuestMovie(env, 21);
			qs.setStatus(QuestStatus.REWARD);
		}
		updateQuestStatus(env);
	}

}
