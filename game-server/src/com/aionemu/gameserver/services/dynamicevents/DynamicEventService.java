package com.aionemu.gameserver.services.dynamicevents;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.DynamicEventConfig;
import com.aionemu.gameserver.dao.DynamicEventDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.CreatureType;
import com.aionemu.gameserver.model.EmotionId;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;

/**
 * Runs dynamic event chains the way Guild Wars 2 does (config/dynamic-events/README.md): once started an event develops
 * whether or not anyone attends, its outcome changes the road, it rescales as people come and go, and everyone who helps
 * earns credit, loot and a tier reward. One timer ticks every running event once a second; monster deaths only record
 * credit, and every stage change happens in the tick under the event's monitor.
 */
public final class DynamicEventService {

	private static final Logger log = LoggerFactory.getLogger(DynamicEventService.class);
	private static final long TICK_MS = 1000, RESCALE_MS = 10_000, FULL_CREDIT_MS = 30_000;
	/** Share of a monster's health (damage or support) a participant needs for personal loot from it. */
	static final double LOOT_SHARE = 5;
	private static final Map<Long, DynamicEvent> RUNNING = new ConcurrentHashMap<>();
	private static final Map<String, Long> BEAT_READY = new ConcurrentHashMap<>();
	private static final Map<Integer, DynamicEvent> BY_MONSTER = new ConcurrentHashMap<>();
	private static final Map<Integer, DynamicEvent> BY_BOT = new ConcurrentHashMap<>();
	/** Event monsters that just died: NpcController.doReward pays every attacker the whole kill (Guild Wars 2: no kill stealing). */
	private static final Map<Integer, Long> FULL_CREDIT = new ConcurrentHashMap<>();
	private static final Map<String, String> SCHEDULED = new ConcurrentHashMap<>();
	private static final Set<String> STARTING = ConcurrentHashMap.newKeySet();
	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final AtomicLong STARTED = new AtomicLong(), WON = new AtomicLong(), LOST = new AtomicLong();
	private static volatile EventBots bots = EventBots.NONE;
	private static volatile boolean ticking;

	public static synchronized void initialize() {
		EventCatalogue.load();
		try (var c = DatabaseFactory.getConnection()) {
			DynamicEventDAO.prepare(c);
		} catch (java.sql.SQLException e) {
			log.error("Dynamic event chest storage unavailable; chests will retry when claimed", e);
		}
		if (!ticking) {
			ticking = true;
			ThreadPoolManager.getInstance().scheduleAtFixedRate(DynamicEventService::tick, TICK_MS, TICK_MS);
		}
		if (!DynamicEventConfig.ENABLED)
			log.info("Dynamic events are switched off (gameserver.dynamicevents.enable)");
	}

	public static void bots(EventBots provider) {
		bots = provider == null ? EventBots.NONE : provider;
	}

	public static String summary() {
		return "dynamic events: running=" + RUNNING.size() + ", started=" + STARTED.get() + ", won=" + WON.get() + ", lost=" + LOST.get() + ", roads held="
			+ EventWorld.count();
	}

	// ----- queries for the bot bridge and the native hooks -----

	public static DynamicEvent eventOf(Player bot) {
		DynamicEvent e = bot == null ? null : BY_BOT.get(bot.getObjectId());
		return e == null || e.finished() ? null : e;
	}

	public static DynamicEvent eventOf(Npc monster) {
		DynamicEvent e = monster == null ? null : BY_MONSTER.get(monster.getObjectId());
		return e == null || e.finished() ? null : e;
	}

	public static boolean warden(Player bot) {
		DynamicEvent e = eventOf(bot);
		return e != null && e.anchor.warden() == bot.getObjectId();
	}

	/** For NpcController.doReward: true once for an event monster that just died; every attacker then earns the whole kill. */
	public static boolean fullCredit(Npc npc) {
		Long until = FULL_CREDIT.remove(npc.getObjectId());
		return until != null && until >= System.currentTimeMillis();
	}

	// ----- trigger -----

	/** Weighted choice among chains with this trigger whose level band holds the level; roll in [0, total weight). */
	static EventChain pick(List<EventChain> chains, int level, int roll, String trigger) {
		var fitting = chains.stream().filter(c -> c.trigger.equals(trigger) && c.levels(level)).toList();
		int total = fitting.stream().mapToInt(c -> c.weight).sum();
		if (total <= 0)
			return null;
		int at = Math.floorMod(roll, total);
		for (var c : fitting) {
			if (at < c.weight)
				return c;
			at -= c.weight;
		}
		return null;
	}

	/** Limits a new event must pass: switch, beat cooldown, server and map caps, Warden not already in an event. */
	static boolean admitted(boolean enabled, long now, long beatReady, int running, int maxRunning, int onMap, int maxPerMap, boolean wardenBusy) {
		return enabled && now >= beatReady && running < maxRunning && onMap < maxPerMap && !wardenBusy;
	}

