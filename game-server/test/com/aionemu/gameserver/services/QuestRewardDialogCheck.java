package com.aionemu.gameserver.services;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import jakarta.xml.bind.JAXBContext;

import com.aionemu.commons.network.AConnection;
import com.aionemu.commons.network.PacketProcessor;
import com.aionemu.commons.network.packet.BaseClientPacket;
import com.aionemu.gameserver.configs.network.NetworkConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.quest.QuestNpc;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.template.ItemCollecting;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Actual dialog service dispatch and queued packets on inert actors, without sockets or SQL. */
public final class QuestRewardDialogCheck {

	private static final int QUEST = 2105;
	private static final int FOLLOW_UP = 2106;
	private static final int NPC = 203502;
	private static Object unsafe;
	private static int checks;

	private static final class Actor extends Player {
		QuestStateList quests;
		Connection connection;
		Actor() { super(null, null); }
		@Override public QuestStateList getQuestStateList() { return quests; }
		@Override public AionConnection getClientConnection() { return connection; }
		@Override public boolean isOnline() { return true; }
		@Override public PlayerStorage getInventory() { throw new AssertionError("Dialog fallback accessed rewards/inventory"); }
	}

	private static final class Contact extends Npc {
		Contact() { super(null, null, null); }
		@Override public int getNpcId() { return NPC; }
		@Override public int getObjectId() { return 1999982201; }
		@Override public NpcTemplate getObjectTemplate() {
			return new NpcTemplate() { @Override public int getTemplateId() { return NPC; } };
		}
	}

	private static final class Connection extends AionConnection {
		Queue<AionServerPacket> packets;
		Connection() throws IOException { super(null, null); }
	}

	private static final class Key extends SelectionKey {
		private final Selector selector;
		Key(Selector selector) { this.selector = selector; }
		@Override public SelectableChannel channel() { return null; }
		@Override public Selector selector() { return selector; }
		@Override public boolean isValid() { return true; }
		@Override public void cancel() { }
		@Override public int interestOps() { return 0; }
		@Override public SelectionKey interestOps(int operations) { return this; }
		@Override public int readyOps() { return 0; }
	}

	private static final class Handler extends AbstractQuestHandler {
		boolean handled;
		boolean mutate;
		int calls;
		Handler() { super(QUEST); }
		@Override public void register() { }
		@Override public boolean onDialogEvent(QuestEnv env) {
			calls++;
			check(env.getExtendedRewardIndex() == 11, "Extended reward index reaches the handler");
			if (mutate) {
				env.setQuestId(FOLLOW_UP);
				env.setDialogActionId(DialogAction.USE_OBJECT);
			}
			return handled;
		}
	}

	private static void check(boolean value, String message) {
		checks++;
		if (!value)
			throw new AssertionError(message);
	}

