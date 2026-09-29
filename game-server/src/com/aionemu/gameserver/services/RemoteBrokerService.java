package com.aionemu.gameserver.services;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Explicit remote broker sessions, separate from ordinary NPC interaction. */
public final class RemoteBrokerService {

	private static final long IDLE_TIMEOUT = TimeUnit.MINUTES.toNanos(30);
	private static final Map<Player, Long> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());

	private RemoteBrokerService() {
	}

	public static void open(Player player) {
		if (!player.isOnline() || player.isTrading())
			return;
		SESSIONS.put(player, System.nanoTime());
		PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(player.getObjectId(), DialogPage.VENDOR.id()));
	}

	public static boolean canAccess(Player player, int brokerObjectId) {
		if (player == null || !player.isOnline())
			return false;
		if (player.isTargetingNpcWithFunction(brokerObjectId, DialogAction.OPEN_VENDOR))
			return true;
		// The remote dialog uses this character's object ID, never a supplied NPC ID.
		if (brokerObjectId != player.getObjectId())
			return false;
		synchronized (SESSIONS) {
			Long lastAccess = SESSIONS.get(player);
			long now = System.nanoTime();
			if (lastAccess == null || now - lastAccess >= IDLE_TIMEOUT) {
				SESSIONS.remove(player);
				return false;
			}
			SESSIONS.put(player, now);
			return true;
		}
	}
}
