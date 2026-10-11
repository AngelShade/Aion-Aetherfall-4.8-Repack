package com.aionemu.gameserver.model.team.alliance.events;

import com.aionemu.gameserver.model.team.alliance.PlayerAlliance;
import com.aionemu.gameserver.model.team.alliance.PlayerAllianceGroup;
import com.aionemu.gameserver.model.team.alliance.PlayerAllianceMember;
import com.aionemu.gameserver.model.team.common.events.AlwaysTrueTeamEvent;
import com.aionemu.gameserver.model.team.common.legacy.PlayerAllianceEvent;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ALLIANCE_MEMBER_INFO;

/**
 * @author ATracer
 */
public class ChangeMemberGroupEvent extends AlwaysTrueTeamEvent {

	private final PlayerAlliance alliance;
	private final int firstMemberId;
	private final int secondMemberId;
	private final int allianceGroupId;

	public ChangeMemberGroupEvent(PlayerAlliance alliance, int firstMemberId, int secondMemberId, int allianceGroupId) {
		this.alliance = alliance;
		this.firstMemberId = firstMemberId;
		this.secondMemberId = secondMemberId;
		this.allianceGroupId = allianceGroupId;
	}

	@Override
	public void handleEvent() {
		PlayerAllianceMember firstMember = alliance.getMember(firstMemberId);
		if (firstMember == null) // probably left or got kicked right before handleEvent() was called
			return;
		if (secondMemberId != 0) {
			PlayerAllianceMember secondMember = alliance.getMember(secondMemberId);
			if (secondMember == null) // probably left or got kicked right before handleEvent() was called
				return;
			swapMembersInGroup(firstMember, secondMember);
		} else {
			moveMemberToGroup(firstMember, allianceGroupId);
		}
	}

	private void swapMembersInGroup(PlayerAllianceMember firstMember, PlayerAllianceMember secondMember) {
		PlayerAllianceGroup firstAllianceGroup = firstMember.getPlayerAllianceGroup();
		PlayerAllianceGroup secondAllianceGroup = secondMember.getPlayerAllianceGroup();
		if (firstMember == secondMember || firstAllianceGroup == secondAllianceGroup)
			return;
		firstAllianceGroup.removeMember(firstMember);
		secondAllianceGroup.removeMember(secondMember);
		firstAllianceGroup.addMember(secondMember);
		secondAllianceGroup.addMember(firstMember);
		alliance.sendPackets(new SM_ALLIANCE_MEMBER_INFO(firstMember, PlayerAllianceEvent.MEMBER_GROUP_CHANGE),
			new SM_ALLIANCE_MEMBER_INFO(secondMember, PlayerAllianceEvent.MEMBER_GROUP_CHANGE));
	}

	private void moveMemberToGroup(PlayerAllianceMember firstMember, int allianceGroupId) {
		PlayerAllianceGroup firstAllianceGroup = firstMember.getPlayerAllianceGroup();
		// Validate destination before removing membership, including native packet callers.
		if (allianceGroupId < 1000 || allianceGroupId > 1003)
			return;
		PlayerAllianceGroup newAllianceGroup = alliance.getAllianceGroup(allianceGroupId);
		if (newAllianceGroup == firstAllianceGroup || newAllianceGroup.size() >= newAllianceGroup.getMaxMemberCount())
			return;
		firstAllianceGroup.removeMember(firstMember);
		newAllianceGroup.addMember(firstMember);
		alliance.sendPackets(new SM_ALLIANCE_MEMBER_INFO(firstMember, PlayerAllianceEvent.MEMBER_GROUP_CHANGE));
	}
}
