package com.aionemu.gameserver.model.team.group.events;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author ATracer
 */
public class PlayerGroupInvite extends RequestResponseHandler<Player> {

	public PlayerGroupInvite(Player inviter) {
		super(inviter);
	}

	@Override
	public void acceptRequest(Player inviter, Player invited) {
		if (com.aionemu.gameserver.services.playerbot.PlayerBotFindGroup.paidInvitation(inviter, invited)) return;
		// Several humans can accept the same bot leader's invitation on different client connections.
		// Serialize initial group creation, then use the native team lock for capacity + admission.
		synchronized (inviter) {
			PlayerGroup group = inviter.getPlayerGroup();
			Runnable admission = () -> {
				if (inviter.getPlayerGroup() != group || inviter.isInAlliance()
					|| group != null && group.size() >= group.getMaxMemberCount()) return;
				if (!PlayerRestrictions.canInviteToGroup(inviter, invited)) return;
				if (group != null) PlayerGroupService.addPlayer(group, invited);
				else PlayerGroupService.createGroup(inviter, invited, TeamType.GROUP, 0);
			};
			if (group == null) admission.run(); else group.executeLocked(admission);
		}
		com.aionemu.gameserver.services.playerbot.PlayerBotFindGroup.accepted(inviter, invited);
	}

	@Override
	public void denyRequest(Player inviter, Player invited) {
		PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.STR_PARTY_HE_REJECT_INVITATION(invited.getName()));
	}

}
