package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.aionemu.gameserver.services.playerbot.PlayerBotActivityChat.*;

/** Pure classification, phrase selection and the same limiter used by observation. */
public final class PlayerBotActivityChatCheck {
    private static int checks;
    private static final long START = 1_000_000;
    private static final Names NAMES = new Names("A New Beginning", "Pernos", "Mountain Kerub", "Alyss");
    private static final Settings DEFAULTS = new Settings(90_000, 180_000, 4);

    public static void main(String[] args) throws Exception {
        phrases();
        classification();
        names();
        cooldowns();
        humanCaps();
        repetition();
        acceptedSends();
        capacity();
        wiring(args.length == 0 ? Path.of(".") : Path.of(args[0]));
        System.out.println("OK: " + checks + " activity chat checks; no World/SQL/tasks");
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value)
            throw new AssertionError(reason);
    }

    /** An independent specification check catches a shared formatter/validator mistake. */
    private static boolean safe(String line) {
        if (line == null || line.isBlank() || line.length() > 80 || line.indexOf('#') >= 0
            || line.indexOf('_') >= 0 || line.codePoints().anyMatch(Character::isDigit)
            || line.codePoints().anyMatch(Character::isISOControl))
            return false;
        Set<String> banned = Set.of("native", "planning", "route", "geometry", "traversal", "corridor",
            "arbitration", "goal", "selection", "admitted", "quota");
        for (String word : line.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}]+")) {
            if (banned.contains(word))
                return false;
        }
        return safeLine(line);
    }

    private static Snapshot state(String state, String kind) {
        return new Snapshot(state, kind, false, false, false, false, false, false, false, false, NAMES);
    }

    private static void phrases() {
        for (Activity activity : Activity.values()) {
            Set<String> seen = new HashSet<>();
            for (int variant = 0; variant < 8; variant++) {
                String line = phrase(activity, NAMES, variant, seen);
                check(safe(line), activity + " produces a safe short line for each variant");
                seen.add(line);
            }
            check(seen.size() == 8, activity + " has eight distinct variants");
            check(phrase(activity, NAMES, 0, seen) == null, activity + " suppresses exhausted variants");
        }
        int different = 0;
        String previous = "";
        for (int bot = 1; bot <= 100; bot++) {
            String line = phrase(Activity.QUEST_HUNT, NAMES, ((long) bot << 32) ^ "SameName".hashCode(), Set.of());
            if (!line.equals(previous))
                different++;
            previous = line;
        }
        check(different >= 70, "Bot identity makes neighboring bots mostly choose different lines");
        check(phrase(Activity.CRAFT, NAMES, 52, Set.of()).equals(phrase(Activity.CRAFT, NAMES, 52, Set.of())),
            "Phrase selection is deterministic for the same identity and state");
    }

    private static void classification() {
        String[] samples = {
            "travel to quest destination | world: travelling to REWARD quest",
            "visit nearby service NPC | world: travelling to CONVERSATION",
            "travelling to HUNT quest",
            "patrol assigned road | merchant travelling assigned road",
            "Warden patrolling assigned road",
            "rest and recover | planning paused: recovering health or mana",
            "native quest crafting: Use the real native crafting task",
            "earn native quest materials",
            "gathering with Find Group party",
            "collecting own rewards while idle",
            "destination released: awaiting selection",
            "service area: eligible destinations busy or cooling down",
            "rpg pause",
            "planning paused: combat",
            "hunting Mountain Kerub for gear",
            "blocked by geometry",
            "stuck pacing; trying another goal",
            "lost: waiting to move to a field camp"
        };
        Activity[] expected = {
            Activity.QUEST_REWARD, Activity.QUEST_TALK, Activity.QUEST_HUNT,
            Activity.MERCHANT_TRAVEL, Activity.WARDEN_PATROL, Activity.REST,
            Activity.CRAFT, Activity.QUEST_OBJECTS, Activity.GROUP_GATHER, Activity.REWARDS,
            Activity.IDLE, Activity.IDLE, Activity.IDLE, Activity.FIGHTING,
            Activity.FARM_GEAR, Activity.STUCK, Activity.STUCK, Activity.STUCK
        };
        for (int index = 0; index < samples.length; index++) {
            Activity activity = classify(state(samples[index], ""));
            check(activity == expected[index], "Classifies actual state " + samples[index]);
            check(safe(phrase(activity, NAMES, index, Set.of())), "Diagnostic state never leaks into chat");
        }
        check(classify(state("travelling to quest destination | move: blocked by geometry", "REWARD")) == Activity.STUCK,
            "Navigation blockage wins over an old quest destination");
        check(classify(state("ground route unavailable", "REWARD")) == Activity.STUCK, "Ground blockage is explained safely");
        check(classify(state("goal unreachable", "HUNT")) == Activity.STUCK, "Unreachable destination is explained safely");
        check(classify(state("lost", "")) == Activity.STUCK, "Bare lost state is recognized");
        check(classify(new Snapshot("travelling to REWARD", "REWARD", false, true, false,
            false, false, false, false, false, NAMES)) == Activity.REST, "Live recovery wins over travel");
        check(classify(new Snapshot("rest and recover | did: native quest crafting", "REWARD", true, true, false,
            false, false, false, false, false, NAMES)) == Activity.FIGHTING, "Actual combat wins over stale rest and craft");
        check(classify(new Snapshot("travelling to HUNT", "HUNT", true, false, false,
            false, false, false, false, false, NAMES)) == Activity.FIGHTING, "Combat never uses the travel hunt pool");
        check(classify(new Snapshot("engaged", "", true, false, true,
            false, false, false, false, false, NAMES)) == Activity.FIGHTING_ELITE, "Native elite flag selects elite fighting");
        check(classify(state("engaged | did: auto attack | world: travelling to REWARD", "REWARD")) == Activity.FIGHTING,
            "Fresh engaged arbitration counts as combat before the first damage");
        check(classify(state("travel to quest destination | did: rest and recover | world: travelling to REWARD", "REWARD"))
            == Activity.QUEST_REWARD, "Stale rest action cannot override current travel");
        check(classify(state("travel to quest destination | did: native quest crafting | world: travelling to REWARD", "REWARD"))
            == Activity.QUEST_REWARD, "Stale craft action cannot override current travel");
        check(classify(state("ready | did: native quest crafting: work | world: travelling to REWARD", "REWARD"))
            == Activity.CRAFT, "Fresh arbitration craft action wins over cached travel intent");
        check(classify(state("ready | did: earn native quest materials | world: travelling to REWARD", "REWARD"))
            == Activity.QUEST_OBJECTS, "Fresh material action explains current work");
        check(classify(state("ready | did: sell surplus gear | world: travelling to HUNT", "HUNT"))
            == Activity.VENDOR, "The normal surplus sale action identifies vendor work");
        check(classify(state("collecting quest object", "")) == Activity.QUEST_OBJECTS, "Singular quest object status works");
        check(classify(state("using a native quest trigger", "")) == Activity.QUEST_OBJECTS, "Actual quest trigger status works");
        check(classify(state("waiting for human at the dungeon entrance", "")) == Activity.GROUP_WAIT,
            "Actual Find Group entrance wait works");
        check(classify(state("hunting for experience", "GRIND")) == Activity.GRIND,
            "Ordinary questless hunting makes no claim about a quest or gear");
        check(classify(state("travelling to HUNT quest", "FARM")) == Activity.FARM_GEAR,
            "Typed gear hunt purpose survives a generic travel description");
        check(classify(state("visiting nearby service NPC | world: travelling to CONVERSATION", "RPG")) == Activity.SERVICE,
            "A service visit does not invent a quest conversation");
        check(classify(new Snapshot("helping nearby player | world: travelling to REWARD", "REWARD", false, false, false,
            false, false, true, false, true, NAMES)) == Activity.HELP_PLAYER,
            "Active bystander aid wins over stale quest and road work");
        check(classify(new Snapshot("patrol assigned road", "", false, false, false,
            true, true, false, false, false, NAMES)) == Activity.MERCHANT_SHOP, "Open merchant shop wins over travel");
        check(classify(new Snapshot("ready", "", false, false, false,
            false, false, false, false, true, NAMES)) == Activity.HELP_PLAYER, "A real human companion or responder is helping");
        check(activityKey(Activity.REST, NAMES).equals(activityKey(Activity.REST,
            new Names("Another Quest", "Another Npc", "Another Monster", "Another Player"))),
            "Irrelevant target names do not bypass the rest reminder");
    }

    private static void names() {
        for (String dangerous : List.of("12345", "npc_internal", "npc#77", "12 34 56", "route", "Native", "Good\nName",
            "<npc>", "goal selection", "", " ", "Daeva ２"))
            check(safeName(dangerous, "fallback").equals("fallback"), "Reject unsafe name " + dangerous);
        check(safeName(null, "fallback").equals("fallback"), "Missing name uses a generic noun");
        check(safeName("Alyss", "fallback").equals("Alyss"), "A valid player name is preserved");
        check(safeName("Kaisinel's Blessing", "fallback").equals("Kaisinel's Blessing"), "Display apostrophes are preserved");
        check(safeName("Navier", "fallback").equals("Navier"), "Banned tokens match whole words");
        check(safeName("A".repeat(100), "fallback").equals("fallback"), "Long names use a noun instead of truncation");
        Names longNames = new Names("The Long Journey To The Distant Mountains", "A Very Long Name Of A Friendly Quest Giver",
            "A Monster With An Exceptionally Long Name", "A Player With A Very Long Character Name");
        for (Activity activity : Activity.values()) {
            check(safe(phrase(activity, longNames, 0, Set.of())), "Long names retain a complete safe purpose for " + activity);
            check(safe(phrase(activity, new Names(null, "npc#123", "_internal", "42"), 1, Set.of())),
                "Missing and unsafe names retain a safe purpose for " + activity);
        }
    }

    private static boolean emit(Limiter limiter, int bot, String key, Activity activity, long time,
        Set<Integer> humans, java.util.function.BiPredicate<Integer, String> sink) {
        return limiter.emit(bot, key, activity, NAMES, ((long) bot << 32), time, humans, DEFAULTS, sink);
    }

    private static void cooldowns() {
        Limiter limiter = new Limiter(64, 64);
        check(!limiter.due(1, START, false), "Disabled observation does nothing");
        check(limiter.due(1, START, true), "Enabled first observation is due");
        check(!limiter.due(1, START + 4_999, true), "Due gate avoids repeated known-list and snapshot work");
        check(limiter.due(1, START + 5_000, true), "An unsent bot rechecks after the observation interval");
        check(emit(limiter, 1, "first", Activity.CRAFT, START, Set.of(101), (id, line) -> true), "First eligible send works");
        check(!emit(limiter, 1, "changed", Activity.GATHER, START + 89_999, Set.of(101), (id, line) -> true),
            "A changed activity still respects the bot cooldown");
        check(emit(limiter, 1, "changed", Activity.GATHER, START + 90_000, Set.of(101), (id, line) -> true),
            "A changed activity is due at the cooldown boundary");
        check(!emit(limiter, 1, "changed", Activity.GATHER, START + 269_999, Set.of(101), (id, line) -> true),
            "The same activity waits for its reminder");
        check(emit(limiter, 1, "changed", Activity.GATHER, START + 270_000, Set.of(101), (id, line) -> true),
            "Long activity reminder works at the exact boundary");
        Limiter idle = new Limiter(64, 64);
        check(emit(idle, 2, "idle", Activity.IDLE, START, Set.of(101), (id, line) -> true), "First rare idle line can send");
        check(!emit(idle, 2, "idle", Activity.IDLE, START + 180_000, Set.of(101), (id, line) -> true), "Idle is rarer than work reminders");
        check(emit(idle, 2, "idle", Activity.IDLE, START + 600_000, Set.of(101), (id, line) -> true), "Idle can speak again after ten minutes");
        Settings custom = new Settings(2_000, 1_000, 9);
        check(custom.cooldown() == 2_000 && custom.reminder() == 2_000 && custom.humanCap() == 9,
            "Explicit settings remain configurable, with reminder no shorter than cooldown");
    }

    private static void humanCaps() {
        Limiter limiter = new Limiter(64, 64);
        for (int bot = 1; bot <= 4; bot++)
            check(emit(limiter, bot, "work", Activity.CRAFT, START + (bot - 1) * 1_000, Set.of(101), (id, line) -> true),
                "Different bots share the human's rolling allowance");
        List<Integer> received = new ArrayList<>();
        check(emit(limiter, 5, "work", Activity.CRAFT, START + 4_000, Set.of(101, 102), (id, line) -> received.add(id)),
            "A capped human does not prevent an eligible neighboring human receiving");
        check(received.equals(List.of(102)), "Only the human with allowance receives the packet");
        check(!emit(limiter, 6, "work", Activity.CRAFT, START + 59_999, Set.of(101), (id, line) -> true),
            "Four bot lines exhaust the full rolling minute");
        check(emit(limiter, 6, "work", Activity.CRAFT, START + 60_000, Set.of(101), (id, line) -> true),
            "The oldest line expires at sixty seconds and a suppressed bot can retry");
        check(!emit(limiter, 7, "work", Activity.CRAFT, START + 60_999, Set.of(101), (id, line) -> true),
            "Only the oldest line expired, not a fixed minute bucket");
        check(emit(limiter, 7, "work", Activity.CRAFT, START + 61_000, Set.of(101), (id, line) -> true),
            "The second old line expires independently");
    }

    private static void repetition() {
        Limiter limiter = new Limiter(64, 64);
        Settings rapid = new Settings(0, 0, 100);
        Set<String> lines = new HashSet<>();
        for (int variant = 0; variant < 8; variant++)
            check(limiter.emit(1, "craft", Activity.CRAFT, NAMES, 35, START, Set.of(101), rapid,
                (id, line) -> lines.add(line)), "Choose another production variant while prior lines are protected");
        check(lines.size() == 8, "The same bot never repeats a protected line");
        check(!limiter.emit(1, "craft", Activity.CRAFT, NAMES, 35, START + 599_999, Set.of(101), rapid, (id, line) -> true),
            "Exhausted variants stay suppressed for the whole ten minutes");
        check(limiter.emit(1, "craft", Activity.CRAFT, NAMES, 35, START + 600_000, Set.of(101), rapid, (id, line) -> true),
            "A line becomes available at the ten-minute boundary");
    }

    private static void acceptedSends() {
        Limiter limiter = new Limiter(64, 64);
        int[] packets = {0};
        check(!emit(limiter, 1, "craft", Activity.CRAFT, START, Set.of(), (id, line) -> { packets[0]++; return true; }),
            "No nearby human means no send");
        check(packets[0] == 0, "No-human case never invokes the packet sink");
        check(!emit(limiter, 1, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> false),
            "A recipient lost before packet delivery is not accepted");
        check(emit(limiter, 1, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true),
            "No-human and failed sends do not consume bot timers or history");
        for (int bot = 2; bot <= 4; bot++)
            check(emit(limiter, bot, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true),
                "Failed sends did not consume the human allowance");
        check(!emit(limiter, 5, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true),
            "Exactly four accepted sends consume the human allowance");
        Limiter partial = new Limiter(64, 64);
        Set<Integer> ordered = new LinkedHashSet<>(List.of(101, 102));
        try {
            emit(partial, 1, "craft", Activity.CRAFT, START, ordered, (id, line) -> {
                if (id == 102)
                    throw new IllegalStateException("simulated packet failure");
                return true;
            });
            check(false, "The simulated second recipient failure must occur");
        } catch (IllegalStateException expected) {
            check(true, "A packet failure after one accepted recipient is observed");
        }
        check(!emit(partial, 1, "gather", Activity.GATHER, START + 1_000, Set.of(103), (id, line) -> true),
            "A partial delivery keeps the first accepted send's bot cooldown");
        Settings zeroCap = new Settings(90_000, 180_000, 0);
        check(!partial.emit(3, "craft", Activity.CRAFT, NAMES, 1, START, Set.of(101), zeroCap, (id, line) -> true),
            "Explicit zero human allowance suppresses sends");
    }

    private static void capacity() {
        Limiter limiter = new Limiter(1, 1);
        check(emit(limiter, 1, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true), "A bounded limiter accepts its first identities");
        check(!emit(limiter, 2, "craft", Activity.CRAFT, START + 90_000, Set.of(101), (id, line) -> true),
            "Capacity suppresses new bot identities instead of resetting protected state");
        check(!emit(limiter, 1, "changed", Activity.GATHER, START + 1_000, Set.of(101), (id, line) -> true),
            "Capacity pressure never erases an existing bot cooldown");
        check(!limiter.due(2, START + 599_999, true), "Bot state cannot expire before repeat protection ends");
        check(limiter.due(2, START + 631_000, true), "Inactive identities eventually expire after the protected window");
        Limiter humans = new Limiter(64, 1);
        check(emit(humans, 1, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true), "Human bound accepts its first listener");
        check(!emit(humans, 2, "craft", Activity.CRAFT, START, Set.of(102), (id, line) -> true),
            "Human capacity refuses a new identity without evicting rolling limits");
        for (int bot = 3; bot <= 5; bot++)
            check(emit(humans, bot, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true), "Protected human allowance stays available");
        check(!emit(humans, 6, "craft", Activity.CRAFT, START, Set.of(101), (id, line) -> true),
            "Human capacity pressure cannot bypass the shared cap");
    }

    private static void wiring(Path root) throws Exception {
        Path gameServer = Files.isDirectory(root.resolve("game-server")) ? root.resolve("game-server") : root;
        String session = Files.readString(gameServer.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotSession.java"));
        check(session.contains("PlayerBotActivityChat.observe(this,System.currentTimeMillis())"),
            "Normal session tick invokes production observation");
    }

    private PlayerBotActivityChatCheck() {}
}
