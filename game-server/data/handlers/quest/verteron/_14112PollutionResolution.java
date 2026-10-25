package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native Q1016 transformation endpoint, adapted to the installed 4.8 Q14112 task table and dialogs. */
public class _14112PollutionResolution extends AbstractQuestHandler {

	private static final int GEOLUS = 203149, LEPIOS = 203148, KATO = 203195, BUBBLEGUT = 210318, MEDICINE = 182215455;

	public _14112PollutionResolution() {
		super(14112);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(GEOLUS).addOnQuestStart(questId);
		for (int npc : new int[] { GEOLUS, LEPIOS, KATO })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		qe.registerQuestNpc(BUBBLEGUT).addOnKillEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, GEOLUS);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != KATO)
				return false;
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT5);
			return sendQuestEndDialog(env);
		}
		if (state.getStatus() != QuestStatus.START || npc != LEPIOS || state.getQuestVarById(0) != 0)
			return false;
		if (action == QUEST_SELECT)
			return sendQuestDialog(env, SELECT2);
		if (action == SELECT2_1)
			return sendQuestDialog(env, action);
		if (action == SETPRO1) {
			if (player.getInventory().getItemCountByItemId(MEDICINE) == 0 && !giveQuestItem(env, MEDICINE, 1))
				return false;
			state.setQuestVarById(1, 1); // Keep section 0 for the installed client's kill counter.
			updateQuestStatus(env);
			return closeDialogWindow(env);
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.getQuestVarById(1) != 1 || env.getTargetId() != BUBBLEGUT || player.getWorldId() != 210030000
			|| state.getStatus() != QuestStatus.START && state.getStatus() != QuestStatus.REWARD)
			return false;
		Npc corpse = (Npc) env.getVisibleObject();
		// Q1016 places Kato at this defeated monster, rather than at invented static coordinates.
		// Repeating the kill also recovers the endpoint after logout or its bounded lifetime.
		if (player.getKnownList().findObject(known -> known.get() instanceof Npc npc && npc.getNpcId() == KATO
			&& npc.isSpawned() && !npc.isDead() && PositionUtil.isInRange(corpse, npc, 15)) == null)
			spawnForFiveMinutes(KATO, corpse.getPosition(), (byte) 0);
		if (state.getStatus() == QuestStatus.START) {
			state.setQuestVarById(0, 1);
			state.setStatus(QuestStatus.REWARD);
			updateQuestStatus(env);
		}
		return true;
	}
}
