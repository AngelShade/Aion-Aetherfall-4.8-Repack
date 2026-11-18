package com.aionemu.gameserver.services.dynamicevents;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.stats.calc.Stat2;
import com.aionemu.gameserver.model.stats.calc.StatOwner;
import com.aionemu.gameserver.model.stats.calc.functions.StatFunction;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.stats.CalculationType;
import com.aionemu.gameserver.world.geo.GeoService;

/** Native monsters of the event's map: raiders, lieutenants and a leader of one tribe, placed on native ground. */
public final class EventSpawns {

	/** What one event spawns. camp is a native hostile spawn spot near the road; campSpots are native spots around it. */
	public record Pool(List<NpcTemplate> normal, List<NpcTemplate> elite, NpcTemplate boss, DynamicEvent.Spot camp, List<DynamicEvent.Spot> campSpots,
		String enemy) {
	}

	record Candidate(NpcTemplate template, DynamicEvent.Spot spot) {
	}

	/** Level window around the beat: raiders slightly below to slightly above it. */
	static boolean levelFits(int beatLevel, int npcLevel, int below, int above) {
		return npcLevel >= beatLevel - below && npcLevel <= beatLevel + above;
	}

	static int ratingRank(NpcRating rating) {
		return switch (rating) {
			case LEGENDARY -> 4;
			case HERO -> 3;
			case ELITE -> 2;
			default -> 1;
		};
	}

	/** The leader: the strongest rating, then the highest level, then the lowest template id (stable choice). */
	static NpcTemplate leader(List<NpcTemplate> templates) {
		return templates.stream().max(Comparator.<NpcTemplate>comparingInt(t -> ratingRank(t.getRating())).thenComparingInt(NpcTemplate::getLevel)
			.thenComparing(Comparator.comparingInt(NpcTemplate::getTemplateId).reversed())).orElse(null);
	}

