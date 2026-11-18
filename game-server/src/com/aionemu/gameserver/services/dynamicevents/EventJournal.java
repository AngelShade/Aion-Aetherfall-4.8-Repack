package com.aionemu.gameserver.services.dynamicevents;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION.ActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;

/**
 * The event's entry in the native quest journal and tracker, through the Help Request card pipeline (AetherfallCore
 * help_quest.h slot 3, custom quest 14979). The wrapper quest only displays the card: it never completes or pays; rewards
 * come from EventRewards. A changed card bumps the wrapper's quest variable, which is what makes the client read it again.
 */
public final class EventJournal {

	public static final int QUEST = 14979;
	/** The client slot after the three help slots (help_quest.h QUESTS[3]). */
	public static final int SLOT = 3;
	/** A player who left the event area keeps the entry this long, so walking a few steps out does not flicker it. */
	static final long AWAY_MS = 30_000;
	/** The result stays in the journal this long after the event ends. */
	static final long RESULT_MS = 20_000;
	private static final Map<Integer, DynamicEvent> SHOWN = new ConcurrentHashMap<>();

	public static boolean journalQuest(int quest) {
		return quest == QUEST;
	}

	/** The revision written into the wrapper's packed quest variable: never 0, always changes with the counter. */
	static int revision(int counter) {
		return (Math.floorMod(counter, 0x7fffffe) + 1) << 4;
	}

	/** Whether a player keeps an entry: shown before, still within the away grace, and never abandoned for this event. */
	static boolean keeps(long lastSeen, long now, boolean declined) {
		return !declined && now - lastSeen <= AWAY_MS;
	}

	/** Each tick, under the event's monitor: show the entry to players in the area, refresh it when its text changed, drop absent players. */
	static void sync(DynamicEvent e, Collection<Player> audience, long now) {
		for (Player p : audience) {
			if (e.declined.contains(p.getObjectId()))
				continue;
			DynamicEvent other = SHOWN.get(p.getObjectId());
			if (other != null && other != e && !other.finished())
				continue;
			e.shown.put(p.getObjectId(), now);
			if (SHOWN.put(p.getObjectId(), e) != e || !active(p))
				show(p, e);
		}
		String key = cardKey(e, now);
		boolean changed = !key.equals(e.cardKey);
		if (changed) {
			e.cardKey = key;
			e.cardRevision++;
		}
		for (var it = e.shown.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			Player p = World.getInstance().getPlayer(entry.getKey());
			if (p == null || !p.isOnline() || !keeps(entry.getValue(), now, e.declined.contains(entry.getKey()))) {
				it.remove();
				if (SHOWN.remove(entry.getKey(), e) && p != null && p.isOnline())
					hide(p);
			} else if (changed)
				refresh(p, e);
		}
	}

	/** The event ended: show its result, then take the entry out of every journal that still holds it. */
	static void close(DynamicEvent e, String outcome) {
		e.outcome = outcome;
		e.cardRevision++;
		var holders = new ArrayList<>(e.shown.keySet());
		for (int id : holders) {
			Player p = World.getInstance().getPlayer(id);
			if (p != null && p.isOnline() && SHOWN.get(id) == e)
				refresh(p, e);
		}
		ThreadPoolManager.getInstance().schedule(() -> {
			for (int id : holders) {
				if (!SHOWN.remove(id, e))
					continue;
				Player p = World.getInstance().getPlayer(id);
				if (p != null && p.isOnline())
					hide(p);
			}
		}, RESULT_MS);
	}

	private static boolean active(Player p) {
		var q = p.getQuestStateList().getQuestState(QUEST);
		return q != null && q.getStatus() == QuestStatus.START;
	}

	private static void show(Player p, DynamicEvent e) {
		if (!active(p) && !QuestService.startQuest(new QuestEnv(null, p, QUEST), QuestStatus.START, false))
			return;
		refresh(p, e);
	}

	private static void refresh(Player p, DynamicEvent e) {
		var q = p.getQuestStateList().getQuestState(QUEST);
		if (q == null || q.getStatus() != QuestStatus.START)
			return;
		int value = revision(e.cardRevision);
		if (q.getQuestVars().getQuestVars() != value) {
			q.setQuestVar(value);
			PacketSendUtility.sendPacket(p, new SM_QUEST_ACTION(ActionType.UPDATE, q));
		}
	}

