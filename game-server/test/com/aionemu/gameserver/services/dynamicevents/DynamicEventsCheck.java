package com.aionemu.gameserver.services.dynamicevents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Dynamic events (config/dynamic-events/README.md): chain files, branch validation, scaling, rewards, narration and hook wiring. No World/SQL/tasks. */
public final class DynamicEventsCheck {
	static int checks;

	static void check(boolean value, String why) {
		checks++;
		if (!value)
			throw new AssertionError(why);
	}

	public static void main(String[] args) throws Exception {
		var root = args.length > 0 ? Path.of(args[0]) : Path.of(".");
		catalogue(root);
		validation();
		choice();
		scaling();
		rewards();
		narration();
		wiring(root);
		journal();
		journalWiring(root);
		guildWars(root);
		assistant(root);
		System.out.println("OK: " + checks + " dynamic event checks; no World/SQL/tasks");
	}

	static void catalogue(Path root) {
		var chains = EventCatalogue.load(root.resolve("config/dynamic-events/chains"));
		check(chains.size() == 3, "All three shipped chains load: " + chains.size());
		var reclaim = chains.stream().filter(c -> c.id.equals("road-reclaim")).findFirst().orElseThrow();
		check(reclaim.trigger.equals(EventChain.OCCUPIED) && !reclaim.occupyOnFailure && reclaim.stages.size() == 2, "Retake the Road starts on a held road");
		var ambush = chains.stream().filter(c -> c.id.equals("road-ambush")).findFirst().orElseThrow();
		check(ambush.stages.size() == 6 && ambush.first.equals("ambush"), "Ambush on the Road has six stages from the ambush");
		check(ambush.stage("warlord").kind() == EventChain.Type.BOSS && ambush.stage("warlord").phases.equals(List.of(75, 50, 25)), "The warlord fights in three phases");
		check(ambush.stage("ambush").failure.equals("road-lost") && ambush.stage("road-lost").setback, "Losing the ambush opens the setback branch");
		check(ambush.stage("trail").kind() == EventChain.Type.ESCORT && ambush.stage("trail").protect, "The escort protects the Warden");
		check(ambush.stage("road-lost").success.equals("camp"), "Retaking the road still leads to the camp");
		check(ambush.occupyOnFailure && ambush.trigger.equals(EventChain.WARDEN), "A lost ambush leaves the raiders holding the road");
		check(ambush.schedule != null && ambush.schedule.everyMinutes == 120 && ambush.schedule.atMinute == 45, "The ambush also runs on a timer, every two hours at :45");
		check(ambush.stage("camp").group && ambush.stage("camp-alerted").group && !ambush.stage("ambush").group, "The camp stages are Group Events");
		var raid = chains.stream().filter(c -> c.id.equals("supply-raid")).findFirst().orElseThrow();
		check(raid.stage("captain").say.phase.size() == 2, "The raid captain speaks at both phases");
		for (var c : chains)
			for (var s : c.stages)
				check(!s.say.start.isEmpty() && !s.say.success.isEmpty() && !s.say.failure.isEmpty(), c.id + "/" + s.id + " narrates its start and both endings");
		check(EventCatalogue.load(root.resolve("config/dynamic-events/missing")).isEmpty(), "A missing folder loads no chains");
	}

	static String chain(String stages) {
		return "{\"id\":\"t\",\"title\":\"Test\",\"first\":\"a\",\"stages\":[" + stages + "]}";
	}

	static String stage(String id, String type, String rating, String success, String failure, String extra) {
		return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"rating\":\"" + rating + "\",\"title\":\"T\",\"objective\":\"O\",\"success\":\"" + success
			+ "\",\"failure\":\"" + failure + "\"" + extra + ",\"say\":{\"start\":[\"notice|Go\"]}}";
	}

	static boolean rejected(String json, String reason) {
		try {
			EventCatalogue.parse(json);
			return false;
		} catch (IllegalArgumentException e) {
			return e.getMessage().contains(reason);
		}
	}

