package com.aionemu.gameserver.services;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.configs.main.HTMLConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.drop.*;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.globaldrops.*;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.model.templates.item.enums.EquipType;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.npc.NpcTemplateType;
import com.aionemu.gameserver.model.templates.quest.*;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.services.event.EventService;
import com.aionemu.gameserver.world.World;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.LoggerFactory;

/**
 * Read-only item and creature reference for the Central Market's Codex tab and the world map's Codex panel. It never rolls loot, creates actors or changes character data.
 * A market session is optional; with one, the page can open the character's current target and show its personal drop rate.
 */
public final class LootCodexService {

	private static final int PAGE_SIZE = 12, MARKERS = 48;
	private static final Path MEDIA = Path.of("config/codex/media");
	private static volatile Index index;
	private record Source(String kind, int id, String name, String detail, float chance, long min, long max) {}
	private record QuestDropRow(int quest, String name, int item, float chance) {}
	private record ScriptedSpawn(int map, Double x, Double y, Double z, String condition) {
		boolean fixed() { return map != 0 && x != null && y != null && z != null; }
	}
	private record Index(Object items, Object drops, Object rules, Object spawns, List<ItemTemplate> sortedItems, List<NpcTemplate> sortedNpcs,
		Map<Integer, List<Source>> sources, Map<Integer, Integer> sourceCounts, Map<Integer, List<SpawnTemplate>> spots, Map<Integer, Set<Integer>> npcMaps, Map<Integer, List<QuestDropRow>> questDrops,
		Map<Integer, List<NpcTemplate>> ruleNpcs, Map<Integer, List<ScriptedSpawn>> scripted) {}
	private LootCodexService() {}

	/** Builds the static index in the background so the first page opens without the full data scan. */
	public static void start() {
		if (!HTMLConfig.CODEX_ENABLED)
			return;
		Thread warm = new Thread(() -> {
			try { current(); }
			catch (Exception e) { LoggerFactory.getLogger(LootCodexService.class).warn("Codex index could not be prepared; it will be built on first use", e); }
		}, "LootCodexIndex");
		warm.setDaemon(true);
		warm.setPriority(Thread.MIN_PRIORITY);
		warm.start();
	}

	public static void handle(HttpExchange x) throws IOException {
		try {
			x.getResponseHeaders().set("Cache-Control", "no-store");
			x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			x.getResponseHeaders().set("Referrer-Policy", "no-referrer");
			if (!HTMLConfig.CODEX_ENABLED) { send(x, 404, "application/json", Json.bytes(Map.of("error", "The Codex is turned off on this server."))); return; }
			if (!x.getRequestMethod().equals("GET")) { send(x, 405, "application/json", Json.bytes(Map.of("error", "Use GET."))); return; }
			String path = x.getRequestURI().getPath();
			if (path.equals("/market/codex") || path.matches("/market/codex/media/codex\\.(html|css|js)")) {
				String file = path.equals("/market/codex") ? "codex.html" : path.substring(path.lastIndexOf('/') + 1);
				byte[] bytes = Files.readAllBytes(MEDIA.resolve(file));
				if (file.endsWith(".css")) bytes = WindowTextScale.css(bytes);
				send(x, 200, file.endsWith(".js") ? "application/javascript" : file.endsWith(".css") ? "text/css" : "text/html", bytes); return;
			}
			if (!path.equals("/market/codex/state")) { send(x, 404, "application/json", Json.bytes(Map.of("error", "Not found."))); return; }
			Map<String, String> args = new HashMap<>();
			String query = x.getRequestURI().getRawQuery();
			if (query != null) {
				if (query.length() > 2048) throw new IllegalArgumentException("Search is too long.");
				for (String part : query.split("&")) {
					String[] kv = part.split("=", 2);
					if (kv.length == 2) args.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
				}
			}
			Player player = MarketplaceService.findPlayer(args.getOrDefault("session_id", ""));
			send(x, 200, "application/json", Json.bytes(snapshot(args, player != null && player.isOnline() ? player : null)));
		} catch (IllegalArgumentException e) { send(x, 400, "application/json", Json.bytes(Map.of("error", e.getMessage()))); }
		catch (Exception e) {
			LoggerFactory.getLogger(LootCodexService.class).error("Codex request failed", e);
			send(x, 503, "application/json", Json.bytes(Map.of("error", "The Codex is unavailable. Try again.")));
		} finally { x.close(); }
	}

	private static void send(HttpExchange x, int status, String type, byte[] bytes) throws IOException {
		x.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
		x.sendResponseHeaders(status, bytes.length); x.getResponseBody().write(bytes);
	}

