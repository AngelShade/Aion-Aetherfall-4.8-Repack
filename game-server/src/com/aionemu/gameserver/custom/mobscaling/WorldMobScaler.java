package com.aionemu.gameserver.custom.mobscaling;

import static com.aionemu.gameserver.configs.main.MobScalingConfig.*;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.SummonedObject;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.calc.Stat2;
import com.aionemu.gameserver.model.stats.calc.AdditionStat;
import com.aionemu.gameserver.model.stats.calc.StatCapUtil;
import com.aionemu.gameserver.model.stats.calc.StatOwner;
import com.aionemu.gameserver.model.stats.calc.functions.StatFunction;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.templates.npc.AbyssNpcType;
import com.aionemu.gameserver.model.templates.npc.NpcTemplateType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_NPC_STATUS_UPDATE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.stats.CalculationType;
import com.aionemu.gameserver.world.MapRegion;
import com.aionemu.gameserver.world.WorldMapInstance;

/** Isolated difficulty modifiers attached through the existing NPC lifecycle. */
public final class WorldMobScaler {

	private static final ConcurrentMap<Integer, Scaling> scalings = new ConcurrentHashMap<>();
	private static final Set<MapRegion> pendingRegions = ConcurrentHashMap.newKeySet();

	public static void onBeforeSpawn(Npc npc) {
		onDespawn(npc);
		if (!isEligible(npc))
			return;
		Scaling scaling = new Scaling();
		if (GROUP_ENABLED && npc.getPosition().getMapRegion().isActive())
			scaling.update(nearbyMembers(npc, nearbyPlayers(npc.getPosition().getMapRegion())));
		scalings.put(npc.getObjectId(), scaling);
		npc.getGameStats().addEffect(scaling, List.of(new HealthFunction(scaling), new DamageFunction(scaling)));
	}

	/** Native movement/spawn/removal events dirty only the affected spatial regions. */
	public static void onWorldChange(VisibleObject object, MapRegion previousRegion) {
		if (!ENABLED || !GROUP_ENABLED)
			return;
		if (object instanceof Player player) {
			requestAround(previousRegion);
			if (player.isSpawned())
				requestAround(player.getPosition().getMapRegion());
		} else if (object instanceof Npc npc && scalings.containsKey(npc.getObjectId()) && npc.isSpawned()) {
			requestRegion(npc.getPosition().getMapRegion());
		}
	}

	/** Also used for stationary joins/leaves and life-state transitions, including headless bots. */
	public static void onPlayerChanged(Player player) {
		if (ENABLED && GROUP_ENABLED && player.isSpawned())
			requestAround(player.getPosition().getMapRegion());
	}

	private static void requestAround(MapRegion region) {
		for (MapRegion nearby : nearbyRegions(region))
			requestRegion(nearby);
	}

	private static void requestRegion(MapRegion region) {
		if (region == null || !region.isActive() || region.getParent().getParent().isInstanceType()
			|| EXCLUDED_MAPS.contains(region.getParent().getMapId()) || !pendingRegions.add(region))
			return;
		ThreadPoolManager.getInstance().schedule(() -> {
			pendingRegions.remove(region);
			updateRegion(region);
		}, GROUP_UPDATE_INTERVAL);
	}

	/** One coalesced pass per dirty region; no world, instance-population or all-scaling-state scan. */
	static void updateRegion(MapRegion region) {
		List<Player> players = nearbyPlayers(region);
		for (VisibleObject object : region.getObjects().values()) {
			if (object instanceof Npc npc && npc.isSpawned() && !npc.isDead() && npc.getPosition().getMapRegion() == region) {
				Scaling scaling = scalings.get(npc.getObjectId());
				if (scaling != null)
					updateMembers(npc, scaling, nearbyMembers(npc, players), false);
			}
		}
	}

	static Set<MapRegion> nearbyRegions(MapRegion region) {
		if (region == null || region.getParent().getParent().isInstanceType())
			return Set.of();
		Set<MapRegion> result = new HashSet<>();
		result.add(region);
		Set<MapRegion> edge = Set.of(region);
		int steps = (int) Math.ceil(MEMBER_RADIUS / WorldMapInstance.regionSize);
		for (int i = 0; i < steps; i++) {
			Set<MapRegion> next = new HashSet<>();
			for (MapRegion current : edge) {
				for (MapRegion neighbour : current.getNeighbours()) {
					if (result.add(neighbour))
						next.add(neighbour);
				}
			}
			edge = next;
		}
		return result;
	}

