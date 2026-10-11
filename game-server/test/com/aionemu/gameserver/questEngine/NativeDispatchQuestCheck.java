package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Real dispatch handlers: existing campaign enrollment, offered pages and native delivery. */
public final class NativeDispatchQuestCheck {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static final String[] TYPES = {
        "_19070ADispatchtoVerteron", "_19071ADispatchtoVerteron", "_1913DispatchtoVerteron",
        "_1914DispatchtoVerteron", "_1915DispatchtoVerteron", "_1916DispatchtoVerteron",
        "_2901DispatchtoAltgard", "_2902DispatchtoAltgard", "_2903DispatchtoAltgard",
        "_2904DispatchtoAltgard", "_29070ADispatchtoAltgard", "_29071ADispatchtoAltgard"
    };
    public static void main(String[] args) throws Exception {
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true);
        NativeSielQuestCheck.unsafe = field.get(null);
        var old = DataManager.QUEST_DATA;
        try {
            DataManager.QUEST_DATA = (QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0], "data/static_data/quest_data/quest_data.xml").toFile());
            for (String type : TYPES) {
                var handler = (AbstractQuestHandler) Class.forName("quest.ascension." + type).getConstructor().newInstance();
                int quest = handler.getQuestId(); int sender = quest < 2000 || quest < 20000 && quest >= 19000 ? 203726 : 204191;
                int recipient = sender == 203726 ? 203097 : 203559;
                for (QuestStatus status : new QuestStatus[] {null, QuestStatus.LOCKED, QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE}) {
                    for (int stage : new int[] {0, 1, 2, 7}) {
                        var bot = NativeSielQuestCheck.bot(quest, status, stage);
                        var actions = handler.getPlayerBotActions(bot);
                        check(bot.cube.counts.isEmpty() && (status == null || bot.quests.getQuestState(quest).getStatus() == status
                            && bot.quests.getQuestState(quest).getQuestVarById(0) == stage
                            && bot.quests.getQuestState(quest).getQuestVarById(1) == 5), "Discovery preserves native enrollment, variables and ownership");
                        if (status == null || status == QuestStatus.LOCKED || status == QuestStatus.COMPLETE)
                            check(actions.isEmpty(), "Campaign prerequisite propagation remains the only enrollment path");
                        else if (status == QuestStatus.REWARD)
                            check(actions.getFirst().kind() == QuestBotAction.Kind.REWARD && actions.getFirst().targetId() == recipient, "Actual native reward recipient");
                        else if (stage == 0)
                            check(actions.getFirst().kind() == QuestBotAction.Kind.CONVERSATION && actions.getFirst().targetId() == sender
                                && actions.getFirst().page() == 1352 && actions.getFirst().dialogAction() == DialogAction.SETPRO1, "Actual first native conversation");
                        else if (stage == 1)
                            check(actions.getFirst().kind() == QuestBotAction.Kind.EVENT && actions.getFirst().targetId() == recipient
                                && actions.getFirst().dialogAction() == DialogAction.QUEST_SELECT, "Native delivery action owns reward transition");
                        else check(actions.getFirst().kind() == QuestBotAction.Kind.EVENT && actions.getFirst().targetId() == 0,
                            "Unknown saved stage cannot fabricate delivery");
                    }
                }
                var bot = NativeSielQuestCheck.bot(quest, QuestStatus.START, 0);
                var first = handler.getPlayerBotActions(bot).getFirst();
                check(Objects.equals(NativeSielQuestCheck.offered(handler, bot, sender, DialogAction.QUEST_SELECT), first.page()), "Real sender offers exactly the declared page");
                NativeSielQuestCheck.offered(handler, bot, recipient, DialogAction.QUEST_SELECT);
                check(bot.quests.getQuestState(quest).getQuestVarById(0) == 0 && bot.quests.getQuestState(quest).getStatus() == QuestStatus.START, "Premature recipient cannot advance quest");
                NativeSielQuestCheck.offered(handler, bot, sender, first.dialogAction());
                check(bot.quests.getQuestState(quest).getQuestVarById(0) == 1, "Native sender advances its own objective");
                var delivery = handler.getPlayerBotActions(bot).getFirst();
                check(Objects.equals(NativeSielQuestCheck.offered(handler, bot, recipient, delivery.dialogAction()), 2375), "Actual native delivery offers reward page");
                check(bot.quests.getQuestState(quest).getStatus() == QuestStatus.REWARD
                    && bot.quests.getQuestState(quest).getQuestVarById(1) == 5 && bot.cube.counts.isEmpty(), "Actual native transition preserves unrelated progress and items");
                check(handler.getPlayerBotActions(bot).getFirst().kind() == QuestBotAction.Kind.REWARD, "Next action uses native reward flow");
            }
            System.out.println("OK: " + checks + " actual dispatch handler and native delivery checks; no World/SQL/IDs/rewards");
        } finally { DataManager.QUEST_DATA = old; }
    }
}
