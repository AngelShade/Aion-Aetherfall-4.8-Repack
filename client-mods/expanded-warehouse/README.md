# Expanded Character and Account Warehouses (Aion 4.8 NA)

The matching Aetherfall GameServer gives every character **360 Character Warehouse slots** and every account **540 Account Warehouse slots**. Previously earned Character Warehouse expansion credits remain saved but do not add slots in Aetherfall. The native expansion button is hidden so a player is not charged for capacity already available.

The client patch keeps the existing native warehouse item lists, tooltips, transfers, sorting, Kinah display, and Legion Warehouse. It widens the dialog to 12 columns and gives the Character and Account lists scrollable rows. Each list has its own Search and Clear controls. Search dims nonmatching items in their original slots; it does not move items or rewrite their positions. The version-checked DLL patch enlarges the native list initialization bounds and active slot counts and routes warehouse search drawing through the existing Inventory search hook.

Both warehouse sections have native collapse controls. The window shrinks around
the visible sections, keeping its position and scaled spacing. Inventory
right-click deposits select the expanded section. When both sections are open,
they use the last-selected section; when both are collapsed, they reopen the
last-used section before native transfer or count handling. The selection is
session state. Legion Warehouse retains its native destination.

The Kinah amount label in Warehouse and both Inventory layouts passes mouse
input through to the native coin control. No transfer amounts, confirmations or
server rules change. Only these three XML entries change in each UI archive;
every unrelated compressed entry is retained. The native UI catalogue, matching
signatures and graphics/cursor restore images are refreshed with those resources.

For an installed cumulative client, stage just this repair with
`../transmog-menu/prepare_warehouse_item_context.py --repair collapse --client <original> --output <external>`
and install through `../transmog-menu/install_window_queue.py --client <original> --package <external>`.
The existing detached-Inventory check's `--warehouse-controls` option enters the
actual Warehouse button, initialization, OnVisible and both Inventory destination
paths with offline widgets. It checks collapse/expand, last-used reopening and
Legion routing at three scales. Actual appearance, scrolling, reopening, item
transfers and single-click Kinah still require an in-game check.

## Build and install

The existing `client-mods/transmog-menu/build_package.py` builder includes this patch when `menus.json` has the `warehouse` entry. Use that builder and `Install.ps1` to stage and install the combined client package. Both scripts verify the client files; the installer backs up replaced files under `TransmogMenu-backups`. Fully close Aion first. Deploy the matching newly built GameServer JAR during a normal maintenance stop, then start GameServer and Aion.

The builder supports the exact original Aion 4.8 NA DLL, the Inventory-only DLL, and the verified local Inventory/Market DLL. Other binaries are refused. Both UI archives are rebuilt and checked, and unrelated archive entries must stay byte-identical. A recipient needs this client patch and server setting together to use slots above the old limits.

## In-game check

Open the standard Warehouse with a Character and Account Warehouse. Check that both lists have 12 columns. Search for an item in each list, check that nonmatches dim, then use Clear. Scroll to the final row, move an item into a high slot in each list, close/reopen the window, relog, and verify the positions persist. Test a second character on the same account to confirm the Account Warehouse items and capacity. Check Legion Warehouse, Inventory, sorting, Kinah transfers, and the Central Market Warehouse tabs as regression checks.

Before restoring an older server/client build, move items out of all slots that exceed the old limits. Keep a consistent database backup containing `inventory`, `item_stones`, and the `central_market_*` tables when market activity exists.

The expanded storage server classes compile and package normally. Client
controls have focused native-entry and archive-preservation checks. Actual
gameplay behavior and high-slot persistence require the in-game check above.

## Capacity and rejected-deposit repair (5 November 2026)

The old DLL enlarged only the rendered lists. Its shared native storage capacity
setter at file offset `0x45fa70` still received `24 + 8 * earned expansions` for
Character and a hardcoded 16 for Account. `SM_WAREHOUSE_INFO` consumes this at
`0x377fa0`; `SM_CUBE_UPDATE` consumes it at `0x374f93`. Right-click admission at
`0x4592f7` calls the native free-slot function `0x44a370`, then emits client-side
message 1300417 if full. Incoming slot admission at `0x45fe26` also uses that
capacity, hiding otherwise retained server items above it.

`patch_capacity_dll` repairs the shared setter for storage types 1 and 2 only,
using the verified empty bytes at `0x144e6c8`. The constructor assigns these types
at `0x442349` and `0x4423bc`. Inventory, Legion and pet capacities are retained.
Both server warehouse packet paths advertise character capacity as expansion byte
42 (`2a`); saved earned credits are untouched. The obsolete expanded setting is
accepted for existing settings files but no longer reduces the shipped capacity.

Stage an existing original client with `prepare_warehouse_item_context.py --repair
capacity` and install its matching graphics/cursor package and restore cohort.
The active English data.pak is never replaced by this repair. The normal combined
builder also includes the setter repair for fresh installations.

`check_capacity.py` executes both real DLL consumers for 0/6/11 credits and native
admission with Unicorn. `WarehouseDepositCheck` enters actual CM_MOVE_ITEM and
CM_SPLIT_ITEM handlers, checks slot 73 and the final slots, native save bindings,
and failed-add source custody/corrective packets. Its optional before-repair mode
reproduces high-slot retention and the old rejected-split loss against an old JAR.
The default check uses in-memory JDBC. Optional --scratch-before/--scratch-after modes verify native SQL save/reload only on port 33907 in a warehouse_deposit_* schema; no player databases are inspected.
