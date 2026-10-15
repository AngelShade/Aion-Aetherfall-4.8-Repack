package com.aionemu.gameserver.network.aion.serverpackets;

import com.aionemu.gameserver.controllers.movement.CreatureMoveController;
import com.aionemu.gameserver.controllers.movement.GlideFlag;
import com.aionemu.gameserver.controllers.movement.MovementMask;
import com.aionemu.gameserver.controllers.movement.PlayableMoveController;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

/**
 * This packet is displaying movement of players etc.
 *
 * @author -Nemesiss-
 */
public class SM_MOVE extends AionServerPacket {

	/**
	 * Object that is moving.
	 */
	private final Creature creature;
	private final byte movementMask;
	private final MovementSnapshot snapshot;

	/** Queued bot packets retain one emission state, including all optional wire fields. */
	private record MovementSnapshot(int objectId, float x, float y, float z, byte heading,
		float moveX, float moveY, float moveZ, boolean playable, byte glideFlag, int geyserLocationId,
		int vehicleId, int vehiclePart, float vehicleX, float vehicleY, float vehicleZ) {
		static MovementSnapshot capture(Creature creature, CreatureMoveController<?> mc, byte mask) {
			PlayableMoveController<?> pmc = mc instanceof PlayableMoveController ? (PlayableMoveController<?>) mc : null;
			boolean relative = pmc != null && (mask & MovementMask.ABSOLUTE) == 0;
			return new MovementSnapshot(creature.getObjectId(), creature.getX(), creature.getY(), creature.getZ(), creature.getHeading(),
				relative ? pmc.vectorX : mc.getTargetX2(), relative ? pmc.vectorY : mc.getTargetY2(), relative ? pmc.vectorZ : mc.getTargetZ2(),
				pmc != null, pmc == null ? 0 : pmc.glideFlag, pmc == null ? 0 : pmc.geyserLocationId,
				pmc == null ? 0 : pmc.unk1, pmc == null ? 0 : pmc.unk2,
				pmc == null ? 0 : pmc.vehicleX, pmc == null ? 0 : pmc.vehicleY, pmc == null ? 0 : pmc.vehicleZ);
		}
	}

	public SM_MOVE(Creature creature) {
		this(creature, (byte) 0, true);
	}

	public SM_MOVE(Creature creature, byte movementMask) {
		this(creature, movementMask, false);
	}

	private SM_MOVE(Creature creature, byte movementMask, boolean currentMask) {
		this.creature = creature;
		CreatureMoveController<?> mc = creature.getMoveController();
		if (creature instanceof Player player && player.isPlayerBot()) {
			synchronized (mc) {
				this.movementMask = currentMask ? mc.getMovementMask() : movementMask;
				snapshot = MovementSnapshot.capture(creature, mc, this.movementMask);
			}
		} else {
			this.movementMask = currentMask ? mc.getMovementMask() : movementMask;
			snapshot = null;
		}
	}

	@Override
	protected void writeImpl(AionConnection client) {
		if (snapshot != null) {
			writeSnapshot();
			return;
		}
		CreatureMoveController<?> mc = creature.getMoveController();
		PlayableMoveController<?> pmc = mc instanceof PlayableMoveController ? (PlayableMoveController<?>) mc : null;
		writeD(creature.getObjectId());
		writeF(creature.getX());
		writeF(creature.getY());
		writeF(creature.getZ());
		writeC(creature.getHeading());

		writeC(movementMask);

		if ((movementMask & MovementMask.POSITION) == MovementMask.POSITION && (movementMask & MovementMask.MANUAL) == MovementMask.MANUAL) {
			if (pmc != null && (movementMask & MovementMask.ABSOLUTE) == 0) {
				writeF(pmc.vectorX);
				writeF(pmc.vectorY);
				writeF(pmc.vectorZ);
			} else {
				writeF(mc.getTargetX2());
				writeF(mc.getTargetY2());
				writeF(mc.getTargetZ2());
			}
		}
		if ((movementMask & MovementMask.GLIDE) == MovementMask.GLIDE) {
			byte glideFlag = pmc == null ? 0 : pmc.glideFlag;
			writeC(glideFlag);
			if (glideFlag == GlideFlag.GEYSER)
				writeC(pmc.geyserLocationId);
		}
		if (pmc != null && (movementMask & MovementMask.VEHICLE) == MovementMask.VEHICLE) {
			writeD(pmc.unk1);
			writeD(pmc.unk2);
			// Native S_MOVE_NEW keeps the vehicle tuple separate from movement
			// vectors, exactly as C_MOVE_NEW supplies it. Replacing these with
			// ordinary velocity vectors corrupts the moving-object coordinate frame.
			writeF(pmc.vehicleX);
			writeF(pmc.vehicleY);
			writeF(pmc.vehicleZ);
		}
	}

	private void writeSnapshot() {
		writeD(snapshot.objectId());
		writeF(snapshot.x());
		writeF(snapshot.y());
		writeF(snapshot.z());
		writeC(snapshot.heading());
		writeC(movementMask);
		if ((movementMask & MovementMask.POSITION) == MovementMask.POSITION && (movementMask & MovementMask.MANUAL) == MovementMask.MANUAL) {
			writeF(snapshot.moveX());
			writeF(snapshot.moveY());
			writeF(snapshot.moveZ());
		}
		if ((movementMask & MovementMask.GLIDE) == MovementMask.GLIDE) {
			writeC(snapshot.glideFlag());
			if (snapshot.glideFlag() == GlideFlag.GEYSER)
				writeC(snapshot.geyserLocationId());
		}
		if (snapshot.playable() && (movementMask & MovementMask.VEHICLE) == MovementMask.VEHICLE) {
			writeD(snapshot.vehicleId());
			writeD(snapshot.vehiclePart());
			writeF(snapshot.vehicleX());
			writeF(snapshot.vehicleY());
			writeF(snapshot.vehicleZ());
		}
	}
}
