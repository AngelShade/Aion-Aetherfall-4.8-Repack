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

import java.util.ArrayList;
import java.util.List;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemAddType;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** TypeZero campaign flow adapted to matching 4.8 dialogue and owned native items. */
public class _2092GravesoftheRedSkyLegion extends AbstractQuestHandler {
    private static final int REMEDY = 182209009;
    private static final int[] GHOSTS = {205208,205209,205210,205212,205213,205214};
    private static final int[] GHOST_PAGES = {2717,2802,2887,2972,3058,3143};
    private static final int[] GHOST_ACTIONS = {SELECT6_1_1,SELECT6_2_1,SELECT6_3_1,SELECT6_4_1,SELECT7_1_1,SELECT7_2_1};

    public _2092GravesoftheRedSkyLegion() { super(2092); }

    @Override public void register() {
        qe.registerOnLevelChanged(questId);
        qe.registerOnQuestCompleted(questId);
        qe.registerOnEnterWorld(questId);
        for (int npc : new int[]{205150,205188,700394,205190}) qe.registerQuestNpc(npc).addOnTalkEvent(questId);
        for (int npc : GHOSTS) qe.registerQuestNpc(npc).addOnTalkEvent(questId);
        for (int npc : new int[]{214402,214403}) qe.registerQuestNpc(npc).addOnKillEvent(questId);
    }
    @Override public void onLevelChangedEvent(Player player) { defaultOnLevelChangedEvent(player,2091); }
    @Override public void onQuestCompletedEvent(QuestEnv env) { defaultOnQuestCompletedEvent(env,2091); }
    @Override public boolean onEnterWorldEvent(QuestEnv env) { return defaultOnLevelChangedEvent(env.getPlayer(),2091); }

    private boolean local(Player player, Npc npc) {
        return player.getWorldId() == 220050000 && player.isSpawned() && !player.isDead()
            && npc.isSpawned() && !npc.isDead() && player.getWorldMapInstance() == npc.getWorldMapInstance()
            && player.getKnownList().sees(npc) && PositionUtil.isInTalkRange(player,npc)
            && GeoService.getInstance().canSee(player,npc) && DialogService.isInteractionAllowed(player,npc);
    }

    @Override public boolean onCanAct(QuestEnv env, QuestActionType action, Object... objects) {
        QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
        return action == QuestActionType.ACTION_ITEM_USE && env.getTargetId() == 700394
            && state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 1;
    }

    @Override public boolean onDialogEvent(QuestEnv env) {
        Player player = env.getPlayer();
        QuestState state = player.getQuestStateList().getQuestState(questId);
        if (state == null || !(env.getVisibleObject() instanceof Npc npc)) return false;
        int target = npc.getNpcId(), action = env.getDialogActionId(), step = state.getQuestVarById(0);
        if (state.getStatus() == QuestStatus.REWARD) {
            if (target != 205150) return false;
            return action == USE_OBJECT || action == QUEST_SELECT ? sendQuestDialog(env,10002) : sendQuestEndDialog(env);
        }
        if (state.getStatus() != QuestStatus.START || !local(player,npc)) return false;
        if (target == 205150 && step == 0) {
            if (action == QUEST_SELECT) return sendQuestDialog(env,1011);
            if (action == SETPRO1) return defaultCloseDialog(env,0,1);
        } else if (target == 700394 && step == 1 && action == USE_OBJECT) {
            return useQuestObject(env,1,2,false,0);
        } else if (target == 205188 && step == 2) {
            if (action == QUEST_SELECT) return sendQuestDialog(env,1693);
            if (action == SETPRO3) return defaultCloseDialog(env,2,3);
        } else if (target == 205190) {
            if (step == 3) {
                if (action == QUEST_SELECT) return sendQuestDialog(env,2034);
                if (action == SETPRO4) return defaultCloseDialog(env,3,4);
            } else if (step == 4) {
                if (action == QUEST_SELECT) return sendQuestDialog(env,2375);
                if (action == CHECK_USER_HAS_QUEST_ITEM) return exchange(env,state,npc);
            }
        } else if (step == 5) {
            for (int i = 0; i < GHOSTS.length; i++) if (target == GHOSTS[i]) {
                if (player.getInventory().getItemCountByItemId(REMEDY) < 1) return false;
                if (action == QUEST_SELECT) return sendQuestDialog(env,GHOST_PAGES[i]);
                if (action == SETPRO6) synchronized (player) {
                    if (state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 5 || !local(player,npc)
                        || !removeQuestItem(env,REMEDY,1)) return false;
                    return defaultCloseDialog(env,5,6);
                }
            }
        }
        return false;
    }

