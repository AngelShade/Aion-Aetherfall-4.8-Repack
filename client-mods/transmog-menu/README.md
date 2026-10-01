# In-game service menu: Wardrobe, Broker, Cash Shop, Central Market

Wardrobe is the permanent account appearance collection. See
[Wardrobe instructions](../../docs/WARDROBE.md) for unlocking skins, applying
appearances, saved outfits, deployment and rollback.

## English client menu icons

`menus.json` assigns native client skins to each custom option: remodeling for
Wardrobe, exchange hands for Broker, a gift box for Cash Shop, and a warehouse
chest for Central Market. Relic Appraiser retains its stock relic icon. Both the
base and English `UI_Preload.xml` definitions and texture bounds are verified
before preparing packages. Menu labels and actions remain in English.

For an already installed archive-v2 client, `prepare_menu_icons.py` accepts the
same `--codec-directory`, `--client-path`, `--java`, and `--output` arguments as
the complete builder. It changes only icon references in the installed
`PrivateMenus.lua`, preserving all other plugin entries byte-for-byte. It stages
the plugin archive, its signature, the other two archive signatures, and
`Addon.key`; the stock pet/model key, engine DLLs, and localized inventory and
warehouse archives remain untouched. Install and restore use the normal scripts
with hash verification and backups. Aion must be closed during installation.

## Pet model signing correction

The client verifies signatures embedded in pet CGF models using the stock
`Pub.key`. Replacing that key with a local menu key makes the stock models fail
verification, even when the pet template and archive CRCs are valid.

The builder preserves the original model key and stock package contents.
It signs all three checked archives with a separate `Addon.key`. A version-checked
CrySystem patch changes both archive loaders' key-file references;
the model signature verifier continues using `Pub.key`. Installation
backs up the engine DLL and records whether the addon key previously existed.

`prepare_signature_repair.py` stages the signing correction without rebuilding
the installed game DLL, plugin UI, inventory, or login announcement. It verifies
the stock package contents against their original signatures before staging
new archive signatures. Both loaders need the addon key: a live process check
showed the stock-key loader rejecting RelicCalc and the addon-key loader rejecting
bin32 when the first correction changed only one loader.

The Additional Functions submenu contains, in order:

1. **Wardrobe** — opens the permanent account appearance collection.
2. **Broker** — opens the normal Broker window with remote access.
3. **Cash Shop** — opens the private web shop inside an Aion window.
4. **Central Market** — opens the combined storage and market window.
5. **Relic Appraiser** — retains its original behavior.

## How it works

`RelicCalc.pak` registers the menu entries. `PrivateMenus.lua` handles Cash Shop
through the client's existing `Browser` widget, `CreateWebView`, and
`LoadUrlWithWebAuth` methods. It does not launch an external browser.
`CashShop.xml`, `Warehouse.xml` and `Wardrobe.xml` define these native browser
windows. The Wardrobe button dispatches the local `/wardrobe` action.

The small `bin64/game.dll` hook recognizes only the exact server commands listed
in `menus.json` and sends them through normal in-game chat. Other menu actions
continue through the original addon handler. This patch supports one verified
4.8 NA DLL build; `patch_game_dll.py` validates its full original SHA256 and the
empty code region before building. It never writes to a running process.

Server command `.broker` authorizes a remote session tied to the current player
and opens native dialog page 13. The eight Broker request handlers accept either
normal Broker NPC interaction or that player's active remote session. A remote
session expires after 30 minutes without Broker requests; reopen the window to
renew it. Listing, purchase, item ownership, fees, inventory, and settlement
logic remain in the existing BrokerService.

`.transmog` continues to open dialog page 19. Remodeling uses the normal item
restrictions, costs, and appearance-item consumption.

## Editing menus yourself

Edit **menus.json**. For another existing server player command, add an entry:

```json
{"label": "My Service", "command": "myservice"}
```

Use the command name without the leading dot. The command must already exist
on the server and be permitted for your account. Labels use letters, numbers,
and spaces. Adding a button does not implement a missing server service or
remove its NPC requirements. The Cash Shop label and URL are also in this file.