	private static synchronized Index current() {
		Index data = index;
		if (data != null && data.items == DataManager.ITEM_DATA && data.drops == DataManager.CUSTOM_NPC_DROP && data.rules == DataManager.GLOBAL_DROP_DATA && data.spawns == DataManager.SPAWNS_DATA) return data;
		Map<Integer, List<Source>> sources = new HashMap<>();
		Map<Integer, List<SpawnTemplate>> spots = new HashMap<>();
		Map<Integer, Set<Integer>> npcMaps = new HashMap<>();
		Map<Integer, List<QuestDropRow>> questDrops = new HashMap<>();
		for (var map : DataManager.WORLD_MAPS_DATA) for (var group : DataManager.SPAWNS_DATA.getSpawnsByWorldId(map.getMapId()))
			for (var spot : group.getSpawnTemplates()) {
				spots.computeIfAbsent(spot.getNpcId(), _ -> new ArrayList<>()).add(spot);
				npcMaps.computeIfAbsent(spot.getNpcId(), _ -> new HashSet<>()).add(map.getMapId());
			}
		Map<Integer, List<ScriptedSpawn>> scripted = scriptedSpawns();
		scripted.forEach((npc, entries) -> entries.stream().filter(entry -> entry.map != 0)
			.forEach(entry -> npcMaps.computeIfAbsent(npc, _ -> new HashSet<>()).add(entry.map)));
		for (NpcTemplate npc : DataManager.NPC_DATA.getNpcData()) {
			NpcDrop drops = DataManager.CUSTOM_NPC_DROP.getNpcDrop(npc.getTemplateId());
			if (drops != null && drops.getDropGroup() != null) for (DropGroup group : drops.getDropGroup()) for (Drop drop : group.getDrop()) {
				add(sources, drop.getItemId(), new Source("npc", npc.getTemplateId(), npc.getName(), groupDetail(group), drop.getChance(), drop.getMinAmount(), drop.getMaxAmount()));
			}
			var shop = DataManager.TRADE_LIST_DATA.getTradeListTemplate(npc.getTemplateId());
			if (shop != null) for (var tab : shop.getTradeTablist()) {
				var goods = DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());
				if (goods != null && goods.getItemIdList() != null) for (int item : goods.getItemIdList())
					add(sources, item, new Source("vendor", npc.getTemplateId(), npc.getName(), "Vendor; prices, faction and stock requirements apply", -1, 1, 1));
			}
		}
		int ruleId = 0;
		for (GlobalRule rule : DataManager.GLOBAL_DROP_DATA.getAllRules()) {
			if (rule.getGlobalRuleNpcs() != null && rule.getGlobalRuleMaps() != null) for (var npc : rule.getGlobalRuleNpcs().getGlobalDropNpcs())
				for (var map : rule.getGlobalRuleMaps().getGlobalDropMaps()) npcMaps.computeIfAbsent(npc.getNpcId(), _ -> new HashSet<>()).add(map.getMapId());
			for (GlobalDropItem drop : rule.getDropItems()) {
				if (rule.getGlobalRuleNpcs() == null) {
					add(sources, drop.getId(), new Source("rule", ruleId, rule.getRuleName(), ruleDetail(rule), rule.getChance(), drop.getMinCount(), drop.getMaxCount()));
					continue;
				}
				var item = DataManager.ITEM_DATA.getItemTemplate(drop.getId());
				for (var named : rule.getGlobalRuleNpcs().getGlobalDropNpcs()) {
					var npc = DataManager.NPC_DATA.getNpcTemplate(named.getNpcId());
					if (npc == null || item == null || npc.getLevel() - item.getLevel() < rule.getMinDiff() || npc.getLevel() - item.getLevel() > rule.getMaxDiff()) continue;
					if (!matches(spots, rule, npc)) continue;
					add(sources, drop.getId(), new Source("npc", named.getNpcId(), npc.getName(), "Named loot; " + ruleDetail(rule), rule.getChance(), drop.getMinCount(), drop.getMaxCount()));
				}
			}
			ruleId++;
		}
		for (var quest : DataManager.QUEST_DATA.getQuestTemplates()) {
			String faction = faction(quest.getRacePermitted());
			for (Rewards reward : quest.getRewards()) {
				for (QuestItems item : reward.getRewardItem()) add(sources, item.getItemId(), new Source("quest", quest.getId(), quest.getName(), "Quest reward; " + faction, -1, item.getCount(), item.getCount()));
				for (QuestItems item : reward.getSelectableRewardItem()) add(sources, item.getItemId(), new Source("quest", quest.getId(), quest.getName(), "Choice of quest reward; " + faction, -1, item.getCount(), item.getCount()));
			}
			for (QuestDrop drop : quest.getQuestDrop()) {
				add(sources, drop.getItemId(), new Source("npc", drop.getNpcId(), npcName(drop.getNpcId()), "Quest " + quest.getId() + ": " + quest.getName() + "; active collecting step required", drop.getChance(), 1, 1));
				questDrops.computeIfAbsent(drop.getNpcId(), _ -> new ArrayList<>()).add(new QuestDropRow(quest.getId(), quest.getName(), drop.getItemId(), drop.getChance()));
			}
		}
		for (var recipe : DataManager.RECIPE_DATA.getRecipeTemplates()) {
			String detail = (recipe.isMorph() ? "Morphing" : "Crafting") + "; skill " + recipe.getSkillId() + ", proficiency " + recipe.getSkillpoint() + "; " + faction(recipe.getRace());
			add(sources, recipe.getProductId(), new Source("recipe", recipe.getId(), "Recipe " + recipe.getId(), detail, -1, recipe.getQuantity(), recipe.getQuantity()));
			for (int i = 1; i <= recipe.getComboProductSize(); i++) {
				Integer product = recipe.getComboProduct(i);
				if (product != null) add(sources, product, new Source("recipe", recipe.getId(), "Recipe " + recipe.getId(), detail + "; critical result", -1, 1, 1));
			}
		}
		List<ItemTemplate> items = new ArrayList<>();
		for (ItemTemplate item : DataManager.ITEM_DATA.getItemTemplates()) {
			if (item.getName() == null || item.getName().isBlank()) continue;
			items.add(item);
			var box = DataManager.DECOMPOSABLE_ITEMS_DATA.getInfoByItemId(item.getTemplateId());
			if (box == null) continue;
			for (var set : box.getSets()) for (var reward : set.getRewards()) for (var result : reward.getItems())
				add(sources, result.getItemId(), new Source("item", item.getTemplateId(), item.getName(), box.isSelectable() ? "Choose from this container; class, level and faction requirements apply" : "Open this container; branch and weighted reward rolls, class, level and faction requirements apply", -1, result.getMinimumCount(), result.getMaximumCount()));
		}
		// Items without any recorded source (test items, unused templates) stay reachable by exact ID only.
		items.removeIf(item -> !sources.containsKey(item.getTemplateId()));
		items.sort(Comparator.comparing(ItemTemplate::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(ItemTemplate::getTemplateId));
		List<NpcTemplate> npcs = new ArrayList<>();
		// Creatures & bosses: real monsters only. Skill objects, placeholders and level 100+ test templates are left out.
		for (NpcTemplate npc : DataManager.NPC_DATA.getNpcData())
			if (creature(npc) && (spots.containsKey(npc.getTemplateId()) || scripted.containsKey(npc.getTemplateId()) || questDrops.containsKey(npc.getTemplateId()) || npcMaps.containsKey(npc.getTemplateId())
				|| DataManager.CUSTOM_NPC_DROP.getNpcDrop(npc.getTemplateId()) != null)) npcs.add(npc);
		npcs.sort(Comparator.comparing(LootCodexService::display, String.CASE_INSENSITIVE_ORDER).thenComparingInt(NpcTemplate::getTemplateId));
		sources.replaceAll((_, rows) -> List.copyOf(new LinkedHashSet<>(rows)));
		Map<Integer, Integer> sourceCounts = new HashMap<>();
		sources.forEach((item, rows) -> sourceCounts.put(item, (int) rows.stream().filter(source -> !source.kind.equals("rule")).map(source -> source.kind + ":" + source.id).distinct().count()));
		spots.replaceAll((_, rows) -> List.copyOf(rows));
		npcMaps.replaceAll((_, rows) -> Set.copyOf(rows));
		questDrops.replaceAll((_, rows) -> List.copyOf(rows));
		return index = new Index(DataManager.ITEM_DATA, DataManager.CUSTOM_NPC_DROP, DataManager.GLOBAL_DROP_DATA, DataManager.SPAWNS_DATA, List.copyOf(items),
			List.copyOf(npcs), Map.copyOf(sources), Map.copyOf(sourceCounts), Map.copyOf(spots), Map.copyOf(npcMaps), Map.copyOf(questDrops), new ConcurrentHashMap<>(), scripted);
	}