	private static boolean admitted(DynamicEvent.Anchor anchor, long now, boolean ignoreBeat) {
		int onMap = (int) RUNNING.values().stream().filter(e -> e.anchor.map() == anchor.map()).count();
		return admitted(DynamicEventConfig.ENABLED, now, ignoreBeat ? 0 : BEAT_READY.getOrDefault(anchor.beat(), 0L), RUNNING.size() + STARTING.size(),
			DynamicEventConfig.MAX_RUNNING, onMap, DynamicEventConfig.MAX_PER_MAP, BY_BOT.containsKey(anchor.warden()));
	}

	/** A Warden asks for help. Cheap checks run here; building the monster pool runs on the general pool. */
	public static boolean offer(DynamicEvent.Anchor anchor) {
		if (EventWorld.held(anchor.beat()) || !admitted(anchor, System.currentTimeMillis(), false))
			return false;
		EventChain chain = pick(EventCatalogue.chains(), anchor.level(), ThreadLocalRandom.current().nextInt(1 << 20), EventChain.WARDEN);
		return chain != null && launch(anchor, chain, null);
	}

	/** A player came near a held road: its retake starts with the guards as the first monsters. */
	static boolean retake(EventWorld.Hold hold) {
		if (!DynamicEventConfig.ENABLED || BY_BOT.containsKey(hold.anchor.warden()) || RUNNING.size() + STARTING.size() >= DynamicEventConfig.MAX_RUNNING)
			return false;
		EventChain chain = pick(EventCatalogue.chains(), hold.anchor.level(), ThreadLocalRandom.current().nextInt(1 << 20), EventChain.OCCUPIED);
		return chain != null && launch(hold.anchor, chain, hold);
	}

	/**
	 * GM shortcut (//dynevent start): the targeted Warden starts a chain now, without the chance roll, beat cooldown or
	 * caps. The switch, a Warden already in an event and the chain's level band still apply. Returns what happened.
	 */
	public static String force(DynamicEvent.Anchor anchor, String chainId) {
		if (!DynamicEventConfig.ENABLED)
			return "Dynamic events are switched off (gameserver.dynamicevents.enable).";
		if (BY_BOT.containsKey(anchor.warden()))
			return anchor.wardenName() + " is already running an event.";
		if (EventWorld.held(anchor.beat()))
			return "Raiders hold this road; walk up to them to start the retake.";
		var chains = EventCatalogue.chains();
		EventChain chain = chainId == null || chainId.isBlank() ? pick(chains, anchor.level(), ThreadLocalRandom.current().nextInt(1 << 20), EventChain.WARDEN)
			: chains.stream().filter(c -> c.id.equals(chainId)).findFirst().orElse(null);
		if (chain == null)
			return chainId == null || chainId.isBlank() ? "No chain fits this beat's level " + anchor.level() + "." : "Unknown chain " + chainId + ". Loaded: "
				+ chains.stream().map(c -> c.id).toList() + ".";
		if (!EventChain.WARDEN.equals(chain.trigger))
			return chain.id + " starts on a held road, not from a Warden.";
		if (!chain.levels(anchor.level()))
			return chain.id + " is for levels " + chain.minLevel + "-" + chain.maxLevel + "; this beat is level " + anchor.level() + ".";
		BEAT_READY.remove(anchor.beat());
		return launch(anchor, chain, null) ? "Starting " + chain.title + " with " + anchor.wardenName() + " on " + anchor.road() + "."
			: "An event is already starting on this beat.";
	}

	/** GM shortcut (//dynevent stop): the event around the player ends now, as a failure without occupation, and leaves nothing in the world. */
	public static String stop(Player gm) {
		DynamicEvent e = eventAt(gm);
		if (e == null)
			return "You are not in a running event's area.";
		synchronized (e) {
			if (e.finished)
				return "That event has already ended.";
			log.info("Dynamic event {} '{}' stopped by {}", e.key, e.chain.title, gm.getName());
			finish(e, false, System.currentTimeMillis(), audience(e), false);
		}
		return "Stopped " + e.chain.title + ".";
	}

	/** GM shortcut (//dynevent status): counters, loaded chains, held roads and every running event. */
	public static String status() {
		var lines = new ArrayList<String>();
		lines.add(summary() + ", chains=" + EventCatalogue.chains().stream().map(c -> c.id).toList());
		long now = System.currentTimeMillis();
		for (DynamicEvent e : RUNNING.values())
			synchronized (e) {
				if (!e.finished && e.stage != null)
					lines.add(e.chain.title + " on " + e.anchor.road() + " (map " + e.anchor.map() + "): " + EventTracker.headline(e) + " - " + EventTracker.progress(e, now)
						+ "; " + e.joined.size() + " player(s), " + e.recruits.size() + " bot(s)");
			}
		return String.join("\n", lines);
	}

	private static boolean launch(DynamicEvent.Anchor anchor, EventChain chain, EventWorld.Hold hold) {
		if (!STARTING.add(anchor.beat()))
			return false;
		ThreadPoolManager.getInstance().execute(() -> {
			try {
				start(anchor, chain, hold);
			} catch (RuntimeException e) {
				log.warn("Dynamic event {} could not start on {}: {}", chain.id, anchor.beat(), e.toString());
				if (hold != null)
					hold.retaking = false;
			} finally {
				STARTING.remove(anchor.beat());
			}
		});
		return true;
	}

