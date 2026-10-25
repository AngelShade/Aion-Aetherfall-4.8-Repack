package com.aionemu.gameserver.configs.main;

import java.util.Set;

import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.model.templates.npc.NpcRating;

/** Optional open-world difficulty; instanced maps are always excluded. */
public final class MobScalingConfig {

	@Property(key = "gameserver.mob.scaling.enable", defaultValue = "false")
	public static boolean ENABLED;
	@Property(key = "gameserver.mob.scaling.hp_multiplier", defaultValue = "1")
	public static float HP_MULTIPLIER;
	@Property(key = "gameserver.mob.scaling.damage_multiplier", defaultValue = "1")
	public static float DAMAGE_MULTIPLIER;
	@Property(key = "gameserver.mob.scaling.ratings", defaultValue = "JUNK,NORMAL,ELITE,HERO,LEGENDARY")
	public static Set<NpcRating> RATINGS;
	@Property(key = "gameserver.mob.scaling.excluded_maps", defaultValue = "")
	public static Set<Integer> EXCLUDED_MAPS;
	@Property(key = "gameserver.mob.scaling.excluded_npcs", defaultValue = "")
	public static Set<Integer> EXCLUDED_NPCS;
	@Property(key = "gameserver.mob.scaling.group.enable", defaultValue = "true")
	public static boolean GROUP_ENABLED;
	@Property(key = "gameserver.mob.scaling.group.member_radius", defaultValue = "100")
	public static float MEMBER_RADIUS;
	@Property(key = "gameserver.mob.scaling.group.update_interval", defaultValue = "1000")
	public static long GROUP_UPDATE_INTERVAL;
	@Property(key = "gameserver.mob.scaling.group.hp_per_member", defaultValue = "0.5")
	public static float HP_PER_MEMBER;
	@Property(key = "gameserver.mob.scaling.group.damage_per_member", defaultValue = "0.05")
	public static float DAMAGE_PER_MEMBER;
	@Property(key = "gameserver.mob.scaling.group.max_members", defaultValue = "24")
	public static int MAX_MEMBERS;
	@Property(key = "gameserver.mob.scaling.group.max_hp_multiplier", defaultValue = "30")
	public static float MAX_HP_MULTIPLIER;
	@Property(key = "gameserver.mob.scaling.group.max_damage_multiplier", defaultValue = "4")
	public static float MAX_DAMAGE_MULTIPLIER;

	public static void validate() {
		positive("hp_multiplier", HP_MULTIPLIER);
		positive("damage_multiplier", DAMAGE_MULTIPLIER);
		positive("group.member_radius", MEMBER_RADIUS);
		if (MEMBER_RADIUS > 512 || GROUP_UPDATE_INTERVAL < 1000 || GROUP_UPDATE_INTERVAL > 60000)
			throw new IllegalArgumentException("Mob scaling radius cannot exceed 512m; update interval must be 1000-60000ms");
		positive("group.max_hp_multiplier", MAX_HP_MULTIPLIER);
		positive("group.max_damage_multiplier", MAX_DAMAGE_MULTIPLIER);
		if (!Float.isFinite(HP_PER_MEMBER) || HP_PER_MEMBER < 0 || !Float.isFinite(DAMAGE_PER_MEMBER) || DAMAGE_PER_MEMBER < 0)
			throw new IllegalArgumentException("Mob scaling per-member increases must be finite and non-negative");
		if (MAX_MEMBERS < 1 || MAX_MEMBERS > 24)
			throw new IllegalArgumentException("Mob scaling group.max_members must be between 1 and the native alliance limit of 24");
		if (MAX_HP_MULTIPLIER < HP_MULTIPLIER || MAX_DAMAGE_MULTIPLIER < DAMAGE_MULTIPLIER || MAX_DAMAGE_MULTIPLIER > 6)
			throw new IllegalArgumentException("Mob scaling caps must cover the solo baseline; damage cannot exceed the native PvE ratio ceiling of 6x");
	}

	private static void positive(String name, float value) {
		if (!Float.isFinite(value) || value <= 0)
			throw new IllegalArgumentException("gameserver.mob.scaling." + name + " must be finite and positive");
	}

	private MobScalingConfig() {
	}
}
