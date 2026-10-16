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
import static com.aionemu.gameserver.model.items.ItemUseAnimation.*;

import java.util.List;
import java.util.concurrent.FutureTask;
import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.geo.GeoService;

/** TypeZero flow adapted to matching 4.8 pages and current native item custody. */
public class _2054LightuptheLighthouse extends AbstractQuestHandler {
    public _2054LightuptheLighthouse() { super(2054); }

    @Override public void register() {
        qe.registerQuestItem(182204308, questId);
        qe.registerOnLevelChanged(questId);
        qe.registerOnQuestCompleted(questId);
        qe.registerOnEnterWorld(questId);
        for (int npc : new int[]{204768,204739,730109,730140,700287})
            qe.registerQuestNpc(npc).addOnTalkEvent(questId);
    }
    @Override public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player,2500); }
    @Override public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env,2500); }
    @Override public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer(),2500); }

    private boolean local(Player player, Npc npc) {
        return player.getWorldId() == 220040000 && !player.isDead() && player.isSpawned() && npc.isSpawned() && !npc.isDead()
            && player.getWorldMapInstance() == npc.getWorldMapInstance() && player.getKnownList().sees(npc)
            && PositionUtil.isInTalkRange(player,npc) && GeoService.getInstance().canSee(player,npc)
            && DialogService.isInteractionAllowed(player,npc);
    }

    @Override public boolean onCanAct(QuestEnv env, QuestActionType action, Object... objects) {
        QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
        return action == QuestActionType.ACTION_ITEM_USE && env.getTargetId() == 700287
            && state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 4
            && env.getPlayer().getInventory().getItemCountByItemId(182204309) >= 1;
    }

    @Override public boolean onDialogEvent(QuestEnv env) {
        Player player = env.getPlayer();
        QuestState state = player.getQuestStateList().getQuestState(questId);
        if (state == null) return false;
        int target = env.getTargetId(), action = env.getDialogActionId(), step = state.getQuestVarById(0);
        if (state.getStatus() == QuestStatus.REWARD) {
            if (target != 204768) return false;
            return action == USE_OBJECT || action == QUEST_SELECT ? sendQuestDialog(env,10002) : sendQuestEndDialog(env);
        }
        if (state.getStatus() != QuestStatus.START) return false;
        if (target == 204768 && step == 0) {
            if (action == QUEST_SELECT) return sendQuestDialog(env,1011);
            if (action == SETPRO1) return defaultCloseDialog(env,0,1);
        } else if (target == 204739 && step == 1) {
            if (action == QUEST_SELECT) return sendQuestDialog(env,1352);
            if (action == SETPRO2) return defaultCloseDialog(env,1,2);
        } else if (step == 3 && env.getVisibleObject() instanceof Npc chest && local(player,chest)) {
            if (target == 730109) {
                if (action == QUEST_SELECT) return sendQuestDialog(env,2034);
                if (action == SETPRO4) {
                    synchronized (chest) {
                        if (!local(player,chest) || state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 3) return false;
                        boolean present = chest.getWorldMapInstance().getNpcs(213912).stream()
                            .anyMatch(npc -> npc.isSpawned() && !npc.isDead() && npc.getCreatorId() == chest.getObjectId());
                        if (!present) SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(chest.getWorldId(),213912,
                            chest.getX(),chest.getY(),chest.getZ(),chest.getHeading(),chest,null),chest.getInstanceId());
                    }
                    return closeDialogWindow(env);
                }
            } else if (target == 730140) {
                if (action == QUEST_SELECT) return sendQuestDialog(env,2120);
                if (action == SETPRO5) {
                    synchronized (player) {
                        if (state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 3 || !local(player,chest)) return false;
                        if (player.getInventory().getItemCountByItemId(182204309) == 0 && !giveQuestItem(env,182204309,1)) return false;
                        if (player.getInventory().getItemCountByItemId(182204309) < 1) return false;
                        return defaultCloseDialog(env,3,4);
                    }
                }
            }
        } else if (target == 700287 && step == 4 && action == USE_OBJECT
            && env.getVisibleObject() instanceof Npc core && local(player,core)) {
            synchronized (player) {
                if (state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 4
                    || !removeQuestItem(env,182204309,1)) return false;
                changeQuestStep(env,4,5,true);
                return closeDialogWindow(env);
            }
        }
        return false;
    }

    @Override public HandlerResult onItemUseEvent(QuestEnv env, Item item) {
        if (item.getItemId() != 182204308) return HandlerResult.UNKNOWN;
        Player player = env.getPlayer();
        QuestState state = player.getQuestStateList().getQuestState(questId);
        if (state == null || state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 2
            || player.getInventory().getItemByObjId(item.getObjectId()) != item || item.getItemCount() < 1
            || player.getController().hasScheduledTask(TaskId.ITEM_USE)) return HandlerResult.FAILED;
        LetterRead read = new LetterRead(player,item,state);
        try {
            player.getObserveController().addObserver(read.observer);
            player.getController().addTask(TaskId.ITEM_USE,read);
            PacketSendUtility.broadcastPacketAndReceive(player,new SM_ITEM_USAGE_ANIMATION(player.getObjectId(),item.getObjectId(),item.getItemId(),1000,USE_START));
            ThreadPoolManager.getInstance().schedule(read,1000);
        } catch (RuntimeException | Error error) {
            player.getController().cancelTaskIfPresent(TaskId.ITEM_USE,read);
            read.cancel(false);
            throw error;
        }
        return HandlerResult.SUCCESS;
    }

    /** The existing useQuestItem helper has an unguarded three-second callback; retain the reference one-second read with native task custody. */
    private final class LetterRead extends FutureTask<Void> {
        private final Player player;
        private final Item item;
        private final QuestState state;
        private final int world, instance;
        private final ItemUseObserver observer;
        LetterRead(Player player, Item item, QuestState state) {
            super(() -> null);
            this.player=player; this.item=item; this.state=state; world=player.getWorldId(); instance=player.getInstanceId();
            observer=new ItemUseObserver(player) {
                @Override protected void onAbort() {
                    player.getController().cancelTaskIfPresent(TaskId.ITEM_USE,LetterRead.this);
                    LetterRead.this.cancel(false);
                }
            };
        }
        @Override public synchronized boolean cancel(boolean interrupt) {
            boolean cancelled=super.cancel(interrupt);
            if (cancelled) { player.getObserveController().removeObserver(observer); animation(USE_CANCEL); }
            return cancelled;
        }
        private void animation(com.aionemu.gameserver.model.items.ItemUseAnimation animation) {
            PacketSendUtility.broadcastPacketAndReceive(player,new SM_ITEM_USAGE_ANIMATION(player.getObjectId(),item.getObjectId(),item.getItemId(),0,animation));
        }
        @Override public synchronized void run() {
            if (isDone()) return;
            try {
                player.getObserveController().removeObserver(observer);
                synchronized (player) {
                    boolean present=player.isPlayerBot() ? PlayerBotService.getInstance().allowsHeadlessInteraction(player)
                        : player.isPlaying() && player.isOnline() && player.isSpawned() && !player.isDead();
                    if (!present || player.getWorldId()!=world || player.getInstanceId()!=instance
                        || player.getQuestStateList().getQuestState(questId)!=state || state.getStatus()!=QuestStatus.START
                        || state.getQuestVarById(0)!=2 || player.getInventory().getItemByObjId(item.getObjectId())!=item
                        || !removeQuestItem(new QuestEnv(null,player,questId),182204308,1)) { cancel(false); return; }
                    changeQuestStep(new QuestEnv(null,player,questId),2,3);
                }
                animation(USE_SUCCESS);
                super.run();
            } catch (RuntimeException | Error error) {
                cancel(false);
                throw error;
            }
        }
    }

    @Override public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null || state.isStartable()) return List.of(QuestBotAction.event("Native lighthouse campaign enrollment requires completed 2500"));
        if (state.getStatus()==QuestStatus.REWARD) return List.of(QuestBotAction.reward(204768));
        if (state.getStatus()!=QuestStatus.START) return List.of();
        return switch(state.getQuestVarById(0)) {
            case 0 -> List.of(QuestBotAction.dialog(204768,SELECT1_1,1011).then(SELECT1_1_1,1012).then(SETPRO1,1013));
            case 1 -> List.of(QuestBotAction.dialog(204739,SELECT2_1,1352).then(SETPRO2,1353));
            case 2 -> player.getInventory().getItemCountByItemId(182204308)>0
                ? List.of(QuestBotAction.item(182204308)) : List.of(QuestBotAction.hunt(213739));
            case 3 -> List.of(QuestBotAction.dialog(730140,SETPRO5,2120));
            case 4 -> List.of(QuestBotAction.object(700287).requiring(182204309,1));
            default -> List.of(QuestBotAction.event("Saved lighthouse stage has no native transition"));
        };
    }
}
