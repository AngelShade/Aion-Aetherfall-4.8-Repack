package com.aionemu.gameserver.services.dynamicevents;

import java.util.ArrayList;
import java.util.List;

/** One event chain as written in config/dynamic-events/chains/*.json (field names are the file's keys). */
public final class EventChain {

	public enum Type { KILL, ESCORT, BOSS }

	/** Which native monsters a stage spawns. BOSS is the chain's leader. */
	public enum Rating { NORMAL, ELITE, BOSS }

	public static final String END = "end", FAIL = "fail";
	/** How a chain starts: a Road Warden asking for help, or players coming near a road the raiders hold (EventWorld). */
	public static final String WARDEN = "warden", OCCUPIED = "occupied";
	public static final List<String> SPEAKERS = List.of("warden", "boss", "notice", "bots");

	public String id = "", title = "", first = "";
	public int minLevel = 1, maxLevel = 65, weight = 1;
	public String trigger = WARDEN;
	/** A failed chain leaves the raiders holding the road (EventWorld) until players retake it or the hold expires. */
	public boolean occupyOnFailure;
	/** Optional fixed times, like a world boss timer: every everyMinutes, at atMinute past the period (server time). */
	public Schedule schedule;
	public List<Stage> stages = new ArrayList<>();

	public static final class Schedule {
		public int everyMinutes, atMinute;
	}

	public static final class Stage {
		public String id = "", type = "KILL", title = "", objective = "", success = END, failure = FAIL, rating = "NORMAL";
		/** Base count of monsters to defeat (KILL), ambushes on the way (ESCORT) or reinforcements per phase (BOSS). */
		public int count = 1;
		/** Extra count per participant beyond the first, in percent (100 = one more per participant). */
		public int perParticipant = 0;
		/** Monsters alive at once (KILL waves, ambush and reinforcement size). */
		public int wave = 3;
		public int seconds = 240;
		/** The Warden must survive this stage. */
		public boolean protect;
		/** A failure branch: the story's recovery event. It pays like any other part. */
		public boolean setback;
		/** A [Group Event]: shown as one, and scaled for up to the group cap instead of the normal cap. */
		public boolean group;
		/** BOSS only: health percents at which the boss shields itself, speaks and calls reinforcements, highest first. */
		public List<Integer> phases = new ArrayList<>();
		/** Narration, each line "speaker|text". */
		public Say say = new Say();

		public Type kind() {
			return Type.valueOf(type);
		}

		public Rating monsters() {
			return Rating.valueOf(rating);
		}
	}

	/** start: as the stage opens; progress: spread over its quarters; success / failure: as it ends; phase: one per BOSS phase, in order. */
	public static final class Say {
		public List<String> start = new ArrayList<>(), progress = new ArrayList<>(), success = new ArrayList<>(),
			failure = new ArrayList<>(), phase = new ArrayList<>();
	}

	public Stage stage(String id) {
		for (Stage s : stages)
			if (s.id.equals(id))
				return s;
		return null;
	}

	public boolean levels(int level) {
		return level >= minLevel && level <= maxLevel;
	}
}
