package com.aionemu.gameserver.questEngine;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.questEngine.model.*;
import quest.heiron._18602NightmareinShiningArmor;

/** Executes the real handler and native kill helper without World, SQL or networking. */
public class KaligaQuestCreditCheck {
	private static int checks;
	private static class Handler extends _18602NightmareinShiningArmor {
		int updates;
		@Override public void updateQuestStatus(QuestEnv env) { updates++; }
	}
	private static void check(boolean ok, String message) {
		checks++;
		if (!ok) throw new AssertionError(message);
	}
	public static void main(String[] args) throws Exception {
		DataManager.QUEST_DATA = new QuestsData();
		DataManager.NPC_DATA = new com.aionemu.gameserver.dataholders.NpcData() {
			@Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getNpcTemplate(int id) {
				return new com.aionemu.gameserver.model.templates.npc.NpcTemplate();
			}
		};
		var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		var unsafe = (sun.misc.Unsafe) field.get(null);
		Handler registration = new Handler();
		registration.register();
		for (int id : new int[] {217005, 217006})
			check(QuestEngine.getInstance().getQuestNpc(id).getOnKillEvent().contains(18602), "Unregistered Kaliga " + id);
		for (int target : new int[] {217005, 217006, 217002, 217000, 216982, 0}) {
			for (QuestStatus status : new QuestStatus[] {null, QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE}) {
				for (int step : new int[] {0, 1, 2, 3, 4}) {
					Player player = (Player) unsafe.allocateInstance(Player.class);
					player.setQuestStateList(new QuestStateList());
					QuestState state = status == null ? null : new QuestState(18602, status);
					if (state != null) {
						state.setQuestVarById(0, step);
						state.setQuestVarById(1, 7);
						player.getQuestStateList().addQuest(18602, state);
					}
					QuestEnv env = new QuestEnv(null, player, 18602) {
						@Override public int getTargetId() { return target; }
					};
					Handler handler = new Handler();
					boolean expected = (target == 217005 || target == 217006) && status == QuestStatus.START && step == 3;
					check(handler.onKillEvent(env) == expected, "Unexpected kill admission");
					check(handler.updates == (expected ? 1 : 0), "Unexpected status update");
					if (state != null) {
						check(state.getStatus() == (expected ? QuestStatus.REWARD : status), "Unexpected status");
						check(state.getQuestVarById(0) == step && state.getQuestVarById(1) == 7, "Saved progress changed");
					}
					if (expected) check(!handler.onKillEvent(env) && handler.updates == 1, "Duplicate kill credit");
				}
			}
		}
		System.out.println("KALIGA QUEST CREDIT CHECK PASSED: " + checks + " checks");
		System.out.println("OK: " + checks + " Kaliga quest credit checks");
	}
}
