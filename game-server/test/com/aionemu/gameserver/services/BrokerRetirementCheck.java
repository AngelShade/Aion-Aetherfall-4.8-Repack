package com.aionemu.gameserver.services;

import java.nio.file.*;
import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.services.custom.AuctionHouseSimulator;

/**
 * gameserver.broker.enabled = false retires the native NPC Broker in favour of the Central Market.
 * Config, request gates, generated-stock removal bounds and the client window swap. Runs from game-server; no World/SQL.
 */
public final class BrokerRetirementCheck {
	private static int checks;

	/** The core's CORE_VERSION is this version or a later one (later cores keep the forwarding). */
	private static boolean atLeast(String core, String version) {
		var m = java.util.regex.Pattern.compile("CORE_VERSION\\[\\]=\"(\\d+)\\.(\\d+)\\.(\\d+)\"").matcher(core);
		if (!m.find())
			return false;
		String[] want = version.split("\\.");
		for (int i = 0; i < 3; i++) {
			int have = Integer.parseInt(m.group(i + 1)), need = Integer.parseInt(want[i]);
			if (have != need)
				return have > need;
		}
		return true;
	}

	private static void check(boolean value, String why) {
		checks++;
		if (!value)
			throw new AssertionError(why);
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1))
			n++;
		return n;
	}

	public static void main(String[] args) throws Exception {
		var property = CustomConfig.class.getField("BROKER_ENABLED").getAnnotation(Property.class);
		check(property != null && property.key().equals("gameserver.broker.enabled") && property.defaultValue().equals("true"),
			"The switch is gameserver.broker.enabled and an unconfigured server keeps the native Broker");
		CustomConfig.BROKER_ENABLED = true;
		check(BrokerService.enabled(), "Enabled follows the switch");
		CustomConfig.BROKER_ENABLED = false;
		check(!BrokerService.enabled(), "Disabled follows the switch");
		check(!RemoteBrokerService.canAccess(null, 1), "No player, no access");

		for (int seller = AuctionHouseSimulator.FIRST_SELLER; seller <= AuctionHouseSimulator.LAST_SELLER; seller++)
			check(AuctionHouseSimulator.isSimulatedSeller(seller), "Every ID in the removal range is a generated seller: " + seller);
		check(!AuctionHouseSimulator.isSimulatedSeller(AuctionHouseSimulator.FIRST_SELLER - 1) && !AuctionHouseSimulator.isSimulatedSeller(AuctionHouseSimulator.LAST_SELLER + 1),
			"The removal range stops exactly at the generated sellers, so no player's listing can match");

		var src = Path.of("src/com/aionemu/gameserver");
		var broker = Files.readString(src.resolve("services/BrokerService.java"));
		var remote = Files.readString(src.resolve("services/RemoteBrokerService.java"));
		var dialog = Files.readString(src.resolve("services/DialogService.java"));
		var sell = Files.readString(src.resolve("network/aion/clientpackets/CM_BROKER_SELL_WINDOW.java"));
		var simulator = Files.readString(src.resolve("services/custom/AuctionHouseSimulator.java"));
		int gate = remote.indexOf("if (!BrokerService.enabled())"), npc = remote.indexOf("isTargetingNpcWithFunction");
		check(gate > 0 && gate < npc, "Remote and NPC Broker requests are refused before NPC access is granted");
		var clientpackets = src.resolve("network/aion/clientpackets");
		for (var name : new String[] { "CM_BROKER_CANCEL_REGISTERED", "CM_BROKER_LIST", "CM_BROKER_REGISTERED", "CM_BROKER_SEARCH", "CM_BROKER_SETTLE_ACCOUNT",
			"CM_BROKER_SETTLE_LIST", "CM_BUY_BROKER_ITEM", "CM_REGISTER_BROKER_ITEM" })
			check(Files.readString(clientpackets.resolve(name + ".java")).contains("RemoteBrokerService.canAccess(player"), name + " passes through the gated access check");
		check(sell.indexOf("if (!BrokerService.enabled())") > 0 && sell.indexOf("if (!BrokerService.enabled())") < sell.indexOf("showSellWindow"), "The sell window, which has no access check, is gated too");
		int vendor = dialog.indexOf("case OPEN_VENDOR:"), vendorEnd = dialog.indexOf("break;", vendor);
		String vendorCase = dialog.substring(vendor, vendorEnd);
		check(vendorCase.contains("if (!BrokerService.enabled() && npc.getObjectTemplate().supportsAction(dialogActionId))")
			&& vendorCase.indexOf("new SM_DIALOG_WINDOW(npc.getObjectId(), 0)") < vendorCase.indexOf("BrokerService.openCentralMarket(player)")
			&& vendorCase.indexOf("BrokerService.openCentralMarket(player)") < vendorCase.indexOf("} else") && vendorCase.indexOf("sendDialogWindow(dialogActionId, player, npc)") > vendorCase.indexOf("} else"),
			"A retired Broker closes the NPC conversation and opens the Central Market; no native Broker page is sent");
		check(!vendorCase.contains("refuse(player)"), "Choosing the Broker at an NPC prints no chat notice");
		int open = remote.indexOf("public static void open(Player player)");
		check(open > 0 && remote.indexOf("BrokerService.openCentralMarket(player);", open) < remote.indexOf("SESSIONS.put(player, System.nanoTime());", open)
			&& remote.indexOf("BrokerService.openCentralMarket(player);", open) > open, "The .broker command also opens the Central Market directly");
		var command = Files.readString(src.resolve("network/aion/serverpackets/SM_WEB_COMMAND.java"));
		check(command.contains("super(SM_SECURITY_TOKEN.class)") && command.contains("writeC(0xAF);") && command.contains("writeB(new byte[] { 'A', 'F', 'W', '1' });")
			&& command.contains("writeC(2);") && command.contains("writeC(command);") && command.contains("writeC(value);") && command.contains("writeB(new byte[21]);"),
			"The command is AFW1 version 2 on the web-session opcode, padded to 29 bytes");
		check(broker.contains("SM_WEB_COMMAND.OPEN_CENTRAL_MARKET, 0)"), "openCentralMarket sends the Central Market command");
		var session = Files.readString(src.resolve("services/player/WebSessionService.java"));
		check(session.contains("SM_WEB_COMMAND.BROKER_STATE, com.aionemu.gameserver.services.BrokerService.enabled() ? 1 : 0)")
			&& session.indexOf("BROKER_STATE") < session.indexOf("new SM_WEB_CONNECTION("), "Every web-session message tells the client the Broker state first");
		check(!broker.contains("refuse(") && !remote.contains("refuse(") && !sell.contains("refuse("), "A refused Broker request is silent; no notice code is left");
		check(count(dialog, "case OPEN_VENDOR:") == 1, "OPEN_VENDOR is handled once");
		int ctor = broker.indexOf("private BrokerService() {");
		check(broker.indexOf("if (!CustomConfig.BROKER_ENABLED)\n\t\t\tretireGeneratedStock();", ctor) > 0 || broker.indexOf("if (!CustomConfig.BROKER_ENABLED)\r\n\t\t\tretireGeneratedStock();", ctor) > 0,
			"Generated stock is removed only when the Broker is retired");
		check(broker.contains("if (CustomConfig.BROKER_ENABLED)") && broker.indexOf("scheduleAtFixedRate(this::checkExpiredItems") > broker.indexOf("if (CustomConfig.BROKER_ENABLED)"),
			"A retired Broker expires nothing, so real listings wait unchanged");
		check(broker.contains("\"seller_id BETWEEN \" + AuctionHouseSimulator.FIRST_SELLER + \" AND \" + AuctionHouseSimulator.LAST_SELLER")
			&& broker.contains("i.item_owner = b.seller_id AND i.item_location = \" + StorageType.BROKER.getId()"),
			"Removal touches only generated sellers' rows and their own Broker-stored items");
		check(broker.contains("connection.setAutoCommit(false)") && broker.contains("connection.rollback()") && broker.contains("connection.commit()"), "Removal is one transaction");
		check(broker.indexOf("item_stones") < broker.indexOf("DELETE i FROM inventory") && broker.indexOf("DELETE i FROM inventory") < broker.indexOf("DELETE FROM broker WHERE"),
			"Child rows go first: stones, items, then listings");
		check(simulator.contains("if (!com.aionemu.gameserver.configs.main.CustomConfig.BROKER_ENABLED)"), "Generated stock is not seeded for a retired Broker");

		var config = Files.readString(Path.of("config/main/custom.properties"));
		check(config.contains("gameserver.broker.enabled = false"), "Aetherfall retires the Broker in its shipped configuration");
		var options = Files.readString(Path.of("../client-mods/aetherfall-core/AetherfallOptions.lua"));
		var coreLua = Files.readString(Path.of("../client-mods/aetherfall-core/AetherfallCore.lua"));
		check(!options.contains("BrokerToMarket") && !options.contains("takemarket"), "The HUD Lua holds no Broker swap code");
		check(!coreLua.contains("takemarket") && !coreLua.contains("brokerstate"), "The core Lua API is unchanged; the module calls the bridge itself");
		var module = Files.readString(Path.of("../client-mods/broker-swap/BrokerSwap.lua"));
		check(module.contains("tonumber((core('brokerstate')))") && module.contains("if state == nil then return end"), "The module stays inert on a core without the command");
		check(module.contains("local show = state == 1"), "The button stays hidden until the server says the Broker is enabled");
		check(module.contains("core('widgetshown', id, BUTTON, show)") && module.contains("local BUTTON = 'aetherfall_broker_button'"), "Only the HUD Broker button is hidden or shown");
		check(module.contains("if core('takemarket') == true then openMarket() end") && module.contains("PrivateWarehouse_Open()"), "The module opens the Central Market when the server asks");
		check(module.contains("local ok, why = pcall(B.Tick)"), "A module error never reaches the plugin");
		check(!module.contains("vendor_dialog") && !module.contains("SetShown"), "No native Broker window is ever opened or hidden");
		var moduleXml = Files.readString(Path.of("../client-mods/broker-swap/BrokerSwap.xml"));
		check(moduleXml.contains("name=\"BrokerSwapTimer\"") && moduleXml.contains("flag=\"not_visible\"") && moduleXml.contains("BrokerSwap_OnEvent(event);"), "The module runs from its own hidden window");
		var core = Files.readString(Path.of("../client-mods/aetherfall-core/aetherfall_core.cpp"));
		check(core.contains("if(is(cmd,\"takemarket\"))") && core.contains("GetProcAddress(bridge,\"AionTakeMarketOpen\")") && atLeast(core,"1.3.8"),
			"Native core 1.3.8 or later forwards the request from the icon bridge");
		check(core.contains("if(is(cmd,\"brokerstate\"))") && core.contains("GetProcAddress(bridge,\"AionBrokerState\")") && core.contains("return push(L,double(state?state():-1));"),
			"The core reports the Broker state, -1 without a bridge");
		int shown = core.indexOf("if(is(cmd,\"widgetshown\"))");
		check(shown > 0 && core.indexOf("if(!w || !v)return boolean(L,false);", shown) > shown, "Showing a missing widget is refused, not attempted");
		var bridge = Files.readString(Path.of("../client-mods/native-icon-bridge/icon_bridge.cpp"));
		int command2 = bridge.indexOf("body[5]==2"), metadata = bridge.indexOf("web_metadata.received=false;}", command2);
		check(command2 > 0 && metadata > command2 && bridge.indexOf("return 0;", command2) < metadata,
			"The bridge consumes a command before, and without touching, the stored connection metadata");
		check(bridge.contains("AionTakeMarketOpen()") && bridge.contains("market_open_requested.exchange(0)") && bridge.contains("GetTickCount64()-at<10000"),
			"Each request is handed over once and expires after 10 seconds");
		check(bridge.contains("std::atomic<int> broker_state{-1};") && bridge.contains("else if(body[6]==2 && body[7]<=1){broker_state.store(body[7]);")
			&& bridge.contains("int __cdecl AionBrokerState(){return broker_state.load();}"), "The bridge keeps the server's Broker state and starts unknown");
		var menus = Files.readString(Path.of("../client-mods/transmog-menu/PrivateMenus.lua"));
		check(menus.contains("PrivateWarehouse:SetText(\"Central Market\");"), "The window the Broker NPC opens is titled Central Market");
		System.out.println("OK: " + checks + " broker retirement config/gate/stock removal/Broker Swap module checks; no World/SQL");
	}
}
