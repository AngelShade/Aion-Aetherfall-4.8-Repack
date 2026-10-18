package quest.theobomos;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.List;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.model.templates.rewards.BonusType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.item.ItemService;

/**
 * @author Wakizashi, vlog
 */
public class _3074DangerousProbability extends AbstractQuestHandler {

	public _3074DangerousProbability() {
		super(3074);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(798193).addOnQuestStart(questId);
		qe.registerQuestNpc(798193).addOnTalkEvent(questId);
		qe.registerOnBonusApply(questId, BonusType.REDEEM);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		int targetId = env.getTargetId();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int dialogActionId = env.getDialogActionId();

		if (qs == null || qs.isStartable()) {
			if (targetId == 798193) { // Nagrunerk
				if (dialogActionId == EXCHANGE_COIN) {
					if (QuestService.startQuest(env)) {
						return sendQuestDialog(env, 1011);
					} else {
						return sendQuestSelectionDialog(env);
					}
				}
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			if (targetId == 798193) { // Nagrunerk
				long kinahAmount = player.getInventory().getKinah();
				long angelsEye = player.getInventory().getItemCountByItemId(186000037);
				switch (dialogActionId) {
					case EXCHANGE_COIN:
						return sendQuestDialog(env, 1011);
					case SELECT1:
						if (kinahAmount >= 1000 && angelsEye >= 1) {
							changeQuestStep(env, 0, 0, true);
							qs.setRewardGroup(0);
							return sendQuestDialog(env, 5);
						} else {
							return sendQuestDialog(env, 1009);
						}
					case SELECT2:
						if (kinahAmount >= 5000 && angelsEye >= 1) {
							changeQuestStep(env, 0, 0, true);
							qs.setRewardGroup(1);
							return sendQuestDialog(env, 6);
						} else {
							return sendQuestDialog(env, 1009);
						}
					case SELECT3:
						if (kinahAmount >= 25000 && angelsEye >= 1) {
							changeQuestStep(env, 0, 0, true);
							qs.setRewardGroup(2);
							return sendQuestDialog(env, 7);
						} else {
							return sendQuestDialog(env, 1009);
						}
					case FINISH_DIALOG:
						return sendQuestSelectionDialog(env);
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 798193) { // Nagrunerk
				if (dialogActionId == SELECTED_QUEST_NOREWARD) {
					Integer group = qs.getRewardGroup();
					long cost = group == null ? 0 : switch (group) { case 0 -> 1000; case 1 -> 5000; case 2 -> 25000; default -> 0; };
					if (cost == 0 || player.getInventory().getItemCountByItemId(186000037) < 1
						|| !player.getInventory().tryDecreaseKinah(cost)) return sendQuestDialog(env, 1009);
					if (!player.getInventory().decreaseByItemId(186000037, 1)) {
						player.getInventory().increaseKinah(cost);
						return sendQuestDialog(env, 1009);
					}
					if (!QuestService.finishQuest(env)) {
						player.getInventory().increaseKinah(cost);
						ItemService.addItem(player, 186000037, 1);
					}
					return closeDialogWindow(env);
				} else {
					QuestService.abandonQuest(player, questId);
				}
			}
		}
		return false;
	}

	@Override
	public HandlerResult onBonusApplyEvent(QuestEnv env, BonusType type, List<QuestItems> rewards) {
		if (type != BonusType.REDEEM || env.getQuestId() != questId) return HandlerResult.UNKNOWN;
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state != null && state.getStatus() == QuestStatus.REWARD && state.getRewardGroup() != null) {
			// The client's 50a/50b/50c exchange groups are scripted rewards, not
			// the general random-item pool. Retain the existing exchange amounts.
			int count = switch (state.getRewardGroup()) { case 0 -> 1; case 1 -> Rnd.get(1, 3); case 2 -> Rnd.get(1, 6); default -> 0; };
			if (count > 0) rewards.add(new QuestItems(186000005, count));
		}
		// QuestService's bonus hook uses FAILED to skip the generic item pool;
		// this handler has already supplied the exchange reward in its item list.
		return HandlerResult.FAILED;
	}
}