	static void validation() {
		check(EventCatalogue.parse(chain(stage("a", "KILL", "NORMAL", "end", "fail", ""))).stages.size() == 1, "A one-stage chain is valid");
		check(rejected(chain(stage("a", "KILL", "NORMAL", "b", "fail", "")), "unknown b"), "Links to unknown stages are rejected");
		check(rejected(chain(stage("a", "KILL", "NORMAL", "end", "fail", "") + "," + stage("b", "KILL", "NORMAL", "end", "fail", "")), "never reached"),
			"Unreachable stages are rejected");
		check(rejected(chain(stage("a", "KILL", "NORMAL", "b", "fail", "") + "," + stage("b", "KILL", "NORMAL", "b", "b", "")), "loops without an ending"),
			"A loop without an exit is rejected");
		check(EventCatalogue.parse(chain(stage("a", "KILL", "NORMAL", "b", "fail", "") + "," + stage("b", "KILL", "NORMAL", "a", "end", ""))).stages.size() == 2,
			"A loop with an exit is allowed (retake, then return)");
		check(rejected(chain(stage("a", "BOSS", "NORMAL", "end", "fail", "")), "BOSS rating"), "A BOSS stage spawns the boss");
		check(rejected(chain(stage("a", "KILL", "BOSS", "end", "fail", "")), "only a BOSS stage"), "Only BOSS stages spawn the boss");
		check(rejected(chain(stage("a", "BOSS", "BOSS", "end", "fail", ",\"phases\":[50]")), "one phase line"), "Every phase needs its line");
		check(rejected(chain("{\"id\":\"a\",\"type\":\"BOSS\",\"rating\":\"BOSS\",\"title\":\"T\",\"objective\":\"O\",\"phases\":[50,60],"
			+ "\"say\":{\"start\":[\"notice|Go\"],\"phase\":[\"boss|a\",\"boss|b\"]}}"), "fall"),
			"Phases must fall");
		check(rejected(chain("{\"id\":\"a\",\"type\":\"KILL\",\"rating\":\"NORMAL\",\"title\":\"T\",\"objective\":\"O\",\"say\":{\"start\":[\"king|Hi\"]}}"), "speaker"),
			"Unknown speakers are rejected");
		check(rejected(chain("{\"id\":\"a\",\"type\":\"KILL\",\"rating\":\"NORMAL\",\"title\":\"T\",\"objective\":\"O\",\"say\":{\"start\":[\"notice|{player} go\"]}}"), "placeholder"),
			"Unknown placeholders are rejected");
		check(rejected(chain("{\"id\":\"a\",\"type\":\"DANCE\",\"rating\":\"NORMAL\",\"title\":\"T\",\"objective\":\"O\"}"), "unknown type"), "Unknown stage types are rejected");
		check(rejected("{\"id\":\"Bad Id\",\"title\":\"T\",\"first\":\"a\",\"stages\":[]}", "id must"), "Chain ids are checked");
		check(rejected(chain(stage("end", "KILL", "NORMAL", "end", "fail", "")), "stage id"), "end and fail are reserved");
	}

	static void choice() {
		var low = new EventChain();
		low.id = "low";
		low.minLevel = 1;
		low.maxLevel = 20;
		low.weight = 1;
		var high = new EventChain();
		high.id = "high";
		high.minLevel = 15;
		high.maxLevel = 65;
		high.weight = 3;
		var both = List.of(low, high);
		check(DynamicEventService.pick(both, 10, 7, EventChain.WARDEN) == low, "Only fitting chains are chosen");
		check(DynamicEventService.pick(both, 50, 0, EventChain.WARDEN) == high, "A single fitting chain always wins");
		check(DynamicEventService.pick(both, 70, 0, EventChain.WARDEN) == null, "No chain fits above every band");
		int lows = 0;
		for (int roll = 0; roll < 400; roll++)
			if (DynamicEventService.pick(both, 18, roll, EventChain.WARDEN) == low)
				lows++;
		check(lows == 100, "Weights split the choice 1:3: " + lows);
		check(DynamicEventService.pick(both, 18, 0, EventChain.OCCUPIED) == null, "Warden chains never start on a held road");
		check(DynamicEventService.admitted(true, 10, 5, 0, 4, 0, 1, false), "A free beat on a quiet map is admitted");
		check(!DynamicEventService.admitted(false, 10, 5, 0, 4, 0, 1, false), "The switch stops new events");
		check(!DynamicEventService.admitted(true, 4, 5, 0, 4, 0, 1, false), "A cooling beat waits");
		check(!DynamicEventService.admitted(true, 10, 5, 4, 4, 0, 1, false), "The server cap holds");
		check(!DynamicEventService.admitted(true, 10, 5, 0, 4, 1, 1, false), "The map cap holds");
		check(!DynamicEventService.admitted(true, 10, 5, 0, 4, 0, 1, true), "A Warden runs one event at a time");
	}

	static void scaling() {
		check(DynamicEvent.participants(1, 0, 50) == 1, "One player is one participant");
		check(DynamicEvent.participants(2, 2, 50) == 3, "Bots count half by default");
		check(DynamicEvent.participants(0, 0, 50) == 1, "Scaling never drops below one participant");
		check(DynamicEvent.scaled(6, 50, 1) == 6, "A lone player faces the base count");
		check(DynamicEvent.scaled(6, 50, 3) == 7 && DynamicEvent.scaled(6, 50, 5) == 8, "Half a monster more per extra participant");
		check(DynamicEvent.scaled(6, 0, 20) == 6, "perParticipant 0 keeps the count fixed");
		check(DynamicEvent.scaled(40, 300, 40) == 60, "Counts are capped at 60");
		check(EventSpawns.bossHealth(1, 35, 500) == 1f, "A lone player meets the native boss");
		check(Math.abs(EventSpawns.bossHealth(3, 35, 500) - 1.7f) < 1e-5, "35% more health per extra participant");
		check(EventSpawns.bossHealth(40, 35, 500) == 5f, "Boss health is capped");
		check(EventSpawns.levelFits(30, 27, 3, 2) && EventSpawns.levelFits(30, 32, 3, 2) && !EventSpawns.levelFits(30, 33, 3, 2) && !EventSpawns.levelFits(30, 26, 3, 2),
			"Raiders lie within three below to two above the beat");
		check(EventSpawns.ratingRank(com.aionemu.gameserver.model.templates.npc.NpcRating.LEGENDARY) > EventSpawns.ratingRank(com.aionemu.gameserver.model.templates.npc.NpcRating.HERO)
			&& EventSpawns.ratingRank(com.aionemu.gameserver.model.templates.npc.NpcRating.HERO) > EventSpawns.ratingRank(com.aionemu.gameserver.model.templates.npc.NpcRating.ELITE),
			"Legendary leads before Hero before Elite");
		check(EventSpawns.leaderHealth(com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL) == 6f && EventSpawns.leaderHealth(com.aionemu.gameserver.model.templates.npc.NpcRating.ELITE) == 3f
			&& EventSpawns.leaderHealth(com.aionemu.gameserver.model.templates.npc.NpcRating.HERO) == 1f && EventSpawns.leaderHealth(com.aionemu.gameserver.model.templates.npc.NpcRating.LEGENDARY) == 1f,
			"A leader below Hero is raised to boss health (probe 18 Nov: 26 of 43 sampled leaders were normal-rated)");
		check(EventSpawns.leaderDamage(com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL) == 1.5f && EventSpawns.leaderDamage(com.aionemu.gameserver.model.templates.npc.NpcRating.HERO) == 1f,
			"and hits harder; native Heroes stay native");
		check(EventSpawns.lieutenantHealth(com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL) == 2.5f && EventSpawns.lieutenantHealth(com.aionemu.gameserver.model.templates.npc.NpcRating.ELITE) == 1f
			&& EventSpawns.lieutenantDamage(com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL) == 1.25f, "Lieutenants drawn from normal monsters are tougher than raiders");
	}

