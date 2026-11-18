package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.aionemu.gameserver.configs.main.DynamicEventConfig;
import com.aionemu.gameserver.configs.main.PlayerBotWardenConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.dynamicevents.DynamicEvent;
import com.aionemu.gameserver.services.dynamicevents.DynamicEventService;
import com.aionemu.gameserver.services.dynamicevents.EventBots;
import com.aionemu.gameserver.services.dynamicevents.EventWorld;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * The Playerbots side of dynamic events (config/dynamic-events/README.md): a Road Warden calls for help, free world bots answer,
 * and both walk to and fight the event's monsters through their normal movement and combat. This class takes no session
 * monitor: the event engine calls recruit/released while holding an event's monitor, and sessions call in while holding theirs.
 */
public final class PlayerBotDynamicEvents implements EventBots {

	private static final PlayerBotDynamicEvents INSTANCE = new PlayerBotDynamicEvents();
	private static final Map<Integer, Long> NEXT_CHECK = new ConcurrentHashMap<>();
	/** How far from the beat's level a player may be for a Warden to ask them for help. */
	static final int LEVEL_GAP = 8;

	public static void register() {
		DynamicEventService.bots(INSTANCE);
	}

	/** Whether a Warden asks a nearby player for help now: the player's level fits the beat and the Warden is free. */
	static boolean asks(boolean enabled, boolean wardenFree, boolean humanFree, int beatLevel, int humanLevel, int roll, int chance) {
		return enabled && wardenFree && humanFree && Math.abs(beatLevel - humanLevel) <= LEVEL_GAP && roll < chance;
	}

	/** From PlayerBotWardens.observe, once a second per Warden, for each player of its faction near it on the field. */
	static void wardenSees(PlayerBotRoadDuty.State s, Player human, long now) {
		if (!DynamicEventConfig.ENABLED || s.route == null || !s.route.id().startsWith("beat:") || EventWorld.held(s.route.id()))
			return;
		Player bot = s.session.bot();
		if (now < NEXT_CHECK.getOrDefault(bot.getObjectId(), 0L))
			return;
		NEXT_CHECK.put(bot.getObjectId(), now + Math.max(10, DynamicEventConfig.TRIGGER_CHECK_SECONDS) * 1000L);
		boolean wardenFree = !bot.isDead() && !bot.getController().isInCombat() && s.visitor == 0 && !PlayerBotWardens.responding(bot)
			&& DynamicEventService.eventOf(bot) == null;
		boolean humanFree = !human.isDead() && !human.isInInstance() && DynamicEventService.eventAt(human) == null;
		if (!asks(true, wardenFree, humanFree, s.route.level(), human.getLevel(), ThreadLocalRandom.current().nextInt(100), Math.clamp(DynamicEventConfig.TRIGGER_CHANCE, 0, 100)))
			return;
		DynamicEventService.offer(anchor(s));
	}

	/** Where the Warden stands on its beat, and the beat's road points within 40 m (native ground for arrivals). */
	static DynamicEvent.Anchor anchor(PlayerBotRoadDuty.State s) {
		Player bot = s.session.bot();
		var roadSpots = new ArrayList<DynamicEvent.Spot>();
		for (var p : s.route.points())
			if (PositionUtil.getDistance(bot, p.x(), p.y(), p.z()) <= 40)
				roadSpots.add(new DynamicEvent.Spot(p.x(), p.y(), p.z()));
		return new DynamicEvent.Anchor(bot.getWorldId(), bot.getInstanceId(), bot.getRace(), s.route.level(), bot.getObjectId(), bot.getName(), s.route.id(),
			s.route.name(), new DynamicEvent.Spot(bot.getX(), bot.getY(), bot.getZ()), List.copyOf(roadSpots));
	}

