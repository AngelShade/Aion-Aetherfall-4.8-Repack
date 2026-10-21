package com.aionemu.gameserver.services;

import static com.aionemu.gameserver.services.CentralMarketService.*;

import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.calc.StatOwner;
import com.aionemu.gameserver.model.stats.calc.functions.IStatFunction;
import com.aionemu.gameserver.model.stats.calc.functions.StatAddFunction;
import com.aionemu.gameserver.model.stats.calc.functions.StatRateFunction;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ACCOUNT_PROPERTIES;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PACKAGE_INFO_NOTIFY;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * Aetherfall Boons: account bonuses shown on the Profile window's Account tab. Every boon is earned
 * in game (Season Pass tiers bought with Kinah, character levels, life skills, story quests, account
 * age); none is sold. The client's first five F2P pack slots carry the Aetherfall names and texts.
 */
public final class AetherfallBoonService {

	private static final Logger log = LoggerFactory.getLogger(AetherfallBoonService.class);

	/** Pack ids in the client's Data/USA/BM client_bm_pack.xml. */
	public enum Boon {
		SHUGO_TRADE_CHARTER(1), GRACE_OF_THE_TOWER(2), AETHERFALL_RENOWN(3), CHRONICLE_OF_ATREIA(4), DAEVA_VETERANS_COFFER(5);

		public final int pack;

		Boon(int pack) {
			this.pack = pack;
		}
	}

	/** Instant.MAX marks a permanent boon. */
	public record State(Map<Boon,Instant> boons, int renown, int chapters, int combat) {
		public boolean has(Boon boon) {
			Instant until = boons.get(boon);
			return until != null && Instant.now().isBefore(until);
		}
	}

	public static final int RENOWN_MARKET = 1000, RENOWN_FORTUNE = 2500;
	// Account Fame (BDO family fame): Combat from levels, Life from skills, Legend from quests and titles.
	static final int FAME_MIN_LEVEL = 10, LIFE_MIN_SKILL = 100, QUESTS_PER_LEGEND = 10, LEGEND_PER_TITLE = 5;
	// Characters below level 55 gain 1% hunting/group/quest XP per 100 Combat Fame, up to 20%.
	static final int YOUTH_LEVEL = 55, YOUTH_XP_PER = 100, YOUTH_XP_MAX = 20;
	static final int CHARTER_XP = 30, GRACE_DROP = 20, RENOWN_DROP = 10, MARKET_BONUS_BP = 500, GRACE_VOLUME = 20_000;
	static final long STIPEND_PER_POINT = 100, STIPEND_CAP = 1_000_000;
	static final int[] LIFE_SKILLS = { 30001, 30002, 30003, 40001, 40002, 40003, 40004, 40007, 40008, 40010 };
	// Story chapters (Elyos or Asmodian): Ascension ceremony, Abyss entry, then Aetherfall's Red Journal continuation.
	static final int[][] STORY_CHAPTERS = { { 1007, 2009 }, { 1922, 2947 }, { 3999 } };
	static final int[] QUEST_CHAPTERS = { 300, 800 };
	private static final StatEnum[] CHARTER_STATS = { StatEnum.BOOST_HUNTING_XP_RATE, StatEnum.BOOST_GROUP_HUNTING_XP_RATE,
		StatEnum.BOOST_QUEST_XP_RATE, StatEnum.BOOST_GATHERING_XP_RATE, StatEnum.BOOST_CRAFTING_XP_RATE, StatEnum.BOOST_ESSENCETAPPING_XP_RATE,
		StatEnum.BOOST_AETHERTAPPING_XP_RATE, StatEnum.BOOST_COOKING_XP_RATE, StatEnum.BOOST_WEAPONSMITHING_XP_RATE,
		StatEnum.BOOST_ARMORSMITHING_XP_RATE, StatEnum.BOOST_TAILORING_XP_RATE, StatEnum.BOOST_ALCHEMY_XP_RATE,
		StatEnum.BOOST_HANDICRAFTING_XP_RATE, StatEnum.BOOST_MENUISIER_XP_RATE };
	private static final String SCHEMA = "CREATE TABLE IF NOT EXISTS aetherfall_renown (account_id INT NOT NULL PRIMARY KEY,"
		+ " last_stipend DATE NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

	private static final Map<Integer,State> accounts = new ConcurrentHashMap<>();
	private static final Map<Integer,StatOwner> owners = new ConcurrentHashMap<>();
	private static volatile boolean schemaReady;

	private AetherfallBoonService() {}

