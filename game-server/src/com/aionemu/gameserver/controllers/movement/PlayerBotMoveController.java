package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.taskmanager.tasks.PlayerMoveTaskManager;
import com.aionemu.gameserver.utils.PositionUtil;

/** Server-driven movement; keeps the normal player's speed, effects and movement packets. */
public final class PlayerBotMoveController extends PlayerMoveController {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerBotMoveController.class);
	private volatile boolean failed;
	public PlayerBotMoveController(Player owner) { super(owner); }
	public boolean hasFailed() { return failed; }

	@Override
	public synchronized void startMovingToDestination() {
		if (failed || !owner.canPerformMove() || owner.isCasting()) return;
		if (started.compareAndSet(false, true)) {
			updateLastMove();
			owner.getController().onStartMove();
			setInMove(true);
			com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
			setAndSendStartMove(owner);
			PlayerMoveTaskManager.getInstance().addPlayer(owner);
		} else if (com.aionemu.gameserver.services.playerbot.PlayerBotFormation.sendUpdate(owner)) {
			com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
			setAndSendStartMove(owner);
		}
	}

	@Override
	public synchronized void moveToDestination() {
		try { moveStep(); }
		catch (RuntimeException e) {
			// A headless player's failure must not abort the shared human movement task.
			failed = true; started.set(false);
			PlayerMoveTaskManager.getInstance().removePlayer(owner);
			log.error("Companion {} movement failed; its session will dismiss and save", owner.getObjectId(), e);
			try { super.abortMove(); } catch (RuntimeException stopError) { e.addSuppressed(stopError); }
		}
	}

	private void moveStep() {
		if (failed) return;
		if (!owner.isSpawned() || owner.isDead() || !owner.canPerformMove()) {
			abortMove();
			return;
		}
		long now=System.currentTimeMillis(),elapsed=Math.max(0,Math.min(1000,now-lastMoveUpdate));
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
		lastMoveUpdate=now-elapsed;
		super.moveToDestination();
		owner.getKnownList().update();
		owner.getController().onMove();
		if (PositionUtil.getDistance(owner, targetDestX, targetDestY, targetDestZ) < 0.3)
			abortMove();
	}

	@Override
	public synchronized void abortMove() {
		boolean wasMoving = isInMove();
		if (wasMoving || started.get()) super.abortMove();
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.close(owner);
		if (wasMoving) owner.getController().onStopMove();
	}
}
