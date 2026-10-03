package playercommands;

import java.util.List;
import java.util.Locale;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Order;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/** Ordinary players control only their own companions. */
public final class Bot extends PlayerCommand {
	public Bot() {
		super("bot", "Recruit and control your PvE player companions.", """
			list - List your offline characters and active companion status.
			add <name> - Recruit an offline character from your account into your party.
			create <name> <starting-class> - Create a normal level-1 character and recruit if eligible.
			generate <name> <class> - Create a companion at your level in a separate roster, without a character slot.
			dismiss <name|all> - Save and dismiss companions.
			follow|stay|guard|passive [name|all] - Change companion orders (default all).
			attack [name|all] - Attack your selected hostile PvE NPC.
			role <name> <tank|healer|support|melee|ranged> - Set combat role.
			aoe <on|off> [name|all] - Enable or disable area attacks (default off).
			supplies <on|off> [name|all] - Use the companion's recovery consumables (default on).
			gear <on|off> [name|all] - Automatically equip compatible upgrades from the bot's cube (default off).
			loot <on|off> [name|all] - Collect eligible nearby corpse loot after combat (default off; rolls/bids pass).
			inventory <name> - List the companion's items and object IDs.
			equip <name> <item-object-id> <slot> - Equip a cube item using normal character rules.
			quests <name> - Show the companion's active quest progress.
			mission <name> <quest-id|0> - Walk to supported same-map objectives and turn-in NPCs; 0 cancels.
			share <quest-id> [name|all] - Share one of your eligible quests with companions.
			questing <on|off> [name|all] - Accept your ordinary quests nearby and turn in completed bot quests (default off).
			""");
	}

	@Override public void execute(Player owner, String... params) {
		PlayerBotService service = PlayerBotService.getInstance();
		try {
			if (params.length == 0 || params[0].equalsIgnoreCase("help")) { sendInfo(owner); return; }
			switch (params[0].toLowerCase(Locale.ROOT)) {
				case "list", "status" -> {
					for (var session : service.companions(owner)) sendInfo(owner, session.describe());
					for (var data : owner.getAccount().getPlayerAccDataList()) {
						var c = data.getPlayerCommonData();
						if (c.getPlayerObjId() != owner.getObjectId()) sendInfo(owner, c.getName() + " Lv" + c.getLevel() + " " + c.getPlayerClass()
							+ (PlayerBotLease.isReserved(c.getPlayerObjId()) ? " (reserved)" : " (available if eligible)"));
					}
					for (var generated : PlayerBotRoster.list(owner.getAccount().getId()))
						sendInfo(owner, "Generated: " + (generated.name() == null ? "id " + generated.id() : generated.name())
							+ (generated.ready() ? " (available if eligible)" : " (pending creation; check server logs)"));
				}
				case "add", "recruit" -> { require(params, 2); service.recruit(owner, params[1]); }
				case "create" -> { require(params, 3); service.create(owner, params[1], PlayerClass.valueOf(params[2].toUpperCase(Locale.ROOT))); }
				case "generate" -> { require(params, 3); service.generate(owner, params[1], PlayerClass.valueOf(params[2].toUpperCase(Locale.ROOT))); }
				case "dismiss", "remove" -> {
					require(params, 2);
					if (params[1].equalsIgnoreCase("all")) service.dismissAll(owner); else service.dismiss(owner, params[1]);
				}
				case "follow", "stay", "guard", "passive" -> {
					Order order = Order.valueOf(params[0].toUpperCase(Locale.ROOT));
					for (var session : selected(service, owner, params.length > 1 ? params[1] : "all")) session.order(order);
					sendInfo(owner, "Companion order: " + order + ".");
				}
				case "attack" -> {
					for (var session : selected(service, owner, params.length > 1 ? params[1] : "all")) session.attackSelectedTarget();
					sendInfo(owner, "Companions will attack your selected PvE target.");
				}
				case "role" -> { require(params, 3); service.find(owner, params[1]).setRole(Role.valueOf(params[2].toUpperCase(Locale.ROOT))); sendInfo(owner, "Companion role updated."); }
				case "inventory" -> { require(params, 2); service.find(owner, params[1]).inventory().forEach(line -> sendInfo(owner, line)); }
				case "quests" -> {
					require(params, 2);
					for (var quest : service.find(owner, params[1]).bot().getQuestStateList().getAllQuestState())
						if (quest.getStatus() != com.aionemu.gameserver.questEngine.model.QuestStatus.COMPLETE)
							sendInfo(owner, "Quest " + quest.getQuestId() + " " + quest.getStatus() + " progress=" + quest.getQuestVars().getQuestVars());
				}
				case "share" -> {
					if (params.length < 2 || params.length > 3) throw new IllegalArgumentException("Use .bot share <quest-id> [name|all].");
					int id = Integer.parseInt(params[1]);
					for (var session : selected(service, owner, params.length > 2 ? params[2] : "all"))
						sendInfo(owner, session.bot().getName() + (service.acceptSharedQuest(owner, session.bot(), id) ? " accepted quest " : " cannot accept quest ") + id + ".");
				}
				case "mission" -> { require(params, 3); service.find(owner, params[1]).mission(Integer.parseInt(params[2])); sendInfo(owner, "Companion mission updated."); }
				case "equip" -> {
					require(params, 4);
					service.find(owner, params[1]).equip(Integer.parseInt(params[2]), com.aionemu.gameserver.model.items.ItemSlot.valueOf(params[3].toUpperCase(Locale.ROOT)));
					sendInfo(owner, "Companion equipment updated.");
				}
				case "aoe", "supplies", "gear", "loot", "questing" -> {
					if (params.length < 2 || params.length > 3) throw new IllegalArgumentException("Use .bot " + params[0] + " on|off [name|all].");
					if (!params[1].equalsIgnoreCase("on") && !params[1].equalsIgnoreCase("off")) throw new IllegalArgumentException("Use .bot " + params[0] + " on|off [name|all].");
					for (var session : selected(service, owner, params.length > 2 ? params[2] : "all")) {
						if (params[0].equalsIgnoreCase("aoe")) session.setAreaSkills(params[1].equalsIgnoreCase("on"));
						else if (params[0].equalsIgnoreCase("supplies")) session.setConsumables(params[1].equalsIgnoreCase("on"));
						else if (params[0].equalsIgnoreCase("gear")) session.setAutoGear(params[1].equalsIgnoreCase("on"));
						else if (params[0].equalsIgnoreCase("questing")) session.setQuesting(params[1].equalsIgnoreCase("on"));
						else session.setAutoLoot(params[1].equalsIgnoreCase("on"));
					}
					sendInfo(owner, "Companion " + params[0] + ": " + params[1] + ".");
				}
				default -> sendInfo(owner);
			}
		} catch (IllegalArgumentException e) { sendInfo(owner, e.getMessage() + " Use .bot help."); }
		catch (IllegalStateException e) {
			org.slf4j.LoggerFactory.getLogger(Bot.class).error("Companion command failed for {}", owner.getObjectId(), e);
			sendInfo(owner, "The companion request could not be completed. Check the server logs before retrying.");
		}
	}

	private static List<PlayerBotSession> selected(PlayerBotService service, Player owner, String name) {
		List<PlayerBotSession> result = name.equalsIgnoreCase("all") ? service.companions(owner) : List.of(service.find(owner, name));
		if (result.isEmpty()) throw new IllegalArgumentException("Recruit a companion with .bot add <name> first.");
		return result;
	}
	private static void require(String[] params, int count) { if (params.length != count) throw new IllegalArgumentException("Invalid command arguments."); }
}
