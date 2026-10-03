package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.questEngine.model.QuestEnv;

/** Captures only the native dialog emitted during one synchronous companion interaction. */
public final class PlayerBotQuestDialog implements AutoCloseable {
	private static final ThreadLocal<PlayerBotQuestDialog> ACTIVE = new ThreadLocal<>();
	private final int player, npc, quest;
	private Integer page;
	private PlayerBotQuestDialog(int player, int npc, int quest) {
		this.player = player; this.npc = npc; this.quest = quest;
	}
	static PlayerBotQuestDialog open(int player, int npc, int quest) {
		if (ACTIVE.get() != null) throw new IllegalStateException("Nested companion quest interaction");
		var dialog = new PlayerBotQuestDialog(player, npc, quest);
		ACTIVE.set(dialog); return dialog;
	}
	public static void observe(QuestEnv env, int npc, int page, int quest) {
		if (env.getPlayer().isPlayerBot()) record(env.getPlayer().getObjectId(), npc, page, quest);
	}
	static void record(int player, int npc, int page, int quest) {
		var dialog = ACTIVE.get();
		if (dialog != null && dialog.player == player && dialog.npc == npc && dialog.quest == quest) dialog.page = page;
	}
	boolean offered(int expectedPage) { return page != null && page == expectedPage && expectedPage > 0; }
	@Override public void close() { if (ACTIVE.get() == this) ACTIVE.remove(); }
}
