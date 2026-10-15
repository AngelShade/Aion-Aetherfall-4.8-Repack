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

/** Restored from TypeZero 4.8; native completion dispatch unlocks the follow-up. */
public class _1091ARequestFromAtropos extends AbstractQuestHandler {
    public _1091ARequestFromAtropos() { super(1091); }

    @Override
    public void register() {
        qe.registerOnLevelChanged(questId);
        qe.registerOnEnterWorld(questId);
        qe.registerQuestNpc(798155).addOnTalkEvent(questId);
    }

    @Override
    public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player); }

    @Override
    public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer()); }

    @Override
    public boolean onDialogEvent(QuestEnv env) {
        QuestState qs = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (qs == null || env.getTargetId() != 798155)
            return false;
        if (qs.getStatus() == QuestStatus.REWARD)
            return sendQuestEndDialog(env);
        if (qs.getStatus() != QuestStatus.START || qs.getQuestVarById(0) != 0)
            return false;
        if (env.getDialogActionId() == QUEST_SELECT)
            return sendQuestDialog(env, 10002);
        if (env.getDialogActionId() == SELECT_QUEST_REWARD) {
            changeQuestStep(env, 0, 1, true);
            return sendQuestDialog(env, 5);
        }
        return false;
    }
}

