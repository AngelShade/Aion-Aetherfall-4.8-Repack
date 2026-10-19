package com.aionemu.gameserver.network.chatserver.serverpackets;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.chatserver.ChatServerConnection;
import com.aionemu.gameserver.network.chatserver.CsServerPacket;

/** GameServer-authorized resident bot's native LFG announcement. */
public final class SM_CS_BOT_FIND_GROUP extends CsServerPacket {

	private final int playerId, raceId;
	private final String name, message;

	public SM_CS_BOT_FIND_GROUP(Player bot, String message) {
		super(0x04);
		playerId = bot.getObjectId();
		raceId = bot.getRace().getRaceId();
		name = bot.getName();
		this.message = message;
	}

	@Override
	protected void writeImpl(ChatServerConnection con) {
		writeD(playerId);
		writeD(raceId);
		writeS(name);
		writeS(message);
	}
}