	/**
	 * Applies the account's boons while the character enters the world, before Energy of Repose and the
	 * first stat packets. The Account tab list follows CM_LEVEL_READY; the daily stipend is mailed after.
	 */
	public static void onEnterWorld(Player p) {
		if (p.isPlayerBot())
			return;
		try (Connection c = DatabaseFactory.getConnection()) {
			State state = compute(c, p.getAccount().getId(), p.getAccount().getCreationDate(), Instant.now());
			accounts.put(p.getAccount().getId(), state);
			apply(p, state, false);
			ThreadPoolManager.getInstance().execute(() -> stipend(p, state));
		} catch (Exception e) {
			log.error("Could not load Aetherfall boons for {}", p, e);
		}
	}

	public static void onLogout(Player p) {
		owners.remove(p.getObjectId());
	}

	/** Recomputes the account's boons, reapplies their stats and updates the Account tab. */
	public static void refresh(Player p) {
		if (p.isPlayerBot())
			return;
		ThreadPoolManager.getInstance().execute(() -> {
			try (Connection c = DatabaseFactory.getConnection()) {
				State state = compute(c, p.getAccount().getId(), p.getAccount().getCreationDate(), Instant.now());
				accounts.put(p.getAccount().getId(), state);
				apply(p, state, true);
			} catch (Exception e) {
				log.error("Could not refresh Aetherfall boons for {}", p, e);
			}
		});
	}

	static State compute(Connection c, int account, long accountCreated, Instant now) throws SQLException {
		Map<Boon,Instant> boons = new EnumMap<>(Boon.class);
		SeasonPassRules.Season season = SeasonPassService.season;
		if (season != null && season.active(now)) {
			long tier = lng(row(c, "SELECT COALESCE(MAX(s.tier),0) tier FROM season_pass_progress s JOIN players p ON p.id=s.player_id"
				+ " WHERE s.season_id=? AND p.account_id=?", season.id(), account), "tier");
			if (tier >= 1)
				boons.put(Boon.SHUGO_TRADE_CHARTER, season.ends());
			if (tier >= 2)
				boons.put(Boon.GRACE_OF_THE_TOWER, season.ends());
		}
		Fame fame = fame(c, account);
		int renown = fame.total();
		if (renown > 0)
			boons.put(Boon.AETHERFALL_RENOWN, Instant.MAX);
		Set<Integer> completed = fame.quests();
		int chapters = chapters(completed);
		if (chapters > 0)
			boons.put(Boon.CHRONICLE_OF_ATREIA, Instant.MAX);
		if (fame.maxLevel() >= 65)
			boons.put(Boon.DAEVA_VETERANS_COFFER, nextVeteranMonth(accountCreated, now));
		return new State(boons, renown, chapters, fame.combat());
	}

