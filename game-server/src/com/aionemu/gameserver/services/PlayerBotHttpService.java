package com.aionemu.gameserver.services;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.alibaba.fastjson2.JSON;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import com.sun.net.httpserver.HttpExchange;

/** Dedicated companion controls in the client's authenticated native browser. */
public final class PlayerBotHttpService {
	private static final Path MEDIA = Path.of("config/playerbots/media");
	private static final Map<String, Ticket> TICKETS = new ConcurrentHashMap<>();
	private static final SecureRandom RANDOM = new SecureRandom();
	private record Ticket(Player player, Object connection, long expires) {}
	private PlayerBotHttpService() {}
	public static void handle(HttpExchange exchange) throws IOException {
		try {
			exchange.getResponseHeaders().set("Cache-Control", "no-store");
			exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
			String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
			if (method.equals("GET") && (path.equals("/market/companions") || path.equals("/market/companions/media/bots.js") || path.equals("/market/companions/media/bots.css"))) {
				String file = path.endsWith("bots.js") ? "bots.js" : path.endsWith("bots.css") ? "bots.css" : "bots.html";
				send(exchange, 200, file.endsWith("js") ? "application/javascript" : file.endsWith("css") ? "text/css" : "text/html", Files.readString(MEDIA.resolve(file))); return;
			}
			boolean mutation = path.equals("/market/companions/action");
			if (!mutation && !path.equals("/market/companions/state")) { send(exchange, 404, "text/plain", "Not found"); return; }
			if (!(mutation ? method.equals("POST") : method.equals("GET"))) { send(exchange, 405, "text/plain", "Method not allowed"); return; }
			var values = parse(mutation ? new String(exchange.getRequestBody().readNBytes(8193), StandardCharsets.UTF_8) : exchange.getRequestURI().getRawQuery());
			Player player = MarketplaceService.findPlayer(values.getOrDefault("session_id", ""));
			if (player == null || !player.isOnline() || player.isPlayerBot()) { send(exchange, 403, "application/json", JSON.toJSONString(Map.of("error", "Log in and reopen Companions from Additional Functions."))); return; }
			String notice = "";
			var service = PlayerBotService.getInstance();
			synchronized (service) {
				if (mutation) {
					String origin = exchange.getRequestHeaders().getFirst("Origin"), host = exchange.getRequestHeaders().getFirst("Host");
					if (origin != null && !origin.equals("http://" + host)) throw new IllegalArgumentException("Reopen Companions to continue.");
					Ticket ticket = TICKETS.remove(values.getOrDefault("request", ""));
					if (ticket == null || !validTicket(ticket.player(), player, ticket.connection(), player.getClientConnection(), ticket.expires(), System.currentTimeMillis()))
						throw new IllegalArgumentException("This selection expired or was already submitted. Refresh Companions.");
					if (!player.isOnline() || !player.isSpawned()) throw new IllegalArgumentException("Wait until your character is in the world.");
					notice = action(player, values);
				}
				var state = snapshot(player); state.put("notice", notice);
				long now = System.currentTimeMillis(); TICKETS.entrySet().removeIf(e -> e.getValue().expires() < now || !e.getValue().player().isOnline() || e.getValue().player() == player);
				if (TICKETS.size() >= 4096) throw new IllegalStateException("Companion panel is busy");
				byte[] bytes = new byte[24]; RANDOM.nextBytes(bytes); String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
				TICKETS.put(id, new Ticket(player, player.getClientConnection(), now + 120000)); state.put("request", id);
				send(exchange, 200, "application/json", JSON.toJSONString(state));
			}
		} catch (IllegalArgumentException error) { send(exchange, 400, "application/json", JSON.toJSONString(Map.of("error", error.getMessage()))); }
		catch (Exception error) {
			org.slf4j.LoggerFactory.getLogger(PlayerBotHttpService.class).error("Companion panel request failed", error);
			send(exchange, 503, "application/json", JSON.toJSONString(Map.of("error", "The request could not be completed. Refresh before retrying.")));
		} finally { exchange.close(); }
	}
	public static boolean validTicket(Object expectedPlayer, Object player, Object expectedConnection, Object connection, long expires, long now) {
		return expectedPlayer == player && player != null && expectedConnection == connection && connection != null && expires > now;
	}
	public static Map<String, String> parse(String text) {
		Map<String, String> result = new HashMap<>();
		if (text == null) return result;
		if (text.length() > 8192) throw new IllegalArgumentException("Request too large.");
		for (String part : text.split("&")) {
			String[] pair = part.split("=", 2);
			if (pair.length != 2) continue;
			String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
			if (result.putIfAbsent(key, URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) != null) throw new IllegalArgumentException("Repeated request field.");
		}
		return result;
	}
	private static Map<String, Object> snapshot(Player owner) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("owner", owner.getName()); state.put("enabled", PlayerBotConfig.ENABLED); state.put("limit", Math.min(5, PlayerBotConfig.MAX_PER_OWNER));
		state.put("generatedEnabled", PlayerBotConfig.GENERATED_ENABLED);
		state.put("active", PlayerBotService.getInstance().companions(owner).stream().map(PlayerBotSession::snapshot).toList());
		List<Map<String, Object>> roster = new ArrayList<>();
		for (var data : owner.getAccount().getPlayerAccDataList()) {
			var character = data.getPlayerCommonData();
			if (character.getPlayerObjId() != owner.getObjectId()) roster.add(Map.of("name", character.getName(), "id", character.getPlayerObjId(), "level", character.getLevel(),
				"playerClass", character.getPlayerClass().name(), "reserved", PlayerBotLease.isReserved(character.getPlayerObjId()), "generated", false, "ready", true));
		}
		for (var data : PlayerBotRoster.list(owner.getAccount().getId())) roster.add(Map.of("name", data.name() == null ? "Pending companion " + data.id() : data.name(), "id", data.id(),
			"reserved", PlayerBotLease.isReserved(data.id()), "generated", true, "ready", data.ready()));
		state.put("roster", roster);
		state.put("classes", Arrays.stream(PlayerClass.values()).filter(pc -> pc.isStartingClass() == (owner.getLevel() < 10)).map(Enum::name).toList());
		state.put("startingClasses", Arrays.stream(PlayerClass.values()).filter(PlayerClass::isStartingClass).map(Enum::name).toList());
		state.put("roles", Arrays.stream(Role.values()).map(Enum::name).toList());
		state.put("quests", owner.getQuestStateList().getUncompletedQuests().stream().map(q -> Map.of("id", q.getQuestId(), "status", q.getStatus().name())).toList());
		return state;
	}
	private static String action(Player owner, Map<String, String> values) {
		var service = PlayerBotService.getInstance();
		String op = values.getOrDefault("action", ""), name = values.getOrDefault("name", "");
		switch (op) {
			case "recruit" -> service.recruit(owner, name);
			case "create", "generate" -> {
				PlayerClass pc = PlayerClass.valueOf(values.getOrDefault("playerClass", "").toUpperCase(Locale.ROOT));
				if (op.equals("create")) service.create(owner, name, pc); else service.generate(owner, name, pc);
			}
			case "dismiss" -> { if (name.equals("all")) service.dismissAll(owner); else service.dismiss(owner, name); }
			case "order", "attack", "setting", "share" -> {
				List<PlayerBotSession> sessions = name.equals("all") ? service.companions(owner) : List.of(service.find(owner, name));
				if (sessions.isEmpty()) throw new IllegalArgumentException("Recruit a companion first.");
				for (var session : sessions) switch (op) {
					case "order" -> session.order(Order.valueOf(values.getOrDefault("order", "").toUpperCase(Locale.ROOT)));
					case "attack" -> session.attackSelectedTarget();
					case "share" -> { if (!service.acceptSharedQuest(owner, session.bot(), Integer.parseInt(values.getOrDefault("quest", "0")))) throw new IllegalArgumentException(session.bot().getName() + " cannot accept that shared quest."); }
					case "setting" -> {
						String raw = values.getOrDefault("enabled", ""); if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException("Invalid setting value.");
						boolean enabled = Boolean.parseBoolean(raw);
						switch (values.getOrDefault("setting", "")) {
							case "area" -> session.setAreaSkills(enabled); case "supplies" -> session.setConsumables(enabled); case "gear" -> session.setAutoGear(enabled);
							case "loot" -> session.setAutoLoot(enabled); case "questing" -> session.setQuesting(enabled); default -> throw new IllegalArgumentException("Unknown companion setting.");
						}
					}
				}
			}
			case "role" -> service.find(owner, name).setRole(Role.valueOf(values.getOrDefault("role", "").toUpperCase(Locale.ROOT)));
			case "mission" -> service.find(owner, name).mission(Integer.parseInt(values.getOrDefault("quest", "0")));
			case "equip" -> service.find(owner, name).equip(Integer.parseInt(values.getOrDefault("item", "0")), com.aionemu.gameserver.model.items.ItemSlot.valueOf(values.getOrDefault("slot", "")));
			default -> throw new IllegalArgumentException("Unknown companion action.");
		}
		return "Companion request completed.";
	}
	private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
	}
}
