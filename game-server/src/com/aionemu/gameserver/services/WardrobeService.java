package com.aionemu.gameserver.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.commons.utils.Json;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_UPDATE_PLAYER_APPEARANCE;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemDeleteType;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Permanent account appearances. Unlocks and ticket consumption commit together. */
public final class WardrobeService {

	private static final Logger log = LoggerFactory.getLogger(WardrobeService.class);
	private static volatile List<Entry> catalog = List.of();
	private static volatile Map<Integer,ItemTemplate> byId = Map.of();
	private static volatile Map<Integer,Entry> entries = Map.of();
	private static final Map<String,List<Entry>> permittedCatalog = new ConcurrentHashMap<>();
	private static final Map<Integer,CollectionRead> collectionReads = new ConcurrentHashMap<>();
	private record CollectionRead(Object connection, long expires, Set<Integer> unlocked, Palette palette, List<Map<String,Object>> outfits) {}
	private static volatile boolean ready;
	private record Entry(ItemTemplate template, String search, String category) {}
	/** One palette color, shown with the native dye item that represents it. */
	private record Dye(int color, ItemTemplate item, String name) {}
	private static volatile Map<Integer,Dye> dyes = Map.of();
	private static volatile Map<Integer,Integer> dyeItems = Map.of();
	/** Account dye palette. A Season Pass palette opens every color without registrations. */
	record Palette(boolean full, Set<Integer> registered) {
		boolean has(Integer color) { return color != null && dyes.containsKey(color) && (full || registered.contains(color)); }
	}
	private WardrobeService() {}

	public static void start() throws Exception {
		try (Connection c = DatabaseFactory.getConnection(); Statement s = c.createStatement()) {
			for (String sql : Files.readString(Path.of("config/wardrobe/schema.sql")).split(";"))
				if (!sql.isBlank()) s.execute(sql);
			try (ResultSet r = s.executeQuery("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND (TABLE_NAME='inventory' OR TABLE_NAME LIKE 'wardrobe_%')")) {
				while (r.next()) if (!"InnoDB".equalsIgnoreCase(r.getString(2)))
					throw new SQLException("Wardrobe requires InnoDB: " + r.getString(1));
			}
		}
		catalog = DataManager.ITEM_DATA.getItemTemplates().stream()
			.filter(t -> WardrobeRules.eligible(t) && CentralMarketHttpService.hasClientItem(t.getTemplateId()))
			.sorted(Comparator.comparing(ItemTemplate::getName).thenComparingInt(ItemTemplate::getTemplateId))
			.map(t -> new Entry(t,t.getName().toLowerCase(Locale.ROOT),WardrobeRules.category(t))).toList();
		byId = catalog.stream().collect(Collectors.toUnmodifiableMap(e -> e.template().getTemplateId(),Entry::template));
		entries = catalog.stream().collect(Collectors.toUnmodifiableMap(e -> e.template().getTemplateId(), e -> e));
		Map<Integer,Integer> items = new HashMap<>(); Map<Integer,ItemTemplate> shown = new HashMap<>();
		for (ItemTemplate t : DataManager.ITEM_DATA.getItemTemplates()) {
			Integer color = WardrobeRules.paletteColor(t);
			if (color == null || !CentralMarketHttpService.hasClientItem(t.getTemplateId())) continue;
			items.put(t.getTemplateId(),color);
			// Prefer the plain native dye name over event and reward editions of the same color.
			shown.merge(color,t,(a,b) -> rank(b) < rank(a) || rank(b) == rank(a) && b.getTemplateId() < a.getTemplateId() ? b : a);
		}
		Map<Integer,Dye> palette = new LinkedHashMap<>();
		shown.values().stream().sorted(Comparator.comparingDouble((ItemTemplate t) -> hue(items.get(t.getTemplateId())))
			.thenComparingInt(t -> items.get(t.getTemplateId()))).forEach(t -> {
				int color = items.get(t.getTemplateId());
				palette.put(color,new Dye(color,t,t.getName().replaceFirst("^\\[[^\\]]*\\]\\s*","").replaceFirst("^Dye:\\s*","")));
			});
		dyes = Collections.unmodifiableMap(palette); dyeItems = Map.copyOf(items);
		permittedCatalog.clear(); collectionReads.clear();
		if (DataManager.ITEM_DATA.getItemTemplate(WardrobeRules.UNLOCK_ITEM) == null)
			throw new IllegalStateException("Appearance Unlock item template is missing.");
		if (DataManager.ITEM_DATA.getItemTemplate(WardrobeRules.PALETTE_ITEM) == null)
			throw new IllegalStateException("Aether Palette item template is missing.");
		ready = true;
		log.info("Wardrobe ready: {} permanent appearances, {} palette dyes; account collection enabled", catalog.size(), dyes.size());
	}

	private static int rank(ItemTemplate t) { return t.getName().startsWith("[") ? 2 : t.getName().startsWith("Dye:") ? 0 : 1; }

