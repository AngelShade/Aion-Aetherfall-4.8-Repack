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

package quest.beluslan;

import static com.aionemu.gameserver.model.DialogAction.*;
import java.util.List;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Native Beluslan arrival campaign retained as the lighthouse prerequisite. */
public class _2500OrdersFromNerita extends AbstractQuestHandler {
    public _2500OrdersFromNerita() { super(2500); }
    @Override public void register() {
        qe.registerQuestNpc(204702).addOnTalkEvent(questId);
        qe.registerOnEnterWorld(questId);
        qe.registerOnLevelChanged(questId);
    }
    @Override public boolean onEnterWorldEvent(QuestEnv env) {
        Player player=env.getPlayer();
        if (player.getWorldId()!=220040000) return false;
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null || state.getStatus()==QuestStatus.LOCKED) return QuestService.startQuest(new QuestEnv(null,player,questId));
        return false;
    }
    @Override public void onLevelChangedEvent(Player player) { onEnterWorldEvent(new QuestEnv(null,player,questId)); }
    @Override public boolean onDialogEvent(QuestEnv env) {
        QuestState state=env.getPlayer().getQuestStateList().getQuestState(questId);
        if (state==null || env.getTargetId()!=204702) return false;
        if (state.getStatus()==QuestStatus.REWARD) return sendQuestEndDialog(env);
        if (state.getStatus()!=QuestStatus.START) return false;
        if (env.getDialogActionId()==QUEST_SELECT) return sendQuestDialog(env,10002);
        if (env.getDialogActionId()==SELECT_QUEST_REWARD) {
            changeQuestStep(env,0,1,true);
            return sendQuestEndDialog(env);
        }
        return false;
    }
    @Override public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null || state.isStartable()) return List.of(QuestBotAction.event("Native campaign enrollment requires arrival in Beluslan"));
        return state.getStatus()==QuestStatus.START || state.getStatus()==QuestStatus.REWARD
            ? List.of(QuestBotAction.reward(204702)) : List.of();
    }
}
