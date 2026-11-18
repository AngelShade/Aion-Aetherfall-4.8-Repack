package com.aionemu.gameserver.services.dynamicevents;

import java.util.List;

import com.aionemu.gameserver.model.gameobjects.player.Player;

/** The bot side of an event, provided by the Playerbots module (PlayerBotDynamicEvents). Without it events run with players only. */
public interface EventBots {

	/** Free world bots near the event answer its call; returns the bots that joined (at most max). */
	List<Player> recruit(DynamicEvent event, int max);

	/** The event ended: its bots and its Warden go back to what they were doing. */
	void released(DynamicEvent event);

	/**
	 * A scheduled chain's start points: per map with a player of a fitting level out on the roads, the free on-duty Road Warden
	 * on a field beat nearest to that player, whose beat level lies in [minLevel, maxLevel] and is not held by raiders.
	 */
	List<DynamicEvent.Anchor> scheduled(int minLevel, int maxLevel);

	EventBots NONE = new EventBots() {
		@Override
		public List<Player> recruit(DynamicEvent event, int max) {
			return List.of();
		}

		@Override
		public void released(DynamicEvent event) {
		}

		@Override
		public List<DynamicEvent.Anchor> scheduled(int minLevel, int maxLevel) {
			return List.of();
		}
	};
}
