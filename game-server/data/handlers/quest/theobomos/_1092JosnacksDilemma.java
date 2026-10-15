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
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Restored from TypeZero 4.8 (Dune11/vlog), using the existing native drops. */
public class _1092JosnacksDilemma extends AbstractQuestHandler {
    public _1092JosnacksDilemma() { super(1092); }

    @Override
    public void register() {
        qe.registerOnLevelChanged(questId);
        qe.registerOnQuestCompleted(questId);
        qe.registerOnEnterWorld(questId);
        qe.registerOnDie(questId);
        for (int id : new int[]{798155, 798206, 700389, 700388})
            qe.registerQuestNpc(id).addOnTalkEvent(questId);
        qe.registerQuestNpc(700390).addOnKillEvent(questId);
    }

    @Override
    public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player, 1091); }

    @Override
    public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env, 1091); }

    @Override
    public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer(), 1091); }

    @Override
    public boolean onCanAct(QuestEnv env, com.aionemu.gameserver.questEngine.model.QuestActionType action, Object... objects) {
        if (action != com.aionemu.gameserver.questEngine.model.QuestActionType.ACTION_ITEM_USE) return false;
        int target = env.getTargetId();
        if (target != 700388 && target != 700389) return false;
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        return qs != null && qs.getStatus() == QuestStatus.START && qs.getQuestVarById(0) == 2
            && qs.getQuestVarById(target == 700389 ? 1 : 2) == 0;
    }

    @Override
    public boolean onDialogEvent(QuestEnv env) {
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (qs == null)
            return false;
        int target = env.getTargetId(), action = env.getDialogActionId(), step = qs.getQuestVarById(0);
        if (qs.getStatus() == QuestStatus.REWARD)
            return target == 798155 && sendQuestEndDialog(env);
        if (qs.getStatus() != QuestStatus.START)
            return false;
        if (target == 798155) {
            if (action == QUEST_SELECT) {
                if (step == 0) return sendQuestDialog(env, 1011);
                if (step == 3) return sendQuestDialog(env, 2034);
                if (step == 4) return sendQuestDialog(env, 2375);
            }
            if (action == SETPRO1 && step == 0) return defaultCloseDialog(env, 0, 1);
            if (action == SETPRO4 && step == 3) return defaultCloseDialog(env, 3, 4);
            if (action == CHECK_USER_HAS_QUEST_ITEM && step == 4)
                return checkQuestItems(env, 4, 4, true, 10001, 10008);
        } else if (target == 798206 && (step == 1 || step == 2)) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, step == 1 ? 1352 : 1693);
            if (action == SELECT2_1 && step == 1) {
                playQuestMovie(env, 364);
                return true;
            }
            if (action == SETPRO2 && step == 1) return defaultCloseDialog(env, 1, 2);
        } else if ((target == 700389 || target == 700388) && action == USE_OBJECT && step == 2) {
            int index = target == 700389 ? 1 : 2;
            if (qs.getQuestVarById(index) != 0) return false;
            qs.setQuestVarById(index, 1);
            if (qs.getQuestVarById(1) == 1 && qs.getQuestVarById(2) == 1)
                qs.setQuestVar(3);
            updateQuestStatus(env);
            return true;
        }
        return false;
    }

    @Override
    public boolean onDieEvent(QuestEnv env) {
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (qs == null || qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != 2)
            return false;
        qs.setQuestVar(1); // Both stones must be cleared again, in either order.
        updateQuestStatus(env);
        return true;
    }

    @Override
    public boolean onKillEvent(QuestEnv env) {
        Player player = env.getPlayer();
        QuestState qs = player.getQuestStateList().getQuestState(questId);
        if (qs == null || qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != 4
            || player.getWorldId() != 210060000 || !(env.getVisibleObject() instanceof Npc flame)
            || flame.getNpcId() != 700390 || player.getInventory().getItemCountByItemId(182208012) >= 6)
            return false;
        for (float offset : new float[]{0, -2, 2})
            spawn(214552, flame, flame.getX() + offset, flame.getY(), flame.getZ(), flame.getHeading());
        return true;
    }
}

