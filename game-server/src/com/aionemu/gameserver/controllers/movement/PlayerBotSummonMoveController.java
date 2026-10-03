package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.taskmanager.tasks.PlayerMoveTaskManager;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native summon movement, interpolated at its actual movement speed. */
public final class PlayerBotSummonMoveController extends SummonMoveController {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerBotSummonMoveController.class);
	private volatile boolean failed;
	public PlayerBotSummonMoveController(Summon owner) { super(owner); }
	public boolean hasFailed() { return failed; }
	@Override public synchronized void startMovingToDestination() {
		if (failed || !owner.canPerformMove() || owner.isCasting() || owner.isBeingReleased()) return;
		updateLastMove();
		if (started.compareAndSet(false, true)) owner.getController().onStartMove();
		setAndSendStartMove(owner);
		PlayerMoveTaskManager.getInstance().addPlayer(owner);
	}
	@Override public synchronized void moveToDestination() {
		try { moveStep(); }
		catch (RuntimeException e) {
			failed = true; started.set(false);
			PlayerMoveTaskManager.getInstance().removePlayer(owner);
			log.error("Companion summon {} movement failed; it will be released", owner.getObjectId(), e);
			try { super.abortMove(); } catch (RuntimeException stopError) { e.addSuppressed(stopError); }
		}
	}
	private void moveStep() {
		if (failed) return;
		if (!owner.isSpawned() || owner.isDead() || !owner.canPerformMove() || owner.isBeingReleased()) { abortMove(); return; }
		super.moveToDestination();
		owner.getKnownList().update();
		owner.getController().onMove();
		if (PositionUtil.getDistance(owner, targetDestX, targetDestY, targetDestZ) < 0.3) abortMove();
	}
	@Override public synchronized void abortMove() {
		boolean moving = isInMove();
		super.abortMove();
		if (moving) owner.getController().onStopMove();
	}
}
