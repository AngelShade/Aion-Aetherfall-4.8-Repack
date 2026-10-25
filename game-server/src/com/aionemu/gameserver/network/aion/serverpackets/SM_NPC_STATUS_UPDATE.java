package com.aionemu.gameserver.network.aion.serverpackets;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

/** Native S_CHANGE_NPC_STATUS: update an existing actor without replacing it. */
public final class SM_NPC_STATUS_UPDATE extends AionServerPacket {

	private final int objectId, currentHp, maxHp;
	private final byte level;

	public SM_NPC_STATUS_UPDATE(Npc npc) {
		objectId = npc.getObjectId();
		level = npc.getLevel();
		currentHp = npc.getLifeStats().getCurrentHp();
		maxHp = npc.getLifeStats().getMaxHp();
	}

	@Override
	protected void writeImpl(AionConnection con) {
		writeD(objectId);
		writeC(level);
		writeD(currentHp);
		writeD(maxHp);
	}
}