	/** Everything the Account window shows: every boon (earned or not), its progress and the account's characters. */
	public static Map<String,Object> view(Player p) throws SQLException {
		int account = p.getAccount().getId();
		Instant now = Instant.now();
		try (Connection c = DatabaseFactory.getConnection()) {
			State state = compute(c, account, p.getAccount().getCreationDate(), now);
			SeasonPassRules.Season season = SeasonPassService.season;
			boolean seasonActive = season != null && season.active(now);
			long tier = !seasonActive ? 0 : lng(row(c, "SELECT COALESCE(MAX(s.tier),0) tier FROM season_pass_progress s JOIN players p ON p.id=s.player_id"
				+ " WHERE s.season_id=? AND p.account_id=?", season.id(), account), "tier");
			Fame fame = fame(c, account);
			Set<Integer> completed = fame.quests();
			int maxLevel = fame.maxLevel();
			List<Map<String,Object>> characters = new ArrayList<>();
			for (var r : rows(c, "SELECT id,name,player_class,race,exp FROM players WHERE account_id=? ORDER BY exp DESC,name", account)) {
				int id = (int) lng(r, "id");
				characters.add(Map.of("name", str(r, "name"), "playerClass", str(r, "player_class"), "race", str(r, "race"),
					"level", DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(lng(r, "exp")), "renown", fame.characterFame().getOrDefault(id, 0),
					"online", id == p.getObjectId()));
			}
			List<Map<String,Object>> chapterList = new ArrayList<>();
			String[] storyNames = { "The Ascension ceremony", "Into the Abyss", "The Unfinished Entry" };
			for (int i = 0; i < STORY_CHAPTERS.length; i++)
				chapterList.add(Map.of("name", storyNames[i], "done", Arrays.stream(STORY_CHAPTERS[i]).anyMatch(completed::contains)));
			for (int count : QUEST_CHAPTERS)
				chapterList.add(Map.of("name", String.format("%,d completed quests", count), "done", completed.size() >= count));
			int chapterGoal = STORY_CHAPTERS.length + QUEST_CHAPTERS.length;

			List<Map<String,Object>> boons = new ArrayList<>();
			for (Boon boon : Boon.values()) {
				Map<String,Object> b = new LinkedHashMap<>();
				b.put("id", boon.name().toLowerCase(Locale.ROOT));
				b.put("active", state.has(boon));
				Instant until = state.boons().get(boon);
				b.put("until", until == null || until == Instant.MAX ? 0 : until.toEpochMilli());
				switch (boon) {
					case SHUGO_TRADE_CHARTER, GRACE_OF_THE_TOWER -> {
						int need = boon == Boon.SHUGO_TRADE_CHARTER ? 1 : 2;
						b.put("progress", Math.min(tier, need));
						b.put("goal", need);
						b.put("progressLabel", !seasonActive ? "No season is running" : tier >= need ? "Unlocked for " + season.name()
							: need == 1 ? "Unlock Premium in the Season Pass" : tier == 1 ? "Upgrade to Advanced Premium" : "Unlock Advanced Premium in the Season Pass");
					}
					case AETHERFALL_RENOWN -> {
						int next = state.renown() < RENOWN_MARKET ? RENOWN_MARKET : RENOWN_FORTUNE;
						b.put("progress", Math.min(state.renown(), next));
						b.put("goal", next);
						b.put("progressLabel", state.renown() >= RENOWN_FORTUNE ? String.format("%,d Account Fame · every tier reached", state.renown())
							: String.format("%,d / %,d Account Fame to the next tier", state.renown(), next));
						b.put("stipend", Math.min(STIPEND_CAP, state.renown() * STIPEND_PER_POINT));
					}
					case CHRONICLE_OF_ATREIA -> {
						b.put("progress", state.chapters());
						b.put("goal", chapterGoal);
						b.put("progressLabel", state.chapters() + " of " + chapterGoal + " chapters written · " + String.format("%,d", completed.size()) + " quests completed");
						b.put("chapters", chapterList);
					}
					case DAEVA_VETERANS_COFFER -> {
						b.put("progress", Math.min(maxLevel, 65));
						b.put("goal", 65);
						b.put("progressLabel", maxLevel >= 65 ? "A level 65 Daeva is on your account" : "Highest level " + maxLevel + " of 65");
					}
				}
				boons.add(b);
			}
			Map<String,Object> out = new LinkedHashMap<>();
			out.put("account", p.getAccount().getName());
			out.put("character", p.getName());
			out.put("now", now.toEpochMilli());
			out.put("season", seasonActive ? season.name() : "");
			out.put("seasonEnds", seasonActive ? season.ends().toEpochMilli() : 0);
			out.put("renown", state.renown());
			out.put("stipend", Math.min(STIPEND_CAP, state.renown() * STIPEND_PER_POINT));
			out.put("chapters", state.chapters());
			out.put("chapterGoal", chapterGoal);
			out.put("marketReturn", marketReturn(p) / 100.0);
			Map<String,Object> f = new LinkedHashMap<>();
			f.put("combat", fame.combat());
			f.put("life", fame.life());
			f.put("legend", fame.legend());
			f.put("total", fame.total());
			f.put("quests", completed.size());
			f.put("titles", fame.titles());
			f.put("youthXp", youthXp(fame.combat()));
			f.put("youthXpMax", YOUTH_XP_MAX);
			f.put("tiers", List.of(
				Map.of("at", 1, "name", "Known", "benefit", String.format("Daily Kinah stipend: %,d per fame, up to %,d", STIPEND_PER_POINT, STIPEND_CAP)),
				Map.of("at", RENOWN_MARKET, "name", "Renowned", "benefit", "Central Market sales return 5% more"),
				Map.of("at", RENOWN_FORTUNE, "name", "Illustrious", "benefit", "+10% item drops on every character")));
			out.put("fame", f);
			out.put("boons", boons);
			out.put("characters", characters);
			return out;
		}
	}

	/** One account's fame, with each character's Combat and Life share. */
	record Fame(int combat, int life, int legend, int titles, int maxLevel, Set<Integer> quests, Map<Integer,Integer> characterFame) {
		int total() {
			return combat + life + legend;
		}
	}