	static void rewards() {
		check(EventRewards.tier(50, 100, 50, 20) == EventRewards.Tier.GOLD, "Half the top share is Gold");
		check(EventRewards.tier(49, 100, 50, 20) == EventRewards.Tier.SILVER, "Just under is Silver");
		check(EventRewards.tier(19, 100, 50, 20) == EventRewards.Tier.BRONZE, "Any contribution earns Bronze");
		check(EventRewards.tier(0, 100, 50, 20) == EventRewards.Tier.NONE, "No contribution earns nothing");
		check(EventRewards.tier(10, 0, 50, 20) == EventRewards.Tier.NONE, "No top contribution earns nothing");
		check(EventRewards.tierPercent(EventRewards.Tier.GOLD, 85, 75) == 100 && EventRewards.tierPercent(EventRewards.Tier.SILVER, 85, 75) == 85
			&& EventRewards.tierPercent(EventRewards.Tier.BRONZE, 85, 75) == 75 && EventRewards.tierPercent(EventRewards.Tier.NONE, 85, 75) == 0, "Gold 100%, Silver 85%, Bronze 75% (Guild Wars 2)");
		check(EventRewards.xp(1_000_000, 70, 100, true, 50) == 70_000, "A won part at Gold pays 7% of a level (Guild Wars 2)");
		check(EventRewards.xp(1_000_000, 70, 85, true, 50) == 59_500 && EventRewards.xp(1_000_000, 70, 75, true, 50) == 52_500, "Silver and Bronze pay their share");
		check(EventRewards.xp(1_000_000, 70, 100, false, 50) == 35_000 && EventRewards.xp(1_000_000, 70, 75, false, 50) == 26_250, "A failed part pays half");
		check(EventRewards.xp(0, 70, 100, true, 50) == 0, "Maximum level earns no XP");
		check(EventRewards.kinah(40, 120, 100, true, 50) == 4_800 && EventRewards.kinah(40, 120, 100, false, 50) == 2_400, "Kinah follows the level, halved on failure");
		check(EventRewards.eligible(30, 39, 10) && !EventRewards.eligible(30, 40, 10) && !EventRewards.eligible(40, 30, 10), "Ten or more levels away earns nothing");
		check(EventRewards.rewardLevel(25, 30) == 30 && EventRewards.rewardLevel(35, 30) == 35, "Below the event its level pays; above it, your own");
		check(EventRewards.Tier.GOLD.label().equals("Gold"), "Tiers read as words");
	}

	static void narration() {
		String line = EventNarration.render("{warden} on {road}: {enemy} came {place}, {count} of them. {boss}!", "Arwen", "Kishar to Splendora", "Raid Lord",
			"Krall Raiders", "to the north", 7);
		check(line.equals("Arwen on Kishar to Splendora: Krall Raiders came to the north, 7 of them. Raid Lord!"), "Every placeholder renders: " + line);
		check(EventNarration.plural("Krall Raider").equals("Krall Raiders") && EventNarration.plural("Lycan Sentry").equals("Lycan Sentries")
			&& EventNarration.plural("Spriggans").equals("Spriggans") && EventNarration.plural("Monkey").equals("Monkeys"), "Crowds read plural");
		check(EventNarration.direction(1, 0).equals("east") && EventNarration.direction(0, 1).equals("north") && EventNarration.direction(-1, -1).equals("southwest"),
			"Directions follow the map");
		check(EventNarration.speaker("warden|Hi|there").equals("warden") && EventNarration.text("warden|Hi|there").equals("Hi|there"), "Only the first bar splits");
		var three = List.of("a", "b", "c");
		check("a".equals(EventNarration.quarterLine(three, 1)) && "b".equals(EventNarration.quarterLine(three, 2)) && "c".equals(EventNarration.quarterLine(three, 3)),
			"Three lines fill three quarters");
		var two = List.of("a", "b");
		check("a".equals(EventNarration.quarterLine(two, 1)) && EventNarration.quarterLine(two, 2) == null && "b".equals(EventNarration.quarterLine(two, 3)),
			"Two lines open and close the stage");
		check(EventNarration.quarterLine(List.of(), 1) == null && EventNarration.quarterLine(three, 4) == null, "No line past the last quarter");
		check(EventTracker.clock(161_000).equals("2:41") && EventTracker.clock(1).equals("0:01") && EventTracker.clock(0).equals("0:00"), "Timers read m:ss");
	}

