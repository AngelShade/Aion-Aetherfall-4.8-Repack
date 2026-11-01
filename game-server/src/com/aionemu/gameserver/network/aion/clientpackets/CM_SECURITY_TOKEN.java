package com.aionemu.gameserver.network.aion.clientpackets;

import java.util.Set;

import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;

/**
 * @author ginho1
 */
public class CM_SECURITY_TOKEN extends AionClientPacket {

	public CM_SECURITY_TOKEN(int opcode, Set<State> validStates) {
		super(opcode, validStates);
	}

	@Override
	protected void readImpl() {

	}

	@Override
	protected void runImpl() {
		com.aionemu.gameserver.services.player.WebSessionService.send(getConnection());
	}
}
