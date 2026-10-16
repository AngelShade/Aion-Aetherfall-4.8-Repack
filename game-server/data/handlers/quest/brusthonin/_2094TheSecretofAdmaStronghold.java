/*
 * Copyright (c) 2015, TypeZero Engine (game.developpers.com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of TypeZero Engine nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package quest.brusthonin;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PositionUtil;

/** TypeZero 4.8 campaign flow, adapted to current quest and instance custody. */
public class _2094TheSecretofAdmaStronghold extends AbstractQuestHandler {
    private final int waterItem;
    public _2094TheSecretofAdmaStronghold() { this(2094,182209013); }
    protected _2094TheSecretofAdmaStronghold(int id, int waterItem) { super(id); this.waterItem = waterItem; }

    @Override
    public void register() {
        if (questId == 2094) {
            qe.registerOnLevelChanged(questId);
            qe.registerOnQuestCompleted(questId);
            qe.registerOnEnterWorld(questId);
        } else qe.registerQuestNpc(205150).addOnQuestStart(questId);
        qe.registerQuestNpc(214701).addOnKillEvent(questId);
        for (int id : new int[]{205150, 205192, 205155, 730164, 205191, 204057})
            qe.registerQuestNpc(id).addOnTalkEvent(questId);
    }

    @Override
    public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player, 2093, 2054); }

    @Override
    public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env, 2093, 2054); }

    @Override
    public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer(), 2093, 2054); }

    @Override
    public boolean onCanAct(QuestEnv env, QuestActionType action, Object... objects) {
        QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
        return action == QuestActionType.ACTION_ITEM_USE && env.getTargetId() == 730164
            && state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 6;
    }

    @Override
    public boolean onDialogEvent(QuestEnv env) {
        Player player = env.getPlayer();
        QuestState state = player.getQuestStateList().getQuestState(questId);
        int target = env.getTargetId(), action = env.getDialogActionId();
        if (questId != 2094 && (state == null || state.isStartable()) && target == 205150) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 4762);
            return sendQuestStartDialog(env);
        }
        if (state == null) return false;
        int step = state.getQuestVarById(0);
        if (state.getStatus() == QuestStatus.REWARD) {
            if (target != 204057) return false;
            return action == USE_OBJECT || action == QUEST_SELECT ? sendQuestDialog(env, 10002) : sendQuestEndDialog(env);
        }
        if (state.getStatus() != QuestStatus.START) return false;
        if (target == 205150 && step == 0) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1011);
            if (action == SETPRO1) return defaultCloseDialog(env, 0, 1);
        } else if (target == 205192) {
            if (action == QUEST_SELECT && step >= 1 && step <= 3)
                return sendQuestDialog(env, 1011 + step * 341);
            if (step == 1 && action == SETPRO2) return defaultCloseDialog(env, 1, 2);
            if (step == 2 && action == CHECK_USER_HAS_QUEST_ITEM) {
                if (!QuestService.collectItemCheck(env, true)) return sendQuestDialog(env, 10008);
                changeQuestStep(env, 2, 3);
                return sendQuestDialog(env, 10001);
            }
            if (step == 3 && action == SETPRO4) {
                if (questId == 24203 && player.getInventory().getItemCountByItemId(182215467) == 0
                    && !giveQuestItem(env,182215467,1)) return false;
                return defaultCloseDialog(env, 3, 4);
            }
        } else if (target == 205155 && step == 5) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 2716);
            if (action == SETPRO6) return defaultCloseDialog(env, 5, 6);
        } else if (target == 730164 && step == 6 && action == USE_OBJECT && env.getVisibleObject() instanceof Npc grave) {
            // The shared tombstone remains usable. Reuse one temporary soul in this actual instance.
            synchronized (grave) {
                boolean present = grave.getWorldMapInstance().getNpcs(205191).stream()
                    .anyMatch(soul -> soul.isSpawned() && !soul.isDead() && PositionUtil.isInRange(grave, soul, 10));
                if (!present) spawnForFiveMinutes(205191, grave.getPosition());
            }
            return closeDialogWindow(env);
        } else if (target == 205191 && step == 6 && (action == USE_OBJECT || action == QUEST_SELECT)) {
            changeQuestStep(env, 6, 7, true);
            return closeDialogWindow(env);
        }
        return false;
    }

    @Override
    public boolean onKillEvent(QuestEnv env) { return defaultOnKillEvent(env, 214701, 4, 5); }

    @Override
    public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state = player.getQuestStateList().getQuestState(questId);
        if (state == null || state.isStartable()) return questId == 2094
            ? List.of(QuestBotAction.event("Native campaign enrollment requires completed 2093 and 2054"))
            : List.of(QuestBotAction.dialog(205150,ASK_QUEST_ACCEPT,4762).then(QUEST_ACCEPT_1,4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(204057));
        if (state.getStatus() != QuestStatus.START) return List.of();
        return switch (state.getQuestVarById(0)) {
            case 0 -> List.of(QuestBotAction.dialog(205150, SETPRO1, 1011));
            case 1 -> List.of(QuestBotAction.dialog(205192, SETPRO2, 1352));
            case 2 -> player.getInventory().getItemCountByItemId(waterItem) >= 3
                ? List.of(QuestBotAction.dialog(205192, CHECK_USER_HAS_QUEST_ITEM, 1693).requiring(waterItem, 3))
                : List.of(QuestBotAction.hunt(213360));
            case 3 -> List.of(QuestBotAction.dialog(205192, SETPRO4, 2034));
            case 4 -> List.of(QuestBotAction.hunt(214701), QuestBotAction.hunt(214700));
            case 5 -> List.of(QuestBotAction.dialog(205155, SETPRO6, 2716));
            case 6 -> player.getKnownList().stream().map(known -> known.get())
                .anyMatch(object -> object instanceof Npc soul && soul.getNpcId() == 205191 && soul.isSpawned() && !soul.isDead())
                ? List.of(QuestBotAction.scripted(205191, USE_OBJECT, "Speak to the actual summoned soul of Virkel"))
                : List.of(QuestBotAction.object(730164));
            default -> List.of(QuestBotAction.event("Saved campaign stage has no native transition"));
        };
    }
}
