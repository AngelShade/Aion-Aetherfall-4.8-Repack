package com.aionemu.gameserver.services;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.configs.main.HTMLConfig;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.sun.net.httpserver.HttpExchange;

/** Server-owned connection metadata delivered through the authenticated game session. */
public final class WebUiConnection {

	private WebUiConnection() {}

	public record Description(String origin, String message, int textScale, boolean localJourney) {}
	public static Description describe(AionConnection connection) {
		try {
			InetAddress local = ((InetSocketAddress) connection.getSocketChannel().getLocalAddress()).getAddress();
			InetAddress remote = ((InetSocketAddress) connection.getSocketChannel().getRemoteAddress()).getAddress();
			return describe(local, remote);
		} catch (Exception e) { return unavailable("The game server could not provide its window connection. Reopen this window after reconnecting."); }
	}
	static Description describe(InetAddress local, InetAddress remote) {
		if (!GSConfig.ENABLE_MARKETPLACE) return unavailable("In-game windows are disabled by this server.");
		try {
			InetAddress bind = InetAddress.getByName(GSConfig.MARKETPLACE_BIND);
			boolean samePc = remote.isLoopbackAddress() || remote.equals(local);
			String origin = GSConfig.WEB_PUBLIC_URL;
			if (origin == null || origin.isBlank()) {
				if (bind.isLoopbackAddress() && !samePc)
					return unavailable("This server currently permits in-game windows only on its own PC. The server administrator needs to enable its LAN window service.");
				if (!samePc && !remote.isSiteLocalAddress() && !remote.isLinkLocalAddress())
					return unavailable("This server has not configured its public in-game window address. Please contact the server administrator.");
				InetAddress host = bind.isAnyLocalAddress() ? local : bind;
				String address = host.getHostAddress();
				origin = "http://" + (address.contains(":") ? "[" + address + "]" : address) + ":" + GSConfig.MARKETPLACE_PORT;
			}
			URI uri = URI.create(origin.trim());
			if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")) || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))
				|| origin.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 512)
				return unavailable("The server's in-game window address is invalid. Please contact the server administrator.");
			return new Description(uri.getScheme() + "://" + uri.getRawAuthority(), "", HTMLConfig.WINDOW_TEXT_SCALE, samePc && bind.isLoopbackAddress());
		} catch (Exception e) { return unavailable("The server's in-game window connection is unavailable. Please contact the server administrator."); }
	}
	private static Description unavailable(String message) { return new Description("", message, HTMLConfig.WINDOW_TEXT_SCALE, false); }

	public static boolean sameOrigin(HttpExchange exchange) {
		return matchesOriginHost(exchange.getRequestHeaders().getFirst("Origin"), exchange.getRequestHeaders().getFirst("Host"));
	}

	public static boolean matchesOriginHost(String origin, String host) {
		if (origin == null) return true; // Native WinHTTP and the supported browser may omit it.
		try {
			URI uri = URI.create(origin);
			return host != null && uri.getUserInfo() == null && "".equals(uri.getRawPath()) && uri.getQuery() == null && uri.getFragment() == null
				&& ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
				&& uri.getRawAuthority() != null && uri.getRawAuthority().equalsIgnoreCase(host);
		} catch (IllegalArgumentException e) { return false; }
	}

}