	/** GM shortcut (//dynevent start [chain]): the targeted Road Warden, on duty on a field beat, starts an event now. */
	public static String force(Player gm, String chainId) {
		if (!(gm.getTarget() instanceof Player bot) || !bot.isPlayerBot())
			return "Target a Road Warden first.";
		var s = PlayerBotRoadDuty.state(bot);
		if (s == null || !PlayerBotRoadDuty.publicWarden(bot) || s.route == null || !s.route.id().startsWith("beat:"))
			return bot.getName() + " is not a Road Warden on duty on a beat.";
		if (bot.isDead() || bot.getController().isInCombat())
			return bot.getName() + " is " + (bot.isDead() ? "dead" : "fighting") + "; try again shortly.";
		if (!PlayerBotWardenBeats.field(bot.getWorldId(), bot.getX(), bot.getY(), bot.getZ()))
			return bot.getName() + " is inside a settlement; events start on the road.";
		return DynamicEventService.force(anchor(s), chainId);
	}

	/** A free world bot: the same rules as a bystander joining a fight (PlayerBotBystanders), plus faction and level fit. */
	static boolean free(Player bot, DynamicEvent.Anchor anchor) {
		return bot.isPlayerBot() && !bot.isDead() && bot.isSpawned() && PlayerBotPopulationManager.global(bot) && !bot.isInTeam() && !PlayerBotRoadDuty.onDuty(bot)
			&& !PlayerBotFindGroup.committed(bot) && !bot.getController().isInCombat() && !bot.isTrading() && !bot.isFlying() && bot.getFlightPath() == null
			&& bot.getLifeStats().getHpPercentage() >= 50 && bot.getRace() == anchor.race() && bot.getWorldId() == anchor.map() && bot.getInstanceId() == anchor.instance()
			&& Math.abs(bot.getLevel() - anchor.level()) <= LEVEL_GAP && DynamicEventService.eventOf(bot) == null;
	}

	@Override
	public List<Player> recruit(DynamicEvent event, int max) {
		var anchor = event.anchor();
		Player warden = com.aionemu.gameserver.world.World.getInstance().getPlayer(anchor.warden());
		var spot = event.spot();
		if (warden == null || !warden.isSpawned() || max <= 0)
			return List.of();
		var found = new ArrayList<Player>();
		warden.getKnownList().forEachPlayer(p -> {
			if (free(p, anchor) && PositionUtil.getDistance(p, spot.x(), spot.y(), spot.z()) <= DynamicEventConfig.BOT_RECRUIT_RANGE)
				found.add(p);
		});
		found.sort(Comparator.comparingDouble((Player p) -> PositionUtil.getDistance(p, spot.x(), spot.y(), spot.z())).thenComparingInt(Player::getObjectId));
		return List.copyOf(found.subList(0, Math.min(max, found.size())));
	}

	@Override
	public void released(DynamicEvent event) {
		// Nothing to undo: bots only read the engine's maps, which the engine clears as the event ends.
	}

	/** Whether a Warden can start a scheduled chain: public, on a field beat of a fitting level, free, and its road not held. */
	public static boolean schedulable(boolean publicWarden, boolean field, boolean free, boolean held, int beatLevel, int minLevel, int maxLevel) {
		return publicWarden && field && free && !held && beatLevel >= minLevel && beatLevel <= maxLevel;
	}

	@Override
	public List<DynamicEvent.Anchor> scheduled(int minLevel, int maxLevel) {
		// Players out on the roads, one per map: the scheduled event appears with the Warden nearest to them.
		var byMap = new LinkedHashMap<String, Player>();
		for (Player p : com.aionemu.gameserver.world.World.getInstance().getAllPlayers())
			if (!p.isPlayerBot() && p.isOnline() && p.isSpawned() && !p.isDead() && !p.isInInstance() && p.getLevel() >= minLevel - LEVEL_GAP && p.getLevel() <= maxLevel + LEVEL_GAP
				&& PlayerBotWardenBeats.field(p.getWorldId(), p.getX(), p.getY(), p.getZ()))
				byMap.putIfAbsent(p.getWorldId() + ":" + p.getRace(), p);
		var anchors = new ArrayList<DynamicEvent.Anchor>();
		for (Player human : byMap.values()) {
			PlayerBotRoadDuty.State best = null;
			double bestDistance = Double.MAX_VALUE;
			for (var s : PlayerBotRoadDuty.all()) {
				Player bot = s.session.bot();
				if (s.career != PlayerBotRoadDuty.Career.WARDEN || s.route == null || !s.route.id().startsWith("beat:") || bot.getWorldId() != human.getWorldId()
					|| bot.getInstanceId() != human.getInstanceId() || bot.getRace() != human.getRace())
					continue;
				boolean free = !bot.isDead() && !bot.getController().isInCombat() && s.visitor == 0 && !PlayerBotWardens.responding(bot) && DynamicEventService.eventOf(bot) == null;
				if (!schedulable(PlayerBotRoadDuty.publicWarden(bot), PlayerBotWardenBeats.field(bot.getWorldId(), bot.getX(), bot.getY(), bot.getZ()), free,
					EventWorld.held(s.route.id()), s.route.level(), minLevel, maxLevel))
					continue;
				double d = PositionUtil.getDistance(bot, human);
				if (d < bestDistance) {
					bestDistance = d;
					best = s;
				}
			}
			if (best != null)
				anchors.add(anchor(best));
		}
		return anchors;
	}

