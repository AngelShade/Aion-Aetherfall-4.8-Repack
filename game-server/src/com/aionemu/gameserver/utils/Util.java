package com.aionemu.gameserver.utils;

import java.util.Locale;

import com.aionemu.gameserver.configs.main.NameConfig;

/**
 * @author -Nemesiss-
 */
public class Util {

	/**
	 * Converts name to valid pattern For example : "atracer" -> "Atracer"
	 * 
	 * @param name
	 * @return String
	 */
	public static String convertName(String name) {
		if (!name.isEmpty()) {
			if (NameConfig.ALLOW_CUSTOM_NAMES)
				return name;
			else
				return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.toLowerCase(Locale.ROOT).substring(1);
		} else
			return "";
	}
}