	/** Builds the pool from native spawn data; null when the map has no fitting monsters near the road. Runs off the actor tick. */
	static Pool pool(DynamicEvent.Anchor anchor) {
		var tribe = anchor.race() == Race.ELYOS ? TribeClass.PC : TribeClass.PC_DARK;
		var near = new ArrayList<Candidate>();
		var start = anchor.start();
		for (var group : DataManager.SPAWNS_DATA.getSpawnsByWorldId(anchor.map())) {
			NpcTemplate n = DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
			if (n == null || group.isTemporarySpawn() || group.getHandlerType() != null || n.getRating() == NpcRating.JUNK
				|| !DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(), tribe) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(), tribe))
				continue;
			for (var spawn : group.getSpawnTemplates()) {
				if (spawn.isAerialSpawn())
					continue;
				double d = start.distance(spawn.getX(), spawn.getY(), spawn.getZ());
				if (d <= 400)
					near.add(new Candidate(n, new DynamicEvent.Spot(spawn.getX(), spawn.getY(), spawn.getZ())));
			}
		}
		// The camp: the nearest native elite 30-100 m from the Warden, else the nearest native hostile spot in that ring.
		Candidate camp = near.stream().filter(c -> ring(start, c.spot()) && ratingRank(c.template().getRating()) >= 2)
			.min(Comparator.comparingDouble(c -> start.distance(c.spot().x(), c.spot().y(), c.spot().z())))
			.orElseGet(() -> near.stream().filter(c -> ring(start, c.spot()))
				.min(Comparator.comparingDouble(c -> start.distance(c.spot().x(), c.spot().y(), c.spot().z()))).orElse(null));
		if (camp == null)
			return null;
		TribeClass enemyTribe = camp.template().getTribe();
		var tribal = near.stream().filter(c -> c.template().getTribe() == enemyTribe).toList();
		var source = tribal.stream().anyMatch(c -> c.template().getRating() == NpcRating.NORMAL && levelFits(anchor.level(), c.template().getLevel(), 3, 2)) ? tribal : near;
		var normal = distinct(source, NpcRating.NORMAL, anchor.level(), 3, 2);
		if (normal.isEmpty())
			return null;
		var elite = distinct(source, NpcRating.ELITE, anchor.level(), 3, 3);
		if (elite.isEmpty())
			elite = distinct(near, NpcRating.ELITE, anchor.level(), 4, 4);
		if (elite.isEmpty())
			elite = normal;
		// The leader: a Hero or Legendary of the camp's tribe on this map near the level band, then one of any hostile tribe
		// there, else the strongest lieutenant (which the engine then raises to boss strength).
		var tribeLeaders = new ArrayList<NpcTemplate>();
		var anyLeaders = new ArrayList<NpcTemplate>();
		for (var group : DataManager.SPAWNS_DATA.getSpawnsByWorldId(anchor.map())) {
			NpcTemplate n = DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
			if (n == null || ratingRank(n.getRating()) < 3 || !levelFits(anchor.level(), n.getLevel(), 2, 5) || group.getHandlerType() != null || group.isTemporarySpawn()
				|| !DataManager.TRIBE_RELATIONS_DATA.isHostileRelation(n.getTribe(), tribe) && !DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(n.getTribe(), tribe))
				continue;
			(n.getTribe() == enemyTribe ? tribeLeaders : anyLeaders).add(n);
		}
		NpcTemplate boss = !tribeLeaders.isEmpty() ? leader(tribeLeaders) : !anyLeaders.isEmpty() ? leader(anyLeaders) : leader(elite);
		var campSpots = near.stream().map(Candidate::spot).filter(s -> camp.spot().distance(s.x(), s.y(), s.z()) <= 40).distinct().limit(24).toList();
		return new Pool(normal, elite, boss, camp.spot(), campSpots.isEmpty() ? List.of(camp.spot()) : campSpots, normal.getFirst().getName());
	}

	private static boolean ring(DynamicEvent.Spot start, DynamicEvent.Spot spot) {
		double d = start.distance(spot.x(), spot.y(), spot.z());
		return d >= 30 && d <= 100 && Math.abs(spot.z() - start.z()) <= 25;
	}

	private static List<NpcTemplate> distinct(List<Candidate> from, NpcRating rating, int level, int below, int above) {
		var seen = new LinkedHashMap<Integer, NpcTemplate>();
		for (var c : from)
			if (c.template().getRating() == rating && levelFits(level, c.template().getLevel(), below, above))
				seen.putIfAbsent(c.template().getTemplateId(), c.template());
		return List.copyOf(seen.values());
	}

	/** Spawns one native monster facing the target, on ground within 2 m of the spot's height; null when there is no ground. */
	static Npc spawn(DynamicEvent.Anchor anchor, NpcTemplate template, DynamicEvent.Spot near, float spread, Creature target) {
		var random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 4; attempt++) {
			double angle = random.nextDouble(Math.PI * 2), radius = spread <= 0 ? 0 : random.nextDouble(spread * 0.4, spread);
			float x = (float) (near.x() + Math.cos(angle) * radius), y = (float) (near.y() + Math.sin(angle) * radius);
			float z = spread <= 0 ? near.z() : GeoService.getInstance().getZ(anchor.map(), x, y, near.z() + 4, near.z() - 6, anchor.instance());
			if (Float.isNaN(z))
				continue;
			byte heading = target == null ? 0 : PositionUtil.getHeadingTowards(x, y, target.getX(), target.getY());
			var spawned = SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(anchor.map(), template.getTemplateId(), x, y, z, heading), anchor.instance());
			if (spawned instanceof Npc npc) {
				if (target != null && !target.isDead())
					npc.getAggroList().addHate(target, 100);
				return npc;
			}
		}
		return null;
	}

	/** A leader below Hero is raised to boss strength: health and damage multipliers by native rating. */
	static float leaderHealth(NpcRating rating) {
		return switch (rating) {
			case NORMAL, JUNK -> 6f;
			case ELITE -> 3f;
			default -> 1f;
		};
	}

	static float leaderDamage(NpcRating rating) {
		return switch (rating) {
			case NORMAL, JUNK -> 1.5f;
			case ELITE -> 1.25f;
			default -> 1f;
		};
	}

	/** A lieutenant drawn from normal monsters (maps without fitting Elites) is made tougher than its raiders. */
	static float lieutenantHealth(NpcRating rating) {
		return rating == NpcRating.NORMAL || rating == NpcRating.JUNK ? 2.5f : 1f;
	}

	static float lieutenantDamage(NpcRating rating) {
		return rating == NpcRating.NORMAL || rating == NpcRating.JUNK ? 1.25f : 1f;
	}

	/** Boss health from event scaling, on top of the native group scaling. */
	static float bossHealth(double participants, int perParticipantPercent, int maxPercent) {
		float multiplier = (float) (1 + Math.max(0, participants - 1) * perParticipantPercent / 100d);
		return Math.clamp(multiplier, 1f, Math.max(100, maxPercent) / 100f);
	}

	/** Event strength on one monster. health can change while it fights (live scaling); the stat functions read it on each calculation. */
	static final class Scaling implements StatOwner {
		volatile float health;
		final float damage;

		Scaling(float health, float damage) {
			this.health = health;
			this.damage = damage;
		}
	}

	static Scaling scale(Npc npc, float health, float damage) {
		var scaling = new Scaling(Math.max(1f, health), Math.max(1f, damage));
		npc.getGameStats().addEffect(scaling, List.of(new BossHealth(scaling), new BossDamage(scaling)));
		npc.getLifeStats().synchronizeWithMaxStats();
		return scaling;
	}

	/** Live scaling, as WorldMobScaler updates a fighting actor: the health fraction is kept and nearby clients see the new bar. */
	static boolean rescale(Npc npc, Scaling scaling, float health) {
		float next = Math.max(1f, health);
		if (scaling == null || Math.abs(next - scaling.health) <= scaling.health * 0.05f)
			return false;
		scaling.health = next;
		npc.getLifeStats().onStatsChange(null);
		npc.getKnownList().forEachPlayer(p -> {
			if (!p.isPlayerBot() && p.getKnownList().sees(npc))
				com.aionemu.gameserver.utils.PacketSendUtility.sendPacket(p, new com.aionemu.gameserver.network.aion.serverpackets.SM_NPC_STATUS_UPDATE(npc));
		});
		return true;
	}

	/** Chance that an arriving raider is an Elite: none below the threshold, then 10% per participant from it, at most half. */
	static double eliteChance(double participants, int from) {
		return participants < Math.max(1, from) ? 0 : Math.min(0.5, (Math.floor(participants) - from + 1) * 0.1);
	}

	/**
	 * After a won chain a merchant comes out to the defenders, as Guild Wars 2 NPCs open temporary shops: the native friendly
	 * merchant of this faction (one with a native trade list) nearest to the event, spawned at its spot. Null when the map has none.
	 */
	static Npc merchant(DynamicEvent.Anchor anchor, DynamicEvent.Spot at) {
		NpcTemplate best = merchantTemplate(anchor, at);
		return best == null ? null : spawn(anchor, best, at, 3, null);
	}

	/** The merchant the stall would bring: native data only, no spawn. */
	static NpcTemplate merchantTemplate(DynamicEvent.Anchor anchor, DynamicEvent.Spot at) {
		NpcTemplate best = null;
		double bestDistance = Double.MAX_VALUE;
		var friendly = anchor.race() == Race.ELYOS ? Race.ELYOS : Race.ASMODIANS;
		for (var group : DataManager.SPAWNS_DATA.getSpawnsByWorldId(anchor.map())) {
			NpcTemplate n = DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());
			if (n == null || group.isTemporarySpawn() || group.getHandlerType() != null || n.getRace() != friendly && n.getRace() != Race.PC_ALL
				|| DataManager.TRADE_LIST_DATA.getTradeListTemplate(n.getTemplateId()) == null)
				continue;
			for (var spawn : group.getSpawnTemplates()) {
				double d = at.distance(spawn.getX(), spawn.getY(), spawn.getZ());
				if (!spawn.isAerialSpawn() && d < bestDistance) {
					bestDistance = d;
					best = n;
				}
			}
		}
		return best;
	}

	private static final class BossHealth extends StatFunction {
		private final Scaling scaling;

		BossHealth(Scaling scaling) {
			this.scaling = scaling;
			stat = StatEnum.MAXHP;
		}

		@Override
		public void apply(Stat2 stat, Set<CalculationType> calculationTypes) {
			float multiplier = scaling.health;
			stat.setBaseRate(stat.getBaseRate() * multiplier);
			stat.setBonusRate(stat.getBonusRate() * multiplier);
			stat.setFixedBonusRate(stat.getFixedBonusRate() * multiplier);
		}

		@Override
		public int getPriority() {
			return 121;
		}
	}

	private static final class BossDamage extends StatFunction {
		private final Scaling scaling;

		BossDamage(Scaling scaling) {
			this.scaling = scaling;
			stat = StatEnum.PVE_ATTACK_RATIO;
		}

		@Override
		public void apply(Stat2 stat, Set<CalculationType> calculationTypes) {
			// Native ratio is per mille, as in WorldMobScaler; defences and other bonuses stay native.
			stat.addToBonus((scaling.damage - 1f) * 1000f);
		}

		@Override
		public int getPriority() {
			return 121;
		}
	}

	private EventSpawns() {
	}
}
