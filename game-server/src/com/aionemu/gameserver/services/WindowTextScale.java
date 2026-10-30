package com.aionemu.gameserver.services;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aionemu.gameserver.configs.main.HTMLConfig;

/**
 * Shared text size for the custom web windows (Companions, Account, Season Pass, Wardrobe, markets, journey).
 * Rewrites pixel font sizes in their stylesheets as they are served: small text gets the biggest bump, so 9-11px
 * labels become readable while headings keep their size, then the whole set follows gameserver.html.window.text.scale.
 */
public final class WindowTextScale {

	/** font-size:Npx, or the size token of a font: shorthand (the first px value, before any /line-height). */
	private static final Pattern SIZE = Pattern.compile("(font-size\\s*:\\s*|font\\s*:[^;}]*?)(\\d+(?:\\.\\d+)?)px");
	/** Text below READABLE px is raised; BOOST is the share of the gap to it that closes. */
	private static final double READABLE = 16, BOOST = 0.45;

	private WindowTextScale() {}

	public static String css(String css) {
		int scale = HTMLConfig.WINDOW_TEXT_SCALE;
		if (scale <= 0)
			return css;
		Matcher m = SIZE.matcher(css);
		StringBuilder out = new StringBuilder(css.length() + 64);
		while (m.find())
			m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + size(Double.parseDouble(m.group(2)), scale) + "px"));
		m.appendTail(out);
		return out.toString();
	}

	public static byte[] css(byte[] css) {
		return css(new String(css, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
	}

	static long size(double px, int scale) {
		if (px <= 0)
			return 0; // font-size:0 hides whitespace between inline blocks
		double readable = px < READABLE ? px + (READABLE - px) * BOOST : px;
		return Math.round(readable * Math.min(Math.max(scale, 50), 200) / 100.0);
	}
}
