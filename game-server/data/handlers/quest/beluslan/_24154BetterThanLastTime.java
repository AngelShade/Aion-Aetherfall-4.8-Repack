package quest.beluslan;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUEST_ACTION;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.model.QuestActionType;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.Effect.ForceType;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Original Java Q2058 infiltration, adapted to the installed Q24154 dialog, task table and native portals. */
public class _24154BetterThanLastTime extends BeluslanQuestItem {

	private static final int TRISTRAN = 204774, STUA = 204809, PORT = 700359, GENERATOR = 700349, FLARE = 182215463;
	private final Map<Integer, Infiltration> attempts = new ConcurrentHashMap<>();

	public _24154BetterThanLastTime() {
		super(24154);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(TRISTRAN).addOnQuestStart(questId);
		for (int npc : new int[] { TRISTRAN, STUA, PORT })
			qe.registerQuestNpc(npc).addOnTalkEvent(questId);
		qe.registerQuestNpc(GENERATOR).addOnKillEvent(questId);
		qe.registerQuestItem(FLARE, questId);
		qe.registerCanAct(questId, PORT);
		qe.registerOnDie(questId);
		qe.registerOnLogOut(questId);
		qe.registerOnEnterWorld(questId);
	}

	@Override
	public boolean onCanAct(QuestEnv env, QuestActionType action, Object... objects) {
		QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
		return action == QuestActionType.ACTION_ITEM_USE && env.getTargetId() == PORT
			&& state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 1
			&& attempts.containsKey(env.getPlayer().getObjectId());
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return sendQuestNoneDialog(env, TRISTRAN, SELECT_NONE);
		int npc = env.getTargetId(), action = env.getDialogActionId();
		if (state.getStatus() == QuestStatus.REWARD) {
			if (npc != TRISTRAN)
				return false;
			if (action == QUEST_SELECT || action == USE_OBJECT)
				return sendQuestDialog(env, 10002);
			return sendQuestEndDialog(env);
		}
		if (state.getStatus() != QuestStatus.START)
			return false;
		int stage = state.getQuestVarById(0);
		if (npc == STUA && stage == 0) {
			if (action == QUEST_SELECT)
				return sendQuestDialog(env, 1352);
			if (action == SELECT2_1)
				return sendQuestDialog(env, action);
			if (action == SETPRO2) {
				if (player.getController().hasScheduledTask(TaskId.QUEST_TIMER))
					return closeDialogWindow(env); // Do not replace another quest's timer.
				if (player.getInventory().getItemCountByItemId(FLARE) == 0 && !giveQuestItem(env, FLARE, 1))
					return false;
				changeQuestStep(env, 0, 1);
				SkillEngine.getInstance().applyEffectDirectly(267, player, player, 350000, ForceType.DEFAULT);
				Infiltration attempt = new Infiltration(player, state);
				attempts.put(player.getObjectId(), attempt);
				player.getController().addTask(TaskId.QUEST_TIMER, attempt);
				attempt.attach(ThreadPoolManager.getInstance().schedule(attempt, 240000));
				PacketSendUtility.sendPacket(player, new SM_QUEST_ACTION(questId, 240));
				return closeDialogWindow(env);
			}
		} else if (npc == PORT && stage == 1 && action == USE_OBJECT && player.getWorldId() == 220040000) {
			Infiltration attempt = attempts.get(player.getObjectId());
			if (attempt == null)
				return false;
			attempt.stop(false);
			// Commit the entry before its cinematic; a skipped/lost movie-end packet must not strand the quest.
			changeQuestStep(env, 1, 3);
			TeleportService.teleportTo(player, 220040000, player.getInstanceId(), 2452, 2474, 672.25f, (byte) 28);
			playQuestMovie(env, 250);
			return true;
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		return env.getPlayer().getWorldId() == 320110000 && defaultOnKillEvent(env, GENERATOR, 3, 4);
	}

	@Override
	public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
		if (item.getItemId() != FLARE)
			return HandlerResult.UNKNOWN;
		return HandlerResult.fromBoolean(useBeluslanItem(env, item, "DF3_ITEMUSEAREA_Q2058", state -> state.getQuestVarById(0) == 4,
			251, captured -> changeQuestStep(captured, 4, 4, true)));
	}

	private void reset(Player player) {
		Infiltration attempt = attempts.get(player.getObjectId());
		if (attempt != null)
			attempt.stop(true);
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) <= 2) {
			player.getEffectController().removeEffect(267);
			state.setQuestVar(0);
			updateQuestStatus(new QuestEnv(null, player, questId));
		}
	}

	@Override
	public boolean onDieEvent(QuestEnv env) {
		reset(env.getPlayer());
		return false;
	}

	@Override
	public boolean onLogOutEvent(QuestEnv env) {
		reset(env.getPlayer());
		return false;
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.getStatus() != QuestStatus.START)
			return false;
		if (state.getQuestVarById(0) <= 2 && player.getWorldId() != 320110000)
			reset(player);
		return false;
	}

	/** Bounded attempt lifetime; transient custody only, progress stays in native QuestState. */
	private final class Infiltration extends FutureTask<Void> {
		private final Player player;
		private final QuestState state;
		private final AtomicBoolean cleaned = new AtomicBoolean();
		private volatile Future<?> timer;
		private volatile boolean reset = true;

		Infiltration(Player player, QuestState state) {
			super(() -> null);
			this.player = player;
			this.state = state;
		}

		void attach(Future<?> timer) {
			this.timer = timer;
			if (isDone())
				timer.cancel(false);
		}

		void stop(boolean reset) {
			this.reset = reset;
			player.getController().cancelTaskIfPresent(TaskId.QUEST_TIMER, this);
			cancel(false);
		}

		@Override
		public void run() {
			if (!isDone())
				stop(true);
		}

		@Override
		protected void done() {
			if (!cleaned.compareAndSet(false, true))
				return;
			attempts.remove(player.getObjectId(), this);
			if (timer != null)
				timer.cancel(false);
			if (player.getQuestStateList().getQuestState(questId) != state || state.getStatus() != QuestStatus.START
				|| state.getQuestVarById(0) != 1)
				return;
			player.getEffectController().removeEffect(267);
			PacketSendUtility.sendPacket(player, new SM_QUEST_ACTION(questId, 0));
			if (reset) {
				state.setQuestVar(0);
				updateQuestStatus(new QuestEnv(null, player, questId));
			}
		}
	}
}
