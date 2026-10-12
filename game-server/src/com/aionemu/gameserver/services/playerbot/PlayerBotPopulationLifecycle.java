/* RandomPlayerbotFactory::CreateRandomBots and RandomPlayerbotMgr::Init/ProcessBot,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Native execution stays in Aion. */
package com.aionemu.gameserver.services.playerbot;

/** Pure population transitions; actor, party and durable custody authority remain with the caller. */
final class PlayerBotPopulationLifecycle {
    /** Factory completion precedes automatic admission; excess saved identities are retained. */
    static boolean factoryReady(int registered, int capacity) {
        nonnegative(registered, "registered factory population");
        nonnegative(capacity, "factory capacity");
        return registered >= capacity;
    }

    /** Reset transient automatic admission only, never manual actors or an unfinished native save. */
    static boolean resetAdmission(boolean automatic, boolean active, boolean retiring, boolean savingHeld) {
        return automatic && !active && !retiring && !savingHeld;
    }

    /** Presence expiration belongs to its saved event, independent of target changes and placement. */
    static boolean expiredPresence(boolean periodic, long presentUntil, long now) {
        nonnegative(presentUntil, "presence deadline");
        nonnegative(now, "current time");
        return periodic && presentUntil > 0 && now >= presentUntil;
    }

    /** Combat, casting, death and nearby observers do not extend an expired presence event. */
    static boolean retirementAllowed(boolean autonomous, boolean closing, boolean inParty,
                                     boolean inAlliance, boolean custodyUncertain) {
        return autonomous && !closing && !inParty && !inAlliance && !custodyUncertain;
    }

    /** Placement may redistribute existing excess; this value must never authorize new admission. */
    static int redistributionCeiling(int chosenTarget, int alreadyAdmitted) {
        nonnegative(chosenTarget, "chosen target");
        nonnegative(alreadyAdmitted, "admitted population");
        return Math.max(chosenTarget, alreadyAdmitted);
    }

    /** Optional extra cooldown cannot shorten a previously committed deadline, even when disabled. */
    static long postExpiryOfflineUntil(boolean enabled, long previousDeadline, long sampledDeadline) {
        nonnegative(previousDeadline, "previous offline deadline");
        nonnegative(sampledDeadline, "sampled offline deadline");
        return enabled ? Math.max(previousDeadline, sampledDeadline) : previousDeadline;
    }

    /** Previously saved breaks continue to apply regardless of the current optional cooldown setting. */
    static boolean admissionAllowed(long offlineUntil, long now) {
        nonnegative(offlineUntil, "offline deadline");
        nonnegative(now, "current time");
        return now >= offlineUntil;
    }

    private static void nonnegative(long value, String description) {
        if (value < 0) throw new IllegalArgumentException("Negative " + description);
    }

    private PlayerBotPopulationLifecycle() {}
}
