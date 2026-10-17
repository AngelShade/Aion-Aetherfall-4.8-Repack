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

package quest.theobomos;

import static com.aionemu.gameserver.model.DialogAction.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/** Restored from TypeZero 4.8 (vlog), retaining all four native key requirements. */
public class _1094ProjectDrakanhammer extends AbstractQuestHandler {
    public _1094ProjectDrakanhammer() { super(1094); }

    @Override
    public void register() {
        qe.registerOnLevelChanged(questId);
        qe.registerOnQuestCompleted(questId);
        qe.registerOnEnterWorld(questId);
        for (int id : new int[]{203834, 798155, 700411, 730153})
            qe.registerQuestNpc(id).addOnTalkEvent(questId);
    }

    @Override
    public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player, 1093); }

    @Override
    public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env, 1093); }

    @Override
    public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer(), 1093); }

    @Override
    public boolean onCanAct(QuestEnv env, com.aionemu.gameserver.questEngine.model.QuestActionType action, Object... objects) {
        if (action != com.aionemu.gameserver.questEngine.model.QuestActionType.ACTION_ITEM_USE) return false;
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (qs == null || qs.getStatus() != QuestStatus.START) return false;
        int target = env.getTargetId(), step = qs.getQuestVarById(0);
        return target == 700411 && step == 2 || target == 730153 && step == 3
            && env.getPlayer().getInventory().getItemCountByItemId(182208017) > 0 && QuestService.collectItemCheck(env, false);
    }

    @Override
    public void onMovieEndEvent(QuestEnv env, int movieId) {
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (movieId == 367 && env.getTargetId() == 798155 && qs != null
            && qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 1)
            sendQuestDialog(env, 1353); // Native select2_1; the player still confirms SETPRO2.
    }

    @Override
    public boolean onDialogEvent(QuestEnv env) {
        Player player = env.getPlayer();
        QuestState qs = player.getQuestStateList().getQuestState(questId);
        if (qs == null) return false;
        int target = env.getTargetId(), action = env.getDialogActionId(), step = qs.getQuestVarById(0);
        if (qs.getStatus() == QuestStatus.REWARD) {
            if (target != 203834) return false;
            return action == USE_OBJECT || action == QUEST_SELECT ? sendQuestDialog(env, 10002) : sendQuestEndDialog(env);
        }
        if (qs.getStatus() != QuestStatus.START) return false;
        if (step == 0 && target == 203834) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1011);
            if (action == SETPRO1) return defaultCloseDialog(env, 0, 1);
        } else if (step == 1 && target == 798155) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1352);
            if (action == SELECT2_1) { playQuestMovie(env, 367); return true; }
            if (action == SETPRO2) return defaultCloseDialog(env, 1, 2);
        } else if (step == 2 && target == 700411 && action == USE_OBJECT) {
            if (player.getInventory().getItemCountByItemId(182208017) == 0 && !giveQuestItem(env, 182208017, 1))
                return false;
            changeQuestStep(env, 2, 3);
            return closeDialogWindow(env);
        } else if (step == 3 && target == 730153 && action == USE_OBJECT) {
            // A failed native collection check must never unlock the reward.
            if (player.getInventory().getItemCountByItemId(182208017) == 0 || !QuestService.collectItemCheck(env, true))
                return false;
            changeQuestStep(env, 3, 4, true);
            return closeDialogWindow(env);
        }
        return false;
    }
}