	private static void hide(Player p) {
		if (active(p))
			QuestService.abandonQuest(p, QUEST);
	}

	/** Login: an entry whose event is gone (events end with a restart) is removed; a live one is refreshed. */
	public static void entered(Player p) {
		if (p.isPlayerBot())
			return;
		DynamicEvent e = SHOWN.get(p.getObjectId());
		if (e == null) {
			hide(p);
			return;
		}
		synchronized (e) {
			if (e.shown.containsKey(p.getObjectId()))
				refresh(p, e);
			else
				hide(p);
		}
	}

	/** Native Abandon on the entry: this event is not shown to the player again; it still counts their help. */
	public static void abandoned(Player p) {
		DynamicEvent e = SHOWN.remove(p.getObjectId());
		if (e != null)
			synchronized (e) {
				e.declined.add(p.getObjectId());
				e.shown.remove(p.getObjectId());
			}
		hide(p);
		PacketSendUtility.sendMessage(p, "[Event] Removed from your journal. You can still take part while you are nearby.");
	}

	/** The journal's in-page Track button (help_journal.h posts it with this slot). */
	public static String action(Player p, String operation) {
		if (!"track".equals(operation))
			return "Choose an event control.";
		DynamicEvent e = SHOWN.get(p.getObjectId());
		if (e == null)
			return "That event has ended.";
		PacketSendUtility.sendMessage(p, "[Event] Tracking " + e.chain.title + " in the quest tracker.");
		return "ok";
	}

	// ----- the card -----

	/** Changes only when what the player reads changes: the timer counts in whole minutes. */
	static String cardKey(DynamicEvent e, long now) {
		return e.stage.id + "|" + e.defeated + "/" + e.required + "|" + e.phase + "|" + e.shielded + ":" + e.phaseAdds.size() + "|"
			+ (e.stage.kind() == EventChain.Type.ESCORT ? Math.round(distance(e) / 10) : 0) + "|" + minutes(e.stageEnds - now) + "|" + e.outcome;
	}

	static long minutes(long millis) {
		return Math.max(0, (millis + 59_999) / 60_000);
	}

	private static double distance(DynamicEvent e) {
		var camp = e.pool.camp();
		return e.spot.distance(camp.x(), camp.y(), camp.z());
	}

	/** card2 for the player's entry: title, step, "STATUS map x y z", tracker lines, then the details page. Empty without an entry. */
	public static String card(Player p) {
		DynamicEvent e = SHOWN.get(p.getObjectId());
		if (e == null)
			return "";
		synchronized (e) {
			return e.stage == null ? "" : card(e, p.getObjectId(), System.currentTimeMillis());
		}
	}

	/** The card text itself, under the event's monitor. help_quest.h apply_card reads exactly four line breaks before the page. */
	static String card(DynamicEvent e, int playerId, long now) {
		var spot = e.spot;
		return single((EventTracker.group(e) ? "[Group Event] " : "Event: ") + e.chain.title) + "\n" + single(step(e, now)) + "\n" + (e.outcome == null ? "ACTIVE" : "ENDED") + " " + e.anchor.map() + " " + spot.x() + " "
			+ spot.y() + " " + spot.z() + "\n" + tracker(e, now).replace('\n', ' ') + "\n" + page(e, playerId, now);
	}

	private static String single(String text) {
		String t = text.replace('\n', ' ').replace('\r', ' ');
		return t.length() > 120 ? t.substring(0, 120) : t;
	}

	/** The one native step line: the objective and its count. */
	static String step(DynamicEvent e, long now) {
		if (e.outcome != null)
			return e.outcome;
		return EventNarration.render(e, e.stage.objective) + count(e);
	}

	private static String count(DynamicEvent e) {
		return switch (e.stage.kind()) {
			case KILL -> " " + Math.min(e.defeated, e.required) + "/" + e.required;
			case ESCORT -> " (" + Math.round(distance(e) / 10) * 10 + " m to go)";
			case BOSS -> e.shielded ? " - shielded, " + e.phaseAdds.size() + "/" + e.phaseTotal + " reinforcements left" : e.phase == 0 ? "" : " (phase " + (e.phase + 1) + ")";
		};
	}

	private static String left(DynamicEvent e, long now) {
		long m = minutes(e.stageEnds - now);
		return m <= 1 ? "Less than a minute left" : "About " + m + " minutes left";
	}