	private static void start(DynamicEvent.Anchor anchor, EventChain chain, EventWorld.Hold hold) {
		long now = System.currentTimeMillis();
		EventSpawns.Pool pool = hold != null ? hold.pool : EventSpawns.pool(anchor);
		if (pool == null) {
			// No native monsters fit this beat; ask again much later rather than every minute.
			BEAT_READY.put(anchor.beat(), now + DynamicEventConfig.BEAT_COOLDOWN_MINUTES * 60_000L);
			log.info("Dynamic event {} skipped on {}: no native monsters of level {} near the road", chain.id, anchor.beat(), anchor.level());
			return;
		}
		// STARTING holds the beat, and a Warden walks one beat, so no second event can claim it meanwhile. A retake runs even
		// while its Warden is down: the guards are the event.
		Player warden = World.getInstance().getPlayer(anchor.warden());
		if (hold == null && (warden == null || warden.isDead()) || BY_BOT.containsKey(anchor.warden())) {
			if (hold != null)
				hold.retaking = false;
			return;
		}
		var event = new DynamicEvent(now * 1000 + SEQUENCE.incrementAndGet() % 1000, chain, anchor, pool, now);
		event.hold = hold;
		synchronized (event) {
			BY_BOT.put(anchor.warden(), event);
			RUNNING.put(event.key, event);
			BEAT_READY.put(anchor.beat(), Long.MAX_VALUE);
			STARTED.incrementAndGet();
			log.info("Dynamic event {} '{}' started {} on {} (map {}, level {}, enemy {}, leader {})", event.key, chain.title,
				hold != null ? "by players at the held road" : "by Warden " + anchor.wardenName(), anchor.road(), anchor.map(), anchor.level(), pool.enemy(),
				pool.boss() == null ? "none" : pool.boss().getName());
			enter(event, chain.stage(chain.first), now, audience(event));
		}
	}

	// ----- stages -----

	private static void enter(DynamicEvent e, EventChain.Stage s, long now, Collection<Player> audience) {
		despawnLeftovers(e);
		e.stage = s;
		e.stageStarted = now;
		e.stageEnds = now + s.seconds * 1000L;
		e.defeated = e.quartersSaid = e.phase = e.ambushes = 0;
		e.pending = e.spawnFailures = e.phaseTotal = 0;
		e.boss = 0;
		e.leaderDown = e.shielded = false;
		e.phaseAdds.clear();
		e.bossScaling = null;
		e.nextRescale = now + RESCALE_MS;
		e.contributions.values().forEach(c -> c.stage = 0);
		if (s.monsters() != EventChain.Rating.NORMAL)
			e.spot = e.pool.camp();
		int room = Math.max(0, DynamicEventConfig.BOT_RECRUIT_MAX - e.recruits.size());
		if (room > 0)
			for (Player bot : bots.recruit(e, room))
				if (BY_BOT.putIfAbsent(bot.getObjectId(), e) == null)
					e.recruits.add(bot.getObjectId());
		double participants = participantsNow(e, audience);
		switch (s.kind()) {
			case KILL -> {
				// A retake begins with the guards still standing on the held road.
				if (e.hold != null && s.id.equals(e.chain.first))
					for (int id : EventWorld.standing(e.hold))
						if (World.getInstance().findVisibleObject(id) instanceof Npc guard) {
							track(e, guard);
							Creature target = target(e);
							if (target != null)
								guard.getAggroList().addHate(target, 100);
						}
				e.required = DynamicEvent.liveRequired(DynamicEvent.scaled(s.count, s.perParticipant, participants), 0, e.alive.size());
				e.pending = e.required - e.alive.size();
				wave(e, participants);
			}
			case ESCORT -> e.required = 1;
			case BOSS -> {
				e.required = 1;
				Npc boss = e.pool.boss() == null ? null : EventSpawns.spawn(e.anchor, e.pool.boss(), e.pool.camp(), 0, target(e));
				if (boss == null) {
					log.warn("Dynamic event {}: leader {} could not appear at the camp", e.key, e.pool.boss() == null ? "none" : e.pool.boss().getTemplateId());
					e.stageEnds = now; // resolves as a failure on the next tick
				} else {
					var rating = e.pool.boss().getRating();
					e.bossScaling = EventSpawns.scale(boss, bossHealth(rating, participants), EventSpawns.leaderDamage(rating));
					track(e, boss);
					e.boss = boss.getObjectId();
				}
			}
		}
		EventNarration.say(e, s.say.start, audience);
		EventNarration.gesture(e, EmotionId.POINT);
		for (Player p : audience)
			PacketSendUtility.sendMessage(p, "[Event] " + EventTracker.headline(e) + ": " + EventTracker.progress(e, now));
	}

	private static float bossHealth(com.aionemu.gameserver.model.templates.npc.NpcRating rating, double participants) {
		return EventSpawns.leaderHealth(rating) * EventSpawns.bossHealth(participants, DynamicEventConfig.BOSS_HP_PER_PARTICIPANT, DynamicEventConfig.BOSS_HP_MAX);
	}

