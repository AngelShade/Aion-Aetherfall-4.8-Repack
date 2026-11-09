package com.aionemu.gameserver.network.aion.serverpackets;

import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

/**
 * A client window command on the existing web-session opcode, next to {@link SM_WEB_CONNECTION} (AFW1 version 1).
 * The Aetherfall native bridge consumes every 0xAF body before the original token reader sees it; version 2 carries a
 * command byte and a value byte. Padded to the 29-byte minimum the bridge inspects.
 */
public final class SM_WEB_COMMAND extends AionServerPacket {

	/** Opens the Central Market window, as the HUD shortcut does. */
	public static final int OPEN_CENTRAL_MARKET = 1;
	/** Whether the native Broker is enabled (value 1) or retired for the Central Market (value 0); the client's Broker Swap module follows it. */
	public static final int BROKER_STATE = 2;

	private final int command, value;

	public SM_WEB_COMMAND(int command, int value) {
		super(SM_SECURITY_TOKEN.class);
		if (command < 1 || command > 255 || value < 0 || value > 255)
			throw new IllegalArgumentException("Window command out of range");
		this.command = command;
		this.value = value;
	}

	@Override
	protected void writeImpl(AionConnection connection) {
		writeC(0xAF);
		writeB(new byte[] { 'A', 'F', 'W', '1' });
		writeC(2);
		writeC(command);
		writeC(value);
		writeB(new byte[21]);
	}
}
