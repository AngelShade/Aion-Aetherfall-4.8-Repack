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
 * Solo arena for quest 1922. Revive at the first native arena entry revival point.
 */
@InstanceID(310080000)
public class SanctumUndergroundArenaInstance extends GeneralInstanceHandler {

	public SanctumUndergroundArenaInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PlayerReviveService.revive(player, 25, 25, true, 0);
		player.getGameStats().updateStatsAndSpeedVisually();
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REBIRTH_MASSAGE_ME());
		TeleportService.teleportTo(player, instance, 278.503967f, 289.728882f, 164.1f, (byte) 90);
		return true;
	}
}
