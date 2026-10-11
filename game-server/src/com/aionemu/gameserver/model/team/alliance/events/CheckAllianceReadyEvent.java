package com.aionemu.gameserver.model.team.alliance.events;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.alliance.PlayerAlliance;
import com.aionemu.gameserver.model.team.common.events.AlwaysTrueTeamEvent;
import com.aionemu.gameserver.model.team.common.events.TeamCommand;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ALLIANCE_READY_CHECK;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author ATracer
 */
public class CheckAllianceReadyEvent extends AlwaysTrueTeamEvent {

	private final PlayerAlliance alliance;
	private final Player player;
	private final TeamCommand eventCode;

	public CheckAllianceReadyEvent(PlayerAlliance alliance, Player player, TeamCommand eventCode) {
		this.alliance = alliance;
		this.player = player;
		this.eventCode = eventCode;
	}

	@Override
	public void handleEvent() {
		if (!alliance.hasMember(player.getObjectId())) return;
		switch (eventCode) {
			case ALLIANCE_CHECKREADY_START:
				if (!alliance.isSomeCaptain(player)) return;
				alliance.getReadyCheck().start(player.getObjectId(), alliance.getOnlineMembers().stream().map(Player::getObjectId).toList(), System.currentTimeMillis());
				break;
			case ALLIANCE_CHECKREADY_CANCEL:
			case ALLIANCE_CHECKREADY_AUTOCANCEL:
				if (!alliance.isSomeCaptain(player)) return;
				alliance.getReadyCheck().cancel();
				break;
			case ALLIANCE_CHECKREADY_READY:
			case ALLIANCE_CHECKREADY_NOTREADY:
				if (!alliance.getReadyCheck().reply(player.getObjectId(), eventCode == TeamCommand.ALLIANCE_CHECKREADY_READY)) return;
				break;
			default: return;
		}
		alliance.setAllianceReadyStatus(alliance.getReadyCheck().waiting());
		alliance.forEach(member -> {
			switch (eventCode) {
				case ALLIANCE_CHECKREADY_CANCEL:
					PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(player.getObjectId(), 0));
					break;
				case ALLIANCE_CHECKREADY_START:
					PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(player.getObjectId(), 5));
					PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(player.getObjectId(), 1));
					break;
				case ALLIANCE_CHECKREADY_AUTOCANCEL:
					PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(player.getObjectId(), 2));
					break;
				case ALLIANCE_CHECKREADY_READY:
					PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(player.getObjectId(), 5));
					if (alliance.getAllianceReadyStatus() == 0) {
						PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(0, 3));
					}
					break;
				case ALLIANCE_CHECKREADY_NOTREADY:
					PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(player.getObjectId(), 4));
					if (alliance.getAllianceReadyStatus() == 0) {
						PacketSendUtility.sendPacket(member, new SM_ALLIANCE_READY_CHECK(0, 3));
					}
					break;
			}
		});
	}

}
