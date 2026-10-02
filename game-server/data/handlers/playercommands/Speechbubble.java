package playercommands;

import com.aionemu.gameserver.dao.PlayerSettingsDAO;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Native Chat Options negotiates this extension before receiving style metadata. */
public class Speechbubble extends PlayerCommand {
	public Speechbubble() {
		super("speechbubble", "Choose your speech bubble appearance.", "sync | preview | 0-4");
	}

	@Override
	public void execute(Player player, String... params) {
		if (params.length != 1) {
			sendInfo(player);
			return;
		}
		String choice = params[0];
		if (!choice.equals("sync") && !choice.equals("preview") && !choice.matches("[0-4]")) {
			sendInfo(player);
			return;
		}
		player.getClientConnection().enableSpeechBubbleClient();
		if (choice.matches("[0-4]")) {
			player.getPlayerSettings().setSpeechBubbleStyle(Integer.parseInt(choice));
			PlayerSettingsDAO.saveSettings(player);
		}
		PacketSendUtility.sendPacket(player, SM_MESSAGE.speechBubbleAcknowledgement(player));
		if (choice.equals("preview"))
			PacketSendUtility.sendPacket(player, new SM_MESSAGE(player, "Speech bubble preview", ChatType.NORMAL));
	}
}