	/** Hue order with grays first keeps the palette readable like a color wheel. */
	private static double hue(int rgb) {
		double r = (rgb >> 16 & 255) / 255d, g = (rgb >> 8 & 255) / 255d, b = (rgb & 255) / 255d;
		double max = Math.max(r,Math.max(g,b)), min = Math.min(r,Math.min(g,b)), d = max-min;
		if (d < 0.08) return -1 + max/2;
		double h = max == r ? ((g-b)/d + 6) % 6 : max == g ? (b-r)/d + 2 : (r-g)/d + 4;
		return h + (1-max) * 0.01;
	}

	static Palette palette(Connection c, int account) throws SQLException {
		boolean full = row(c,"SELECT account_id FROM wardrobe_palettes WHERE account_id=?",account) != null;
		return new Palette(full,rows(c,"SELECT color FROM wardrobe_dyes WHERE account_id=?",account).stream()
			.map(r -> (int)number(r,"color")).collect(Collectors.toSet()));
	}

	private static void available(Player p) {
		if (!ready) throw new IllegalStateException("Wardrobe is unavailable.");
		if (!p.isOnline() || World.getInstance().getPlayer(p.getObjectId()) != p || p.getClientConnection() == null)
			throw new IllegalArgumentException("Log in, then reopen Wardrobe.");
	}

	private static List<Item> equipment(Player p) {
		List<Item> items = new ArrayList<>(p.getEquipment().getEquippedItems());
		items.addAll(p.getInventory().getItems());
		return items.stream().filter(i -> WardrobeRules.eligible(i.getItemTemplate())
			&& i.getExpireTime() == 0 && i.getPendingTuneResult() == null).toList();
	}

	static Set<Integer> unlocked(Connection c, int account) throws SQLException {
		return rows(c,"SELECT item_id FROM wardrobe_skins WHERE account_id=?",account).stream()
			.map(r -> (int)number(r,"item_id")).collect(Collectors.toSet());
	}