	/** A merchant heading into a held stretch of road turns around before it, as a Guild Wars 2 trader avoids an overrun area. */
	static boolean avoid(PlayerBotRoadDuty.State s, PlayerBotNavigation navigation, long now) {
		if (s.route == null || s.route.points().isEmpty())
			return false;
		var points = s.route.points();
		var ahead = points.get(Math.clamp(s.point, 0, points.size() - 1));
		Player bot = s.session.bot();
		if (!EventWorld.blocked(s.route.map(), bot.getInstanceId(), ahead.x(), ahead.y(), ahead.z()))
			return false;
		s.direction = -s.direction;
		s.point = Math.clamp(s.point + 2 * s.direction, 0, points.size() - 1);
		s.lane = null;
		s.ahead.clear();
		s.next = now + 3000;
		navigation.stop();
		s.progress.reset();
		s.save();
		return true;
	}

	/** Event monsters count as engaged for the Warden and the bots answering that same event; a shielded leader does not. */
	static boolean engaged(Player bot, Npc npc) {
		DynamicEvent monsterEvent = DynamicEventService.eventOf(npc);
		return monsterEvent != null && !npc.isInvulnerable() && DynamicEventService.eventOf(bot) == monsterEvent && PositionUtil.isInRange(bot, npc, 50);
	}

	/** A world bot answering an event (not its Warden). */
	static boolean answering(Player bot) {
		return DynamicEventService.eventOf(bot) != null && !DynamicEventService.warden(bot);
	}

	/** Out of combat, an answering bot walks to the event and holds there, so other activities do not lead it away. */
	static boolean travel(PlayerBotSession session, PlayerBotNavigation navigation) {
		DynamicEvent e = DynamicEventService.eventOf(session.bot());
		if (e == null)
			return false;
		var spot = e.spot();
		if (PositionUtil.getDistance(session.bot(), spot.x(), spot.y(), spot.z()) > 12)
			return navigation.move(spot.x(), spot.y(), spot.z());
		navigation.stop();
		return true;
	}

	/** The calling Warden leaves its patrol while its event runs: it holds the fight's spot, or leads the escort to the camp. */
	static boolean wardenTravel(PlayerBotRoadDuty.State s, PlayerBotNavigation navigation) {
		if (!PlayerBotWardenConfig.ENABLED)
			return false;
		Player bot = s.session.bot();
		DynamicEvent e = DynamicEventService.eventOf(bot);
		if (e == null || e.anchor().warden() != bot.getObjectId()) {
			var held = s.route == null ? null : EventWorld.heldSpot(s.route.id());
			if (held == null)
				return false;
			// Raiders hold this road: the Warden falls back to the end of its beat farthest from them and waits for help.
			var points = s.route.points();
			var first = points.getFirst();
			var last = points.getLast();
			var fallback = held.distance(first.x(), first.y(), first.z()) >= held.distance(last.x(), last.y(), last.z()) ? first : last;
			s.progress.reset();
			if (PositionUtil.getDistance(bot, fallback.x(), fallback.y(), fallback.z()) > 6)
				return navigation.move(fallback.x(), fallback.y(), fallback.z());
			navigation.stop();
			return true;
		}
		var goal = e.wardenGoal();
		s.progress.reset();
		if (PositionUtil.getDistance(bot, goal.x(), goal.y(), goal.z()) > 6)
			return navigation.move(goal.x(), goal.y(), goal.z());
		navigation.stop();
		return true;
	}

	private PlayerBotDynamicEvents() {
	}
}
