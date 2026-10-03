package com.aionemu.gameserver.services.item;

import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.actions.DecomposeAction;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Bundle reuse is immediate after completion; an active opening cannot be restarted by another click. */
public final class BundleUseService {

	private BundleUseService() {
	}

	public static boolean isBundle(Item item) {
		if (item == null || item.getItemTemplate().getActions() == null)
			return false;
		return item.getItemTemplate().getActions().getItemActions().stream().anyMatch(DecomposeAction.class::isInstance);
	}

	public static boolean isOpening(Player player, Item item) {
		return isBundle(item) && player.getController().hasScheduledTask(TaskId.ITEM_USE);
	}

	public static boolean rejectWhileOpening(Player player, Item item) {
		if (!isOpening(player, item))
			return false;
		PacketSendUtility.sendMessage(player, "An item is already being opened. Wait for it to finish.");
		return true;
	}

}
