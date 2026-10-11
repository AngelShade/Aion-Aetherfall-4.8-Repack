package com.aionemu.gameserver.questEngine.handlers.template;

import static com.aionemu.gameserver.model.DialogAction.QUEST_SELECT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.lang.reflect.Field;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.models.Monster;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.events.OnKillEvent;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.events.OnTalkEvent;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.conditions.QuestConditions;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.ActionItemUseOperation;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.CollectItemQuestOperation;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.NpcDialogOperation;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.SetQuestVarOperation;
import com.aionemu.gameserver.model.DialogAction;

/**
 * @author Mr.Poke, Bobobear, Pad
 */
public class XmlQuest extends AbstractTemplateQuestHandler {

	private final Set<Integer> startNpcIds = new HashSet<>();
	private final Set<Integer> endNpcIds = new HashSet<>();
	private final List<OnTalkEvent> onTalkEvents = new ArrayList<>();
	private final List<OnKillEvent> onKillEvents = new ArrayList<>();
	private final boolean isDataDriven;
	private record Declaration(int variables, int npc, int action, int page, boolean object,
		boolean collecting, QuestConditions eventConditions, QuestConditions dialogConditions) {}
	private final List<Declaration> declarations;

	public XmlQuest(int questId, List<Integer> startNpcIds, List<Integer> endNpcIds, List<OnTalkEvent> onTalkEvents, List<OnKillEvent> onKillEvents) {
		super(questId);
		if (startNpcIds != null)
			this.startNpcIds.addAll(startNpcIds);
		if (endNpcIds != null)
			this.endNpcIds.addAll(endNpcIds);
		else
			this.endNpcIds.addAll(this.startNpcIds);
		if (onTalkEvents != null)
			this.onTalkEvents.addAll(onTalkEvents);
		if (onKillEvents != null)
			this.onKillEvents.addAll(onKillEvents);
		isDataDriven = DataManager.QUEST_DATA.getQuestById(questId).isDataDriven();
		declarations = declarations();
	}

	/** Immutable discovery from the registered XML operations; operations retain authority. */
	private List<Declaration> declarations() {
		List<Declaration> result = new ArrayList<>();
		for (var event : onTalkEvents) for (var variable : list(event, "var")) for (var npc : list(variable, "npc")) {
			int page = 0;
			for (var dialog : list(npc, "dialog")) if (number(dialog, "id") == QUEST_SELECT)
				for (var operation : list(field(dialog, "operations"), "operations"))
					if (operation instanceof NpcDialogOperation) page = number(operation, "id");
			for (var dialog : list(npc, "dialog")) {
				int action = number(dialog, "id");
				var operations = list(field(dialog, "operations"), "operations");
				boolean object = action == DialogAction.USE_OBJECT && operations.stream().anyMatch(o -> o instanceof ActionItemUseOperation);
				boolean collecting = operations.stream().anyMatch(o -> o instanceof CollectItemQuestOperation);
				boolean progressing = operations.stream().anyMatch(o -> o instanceof SetQuestVarOperation);
				if (object || action > 0 && page > 0 && (collecting || progressing))
					result.add(new Declaration(number(variable, "value"), number(npc, "id"), action, object ? 0 : page,
						object, collecting, (QuestConditions) field(event, "conditions"), (QuestConditions) field(dialog, "conditions")));
			}
		}
		return List.copyOf(result);
	}

	@Override
	public List<QuestBotAction> getPlayerBotActions(Player player) {
		var state = player.getQuestStateList().getQuestState(questId);
		if (state == null || state.isStartable())
			return startNpcIds.stream().sorted().map(npc -> QuestBotAction.dialog(npc, DialogAction.QUEST_ACCEPT, 4)).toList();
		if (state.getStatus() == QuestStatus.REWARD)
			return endNpcIds.stream().sorted().map(QuestBotAction::reward).toList();
		if (state.getStatus() != QuestStatus.START) return List.of();
		List<QuestBotAction> result = new ArrayList<>();
		for (var declaration : declarations) {
			if (declaration.variables() != state.getQuestVars().getQuestVars()) continue;
			// Conditions are checked read-only. Object/range/rights and all XML
			// conditions are checked again by the actual native dispatch.
			QuestEnv probe = new QuestEnv(null, player, questId, declaration.action());
			if (declaration.eventConditions() != null && !declaration.eventConditions().checkConditionOfSet(probe)
				|| declaration.dialogConditions() != null && !declaration.dialogConditions().checkConditionOfSet(probe)) continue;
			QuestBotAction action = declaration.object() ? QuestBotAction.object(declaration.npc())
				: QuestBotAction.dialog(declaration.npc(), declaration.action(), declaration.page());
			if (declaration.collecting()) action = action.requiringCollection(DataManager.QUEST_DATA.getQuestById(questId).getCollectItems());
			result.add(action);
		}
		return List.copyOf(result);
	}

	private static Object field(Object object, String name) {
		if (object == null) return null;
		for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
			Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
		} catch (NoSuchFieldException missing) {} catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
		return null;
	}
	private static List<?> list(Object object, String name) { var value = field(object, name); return value instanceof List<?> values ? values : List.of(); }
	private static int number(Object object, String name) { var value = field(object, name); return value instanceof Number number ? number.intValue() : 0; }

	@Override
	public void register() {
		for (Integer startNpcId : startNpcIds) {
			qe.registerQuestNpc(startNpcId).addOnQuestStart(questId);
			qe.registerQuestNpc(startNpcId).addOnTalkEvent(questId);
		}
		if (!endNpcIds.equals(startNpcIds)) {
			for (Integer endNpcId : endNpcIds) {
				qe.registerQuestNpc(endNpcId).addOnTalkEvent(questId);
			}
		}
		for (OnTalkEvent onTalkEvent : onTalkEvents) {
			for (Integer npcId : onTalkEvent.getIds()) {
				qe.registerQuestNpc(npcId).addOnTalkEvent(questId);
			}
		}
		for (OnKillEvent onKillEvent : onKillEvents) {
			for (Monster monster : onKillEvent.getMonsters()) {
				for (Integer monsterId : monster.getNpcIds()) {
					qe.registerQuestNpc(monsterId).addOnKillEvent(questId);
				}
			}
		}
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		//env.setQuestId(questId);
		for (OnTalkEvent onTalkEvent : onTalkEvents) {
			if (onTalkEvent.operate(env))
				return true;
		}

		Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);
		int targetId = env.getTargetId();
		
		if (qs == null || qs.isStartable()) {
			if (startNpcIds.contains(targetId)) {
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, isDataDriven ? 4762 : 1011);
				else
					return sendQuestStartDialog(env);
			}
		} else if (qs.getStatus() == QuestStatus.REWARD && endNpcIds.contains(targetId)) {
			return sendQuestEndDialog(env);
		}
		return false;
	}

	@Override
	public boolean onKillEvent(QuestEnv env) {
		//env.setQuestId(questId);
		for (OnKillEvent onKillEvent : onKillEvents) {
			if (onKillEvent.operate(env))
				return true;
		}
		return false;
	}
}