	private static Field field(Class<?> type, String name) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type));
	}

	private static Actor actor(Connection connection, QuestStatus status, Integer group) throws Exception {
		Actor player = allocate(Actor.class);
		player.connection = connection;
		player.quests = new QuestStateList();
		if (status != null) {
			QuestState state = new QuestState(QUEST, status);
			state.setQuestVarById(0, 7);
			state.setRewardGroup(group);
			player.quests.addQuest(QUEST, state);
		}
		return player;
	}

	private static void dispatch(Actor player, Contact npc, int action, int quest, Integer page, int packetQuest) throws Exception {
		player.connection.packets.clear();
		QuestState state = player.quests.getQuestState(QUEST);
		QuestStatus status = state == null ? null : state.getStatus();
		Integer group = state == null ? null : state.getRewardGroup();
		int count = state == null ? 0 : state.getCompleteCount();
		DialogService.onDialogSelect(action, player, npc, quest, 11);
		if (page == null) {
			check(player.connection.packets.isEmpty(), "Handled confirmation has no fallback packet");
		} else {
			check(player.connection.packets.size() == 1, "Exactly one dialog packet is sent");
			AionServerPacket packet = player.connection.packets.remove();
			check(packet instanceof SM_DIALOG_WINDOW, "Fallback sends a native dialog window");
			int actualPage = field(SM_DIALOG_WINDOW.class, "dialogPageId").getInt(packet);
			check(actualPage == page, "Expected dialog page " + page + ", got " + actualPage);
			check(field(SM_DIALOG_WINDOW.class, "questId").getInt(packet) == packetQuest, "Expected packet quest " + packetQuest);
			check(field(SM_DIALOG_WINDOW.class, "targetObjectId").getInt(packet) == npc.getObjectId(), "NPC object is preserved");
		}
		check(state == null || state.getStatus() == status && state.getCompleteCount() == count
			&& state.getQuestVarById(0) == 7 && Objects.equals(state.getRewardGroup(), group), "Fallback preserves quest state and completion count");
	}

	@SuppressWarnings("unchecked")
	private static void stopProcessor(PacketProcessor<AionConnection> processor, Connection connection) throws Exception {
		List<Thread> threads = (List<Thread>) field(PacketProcessor.class, "threads").get(processor);
		for (Thread thread : threads)
			thread.interrupt();
		// The worker waits uninterruptibly until a packet wakes it, then observes the interrupt.
		field(AConnection.class, "pendingPackets").set(connection, new ArrayDeque<>());
		BaseClientPacket<AionConnection> wake = new BaseClientPacket<>(0) {
			@Override protected void readImpl() { }
			@Override protected void runImpl() { }
			@Override public void run() { }
		};
		wake.setConnection(connection);
		processor.executePacket(wake);
		for (Thread thread : threads) {
			thread.join(1000);
			check(!thread.isAlive(), "Isolated packet worker terminates");
		}
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		unsafe = field(Class.forName("sun.misc.Unsafe"), "theUnsafe").get(null);
		QuestEngine engine = QuestEngine.getInstance();
		Map<Integer, AbstractQuestHandler> handlers = (Map<Integer, AbstractQuestHandler>) field(QuestEngine.class, "questHandlers").get(engine);
		Map<Integer, QuestNpc> npcs = (Map<Integer, QuestNpc>) field(QuestEngine.class, "questNpcs").get(engine);
		AbstractQuestHandler previousHandler = handlers.get(QUEST);
		QuestNpc previousNpc = npcs.get(NPC);
		QuestsData previousData = DataManager.QUEST_DATA;
		String[] configNames = {"PACKET_PROCESSOR_MIN_THREADS", "PACKET_PROCESSOR_MAX_THREADS", "PACKET_PROCESSOR_THREAD_SPAWN_THRESHOLD",
			"PACKET_PROCESSOR_THREAD_KILL_THRESHOLD", "PACKET_PROCESSOR_MAX_PENDING_PACKETS_PER_CONNECTION"};
		int[] previousConfig = new int[configNames.length];
		for (int i = 0; i < configNames.length; i++) {
			Field config = field(NetworkConfig.class, configNames[i]);
			previousConfig[i] = config.getInt(null);
			config.setInt(null, 1);
		}
		Connection connection = null;
		PacketProcessor<AionConnection> processor = null;
		try (Selector selector = Selector.open()) {
			DataManager.QUEST_DATA = (QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader(
				"<quests><quest id='2105' name='Sparkle and Shine' category='QUEST'><rewards exp='0'/></quest></quests>"));
			connection = allocate(Connection.class);
			processor = (PacketProcessor<AionConnection>) field(AionConnection.class, "packetProcessor").get(null);
			connection.packets = new ArrayDeque<>();
			field(AionConnection.class, "sendMsgQueue").set(connection, connection.packets);
			field(AConnection.class, "guard").set(connection, new Object());
			field(AConnection.class, "key").set(connection, new Key(selector));
			Contact npc = allocate(Contact.class);
			QuestNpc questNpc = new QuestNpc(NPC);
			npcs.put(NPC, questNpc);
			ItemCollecting collecting = new ItemCollecting(QUEST, List.of(NPC), 0, null, null, 0, 0, 0, 0, 0);
			handlers.put(QUEST, collecting);
			Actor reportedPlayer = actor(connection, QuestStatus.COMPLETE, null);
			reportedPlayer.quests.addQuest(FOLLOW_UP, new QuestState(FOLLOW_UP, QuestStatus.START));
			questNpc.getOnTalkEvent().add(FOLLOW_UP);
			check(!collecting.onDialogEvent(new QuestEnv(npc, reportedPlayer, QUEST, DialogAction.SELECTED_QUEST_NOREWARD)),
				"Completed ItemCollecting quest declines a repeated confirmation");
			dispatch(reportedPlayer, npc, DialogAction.SELECTED_QUEST_NOREWARD, QUEST, 10, 0);
			questNpc.getOnTalkEvent().clear();
			for (int action = DialogAction.SELECTED_QUEST_REWARD1; action <= DialogAction.SELECTED_QUEST_NOREWARD; action++) {
				Actor player = actor(connection, QuestStatus.COMPLETE, null);
				player.quests.addQuest(FOLLOW_UP, new QuestState(FOLLOW_UP, QuestStatus.START));
				questNpc.getOnTalkEvent().add(FOLLOW_UP);
				check(!collecting.onDialogEvent(new QuestEnv(npc, player, QUEST, action)), "Completed ItemCollecting confirmation is declined without exception");
				dispatch(player, npc, action, QUEST, 10, 0);
				dispatch(player, npc, action, QUEST, 10, 0);
				questNpc.getOnTalkEvent().clear();
				dispatch(player, npc, action, QUEST, 0, 0);
			}
			Handler handler = new Handler();
			handlers.put(QUEST, handler);
			for (int action = DialogAction.SELECTED_QUEST_REWARD1; action <= DialogAction.SELECTED_QUEST_NOREWARD; action++) {
				for (Integer group : new Integer[] {null, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}) {
					int page = DialogPage.getRewardPageByIndex(group).id();
					dispatch(actor(connection, QuestStatus.REWARD, group), npc, action, QUEST, page, page == 0 ? 0 : QUEST);
				}
				for (QuestStatus status : new QuestStatus[] {null, QuestStatus.START, QuestStatus.LOCKED})
					dispatch(actor(connection, status, null), npc, action, QUEST, 0, 0);
			}
			for (int action : new int[] {DialogAction.SELECTED_QUEST_REWARD1 - 1, DialogAction.SELECTED_QUEST_NOREWARD + 1, DialogAction.SELECT1})
				dispatch(actor(connection, QuestStatus.COMPLETE, null), npc, action, QUEST, action, QUEST);
			dispatch(actor(connection, QuestStatus.COMPLETE, null), npc, DialogAction.SELECTED_QUEST_NOREWARD, 0, DialogAction.SELECTED_QUEST_NOREWARD, 0);
			handler.handled = true;
			dispatch(actor(connection, QuestStatus.REWARD, 0), npc, DialogAction.SELECTED_QUEST_NOREWARD, QUEST, null, 0);
			handler.handled = false;
			handler.mutate = true;
			dispatch(actor(connection, QuestStatus.REWARD, 4), npc, DialogAction.SELECTED_QUEST_NOREWARD, QUEST, 45, QUEST);
			dispatch(actor(connection, QuestStatus.COMPLETE, null), npc, DialogAction.SELECTED_QUEST_NOREWARD, QUEST, 0, 0);
			check(handler.calls > 0, "Registered quest handler is dispatched");
			System.out.println("OK: " + checks + " quest reward dialog checks through DialogService.onDialogSelect; no sockets/SQL/reward grants");
		} finally {
			if (processor != null)
				stopProcessor(processor, connection);
			for (int i = 0; i < configNames.length; i++)
				field(NetworkConfig.class, configNames[i]).setInt(null, previousConfig[i]);
			DataManager.QUEST_DATA = previousData;
			if (previousHandler == null) handlers.remove(QUEST); else handlers.put(QUEST, previousHandler);
			if (previousNpc == null) npcs.remove(NPC); else npcs.put(NPC, previousNpc);
		}
	}
}
