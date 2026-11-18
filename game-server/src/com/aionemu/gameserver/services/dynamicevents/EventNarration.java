package com.aionemu.gameserver.services.dynamicevents;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.EmotionId;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/** Speech and notices. The Warden speaks as itself (chat bubble and shout), the boss as itself, bots as themselves. */
final class EventNarration {

	static String direction(float dx, float dy) {
		int octant = Math.floorMod((int) Math.round(Math.atan2(dy, dx) / (Math.PI / 4)), 8);
		return List.of("east", "northeast", "north", "northwest", "west", "southwest", "south", "southeast").get(octant);
	}

	/** "to the northeast" from the event's start to the camp. */
	static String place(DynamicEvent.Spot from, DynamicEvent.Spot camp) {
		return "to the " + direction(camp.x() - from.x(), camp.y() - from.y());
	}

	static String render(String text, String warden, String road, String boss, String enemy, String place, int count) {
		return text.replace("{warden}", warden).replace("{road}", road).replace("{boss}", boss).replace("{enemy}", enemy).replace("{place}", place)
			.replace("{count}", Integer.toString(count));
	}

	static String render(DynamicEvent e, String text) {
		var a = e.anchor;
		return render(text, a.wardenName(), a.road(), e.pool.boss() == null ? "the raid leader" : e.pool.boss().getName(), plural(e.pool.enemy()),
			place(a.start(), e.pool.camp()), e.required);
	}

	/** Native monster names are singular ("Krall Raider"); a crowd reads better plural. */
	static String plural(String name) {
		if (name.isEmpty() || name.endsWith("s"))
			return name;
		return name.endsWith("y") && !name.endsWith("ey") ? name.substring(0, name.length() - 1) + "ies" : name + "s";
	}

	static String speaker(String line) {
		return line.substring(0, line.indexOf('|'));
	}

	static String text(String line) {
		return line.substring(line.indexOf('|') + 1);
	}

	/** Each line is sent at once, in order; the audience is every player in the event area. */
	static void say(DynamicEvent e, List<String> lines, Collection<Player> audience) {
		for (String line : lines)
			say(e, line, audience);
	}

	static void say(DynamicEvent e, String line, Collection<Player> audience) {
		String text = render(e, text(line));
		switch (speaker(line)) {
			case "notice" -> {
				for (Player p : audience) {
					PacketSendUtility.sendPacket(p, new SM_MESSAGE(0, null, text, ChatType.BRIGHT_YELLOW_CENTER));
					PacketSendUtility.sendMessage(p, "[Event] " + text);
				}
			}
			case "warden" -> {
				Player warden = World.getInstance().getPlayer(e.anchor.warden());
				if (warden != null && warden.isSpawned() && !warden.isDead())
					for (Player p : audience)
						PacketSendUtility.sendPacket(p, new SM_MESSAGE(warden, text, ChatType.SHOUT));
			}
			case "boss" -> {
				Npc boss = e.boss == 0 ? null : World.getInstance().findVisibleObject(e.boss) instanceof Npc n ? n : null;
				String name = e.pool.boss() == null ? "Raid Leader" : e.pool.boss().getName();
				for (Player p : audience)
					PacketSendUtility.sendPacket(p, boss != null && !boss.isDead() ? new SM_MESSAGE(boss, text, ChatType.SHOUT) : new SM_MESSAGE(0, name, text, ChatType.SHOUT));
			}
			case "bots" -> {
				var speakers = new ArrayList<Player>();
				for (int id : e.recruits) {
					Player bot = World.getInstance().getPlayer(id);
					if (bot != null && bot.isSpawned() && !bot.isDead())
						speakers.add(bot);
				}
				if (!speakers.isEmpty()) {
					Player bot = speakers.get(ThreadLocalRandom.current().nextInt(speakers.size()));
					for (Player p : audience)
						PacketSendUtility.sendPacket(p, new SM_MESSAGE(bot, text, ChatType.NORMAL));
				}
			}
			default -> throw new IllegalStateException("validated speaker");
		}
	}

	/** A short native gesture by the Warden, seen by everyone around it. */
	static void gesture(DynamicEvent e, EmotionId emotion) {
		Player warden = World.getInstance().getPlayer(e.anchor.warden());
		if (warden != null && warden.isSpawned() && !warden.isDead())
			PacketSendUtility.broadcastToSightedPlayers(warden,
				new SM_EMOTION(warden, EmotionType.EMOTE, emotion.id(), warden.getX(), warden.getY(), warden.getZ(), warden.getHeading(), 0), true);
	}

	/** The line of the stage's progress list for a reached quarter (1-3), spread evenly over the list; null when none fits. */
	static String quarterLine(List<String> progress, int quarter) {
		if (progress.isEmpty() || quarter < 1 || quarter > 3)
			return null;
		int index = (int) Math.floor((quarter - 1) * progress.size() / 3d);
		int previous = quarter == 1 ? -1 : (int) Math.floor((quarter - 2) * progress.size() / 3d);
		return index == previous ? null : progress.get(index);
	}

	private EventNarration() {
	}
}