Rebuild and reinstall after a change. Close Aion before installation. You do
not need to edit machine-code offsets or sign files by hand.

## Build and install

Requires Python, JDK 17+, and the local Aion PAK codec scripts. Use a fresh
staging directory outside the client. Preparation does not change the client:

```powershell
python build_package.py --codec-directory '<codec directory>' --client-path '<client root>' --java '<path to java.exe>' --output '<new staging directory>'
./Install.ps1 -ClientPath '<client root>' -PreparedPath '<new staging directory>'
```

The source client must contain its verified original `bin64/game.dll.orig`.
The builder accepts the original RelicCalc, the earlier Transmog version, or
this menu version. It verifies existing signed packages, creates an ephemeral
local signing key, and verifies the new package signatures. No private key is
saved. The legacy RSA/SHA1 format is used only for client compatibility.

The menu replacements are `bin64/game.dll`, `bin64/crysystem.dll`, `Pub.key`,
`Addon.key`, `Plugin/RelicCalc/RelicCalc.pak`, and signatures for RelicCalc, bin32,
and func_pet. Unified inventory also stages its UI archives. The bin32 and
func_pet package contents are unchanged. Any old pending
`bin64/game.dll.patched` is backed up and retired so the launcher cannot
replace the new DLL with an older patch.

The installer refuses to run while Aion is open, validates current and prepared
hashes, and backs up every replaced file before writing. It rolls back on an
installation error. A full restart is required.

The original model trust key remains in `Pub.key`; local archive signatures use
`Addon.key`. Keep the original signed-file backup and `game.dll.orig` for
returning to the publisher files.

## Server deployment

Deploy `RemoteBrokerService` and the eight updated Broker request handlers
along with `data/handlers/playercommands/Broker.java` and `broker = 0` in
`config/administration/commands.properties`. Restart GameServer after replacing
its JAR, with the game client closed. Existing Transmog and private Cash Shop
services remain required. The default shop URL is `http://127.0.0.1:8091/shop`.
The DLL sends this exact configured local URL directly to the embedded browser.
This avoids the publisher web-login redirect, which fails for the private shop.
Other browser URLs keep the client's original authentication behavior. The
builder restricts this navigation bridge to the loopback marketplace endpoint.

## Undo the latest installation

The Central Market entry now has a combined character/account Warehouse and Central
Market window. See [Central Market instructions](../../docs/CENTRAL_MARKET.md)
for storage, orders, tables and installation. Its browser uses the client's
native account-token request and opens `/market?session_id=...`; several online
accounts remain separate. The native Item Preview also docks to this window.

Fully close Aion, then run:

```powershell
./Restore.ps1 -ClientPath '<client root>' -BackupPath '<backup path printed during installation>'
```

This restores the state before that installation, including the DLL and any
pending DLL when recorded in the backup. Older five-file backups predate DLL
support and cannot restore the DLL. The launcher is not changed by this version.
Server changes have their own deployment backup.

## Verification status

Transmog and the Broker window were confirmed working by the user. The new Broker
classes and command compile; the staged JAR preserves every unrelated entry.
Package round-trip/CRC and original/new signatures are checked by the builder.
Broker transactions remain untested. Cash Shop purchases were confirmed by the
user. The embedded Cash Shop authentication
fix now loads the catalog, as reported by the user. The storefront was rebuilt
for the 968 x 620 browser area: six visible offers, a category sidebar, separate
catalog scrolling, and compact item details and purchase review. Browser checks
at that size passed navigation, search, sorting, pagination, and review-screen
fit without submitting a purchase. The revised layout still needs a visual
check inside Aion.

## Warehouse and Cash Shop additions

`menus.json` now also adds **Central Market** before Relic Appraiser. Deploy
`data/handlers/playercommands/Warehouse.java` and `warehouse = 0` alongside the
signed client package. The command opens the standard character/account
warehouse using the existing storage service and item movement rules.

The Cash Shop has character equipment requirements, a requirements filter,
Favorites, and Purchase history. Favorites and the last 200 purchases are stored
per character in `data/marketplace/<character ID>.properties`; include this
directory in server backups. Earlier successful purchases in the current
`log/server_console.log` are imported once. This history records delivery to
Black Cloud mail, not whether a character has collected the attachment.