	/** Participants now, as Guild Wars 2 counts them: everyone around (players 1, answering bots by weight), capped by event size. */
	private static double participantsNow(DynamicEvent e, Collection<Player> audience) {
		int bots = 0;
		for (int id : e.recruits) {
			Player bot = World.getInstance().getPlayer(id);
			if (bot != null && bot.isSpawned() && !bot.isDead() && e.spot.distance(bot.getX(), bot.getY(), bot.getZ()) <= DynamicEventConfig.RADIUS)
				bots++;
		}
		boolean group = e.stage.group || e.stage.kind() == EventChain.Type.BOSS;
		return DynamicEvent.capped(DynamicEvent.participants(audience.size(), bots, DynamicEventConfig.BOT_WEIGHT_PERCENT), group, DynamicEventConfig.SCALE_CAP,
			DynamicEventConfig.GROUP_SCALE_CAP);
	}

	private static void track(DynamicEvent e, Npc npc) {
		e.alive.add(npc.getObjectId());
		BY_MONSTER.put(npc.getObjectId(), e);
	}

	/** Who the arrivals attack: the Warden while it stands in the fight, else the nearest player there, else an answering bot. */
	private static Creature target(DynamicEvent e) {
		Player warden = World.getInstance().getPlayer(e.anchor.warden());
		if (warden != null && warden.isSpawned() && !warden.isDead() && e.spot.distance(warden.getX(), warden.getY(), warden.getZ()) <= 50)
			return warden;
		Player near = audience(e).stream().min(Comparator.comparingDouble(p -> e.spot.distance(p.getX(), p.getY(), p.getZ()))).orElse(null);
		if (near != null)
			return near;
		for (int id : e.recruits) {
			Player bot = World.getInstance().getPlayer(id);
			if (bot != null && bot.isSpawned() && !bot.isDead() && e.spot.distance(bot.getX(), bot.getY(), bot.getZ()) <= 50)
				return bot;
		}
		return null;
	}

	/** The next wave of a KILL stage arrives once the last one is down. With a crowd some arrivals are Elites. */
	private static void wave(DynamicEvent e, double participants) {
		if (!e.alive.isEmpty() || e.pending <= 0)
			return;
		int size = Math.min(e.stage.wave, e.pending);
		boolean camp = e.stage.monsters() != EventChain.Rating.NORMAL;
		boolean distinctElites = e.pool.elite() != e.pool.normal();
		double eliteChance = EventSpawns.eliteChance(participants, DynamicEventConfig.ELITE_FROM);
		Creature target = target(e);
		var random = ThreadLocalRandom.current();
		int spawned = 0;
		for (int i = 0; i < size; i++) {
			boolean upgraded = e.stage.monsters() == EventChain.Rating.NORMAL && random.nextDouble() < eliteChance;
			var templates = e.stage.monsters() == EventChain.Rating.ELITE || upgraded && distinctElites ? e.pool.elite() : e.pool.normal();
			var template = templates.get(random.nextInt(templates.size()));
			DynamicEvent.Spot at;
			float spread;
			if (camp) {
				at = e.pool.campSpots().get(random.nextInt(e.pool.campSpots().size()));
				spread = 0;
			} else {
				var nearRoad = e.anchor.roadSpots().stream().filter(p -> e.spot.distance(p.x(), p.y(), p.z()) <= 35).toList();
				at = nearRoad.isEmpty() ? e.spot : nearRoad.get(random.nextInt(nearRoad.size()));
				spread = 6;
			}
			Npc npc = EventSpawns.spawn(e.anchor, template, at, spread, target);
			if (npc != null) {
				// A lieutenant (or a crowd's Elite) drawn from normal monsters gets veteran strength.
				if (e.stage.monsters() == EventChain.Rating.ELITE || upgraded)
					EventSpawns.scale(npc, EventSpawns.lieutenantHealth(template.getRating()), EventSpawns.lieutenantDamage(template.getRating()));
				track(e, npc);
				spawned++;
			}
		}
		// What could not arrive tries again next second. A stage whose ground refuses every spot fails instead of passing for free.
		e.pending -= spawned;
		e.spawnFailures = spawned == 0 ? e.spawnFailures + 1 : 0;
	}

	private static List<Integer> reinforce(DynamicEvent e, DynamicEvent.Spot near, float spread, int size) {
		var random = ThreadLocalRandom.current();
		Creature target = target(e);
		var arrived = new ArrayList<Integer>();
		for (int i = 0; i < size; i++) {
			Npc npc = EventSpawns.spawn(e.anchor, e.pool.normal().get(random.nextInt(e.pool.normal().size())), near, spread, target);
			if (npc != null) {
				track(e, npc);
				arrived.add(npc.getObjectId());
			}
		}
		return arrived;
	}

	/** Boss phase reinforcements, growing each phase like the Behemoth's portals (5, 9, 13): count x (phase + 1), scaled, at most 15. */
	static int phaseAdds(int count, int phase, int perParticipant, double participants) {
		return Math.min(15, DynamicEvent.scaled(Math.max(1, count) * (phase + 1), perParticipant, participants));
	}

	// ----- tick -----

