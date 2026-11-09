package com.aionemu.gameserver.services;

import java.nio.file.Files;
import java.nio.file.Path;

import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.services.trade.MarketPolicy;

/** Central Market player feedback (owner, 9 Nov): removals, price factors, cash-shop goods outside generated quotes,
 * buyable generated enchanted stock, Bootleg gear valuation, one-time re-appraisal, the second box pass and the Black Cloud
 * catalogue changes. Static data, no World/SQL. */
public final class CentralMarketFeedbackCheck {
	static int checks;
	static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

	public static void main(String[] args) throws Exception {
		var root = args.length > 0 ? Path.of(args[0]) : Path.of(".");
		rules();
		wiring(root);
		bootleg();
		System.out.println("OK: " + checks + " Central Market feedback checks; no World/SQL/tasks");
	}

	static void rules() {
		check(MarketPolicy.excluded(162000028), "The all-ailment potion leaves the market (Black Cloud does not sell it)");
		check(!MarketPolicy.excluded(166000050) && !MarketPolicy.excluded(141000001) && !MarketPolicy.excluded(188052083),
			"L-stones, Stigma Shards and GM items stay on the Central Market; the feedback removed them from Black Cloud");
		check(MarketPolicy.factor(true, ItemQuality.MYTHIC, 65) == 10, "Level 65 mythic gear costs ten times");
		check(MarketPolicy.factor(true, ItemQuality.MYTHIC, 64) == 1 && MarketPolicy.factor(true, ItemQuality.EPIC, 65) == 1
			&& MarketPolicy.factor(false, ItemQuality.MYTHIC, 65) == 1, "Only level 65 mythic gear");
		check(MarketPolicy.generated(0) && !MarketPolicy.generated(80), "Generated quotes skip Black Cloud Marketplace goods");
		check(CentralMarketRules.maxQuantity(false, 1) == 1, "Items that do not stack trade one at a time");
	}

	static void wiring(Path root) throws Exception {
		var src = root.resolve("src/com/aionemu/gameserver/services");
		var market = Files.readString(src.resolve("CentralMarketService.java")).replace("\r\n", "\n");
		check(market.contains("&& !com.aionemu.gameserver.services.trade.MarketPolicy.excluded(t);"), "Eligibility applies the potion removal");
		check(market.contains("int[] grade=template==null?null:CentralMarketSimulation.virtualGrade(template,key);")
			&& market.contains("if(grade[0]>0)item.setEnchantLevel(grade[0]);") && market.contains("if(grade[1]>0)item.setTempering(grade[1]);"),
			"Generated enchanted and tempered stock is created with its grade");
		check(!market.contains("!key.equals(template.getTemplateId()+\":0:0\")) throw new SQLException(\"Invalid virtual supply variant\")"), "The plain-only guard is gone");
		check(market.indexOf("migrateEconomy(c);\n\t\t\treappraise(c);") > 0 && market.contains("static final int ECONOMY_VERSION=5;"), "Economy v5 re-appraises the catalogue once, after v2");
		var simulation = Files.readString(src.resolve("CentralMarketSimulation.java"));
		check(simulation.contains("if (t == null || !eligible(t) || !com.aionemu.gameserver.services.trade.MarketPolicy.generated(t)) {"), "No generated asks or bids for cash-shop goods");
		check(simulation.contains("&& com.aionemu.gameserver.services.trade.MarketPolicy.generated(t) &&"), "No generated supply for cash-shop goods");
		check(Files.readString(src.resolve("trade/BoxAppraisal.java")).contains("value(t,revalued,new HashSet<>(),0,known);"), "Boxes are valued again with known content values");
		var prices = Files.readString(src.resolve("trade/EconomyPrices.java"));
		check(prices.contains("double factor = MarketPolicy.factor(item);"), "The mythic factor shapes replacement and every price after it");
		check(prices.contains("ItemTemplate original = MarketPolicy.original(item);"), "Bootleg gear is appraised as its original");
		blackCloud(root);
	}

	/** Every Bootleg weapon and armour piece is appraised as the gear it copies (real static data). */
	static void bootleg() throws Exception {
		EconomyStaticData.load();
		int linked = 0;
		for (var t : com.aionemu.gameserver.dataholders.DataManager.ITEM_DATA.getItemTemplates()) {
			if (!t.getName().startsWith("Bootleg ") || !(t.isWeapon() || t.isArmor())) continue;
			var original = MarketPolicy.original(t);
			check(original != null && !original.getName().startsWith("Bootleg "), "Bootleg gear finds its original: " + t.getTemplateId());
			check(com.aionemu.gameserver.services.trade.EconomyPrices.nativeReference(t) == com.aionemu.gameserver.services.trade.EconomyPrices.nativeReference(original),
				"Bootleg gear takes its original's value: " + t.getTemplateId());
			linked++;
		}
		check(linked == 172, "All 172 Bootleg gear pieces are linked, found " + linked);
		var sword = com.aionemu.gameserver.dataholders.DataManager.ITEM_DATA.getItemTemplate(100001817);
		check(com.aionemu.gameserver.services.trade.EconomyPrices.replacement(sword) >= 30_000_000L, "Bootleg Lunatic Modor's Sword is no longer 339,000 Kinah");
	}

	/** The feedback removals and prices live in the Black Cloud Marketplace catalogue. */
	static void blackCloud(Path root) throws Exception {
		var catalog = Files.readString(root.resolve("config/ingameshop/marketplace_cash.tsv"));
		for (String id : new String[] { "166000050", "166000001", "141000001", "188052083", "188052086", "110900338", "101300992" })
			check(!catalog.contains("\n" + id + "\t"), "Black Cloud no longer sells " + id);
		check(catalog.contains("\n166000193\t"), "Black Cloud keeps the Gamma Enchantment Stone");
		check(catalog.contains("\n167020024\t10\t105000\t"), "Ancient manastones: low offers");
		check(catalog.contains("\n169000005\t500\t16500\t"), "Power Shards below the merchant");
		check(catalog.contains("\n187060103\t1\t6000000\t"), "Level 65 mythic wings cost ten times");
		var tool = Files.readString(root.resolve("config/ingameshop/rebalance_catalog.py"));
		check(tool.contains("return \"player_feedback_removed\"") && tool.contains("POWER_SHARD_MERCHANT_PERCENT = 75"), "The catalogue tool keeps the removals and shard price");
	}

	private CentralMarketFeedbackCheck() {}
}
