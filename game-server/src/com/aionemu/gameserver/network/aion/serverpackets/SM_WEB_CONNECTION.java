package com.aionemu.gameserver.network.aion.serverpackets;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;
import com.aionemu.gameserver.services.WebUiConnection;

/** Metadata on the existing web-session opcode. The original status-zero-only
 * consumer ignores 0xAF; its normal SM_SECURITY_TOKEN follows byte-for-byte. */
public final class SM_WEB_CONNECTION extends AionServerPacket {
	private final byte[] fingerprint;
	private final WebUiConnection.Description description;
	public SM_WEB_CONNECTION(byte[] nativeToken, WebUiConnection.Description description) {
		super(SM_SECURITY_TOKEN.class);
		this.fingerprint = Arrays.copyOf(nativeToken, 16);
		this.description = description;
	}
	@Override protected void writeImpl(AionConnection connection) {
		byte[] origin = description.origin().getBytes(StandardCharsets.UTF_8), message = description.message().getBytes(StandardCharsets.UTF_8);
		if (origin.length > 512 || message.length > 512) throw new IllegalStateException("Window metadata exceeds protocol bounds");
		writeC(0xAF); writeB(new byte[] {'A', 'F', 'W', '1'}); writeC(1);
		writeH(origin.length); writeH(message.length); writeH(Math.max(0, Math.min(200, description.textScale())));
		writeC(description.journey() ? 1 : 0); writeB(fingerprint); writeB(origin); writeB(message);
	}
}