	static void journal() {
		check(EventJournal.revision(0) == 16 && EventJournal.revision(1) == 32 && EventJournal.revision(0x7fffffe) == 16, "Revisions are never 0 and wrap inside the packed variable");
		check(EventJournal.revision(-1) > 0, "A wrapped counter still gives a positive revision");
		check(EventJournal.keeps(0, 30_000, false) && !EventJournal.keeps(0, 30_001, false), "A player away for 30 s loses the entry");
		check(!EventJournal.keeps(0, 0, true), "An abandoned entry is not shown again");
		check(EventJournal.minutes(0) == 0 && EventJournal.minutes(1) == 1 && EventJournal.minutes(60_000) == 1 && EventJournal.minutes(60_001) == 2, "Time left counts whole minutes");
		check(EventJournal.escape("<b>[%x]&").equals("&lt;b&gt;[ %x]&amp;"), "Markup and template tokens never reach the native view");
		check(EventJournal.journalQuest(14979) && !EventJournal.journalQuest(14996) && EventJournal.SLOT == 3, "The event wrapper is its own quest after the three help slots");
		var chain = EventCatalogue.parse("{\"id\":\"t\",\"title\":\"Test\",\"first\":\"a\",\"stages\":[{\"id\":\"a\",\"type\":\"KILL\",\"rating\":\"NORMAL\",\"title\":\"Ambush on {road}\","
			+ "\"objective\":\"Defeat {count} raiders\",\"say\":{\"start\":[\"notice|Go\"]}}]}");
		var anchor = new DynamicEvent.Anchor(220030000, 1, com.aionemu.gameserver.model.Race.ASMODIANS, 20, 7, "Arwen", "beat:x", "Kishar to Splendora",
			new DynamicEvent.Spot(10, 20, 30), List.of());
		var pool = new EventSpawns.Pool(List.of(), List.of(), null, new DynamicEvent.Spot(60, 20, 30), List.of(new DynamicEvent.Spot(60, 20, 30)), "Krall Raider");
		long now = 1_000_000;
		var e = new DynamicEvent(1, chain, anchor, pool, now);
		e.stage = chain.stage("a");
		e.required = 6;
		e.defeated = 2;
		e.stageEnds = now + 150_000;
		String card = EventJournal.card(e, 99, now);
		check(card.chars().filter(c -> c == '\n').count() == 4, "The card has exactly the four line breaks apply_card reads");
		var parts = card.split("\n", 5);
		check(parts[0].equals("Event: Test"), "Title line: " + parts[0]);
		check(parts[1].equals("Defeat 6 raiders 2/6"), "Step line carries the objective and its count: " + parts[1]);
		check(parts[2].equals("ACTIVE 220030000 10.0 20.0 30.0"), "State line is STATUS map x y z for Locate: " + parts[2]);
		check(parts[3].contains("<font color=\"#ffd84a\">2/6</font>") && parts[3].contains("About 3 minutes left"), "Tracker rows: count in yellow, then the time: " + parts[3]);
		check(parts[4].startsWith("<html>") && parts[4].endsWith("</html>") && parts[4].contains("href=\"af_help_location\"") && parts[4].contains("href=\"af_help_track\""),
			"The page has the native Locate and Track buttons");
		check(parts[4].contains("Ambush on Kishar to Splendora: Defeat 6 raiders 2/6") && parts[4].contains("v5_quest_working_bullet"), "The current part is the working bullet");
		check(!parts[4].contains("v5_quest_finished_bullet"), "No finished parts yet");
		e.history.add("Ambush on Kishar to Splendora: done");
		check(EventJournal.card(e, 99, now).contains("v5_quest_finished_bullet"), "Finished parts are greyed bullets");
		String key = EventJournal.cardKey(e, now);
		check(key.equals(EventJournal.cardKey(e, now + 10_000)), "Seconds passing do not refresh the card");
		check(!key.equals(EventJournal.cardKey(e, now + 100_000)), "A new minute does");
		e.defeated = 3;
		check(!key.equals(EventJournal.cardKey(e, now)), "A kill does");
		e.outcome = "Event complete: Test";
		String ended = EventJournal.card(e, 99, now);
		check(ended.split("\n", 5)[2].startsWith("ENDED ") && ended.split("\n", 5)[1].equals("Event complete: Test"), "An ended event shows its result, never COMPLETE (no Collect reward)");
		check(ended.contains("href=\"af_help_track\" font_xml=\"v3_quest_button\" skin=\"v5_quest_button\" width=\"73\" height=\"26\" disabled=\"true\""), "Track is off once it ended");
		check(!EventJournal.cardKey(e, now).equals(key), "The result refreshes the card");
	}

