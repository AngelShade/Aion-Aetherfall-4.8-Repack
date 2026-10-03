package com.aionemu.gameserver.dataholders;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SECONDARY_SHOW_DECOMPOSABLE;
import com.aionemu.gameserver.utils.xml.JAXBUtil;
import com.aionemu.gameserver.utils.xml.XmlUtil;

/** Production XML/JAXB/reward/packet regression checks. No database, connections, or live players. */
public final class DecomposableItemsCheck {

	private static int checks;
	private static void check(boolean condition, String message) {
		checks++;
		if (!condition)
			throw new AssertionError(message);
	}

	public static final class FixturePlayer extends Player {
		private Race race;
		private PlayerClass pc;
		private byte level;
		private FixturePlayer() { super(null, null); }
		@Override public Race getRace() { return race; }
		@Override public PlayerClass getPlayerClass() { return pc; }
		@Override public byte getLevel() { return level; }
	}

	public static Player player(Race race, PlayerClass pc, int level) throws Exception {
		var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		var fixture = (FixturePlayer) ((sun.misc.Unsafe) field.get(null)).allocateInstance(FixturePlayer.class);
		fixture.race = race;
		fixture.pc = pc;
		fixture.level = (byte) level;
		return fixture;
	}

	private static DecomposableItemsData fixture(String xml) {
		return JAXBUtil.deserialize("<decomposable_items>" + xml + "</decomposable_items>", DecomposableItemsData.class);
	}

	private static void refused(String xml, String message) {
		try {
			fixture(xml);
		} catch (RuntimeException expected) {
			check(true, message);
			return;
		}
		throw new AssertionError(message);
	}

