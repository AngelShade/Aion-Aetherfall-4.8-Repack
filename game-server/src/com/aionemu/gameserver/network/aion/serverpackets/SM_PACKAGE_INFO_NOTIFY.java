package com.aionemu.gameserver.network.aion.serverpackets;

import java.util.Map;

import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

/**
 * Pack list on the Profile window's Account tab (S_BM_PACK_LIST). Aetherfall sends its earned boons here.
 *
 * @author Rolandas
 */
public class SM_PACKAGE_INFO_NOTIFY extends AionServerPacket {

	private final Map<Integer, Integer> packs;

	/**
	 * @param packs
	 *          zero-based client pack slot -> seconds until expiration (0 = no time limit)
	 */
	public SM_PACKAGE_INFO_NOTIFY(Map<Integer, Integer> packs) {
		this.packs = packs;
	}

	@Override
	protected void writeImpl(AionConnection con) {
		writeH(packs.size());
		for (Map.Entry<Integer, Integer> pack : packs.entrySet()) {
			writeC(pack.getKey());
			writeD(pack.getValue());
		}
	}

}