	static void journalWiring(Path root) throws Exception {
		var src = root.resolve("src/com/aionemu/gameserver");
		var http = Files.readString(src.resolve("services/PlayerBotHttpService.java"));
		check(http.indexOf("EventJournal.SLOT).equals(helpQuery.get(\"slot\"))") > 0
			&& http.indexOf("EventJournal.SLOT).equals(helpQuery.get(\"slot\"))") < http.indexOf("String title = PlayerBotHelpObjectives.title(helper)"),
			"The event card is routed before the help slots clamp the slot number");
		check(http.indexOf("EventJournal.SLOT).equals(helpValues.get(\"slot\"))") > 0
			&& http.indexOf("EventJournal.SLOT).equals(helpValues.get(\"slot\"))") < http.indexOf("synchronized (helper) { outcome = PlayerBotHelpObjectives.journal("),
			"The event's Track button is routed before the help journal");
		var delete = Files.readString(src.resolve("network/aion/clientpackets/CM_DELETE_QUEST.java"));
		check(delete.indexOf("EventJournal.journalQuest(questId)") > 0 && delete.indexOf("EventJournal.journalQuest(questId)") < delete.indexOf("PlayerBotHelpObjectives.helpQuest(questId)"),
			"Native Abandon on the event entry goes to the event module");
		var service = Files.readString(src.resolve("services/dynamicevents/DynamicEventService.java"));
		check(service.contains("EventJournal.sync(e, audience, now);") && service.contains("EventJournal.close(e, (success ? \"Event complete: \" : \"Event failed: \") + e.chain.title);"),
			"Every tick syncs the entry and every ending closes it");
		check(Files.readString(root.resolve("data/static_data/quest_data/quest_data.xml"))
			.contains("<quest id=\"14979\" name=\"World Event\" nameId=\"1801389\" minlevel_permitted=\"1\" max_repeat_count=\"255\" cannot_share=\"true\" category=\"QUEST\" />"),
			"Server quest data has the event wrapper");
		check(Files.readString(root.resolve("data/handlers/quest/aetherfall/_14979DynamicEvent.java")).contains("EventJournal.entered(env.getPlayer());"), "Login clears a stale entry");
		var client = root.resolve("../client-mods");
		var slots = Files.readString(client.resolve("aetherfall-core/help_quest.h"));
		check(slots.contains("constexpr int SLOTS=4;") && slots.contains("QUESTS[SLOTS]={14996,14998,14999,14979}") && slots.contains("TITLES[SLOTS]={1801381,1801385,1801387,1801389}")
			&& slots.contains("PROGRESSES[SLOTS]={1801382,1801386,1801388,1801390}"), "Core's fourth slot is quest 14979 with its own strings, at index EventJournal.SLOT");
		var strings = Files.readString(client.resolve("custom-quests/Quests/14979-DynamicEvent/strings.xml"));
		check(strings.contains("<id>1801389</id>") && strings.contains("STR_QUEST_NAME_Q14979") && strings.contains("<id>1801390</id>"), "The client package names the same strings");
		check(Files.readString(client.resolve("custom-quests/Quests/14979-DynamicEvent/quest.json")).contains("\"Id\": 14979"), "The client package is quest 14979");
		check(Files.readString(client.resolve("custom-quests/Quests/14979-DynamicEvent/dialog.html")).contains("[%dic:STR_AETHERFALL_EVENT_PROGRESS]"), "The journal page reads the event's progress string");
		var core = Files.readString(client.resolve("aetherfall-core/aetherfall_core.cpp"));
		int versionAt = core.indexOf("CORE_VERSION[]=\"") + "CORE_VERSION[]=\"".length();
		String coreVersion = core.substring(versionAt, core.indexOf('"', versionAt));
		check(coreVersion.startsWith("1.3.") && Integer.parseInt(coreVersion.substring(4)) >= 30
			&& Files.readString(client.resolve("aetherfall-core/prepare.py")).contains("NATIVE_VERSION = '" + coreVersion + "'"), "Core and its builder agree, at 1.3.30 or later");
		check(Files.readString(root.resolve("config/administration/commands.properties")).contains("dynevent = 9"), "The GM shortcut has its access level");
		var gm = Files.readString(root.resolve("data/handlers/admincommands/DynEvent.java"));
		check(gm.contains("PlayerBotDynamicEvents.force(player,") && gm.contains("DynamicEventService.stop(player)") && gm.contains("DynamicEventService.status()"), "The GM shortcut starts, stops and lists");
		check(Files.readString(root.resolve("config/dynamic-events/README.md")).contains("//dynevent start"), "The README explains the shortcut");
	}

