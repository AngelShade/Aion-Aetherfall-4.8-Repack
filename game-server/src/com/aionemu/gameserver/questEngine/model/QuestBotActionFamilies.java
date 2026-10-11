package com.aionemu.gameserver.questEngine.model;

import java.util.List;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Explicit, semantically reviewed native families. AST shape alone never selects a family. */
public final class QuestBotActionFamilies {
    private QuestBotActionFamilies() { }

    /** Ascension dispatch: enrollment stays with native campaign prerequisite propagation. */
    public static List<QuestBotAction> dispatch(Player player, int quest, int sender, int recipient) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable()) return List.of();
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(recipient));
        if (state.getStatus() != QuestStatus.START) return List.of();
        return switch (state.getQuestVarById(0)) {
            case 0 -> List.of(QuestBotAction.dialog(sender, DialogAction.SETPRO1, 1352));
            case 1 -> List.of(QuestBotAction.scripted(recipient, DialogAction.QUEST_SELECT,
                "Native dispatch delivery opens its reward dialog"));
            default -> List.of(QuestBotAction.event("Saved dispatch stage has no declared native transition"));
        };
    }

    /** Daevanion field series: the native kill callback owns its ten-kill counter and reward. */
    public static List<QuestBotAction> daevanionField(Player player, int quest, int sender, int recipient, int... enemies) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(sender, DialogAction.ASK_QUEST_ACCEPT, 4762)
                .then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(recipient));
        if (state.getStatus() != QuestStatus.START) return List.of();
        if (state.getQuestVarById(0) == 0 && state.getQuestVarById(1) >= 0 && state.getQuestVarById(1) <= 9)
            return QuestBotAction.hunts(enemies);
        return List.of(QuestBotAction.event("Saved Daevanion stage/counter has no declared native kill transition"));
    }

    /** Reviewed defaultOnKillEvent family; lastCounter is the native final pre-reward value. */
    public static List<QuestBotAction> countedHunt(Player player, int quest, int npc, int lastCounter, int... enemies) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(npc, DialogAction.ASK_QUEST_ACCEPT, 1011)
                .then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(npc));
        if (state.getStatus() != QuestStatus.START) return List.of();
        if (state.getQuestVarById(0) >= 0 && state.getQuestVarById(0) <= lastCounter)
            return QuestBotAction.hunts(enemies);
        return List.of(QuestBotAction.event("Saved hunt counter has no declared native transition"));
    }

    /** Reviewed one/two-report circuits; native dialogs own every report and the return transition. */
    public static List<QuestBotAction> reportCircuit(Player player, int quest, int sender, int secondDialogAction, int... reports) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(sender, DialogAction.ASK_QUEST_ACCEPT, 1011)
                .then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(sender));
        if (state.getStatus() != QuestStatus.START) return List.of();
        int stage = state.getQuestVarById(0);
        if (stage >= 0 && stage < reports.length)
            return List.of(QuestBotAction.dialog(reports[stage], stage == 0 ? DialogAction.SETPRO1 : secondDialogAction,
                stage == 0 ? 1352 : 1693));
        if (stage == reports.length) return List.of(QuestBotAction.dialog(sender, DialogAction.SELECT_QUEST_REWARD, 2375));
        return List.of(QuestBotAction.event("Saved report circuit stage has no declared native transition"));
    }

    /** Expert qualification: actual crafted item, native profession limit and original turn-in. */
    public static List<QuestBotAction> expertQualification(Player player, int quest, int npc, int... products) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state != null && state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(npc));
        if (state != null && !state.isStartable() && state.getStatus() != QuestStatus.START) return List.of();
        var service = com.aionemu.gameserver.services.craft.CraftSkillUpdateService.getInstance();
        if (service.getTotalExpertCraftingSkills(player) + service.getTotalMasterCraftingSkills(player)
            >= com.aionemu.gameserver.configs.main.CraftConfig.MAX_EXPERT_CRAFTING_SKILLS)
            return List.of(QuestBotAction.event("Native expert profession limit prevents this qualification dialog"));
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(npc, DialogAction.ASK_QUEST_ACCEPT, 1011).then(DialogAction.QUEST_ACCEPT, 4));
        if (products.length > 0 && products[0] > 0) {
            var action = QuestBotAction.scripted(npc, DialogAction.QUEST_SELECT,
                "Native qualification verifies and consumes its actual crafted products");
            for (int product : products) action = action.requiring(product, 1);
            return List.of(action);
        }
        return List.of(QuestBotAction.dialog(npc, DialogAction.CHECK_USER_HAS_QUEST_ITEM, 2375)
            .requiringCollection(DataManager.QUEST_DATA.getQuestById(quest).getCollectItems()));
    }

    public static List<QuestBotAction> tappingTest(Player player, int quest, int sender, int equipmentNpc, int equipment,
                                                   boolean explicitCollectionDialog) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(sender, DialogAction.ASK_QUEST_ACCEPT, 4762).then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(sender));
        if (state.getStatus() != QuestStatus.START) return List.of();
        if (state.getQuestVarById(0) == 0)
            return List.of(player.getInventory().getItemCountByItemId(equipment) > 0
                ? QuestBotAction.event("Owned native tapping equipment awaits its entry/acquisition callback")
                : QuestBotAction.dialog(equipmentNpc, DialogAction.SETPRO1, 1011));
        if (state.getQuestVarById(0) != 1)
            return List.of(QuestBotAction.event("Saved tapping test stage has no declared native transition"));
        var collection = DataManager.QUEST_DATA.getQuestById(quest).getCollectItems();
        return List.of((explicitCollectionDialog
            ? QuestBotAction.dialog(sender, DialogAction.CHECK_USER_HAS_QUEST_ITEM, 1352)
            : QuestBotAction.scripted(sender, DialogAction.QUEST_SELECT, "Original tapping test verifies its owned collection"))
            .requiringCollection(collection));
    }

    public static List<QuestBotAction> tappingQualification(Player player, int quest, int sender, int recipient, int token) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(sender, DialogAction.ASK_QUEST_ACCEPT, 1011).then(DialogAction.QUEST_ACCEPT_1, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(recipient));
        if (state.getStatus() != QuestStatus.START) return List.of();
        return List.of(QuestBotAction.scripted(recipient, DialogAction.QUEST_SELECT,
            "Original tapping qualification recipient opens its earned reward dialog").requiring(token, 1));
    }

    /** Native design item and its independently reviewed craftlearn recipe ID. */
    public record Design(int item, int recipe) { }

    private static List<QuestBotAction> masterAdmission(Player player, int quest, int sender) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state != null && state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(sender));
        if (state != null && !state.isStartable() && state.getStatus() != QuestStatus.START) return List.of();
        if (com.aionemu.gameserver.services.craft.CraftSkillUpdateService.getInstance().getTotalMasterCraftingSkills(player)
            >= com.aionemu.gameserver.configs.main.CraftConfig.MAX_MASTER_CRAFTING_SKILLS)
            return List.of(QuestBotAction.event("Native master profession limit prevents this qualification dialog"));
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(sender, DialogAction.ASK_QUEST_ACCEPT, 4762).then(DialogAction.QUEST_ACCEPT, 4));
        return null; // Current native START stage needs its family-specific objective.
    }

    private static boolean learned(Player player, Design design) {
        return player.getRecipeList().isRecipePresent(design.recipe());
    }

    private static QuestBotAction ownedDesign(Player player, Design... designs) {
        for (var design : designs)
            if (!learned(player, design) && player.getInventory().getItemCountByItemId(design.item()) > 0)
                return QuestBotAction.item(design.item());
        return null;
    }

    /** Five reviewed master professions: native design alternatives and actual crafted-product turn-in. */
    public static List<QuestBotAction> masterPotential(Player player, int quest, int sender, int designNpc,
                                                       int product, Design... designs) {
        var admission = masterAdmission(player, quest, sender);
        if (admission != null) return admission;
        int stage = player.getQuestStateList().getQuestState(quest).getQuestVarById(0);
        if (stage != 0 && stage != 1)
            return List.of(QuestBotAction.event("Saved master design stage has no declared native transition"));
        var turnIn = QuestBotAction.scripted(sender, DialogAction.QUEST_SELECT,
            "Native master verifies and consumes its actual crafted product").requiring(product, 1);
        if (player.getInventory().getItemCountByItemId(product) > 0) return List.of(turnIn);
        for (var design : designs) if (learned(player, design)) return List.of(turnIn);
        var owned = ownedDesign(player, designs);
        if (owned != null) return List.of(owned);
        return List.of(QuestBotAction.dialog(designNpc, DialogAction.SETPRO10, 1011),
            QuestBotAction.dialog(designNpc, DialogAction.SETPRO20, 1011));
    }

    /** Construction has paid alternatives and a native retry stage after an absent design/recipe. */
    public static List<QuestBotAction> masterConstruction(Player player, int quest, int sender, int designNpc, Design... designs) {
        var admission = masterAdmission(player, quest, sender);
        if (admission != null) return admission;
        int stage = player.getQuestStateList().getQuestState(quest).getQuestVarById(0);
        var turnIn = QuestBotAction.dialog(sender, DialogAction.CHECK_USER_HAS_QUEST_ITEM, 1352)
            .requiringCollection(DataManager.QUEST_DATA.getQuestById(quest).getCollectItems());
        if (stage < 0 || stage > 2)
            return List.of(QuestBotAction.event("Saved construction master stage has no declared native transition"));
        if (turnIn.requiredItems().entrySet().stream().allMatch(e -> player.getInventory().getItemCountByItemId(e.getKey()) >= e.getValue()))
            return List.of(turnIn);
        for (var design : designs) if (learned(player, design)) return List.of(turnIn);
        var owned = ownedDesign(player, designs);
        if (owned != null) return List.of(owned);
        if (stage == 1) return List.of(QuestBotAction.scripted(sender, DialogAction.CHECK_USER_HAS_QUEST_ITEM,
            "Original construction handler restores its missing-design retry stage"));
        int page = stage == 0 ? 1011 : 4080;
        int kinah = com.aionemu.gameserver.model.items.ItemId.KINAH;
        return List.of(QuestBotAction.dialog(designNpc, DialogAction.SETPRO10, page).requiring(kinah, 167500),
            QuestBotAction.dialog(designNpc, DialogAction.SETPRO20, page).requiring(kinah, 223000));
    }

    /** Four native cooking products, including original failed-craft retry stages. */
    public static List<QuestBotAction> masterCooking(Player player, int quest, int sender, int designNpc,
                                                    int[] products, Design... designs) {
        var admission = masterAdmission(player, quest, sender);
        if (admission != null) return admission;
        int stage = player.getQuestStateList().getQuestState(quest).getQuestVarById(0);
        if (stage < 0 || stage > 11)
            return List.of(QuestBotAction.event("Saved master cooking stage has no declared native transition"));
        int step = stage / 3;
        var turnIn = QuestBotAction.dialog(sender, new int[]{DialogAction.SETPRO11, DialogAction.SETPRO21,
            DialogAction.SETPRO31, DialogAction.SETPRO41}[step], new int[]{1097, 1438, 1779, 2120}[step])
            .requiring(products[step], 1);
        if (stage % 3 == 2) {
            if (player.getInventory().getItemCountByItemId(products[step]) > 0 || learned(player, designs[step])) return List.of(turnIn);
            var owned = ownedDesign(player, designs[step]);
            if (owned != null) return List.of(owned);
            return List.of(QuestBotAction.event(
                "Native failed-craft callback or owned design is required before another recipe purchase"));
        }
        if (learned(player, designs[step]))
            return List.of(QuestBotAction.event("Saved cooking design stage awaits its native failed-craft continuation"));
        var owned = ownedDesign(player, designs[step]);
        if (owned != null) return List.of(owned);
        int page = stage % 3 == 0 ? new int[]{1011,1352,1693,2034}[step] : new int[]{4081,4166,4251,4336}[step];
        return List.of(QuestBotAction.dialog(designNpc, new int[]{DialogAction.SETPRO10, DialogAction.SETPRO20,
            DialogAction.SETPRO30, DialogAction.SETPRO40}[step], page).requiring(com.aionemu.gameserver.model.items.ItemId.KINAH, 6500));
    }

    /** Siel weapon upgrade: native acceptance, collection/boss kill, work-token event, reward. */
    public static List<QuestBotAction> weaponUpgrade(Player player, int quest, int npc, int weapon, int token, int... bosses) {
        var state = player.getQuestStateList().getQuestState(quest);
        if (state == null || state.isStartable())
            return List.of(QuestBotAction.dialog(npc, DialogAction.ASK_QUEST_ACCEPT, 4762)
                .requiring(weapon, 1).then(DialogAction.QUEST_ACCEPT, 4));
        if (state.getStatus() == QuestStatus.REWARD) return List.of(QuestBotAction.reward(npc).requiring(token, 1));
        if (state.getStatus() != QuestStatus.START) return List.of();
        if (state.getQuestVarById(0) != 0)
            return List.of(QuestBotAction.event("Siel upgrade requires native stage 0; saved stage has no declared transition"));
        if (ownsUpgradeToken(player, quest, token))
            return List.of(QuestBotAction.event("Owned Siel work token awaits the native acquisition/entry callback"));
        var collection = DataManager.QUEST_DATA.getQuestById(quest).getCollectItems();
        return QuestBotAction.hunts(bosses).stream().map(action -> action.requiringCollection(collection)).toList();
    }

    /** A saved, actually owned work token can resume its original native acquisition handler. */
    public static boolean ownsUpgradeToken(Player player, int quest, int token) {
        var state = player.getQuestStateList().getQuestState(quest);
        return state != null && state.getStatus() == QuestStatus.START && state.getQuestVarById(0) == 0
            && player.getInventory().getItemCountByItemId(token) > 0;
    }
}
