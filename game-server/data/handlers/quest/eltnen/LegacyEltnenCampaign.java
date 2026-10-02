package quest.eltnen;

import static com.aionemu.gameserver.model.items.ItemUseAnimation.*;

import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Shared casting behavior for the restored campaigns. */
public abstract class LegacyEltnenCampaign extends AbstractQuestHandler {

	protected LegacyEltnenCampaign(int questId) {
		super(questId);
	}

	protected boolean useCampaignItem(QuestEnv env, Item item, String zone, int step, int nextStep, boolean consume,
		int giveItemId) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != step
			|| zone != null && !player.isInsideItemUseZone(zone) || player.getInventory().getItemByObjId(item.getObjectId()) != item)
			return false;
		class Cast extends ItemUseObserver {
			private boolean finished;
			Cast() { super(player); }
			@Override
			protected synchronized void onAbort() {
				if (!finished) {
					finished = true;
					animation(USE_CANCEL);
				}
			}
			private void animation(com.aionemu.gameserver.model.items.ItemUseAnimation animation) {
				PacketSendUtility.broadcastPacketAndReceive(player,
					new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), item.getObjectId(), item.getItemId(), 0, animation));
			}
			synchronized void complete() {
				if (finished)
					return;
				finished = true;
				player.getObserveController().removeObserver(this);
				if (!player.isOnline() || qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != step
					|| zone != null && !player.isInsideItemUseZone(zone) || player.getInventory().getItemByObjId(item.getObjectId()) != item) {
					animation(USE_CANCEL);
					return;
				}
				if (giveItemId != 0 && !giveQuestItem(env, giveItemId, 1)) {
					animation(USE_CANCEL);
					return;
				}
				if (consume && !removeQuestItem(env, item.getItemId(), 1)) {
					if (giveItemId != 0)
						removeQuestItem(env, giveItemId, 1);
					animation(USE_CANCEL);
					return;
				}
				changeQuestStep(env, step, nextStep);
				animation(USE_SUCCESS);
			}
		}
		Cast cast = new Cast();
		player.getObserveController().addObserver(cast);
		PacketSendUtility.broadcastPacketAndReceive(player,
			new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), item.getObjectId(), item.getItemId(), 3000, USE_START));
		ThreadPoolManager.getInstance().schedule(cast::complete, 3000);
		return true;
	}
}