**Preview appearance** opens Aion's native Item Preview window for supported
outfits, headwear, weapons, shields, and wings through
`window.AionObject.ItemPreview(itemId)`. It does not change the character's
appearance, inventory, equipment, stats, or Kinah. Aether Keys have no preview
button because their appearance requires the transformed model.

The version-checked client patch extends native preview positioning only for
addon dialogs containing the `PrivateCashShopBrowser` browser widget. It uses
Aion's existing placement logic to choose the side of Cash Shop and keep the
preview within the screen. Other dialogs retain their existing behavior.

`MarketplaceService`, `MarketplaceProfileService`, and their nested classes must be
deployed together. The new interface uses the existing item artwork, gold/steel
controls, hover transitions, and brief status feedback. It avoids modern layout
features unsupported by the client's embedded browser. Persistence checks and
968 x 620 browser layout checks passed. Native Warehouse access and appearance
preview visuals still require in-game verification after installation.

Installed on 30 September 2026. GameServer restarted after a normal save;
Warehouse compiled and loaded, and the shop started on port 8091. All six
client replacement hashes and all four deployed interface assets match staging.
The live browser passed saving/removing a Favorite and showed the imported
purchase history without changing Kinah. Deployment backups are under
`game-server/target/warehouse-shop-deployment-backup` and the client's
`TransmogMenu-backups/signed-20260930-011918-489` directory.

The storefront stylesheet is `game-server/config/ingameshop/media/marketplace.css`.

### Native Item Preview correction

Installed on 30 September 2026 after a normal GameServer save with no characters
online. The shop invokes the client's native Item Preview bridge. The native
dialog docking hook, browser navigation hook, menu command hook, and JavaScript
bridge passed isolated checks. The deployed JAR, stylesheet, and six client
files were hash-verified, and GameServer and Cash Shop started successfully.
Native window appearance and placement are awaiting the in-game check.

Client staging: `game-server/target/native-preview-menu-v4`. Client backup:
`TransmogMenu-backups/signed-20260930-020636-000`.

The obsolete server preview service, POST handler, and preview form logic were
removed. Native item eligibility is handled directly by `MarketplaceService`.
The cleaned server JAR is staged in `game-server/target/native-preview-cleanup-v5`
and deployed to `target-deploy/game-server/libs`. Superseded shop JAR snapshots
and loose compiled files containing the retired service were removed, including
the old server JAR from `native-preview-deployment-backup`; its stylesheet
backup remains. Client package backups still provide the native menu rollback.
Copy edits to `target-deploy/game-server/config/ingameshop/media/marketplace.css`
and close/reopen Cash Shop to reload them; CSS changes require no server restart
or client package installation. The previous stylesheet is backed up under
`game-server/target/marketplace-layout-backup` for this deployment.

## Unified inventory

`menus.json` enables a 12-column inventory with 180 starting slots and native
cells for up to 279 unlocked slots. The client package
includes `Data/ui/game/game.pak`, the English override
`L10N/enu/data/data.pak` when present, and matching version-checked DLL changes.
Normal inventory uses one native list with scrolling, existing item icons,
slot borders, tooltips, item actions, and the global Sort button. Cube headers,
navigation buttons, and gaps between cubes are removed. Extra Inventory retains
the game's separate item rules and native capacity; its grid has the same layout.

Deploy the matching configuration, player capacity, login/update packets,
expansion service, ticket action, and character creation/login classes with
`gameserver.inventory.unified = true` in `config/main/custom.properties`.
Capacity is 180 plus nine slots for each saved NPC, quest, or item expansion,
up to 11 expansions (279 slots). New characters start without expansion credits;
existing saved credits count toward their capacity. Ticket levels and NPC prices
retain their existing rules. Login and cube-update packets encode the actual
capacity. Saved counters, items, and Kinah are not rewritten during installation.
Setting the server option to false retains the original cube calculation.

