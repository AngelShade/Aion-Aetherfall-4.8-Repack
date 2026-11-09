package com.aionemu.gameserver.services.trade;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.recipe.RecipeTemplate;

/**
 * Crafted goods are worth at least the materials they consume. Without this floor, 6,680 craft-only market items
 * were appraised below their cheapest recipe, so crafting for the market only lost Kinah: housing decor needing
 * 1.8 million of NPC catalysts was quoted 105 Kinah (economy audit, 9 November 2026).
 * Materials are valued at their own appraisal (including this floor), capped by a live NPC seller; the cheapest
 * recipe wins. A cycle (morph recipes) falls back to the material's plain appraisal.
 * Only craft-only goods take the floor: anything a monster drops, a box gives or a gatherer collects is priced by
 * that supply, because nobody crafts it at a loss (an expensive alternative recipe put Water Fragment at 50 million).
 */
final class CraftAppraisal {
	private static volatile Map<Integer, java.util.List<RecipeTemplate>> recipes;
	private static final Map<Integer, Long> FLOORS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Set<Integer>> VISITING = ThreadLocal.withInitial(HashSet::new);
	private static volatile Set<Integer> otherSources;
	private static final java.nio.file.Path DATA = java.nio.file.Path.of("data/static_data");
	private static final java.util.regex.Pattern SOURCE = java.util.regex.Pattern.compile("<(?:gd_item id|drop item_id|item id|material[^>]*?itemid)=\"(\\d+)\"");

	private CraftAppraisal() {}

	static void invalidate() {
		FLOORS.clear();
	}

	/**
	 * Items with a supply other than crafting: global and custom drops, box contents and gathering. Read from the same
	 * static data files the server loads (the data holders keep no reverse index). Null when the files are absent, which
	 * turns the floor off rather than pricing droppable goods as crafted.
	 */
	private static Set<Integer> otherSources() {
		var known = otherSources;
		if (known != null) return known.isEmpty() ? null : known;
		synchronized (CraftAppraisal.class) {
			if (otherSources != null) return otherSources.isEmpty() ? null : otherSources;
			var ids = new HashSet<Integer>();
			try {
				for (var folder : java.util.List.of(DATA.resolve("global_drops/rules"), DATA.resolve("custom_drop"), DATA.resolve("decomposable_items"), DATA.resolve("gatherables")))
					if (java.nio.file.Files.isDirectory(folder)) try (var files = java.nio.file.Files.walk(folder)) {
						for (var file : files.filter(f -> f.toString().endsWith(".xml")).toList()) {
							var m = SOURCE.matcher(new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1));
							while (m.find()) ids.add(Integer.parseInt(m.group(1)));
						}
					}
			} catch (java.io.IOException e) {
				ids.clear();
			}
			otherSources = Set.copyOf(ids);
			return ids.isEmpty() ? null : otherSources;
		}
	}

	private static Map<Integer, java.util.List<RecipeTemplate>> recipes() {
		var r = recipes;
		if (r != null) return r;
		synchronized (CraftAppraisal.class) {
			if (recipes != null) return recipes;
			var map = new java.util.HashMap<Integer, java.util.List<RecipeTemplate>>();
			if (DataManager.RECIPE_DATA != null)
				for (RecipeTemplate t : DataManager.RECIPE_DATA.getRecipeTemplates()) {
					if (t.getProductId() != null) map.computeIfAbsent(t.getProductId(), k -> new java.util.ArrayList<>()).add(t);
					// A combo (critical) product comes from the same materials, so its floor is at most that recipe's cost:
					// otherwise "Noble" gear was floored at its three-base-pieces upgrade recipe.
					for (int n = 1; n <= t.getComboProductSize(); n++) {
						Integer combo = t.getComboProduct(n);
						if (combo != null) map.computeIfAbsent(combo, k -> new java.util.ArrayList<>()).add(t);
					}
				}
			return recipes = Map.copyOf(map);
		}
	}

	/** Cheapest material cost of one unit, or 0 when it is not craftable (or a recipe cycle reaches it). */
	static long floor(ItemTemplate item) {
		int id = item.getTemplateId();
		Long known = FLOORS.get(id);
		if (known != null) return known;
		var list = recipes().get(id);
		if (list == null) return 0;
		var sources = otherSources();
		if (sources == null || sources.contains(id)) return 0;
		Set<Integer> visiting = VISITING.get();
		if (!visiting.add(id)) return 0;
		long best = Long.MAX_VALUE;
		boolean cycle = false;
		try {
			for (RecipeTemplate recipe : list) {
				long cost = cost(recipe, visiting);
				if (cost < 0) cycle = true;
				else best = Math.min(best, cost);
			}
		} finally {
			visiting.remove(id);
		}
		long value = best == Long.MAX_VALUE ? 0 : best;
		// A floor found while an outer recipe was still open is incomplete; only memoise complete ones.
		if (!cycle) FLOORS.put(id, value);
		return value;
	}

	/** Material cost per produced unit; -1 when a material is mid-appraisal higher up (cycle) or unknown. */
	private static long cost(RecipeTemplate recipe, Set<Integer> visiting) {
		if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) return -1;
		java.math.BigInteger total = java.math.BigInteger.ZERO;
		boolean any = false;
		for (var data : recipe.getComponents())
			for (var component : data.getComponent()) {
				ItemTemplate material = DataManager.ITEM_DATA.getItemTemplate(component.getItemId());
				if (material == null || component.getQuantity() <= 0) return -1;
				if (visiting.contains(material.getTemplateId())) return -1;
				total = total.add(java.math.BigInteger.valueOf(EconomyPrices.materialValue(material)).multiply(java.math.BigInteger.valueOf(component.getQuantity())));
				any = true;
			}
		if (!any) return -1;
		int quantity = recipe.getQuantity() == null ? 1 : Math.max(1, recipe.getQuantity());
		return total.add(java.math.BigInteger.valueOf(quantity - 1)).divide(java.math.BigInteger.valueOf(quantity))
			.min(java.math.BigInteger.valueOf(com.aionemu.gameserver.services.CentralMarketRules.MAX_KINAH)).longValueExact();
	}
}
