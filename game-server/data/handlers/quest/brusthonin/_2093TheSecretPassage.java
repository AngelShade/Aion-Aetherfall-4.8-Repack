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
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** TypeZero campaign adapted to the matching 4.8 pages and native object drops. */
public class _2093TheSecretPassage extends AbstractQuestHandler {
    public _2093TheSecretPassage() { super(2093); }
    @Override public void register() {
        qe.addHandlerSideQuestDrop(questId,730174,182209011,1,100,6);
        qe.addHandlerSideQuestDrop(questId,700395,182209012,1,100,8);
        for (int npc : new int[]{205150,205159,205164,205197,205198,730174,700395})
            qe.registerQuestNpc(npc).addOnTalkEvent(questId);
        qe.registerOnGetItem(182209011,questId);
        qe.registerOnGetItem(182209012,questId);
        qe.registerOnLevelChanged(questId);
        qe.registerOnQuestCompleted(questId);
        qe.registerOnEnterWorld(questId);
    }
    @Override public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player,2091,2092); }
    @Override public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env,2091,2092); }
    @Override public boolean onEnterWorldEvent(QuestEnv env) {
        boolean started=defaultOnLevelChangedEvent(env.getPlayer(),2091,2092);
        return onGetItemEvent(env) || started;
    }
    @Override public boolean onCanAct(QuestEnv env, QuestActionType action, Object... objects) {
        QuestState state=env.getPlayer().getQuestStateList().getQuestState(questId);
        if (action!=QuestActionType.ACTION_ITEM_USE || state==null || state.getStatus()!=QuestStatus.START) return false;
        int step=state.getQuestVarById(0);
        return step==6 && env.getTargetId()==730174 && env.getPlayer().getInventory().getItemCountByItemId(182209011)==0
            || step==8 && env.getTargetId()==700395 && env.getPlayer().getInventory().getItemCountByItemId(182209012)==0;
    }
    @Override public boolean onGetItemEvent(QuestEnv env) {
        Player player=env.getPlayer();
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null || state.getStatus()!=QuestStatus.START) return false;
        int step=state.getQuestVarById(0);
        if (step==6 && player.getInventory().getItemCountByItemId(182209011)>0) return defaultOnGetItemEvent(env,6,7,false);
        if (step==8 && player.getInventory().getItemCountByItemId(182209012)>0) return defaultOnGetItemEvent(env,8,8,true);
        return false;
    }
    @Override public boolean onDialogEvent(QuestEnv env) {
        Player player=env.getPlayer();
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null) return false;
        int target=env.getTargetId(), action=env.getDialogActionId(), step=state.getQuestVarById(0);
        if (state.getStatus()==QuestStatus.REWARD) return target==205150
            && (action==USE_OBJECT || action==QUEST_SELECT ? sendQuestDialog(env,10002) : sendQuestEndDialog(env));
        if (state.getStatus()!=QuestStatus.START) return false;
        if (target==205150 && step==0) {
            if (action==QUEST_SELECT) return sendQuestDialog(env,1011);
            if (action==SETPRO1) return defaultCloseDialog(env,0,1);
        } else if (target==205159 && step==1) {
            if (action==QUEST_SELECT) return sendQuestDialog(env,1352);
            if (action==SETPRO2) return defaultCloseDialog(env,1,2);
        } else if (target==205164 && step==2) {
            if (action==QUEST_SELECT) return sendQuestDialog(env,1693);
            if (action==SETPRO3) return defaultCloseDialog(env,2,3);
        } else if (target==205197 && (step==3 || step==4)) {
            if (action==QUEST_SELECT) return sendQuestDialog(env,step==3 ? 2034 : 2375);
            if (step==3 && action==SETPRO4) return defaultCloseDialog(env,3,4);
            if (step==4 && action==CHECK_USER_HAS_QUEST_ITEM) return checkQuestItems(env,4,5,false,10000,10001);
        } else if (target==205198) {
            if (step==5) {
                if (action==QUEST_SELECT) return sendQuestDialog(env,2716);
                if (action==SETPRO6) return defaultCloseDialog(env,5,6);
            } else if (step==7 && player.getInventory().getItemCountByItemId(182209011)>0) {
                if (action==QUEST_SELECT) return sendQuestDialog(env,3398);
                // The reference debited the book on a preview. Only the committed transition owns it.
                if (action==SETPRO8) {
                    synchronized (player) {
                        if (state.getStatus()!=QuestStatus.START || state.getQuestVarById(0)!=7 || !removeQuestItem(env,182209011,1)) return false;
                        return defaultCloseDialog(env,7,8);
                    }
                }
            }
        } else if (action==USE_OBJECT && (step==6 && target==730174 || step==8 && target==700395)) {
            // QuestItemNpcAI owns the real use channel, loot registration and item acquisition callback.
            return true;
        }
        return false;
    }
    @Override public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null || state.isStartable()) return List.of(QuestBotAction.event("Native campaign enrollment requires completed2091/2092"));
        if (state.getStatus()==QuestStatus.REWARD) return List.of(QuestBotAction.reward(205150));
        if (state.getStatus()!=QuestStatus.START) return List.of();
        return switch(state.getQuestVarById(0)) {
            case 0 -> List.of(QuestBotAction.dialog(205150,SELECT1_1,1011).then(SELECT1_1_1,1012).then(SETPRO1,1013));
            case 1 -> List.of(QuestBotAction.dialog(205159,SELECT2_1,1352).then(SETPRO2,1353));
            case 2 -> List.of(QuestBotAction.dialog(205164,SELECT3_1,1693).then(SETPRO3,1694));
            case 3 -> List.of(QuestBotAction.dialog(205197,SELECT4_1,2034).then(SETPRO4,2035));
            case 4 -> List.of(QuestBotAction.dialog(205197,CHECK_USER_HAS_QUEST_ITEM,2375)
                .requiringCollection(DataManager.QUEST_DATA.getQuestById(questId).getCollectItems()));
            case 5 -> List.of(QuestBotAction.dialog(205198,SELECT6_1,2716).then(SETPRO6,2717));
            case 6 -> List.of(QuestBotAction.object(730174));
            case 7 -> List.of(QuestBotAction.dialog(205198,SELECT8_1,3398).then(SETPRO8,3399).requiring(182209011,1));
            case 8 -> List.of(QuestBotAction.object(700395));
            default -> List.of(QuestBotAction.event("Saved secret passage stage has no native transition"));
        };
    }
}
