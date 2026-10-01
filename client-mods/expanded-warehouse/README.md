# Expanded Character and Account Warehouses (Aion 4.8 NA)

The matching GameServer setting `gameserver.warehouse.expanded = true` gives every character **360 Character Warehouse slots** and every account **540 Account Warehouse slots**. Previously earned Character Warehouse expansion credits remain saved but do not add slots while this setting is enabled. The native expansion button is hidden so a player is not charged for capacity already available.

The client patch keeps the existing native warehouse item lists, tooltips, transfers, sorting, Kinah display, and Legion Warehouse. It widens the dialog to 12 columns and gives the Character and Account lists scrollable rows. Each list has its own Search and Clear controls. Search dims nonmatching items in their original slots; it does not move items or rewrite their positions. Only `warehouse_dialog.xml` changes inside each UI archive. The version-checked DLL patch enlarges the native list initialization bounds and active slot counts and routes warehouse search drawing through the existing Inventory search hook.

## Build and install

The existing `client-mods/transmog-menu/build_package.py` builder includes this patch when `menus.json` has the `warehouse` entry. Use that builder and `Install.ps1` to stage and install the combined client package. Both scripts verify the client files; the installer backs up replaced files under `TransmogMenu-backups`. Fully close Aion first. Deploy the matching newly built GameServer JAR and set `gameserver.warehouse.expanded = true` in the **running server** `config/main/custom.properties` during a normal maintenance stop, then start GameServer and Aion.

The builder supports the exact original Aion 4.8 NA DLL, the Inventory-only DLL, and the verified local Inventory/Market DLL. Other binaries are refused. Both UI archives are rebuilt and checked, and unrelated archive entries must stay byte-identical. A recipient needs this client patch and server setting together to use slots above the old limits.

## In-game check

Open the standard Warehouse with a Character and Account Warehouse. Check that both lists have 12 columns. Search for an item in each list, check that nonmatches dim, then use Clear. Scroll to the final row, move an item into a high slot in each list, close/reopen the window, relog, and verify the positions persist. Test a second character on the same account to confirm the Account Warehouse items and capacity. Check Legion Warehouse, Inventory, sorting, Kinah transfers, and the Central Market Warehouse tabs as regression checks.

Before disabling the setting or restoring an older server/client build, move items out of all slots that exceed the old limits. Keep a consistent database backup containing `inventory`, `item_stones`, and the `central_market_*` tables when market activity exists.

Validation so far: the changed server classes compile and a full Maven package builds; the combined client builder produces a staged package and changes only the two `warehouse_dialog.xml` archive entries. Native runtime behavior and high-slot persistence require the in-game check above.
