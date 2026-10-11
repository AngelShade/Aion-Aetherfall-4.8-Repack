package com.aionemu.gameserver.questEngine.model;

import java.util.Objects;
import java.util.Set;

/** Read-only native story reward groups reachable by this actor; never an item-selection index. */
public record QuestBotRewardChoices(boolean declared, Set<Integer> groups, String reason) {
    public QuestBotRewardChoices {
        groups = Set.copyOf(groups);
        reason = Objects.requireNonNull(reason);
        if (groups.stream().anyMatch(group -> group < 0) || (!declared && !groups.isEmpty()))
            throw new IllegalArgumentException("Invalid native story reward declaration");
    }

    /** Missing review cannot be treated as proof that a native branch is impossible. */
    public static QuestBotRewardChoices unknown() {
        return new QuestBotRewardChoices(false, Set.of(), "Native story reward routes have not been declared");
    }

    public static QuestBotRewardChoices nativeGroups(Set<Integer> groups, String reason) {
        return new QuestBotRewardChoices(true, groups, reason);
    }
}