    private record Debit(Item item, long count) {}

    /** Only the confirmed exchange mutates inventory; native stack operations retain debit receipts. */
    private boolean exchange(QuestEnv env, QuestState state, Npc npc) {
        Player player = env.getPlayer();
        synchronized (player) {
            if (state.getStatus() != QuestStatus.START || state.getQuestVarById(0) != 4 || !local(player,npc)) return false;
            if (!QuestService.collectItemCheck(env,false)) return sendQuestDialog(env,10001);
            var inventory = player.getInventory();
            var planned = new ArrayList<Debit>();
            for (var required : DataManager.QUEST_DATA.getQuestById(questId).getCollectItems().getCollectItem()) {
                long remaining = required.getCount();
                for (Item item : inventory.getItemsByItemId(required.getItemId())) {
                    long count = Math.min(remaining,item.getItemCount());
                    if (count > 0) planned.add(new Debit(item,count));
                    remaining -= count;
                    if (remaining == 0) break;
                }
                if (remaining != 0) return sendQuestDialog(env,10001);
            }
            var paid = new ArrayList<Debit>();
            boolean committed = false;
            try {
                for (Debit debit : planned) {
                    if (!inventory.decreaseByObjectId(debit.item().getObjectId(),debit.count(),QuestStatus.START))
                        return sendQuestDialog(env,10001);
                    paid.add(debit);
                }
                if (inventory.getItemCountByItemId(REMEDY) == 0) {
                    if (ItemService.addItem(player,REMEDY,1,true,
                        new ItemService.ItemUpdatePredicate(ItemAddType.QUEST_WORK_ITEM,ItemUpdateType.INC_ITEM_COLLECT)) != 0
                        || inventory.getItemCountByItemId(REMEDY) < 1) return false;
                }
                committed = true;
            } finally {
                if (!committed) for (Debit debit : paid)
                    if (ItemService.addItem(player,debit.item(),debit.count(),true,ItemService.DEFAULT_UPDATE_PREDICATE) != 0)
                        throw new IllegalStateException("Quest 2092 material debit could not be returned");
            }
            changeQuestStep(env,4,5,false);
            return sendQuestDialog(env,10000);
        }
    }

    @Override public boolean onKillEvent(QuestEnv env) {
        QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
        if (state == null || state.getStatus() != QuestStatus.START || env.getTargetId() != 214402 && env.getTargetId() != 214403) return false;
        synchronized (env.getPlayer()) {
            if (state.getStatus() != QuestStatus.START) return false;
            int step = state.getQuestVarById(0);
            if (step < 6 || step > 20) return false;
            if (step == 20) state.setStatus(QuestStatus.REWARD);
            else state.setQuestVarById(0,step+1);
            updateQuestStatus(env);
            return true;
        }
    }

    @Override public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state = player.getQuestStateList().getQuestState(questId);
        if (state == null || state.isStartable()) return List.of(QuestBotAction.event("Native campaign enrollment requires completed 2091"));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(205150));
        if (state.getStatus() != QuestStatus.START) return List.of();
        int step = state.getQuestVarById(0);
        if (step >= 6 && step <= 20) return QuestBotAction.hunts(214402,214403);
        return switch (step) {
            case 0 -> List.of(QuestBotAction.dialog(205150,SELECT1_1,1011).then(SETPRO1,1012));
            case 1 -> List.of(QuestBotAction.object(700394));
            case 2 -> List.of(QuestBotAction.dialog(205188,SELECT3_1,1693).then(SELECT3_1_1,1694).then(SETPRO3,1695));
            case 3 -> List.of(QuestBotAction.dialog(205190,SELECT4_1,2034).then(SETPRO4,2035));
            case 4 -> {
                var actions = new ArrayList<QuestBotAction>();
                actions.add(QuestBotAction.dialog(205190,CHECK_USER_HAS_QUEST_ITEM,2375)
                    .requiringCollection(DataManager.QUEST_DATA.getQuestById(questId).getCollectItems()));
                if (player.getInventory().getItemCountByItemId(182209008) < 10) actions.addAll(QuestBotAction.hunts(214380,214381));
                yield List.copyOf(actions);
            }
            case 5 -> {
                var actions = new ArrayList<QuestBotAction>();
                for (int i = 0; i < GHOSTS.length; i++) actions.add(QuestBotAction.dialog(GHOSTS[i],GHOST_ACTIONS[i],GHOST_PAGES[i])
                    .then(SETPRO6,GHOST_PAGES[i]+1).requiring(REMEDY,1));
                yield List.copyOf(actions);
            }
            default -> List.of(QuestBotAction.event("Saved campaign stage has no native transition"));
        };
    }
}
