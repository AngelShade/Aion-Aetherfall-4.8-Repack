package admincommands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.Util;
import com.aionemu.gameserver.utils.chathandlers.AdminCommand;
import com.aionemu.gameserver.world.World;

/**
 * @author Phantom, ATracer
 */
public class Remove extends AdminCommand {

	public Remove() {
		super("remove", "Remove items from a player.",
			"<player> <item ID|item @link> [quantity] - Remove the specified items.");
	}

	@Override
	public void execute(Player admin, String... params) {
		if (params.length < 2) {
			sendUsage(admin, null);
			return;
		}

		int itemId = 0;
		long itemCount = 1;
		byte itemCountIndex = 2;
		Player target = World.getInstance().getPlayer(Util.convertName(params[0]));
		if (target == null) {
			sendUsage(admin, "Player isn't online.");
			return;
		}

		String itemString = params[1];
		if (itemString.equals("[item:") && params.length > 2) {
			// some item links have space before their ID
			itemString += params[2];
			if (params.length > 3) {
				itemCountIndex = 3;
			}
		}
		try {
			if (params.length > 2 && (itemCountIndex < params.length)) {
				// count parameter was passed
				itemCount = Long.parseLong(params[itemCountIndex]);
			}

			Pattern id = Pattern.compile("(?:\\[item:)??(\\d{9})");
			Matcher result = id.matcher(itemString);
			if (result.find()) {
				itemId = Integer.parseInt(result.group(1));
			}

		} catch (NumberFormatException e) {
			sendUsage(admin, "Invalid number parameter passed.");
			return;
		}

		if (itemId > 0) {
			if (itemCount > 0) {
				Storage bag = target.getInventory();
				long bagItemCount = bag.getItemCountByItemId(itemId);
				if (bagItemCount >= 1) {
					if (itemCount <= bagItemCount) {
						bag.decreaseByItemId(itemId, itemCount);
						PacketSendUtility.sendMessage(admin, "Successfully removed " + itemCount + "x [item:" + itemId + "] from " + target.getName()
							+ "'s inventory.");
						PacketSendUtility.sendMessage(target, "Admin removed " + itemCount + "x [item:" + itemId + "] from your inventory.");
					} else {
						sendUsage(admin, "Player only has " + bagItemCount + " of this item.");
					}
				} else {
					sendUsage(admin, "Player doesn't have that item.");
				}
			} else {
				sendUsage(admin, "Invalid item count.");
			}
		} else {
			sendUsage(admin, "Invalid item ID.");
		}
	}

	private void sendUsage(Player player, String message) {
		if (message != null && !message.isEmpty())
			sendInfo(player, message);
		sendInfo(player);
	}
}
