package com.aionemu.gameserver.services;

import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;

/** Admission shared by dialog and purchase packets for the Aetherfall NPC restoration. */
public final class RestoredNpcService {

	private RestoredNpcService() {
	}

	public static boolean isInteractionAllowed(Player player, Npc npc) {
		int id = npc.getNpcId();
		boolean balaur = id == 800371 || id == 800372 || id == 800417;
		boolean asmodian = id == 801494 || id == 801537;
		boolean elyos = id == 801495 || id == 801538;
		if (!balaur && !asmodian && !elyos)
			return true;
		if (player.isDead() || player.getWorldId() != npc.getWorldId()
			|| player.getInstanceId() != npc.getInstanceId()
			|| !PositionUtil.isInTalkRange(player, npc))
			return false;
		if (asmodian && player.getRace() != Race.ASMODIANS || elyos && player.getRace() != Race.ELYOS)
			return false;
		if (player.getLevel() < 60) {
			PacketSendUtility.sendMessage(player, "This service is available from level 60.");
			return false;
		}
		if (balaur && (player.getWorldId() != 300510000 || !player.getEffectController().hasAbnormalEffect(300))) {
			PacketSendUtility.sendMessage(player, "Use Vershaka's Statue in the Noble Garden to take Drakan form before trading.");
			return false;
		}
		return true;
	}

	/** Norshaka's captured armor must match the buyer's faction, including forged purchase requests. */
	public static boolean isTradeTabAllowed(Player player, Npc npc, int tabId) {
		return npc.getNpcId() != 800417 || tabId == (player.getRace() == Race.ELYOS ? 60012 : 60013);
	}
}
