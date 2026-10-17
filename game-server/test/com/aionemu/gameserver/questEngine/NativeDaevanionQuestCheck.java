package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Native acceptance pages, all declared enemy variants and exact ten-kill reward transitions. */
public final class NativeDaevanionQuestCheck {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static final String[] TYPES = {
        "_19631CoastalCrush", "_19632CascadeCritters", "_19633AlisaryAssistance", "_19634FurtherAidforAlisary",
        "_19635SouthernQuell", "_19636FinalStabilization", "_19637OnboardforOne", "_19638TroublewithTwos",
        "_19639TreesandThrees", "_19640FlyingthroughFour", "_19641FidgetyFives", "_19642SuccessforSix",
        "_29631GlugGlugGlug", "_29632SweepingNahorLake", "_29633StabilizetheSaplands", "_29634ScaredSkurvs",
        "_29635BeachDay", "_29636BacktoSurt", "_29637TroubleNotTrivial", "_29638NotSoSweet",
        "_29639MonstersUnholy", "_29640FinalKrugClearing", "_29641MoveAlongNow", "_29642GoodOnGelkmaros"
    };
    static int[] ints(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true);
        var value = field.get(null); return value instanceof Integer number ? new int[] {number} : ((int[]) value).clone();
    }
    static QuestEnv kill(NativeSielQuestCheck.Bot bot, int quest, int npc) throws Exception {
        var target = NativeSielQuestCheck.allocate(NativeSielQuestCheck.Contact.class); target.id = npc;
        return new QuestEnv(target, bot, quest);
    }
    public static void main(String[] args) throws Exception {
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true);
        NativeSielQuestCheck.unsafe = field.get(null);
        var old = DataManager.QUEST_DATA;
        try {
            DataManager.QUEST_DATA = (QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0], "data/static_data/quest_data/quest_data.xml").toFile());
            for (String name : TYPES) {
                Class<?> type = Class.forName("quest.daevanion." + name);
                var handler = (AbstractQuestHandler) type.getConstructor().newInstance(); int quest = handler.getQuestId();
                int[] npcs;
                try { npcs = ints(type, "npcIds"); } catch (NoSuchFieldException ignored) { npcs = ints(type, "npcId"); }
                int[] enemies = ints(type, "mobIds");
                var bot = NativeSielQuestCheck.bot(quest, null, 0); var acceptance = handler.getPlayerBotActions(bot).getFirst();
                check(acceptance.targetId() == npcs[0] && acceptance.dialogAction() == DialogAction.ASK_QUEST_ACCEPT
                    && acceptance.continuation().getFirst().action() == DialogAction.QUEST_ACCEPT, "Acceptance uses actual native sender/actions");
                check(Objects.equals(NativeSielQuestCheck.offered(handler, bot, npcs[0], DialogAction.QUEST_SELECT), acceptance.page()), "Real native offered acceptance page");
                check(Objects.equals(NativeSielQuestCheck.offered(handler, bot, npcs[0], acceptance.dialogAction()), acceptance.continuation().getFirst().page()), "Real offered native accept window");
                check(bot.quests.getQuestState(quest) == null && bot.cube.counts.isEmpty(), "Dialog discovery never grants enrollment/items");
                for (int enemy : enemies) {
                    for (int counter : new int[] {0, 8, 9, 10, 15}) {
                        var actor = NativeSielQuestCheck.bot(quest, QuestStatus.START, 0); var state = actor.quests.getQuestState(quest);
                        state.setQuestVarById(1, counter); state.setQuestVarById(3, 7);
                        var actions = handler.getPlayerBotActions(actor);
                        check(counter > 9 ? actions.getFirst().kind() == QuestBotAction.Kind.EVENT :
                            actions.stream().allMatch(a -> a.kind() == QuestBotAction.Kind.HUNT)
                            && actions.stream().mapToInt(QuestBotAction::targetId).boxed().collect(java.util.stream.Collectors.toSet())
                                .equals(Arrays.stream(enemies).boxed().collect(java.util.stream.Collectors.toSet())), "Only current native stage/count admits exact enemies");
                        check(!handler.onKillEvent(kill(actor, quest, 999999)), "Unrelated enemy receives no credit");
                        check(handler.onKillEvent(kill(actor, quest, enemy)) == (counter <= 9), "Actual native kill guard matches adapter");
                        if (counter < 9) check(state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 0
                            && state.getQuestVarById(1) == counter + 1, "Actual native counter increments once");
                        else if (counter == 9) check(state.getStatus() == QuestStatus.REWARD && state.getQuestVarById(0) == 1
                            && state.getQuestVarById(1) == 9, "Tenth actual kill opens native reward state");
                        else check(state.getStatus() == QuestStatus.START && state.getQuestVarById(1) == counter, "Invalid saved counter cannot fabricate reward");
                        check(state.getQuestVarById(3) == 7 && actor.cube.counts.isEmpty(), "Native objective preserves unrelated progress/ownership");
                    }
                }
                var wrong = NativeSielQuestCheck.bot(quest, QuestStatus.START, 1);
                check(handler.getPlayerBotActions(wrong).getFirst().kind() == QuestBotAction.Kind.EVENT
                    && !handler.onKillEvent(kill(wrong, quest, enemies[0])), "Different stage cannot receive kill credit");
                var reward = NativeSielQuestCheck.bot(quest, QuestStatus.REWARD, 1);
                check(handler.getPlayerBotActions(reward).getFirst().kind() == QuestBotAction.Kind.REWARD
                    && handler.getPlayerBotActions(reward).getFirst().targetId() == npcs[npcs.length - 1], "Reward uses actual destination");
                check(NativeSielQuestCheck.offered(handler, reward, npcs[npcs.length - 1], DialogAction.USE_OBJECT) != null, "Real native reward page is available");
                check(!handler.onKillEvent(kill(reward, quest, enemies[0])), "Reward cannot repeat kills");
                var complete = NativeSielQuestCheck.bot(quest, QuestStatus.COMPLETE, 1);
                check(handler.getPlayerBotActions(complete).isEmpty(), "Finished nonrepeatable field quest cannot replay");
            }
            System.out.println("OK: " + checks + " actual Daevanion native action/kill/reward checks; no World/SQL/IDs/grants");
        } finally { DataManager.QUEST_DATA = old; }
    }
}
