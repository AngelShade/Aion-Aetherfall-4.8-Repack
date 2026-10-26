package quest.altgard;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/**
 * Original Java Q2018 mechanics by Mr. Poke, Gigi, vlog and Majka, adapted to the installed 4.8 Q24114 dialog and collecting step.
 */
public class _24114YouGottaStopUmkata extends AbstractQuestHandler {

	private static final int GIVER = 798033;
	private static final int GULKALLA = 203649;
	private static final int JEWEL_BOX = 700097;
	private static final int GRAVE = 700098;
	private static final int UMKATA = 210752;

	public _24114YouGottaStopUmkata() {
		super(24114);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(GIVER).addOnQuestStart(questId);
		for (int npcId : new int[] { GIVER, GULKALLA, JEWEL_BOX, GRAVE })
			qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
		for (int npcId : new int[] { 210588, 210722, UMKATA })
			qe.registerQuestNpc(npcId).addOnKillEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int targetId = env.getTargetId();
		if (targetId != GIVER && targetId != GULKALLA && targetId != JEWEL_BOX && targetId != GRAVE)
			return false;
		int action = env.getDialogActionId();
		if (qs == null || qs.isStartable()) {
			if (sendQuestNoneDialog(env, GIVER, SELECT_NONE))
				return true;
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == GULKALLA && sendQuestEndDialog(env))
				return true;
		} else if (qs.getStatus() == QuestStatus.START) {
			return onProgressDialog(env, targetId, action, qs.getQuestVarById(0));
		}
		return handleRemainingDialog(env, targetId, action);
	}

	private boolean onProgressDialog(QuestEnv env, int targetId, int action, int var) {
		Player player = env.getPlayer();
		if (targetId == GULKALLA) {
			if (var == 3 || var == 4) {
				if (action == QUEST_SELECT)
					return sendQuestDialog(env, var == 3 ? SELECT1 : SELECT1_1_1_1);
				if (action == SELECT1_1 || action == SELECT1_1_1 || action == SELECT1_1_1_1)
					return sendQuestDialog(env, action);
				if (action == SETPRO1 && var == 3)
					return defaultCloseDialog(env, 3, 4);
			} else if (var == 5) {
				if (action == QUEST_SELECT)
					return sendQuestDialog(env, 1352);
				if (action == SELECT_QUEST_REWARD && changeQuestStep(env, 5, 5, true))
					return sendQuestDialog(env, 5);
			}
		} else if (targetId == JEWEL_BOX && var == 4 && action == USE_OBJECT) {
			return true; // Native object loot supplies the existing quest drop.
		} else if (targetId == GRAVE && var == 4 && player.getWorldId() == 220030000) {
			if (action == USE_OBJECT || action == QUEST_SELECT)
				return sendQuestDialog(env, 1693);
			if (action == CHECK_USER_HAS_QUEST_ITEM) {
				if (!QuestService.collectItemCheck(env, false))
					return closeDialogWindow(env);
				if (!(env.getVisibleObject() instanceof Npc grave))
					return false;
				// One shared encounter at this native grave; repeated clicks cannot pile up summons.
				synchronized (grave) {
					if (grave.getWorldMapInstance().getNpcs(UMKATA).stream().noneMatch(npc -> npc.isSpawned() && !npc.isDead()))
						spawnForFiveMinutes(UMKATA, grave.getWorldMapInstance(), 2889.9834f, 1741.3108f, 254.75f, (byte) 0);
				}
				return closeDialogWindow(env);
			}
			if (action == FINISH_DIALOG)
				return closeDialogWindow(env);
		}
		return handleRemainingDialog(env, targetId, action);
	}

	private boolean handleRemainingDialog(QuestEnv env, int targetId, int action) {
		if (action == USE_OBJECT)
			return false; // Let ordinary NPC menus/object use continue when this quest has no interaction.
		if (action == QUEST_SELECT && (targetId == GIVER || targetId == GULKALLA))
			return sendQuestSelectionDialog(env);
		// Never let an early report, repeated button or stale reward become an HTML page ID.
		return closeDialogWindow(env);
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;
		int var = qs.getQuestVarById(0);
		if (var < 3)
			return defaultOnKillEvent(env, new int[] { 210588, 210722 }, 0, 3);
		if (var == 4 && env.getTargetId() == UMKATA && QuestService.collectItemCheck(env, true))
			return changeQuestStep(env, 4, 5);
		return false;
	}
}
