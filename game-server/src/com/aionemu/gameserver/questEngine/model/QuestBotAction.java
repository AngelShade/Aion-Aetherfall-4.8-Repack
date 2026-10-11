package com.aionemu.gameserver.questEngine.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only current-stage intent declared by the registered native handler.
 * This is discovery, never permission to award items, credit kills, or change quest state.
 * Dialogs must still be opened and the offered page checked through native interaction.
 */
public record QuestBotAction(Kind kind, int targetId, int dialogAction, int page,
                             Map<Integer, Long> requiredItems, String reason, List<DialogStep> continuation) {

    public enum Kind { CONVERSATION, OBJECT, HUNT, ITEM, SKILL, REWARD, EVENT, FLIGHT }

    public record DialogStep(int action, int page) {
        public DialogStep {
            if (page <= 0) throw new IllegalArgumentException("Continuation requires an offered native page");
        }
    }

    public QuestBotAction(Kind kind, int targetId, int dialogAction, int page,
                          Map<Integer, Long> requiredItems, String reason) {
        this(kind, targetId, dialogAction, page, requiredItems, reason, List.of());
    }

    public QuestBotAction {
        Objects.requireNonNull(kind);
        requiredItems = Map.copyOf(requiredItems);
        continuation = List.copyOf(continuation);
        reason = Objects.requireNonNull(reason);
        if (targetId < 0 || page < 0 || requiredItems.entrySet().stream()
            .anyMatch(e -> e.getKey() <= 0 || e.getValue() <= 0))
            throw new IllegalArgumentException("Invalid native quest action");
        if ((kind == Kind.EVENT || kind == Kind.FLIGHT) && reason.isBlank())
            throw new IllegalArgumentException("Native event dependency needs a reason");
    }

    public static QuestBotAction dialog(int npc, int action, int page) {
        return new QuestBotAction(Kind.CONVERSATION, npc, action, page, Map.of(), "");
    }

    public static QuestBotAction object(int npc) {
        return new QuestBotAction(Kind.OBJECT, npc, -1, 0, Map.of(), "");
    }

    public static QuestBotAction hunt(int npc) {
        return new QuestBotAction(Kind.HUNT, npc, 0, 0, Map.of(), "");
    }

    public static QuestBotAction reward(int npc) {
        return new QuestBotAction(Kind.REWARD, npc, 0, 0, Map.of(), "");
    }

    public static QuestBotAction item(int item) {
        return new QuestBotAction(Kind.ITEM, item, 0, 0, Map.of(item, 1L), "Use the owned native quest item");
    }

    public static QuestBotAction event(String reason) {
        return new QuestBotAction(Kind.EVENT, 0, 0, 0, Map.of(), reason);
    }

    /** Invoke this native initiating dialog once; its emitted operation owns completion. */
    public static QuestBotAction scripted(int npc, int action, String reason) {
        return new QuestBotAction(Kind.EVENT, npc, action, 0, Map.of(), reason);
    }

    /** Typed native flight initiation; pending native completion is independent of feature switches. */
    public static QuestBotAction flight(int npc, int action, String reason) {
        if (npc <= 0 || action <= 0) throw new IllegalArgumentException("Native flight requires its initiating NPC/action");
        return new QuestBotAction(Kind.FLIGHT, npc, action, 0, Map.of(), reason);
    }

    public QuestBotAction requiring(int item, long count) {
        var items = new java.util.HashMap<>(requiredItems);
        items.put(item, count);
        return new QuestBotAction(kind, targetId, dialogAction, page, items, reason, continuation);
    }

    /** Native collection requirements are custody prerequisites, not generated items. */
    public QuestBotAction requiringCollection(com.aionemu.gameserver.model.templates.quest.CollectItems collection) {
        QuestBotAction action = this;
        if (collection != null)
            for (var item : collection.getCollectItem())
                action = action.requiring(item.getItemId(), item.getCount());
        return action;
    }

    /** Each continuation runs only if the preceding native action offered this exact page. */
    public QuestBotAction then(int action, int offeredPage) {
        var steps = new java.util.ArrayList<>(continuation);
        steps.add(new DialogStep(action, offeredPage));
        return new QuestBotAction(kind, targetId, dialogAction, page, requiredItems, reason, steps);
    }

    public static List<QuestBotAction> hunts(int... npcs) {
        return java.util.Arrays.stream(npcs).mapToObj(QuestBotAction::hunt).toList();
    }
}