The installer verifies and backs up all replacement files, including both UI
archives. Restore client and server together; reduce inventory contents and slot
positions to fit the original capacity before returning to the old client.
Native hook checks, binary XML/archive round trips, 12-column grid bounds,
server/client capacity agreement, legacy fallback, and JAR content checks passed.
In-game rendering, scrolling, dragging, item use, sorting, and relog persistence
still require the live client check after installation.

Installed with client backup
`TransmogMenu-backups/signed-20260930-025905-131` and server backup
`game-server/target/unified-inventory-server-backup`. The prepared files and
verification outputs are in `game-server/target/unified-inventory-v1` and
`game-server/target/unified-inventory-inspection`.

### Inventory crash correction

The first in-game attempt crashed in the native slot effect loop at RVA
`0x788b64`. Its vector bounds check skips an iteration rather than exiting;
removing the original 27-slot cutoff allowed an unbounded loop. Both slot effect
loops now retain a 180-slot cutoff and exit at the actual vector length.

`tests/verify_inventory_effect_loops.py` executes the patched native loop control
in an isolated process with item effects replaced by counters. It passed with
empty, missing, 1, 26, 27, 135, 179, 180, and 200-cell lists. Run it from the
repository root, supplying the prepared `bin64/game.dll` path as its argument.
The layout builder also preserves fixed footer positions across repeated builds.

Correction installed from `game-server/target/unified-inventory-v3`; all seven
client files were hash-verified. Client backup:
`TransmogMenu-backups/signed-20260930-030829-701`. The server capacity remains
180. In-game verification is pending a new login.

### Localized layout and scrolling correction

A live client read confirmed normal inventory capacity 180 and native cell IDs
0 through 179, while the English UI override kept the original three-row list
rectangle and all five cube groups. The builder now patches both copies of the
normal inventory templates. Other localized UI entries remain byte-identical.

The native inventory layout now limits its initial viewport to nine complete
rows of twelve slots (108 visible slots), with scrolling for the remaining 72.
The viewport limit follows the client's UI scale. The empty zero-height native
header stays visible because native layout uses that state to show its grid;
it displays no cube header or controls. Other inventory dialog types retain
their existing native layout behavior.

`tests/verify_inventory_viewport.py` executes the native height clamp and scroll
gate with different content heights and UI scales. It passed, as did the slot
effect loop regression. The corrected eight-file package was installed from
`game-server/target/unified-inventory-v4`, with all eight installed file hashes
verified. Backup: `TransmogMenu-backups/signed-20260930-032234-430`.
Live UI confirmation after restarting Aion is pending.

### Scrolling height correction

The next live check confirmed all 180 cells and working item movement, but the
viewport was 730.125 pixels tall against 731.25 pixels of content. The layout
hook had treated native `xmm15` as UI scale; that register actually contains the
spacing constant 2.0. The hook now calculates its viewport limit from `xmm9`,
the actual first grid height, using the ratio `392 / 650`. This retains nine
visible rows at the client's active UI scale and leaves the remaining rows in
the native scrolling range. The execution fixture now supplies the actual
scaled grid height rather than assuming a scale register.

The corrected package was installed from `game-server/target/unified-inventory-v5`.
Native viewport and effect loop checks, both UI archive checks, and package
signature verification passed. All eight installed file hashes match staging.
Backup: `TransmogMenu-backups/signed-20260930-040308-953`.
Live scrolling verification after restarting Aion is pending.

### Item positions and scroll refresh

A live last-row move was received by GameServer as slot 179 and marked for
the normal inventory save. The login packet still advertised the character's
original expansion counts, while subsequent cube updates advertised 180 slots.
`SM_INVENTORY_INFO` now uses the same 0/0/17 expansion header as
`SM_CUBE_UPDATE` when unified inventory is enabled. The original expansion
counters remain unchanged in the database.

The native cube layout recalculated its thumb fraction from the viewport's
layout origin. That origin stays at zero in the continuous grid. Its normal
inventory branch now retains the existing thumb fraction during refresh;
other inventory classes still execute their original callback. The hook uses
the existing native thumb clamp and scrollbar geometry update.

