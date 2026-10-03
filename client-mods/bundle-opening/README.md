# Bundle opening without a reuse cooldown

Server code treats every `DecomposeAction` item as a bundle: no reuse cooldown is checked or started, and `CM_USE_ITEM` rejects a bundle request before aborting the existing item-use observer when `TaskId.ITEM_USE` is still active. The normal opening animation/casting time remains.

The Aion 4.8 client also reads `use_delay` and `use_delay_type_id` from its own item tables. A zero server cooldown packet does not remove that local restriction reliably. This patch removes both fields from the entire client Bundle Item type (`disassembly_item=1`), verifying exact agreement with the server's decomposable item IDs. It covers all 4,125 bundles in the current client, including bags, boxes, pouches and selectable chests.

Preparation preserves every unrelated item field, each encrypted texture/archive entry, and all installed DLLs, UI archives, addon packages and signing keys. Only `Data/Items/Items.pak` and its matching `bin64/AionIconBridge.index` are installed. The icon index is rebased against the new archive with identical texture bytes, mapping, sprite sizes and checksums.

Run from the repository root in PowerShell:

```powershell
python client-mods/bundle-opening/prepare_client.py --client '<client directory>' --items target-deploy/game-server/data/static_data/items/item_templates.xml --output output/bundle-opening/client-v1
python client-mods/bundle-opening/prepare_client.py --client '<client directory>' --output output/bundle-opening/client-v1 --verify-only
# Close every Aion instance normally before installation.
python client-mods/bundle-opening/install_client.py --client '<client directory>' --prepared output/bundle-opening/client-v1
python client-mods/bundle-opening/install_client.py --client '<client directory>' --prepared output/bundle-opening/client-v1 --verify-installed
```

Preparation and installation report explicit `OK` or `FAIL`. Hash guards reject client changes made after preparation. Installation backs up both replaced files under `BundleOpening-backups` and rolls back on failure. Keep that backup when applying later client patches.

In-game acceptance requires opening consecutive bundles, confirming the reuse countdown and tooltip reuse line are gone, spamming a bundle during its opening to verify that the active animation is not restarted, and checking native item icons in Market, Shop and Season Pass.
