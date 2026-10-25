package quest.verteron;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.world.zone.ZoneInstance;

/** Original Java Q1023 scouting sequence, adapted to the installed Q14114 stages and native drop step. */
public class _14114RevolutionIntervention extends AbstractQuestHandler {

	private static final int SPATALOS = 203098, KHIDIA = 203183, PLUMIS = 8197;
	private static final String SHIPWRECK = "MYSTERIOUS_SHIPWRECK_210030000";

	public _14114RevolutionIntervention() {
		super(14114);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(SPATALOS).addOnQuestStart(questId);
		qe.registerQuestNpc(SPATALOS).addOnTalkEvent(questId);
		qe.registerQuestNpc(KHIDIA).addOnTalkEvent(questId);
		qe.registerOnEnterZone(SHIPWRECK, questId);
		qe.registerOnEnterWorld(questId);
		qe.registerOnDie(questId);
		qe.registerOnLogOut(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, SPATALOS, SELECT_NONE);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != SPATALOS)
				return false;
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, SELECT5);
			return sendQuestEndDialog(env);
		}
		if (state.getStatus() != QuestStatus.START || npc != KHIDIA)
			return false;
		int stage = state.getQuestVarById(0);
		if (stage == 0 || stage == 1) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, SELECT1);
			if (action == SELECT1_1)
				return sendQuestDialog(env, action);
			if (action == SETPRO1) {
				SkillEngine.getInstance().applyEffectDirectly(PLUMIS, player, player);
				if (!player.getEffectController().hasAbnormalEffect(PLUMIS))
					return false;
				if (stage == 0)
					changeQuestStep(env, 0, 1);
				playQuestMovie(env, 30);
				return closeDialogWindow(env);
			}
		} else if (stage == 2) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, SELECT2);
			if (action == SELECT2_1)
				return sendQuestDialog(env, action);
			if (action == SETPRO2)
				return defaultCloseDialog(env, 2, 3);
		} else if (stage == 3) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, SELECT3);
			if (action == CHECK_USER_HAS_QUEST_ITEM)
				return checkQuestItems(env, 3, 4, false, 10000, 10001);
		} else if (stage == 4 && action == SETPRO3) {
			return defaultCloseDialog(env, 4, 4, true, false);
		}
		return false;
	}

	@Override
	public boolean onEnterZoneEvent(QuestEnv env, ZoneInstance zone) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (!zone.matches(SHIPWRECK) || state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 1
			|| player.isDead() || !player.getEffectController().hasAbnormalEffect(PLUMIS))
			return false;
		changeQuestStep(env, 1, 2); // Do not depend on a movie-end packet to save the completed scouting.
		player.getEffectController().removeEffect(PLUMIS);
		player.getMoveController().abortMove();
		playQuestMovie(env, 23);
		return true;
	}

	private void clearOwnTransformation(QuestEnv env) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		if (state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 1)
			env.getPlayer().getEffectController().removeEffect(PLUMIS);
	}

	@Override
	public boolean onDieEvent(QuestEnv env) {
		clearOwnTransformation(env);
		return false;
	}

	@Override
	public boolean onLogOutEvent(QuestEnv env) {
		clearOwnTransformation(env);
		return false;
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		clearOwnTransformation(env);
		return false;
	}
}