	static void guildWars(Path root) throws Exception {
		// Scaling: live, capped like Guild Wars 2 (10 normal), group stages higher, never below what is done or standing.
		check(DynamicEvent.capped(25, false, 10, 40) == 10 && DynamicEvent.capped(25, true, 10, 40) == 25 && DynamicEvent.capped(60, true, 10, 40) == 40,
			"Normal parts scale for 10, Group Events for 40");
		check(DynamicEvent.liveRequired(8, 2, 3) == 8 && DynamicEvent.liveRequired(4, 3, 3) == 6, "Players leaving lower the target, never below kills plus standing");
		check(EventSpawns.eliteChance(4, 5) == 0 && Math.abs(EventSpawns.eliteChance(5, 5) - 0.1) < 1e-9 && Math.abs(EventSpawns.eliteChance(7, 5) - 0.3) < 1e-9
			&& EventSpawns.eliteChance(30, 5) == 0.5, "Elites join from five participants, 10% more each, at most half");
		// Boss shield phases (the Shadow Behemoth's portals grow 5, 9, 13).
		check(DynamicEventService.phaseAdds(2, 0, 0, 1) == 2 && DynamicEventService.phaseAdds(2, 1, 0, 1) == 4 && DynamicEventService.phaseAdds(2, 2, 0, 1) == 6,
			"Each phase brings more reinforcements");
		check(DynamicEventService.phaseAdds(5, 2, 100, 40) == 15, "Phase reinforcements are capped at 15");
		// Credit beyond damage: healing and tanking draw threat.
		check(DynamicEventService.share(500, 500, 1000) == 50, "Damage alone counts as damage");
		check(DynamicEventService.share(0, 400, 1000) == 20, "A healer's threat counts at half");
		check(DynamicEventService.share(2000, 2000, 1000) == 100 && DynamicEventService.share(0, 5000, 1000) == 50, "Each part of the share is capped");
		check(DynamicEventService.LOOT_SHARE == 5, "Five percent of a monster earns personal loot");
		// Schedules.
		check(DynamicEventService.slot(105, 120, 105) == 0 && DynamicEventService.slot(225, 120, 105) == 1 && DynamicEventService.slot(106, 120, 105) == -1,
			"A two-hourly :45 timer fires at 01:45, 03:45 and no other minute");
		check(rejected("{\"id\":\"t\",\"title\":\"T\",\"first\":\"a\",\"schedule\":{\"everyMinutes\":100,\"atMinute\":5},\"stages\":[" + stage("a", "KILL", "NORMAL", "end", "fail", "") + "]}",
			"schedule"), "A schedule must divide the day");
		check(rejected("{\"id\":\"t\",\"title\":\"T\",\"first\":\"a\",\"trigger\":\"occupied\",\"occupyOnFailure\":true,\"stages\":[" + stage("a", "KILL", "NORMAL", "end", "fail", "") + "]}",
			"occupyOnFailure"), "Only Warden chains occupy on failure");
		check(rejected("{\"id\":\"t\",\"title\":\"T\",\"first\":\"a\",\"trigger\":\"raid\",\"stages\":[" + stage("a", "KILL", "NORMAL", "end", "fail", "") + "]}", "trigger"),
			"Unknown triggers are rejected");
		// Wiring.
		var src = root.resolve("src/com/aionemu/gameserver");
		var service = Files.readString(src.resolve("services/dynamicevents/DynamicEventService.java"));
		check(!service.contains("ABANDON") && !service.contains("lastHuman"), "Events run whether or not anyone attends (no abandon timer)");
		check(service.contains("boss.overrideNpcType(CreatureType.INVULNERABLE);") && service.contains("boss.overrideNpcType(null);"), "The leader's shield goes up and comes down");
		check(service.contains("EventWorld.occupy(e, now);") && service.contains("EventWorld.retaken(e.anchor.beat(), true);"), "Failure holds the road; a won retake frees it");
		check(service.contains("\"New Event Nearby: \""), "Players entering an event's range get the notice");
		check(service.contains("EventSpawns.merchant(e.anchor, e.spot)"), "A won chain opens a merchant's stall");
		check(Files.readString(src.resolve("model/gameobjects/Npc.java")).contains("return overriddenType == CreatureType.INVULNERABLE;"),
			"An NPC shown invulnerable takes no server damage either");
		var controller = Files.readString(src.resolve("controllers/NpcController.java"));
		check(controller.contains("float percentage = fullCredit ? 1f : info.getDamage() / (float) finalList.getTotalDamage();")
			&& controller.indexOf("DynamicEventService.npcDied(owner)") < controller.indexOf("doReward();"), "Every attacker earns the whole kill of an event monster");
		var bots = src.resolve("services/playerbot");
		check(Files.readString(bots.resolve("PlayerBotSession.java")).contains("npc.isFlag() || npc.isInvulnerable() ||"), "Bots do not swing at a shielded leader");
		var duty = Files.readString(bots.resolve("PlayerBotRoadDuty.java"));
		check(duty.contains("if(s.career==Career.MERCHANT && PlayerBotDynamicEvents.avoid(s,navigation,now))return true;"), "Merchants turn before a held road");
		var bridge = Files.readString(bots.resolve("PlayerBotDynamicEvents.java"));
		check(bridge.contains("EventWorld.heldSpot(s.route.id())") && bridge.contains("|| EventWorld.held(s.route.id()))"), "A Warden of a held road falls back and asks for no new event");
		check(com.aionemu.gameserver.services.playerbot.PlayerBotDynamicEvents.schedulable(true, true, true, false, 30, 10, 65) && !com.aionemu.gameserver.services.playerbot.PlayerBotDynamicEvents.schedulable(true, true, true, true, 30, 10, 65)
			&& !com.aionemu.gameserver.services.playerbot.PlayerBotDynamicEvents.schedulable(true, true, false, false, 30, 10, 65) && !com.aionemu.gameserver.services.playerbot.PlayerBotDynamicEvents.schedulable(true, true, true, false, 9, 10, 65),
			"A scheduled event needs a free Warden on a fitting, unheld beat");
		var dao = Files.readString(src.resolve("dao/DynamicEventDAO.java"));
		check(dao.contains("PRIMARY KEY(player_id,chain_id,claim_day)") && dao.contains("PRIMARY KEY(account_id,claim_day)") && dao.contains("INSERT IGNORE INTO dynamic_event_chests"),
			"Daily chests claim once per character per chain, and once per account");
		check(Files.readString(src.resolve("services/dynamicevents/EventRewards.java")).contains("DynamicEventDAO.claimChest(c, player.getObjectId(), chainId, day)"),
			"The chest is claimed before it pays");
		var drops = Files.readString(src.resolve("services/drop/DropRegistrationService.java"));
		int personal = drops.indexOf("public Set<DropItem> personalDrop(Npc npc, Player player)");
		check(personal > 0 && drops.indexOf("DataManager.GLOBAL_DROP_DATA.getAllRules(), items, members", personal) > personal
			&& drops.indexOf("EventService.getInstance().getActiveEventDropRules(), items, members", personal) > personal,
			"Personal loot uses the native custom, global and event drop rules (custom drops alone cover only 177 NPCs)");
		check(service.contains("DropRegistrationService.getInstance().personalDrop(npc, p)") && !service.contains("CUSTOM_NPC_DROP"), "Event helpers roll the full native drop");
		var rewards = Files.readString(src.resolve("services/dynamicevents/EventRewards.java"));
		check(rewards.contains("personalDrop(leader, player)") && EventRewards.GEAR_ROLLS == 40, "The bonus chest's gear comes from the leader's own native drop");
		var props = Files.readString(root.resolve("config/dynamic-events/dynamic-events.properties"));
		for (String key : List.of("scale_cap_players", "group_scale_cap_players", "elite_from_players", "silver_reward_percent", "bronze_reward_percent", "failed_reward_percent",
			"reward_level_gap", "chest_kinah_per_level", "chest_tickets", "bonus_chest_tickets", "occupation_minutes", "occupation_holders", "shop_minutes"))
			check(props.contains("gameserver.dynamicevents." + key + " = "), "Setting documented: " + key);
		check(!props.contains("abandon_seconds") && !props.contains("boss_xp_permille"), "Retired settings are gone");
	}