	/** Generated by the normal builder, never evaluated as code or interpreted as a live sighting. */
	private static Map<Integer, List<ScriptedSpawn>> scriptedSpawns() {
		Path file = Path.of("config/codex/scripted-spawns.tsv");
		Map<Integer, List<ScriptedSpawn>> result = new HashMap<>();
		try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
			lines.filter(line -> !line.isBlank() && !line.startsWith("#")).forEach(line -> {
				String[] fields = line.split("\t", -1);
				if (fields.length != 6) throw new IllegalArgumentException("Invalid scripted spawn catalog row");
				int npc = Integer.parseInt(fields[0]), map = Integer.parseInt(fields[1]);
				if (DataManager.NPC_DATA.getNpcTemplate(npc) == null || map != 0 && DataManager.WORLD_MAPS_DATA.getTemplate(map) == null) return;
				Double x = fields[2].isEmpty() ? null : Double.valueOf(fields[2]), y = fields[3].isEmpty() ? null : Double.valueOf(fields[3]), z = fields[4].isEmpty() ? null : Double.valueOf(fields[4]);
				if (x != null && !Double.isFinite(x) || y != null && !Double.isFinite(y) || z != null && !Double.isFinite(z)) throw new IllegalArgumentException("Invalid scripted spawn coordinate");
				result.computeIfAbsent(npc, _ -> new ArrayList<>()).add(new ScriptedSpawn(map, x, y, z, fields[5]));
			});
		} catch (IOException e) {
			LoggerFactory.getLogger(LootCodexService.class).warn("Codex scripted spawn catalog could not be read; only ordinary spawns are indexed", e);
		}
		result.replaceAll((_, rows) -> List.copyOf(new LinkedHashSet<>(rows)));
		return Map.copyOf(result);
	}

	private static void add(Map<Integer, List<Source>> data, int item, Source source) { data.computeIfAbsent(item, _ -> new ArrayList<>()).add(source); }
	private static String npcName(int id) { var npc = DataManager.NPC_DATA.getNpcTemplate(id); return npc == null ? "NPC " + id : display(npc); }
	private static boolean creature(NpcTemplate npc) {
		String name = npc.getName();
		return name != null && !name.isBlank() && name.indexOf(':') < 0 && npc.getLevel() >= 1 && npc.getLevel() < 100
			&& (npc.getNpcTemplateType() == NpcTemplateType.MONSTER || npc.getNpcTemplateType() == NpcTemplateType.RAID_MONSTER);
	}
	/** Server templates carry lower-case English names; show them in title case. Names with capitals are kept. */
	private static String display(NpcTemplate npc) {
		String name = npc.getName() == null ? "" : npc.getName().strip();
		if (!name.equals(name.toLowerCase(Locale.ROOT))) return name;
		StringBuilder out = new StringBuilder(name.length());
		boolean start = true;
		for (char c : name.toCharArray()) { out.append(start ? Character.toUpperCase(c) : c); start = c == ' ' || c == '-' || c == '('; }
		return out.toString();
	}
	private static String faction(Object race) {
		String name = race == null ? "PC_ALL" : race.toString();
		return switch (name) {
			case "ELYOS" -> "Elyos only";
			case "ASMODIANS" -> "Asmodians only";
			case "PC_ALL", "NONE" -> "both factions";
			default -> name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
		};
	}
	private static String groupDetail(DropGroup group) {
		return "Loot group: " + (group.getName() == null ? "NPC loot" : group.getName()) + "; up to " + group.getMaxItems() + " items; " + faction(group.getRace())
			+ (group.isUseLevelBasedChanceReduction() ? "; level penalty applies" : "") + "; chance shown is the configured roll threshold; entries compete";
	}
	private static String ruleDetail(GlobalRule rule) {
		StringBuilder text = new StringBuilder((rule.getGlobalRuleNpcs() == null ? "World drop pool" : "Named loot pool") + "; rule roll, then weighted selection of up to " + rule.getMaxDropRule() + " items");
		if (rule.getGlobalRuleMaps() != null) {
			StringJoiner maps = new StringJoiner(", ", "; maps: ", "");
			for (var map : rule.getGlobalRuleMaps().getGlobalDropMaps()) maps.add(mapName(map.getMapId()));
			text.append(maps);
		}
		if (rule.getGlobalRuleZones() != null) text.append("; specific zones only");
		if (rule.getRestrictionRace() != null) text.append("; ").append(faction(rule.getRestrictionRace()));
		if (rule.isDynamicChance()) text.append("; rank and rating scale the rule chance");
		if (rule.isUseLevelBasedChanceReduction()) text.append("; level penalty applies");
		return text.toString();
	}
	private static String mapName(int id) { var map = DataManager.WORLD_MAPS_DATA.getTemplate(id); return map == null ? "Map " + id : map.getName(); }

	public static Map<String, Object> snapshot(Map<String, String> args, Player player) {
		Index data = current();
		String view = args.getOrDefault("view", "items"), q = args.getOrDefault("q", "").strip().toLowerCase(Locale.ROOT);
		if (q.length() > 100) throw new IllegalArgumentException("Search is too long.");
		int id = integer(args, "id", 0), page = integer(args, "page", 0);
		int pageSize = integer(args, "pageSize", PAGE_SIZE), minLevel = integer(args, "minLevel", 0), maxLevel = integer(args, "maxLevel", 255);
		if (pageSize < 4 || pageSize > PAGE_SIZE || maxLevel > 255 || minLevel > maxLevel) throw new IllegalArgumentException("Check the level range and page size.");
		String equipmentGroup = args.getOrDefault("group", ""), race = args.getOrDefault("race", ""), sourceKind = args.getOrDefault("source", "");
		String area = args.getOrDefault("area", "").strip().toLowerCase(Locale.ROOT), rating = args.getOrDefault("rating", ""), rank = args.getOrDefault("rank", "");
		String sort = args.getOrDefault("sort", "name");
		Set<Integer> eventItems = new HashSet<>();
		if (sourceKind.equals("event")) for (var rule : activeEventRules()) for (var drop : rule.getDropItems()) eventItems.add(drop.getId());
		if (view.equals("target")) {
			// The map panel's target-frame chest sends the target's object id; the market tab uses its session's character.
			var target = args.containsKey("target") ? World.getInstance().findVisibleObject(integer(args, "target", 0)) : player == null ? null : player.getTarget();
			if (target == null && player == null && !args.containsKey("target")) throw new IllegalArgumentException("Open the Codex from the Central Market or the map to inspect your target.");
			if (!(target instanceof Npc npc)) throw new IllegalArgumentException("Target a creature first, then select My target.");
			view = "npc"; id = npc.getNpcId();
		}
		Map<String, Object> result = new LinkedHashMap<>(); result.put("view", view); result.put("id", id);
		if (args.containsKey("native")) result.put("filterGroups", Arrays.stream(ItemGroup.values()).filter(v -> v != ItemGroup.NONE && !v.name().startsWith("NOWEAPON") && !v.name().startsWith("EXTRACT")).map(v -> Map.of("value", v.name(), "category", category(v))).toList());
		result.put("signedIn", player != null);
		if (player != null) result.put("dropRate", Math.round(DropRegistrationService.getInstance().getPlayerDropRate(player, 100) * 100) / 100.0);
		List<Map<String, Object>> rows = new ArrayList<>();
		List<?> lazy = null; Function<Object, Map<String, Object>> mapper = null;
		switch (view) {
			case "items" -> {
				String category = args.getOrDefault("category", ""), grade = args.getOrDefault("grade", "");
				List<ItemTemplate> found = new ArrayList<>();
				var exact = q.matches("\\d{9}") ? DataManager.ITEM_DATA.getItemTemplate(Integer.parseInt(q)) : null;
				for (ItemTemplate item : exact != null ? List.of(exact) : data.sortedItems) {
					if ((!q.isEmpty() && !item.getName().toLowerCase(Locale.ROOT).contains(q) && !String.valueOf(item.getTemplateId()).equals(q))
						|| (!category.isEmpty() && !category(item).equals(category)) || (!grade.isEmpty() && !item.getItemQuality().name().equalsIgnoreCase(grade))
						|| item.getLevel() < minLevel || item.getLevel() > maxLevel || !equipmentGroup.isEmpty() && !item.getItemGroup().name().equals(equipmentGroup)
						|| !race.isEmpty() && !item.getRace().name().equals(race) && !item.getRace().name().equals("PC_ALL")
						|| !sourceKind.isEmpty() && !(sourceKind.equals("event") ? eventItems.contains(item.getTemplateId()) : hasSource(data, item.getTemplateId(), sourceKind))) continue;
					found.add(item);
				}
				if (sort.equals("level_asc")) found.sort(Comparator.comparingInt(ItemTemplate::getLevel).thenComparing(ItemTemplate::getName, String.CASE_INSENSITIVE_ORDER));
				else if (sort.equals("level_desc")) found.sort(Comparator.comparingInt(ItemTemplate::getLevel).reversed().thenComparing(ItemTemplate::getName, String.CASE_INSENSITIVE_ORDER));
				lazy = found;
				mapper = value -> {
					ItemTemplate item = (ItemTemplate) value; var row = item(item); var sources = data.sources.getOrDefault(item.getTemplateId(), List.of());
					row.put("sources", data.sourceCounts.getOrDefault(item.getTemplateId(), 0));
					row.put("worldDrop", sources.stream().anyMatch(source -> source.kind.equals("rule"))); return row;
				};
				result.put("title", "Item database");
			}
			case "npcs" -> {
				List<NpcTemplate> found = new ArrayList<>();
				for (var npc : data.sortedNpcs) {
					if ((!q.isEmpty() && !display(npc).toLowerCase(Locale.ROOT).contains(q) && !String.valueOf(npc.getTemplateId()).equals(q))
						|| npc.getLevel() < minLevel || npc.getLevel() > maxLevel || !rating.isEmpty() && !npc.getRating().name().equals(rating)
						|| !rank.isEmpty() && !npc.getRank().name().equals(rank) || !race.isEmpty() && !npc.getRace().name().equals(race)
						|| !area.isEmpty() && data.npcMaps.getOrDefault(npc.getTemplateId(), Set.of()).stream().noneMatch(map -> String.valueOf(map).equals(area) || mapName(map).toLowerCase(Locale.ROOT).contains(area))) continue;
					found.add(npc);
				}
				if (sort.equals("level_asc")) found.sort(Comparator.comparingInt(NpcTemplate::getLevel).thenComparing(LootCodexService::display, String.CASE_INSENSITIVE_ORDER));
				else if (sort.equals("level_desc")) found.sort(Comparator.comparingInt(NpcTemplate::getLevel).reversed().thenComparing(LootCodexService::display, String.CASE_INSENSITIVE_ORDER));
				lazy = found; mapper = value -> new LinkedHashMap<>(npc((NpcTemplate) value)); result.put("title", "Creatures & bosses");
			}
			case "item" -> {
				var item = DataManager.ITEM_DATA.getItemTemplate(id); if (item == null) throw new IllegalArgumentException("Item not found.");
				result.put("title", item.getName()); result.put("item", item(item));
				boolean includeWorld = !args.containsKey("native") || args.getOrDefault("world", "0").equals("1");
				result.put("includeWorld", includeWorld); result.put("sources", data.sourceCounts.getOrDefault(id, 0));
				Map<String, Map<String, Object>> distinct = new LinkedHashMap<>();
				if (item.getModifiers() != null) result.put("modifiers", item.getModifiers().stream().map(stat -> Map.of("name", stat.getName().name().replace('_', ' '), "value", stat.getValue(), "conditional", stat.hasConditions())).toList());
				Map<Integer, Map<String, Object>> poolCreatures = new LinkedHashMap<>();
				for (Source source : data.sources.getOrDefault(id, List.of())) {
					if (source.kind.equals("rule") && !includeWorld) continue;
					if (source.kind.equals("rule")) {
						// A world pool is listed as the creatures that can actually roll it, with an approximate per-kill chance.
						GlobalRule rule = DataManager.GLOBAL_DROP_DATA.getAllRules().get(source.id);
						float chance = perKill(rule, id);
						for (NpcTemplate npc : ruleCreatures(data, source.id)) {
							int diff = npc.getLevel() - item.getLevel(); if (diff < rule.getMinDiff() || diff > rule.getMaxDiff()) continue;
							var known = poolCreatures.get(npc.getTemplateId());
							if (known != null && ((Number) known.get("chance")).floatValue() >= chance) continue;
							Map<String, Object> row = new LinkedHashMap<>(); row.put("kind", "npc"); row.put("id", npc.getTemplateId()); row.put("name", display(npc));
							row.put("detail", "Lv " + npc.getLevel() + (rule.getGlobalRuleNpcs() != null ? " | boss loot" : " | world drop")); row.put("chance", chance); row.put("min", source.min); row.put("max", source.max);
							row.put("rateKind", "per kill (approx.)"); row.put("npc", npc(npc)); poolCreatures.put(npc.getTemplateId(), row);
						}
						continue;
					}
					String key = source.kind + ":" + source.id;
					if (distinct.containsKey(key)) {
						var row = distinct.get(key); row.put("methods", (int) row.getOrDefault("methods", 1) + 1);
						row.put("chance", -1); row.put("detail", "Multiple loot entries; select this source for its locations"); continue;
					}
					Map<String, Object> row = new LinkedHashMap<>(); row.put("kind", source.kind); row.put("id", source.id); row.put("name", source.name);
					row.put("detail", source.detail); row.put("chance", source.chance); row.put("min", source.min); row.put("max", source.max);
					row.put("rateKind", source.kind.equals("rule") || source.detail.startsWith("Named loot;") ? "Pool roll" : source.detail.startsWith("Quest ") ? "Quest roll" : "Roll threshold");
					if (source.kind.equals("npc") || source.kind.equals("vendor")) row.put("npc", npc(DataManager.NPC_DATA.getNpcTemplate(source.id)));
					distinct.put(key, row);
				}
				rows.addAll(distinct.values());
				List<Map<String, Object>> pooled = new ArrayList<>(poolCreatures.values());
				pooled.sort(Comparator.comparingDouble((Map<String, Object> r) -> -((Number) r.get("chance")).doubleValue()).thenComparing(r -> String.valueOf(r.get("name"))));
				rows.addAll(pooled);
				if (includeWorld) for (GlobalRule rule : activeEventRules()) for (GlobalDropItem drop : rule.getDropItems()) if (drop.getId() == id) {
					Map<String, Object> row = new LinkedHashMap<>(); row.put("kind", "event"); row.put("id", 0); row.put("name", rule.getRuleName());
					row.put("detail", "Active event; " + ruleDetail(rule)); row.put("chance", rule.getChance()); row.put("min", drop.getMinCount()); row.put("max", drop.getMaxCount()); row.put("rateKind", "Event pool roll");
					rows.add(row);
				}
			}
			case "npc" -> {
				var npc = DataManager.NPC_DATA.getNpcTemplate(id); if (npc == null) throw new IllegalArgumentException("Creature not found.");
				result.put("title", display(npc)); result.put("npc", npc(npc));
				boolean includeWorld = !args.containsKey("native") || args.getOrDefault("world", "0").equals("1");
				result.put("includeWorld", includeWorld);
				var stats = npc.getStatsTemplate(); Map<String, Object> values = new LinkedHashMap<>();
				values.put("HP", stats.getMaxHp()); values.put("MP", stats.getMaxMp()); values.put("Attack", stats.getAttack()); values.put("Accuracy", stats.getAccuracy());
				values.put("Physical defence", stats.getPdef()); values.put("Magic resistance", stats.getMresist()); values.put("Evasion", stats.getEvasion());
				result.put("stats", values);
				NpcDrop drops = DataManager.CUSTOM_NPC_DROP.getNpcDrop(id);
				if (drops != null && drops.getDropGroup() != null) for (DropGroup group : drops.getDropGroup()) for (Drop drop : group.getDrop()) {
					var item = DataManager.ITEM_DATA.getItemTemplate(drop.getItemId()); if (item == null) continue;
					Map<String, Object> row = item(item); row.put("chance", drop.getChance()); row.put("min", drop.getMinAmount()); row.put("max", drop.getMaxAmount()); row.put("detail", groupDetail(group)); rows.add(row);
				}
				for (QuestDropRow drop : data.questDrops.getOrDefault(id, List.of())) {
					var item = DataManager.ITEM_DATA.getItemTemplate(drop.item()); if (item == null) continue;
					Map<String, Object> row = item(item); row.put("chance", drop.chance()); row.put("rateKind", "Quest roll"); row.put("min", 1); row.put("max", 1); row.put("detail", "Quest " + drop.quest() + ": " + drop.name() + "; active collection step required"); rows.add(row);
				}
				List<GlobalRule> rules = DataManager.GLOBAL_DROP_DATA.getAllRules();
				for (GlobalRule rule : rules) if ((includeWorld || rule.getGlobalRuleNpcs() != null) && matches(data, rule, npc)) poolRows(rows, rule, npc, "");
				for (GlobalRule rule : activeEventRules()) if ((includeWorld || rule.getGlobalRuleNpcs() != null) && matches(data, rule, npc)) poolRows(rows, rule, npc, "Active event; ");
				result.put("locations", locations(data, id).size());
				result.put("notice", "Base template stats and configured loot. Personal bonuses, level penalties, faction and pool selection affect rewards. Scripted encounters may add loot. Locations are spawn areas, not live sightings.");
			}
			case "locations" -> {
				result.put("title", npcName(id)); rows.addAll(locations(data, id));
				result.put("notice", "Recorded spawn areas and scripted conditions; these are not live sightings. Only fixed coordinates have map markers.");
				result.put("emptyNotice", "No location is indexed for this creature. This does not mean it never spawns.");
				// Every recorded spawn for the map markers, not only this page's rows.
				result.put("points", markerPoints(rows));
			}
			case "item_locations" -> {
				var item = DataManager.ITEM_DATA.getItemTemplate(id);
				if (item == null) throw new IllegalArgumentException("Item not found.");
				boolean includeWorld = args.getOrDefault("world", "0").equals("1");
				Set<Integer> creatures = new LinkedHashSet<>(), merchants = new LinkedHashSet<>();
				for (Source source : data.sources.getOrDefault(id, List.of())) {
					if (source.kind.equals("npc")) creatures.add(source.id);
					else if (source.kind.equals("vendor")) merchants.add(source.id);
					else if (includeWorld && source.kind.equals("rule")) {
						GlobalRule rule = DataManager.GLOBAL_DROP_DATA.getAllRules().get(source.id);
						for (NpcTemplate npc : ruleCreatures(data, source.id)) {
							int diff = npc.getLevel() - item.getLevel();
							if (diff >= rule.getMinDiff() && diff <= rule.getMaxDiff()) creatures.add(npc.getTemplateId());
						}
					}
				}
				if (includeWorld) for (GlobalRule rule : activeEventRules()) {
					if (rule.getDropItems().stream().noneMatch(drop -> drop.getId() == item.getTemplateId())) continue;
					for (NpcTemplate npc : data.sortedNpcs) {
						int diff = npc.getLevel() - item.getLevel();
						if (diff >= rule.getMinDiff() && diff <= rule.getMaxDiff() && matches(data, rule, npc)) creatures.add(npc.getTemplateId());
					}
				}
				Set<Integer> sources = new LinkedHashSet<>(creatures); sources.addAll(merchants);
				Set<String> unique = new HashSet<>();
				for (int npc : sources) for (var location : locations(data, npc)) {
					if (!area.isEmpty() && !String.valueOf(location.get("map")).equals(area) && !String.valueOf(location.get("name")).toLowerCase(Locale.ROOT).contains(area)) continue;
					String key = npc + ":" + location.get("map") + ":" + location.get("x") + ":" + location.get("y") + ":" + location.get("z") + ":" + location.get("detail");
					if (!unique.add(key)) continue;
					location.put("sourceId", npc);
					location.put("name", npcName(npc) + " — " + location.get("name"));
					location.put("detail", (creatures.contains(npc) ? "Creature source; " : "Merchant; ") + location.get("detail"));
					rows.add(location);
				}
				result.put("title", item.getName() + " — source locations"); result.put("item", item(item));
				result.put("includeWorld", includeWorld);
				result.put("points", markerPoints(rows));
				result.put("notice", "Creature and merchant locations include scripted conditions. Only fixed coordinates are marked, up to " + MARKERS + " points. These are not live sightings. Inspect the item for quests, crafting and containers.");
				result.put("emptyNotice", "No creature or merchant location is indexed. Inspect the item for quests, crafting and containers.");
			}
			case "rule" -> {
				List<GlobalRule> rules = DataManager.GLOBAL_DROP_DATA.getAllRules(); if (id >= rules.size()) throw new IllegalArgumentException("Drop rule not found.");
				GlobalRule rule = rules.get(id); result.put("title", rule.getRuleName()); result.put("notice", ruleDetail(rule));
				var wanted = DataManager.ITEM_DATA.getItemTemplate(integer(args, "item", 0));
				List<NpcTemplate> found = new ArrayList<>();
				for (var template : ruleCreatures(data, id)) {
					if (wanted != null) { int diff = template.getLevel() - wanted.getLevel(); if (diff < rule.getMinDiff() || diff > rule.getMaxDiff()) continue; }
					found.add(template);
				}
				lazy = found; mapper = value -> new LinkedHashMap<>(npc((NpcTemplate) value));
			}
			case "recipe" -> {
				var recipe = DataManager.RECIPE_DATA.getRecipeTemplateById(id); if (recipe == null) throw new IllegalArgumentException("Recipe not found.");
				result.put("title", (recipe.isMorph() ? "Morphing" : "Crafting") + " recipe " + id);
				result.put("notice", "Skill " + recipe.getSkillId() + "; proficiency " + recipe.getSkillpoint() + "; " + faction(recipe.getRace()));
				for (var set : recipe.getComponents()) for (var component : set.getComponent()) {
					var item = DataManager.ITEM_DATA.getItemTemplate(component.getItemId()); if (item == null) continue;
					var row = item(item); row.put("min", component.getQuantity()); row.put("max", component.getQuantity()); row.put("detail", "Required material"); rows.add(row);
				}
			}
			case "quest" -> {
				var quest = DataManager.QUEST_DATA.getQuestById(id); if (quest == null) throw new IllegalArgumentException("Quest not found.");
				result.put("title", quest.getName()); result.put("notice", "Quest " + id + "; " + faction(quest.getRacePermitted()) + ". Prerequisites and reward choices apply.");
				for (Rewards reward : quest.getRewards()) {
					for (QuestItems entry : reward.getRewardItem()) {
						var item = DataManager.ITEM_DATA.getItemTemplate(entry.getItemId()); if (item != null) { var row = item(item); row.put("detail", "Reward"); row.put("min", entry.getCount()); row.put("max", entry.getCount()); rows.add(row); }
					}
					for (QuestItems entry : reward.getSelectableRewardItem()) {
						var item = DataManager.ITEM_DATA.getItemTemplate(entry.getItemId()); if (item != null) { var row = item(item); row.put("detail", "Choice of reward"); row.put("min", entry.getCount()); row.put("max", entry.getCount()); rows.add(row); }
					}
				}
			}
			default -> throw new IllegalArgumentException("Unknown Codex page.");
		}
		int total = lazy != null ? lazy.size() : rows.size(); page = Math.min(page, Math.max(0, (total - 1) / pageSize));
		int from = page * pageSize, to = Math.min(total, from + pageSize);
		result.put("total", total); result.put("page", page); result.put("pages", Math.max(1, (total + pageSize - 1) / pageSize));
		if (lazy != null) { List<Map<String, Object>> shown = new ArrayList<>(); for (Object value : lazy.subList(from, to)) shown.add(mapper.apply(value)); result.put("rows", shown); }
		else result.put("rows", rows.subList(from, to));
		return result;
	}

	private static List<GlobalRule> activeEventRules() {
		List<GlobalRule> rules = EventService.getInstance().getActiveEventDropRules();
		return rules == null ? List.of() : rules;
	}
	private static void poolRows(List<Map<String, Object>> rows, GlobalRule rule, NpcTemplate npc, String prefix) {
		for (GlobalDropItem drop : rule.getDropItems()) {
			var item = DataManager.ITEM_DATA.getItemTemplate(drop.getId()); if (item == null) continue;
			int diff = npc.getLevel() - item.getLevel(); if (diff < rule.getMinDiff() || diff > rule.getMaxDiff()) continue;
			Map<String, Object> row = item(item); row.put("chance", perKill(rule, drop.getId())); row.put("min", drop.getMinCount()); row.put("max", drop.getMaxCount());
			row.put("detail", !prefix.isEmpty() ? "Active event drop" : rule.getGlobalRuleNpcs() != null ? "Boss loot" : "World drop"); row.put("rateKind", "per kill (approx.)"); rows.add(row);
		}
	}

	/** Rule chance times this item's share of the weighted pick; rank, rating, level and personal modifiers are not applied. */
	private static float perKill(GlobalRule rule, int item) {
		double total = 0, weight = 0;
		for (GlobalDropItem drop : rule.getDropItems()) { total += drop.getChance(); if (drop.getId() == item) weight = Math.max(weight, drop.getChance()); }
		if (total <= 0) return rule.getChance();
		return (float) (rule.getChance() * Math.min(1, rule.getMaxDropRule() * weight / total));
	}
	private static List<NpcTemplate> ruleCreatures(Index data, int ruleId) {
		GlobalRule rule = DataManager.GLOBAL_DROP_DATA.getAllRules().get(ruleId);
		return data.ruleNpcs.computeIfAbsent(ruleId, _ -> data.sortedNpcs.stream().filter(npc -> matches(data, rule, npc)).toList());
	}

	private static int integer(Map<String, String> args, String name, int fallback) {
		int value = Integer.parseInt(args.getOrDefault(name, String.valueOf(fallback))); if (value < 0) throw new IllegalArgumentException("Invalid " + name); return value;
	}
	private static String category(ItemTemplate item) {
		return category(item.getItemGroup());
	}
	private static String category(ItemGroup group) {
		return switch (group) {
			case EARRING, RING, NECKLACE, BELT, PLUME, WING -> "Accessories";
			case QUEST -> "Quest";
			case FLUX, BALIC_MATERIAL, RAWHIDE, SOULSTONE, GATHERABLE, GATHERABLE_BONUS, DROP_MATERIAL -> "Materials";
			case RECIPE -> "Recipes";
			case MANASTONE, SPECIAL_MANASTONE, ENCHANTMENT, TAMPERING, GODSTONE, STIGMA, STIGMA_SHARD, COMBINATION -> "Enhancement";
			default -> group.getEquipType() == EquipType.WEAPON ? "Weapons" : group.getEquipType() == EquipType.ARMOR ? "Armour" : "Other";
		};
	}
	private static boolean hasSource(Index data, int item, String kind) {
		return data.sources.getOrDefault(item, List.of()).stream().anyMatch(source -> kind.equals(source.detail.startsWith("Quest ") ? "quest" : source.kind));
	}
	private static Map<String, Object> item(ItemTemplate item) {
		Map<String, Object> row = new LinkedHashMap<>(); row.put("kind", "item"); row.put("id", item.getTemplateId()); row.put("name", item.getName()); row.put("level", item.getLevel());
		row.put("quality", item.getItemQuality().name()); row.put("category", category(item)); row.put("race", faction(item.getRace())); return row;
	}
	private static Map<String, Object> npc(NpcTemplate npc) {
		if (npc == null) return Map.of();
		return Map.of("kind", "npc", "id", npc.getTemplateId(), "name", display(npc), "level", npc.getLevel(), "rank", npc.getRank().name(), "rating", npc.getRating().name(), "race", npc.getRace().name());
	}
	private static List<Map<String, Object>> locations(Index data, int npc) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (var spot : data.spots.getOrDefault(npc, List.of())) {
			var map = DataManager.WORLD_MAPS_DATA.getTemplate(spot.getWorldId());
			var row = new LinkedHashMap<String, Object>(); row.put("kind", "location"); row.put("id", npc); row.put("name", map.getName()); row.put("map", map.getMapId());
			row.put("x", spot.getX()); row.put("y", spot.getY()); row.put("z", spot.getZ());
			row.put("detail", map.isInstance() ? "Instance spawn; an active run is required" : spot.isEventSpawn() || spot.isTemporarySpawn() ? "Conditional spawn" : "Spawn area"); rows.add(row);
		}
		for (ScriptedSpawn entry : data.scripted.getOrDefault(npc, List.of())) {
			var row = new LinkedHashMap<String, Object>();
			row.put("kind", entry.fixed() ? "location" : "spawn_hint"); row.put("id", npc);
			row.put("name", entry.map == 0 ? "Location chosen during gameplay" : mapName(entry.map)); row.put("map", entry.map);
			row.put("detail", entry.condition); row.put("condition", entry.condition);
			if (entry.fixed()) { row.put("x", entry.x); row.put("y", entry.y); row.put("z", entry.z); }
			else row.put("positionNote", "No fixed map point");
			rows.add(row);
		}
		return rows;
	}
	private static List<List<Object>> markerPoints(List<Map<String, Object>> rows) {
		return rows.stream().filter(row -> "location".equals(row.get("kind")))
			.map(row -> List.of(row.get("map"), row.get("x"), row.get("y"), row.get("z"))).distinct().limit(MARKERS).toList();
	}

	/** Static counterpart of DropRegistrationService's rule restrictions. Never rolls loot or creates an NPC. */
	private static boolean matches(Index data, GlobalRule rule, NpcTemplate npc) {
		return matches(data.spots, rule, npc);
	}
	private static boolean matches(Map<Integer, List<SpawnTemplate>> spots, GlobalRule rule, NpcTemplate npc) {
		var exclusions = DataManager.GLOBAL_EXCLUSION_DATA;
		int id = npc.getTemplateId();
		if (rule.getChance() <= 0 || rule.getMaxDropRule() <= 0 || "quest_use_item".equals(npc.getAiName())
			|| exclusions.getNpcIds().contains(id) || exclusions.getNpcNames().contains(npc.getName())
			|| exclusions.getNpcTemplateTypes().contains(npc.getNpcTemplateType()) || exclusions.getNpcAbyssTypes().contains(npc.getAbyssNpcType())
			|| npc.getTribe() != null && exclusions.isTribeExcluded(id, npc.getTribe())) return false;
		boolean named = rule.getGlobalRuleNpcs() != null;
		if (named && rule.getGlobalRuleNpcs().getGlobalDropNpcs().stream().noneMatch(v -> v.getNpcId() == id)) return false;
		if (rule.getGlobalRuleExcludedNpcs() != null && rule.getGlobalRuleExcludedNpcs().getNpcIds().contains(id)) return false;
		if (rule.getGlobalRuleRatings() != null && rule.getGlobalRuleRatings().getGlobalDropRatings().stream().noneMatch(v -> v.getRating() == npc.getRating())) return false;
		if (rule.getGlobalRuleRaces() != null && rule.getGlobalRuleRaces().getGlobalDropRaces().stream().noneMatch(v -> v.getRace() == npc.getRace())) return false;
		if (rule.getGlobalRuleTribes() != null && rule.getGlobalRuleTribes().getGlobalDropTribes().stream().noneMatch(v -> v.getTribe() == npc.getTribe())) return false;
		if (rule.getGlobalRuleNpcGroups() != null && rule.getGlobalRuleNpcGroups().getGlobalDropNpcGroups().stream().noneMatch(v -> v.getGroup() == npc.getGroupDrop())) return false;
		List<SpawnTemplate> spawns = spots.getOrDefault(id, List.of());
		// An explicit named rule is source evidence even when an instance script creates the boss, so a missing
		// ordinary spawn does not hide it. Recorded spawns only in maps without world drops (arenas) still exclude it.
		if (named) return spawns.isEmpty() || spawns.stream().anyMatch(spot -> worldDrops(spot.getWorldId()));
		String group = npc.getGroupDrop().name().toLowerCase(Locale.ROOT);
		if ("chest".equals(npc.getAiName()) || group.startsWith("treasure") || group.endsWith("box")
			|| !Set.of("NONE", "DEFENDER").contains(npc.getAbyssNpcType().name())) return false;
		for (var spot : spawns) {
			var map = DataManager.WORLD_MAPS_DATA.getTemplate(spot.getWorldId());
			if (!worldDrops(map.getMapId()) || npc.getLevel() < 2 && map.getMapId() != 210010000 && map.getMapId() != 220010000) continue;
			if (rule.getGlobalRuleMaps() != null && rule.getGlobalRuleMaps().getGlobalDropMaps().stream().noneMatch(v -> v.getMapId() == map.getMapId())) continue;
			if (rule.getGlobalRuleWorlds() != null && rule.getGlobalRuleWorlds().getGlobalDropWorlds().stream().noneMatch(v -> v.getWorldDropType() == map.getWorldDropType())) continue;
			if (rule.getGlobalRuleZones() != null && rule.getGlobalRuleZones().getGlobalDropZones().stream().noneMatch(v -> DataManager.ZONE_DATA.getZones(map.getMapId()).stream()
				.anyMatch(zone -> zone.getZoneTemplate().getName().equals(v.getZone()) && zone.getArea().isInside3D(spot.getX(), spot.getY(), spot.getZ())))) continue;
			return true;
		}
		return false;
	}
	private static boolean worldDrops(int mapId) {
		var map = DataManager.WORLD_MAPS_DATA.getTemplate(mapId);
		return map != null && !map.getWorldDropType().name().equals("NONE");
	}
}
