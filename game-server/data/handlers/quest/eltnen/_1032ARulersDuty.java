package quest.eltnen;

import static com.aionemu.gameserver.model.DialogAction.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Xitanium
 * @Fixed Ritsu
 */
public class _1032ARulersDuty extends LegacyEltnenCampaign {

	private final static int questId = 1032;

	public _1032ARulersDuty() {
		super(questId);
	}

	@Override
	public void register() {
		qe.registerQuestItem(182201001, questId);
		qe.registerQuestNpc(203932).addOnTalkEvent(questId); // Phomona
		qe.registerQuestNpc(730020).addOnTalkEvent(questId); // Demro
		qe.registerQuestNpc(730019).addOnTalkEvent(questId); // Lodas
		qe.registerQuestNpc(700157).addOnTalkEvent(questId); // Seau kerubien
		qe.registerOnLevelChanged(questId);
		qe.registerOnQuestCompleted(questId);
		qe.registerOnEnterWorld(questId);
	}

	@Override
	public HandlerResult onItemUseEvent(final QuestEnv env, Item item) {
		if (item.getItemId() != 182201001)
			return HandlerResult.UNKNOWN;
		if (!env.getPlayer().isInsideItemUseZone("LF2_ITEMUSEAREA_Q1032"))
			return HandlerResult.FAILED;
		return HandlerResult.fromBoolean(useCampaignItem(env, item, "LF2_ITEMUSEAREA_Q1032", 3, 4, false, 0));
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
	public boolean onEnterWorldEvent(QuestEnv env) {
		onLevelChangedEvent(env.getPlayer());
		return false;
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		int targetId = 0;
		if (env.getVisibleObject() instanceof Npc)
			targetId = ((Npc) env.getVisibleObject()).getNpcId();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;
		if (targetId == 203932) // Phomona
		{
			if (qs.getStatus() == QuestStatus.START) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 1011);
				else if (env.getDialogActionId() == SETPRO1 && qs.getQuestVarById(0) == 0) {
					qs.setQuestVar(1);
					updateQuestStatus(env);
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
					return true;
				} else
					return sendQuestStartDialog(env);
			}

			else if (qs.getStatus() == QuestStatus.REWARD) {
				if (env.getDialogActionId() == USE_OBJECT)
					return sendQuestDialog(env, 2716);
				return sendQuestEndDialog(env);
			}
		} else if (targetId == 730020) // Demro
		{
			if (qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 1) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 1352);
				else if (env.getDialogActionId() == SETPRO2) {
					qs.setQuestVarById(0, qs.getQuestVarById(0) + 1);
					updateQuestStatus(env);
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
					return true;
				} else
					return sendQuestStartDialog(env);
			}

			else if (qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 5) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 2375);
				else if (env.getDialogActionId() == SETPRO5) {
					qs.setStatus(QuestStatus.REWARD);
					updateQuestStatus(env);
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
					return true;
				} else
					return sendQuestStartDialog(env);
			}
		}

		else if (targetId == 730019) // Lodas
		{
			if (qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 2) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 1693);
				else if (env.getDialogActionId() == SETPRO3) {
					qs.setQuestVarById(0, qs.getQuestVarById(0) + 1);
					updateQuestStatus(env);
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
					return true;
				} else
					return sendQuestStartDialog(env);
			}
			else if (qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 4) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 2034);
				else if (env.getDialogActionId() == SELECT4_1)
					return playQuestMovie(env, 49);
				else if (env.getDialogActionId() == SETPRO4) {
					removeQuestItem(env, 182201001, 1);
					qs.setQuestVar(5);
					updateQuestStatus(env);
					PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
					return true;
				} else
					return sendQuestStartDialog(env);
			}
		}

		else if (qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 3) {
			switch (targetId) {
				case 700157: { // Seau kerubien
					if (qs.getQuestVarById(0) == 3 && env.getDialogActionId() == USE_OBJECT) {
						return true; // loot
					}
				}
			}
		}
		return false;
	}
}
