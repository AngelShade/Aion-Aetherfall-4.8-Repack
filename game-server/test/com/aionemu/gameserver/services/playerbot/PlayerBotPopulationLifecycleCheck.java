package com.aionemu.gameserver.services.playerbot;

/** Population event boundaries and preservation under restart/target changes; no actors or storage. */
public final class PlayerBotPopulationLifecycleCheck {
    private static int checks;
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }
    private static void refused(Runnable action) {
        try { action.run(); throw new AssertionError("Malformed lifecycle snapshot admitted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        for (int capacity : new int[]{0, 1, 806, 1500, 3000, 12000, Integer.MAX_VALUE}) {
            check(PlayerBotPopulationLifecycle.factoryReady(capacity, capacity), "exact reserve completion admits manager work");
            if (capacity > 0) check(!PlayerBotPopulationLifecycle.factoryReady(capacity - 1, capacity), "one missing factory identity keeps automatic admission gated");
            if (capacity < Integer.MAX_VALUE) check(PlayerBotPopulationLifecycle.factoryReady(capacity + 1, capacity), "lower factory bounds retain excess saved identities");
        }
        for (int flags = 0; flags < 16; flags++) {
            boolean automatic = (flags & 1) != 0, active = (flags & 2) != 0;
            boolean retiring = (flags & 4) != 0, savingHeld = (flags & 8) != 0;
            boolean reset = PlayerBotPopulationLifecycle.resetAdmission(automatic, active, retiring, savingHeld);
            check(reset == (flags == 1), "startup cannot discard manual, active, retiring or save-held admission");
        }
        for (long deadline : new long[]{1, 600_000, 28_800_000, Long.MAX_VALUE}) {
            check(!PlayerBotPopulationLifecycle.expiredPresence(true, deadline, deadline - 1), "live saved event does not expire early");
            check(PlayerBotPopulationLifecycle.expiredPresence(true, deadline, deadline), "expiry occurs at the native saved deadline");
            check(!PlayerBotPopulationLifecycle.expiredPresence(false, deadline, deadline), "permanent mode does not invent timed expiry");
            check(PlayerBotPopulationLifecycle.admissionAllowed(deadline, deadline), "previously saved offline break permits exact deadline reuse");
            check(!PlayerBotPopulationLifecycle.admissionAllowed(deadline, deadline - 1), "previously saved offline break remains honored");
        }
        check(!PlayerBotPopulationLifecycle.expiredPresence(true, 0, Long.MAX_VALUE), "zero presence is not an expired online event");
        check(PlayerBotPopulationLifecycle.admissionAllowed(0, 0), "absent offline event permits reuse");
        for (int flags = 0; flags < 32; flags++) {
            boolean automatic = (flags & 1) != 0, closing = (flags & 2) != 0, party = (flags & 4) != 0;
            boolean alliance = (flags & 8) != 0, custody = (flags & 16) != 0;
            boolean retire = PlayerBotPopulationLifecycle.retirementAllowed(automatic, closing, party, alliance, custody);
            check(retire == (flags == 1), "retirement preserves controller, party/alliance and native custody ownership");
        }
        for (int target : new int[]{0, 1, 806, 1200, 1500, Integer.MAX_VALUE})
            for (int admitted : new int[]{0, 1, 713, 806, 1500, Integer.MAX_VALUE}) {
                int ceiling = PlayerBotPopulationLifecycle.redistributionCeiling(target, admitted);
                check(ceiling >= target && ceiling >= admitted && (ceiling == target || ceiling == admitted),
                    "geographic redistribution retains target-lowering excess without allocating slots");
            }
        for (long previous : new long[]{0, 600_000, 28_800_000, Long.MAX_VALUE})
            for (long sampled : new long[]{0, 600_000, 28_800_000, Long.MAX_VALUE}) {
                check(PlayerBotPopulationLifecycle.postExpiryOfflineUntil(false, previous, sampled) == previous,
                    "default pinned expiry preserves old deadline without manufacturing a new cooldown");
                long enabled = PlayerBotPopulationLifecycle.postExpiryOfflineUntil(true, previous, sampled);
                check(enabled >= previous && enabled >= sampled && (enabled == previous || enabled == sampled),
                    "optional offline delay never shortens a committed break");
            }
        // Lifecycle trace: startup, lower target, protected expiry, then saved-deadline reuse.
        int registered = 1600, admitted = 806, target = 713;
        check(PlayerBotPopulationLifecycle.factoryReady(registered, 1600), "saved reserve needs no new creation after restart");
        check(PlayerBotPopulationLifecycle.resetAdmission(true, false, false, false), "inactive automatic admission is reconstructed on startup");
        check(PlayerBotPopulationLifecycle.redistributionCeiling(target, admitted) == 806, "lower target does not force immediate active contraction");
        long expiry = 1_000_000;
        check(PlayerBotPopulationLifecycle.expiredPresence(true, expiry, expiry), "lowering target retains actual saved presence expiry");
        check(!PlayerBotPopulationLifecycle.retirementAllowed(true, false, true, false, false), "grouped actor remains protected after expiry");
        check(PlayerBotPopulationLifecycle.retirementAllowed(true, false, false, false, false), "ungrouped actor may leave at expiry");
        long offline = PlayerBotPopulationLifecycle.postExpiryOfflineUntil(false, 0, 0);
        check(offline == 0 && PlayerBotPopulationLifecycle.admissionAllowed(offline, expiry), "pinned default expiry does not require a new random offline break");
        long oldBreak = PlayerBotPopulationLifecycle.postExpiryOfflineUntil(false, expiry + 600_000, 0);
        check(!PlayerBotPopulationLifecycle.admissionAllowed(oldBreak, expiry), "disabling extra cooldown retains a pending native break");
        check(PlayerBotPopulationLifecycle.admissionAllowed(oldBreak, oldBreak), "same saved identity is reusable after its committed break");
        refused(() -> PlayerBotPopulationLifecycle.factoryReady(-1, 0));
        refused(() -> PlayerBotPopulationLifecycle.factoryReady(0, -1));
        refused(() -> PlayerBotPopulationLifecycle.expiredPresence(true, -1, 0));
        refused(() -> PlayerBotPopulationLifecycle.expiredPresence(true, 0, -1));
        refused(() -> PlayerBotPopulationLifecycle.redistributionCeiling(-1, 0));
        refused(() -> PlayerBotPopulationLifecycle.redistributionCeiling(0, -1));
        refused(() -> PlayerBotPopulationLifecycle.postExpiryOfflineUntil(false, -1, 0));
        refused(() -> PlayerBotPopulationLifecycle.postExpiryOfflineUntil(false, 0, -1));
        refused(() -> PlayerBotPopulationLifecycle.admissionAllowed(-1, 0));
        refused(() -> PlayerBotPopulationLifecycle.admissionAllowed(0, -1));
        System.out.println("OK: " + checks + " factory readiness, startup admission, presence expiry, custody and cooldown checks; no World/IDs/SQL");
    }
    private PlayerBotPopulationLifecycleCheck() {}
}
