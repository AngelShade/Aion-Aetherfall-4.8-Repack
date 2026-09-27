package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author Rhys2002
 */
public class _1157GaphyrksLove extends AbstractQuestHandler {
	private static final int MIMITI_ID = 210319;
	private static final long LURE_TIMEOUT_MS = 180000;
	private final Set<Long> watchedLures = ConcurrentHashMap.newKeySet();

	public _1157GaphyrksLove() {
		super(1157);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(798003).addOnQuestStart(questId);
		qe.registerQuestNpc(798003).addOnTalkEvent(questId);
		qe.registerQuestNpc(MIMITI_ID).addOnAttackEvent(questId);
		qe.registerQuestNpc(MIMITI_ID).addOnAddAggroListEvent(questId);
	}

	@Override
	public boolean onAddAggroListEvent(QuestEnv env) {
		return checkOrWatchLure(env);
	}

	@Override
	public boolean onAttackEvent(QuestEnv env) {
		return checkOrWatchLure(env);
	}

	private boolean checkOrWatchLure(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);

		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;

		if (!(env.getVisibleObject() instanceof Npc) || ((Npc) env.getVisibleObject()).getNpcId() != MIMITI_ID)
			return false;

		Npc npc = (Npc) env.getVisibleObject();
		if (atGaphyrk(npc)) {
			completeLure(env, npc);
			return true;
		}
		long key = ((long) player.getObjectId() << 32) | (npc.getObjectId() & 0xffffffffL);
		if (watchedLures.add(key))
			watchLure(env, npc, key, System.currentTimeMillis() + LURE_TIMEOUT_MS);
		return true;
	}

	private boolean atGaphyrk(Npc npc) {
		return PositionUtil.getDistance(892, 2024, 166, npc.getX(), npc.getY(), npc.getZ()) <= 13;
	}

	private void watchLure(QuestEnv env, Npc npc, long key, long deadline) {
		ThreadPoolManager.getInstance().schedule(() -> {
			Player player = env.getPlayer();
			QuestState qs = player.getQuestStateList().getQuestState(questId);
			if (!player.isOnline() || !npc.isSpawned() || npc.isDead() || player.getWorldMapInstance() != npc.getWorldMapInstance()
				|| qs == null || qs.getStatus() != QuestStatus.START || System.currentTimeMillis() > deadline) {
				watchedLures.remove(key);
				return;
			}
			if (atGaphyrk(npc)) {
				completeLure(env, npc);
				watchedLures.remove(key);
				return;
			}
			watchLure(env, npc, key, deadline);
		}, 500);
	}

	private void completeLure(QuestEnv env, Npc npc) {
		QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (qs == null)
			return;
		synchronized (qs) {
			if (qs.getStatus() != QuestStatus.START)
				return;
			changeQuestStep(env, 0, 0, true);
		}
		npc.getController().deleteAndScheduleRespawn();
		playQuestMovie(env, 17);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);

		int targetId = 0;
		if (env.getVisibleObject() instanceof Npc)
			targetId = ((Npc) env.getVisibleObject()).getNpcId();

		if (qs == null || qs.isStartable()) {
			if (targetId == 798003) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 1011);
				else
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 798003) {
				if (env.getDialogActionId() == USE_OBJECT)
					return sendQuestDialog(env, 2375);
				else if (env.getDialogActionId() == SELECT_QUEST_REWARD)
					return sendQuestDialog(env, 5);
				else
					return sendQuestEndDialog(env);
			}
			return false;
		}
		return false;
	}

}
