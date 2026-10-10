package admincommands;

import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.BannedMacManager;
import com.aionemu.gameserver.utils.chathandlers.AdminCommand;

/**
 * @author KID, nrg
 */
public class BanMac extends AdminCommand {

	public BanMac() {
		super("banmac", "Ban a MAC address.",
			"<time in minutes> [mac] - Ban an address, or the selected player if mac is omitted.\nNote: 0 minutes will cause permanent ban.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params == null || params.length < 1) {
			sendUsage(player, "Please add one or more parameters");
			return;
		}

		int time;
		String address;
		String targetName = "direct_type";

		// try parsing
		try {
			time = Integer.parseInt(params[0]);

			if (time == 0) // 0 is 10 years since system don't allow infinte banns without rework - it's pseudo infinity
				time = 60 * 24 * 365 * 10;
		} catch (NumberFormatException e) {
			sendUsage(player, "Please enter a valid integer amount of minutes");
			return;
		}

		// is mac defined?
		if (params.length > 1) {
			address = params[1];
		} else { // no address defined
			VisibleObject target = player.getTarget();
			if (target instanceof Player) {
				if (target.equals(player)) {
					sendUsage(player, "Omg, disselect yourself please.");
					return;
				}

				Player targetpl = (Player) target;
				address = targetpl.getClientConnection().getMacAddress();
				targetName = targetpl.getName();
				targetpl.getClientConnection().close();
			} else {
				sendUsage(player, "You should select a player or give me any mac address");
				return;
			}
		}

		BannedMacManager.getInstance().banAddress(address, System.currentTimeMillis() + time * 60 * 1000,
			"author=" + player.getName() + ", " + player.getObjectId() + "; target=" + targetName);
	}

	private void sendUsage(Player player, String message) {
		if (message != null && !message.isEmpty())
			sendInfo(player, message);
		sendInfo(player);
	}
}