	public static Map<String,Object> snapshot(Player p, Map<String,String> args) throws Exception {
		available(p);
		Object guard = p.getClientConnection();
		if (guard == null) throw new IllegalArgumentException("Log in, then reopen Wardrobe.");
		synchronized (guard) {
			available(p);
			try (Connection c = DatabaseFactory.getConnection()) {
				int account = p.getAccount().getId();
				long now = System.currentTimeMillis();
				CollectionRead collection = collectionReads.get(account);
				if (collection == null || collection.connection() != guard || collection.expires() < now || args.containsKey("refresh")) {
					if (collectionReads.size() > 1024) collectionReads.entrySet().removeIf(e -> e.getValue().expires() < now);
					collection = new CollectionRead(guard, now+3000, unlocked(c,account), palette(c,account),
						rows(c,"SELECT o.name,o.skins_json,o.updated_at,COALESCE(d.dyes_json,'{}') dyes_json FROM wardrobe_outfits o"
							+ " LEFT JOIN wardrobe_outfit_dyes d ON d.account_id=o.account_id AND d.name=o.name WHERE o.account_id=? ORDER BY o.updated_at DESC",account));
					collectionReads.put(account,collection);
				}
				Set<Integer> unlocked = collection.unlocked();
				List<Item> equipment = equipment(p);
				Map<Integer,List<Item>> sources = equipment.stream().filter(i -> byId.containsKey(i.getItemSkinTemplate().getTemplateId()))
					.collect(Collectors.groupingBy(i -> i.getItemSkinTemplate().getTemplateId()));
				String query = args.getOrDefault("q","").strip().toLowerCase(Locale.ROOT);
				if (query.length() > 60) query = query.substring(0,60);
				final String term = query;
				String category = args.getOrDefault("category","All"), filter = args.getOrDefault("filter","unlocked");
				int targetId = integer(args,"target",0);
				Item target = equipment.stream().filter(i -> i.getObjectId() == targetId).findFirst().orElse(null);
				List<Entry> allowed = permittedCatalog.computeIfAbsent(p.getRace()+":"+p.getGender(),
					key -> catalog.stream().filter(e -> WardrobeRules.permitted(e.template(),p.getRace(),p.getGender())).toList());
				// Owned/unlocked collections are small. Do not walk the complete client catalog for these views.
				Collection<Entry> candidates = filter.equals("unlocked") || filter.equals("owned")
					? (filter.equals("unlocked") ? unlocked : sources.keySet()).stream().map(entries::get).filter(Objects::nonNull)
						.sorted(Comparator.comparing((Entry e) -> e.template().getName()).thenComparingInt(e -> e.template().getTemplateId())).toList()
					: allowed;
				List<Entry> matches = candidates.stream().filter(e -> e.search().contains(term))
					.filter(e -> category.equals("All") || e.category().equals(category))
					.filter(e -> WardrobeRules.permitted(e.template(),p.getRace(),p.getGender()))
					.filter(e -> target == null || WardrobeRules.compatible(target.getItemTemplate(),e.template()))
					.filter(e -> switch (filter) {
						case "unlocked" -> unlocked.contains(e.template().getTemplateId());
						case "owned" -> sources.containsKey(e.template().getTemplateId()) && !unlocked.contains(e.template().getTemplateId());
						case "locked" -> !unlocked.contains(e.template().getTemplateId());
						default -> true;
					}).toList();
				int pages = Math.max(1,(matches.size()+35)/36), page = Math.min(pages,Math.max(1,integer(args,"page",1)));
				List<Map<String,Object>> skins = matches.stream().skip((page-1)*36L).limit(36)
					.map(e -> skinView(e.template(),unlocked,sources)).toList();
				Map<String,Object> state = new LinkedHashMap<>();
				state.put("character",p.getName()); state.put("unlocked",unlocked.stream().map(byId::get).filter(Objects::nonNull)
					.filter(t -> WardrobeRules.permitted(t,p.getRace(),p.getGender())).count());
				state.put("total",allowed.size());
				state.put("tickets",p.getInventory().getItemCountByItemId(WardrobeRules.UNLOCK_ITEM));
				state.put("freeUnlock",AetherfallBoonService.has(p,AetherfallBoonService.Boon.SHUGO_TRADE_CHARTER));
				state.put("ticketItem",WardrobeRules.UNLOCK_ITEM); state.put("categories",List.of("All","Weapons","Armor","Costumes","Headwear","Shields","Wings"));
				state.put("equipment",equipment.stream().map(WardrobeService::itemView).toList());
				state.put("skins",skins); state.put("results",matches.size()); state.put("page",page); state.put("pages",pages);
				state.put("target",target == null ? 0 : targetId);
				state.put("outfits",collection.outfits());
				Palette palette = collection.palette();
				Map<Integer,List<Item>> dyeSources = p.getInventory().getItems().stream().filter(i -> dyeItems.containsKey(i.getItemId())
					&& i.getExpireTime() == 0).collect(Collectors.groupingBy(i -> dyeItems.get(i.getItemId())));
				state.put("palette",palette.full());
				state.put("paletteItems",p.getInventory().getItemCountByItemId(WardrobeRules.PALETTE_ITEM));
				state.put("dyesUnlocked",dyes.keySet().stream().filter(palette::has).count()); state.put("dyesTotal",dyes.size());
				state.put("dyes",dyes.values().stream().map(d -> {
					Map<String,Object> v = new LinkedHashMap<>();
					v.put("color",d.color()); v.put("hex",String.format("%06x",d.color())); v.put("name",d.name());
					v.put("item",d.item().getTemplateId()); v.put("unlocked",palette.has(d.color()));
					v.put("sources",dyeSources.getOrDefault(d.color(),List.of()).stream()
						.map(i -> Map.of("object",i.getObjectId(),"name",i.getItemName(),"count",i.getItemCount())).toList());
					return v;
				}).toList());
				Set<Integer> outfitIds = new HashSet<>();
				for (var outfit : collection.outfits()) {
					Map<String,Integer> saved = Json.read(outfit.get("skins_json").toString(),new com.google.gson.reflect.TypeToken<Map<String,Integer>>() {});
					if (saved != null) outfitIds.addAll(saved.values());
				}
				state.put("outfitAppearances",outfitIds.stream().map(byId::get).filter(Objects::nonNull)
					.filter(t -> WardrobeRules.permitted(t,p.getRace(),p.getGender())).map(t -> skinView(t,unlocked,sources)).toList());
				int selected = integer(args,"skin",0);
				ItemTemplate skin = byId.get(selected);
				if (skin != null && WardrobeRules.permitted(skin,p.getRace(),p.getGender()))
				{
					Map<String,Object> selectedView = skinView(skin,unlocked,sources);
					selectedView.put("compatible",target != null && WardrobeRules.compatible(target.getItemTemplate(),skin));
					state.put("selected",selectedView);
				}
				return state;
			}
		}
	}

	private static Map<String,Object> skinView(ItemTemplate t, Set<Integer> unlocked, Map<Integer,List<Item>> sources) {
		Map<String,Object> v = new LinkedHashMap<>();
		v.put("item",t.getTemplateId()); v.put("name",t.getName()); v.put("category",WardrobeRules.category(t));
		v.put("type",WardrobeRules.typeName(t)); v.put("quality",t.getItemQuality().name());
		v.put("unlocked",unlocked.contains(t.getTemplateId()));
		v.put("group",t.getItemGroup().name()); v.put("slots",t.getItemGroup().getValidEquipmentSlots());
		v.put("sources",sources.getOrDefault(t.getTemplateId(),List.of()).stream()
			.map(i -> Map.of("object",i.getObjectId(),"name",i.getItemName(),"equipped",i.isEquipped())).toList());
		return v;
	}

