package com.aionemu.gameserver.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.world.World;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Aggregate presence only; no character details or game actions are exposed. */
public final class WebsiteStatusHttpService {

	private static final Logger log = LoggerFactory.getLogger(WebsiteStatusHttpService.class);
	private record Snapshot(long at, int players, int worldBots, int companions) {}
	private static volatile Snapshot cached;
	private static HttpServer server;
	private static ExecutorService executor;

	private WebsiteStatusHttpService() {}

	public static synchronized void start() {
		if (!GSConfig.ENABLE_WEBSITE_STATUS || server != null) return;
		try {
			if (GSConfig.WEBSITE_STATUS_PORT < 1024 || GSConfig.WEBSITE_STATUS_PORT > 65535)
				throw new IllegalArgumentException("Website status port must be between 1024 and 65535");
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", GSConfig.WEBSITE_STATUS_PORT), 8);
			server.createContext("/website/status", WebsiteStatusHttpService::handle);
			executor = Executors.newFixedThreadPool(2, task -> {
				var thread = new Thread(task, "Website-Status-HTTP");
				thread.setDaemon(true);
				return thread;
			});
			server.setExecutor(executor);
			server.start();
			log.info("Website status listening at http://127.0.0.1:{}/website/status", GSConfig.WEBSITE_STATUS_PORT);
		} catch (IOException | RuntimeException exception) {
			stop();
			log.error("Could not start website status listener", exception);
		}
	}

	public static synchronized void stop() {
		if (server != null) { server.stop(0); server = null; }
		if (executor != null) { executor.shutdown(); executor = null; }
		cached = null;
	}

	private static synchronized Snapshot snapshot() {
		long now = System.currentTimeMillis();
		if (cached != null && now - cached.at() < 5000)
			return cached;
		int players = 0, worldBots = 0, companions = 0;
		for (var player : World.getInstance().getAllPlayers()) {
			if (!player.isSpawned()) continue;
			if (player.isPlayerBot()) {
				if (player.isWorldBot()) worldBots++;
				else companions++;
			} else if (player.isOnline()) players++;
		}
		return cached = new Snapshot(now, players, worldBots, companions);
	}

	public static void handle(HttpExchange exchange) throws IOException {
		try {
			exchange.getResponseHeaders().set("Cache-Control", "no-store");
			exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
				send(exchange, 403, Map.of("error", "Local access only")); return;
			}
			if (!exchange.getRequestURI().getPath().equals("/website/status")) {
				send(exchange, 404, Map.of("error", "Not found")); return;
			}
			if (!exchange.getRequestMethod().equals("GET")) {
				exchange.getResponseHeaders().set("Allow", "GET");
				send(exchange, 405, Map.of("error", "Method not allowed")); return;
			}
			var value = snapshot();
			send(exchange, 200, Map.of("source", "aetherfall-live-world", "online_players", value.players(),
				"online_bots", value.worldBots() + value.companions(), "world_bots", value.worldBots(),
				"companions", value.companions(), "checked_at_epoch", value.at() / 1000));
		} finally { exchange.close(); }
	}

	private static void send(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
		byte[] bytes = Json.write(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
	}
}
