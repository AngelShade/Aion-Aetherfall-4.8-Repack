package quest.ishalgen;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldMapType;

/**
 * @author Mr. Poke, Hellboy, Gigi, Bobobear, Majka
 */
public class _2002WheresRae extends AbstractQuestHandler {

	public _2002WheresRae() {
		super(2002);
	}

	@Override
	public java.util.List<com.aionemu.gameserver.questEngine.model.QuestBotAction> getPlayerBotActions(Player player) {
		var state = player.getQuestStateList().getQuestState(questId);
		if (state == null) return java.util.List.of();
		if (state.getStatus() == QuestStatus.REWARD)
			return java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.reward(203516));
		if (state.getStatus() != QuestStatus.START) return java.util.List.of();
		return switch (state.getQuestVarById(0)) {
			case 0 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(203519, SETPRO1, 1011));
			case 1 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(203534, SETPRO2, 1352));
			case 2 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(790002, SETPRO3, 1693));
			case 3, 4, 5, 6, 7, 8, 9 -> com.aionemu.gameserver.questEngine.model.QuestBotAction.hunts(210377, 210378);
			case 10 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(790002, SETPRO4, 2034));
			case 11 -> player.getInventory().getItemCountByItemId(182203003) < 1
				? java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.object(700045))
				: java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(790002, CHECK_USER_HAS_QUEST_ITEM, 2375)
					.requiring(182203003, 1));
			case 12 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(790002, SETPRO5, 2462));
			case 13 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(790002, SETPRO6, 2716));
			case 14 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.object(203538));
			case 15 -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.dialog(203553, SETPRO7, 3057));
			case 35 -> state.getQuestVars().getQuestVars() == 99 && player.getWorldId() == WorldMapType.ATAXIAR.getId()
				? java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.flight(205020, QUEST_SELECT, "Original Ataxiar flight timer owns return and quest progress"))
				: java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.event("Ataxiar return requires the original packed native instance marker 99"));
			default -> java.util.List.of(com.aionemu.gameserver.questEngine.model.QuestBotAction.event("Saved Rae stage has no declared native transition"));
		};
	}

	@Override
	public void register() {
		int[] npc_ids = { 203519, 203534, 203553, 700045, 203516, 203538, 790002, 205020 };
		qe.registerOnQuestCompleted(questId);
		qe.registerOnLevelChanged(questId);
		qe.registerQuestNpc(210377).addOnKillEvent(questId);
		qe.registerQuestNpc(210378).addOnKillEvent(questId);
		for (int npc_id : npc_ids)
			qe.registerQuestNpc(npc_id).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		final QuestState qs = player.getQuestStateList().getQuestState(questId);
		final QuestEnv qEnv = env;
		if (qs == null)
			return false;

		int var = qs.getQuestVarById(0);
		int targetId = env.getTargetId();
		int dialogActionId = env.getDialogActionId();
		if (qs.getStatus() == QuestStatus.START) {
			switch (targetId) {
				case 203519:
					switch (dialogActionId) {
						case QUEST_SELECT:
							if (var == 0)
								return sendQuestDialog(env, 1011);
							return false;
						case SETPRO1:
							if (var == 0) {
								qs.setQuestVarById(0, var + 1);
								updateQuestStatus(env);
								PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
								return true;
							}
					}
					return false;
				case 203534:
					switch (dialogActionId) {
						case QUEST_SELECT:
							if (var == 1)
								return sendQuestDialog(env, 1352);
							return false;
						case SELECT2_1:
							playQuestMovie(env, 52);
							break;
						case SETPRO2:
							if (var == 1) {
								qs.setQuestVarById(0, var + 1);
								updateQuestStatus(env);
								PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
								return true;
							}
					}
					return false;
				case 790002:
					switch (dialogActionId) {
						case QUEST_SELECT:
							if (var == 2)
								return sendQuestDialog(env, 1693);
							else if (var == 10)
								return sendQuestDialog(env, 2034);
							else if (var == 11)
								return sendQuestDialog(env, 2375);
							else if (var == 12)
								return sendQuestDialog(env, 2462);
							else if (var == 13)
								return sendQuestDialog(env, 2716);
							return false;
						case SETPRO3:
						case SETPRO4:
						case SETPRO6:
							if (var == 2 || var == 10) {
								qs.setQuestVarById(0, var + 1);
								updateQuestStatus(env);
								PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
								return true;
							} else if (var == 13) {
								qs.setQuestVarById(0, 14);
								updateQuestStatus(env);
								PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
								return true;
							}
							break;
						case SETPRO5:
							if (var == 12 || var == 99) {
								qs.setQuestVar(99);
								updateQuestStatus(env);
								PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 0));
								// Create instance
								WorldMapInstance newInstance = InstanceService.getNextAvailableInstance(WorldMapType.ATAXIAR.getId(), player);
								TeleportService.teleportTo(player, newInstance, 457.65f, 426.8f, 230.4f);
								return true;
							}
							return false;
						case CHECK_USER_HAS_QUEST_ITEM:
							if (var == 11) {
								if (QuestService.collectItemCheck(env, true)) {
									qs.setQuestVarById(0, 12);
									updateQuestStatus(env);
									return sendQuestDialog(env, 2461);
								} else
									return sendQuestDialog(env, 2376);
							}
					}
					break;
				case 700045:
					if (var == 11 && env.getDialogActionId() == USE_OBJECT) {
						SkillEngine.getInstance().applyEffectDirectly(8343, player, player);
						return true;
					}
					return false;
				case 203538:
					if (var == 14 && env.getDialogActionId() == USE_OBJECT) {
						qs.setQuestVarById(0, var + 1);
						updateQuestStatus(env);
						Npc npc = (Npc) env.getVisibleObject();
						PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(npc.getObjectId(), 10));
						spawnForFiveMinutes(203553, npc.getPosition());
						npc.getController().deleteAndScheduleRespawn();
						return true;
					}
					break;
				case 203553:
					switch (dialogActionId) {
						case QUEST_SELECT:
							if (var == 15)
								return sendQuestDialog(env, 3057);
							return false;
						case SETPRO7:
							if (var == 15) {
								env.getVisibleObject().getController().delete();
								qs.setStatus(QuestStatus.REWARD);
								updateQuestStatus(env);
								PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(env.getVisibleObject().getObjectId(), 10));
								return true;
							}
					}
					return false;
				case 205020:
					switch (dialogActionId) {
						case QUEST_SELECT:
							if (var >= 12) {
								player.setState(CreatureState.FLYING);
								player.unsetState(CreatureState.ACTIVE);
								player.setFlightTeleportId(3001);
								final var scriptedFlight = player.getFlightPath();
								final int flightWorld = player.getWorldId(), flightInstance = player.getInstanceId();
								final int flightQuestVars = qs.getQuestVars().getQuestVars();
								PacketSendUtility.sendPacket(player, new SM_EMOTION(player, EmotionType.START_FLYTELEPORT, 3001, 0));
								ThreadPoolManager.getInstance().schedule(new Runnable() {

									@Override
									public void run() {
										if (player.isPlayerBot() && (!player.getController().isHeadlessScriptedFlightCurrent(scriptedFlight, flightWorld, flightInstance)
											|| qs.getStatus() != QuestStatus.START || qs.getQuestVars().getQuestVars() != flightQuestVars)) return;
										TeleportService.teleportTo(player, 220010000, 940.15f, 2295.64f, 265.7f, (byte) 43);
										qs.setQuestVar(13);
										updateQuestStatus(qEnv);
										player.getController().finishHeadlessScriptedFlight(scriptedFlight);
									}
								}, 40000);
								return true;
							}
							return false;
						default:
							return false;
					}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203516) {
				if (dialogActionId == USE_OBJECT || dialogActionId == QUEST_SELECT)
					return sendQuestDialog(env, 3398);
				else if (dialogActionId == SETPRO8)
					return sendQuestDialog(env, 5);
				else
					return sendQuestEndDialog(env);
			}
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;

		int var = qs.getQuestVarById(0);
		int targetId = 0;
		if (env.getVisibleObject() instanceof Npc)
			targetId = ((Npc) env.getVisibleObject()).getNpcId();

		if (qs.getStatus() != QuestStatus.START)
			return false;
		switch (targetId) {
			case 210377:
			case 210378:
				if (var >= 3 && var < 10) {
					qs.setQuestVarById(0, qs.getQuestVarById(0) + 1);
					updateQuestStatus(env);
					return true;
				}
		}
		return false;
	}

	@Override
	public void onQuestCompletedEvent(QuestEnv env) {
		defaultOnQuestCompletedEvent(env, 2100);
	}

	@Override
	public void onLevelChangedEvent(Player player) {
		defaultOnLevelChangedEvent(player, 2100);
	}
}
