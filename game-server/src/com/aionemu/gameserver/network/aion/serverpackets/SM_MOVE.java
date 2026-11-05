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

	private final byte movementMask;
	private final MovementSnapshot snapshot;

	/** Queued packets retain one emission state for every creature, including all optional wire fields. */
	private record MovementSnapshot(int objectId, float x, float y, float z, byte heading,
		float moveX, float moveY, float moveZ, boolean playable, byte glideFlag, int windBoxId,
		int vehicleId, int vehiclePart, float vehicleX, float vehicleY, float vehicleZ) {
		static MovementSnapshot capture(Creature creature, CreatureMoveController<?> mc, byte mask) {
			PlayableMoveController<?> pmc = mc instanceof PlayableMoveController ? (PlayableMoveController<?>) mc : null;
			boolean relative = pmc != null && (mask & MovementMask.ABSOLUTE) == 0;
			return new MovementSnapshot(creature.getObjectId(), creature.getX(), creature.getY(), creature.getZ(), creature.getHeading(),
				relative ? pmc.vectorX : mc.getTargetX2(), relative ? pmc.vectorY : mc.getTargetY2(), relative ? pmc.vectorZ : mc.getTargetZ2(),
				pmc != null, pmc == null ? 0 : pmc.glideFlag, pmc == null ? 0 : pmc.windBoxId,
				pmc == null ? 0 : pmc.vehicleId, pmc == null ? 0 : pmc.vehiclePartId,
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
		CreatureMoveController<?> mc = creature.getMoveController();
		if (creature instanceof Player player && player.isPlayerBot()) {
			synchronized (mc) {
				this.movementMask = currentMask ? mc.getMovementMask() : movementMask;
				snapshot = MovementSnapshot.capture(creature, mc, this.movementMask);
			}
		} else {
			this.movementMask = currentMask ? mc.getMovementMask() : movementMask;
			snapshot = MovementSnapshot.capture(creature, mc, this.movementMask);
		}
	}

	@Override
	protected void writeImpl(AionConnection client) {
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
			writeC(snapshot.glideFlag() | snapshot.windBoxId() >> 8);
			if ((snapshot.glideFlag() & GlideFlag.WIND_BOX) != 0)
				writeC(snapshot.windBoxId());
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
