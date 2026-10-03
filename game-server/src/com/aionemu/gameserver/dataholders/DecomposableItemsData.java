package com.aionemu.gameserver.dataholders;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;

import com.aionemu.gameserver.model.templates.item.DecomposableItemInfo;
import com.aionemu.gameserver.utils.xml.JAXBUtil;
import com.aionemu.gameserver.utils.xml.XmlUtil;

/**
 * @author antness
 */
@XmlRootElement(name = "decomposable_items")
@XmlAccessorType(XmlAccessType.FIELD)
public class DecomposableItemsData {

	@XmlElement(name = "decomposable")
	private List<DecomposableItemInfo> decomposableItemsTemplates;

	@XmlTransient
	private final Map<Integer, DecomposableItemInfo> decomposableItemsInfo = new HashMap<>();
	@XmlTransient
	private int overrideCount;

	/** Reload all definitions together so duplicate checks and override precedence match startup. */
	public static DecomposableItemsData load(Collection<File> files, String schemaFile) {
		var merged = XmlUtil.getDocument("<decomposable_items/>");
		for (File file : files) {
			try {
				var document = XmlUtil.getDocument(Files.readString(file.toPath()));
				if (!document.getDocumentElement().getTagName().equals("decomposable_items"))
					throw new IllegalArgumentException("Unexpected decomposable XML root: " + file);
				var nodes = document.getDocumentElement().getChildNodes();
				for (int i = 0; i < nodes.getLength(); i++) {
					if (nodes.item(i) instanceof org.w3c.dom.Element)
						merged.getDocumentElement().appendChild(merged.importNode(nodes.item(i), true));
				}
			} catch (java.io.IOException e) {
				throw new IllegalStateException("Failed to read decomposable items: " + file, e);
			}
		}
		return JAXBUtil.deserialize(merged, DecomposableItemsData.class, schemaFile);
	}

	void afterUnmarshal(Unmarshaller u, Object parent) {
		decomposableItemsInfo.clear();
		Map<Integer, DecomposableItemInfo> overrides = new HashMap<>();
		for (DecomposableItemInfo template : decomposableItemsTemplates) {
			Map<Integer, DecomposableItemInfo> target = template.isOverride() ? overrides : decomposableItemsInfo;
			if (target.putIfAbsent(template.getItemId(), template) != null)
				throw new IllegalArgumentException("Duplicate decomposable item " + template.getItemId());
		}
		// the files of the folder are merged in no particular order, so the custom entries replace the retail ones only after all are read
		decomposableItemsInfo.putAll(overrides);
		decomposableItemsInfo.values().removeIf(info -> info.getSets().isEmpty());
		overrideCount = overrides.size();
		decomposableItemsTemplates = null;
	}

	public int size() {
		return decomposableItemsInfo.size();
	}

	public int overrideCount() {
		return overrideCount;
	}

	public DecomposableItemInfo getInfoByItemId(int itemId) {
		return decomposableItemsInfo.get(itemId);
	}
}
