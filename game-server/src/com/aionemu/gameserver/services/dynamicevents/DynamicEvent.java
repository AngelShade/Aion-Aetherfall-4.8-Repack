package com.aionemu.gameserver.services.dynamicevents;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.model.Race;

/** One running chain. Every field is guarded by the event's own monitor. */
public final class DynamicEvent {

	public record Spot(float x, float y, float z) {
		public double distance(float ox, float oy, float oz) {
			return Math.sqrt(Math.pow(x - ox, 2) + Math.pow(y - oy, 2) + Math.pow(z - oz, 2));
		}
	}

	/** Where and for whom the chain runs: the calling Warden's beat and the camp it points to. */
	public record Anchor(int map, int instance, Race race, int level, int warden, String wardenName, String beat, String road,
		Spot start, List<Spot> roadSpots) {
	}

	/** Damage share, in percent of each defeated monster's health, summed over the chain (stage resets nothing). */
	static final class Contribution {
		final int id;
		final boolean bot;
		final String name;
		double chain, stage, boss;

		Contribution(int id, boolean bot, String name) {
			this.id = id;
			this.bot = bot;
			this.name = name;
		}
	}

	final long key;
	final EventChain chain;
	final Anchor anchor;
	final EventSpawns.Pool pool;
	EventChain.Stage stage;
	Spot spot;
	long stageStarted, stageEnds;
	int required, defeated, quartersSaid, phase, ambushes, stagesWon, spawnFailures;
	boolean leaderDown;
	/** Monsters of this stage still standing (object ids). */
	final Set<Integer> alive = ConcurrentHashMap.newKeySet();
	/** Monsters of this stage still to arrive. */
	int pending;
	int boss;
	final Map<Integer, Contribution> contributions = new HashMap<>();
	/** Players told they joined (object ids). */
	final Set<Integer> joined = new HashSet<>();
	final Set<Integer> recruits = new HashSet<>();
	/** Journal entry holders (EventJournal): player id to when they were last in the area. */
	final Map<Integer, Long> shown = new HashMap<>();
	/** Players who abandoned the journal entry: it is not shown to them again for this event. */
	final Set<Integer> declined = new HashSet<>();
	/** Finished parts of the chain, as the journal lists them. */
	final List<String> history = new ArrayList<>();
	String cardKey = "";
	int cardRevision;
	/** Set once the event ended: the result line the journal shows until it removes the entry. */
	String outcome;
	/** The held road this event retakes (a chain with trigger "occupied"), else null. */
	EventWorld.Hold hold;
	/** Boss phase shield (Guild Wars 2's Shadow Behemoth): the leader cannot be harmed while its phase reinforcements stand. */
	boolean shielded;
	final Set<Integer> phaseAdds = new HashSet<>();
	int phaseTotal;
	EventSpawns.Scaling bossScaling;
	long nextRescale;
	volatile boolean finished;

	DynamicEvent(long key, EventChain chain, Anchor anchor, EventSpawns.Pool pool, long now) {
		this.key = key;
		this.chain = chain;
		this.anchor = anchor;
		this.pool = pool;
		this.spot = anchor.start();
	}

	public long key() {
		return key;
	}

	public Anchor anchor() {
		return anchor;
	}

	public synchronized Spot spot() {
		return spot;
	}

	public boolean finished() {
		return finished;
	}

	/** The stage's spot the Warden heads for: the camp while escorting, else the event's current spot. */
	public synchronized Spot wardenGoal() {
		return stage != null && stage.kind() == EventChain.Type.ESCORT ? pool.camp() : spot;
	}

	public synchronized boolean monster(int objectId) {
		return alive.contains(objectId);
	}

	/** Participants for scaling: each player counts 1, each bot the configured weight; capped like Guild Wars 2 (10 normal, more for group events). */
	static double participants(int humans, int bots, int botWeightPercent) {
		return Math.max(1, humans + bots * Math.clamp(botWeightPercent, 0, 100) / 100d);
	}

	static double capped(double participants, boolean group, int cap, int groupCap) {
		return Math.min(participants, Math.max(1, group ? groupCap : cap));
	}

	/**
	 * Live KILL scaling: the stage's target for the participants now, never below what was already defeated or is standing.
	 * Returns the new required count; what is still to arrive is required - defeated - standing.
	 */
	static int liveRequired(int target, int defeated, int standing) {
		return Math.max(target, defeated + standing);
	}

	/** count + perParticipant% for each participant beyond the first, rounded down; at least 1, at most 60. */
	static int scaled(int count, int perParticipantPercent, double participants) {
		return (int) Math.clamp(count + Math.floor(perParticipantPercent / 100d * Math.max(0, participants - 1)), 1, 60);
	}
}
