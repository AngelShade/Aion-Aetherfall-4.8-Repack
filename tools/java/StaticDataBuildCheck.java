package com.aionemu.gameserver.dataholders;

import java.nio.file.Path;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;

import com.aionemu.gameserver.configs.main.GSConfig;
import com.aionemu.gameserver.configs.main.ThreadConfig;
import com.aionemu.gameserver.dataholders.loadingutils.XmlMerger;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.xml.JAXBUtil;
import com.aionemu.gameserver.utils.xml.XmlUtil;

/** Complete isolated static XML merge/schema/JAXB check. No GameServer, database or world actors. */
public final class StaticDataBuildCheck {
    public static void main(String[] args) {
        int status = 1;
        try {
            ThreadConfig.BASE_THREAD_POOL_SIZE = 4;
            ThreadConfig.SCHEDULED_THREAD_POOL_SIZE = 4;
            ThreadConfig.MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING = 60_000;
            GSConfig.SERVER_COUNTRY_CODE = Integer.parseInt(args[2]);
            Path folder = Path.of(args[0]), cache = Path.of(args[1]);
            var merged = new XmlMerger(folder.resolve("static_data.xml").toFile(), cache.toFile()).merge();
            if (!merged.waitUntilFileIsWritten()) throw new IllegalStateException("Static XML cache write failed");
            try (var reader = merged.newReader()) {
                XmlUtil.getSchema(folder.resolve("static_data.xsd").toString()).newValidator()
                    .validate(new SAXSource(new InputSource(reader)));
            }
            StaticData data;
            try (var reader = merged.newReader()) { data = JAXBUtil.deserialize(reader, StaticData.class); }
            data.waitForAfterUnmarshalTasksToFinish();
            var exclusions = data.globalExclusionData;
            var silikor = data.npcData.getNpcTemplate(214668);
            if (silikor.getTribe() != com.aionemu.gameserver.model.TribeClass.HOLYSERVANT
                || exclusions.isTribeExcluded(214668, silikor.getTribe()))
                throw new AssertionError("Silikor's native tribe must retain encounter relations without suppressing loot");
            for (var npc : data.npcData.getNpcData()) {
                boolean expected = exclusions.getNpcTribes().contains(npc.getTribe()) && npc.getTemplateId() != 214668;
                if (exclusions.isTribeExcluded(npc.getTemplateId(), npc.getTribe()) != expected)
                    throw new AssertionError("Unrelated tribe loot exclusion changed: " + npc.getTemplateId());
            }
            boolean key = data.globalDropData.getAllRules().stream().anyMatch(rule ->
                rule.getGlobalRuleNpcs() != null
                && rule.getGlobalRuleNpcs().getGlobalDropNpcs().stream().anyMatch(npc -> npc.getNpcId() == 214668)
                && rule.getGlobalRuleMaps() != null
                && rule.getGlobalRuleMaps().getGlobalDropMaps().stream().anyMatch(map -> map.getMapId() == 310110000)
                && rule.getChance() == 100 && !rule.isUseLevelBasedChanceReduction()
                && rule.getDropItems().stream().anyMatch(item -> item.getId() == 185000024));
            if (!key) throw new AssertionError("Silikor's original guaranteed key rule is missing");
            for (int id : new int[]{214669, 214696}) {
                var boss = data.npcData.getNpcTemplate(id);
                if (boss.getRating() != com.aionemu.gameserver.model.templates.npc.NpcRating.HERO || boss.getHpGauge() != 21)
                    throw new AssertionError("Boss classification and target frame disagree: " + id);
            }
            System.out.println("OK: native Silikor loot exception, all other tribe exclusions, guaranteed key and paired boss classifications");
            com.aionemu.gameserver.services.playerbot.PopulationCatalogBuildCheck.check(data);
            com.aionemu.gameserver.services.playerbot.PlayerBotQuestCoverageBuildCheck.check(data);
            com.aionemu.gameserver.services.playerbot.PlayerBotMaterialCatalogBuildCheck.check(data);
            System.out.println("OK: complete static XML merge, schema and production JAXB load; items=" + data.itemData.size()
                + "; decomposable=" + data.decomposableItemsData.size() + "; overrides=" + data.decomposableItemsData.overrideCount()
                + "; no GameServer startup/database/world actors");
            status = 0;
        } catch (Throwable failure) {
            failure.printStackTrace();
        } finally {
            ThreadPoolManager.getInstance().shutdown();
        }
        System.exit(status);
    }
}
