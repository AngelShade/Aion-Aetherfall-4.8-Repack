package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;

/** Optional, local activity chatter. Reading an activity never drives that activity. */
public final class PlayerBotActivityChat {
    private static final Logger LOG = LoggerFactory.getLogger(PlayerBotActivityChat.class);
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static final Limiter LIMITER = new Limiter(20_000, 4_000);
    static final long REPEAT_MS = 600_000;
    private static final long OBSERVE_MS = 5_000;
    private static final Pattern BANNED = Pattern.compile(
        "(?iu)\\b(native|planning|route|geometry|traversal|corridor|arbitration|goal|selection|admitted|quota)\\b");
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Nd}#_\\r\\n]");
    private static final Names GENERIC = new Names("a quest", "the quest giver", "a monster", "a nearby player");

    enum Activity {
        QUEST_REWARD, QUEST_HUNT, QUEST_TALK, QUEST_OBJECTS, FARM_GEAR, HELP_PLAYER,
        REST, VENDOR, CRAFT, GATHER, MERCHANT_TRAVEL, MERCHANT_SHOP, WARDEN_PATROL,
        GROUP_WAIT, GROUP_GATHER, STUCK, IDLE, FIGHTING, FIGHTING_ELITE, REWARDS, GRIND, SERVICE
    }

    record Names(String quest, String npc, String monster, String player) {}

    record Snapshot(String state, String kind, boolean combat, boolean recovering,
        boolean elite, boolean merchant, boolean shop, boolean warden, boolean group,
        boolean helping, Names names) {}

    record Settings(long cooldown, long reminder, int humanCap) {
        Settings {
            cooldown = Math.max(0, cooldown);
            reminder = Math.max(cooldown, reminder);
            humanCap = Math.max(0, humanCap);
        }
    }

    /** Safe to call from the session's finally block, including unsuccessful ticks. */
    public static void observe(PlayerBotSession session, long now) {
        try {
            if (!PlayerBotConfig.ACTIVITY_CHAT_ENABLED || session == null || session.closing())
                return;
            Player bot = session.bot();
            if (bot == null || bot.isDead() || !bot.isSpawned()
                || !LIMITER.due(bot.getObjectId(), now, true))
                return;
            Map<Integer, Player> listeners = new LinkedHashMap<>();
            bot.getKnownList().forEachPlayer(human -> {
                if (eligible(bot, human))
                    listeners.put(human.getObjectId(), human);
            });
            if (listeners.isEmpty())
                return;
            Snapshot snapshot = snapshot(session);
            Activity activity = classify(snapshot);
            // Raw diagnostic wording never forms a message or an activity identity.
            String key = activityKey(activity, snapshot.names());
            Settings settings = new Settings(PlayerBotConfig.ACTIVITY_CHAT_COOLDOWN_SECONDS * 1_000L,
                PlayerBotConfig.ACTIVITY_CHAT_REMINDER_SECONDS * 1_000L,
                PlayerBotConfig.ACTIVITY_CHAT_HUMAN_CAP_PER_MINUTE);
            long seed = ((long) bot.getObjectId() << 32) ^ bot.getName().hashCode();
            LIMITER.emit(bot.getObjectId(), key, activity, snapshot.names(), seed, now,
                listeners.keySet(), settings, (humanId, line) -> {
                    Player human = listeners.get(humanId);
                    if (!eligible(bot, human))
                        return false;
                    PacketSendUtility.sendPacket(human, new SM_MESSAGE(bot, line, ChatType.NORMAL));
                    return true;
                });
        } catch (RuntimeException error) {
            if (FAILURES.getAndIncrement() % 100 == 0)
                LOG.warn("Playerbot activity chat observation failed", error);
        }
    }

    private static boolean eligible(Player bot, Player human) {
        return human != null && !human.isPlayerBot() && human.isOnline() && human.isSpawned()
            && human.getKnownList().sees(bot)
            && (!human.getBlockList().contains(bot.getObjectId()) || human.isStaff() || bot.isStaff())
            // Native NORMAL say uses the sender's known list. Recheck its current range.
            && PositionUtil.isInRange(bot, human, Math.max(bot.getVisibleDistance(), human.getVisibleDistance()));
    }

    private static Snapshot snapshot(PlayerBotSession session) {
        Player bot = session.bot();
        Map<String, Object> selected = PlayerBotWorldActivities.activity(bot);
        String kind = String.valueOf(selected.getOrDefault("kind", ""));
        int quest = number(selected.get("quest"));
        int npc = number(selected.get("npc"));
        if (kind.equals("HUNT") && quest == 0)
            kind = PlayerBotWorldActivities.goalHuntTravel(bot) ? "FARM" : "GRIND";
        var questTemplate = quest == 0 ? null : DataManager.QUEST_DATA.getQuestById(quest);
        var npcTemplate = npc == 0 ? null : DataManager.NPC_DATA.getNpcTemplate(npc);
        Npc target = bot.getTarget() instanceof Npc value ? value : null;
        Npc enemy = target != null && !target.isDead() && bot.isEnemy(target) ? target : null;
        String state = session.diagnosticState();
        boolean combat = bot.getController().isInCombat() || combatState(currentState(state));
        NpcRating rating = enemy == null ? null : enemy.getObjectTemplate().getRating();
        boolean elite = combat && (rating == NpcRating.ELITE || rating == NpcRating.HERO
            || rating == NpcRating.LEGENDARY);
        boolean answering = PlayerBotBystanders.answering(bot);
        if (answering)
            state = "helping nearby player | " + currentState(state);
        var interaction = bot.getInteractionTask();
        if (interaction != null && interaction.isInProgress()) {
            if (interaction instanceof com.aionemu.gameserver.skillengine.task.CraftingTask)
                state = "crafting at a workbench | " + currentState(state);
            else if (interaction instanceof com.aionemu.gameserver.skillengine.task.GatheringTask)
                state = "gathering materials | " + currentState(state);
        }
        boolean merchant = PlayerBotRoadDuty.onDuty(bot) && PlayerBotRoadDuty.merchant(bot);
        boolean warden = PlayerBotRoadDuty.publicWarden(bot);
        if (merchant || warden)
            state += " | " + PlayerBotRoadDuty.planningReason(bot);
        if (PlayerBotFindGroup.waitingAtEntrance(session))
            state = "waiting at the dungeon entrance | " + state;
        String npcName = npcTemplate == null ? null : npcTemplate.getName();
        if (npcTemplate == null && target != null && target.isSpawned() && !target.isDead() && !bot.isEnemy(target))
            npcName = target.getObjectTemplate().getName();
        String monsterName = enemy == null ? null : enemy.getObjectTemplate().getName();
        boolean hunting = kind.equals("HUNT") || kind.equals("GRIND") || kind.equals("FARM");
        if (!combat && hunting && npcTemplate != null)
            monsterName = npcTemplate.getName();
        Names names = new Names(questTemplate == null ? null : questTemplate.getName(), npcName, monsterName,
            session.owner() == bot ? null : session.owner().getName());
        return new Snapshot(state, kind, combat, PlayerBotRecoveryIntent.active(bot), elite,
            merchant, bot.hasStore(), warden, PlayerBotFindGroup.cooperative(bot),
            session.owner() != bot && !session.owner().isPlayerBot()
                || answering, names);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    static Activity classify(Snapshot snapshot) {
        String state = currentState(snapshot.state());
        String kind = snapshot.kind() == null ? "" : snapshot.kind().toUpperCase(Locale.ROOT);
        // Current combat and recovery win over the previous action and travel intent.
        if (snapshot.combat() || combatState(state)) {
            if (snapshot.elite())
                return Activity.FIGHTING_ELITE;
            return Activity.FIGHTING;
        }
        if (snapshot.recovering() || state.contains("rest and recover") || state.contains("recovering health or mana"))
            return Activity.REST;
        if (state.contains("blocked by geometry") || state.contains("stuck") || state.contains("lost:")
            || state.equals("lost") || state.startsWith("lost ") || state.contains("move: lost")
            || state.contains("ground route unavailable") || state.contains("goal unreachable")
            || state.contains("pacing") || state.contains("cannot reach"))
            return Activity.STUCK;
        if (state.contains("helping nearby player") || state.contains("helping player"))
            return Activity.HELP_PLAYER;
        if (state.contains("entrance") && (state.contains("waiting") || state.contains("gathering")))
            return Activity.GROUP_WAIT;
        if (snapshot.merchant())
            return snapshot.shop() ? Activity.MERCHANT_SHOP : Activity.MERCHANT_TRAVEL;
        if (state.contains("merchant") && state.contains("travelling"))
            return Activity.MERCHANT_TRAVEL;
        if (state.contains("merchant serving") || state.contains("shop open"))
            return Activity.MERCHANT_SHOP;
        if (snapshot.warden() || state.contains("warden patrolling"))
            return Activity.WARDEN_PATROL;
        if (state.contains("crafting") || state.contains("workbench"))
            return Activity.CRAFT;
        if (state.contains("quest materials") || state.contains("quest object") || state.contains("quest trigger"))
            return Activity.QUEST_OBJECTS;
        if (state.contains("gathering with find group") || snapshot.group() && state.contains("gathering"))
            return Activity.GROUP_GATHER;
        if (state.contains("gathering") || state.contains("herbs") || state.contains("essencetapping"))
            return Activity.GATHER;
        if (state.contains("vendor") || state.contains("selling") || state.contains("sell surplus") || state.contains("central market")
            || state.contains("trading with owner"))
            return Activity.VENDOR;
        if (state.contains("collecting own rewards"))
            return Activity.REWARDS;
        if (kind.equals("REWARD") || state.contains("travelling to reward"))
            return Activity.QUEST_REWARD;
        if (kind.equals("FARM") || state.contains("for gear") || state.contains("equipment"))
            return Activity.FARM_GEAR;
        if (kind.equals("GRIND"))
            return Activity.GRIND;
        if (kind.equals("HUNT") || state.contains("travelling to hunt"))
            return Activity.QUEST_HUNT;
        if (kind.equals("OBJECT"))
            return Activity.QUEST_OBJECTS;
        if (kind.equals("RPG"))
            return Activity.SERVICE;
        if (kind.equals("CONVERSATION") || state.contains("travelling to conversation")
            || state.contains("quest conversation"))
            return Activity.QUEST_TALK;
        if (snapshot.helping() || state.contains("helping player") || state.contains("following owner"))
            return Activity.HELP_PLAYER;
        if (snapshot.group())
            return Activity.GROUP_GATHER;
        return Activity.IDLE;
    }

    private static boolean combatState(String state) {
        return state.startsWith("engaged |") || state.equals("engaged")
            || state.contains("planning paused: engaged") || state.contains("planning paused: combat");
    }

    /** Only normal arbitration statuses guarantee that the last action belongs to this tick. */
    private static String currentState(String diagnostic) {
        if (diagnostic == null)
            return "";
        String state = diagnostic.toLowerCase(Locale.ROOT);
        int action = state.indexOf(" | did:");
        int separator = state.indexOf(" |");
        String primary = (separator < 0 ? state : state.substring(0, separator)).strip();
        boolean arbitration = primary.equals("ready") || primary.equals("engaged") || primary.equals("recovering");
        if (action >= 0 && !arbitration) {
            int world = state.indexOf(" | world:", action);
            state = state.substring(0, action) + (world < 0 ? "" : state.substring(world));
        }
        int summaryAction = state.indexOf("; last action:");
        return summaryAction < 0 ? state : state.substring(0, summaryAction);
    }

    static String activityKey(Activity activity, Names names) {
        Names source = names == null ? GENERIC : names;
        String quest = safeName(source.quest(), GENERIC.quest());
        String npc = safeName(source.npc(), GENERIC.npc());
        String monster = safeName(source.monster(), GENERIC.monster());
        String player = safeName(source.player(), GENERIC.player());
        String detail = switch (activity) {
            case QUEST_REWARD, QUEST_TALK -> quest + "|" + npc;
            case QUEST_HUNT -> quest + "|" + monster;
            case QUEST_OBJECTS -> quest;
            case SERVICE -> npc;
            case FARM_GEAR, GRIND, FIGHTING, FIGHTING_ELITE -> monster;
            case HELP_PLAYER -> player;
            default -> "";
        };
        return activity.name() + "|" + detail;
    }

    static String safeName(String value, String fallback) {
        if (value == null)
            return fallback;
        String name = value.strip().replaceAll(" +", " ");
        if (name.isEmpty() || name.length() > 40 || BANNED.matcher(name).find()
            || !name.matches("[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*"))
            return fallback;
        return name;
    }

    static boolean safeLine(String line) {
        return line != null && !line.isBlank() && line.length() <= 80
            && !BANNED.matcher(line).find() && !UNSAFE.matcher(line).find();
    }

    static String phrase(Activity activity, Names names, long seed, Set<String> excluded) {
        Names source = names == null ? GENERIC : names;
        Names generic = activity == Activity.SERVICE
            ? new Names(GENERIC.quest(), "a nearby service NPC", GENERIC.monster(), GENERIC.player()) : GENERIC;
        Names clean = new Names(safeName(source.quest(), generic.quest()),
            safeName(source.npc(), generic.npc()), safeName(source.monster(), generic.monster()),
            safeName(source.player(), GENERIC.player()));
        String[] choices = phrases(activity);
        int first = Math.floorMod(mix(seed), choices.length);
        for (int offset = 0; offset < choices.length; offset++) {
            String template = choices[(first + offset) % choices.length];
            String line = render(template, clean);
            if (!safeLine(line))
                line = render(template, generic);
            if (safeLine(line) && !excluded.contains(line))
                return line;
        }
        return null;
    }

    private static String render(String template, Names names) {
        return template.replace("$q", names.quest()).replace("$n", names.npc())
            .replace("$m", names.monster()).replace("$p", names.player());
    }

    private static int mix(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdl;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53l;
        return (int) (value ^ (value >>> 33));
    }

    private static String[] phrases(Activity activity) {
        return switch (activity) {
            case QUEST_REWARD -> new String[] {
                "Off to $n to turn in $q.", "Heading to $n for my reward from $q.",
                "Finished $q. Time to see $n.", "Taking $q back to $n.",
                "Going to $n to hand in $q.", "$q is done. Back to $n for the reward.",
                "Looking for $n to finish $q.", "Time to collect my reward for $q from $n." };
            case QUEST_HUNT -> new String[] {
                "Hunting $m for $q.", "Looking for $m to finish $q.",
                "Still need $m for $q.", "Off to hunt $m for $q.",
                "Working on $q. Need to find $m.", "After $m again for $q.",
                "Going after $m to get $q done.", "A bit more hunting for $q. Looking for $m." };
            case QUEST_TALK -> new String[] {
                "Going to ask $n about $q.", "Need to speak with $n about $q.",
                "Off to see $n for $q.", "Checking with $n to move $q along.",
                "Looking for $n about $q.", "Time to talk to $n and work on $q.",
                "Stopping by $n to ask about $q.", "Hoping $n can help with $q." };
            case SERVICE -> new String[] {
                "Stopping by $n before I head out.", "Off to see $n and get ready for my next trip.",
                "Visiting $n to sort a few things out.", "Need to see $n before I carry on.",
                "Taking a moment to visit $n.", "Checking in with $n before I leave.",
                "Heading to $n to get a few things sorted.", "A quick stop at $n, then back out again." };
            case QUEST_OBJECTS -> new String[] {
                "Looking for the supplies I need for $q.", "Collecting materials to finish $q.",
                "Still gathering what I need for $q.", "Checking for quest supplies around here.",
                "Need some more materials for $q.", "Picking up supplies before I turn in $q.",
                "Working on the collection for $q.", "Looking around for the last quest materials." };
            case FARM_GEAR -> new String[] {
                "Hunting $m for better gear.", "Trying my luck with $m for an upgrade.",
                "Could use better equipment. Hunting for a while.", "Staying here to hunt for some better gear.",
                "Looking for an upgrade from $m.", "A little more hunting. My gear could use the help.",
                "Hoping $m drops something I can use.", "Fighting for some gear before I move on." };
            case GRIND -> new String[] {
                "Hunting $m for experience and loot.", "Getting some more practice fighting $m.",
                "Hunting here for a while to get stronger.", "Fighting nearby monsters for experience.",
                "A bit more hunting before I move on.", "Looking for $m to earn some experience.",
                "Staying here for a little hunting and loot.", "Working on getting stronger before my next trip." };
            case HELP_PLAYER -> new String[] {
                "Sticking with $p to help out.", "Helping $p get through this.",
                "Keeping up with $p. Might need a hand.", "Ready to lend $p a hand.",
                "Going along with $p to help.", "Watching out for $p while we travel.",
                "Backing up $p for a while.", "Staying close so I can help $p." };
            case REST -> new String[] {
                "Need a sec to get my health and mana back.", "Resting a moment before the next fight.",
                "Need to regen a bit before I carry on.", "Catching my breath. Low on health or mana.",
                "A short rest, then back to it.", "Recovering here so I can keep going.",
                "Sitting for a moment to get my strength back.", "Letting my health and mana recover first." };
            case VENDOR -> new String[] {
                "Sorting out supplies before I head back out.", "Bags are full, off to sell some loot.",
                "Taking care of my trading for a moment.", "Checking supplies for the next trip.",
                "Need to sort my bags and supplies.", "Doing a little trading before I carry on.",
                "Stocking up so I can stay out longer.", "Getting my supplies in order for more adventuring." };
            case CRAFT -> new String[] {
                "Heading to the workbench to finish some crafting.", "Making what I need before I head out again.",
                "Working on a craft at the workbench.", "Putting these materials to use at the workbench.",
                "Time for some crafting. I have work to finish.", "Stopping to make a few things I need.",
                "Getting my crafting work done while I am here.", "At the workbench to finish what I started." };
            case GATHER -> new String[] {
                "Gathering useful materials while I am out here.", "Looking for herbs and other supplies.",
                "Picking up materials for my work.", "Gathering a few things before I move on.",
                "Keeping an eye out for useful herbs.", "Collecting supplies for my next craft.",
                "Looking around for materials I can use.", "A little gathering now saves me a trip later." };
            case MERCHANT_TRAVEL -> new String[] {
                "Taking my wares to the next stop.", "Moving along to find more customers.",
                "Heading to my next trading stop.", "Bringing supplies down the road.",
                "Time to move on and sell a few things.", "Looking for another place to offer my wares.",
                "On my way to the next market stop.", "Carrying my stock to the next trading spot." };
            case MERCHANT_SHOP -> new String[] {
                "Stopping here to sell my wares.", "Shop is open while I take a break here.",
                "Offering a few supplies before I move on.", "Taking a trading break. Have a look at my wares.",
                "Staying here for anyone who needs supplies.", "Selling what I brought along the road.",
                "A good spot to offer my wares for a while.", "Trading here before the next stretch of road." };
            case WARDEN_PATROL -> new String[] {
                "Keeping watch along the road for trouble.", "Patrolling here to keep travellers safe.",
                "Checking the road so others can pass safely.", "Watching for anything threatening travellers.",
                "Making another pass to keep this road clear.", "Keeping an eye on the road for anyone in trouble.",
                "Walking the road and watching for danger.", "Staying on watch for travellers who need help." };
            case GROUP_WAIT -> new String[] {
                "Waiting at the entrance for the party.", "Holding here so we can enter together.",
                "Waiting for everyone before we go inside.", "Staying by the entrance until the group is ready.",
                "Taking a moment here while the party catches up.", "Ready at the entrance. Waiting for the others.",
                "Keeping the meeting spot until everyone arrives.", "Waiting here so nobody misses the group." };
            case GROUP_GATHER -> new String[] {
                "Meeting up with the party before we head out.", "Getting the group together for the next job.",
                "Catching up with the party so we can work together.", "Waiting for the group to get ready.",
                "Sticking with the party for this trip.", "Gathering the party before we carry on.",
                "Finding the others so we can go together.", "Getting ready to head out with the group." };
            case STUCK -> new String[] {
                "Ugh, that way is blocked. Trying another way.", "Having trouble getting through here.",
                "A little lost. Looking for a clear way forward.", "Need to find another way past this spot.",
                "Something is in the way. Trying to get around it.", "Looking for a safer way to keep moving.",
                "Got turned around here. Finding my way again.", "This way is awkward. Checking how to get past." };
            case IDLE -> new String[] {
                "Taking a short break before the next job.", "Looking around for something worth doing.",
                "Catching my breath before I head out again.", "Seeing what needs doing around here.",
                "Having a quiet moment before I carry on.", "Checking things out before my next trip.",
                "Taking it easy for a moment between jobs.", "Anyone need a hand with a quest?" };
            case FIGHTING -> new String[] {
                "Dealing with $m before I carry on.", "Busy fighting $m. Need to finish this first.",
                "Taking care of this fight before I move on.", "Fighting $m so I can get back to work.",
                "Have to get through this fight first.", "Keeping $m busy for a moment.",
                "Trying to bring $m down safely.", "Caught in a fight. Finishing this before I go." };
            case FIGHTING_ELITE -> new String[] {
                "$m is tough. Taking this fight carefully.", "Fighting a tough enemy. Could use a hand here.",
                "This elite needs my full attention for a bit.", "Working on an elite before I carry on.",
                "Taking my time with this elite. It hits hard.", "Trying to bring this tough enemy down safely.",
                "Busy with an elite. This might take a moment.", "Giving this elite everything I have." };
            case REWARDS -> new String[] {
                "Got some rewards waiting. Picking those up.", "Claiming what I earned before I head out.",
                "Taking a sec to collect my rewards.", "Checking the rewards I earned along the way.",
                "Picking up my rewards while I have a moment.", "A few rewards to collect, then back to it.",
                "Collecting my rewards before the next job.", "Time to grab the rewards I earned." };
        };
    }

    /** In-memory pacing only. Persistent identities and activity progress remain with their owners. */
    static final class Limiter {
        private final ConcurrentHashMap<Integer, BotState> bots = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, HumanState> humans = new ConcurrentHashMap<>();
        private final AtomicLong nextSweep = new AtomicLong();
        private Iterator<Map.Entry<Integer, BotState>> botSweep;
        private Iterator<Map.Entry<Integer, HumanState>> humanSweep;
        private final int botLimit;
        private final int humanLimit;

        Limiter(int botLimit, int humanLimit) {
            this.botLimit = botLimit;
            this.humanLimit = humanLimit;
        }

        boolean due(int bot, long now, boolean enabled) {
            if (!enabled)
                return false;
            sweep(now);
            BotState state = bot(bot, now);
            if (state == null)
                return false;
            synchronized (state) {
                if (bots.get(bot) != state)
                    return false;
                state.touched = now;
                if (now < state.nextObservation)
                    return false;
                state.nextObservation = now + OBSERVE_MS;
                return true;
            }
        }

        boolean emit(int bot, String key, Activity activity, Names names, long seed, long now,
            Set<Integer> recipients, Settings settings, BiPredicate<Integer, String> send) {
            if (recipients.isEmpty() || settings.humanCap() == 0)
                return false;
            BotState state = bot(bot, now);
            if (state == null)
                return false;
            synchronized (state) {
                if (bots.get(bot) != state)
                    return false;
                state.touched = now;
                long reminder = activity == Activity.IDLE ? Math.max(REPEAT_MS, settings.reminder()) : settings.reminder();
                if (state.sent && (now - state.lastSent < settings.cooldown()
                    || key.equals(state.key) && now - state.lastSent < reminder))
                    return false;
                state.lines.entrySet().removeIf(entry -> now - entry.getValue() >= REPEAT_MS);
                String line = phrase(activity, names, seed ^ state.sequence, state.lines.keySet());
                if (line == null)
                    return false;
                boolean accepted = false;
                for (int recipient : recipients) {
                    HumanState human = human(recipient, now);
                    if (human == null)
                        continue;
                    synchronized (human) {
                        if (humans.get(recipient) != human)
                            continue;
                        human.touched = now;
                        while (!human.sent.isEmpty() && now - human.sent.peekFirst() >= 60_000)
                            human.sent.removeFirst();
                        if (human.sent.size() >= settings.humanCap() || !send.test(recipient, line))
                            continue;
                        human.sent.addLast(now);
                        if (!accepted) {
                            state.lastSent = now;
                            state.sent = true;
                            state.key = key;
                            state.lines.put(line, now);
                            state.sequence++;
                            state.nextObservation = now + Math.max(OBSERVE_MS, settings.cooldown());
                            state.keepUntil = now + Math.max(REPEAT_MS, Math.max(settings.cooldown(), reminder));
                            accepted = true;
                        }
                    }
                }
                return accepted;
            }
        }

        private BotState bot(int id, long now) {
            BotState existing = bots.get(id);
            if (existing != null)
                return existing;
            synchronized (bots) {
                existing = bots.get(id);
                if (existing != null)
                    return existing;
                if (bots.size() >= botLimit)
                    return null;
                BotState created = new BotState();
                created.touched = now;
                bots.put(id, created);
                return created;
            }
        }

        private HumanState human(int id, long now) {
            HumanState existing = humans.get(id);
            if (existing != null)
                return existing;
            synchronized (humans) {
                existing = humans.get(id);
                if (existing != null)
                    return existing;
                if (humans.size() >= humanLimit)
                    return null;
                HumanState created = new HumanState();
                created.touched = now;
                humans.put(id, created);
                return created;
            }
        }

        private void sweep(long now) {
            long next = nextSweep.get();
            if (now < next || !nextSweep.compareAndSet(next, now + 30_000))
                return;
            expire(now);
        }

        private synchronized void expire(long now) {
            // A bounded slice per sweep, never a whole-population cleanup in a tick.
            if (botSweep == null || !botSweep.hasNext())
                botSweep = bots.entrySet().iterator();
            int remaining = 128;
            while (botSweep.hasNext() && remaining-- > 0) {
                var entry = botSweep.next();
                BotState state = entry.getValue();
                synchronized (state) {
                    if (now - state.touched >= REPEAT_MS && now >= state.keepUntil)
                        bots.remove(entry.getKey(), state);
                }
            }
            if (humanSweep == null || !humanSweep.hasNext())
                humanSweep = humans.entrySet().iterator();
            remaining = 128;
            while (humanSweep.hasNext() && remaining-- > 0) {
                var entry = humanSweep.next();
                HumanState state = entry.getValue();
                synchronized (state) {
                    if (now - state.touched >= REPEAT_MS)
                        humans.remove(entry.getKey(), state);
                }
            }
        }

        private static final class BotState {
            long touched, nextObservation, lastSent, keepUntil, sequence;
            boolean sent;
            String key;
            final Map<String, Long> lines = new HashMap<>();
        }

        private static final class HumanState {
            long touched;
            final ArrayDeque<Long> sent = new ArrayDeque<>();
        }
    }

    private PlayerBotActivityChat() {}
}
