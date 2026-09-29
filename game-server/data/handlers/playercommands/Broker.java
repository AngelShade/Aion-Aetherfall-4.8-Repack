package playercommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.RemoteBrokerService;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Opens the normal broker using an explicitly authorized remote session. */
public class Broker extends PlayerCommand {

	public Broker() {
		super("broker", "Opens the Broker window anywhere.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length != 0) {
			sendInfo(player);
			return;
		}
		RemoteBrokerService.open(player);
	}
}