	/** The tracker rows under the title, like a help request's: the objective with its count in yellow, then the time. */
	static String tracker(DynamicEvent e, long now) {
		if (e.outcome != null)
			return escape(e.outcome);
		String objective = escape(EventNarration.render(e, e.stage.objective));
		String count = count(e).trim();
		return objective + (count.isEmpty() ? "" : " <font color=\"#ffd84a\">" + escape(count) + "</font>") + "<br>" + escape(left(e, now));
	}

	/** The details page in the native quest page layout (as PlayerBotHelpObjectives.page): head, stage bullets, Locate and Track, summary, rewards. */
	static String page(DynamicEvent e, int playerId, long now) {
		var html = new StringBuilder("<html><body text=\"#302405\" topmargin=\"5\" leftmargin=\"15\">");
		html.append("<p> <font font_xml=\" v3_quest_title\" > ").append(escape((EventTracker.group(e) ? "[Group Event] " : "Event: ") + e.chain.title)).append("</font> </p>");
		html.append("<table>");
		for (String done : e.history)
			html.append(row(done, true));
		if (e.outcome == null)
			html.append(row(EventNarration.render(e, e.stage.title) + ": " + step(e, now), false));
		else
			html.append(row(e.outcome, true));
		html.append("</table><p> </p>");
		html.append("<p>").append(button("af_help_location", "Locate", e.anchor.map() == 0)).append(" ").append(button("af_help_track", "Track", e.outcome != null))
			.append("</p><p> </p>");
		var map = DataManager.WORLD_MAPS_DATA == null ? null : DataManager.WORLD_MAPS_DATA.getTemplate(e.anchor.map());
		html.append(line("Where: " + (map == null ? "this map" : map.getName()) + ", on " + e.anchor.road() + "."));
		html.append(line(e.outcome != null ? "This event has ended. It leaves your journal shortly." : left(e, now) + " for this part. Stay nearby to take part."));
		var mine = e.contributions.get(playerId);
		double top = e.contributions.values().stream().mapToDouble(c -> c.chain).max().orElse(0);
		var tier = mine == null ? EventRewards.Tier.NONE
			: EventRewards.tier(mine.chain, top, com.aionemu.gameserver.configs.main.DynamicEventConfig.GOLD_SHARE, com.aionemu.gameserver.configs.main.DynamicEventConfig.SILVER_SHARE);
		html.append(line(tier == EventRewards.Tier.NONE ? "Your contribution: none yet. Fight the event's enemies to earn a reward."
			: "Your contribution so far: " + tier.label() + "."));
		html.append("<p> </p><p><font font_xml=\"v3_quest_subtitle\">Basic Reward</font></p>");
		html.append(line("Every part pays XP and Kinah by your tier: Gold in full, Silver 85%, Bronze 75%, and half if the part fails."));
		html.append(line("Defeating the leader opens its chest once a day, and a bonus chest once a day per account."));
		return html.append("</body></html>").toString().replace('\n', ' ');
	}

	/** One objective row, as the native step head/tail draw it: working or finished bullet, finished text greyed. */
	private static String row(String text, boolean done) {
		return "<tr><td width=\"30\" valign=\"top\"><button skin=\"" + (done ? "v5_quest_finished_bullet" : "v5_quest_working_bullet") + "\" width=\"28\" height=\"21\"></button></td>"
			+ "<td align=\"left\" valign=\"top\"><font font_xml=\"v3_quest_list\"><p align=\"left\">" + (done ? "<font color=\"#6F6F64\">" + escape(text) + "</font>" : escape(text))
			+ "</p></font></td></tr>";
	}

	/** The native in-page quest button; AetherfallCore help_journal.h answers these hrefs for every slot. */
	private static String button(String href, String label, boolean disabled) {
		return "<button href=\"" + href + "\" font_xml=\"v3_quest_button\" skin=\"v5_quest_button\" width=\"73\" height=\"26\" disabled=\"" + disabled + "\">" + label + "</button>";
	}

	private static String line(String text) {
		return "<p><font font_xml=\"quest_summary\">" + escape(text) + "</font></p>";
	}

	/** Plain text for the native HTML view: markup characters and its "[%" template tokens never pass through. */
	static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("[%", "[ %").replace('\n', ' ').replace('\r', ' ');
	}

	private EventJournal() {
	}
}
