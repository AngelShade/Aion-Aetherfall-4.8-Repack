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
		// Removal from the shared scheduler does not revoke its current snapshot.
		// A queued step after stop/cast must not emit native movement callbacks:
		// PlayerController.onStopMove and skill movement observers cancel casts.
		if (failed || !started.get() || !isInMove() || owner.isCasting()) return;
		if (!owner.isSpawned() || owner.isDead() || !owner.canPerformMove()) {
			abortMove();
			return;
		}
		long now=System.currentTimeMillis(),elapsed=Math.max(0,Math.min(1000,now-lastMoveUpdate));
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.refresh(owner);
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
		lastMoveUpdate=now-elapsed;
		if (owner.isFlying()) super.moveToDestination();
		else {
			// Native player interpolation is a straight XYZ line supplied by a client.
			// Headless bots must follow the floor and recheck dynamic obstacles at every movement tick.
			double distance = Math.hypot(targetDestX-owner.getX(), targetDestY-owner.getY());
			if (distance < 0.01) {
				var ground = com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
					owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),owner.getX(),owner.getY(),targetDestZ);
				if (ground != null && Float.isFinite(ground.z) && ground.z < owner.getZ() - 1.25f) {
					com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,ground.x,ground.y,ground.z,heading,false);
					owner.getKnownList().update();
					owner.getController().onMove();
				}
				if (!com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner)) abortMove();
				else updateLastMove();
				return;
			}
			float speed = com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(owner,
				com.aionemu.gameserver.model.stats.container.StatEnum.SPEED, owner.getGameStats().getMovementSpeedFloat());
			double fraction = Math.min(1, speed*elapsed/1000.0/distance);
			if (fraction <= 0) { updateLastMove(); return; }
			float x = owner.getX()+(float)((targetDestX-owner.getX())*fraction);
			float y = owner.getY()+(float)((targetDestY-owner.getY())*fraction);
			var ground = com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
				owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),x,y,targetDestZ);
			if (ground == null || !Float.isFinite(ground.x) || !Float.isFinite(ground.y) || !Float.isFinite(ground.z)
				|| Math.hypot(ground.x-owner.getX(),ground.y-owner.getY()) < 0.001) { abortMove(); return; }
			com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,ground.x,ground.y,ground.z,heading,false);
			updateLastMove();
		}
		owner.getKnownList().update();
		owner.getController().onMove();
		if (PositionUtil.getDistance(owner, targetDestX, targetDestY, targetDestZ) < 0.3
			&& !com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner))
			abortMove();
	}

	@Override
	public synchronized void abortMove() {
		if (com.aionemu.gameserver.services.playerbot.PlayerBotRecall.recalling(owner)) failed = false;
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.clear(owner);
		boolean wasMoving = isInMove();
		if (wasMoving || started.get()) super.abortMove();
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.close(owner);
		if (wasMoving) owner.getController().onStopMove();
	}
}
