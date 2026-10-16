package quest.brusthonin;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Matching 4.8 conversation followed by twelve real Undead Grunt kills. */
public class _24201HistoryMystery extends AbstractQuestHandler {
    public _24201HistoryMystery() { super(24201); }

    @Override public void register() {
        qe.registerQuestNpc(205150).addOnQuestStart(questId);
        qe.registerQuestNpc(205150).addOnTalkEvent(questId);
        qe.registerQuestNpc(205188).addOnTalkEvent(questId);
        for (int npc : new int[]{214402,214403}) qe.registerQuestNpc(npc).addOnKillEvent(questId);
    }

    @Override public boolean onDialogEvent(QuestEnv env) {
        QuestState state=env.getPlayer().getQuestStateList().getQuestState(questId);
        int target=env.getTargetId(), action=env.getDialogActionId();
        if (state==null || state.isStartable()) {
            if (target!=205150) return false;
            return action==QUEST_SELECT ? sendQuestDialog(env,1011) : sendQuestStartDialog(env);
        }
        if (state.getStatus()==QuestStatus.REWARD) return target==205150
            && (action==QUEST_SELECT ? sendQuestDialog(env,2375) : sendQuestEndDialog(env));
        if (state.getStatus()!=QuestStatus.START) return false;
        if (target==205188 && state.getQuestVarById(1)==0) {
            if (action==QUEST_SELECT) return sendQuestDialog(env,1352);
            if (action==SETPRO1) {
                // Keep the native XML kill counter in section 0; section 1 records the completed conversation.
                state.setQuestVarById(1,1);
                updateQuestStatus(env);
                return closeDialogWindow(env);
            }
        }
        return false;
    }

    @Override public boolean onKillEvent(QuestEnv env) {
        QuestState state=env.getPlayer().getQuestStateList().getQuestState(questId);
        if (state==null || state.getStatus()!=QuestStatus.START || state.getQuestVarById(1)!=1
            || env.getTargetId()!=214402 && env.getTargetId()!=214403 || state.getQuestVarById(0)>=12) return false;
        state.setQuestVarById(0,state.getQuestVarById(0)+1);
        if (state.getQuestVarById(0)==12) state.setStatus(QuestStatus.REWARD);
        updateQuestStatus(env);
        return true;
    }

    @Override public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state=player.getQuestStateList().getQuestState(questId);
        if (state==null || state.isStartable()) return List.of(QuestBotAction.dialog(205150,SELECT1_1,1011)
            .then(QUEST_ACCEPT_SIMPLE,1012));
        if (state.getStatus()==QuestStatus.REWARD) return List.of(QuestBotAction.reward(205150));
        if (state.getStatus()!=QuestStatus.START) return List.of();
        if (state.getQuestVarById(1)==0) return List.of(QuestBotAction.dialog(205188,SELECT2_1,1352).then(SETPRO1,1353));
        return QuestBotAction.hunts(214402,214403);
    }
}