	private static void tick() {
		long now = System.currentTimeMillis();
		FULL_CREDIT.values().removeIf(until -> until < now);
		try {
			EventWorld.tick(now);
			schedules(now);
		} catch (RuntimeException error) {
			log.warn("Dynamic event world tick failed: {}", error.toString());
		}
		for (DynamicEvent e : RUNNING.values()) {
			try {
				synchronized (e) {
					if (!e.finished)
						step(e, now);
				}
			} catch (RuntimeException error) {
				log.warn("Dynamic event {} ended after an error", e.key, error);
				synchronized (e) {
					if (!e.finished)
						finish(e, false, now, List.of(), false);
				}
			}
		}
	}

	private static void step(DynamicEvent e, long now) {
		var audience = audience(e);
		for (Player p : audience)
			if (e.joined.add(p.getObjectId())) {
				// Guild Wars 2: "New Event Nearby" as a player comes within range of a running event.
				PacketSendUtility.sendPacket(p, new SM_MESSAGE(0, null, "New Event Nearby: " + EventTracker.headline(e), ChatType.BRIGHT_YELLOW_CENTER));
				PacketSendUtility.sendMessage(p, "[Event] New event nearby: " + EventTracker.headline(e) + ". " + EventTracker.progress(e, now));
			}
		EventJournal.sync(e, audience, now);
		// Once started an event develops whether or not anyone attends; with nobody there the Warden and bots fight it out.
		Player warden = World.getInstance().getPlayer(e.anchor.warden());
		boolean wardenStanding = warden != null && warden.isSpawned() && !warden.isDead();
		if (e.stage.protect && !wardenStanding) {
			end(e, false, now, audience);
			return;
		}
		e.alive.removeIf(id -> {
			boolean gone = !(World.getInstance().findVisibleObject(id) instanceof Npc npc) || !npc.isSpawned();
			if (gone && BY_MONSTER.remove(id, e)) {
				e.phaseAdds.remove(id);
				if (e.stage.kind() == EventChain.Type.KILL)
					e.pending++; // removed without dying (despawned): it comes back with the next wave
			}
			return gone;
		});
		rouse(e);
		double participants = participantsNow(e, audience);
		switch (e.stage.kind()) {
			case KILL -> {
				// Live scaling (Guild Wars 2 rescales at once as people arrive or leave), never below what is already done or standing.
				int target = DynamicEvent.scaled(e.stage.count, e.stage.perParticipant, participants);
				e.required = DynamicEvent.liveRequired(target, e.defeated, e.alive.size());
				e.pending = Math.max(0, e.required - e.defeated - e.alive.size());
				speakQuarters(e, (double) e.defeated / Math.max(1, e.required), audience);
				if (e.defeated >= e.required) {
					end(e, true, now, audience);
					return;
				}
				wave(e, participants);
				if (e.spawnFailures >= 10) {
					log.warn("Dynamic event {}: no ground for stage {} monsters near {}, {}", e.key, e.stage.id, e.spot.x(), e.spot.y());
					end(e, false, now, audience);
					return;
				}
			}
			case ESCORT -> {
				if (wardenStanding) {
					e.spot = new DynamicEvent.Spot(warden.getX(), warden.getY(), warden.getZ());
					// Objective credit: walking with the Warden counts toward the reward, as Guild Wars 2 credits objectives.
					for (Player p : audience) {
						var c = e.contributions.computeIfAbsent(p.getObjectId(), id -> new DynamicEvent.Contribution(id, false, p.getName()));
						if (c.stage < 60) {
							c.stage += 0.5;
							c.chain += 0.5;
						}
					}
					var camp = e.pool.camp();
					double total = Math.max(1, e.anchor.start().distance(camp.x(), camp.y(), camp.z()));
					double left = e.spot.distance(camp.x(), camp.y(), camp.z());
					if (left <= 10 && e.alive.isEmpty()) {
						end(e, true, now, audience);
						return;
					}
					double done = 1 - left / total;
					if (e.ambushes < e.stage.count && e.alive.isEmpty() && done >= (e.ambushes + 1d) / (e.stage.count + 1d)) {
						e.ambushes++;
						reinforce(e, e.spot, 8, DynamicEvent.scaled(e.stage.wave, e.stage.perParticipant, participants));
						String line = EventNarration.quarterLine(e.stage.say.progress, Math.min(3, e.ambushes));
						if (line != null)
							EventNarration.say(e, line, audience);
					}
				}
			}
			case BOSS -> {
				if (e.leaderDown) {
					end(e, true, now, audience);
					return;
				}
				Npc boss = World.getInstance().findVisibleObject(e.boss) instanceof Npc n ? n : null;
				if (boss == null || !boss.isSpawned()) {
					end(e, false, now, audience); // the leader left the world without being defeated
					return;
				}
				if (now >= e.nextRescale) {
					e.nextRescale = now + RESCALE_MS;
					EventSpawns.rescale(boss, e.bossScaling, bossHealth(e.pool.boss().getRating(), participants));
				}
				if (e.shielded) {
					if (e.phaseAdds.isEmpty()) {
						e.shielded = false;
						boss.overrideNpcType(null);
						notice(e, EventNarration.render(e, "{boss}'s shield breaks! Strike now!"), audience);
					}
				} else {
					int hp = boss.getLifeStats().getHpPercentage();
					if (e.phase < e.stage.phases.size() && hp <= e.stage.phases.get(e.phase)) {
						EventNarration.say(e, e.stage.say.phase.get(e.phase), audience);
						var adds = reinforce(e, e.pool.camp(), 8, phaseAdds(e.stage.count, e.phase, e.stage.perParticipant, participants));
						e.phase++;
						if (!adds.isEmpty()) {
							// Guild Wars 2's Shadow Behemoth: untargetable until its portals fall. Ours: shielded until its reinforcements fall.
							e.shielded = true;
							e.phaseAdds.addAll(adds);
							e.phaseTotal = adds.size();
							boss.overrideNpcType(CreatureType.INVULNERABLE);
							notice(e, EventNarration.render(e, "{boss} is shielded! Defeat its reinforcements to break the shield."), audience);
						}
					}
				}
			}
		}
		if (now >= e.stageEnds)
			end(e, false, now, audience);
	}