	static void assistant(Path root) throws Exception {
		var chain = EventCatalogue.parse("{\"id\":\"t\",\"title\":\"Test\",\"first\":\"a\",\"stages\":[{\"id\":\"a\",\"type\":\"KILL\",\"rating\":\"NORMAL\",\"title\":\"T\","
			+ "\"objective\":\"O\",\"protect\":true,\"success\":\"b\",\"say\":{\"start\":[\"notice|Go\"]}},{\"id\":\"b\",\"type\":\"BOSS\",\"rating\":\"BOSS\",\"title\":\"B\",\"objective\":\"O\",\"success\":\"c\","
			+ "\"phases\":[50],\"say\":{\"start\":[\"notice|Go\"],\"phase\":[\"boss|x\"]}},{\"id\":\"c\",\"type\":\"ESCORT\",\"rating\":\"NORMAL\",\"title\":\"C\",\"objective\":\"O\",\"success\":\"d\","
			+ "\"say\":{\"start\":[\"notice|Go\"]}},{\"id\":\"d\",\"type\":\"KILL\",\"rating\":\"NORMAL\",\"title\":\"D\",\"objective\":\"O\",\"say\":{\"start\":[\"notice|Go\"]}}]}");
		var anchor = new DynamicEvent.Anchor(1, 1, com.aionemu.gameserver.model.Race.ELYOS, 20, 7, "W", "beat:x", "R", new DynamicEvent.Spot(0, 0, 0), List.of());
		var pool = new EventSpawns.Pool(List.of(), List.of(), null, new DynamicEvent.Spot(100, 0, 0), List.of(), "Raider");
		var e = new DynamicEvent(1, chain, anchor, pool, 0);
		e.stage = chain.stage("a");
		e.required = 8;
		e.defeated = 2;
		check(EventJournal.icon(e).equals("defend") && EventJournal.progress(e, 100) == 250, "A protect KILL shows the shield icon and kills done");
		e.stage = chain.stage("d");
		check(EventJournal.icon(e).equals("kill"), "A plain KILL shows crossed swords");
		e.stage = chain.stage("c");
		e.spot = new DynamicEvent.Spot(25, 0, 0);
		check(EventJournal.icon(e).equals("escort") && EventJournal.progress(e, 100) == 250, "An escort shows the distance walked");
		e.stage = chain.stage("b");
		check(EventJournal.icon(e).equals("boss") && EventJournal.progress(e, 40) == 600, "A leader fight shows the health taken");
		e.shielded = true;
		e.phaseTotal = 4;
		e.phaseAdds.addAll(List.of(1, 2, 3));
		check(EventJournal.progress(e, 40) == 250, "While shielded it shows the reinforcements defeated");
		e.outcome = "Event complete: Test";
		check(EventJournal.icon(e).equals("chest") && EventJournal.progress(e, 40) == 1000, "An ended event shows the chest, full");
		check(EventJournal.medal(e, 99).equals("-"), "No contribution, no medal");
		var src = root.resolve("src/com/aionemu/gameserver");
		var http = Files.readString(src.resolve("services/PlayerBotHttpService.java"));
		check(http.contains("path.equals(\"/market/companions/events/assistant\")") && http.contains("EventJournal.assistant(viewer)"),
			"The assistant's data is served under the Companions context (/market/events would reach the Central Market)");
		var journal = Files.readString(src.resolve("services/dynamicevents/EventJournal.java"));
		for (String key : List.of("active", "chain", "group", "stage", "objective", "count", "progress", "seconds", "medal", "icon", "shield", "outcome", "map", "x", "y", "z"))
			check(journal.contains("lines.put(\"" + key + "\""), "Assistant field " + key);
		var client = root.resolve("../client-mods/aetherfall-core");
		var header = Files.readString(client.resolve("event_assistant.h"));
		check(header.contains("/market/companions/events/assistant?session_id=") && header.contains("constexpr int QUEST=14979;"), "Core asks the same route, only while quest 14979 is active");
		for (String key : List.of("active", "chain", "stage", "objective", "count", "medal", "icon", "outcome", "group", "progress", "seconds", "shield", "map"))
			check(header.contains("k==\"" + key + "\""), "Core reads field " + key);
		check(Files.readString(client.resolve("aetherfall_core.cpp")).contains("if(is(cmd,\"event\"))return event_assistant::query(L);"), "AFCore.Event reaches the cache");
		check(Files.readString(client.resolve("AetherfallCore.lua")).contains("function AFCore.Event() if core then return core(\"event\") end end"), "The Lua wrapper exists");
		var module = Files.readString(client.resolve("modules/EventAssistant.lua"));
		check(module.contains("offsetX={0,'int',-1600,1600}") && module.contains("offsetY={0,'int',-1000,1000}") && !module.contains("AF.Movers:Register")
			&& !module.contains("K.Hide(canvas)"), "The assistant moves by its offsets (no editor box) and never hides the shared canvas");
		check(Files.readString(client.resolve("load_order.json")).contains("\"modules/EventAssistant.lua\"") && Files.readString(client.resolve("load_order.json")).contains("\"options/EventAssistant.lua\""),
			"The module and its options load");
		var prepare = Files.readString(client.resolve("prepare.py"));
		check(prepare.contains("root.append(event_assistant_widgets(unitframes_dialog()))") && prepare.contains("return event_assistant_update_xml(update_xml(payload))")
			&& prepare.contains("event_assistant_assets().items()") && !Files.readString(client.resolve("layout/unitframes.py")).contains("event_assistant"),
			"The pieces join the passive canvas through prepare.py (startup, retained updates, textures); the unit-frame layout stays its own");
	}