	private static Map<String,Object> itemView(Item i) {
		Map<String,Object> result = new LinkedHashMap<>(Map.of("object",i.getObjectId(),"item",i.getItemId(),"skin",i.getItemSkinTemplate().getTemplateId(),
			"name",i.getItemName(),"equipped",i.isEquipped(),"slot",i.getEquipmentSlot(),"type",WardrobeRules.typeName(i.getItemTemplate()),
			"tooltip","item="+i.getItemId()+"&count=1&enchant_count="+i.getEnchantLevel()+"&authorize_count="+i.getTempering()));
		result.put("group",i.getItemTemplate().getItemGroup().name());
		result.put("slots",i.getItemTemplate().getItemGroup().getValidEquipmentSlots());
		result.put("color",i.getItemColor() == null ? -1 : i.getItemColor());
		result.put("dyeable",i.getItemSkinTemplate().isItemDyePermitted());
		return result;
	}

	public static String action(Player p, Map<String,String> args, String request) throws Exception {
		available(p);
		Object guard = p.getClientConnection();
		if (guard == null) throw new IllegalArgumentException("Log in, then reopen Wardrobe.");
		synchronized (guard) {
			available(p);
			if (p.isDead() || p.isTrading() || GameServer.isShuttingDownSoon())
				throw new IllegalArgumentException("Wardrobe is unavailable during trade, death or shutdown.");
			if (!InventoryDAO.store(p)) throw new SQLException("Inventory save failed.");
			List<Runnable> committed = new ArrayList<>();
			try (Connection c = DatabaseFactory.getConnection()) {
				c.setAutoCommit(false);
				boolean saved = false;
				try {
					int account = p.getAccount().getId();
					update(c,"INSERT IGNORE INTO wardrobe_accounts VALUES(?)",account);
					row(c,"SELECT account_id FROM wardrobe_accounts WHERE account_id=? FOR UPDATE",account);
					Map<String,Object> receipt = row(c,"SELECT result FROM wardrobe_requests WHERE request_id=? AND account_id=?",request,account);
					if (receipt != null) return receipt.get("result").toString();
					String result = switch (args.getOrDefault("action","")) {
						case "unlock" -> unlock(c,p,args,committed);
						case "registerDye" -> registerDye(c,p,args,committed);
						case "unlockPalette" -> unlockPalette(c,p,committed);
						case "apply" -> apply(c,p,args,committed);
						case "applyChanges" -> applyChanges(c,p,args,committed);
						case "restore" -> restore(c,p,args,committed);
						case "saveOutfit" -> saveOutfit(c,p,args);
						case "applyOutfit" -> applyOutfit(c,p,args,committed);
						case "deleteOutfit" -> deleteOutfit(c,account,args);
						default -> throw new IllegalArgumentException("Select a Wardrobe action.");
					};
					update(c,"DELETE FROM wardrobe_requests WHERE created_at<?",System.currentTimeMillis()-86400000);
					update(c,"INSERT INTO wardrobe_requests VALUES(?,?,?,?)",request,account,result,System.currentTimeMillis());
					c.commit(); saved = true;
					for (Runnable change : committed) change.run();
					collectionReads.remove(account);
					if (Set.of("apply","restore","applyChanges","applyOutfit").contains(args.get("action")) && !committed.isEmpty())
						PacketSendUtility.broadcastPacket(p,new SM_UPDATE_PLAYER_APPEARANCE(p.getObjectId(),p.getEquipment().getEquippedForAppearance()),true);
					return result;
				} catch (Exception e) {
					if (!saved) c.rollback();
					else {
						InventoryDAO.quarantineMarketInventory(p.getObjectId());
						log.error("Committed Wardrobe update could not refresh {}. Inventory saves blocked until login.",p,e);
						p.getClientConnection().close();
					}
					throw e;
				}
			}
		}
	}

	private static Item target(Player p, Map<String,String> args) {
		int id = integer(args,"target",0);
		return equipment(p).stream().filter(i -> i.getObjectId() == id).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Select compatible equipment in Inventory or equipped slots."));
	}

	private static ItemTemplate skin(Player p, Map<String,String> args) {
		ItemTemplate skin = byId.get(integer(args,"skin",0));
		if (skin == null || !WardrobeRules.permitted(skin,p.getRace(),p.getGender()))
			throw new IllegalArgumentException("This appearance is unavailable for your character.");
		return skin;
	}

