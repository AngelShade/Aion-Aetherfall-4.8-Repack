package com.aionemu.gameserver.services;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
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
	private static final Path MEDIA = Path.of("config/ingameshop/media");
	private static final int PAGE_SIZE = 12;
	private static final SecureRandom random = new SecureRandom();
	private static final Map<String, PurchaseForm> purchaseForms = new ConcurrentHashMap<>();
	private static volatile CatalogState catalogState;
	private static volatile List<FileStamp> failedStamps = List.of();
	private static HttpServer server;

	private MarketplaceService() {
	}

	public static void start() {
		if (!GSConfig.ENABLE_MARKETPLACE)
			return;
		try {
			catalogState = new CatalogState(loadOffers(), sourceStamps(), 1);
			server = HttpServer.create(new InetSocketAddress(GSConfig.MARKETPLACE_BIND, GSConfig.MARKETPLACE_PORT), 16);
			server.createContext("/shop", MarketplaceService::handle);
			server.createContext("/shop/media/", MarketplaceService::serveMedia);
			server.setExecutor(ThreadPoolManager.getInstance());
			server.start();
			log.info("Private marketplace listening at http://{}:{}/shop ({} offers)", GSConfig.MARKETPLACE_BIND,
				GSConfig.MARKETPLACE_PORT, catalogState.offers().size());
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
			result.add(new Offer(itemId, count, price, section, unlockLevel, item.getName(), parts[5], item.getRace(), gender, item.getItemQuality()));
		}
		}
		return List.copyOf(result);
	}

	private static List<FileStamp> sourceStamps() throws IOException {
		List<FileStamp> stamps = new ArrayList<>(2);
		for (Path file : List.of(CATALOG, EXTRA_CATALOG))
			stamps.add(new FileStamp(Files.getLastModifiedTime(file), Files.size(file)));
		return List.copyOf(stamps);
	}

	private static CatalogState currentCatalog() {
		CatalogState current = catalogState;
		List<FileStamp> stamps;
		try {
			stamps = sourceStamps();
		} catch (IOException e) {
			log.warn("Could not check marketplace catalogs; keeping the current offers", e);
			return current;
		}
		if (current.stamps().equals(stamps) || failedStamps.equals(stamps))
			return current;
		synchronized (MarketplaceService.class) {
			current = catalogState;
			if (current.stamps().equals(stamps) || failedStamps.equals(stamps))
				return current;
			try {
				List<Offer> updated = loadOffers();
				if (!stamps.equals(sourceStamps()))
					return current; // An editor was still writing; retry on the next request.
				CatalogState next = new CatalogState(updated, stamps, current.version() + 1);
				catalogState = next;
				failedStamps = List.of();
				purchaseForms.clear();
				log.info("Marketplace catalogs reloaded ({} offers)", updated.size());
				return next;
			} catch (Exception e) {
				failedStamps = stamps;
				log.warn("Marketplace catalog update is invalid; keeping the current offers", e);
				return current;
			}
		}
	}

	private static void serveMedia(HttpExchange exchange) throws IOException {
		try {
			if (!exchange.getRequestMethod().equals("GET")) {
				exchange.sendResponseHeaders(405, -1);
				return;
			}
			String name = exchange.getRequestURI().getPath().substring("/shop/media/".length());
			if (!name.matches("icons/[0-9]{9}\\.png|marketplace\\.css|hero\\.webp|banb\\.ttf")) {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			Path file = MEDIA.resolve(name);
			if (!Files.isRegularFile(file)) {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			String type = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp"
				: name.endsWith(".ttf") ? "font/ttf" : "text/css; charset=utf-8";
			byte[] data = Files.readAllBytes(file);
			if (name.startsWith("icons/"))
				data = fillIconFrame(data);
			exchange.getResponseHeaders().set("Content-Type", type);
			exchange.getResponseHeaders().set("Cache-Control", "no-store");
			exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			exchange.sendResponseHeaders(200, data.length);
			exchange.getResponseBody().write(data);
		} finally {
			exchange.close();
		}
	}

	private static byte[] fillIconFrame(byte[] data) throws IOException {
		BufferedImage icon = ImageIO.read(new ByteArrayInputStream(data));
		if (icon == null || icon.getWidth() != 64 || icon.getHeight() != 64)
			return data;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if ((x >= 40 || y >= 40) && (icon.getRGB(x, y) >>> 24) != 0)
					return data;
			}
		}
		BufferedImage expanded = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = expanded.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.drawImage(icon, 0, 0, 64, 64, 0, 0, 40, 40, null);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		ImageIO.write(expanded, "png", result);
		return result.toByteArray();
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
			CatalogState catalog = currentCatalog();
			String sessionId = args.getOrDefault("session_id", args.getOrDefault("token", ""));
			Player player = findPlayer(sessionId);
			// The NA browser may omit its session query or keep the old one after a game-server restart.
			// For the loopback-only solo shop, the sole online character can still be identified locally.
			if (player == null && exchange.getRemoteAddress().getAddress().isLoopbackAddress()
				&& GSConfig.MARKETPLACE_BIND.equals("127.0.0.1") && World.getInstance().getAllPlayers().size() == 1) {
				player = World.getInstance().getAllPlayers().iterator().next();
				sessionId = ""; // Do not carry a stale token into links or purchase forms.
			}
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
				PurchaseForm purchaseForm = purchaseForms.remove(form);
				if (purchaseForm == null || purchaseForm.expires() < System.currentTimeMillis()
					|| purchaseForm.catalogVersion() != catalog.version() || purchaseForm.playerId() != player.getObjectId()) {
					reply(exchange, 403, page("The shop changed or this purchase form expired. Reopen the Marketplace and try again."));
					return;
				}
				Offer offer = purchaseForm.offer();
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
			reply(exchange, 200, storefront(player, sessionId, section, query, pageNumber, notice, currentCatalog()));
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

	private static String storefront(Player player, String sessionId, Section section, String query, int pageNumber, String notice, CatalogState catalog) {
		purchaseForms.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
		long balance = player.getInventory().getKinah();
		String search = query.toLowerCase(Locale.ROOT);
		StringBuilder body = new StringBuilder("<div class='shell'><header class='hero'><div class='hero-inner'>")
			.append("<div class='realm'><span class='sigil'>&#9670;</span> ATREIA / PRIVATE REALM</div><h1>Black Cloud<span>Marketplace</span></h1>")
			.append("<p>Rare curiosities, trusted supplies, and a little Shugo magic for every journey.</p>")
			.append("<div class='account'><div class='account-label'>SHOPPING AS</div><div class='character'>")
			.append(escape(player.getName())).append(" <span>Lv ").append(player.getLevel())
			.append("</span></div><div class='account-label'>YOUR BALANCE</div><div class='balance'>")
			.append(String.format("%,d", balance)).append(" <span>Kinah</span></div></div></div></header>");
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
		for (int i = 0; i < catalog.offers().size(); i++) {
			Offer offer = catalog.offers().get(i);
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
			.append(pageNumber).append(" OF ").append(pages).append("</span></div><div class='products'>");
		for (int pos = from; pos < to; pos++) {
			int i = matches.get(pos);
			Offer offer = catalog.offers().get(i);
			body.append("<article class='product quality-").append(offer.quality().name().toLowerCase(Locale.ROOT))
				.append("'><div class='product-main'><div class='icon-frame'><img src='/shop/media/icons/")
				.append(offer.itemId()).append(".png?v=3' width='64' height='64' alt=''></div><div class='item-copy'><h3 class='item-name'>")
				.append(escape(offer.name())).append("</h3><div class='item-meta'>LV ").append(offer.unlockLevel())
				.append("+ <span>/</span> QUANTITY ").append(offer.count());
			if (offer.race() != Race.PC_ALL)
				body.append(" <span>/</span> ").append(offer.race() == Race.ELYOS ? "ELYOS" : "ASMODIAN");
			if (offer.gender() != null)
				body.append(" <span>/</span> ").append(offer.gender());
			body.append("</div><div class='item-note'>")
				.append(escape(offer.description())).append("</div></div></div><div class='product-bottom'><div class='price-block'><div class='price'>")
				.append(String.format("%,d", offer.price())).append("</div><div class='currency'>KINAH</div></div>");
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
				purchaseForms.put(form, new PurchaseForm(offer, player.getObjectId(), catalog.version(),
					System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
				body.append("<form method='post' action='/shop'><input type='hidden' name='session_id' value='")
					.append(escape(sessionId)).append("'><input type='hidden' name='category' value='")
					.append(section.id).append("'><input type='hidden' name='q' value='").append(escape(query))
					.append("'><input type='hidden' name='page' value='").append(pageNumber)
					.append("'><input type='hidden' name='form' value='").append(form)
					.append("'><button class='buy' type='submit'>Buy now</button></form>");
			}
			body.append("</div></article>");
		}
		body.append("</div>");
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
			+ "<title>Black Cloud Marketplace</title><link rel='stylesheet' href='/shop/media/marketplace.css?v=3'>"
			+ "</head><body>" + body + "</body></html>";
	}

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

	private record Offer(int itemId, long count, long price, Section section, int unlockLevel, String name, String description, Race race, Gender gender, ItemQuality quality) {
	}

	private record FileStamp(FileTime modified, long size) {
	}

	private record CatalogState(List<Offer> offers, List<FileStamp> stamps, long version) {
	}

	private record PurchaseForm(Offer offer, int playerId, long catalogVersion, long expires) {
	}
}
