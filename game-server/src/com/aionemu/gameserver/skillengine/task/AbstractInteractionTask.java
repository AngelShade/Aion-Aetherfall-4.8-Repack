package com.aionemu.gameserver.skillengine.task;

import java.util.concurrent.Future;

import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.RecallService;
import com.aionemu.gameserver.services.RecallService.CancelReason;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author ATracer
 */
public abstract class AbstractInteractionTask {

	private Future<?> task;
	protected int interval = 2500;
	protected int delay = 1000;

	protected final Player requester;
	protected final VisibleObject responder;
	private final int worldId, instanceId;

	/**
	 * @param requester
	 * @param responder
	 */
	public AbstractInteractionTask(Player requester, VisibleObject responder) {
		this.requester = requester;
		worldId = requester.isPlayerBot() ? requester.getWorldId() : 0;
		instanceId = requester.isPlayerBot() ? requester.getInstanceId() : 0;
		if (responder == null)
			this.responder = requester;
		else
			this.responder = responder;
	}

	/**
	 * Called on each interaction
	 * 
	 * @return
	 */
	protected abstract boolean onInteraction();

	/**
	 * Called when interaction is complete
	 */
	protected abstract void onInteractionFinish();

	/**
	 * Called before interaction is started
	 */
	protected abstract void onInteractionStart();

	/**
	 * Called when interaction is not complete and need to be aborted
	 */
	protected abstract void onInteractionAbort();

	/**
	 * Interaction scheduling method
	 */
	public void start() {
		AbstractInteractionTask oldTask = requester.getInteractionTask();
		if (oldTask != null)
			oldTask.abort();
		requester.setInteractionTask(this);
		RecallService.getInstance().cancel(requester, CancelReason.CANCELLED);
		onInteractionStart();

		task = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				runInteraction();
			}

		}, delay, interval);
	}

	/** Preserve the native progression task; headless actors have no client socket. */
	protected boolean interactionPresent() {
		return requester.isPlayerBot() ? PlayerBotService.getInstance().allowsHeadlessInteraction(requester) : requester.isOnline();
	}

	protected final void runInteraction() {
		if (requester.isPlayerBot() && (requester.getInteractionTask() != this
			|| requester.getWorldId() != worldId || requester.getInstanceId() != instanceId || !interactionPresent())) {
			abort();
			return;
		}
		if (!interactionPresent() || onInteraction())
			stop();
	}

	/**
	 * Stop current interaction
	 */
	public void stop() {
		if (requester.getInteractionTask() == this)
			requester.setInteractionTask(null);
		onInteractionFinish();

		if (task != null && !task.isCancelled()) {
			task.cancel(false);
			task = null;
		}
	}

	/**
	 * Abort current interaction
	 */
	public void abort() {
		onInteractionAbort();
		stop();
	}

	/**
	 * @return true or false
	 */
	public boolean isInProgress() {
		return task != null && !task.isCancelled();
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}
}
