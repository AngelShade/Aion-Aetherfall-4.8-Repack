package consolecommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_STATS_INFO;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.ConsoleCommand;

/**
 * @author ginho1
 */
public class Set_vitalpoint extends ConsoleCommand {

	public Set_vitalpoint() {
		super("set_vitalpoint", "Set Salvation points.",
			"<value> - Set the Salvation point value.");
	}

	@Override
	public void execute(Player admin, String... params) {
		if (params.length < 1) {
			sendInfo(admin);
			return;
		}

		final Player player = admin.getTarget() instanceof Player target ? target : admin;

		int value;

		try {
			value = Integer.parseInt(params[0]);
		} catch (NumberFormatException e) {
			sendInfo(admin);
			return;
		}

		player.getCommonData().setCurrentSalvationPoints(value);
		PacketSendUtility.sendPacket(player, new SM_STATS_INFO(player));
	}
}