	private static void notice(DynamicEvent e, String text, Collection<Player> audience) {
		for (Player p : audience) {
			PacketSendUtility.sendPacket(p, new SM_MESSAGE(0, null, text, ChatType.BRIGHT_YELLOW_CENTER));
			PacketSendUtility.sendMessage(p, "[Event] " + text);
		}
	}

	/** A monster hears its target only once its known list holds it, a moment after spawning: idle ones are sent again. */
	private static void rouse(DynamicEvent e) {
		Creature target = null;
		for (int id : e.alive)
			if (World.getInstance().findVisibleObject(id) instanceof Npc npc && npc.isSpawned() && !npc.isDead() && npc.getAggroList().stream().findAny().isEmpty()) {
				if (target == null && (target = target(e)) == null)
					return;
				npc.getAggroList().addHate(target, 100);
			}
	}

	private static void speakQuarters(DynamicEvent e, double fraction, Collection<Player> audience) {
		int quarter = (int) Math.min(3, Math.floor(fraction * 4));
		while (e.quartersSaid < quarter) {
			e.quartersSaid++;
			String line = EventNarration.quarterLine(e.stage.say.progress, e.quartersSaid);
			if (line != null)
				EventNarration.say(e, line, audience);
			for (Player p : audience)
				PacketSendUtility.sendMessage(p, "[Event] " + EventTracker.progress(e, System.currentTimeMillis()));
		}
	}

	// ----- schedules -----

	/** Minute slot of a schedule at this time of day, or -1 when this minute is not one of its times. */
	static int slot(int minuteOfDay, int everyMinutes, int atMinute) {
		return everyMinutes > 0 && Math.floorMod(minuteOfDay, everyMinutes) == atMinute ? minuteOfDay / everyMinutes : -1;
	}

	/** Chains with a schedule start at their times, like Guild Wars 2's world-boss timers, on each map where players are out on the roads. */
	private static void schedules(long now) {
		if (!DynamicEventConfig.ENABLED)
			return;
		int minute = LocalTime.now().toSecondOfDay() / 60;
		for (EventChain chain : EventCatalogue.chains()) {
			if (chain.schedule == null)
				continue;
			int slot = slot(minute, chain.schedule.everyMinutes, chain.schedule.atMinute);
			String key = LocalDate.now() + ":" + slot;
			if (slot < 0 || key.equals(SCHEDULED.put(chain.id, key)))
				continue;
			for (DynamicEvent.Anchor anchor : bots.scheduled(chain.minLevel, chain.maxLevel)) {
				if (EventWorld.held(anchor.beat()) || !admitted(anchor, now, true) || !launch(anchor, chain, null))
					continue;
				log.info("Dynamic event {} starts on schedule on {} (map {})", chain.id, anchor.road(), anchor.map());
				String text = "Scouts report trouble on " + anchor.road() + ": " + chain.title + " has begun.";
				for (Player p : World.getInstance().getAllPlayers())
					if (!p.isPlayerBot() && p.isOnline() && p.getWorldId() == anchor.map() && p.getRace() == anchor.race())
						PacketSendUtility.sendMessage(p, "[Event] " + text);
			}
		}
	}

	// ----- monster deaths -----

	/** Called by NpcController.onDie before rewards; reads the native damage list, which is still complete here. Never throws. */
	public static void npcDied(Npc npc) {
		try {
			DynamicEvent e = BY_MONSTER.remove(npc.getObjectId());
			if (e == null)
				return;
			synchronized (e) {
				if (e.finished || !e.alive.remove(npc.getObjectId()))
					return;
				FULL_CREDIT.put(npc.getObjectId(), System.currentTimeMillis() + FULL_CREDIT_MS);
				e.phaseAdds.remove(npc.getObjectId());
				boolean leader = npc.getObjectId() == e.boss;
				credit(e, npc, leader);
				if (e.stage.kind() == EventChain.Type.KILL)
					e.defeated++;
				if (leader)
					e.leaderDown = true;
			}
		} catch (RuntimeException error) {
			log.warn("Dynamic event credit for {} skipped: {}", npc.getObjectId(), error.toString());
		}
	}

	/** A participant's share of one monster: damage dealt, plus half of the threat they drew beyond it (healing and tanking). */
	static double share(int damage, int hate, int maxHp) {
		double max = Math.max(1, maxHp);
		return Math.min(100, Math.max(0, damage) * 100d / max) + Math.min(50, Math.max(0, hate - Math.max(0, damage)) * 50d / max);
	}

