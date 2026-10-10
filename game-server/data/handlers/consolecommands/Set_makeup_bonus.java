package consolecommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_STATS_INFO;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.ConsoleCommand;

/**
 * @author ginho1
 */
public class Set_makeup_bonus extends ConsoleCommand {

	public Set_makeup_bonus() {
		super("set_makeup_bonus", "Set Repose Energy.",
			"<value> - Set the Repose Energy value.");
	}

	@Override
	public void execute(Player admin, String... params) {
		if (params.length < 1) {
			sendInfo(admin);
			return;
		}

		final Player player = admin.getTarget() instanceof Player target ? target : admin;

		long value;

		try {
			value = Long.parseLong(params[0]);
		} catch (NumberFormatException e) {
			sendInfo(admin);
			return;
		}

		player.getCommonData().setCurrentReposeEnergy(value);
		PacketSendUtility.sendPacket(player, new SM_STATS_INFO(player));
		PacketSendUtility.sendMessage(admin, "Repose Energy set to: " + value);
	}
}
