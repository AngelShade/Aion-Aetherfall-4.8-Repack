package quest.brusthonin;

import static com.aionemu.gameserver.model.DialogAction.*;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestBotAction;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/** Matching 4.8 Q24202 dialogue stages and native Secret Passage Key collection. */
public class _24202LetMeInLetMeIn extends AbstractQuestHandler {
    public _24202LetMeInLetMeIn() { super(24202); }

    @Override
    public void register() {
        qe.registerQuestNpc(205150).addOnQuestStart(questId);
        for (int npc : new int[]{205150, 205159, 205198})
            qe.registerQuestNpc(npc).addOnTalkEvent(questId);
    }

    @Override
    public boolean onDialogEvent(QuestEnv env) {
        QuestState state = env.getPlayer().getQuestStateList().getQuestState(questId);
        int target = env.getTargetId(), action = env.getDialogActionId();
        if (state == null || state.isStartable()) {
            if (target != 205150) return false;
            return action == QUEST_SELECT ? sendQuestDialog(env, 1011) : sendQuestStartDialog(env);
        }
        if (state.getStatus() == QuestStatus.REWARD)
            return target == 205150 && sendQuestEndDialog(env);
        if (state.getStatus() != QuestStatus.START) return false;
        int step = state.getQuestVarById(0);
        if (target == 205159 && step == 0) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1352);
            if (action == SETPRO1) return defaultCloseDialog(env, 0, 1);
        } else if (target == 205198 && step == 1) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 1693);
            if (action == SETPRO2) return defaultCloseDialog(env, 1, 2);
        } else if (target == 205150 && step == 2) {
            if (action == QUEST_SELECT) return sendQuestDialog(env, 2375);
            if (action == CHECK_USER_HAS_QUEST_ITEM_SIMPLE)
                return checkQuestItemsSimple(env, 2, 3, true, 10002, 0, 0);
        }
        return false;
    }

    @Override
    public List<QuestBotAction> getPlayerBotActions(Player player) {
        QuestState state = player.getQuestStateList().getQuestState(questId);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(205150, SELECT1_1, 1011)
                .then(ASK_QUEST_ACCEPT, 1012).then(QUEST_ACCEPT_1, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(205150));
        if (state.getStatus() != QuestStatus.START) return List.of();
        return switch (state.getQuestVarById(0)) {
            case 0 -> List.of(QuestBotAction.dialog(205159, SELECT2_1, 1352).then(SETPRO1,1353));
            case 1 -> List.of(QuestBotAction.dialog(205198, SELECT3_1, 1693).then(SETPRO2,1694));
            case 2 -> player.getInventory().getItemCountByItemId(182215465) >= 1
                ? List.of(QuestBotAction.dialog(205150, CHECK_USER_HAS_QUEST_ITEM_SIMPLE, 2375).requiring(182215465, 1))
                : QuestBotAction.hunts(214437, 214438);
            default -> List.of(QuestBotAction.event("Saved key quest stage has no native transition"));
        };
    }
}