	/** Each attacker's share; the leader's shares weigh triple and count as boss shares. Players with a real share also roll personal loot. */
	private static void credit(DynamicEvent e, Npc npc, boolean leader) {
		int max = Math.max(1, npc.getLifeStats().getMaxHp());
		var looters = new ArrayList<Player>();
		int[] topDamage = { 0 };
		Player[] top = { null };
		npc.getAggroList().stream().forEach(info -> {
			if (!(info.getAttacker() != null && info.getAttacker().getMaster() instanceof Player p))
				return;
			double share = share(info.getDamage(), info.getHate(), max);
			if (share <= 0)
				return;
			var c = e.contributions.computeIfAbsent(p.getObjectId(), id -> new DynamicEvent.Contribution(id, p.isPlayerBot(), p.getName()));
			double weighted = share * (leader ? 3 : 1);
			c.chain += weighted;
			c.stage += weighted;
			if (leader)
				c.boss += weighted;
			if (!p.isPlayerBot() && share >= LOOT_SHARE)
				looters.add(p);
			if (info.getDamage() > topDamage[0]) {
				topDamage[0] = info.getDamage();
				top[0] = p;
			}
		});
		// Native loot goes to the native winner as always; every other player who helped gets a personal roll by the same
		// native calculation (Guild Wars 2: loot is "given to each player that participated, as if they were the only participant").
		for (Player p : looters)
			if (p != top[0] && (top[0] == null || p.getCurrentTeam() == null || p.getCurrentTeam() != top[0].getCurrentTeam()))
				ThreadPoolManager.getInstance().execute(() -> personalLoot(npc, p));
	}

	private static void personalLoot(Npc npc, Player p) {
		try {
			if (!p.isOnline())
				return;
			Set<DropItem> items = DropRegistrationService.getInstance().personalDrop(npc, p);
			for (DropItem item : items) {
				var template = DataManager.ITEM_DATA.getItemTemplate(item.getDropTemplate().getItemId());
				if (template == null || item.getCount() <= 0)
					continue;
				long left = com.aionemu.gameserver.services.item.ItemService.addItem(p, template.getTemplateId(), item.getCount());
				if (left > 0)
					com.aionemu.gameserver.services.mail.SystemMailService.sendMail("World Event", p.getName(), "Event loot", "Your bag was full, so your loot came by mail.",
						template.getTemplateId(), left, 0, com.aionemu.gameserver.model.gameobjects.LetterType.NORMAL);
				PacketSendUtility.sendMessage(p, "[Event] Your loot: " + template.getName() + (item.getCount() > 1 ? " x" + item.getCount() : "") + ".");
			}
		} catch (RuntimeException error) {
			log.warn("Dynamic event personal loot for {} skipped: {}", p.getObjectId(), error.toString());
		}
	}

	// ----- endings -----

	/** A part ends. Every part pays its participants by tier, full when won and half when lost, as Guild Wars 2 pays each event. */
	private static void end(DynamicEvent e, boolean success, long now, Collection<Player> audience) {
		var s = e.stage;
		EventNarration.say(e, success ? s.say.success : s.say.failure, audience);
		despawnLeftovers(e);
		e.history.add(EventNarration.render(e, s.title) + (success ? ": done" : ": failed"));
		if (success) {
			e.stagesWon++;
			EventNarration.gesture(e, EmotionId.SALUTE);
		}
		double top = e.contributions.values().stream().mapToDouble(c -> c.stage).max().orElse(0);
		double bossTop = e.contributions.values().stream().mapToDouble(c -> c.boss).max().orElse(0);
		String title = EventNarration.render(e, s.title);
		for (var c : e.contributions.values()) {
			Player p = c.bot ? null : World.getInstance().getPlayer(c.id);
			if (p == null || !p.isOnline())
				continue;
			var tier = EventRewards.tier(c.stage, top, DynamicEventConfig.GOLD_SHARE, DynamicEventConfig.SILVER_SHARE);
			EventRewards.stage(p, title, tier, success, e.anchor.level());
			if (success && s.kind() == EventChain.Type.BOSS && leaderBody(e) != null)
				EventRewards.chest(p, e.chain.id, e.chain.title, leaderBody(e),
					EventRewards.tier(c.boss, bossTop, DynamicEventConfig.GOLD_SHARE, DynamicEventConfig.SILVER_SHARE), e.anchor.level());
		}
		String next = success ? s.success : s.failure;
		if (next.equals(EventChain.END))
			finish(e, true, now, audience, false);
		else if (next.equals(EventChain.FAIL))
			finish(e, false, now, audience, true);
		else if (!DynamicEventConfig.ENABLED)
			finish(e, false, now, audience, false);
		else
			enter(e, e.chain.stage(next), now, audience);
	}

	/** The defeated leader, still lying where it fell (its decay takes minutes; the stage ends within a second). */
	private static Npc leaderBody(DynamicEvent e) {
		return e.boss != 0 && World.getInstance().findVisibleObject(e.boss) instanceof Npc n ? n : null;
	}

