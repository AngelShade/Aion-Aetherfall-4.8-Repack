package quest.beluslan;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Original Java Q2057 mechanics; installed 4.8 Q24153 counters/items and native cast custody. */
public class _24153TheEfficacyOfFire extends BeluslanQuestItem {
	private static final int AKAGITAN = 204787, DELRIS = 204784, BOMB = 182215462;
	private static final int[] TARGETS = { 213730, 213788, 213789, 213790, 213791 };

	public _24153TheEfficacyOfFire() {
		super(24153);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(AKAGITAN).addOnQuestStart(questId);
		for (int npc : new int[] { AKAGITAN, DELRIS })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		for (int npc : TARGETS)
			qe.registerQuestNpc(npc).addOnKillEvent(questId);
		qe.registerQuestItem(BOMB, questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable()) {
			boolean result = sendQuestNoneDialog(env, AKAGITAN);
			QuestState started = env.getPlayer().getQuestStateList().getQuestState(questId);
			if (started != null && started.getStatus() == QuestStatus.START) {
				started.setQuestVarById(5, 1); // Counts 0..4 remain native; section 5 blocks hunting until the bomb.
				updateQuestStatus(env);
			}
			return result;
		}
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != AKAGITAN)
				return false;
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 2375);
			return sendQuestEndDialog(env);
		}
		if (state.getStatus() != QuestStatus.START || state.getQuestVarById(5) != 1 || npc != DELRIS)
			return false;
		if (action == QUEST_SELECT)
			return sendQuestDialog(env, 1352);
		if (action == SELECT2_1 || action == SELECT2_1_1)
			return sendQuestDialog(env, action);
		if (action == SETPRO2) {
			if (env.getPlayer().getInventory().getItemCountByItemId(BOMB) == 0 && !giveQuestItem(env, BOMB, 1))
				return false;
			return closeDialogWindow(env);
		}
		return false;
	}

	@Override
	public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
		if (item.getItemId() != BOMB)
			return HandlerResult.UNKNOWN;
		return HandlerResult.fromBoolean(useBeluslanItem(env, item, "DF3_ITEMUSEAREA_Q2057", state -> state.getQuestVarById(5) == 1,
			248, captured -> {
				QuestState state = captured.getPlayer().getQuestStateList().getQuestState(questId);
				state.setQuestVarById(5, 0);
				updateQuestStatus(captured);
			}));
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(5) != 0)
			return false;
		for (int i = 0; i < TARGETS.length; i++) {
			if (TARGETS[i] != env.getTargetId() || state.getQuestVarById(i) != 0)
				continue;
			state.setQuestVarById(i, 1);
			boolean complete = true;
			for (int counter = 0; counter < TARGETS.length; counter++)
				complete &= state.getQuestVarById(counter) == 1;
			if (complete)
				state.setStatus(QuestStatus.REWARD);
			updateQuestStatus(env);
			return true;
		}
		return false;
	}
}
