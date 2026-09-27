package com.aionemu.gameserver.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** A small private storefront for the NA client's in-game browser. */
public final class MarketplaceService {

	private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);
	private static final Path CATALOG = Path.of("config/ingameshop/marketplace.tsv");
	private static final Path EXTRA_CATALOG = Path.of("config/ingameshop/marketplace_extra.tsv");
	private static final int PAGE_SIZE = 12;
	private static final SecureRandom random = new SecureRandom();
	private static final Map<String, Long> purchaseForms = new ConcurrentHashMap<>();
	private static volatile List<Offer> offers = List.of();
	private static HttpServer server;

	private MarketplaceService() {
	}

	public static void start() {
		if (!GSConfig.ENABLE_MARKETPLACE)
			return;
		try {
			offers = loadOffers();
			server = HttpServer.create(new InetSocketAddress(GSConfig.MARKETPLACE_BIND, GSConfig.MARKETPLACE_PORT), 16);
			server.createContext("/shop", MarketplaceService::handle);
			server.setExecutor(ThreadPoolManager.getInstance());
			server.start();
			log.info("Private marketplace listening at http://{}:{}/shop ({} offers)", GSConfig.MARKETPLACE_BIND,
				GSConfig.MARKETPLACE_PORT, offers.size());
		} catch (Exception e) {
			log.error("Could not start private marketplace", e);
		}
	}

	public static void stop() {
		if (server != null)
			server.stop(0);
	}

	private static List<Offer> loadOffers() throws IOException {
		List<Offer> result = new ArrayList<>();
		for (Path catalog : List.of(CATALOG, EXTRA_CATALOG)) {
		for (String line : Files.readAllLines(catalog, StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#"))
				continue;
			String[] parts = line.split("\t", 6);
			if (parts.length != 6)
				throw new IOException("Invalid marketplace catalog row: " + line);
			int itemId = Integer.parseInt(parts[0]);
			long count = Long.parseLong(parts[1]);
			long price = Long.parseLong(parts[2]);
			Section section = Section.find(parts[3]);
			int unlockLevel = Integer.parseInt(parts[4]);
			ItemTemplate item = DataManager.ITEM_DATA.getItemTemplate(itemId);
			if (item == null || item.getName().isBlank() || count <= 0 || count > item.getMaxStackCount() || price <= 0
				|| section == null || section == Section.ALL || unlockLevel < 1 || unlockLevel > GSConfig.PLAYER_MAX_LEVEL
				|| parts[5].isBlank())
				throw new IOException("Invalid marketplace item, quantity, or price: " + line);
			Gender gender = item.getUseLimits() == null ? null : item.getUseLimits().getGenderPermitted();
			result.add(new Offer(itemId, count, price, section, unlockLevel, item.getName(), parts[5], item.getRace(), gender));
		}
		}
		return List.copyOf(result);
	}

	private static void handle(HttpExchange exchange) throws IOException {
		try {
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.getResponseHeaders().set("Cache-Control", "no-store");
			exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
			exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			String method = exchange.getRequestMethod();
			if (!method.equals("GET") && !method.equals("POST")) {
				reply(exchange, 405, page("Method not allowed"));
				return;
			}
			Map<String, String> args = parseForm(method.equals("POST")
				? new String(exchange.getRequestBody().readNBytes(4097), StandardCharsets.UTF_8)
				: exchange.getRequestURI().getRawQuery());
			if (args == null) {
				reply(exchange, 400, page("Invalid request"));
				return;
			}
			String sessionId = args.getOrDefault("session_id", args.getOrDefault("token", ""));
			Player player = findPlayer(sessionId);
			// The NA browser sometimes opens the configured URL without a session query.
			// On a local, single-player server the loopback connection identifies that player.
			if (player == null && sessionId.isBlank() && exchange.getRemoteAddress().getAddress().isLoopbackAddress()
				&& GSConfig.MARKETPLACE_BIND.equals("127.0.0.1") && World.getInstance().getAllPlayers().size() == 1)
				player = World.getInstance().getAllPlayers().iterator().next();
			if (player == null) {
				reply(exchange, 403, page("Open the Marketplace while logged in to your character."));
				return;
			}
			Section section = Section.selected(args.get("category"), player.getLevel());
			String query = args.getOrDefault("q", "").trim();
			if (query.length() > 60)
				query = query.substring(0, 60);
			int pageNumber;
			try {
				pageNumber = Math.max(1, Integer.parseInt(args.getOrDefault("page", "1")));
			} catch (NumberFormatException e) {
				pageNumber = 1;
			}
			String notice = "";
			if (method.equals("POST")) {
				String form = args.getOrDefault("form", "");
				Long expires = purchaseForms.remove(form);
				if (expires == null || expires < System.currentTimeMillis()) {
					reply(exchange, 403, page("This purchase form expired. Reopen the Marketplace and try again."));
					return;
				}
				int index;
				try {
					index = Integer.parseInt(args.getOrDefault("offer", "-1"));
				} catch (NumberFormatException e) {
					index = -1;
				}
				if (index < 0 || index >= offers.size()) {
					reply(exchange, 400, page("Unknown item."));
					return;
				}
				Offer offer = offers.get(index);
				Player buyer = player;
				CompletableFuture<String> result = new CompletableFuture<>();
				ThreadPoolManager.getInstance().execute(() -> {
					try {
						result.complete(purchase(buyer, offer));
					} catch (Exception e) {
						result.completeExceptionally(e);
					}
				});
				try {
					notice = result.get(10, TimeUnit.SECONDS);
				} catch (Exception e) {
					log.error("Marketplace purchase timed out for " + player, e);
					notice = "The purchase could not be confirmed. Check your Kinah and Black Cloud mail before retrying.";
				}
			}
			reply(exchange, 200, storefront(player, sessionId, section, query, pageNumber, notice));
		} finally {
			exchange.close();
		}
	}

	private static String purchase(Player player, Offer offer) {
		synchronized (player.getInventory()) {
			if (World.getInstance().getPlayer(player.getObjectId()) != player)
				return "You are no longer logged in.";
			if (player.getLevel() < offer.unlockLevel())
				return "This item unlocks at level " + offer.unlockLevel() + ".";
			if (offer.race() != Race.PC_ALL && offer.race() != player.getRace())
				return "This item is for the other faction.";
			if (offer.gender() != null && offer.gender() != player.getGender())
				return "This item requires a different character gender.";
			if (!player.getInventory().tryDecreaseKinah(offer.price()))
				return "You do not have enough Kinah.";
			boolean delivered = SystemMailService.sendMail("$$CASH_ITEM_MAIL", player.getName(),
				offer.itemId() + ", " + offer.count(), "0, " + (System.currentTimeMillis() / 1000) + ",",
				offer.itemId(), offer.count(), 0, LetterType.BLACKCLOUD);
			if (!delivered) {
				player.getInventory().increaseKinah(offer.price());
				return "Delivery failed. Your Kinah was returned.";
			}
			log.info("Marketplace: {} bought item {} x{} for {} Kinah", player, offer.itemId(), offer.count(), offer.price());
			return "Purchased " + offer.name() + ". Check your Black Cloud mail.";
		}
	}

	private static Player findPlayer(String sessionId) {
		if (sessionId.isBlank())
			return null;
		for (Player player : World.getInstance().getAllPlayers()) {
			String token = player.getAccount().getSecurityToken();
			if (token.isEmpty())
				continue;
			if (same(sessionId, token) || same(sessionId, hex(token.substring(0, Math.min(16, token.length())))))
				return player;
		}
		return null;
	}

	private static boolean same(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
	}

	private static String hex(String value) {
		return java.util.HexFormat.of().formatHex(value.getBytes(StandardCharsets.US_ASCII));
	}

	private static Map<String, String> parseForm(String text) {
		if (text == null)
			return Map.of();
		if (text.length() > 4096)
			return null;
		Map<String, String> result = new HashMap<>();
		for (String pair : text.split("&")) {
			String[] parts = pair.split("=", 2);
			if (parts.length == 2)
				result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
		}
		return result;
	}

	private static String storefront(Player player, String sessionId, Section section, String query, int pageNumber, String notice) {
		purchaseForms.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis());
		long balance = player.getInventory().getKinah();
		String search = query.toLowerCase(Locale.ROOT);
		StringBuilder body = new StringBuilder("<div class='shell'><div class='masthead'><div class='brand'>")
			.append("<div class='eyebrow'>PRIVATE REALM / LIVE SHOP</div><h1>Black Cloud<br><span>Marketplace</span></h1>")
			.append("<p>Supplies for the first quest and every challenge after level 65.</p></div>")
			.append("<div class='account'><div class='account-label'>SHOPPING AS</div><div class='character'>")
			.append(escape(player.getName())).append(" <span>Lv ").append(player.getLevel())
			.append("</span></div><div class='account-label'>YOUR BALANCE</div><div class='balance'>")
			.append(String.format("%,d", balance)).append(" <span>Kinah</span></div></div></div>");
		if (!notice.isBlank())
			body.append("<div class='notice'>").append(escape(notice)).append("</div>");
		body.append("<div class='navigation'><form method='get' action='/shop'><input type='hidden' name='session_id' value='")
			.append(escape(sessionId)).append("'><label for='category'>SHOP CATEGORY</label><select id='category' name='category'>");
		String group = "";
		for (Section tab : Section.values()) {
			if (!group.equals(tab.group)) {
				if (!group.isEmpty()) body.append("</optgroup>");
				group = tab.group;
				body.append("<optgroup label='").append(group).append("'>");
			}
			body.append("<option value='").append(tab.id).append("'")
				.append(tab == section ? " selected" : "").append(">").append(tab.title).append("</option>");
		}
		body.append("</optgroup></select><button type='submit'>Browse</button></form><div class='quicklinks'>")
			.append("<a href='").append(escape(shopUrl(sessionId, Section.ALL, "", 1))).append("'>All goods</a>")
			.append("<a href='").append(escape(shopUrl(sessionId, Section.OUTFITS, "", 1))).append("'>Clothing</a>")
			.append("<a href='").append(escape(shopUrl(sessionId, Section.PETS, "", 1))).append("'>Pets</a>")
			.append("<a href='").append(escape(shopUrl(sessionId, Section.GM, "", 1))).append("'>GM collection</a></div></div>");
		body.append("<div class='content'><div class='section-heading'><div><div class='eyebrow'>CURATED FOR THE JOURNEY</div><h2>")
			.append(section.title).append("</h2><p>").append(section.description).append("</p></div>")
			.append("<div class='delivery'>DELIVERED BY<br><strong>BLACK CLOUD MAIL</strong></div></div>")
			.append("<form class='search' method='get' action='/shop'><input type='hidden' name='session_id' value='")
			.append(escape(sessionId)).append("'><input type='hidden' name='category' value='")
			.append(section.id).append("'><input type='text' name='q' maxlength='60' placeholder='Find an item in this section' value='")
			.append(escape(query)).append("'><button type='submit'>Find items</button></form>");
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < offers.size(); i++) {
			Offer offer = offers.get(i);
			if (section != Section.ALL && offer.section() != section)
				continue;
			if (!search.isEmpty() && !(offer.name() + " " + offer.description()).toLowerCase(Locale.ROOT).contains(search))
				continue;
			matches.add(i);
		}
		int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		pageNumber = Math.min(pageNumber, pages);
		int from = (pageNumber - 1) * PAGE_SIZE;
		int to = Math.min(matches.size(), from + PAGE_SIZE);
		body.append("<div class='results'>").append(matches.size()).append(" offers <span>PAGE ")
			.append(pageNumber).append(" OF ").append(pages).append("</span></div>");
		for (int pos = from; pos < to; pos++) {
			int i = matches.get(pos);
			Offer offer = offers.get(i);
			body.append("<div class='product'><table><tr><td class='mark-cell'><div class='mark'>")
				.append(offer.section().badge).append("</div></td><td class='details'><div class='item-name'>")
				.append(escape(offer.name())).append("</div><div class='item-meta'>LV ").append(offer.unlockLevel())
				.append("+ <span>/</span> QUANTITY ").append(offer.count());
			if (offer.race() != Race.PC_ALL)
				body.append(" <span>/</span> ").append(offer.race() == Race.ELYOS ? "ELYOS" : "ASMODIAN");
			if (offer.gender() != null)
				body.append(" <span>/</span> ").append(offer.gender());
			body.append("</div><div class='item-note'>")
				.append(escape(offer.description())).append("</div></td><td class='purchase'><div class='price'>")
				.append(String.format("%,d", offer.price())).append("</div><div class='currency'>KINAH</div>");
			if (player.getLevel() < offer.unlockLevel()) {
				body.append("<div class='unavailable'>Unlocks at Lv ").append(offer.unlockLevel()).append("</div>");
			} else if (offer.race() != Race.PC_ALL && offer.race() != player.getRace()) {
				body.append("<div class='unavailable'>Other faction</div>");
			} else if (offer.gender() != null && offer.gender() != player.getGender()) {
				body.append("<div class='unavailable'>Other gender</div>");
			} else if (balance < offer.price()) {
				body.append("<div class='unavailable'>More Kinah needed</div>");
			} else {
				byte[] bytes = new byte[18];
				random.nextBytes(bytes);
				String form = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
				purchaseForms.put(form, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10));
				body.append("<form method='post' action='/shop'><input type='hidden' name='session_id' value='")
					.append(escape(sessionId)).append("'><input type='hidden' name='category' value='")
					.append(section.id).append("'><input type='hidden' name='q' value='").append(escape(query))
					.append("'><input type='hidden' name='page' value='").append(pageNumber)
					.append("'><input type='hidden' name='form' value='").append(form)
					.append("'><input type='hidden' name='offer' value='").append(i)
					.append("'><button class='buy' type='submit'>Buy now</button></form>");
			}
			body.append("</td></tr></table></div>");
		}
		if (matches.isEmpty())
			body.append("<div class='empty'>No items match that search in this section. Try another word or choose All goods.</div>");
		if (pages > 1) {
			body.append("<div class='pagination'>");
			if (pageNumber > 1)
				body.append("<a href='").append(escape(shopUrl(sessionId, section, query, pageNumber - 1))).append("'>&larr; Previous</a>");
			body.append("<span>").append(pageNumber).append(" / ").append(pages).append("</span>");
			if (pageNumber < pages)
				body.append("<a href='").append(escape(shopUrl(sessionId, section, query, pageNumber + 1))).append("'>Next &rarr;</a>");
			body.append("</div>");
		}
		body.append("<div class='footer'>").append(matches.size()).append(matches.size() == 1 ? " offer" : " offers")
			.append(" &nbsp; / &nbsp; Kinah is charged when you buy. Items arrive through Black Cloud mail.</div></div></div>");
		return page(body.toString());
	}

	private static String shopUrl(String sessionId, Section section, String query, int pageNumber) {
		return "/shop?session_id=" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8)
			+ "&category=" + section.id + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&page=" + pageNumber;
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private static String page(String body) {
		return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
			+ "<title>Black Cloud Marketplace</title><style>" + STYLE + "</style></head><body>" + body + "</body></html>";
	}

	private static final String STYLE = """
		body{margin:0;background:#0b1521;color:#e6e0cf;font:13px Arial,sans-serif;line-height:1.45}
		.shell{max-width:860px;margin:0 auto;background:#111e2b;min-height:100%}
		.masthead{padding:22px 26px 20px;background:#172839;border-bottom:3px solid #ac884e;overflow:hidden}
		.brand{float:left;width:63%}.brand h1{font:normal 30px Georgia,serif;color:#f4d392;line-height:1.02;margin:5px 0 9px}
		.brand h1 span{color:#f6eee0}.brand p{color:#b7c5cc;margin:0;max-width:420px}
		.eyebrow{font-size:10px;letter-spacing:2px;font-weight:bold;color:#d2a95f}
		.account{float:right;width:31%;background:#0e1c2b;border:1px solid #46586b;padding:12px;box-sizing:border-box}
		.account-label{color:#9aacb7;font-size:10px;letter-spacing:1px;font-weight:bold}
		.character{font-size:15px;font-weight:bold;color:#f6eee0;margin:3px 0 9px;overflow-wrap:break-word}
		.character span{color:#a8d5d4;font-size:11px;font-weight:normal}
		.balance{font:bold 20px Georgia,serif;color:#f4d392;margin-top:3px}.balance span{font:11px Arial,sans-serif;color:#c6aa79}
		.notice{margin:14px 24px 0;padding:10px 14px;background:#1d3d3e;border-left:4px solid #72c5b3;color:#eff9ee}
		.navigation{padding:11px 24px;background:#0d1926;border-bottom:1px solid #35485b;overflow:hidden}
		.navigation form{float:left}.navigation label{font-size:10px;letter-spacing:1px;font-weight:bold;color:#d2a95f;margin-right:9px}
		.navigation select{width:205px;padding:7px;background:#e8e6de;color:#172331;border:1px solid #657787}
		.navigation button{padding:7px 15px;background:#b28b50;border:1px solid #d8b977;color:#111b26;font-weight:bold;cursor:pointer}
		.quicklinks{float:right;padding-top:6px}.quicklinks a{color:#d3dfe1;margin-left:11px;text-decoration:none;font-size:11px}
		.quicklinks a:hover{color:#f4d392;text-decoration:underline}
		.content{padding:19px 24px 24px}.section-heading{overflow:hidden;margin-bottom:15px}
		.section-heading h2{font:normal 24px Georgia,serif;color:#f6eee0;margin:2px 0 3px}
		.section-heading p{margin:0;color:#afbec7}.delivery{float:right;text-align:right;color:#90a6b1;font-size:10px;letter-spacing:1px;margin-top:-28px}
		.delivery strong{color:#e6bf7d;font-size:11px}.search{background:#172838;border:1px solid #3e5368;padding:8px;margin-bottom:13px;overflow:hidden}
		.search input[type=text]{background:#e8e6de;color:#172331;border:1px solid #657787;padding:8px;width:69%;font:13px Arial,sans-serif;box-sizing:border-box}
		.search button{float:right;width:28%;padding:8px 3px;background:#426174;border:1px solid #658698;color:white;font-weight:bold;cursor:pointer}
		.results{font-size:11px;color:#c6b28e;margin:0 1px 9px}.results span{float:right;letter-spacing:1px}
		.product{background:#192b3b;border:1px solid #334a5e;border-left:3px solid #9c7947;margin-bottom:8px}
		.product table{width:100%;border-collapse:collapse}.product td{vertical-align:middle;padding:10px 7px}
		.mark-cell{width:49px}.mark{height:39px;width:43px;line-height:39px;text-align:center;background:#273d50;border:1px solid #637a84;color:#e9c78e;font:bold 11px Arial,sans-serif;letter-spacing:1px}
		.details{width:auto}.item-name{font:bold 15px Georgia,serif;color:#f4e9d4;line-height:1.2}
		.item-meta{color:#d9b676;font-size:10px;letter-spacing:1px;margin-top:4px;font-weight:bold}.item-meta span{color:#798c9c;margin:0 3px}
		.item-note{color:#acbdc6;font-size:12px;margin-top:4px}.purchase{width:142px;text-align:right;white-space:nowrap}
		.price{font:bold 17px Georgia,serif;color:#f2cb83}.currency{color:#a8b4bd;font-size:10px;letter-spacing:1px}
		.buy{margin-top:7px;padding:7px 15px;width:118px;background:#c39755;border:1px solid #e8c88b;color:#13202d;font-weight:bold;cursor:pointer}
		.buy:hover{background:#efc779}.unavailable{margin-top:8px;color:#98a9b4;font-size:11px}
		.empty{padding:27px 16px;background:#192b3b;border:1px solid #3e5368;color:#cbd6d7;text-align:center}
		.pagination{text-align:center;padding:12px 0 3px}.pagination a,.pagination span{display:inline-block;padding:8px 13px;margin:0 3px;border:1px solid #567083;color:#e9d6ad;text-decoration:none;background:#1b3042}
		.pagination a:hover{background:#b28b50;color:#111b26}
		.footer{padding:15px 2px 2px;color:#9aadb6;font-size:11px}
		@media(max-width:680px){.masthead{padding:17px}.brand,.account{float:none;width:auto}.account{margin-top:14px}.content{padding:15px}.delivery{float:none;text-align:left;margin:8px 0 0}.navigation{padding:9px}.navigation form,.quicklinks{float:none}.quicklinks{padding:8px 0 0}.quicklinks a{margin:0 11px 0 0}.purchase{width:104px}.buy{width:95px}.item-name{font-size:13px}.item-note{font-size:11px}.price{font-size:14px}.mark-cell{width:38px}.mark{width:33px}}
		""";

	private static void reply(HttpExchange exchange, int status, String html) throws IOException {
		byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
	}

	private enum Section {
		ALL("all", "All goods", "Browse the entire Kinah catalog.", "ALL", "Marketplace"),
		STARTER("starter", "Starter", "Simple supplies for the first levels.", "NEW", "Adventure"),
		SUPPLIES("supplies", "Recovery", "Potions and serums matched to your next challenge.", "SUP", "Adventure"),
		TRAVEL("travel", "Travel", "Move faster, return home, and keep your adventure going.", "MOV", "Adventure"),
		WEAPONS("weapons", "Weapons", "Sauro Guard gear to enter endgame and Sauro Commander goals beyond it.", "ARM", "Adventure"),
		ARMOR("armor", "Armor", "Build a complete level 65 Sauro Guard set for your class.", "DEF", "Adventure"),
		UPGRADES("upgrades", "Upgrades", "Shards, stones, manastones, and supplements for stronger gear.", "UPG", "Adventure"),
		FOOD("food", "Food & drinks", "Meals and drinks for every journey.", "EAT", "Consumables"),
		POTIONS("potions", "Shop potions", "Extra potions from the 4.8 item set.", "POT", "Consumables"),
		OUTFITS("outfits", "Clothing", "Costumes and armor looks.", "FIT", "Appearance"),
		HATS("hats", "Hats", "Headpieces and costume hats.", "HAT", "Appearance"),
		WEAPON_SKINS("weapon_skins", "Weapon looks", "Collectible weapon appearances.", "SKN", "Appearance"),
		WINGS("wings", "Wings", "Wings for an unmistakable silhouette.", "WNG", "Appearance"),
		STYLE("style", "Wings & style", "Wing upgrades and the signature Black Cloud look.", "WNG", "Appearance"),
		DYES("dyes", "Dyes", "Give your outfit a new color.", "DYE", "Appearance"),
		HAIR("hair", "Hairstyles", "Character hairstyles by faction and gender.", "HAI", "Appearance"),
		EMOTES("emotes", "Emotes", "Dances, gestures, and expressions.", "EMO", "Collection"),
		TITLES("titles", "Titles", "Collect title cards for your character.", "TTL", "Collection"),
		PETS("pets", "Pets", "Adopt a loyal companion.", "PET", "Collection"),
		MOUNTS("mounts", "Mounts", "Travel companions from the 4.8 item set.", "RDE", "Collection"),
		SERVICES("services", "Services", "Cube and warehouse expansion cards.", "SVC", "Collection"),
		GM("gm", "GM collection", "GM costumes, weapon, and gift boxes.", "GM", "Collection");

		private final String id;
		private final String title;
		private final String description;
		private final String badge;
		private final String group;

		Section(String id, String title, String description, String badge, String group) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.badge = badge;
			this.group = group;
		}

		private static Section find(String id) {
			for (Section section : values()) {
				if (section.id.equals(id))
					return section;
			}
			return null;
		}

		private static Section selected(String id, int level) {
			Section section = find(id);
			return section != null ? section : level < 20 ? STARTER : level < 60 ? SUPPLIES : UPGRADES;
		}
	}

	private record Offer(int itemId, long count, long price, Section section, int unlockLevel, String name, String description, Race race, Gender gender) {
	}
}