	/**
	 * The chain ends. Won: the road is safe (a retake frees it) and a merchant opens a stall for the defenders. Lost with
	 * consequences: the raiders hold the road (a chain with occupyOnFailure, or a failed retake).
	 */
	private static void finish(DynamicEvent e, boolean success, long now, Collection<Player> audience, boolean consequences) {
		e.finished = true;
		if (success) {
			WON.incrementAndGet();
			EventNarration.gesture(e, EmotionId.VICTORY);
			if (e.hold != null)
				EventWorld.retaken(e.anchor.beat(), true);
			stall(e, audience);
		} else {
			LOST.incrementAndGet();
			for (Player p : audience)
				PacketSendUtility.sendMessage(p, "[Event] " + e.chain.title + " has ended.");
			if (consequences && (e.chain.occupyOnFailure || e.hold != null)) {
				EventWorld.occupy(e, now);
				notice(e, EventNarration.render(e, "The raiders now hold {road}. Return to drive them out."), audience);
			} else if (e.hold != null)
				EventWorld.retaken(e.anchor.beat(), false);
		}
		despawnLeftovers(e);
		EventJournal.close(e, (success ? "Event complete: " : "Event failed: ") + e.chain.title);
		try {
			bots.released(e);
		} catch (RuntimeException error) {
			log.warn("Dynamic event {} bot release: {}", e.key, error.toString());
		}
		BY_BOT.values().removeIf(v -> v == e);
		RUNNING.remove(e.key);
		BEAT_READY.put(e.anchor.beat(), now + DynamicEventConfig.BEAT_COOLDOWN_MINUTES * 60_000L);
		log.info("Dynamic event {} '{}' {} after {} won stage(s): {} player(s), {} bot(s) took part", e.key, e.chain.title, success ? "won" : "lost",
			e.stagesWon, e.contributions.values().stream().filter(c -> !c.bot).count(), e.contributions.values().stream().filter(c -> c.bot).count());
	}

	/** After a won chain a native merchant keeps a stall at the event for a while (Guild Wars 2: NPCs open temporary shops). */
	private static void stall(DynamicEvent e, Collection<Player> audience) {
		int minutes = DynamicEventConfig.SHOP_MINUTES;
		if (minutes <= 0)
			return;
		Npc merchant = EventSpawns.merchant(e.anchor, e.spot);
		if (merchant == null)
			return;
		for (Player p : audience)
			PacketSendUtility.sendMessage(p, "[Event] " + merchant.getName() + " has set up a stall here for the defenders, for " + minutes + " minutes.");
		ThreadPoolManager.getInstance().schedule(() -> {
			if (merchant.isSpawned())
				merchant.getController().delete();
		}, minutes * 60_000L);
	}

	/** Monsters of a finished stage leave the field; nothing of an event stays in the world (a hold posts its own guards). */
	private static void despawnLeftovers(DynamicEvent e) {
		for (int id : List.copyOf(e.alive)) {
			BY_MONSTER.remove(id, e);
			if (World.getInstance().findVisibleObject(id) instanceof Npc npc && npc.isSpawned() && !npc.isDead())
				npc.getController().delete();
		}
		e.alive.clear();
		e.phaseAdds.clear();
	}

	// ----- area -----

	/** Players (not bots) in the event area, read from the known lists of the event's own actors only. */
	static List<Player> audience(DynamicEvent e) {
		var found = new LinkedHashMap<Integer, Player>();
		var eyes = new ArrayList<Creature>();
		Player warden = World.getInstance().getPlayer(e.anchor.warden());
		if (warden != null && warden.isSpawned())
			eyes.add(warden);
		int monsters = 0;
		for (int id : e.alive)
			if (monsters < 3 && World.getInstance().findVisibleObject(id) instanceof Npc npc && npc.isSpawned()) {
				eyes.add(npc);
				monsters++;
			}
		for (int id : e.recruits) {
			Player bot = World.getInstance().getPlayer(id);
			if (bot != null && bot.isSpawned() && eyes.size() < 8)
				eyes.add(bot);
		}
		for (int id : e.joined) {
			Player p = World.getInstance().getPlayer(id);
			if (inArea(e, p))
				found.put(id, p);
		}
		for (Creature eye : eyes)
			eye.getKnownList().forEachPlayer(p -> {
				if (inArea(e, p))
					found.putIfAbsent(p.getObjectId(), p);
			});
		return List.copyOf(found.values());
	}

	private static boolean inArea(DynamicEvent e, Player p) {
		return p != null && !p.isPlayerBot() && p.isOnline() && p.isSpawned() && p.getWorldId() == e.anchor.map() && p.getInstanceId() == e.anchor.instance()
			&& e.spot.distance(p.getX(), p.getY(), p.getZ()) <= DynamicEventConfig.RADIUS;
	}

	/** Whether a player stands in a running event's area (for the bot bridge and the tracker). */
	public static DynamicEvent eventAt(Player p) {
		for (DynamicEvent e : RUNNING.values())
			synchronized (e) {
				if (!e.finished && inArea(e, p))
					return e;
			}
		return null;
	}

	static boolean near(Creature a, DynamicEvent.Spot spot, float range) {
		return PositionUtil.getDistance(a, spot.x(), spot.y(), spot.z()) <= range;
	}

	private DynamicEventService() {
	}
}
