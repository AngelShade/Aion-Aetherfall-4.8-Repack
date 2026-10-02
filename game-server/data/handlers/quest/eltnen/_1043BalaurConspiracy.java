package quest.eltnen;

import java.util.List;

import static com.aionemu.gameserver.model.DialogAction.*;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * @author Balthazar
 * @modified Pad
 */

public class _1043BalaurConspiracy extends AbstractQuestHandler {

	private final static int questId = 1043;
	private final static int mobId = 213575; // Crusader
	private final static int[] npcIds = { 203901, 204020, 204044 };

	public _1043BalaurConspiracy() {
		super(questId);
	}

	@Override
	public void register() {
		qe.registerOnLevelChanged(questId);
		qe.registerOnQuestCompleted(questId);
		qe.registerOnQuestTimerEnd(questId);
		qe.registerOnLogOut(questId);
		qe.registerOnDie(questId);
		qe.registerOnEnterWorld(questId);
		for (int npcId : npcIds)
			qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
		qe.registerQuestNpc(mobId).addOnKillEvent(questId);
	}


	@Override
	public void onLevelChangedEvent(Player player) {
		defaultOnLevelChangedEvent(player, 1300);
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		defaultOnQuestCompletedEvent(env, 1300);
	}


	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;
		int var = qs.getQuestVarById(0);
		int targetId = env.getTargetId();

		if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203901) { // Telemachus
				if (env.getDialogActionId() == USE_OBJECT)
					return sendQuestDialog(env, 2375);
				else
					return sendQuestEndDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			switch (targetId) {
				case 203901: { // Telemachus
					switch (env.getDialogActionId()) {
						case QUEST_SELECT:
							if (var == 0)
								return sendQuestDialog(env, 1011);
						case SETPRO1:
							if (!defaultCloseDialog(env, 0, 1))
								return false;
							TeleportService.teleportTo(player, 210020000, 1596.1948f, 1529.9152f, 317, (byte) 120, TeleportAnimation.FADE_OUT_BEAM);
							return true;
					}
					break;
				}
				case 204020: { // Mabangtah
					switch (env.getDialogActionId()) {
						case QUEST_SELECT:
							if (var == 1 || var == 2)
								return sendQuestDialog(env, 1352);
						case SETPRO2:
							if (var != 1 && var != 2)
								return false;
							WorldMapInstance instance = InstanceService.getNextAvailableInstance(310040000, player.getObjectId(), (byte) 0, 1, true);
							instance.register(player.getObjectId());
							defaultCloseDialog(env, var, 2);
							TeleportService.teleportTo(player, instance, 272.83f, 176.81f, 204.35f, (byte) 0);
							return true;
					}
					break;
				}
				case 204044: { // Kimeia
					switch (env.getDialogActionId()) {
						case USE_OBJECT:
						case QUEST_SELECT:
							if (var == 2)
								return sendQuestDialog(env, 1693);
							else if (var == 4)
								return sendQuestDialog(env, 2034);
							return false;
						case SETPRO3: {
							if (var != 2 || player.getWorldId() != 310040000 || !kimeiaIsAlive(env))
								return false;
							qs.setQuestVarById(1, 0);
							deleteBalaur(env);
							spawnWave(player);
							QuestService.questTimerStart(env, 240);
							return defaultCloseDialog(env, 2, 3); // 3
						}
						case SETPRO4:
							if (var == 4) {
								if (!defaultCloseDialog(env, 4, 4, true, false))
									return false;
								returnToEltnen(player);
								return true;
							}
					}
					break;
				}
			}
		}
		return false;
	}

	@Override
	public void onMovieEndEvent(QuestEnv env, int movieId) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (movieId == 157 && player.getWorldId() == 310040000 && qs != null
			&& qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 4)
			showDefenseContinuation(player);
	}

	/** Reopen the native conversation after the movie instead of relying on a quest marker. */
	protected void showDefenseContinuation(Player player) {
		QuestEnv env = new QuestEnv(null, player, questId);
		respawnKimeia(env);
		Npc kimeia = player.getWorldMapInstance().getNpc(204044);
		if (kimeia != null && !kimeia.isDead())
			sendQuestDialog(new QuestEnv(kimeia, player, questId, QUEST_SELECT), 2034);
	}

	protected void returnToEltnen(Player player) {
		TeleportService.teleportTo(player, 210020000, 271.69f, 2787.04f, 272.47f, (byte) 50, TeleportAnimation.FADE_OUT_BEAM);
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;

		if (qs.getQuestVarById(0) == 3 && player.getWorldId() == 310040000) {
			if (env.getTargetId() == mobId) {
				int balaurKilled = qs.getQuestVarById(1) + 1;
				qs.setQuestVarById(1, balaurKilled);
				updateQuestStatus(env);
				if (balaurKilled == 2) {
					spawnWave(player);
				} else if (balaurKilled == 4) {
					finishDefense(env);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onQuestTimerEndEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;

		if (qs.getQuestVarById(0) == 3) {
			finishDefense(env);
			return true;
		}
		return false;
	}

	@Override
	public boolean onLogOutEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;

		if (qs.getQuestVarById(0) == 3) {
			deleteBalaur(env);
			changeQuestStep(env, 3, 2, false);
			QuestService.questTimerEnd(env);
			respawnKimeia(env);
			return true;
		}
		return false;
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		onLevelChangedEvent(env.getPlayer());
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null || qs.getStatus() != QuestStatus.START)
			return false;

		if (qs.getQuestVarById(0) == 3 && player.getWorldId() != 310040000) {
			changeQuestStep(env, 3, 2, false);
			QuestService.questTimerEnd(env);
			return true;
		}
		return false;
	}

	private boolean kimeiaIsAlive(QuestEnv env) {
		if (env.getPlayer().getWorldId() != 310040000)
			return false;
		Npc kimeia = env.getPlayer().getPosition().getWorldMapInstance().getNpc(204044);
		if (kimeia != null && !kimeia.isDead())
			return true;
		return false;
	}

	private void deleteBalaur(QuestEnv env) {
		if (env.getPlayer().getWorldId() != 310040000)
			return;
		List<Npc> npcs = env.getPlayer().getPosition().getWorldMapInstance().getNpcs(mobId);
		for (Npc npc : npcs) {
				npc.getController().onDelete();
		}
	}

	@Override
	public boolean onDieEvent(QuestEnv env) {
		return onLogOutEvent(env);
	}

	private void spawnWave(Player player) {
		spawn(mobId, player, 248.78f, 259.28f, 227.74f, (byte) 94);
		spawn(mobId, player, 259.10f, 261.79f, 227.77f, (byte) 94);
	}

	private void respawnKimeia(QuestEnv env) {
		if (env.getPlayer().getWorldId() == 310040000 && !kimeiaIsAlive(env)) {
			Npc old = env.getPlayer().getWorldMapInstance().getNpc(204044);
			if (old != null)
				old.getController().onDelete();
			spawn(204044, env.getPlayer(), 272.83f, 176.81f, 204.35f, (byte) 0);
		}
	}

	private void finishDefense(QuestEnv env) {
		boolean alive = kimeiaIsAlive(env);
		deleteBalaur(env);
		// Update first: ending a timer must not recursively finish the same step.
		changeQuestStep(env, 3, alive ? 4 : 2, false);
		QuestService.questTimerEnd(env);
		if (alive)
			playQuestMovie(env, 157);
		else
			respawnKimeia(env);
	}
}
