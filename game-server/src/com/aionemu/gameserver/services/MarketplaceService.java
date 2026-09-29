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
import com.aionemu.gameserver.model.templates.item.WeaponStats;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.model.stats.calc.functions.StatFunction;
import com.aionemu.gameserver.model.stats.calc.functions.StatRateFunction;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** A small private storefront for the NA client's in-game browser. */
public final class MarketplaceService {

	private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);
	private static final Path CASH_CATALOG = Path.of("config/ingameshop/marketplace_cash.tsv");
	private static final Path MEDIA = Path.of("config/ingameshop/media");
	private static final int PAGE_SIZE = 12;
	private static final SecureRandom random = new SecureRandom();
	private static final Map<String, PurchaseForm> purchaseForms = new ConcurrentHashMap<>();
	private static final Map<String, StoredReceipt> purchaseReceipts = new ConcurrentHashMap<>();
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
		for (String line : Files.readAllLines(CASH_CATALOG, StandardCharsets.UTF_8)) {
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
		return List.copyOf(result);
	}

	private static List<FileStamp> sourceStamps() throws IOException {
		return List.of(new FileStamp(Files.getLastModifiedTime(CASH_CATALOG), Files.size(CASH_CATALOG)));
	}

	private static CatalogState currentCatalog() {
		CatalogState current = catalogState;
		List<FileStamp> stamps;
		try {
			stamps = sourceStamps();
		} catch (IOException e) {
			log.warn("Could not check marketplace catalog; keeping the current offers", e);
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
				log.info("Marketplace catalog reloaded ({} offers)", updated.size());
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
			if (!name.matches("icons/[0-9]{9}\\.png|marketplace\\.css|hero\\.webp|fashion\\.webp|companions\\.webp|adventure\\.webp|banb\\.ttf")) {
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
			Section section = Section.selected(args.get("category"));
			String query = args.getOrDefault("q", "").trim();
			if (query.length() > 60)
				query = query.substring(0, 60);
			int pageNumber;
			try {
				pageNumber = Math.max(1, Integer.parseInt(args.getOrDefault("page", "1")));
			} catch (NumberFormatException e) {
				pageNumber = 1;
			}
			PurchaseResult receipt = null;
			int responseStatus = 200;
			if (method.equals("POST")) {
				String form = args.getOrDefault("form", "");
				PurchaseForm purchaseForm = purchaseForms.remove(form);
				StoredReceipt prior = purchaseReceipts.get(form);
				if (purchaseForm == null && prior != null && prior.playerId() == player.getObjectId()
					&& prior.expires() >= System.currentTimeMillis()) {
					receipt = prior.result();
				} else if (purchaseForm == null || purchaseForm.expires() < System.currentTimeMillis()
					|| purchaseForm.catalogVersion() != catalog.version() || purchaseForm.playerId() != player.getObjectId()) {
					receipt = new PurchaseResult(PurchaseStatus.FAILED, null,
						"This purchase form expired or the shop changed. Open the item again to review a new purchase.");
					responseStatus = 403;
				} else {
					Offer offer = purchaseForm.offer();
					Player buyer = player;
					CompletableFuture<PurchaseResult> result = new CompletableFuture<>();
					ThreadPoolManager.getInstance().execute(() -> {
						try {
							result.complete(purchase(buyer, offer));
						} catch (Exception e) {
							result.completeExceptionally(e);
						}
					});
					result.thenAccept(completed -> purchaseReceipts.put(form,
						new StoredReceipt(buyer.getObjectId(), completed, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10))));
					try {
						receipt = result.get(10, TimeUnit.SECONDS);
					} catch (Exception e) {
						log.error("Marketplace purchase confirmation failed for " + player, e);
						receipt = new PurchaseResult(PurchaseStatus.UNKNOWN, offer,
							"The server could not confirm this purchase. Check your Kinah and Black Cloud mail before trying again.");
					}
					purchaseReceipts.putIfAbsent(form, new StoredReceipt(buyer.getObjectId(), receipt,
						System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
				}
			}
			reply(exchange, responseStatus, storefront(player, sessionId, section, query, pageNumber, receipt, currentCatalog(),
				args.getOrDefault("item", ""), "1".equals(args.get("review")),
				args.getOrDefault("sort", "curated"), args.getOrDefault("filter", "all")));
		} finally {
			exchange.close();
		}
	}

	private static PurchaseResult purchase(Player player, Offer offer) {
		synchronized (player.getInventory()) {
			if (World.getInstance().getPlayer(player.getObjectId()) != player)
				return new PurchaseResult(PurchaseStatus.FAILED, offer, "You are no longer logged in.");
			if (player.getLevel() < offer.unlockLevel())
				return new PurchaseResult(PurchaseStatus.FAILED, offer, "This item unlocks at level " + offer.unlockLevel() + ".");
			if (offer.race() != Race.PC_ALL && offer.race() != player.getRace())
				return new PurchaseResult(PurchaseStatus.FAILED, offer, "This item is for the other faction.");
			if (offer.gender() != null && offer.gender() != player.getGender())
				return new PurchaseResult(PurchaseStatus.FAILED, offer, "This item requires a different character gender.");
			if (!player.getInventory().tryDecreaseKinah(offer.price()))
				return new PurchaseResult(PurchaseStatus.FAILED, offer, "You do not have enough Kinah.");
			boolean delivered = SystemMailService.sendMail("$$CASH_ITEM_MAIL", player.getName(),
				offer.itemId() + ", " + offer.count(), "0, " + (System.currentTimeMillis() / 1000) + ",",
				offer.itemId(), offer.count(), 0, LetterType.BLACKCLOUD);
			if (!delivered) {
				player.getInventory().increaseKinah(offer.price());
				return new PurchaseResult(PurchaseStatus.FAILED, offer, "Delivery failed. Your Kinah was returned.");
			}
			log.info("Marketplace: {} bought item {} x{} for {} Kinah", player, offer.itemId(), offer.count(), offer.price());
			return new PurchaseResult(PurchaseStatus.SUCCESS, offer, "Your item was sent through Black Cloud mail.");
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

	private static String storefront(Player player, String sessionId, Section section, String query, int pageNumber,
		PurchaseResult receipt, CatalogState catalog, String itemRequest, boolean review, String sort, String filter) {
		purchaseForms.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
		purchaseReceipts.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
		long balance = player.getInventory().getKinah();
		if (!List.of("curated", "price-low", "price-high", "name").contains(sort)) sort = "curated";
		if (!List.of("all", "usable", "affordable").contains(filter)) filter = "all";
		StringBuilder body = new StringBuilder("<div class='shell'><div class='skyline'><div class='skyline-inner'>")
			.append("<span class='realm-mark'>◆</span> BLACK CLOUD TRADING COMPANY <span class='skyline-right'>ATREIA · KINAH MARKETPLACE</span></div></div>")
			.append("<header class='hero'><div class='hero-inner'><div class='hero-eyebrow'>THE SHUGO COLLECTION</div>")
			.append("<h1>Black Cloud <span>Marketplace</span></h1><p>Curiosities from across Atreia. ")
			.append("Find a new look, a loyal companion, or the comforts that keep your journey moving.</p>")
			.append("<div class='hero-actions'><a class='hero-button' href='")
			.append(escape(shopUrl(sessionId, Section.FEATURED, "", 1) + "#shop-content")).append("'>Explore featured <span>→</span></a>")
			.append("<span class='hero-currency'>EVERY OFFER · KINAH ONLY</span></div></div></header>")
			.append("<div class='trustbar'><span><b>01</b> Choose your treasure</span><span><b>02</b> Pay with Kinah</span>")
			.append("<span><b>03</b> Receive Black Cloud mail</span></div>");
		body.append("<div class='shop-layout'><aside class='sidebar'><div class='sidebar-top'>");
		body.append("<div class='sidebar-kicker'>YOUR CHARACTER</div><div class='character-name'>")
			.append(escape(player.getName())).append(" <small>LV ").append(player.getLevel()).append("</small></div>")
			.append("<div class='wallet-label'>AVAILABLE BALANCE</div><div class='wallet-number'>")
			.append(String.format("%,d", balance)).append("</div><div class='wallet-unit'>KINAH</div></div>")
			.append("<div class='sidebar-heading'>EXPLORE THE SHOP</div><nav class='side-nav' aria-label='Marketplace categories'>");
		Map<Section, Integer> categoryCounts = new HashMap<>();
		java.util.Set<Integer> currentItems = new java.util.HashSet<>();
		for (Offer offer : catalog.offers()) {
			for (Section tab : Section.values()) if (tab != Section.ALL && tab.includes(offer.section()))
				categoryCounts.merge(tab, 1, Integer::sum);
			currentItems.add(offer.itemId());
		}
		categoryCounts.put(Section.ALL, currentItems.size());
		for (Section tab : Section.values()) {
			if (tab.isChild() && tab.parent() != section && tab.parent() != section.parent()
				&& (section.parent() == null || tab.parent() != section.parent().parent())) continue;
			body.append("<a class='side-link").append(tab == section ? " active" : "")
				.append(tab.isChild() ? " child" : "")
				.append(tab.isGrandchild() ? " grandchild" : "")
				.append(tab != Section.ALL && tab != section && tab.includes(section) ? " branch" : "").append("' href='")
				.append(escape(shopUrl(sessionId, tab, "", 1) + "#shop-content")).append("'><span class='side-symbol'>")
				.append(tab == Section.FEATURED ? "✦" : tab.isChild() ? "·" : "◇")
				.append("</span><span>").append(escape(tab.title)).append("</span><span class='side-count'>")
				.append(String.format("%,d", categoryCounts.getOrDefault(tab, 0)))
				.append("</span><span class='side-arrow'>›</span></a>");
		}
		body.append("</nav><div class='sidebar-help'><div class='sidebar-kicker'>BLACK CLOUD PROMISE</div>")
			.append("<p>Every listed item comes from this server's Aion 4.8 data. Check the item details before spending Kinah.</p></div></aside>")
			.append("<main class='main-content' id='shop-content'>");

		Offer chosen = null;
		if (!itemRequest.isBlank()) {
			try {
				int requestedId = Integer.parseInt(itemRequest);
				for (Offer offer : catalog.offers()) {
					if (offer.itemId() == requestedId && section.includes(offer.section())) {
						chosen = offer;
						break;
					}
				}
			} catch (NumberFormatException ignored) {
				// An invalid detail link falls back to its collection.
			}
		}
		if (receipt != null) {
			appendReceipt(body, player, sessionId, section, query, pageNumber, receipt, balance, sort, filter);
		} else if (chosen != null) {
			appendDetail(body, player, sessionId, section, query, pageNumber, chosen, balance, catalog, review, sort, filter);
		} else {
			if (section == Section.FEATURED && query.isBlank()) appendSpotlights(body, sessionId);
			body.append("<div class='collection-heading'><div><div class='eyebrow'>THE BLACK CLOUD EDIT</div><h2>")
				.append(escape(section.title)).append("</h2><p>").append(escape(section.description)).append("</p></div>")
				.append("<div class='heading-seal'>◆<small>ATREIA</small></div></div>");
			if (section.hasChildren()) {
				body.append("<div class='subcat-strip' aria-label='Browse subcategories'>");
				for (Section child : Section.values()) if (child.parent() == section && categoryCounts.getOrDefault(child, 0) > 0) {
					body.append("<a class='subcat-tile' href='")
						.append(escape(shopUrl(sessionId, child, "", 1) + "#shop-content"))
						.append("'><span class='subcat-kicker'>EXPLORE COLLECTION</span><strong>")
						.append(escape(child.title)).append("</strong><span class='subcat-count'>")
						.append(String.format("%,d", categoryCounts.getOrDefault(child, 0)))
						.append(" offers →</span></a>");
				}
				body.append("</div>");
			}
			body.append("<form class='finder' method='get' action='/shop#shop-content'><input type='hidden' name='session_id' value='")
				.append(escape(sessionId)).append("'><input type='hidden' name='category' value='").append(section.id)
				.append("'><div class='search-field'><span>⌕</span><input name='q' maxlength='60' value='")
				.append(escape(query)).append("' placeholder='Search this collection' aria-label='Search items'></div>")
				.append("<label>ORDER BY<select name='sort'>");
		for (String[] option : new String[][] {{"curated", "Curated order"}, {"price-low", "Price: low to high"},
			{"price-high", "Price: high to low"}, {"name", "Name A–Z"}}) {
			body.append("<option value='").append(option[0]).append("'")
				.append(option[0].equals(sort) ? " selected" : "").append(">").append(option[1]).append("</option>");
		}
		body.append("</select></label><label>SHOW<select name='filter'>");
		for (String[] option : new String[][] {{"all", "All offers"}, {"usable", "Usable by me"}, {"affordable", "Within my balance"}}) {
			body.append("<option value='").append(option[0]).append("'")
				.append(option[0].equals(filter) ? " selected" : "").append(">").append(option[1]).append("</option>");
		}
		body.append("</select></label><button class='filter-button' type='submit'>Apply <span>→</span></button></form>");
		List<Offer> matches = new ArrayList<>();
		java.util.Set<Integer> regularItems = new java.util.HashSet<>();
		if (section == Section.ALL) for (Offer offer : catalog.offers()) {
			if (offer.section() != Section.FEATURED)
				regularItems.add(offer.itemId());
		}
		String search = query.toLowerCase(Locale.ROOT);
		for (Offer offer : catalog.offers()) {
			if (!section.includes(offer.section())) continue;
			if (section == Section.ALL && offer.section() == Section.FEATURED && regularItems.contains(offer.itemId())) continue;
			if (!search.isEmpty() && !(offer.name() + " " + offer.description()).toLowerCase(Locale.ROOT).contains(search)) continue;
			if (filter.equals("usable") && !availabilityMessage(player, offer, Long.MAX_VALUE).isEmpty()) continue;
			if (filter.equals("affordable") && offer.price() > balance) continue;
			matches.add(offer);
		}
		if (sort.equals("price-low")) matches.sort(java.util.Comparator.comparingLong(Offer::price));
		if (sort.equals("price-high")) matches.sort(java.util.Comparator.comparingLong(Offer::price).reversed());
		if (sort.equals("name")) matches.sort(java.util.Comparator.comparing(Offer::name, String.CASE_INSENSITIVE_ORDER));
		int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		pageNumber = Math.min(pageNumber, pages);
		int from = (pageNumber - 1) * PAGE_SIZE;
		int to = Math.min(matches.size(), from + PAGE_SIZE);
		body.append("<div class='results-line'><div><span class='result-count'>").append(matches.size())
			.append("</span> TREASURES FOUND</div><span>PAGE ").append(pageNumber).append(" / ").append(pages).append("</span></div>")
			.append("<div class='products'>");
		for (int i = from; i < to; i++) appendProductCard(body, player, sessionId, section, query, pageNumber, matches.get(i), balance, i - from, sort, filter);
		body.append("</div>");
		if (matches.isEmpty()) body.append("<div class='empty'><span>◇</span><h3>No treasures found</h3><p>Try a different search or clear the shop filters.</p></div>");
		if (pages > 1) {
			body.append("<div class='pagination'>");
			if (pageNumber > 1) body.append("<a href='").append(escape(shopUrl(sessionId, section, query, pageNumber - 1)
				+ "&sort=" + sort + "&filter=" + filter + "#shop-content")).append("'>← Previous</a>");
			int firstPage = Math.max(1, pageNumber - 2);
			int lastPage = Math.min(pages, pageNumber + 2);
			if (firstPage > 1) body.append("<a href='").append(escape(shopUrl(sessionId, section, query, 1)
				+ "&sort=" + sort + "&filter=" + filter + "#shop-content")).append("'>1</a>");
			if (firstPage > 2) body.append("<span>…</span>");
			for (int page = firstPage; page <= lastPage; page++) {
				if (page == pageNumber) body.append("<span class='current-page'>").append(page).append("</span>");
				else body.append("<a href='").append(escape(shopUrl(sessionId, section, query, page)
					+ "&sort=" + sort + "&filter=" + filter + "#shop-content")).append("'>").append(page).append("</a>");
			}
			if (lastPage < pages - 1) body.append("<span>…</span>");
			if (lastPage < pages) body.append("<a href='").append(escape(shopUrl(sessionId, section, query, pages)
				+ "&sort=" + sort + "&filter=" + filter + "#shop-content")).append("'>").append(pages).append("</a>");
			if (pageNumber < pages) body.append("<a href='").append(escape(shopUrl(sessionId, section, query, pageNumber + 1)
				+ "&sort=" + sort + "&filter=" + filter + "#shop-content")).append("'>Next →</a>");
			body.append("</div>");
		}
		}
		body.append("</main></div><footer class='shop-footer'><div><strong>BLACK CLOUD MARKETPLACE</strong>")
			.append("<p>Pay in Kinah. Your purchase is sent to Black Cloud mail.</p></div>")
			.append("<div>Check level, faction, gender, and item use details before buying.<br>Browse the collections above for every current offer.</div></footer></div>");
		return page(body.toString());
	}

	private static void appendReceipt(StringBuilder body, Player player, String sessionId, Section section, String query,
		int pageNumber, PurchaseResult receipt, long balance, String sort, String filter) {
		PurchaseStatus status = receipt.status();
		Offer offer = receipt.offer();
		String collectionUrl = shopUrl(sessionId, section, query, pageNumber) + "&sort=" + sort + "&filter=" + filter + "#shop-content";
		String itemUrl = offer == null ? collectionUrl : shopUrl(sessionId, section, query, pageNumber)
			+ "&sort=" + sort + "&filter=" + filter + "&item=" + offer.itemId() + "#shop-content";
		body.append("<section id='shop-receipt' class='receipt receipt-")
			.append(status.name().toLowerCase(Locale.ROOT)).append("' role='status' aria-live='assertive'>")
			.append("<div class='receipt-head'><div class='receipt-signet' aria-hidden='true'>")
			.append(status == PurchaseStatus.SUCCESS ? "✓" : status == PurchaseStatus.FAILED ? "×" : "?")
			.append("</div><div class='receipt-eyebrow'>BLACK CLOUD MARKETPLACE · KINAH TRANSACTION</div><h2>")
			.append(status == PurchaseStatus.SUCCESS ? "Purchase complete" : status == PurchaseStatus.FAILED
				? "Purchase not completed" : "Purchase status uncertain")
			.append("</h2><p>")
			.append(status == PurchaseStatus.SUCCESS ? "Payment accepted. Your treasure is on its way by Black Cloud mail."
				: escape(receipt.message())).append("</p></div><div class='receipt-body'>");
		if (offer != null) {
			body.append("<div class='receipt-item'><div class='receipt-icon'><img src='/shop/media/icons/")
				.append(offer.itemId()).append(".png?v=6' width='64' height='64' alt=''></div>")
				.append("<div class='receipt-item-copy'><span>YOUR SELECTED TREASURE</span><strong>")
				.append(escape(offer.name())).append("</strong><small>Quantity ").append(offer.count())
				.append(" · ").append(escape(offer.section().title)).append("</small></div></div>");
		}
		if (status == PurchaseStatus.SUCCESS) {
			body.append("<div class='receipt-ledger'><div><span>Kinah paid</span><strong>")
				.append(String.format("%,d", offer.price())).append(" Kinah</strong></div>")
				.append("<div><span>Current balance</span><strong>").append(String.format("%,d", balance))
				.append(" Kinah</strong></div></div>")
				.append("<div class='receipt-delivery'><span aria-hidden='true'>✉</span><div><strong>BLACK CLOUD MAIL DELIVERY</strong><p>")
				.append(escape(offer.name())).append(" was sent to ").append(escape(player.getName()))
				.append(". Open your in-game mail to collect it.</p></div></div>");
		} else if (status == PurchaseStatus.FAILED) {
			body.append("<div class='receipt-guidance'><strong>No purchase was completed.</strong><p>")
				.append(offer == null ? "Return to the shop and review a fresh offer before trying again."
					: "Review the item requirements and your Kinah balance before trying again.")
				.append("</p></div>");
		} else {
			body.append("<div class='receipt-guidance'><strong>Check before retrying.</strong><p>")
				.append("Look for the item in Black Cloud mail and check your Kinah balance to avoid a second purchase.")
				.append("</p></div>");
		}
		body.append("<div class='receipt-actions'><a class='receipt-action-primary' href='")
			.append(escape(status == PurchaseStatus.FAILED && offer != null ? itemUrl : collectionUrl)).append("'>")
			.append(status == PurchaseStatus.FAILED && offer != null ? "Review item" : "Continue shopping")
			.append(" <span>→</span></a>");
		if (offer != null) body.append("<a class='receipt-action-secondary' href='")
			.append(escape(status == PurchaseStatus.FAILED ? collectionUrl : itemUrl)).append("'>")
			.append(status == PurchaseStatus.FAILED ? "Browse collection" : "View item details")
			.append("</a>");
		body.append("</div></div></section>");
	}

	private static void appendSpotlights(StringBuilder body, String sessionId) {
		body.append("<div class='spotlight-intro'><span class='eyebrow'>CURATED IN ATREIA</span><h2>Find your next adventure.</h2>")
			.append("<p>The clouds have carried more than supplies. Discover looks and companions that make every journey yours.</p></div>")
			.append("<div class='spotlight-grid'><a class='spotlight fashion-art' href='")
			.append(escape(shopUrl(sessionId, Section.FASHION, "", 1) + "#shop-content")).append("'><span class='spotlight-kicker'>THE STYLE EDIT</span>")
			.append("<strong>Wear the extraordinary.</strong><span class='spotlight-link'>Explore fashion →</span></a>")
			.append("<a class='spotlight companions-art' href='")
			.append(escape(shopUrl(sessionId, Section.COMPANIONS, "", 1) + "#shop-content")).append("'><span class='spotlight-kicker'>TRAVEL TOGETHER</span>")
			.append("<strong>A companion for every road.</strong><span class='spotlight-link'>Meet pets & mounts →</span></a></div>")
			.append("<a class='adventure-banner' href='").append(escape(shopUrl(sessionId, Section.ESSENTIALS, "", 1) + "#shop-content"))
			.append("'><span>PREPARE FOR WHAT'S NEXT</span><strong>Supplies for the journey</strong><em>Browse essentials →</em></a>");
	}

	private static void appendProductCard(StringBuilder body, Player player, String sessionId, Section section,
		String query, int pageNumber, Offer offer, long balance, int position, String sort, String filter) {
		String detailUrl = shopUrl(sessionId, section, query, pageNumber) + "&sort=" + sort + "&filter=" + filter + "&item=" + offer.itemId() + "#shop-content";
		String availability = availabilityMessage(player, offer, balance);
		body.append("<article id='offer-").append(offer.itemId()).append("' class='product quality-")
			.append(offer.quality().name().toLowerCase(Locale.ROOT))
			.append("' style='--card-index:").append(position).append("'><div class='product-art'><span class='product-orbit'></span>")
			.append("<img src='/shop/media/icons/").append(offer.itemId()).append(".png?v=6' width='64' height='64' alt=''>")
			.append("<span class='product-quantity'>×").append(offer.count()).append("</span></div>")
			.append("<div class='product-content'><div class='product-overline'>")
			.append(escape(offer.section().title)).append(" <span>·</span> ")
			.append(escape(offer.quality().name().replace('_', ' '))).append("</div><h3>")
			.append(escape(offer.name())).append("</h3><p>").append(escape(offer.description())).append("</p>")
			.append("<div class='item-chips'><span>LV ").append(offer.unlockLevel()).append("+</span>");
		if (offer.race() != Race.PC_ALL) body.append("<span>").append(offer.race() == Race.ELYOS ? "ELYOS" : "ASMODIAN").append("</span>");
		if (offer.gender() != null) body.append("<span>").append(offer.gender()).append("</span>");
		body.append("</div></div><div class='product-bottom'><div class='price-label'>PRICE IN KINAH</div><div class='price'>")
			.append(String.format("%,d", offer.price())).append(" <small>Kinah</small></div>")
			.append("<a class='product-button' href='").append(escape(detailUrl)).append("'>View item <span>→</span></a></div>")
			.append("<button class='info-trigger' type='button' aria-label='Quick details for ")
			.append(escape(offer.name())).append("'>i</button><div class='mini-tooltip' role='tooltip'><span>QUICK LOOK</span><strong>")
			.append(escape(offer.name())).append("</strong><p>").append(escape(offer.description())).append("</p><small>")
			.append(availability.isEmpty() ? "Available for your character" : escape(availability)).append("</small></div></article>");
	}

	private static void appendDetail(StringBuilder body, Player player, String sessionId, Section section, String query,
		int pageNumber, Offer offer, long balance, CatalogState catalog, boolean review, String sort, String filter) {
		String back = shopUrl(sessionId, section, query, pageNumber) + "&sort=" + sort + "&filter=" + filter;
		String detailUrl = back + "&item=" + offer.itemId();
		String availability = availabilityMessage(player, offer, balance);
		body.append("<div class='breadcrumb'><a href='").append(escape(back + "#offer-" + offer.itemId())).append("'>← ")
			.append(escape(section.title)).append("</a><span> / </span>").append(review ? "Review purchase" : escape(offer.name())).append("</div>");
		if (review) {
			body.append("<div class='review-page'><div class='eyebrow'>ONE LAST LOOK</div><h2>Review your purchase</h2>")
				.append("<p>Confirm the item, your Kinah total, and where the delivery will go.</p><div class='review-item'>")
				.append("<img src='/shop/media/icons/").append(offer.itemId()).append(".png?v=6' width='64' height='64' alt=''>")
				.append("<div><strong>").append(escape(offer.name())).append("</strong><span>Quantity ")
				.append(offer.count()).append(" · ").append(escape(offer.section().title)).append("</span></div></div>")
				.append("<div class='review-line'><span>Price</span><strong>").append(String.format("%,d", offer.price()))
				.append(" Kinah</strong></div><div class='review-line'><span>Your balance</span><strong>")
				.append(String.format("%,d", balance)).append(" Kinah</strong></div>")
				.append("<div class='review-line total'><span>Balance after purchase</span><strong>")
				.append(String.format("%,d", Math.max(0, balance - offer.price()))).append(" Kinah</strong></div>")
				.append("<div class='delivery-note'>✉ &nbsp; Delivered to <strong>").append(escape(player.getName()))
				.append("</strong> through Black Cloud mail after payment.</div>");
			if (availability.isEmpty()) {
				byte[] bytes = new byte[18];
				random.nextBytes(bytes);
				String form = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
				purchaseForms.put(form, new PurchaseForm(offer, player.getObjectId(), catalog.version(),
					System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
				body.append("<form method='post' action='/shop#shop-receipt' onsubmit='return shopConfirm(this)'><input type='hidden' name='session_id' value='")
					.append(escape(sessionId)).append("'><input type='hidden' name='category' value='").append(section.id)
					.append("'><input type='hidden' name='q' value='").append(escape(query))
					.append("'><input type='hidden' name='page' value='").append(pageNumber)
					.append("'><input type='hidden' name='sort' value='").append(sort)
					.append("'><input type='hidden' name='filter' value='").append(filter)
					.append("'><input type='hidden' name='item' value='").append(offer.itemId())
					.append("'><input type='hidden' name='form' value='").append(form)
					.append("'><button class='purchase-button' type='submit'>Confirm & pay ")
					.append(String.format("%,d", offer.price())).append(" Kinah <span>→</span></button></form>");
			} else body.append("<div class='detail-unavailable'>").append(escape(availability)).append("</div>");
			body.append("<a class='quiet-link' href='").append(escape(detailUrl + "#shop-content")).append("'>← Back to item details</a></div>");
			return;
		}
		body.append("<div class='detail-page'><div class='detail-art quality-")
			.append(offer.quality().name().toLowerCase(Locale.ROOT)).append("'><div class='detail-halo'></div>")
			.append("<img src='/shop/media/icons/").append(offer.itemId()).append(".png?v=6' width='64' height='64' alt=''>")
			.append("<div class='detail-art-caption'>AUTHENTIC AION 4.8 ITEM</div></div><div class='detail-copy'>")
			.append("<div class='eyebrow'>").append(escape(offer.section().title)).append(" · ")
			.append(escape(offer.quality().name().replace('_', ' '))).append("</div><h2>")
			.append(escape(offer.name())).append("</h2><p class='detail-description'>")
			.append(escape(offer.description())).append("</p><div class='detail-promise'><span>◆</span><div><strong>What arrives</strong>")
			.append("<p>").append(offer.count()).append(offer.count() == 1 ? " item" : " items")
			.append(" in Black Cloud mail for your character.</p></div></div><div class='detail-price'>")
			.append("<span>PRICE IN KINAH</span><strong>").append(String.format("%,d", offer.price())).append("</strong></div>");
		if (availability.isEmpty()) body.append("<a class='purchase-button' href='")
			.append(escape(detailUrl + "&review=1#shop-content")).append("'>Review purchase <span>→</span></a>");
		else body.append("<div class='detail-unavailable'>").append(escape(availability)).append("</div>");
		body.append("<p class='purchase-help'>Payment is taken only after you confirm on the next screen.</p></div></div>")
			.append("<section class='detail-information'><div class='eyebrow'>KNOW YOUR TREASURE</div><h3>Item details</h3>")
			.append("<p>These attributes come from this server's item template. Check your character's requirements before buying.</p>")
			.append("<div class='detail-tooltip'>").append(itemTooltip(offer)).append("</div></section>");
	}

	private static String availabilityMessage(Player player, Offer offer, long balance) {
		if (player.getLevel() < offer.unlockLevel()) return "Unlocks at level " + offer.unlockLevel();
		if (offer.race() != Race.PC_ALL && offer.race() != player.getRace()) return "For the other faction";
		if (offer.gender() != null && offer.gender() != player.getGender()) return "For another character gender";
		if (balance < offer.price()) return "You need more Kinah";
		return "";
	}

	private static String shopUrl(String sessionId, Section section, String query, int pageNumber) {
		return "/shop?session_id=" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8)
			+ "&category=" + section.id + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&page=" + pageNumber;
	}

	private static String itemTooltip(Offer offer) {
		ItemTemplate item = DataManager.ITEM_DATA.getItemTemplate(offer.itemId());
		StringBuilder tip = new StringBuilder("<div class='tip-overline'>ITEM DETAILS <span>")
			.append(escape(offer.quality().name().replace('_', ' '))).append("</span></div><div class='tip-name'>")
			.append(escape(offer.name())).append("</div><div class='tip-facts'>");
		if (item.getItemGroup() == ItemGroup.NONE)
			appendTipFact(tip, "Category", offer.section().title);
		else
			appendTipFact(tip, "Type", item.getItemGroup().name().replace('_', ' '));
		if (item.getLevel() > 0)
			appendTipFact(tip, "Item level", String.valueOf(item.getLevel()));
		appendTipFact(tip, "Shop unlock", "Level " + offer.unlockLevel());
		if (offer.race() != Race.PC_ALL)
			appendTipFact(tip, "Faction", offer.race() == Race.ELYOS ? "Elyos" : "Asmodian");
		if (offer.gender() != null)
			appendTipFact(tip, "Gender", offer.gender().name());
		if (item.getWeaponBoost() > 0)
			appendTipFact(tip, "Weapon boost", "+" + item.getWeaponBoost());
		if (item.getActions() != null) {
			for (var action : item.getActions().getItemActions()) {
				String effect = switch (action.getClass().getSimpleName()) {
					case "SkillUseAction" -> "Applies an item skill";
					case "PackAction" -> "Opens a bundle";
					case "AdoptPetAction" -> "Adopts a pet";
					case "RideAction" -> "Adds a mount";
					case "TitleAddAction" -> "Unlocks a title";
					case "EmotionLearnAction" -> "Learns an emote";
					case "ExpandInventoryAction" -> "Expands storage";
					case "DyeAction" -> "Changes an item's color";
					case "CosmeticItemAction", "RemodelAction" -> "Changes appearance";
					default -> null;
				};
				if (effect != null)
					appendTipFact(tip, "Use", effect);
			}
		}
		if (item.isWeapon()) {
			WeaponStats weapon = item.getWeaponStats();
			if (weapon.getMaxDamage() > 0)
				appendTipFact(tip, "Weapon damage", weapon.getMinDamage() + "–" + weapon.getMaxDamage());
			if (weapon.getPhysicalAccuracy() > 0)
				appendTipFact(tip, "Accuracy", String.valueOf(weapon.getPhysicalAccuracy()));
			if (weapon.getCritical() > 0)
				appendTipFact(tip, "Critical", String.valueOf(weapon.getCritical()));
		}
		if (item.getManastoneSlots() > 0)
			appendTipFact(tip, "Manastone slots", String.valueOf(item.getManastoneSlots()));
		if (item.getModifiers() != null) {
			for (StatFunction modifier : item.getModifiers()) {
				if (modifier.getName() == null)
					continue;
				String value = (modifier.getValue() > 0 ? "+" : "") + modifier.getValue()
					+ (modifier instanceof StatRateFunction ? "%" : "");
				appendTipFact(tip, modifier.getName().name().replace('_', ' '), value);
			}
		}
		appendTipFact(tip, "Quantity", String.format("%,d", offer.count()));
		appendTipFact(tip, "Price", String.format("%,d Kinah", offer.price()));
		tip.append("</div><div class='tip-description'>").append(escape(offer.description())).append("</div>");
		return tip.toString();
	}

	private static void appendTipFact(StringBuilder tip, String label, String value) {
		tip.append("<div class='tip-row'><span>").append(escape(label)).append("</span><strong>")
			.append(escape(value)).append("</strong></div>");
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private static String page(String body) {
		return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
		+ "<title>Black Cloud Marketplace</title><link rel='stylesheet' href='/shop/media/marketplace.css?v=11'>"
			+ "<script>(function(){var c=window.CSS;if(!c||!c.supports||!c.supports('display','grid'))document.documentElement.className+=' legacy';})();</script>"
			+ "<script>function shopConfirm(form){var b=form.getElementsByTagName('button')[0];if(!b||b.disabled)return false;b.disabled=true;b.innerHTML='Processing purchase...';return true;}</script>"
			+ "<script>window.addEventListener('load',function(){var n=document.querySelector('.side-nav');if(!n||n.scrollWidth<=n.clientWidth)return;var a=n.querySelector('.side-link.active');if(a)n.scrollLeft+=a.getBoundingClientRect().left-n.getBoundingClientRect().left;});</script>"
			+ "</head><body>" + body + "</body></html>";
	}

	private static void reply(HttpExchange exchange, int status, String html) throws IOException {
		byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
	}

	private enum Section {
		ALL("all", "All collections", "Shop the current Black Cloud collection with Kinah.", "Marketplace"),
		FEATURED("featured", "Featured", "Handpicked favorites from across the shop.", "Discover"),
		FASHION("fashion", "Fashion & dyes", "Costumes, headpieces, colors, and hairstyles.", "Appearance"),
		FASHION_OUTFITS("fashion_outfits", "Outfits & pieces", "Costumes and armor pieces for a memorable look.", "Appearance"),
		FASHION_HEADWEAR("fashion_headwear", "Headwear & accessories", "Hats, hoods, and character accessories.", "Appearance"),
		FASHION_DYES("fashion_dyes", "Dyes", "Colors for compatible gear and costumes.", "Appearance"),
		FASHION_HAIR("fashion_hair", "Hairstyles", "Hair change tickets for a fresh look.", "Appearance"),
		WEAPON_LOOKS("weapon_looks", "Weapon looks", "Signature appearances for your weapons.", "Appearance"),
		WEAPON_MELEE("weapon_melee", "Melee & shields", "Blades, heavy weapons, staves, and shields.", "Appearance"),
		WEAPON_RANGED("weapon_ranged", "Ranged looks", "Bows, pistols, and aethercannons.", "Appearance"),
		WEAPON_ARCANE("weapon_arcane", "Arcane looks", "Orbs, cipher-blades, harps, and spellbooks.", "Appearance"),
		EQUIPMENT("equipment", "Equipment", "Sauro weapons and armor for level 65 characters.", "Adventure"),
		EQUIPMENT_WEAPONS("equipment_weapons", "Weapons & shields", "Sauro weapons and shields for combat.", "Adventure"),
		EQUIPMENT_ARMOR("equipment_armor", "Armor sets", "Sauro armor pieces for different classes.", "Adventure"),
		WINGS("wings", "Wings", "Wings and wing appearances for every Daeva.", "Appearance"),
		WING_PERMANENT("wing_permanent", "Permanent wings", "Wings for your character's wing slot, from classic feathers to rare designs.", "Appearance"),
		WING_SKINS("wing_skins", "Wing skins & timed", "Wing appearances and limited duration styles. Check each item's duration before buying.", "Appearance"),
		COMPANIONS("companions", "Pets & mounts", "Travel and adventure with a new companion.", "Collection"),
		PETS("pets", "Pets", "Eggs that unlock a pet companion.", "Collection"),
		MOUNTS("mounts", "Mounts", "Rideable companions for travel across Atreia.", "Collection"),
		COLLECTIBLES("collectibles", "Emotes & titles", "Expressions and titles to make your character yours.", "Collection"),
		EMOTES("emotes", "Emotes & dances", "Emotion cards for expressions and dances.", "Collection"),
		TITLES("titles", "Titles", "Cards that add a title to your character.", "Collection"),
		CONVENIENCE("convenience", "Convenience", "Practical services, extra space, travel, and more time for adventure.", "Adventure"),
		STORAGE("storage", "Storage expansion", "Cube and warehouse tickets for the next storage tier.", "Adventure"),
		ENTRY_SCROLLS("entry_scrolls", "Bonus entry scrolls", "Clear a matching instance entry cooldown and head back in.", "Adventure"),
		CHARACTER_SERVICES("character_services", "Character services", "Appearance, name, house, and account convenience items.", "Adventure"),
		SUMMONED_NPCS("summoned_npcs", "Summon NPC services", "Bring a trade broker, merchant, or warehouse manager to your location for five minutes.", "Adventure"),
		TRAVEL("travel", "Travel & return", "Instant return scrolls for quick travel across Atreia.", "Adventure"),
		ESSENTIALS("essentials", "Adventure supplies", "Food and recovery items for the road.", "Adventure"),
		FOOD_DRINK("food_drink", "Meals & drinks", "Prepared food and drinks for your journey.", "Adventure"),
		TRANSFORMATION_CANDY("transformation_candy", "Transformation candy", "Candies that change your form and grant their listed effects.", "Adventure"),
		RECOVERY("recovery", "Potions & revival", "Health, mana, and revival consumables.", "Adventure"),
		SUPPLY_SCROLLS("supply_scrolls", "Scrolls & supplies", "Travel and combat scrolls, stones, and shards.", "Adventure"),
		UPGRADES("upgrades", "Gear upgrades", "Stones, shards, and supplements for your equipment.", "Adventure"),
		UPGRADE_STONES("upgrade_stones", "Enchantment stones", "Browse stones by level to enchant compatible equipment.", "Adventure"),
		MANASTONES("manastones", "Manastones", "Socket compatible equipment with the attributes you need.", "Adventure"),
		MANASTONE_PHYSICAL("manastone_physical", "Physical offense", "Attack, accuracy, and critical strike manastones.", "Adventure"),
		MANASTONE_MAGIC("manastone_magic", "Magic & healing", "Magic Boost, Magical Accuracy, and healing manastones.", "Adventure"),
		MANASTONE_VITALITY("manastone_vitality", "HP, MP & flight", "Vitality manastones, including dual-stat varieties.", "Adventure"),
		MANASTONE_DEFENSE("manastone_defense", "Defense & resistance", "Block, evasion, parry, and resistance manastones.", "Adventure"),
		MANASTONE_ANCIENT("manastone_ancient", "Ancient manastones", "Stones for compatible ancient manastone slots.", "Adventure"),
		ENCHANT_SUPPLEMENTS("enchant_supplements", "Enchanting supplements", "Choose a supplement for the target gear quality.", "Adventure"),
		SOCKETING_AIDS("socketing_aids", "Socketing aids", "Supplements for compatible manastone socketing attempts.", "Adventure"),
		UPGRADE_SHARDS("upgrade_shards", "Power & stigma shards", "Shard supplies for combat and stigma use.", "Adventure"),
		UPGRADE_TOOLS("upgrade_tools", "Extraction tools", "Tools used when extracting from equipment.", "Adventure"),
		BUNDLES("bundles", "Bundles & gifts", "Ready-to-open boxes and travel bundles.", "Discover"),
		GIFT_BOXES("gift_boxes", "Gift boxes", "Open boxes for their server-defined contents.", "Discover"),
		TRAVEL_BUNDLES("travel_bundles", "Travel bundles", "Bundles that contain travel supplies.", "Discover");

		private final String id;
		private final String title;
		private final String description;
		private final String group;

		Section(String id, String title, String description, String group) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.group = group;
		}

		private static Section find(String id) {
			for (Section section : values()) {
				if (section.id.equals(id))
					return section;
			}
			return null;
		}

		private static Section selected(String id) {
			Section section = find(id);
			return section != null ? section : FEATURED;
		}

		private Section parent() {
			return switch (this) {
				case FASHION_OUTFITS, FASHION_HEADWEAR, FASHION_DYES, FASHION_HAIR -> FASHION;
				case WEAPON_MELEE, WEAPON_RANGED, WEAPON_ARCANE -> WEAPON_LOOKS;
				case EQUIPMENT_WEAPONS, EQUIPMENT_ARMOR -> EQUIPMENT;
				case WING_PERMANENT, WING_SKINS -> WINGS;
				case PETS, MOUNTS -> COMPANIONS;
				case EMOTES, TITLES -> COLLECTIBLES;
				case STORAGE, ENTRY_SCROLLS, CHARACTER_SERVICES, SUMMONED_NPCS, TRAVEL -> CONVENIENCE;
				case FOOD_DRINK, TRANSFORMATION_CANDY, RECOVERY, SUPPLY_SCROLLS -> ESSENTIALS;
				case UPGRADE_STONES, MANASTONES, ENCHANT_SUPPLEMENTS, SOCKETING_AIDS, UPGRADE_SHARDS, UPGRADE_TOOLS -> UPGRADES;
				case MANASTONE_PHYSICAL, MANASTONE_MAGIC, MANASTONE_VITALITY, MANASTONE_DEFENSE, MANASTONE_ANCIENT -> MANASTONES;
				case GIFT_BOXES, TRAVEL_BUNDLES -> BUNDLES;
				default -> null;
			};
		}

		private boolean isChild() {
			return parent() != null;
		}

		private boolean isGrandchild() {
			return parent() != null && parent().parent() != null;
		}

		private boolean hasChildren() {
			for (Section section : values()) if (section.parent() == this) return true;
			return false;
		}

		private boolean includes(Section offerSection) {
			if (this == ALL)
				return true;
			for (Section current = offerSection; current != null; current = current.parent()) {
				if (current == this)
					return true;
			}
			return false;
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

	private enum PurchaseStatus {
		SUCCESS, FAILED, UNKNOWN
	}

	private record PurchaseResult(PurchaseStatus status, Offer offer, String message) {
	}

	private record StoredReceipt(int playerId, PurchaseResult result, long expires) {
	}
}
