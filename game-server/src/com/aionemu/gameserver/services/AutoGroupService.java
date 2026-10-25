package com.aionemu.gameserver.services;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.configs.main.AutoGroupConfig;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.autogroup.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.alliance.PlayerAllianceService;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_AUTO_GROUP;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.autogroup.AutoGroupUtility;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.instance.PeriodicInstanceManager;
import com.aionemu.gameserver.services.instance.PvPArenaService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * @author xTz, Estrayl
 */
public class AutoGroupService {

	private final Map<WorldMapInstance, AutoInstance> autoInstances = new ConcurrentHashMap<>();
	private final Map<Integer, List<LookingForParty>> lookingParties = new ConcurrentHashMap<>();
	private final Set<Integer> penalties = ConcurrentHashMap.newKeySet();

	private AutoGroupService() {
	}

	public void startLooking(Player player, int maskId, EntryRequestType ert) {
		AutoGroupType agt = AutoGroupType.getAGTByMaskId(maskId);
		if (agt == null || !canRegister(player, ert, agt))
			return;
		List<LookingForParty> lfps = lookingParties.computeIfAbsent(maskId, k -> new ArrayList<>());
		LookingForParty lfp;
		synchronized (lfps) {
			lfp = getSearchEntry(player.getObjectId(), lfps);
			if (lfp != null) {
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_CANT_INSTANCE_ALREADY_REGISTERED(agt.getTemplate().getInstanceMapId()));
				return;
			}
			lfp = new LookingForParty(player, ert, maskId);
			lfps.add(lfp);

			AutoGroupUtility.sendSuccessfulRegistration(lfp, player.getName(), agt, maskId);
			if (AutoGroupConfig.ANNOUNCE_BATTLEGROUND_REGISTRATIONS && agt.isPeriodicInstance() && ert == EntryRequestType.GROUP_ENTRY
				&& lfps.stream().filter(s -> s.getRace() == player.getRace()).count() == 1) {
				PacketSendUtility.broadcastToWorld(
					new SM_MESSAGE(0, null, player.getRace().getL10n() + " have registered for " + agt.getL10n() + ".", ChatType.BRIGHT_YELLOW_CENTER),
					p -> p.getRace() != player.getRace() && agt.isInLvlRange(p.getLevel()));
			}

			if (!checkInstancesForOpenQuickEntries(lfp, maskId))
				checkQueueForNewMatches(maskId);
		}
	}

	private void checkQueueForNewMatches(int maskId) {
		List<LookingForParty> queuedParties = lookingParties.get(maskId);
		if (queuedParties == null || queuedParties.isEmpty())
			return;
		AutoGroupType agt = AutoGroupType.getAGTByMaskId(maskId);
		if (agt == null)
			return;
		synchronized (queuedParties) {
			queuedParties.sort(null);

			for (int i = 0; i < queuedParties.size(); i++) {
				AutoInstance autoInstance = agt.createAutoInstance();
				LookingForParty lfp = queuedParties.get(i);
				AGQuestion question = autoInstance.addLookingForParty(lfp);
				if (question == AGQuestion.FAILED)
					continue;
				List<LookingForParty> filteredParties = new ArrayList<>();
				filteredParties.add(lfp);
				if (question != AGQuestion.READY) {
					for (int j = i + 1; j < queuedParties.size(); j++) {
						lfp = queuedParties.get(j);
						question = autoInstance.addLookingForParty(lfp);
						if (question != AGQuestion.FAILED) {
							filteredParties.add(lfp);
							if (question == AGQuestion.READY)
								break;
						}
					}
				}
				if (question == AGQuestion.READY) {
					createNewInstance(autoInstance, agt, filteredParties, maskId);
					break;
				}
			}
		}
	}

	private void createNewInstance(AutoInstance autoInstance, AutoGroupType agt, List<LookingForParty> filteredParties, int maskId) {
		WorldMapInstance instance = InstanceService.getNextAvailableInstance(agt.getTemplate().getInstanceMapId(), 0, agt.getDifficultId(), null,
			autoInstance.getMaxPlayers(), false);
		autoInstance.onInstanceCreate(instance);
		autoInstances.put(instance, autoInstance);
		for (LookingForParty lfp : filteredParties) {
			removeSearchEntry(lfp);
			lfp.setStartEnterTime();
			lfp.getMembers().keySet().forEach(id -> {
				searchAndRemoveAdditionalRegistrations(id);
				AutoGroupUtility.sendWindowToPlayerIfOnline(id, maskId, 4);
			});
		}
	}

	private boolean checkInstancesForOpenQuickEntries(LookingForParty lfp, int maskId) {
		if (lfp.getEntryRequestType() != EntryRequestType.QUICK_GROUP_ENTRY || lfp.isOnStartEnterTask())
			return false;
		for (AutoInstance autoInstance : autoInstances.values()) {
			if (autoInstance.getAutoGroupType().getTemplate().getMaskId() == maskId && autoInstance.addLookingForParty(lfp) == AGQuestion.ADDED) {
				removeSearchEntry(lfp);
				lfp.setStartEnterTime();
				AutoGroupUtility.sendWindowToPlayerIfOnline(lfp.getLeaderObjId(), maskId, 4);
				searchAndRemoveAdditionalRegistrations(lfp.getLeaderObjId());
				return true;
			}
		}
		return false;
	}

	private void checkQueueForQuickEntries(AutoInstance autoInstance) {
		int maskId = autoInstance.getAutoGroupType().getTemplate().getMaskId();
		List<LookingForParty> parties = lookingParties.get(maskId);
		if (parties == null || parties.isEmpty())
			return;
		synchronized (parties) {
			for (LookingForParty lfp : parties) {
				if (lfp.getEntryRequestType() == EntryRequestType.QUICK_GROUP_ENTRY && !lfp.isOnStartEnterTask()
					&& autoInstance.addLookingForParty(lfp) == AGQuestion.ADDED) {
					removeSearchEntry(lfp);
					lfp.setStartEnterTime();
					AutoGroupUtility.sendWindowToPlayerIfOnline(lfp.getLeaderObjId(), maskId, 4);
					searchAndRemoveAdditionalRegistrations(lfp.getLeaderObjId());
					return;
				}
			}
		}
	}

	private void searchAndRemoveAdditionalRegistrations(int objectId) {
		List<LookingForParty> partiesToRemove = getSearchEntries(objectId);
		for (LookingForParty lfp : partiesToRemove) {
			int maskId = lfp.getMaskId();
			if (lfp.isLeader(objectId)) {
				removeSearchEntry(lfp);
				penaliseParty(lfp);
				lfp.getMembers().keySet().forEach(id -> AutoGroupUtility.sendWindowToPlayerIfOnline(id, maskId, 2));
			} else {
				lfp.unregisterMember(objectId);
				AutoGroupUtility.sendWindowToPlayerIfOnline(objectId, maskId, 2);
				penalisePlayerAndScheduleRemoval(objectId);
				checkQueueForNewMatches(maskId);
			}
		}
	}

	public void pressEnter(Player player, int instanceMaskId) {
		AutoInstance instance = getAutoInstance(player, instanceMaskId);
		if (instance == null)
			return;

		if (player.isInGroup())
			PlayerGroupService.removePlayer(player);
		if (player.isInAlliance())
			PlayerAllianceService.removePlayer(player);

		instance.onPressEnter(player);
		PacketSendUtility.sendPacket(player, new SM_AUTO_GROUP(instanceMaskId, 5));
	}

	public void onEnterInstance(Player player) {
		if (player.isInInstance()) {
			int obj = player.getObjectId();
			AutoInstance autoInstance = autoInstances.get(player.getWorldMapInstance());
			if (autoInstance != null && autoInstance.getRegisteredAGPlayers().containsKey(obj))
				autoInstance.onEnterInstance(player);
		}
	}

	public void cancelEnter(Player player, int instanceMaskId) {
		AutoInstance autoInstance = getAutoInstance(player, instanceMaskId);
		if (autoInstance != null) {
			int objectId = player.getObjectId();
			autoInstance.unregister(player);
			penalisePlayerAndScheduleRemoval(objectId);
			destroyOrAddPlayersFromQuickEntries(autoInstance);
			PacketSendUtility.sendPacket(player, new SM_AUTO_GROUP(instanceMaskId, 2));
		}
	}

	public void onPlayerLogin(Player player) {
		PeriodicInstanceManager.getInstance().checkAndSendOpenRegistrations(player);
	}

	public boolean isSearching(Player player, int maskId) {
		return getSearchEntry(player.getObjectId(), lookingParties.get(maskId)) != null;
	}

	private LookingForParty getSearchEntry(Player player, int maskId) {
		return getSearchEntry(player.getObjectId(), lookingParties.get(maskId));
	}

	private LookingForParty getSearchEntry(int playerObjectId, List<LookingForParty> parties) {
		if (parties != null) {
			synchronized (parties) {
				for (LookingForParty lfp : parties)
					if (lfp.isMember(playerObjectId))
						return lfp;
			}
		}
		return null;
	}

	private List<LookingForParty> getSearchEntries(int playerObjectId) {
		return lookingParties.values().stream().map(parties -> getSearchEntry(playerObjectId, parties)).filter(Objects::nonNull).toList();
	}

	public void onLogout(Player player) {
		int objectId = player.getObjectId();
		// A generated actor can leave before pressing Enter. Its current map is
		// still outdoors, so the normal current-instance lookup cannot find it.
		if (generatedPopulationBot(player))
			for (var pending : autoInstances.values())
				if (pending.getInstance() != player.getWorldMapInstance() && pending.getRegisteredAGPlayers().containsKey(objectId))
					cancelEnter(player, pending.getAutoGroupType().getTemplate().getMaskId());
		for (LookingForParty lfp : getSearchEntries(objectId)) {
			if (lfp.isOnStartEnterTask()) {
				for (AutoInstance autoInstance : autoInstances.values()) {
					cancelEnter(player, autoInstance.getAutoGroupType().getTemplate().getMaskId());
				}
			} else if (lfp.isLeader(objectId)) {
				lfp.setLeaderObjId(lfp.getMembers().keySet().stream().filter(id -> id != objectId).findFirst().orElse(0));
				if (generatedPopulationBot(player))
					lfp.unregisterMember(objectId);
				if (lfp.getLeaderObjId() == 0) {
					removeSearchEntry(lfp);
				}
			} else {
				lfp.unregisterMember(objectId);
				checkQueueForNewMatches(lfp.getMaskId());
			}
		}

		AutoInstance autoInstance = autoInstances.get(player.getWorldMapInstance());
		if (autoInstance != null && autoInstance.getRegisteredAGPlayers().containsKey(objectId)) {
			destroyIfPossible(autoInstance);
		}
	}

	private void removeSearchEntry(LookingForParty lfp) {
		List<LookingForParty> lfps = lookingParties.get(lfp.getMaskId());
		synchronized (lfps) {
			lfps.remove(lfp);
		}
	}

	public void onLeaveInstance(Player player) {
		AutoInstance autoInstance = autoInstances.get(player.getWorldMapInstance());
		if (autoInstance != null && autoInstance.getRegisteredAGPlayers().containsKey(player.getObjectId())) {
			autoInstance.onLeaveInstance(player);
			destroyOrAddPlayersFromQuickEntries(autoInstance);
		}
		PeriodicInstanceManager.getInstance().checkAndSendOpenRegistrations(player);
	}

	private boolean canRegister(Player player, EntryRequestType ert, AutoGroupType agt) {
		int mapId = agt.getTemplate().getInstanceMapId();
		int instanceMaskId = agt.getTemplate().getMaskId();
		if (!agt.isInLvlRange(player.getLevel())) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_CANT_INSTANCE_ENTER_LEVEL());
			return false;
		} else if ((agt.isPvPFFAArena() || agt.isPvPSoloArena() || agt.isHarmonyArena() || agt.isGloryArena())
			&& !PvPArenaService.isPvPArenaAvailable(player, agt)) {
			return false;
		} else if (AutoGroupUtility.hasCoolDown(player, mapId)) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_CANNOT_MAKE_INSTANCE_COOL_TIME());
			return false;
		}
		return switch (ert) {
			case NEW_GROUP_ENTRY -> AutoGroupUtility.canRegisterNewEntry(player, agt);
			case QUICK_GROUP_ENTRY -> AutoGroupUtility.canRegisterQuickEntry(player, agt);
			case GROUP_ENTRY -> AutoGroupUtility.canRegisterGroupEntry(player, agt, mapId, instanceMaskId);
		};
	}

	private void penaliseParty(LookingForParty lfp) {
		lfp.getMembers().keySet().forEach(this::penalisePlayerAndScheduleRemoval);
	}

	private void penalisePlayerAndScheduleRemoval(int objectId) {
		if (penalties.add(objectId)) {
			ThreadPoolManager.getInstance().schedule(() -> {
				penalties.remove(objectId);
				PeriodicInstanceManager.getInstance().checkAndSendOpenRegistrations(objectId);
			}, 10000);
		}
	}

	public void stopRegistrationsByMaskId(int maskId) {
		List<LookingForParty> parties = lookingParties.remove(maskId);
		if (parties != null && !parties.isEmpty())
			parties.forEach(lfp -> lfp.getMembers().keySet().forEach(id -> AutoGroupUtility.sendWindowToPlayerIfOnline(id, maskId, 2)));
	}

	public void cancelRegistration(Player player, int maskId) {
		cancelRegistration(getSearchEntry(player, maskId), player, maskId);
	}

	public void cancelRegistration(LookingForParty lfp, Player player, int maskId) {
		int objectId = player.getObjectId();
		if (lfp != null) {
			if (lfp.isLeader(objectId)) {
				lookingParties.get(maskId).remove(lfp);
				penaliseParty(lfp);
				lfp.getMembers().keySet().forEach(id -> AutoGroupUtility.sendWindowToPlayerIfOnline(id, maskId, 2));
			} else {
				lfp.unregisterMember(objectId);
				AutoGroupUtility.sendWindowToPlayer(player, maskId, 2);
				penalisePlayerAndScheduleRemoval(objectId);
				checkQueueForNewMatches(maskId);
			}
		}
	}

	private void destroyOrAddPlayersFromQuickEntries(AutoInstance autoInstance) {
		if (!destroyIfPossible(autoInstance) && autoInstance.getAutoGroupType().getTemplate().canRegisterQuickEntry())
			checkQueueForQuickEntries(autoInstance);
	}

	public boolean destroyIfPossible(AutoInstance autoInstance) {
		WorldMapInstance instance = autoInstance.getInstance();
		if (autoInstance.getRegisteredAGPlayers().isEmpty() && instance.getPlayersInside().stream().noneMatch(Player::isOnline)) {
			autoInstances.remove(instance);
			InstanceService.destroyInstance(instance);
			return true;
		}
		return false;
	}

	private AutoInstance getAutoInstance(Player player, int instanceMaskId) {
		for (AutoInstance autoInstance : autoInstances.values())
			if (autoInstance.getAutoGroupType().getTemplate().getMaskId() == instanceMaskId
				&& autoInstance.getRegisteredAGPlayers().containsKey(player.getObjectId()))
				return autoInstance;
		return null;
	}

	public boolean isInAutoInstance(Player player) {
		return autoInstances.containsKey(player.getWorldMapInstance());
	}

	/** Native queue custody, including a party entry, before a background bot rebuild. */
	public boolean isLookingForInstance(Player player) {
		for (var searches : lookingParties.values()) {
			synchronized (searches) {
				if (getSearchEntry(player.getObjectId(), searches) != null)
					return true;
			}
		}
		return autoInstances.values().stream().anyMatch(instance -> instance.getRegisteredAGPlayers().containsKey(player.getObjectId()));
	}

	public int populationInvitationMask(Player player) {
		return autoInstances.values().stream().filter(instance -> instance.getInstance() != player.getWorldMapInstance()
			&& instance.getRegisteredAGPlayers().containsKey(player.getObjectId()))
			.mapToInt(instance -> instance.getAutoGroupType().getTemplate().getMaskId()).findFirst().orElse(0);
	}

	public int populationQueuedMask(Player player) {
		if (!generatedPopulationBot(player))
			return 0;
		return getSearchEntries(player.getObjectId()).stream()
			.filter(search -> search.getMembers().size() == 1 && !search.isOnStartEnterTask())
			.mapToInt(LookingForParty::getMaskId).findFirst().orElse(0);
	}

	/** Explicit feature disable; never cancels a matched invitation or a human party. */
	public boolean leavePopulationQueue(Player player, int maskId) {
		if (!generatedPopulationBot(player))
			return false;
		var searches = lookingParties.get(maskId);
		if (searches == null)
			return false;
		synchronized (searches) {
			var search = getSearchEntry(player, maskId);
			if (search == null || search.getMembers().size() != 1 || search.isOnStartEnterTask())
				return false;
			cancelRegistration(search, player, maskId);
			return true;
		}
	}

	private static boolean generatedPopulationBot(Player player) {
		return player.isWorldBot() && player.getAccount().getId() <= -1000000 && player.getAccount().getId() >= -1023999;
	}

	/** Same entry checks and matching path as a human request; no synthetic client packets. */
	public boolean joinPopulationQueue(Player player, int maskId) {
		if (!AutoGroupConfig.AUTO_GROUP_ENABLE || !generatedPopulationBot(player)
			|| player.isInTeam() || player.isInInstance() || isLookingForInstance(player))
			return false;
		var type = AutoGroupType.getAGTByMaskId(maskId);
		if (type == null || type.isPeriodicInstance() && !PeriodicInstanceManager.getInstance().isRegistrationOpen(maskId))
			return false;
		// A human demand must still exist after queue-census planning.
		var demand = populationQueues().stream().filter(queue -> queue.maskId() == maskId && queue.humanDemand()).findFirst().orElse(null);
		if (demand == null)
			return false;
		boolean open = player.getRace() == com.aionemu.gameserver.model.Race.ELYOS ? demand.elyosOpenSeats() > 0 : demand.asmodianOpenSeats() > 0;
		var request = open && type.getTemplate().canRegisterQuickEntry() || !type.getTemplate().canRegisterNewEntry()
			? EntryRequestType.QUICK_GROUP_ENTRY : EntryRequestType.NEW_GROUP_ENTRY;
		startLooking(player, maskId, request);
		return isLookingForInstance(player);
	}

	/** Native counterpart of population CheckBgQueue/CheckLfgQueue; no invented WoW queue IDs. */
	public record PopulationQueue(int maskId, int mapId, int minimumLevel, int maximumLevel,
		int elyosPlayers, int asmodianPlayers, int elyosBots, int asmodianBots, int instances,
		int instanceHumans, int elyosOpenSeats, int asmodianOpenSeats) {
		public boolean humanDemand() { return elyosPlayers + asmodianPlayers + instanceHumans > 0; }
	}

	public List<PopulationQueue> populationQueues() {
		var rows = new TreeMap<Integer, int[]>();
		for (var pair : lookingParties.entrySet()) {
			var type = AutoGroupType.getAGTByMaskId(pair.getKey());
			if (type == null)
				continue;
			var counts = rows.computeIfAbsent(pair.getKey(), ignored -> new int[8]);
			var seen = new HashSet<Integer>();
			synchronized (pair.getValue()) {
				for (var search : pair.getValue())
					for (var member : search.getMembers().values()) {
						var player = com.aionemu.gameserver.world.World.getInstance().getPlayer(member.objectId());
						if (player == null || !player.isSpawned() || !seen.add(member.objectId()))
							continue;
						int faction = player.getRace() == com.aionemu.gameserver.model.Race.ELYOS ? 0 : 1;
						counts[faction + (player.isPlayerBot() ? 2 : 0)]++;
					}
			}
		}
		for (var instance : autoInstances.values()) {
			var type = instance.getAutoGroupType(); int mask = type.getTemplate().getMaskId();
			var counts = rows.computeIfAbsent(mask, ignored -> new int[8]); counts[4]++;
			var members = List.copyOf(instance.getRegisteredAGPlayers().values());
			int humans = (int) members.stream().filter(member -> {
				var player = com.aionemu.gameserver.world.World.getInstance().getPlayer(member.objectId());
				return player != null && player.isOnline() && !player.isPlayerBot();
			}).count(); counts[5] += humans;
			var score = instance.getInstance().getInstanceHandler().getInstanceScore();
			if (humans == 0 || !type.getTemplate().canRegisterQuickEntry()
				|| System.currentTimeMillis() - instance.getStartInstanceTime() > type.getMaximumJoinTime()
				|| score != null && score.getInstanceProgressionType() == com.aionemu.gameserver.model.instance.InstanceProgressionType.END_PROGRESS)
				continue;
			for (var race : List.of(com.aionemu.gameserver.model.Race.ELYOS, com.aionemu.gameserver.model.Race.ASMODIANS)) {
				int maximum = com.aionemu.gameserver.dataholders.DataManager.INSTANCE_COOLTIME_DATA.getMaxMemberCount(type.getTemplate().getInstanceMapId(), race);
				int occupied = (int) members.stream().filter(member -> member.race() == race).count();
				counts[race == com.aionemu.gameserver.model.Race.ELYOS ? 6 : 7] += Math.max(0, maximum - occupied);
			}
		}
		var result = new ArrayList<PopulationQueue>();
		for (var row : rows.entrySet()) {
			var type = AutoGroupType.getAGTByMaskId(row.getKey());
			if (type == null)
				continue;
			var template = type.getTemplate(); var counts = row.getValue();
			result.add(new PopulationQueue(row.getKey(), template.getInstanceMapId(), template.getMinLvl(), template.getMaxLvl(),
				counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], counts[7]));
		}
		return List.copyOf(result);
	}

	public static AutoGroupService getInstance() {
		return NewSingletonHolder.INSTANCE;
	}

	private static class NewSingletonHolder {

		private static final AutoGroupService INSTANCE = new AutoGroupService();
	}

}
