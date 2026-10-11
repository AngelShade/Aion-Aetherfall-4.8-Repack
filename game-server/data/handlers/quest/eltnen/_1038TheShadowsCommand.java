package quest.eltnen;

import static com.aionemu.gameserver.model.DialogAction.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;
import com.aionemu.gameserver.services.QuestService;

/**
 * @author Rhys2002
 * @reworked vlog
 * @Modified Majka
 */
public class _1038TheShadowsCommand extends AbstractQuestHandler {

	private final static int questId = 1038;

	public _1038TheShadowsCommand() {
		super(questId);
	}

	@Override
	public java.util.List<QuestBotAction> getPlayerBotActions(Player player) {
		var qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null) return java.util.List.of();
		if (qs.getStatus() == QuestStatus.REWARD) return java.util.List.of(QuestBotAction.reward(203991));
		if (qs.getStatus() != QuestStatus.START) return java.util.List.of();
		return switch (qs.getQuestVarById(0)) {
			case 0 -> java.util.List.of(QuestBotAction.object(700162));
			case 1 -> java.util.List.of(QuestBotAction.dialog(203933, SETPRO2, 1352));
			case 2 -> java.util.List.of(player.getInventory().getItemCountByItemId(182201007) > 0
				? QuestBotAction.item(182201007) : QuestBotAction.object(700172));
			case 3 -> java.util.List.of(QuestBotAction.dialog(203933, SETPRO3, 1694).requiring(182201007, 1));
			case 4 -> java.util.List.of(QuestBotAction.dialog(203933, CHECK_USER_HAS_QUEST_ITEM, 2034)
				.requiringCollection(com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA.getQuestById(questId).getCollectItems()));
			case 5 -> java.util.List.of(QuestBotAction.dialog(203933, SETPRO4, 2035));
			case 6 -> java.util.List.of(QuestBotAction.dialog(203991, SETPRO5, 2375));
			case 7 -> java.util.List.of(QuestBotAction.hunt(204005));
			default -> java.util.List.of(QuestBotAction.event("Unexpected native Shadows Command stage; no transition declared"));
		};
	}

	@Override
	public void register() {
		int[] npcs = { 203933, 700172, 203991, 700162 };
		qe.registerOnLevelChanged(questId);
		qe.registerOnQuestCompleted(questId);
		qe.registerOnEnterWorld(questId);
		qe.registerOnQuestTimerEnd(questId);
		qe.registerQuestNpc(204005).addOnKillEvent(questId);
		qe.addHandlerSideQuestDrop(questId, 700172, 182201007, 1, 100, 2);
		qe.registerQuestItem(182201007, questId);
		qe.registerOnGetItem(182201007, questId);
		for (int npc : npcs) {
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		}
	}

	@Override
	public boolean onCanAct(QuestEnv env, QuestActionType action, Object... objects) {
		if (action != QuestActionType.ACTION_ITEM_USE)
			return false;
		QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;
		return switch (env.getTargetId()) {
			case 700162 -> qs.getQuestVarById(0) == 0;
			case 700172 -> qs.getQuestVarById(0) == 2
				&& env.getPlayer().getInventory().getItemCountByItemId(182201007) == 0;
			default -> false;
		};
	}

	@Override
	public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
		if (item.getItemId() == 182201007 && env.getPlayer().getInventory().getItemByObjId(item.getObjectId()) == item)
			onGetItemEvent(env); // Recover a diary already looted before the acquisition listener was restored.
		return HandlerResult.UNKNOWN; // The native ReadAction must still open the diary.
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		if (defaultOnKillEvent(env, 204005, 7, true)) { // reward
			QuestService.questTimerEnd(env);
			return true;
		}
		return false;
	}

	@Override
	public boolean onDialogEvent(final QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;
		int var = qs.getQuestVarById(0);
		int targetId = env.getTargetId();
		int dialog = env.getDialogActionId();

		if (qs.getStatus() == QuestStatus.START) {
			switch (targetId) {
				case 700162: { // Underground Temple Artifact
					if (dialog == USE_OBJECT) {
						return useQuestObject(env, 0, 1, false, 0, 34); // 1 + movie
					}
					break;
				}
				case 203933: { // Actaeon
					switch (dialog) {
						case QUEST_SELECT: {
							if (var == 1) {
								return sendQuestDialog(env, 1352);
							}
							else if (var == 3) {
								return sendQuestDialog(env, 1694);
							}
							else if (var == 4) {
								return sendQuestDialog(env, 2034);
							}
							else if (var == 5) {
								return sendQuestDialog(env, 2035);
							}
						}
						case CHECK_USER_HAS_QUEST_ITEM: {
							return checkQuestItems(env, 4, 5, false, 2035, 2120); // 5
						}
						case SETPRO2: {
							return defaultCloseDialog(env, 1, 2); // 2
						}
						case SETPRO3: {
							removeQuestItem(env, 182201015, 1);
							removeQuestItem(env, 182201016, 1);
							removeQuestItem(env, 182201017, 1);
							return defaultCloseDialog(env, 3, 4, 0, 0, 182201007, 1); // 4
						}
						case SETPRO4: {
							return defaultCloseDialog(env, 5, 6); // 6
						}
						case FINISH_DIALOG: {
							return sendQuestSelectionDialog(env);
						}
					}
					break;
				}
				case 700172: { // Philipemos's Corpse
					if (var == 2) {
						if (dialog == USE_OBJECT) {
							return true; // loot
						}
					}
					break;
				}
				case 203991: { // Dionera
					switch (dialog) {
						case QUEST_SELECT: {
							if (var == 6) {
								return sendQuestDialog(env, 2375);
							}
						}
						case SETPRO5: {
							playQuestMovie(env, 35);
							return defaultCloseDialog(env, 6, 7); // 7
						}
					}
				}
			}
		}
		else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203991) { // Dionera
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public boolean onGetItemEvent(QuestEnv env) {
		return env.getPlayer().getInventory().getItemCountByItemId(182201007) >= 1
			&& defaultOnGetItemEvent(env, 2, 3, false); // 3
	}

	@Override
	public boolean onQuestTimerEndEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs != null && qs.getStatus() == QuestStatus.START) {
			int var = qs.getQuestVarById(0);
			if (var == 7) {
				changeQuestStep(env, 7, 6, false); // 6
				return true;
			}
		}
		return false;
	}

	@Override
	public void onMovieEndEvent(QuestEnv env, int movieId) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (movieId == 35 && player.getWorldId() == 210020000 && qs != null
			&& qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 7) {
			QuestService.questTimerStart(env, 180);
			spawn(204005, player, 1768.16f, 924.47f, 422.02f, (byte) 0);
			return;
		}
		return;
	}

	@Override
	public void onLevelChangedEvent(Player player) {
		defaultOnLevelChangedEvent(player, 1300); // Sets as zone mission to avoid it appears on new player list.
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		defaultOnQuestCompletedEvent(env, 1300); // Sets as zone mission to avoid it appears on new player list.
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		onLevelChangedEvent(env.getPlayer());
		onGetItemEvent(env); // Existing saved step-2 holders retain their diary and can continue.
		return false;
	}
}
