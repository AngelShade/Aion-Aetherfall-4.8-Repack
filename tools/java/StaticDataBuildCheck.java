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
