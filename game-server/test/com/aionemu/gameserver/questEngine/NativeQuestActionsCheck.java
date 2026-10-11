package com.aionemu.gameserver.questEngine;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import quest.poeta.*;
import quest.ishalgen.*;
import quest.eltnen._1038TheShadowsCommand;
import quest.heiron._18602NightmareinShiningArmor;

/** Real registered handlers and inert actors; no World, ID factory, SQL or native transport. */
public final class NativeQuestActionsCheck {
    static int checks;
    static Object unsafe;
    static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static final class Bot extends Player {
        QuestStateList quests; Inventory cube;
        Bot() { super(null, null); }
        @Override public QuestStateList getQuestStateList() { return quests; }
        @Override public PlayerStorage getInventory() { return cube; }
        @Override public boolean isOnline() { return false; }
    }
    static final class Inventory extends PlayerStorage {
        final Map<Integer, Long> counts = new HashMap<>();
        int removals;
        Inventory(Player player) { super(player, StorageType.CUBE); }
        @Override public long getItemCountByItemId(int id) { return counts.getOrDefault(id, 0L); }
        @Override public boolean decreaseByItemId(int id, long count, QuestStatus status) {
            if (getItemCountByItemId(id) < count) return false;
            counts.put(id, getItemCountByItemId(id) - count); removals++; return true;
        }
    }
    static final class Contact extends Npc {
        int id;
        Contact() { super(null, null, null); }
        @Override public int getNpcId() { return id; }
        @Override public com.aionemu.gameserver.ai.AbstractAI<Npc> getAi() {
            return new com.aionemu.gameserver.ai.AITemplate<Npc>(this) {};
        }
        @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate() {
            return new com.aionemu.gameserver.model.templates.npc.NpcTemplate() {
                @Override public int getTemplateId() { return id; }
            };
        }
    }
    interface Captured { int page(); }
    static final class Kerub extends _1001TheKerubThreat implements Captured {
        int page;
        public int page() { return page; }
        @Override public boolean sendQuestDialog(QuestEnv env, int page) { this.page = page; return true; }
        @Override public void updateQuestStatus(QuestEnv env) { }
    }
    static final class Logging extends _1003IllegalLogging implements Captured {
        int page;
        public int page() { return page; }
        @Override public boolean sendQuestDialog(QuestEnv env, int page) { this.page = page; return true; }
        @Override public void updateQuestStatus(QuestEnv env) { }
    }
    static final class Thinking extends _2001ThinkingAhead implements Captured {
        int page;
        public int page() { return page; }
        @Override public boolean sendQuestDialog(QuestEnv env, int page) { this.page = page; return true; }
        @Override public boolean closeDialogWindow(QuestEnv env) { return true; }
        @Override public void updateQuestStatus(QuestEnv env) { }
    }
    static Bot bot(int quest, QuestStatus status, int stage) throws Exception {
        Bot bot = allocate(Bot.class);
        bot.quests = new QuestStateList(); bot.cube = new Inventory(bot);
        if (status != null) {
            var state = new QuestState(quest, status);
            if (status == QuestStatus.COMPLETE) state.setCompleteCount(1);
            state.setQuestVarById(0, stage); state.setQuestVarById(1, 5);
            bot.quests.addQuest(quest, state);
        }
        return bot;
    }
    static QuestEnv env(Bot bot, int quest, int npc, int action) throws Exception {
        Contact target = allocate(Contact.class); target.id = npc;
        return new QuestEnv(target, bot, quest, action);
    }
    static void readonly(AbstractQuestHandler handler) throws Exception {
        int quest = handler.getQuestId();
        for (QuestStatus status : new QuestStatus[] {null, QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE}) {
            for (int stage = 0; stage <= 21; stage++) {
                Bot bot = bot(quest, status, stage); bot.cube.counts.put(182201007, 1L);
                int before = status == null ? 0 : bot.quests.getQuestState(quest).getQuestVars().getQuestVars();
                var items = Map.copyOf(bot.cube.counts);
                var actions = handler.getPlayerBotActions(bot);
                check(actions != null, "Native action list cannot be null");
                check(bot.cube.counts.equals(items) && bot.cube.removals == 0, "Discovery must preserve native inventory");
                check(status == null || bot.quests.getQuestState(quest).getStatus() == status
                    && bot.quests.getQuestState(quest).getQuestVars().getQuestVars() == before, "Discovery must preserve all variables/status");
                if (status == null && quest == 18602) check(actions.size() == 1 && actions.getFirst().dialogAction() == DialogAction.ASK_QUEST_ACCEPT, "Only native declared acceptance is discoverable");
                else if (status == null || status == QuestStatus.COMPLETE) check(actions.isEmpty(), "No fabricated acceptance/replay");
                if (status == QuestStatus.REWARD) check(actions.stream().allMatch(a -> a.kind() == QuestBotAction.Kind.REWARD), "Reward never repeats previous objective");
            }
        }
    }
    static void dialog(AbstractQuestHandler handler, Captured capture, int stage, int expectedNext) throws Exception {
        int quest = handler.getQuestId(); Bot bot = bot(quest, QuestStatus.START, stage);
        var action = handler.getPlayerBotActions(bot).getFirst();
        check(action.kind() == QuestBotAction.Kind.CONVERSATION, "Native conversation declared");
        var request = env(bot, quest, action.targetId(), DialogAction.QUEST_SELECT);
        handler.onDialogEvent(request);
        check(capture.page() == action.page(), "Actual handler offered the declared page");
        request.setDialogActionId(action.dialogAction()); handler.onDialogEvent(request);
        check(bot.quests.getQuestState(quest).getQuestVarById(0) == expectedNext, "Actual native action advanced exact objective");
        check(bot.quests.getQuestState(quest).getQuestVarById(1) == 5, "Other saved variables preserved");
    }
    static void hunts(AbstractQuestHandler handler, int stage, int expectedNext) throws Exception {
        int quest = handler.getQuestId(); var actions = handler.getPlayerBotActions(bot(quest, QuestStatus.START, stage));
        for (var action : actions) {
            check(action.kind() == QuestBotAction.Kind.HUNT, "Only current-stage native enemies");
            Bot bot = bot(quest, QuestStatus.START, stage);
            check(!handler.onKillEvent(env(bot, quest, 999999, 0)), "Unrelated kill cannot receive credit");
            check(handler.onKillEvent(env(bot, quest, action.targetId(), 0)), "Declared hunt receives actual native kill credit");
            check(bot.quests.getQuestState(quest).getQuestVarById(0) == expectedNext, "Kill advanced exact native counter");
        }
    }
    public static void main(String[] args) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = field.get(null);
        var previous = DataManager.QUEST_DATA;
        try {
            StringBuilder xml = new StringBuilder("<quests>");
            for (int id : new int[] {1001, 1002, 1003, 1004, 1005, 2001, 2003, 2004, 2005, 2006, 2007, 1038, 18602})
                xml.append("<quest id='").append(id).append("' name='Native fixture' category='QUEST'><collect_items><collect_item item_id='182200009' count='2'/></collect_items><rewards exp='10'/></quest>");
            DataManager.QUEST_DATA = (QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader(xml.append("</quests>").toString()));
            var kerub = new Kerub(); var logging = new Logging(); var thinking = new Thinking();
            for (var handler : List.of(kerub, logging, thinking, new _1002RequestoftheElim(), new _1004NeutralizingOdium(), new _1005BarringtheGate(), new _2003TreasureOfTheDeceased(), new _2004ACharmedCube(), new _2005TeachingaLesson(), new _2006HitThemWhereitHurts(), new _2007WheresRaeThisTime(), new _1038TheShadowsCommand(), new _18602NightmareinShiningArmor())) readonly(handler);
            dialog(kerub, kerub, 0, 1); dialog(kerub, kerub, 6, 7);
            dialog(logging, logging, 0, 1); dialog(logging, logging, 7, 8);
            dialog(thinking, thinking, 0, 1); dialog(thinking, thinking, 2, 3);
            hunts(kerub, 1, 2); hunts(logging, 1, 2); hunts(logging, 8, 9); hunts(thinking, 3, 4);
            Bot holder = bot(1038, QuestStatus.START, 2);
            var diary = new _1038TheShadowsCommand();
            check(diary.getPlayerBotActions(holder).getFirst().targetId() == 700172, "Missing diary selects real loot object");
            holder.cube.counts.put(182201007, 1L);
            check(diary.getPlayerBotActions(holder).getFirst().kind() == QuestBotAction.Kind.ITEM, "Already-owned diary uses native item continuation");
            var kerubCollect = kerub.getPlayerBotActions(bot(1001, QuestStatus.START, 7)).getFirst();
            check(kerubCollect.requiredItems().equals(Map.of(182200001, 3L)), "Exact owned collection requirement");
            check(kerubCollect.continuation().equals(List.of(new QuestBotAction.DialogStep(DialogAction.SETPRO3, 1694))), "Native checked-item page must precede final action");
            try { kerubCollect.requiredItems().put(1, 1L); throw new AssertionError("Mutable custody prerequisites"); } catch (UnsupportedOperationException expected) { checks++; }
            var nightmare = new _18602NightmareinShiningArmor();
            check(nightmare.getPlayerBotActions(bot(18602, QuestStatus.START, 3)).stream().map(QuestBotAction::targetId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(217005, 217006)), "Both verified native Kaliga variants preserved");
            check(nightmare.getPlayerBotActions(bot(18602, QuestStatus.START, 1)).getFirst().requiredItems().equals(Map.of(185000109, 1L)), "Native key custody is required");
            check(diary.getPlayerBotActions(bot(1038, QuestStatus.START, 4)).getFirst().requiredItems().equals(Map.of(182200009, 2L)), "Collection comes from actual native metadata");
            var engine = QuestEngine.getInstance();
            field = QuestEngine.class.getDeclaredField("questHandlers"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var registered = (Map<Integer, AbstractQuestHandler>) field.get(engine);
            var old = registered.put(1001, kerub);
            try {
                check(engine.getPlayerBotActions(bot(1001, QuestStatus.START, 1), 1001).getFirst().targetId() == 210670, "Actual registered handler supplies intent");
                check(engine.getPlayerBotActions(bot(999999, QuestStatus.START, 1), 999999).isEmpty(), "No handler means no authorized action");
                try { engine.getPlayerBotActions(bot(1001, QuestStatus.START, 1), 1001).clear(); throw new AssertionError("Mutable engine result"); } catch (UnsupportedOperationException expected) { checks++; }
            } finally { if (old == null) registered.remove(1001); else registered.put(1001, old); }
            System.out.println("OK: " + checks + " registered native quest action and transition checks; no World/SQL/IDs/transport");
        } finally { DataManager.QUEST_DATA = previous; }
    }
}
