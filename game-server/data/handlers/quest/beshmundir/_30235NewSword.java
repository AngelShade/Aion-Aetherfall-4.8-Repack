package quest.beshmundir;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/**
 * @author vlog
 */
public class _30235NewSword extends AbstractQuestHandler {

	public _30235NewSword() {
		super(30235);
	}

	@Override
	public java.util.List<com.aionemu.gameserver.questEngine.model.QuestBotAction> getPlayerBotActions(Player player) {
		return com.aionemu.gameserver.questEngine.model.QuestBotActionFamilies.weaponUpgrade(player, questId, 799032, 100000939, 182209633, 286904, 281419, 215795);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		return com.aionemu.gameserver.questEngine.model.QuestBotActionFamilies.ownsUpgradeToken(env.getPlayer(), questId, 182209633)
			&& onGetItemEvent(env);
	}

	@Override
	public void register() {
		qe.registerOnEnterWorld(questId);
		int[] debilkarims = { 286904, 281419, 215795 };
		qe.registerQuestNpc(799032).addOnQuestStart(questId);
		qe.registerQuestNpc(799032).addOnTalkEvent(questId);
		qe.registerOnGetItem(182209633, questId);
		for (int debilkarim : debilkarims) {
			qe.registerQuestNpc(debilkarim).addOnKillEvent(questId);
		}
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int dialogActionId = env.getDialogActionId();
		int targetId = env.getTargetId();

		if (qs == null || qs.isStartable()) {
			if (targetId == 799032) { // Gefeios
				if (player.getInventory().getItemCountByItemId(100000939) >= 1) { // Noble Siel's Supreme Sword
					if (dialogActionId == QUEST_SELECT) {
						return sendQuestDialog(env, 4762);
					} else {
						return sendQuestStartDialog(env);
					}
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 799032) { // Gefeios
				if (dialogActionId == USE_OBJECT) {
					if (player.getInventory().getItemCountByItemId(182209633) > 0) {
						return sendQuestDialog(env, 10002);
					}
				} else if (player.getInventory().getItemCountByItemId(182209633) > 0) {
					// Successful native finishQuest removes this work token; opening a dialog must preserve it.
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
		int targetId = env.getTargetId();
		if (qs != null && qs.getStatus() == QuestStatus.START) {
			switch (targetId) {
				case 286904:
				case 281419:
				case 215795:
					if (QuestService.collectItemCheck(env, true)) {
						return giveQuestItem(env, 182209633, 1);
					}
			}
		}
		return false;
	}

	@Override
	public boolean onGetItemEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs != null && qs.getStatus() == QuestStatus.START) {
			changeQuestStep(env, 0, 0, true); // reward
			return true;
		}
		return false;
	}
}
