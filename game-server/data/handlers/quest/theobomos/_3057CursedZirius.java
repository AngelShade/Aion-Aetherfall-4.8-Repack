package quest.theobomos;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.RespawnService;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * @author Cheatkiller
 */
public class _3057CursedZirius extends AbstractQuestHandler {

	public _3057CursedZirius() {
		super(3057);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(798213).addOnQuestStart(questId);
		qe.registerQuestNpc(798213).addOnTalkEvent(questId);
		qe.registerQuestNpc(214576).addOnAttackEvent(questId);
		qe.registerQuestNpc(214576).addOnAtDistanceEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int dialogActionId = env.getDialogActionId();
		int targetId = env.getTargetId();

		if (qs == null || qs.isStartable()) {
			if (targetId == 798213) {
				if (dialogActionId == QUEST_SELECT) {
					return sendQuestDialog(env, 4762);
				} else {
					return sendQuestStartDialog(env);
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 798213) {
				switch (dialogActionId) {
					case USE_OBJECT:
						return sendQuestDialog(env, 10002);
					default: {
						return sendQuestEndDialog(env);
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean onAttackEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != 0
			|| !(env.getVisibleObject() instanceof Npc npc) || player.isDead()
			|| player.getWorldMapInstance() != npc.getWorldMapInstance())
			return false;
		synchronized (npc) {
			// Quest and AI scripts compile in separate contexts. Keep this guard
			// aligned with CursedZiriusAI without importing that script class.
			if (npc.getNpcId() != 214576 || !npc.isSpawned() || npc.isDead()
				|| npc.getWorldId() != 210060000 || npc.getLifeStats().getHpPercentage() > 20
				|| PositionUtil.getDistance(npc, 1691.41f, 219.09f, 72.62f) > 30
				|| qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != 0)
				return false;
			// Spawn the native human form at the spring for the reward dialogue.
			// Do not award combat XP, drops or a corpse for breaking the curse.
			Npc cured = (Npc) spawnForFiveMinutes(798213, npc.getPosition());
			if (cured == null)
				return false;
			if (!changeQuestStep(env, 0, 1, true)) {
				cured.getController().delete();
				return false;
			}
			RespawnService.scheduleRespawn(npc);
			npc.getController().delete();
			return true;
		}
	}

	@Override
	public boolean onAtDistanceEvent(QuestEnv env) {
		// A passer-by must not claim another player's lure.
		if (env.getVisibleObject() instanceof Npc npc && npc.getTarget() instanceof com.aionemu.gameserver.model.gameobjects.Creature target
			&& target.getMaster() == env.getPlayer())
			return onAttackEvent(env);
		return false;
	}
}
