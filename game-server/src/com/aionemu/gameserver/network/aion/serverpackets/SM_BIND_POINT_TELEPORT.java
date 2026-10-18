package com.aionemu.gameserver.network.aion.serverpackets;

import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

/**
 * @author ginho1
 */
public class SM_BIND_POINT_TELEPORT extends AionServerPacket {

	int action, playerId, locId, cooldown;
	private final int castTimeMillis;

	public SM_BIND_POINT_TELEPORT(int action, int playerId, int locId, int cooldown) {
		this(action, playerId, locId, cooldown, 0);
	}

	public SM_BIND_POINT_TELEPORT(int action, int playerId, int locId, int cooldown, int castTimeMillis) {
		this.action = action;
		this.playerId = playerId;
		this.locId = locId;
		this.cooldown = cooldown;
		this.castTimeMillis = castTimeMillis;
	}

	@Override
	protected void writeImpl(AionConnection con) {
		writeC(action);
		writeD(playerId);
		switch (action) {
			case 1:
				// The native 4.8 action-1 handler ignores this second D field.
				// Negative values carry milliseconds for Aetherfall's native bar;
				// positive legacy location IDs keep their original wire meaning.
				// Packet size and action 2/3 cooldown/cancel layouts stay unchanged.
				writeD(castTimeMillis > 0 ? -castTimeMillis : locId);
				break;
			case 3:
				writeD(locId);
				writeD(cooldown);
				break;
		}

	}
}
