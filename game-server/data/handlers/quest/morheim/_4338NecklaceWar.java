package quest.morheim;

import static com.aionemu.gameserver.model.DialogAction.*;
import static com.aionemu.gameserver.model.items.ItemUseAnimation.*;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemUseAnimation;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Original Java Q2033 mechanics by Erin, vlog and Majka, adapted to the installed 4.8 Q4338 dialog and items. */
public class _4338NecklaceWar extends AbstractQuestHandler {

	private static final int URAKON = 204391;
	private static final int KELLAN = 790020;
	private static final int KIMSSI = 204393;
	private static final int NECKLACE = 182215326;
	private static final String LAVA = "DF2_ITEMUSEAREA_Q2033";

	public _4338NecklaceWar() {
		super(4338);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(URAKON).addOnQuestStart(questId);
		for (int npcId : new int[] { URAKON, KELLAN, KIMSSI, 204394, 204395, 204396, 204397, 204398 })
			qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
		qe.registerQuestItem(NECKLACE, questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.isStartable())
			return sendQuestNoneDialog(env, URAKON, SELECT_NONE);
		int targetId = env.getTargetId();
		int action = env.getDialogActionId();
		if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId != KELLAN)
				return false;
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 10002); // Native select_success page.
			return sendQuestEndDialog(env);
		}
		if (qs.getStatus() != QuestStatus.START)
			return false;
		int var = qs.getQuestVarById(0);
		if (targetId == KELLAN) {
			if (var == 0) {
				if (action == QUEST_SELECT)
					return sendQuestDialog(env, SELECT1);
				if (action == SELECT1_1)
					return sendQuestDialog(env, action);
				if (action == SETPRO1)
					return defaultCloseDialog(env, 0, 1);
			} else if (var == 1) {
				if (action == QUEST_SELECT)
					return sendQuestDialog(env, SELECT2);
				if (action == CHECK_USER_HAS_QUEST_ITEM)
					return checkQuestItems(env, 1, 2, false, 10000, 10001);
			} else if (var == 2) {
				if (action == QUEST_SELECT || action == SELECT3)
					return sendQuestDialog(env, SELECT3);
				if (action == SETPRO3)
					return defaultCloseDialog(env, 2, 3, NECKLACE, 1);
			}
		} else if (targetId == KIMSSI && var == 3) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, SELECT4);
			if (action == SELECT4_1)
				return sendQuestDialog(env, action);
			if (action == SETPRO4)
				return defaultCloseDialog(env, 3, 4);
		} else if (targetId >= 204394 && targetId <= 204398 && var == 4) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, SELECT5);
			if (action == SETPRO5)
				return defaultCloseDialog(env, 4, 5);
		}
		return false;
	}

	@Override
	public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
		if (item.getItemId() != NECKLACE)
			return HandlerResult.UNKNOWN;
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (!canDestroy(player, item, state) || player.getController().hasScheduledTask(TaskId.ITEM_USE))
			return HandlerResult.FAILED;
		NecklaceUse use = new NecklaceUse(player, item, state);
		try {
			player.getObserveController().addObserver(use.observer);
			player.getController().addTask(TaskId.ITEM_USE, use);
			int delay = item.getItemTemplate().getCastingDelay();
			PacketSendUtility.broadcastPacketAndReceive(player,
				new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), item.getObjectId(), NECKLACE, delay, USE_START));
			use.attach(ThreadPoolManager.getInstance().schedule(use, delay));
		} catch (RuntimeException | Error error) {
			player.getController().cancelTaskIfPresent(TaskId.ITEM_USE, use);
			use.cancel(false);
			throw error;
		}
		return HandlerResult.SUCCESS;
	}

	private boolean canDestroy(Player player, Item item, QuestState state) {
		boolean present = player.isPlayerBot() ? PlayerBotService.getInstance().allowsHeadlessInteraction(player)
			: player.isPlaying() && player.isOnline() && player.isSpawned() && !player.isDead();
		return present && state != null && player.getQuestStateList().getQuestState(questId) == state
			&& state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 5
			&& player.getWorldId() == 220020000 && player.isInsideItemUseZone(LAVA)
			&& player.getInventory().getItemByObjId(item.getObjectId()) == item && item.getItemCount() > 0;
	}

	/** Use native cancellation/task custody and revalidate after the actual item's one-second cast. */
	private final class NecklaceUse extends FutureTask<Void> {
		private final Player player;
		private final Item item;
		private final QuestState state;
		private final int world, instance;
		private final ItemUseObserver observer;
		private volatile Future<?> timer;
		private volatile boolean completing;
		private final AtomicBoolean cleaned = new AtomicBoolean();

		NecklaceUse(Player player, Item item, QuestState state) {
			super(() -> null);
			this.player = player;
			this.item = item;
			this.state = state;
			world = player.getWorldId();
			instance = player.getInstanceId();
			observer = new ItemUseObserver(player) {
				@Override
				protected void onAbort() {
					player.getController().cancelTaskIfPresent(TaskId.ITEM_USE, NecklaceUse.this);
					NecklaceUse.this.cancel(false);
				}
			};
		}

		void attach(Future<?> timer) {
			this.timer = timer;
			if (isDone())
				timer.cancel(false);
		}

		@Override
		public boolean cancel(boolean interrupt) {
			boolean cancelled = super.cancel(interrupt);
			if (cancelled && !completing)
				clean(USE_CANCEL);
			return cancelled;
		}

		private void clean(ItemUseAnimation animation) {
			if (!cleaned.compareAndSet(false, true))
				return;
			player.getObserveController().removeObserver(observer);
			if (timer != null)
				timer.cancel(false);
			PacketSendUtility.broadcastPacketAndReceive(player,
				new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), item.getObjectId(), NECKLACE, 0, animation));
		}

		@Override
		public void run() {
			if (isDone())
				return;
			boolean succeeded = false;
			try {
				completing = true;
				if (!player.getController().cancelTaskIfPresent(TaskId.ITEM_USE, this))
					return;
				player.getObserveController().removeObserver(observer);
				if (player.getWorldId() != world || player.getInstanceId() != instance || !canDestroy(player, item, state))
					return;
				QuestEnv captured = new QuestEnv(null, player, questId);
				if (!removeQuestItem(captured, NECKLACE, 1))
					return;
				changeQuestStep(captured, 5, 6, true);
				playQuestMovie(captured, 75);
				succeeded = true;
			} finally {
				cancel(false);
				completing = false;
				clean(succeeded ? USE_SUCCESS : USE_CANCEL);
			}
		}
	}
}