	public static void main(String[] args) throws Exception {
		Path folder = Path.of("game-server/data/static_data/decomposable_items");
		String schema = folder.resolve("decomposable_items.xsd").toString();
		var xsd = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(Path.of(schema).toFile());
		var files = new ArrayList<>(XmlUtil.listFiles(folder.toFile(), false));
		for (var file : files) {
			xsd.newValidator().validate(new StreamSource(file));
			check(true, "schema: " + file.getName());
		}
		DataManager.ITEM_DATA = (ItemData) JAXBContext.newInstance(ItemData.class).createUnmarshaller()
			.unmarshal(Path.of("game-server/data/static_data/items/item_templates.xml").toFile());
		var data = DecomposableItemsData.load(files, schema);
		Collections.reverse(files);
		var reversed = DecomposableItemsData.load(files, schema);
		check(data.size() == reversed.size() && data.overrideCount() == reversed.overrideCount(), "file-order-independent overrides");
		for (int id : new int[] { 188052649, 188052918, 188053543, 188053544, 188053545, 188053634 }) {
			var info = data.getInfoByItemId(id);
			check(info != null && !info.isSelectable() && !info.isOverride(), "six reviewed boxes use upstream table");
			for (var race : List.of(Race.ELYOS, Race.ASMODIANS)) {
				for (var pc : PlayerClass.values()) {
					for (int level : new int[] { 1, 65, 80 }) {
						var player = player(race, pc, level);
						for (int n = 0; n < 12; n++) {
							var rewards = info.decompose(player);
							if (id >= 188053543 && id <= 188053545) {
								int egg = id == 188053543 ? 190020221 : id == 188053544 ? 190020223 : 190020214;
								check(rewards.size() == 2 && rewards.getFirst().getItemId() == egg && rewards.getFirst().getCount() == 1,
									"guaranteed correct egg");
								check(rewards.getLast().getItemId() == 182007162 && rewards.getLast().getCount() == 200, "guaranteed 200 cherries");
							} else {
								check(rewards.size() == 1 && rewards.getFirst().getCount() == 1, "exactly one reward");
								int reward = rewards.getFirst().getItemId();
								check(id == 188052649 ? reward == 190020174 || reward == 190020180
									: id == 188052918 ? reward >= 187050022 && reward <= 187050025 : reward == 141000001, "reviewed reward ID");
							}
						}
					}
				}
			}
		}
		var p = player(Race.ELYOS, PlayerClass.GLADIATOR, 65);
		var precedence = fixture("<decomposable item_id=\"188052649\" override=\"true\"><set><item id=\"190020180\"/></set></decomposable>"
			+ "<decomposable item_id=\"188052649\"><set><item id=\"190020174\"/></set></decomposable>");
		check(precedence.getInfoByItemId(188052649).decompose(p).getFirst().getItemId() == 190020180, "override beats later regular definition");
		refused("<decomposable item_id=\"188052649\"/><decomposable item_id=\"188052649\"/>", "duplicate regular definitions rejected");
		refused("<decomposable item_id=\"188052649\" override=\"true\"/><decomposable item_id=\"188052649\" override=\"true\"/>", "duplicate overrides rejected");
		refused("<decomposable item_id=\"188052649\"><set><item id=\"1\"/></set></decomposable>", "unknown reward rejected");
		refused("<decomposable item_id=\"188052649\"><set><item id=\"190020174\" count=\"5\" max_count=\"4\"/></set></decomposable>", "inverted local quantity range rejected");
		var onlyOne = fixture("<decomposable item_id=\"188052649\" only_one=\"true\"><set chance=\"90\"><item id=\"190020174\"/></set>"
			+ "<set chance=\"1\"><item id=\"190020180\"/></set></decomposable>").getInfoByItemId(188052649);
		check(onlyOne.getSets().getFirst().getChance() == 1, "rare branch is rolled first");
		var conditional = fixture("<decomposable item_id=\"188052649\" selectable=\"true\"><set race=\"ELYOS\" player_classes=\"GLADIATOR\" min_level=\"50\">"
			+ "<item id=\"190020174\"/></set></decomposable>").getInfoByItemId(188052649);
		check(conditional.getSelectableSet(p) != null, "eligible selection offered");
		check(conditional.getSelectableSet(player(Race.ASMODIANS, PlayerClass.GLADIATOR, 65)) == null, "other faction excluded");
		check(conditional.getSelectableSet(player(Race.ELYOS, PlayerClass.TEMPLAR, 65)) == null, "other class excluded");
		check(conditional.getSelectableSet(player(Race.ELYOS, PlayerClass.GLADIATOR, 49)) == null, "low level excluded");
		var xml = fixture("<decomposable item_id=\"188052649\"><set><item id=\"190020174\" count=\"2\" max_count=\"5\"/></set></decomposable>");
		var ranged = xml.getInfoByItemId(188052649).getSets().getFirst().getItems().getFirst();
		check(ranged.getMinimumCount() == 2 && ranged.getMaximumCount() == 5, "preview retains quantity bounds");
		boolean min = false, max = false;
		for (int n = 0; n < 256; n++) {
			int count = ranged.getCount();
			check(count >= 2 && count <= 5, "inclusive quantity range");
			min |= count == 2; max |= count == 5;
		}
		check(min && max, "quantity endpoints reachable");
		var write = SM_SECONDARY_SHOW_DECOMPOSABLE.class.getDeclaredMethod("writeImpl", com.aionemu.gameserver.network.aion.AionConnection.class);
		write.setAccessible(true);
		for (int result : new int[] { SM_SECONDARY_SHOW_DECOMPOSABLE.GRANTED, SM_SECONDARY_SHOW_DECOMPOSABLE.NOT_GRANTED }) {
			var packet = new SM_SECONDARY_SHOW_DECOMPOSABLE(123456, result);
			var buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
			packet.setBuf(buffer); write.invoke(packet, new Object[] { null });
			check(buffer.position() == 9 && buffer.getInt(0) == 123456 && buffer.getInt(4) == 0 && buffer.get(8) == result,
				"selection reply is 64-bit object ID plus result byte");
		}
		check(SM_SECONDARY_SHOW_DECOMPOSABLE.GRANTED == 1 && SM_SECONDARY_SHOW_DECOMPOSABLE.NOT_GRANTED == 0, "PR200 reviewer return codes");
		System.out.println("OK: " + checks + " production schema/JAXB/reward/packet checks; " + data.size() + " active definitions, "
			+ data.overrideCount() + " overrides. No live server, players or database changed.");
	}
}
