package playercommands;

import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/**
 * Opens the normal appearance remodeling window without visiting an NPC.
 */
public class Transmog extends PlayerCommand {

	public Transmog() {
		super("transmog", "Opens the appearance remodeling window anywhere.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length != 0) {
			sendInfo(player);
			return;
		}

		// Use the player's always-visible object as the dialog anchor. The existing
		// ItemRemodelService still validates both items and charges the normal fee.
		PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(player.getObjectId(), DialogPage.CHANGE_ITEM_SKIN.id()));
	}
}