	static void wiring(Path root) throws Exception {
		var src = root.resolve("src/com/aionemu/gameserver");
		check(Files.readString(src.resolve("controllers/NpcController.java")).contains("DynamicEventService.npcDied(owner); // credits event contribution, never throws"),
			"Monster deaths credit events before rewards");
		var server = Files.readString(src.resolve("GameServer.java"));
		check(server.indexOf("PlayerBotWardens.initialize();") < server.indexOf("DynamicEventService.initialize();")
			&& server.indexOf("DynamicEventService.initialize();") < server.indexOf("PlayerBotDynamicEvents.register();"), "Events start after the Wardens, then bots join");
		check(Files.readString(src.resolve("configs/Config.java")).contains("./config/dynamic-events/dynamic-events.properties")
			&& Files.readString(src.resolve("configs/Config.java")).contains("DynamicEventConfig.class"), "The settings file loads");
		var bots = src.resolve("services/playerbot");
		check(Files.readString(bots.resolve("PlayerBotWardens.java")).contains("   ward(bot,human);\n   PlayerBotDynamicEvents.wardenSees(state,human,now);")
			|| Files.readString(bots.resolve("PlayerBotWardens.java")).contains("   ward(bot,human);\r\n   PlayerBotDynamicEvents.wardenSees(state,human,now);"), "Wardens ask for help where they ward travellers");
		var duty = Files.readString(bots.resolve("PlayerBotRoadDuty.java"));
		check(duty.indexOf("PlayerBotDynamicEvents.wardenTravel(s,navigation)") > 0 && duty.indexOf("PlayerBotDynamicEvents.wardenTravel(s,navigation)") < duty.indexOf("PlayerBotWardens.travel(s,navigation,now)"),
			"An event Warden leaves its patrol before escorts and aid");
		var session = Files.readString(bots.resolve("PlayerBotSession.java"));
		check(session.contains("PlayerBotDynamicEvents.engaged(bot,npc)"), "Event monsters are engaged targets");
		check(session.contains("strategyPlan.defaults(\"dynamic event\",new SimpleAction(\"answer a call for help\""), "Answering bots walk to the event");
		var bridge = Files.readString(bots.resolve("PlayerBotDynamicEvents.java"));
		check(!bridge.contains("synchronized"), "The bridge takes no monitor");
		var chains = root.resolve("config/dynamic-events/chains");
		check(Files.isDirectory(chains) && Files.isRegularFile(root.resolve("config/dynamic-events/dynamic-events.properties")), "Settings and chains ship in config");
		for (String key : List.of("enable", "trigger_chance_percent", "max_running", "beat_cooldown_minutes", "radius", "boss_hp_max_percent", "chest_tickets"))
			check(Files.readString(root.resolve("config/dynamic-events/dynamic-events.properties")).contains("gameserver.dynamicevents." + key + " = "), "Setting documented: " + key);
	}
}
