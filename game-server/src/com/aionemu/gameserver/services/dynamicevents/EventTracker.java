package com.aionemu.gameserver.services.dynamicevents;

import java.util.Locale;

/** The chat lines a player reads about an event: its headline and its live objective with the stage timer. The journal card is EventJournal. */
public final class EventTracker {

	/** Guild Wars 2 labels harder parts "[Group Event]"; a leader's fight always is one. */
	static boolean group(DynamicEvent e) {
		return e.stage.group || e.stage.kind() == EventChain.Type.BOSS;
	}

	static String headline(DynamicEvent e) {
		return (group(e) ? "[Group Event] " : "") + e.chain.title + " - " + EventNarration.render(e, e.stage.title);
	}

	/** "Defeat the raiders and keep Arwen alive (3/6), 2:41 left". */
	static String progress(DynamicEvent e, long now) {
		String objective = EventNarration.render(e, e.stage.objective);
		String count = switch (e.stage.kind()) {
			case KILL -> " (" + Math.min(e.defeated, e.required) + "/" + e.required + ")";
			case ESCORT -> {
				var camp = e.pool.camp();
				yield " (" + Math.round(e.spot.distance(camp.x(), camp.y(), camp.z())) + " m to go)";
			}
			case BOSS -> e.shielded ? " (shielded: " + e.phaseAdds.size() + "/" + e.phaseTotal + " reinforcements left)" : e.phase == 0 ? "" : " (phase " + (e.phase + 1) + ")";
		};
		return objective + count + ", " + clock(Math.max(0, e.stageEnds - now)) + " left";
	}

	static String clock(long millis) {
		long seconds = (millis + 999) / 1000;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	private EventTracker() {
	}
}
