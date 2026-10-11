package quest.ishalgen;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Mr. Poke, Majka
 */
public class _2003TreasureOfTheDeceased extends AbstractQuestHandler {

	public _2003TreasureOfTheDeceased() {
		super(2003);
	}

	@Override
	public java.util.List<QuestBotAction> getPlayerBotActions(Player player) {
		var qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null) return java.util.List.of();
		if (qs.getStatus() == QuestStatus.REWARD) return java.util.List.of(QuestBotAction.reward(203539));
		if (qs.getStatus() != QuestStatus.START) return java.util.List.of();
		return switch (qs.getQuestVarById(0)) {
			case 0 -> java.util.List.of(QuestBotAction.dialog(203539, SETPRO1, 1011));
			case 1 -> java.util.List.of(QuestBotAction.dialog(203539, CHECK_USER_HAS_QUEST_ITEM, 1352)
				.requiringCollection(com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA.getQuestById(questId).getCollectItems()));
			default -> java.util.List.of(QuestBotAction.event("Unexpected native Treasure of the Deceased stage; no transition declared"));
		};
	}

	@Override
	public void register() {
		qe.registerOnQuestCompleted(questId);
		qe.registerOnLevelChanged(questId);
		qe.registerQuestNpc(203539).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		final QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;

		int var = qs.getQuestVarById(0);
		int targetId = env.getTargetId();
		if (qs.getStatus() == QuestStatus.START) {
			if (targetId == 203539) {
				switch (env.getDialogActionId()) {
					case QUEST_SELECT:
						if (var == 0)
							return sendQuestDialog(env, 1011);
						else if (var == 1)
							return sendQuestDialog(env, 1352);
						return false;
					case SELECT1_1:
						playQuestMovie(env, 53);
						break;
					case SETPRO1:
						if (var == 0) {
							qs.setQuestVarById(0, var + 1);
							updateQuestStatus(env);
							PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
							return true;
						}
						return false;
					case CHECK_USER_HAS_QUEST_ITEM:
						if (var == 1) {
							if (QuestService.collectItemCheck(env, true)) {
								qs.setStatus(QuestStatus.REWARD);
								updateQuestStatus(env);
								return sendQuestDialog(env, 5);
							} else
								return sendQuestDialog(env, 1693);
						}
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203539)
				return sendQuestEndDialog(env);
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
