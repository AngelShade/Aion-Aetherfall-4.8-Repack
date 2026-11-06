package com.aionemu.gameserver.services;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.commons.utils.Json;
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
			String cardPrefix = "/market/companions/media/cards/";
			if (method.equals("GET") && path.startsWith(cardPrefix) && PlayerBotMedia.cardArt(path.substring(cardPrefix.length()))) {
				byte[] image = PlayerBotMedia.readCardArt(path.substring(cardPrefix.length()));
				exchange.getResponseHeaders().set("Content-Type", path.endsWith(".jpg") ? "image/jpeg" : "image/png");
				exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
				exchange.sendResponseHeaders(200, image.length); exchange.getResponseBody().write(image); return;
			}
			if (method.equals("GET") && (path.equals("/market/companions") || path.equals("/market/companions/media/bots.js") || path.equals("/market/companions/media/bots.css"))) {
				String file = path.endsWith("bots.js") ? "bots.js" : path.endsWith("bots.css") ? "bots.css" : "bots.html";
				send(exchange, 200, file.endsWith("js") ? "application/javascript" : file.endsWith("css") ? "text/css" : "text/html", file.endsWith("css") ? WindowTextScale.css(PlayerBotMedia.read(file)) : PlayerBotMedia.read(file)); return;
			}
			if (method.equals("GET") && path.equals("/market/companions/help")) {
				var helpQuery = parse(exchange.getRequestURI().getRawQuery());
				Player helper = MarketplaceService.findPlayer(helpQuery.getOrDefault("session_id", ""));
				if (helper == null || !helper.isOnline() || helper.isPlayerBot()) { send(exchange, 403, "text/plain", ""); return; }
				String title = PlayerBotHelpObjectives.title(helper).replace('\n',' ').replace('\r',' ');
				send(exchange, 200, "text/plain", "journal".equals(helpQuery.get("format")) ? title + "\n" + PlayerBotHelpObjectives.progress(helper) : title); return;
			}
			if (method.equals("GET") && path.equals("/market/companions/identity")) {
				var identityQuery = parse(exchange.getRequestURI().getRawQuery());
				Player viewer = MarketplaceService.findPlayer(identityQuery.getOrDefault("session_id", ""));
				if (viewer == null || !viewer.isOnline() || viewer.isPlayerBot()) { send(exchange, 403, "text/plain", ""); return; }
				send(exchange, 200, "text/plain", PlayerBotGrowth.identities(identityQuery.getOrDefault("names", ""))); return;
			}
			boolean mutation = path.equals("/market/companions/action");
			if (!mutation && !path.equals("/market/companions/state")) { send(exchange, 404, "text/plain", "Not found"); return; }
			if (!(mutation ? method.equals("POST") : method.equals("GET"))) { send(exchange, 405, "text/plain", "Method not allowed"); return; }
			var values = parse(mutation ? new String(exchange.getRequestBody().readNBytes(8193), StandardCharsets.UTF_8) : exchange.getRequestURI().getRawQuery());
			Player player = MarketplaceService.findPlayer(values.getOrDefault("session_id", ""));
			if (player == null || !player.isOnline() || player.isPlayerBot()) { send(exchange, 403, "application/json", Json.write(Map.of("error", "Log in and reopen Companions from Additional Functions."))); return; }
			String notice = "";
			Map<String,Object> responseDetails = new LinkedHashMap<>();
			var service = PlayerBotService.getInstance();
			synchronized (player) {
				if (mutation) {
					if (!WebUiConnection.sameOrigin(exchange)) throw new IllegalArgumentException("Reopen Companions to continue.");
					Ticket ticket = TICKETS.remove(values.getOrDefault("request", ""));
					if (ticket == null || !validTicket(ticket.player(), player, ticket.connection(), player.getClientConnection(), ticket.expires(), System.currentTimeMillis()))
						throw new IllegalArgumentException("This selection expired or was already submitted. Refresh Companions.");
					if (!player.isOnline() || !player.isSpawned()) throw new IllegalArgumentException("Wait until your character is in the world.");
					if ("recyclequote".equals(values.get("action"))) {
						responseDetails.put("recycleQuote", PlayerBotRecycling.quote(player, values.getOrDefault("name",""), values.getOrDefault("target","")));
						notice = "Review the exact recycling rewards before confirming.";
					} else if ("recycle".equals(values.get("action"))) {
						if (!"yes".equals(values.get("confirm"))) throw new IllegalArgumentException("Review and confirm the recycling quote first.");
						var outcome = PlayerBotRecycling.execute(player, values.getOrDefault("name",""), values.getOrDefault("target",""), values.getOrDefault("quote",""));
						notice = outcome.notice();
						if (outcome.quote() != null) responseDetails.put("recycleQuote", outcome.quote());
					} else notice = action(player, values);
				}
				var state = snapshot(player,values); state.put("notice", notice);
				state.putAll(responseDetails);
				long now = System.currentTimeMillis(); TICKETS.entrySet().removeIf(e -> e.getValue().expires() < now || !e.getValue().player().isOnline() || e.getValue().player() == player);
				if (TICKETS.size() >= 4096) throw new IllegalStateException("Companion panel is busy");
				byte[] bytes = new byte[24]; RANDOM.nextBytes(bytes); String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
				TICKETS.put(id, new Ticket(player, player.getClientConnection(), now + 120000)); state.put("request", id);
				send(exchange, 200, "application/json", Json.write(state));
			}
		} catch (IllegalArgumentException error) { send(exchange, 400, "application/json", Json.write(Map.of("error", error.getMessage()))); }
		catch (Exception error) {
			org.slf4j.LoggerFactory.getLogger(PlayerBotHttpService.class).error("Companion panel request failed", error);
			send(exchange, 503, "application/json", Json.write(Map.of("error", "The request could not be completed. Refresh before retrying.")));
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
	private static Map<String, Object> snapshot(Player owner,Map<String,String> values) throws Exception {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("owner", owner.getName()); state.put("ownerLevel", owner.getLevel()); state.put("levelDifference", PlayerBotConfig.LEVEL_DIFFERENCE); state.put("enabled", PlayerBotConfig.ENABLED); state.put("limit", PlayerBotRaidPolicy.limit());
		state.put("generatedEnabled", PlayerBotConfig.GENERATED_ENABLED);
		state.put("tickets", com.aionemu.gameserver.services.playerbot.PlayerBotRecruitment.snapshot(owner));
		var active=PlayerBotService.getInstance().companions(owner).stream().map(s->{var bot=s.snapshot();bot.put("giveOnly",PlayerBotGrowth.giveOnly(s.bot()));var growth=PlayerBotGrowth.identity(s.bot());if(growth!=null)bot.put("growth",growth);return bot;}).toList();
		state.put("active",active);
		state.put("recycleReceivers",PlayerBotRoster.recycleReceivers(owner.getAccount().getId(),owner.getObjectId(),PlayerBotService.getInstance().companions(owner)));
		state.put("collectionRenown", PlayerBotCollectionRenown.view(owner));
		state.put("recyclingEnabled", PlayerBotRecycling.enabled());
		state.put("helpRequest", PlayerBotHelpObjectives.snapshot(owner));
		state.put("helpPosts", PlayerBotFindGroup.helpRequests(owner));
		String benefitId = values.getOrDefault("benefitBot", "");
		for (var session : PlayerBotService.getInstance().companions(owner))
			if (Integer.toString(session.bot().getObjectId()).equals(benefitId)
			 && (session.bot().isWorldBot() || Boolean.TRUE.equals(session.snapshot().get("generated")))) {
				try { state.put("benefits", Map.of("id", session.bot().getObjectId(), "pass", PlayerBotPass.snapshot(owner, session.bot().getName()))); }
				catch (Exception error) {
					org.slf4j.LoggerFactory.getLogger(PlayerBotHttpService.class).warn("Companion pass view unavailable for {}", session.bot().getObjectId(), error);
					state.put("benefits", Map.of("id", session.bot().getObjectId(), "error", "Season Pass is unavailable. Refresh after the companion finishes its current work."));
				}
				break;
			}
		state.put("recruitRemaining", PlayerBotRaidPolicy.remaining(owner, PlayerBotService.getInstance().companions(owner).size(), PlayerBotService.getInstance().companionCount()));
		state.put("teamKind", owner.isInAlliance() ? "Alliance" : "Party");
		state.put("team", PlayerBotAllianceControl.snapshot(owner));
		List<Map<String, Object>> roster = new ArrayList<>();
		for (var data : owner.getAccount().getPlayerAccDataList()) {
			var character = data.getPlayerCommonData();
			if (character.getPlayerObjId() != owner.getObjectId()) roster.add(Map.of("name", character.getName(), "id", character.getPlayerObjId(), "level", character.getLevel(),
				"playerClass", character.getPlayerClass().name(), "gender", character.getGender().name(), "reserved", PlayerBotLease.isReserved(character.getPlayerObjId()), "generated", false, "ready", true));
		}

        state.put("accountCharacters",List.copyOf(roster));
        var activeIds=PlayerBotService.getInstance().companions(owner).stream().map(session->session.bot().getObjectId()).collect(java.util.stream.Collectors.toSet());
        var presets=PlayerBotPresets.snapshot(owner);
        var savedIds=new HashSet<Integer>();for(var id:(Collection<?>)presets.get("savedBots"))savedIds.add(Integer.parseInt(id.toString()));
        var page=PlayerBotRoster.page(owner.getAccount().getId(),owner.getObjectId(),values.getOrDefault("search",""),values.getOrDefault("filter","all"),values.getOrDefault("sort","name"),Integer.parseInt(values.getOrDefault("page","0")),activeIds,savedIds);
        roster=new ArrayList<>(page.rows());
        for(var row:roster)for(var live:active)if(row.get("id").equals(live.get("id"))) {
            row.put("level",live.get("level"));row.put("playerClass",live.get("playerClass"));
            if(live.containsKey("growth"))row.put("growth",live.get("growth"));
        }
        for(var row:roster) {
            boolean ticket=row.get("growth") instanceof Map<?,?> growth && "GACHA".equals(growth.get("origin"));
            row.put("recruitmentEnabled",PlayerBotCreationPolicy.recruitmentAllowed(Boolean.TRUE.equals(row.get("generated")),ticket));
        }
        state.put("generatedCount",page.generatedCount());state.put("generatedLimit",PlayerBotCreationPolicy.limit());
        state.put("freeGeneratedCount",PlayerBotRoster.freeGeneratedCount(owner.getAccount().getId()));
        state.put("rosterTotal",page.total());state.put("rosterPage",page.page());

		state.put("roster", roster);
		// Companions window item transfer: the owner's tradeable bag and each companion's movable stacks.
		state.put("ownerBag", PlayerBotItemTransfer.ownerBag(owner));
		state.put("ownerKinah", owner.getInventory().getKinah());
		var movable = new HashMap<String, Object>();
		var companionMovable = new HashMap<String, Object>();
		for (var session : PlayerBotService.getInstance().companions(owner))
		{
			movable.put(session.bot().getName(), session.bot().getInventory().getItems().stream().filter(PlayerBotItemTransfer::transferable).map(i -> i.getObjectId()).toList());
			if (PlayerBotItemTransfer.collectionCompanion(owner, session))
				companionMovable.put(session.bot().getName(), session.bot().getInventory().getItems().stream().filter(i -> PlayerBotItemTransfer.companionTransferable(session, i)).map(i -> i.getObjectId()).toList());
		}
		state.put("movable", movable);
		state.put("companionMovable", companionMovable);
		state.putAll(presets);
		state.put("classes", Arrays.stream(PlayerClass.values()).filter(pc -> pc.isStartingClass() == (owner.getLevel() < 10)).map(Enum::name).toList());
		state.put("startingClasses", Arrays.stream(PlayerClass.values()).filter(PlayerClass::isStartingClass).map(Enum::name).toList());
		state.put("creationJourney", PoetaJourneyService.creationOptions(owner.getRace()));
		state.put("generatedRandomLooks", PlayerBotConfig.GENERATED_RANDOM_LOOKS);
		state.put("roles", Arrays.stream(Role.values()).map(Enum::name).toList());
		state.put("quests", owner.getQuestStateList().getUncompletedQuests().stream().map(q -> Map.of("id", q.getQuestId(), "name", PlayerBotQuestJournal.name(q.getQuestId()), "status", q.getStatus().name())).toList());
		return state;
	}
	private static String action(Player owner, Map<String, String> values) throws Exception {
		var service = PlayerBotService.getInstance();
		String op = values.getOrDefault("action", ""), name = values.getOrDefault("name", "");
		switch (op) {
			case "helpaccept" -> { return PlayerBotFindGroup.acceptHelp(owner,Integer.parseInt(values.getOrDefault("bot","0"))); }
			case "helptrack", "helpabandon", "helpclaim" -> { return PlayerBotHelpObjectives.action(owner,op.substring(4)); }
			case "team" -> {return PlayerBotAllianceControl.action(owner,values);}
			case "savebot" -> { return PlayerBotPresets.saveBot(owner,name); }
			case "saveparty" -> { return PlayerBotPresets.saveParty(owner,values.getOrDefault("presetName","")); }
			case "loadparty" -> { return PlayerBotPresets.activate(owner,values.getOrDefault("preset","")); }
			case "transfer" -> { return PlayerBotItemTransfer.move(owner, name, "give".equals(values.get("direction")), Integer.parseInt(values.getOrDefault("item", "0")), Long.parseLong(values.getOrDefault("kinah", "0"))); }
			case "companiontransfer" -> { return PlayerBotItemTransfer.moveBetween(owner, name, values.getOrDefault("target", ""), Integer.parseInt(values.getOrDefault("item", "0"))); }
			case "collectionstipend" -> { return PlayerBotCollectionRenown.claimStipend(owner, name); }
			case "focus" -> { return PlayerBotFocus.select(owner, name, values.getOrDefault("focus", ""), Integer.parseInt(values.getOrDefault("profession", "0"))); }
			case "passaction" -> { var args = new HashMap<>(values); args.put("action", values.getOrDefault("passAction", "")); return PlayerBotPass.action(owner, name, args, values.get("request")); }
			case "sendalt" -> { return PlayerBotItemTransfer.sendToAlt(owner, values.getOrDefault("alt", ""), Integer.parseInt(values.getOrDefault("item", "0")), Long.parseLong(values.getOrDefault("kinah", "0"))); }
			case "editparty" -> { return PlayerBotPresets.editParty(owner, values.getOrDefault("preset", ""), values.getOrDefault("presetName", ""), values.getOrDefault("members", "")); }
			case "deleteparty" -> { String preset=values.getOrDefault("preset",""); if(!preset.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Choose a party preset."); return PlayerBotPresets.remove(owner,preset); }
			case "removebot" -> { if(!"yes".equals(values.get("confirm")))throw new IllegalArgumentException("Confirm removal of this Temporary Bot first."); return PlayerBotPresets.remove(owner,"bot:"+values.getOrDefault("id","0")); }
			case "renamebot" -> { return PlayerBotNames.renameCompanion(owner.getAccount().getId(), name, values.getOrDefault("newName", "")); }
			case "recruit" -> service.recruit(owner, name);
			case "recruitticket" -> com.aionemu.gameserver.services.playerbot.PlayerBotRecruitment.recruit(owner, name, values.getOrDefault("recruitRequest", ""), values.getOrDefault("start", "matched"));
			case "ascend" -> PlayerBotOwnedProgression.ascend(service.find(owner,name),values.getOrDefault("playerClass",""));
			case "leveltogether" -> PlayerBotOwnedProgression.levelTogether(service.find(owner,name),"true".equals(values.get("enabled")));
			case "create", "generate" -> {
				PlayerClass pc = PlayerClass.valueOf(values.getOrDefault("playerClass", "").toUpperCase(Locale.ROOT));
				if (op.equals("create")) service.create(owner, name, pc, values.getOrDefault("start", "level1")); else PlayerBotGenerationOptions.generate(owner, name, pc, values.getOrDefault("start", "matched"));
			}
			case "dismiss" -> { for(var s:PlayerBotAllianceControl.scoped(owner,name,values))service.dismiss(owner,s.bot().getName()); }
			case "summon" -> PlayerBotTravel.summonAll(values.containsKey("group")?PlayerBotAllianceControl.scoped(owner,name,values):PlayerBotRecovery.selected(owner,name));
			case "answer" -> {
				String answer = values.getOrDefault("answer", "");
				if (!answer.equals("yes") && !answer.equals("no")) throw new IllegalArgumentException("Answer Yes or No.");
				PlayerBotQuestSync.answer(service.find(owner, name), Integer.parseInt(values.getOrDefault("quest", "0")), answer.equals("yes"));
			}
			case "carebudget" -> PlayerBotQuestSync.setBudget(service.find(owner, name), Long.parseLong(values.getOrDefault("reserve", "")), Long.parseLong(values.getOrDefault("dailyBudget", "")));
			case "spacing" -> PlayerBotSpacing.configure(service.find(owner,name),Float.parseFloat(values.getOrDefault("ownerSpacing","")),Float.parseFloat(values.getOrDefault("attackSpacing","")));
			case "gearpolicy" -> PlayerBotGearPolicy.configure(service.find(owner, name), values.getOrDefault("mode", ""), values.getOrDefault("profile", ""),
				values.getOrDefault("quality", ""), values.getOrDefault("level", ""), values.getOrDefault("threshold", ""), values.getOrDefault("weapon", ""), values.getOrDefault("vendors", ""), values.getOrDefault("rolls", ""));
			case "order", "attack", "setting", "share" -> {
				List<PlayerBotSession> sessions = PlayerBotAllianceControl.scoped(owner, name, values);
				if (sessions.isEmpty()) throw new IllegalArgumentException("Recruit a companion first.");
				for (var session : sessions) switch (op) {
					case "order" -> session.command(Order.valueOf(values.getOrDefault("order", "").toUpperCase(Locale.ROOT)));
					case "attack" -> session.attackSelectedTarget();
					case "share" -> { if (!service.acceptSharedQuest(owner, session.bot(), Integer.parseInt(values.getOrDefault("quest", "0")))) throw new IllegalArgumentException(session.bot().getName() + " cannot accept that shared quest."); }
					case "setting" -> {
						String raw = values.getOrDefault("enabled", ""); if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException("Invalid setting value.");
						boolean enabled = Boolean.parseBoolean(raw);
						switch (values.getOrDefault("setting", "")) {
							case "area" -> session.setAreaSkills(enabled); case "supplies" -> session.setConsumables(enabled); case "gear" -> session.setAutoGear(enabled);
							case "loot" -> session.setAutoLoot(enabled); case "questing" -> session.setQuesting(enabled);
							case "questCombat" -> PlayerBotPartyBehavior.configure(session,enabled);
							case "enchant", "salvage", "partySync" -> PlayerBotQuestSync.setCare(session, values.get("setting"), enabled);
							default -> throw new IllegalArgumentException("Unknown companion setting.");
						}
					}
				}
			}
			case "pull" -> { return PlayerBotMarks.pull(owner, service.companions(owner)); }
			case "mark" -> { return PlayerBotMarks.mark(owner, service.companions(owner), values.getOrDefault("mark", "").toLowerCase(Locale.ROOT)); }
			case "stigmabuild" -> { return PlayerBotGearPolicy.configureBuild(service.find(owner, name), values.getOrDefault("build", "")); }
			case "loadoutsave" -> { return PlayerBotGearPolicy.saveLoadout(service.find(owner, name)); }
			case "loadoutclear" -> { return PlayerBotGearPolicy.clearLoadout(service.find(owner, name)); }
			case "role" -> service.find(owner, name).setRole(Role.valueOf(values.getOrDefault("role", "").toUpperCase(Locale.ROOT)));
			case "mission" -> service.find(owner, name).mission(Integer.parseInt(values.getOrDefault("quest", "0")));
			case "equip" -> service.find(owner, name).equip(Integer.parseInt(values.getOrDefault("item", "0")), com.aionemu.gameserver.model.items.ItemSlot.valueOf(values.getOrDefault("slot", "")));
			case "legiondeposit" -> {return com.aionemu.gameserver.services.playerbot.PlayerBotLegionStorage.deposit(owner,name,Integer.parseInt(values.getOrDefault("item","0")));}
            case "bankdeposit", "bankwithdraw" -> { return PlayerBotWarehouse.move(owner,name,op.equals("bankdeposit"),Integer.parseInt(values.getOrDefault("item","0"))); }
			case "maillist", "mailread", "mailtake", "maildelete", "mailsenditem", "mailsendkinah" -> {return PlayerBotMail.request(owner,name,op.substring(4),Integer.parseInt(values.getOrDefault("object","0")),Long.parseLong(values.getOrDefault("kinah","0")));}
			case "appearance", "resetappearance" -> { return PlayerBotAppearance.configure(service.find(owner,name),Integer.parseInt(values.getOrDefault("item","0")),Integer.parseInt(values.getOrDefault("target","0")),op.equals("resetappearance")); }
			default -> throw new IllegalArgumentException("Unknown companion action.");
		}
		return op.equals("order") && "FOLLOW".equalsIgnoreCase(values.get("order"))
			? "Companions will withdraw and follow you. Normal activity resumes after regrouping out of combat; Attack resumes combat."
			: "Companion request completed.";
	}
	private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
	}
}
