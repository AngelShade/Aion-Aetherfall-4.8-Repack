package quest.beluslan;

import static com.aionemu.gameserver.model.items.ItemUseAnimation.*;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemUseAnimation;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Guarded native item casts for this restoration; no changes to shared quest helpers. */
public abstract class BeluslanQuestItem extends AbstractQuestHandler {

	protected BeluslanQuestItem(int questId) {
		super(questId);
	}

	protected boolean useBeluslanItem(QuestEnv env, Item item, String zone, Predicate<QuestState> stage, int movie,
		Consumer<QuestEnv> finish) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (!canUse(player, item, state, zone, stage) || player.getController().hasScheduledTask(TaskId.ITEM_USE))
			return false;
		Use cast = new Use(player, item, state, zone, stage, movie, finish);
		try {
			player.getObserveController().addObserver(cast.observer);
			player.getController().addTask(TaskId.ITEM_USE, cast);
			int delay = item.getItemTemplate().getCastingDelay();
			PacketSendUtility.broadcastPacketAndReceive(player,
				new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), item.getObjectId(), item.getItemId(), delay, USE_START));
			cast.attach(ThreadPoolManager.getInstance().schedule(cast, delay));
		} catch (RuntimeException | Error error) {
			player.getController().cancelTaskIfPresent(TaskId.ITEM_USE, cast);
			cast.cancel(false);
			throw error;
		}
		return true;
	}

	private boolean canUse(Player player, Item item, QuestState state, String zone, Predicate<QuestState> stage) {
		boolean present = player.isPlayerBot() ? PlayerBotService.getInstance().allowsHeadlessInteraction(player)
			: player.isPlaying() && player.isOnline() && player.isSpawned() && !player.isDead();
		return present && state != null && player.getQuestStateList().getQuestState(questId) == state
			&& state.getStatus() == QuestStatus.START && stage.test(state) && player.getWorldId() == 220040000
			&& player.isInsideItemUseZone(zone) && player.getInventory().getItemByObjId(item.getObjectId()) == item
			&& item.getItemCount() > 0;
	}

	private final class Use extends FutureTask<Void> {
		private final Player player;
		private final Item item;
		private final QuestState state;
		private final String zone;
		private final Predicate<QuestState> stage;
		private final Consumer<QuestEnv> finish;
		private final int world, instance, variables, movie;
		private final ItemUseObserver observer;
		private final AtomicBoolean cleaned = new AtomicBoolean();
		private volatile Future<?> timer;
		private volatile boolean completing;

		Use(Player player, Item item, QuestState state, String zone, Predicate<QuestState> stage, int movie, Consumer<QuestEnv> finish) {
			super(() -> null);
			this.player = player;
			this.item = item;
			this.state = state;
			this.zone = zone;
			this.stage = stage;
			this.movie = movie;
			this.finish = finish;
			world = player.getWorldId();
			instance = player.getInstanceId();
			variables = state.getQuestVars().getQuestVars();
			observer = new ItemUseObserver(player) {
				@Override
				protected void onAbort() {
					player.getController().cancelTaskIfPresent(TaskId.ITEM_USE, Use.this);
					Use.this.cancel(false);
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
				new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), item.getObjectId(), item.getItemId(), 0, animation));
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
				if (player.getWorldId() != world || player.getInstanceId() != instance || state.getQuestVars().getQuestVars() != variables
					|| !canUse(player, item, state, zone, stage))
					return;
				QuestEnv captured = new QuestEnv(null, player, questId);
				if (!removeQuestItem(captured, item.getItemId(), 1))
					return;
				finish.accept(captured);
				if (movie != 0)
					playQuestMovie(captured, movie);
				succeeded = true;
			} finally {
				cancel(false);
				completing = false;
				clean(succeeded ? USE_SUCCESS : USE_CANCEL);
			}
		}
	}
}