	static Fame fame(Connection c, int account) throws SQLException {
		Map<Integer,Integer> characterFame = new HashMap<>();
		int combat = 0, life = 0, maxLevel = 0;
		for (var r : rows(c, "SELECT id,exp FROM players WHERE account_id=?", account)) {
			int level = DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(lng(r, "exp"));
			maxLevel = Math.max(maxLevel, level);
			int points = level >= FAME_MIN_LEVEL ? levelRenown(level) : 0;
			combat += points;
			characterFame.merge((int) lng(r, "id"), points, Integer::sum);
		}
		StringJoiner skills = new StringJoiner(",");
		for (int id : LIFE_SKILLS)
			skills.add(Integer.toString(id));
		for (var r : rows(c, "SELECT s.player_id,s.skill_level FROM player_skills s JOIN players p ON p.id=s.player_id WHERE p.account_id=? AND s.skill_id IN ("
			+ skills + ") AND s.skill_level>=?", account, LIFE_MIN_SKILL)) {
			int points = (int) lng(r, "skill_level") / 5;
			life += points;
			characterFame.merge((int) lng(r, "player_id"), points, Integer::sum);
		}
		Set<Integer> quests = new HashSet<>();
		for (var r : rows(c, "SELECT DISTINCT q.quest_id FROM player_quests q JOIN players p ON p.id=q.player_id WHERE p.account_id=?"
			+ " AND (q.status='COMPLETE' OR q.complete_count>0)", account))
			quests.add((int) lng(r, "quest_id"));
		int titles = (int) lng(row(c, "SELECT COUNT(DISTINCT t.title_id) n FROM player_titles t JOIN players p ON p.id=t.player_id WHERE p.account_id=?", account), "n");
		return new Fame(combat, life, quests.size() / QUESTS_PER_LEGEND + titles * LEGEND_PER_TITLE, titles, maxLevel, quests, characterFame);
	}

	static int youthXp(int combat) {
		return Math.min(YOUTH_XP_MAX, combat / YOUTH_XP_PER);
	}

	/** Level 55+ counts double and level 65 five times, like BDO's family fame. */
	static int levelRenown(int level) {
		return level >= 65 ? level * 5 : level >= 55 ? level * 2 : level;
	}

	static int chapters(Set<Integer> completed) {
		int chapters = 0;
		for (int[] chapter : STORY_CHAPTERS)
			if (Arrays.stream(chapter).anyMatch(completed::contains))
				chapters++;
		for (int count : QUEST_CHAPTERS)
			if (completed.size() >= count)
				chapters++;
		return chapters;
	}

	/** VeteranRewardService mails a coffer for every full month of account age. */
	static Instant nextVeteranMonth(long accountCreated, Instant now) {
		ZonedDateTime created = Instant.ofEpochMilli(accountCreated).atZone(ZoneId.systemDefault());
		long months = ChronoUnit.MONTHS.between(created, now.atZone(ZoneId.systemDefault()));
		return created.plusMonths(months + 1).toInstant();
	}

	private static void apply(Player p, State state, boolean notify) {
		StatOwner owner = owners.computeIfAbsent(p.getObjectId(), id -> new StatOwner() {});
		p.getGameStats().endEffect(owner);
		List<IStatFunction> functions = new ArrayList<>();
		if (state.has(Boon.SHUGO_TRADE_CHARTER))
			for (StatEnum stat : CHARTER_STATS)
				functions.add(new StatAddFunction(stat, CHARTER_XP, true));
		int drop = (state.has(Boon.GRACE_OF_THE_TOWER) ? GRACE_DROP : 0) + (state.renown() >= RENOWN_FORTUNE ? RENOWN_DROP : 0);
		if (drop > 0)
			functions.add(new StatAddFunction(StatEnum.BOOST_DROP_RATE, drop, true));
		int youth = p.getLevel() < YOUTH_LEVEL ? youthXp(state.combat()) : 0;
		if (youth > 0)
			for (StatEnum stat : new StatEnum[] { StatEnum.BOOST_HUNTING_XP_RATE, StatEnum.BOOST_GROUP_HUNTING_XP_RATE, StatEnum.BOOST_QUEST_XP_RATE })
				functions.add(new StatAddFunction(stat, youth, true));
		int n = state.chapters();
		if (n > 0) {
			functions.add(new StatRateFunction(StatEnum.MAXHP, 3 * n, true));
			functions.add(new StatRateFunction(StatEnum.MAXMP, 3 * n, true));
			functions.add(new StatRateFunction(StatEnum.PHYSICAL_ATTACK, 2 * n, true));
			functions.add(new StatAddFunction(StatEnum.BOOST_MAGICAL_SKILL, 20 * n, true));
			functions.add(new StatRateFunction(StatEnum.PHYSICAL_DEFENSE, 3 * n, true));
			functions.add(new StatAddFunction(StatEnum.MAGICAL_RESIST, 10 * n, true));
		}
		if (!functions.isEmpty())
			p.getGameStats().addEffect(owner, functions);
		if (notify)
			sendPackList(p);
	}