Prepared files: `game-server/target/unified-inventory-v6` and
`game-server/target/unified-inventory-v6-server`. Login-header byte checks,
native scroll refresh, viewport scaling, effect bounds, archive contents,
and package signatures passed. `tests/verify_inventory_scroll.py` checks
top, middle, and bottom positions and the other-dialog callback.
The v6 client package was installed and all eight file hashes verified.
Client backup: `TransmogMenu-backups/signed-20260930-043622-049`.
The previous GameServer JAR is backed up as
`game-server/target/unified-inventory-v6-server/game-server-before-v6.jar`.
GameServer was stopped gracefully with zero characters online before its JAR
was replaced. The last-row test item's database position was verified as 179
after logout. Post-login position and scroll retention still need confirmation
in the game.

### Expansion tickets and quest rewards

The v7 client has 279 native cells, with cells above the character's unlocked
capacity retaining the game's locked-slot state. The initial viewport remains
nine rows of twelve slots (108 visible slots); the remaining rows scroll.
Quest rewards and valid expansion tickets add nine slots through the existing
`CubeExpandService`. NPC expansion uses the same total limit. Ticket eligibility
is checked again after the item-use delay, before consuming the ticket.

Prepared files: `game-server/target/unified-inventory-v7` and
`game-server/target/unified-inventory-v7-server`. The isolated
`tests/InventoryExpansionCheck.java` verifies starting capacity, quest and ticket
rewards, ticket levels, saved expansion credits, the 279-slot limit, login/update
packet agreement, and legacy capacity. Native slot bounds, effect loops, viewport
scaling, scroll retention, UI archive contents, and package signatures also
passed. In-game ticket use, quest completion, and relog persistence of newly
unlocked slots still require a client check after installation.

Installed v7 with all eight client file hashes verified. Client backup:
`TransmogMenu-backups/signed-20260930-045152-970`. Previous server JAR:
`game-server/target/unified-inventory-v7-server/game-server-before-v7.jar`.
GameServer shut down gracefully with zero characters online and restarted with
unified inventory enabled; ports 7777 and 8091 are listening and login/chat
connections are established. The expansion regression also passed against the
installed server JAR. No character items or expansion counters were changed
during deployment.

### Inventory search

The inventory footer contains a native Search field, Clear button, and match
count. Typing part of an item name dims nonmatching items and scrolls to the
first match. Clear restores the scroll position from before the search.
Matches refresh after item movement without resetting manual scrolling.
The native editbox and buttons retain the game's fonts, skins, focus, hover,
and pressed states. Nine inventory rows remain visible before scrolling.

`inventory_search.py` adds version-checked hooks for item drawing, search updates,
and the Clear button. Item names come from the client's current item bindings;
English letter case is ignored. Search never rewrites slots, changes item
bindings, sorts items, or sends inventory packets. Other item views retain their
native drawing and buttons. The builder includes search automatically when the
unified inventory is enabled. Both base and English UI archives are patched.

Prepared correction: `game-server/target/unified-inventory-v9-search`.
`tests/verify_inventory_search.py` executes the emitted machine code against
isolated widget and item lookup fixtures. It checks substring matching, inline
and allocated names, query bounds, match counts, clearing, moved
items, short/null cell vectors, dimming, unchanged native button callbacks,
UI scaling, and resized viewport scrolling. Viewport, scroll retention, and
effect-loop regressions and package signature checks also passed. Installation
requires closing Aion; no GameServer change or restart is required. In-game
visuals and text entry still need a client check after installation.

Installed v8 search and hash-verified all eight client files. Backup:
`TransmogMenu-backups/signed-20260930-053834-247`. GameServer was not restarted.
The v9 correction uses existing English string keys for Search and Clear,
the native editbox font and padding, and the standard text-field attributes.
The Next match widget and its cycling code have been removed. Open Inventory
after starting Aion and check Search, Clear, and the blinking caret after
clicking the field. Live rendering remains the final acceptance check.

Installed v9 and hash-verified all eight client files. Backup:
`TransmogMenu-backups/signed-20260930-070316-174`. The installer confirmed
Aion was closed before replacement. No GameServer restart was required.
