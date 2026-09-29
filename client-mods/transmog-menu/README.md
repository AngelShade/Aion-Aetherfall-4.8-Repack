# In-game service menu: Transmog, Broker, Cash Shop

The Additional Functions submenu contains, in order:

1. **Transmog** — opens the normal appearance remodeling window.
2. **Broker** — opens the normal Broker window with remote access.
3. **Cash Shop** — opens the private web shop inside an Aion window.
4. **Relic Appraiser** — retains its original behavior.

## How it works

`RelicCalc.pak` registers the menu entries. `PrivateMenus.lua` handles Cash Shop
through the client's existing `Browser` widget, `CreateWebView`, and
`LoadUrlWithWebAuth` methods. It does not launch an external browser.
`CashShop.xml` defines the window and its close button.

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

The six replacements are `bin64/game.dll`, `Pub.key`,
`Plugin/RelicCalc/RelicCalc.pak`, and signatures for RelicCalc, bin32, and
func_pet. The latter two package contents are unchanged. Any old pending
`bin64/game.dll.patched` is backed up and retired so the launcher cannot
replace the new DLL with an older patch.

The installer refuses to run while Aion is open, validates current and prepared
hashes, and backs up every replaced file before writing. It rolls back on an
installation error. A full restart is required.

The first installation changed the client package trust key. Publisher updates
must not be mixed with these local signatures. Keep the original signed-file
backup and `game.dll.orig` for returning to the publisher files.

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

`menus.json` now also adds **Warehouse** before Relic Appraiser. Deploy
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