	/**
	 * Active packs as the client's bitmask (bit = pack id), sent in SM_ACCOUNT_PROPERTIES. The Account tab lists
	 * only packs whose bit is set; SM_PACKAGE_INFO_NOTIFY supplies their remaining time.
	 */
	public static int packMask(int account) {
		State state = accounts.get(account);
		int mask = 0;
		if (state != null)
			for (Boon boon : Boon.values())
				if (state.has(boon))
					mask |= 1 << boon.pack;
		return mask;
	}

	/** Fills the Account tab list. Sent after login and whenever the boons change. */
	public static void sendPackList(Player p) {
		State state = accounts.get(p.getAccount().getId());
		if (state == null || p.getClientConnection() == null)
			return;
		PacketSendUtility.sendPacket(p, new SM_ACCOUNT_PROPERTIES());
		Instant now = Instant.now();
		Map<Integer,Integer> packs = new LinkedHashMap<>();
		for (Boon boon : Boon.values()) {
			if (!state.has(boon))
				continue;
			Instant until = state.boons().get(boon);
			// 0 shows "No time limit".
			packs.put(boon.pack, until == Instant.MAX ? 0 : (int) Math.max(1, Math.min(Integer.MAX_VALUE, Duration.between(now, until).toSeconds())));
		}
		PacketSendUtility.sendPacket(p, new SM_PACKAGE_INFO_NOTIFY(packs));
	}

	private static void stipend(Player p, State state) {
		if (state.renown() <= 0)
			return;
		try (Connection c = DatabaseFactory.getConnection()) {
			stipend(c, p, state);
		} catch (Exception e) {
			log.error("Could not pay the Aetherfall Renown stipend to {}", p, e);
		}
	}

	private static void stipend(Connection c, Player p, State state) throws SQLException {
		if (!schemaReady) {
			try (Statement s = c.createStatement()) {
				s.execute(SCHEMA);
			}
			schemaReady = true;
		}
		int account = p.getAccount().getId();
		java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
		update(c, "INSERT IGNORE INTO aetherfall_renown VALUES(?,NULL)", account);
		if (update(c, "UPDATE aetherfall_renown SET last_stipend=? WHERE account_id=? AND (last_stipend IS NULL OR last_stipend<?)", today, account,
			today) != 1)
			return;
		long kinah = Math.min(STIPEND_CAP, state.renown() * STIPEND_PER_POINT);
		if (!SystemMailService.sendMail("Aetherfall", p.getName(), "Aetherfall Renown", "Your Account Fame stands at " + state.renown()
			+ " points. Here is today's stipend.\n\nFame grows with every character's level, gathering and crafting skill, completed quest and title on your account.",
			0, 0, kinah, LetterType.NORMAL)) {
			update(c, "UPDATE aetherfall_renown SET last_stipend=NULL WHERE account_id=?", account);
			log.warn("Aetherfall Renown stipend mail failed for {}", p);
		}
	}

	public static State state(Player p) {
		return accounts.get(p.getAccount().getId());
	}

	public static boolean has(Player p, Boon boon) {
		State state = state(p);
		return state != null && state.has(boon);
	}

	/** Share of a Central Market sale returned to the seller, in basis points. */
	public static int marketReturn(Player p) {
		int bp = p.getAccount().getMembership() > 0 || has(p, Boon.SHUGO_TRADE_CHARTER) ? 8450 : 6500;
		State state = state(p);
		return state != null && state.renown() >= RENOWN_MARKET ? bp + MARKET_BONUS_BP : bp;
	}

	/** Central Market warehouse capacity in tenths of VT. */
	public static long marketVolume(Player p) {
		return CentralMarketRules.BASE_VOLUME + (has(p, Boon.GRACE_OF_THE_TOWER) ? GRACE_VOLUME : 0);
	}

	/** Hours offline for a full Energy of Repose refill. */
	public static double reposeRefillHours(Player p) {
		return has(p, Boon.GRACE_OF_THE_TOWER) ? 24 : 48;
	}
}
