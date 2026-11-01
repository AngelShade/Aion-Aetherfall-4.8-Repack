package com.aionemu.gameserver.services.playerbot;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.aionemu.commons.utils.WeightedRandom;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.CombatRank;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.GearRarity;

/** Recruitment distribution, separate from payment and native character custody.
 * A caller must persist the outcome with its recruitment transaction before revealing it.
 * This policy never grants a character, currency, equipment or a second roll on reload.
 */
public final class PlayerBotRecruitmentPolicy {
    private static final long[] WEIGHTS = {4354, 2854, 1854, 554, 384};
    private static final List<PlayerClass> ADVANCED = Arrays.stream(PlayerClass.values())
        .filter(pc -> !pc.isStartingClass()).toList();
    private static final WeightedRandom<PlayerClass> CLASSES = WeightedRandom.of(ADVANCED.stream()
        .map(pc -> new WeightedRandom.Entry<>(pc, 1L)).toList());
    private static final WeightedRandom<CombatRank> RANKS = WeightedRandom.of(Arrays.stream(CombatRank.values())
        .map(rank -> new WeightedRandom.Entry<>(rank, WEIGHTS[rank.ordinal()])).toList());
    private static final WeightedRandom<GearRarity> GEAR = WeightedRandom.of(Arrays.stream(GearRarity.values())
        .map(rarity -> new WeightedRandom.Entry<>(rarity, WEIGHTS[rarity.ordinal()])).toList());

    static {
        if (ADVANCED.size() != 11 || CombatRank.values().length != 5 || GearRarity.values().length != 5)
            throw new IllegalStateException("Recruitment policy needs the eleven native advanced classes and five trait tiers");
    }

    public record Roll(PlayerClass advancedClass, GearRarity autoEquipRarity, CombatRank combatRank) {
        public Roll {
            if (advancedClass == null || advancedClass.isStartingClass() || autoEquipRarity == null || combatRank == null)
                throw new IllegalArgumentException("Incomplete recruitment outcome");
        }
    }

    /** Each attribute uses its own draw; starting families never bias advanced-class odds. */
    public static Roll roll() { return new Roll(CLASSES.sample(), GEAR.sample(), RANKS.sample()); }
    /** A world bot's traits, Poor to Excellent from configuration. Class is the population's choice, not a draw. */
    public static Roll worldRoll(PlayerClass advancedClass) {
        return new Roll(advancedClass, table(GearRarity.values()).sample(), table(CombatRank.values()).sample());
    }
    private static <T> WeightedRandom<T> table(T[] tiers) {
        long[] weights = Arrays.stream(PlayerBotConfig.WORLD_TRAIT_WEIGHTS.split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
        if (weights.length != tiers.length) throw new IllegalStateException("World trait weights need five tiers");
        var entries = new java.util.ArrayList<WeightedRandom.Entry<T>>();
        for (int i = 0; i < tiers.length; i++) entries.add(new WeightedRandom.Entry<>(tiers[i], weights[i]));
        return WeightedRandom.of(entries);
    }
    /** Percent chance of each tier. The rank and gear draws use the same table, separately. */
    public static Map<String, Double> odds() {
        var odds = new java.util.LinkedHashMap<String, Double>();
        for (var rank : CombatRank.values()) odds.put(rank.name(), WEIGHTS[rank.ordinal()] / 100d);
        return odds;
    }
    public static WeightedRandom<PlayerClass> classes() { return CLASSES; }
    public static WeightedRandom<CombatRank> combatRanks() { return RANKS; }
    public static WeightedRandom<GearRarity> gearRarities() { return GEAR; }

    private PlayerBotRecruitmentPolicy() {}
}
