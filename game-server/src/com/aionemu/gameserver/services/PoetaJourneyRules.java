package com.aionemu.gameserver.services;

import java.util.*;
import java.util.function.ToLongFunction;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.services.playerbot.PlayerBotJourneyEvidence;

/** Builds a single-completion reward bundle, including every alternative item. */
public final class PoetaJourneyRules {
	public static final int KINAH = 182400001;
	private PoetaJourneyRules() {}

	public record Journey(Race race, String starter, int starterWorld, int ascension, int ceremony,
		String capital, int capitalWorld, float x, float y, float z, String guide, String travelGuide, String onward) {
		public Map<String,String> presentation() {
			return Map.of("starter",starter,"capital",capital,"guide",guide,"travelGuide",travelGuide,"onward",onward,
				"starterArt",starter.toLowerCase(Locale.ROOT),"capitalArt",capital.toLowerCase(Locale.ROOT),
				"description",race == Race.ELYOS ? "Walk the forests, meet the Elim, and discover the story of your Ascension."
					: "Explore Ishalgen, meet the Archons, and discover the story of your Ascension.");
		}
		public String welcome() {
			return "Welcome to "+capital+"! Collect your skipped "+starter+" quest rewards from the mailbox. Speak to "+guide
				+" for A Ceremony in "+capital+" and earn its rewards when you complete it, then see "+travelGuide+" for Dispatch to "+onward+".";
		}
	}

	public static Journey journey(Race race) {
		return switch (race) {
			case ELYOS -> new Journey(race,"Poeta",210010000,1006,1007,"Sanctum",110010000,1313,1512,568,"Leah","Polyidus","Verteron");
			case ASMODIANS -> new Journey(race,"Ishalgen",220010000,2008,2009,"Pandaemonium",120010000,1685,1400,195,"Heimdall","Doman","Altgard");
			default -> throw new IllegalArgumentException("This journey is available to Elyos and Asmodians.");
		};
	}

	public static boolean skippedQuest(QuestTemplate q) {
		return (q.getRacePermitted() == Race.ELYOS || q.getRacePermitted() == Race.ASMODIANS)
			&& skippedQuest(q,q.getRacePermitted());
	}

	public static boolean skippedQuest(QuestTemplate q,Race race) {
		Journey path = journey(race);
		return q.getRacePermitted() == race && !q.isRestricted() && !q.isNoCount()
			&& q.getMinlevelPermitted() <= 9 && q.getMaxRepeatCount() == 1
			&& q.getId() != path.ceremony()
			&& (path.starter().equals(q.getQuestZone()) || q.getId() == path.ascension());
	}

	public static List<PlayerClass> advancedClasses(PlayerClass base) {
		return Arrays.stream(PlayerClass.values()).filter(c -> !c.isStartingClass() && c.getStartingClass() == base).toList();
	}

	public static int ceremonyGroup(PlayerClass advanced) {
		return switch (advanced.getStartingClass()) {
			case WARRIOR -> 0; case SCOUT -> 1; case MAGE -> 2;
			case PRIEST -> 3; case ENGINEER -> 4; case ARTIST -> 5;
			default -> throw new IllegalArgumentException("Choose an advanced class.");
		};
	}

	public static int dispatchQuest(PlayerClass advanced) {
		return dispatchQuest(Race.ELYOS,advanced);
	}

	public static int dispatchQuest(Race race,PlayerClass advanced) {
		journey(race); // Reject unsupported races rather than defaulting to another faction.
		return (race == Race.ELYOS ? new int[] {1913,1914,1915,1916,19070,19071}
			: new int[] {2901,2902,2903,2904,29070,29071})[ceremonyGroup(advanced)];
	}

	/** A skip finishes at the capital; a played story finishes at native Ascension. */
	public static boolean continuationOpen(String decision,PlayerClass current) {
		return "SKIP".equals(decision) || !current.isStartingClass();
	}

