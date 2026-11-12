package com.aionemu.gameserver.model.instance.instancescore;

import java.util.concurrent.ThreadLocalRandom;

import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.instance.playerreward.PvpInstancePlayerReward;

/** Score, faction starting side, and the same match clock sent to the client. */
public final class DredgionInstanceScore extends PvpInstanceScore<PvpInstancePlayerReward> {

	public static final int PREPARATION_MS = 120000;
	public static final int MATCH_MS = 2400000;

	private final Race starboard;
	private final int elyosOpening = ThreadLocalRandom.current().nextInt();
	private final int asmodianOpening = ThreadLocalRandom.current().nextInt();
	private volatile long openedAt;

	public DredgionInstanceScore(int winnerAp, int loserAp, int drawAp, Race starboard) {
		super(winnerAp, loserAp, drawAp);
		this.starboard = starboard;
	}

	public void startClock(long now) {
		openedAt = now + PREPARATION_MS;
	}

	public int timeRemaining(long now) {
		long opens = openedAt;
		if (opens == 0)
			return 0;
		return (int) Math.max(0, now < opens ? opens - now : opens + MATCH_MS - now);
	}

	public boolean starboard(Race race) {
		return race == starboard;
	}

	public int openingDraw(Race race) {
		return race == Race.ELYOS ? elyosOpening : asmodianOpening;
	}
}
