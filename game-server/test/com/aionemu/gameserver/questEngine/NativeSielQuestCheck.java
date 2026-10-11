package com.aionemu.gameserver.questEngine;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** All 24 actual native upgrade handlers, owned-token recovery and the 30348 prerequisite regression. */
public final class NativeSielQuestCheck {
    static int checks;
    static Object unsafe;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type)); }
    static final class Controller extends PlayerController { @Override public void updateNearbyQuests() { } }
    static final class Bot extends Player {
        QuestStateList quests; Cube cube; Controller controller; int world;
        com.aionemu.gameserver.model.skill.PlayerSkillList skills;
        RecipeList recipes;
        com.aionemu.gameserver.model.Race race;
        com.aionemu.gameserver.world.knownlist.KnownList known;
        Bot() { super(null, null); }
        @Override public QuestStateList getQuestStateList() { return quests; }
        @Override public PlayerStorage getInventory() { return cube; }
        @Override public PlayerController getController() { return controller; }
        @Override public boolean isOnline() { return false; }
        @Override public boolean isPlayerBot() { return true; }
        @Override public int getObjectId() { return 1999982211; }
        @Override public int getWorldId() { return world; }
        @Override public com.aionemu.gameserver.model.skill.PlayerSkillList getSkillList() { return skills; }
        @Override public RecipeList getRecipeList() { return recipes; }
        @Override public com.aionemu.gameserver.model.Race getRace() { return race; }
        @Override public com.aionemu.gameserver.world.knownlist.KnownList getKnownList() { return known; }
    }
    static final class Cube extends PlayerStorage {
        final Map<Integer, Long> counts = new HashMap<>();
        int removals;
        Cube(Player player) { super(player, StorageType.CUBE); }
        @Override public long getItemCountByItemId(int id) { return counts.getOrDefault(id, 0L); }
        @Override public long getKinah() { return getItemCountByItemId(com.aionemu.gameserver.model.items.ItemId.KINAH); }
        @Override public boolean tryDecreaseKinah(long amount) {
            return decreaseByItemId(com.aionemu.gameserver.model.items.ItemId.KINAH, amount);
        }
        @Override public void decreaseKinah(long amount) { tryDecreaseKinah(amount); }
        @Override public boolean decreaseByItemId(int id, long count, QuestStatus status) {
            if (getItemCountByItemId(id) < count) return false;
            counts.put(id, getItemCountByItemId(id) - count); removals++; return true;
        }
        @Override public boolean decreaseByItemId(int id, long count) { return decreaseByItemId(id, count, null); }
    }
    static final class Contact extends Npc {
        int id;
        Contact() { super(null, null, null); }
        @Override public int getObjectId() { return 1999982212; }
        @Override public int getNpcId() { return id; }
        @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate() {
            return new com.aionemu.gameserver.model.templates.npc.NpcTemplate() { @Override public int getTemplateId() { return id; } };
        }
    }
    static final String[] TYPES = {
        "_30235NewSword", "_30236NewGreatsword", "_30237NewPolearm", "_30238NewStaff", "_30239NewSpellbook", "_30240NewOrb",
        "_30241NewBow", "_30242NewDagger", "_30243NewMace", "_30247NewPistol", "_30248NewAethercannon", "_30249NewHarp",
        "_30335ImprovedSword", "_30336ImprovedGreatsword", "_30337ImprovedPolearm", "_30338ImprovedStaff", "_30339ImprovedSpellbook", "_30340ImprovedOrb",
        "_30341ImprovedBow", "_30342ImprovedDagger", "_30343ImprovedMace", "_30347ImprovedPistol", "_30348ImprovedAethercannon", "_30349ImprovedHarp"
    };
    static Bot bot(int quest, QuestStatus status, int stage) throws Exception {
        Bot bot = allocate(Bot.class); bot.quests = new QuestStateList(); bot.cube = new Cube(bot); bot.controller = allocate(Controller.class);
        bot.skills = new com.aionemu.gameserver.model.skill.PlayerSkillList();
        bot.recipes = new RecipeList();
        bot.race = com.aionemu.gameserver.model.Race.ELYOS;
        bot.known = new com.aionemu.gameserver.world.knownlist.KnownList(bot);
        if (status != null) {
            var state = new QuestState(quest, status); state.setQuestVarById(0, stage); state.setQuestVarById(1, 5);
            if (status == QuestStatus.COMPLETE) state.setCompleteCount(1);
            bot.quests.addQuest(quest, state);
        }
        return bot;
    }
    static Integer offered(AbstractQuestHandler handler, Bot bot, int npc, int action) throws Exception {
        Contact contact = allocate(Contact.class); contact.id = npc;
        var capture = Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestDialog");
        var open = capture.getDeclaredMethod("open", int.class, int.class, int.class); open.setAccessible(true);
        var page = capture.getDeclaredField("page"); page.setAccessible(true);
        try (var window = (AutoCloseable) open.invoke(null, bot.getObjectId(), contact.getObjectId(), handler.getQuestId())) {
            handler.onDialogEvent(new QuestEnv(contact, bot, handler.getQuestId(), action));
            return (Integer) page.get(window);
        }
    }
    static void prerequisiteRegression() throws Exception {
        var handler = (AbstractQuestHandler) Class.forName("quest.beshmundir._30348ImprovedAethercannon").getConstructor().newInstance();
        Bot bot = bot(30348, null, 0); bot.cube.counts.put(100100716, 1L);
        check(offered(handler, bot, 799336, DialogAction.QUEST_SELECT) == null, "A Mace cannot authorize the Aethercannon quest");
        bot.cube.counts.clear(); bot.cube.counts.put(101900655, 1L);
        check(Objects.equals(offered(handler, bot, 799336, DialogAction.QUEST_SELECT), 4762), "The actual native Aethercannon authorizes its quest");
    }
    static void rewardCustody(AbstractQuestHandler handler, int token, int npc) throws Exception {
        rewardCustody(handler, token, npc, true);
    }
    static void rewardCustody(AbstractQuestHandler handler, int token, int npc, boolean checkMissing) throws Exception {
        int quest = handler.getQuestId(); Bot bot = bot(quest, QuestStatus.REWARD, 0);
        if (checkMissing) check(offered(handler, bot, npc, DialogAction.SELECT_QUEST_REWARD) == null, "Missing owned token cannot authorize native reward opening");
        bot.cube.counts.put(token, 2L); var before = Map.copyOf(bot.cube.counts);
        check(Objects.equals(offered(handler, bot, npc, DialogAction.USE_OBJECT), 10002), "Native token conversation remains available");
        check(Objects.equals(offered(handler, bot, npc, DialogAction.SELECT_QUEST_REWARD),
            com.aionemu.gameserver.model.DialogPage.getRewardPageByIndex(bot.quests.getQuestState(quest).getRewardGroup()).id()), "Native reward selection page is actually emitted");
        check(bot.cube.counts.equals(before) && bot.cube.removals == 0, "Opening native reward dialogs must not consume the owned work token");
        check(Objects.equals(offered(handler, bot, npc, DialogAction.SELECT_QUEST_REWARD), 5)
            && bot.cube.counts.equals(before), "Reopening the offered reward page preserves custody");
        com.aionemu.gameserver.services.QuestService.removeQuestWorkItems(bot, bot.quests.getQuestState(quest));
        check(bot.cube.getItemCountByItemId(token) == 1 && bot.cube.removals == 1, "Original successful-finish cleanup removes exactly its required token");
    }
    public static void main(String[] args) throws Exception {
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = field.get(null);
        var old = DataManager.QUEST_DATA;
        try {
            DataManager.QUEST_DATA = (QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0], "data/static_data/quest_data/quest_data.xml").toFile());
            if (args.length > 1 && (args[1].equals("--token-only") || args[1].equals("--owned-token-only"))) {
                var handler = (AbstractQuestHandler) Class.forName("quest.beshmundir._30235NewSword").getConstructor().newInstance();
                rewardCustody(handler, 182209633, 799032, args[1].equals("--token-only")); return;
            }
            prerequisiteRegression();
            if (args.length > 1 && args[1].equals("--prerequisite-only")) {
                System.out.println("OK: " + checks + " native Aethercannon prerequisite checks");
                return;
            }
            for (String type : TYPES) {
                var handler = (AbstractQuestHandler) Class.forName("quest.beshmundir." + type).getConstructor().newInstance();
                int quest = handler.getQuestId(); var template = DataManager.QUEST_DATA.getQuestById(quest);
                int weapon = template.getCollectItems().getCollectItem().getFirst().getItemId();
                int token = template.getQuestWorkItems().getQuestWorkItem().getFirst().getItemId();
                Bot bot = bot(quest, null, 0); var acceptance = handler.getPlayerBotActions(bot).getFirst();
                int npc = acceptance.targetId();
                rewardCustody(handler, token, npc);
                check(acceptance.requiredItems().equals(Map.of(weapon, 1L)), "Actual native weapon prerequisite for " + quest);
                check(offered(handler, bot, npc, DialogAction.QUEST_SELECT) == null, "Missing owned weapon cannot offer acceptance");
                bot.cube.counts.put(weapon, 1L);
                check(Objects.equals(offered(handler, bot, npc, DialogAction.QUEST_SELECT), acceptance.page()), "Real handler offered declared native page");
                check(Objects.equals(offered(handler, bot, npc, acceptance.dialogAction()), 4), "Actual native acceptance window precedes acceptance");
                check(bot.quests.getQuestState(quest) == null && bot.cube.getItemCountByItemId(weapon) == 1, "Discovery does not grant acceptance or consume a weapon");
                for (QuestStatus status : new QuestStatus[] {QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE}) {
                    for (int stage : new int[] {0, 1, 5}) {
                        Bot actor = bot(quest, status, stage); var state = actor.quests.getQuestState(quest);
                        int variables = state.getQuestVars().getQuestVars(); var actions = handler.getPlayerBotActions(actor);
                        check(state.getStatus() == status && state.getQuestVars().getQuestVars() == variables && actor.cube.counts.isEmpty(), "Native stage discovery remains read-only");
                        if (status == QuestStatus.COMPLETE) check(actions.isEmpty(), "Completed native quest cannot replay");
                        else if (status == QuestStatus.REWARD) check(actions.getFirst().kind() == QuestBotAction.Kind.REWARD && actions.getFirst().targetId() == npc, "Reward returns to actual native NPC");
                        else if (stage == 0) {
                            check(actions.stream().map(QuestBotAction::targetId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(286904, 281419, 215795)), "Only actual Debilkarim variants");
                            var required = new HashMap<Integer, Long>(); for (var item : template.getCollectItems().getCollectItem()) required.put(item.getItemId(), item.getCount().longValue());
                            check(actions.stream().allMatch(a -> a.requiredItems().equals(required)), "Native boss objective retains every actual collection requirement");
                        } else check(actions.getFirst().kind() == QuestBotAction.Kind.EVENT && actions.getFirst().targetId() == 0, "Unknown saved stage does not invent a kill transition");
                    }
                }
                Bot held = bot(quest, QuestStatus.START, 0); held.cube.counts.put(token + 1000, 1L);
                check(!handler.onEnterWorldEvent(new QuestEnv(null, held, quest)), "An unrelated owned item cannot recover the native token event");
                held.cube.counts.put(token, 1L); var before = Map.copyOf(held.cube.counts);
                check(handler.onEnterWorldEvent(new QuestEnv(null, held, quest)), "The actual saved owned token invokes its original native callback");
                check(held.quests.getQuestState(quest).getStatus() == QuestStatus.REWARD, "Only original native acquisition callback reaches reward state");
                check(held.cube.counts.equals(before), "Native recovery preserves owned token and all inventory");
                check(!handler.onEnterWorldEvent(new QuestEnv(null, held, quest)), "Native recovery cannot replay after reward state");
                Bot wrongStage = bot(quest, QuestStatus.START, 1); wrongStage.cube.counts.put(token, 1L);
                check(!handler.onEnterWorldEvent(new QuestEnv(null, wrongStage, quest)), "Owned token cannot bypass a different saved stage");
            }
            System.out.println("OK: " + checks + " real Siel handler, native prerequisite and owned-token recovery checks; no World/SQL/IDs/grants");
        } finally { DataManager.QUEST_DATA = old; }
    }
}