	public record Chapter(String id,String title,String task,long goal,boolean needsTickets,ToLongFunction<PlayerBotJourneyEvidence.Facts> progress) {
		boolean done(PlayerBotJourneyEvidence.Facts facts) { return progress.applyAsLong(facts) >= goal; }
	}

	/** Each goal is met by play recorded in its own owner, so a button can never complete one. */
	public static List<Chapter> chapters(boolean tickets) {
		long earned = Math.max(1,PlayerBotConfig.JOURNEY_TICKETS_GOAL), runs = Math.max(1,PlayerBotConfig.JOURNEY_RUNS_GOAL), help = Math.max(1,PlayerBotConfig.JOURNEY_HELP_GOAL);
		return List.of(
			new Chapter("earn","Earn Recruitment","Earn "+count(earned,"Recruitment Ticket")+" from challenging instances or PvP wins.",earned,true,PlayerBotJourneyEvidence.Facts::tickets),
			new Chapter("recruit","Recruit a Companion","Press Recruit with Ticket and welcome your first recruit.",1,true,PlayerBotJourneyEvidence.Facts::recruits),
			new Chapter("manage","Manage and Develop","In Roster, save gear to a companion's build.",1,false,PlayerBotJourneyEvidence.Facts::saved),
			new Chapter("help","Group and Help","Answer a bot's call for help and finish "+count(help,"objective")+" together.",help,false,PlayerBotJourneyEvidence.Facts::helped),
			new Chapter("fight","Fight Together","Finish "+count(runs,"challenging run")+" and see the ending through.",runs,true,PlayerBotJourneyEvidence.Facts::runs),
			new Chapter("promote","Earn Promotion","Fight beside a recruit until its combat rank rises.",1,false,PlayerBotJourneyEvidence.Facts::promoted)
		).stream().filter(c -> tickets || !c.needsTickets()).toList();
	}

	private static String count(long amount,String noun) { return amount == 1 ? (noun.startsWith("o") ? "an " : "a ")+noun : amount+" "+noun+"s"; }

	/** Rewards are titles: bound to one character, so they cannot be traded, sold or recycled. */
	public static Map<String,Integer> rewardTitles() {
		Map<String,Integer> titles = new LinkedHashMap<>();
		if (PlayerBotConfig.JOURNEY_MIDPOINT_TITLE > 0) titles.put("midpoint",PlayerBotConfig.JOURNEY_MIDPOINT_TITLE);
		if (PlayerBotConfig.JOURNEY_FINAL_TITLE > 0) titles.put("final",PlayerBotConfig.JOURNEY_FINAL_TITLE);
		return titles;
	}

	public static List<Chapter> rewardChapters(String reward,List<Chapter> chapters) {
		return reward.equals("midpoint") ? chapters.subList(0,Math.min(3,chapters.size())) : chapters;
	}

	public static Map<Integer,Long> rewardItems(QuestTemplate q) {
		Map<Integer,Long> result = new LinkedHashMap<>();
		for (var reward : q.getRewards()) {
			Map<Integer,Long> group = new LinkedHashMap<>();
			for (QuestItems item : reward.getRewardItem()) group.merge(item.getItemId(), item.getCount(), Long::sum);
			for (QuestItems item : reward.getSelectableRewardItem()) group.merge(item.getItemId(), item.getCount(), Math::max);
			group.forEach((id,count) -> result.merge(id,count,Math::max));
		}
		// All selectable weapons, including the other class choices, are requested.
		// Shared alternatives are awarded once per quest, rather than once per class.
		for (PlayerClass c : PlayerClass.values())
			for (QuestItems item : q.getSelectableRewardByClass(c)) result.merge(item.getItemId(), item.getCount(), Math::max);
		return result;
	}

	public static long rewardGold(QuestTemplate q) {
		return q.getRewards().stream().mapToLong(r -> r.getKinah()).max().orElse(0);
	}
}
