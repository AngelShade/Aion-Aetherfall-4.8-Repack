package com.aionemu.gameserver.questEngine.model;

import java.util.List;
import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;

/** Reviewed native kill/object/report families; discovery never supplies credit or a spawn. */
public final class QuestBotKillObjectFamilies {
    private QuestBotKillObjectFamilies() { }

    public static List<QuestBotAction> haramel(Player player, int quest, int sender, int recipient) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(sender, DialogAction.ASK_QUEST_ACCEPT, 4762).then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(recipient));
        if (state.getStatus() != QuestStatus.START) return List.of();
        int stage = state.getQuestVarById(0);
        if (stage >= 0 && stage < 3) return List.of(QuestBotAction.hunt(700950));
        if (stage >= 3 && stage <= 5) return List.of(QuestBotAction.object(700953));
        return List.of(QuestBotAction.event("Saved Haramel cart/object counter has no native transition"));
    }

    public static List<QuestBotAction> spyLeague(Player player, int quest, int enrollmentItem,
                                                int report, int enemy, int recipient, int proof) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable()) return List.of(QuestBotAction.item(enrollmentItem));
        if (state.getStatus() == QuestStatus.REWARD)
            return List.of(QuestBotAction.reward(recipient).requiring(enrollmentItem, 1).requiring(proof, 1));
        if (state.getStatus() != QuestStatus.START) return List.of();
        return switch (state.getQuestVarById(0)) {
            case 0 -> List.of(QuestBotAction.dialog(report, DialogAction.SETPRO1, 1011));
            case 1 -> List.of(QuestBotAction.hunt(enemy));
            case 2 -> List.of(QuestBotAction.dialog(report, DialogAction.SET_SUCCEED, 1693));
            default -> List.of(QuestBotAction.event("Saved spy/league report or boss counter has no native transition"));
        };
    }

    public static List<QuestBotAction> smackShulack(Player player) {
        var state = player.getQuestStateList().getQuestState(4205);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(279010, DialogAction.ASK_QUEST_ACCEPT, 4762).then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(204285));
        if (state.getStatus() != QuestStatus.START) return List.of();
        int stage = state.getQuestVarById(0);
        if (stage >= 0 && stage < 15) return List.of(QuestBotAction.hunt(218972));
        if (stage == 15) return List.of(QuestBotAction.dialog(279010, DialogAction.SETPRO2, 1352));
        if (stage == 16) return List.of(QuestBotAction.dialog(204202, DialogAction.SET_SUCCEED, 1693));
        return List.of(QuestBotAction.event("Saved Shulack kill/report counter has no native transition"));
    }

    public static boolean hasNearbyNativeMentor(Player player) {
        return player.isInGroup() && player.getPlayerGroup().getMembers().stream()
            .anyMatch(member -> member.isMentor() && PositionUtil.isInRange(player, member, GroupConfig.GROUP_MAX_DISTANCE));
    }

    public static List<QuestBotAction> mentoredDaily(Player player, int quest, int object, int enemy, int recipient) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.event("Native daily/organization assignment and enrollment are required before this mentor hunt"));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(recipient));
        if (state.getStatus() != QuestStatus.START) return List.of();
        int stage = state.getQuestVarById(0);
        if (stage == 5) return List.of(QuestBotAction.dialog(recipient, DialogAction.SELECT_QUEST_REWARD, 1352));
        if (stage >= 0 && stage < 5) {
            var hunt = QuestBotAction.hunt(enemy);
            if (hasNearbyNativeMentor(player))
                return List.of(hunt, QuestBotAction.scripted(object, DialogAction.QUEST_SELECT,
                    "Native mentor object interaction creates the original five-minute quest enemy"));
            return List.of(hunt, QuestBotAction.event("Creating a native mentor quest enemy requires a nearby mentor in the actor's actual party"));
        }
        return List.of(QuestBotAction.event("Saved mentor hunt counter has no native transition"));
    }
}