	private static List<Player> nearbyPlayers(MapRegion region) {
		List<Player> players = new ArrayList<>();
		for (MapRegion nearby : nearbyRegions(region)) {
			for (VisibleObject object : nearby.getObjects().values()) {
				if (object instanceof Player player && player.isPlaying() && player.isSpawned() && !player.isDead())
					players.add(player);
			}
		}
		return players;
	}

	static int nearbyMembers(Npc npc, List<Player> players) {
		int members = 1;
		Set<Object> teams = new HashSet<>();
		for (Player player : players) {
			if (player.isPlaying() && player.isSpawned() && !player.isDead()
				&& player.getWorldMapInstance() == npc.getWorldMapInstance() && PositionUtil.isInRange(npc, player, MEMBER_RADIUS)) {
				var team = player.getCurrentTeam();
				if (team != null && teams.add(team)) {
					members = Math.max(members, countMembers(npc, player));
					if (members >= MAX_MEMBERS)
						break;
				}
			}
		}
		return members;
	}

	public static boolean isEligible(Npc npc) {
		if (!ENABLED || npc instanceof SummonedObject<?> || npc.getWorldMapInstance() == null
			|| npc.getWorldMapInstance().getParent().isInstanceType() || EXCLUDED_MAPS.contains(npc.getWorldId())
			|| EXCLUDED_NPCS.contains(npc.getNpcId()))
			return false;
		NpcTemplateType type = npc.getObjectTemplate().getNpcTemplateType();
		return (type == NpcTemplateType.MONSTER || type == NpcTemplateType.RAID_MONSTER || isLegacyMonster(npc, type))
			&& npc.getAbyssNpcType() == AbyssNpcType.NONE && RATINGS.contains(npc.getRating());
	}

	/** Older native templates omit type; their tribe still supplies the monster classification. */
	private static boolean isLegacyMonster(Npc npc, NpcTemplateType type) {
		if (type != NpcTemplateType.NONE || npc.getBaseTribe() != TribeClass.MONSTER
			|| npc.getObjectTemplate().getFuncDialogIds() != null && !npc.getObjectTemplate().getFuncDialogIds().isEmpty())
			return false;
		TribeClass tribe = npc.getTribe();
		return !DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(tribe, TribeClass.PC)
			&& !DataManager.TRIBE_RELATIONS_DATA.isFriendlyRelation(tribe, TribeClass.PC_DARK);
	}

	/** Native hate notification occurs before HP loss, including damage-over-time ticks. */
	public static void onAddHate(Npc npc, Creature attacker) {
		Scaling scaling = scalings.get(npc.getObjectId());
		if (scaling == null || !GROUP_ENABLED || npc.isDead() || !(attacker.getMaster() instanceof Player player))
			return;
		synchronized (scaling) {
			long now = System.nanoTime();
			if (now < scaling.nextGroupCheck || scaling.members >= MAX_MEMBERS)
				return;
			scaling.nextGroupCheck = now + 1_000_000_000L;
			int members = countMembers(npc, player);
			if (members <= scaling.members)
				return;
			updateMembers(npc, scaling, members, true);
		}
	}

	private static void updateMembers(Npc npc, Scaling scaling, int members, boolean retainHighWater) {
		synchronized (scaling) {
			if (members == scaling.members || members < scaling.members
				&& (retainHighWater || npc.getAggroList().stream().anyMatch(info -> info.getHate() > 0)))
				return;
			scaling.update(members);
			// Update this live actor in place, preserving its current health fraction.
			npc.getLifeStats().onStatsChange(null);
		}
		refreshTarget(npc);
	}

	static int countMembers(Npc npc, Player player) {
		var team = player.getCurrentTeam();
		if (team == null)
			return 1;
		int members = 0;
		for (Player member : team.getMembers()) {
			if (member.isPlaying() && member.isSpawned() && !member.isDead()
				&& member.getWorldMapInstance() == npc.getWorldMapInstance() && PositionUtil.isInRange(npc, member, MEMBER_RADIUS)
				&& ++members >= MAX_MEMBERS)
				break;
		}
		return Math.max(1, members);
	}

