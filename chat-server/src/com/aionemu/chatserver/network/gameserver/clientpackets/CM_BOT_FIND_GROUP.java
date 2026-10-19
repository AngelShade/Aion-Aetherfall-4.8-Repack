package com.aionemu.chatserver.network.gameserver.clientpackets;

import java.nio.ByteBuffer;

import com.aionemu.chatserver.network.gameserver.GsClientPacket;
import com.aionemu.chatserver.network.gameserver.GsConnection;
import com.aionemu.chatserver.service.BroadcastService;
import com.aionemu.chatserver.service.GameServerService;

/** Accepted only on the authenticated GameServer connection, never from a chat client. */
public final class CM_BOT_FIND_GROUP extends GsClientPacket {

	private int playerId, raceId;
	private String name, message;

	public CM_BOT_FIND_GROUP(ByteBuffer buf, GsConnection connection) {
		super(buf, connection, 0x04);
	}

	@Override
	protected void readImpl() {
		playerId = readD();
		raceId = readD();
		name = readS();
		message = readS();
	}

	@Override
	protected void runImpl() {
		BroadcastService.getInstance().broadcastBotFindGroup(GameServerService.GAMESERVER_ID & 0xff, playerId, raceId, name, message);
	}
}