	private static String unlock(Connection c, Player p, Map<String,String> args, List<Runnable> committed) throws SQLException {
		ItemTemplate skin = skin(p,args);
		int account = p.getAccount().getId();
		if (unlocked(c,account).contains(skin.getTemplateId())) return "Appearance already unlocked.";
		int sourceId = integer(args,"source",0);
		Item source = equipment(p).stream().filter(i -> i.getObjectId() == sourceId
			&& i.getItemSkinTemplate().getTemplateId() == skin.getTemplateId()).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Keep the appearance item in Inventory or equipped slots to unlock it."));
		lockItem(c,p,source);
		if (AetherfallBoonService.has(p,AetherfallBoonService.Boon.SHUGO_TRADE_CHARTER)) { // Shugo Trade Charter: unlocks cost no ticket
			if (update(c,"INSERT IGNORE INTO wardrobe_skins VALUES(?,?,?,?)",account,skin.getTemplateId(),System.currentTimeMillis(),p.getObjectId()) != 1)
				throw new IllegalArgumentException("Appearance already unlocked.");
			return "Unlocked " + skin.getName() + " with your Shugo Trade Charter. Equipment kept.";
		}
		Item ticket = p.getInventory().getItemsByItemId(WardrobeRules.UNLOCK_ITEM).stream().filter(i -> i.getItemCount() > 0).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("You need one Appearance Unlock item."));
		lockItem(c,p,ticket);
		commitUnlock(c,account,skin.getTemplateId(),p.getObjectId(),ticket.getObjectId(),ticket.getItemCount());
		useOne(p,ticket,committed);
		return "Unlocked " + skin.getName() + ". Equipment kept.";
	}

	/** Applies the committed removal of one consumed item to the live inventory. */
	private static void useOne(Player p,Item used,List<Runnable> committed) {
		long remaining = used.getItemCount()-1;
		committed.add(() -> {
			used.setItemCount(remaining); used.setPersistentState(PersistentState.UPDATED);
			if (remaining == 0) {
				p.getInventory().remove(used);
				ItemPacketService.sendItemDeletePacket(p,StorageType.CUBE,used,ItemDeleteType.USE);
				IDFactory.getInstance().releaseId(used.getObjectId());
			} else ItemPacketService.sendItemUpdatePacket(p,StorageType.CUBE,used,ItemUpdateType.DEC_ITEM_USE);
		});
	}

	static void consume(Connection c,int character,int object,int itemId,long count,String missing) throws SQLException {
		if (count < 1) throw new IllegalArgumentException(missing);
		int changed = count == 1
			? update(c,"DELETE FROM inventory WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=0 AND is_equipped=0 AND item_count=1",object,character,itemId)
			: update(c,"UPDATE inventory SET item_count=item_count-1 WHERE item_unique_id=? AND item_owner=? AND item_id=? AND item_location=0 AND is_equipped=0 AND item_count=?",object,character,itemId,count);
		if (changed != 1) throw new IllegalArgumentException(missing + " Refresh Wardrobe.");
	}

	private static String registerDye(Connection c,Player p,Map<String,String> args,List<Runnable> committed) throws SQLException {
		int sourceId = integer(args,"source",0), account = p.getAccount().getId();
		Item source = p.getInventory().getItems().stream().filter(i -> i.getObjectId() == sourceId && dyeItems.containsKey(i.getItemId())
			&& i.getExpireTime() == 0).findFirst().orElseThrow(() -> new IllegalArgumentException("Keep a permanent dye in your Inventory to register it."));
		Dye dye = dyes.get(dyeItems.get(source.getItemId()));
		if (palette(c,account).has(dye.color())) return dye.name() + " is already in your palette. The dye was kept.";
		lockItem(c,p,source);
		if (update(c,"INSERT IGNORE INTO wardrobe_dyes VALUES(?,?,?,?,?)",account,dye.color(),source.getItemId(),System.currentTimeMillis(),p.getObjectId()) != 1)
			throw new IllegalArgumentException("This dye is already registered.");
		consume(c,p.getObjectId(),source.getObjectId(),source.getItemId(),source.getItemCount(),"The dye changed.");
		useOne(p,source,committed);
		return "Registered " + dye.name() + " to your account palette.";
	}

	private static String unlockPalette(Connection c,Player p,List<Runnable> committed) throws SQLException {
		int account = p.getAccount().getId();
		if (palette(c,account).full()) return "Your account palette is already complete. The Aether Palette was kept.";
		Item palette = p.getInventory().getItemsByItemId(WardrobeRules.PALETTE_ITEM).stream().filter(i -> i.getItemCount() > 0).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("You need an Aether Palette in your Inventory."));
		lockItem(c,p,palette);
		if (update(c,"INSERT IGNORE INTO wardrobe_palettes VALUES(?,?,?)",account,System.currentTimeMillis(),p.getObjectId()) != 1)
			throw new IllegalArgumentException("Your account palette is already complete.");
		consume(c,p.getObjectId(),palette.getObjectId(),WardrobeRules.PALETTE_ITEM,palette.getItemCount(),"The Aether Palette changed.");
		useOne(p,palette,committed);
		return "Every palette color is now unlocked for your account.";
	}

	static void commitUnlock(Connection c,int account,int skin,int character,int ticket,long count) throws SQLException {
		if (count < 1) throw new IllegalArgumentException("You need one Appearance Unlock item.");
		if (update(c,"INSERT IGNORE INTO wardrobe_skins VALUES(?,?,?,?)",account,skin,System.currentTimeMillis(),character) != 1)
			throw new IllegalArgumentException("Appearance already unlocked.");
		consume(c,character,ticket,WardrobeRules.UNLOCK_ITEM,count,"Appearance Unlock item changed.");
	}

	private static String apply(Connection c,Player p,Map<String,String> args,List<Runnable> committed) throws SQLException {
		Item target = target(p,args); ItemTemplate skin = skin(p,args);
		if (!unlocked(c,p.getAccount().getId()).contains(skin.getTemplateId()))
			throw new IllegalArgumentException("Unlock this appearance first.");
		if (!WardrobeRules.compatible(target.getItemTemplate(),skin))
			throw new IllegalArgumentException("This appearance does not match the selected equipment.");
		changeSkin(c,p,target,skin,committed);
		return "Applied " + skin.getName() + ".";
	}

	private static String restore(Connection c,Player p,Map<String,String> args,List<Runnable> committed) throws SQLException {
		Item target = target(p,args); changeSkin(c,p,target,target.getItemTemplate(),committed);
		return "Restored the original appearance.";
	}

	private static String applyChanges(Connection c,Player p,Map<String,String> args,List<Runnable> committed) throws SQLException {
		List<Map<String,Integer>> changes;
		try { changes = Json.read(args.getOrDefault("changes",""), new com.google.gson.reflect.TypeToken<List<Map<String,Integer>>>() {}); }
		catch (Exception e) { throw new IllegalArgumentException("Invalid appearance changes."); }
		if (changes == null || changes.isEmpty() || changes.size() > 16) throw new IllegalArgumentException("Select up to 16 equipment appearances.");
		Set<Integer> owned = unlocked(c,p.getAccount().getId()), seen = new HashSet<>();
		Palette palette = palette(c,p.getAccount().getId());
		List<Item> equipment = equipment(p);
		Map<Item,ItemTemplate> validated = new LinkedHashMap<>();
		Map<Item,Integer> colors = new LinkedHashMap<>();
		for (Map<String,Integer> change : changes) {
			if (change == null) throw new IllegalArgumentException("Invalid appearance changes.");
			Integer object = change.get("object"), skinId = change.get("skin"), expected = change.get("expected");
			Integer color = change.get("color"), expectedColor = change.get("expectedColor");
			// A change carries a skin, a palette dye (-1 removes the dye), or both.
			if (object == null || skinId == null && color == null || skinId != null && expected == null
				|| color != null && (expectedColor == null || color < -1 || color > 0xFFFFFF) || !seen.add(object))
				throw new IllegalArgumentException("Invalid appearance changes.");
			Item item = equipment.stream().filter(i -> i.getObjectId() == object).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Selected equipment is no longer available. Refresh Wardrobe."));
			if (color != null) {
				int current = item.getItemColor() == null ? -1 : item.getItemColor();
				if (current != expectedColor && current != color) throw new IllegalArgumentException(item.getItemName() + ": dye changed. Refresh Wardrobe.");
				if (color != -1 && !palette.has(color)) throw new IllegalArgumentException("Register this dye to your palette before applying it.");
				colors.put(item,color);
			}
			if (skinId == null) continue;
			ItemTemplate skin = skinId == 0 ? item.getItemTemplate() : byId.get(skinId);
			if (skin == null || skinId != 0 && (!owned.contains(skinId) || !WardrobeRules.permitted(skin,p.getRace(),p.getGender())
				|| !WardrobeRules.compatible(item.getItemTemplate(),skin))) throw new IllegalArgumentException("Unlock a compatible appearance before applying changes.");
			int current = item.getItemSkinTemplate().getTemplateId();
			// A stale draft that already matches the requested look is harmless. Keep
			// rejecting stale drafts that would overwrite a different current look.
			if (current != expected && current != skin.getTemplateId()) {
				log.warn("Wardrobe stale appearance for item {}: expected={}, current={}, requested={}",object,expected,current,skin.getTemplateId());
				throw new IllegalArgumentException(item.getItemName() + ": appearance changed. Refresh Wardrobe.");
			}
			validated.put(item,skin);
		}
		for (var entry : colors.entrySet())
			if (entry.getValue() != -1 && !validated.getOrDefault(entry.getKey(),entry.getKey().getItemSkinTemplate()).isItemDyePermitted())
				throw new IllegalArgumentException(entry.getKey().getItemName() + " cannot be dyed with this appearance.");
		// All selections are validated before any writes. The surrounding transaction commits the whole outfit together.
		// Dyes are written first: changeSkin's custody check compares the saved skin with the live item.
		int before = committed.size();
		for (var entry : colors.entrySet()) changeColor(c,p,entry.getKey(),entry.getValue(),committed);
		for (var entry : validated.entrySet()) changeSkin(c,p,entry.getKey(),entry.getValue(),committed);
		int changed = committed.size()-before;
		return changed == 0 ? "Selected appearances are already applied." : "Applied " + changed + " appearance changes.";
	}

	private static void changeColor(Connection c,Player p,Item target,int color,List<Runnable> committed) throws SQLException {
		lockItem(c,p,target);
		Integer value = color == -1 ? null : color;
		if (Objects.equals(target.getItemColor(),value) && target.getColorExpireTime() == 0) return;
		try (PreparedStatement statement = c.prepareStatement("UPDATE inventory SET item_color=?,color_expires=0 WHERE item_unique_id=? AND item_owner=? AND item_location=0")) {
			if (value == null) statement.setNull(1,Types.INTEGER); else statement.setInt(1,value);
			statement.setInt(2,target.getObjectId()); statement.setInt(3,p.getObjectId());
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Equipment is no longer available to update. Refresh Wardrobe.");
		}
		committed.add(() -> {
			target.setItemColor(value); target.setColorExpireTime(0); target.setPersistentState(PersistentState.UPDATED);
			ItemPacketService.updateItemAfterInfoChange(p,target);
		});
	}

	private static void lockItem(Connection c,Player p,Item item) throws SQLException {
		Map<String,Object> saved = row(c,"SELECT item_count,item_id,item_skin,is_equipped FROM inventory WHERE item_unique_id=? AND item_owner=? AND item_location=0 FOR UPDATE",item.getObjectId(),p.getObjectId());
		if (saved == null)
			throw new IllegalArgumentException(item.getItemName() + ": no longer available. Refresh Wardrobe.");
		if (number(saved,"item_count") != item.getItemCount() || number(saved,"item_id") != item.getItemId()
			|| (number(saved,"is_equipped") != 0) != item.isEquipped()
			|| number(saved,"item_skin") != item.getItemSkinTemplate().getTemplateId()
				&& !(number(saved,"item_skin") == 0 && !item.isSkinnedItem())) {
			log.warn("Wardrobe saved equipment mismatch for item {}: saved count/id/skin/equipped={}/{}/{}/{}, live={}/{}/{}/{}",
				item.getObjectId(),number(saved,"item_count"),number(saved,"item_id"),number(saved,"item_skin"),number(saved,"is_equipped"),
				item.getItemCount(),item.getItemId(),item.getItemSkinTemplate().getTemplateId(),item.isEquipped());
			throw new IllegalArgumentException(item.getItemName() + ": saved equipment differs. Log in again.");
		}
	}

	private static void changeSkin(Connection c,Player p,Item target,ItemTemplate skin,List<Runnable> committed) throws SQLException {
		lockItem(c,p,target);
		int skinId = skin.getTemplateId();
		// Do not issue an UPDATE for an unchanged appearance: affected-row JDBC
		// settings can report zero for it even though custody is still valid.
		if (target.getItemSkinTemplate().getTemplateId() == skinId) return;
		commitAppearance(c,p.getObjectId(),target.getObjectId(),skinId);
		committed.add(() -> {
			target.setItemSkinTemplate(skin); target.setPersistentState(PersistentState.UPDATED);
			ItemPacketService.updateItemAfterInfoChange(p,target);
		});
	}

	static void commitAppearance(Connection c,int owner,int object,int skin) throws SQLException {
		try (PreparedStatement statement = c.prepareStatement("UPDATE inventory SET item_skin=? WHERE item_unique_id=? AND item_owner=? AND item_location=0")) {
			statement.setInt(1,skin); statement.setInt(2,object); statement.setInt(3,owner);
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Equipment is no longer available to update. Refresh Wardrobe.");
		}
	}

	private static String name(Map<String,String> args) {
		String name = args.getOrDefault("name","").strip();
		if (name.isBlank() || name.length() > 32 || name.codePoints().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException("Enter an outfit name, up to 32 characters.");
		return name;
	}

	private static String saveOutfit(Connection c,Player p,Map<String,String> args) throws SQLException {
		String name = name(args); int account = p.getAccount().getId();
		if (row(c,"SELECT name FROM wardrobe_outfits WHERE account_id=? AND name=?",account,name) == null
			&& rows(c,"SELECT name FROM wardrobe_outfits WHERE account_id=?",account).size() >= 20)
			throw new IllegalArgumentException("You can save up to 20 outfits. Delete an outfit first.");
		Set<Integer> owned = unlocked(c,account);
		Palette palette = palette(c,account);
		Map<String,Integer> skins = new LinkedHashMap<>(), colors = new LinkedHashMap<>();
		boolean skipped = false;
		for (Item i : equipment(p)) if (i.isEquipped()) {
			int skin = i.isSkinnedItem() ? i.getItemSkinTemplate().getTemplateId() : 0;
			if (skin != 0 && !owned.contains(skin)) throw new IllegalArgumentException("Unlock " + i.getItemSkinTemplate().getName() + " before saving this outfit.");
			String slot = Long.toString(i.getEquipmentSlot());
			skins.put(slot,skin);
			// Undyed slots are saved as -1. Dyes outside the palette are left out so outfits cannot copy them for free.
			if (i.getItemColor() == null) colors.put(slot,-1);
			else if (palette.has(i.getItemColor()) && i.getColorExpireTime() == 0) colors.put(slot,i.getItemColor());
			else skipped = true;
		}
		if (skins.isEmpty()) throw new IllegalArgumentException("Equip appearance-compatible equipment first.");
		update(c,"INSERT INTO wardrobe_outfits VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE skins_json=VALUES(skins_json),updated_at=VALUES(updated_at)",account,name,Json.write(skins),System.currentTimeMillis());
		update(c,"INSERT INTO wardrobe_outfit_dyes VALUES(?,?,?) ON DUPLICATE KEY UPDATE dyes_json=VALUES(dyes_json)",account,name,Json.write(colors));
		return "Saved outfit " + name + "." + (skipped ? " Dyes not registered to your palette were not saved." : "");
	}

	private static String applyOutfit(Connection c,Player p,Map<String,String> args,List<Runnable> committed) throws SQLException {
		String name = name(args);
		Map<String,Object> saved = row(c,"SELECT skins_json FROM wardrobe_outfits WHERE account_id=? AND name=?",p.getAccount().getId(),name);
		if (saved == null) throw new IllegalArgumentException("This outfit was deleted. Refresh Wardrobe.");
		Map<String,Integer> skins = Json.read(saved.get("skins_json").toString(),new com.google.gson.reflect.TypeToken<Map<String,Integer>>() {});
		Set<Integer> owned = unlocked(c,p.getAccount().getId());
		Palette palette = palette(c,p.getAccount().getId());
		Map<String,Object> savedDyes = row(c,"SELECT dyes_json FROM wardrobe_outfit_dyes WHERE account_id=? AND name=?",p.getAccount().getId(),name);
		Map<String,Integer> colors = savedDyes == null ? Map.of()
			: Json.read(savedDyes.get("dyes_json").toString(),new com.google.gson.reflect.TypeToken<Map<String,Integer>>() {});
		// Dyes first, for the same custody reason as applyChanges. Outfits saved before dyes keep the current dyes.
		for (var entry : colors.entrySet()) {
			long slot = Long.parseLong(entry.getKey());
			Item target = equipment(p).stream().filter(i -> i.isEquipped() && i.getEquipmentSlot() == slot).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Equip the equipment slots saved in this outfit first."));
			Integer savedSkin = skins.get(entry.getKey());
			ItemTemplate skin = savedSkin == null ? target.getItemSkinTemplate() : savedSkin == 0 ? target.getItemTemplate() : byId.get(savedSkin);
			if (entry.getValue() != -1 && (!palette.has(entry.getValue()) || skin == null || !skin.isItemDyePermitted()))
				throw new IllegalArgumentException("This outfit's dyes do not match your palette or equipped items.");
			changeColor(c,p,target,entry.getValue(),committed);
		}
		for (var entry : skins.entrySet()) {
			long slot = Long.parseLong(entry.getKey());
			Item target = equipment(p).stream().filter(i -> i.isEquipped() && i.getEquipmentSlot() == slot).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Equip the equipment slots saved in this outfit first."));
			ItemTemplate skin = entry.getValue() == 0 ? target.getItemTemplate() : byId.get(entry.getValue());
			if (skin == null || entry.getValue() != 0 && (!owned.contains(entry.getValue())
				|| !WardrobeRules.permitted(skin,p.getRace(),p.getGender()) || !WardrobeRules.compatible(target.getItemTemplate(),skin)))
				throw new IllegalArgumentException("This outfit does not match your equipped items.");
			changeSkin(c,p,target,skin,committed);
		}
		return "Applied outfit " + name + ".";
	}

	private static String deleteOutfit(Connection c,int account,Map<String,String> args) throws SQLException {
		update(c,"DELETE FROM wardrobe_outfits WHERE account_id=? AND name=?",account,name(args));
		update(c,"DELETE FROM wardrobe_outfit_dyes WHERE account_id=? AND name=?",account,name(args));
		return "Outfit deleted.";
	}

	static int integer(Map<String,String> args,String key,int fallback) {
		try { return Integer.parseInt(args.getOrDefault(key,Integer.toString(fallback))); }
		catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid selection."); }
	}
	static long number(Map<String,Object> row,String key) {
		Object value = row.get(key);
		// Connector/J returns TINYINT(1) inventory flags as Boolean.
		if (value instanceof Boolean flag) return flag ? 1 : 0;
		return value == null ? 0 : ((Number)value).longValue();
	}
	static int update(Connection c,String sql,Object... args) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql)) { bind(s,args); return s.executeUpdate(); }
	}
	static List<Map<String,Object>> rows(Connection c,String sql,Object... args) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql)) {
			bind(s,args);
			try (ResultSet r = s.executeQuery()) {
				List<Map<String,Object>> result = new ArrayList<>();
				while (r.next()) { Map<String,Object> row = new LinkedHashMap<>();
					for (int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));
					result.add(row);
				}
				return result;
			}
		}
	}
	static Map<String,Object> row(Connection c,String sql,Object... args) throws SQLException {
		List<Map<String,Object>> rows = rows(c,sql,args); return rows.isEmpty() ? null : rows.getFirst();
	}
	private static void bind(PreparedStatement s,Object[] args) throws SQLException { for (int i=0;i<args.length;i++) s.setObject(i+1,args[i]); }
}
