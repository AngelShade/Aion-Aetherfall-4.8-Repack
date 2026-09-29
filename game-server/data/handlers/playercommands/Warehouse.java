package playercommands;

import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.services.WarehouseService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Opens the player's existing character/account warehouse. */
public class Warehouse extends PlayerCommand {
	public Warehouse() {
		super("warehouse", "Opens your character and account warehouse.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length != 0) { sendInfo(player); return; }
		if (!player.isOnline() || player.isDead() || player.isTrading()) {
			sendInfo(player, "Warehouse is unavailable during trade or death.");
			return;
		}
		WarehouseService.sendWarehouseInfo(player, true);
		PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(player.getObjectId(), DialogPage.DEPOSIT_CHAR_WAREHOUSE.id()));
	}
}
