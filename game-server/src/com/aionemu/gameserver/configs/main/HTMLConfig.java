package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/**
 * @author lord_rex
 */
public class HTMLConfig {

	/**
	 * Enable HTML Welcome Message
	 */
	@Property(key = "gameserver.html.welcome.enable", defaultValue = "false")
	public static boolean ENABLE_HTML_WELCOME;

	/**
	 * Enable HTML Guide Message
	 */
	@Property(key = "gameserver.html.guides.enable", defaultValue = "false")
	public static boolean ENABLE_GUIDES;

	/**
	 * Html files directory
	 */
	@Property(key = "gameserver.html.root", defaultValue = "./data/static_data/HTML/")
	public static String HTML_ROOT;

	/**
	 * Html cache directory
	 */
	@Property(key = "gameserver.html.cache.file", defaultValue = "./cache/html.cache")
	public static String HTML_CACHE_FILE;

	/**
	 * Encoding
	 */
	@Property(key = "gameserver.html.encoding", defaultValue = "UTF-8")
	public static String HTML_ENCODING;

	/**
	 * Text size of the custom windows in percent; small text is raised most. 0 serves the stylesheets' original sizes.
	 */
	@Property(key = "gameserver.html.window.text.scale", defaultValue = "100")
	public static int WINDOW_TEXT_SCALE;

	/**
	 * Enables the Codex item and creature drop database and its Central Market tab.
	 */
	@Property(key = "gameserver.html.codex.enabled", defaultValue = "true")
	public static boolean CODEX_ENABLED;
}