	/** A reset uses the current local roster; departures cannot weaken an active fight. */
	public static void onReset(Npc npc) {
		Scaling scaling = scalings.get(npc.getObjectId());
		if (scaling != null) {
			synchronized (scaling) {
				scaling.nextGroupCheck = 0;
			}
			updateMembers(npc, scaling, nearbyMembers(npc, nearbyPlayers(npc.getPosition().getMapRegion())), false);
		}
	}

	private static void refreshTarget(Npc npc) {
		if (!npc.isSpawned())
			return;
		// S_CHANGE_NPC_STATUS mutates the existing client actor's HP; no put/delete/target change.
		npc.getKnownList().forEachPlayer(player -> {
			if (player.isPlayerBot() || !player.getKnownList().sees(npc))
				return;
			PacketSendUtility.sendPacket(player, new SM_NPC_STATUS_UPDATE(npc));
		});
	}

	public static void onDespawn(Npc npc) {
		Scaling scaling = scalings.remove(npc.getObjectId());
		if (scaling != null)
			npc.getGameStats().endEffect(scaling);
	}

	/** XP uses max HP in the native server; difficulty must not create extra rewards. */
	public static int getRewardMaxHp(Npc npc) {
		Scaling scaling = scalings.get(npc.getObjectId());
		if (scaling == null)
			return npc.getGameStats().getMaxHp().getCurrent();
		// Read the normal HP modifiers without this feature's owner; never detach live effects.
		Stat2 stat = new AdditionStat(StatEnum.MAXHP, npc.getObjectTemplate().getStatsTemplate().getMaxHp(), npc);
		for (var function : npc.getGameStats().getStatsSorted(StatEnum.MAXHP)) {
			if (function.getOwner() != scaling && function.validate(stat))
				function.apply(stat, Set.of());
		}
		StatCapUtil.calculateBaseValue(stat, npc);
		npc.getAi().modifyOwnerStat(stat);
		return stat.getCurrent();
	}

	static final class Scaling implements StatOwner {
		private int members;
		private long nextGroupCheck;
		private volatile float hpMultiplier;
		private volatile float damageMultiplier;

		Scaling() {
			update(1);
		}

		void update(int members) {
			this.members = members;
			hpMultiplier = multiplier(HP_MULTIPLIER, HP_PER_MEMBER, MAX_HP_MULTIPLIER, members);
			damageMultiplier = multiplier(DAMAGE_MULTIPLIER, DAMAGE_PER_MEMBER, MAX_DAMAGE_MULTIPLIER, members);
		}
	}

	static float multiplier(float baseline, float perMember, float cap, int members) {
		return (float) Math.min(cap, baseline * (1d + perMember * (Math.clamp(members, 1, MAX_MEMBERS) - 1)));
	}

	private static final class HealthFunction extends StatFunction {
		private final Scaling scaling;

		HealthFunction(Scaling scaling) {
			this.scaling = scaling;
			stat = StatEnum.MAXHP;
		}

		@Override
		public void apply(Stat2 stat, Set<CalculationType> calculationTypes) {
			float multiplier = scaling.hpMultiplier;
			stat.setBaseRate(stat.getBaseRate() * multiplier);
			stat.setBonusRate(stat.getBonusRate() * multiplier);
			stat.setFixedBonusRate(stat.getFixedBonusRate() * multiplier);
		}

		@Override
		public int getPriority() {
			return 120;
		}
	}

	private static final class DamageFunction extends StatFunction {
		private final Scaling scaling;

		DamageFunction(Scaling scaling) {
			this.scaling = scaling;
			stat = StatEnum.PVE_ATTACK_RATIO;
		}

		@Override
		public void apply(Stat2 stat, Set<CalculationType> calculationTypes) {
			// Native ratio is per mille. Normal defenses, other bonuses and template exemptions remain native.
			stat.addToBonus((scaling.damageMultiplier - 1f) * 1000f);
		}

		@Override
		public int getPriority() {
			return 120;
		}
	}

	private WorldMobScaler() {
	}
}
