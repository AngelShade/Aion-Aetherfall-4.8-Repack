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

/** Restored from TypeZero 4.8 (Hellboy); candy progress follows the native cast. */
public class _1093TheCalydonRuins extends AbstractQuestHandler {
    public _1093TheCalydonRuins() { super(1093); }

    @Override
    public void register() {
        qe.registerOnLevelChanged(questId);
        qe.registerOnQuestCompleted(questId);
        qe.registerOnEnterWorld(questId);
        qe.registerQuestSkill(18469, questId);
        for (int id : new int[]{798155, 203784, 798176, 700391, 700392, 700393, 798212})
            qe.registerQuestNpc(id).addOnTalkEvent(questId);
    }

    @Override
    public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player, 1092); }

    @Override
    public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env, 1092); }

    @Override
    public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer(), 1092); }

    @Override
    public boolean onCanAct(QuestEnv env, com.aionemu.gameserver.questEngine.model.QuestActionType action, Object... objects) {
        if (action != com.aionemu.gameserver.questEngine.model.QuestActionType.ACTION_ITEM_USE) return false;
        int target = env.getTargetId();
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        return target >= 700391 && target <= 700393 && qs != null && qs.getStatus() == QuestStatus.START
            && qs.getQuestVarById(0) == target - 700387;
    }

    @Override
    public boolean onDialogEvent(QuestEnv env) {
        Player player = env.getPlayer();
        QuestState qs = player.getQuestStateList().getQuestState(questId);
        if (qs == null) return false;
        int target = env.getTargetId(), action = env.getDialogActionId(), step = qs.getQuestVarById(0);
        if (qs.getStatus() == QuestStatus.REWARD) {
            if (target != 798155) return false;
            return action == USE_OBJECT || action == QUEST_SELECT ? sendQuestDialog(env, 10002) : sendQuestEndDialog(env);
        }
        if (qs.getStatus() != QuestStatus.START) return false;
        if (target == 798155 && step == 0) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1011);
            if (action == SETPRO1) return defaultCloseDialog(env, 0, 1);
        } else if (target == 203784 && step == 1) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1352);
            if (action == SETPRO2) {
                if (player.getInventory().getItemCountByItemId(182208013) == 0 && !giveQuestItem(env, 182208013, 1))
                    return false;
                return defaultCloseDialog(env, 1, 2);
            }
        } else if (target == 798176 && step == 2) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1693);
            if (action == SELECT3_1) { playQuestMovie(env, 365); return true; }
            if (action == SETPRO3) return defaultCloseDialog(env, 2, 3);
        } else if (target >= 700391 && target <= 700393 && action == USE_OBJECT && step == target - 700387) {
            int item = 182208014 + target - 700391;
            if (player.getInventory().getItemCountByItemId(item) == 0 && !giveQuestItem(env, item, 1))
                return false;
            changeQuestStep(env, step, step + 1);
            return true;
        } else if (target == 798212) {
            if (action == QUEST_SELECT && (step == 7 || step == 8))
                return sendQuestDialog(env, step == 7 ? 3398 : 3739);
            if (action == SETPRO4 && step == 7) return closeDialogWindow(env);
            if (action == CHECK_USER_HAS_QUEST_ITEM && step == 7) {
                if (!QuestService.collectItemCheck(env, true)) return sendQuestDialog(env, 10001);
                changeQuestStep(env, 7, 8);
                return sendQuestDialog(env, 3739);
            }
            if (action == SET_SUCCEED && step == 8) {
                changeQuestStep(env, 8, 8, true);
                return closeDialogWindow(env);
            }
        }
        return false;
    }

    @Override
    public void onMovieEndEvent(QuestEnv env, int movieId) {
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (movieId == 365 && env.getTargetId() == 798176 && qs != null
            && qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 2)
            sendQuestDialog(env, 1694); // Native select3_1; the player still confirms SETPRO3.
    }

    @Override
    public boolean onUseSkillEvent(QuestEnv env, int skillId) {
        Player player = env.getPlayer();
        QuestState qs = player.getQuestStateList().getQuestState(questId);
        if (skillId != 18469 || qs == null || qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != 3
            || player.getWorldId() != 210060000 || !player.isInsideItemUseZone("LF2A_ITEMUSEAREA_Q1093")
            || player.getInventory().getItemCountByItemId(182208013) == 0)
            return false;
        changeQuestStep(env, 3, 4);
        return true;
    }
}

