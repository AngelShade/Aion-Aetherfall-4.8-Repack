package quest.eltnen;

import static com.aionemu.gameserver.model.DialogAction.*;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.QuestTemplate;

/**
 * @author Xitanium
 * @reworked vlog
 * @Modified Majka
 */
public class _1039SomethingInTheWater extends LegacyEltnenCampaign {

	private final static int questId = 1039;

	public _1039SomethingInTheWater() {
		super(questId);
	}

	@Override
	public void register() {
		int[] mobs = { 210946, 210947 }; // The Vaegir and fighter variants present in the 4.8 Eltnen spawns.
		qe.registerQuestItem(182201009, questId);
		qe.registerQuestNpc(203946).addOnTalkEvent(questId);
		qe.registerQuestNpc(203705).addOnTalkEvent(questId);
		qe.registerOnLevelChanged(questId);
		qe.registerOnQuestCompleted(questId);
		qe.registerOnEnterWorld(questId);
		for (int mob : mobs)
			qe.registerQuestNpc(mob).addOnKillEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		Player player = env.getPlayer();
		int targetId = env.getTargetId();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int dialog = env.getDialogActionId();
		if (qs == null)
			return false;

		if (qs.getStatus() == QuestStatus.START) {
			int var = qs.getQuestVarById(0);
			int var1 = qs.getQuestVarById(1);
			int var2 = qs.getQuestVarById(2);
			switch (targetId) {
				case 203946: { // Asclepius
					switch (dialog) {
						case QUEST_SELECT: {
							if (var == 0) {
								return sendQuestDialog(env, 1011);
							} else if (var == 3) {
								return sendQuestDialog(env, 1693);
							} else if (var == 4 && var1 == 3 && var2 == 3) {
								return sendQuestDialog(env, 2034);
							}
						}
						case SETPRO1: {
							return defaultCloseDialog(env, 0, 1, 182201009, 1, 0, 0); // 1
						}
						case SETPRO3: {
							return defaultCloseDialog(env, 3, 4); // 4
						}
						case SELECT_QUEST_REWARD: {
							if (var != 4 || var1 != 3 || var2 != 3)
								return false;
							qs.setStatus(QuestStatus.REWARD); // reward
							updateQuestStatus(env);
							return sendQuestDialog(env, 5);
						}
					}
					break;
				}
				case 203705: { // Jumentis
					switch (dialog) {
						case QUEST_SELECT: {
							if (var == 2) {
								return sendQuestDialog(env, 1352);
							}
						}
						case SETPRO2: {
							if (!defaultCloseDialog(env, 2, 3, 0, 0, 182201010, 1))
								return false;
							TeleportService.teleportTo(player, 210020000, 1910.61f, 2020.29f, 361.48f, (byte) 85, TeleportAnimation.FADE_OUT_BEAM);
							return true;
						}
					}
					break;
				}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203946) { // Asclepius
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
		if (item.getItemId() != 182201009)
			return HandlerResult.UNKNOWN;
		return HandlerResult.fromBoolean(useCampaignItem(env, item, "LF2_ITEMUSEAREA_Q1039", 1, 2, true, 182201010));
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int targetId = env.getTargetId();
		if (qs != null && qs.getStatus() == QuestStatus.START) {
			int var = qs.getQuestVarById(0);
			if (var == 4) {
				int[] vaegir = { 210946 };
				int[] fighter = { 210947 };
				switch (targetId) {
					case 210946:
					{
						return defaultOnKillEvent(env, vaegir, 0, 3, 1); // 1: 3
					}
					case 210947: {
						return defaultOnKillEvent(env, fighter, 0, 3, 2); // 2: 3
					}
				}
			}
		}
		return false;
	}

	@Override
	public void onLevelChangedEvent(Player player) {
		refreshAvailability(player, 2);
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		refreshAvailability(env.getPlayer(), 15);
	}

	// The engine's default campaign helper requires every XML alternative. Use the
	// start-condition service here so either the legacy or modern Verteron route works.
	private void refreshAvailability(Player player, int levelAllowance) {
		QuestState state = player.getQuestStateList().getQuestState(questId);
		QuestState orders = player.getQuestStateList().getQuestState(1300);
		if (state != null && state.getStatus() != QuestStatus.LOCKED
			|| orders == null || orders.getStatus() != QuestStatus.COMPLETE
			|| !QuestService.checkStartConditions(player, questId, false, levelAllowance, false, false, true))
			return;
		QuestTemplate template = DataManager.QUEST_DATA.getQuestById(questId);
		boolean available = player.getLevel() >= template.getMinlevelPermitted()
			&& QuestService.checkStartConditions(player, questId, false);
		if (available)
			QuestService.addOrUpdateQuest(player, questId, QuestStatus.START);
		else if (state == null)
			QuestService.addOrUpdateQuest(player, questId, QuestStatus.LOCKED);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		onLevelChangedEvent(env.getPlayer());
		return false;
	}

}
