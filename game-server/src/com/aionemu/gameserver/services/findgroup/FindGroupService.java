package com.aionemu.gameserver.services.findgroup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.configs.network.NetworkConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.AionObject;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.findGroup.GroupApplication;
import com.aionemu.gameserver.model.gameobjects.findGroup.FindGroupEntry;
import com.aionemu.gameserver.model.gameobjects.findGroup.GroupRecruitment;
import com.aionemu.gameserver.model.gameobjects.findGroup.ServerWideGroup;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TemporaryPlayerTeam;
import com.aionemu.gameserver.model.team.alliance.PlayerAllianceService;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_FIND_GROUP;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.services.playerbot.PlayerBotFindGroup;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/**
 * @author cura, MrPoke
 */
public class FindGroupService {

	private final Map<Integer, GroupRecruitment> recruitments = new ConcurrentHashMap<>(); // Recruit Group Members tab
	private final Map<Integer, GroupApplication> applications = new ConcurrentHashMap<>(); // Apply for Group tab
	private final Map<Integer, ServerWideGroup> instanceGroups = new ConcurrentHashMap<>(); // Instance Groups tab

	private FindGroupService() {
	}

	public void showRecruitments(Player player) {
		List<GroupRecruitment> recruitments = this.recruitments.values().stream().filter(r -> r.getRace() == player.getRace() && PlayerBotFindGroup.visible(r)).toList();
		PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(0, recruitments));
	}

	public void showApplications(Player player) {
		List<GroupApplication> applications = this.applications.values().stream().filter(r -> r.getPlayer().getRace() == player.getRace() && PlayerBotFindGroup.visible(r)).toList();
		PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(4, applications));
	}

	public GroupRecruitment removeRecruitment(TemporaryPlayerTeam<?> team) {
		return removeRecruitment(team.getTeamId(), (byte) NetworkConfig.GAMESERVER_ID, (byte) 0, (byte) 0, (byte) 0);
	}

	public GroupRecruitment removeRecruitment(Player player, byte serverId, byte unk1, byte unk2, byte unk3) {
		int teamId = player.getCurrentTeamId();
		return removeRecruitment(teamId == 0 ? player.getObjectId() : teamId, serverId, unk1, unk2, unk3);
	}

	private GroupRecruitment removeRecruitment(int playerOrTeamId, byte serverId, byte unk1, byte unk2, byte unk3) {
		GroupRecruitment recruitment = recruitments.remove(playerOrTeamId);
		if (recruitment != null)
			PacketSendUtility.broadcastToWorld(new SM_FIND_GROUP(playerOrTeamId, serverId, unk1, unk2, unk3), p -> p.getRace() == recruitment.getRace());
		return recruitment;
	}

	public void removeApplication(Player player) {
		GroupApplication application = applications.remove(player.getObjectId());
		if (application != null)
			PacketSendUtility.broadcastToWorld(new SM_FIND_GROUP(player.getObjectId()), p -> p.getRace() == application.getPlayer().getRace());
	}

	public void addRecruitment(Player player, String message, int groupType) {
		AionObject playerOrTeam = player.getCurrentTeam();
		if (playerOrTeam == null)
			playerOrTeam = player;
		GroupRecruitment recruitment = new GroupRecruitment(playerOrTeam, message, groupType);
		recruitments.put(playerOrTeam.getObjectId(), recruitment);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_PARTY_MATCH_OFFER_PARTY_POSTED());
		showRecruitments(player); // necessary if player switched tabs before adding this entry (client bug)
	}

	public void addApplication(Player player, String message, int groupType, int classId, int level) {
		GroupApplication application = new GroupApplication(player, message, groupType, classId, level);
		applications.put(player.getObjectId(), application);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_PARTY_MATCH_SEEK_PARTY_POSTED());
		showApplications(player); // necessary if player switched tabs before adding this entry (client bug)
	}

	public void updateRecruitment(Player player, String message, int groupType) {
		int teamId = player.getCurrentTeamId();
		GroupRecruitment recruitment = recruitments.get(teamId == 0 ? player.getObjectId() : teamId);
		if (recruitment != null) {
			recruitment.setMessage(message);
			recruitment.setGroupType(groupType);
			recruitment.updateLastUpdate();
		}
	}

	public void updateApplication(Player player, String message, int groupType, int classId, int level) {
		GroupApplication application = applications.get(player.getObjectId());
		if (application != null) {
			application.setMessage(message);
			application.setGroupType(groupType);
			application.setClassId(classId);
			application.setLevel(level);
			application.updateLastUpdate();
		}
	}

	public void showInstanceGroups(Player player, boolean isUpdate) {
		List<ServerWideGroup> instanceGroups = this.instanceGroups.values().stream().filter(group -> group.getRace() == player.getRace() && PlayerBotFindGroup.visible(group)).toList();

		if (!isUpdate && GroupConfig.FORM_INSTANCE_GROUP_ANYWHERE) {
			List<Integer> instanceMaskIds = null;
			if (player.getTarget() instanceof Npc npc)
				instanceMaskIds = DataManager.AUTO_GROUP.getRecruitableInstanceMaskIds(npc.getNpcId());
			if (instanceMaskIds == null)
				instanceMaskIds = DataManager.AUTO_GROUP.getRecruitableInstanceMaskIds();
			PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(instanceMaskIds));
		}

		PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(10, instanceGroups));
	}

	public void showInstanceGroups(Player player, Npc portalNpc) {
		List<Integer> instanceMaskIds = DataManager.AUTO_GROUP.getRecruitableInstanceMaskIds(portalNpc.getNpcId());
		if (instanceMaskIds != null)
			PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(instanceMaskIds));
	}

	public void registerInstanceGroup(Player player, int instanceMaskId, String message, int minMembers) {
		ServerWideGroup instanceGroup = new ServerWideGroup(player, instanceMaskId, minMembers, message);
		instanceGroups.put(player.getObjectId(), instanceGroup);
		PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(14, List.of(instanceGroup)));
	}

	public void updateInstanceGroup(Player player, String message) {
		ServerWideGroup instanceGroup = instanceGroups.get(player.getObjectId());
		if (instanceGroup != null) {
			instanceGroup.setMessage(message);
			instanceGroup.setLastUpdate();
			showInstanceGroups(player, true);
		}
	}

	public void removeInstanceGroup(Player player) {
		instanceGroups.remove(player.getObjectId());
		showInstanceGroups(player, true);
	}

	public void showInstanceGroupMembersInfo(Player player, int playerObjectId) {
		ServerWideGroup instanceGroup = instanceGroups.get(playerObjectId);
		if (instanceGroup != null && instanceGroup.getRace() == player.getRace() && PlayerBotFindGroup.visible(instanceGroup))
			PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(16, List.of(instanceGroup)));
	}

	public void sendInstanceApplication(Player applicant, int playerOrTeamId) {
		Player player = World.getInstance().getPlayer(playerOrTeamId);
		if (player != null && PlayerBotFindGroup.apply(applicant, player))
			return;
		if (player != null)
			PacketSendUtility.sendPacket(player, new SM_FIND_GROUP(applicant));
	}

	public void sendInstanceApplicationResult(Player responder, int applicantId, byte instanceApplicationReply) {
		Player applicant = World.getInstance().getPlayer(applicantId);
		if (applicant != null) {
			if (instanceApplicationReply == 1) {
				ServerWideGroup instanceGroup = instanceGroups.get(responder.getObjectId());
				if (instanceGroup != null) {
					// custom: invite to team to keep it simple, as cross-server recruitment is currently not implemented.
					// for more info about official server implementation, see CM_/SM_FIND_GROUP action codes 18-25 and
					// https://forum.aion.gameforge.com/forum/thread/742-server-wide-recruitment-guide-by-kelekelio/
					if (instanceGroup.getMinMembers() <= 6)
						PlayerGroupService.inviteToGroup(responder, applicant);
					else
						PlayerAllianceService.inviteToAlliance(responder, applicant);
				}
			}	else {
				PacketSendUtility.sendPacket(applicant, new SM_MESSAGE(responder, ChatUtil.l10n(1400217), ChatType.WHISPER));
			}
		}
	}

	private final Map<Integer, ServerWideGroup> prepared = new ConcurrentHashMap<>(); // leader -> group ready to enter

	/**
	 * Native "Prepare for entry" window (SM_FIND_GROUP 22/24/18): every human in the
	 * leader's team sees the full group; the leader gets the Enter button.
	 */
	public void prepareEntry(Player leader, ServerWideGroup group) {
		TemporaryPlayerTeam<?> team = leader.getCurrentTeam();
		if (team == null || !team.isLeader(leader))
			return;
		prepared.put(leader.getObjectId(), group);
		for (Player member : team.getMembers()) {
			if (member.isPlayerBot() || !member.isOnline())
				continue;
			PacketSendUtility.sendPacket(member, new SM_FIND_GROUP(22, List.of(group)));
			PacketSendUtility.sendPacket(member, new SM_FIND_GROUP(24, List.of(group)));
			if (member == leader)
				PacketSendUtility.sendPacket(member, new SM_FIND_GROUP(18, List.of(group)));
		}
	}

	/** The leader pressed Enter: each human enters through the native portal rules; companions follow their owner. */
	public void enterPrepared(Player leader) {
		ServerWideGroup group = prepared.remove(leader.getObjectId());
		TemporaryPlayerTeam<?> team = leader.getCurrentTeam();
		if (group == null || team == null || !team.isLeader(leader))
			return;
		for (Player member : team.getMembers()) {
			if (member.isPlayerBot() || !member.isOnline())
				continue;
			PlayerBotFindGroup.enterInstance(member, group);
			// Close the window for this member (the boolean-only packet has no group and cannot be written).
			PacketSendUtility.sendPacket(member, new SM_FIND_GROUP(23, List.of(group)));
		}
	}

	public void onJoinedTeam(Player player) {
		PlayerBotFindGroup.teamJoined(player);
		TemporaryPlayerTeam<?> joinedTeam = player.getCurrentTeam();
		if (joinedTeam != null) {
			// The leader's own Instance Group offer is complete: show the native Prepare for entry window.
			Player leader = joinedTeam.getLeaderObject();
			ServerWideGroup leaderOffer = leader == null ? null : instanceGroups.get(leader.getObjectId());
			if (leaderOffer != null && !PlayerBotFindGroup.manages(leaderOffer) && joinedTeam.getMembers().size() >= Math.max(2, leaderOffer.getMinMembers())) {
				instanceGroups.remove(leader.getObjectId());
				prepareEntry(leader, leaderOffer);
			}
		}
		ServerWideGroup instanceGroup = instanceGroups.get(player.getObjectId());
		// custom: team is used as a proxy for a server-wide instance group (forming a team removes instance group registrations on official servers)
		if (instanceGroup != null && !PlayerBotFindGroup.manages(instanceGroup) && instanceGroup.getMembers().size() >= instanceGroup.getMinMembers())
			instanceGroups.remove(player.getObjectId());
		removeApplication(player);
		GroupRecruitment recruitment = removeRecruitment(player.getObjectId(), (byte) NetworkConfig.GAMESERVER_ID, (byte) 0, (byte) 0, (byte) 16);
		TemporaryPlayerTeam<?> team = player.getCurrentTeam();
		if (recruitment != null && team.isLeader(player))
			addRecruitment(player, recruitment.getMessage(), recruitment.getGroupType());
		else if (team.isFull())
			removeRecruitment(team.getObjectId(), (byte) NetworkConfig.GAMESERVER_ID, (byte) 0, (byte) 0, (byte) 0);
	}

	public void onLogout(Player player) {
		recruitments.remove(player.getObjectId());
		applications.remove(player.getObjectId());
		instanceGroups.remove(player.getObjectId());
	}

	/** Human registrations only (snapshot, no packets), so bots can answer them. */
	public List<FindGroupEntry> humanEntries() {
		var entries = new java.util.ArrayList<FindGroupEntry>();
		for (var r : recruitments.values())
			if (!PlayerBotFindGroup.manages(r) && !(r.getObject() instanceof Player p && p.isWorldBot()) && !(r.getObject() instanceof TemporaryPlayerTeam<?> t && t.getLeaderObject().isWorldBot()))
				entries.add(r);
		for (var a : applications.values()) if (!a.getPlayer().isWorldBot()) entries.add(a);
		for (var g : instanceGroups.values()) if (!g.getRecruiter().isWorldBot()) entries.add(g);
		return entries;
	}

	/** Headless registrations share the human maps and packets; never replace another post. */
	public boolean registerBotEntry(FindGroupEntry entry) {
		if (entry instanceof GroupRecruitment r && (r.getObject() instanceof Player p && p.isWorldBot()
			|| r.getObject() instanceof TemporaryPlayerTeam<?> t && (t.getLeaderObject().isWorldBot() || PlayerBotFindGroup.manages(r))))
			return recruitments.putIfAbsent(r.getObjectId(), r) == null;
		if (entry instanceof GroupApplication a && a.getPlayer().isWorldBot())
			return applications.putIfAbsent(a.getPlayer().getObjectId(), a) == null;
		if (entry instanceof ServerWideGroup g && g.getRecruiter().isWorldBot())
			return instanceGroups.putIfAbsent(g.getId(), g) == null;
		return false;
	}

	/** Identity comparison preserves a newer registration and unrelated human teams. */
	public void removeBotEntry(FindGroupEntry entry) {
		if (entry instanceof GroupRecruitment r && recruitments.remove(r.getObjectId(), r))
			PacketSendUtility.broadcastToWorld(new SM_FIND_GROUP(r.getObjectId(), (byte) NetworkConfig.GAMESERVER_ID, (byte) 0, (byte) 0, (byte) (r.getObject() instanceof Player ? 16 : 0)), p -> p.getRace() == r.getRace());
		else if (entry instanceof GroupApplication a && applications.remove(a.getPlayer().getObjectId(), a))
			PacketSendUtility.broadcastToWorld(new SM_FIND_GROUP(a.getPlayer().getObjectId()), p -> p.getRace() == a.getPlayer().getRace());
		else if (entry instanceof ServerWideGroup g)
			instanceGroups.remove(g.getId(), g);
	}

	public static FindGroupService getInstance() {
		return SingletonHolder.instance;
	}

	private static class SingletonHolder {

		protected static final FindGroupService instance = new FindGroupService();
	}

}
