package quest.beluslan;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Native single-counter hunts with a preceding conversation; the client counter stays in section 0. */
public abstract class BeluslanBriefedHunt extends AbstractQuestHandler {

	private final int giver, contact, rewardNpc, advanceAction, count;
	private final int[] targets;
	private final boolean intermediatePage;

	protected BeluslanBriefedHunt(int questId, int giver, int contact, int rewardNpc, int advanceAction, int count,
		boolean intermediatePage, int... targets) {
		super(questId);
		this.giver = giver;
		this.contact = contact;
		this.rewardNpc = rewardNpc;
		this.advanceAction = advanceAction;
		this.count = count;
		this.intermediatePage = intermediatePage;
		this.targets = targets;
	}

	@Override
	public void register() {
		qe.registerQuestNpc(giver).addOnQuestStart(questId);
		for (int npc : new int[] { giver, contact, rewardNpc })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		for (int npc : targets)
			qe.registerQuestNpc(npc).addOnKillEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, giver);
		int target = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (target != rewardNpc)
				return false;
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 2375);
			return sendQuestEndDialog(env);
		}
		if (state.getStatus() != QuestStatus.START || target != contact || state.getQuestVarById(1) != 0)
			return false;
		if (action == QUEST_SELECT)
			return sendQuestDialog(env, 1352);
		if (intermediatePage && action == SELECT2_1)
			return sendQuestDialog(env, action);
		if (action == advanceAction) {
			state.setQuestVarById(1, 1); // Saved briefing state, separate from the native kill counter.
			updateQuestStatus(env);
			return closeDialogWindow(env);
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(1) != 1)
			return false;
		if (!defaultOnKillEvent(env, targets, 0, count))
			return false;
		if (state.getQuestVarById(0) == count) {
			state.setStatus(QuestStatus.REWARD);
			updateQuestStatus(env);
		}
		return true;
	}
}
