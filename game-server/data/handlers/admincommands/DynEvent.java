package admincommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.dynamicevents.DynamicEventService;
import com.aionemu.gameserver.services.playerbot.PlayerBotDynamicEvents;
import com.aionemu.gameserver.utils.chathandlers.AdminCommand;

/** Test shortcut for world events (config/dynamic-events/README.md). Normal play never needs it: Road Wardens start events on their own. */
public class DynEvent extends AdminCommand {

	public DynEvent() {
		super("dynevent", "Starts, stops or lists world events.", """
			start - Your targeted Road Warden starts a fitting event now.
			start <chain> - Starts that chain, for example road-ambush or supply-raid.
			stop - Ends the event around you now, without rewards.
			status - Lists loaded chains and running events.
			""");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length == 0) {
			sendInfo(player);
			return;
		}
		switch (params[0].toLowerCase()) {
			case "start" -> sendInfo(player, PlayerBotDynamicEvents.force(player, params.length > 1 ? params[1] : null));
			case "stop" -> sendInfo(player, DynamicEventService.stop(player));
			case "status" -> {
				for (String line : DynamicEventService.status().split("\n"))
					sendInfo(player, line);
			}
			default -> sendInfo(player);
		}
	}
}
