package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

public class FallDamageConfig {

	/**
	 * Percentage of the player's max HP lost per meter of the fall.
	 */
	@Property(key = "gameserver.falldamage.percentage", defaultValue = "1.0")
	public static float FALL_DAMAGE_PERCENTAGE;

	/**
	 * Fixed damage per meter of the fall, added to the percentage.
	 */
	@Property(key = "gameserver.falldamage.flat", defaultValue = "4")
	public static float FALL_DAMAGE_FLAT;

	/**
	 * Fall height above which you take damage.
	 */
	@Property(key = "gameserver.falldamage.distance.minimum", defaultValue = "10")
	public static float MINIMUM_DISTANCE_DAMAGE;

	/**
	 * Fall height from which you die on hitting the ground.
	 */
	@Property(key = "gameserver.falldamage.distance.maximum", defaultValue = "50")
	public static float MAXIMUM_DISTANCE_DAMAGE;

	/**
	 * Fall height after which you die in mid air when there is no ground within reach.
	 */
	@Property(key = "gameserver.falldamage.distance.midair", defaultValue = "50")
	public static float MAXIMUM_DISTANCE_MIDAIR;

	/**
	 * Same as {@link #MAXIMUM_DISTANCE_MIDAIR}, inside a fly zone.
	 */
	@Property(key = "gameserver.falldamage.distance.midair.flyzone", defaultValue = "100")
	public static float MAXIMUM_DISTANCE_MIDAIR_FLY_ZONE;
}
