package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Solo arena for quest 2947. Revive at the first native arena entry revival point.
 */
@InstanceID(320090000)
public class TrinielUndergroundArenaInstance extends GeneralInstanceHandler {

	public TrinielUndergroundArenaInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PlayerReviveService.revive(player, 25, 25, true, 0);
		player.getGameStats().updateStatsAndSpeedVisually();
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REBIRTH_MASSAGE_ME());
		TeleportService.teleportTo(player, instance, 277.86734f, 289.877625f, 164.1f, (byte) 90);
		return true;
	}
}
