package com.aionemu.gameserver.model.team.alliance.events;

import java.util.ArrayList;
import java.util.List;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.alliance.PlayerAlliance;
import com.aionemu.gameserver.model.team.alliance.PlayerAllianceService;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.collections.Predicates;

/**
 * @author ATracer
 */
public class PlayerAllianceInvite extends RequestResponseHandler<Player> {

	public PlayerAllianceInvite(Player inviter) {
		super(inviter);
	}

	@Override
	public void acceptRequest(Player inviter, Player invited) {
		if (PlayerRestrictions.canInviteToAlliance(inviter, invited)) {
			Runnable admission = () -> acceptNative(inviter, invited);
			if (inviter.isInGroup() || invited.isInGroup()) {
				List<Player> transitioning = new ArrayList<>();
				transitioning.add(inviter);
				transitioning.add(invited);
				if (inviter.isInGroup()) transitioning.addAll(inviter.getPlayerGroup().getMembers());
				if (invited.isInGroup()) transitioning.addAll(invited.getPlayerGroup().getMembers());
				try {
					if (transitioning.stream().anyMatch(Player::isPlayerBot))
						com.aionemu.gameserver.services.playerbot.PlayerBotService.getInstance().convertTeam(transitioning, admission);
					else admission.run();
				} catch (IllegalArgumentException refusal) {
					PacketSendUtility.sendMessage(inviter, refusal.getMessage());
					if (invited != inviter) PacketSendUtility.sendMessage(invited, refusal.getMessage());
				}
			} else {
				admission.run();
			}
		}
	}

	private void acceptNative(Player inviter, Player invited) {
			PlayerAlliance alliance = inviter.getPlayerAlliance();
			List<Player> playersToAdd = new ArrayList<>();
			collectPlayersToAdd(inviter, invited, playersToAdd, alliance);

			if (alliance == null) {
				alliance = PlayerAllianceService.createAlliance(inviter, invited, TeamType.ALLIANCE);
				playersToAdd.remove(invited);
			}

			for (Player member : playersToAdd) {
				PlayerAllianceService.addPlayer(alliance, member);
			}
	}

	private void collectPlayersToAdd(Player inviter, Player invited, List<Player> playersToAdd, PlayerAlliance alliance) {
		// Collect requester Group without leader
		if (inviter.isInGroup()) {
			if (alliance != null)
				throw new IllegalArgumentException("If requester is in group, alliance should be null");
			PlayerGroup group = inviter.getPlayerGroup();
			playersToAdd.addAll(group.filterMembers(Predicates.Players.allExcept(inviter)));

			for (Player player : group.getMembers())
				PlayerGroupService.removePlayer(player);
		}

		// Collect full Invited Group
		if (invited.isInGroup()) {
			PlayerGroup group = invited.getPlayerGroup();
			playersToAdd.addAll(group.getMembers());
			for (Player player : group.getMembers())
				PlayerGroupService.removePlayer(player);
		} else { // or just single player
			playersToAdd.add(invited);
		}
	}

	@Override
	public void denyRequest(Player requester, Player responder) {
		PacketSendUtility.sendPacket(requester, SM_SYSTEM_MESSAGE.STR_PARTY_ALLIANCE_HE_REJECT_INVITATION(responder.getName()));
	}

}
