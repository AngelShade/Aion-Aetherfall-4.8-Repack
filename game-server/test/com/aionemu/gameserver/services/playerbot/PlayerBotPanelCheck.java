package com.aionemu.gameserver.services.playerbot;

import java.net.*;
import java.net.http.*;
import com.aionemu.gameserver.services.PlayerBotHttpService;
import com.sun.net.httpserver.HttpServer;

/** Real local HTTP routes and production media. No database, game account or installed server. */
public final class PlayerBotPanelCheck {
	private static int checks;
	private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError("FAIL: " + message); }
	public static void main(String[] args) throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/market/companions", PlayerBotHttpService::handle); server.start();
		try (var client = HttpClient.newHttpClient()) {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			for (String path : new String[] {"", "/media/bots.js", "/media/bots.css"}) {
				var result = client.send(HttpRequest.newBuilder(URI.create(base + "/market/companions" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
				check(result.statusCode() == 200 && !result.body().isBlank(), "production companion media is served: " + path);
				check(result.headers().firstValue("Cache-Control").orElse("").equals("no-store"), "companion responses do not retain character state");
				check(result.headers().firstValue("X-Content-Type-Options").orElse("").equals("nosniff"), "companion media retains explicit content types");
			}
			for (String path : new String[] {"/state", "/action"}) {
				var request = HttpRequest.newBuilder(URI.create(base + "/market/companions" + path));
				if (path.equals("/action")) request.POST(HttpRequest.BodyPublishers.ofString("action=create&name=Unavailable")); else request.GET();
				var result = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
				check(result.statusCode() == 403 && result.body().contains("Log in"), "unauthenticated " + path + " is rejected before game state access");
			}
			var wrongMethod = client.send(HttpRequest.newBuilder(URI.create(base + "/market/companions/action")).GET().build(), HttpResponse.BodyHandlers.ofString());
			check(wrongMethod.statusCode() == 405, "GET cannot mutate companions");
			var duplicate = client.send(HttpRequest.newBuilder(URI.create(base + "/market/companions/action")).POST(HttpRequest.BodyPublishers.ofString("action=create&action=dismiss")).build(), HttpResponse.BodyHandlers.ofString());
			check(duplicate.statusCode() == 400, "ambiguous mutation request fails closed");
			var oversized = client.send(HttpRequest.newBuilder(URI.create(base + "/market/companions/action")).POST(HttpRequest.BodyPublishers.ofString("name=" + "x".repeat(9000))).build(), HttpResponse.BodyHandlers.ofString());
			check(oversized.statusCode() == 400, "oversized body cannot reach recruitment");
			var traversal = client.send(HttpRequest.newBuilder(URI.create(base + "/market/companions/media/../main/playerbots.properties")).GET().build(), HttpResponse.BodyHandlers.ofString());
			check(traversal.statusCode() == 404, "media handler cannot serve arbitrary configuration files");
			System.out.println("OK: " + checks + " production companion HTTP route, media and unauthenticated request checks");
		} finally { server.stop(0); }
	}
}
