package com.aionemu.gameserver.network.aion.clientpackets;

import java.util.Set;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.actions.DecomposeAction;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;

/**
 * @author xTz
 */
public class CM_SELECT_DECOMPOSABLE extends AionClientPacket {

	private int objectId;
	private int index;

	public CM_SELECT_DECOMPOSABLE(int opcode, Set<State> validStates) {
		super(opcode, validStates);
	}

	@Override
	protected void readImpl() {
		objectId = readD();
		readD(); // the object id is a 64 bit field, ours never fill the upper half
		index = readUC();
	}

	@Override
	protected void runImpl() {
		Player player = getConnection().getActivePlayer();
		if (player == null)
			return;
		Item item = player.getInventory().getItemByObjId(objectId);
		if (item == null)
			return;
		// Players and idle companions share the native checks, consumption, reward and cooldown.
		DecomposeAction.select(player, item, index);
	}

}
