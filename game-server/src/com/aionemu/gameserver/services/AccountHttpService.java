package com.aionemu.gameserver.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded-browser Account window (Profile > Account > View Boons, /account). Read-only: the page shows the account's
 * Aetherfall Boons and characters for the online session. Its frame art is shared with the Season Pass.
 */
public final class AccountHttpService {
	private static final Logger log = LoggerFactory.getLogger(AccountHttpService.class);
	private static final Path MEDIA = Path.of("config/account/media"), PASS_MEDIA = Path.of("config/season-pass/media");
	private static final Set<String> PASS_ART = Set.of("aether-frame.png", "aether-crest.png", "ascendant-crest.png", "pass-panel.png");

	private AccountHttpService() {}

	public static void handle(HttpExchange x) throws IOException {
		try {
			x.getResponseHeaders().set("Cache-Control", "no-store");
			x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			x.getResponseHeaders().set("Referrer-Policy", "no-referrer");
			String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
			if (!method.equals("GET")) {
				send(x, 405, "application/json", "{\"error\":\"Method not allowed.\"}");
				return;
			}
			if (path.equals("/market/account") || path.matches("/market/account/media/account\\.(css|js)")) {
				String file = path.equals("/market/account") ? "account.html" : path.substring(path.lastIndexOf('/') + 1);
				send(x, 200, file.endsWith(".html") ? "text/html" : file.endsWith(".css") ? "text/css" : "application/javascript",
					Files.readString(MEDIA.resolve(file)));
				return;
			}
			if (path.matches("/market/account/media/[a-z-]+\\.png")) {
				String file = path.substring(path.lastIndexOf('/') + 1);
				// Season Pass frames are shared; their optimized render copies paint faster in the client browser.
				Path image = PASS_ART.contains(file) && Files.isRegularFile(PASS_MEDIA.resolve("optimized").resolve(file)) ? PASS_MEDIA.resolve("optimized").resolve(file)
					: PASS_ART.contains(file) ? PASS_MEDIA.resolve(file)
					: file.equals("ascendant-dawn.png") ? PASS_MEDIA.resolve(file) : MEDIA.resolve(file);
				if (!Files.isRegularFile(image)) {
					send(x, 404, "text/plain", "Not found");
					return;
				}
				byte[] bytes = Files.readAllBytes(image);
				x.getResponseHeaders().set("Content-Type", "image/png");
				x.sendResponseHeaders(200, bytes.length);
				x.getResponseBody().write(bytes);
				return;
			}
			if (!path.equals("/market/account/state")) {
				send(x, 404, "text/plain", "Not found");
				return;
			}
			Map<String,String> args = SeasonPassHttpService.parse(x.getRequestURI().getRawQuery());
			Player p = MarketplaceService.findPlayer(args.getOrDefault("session_id", ""));
			if (p == null || !p.isOnline()) {
				send(x, 403, "application/json", "{\"error\":\"Log in and reopen Account from your Profile.\"}");
				return;
			}
			send(x, 200, "application/json", Json.write(AetherfallBoonService.view(p)));
		} catch (Exception e) {
			log.error("Account window request failed", e);
			send(x, 503, "application/json", "{\"error\":\"Account is unavailable. Refresh to try again.\"}");
		} finally {
			x.close();
		}
	}

	private static void send(HttpExchange x, int status, String type, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		x.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
		x.sendResponseHeaders(status, bytes.length);
		x.getResponseBody().write(bytes);
	}
}
