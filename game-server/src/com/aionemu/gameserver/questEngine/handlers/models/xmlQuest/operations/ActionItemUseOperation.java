package com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_USE_OBJECT;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * @author Mr. Poke
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ActionItemUseOperation", propOrder = { "finish" })
public class ActionItemUseOperation extends QuestOperation {

	private static final int USE_TIME = 3000;
	private static final ConcurrentHashMap<Player, NativeUse> CURRENT = new ConcurrentHashMap<>();

	@XmlElement(required = true)
	protected QuestOperations finish;

	/** Native completion retains custody after atomically claiming its controller task. */
	public static boolean isInProgress(Player player) {
		return CURRENT.containsKey(player);
	}

	@Override
	public void doOperate(final QuestEnv env) {
		if (!(env.getVisibleObject() instanceof Npc npc) || finish == null)
			return;
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(env.getQuestId());
		if (state == null || state.getStatus() != QuestStatus.START || !interactionPresent(player) || !local(player, npc))
			return;
		NativeUse use = new NativeUse(player, npc, state, env);
		if (CURRENT.putIfAbsent(player, use) != null)
			return; // A repeated dialog cannot duplicate this committed native operation.
		try {
			player.getObserveController().addObserver(use.observer);
			// The quest-use AI's first three-second task is finishing now. The XML
			// operation owns a distinct second three-second phase, with the same
			// native task identity and movement/death interruption rules.
			player.getController().addTask(TaskId.ACTION_ITEM_NPC, use);
			PacketSendUtility.sendPacket(player, new SM_USE_OBJECT(player.getObjectId(), npc.getObjectId(), USE_TIME, 1));
			PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, EmotionType.START_QUESTLOOT, 0, npc.getObjectId()), true);
			// Keep the existing outer Runnable helper in normal compiler output.
			// Its callback delegates to the tracked commitment rather than owning
			// an unguarded item/progress operation of its own.
			use.attach(schedule(new Runnable() {
				@Override public void run() {
					use.run();
				}
			}, USE_TIME));
		} catch (RuntimeException | Error error) {
			player.getController().cancelTaskIfPresent(TaskId.ACTION_ITEM_NPC, use);
			use.cancel(false);
			throw error;
		}
	}

	/** Scheduling seam for native callback fixtures; production uses its normal timer. */
	protected Future<?> schedule(Runnable completion, int delay) {
		return ThreadPoolManager.getInstance().schedule(completion, delay);
	}

	/** A registered headless actor has native presence without a client socket. */
	protected boolean interactionPresent(Player player) {
		return player.isPlayerBot() ? PlayerBotService.getInstance().allowsHeadlessInteraction(player)
			: player.isPlaying() && player.isOnline() && player.isSpawned() && !player.isDead();
	}

	private static boolean local(Player player, Npc npc) {
		return npc.isSpawned() && !npc.isDead() && player.getWorldId() == npc.getWorldId()
			&& player.getInstanceId() == npc.getInstanceId() && player.getKnownList().sees(npc)
			&& PositionUtil.isInTalkRange(player, npc) && GeoService.getInstance().canSee(player, npc)
			&& DialogService.isInteractionAllowed(player, npc);
	}

	/** Every field used at completion is captured; QuestEngine may reuse/mutate its env. */
	private final class NativeUse extends FutureTask<Void> {
		private final Player player;
		private final Npc npc;
		private final QuestState state;
		private final int quest, variables, world, instance, action, extended;
		private final boolean continuation;
		private final ItemUseObserver observer;
		private final QuestOperations operations;
		private Future<?> timer;
		private final AtomicBoolean cleaned = new AtomicBoolean();
		private final AtomicBoolean running = new AtomicBoolean();
		private volatile boolean completing;

		NativeUse(Player player, Npc npc, QuestState state, QuestEnv env) {
			super(() -> null);
			this.player = player;
			this.npc = npc;
			this.state = state;
			operations = finish;
			quest = env.getQuestId();
			variables = state.getQuestVars().getQuestVars();
			world = player.getWorldId();
			instance = player.getInstanceId();
			action = env.getDialogActionId();
			extended = env.getExtendedRewardIndex();
			continuation = env.isDialogContinuationFromPreQuest();
			observer = new ItemUseObserver(player) {
				@Override protected void onAbort() {
					player.getController().cancelTaskIfPresent(TaskId.ACTION_ITEM_NPC, NativeUse.this);
					NativeUse.this.cancel(false);
				}
			};
		}

		synchronized void attach(Future<?> timer) {
			this.timer = timer;
			if (isDone())
				timer.cancel(false);
		}

		@Override public void run() {
			if (!running.compareAndSet(false, true) || isDone())
				return;
			try {
				if (CURRENT.get(player) != this || player.getWorldId() != world || player.getInstanceId() != instance
					|| player.getQuestStateList().getQuestState(quest) != state || state.getStatus() != QuestStatus.START
					|| state.getQuestVars().getQuestVars() != variables || !interactionPresent(player) || !local(player, npc)
					|| isCancelled())
					return;
				// Claim only this exact controller task. Cancellation/replacement must
				// never remove its successor or authorize a late item grant.
				completing = true;
				if (!player.getController().cancelTaskIfPresent(TaskId.ACTION_ITEM_NPC, this))
					return;
				QuestEnv captured = new QuestEnv(npc, player, quest, action);
				captured.setExtendedRewardIndex(extended);
				captured.setDialogContinuationFromPreQuest(continuation);
				operations.operate(captured);
			} finally {
				player.getController().cancelTaskIfPresent(TaskId.ACTION_ITEM_NPC, this);
				cancel(false);
				completing = false;
				clean();
			}
		}

		@Override protected void done() {
			if (!completing) clean();
		}

		private void clean() {
			if (!cleaned.compareAndSet(false, true)) return;
			CURRENT.remove(player, this);
			player.getObserveController().removeObserver(observer);
			synchronized (this) {
				if (timer != null)
					timer.cancel(false);
			}
			PacketSendUtility.sendPacket(player, new SM_USE_OBJECT(player.getObjectId(), npc.getObjectId(), USE_TIME, 0));
			PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, EmotionType.END_QUESTLOOT, 0, npc.getObjectId()), true);
		}
	}

}
