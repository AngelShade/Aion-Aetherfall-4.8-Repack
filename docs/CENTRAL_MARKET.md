# Central Market

The Central Market menu entry opens one Aion window containing Inventory, Character Warehouse, Account Warehouse, Market Warehouse and Central Market. The existing Broker remains a separate economy.

## Using the window

1. Open **Central Market** using the HUD icon next to Shop. Allow the client to finish loading after login.
2. Choose Inventory, Character or Account. Select an item and use **Transfer** or **Market Warehouse**. Double-click transfers an eligible item to Market Warehouse. Items can also be dragged onto a warehouse tab. Confirm the quantity and destination.
3. Deposit Kinah from the selected Inventory or warehouse. Buy orders use the Market Warehouse balance.
4. Search the market, choose an item and select its enchantment or tempering. **Available to buy** on the left means sale stock; **Waiting buyers** on the right means demand. The initial selection chooses the lowest stocked price in the current band. **Buy Now** fills stock at the selected price or lower; any remainder becomes a funded preorder. The confirmation updates when you choose another price.
5. To sell, select an unreserved item in Market Warehouse, then **Register Sale**. Listed items remain reserved until sold or cancelled.
6. **My Orders** shows active, queued, filled and cancelled orders. Cancel returns only the unfilled items or reserved Kinah.
7. Each order has **Collect Items** or **Collect Kinah** when it has a filled quantity. Collection also works for partial orders while the remainder stays active. Sale collection applies market tax and credits Market Warehouse Kinah. **Withdraw** transfers Kinah to the selected Inventory or warehouse.
8. Purchased items stay in account custody until **Collect Items** releases them into usable Market Warehouse stock. Transfer them to Inventory or a compatible warehouse. Previously delivered purchases remain usable; startup migration assigns only outstanding legacy proceeds to sales and cannot claim them twice.

Searches can be saved, items can be added to Favorites, and lists can be filtered, sorted and paged. Trade History includes purchases, sales and collections. Notifications show your 50 most recent purchases and sales, with links to My Orders, plus queued high-value listings. Item Details includes Aion stats and modified attributes; Item Preview opens the game's native preview window.

The flow follows Pearl Abyss's [Central Market guide](https://www.naeu.playblackdesert.com/en-US/Wiki?wikiNo=47): immediate stock purchases and sales into preorders, funded waiting buy orders, higher-bid priority, random matching among equal highest bids, and delayed high-value registration. Per-order item and Kinah collection is this server's requested behavior.

Simulation defaults to 3,000 traders and is separate from Broker. Account 0 represents virtual quotes, never a character or funded wallet. Only plain item variants are generated on funded purchases; player-sold gear retains its attributes. No simulated-to-simulated trades occur. Existing stock matches before on-demand quote rotation.

Maintenance refreshes at most 40 due quotes per pass, committing and releasing a fair matching lock for each variant so waiting clicks run before the worker acquires it again. Quotes are updated in place instead of producing cancelled rows on every rotation. Only variants with real open orders are matched. Full warehouse reads use a consistent database snapshot without the global matching lock; catalog and item reads are independent. Automatic polling refreshes account activity every 20 seconds without rebuilding warehouse or catalog contents, then refreshes the selected order book. Fonts grow on large viewports and buttons use raised, shaded surfaces compatible with the embedded browser.

### Transfer several items

1. Choose the source tab: Inventory, Character, Account or Market.
2. Click **Select Items**, then click the item stacks to mark them. **Ctrl + click** also enters selection mode. **Select All** marks unreserved items matching the current search; **Clear** removes the selection.
3. Click **Transfer Selected**, choose the destination, and click **Transfer All**. You can also drag a marked item onto the destination tab to open the same confirmation.

Up to 300 full stacks transfer in one transaction. Compatible stacks merge within their stack limits. If any item is restricted, reserved for sale, changed, or would exceed the destination capacity or Market Warehouse volume, the entire transfer is refused. No selected items move. Switching source tabs clears the selection. Single-item Transfer still supports a chosen quantity.

Quest items and items in dedicated special inventories are excluded from these storage lists and cannot be transferred through Central Market.

## Database

The following new InnoDB tables are created in the configured GameServer database:

| Table | Contents |
| --- | --- |
| `central_market_wallet` | Account Kinah, uncollected proceeds and wallet version |
| `central_market_catalog` | Item variants, base prices, absolute limits, traded quantities and recorded attributes |
| `central_market_orders` | Buy orders and sale listings, remaining quantities and registration times |
| `central_market_stock` | Account custody of items and sale reservations |
| `central_market_trades` | Completed trades and buyer/seller records |
| `central_market_collections` | Gross proceeds, tax and collected Kinah |
| `central_market_requests` | Committed request receipts preventing duplicate retries |
| `central_market_favorites` | Account Favorites |
| `central_market_searches` | The account's ten most recent saved searches |
| `central_market_simulation` | Per-variant quote refresh deadlines |
| `central_market_settlements` | Per-order outstanding gross proceeds and collected purchase quantities |

Player-sold market items retain their original `inventory` row at **location 125**, owned by account ID. Existing `item_stones` rows retain manastones, Godstones, armsfusion stones and Idian. Plain simulated purchases create a fresh item transactionally. A stock reservation can reference a sell order or a pending buy collection. Character deletion excludes account storage and market custody.

Escrow, fills, custody changes, refunds and ledger entries commit in one database transaction. Account ownership is checked server-side; an account cannot trade with itself. Native packets and embedded warehouse actions share the character connection guard. If a committed transfer cannot refresh the client, the server restores committed storage and disconnects it. A failed recovery blocks that character's inventory saves until a fresh login load.

Schema: [schema.sql](../game-server/config/central-market/schema.sql). Startup also verifies the required tables use InnoDB. The schema installer reads the deployed GameServer's database settings; it does not copy or change character rows:

```powershell
& 'C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot/bin/java.exe' `
  -cp 'target-deploy/game-server/libs/*' `
  game-server/tools/CentralMarketSchemaInstaller.java `
  target-deploy/game-server game-server/config/central-market/schema.sql
```

## BDO rules and Aion adaptations

The implementation uses published Central Market behavior, with Aion items and Kinah. It is not Pearl Abyss's proprietary server code.

| Rule | Implementation |
| --- | --- |
| Account storage | Shared across characters; 5,000 VT |
| Item volume | Weapons/armor 10 VT; accessories 5 VT; materials 0.1 VT; other items 0.3 VT |
| Capacity | Deposits cannot exceed capacity; purchased items can exceed it and remain withdrawable |
| Orders | Kinah or items reserved before matching; partial fills and cancellation refunds |
| Buy priority | Higher price first; ordinary equal-price orders by registration order |
| Price limit ties | Random eligible buyer at the absolute ceiling; random eligible sale at the absolute floor |
| High-value listing | At least 20 billion Kinah: 15-minute registration queue, announcement and randomized equal-price buyers on release |
| Queued-price restriction | New bids cannot exceed the lowest queued listing's price |
| Duplicate registration | One active buy order per account/variant; one queued sale per account/item/enchantment/tempering |
| Collection | 65% of gross proceeds; Premium membership maps to the Value Pack's 84.5% return |
| Current price band | Up to ±7.5% around base, clamped to absolute limits |
| Item attributes | Aion enchantment, tempering, appearance, sockets, armsfusion and random bonuses remain with the actual item |

BDO's complete price algorithm and all item-specific limits are not publicly specified. The Aion pricing policy is explicit in `CentralMarketRules` and `CentralMarketService`: initial prices use template value, level, quality and enhancement; absolute limits start at one tenth and ten times that seed; the ladder uses 0.5% ticks; open-order imbalance changes the base by 1% every eight hours. Order quantity is at most 1,000 or the template stack limit, and nonstackable gear uses quantity one. Existing orders remain visible when the price band moves.

The floor-price sale lottery is an Aion policy; the cited official sources explicitly document buyer lotteries. The implementation does not create an NPC market maker or duplicate BDO's older guide behavior where the Marketplace Director purchases initial stock.

BDO systems without an Aion equivalent—Family Fame, maids, Pearl items and its mobile application—are not introduced. Premium uses this server's existing membership value. Aion character/account warehouse restrictions remain enforced. Modified gear has separate exact variants rather than losing its Aion attributes.

Primary research:

- [Pearl Abyss Central Market guide](https://blackdesert.pearlabyss.com/Asia/en-US/Game/Wiki?_masterWikiNo=39)
- [Updated item volumes, March 2023](https://blackdesert.pearlabyss.com/Console/en-us/News/Notice/Detail?_boardNo=10996)
- [High-value registration and matching, June 2021](https://www.console.playblackdesert.com/News/Notice/Detail?boardNo=7580&countryType=en-US)
- [Registration-queue price restriction, January 2023](https://blackdesert.pearlabyss.com/Console/en-us/News/Notice/Detail?_boardNo=10873)
- [Central Market price limits, 2026](https://blackdesert.pearlabyss.com/Console/en-US/News/Notice/Detail?_boardNo=13304)

## Client and server installation

The signed menu package contains the Warehouse addon window and the version-checked browser/preview hooks, while retaining the current unified inventory and search hooks. The supported executable is this **Aion 4.8 NA 64-bit client**. Original artwork is extracted from client items and textures; the mapping is recorded in `icon_sources.tsv`.

Central Market uses a version-checked native resize hook for the exact widget names `PrivateWarehouse` and `PrivateWarehouseBrowser`. The XML frame is only an initial rectangle. Each native layout pass uses the actual viewport width and height in pixels, places the dialog at `(0, 0)`, and sizes its browser below the title bar. The 25-unit title inset follows UI scale; viewport width and height are not multiplied by UI scale. The parent resize also resizes its browser, including resolution changes while open. Opening the menu queues a layout pass after Show. Other widget names use the original native setter.

The fit flags alone did not produce fullscreen sizing in the live 1920x1080 client: the native window remained 1440x1080 at x=240. `tests/verify_market_viewport.py` executes the generated hook with the original native rectangle setter in an isolated process. It covers 28 resolution/UI-scale combinations, ultrawide displays, repeated resizing at unchanged coordinates, browser title spacing, other-widget fallback, invalid viewport values, and XML loading before the browser child exists. In-game fullscreen and resolution-change behavior require confirmation after installing the signed client package. The web layout adapts to the resulting browser viewport; controls and confirmation dialogs have opaque dark backgrounds.

Installed the viewport correction from `game-server/target/central-market-viewport-v9` on 2026-09-30. All ten client files were backed up and hash-verified by the installer with Aion closed. Backup: `TransmogMenu-backups/signed-20260930-201408-465`. GameServer was not restarted. Native browser authentication, preview docking, inventory viewport, inventory search, and DLL modification bounds passed. Live fullscreen behavior is pending confirmation.

### Response time

Selecting a catalog item requests only its current prices, orders, attributes and price history. It does not reload warehouse storage or scan prices for every market item. Name-sorted catalog pages aggregate prices and stock for their 24 displayed items; price/stock/trade sorting and price-change filtering aggregate the matching catalog as required.

Warehouse selections and price selections preserve their existing list nodes. New read requests cancel stale browser requests; the newest selection wins. Mutations remain serialized and require the existing account-bound receipt. Client artwork is cached for one day, while market state and actions remain uncached. A cached icon is not market price or stock data.

The responsive package was installed from `game-server/target/central-market-responsive-v6`; all eight client file hashes matched. Client backup: `TransmogMenu-backups/signed-20260930-161307-990`. Server backup: `target-deploy/game-server/backups/central-market-responsive-20260930-161414`. GameServer was stopped gracefully with zero online players, then restarted with the new API. Startup confirmed 46,474 tradeable templates and the market listener. Icon responses returned HTTP 200 with one-day caching; unauthenticated state requests returned HTTP 403.

The package build, 102 isolated database checks, list-node preservation, latest-click handling, and browser layouts at 1024, 1920, 2560 and 3440 pixels wide passed. Native window size, click response and hover tooltip rendering still require confirmation in Aion.

1. Build GameServer and prepare a new signed client package using `client-mods/transmog-menu/build_package.py`.
2. Fully close Aion. Wait for zero online characters, then stop GameServer normally.
3. Back up the deployed server JAR and any existing `config/central-market` directory. Copy the new JAR and the complete `game-server/config/central-market` directory, including icons, to the deployed server.
4. Run `client-mods/transmog-menu/Install.ps1` against the prepared package. It verifies source/staged hashes, backs up every replacement and refuses a running Aion process.
5. Restart GameServer. Confirm the log contains **Central Market ready** and the marketplace listener starts.
6. Reopen Aion. Verify Inventory still works, open Warehouse, transfer an item and Kinah, then test a sale and purchase with different accounts. Verify cancellation, collection and persistence after relogin.

The supplied client URL is `http://127.0.0.1:8091/market`, matching this local server installation. The client requests its existing account security token through the native game packet and opens the market with the token in `session_id`. Each browser is authenticated to its own account, including when several characters are online. The Market API has no unauthenticated player fallback. The browser hook retains the publisher path for unrelated URLs. Remote deployments require a matching client URL/transport configuration; this package uses the local endpoint.

Do not restore an older server JAR after market activity without keeping market custody supported or first reconciling items and escrow. Client backups restore visuals; they do not reverse trades. Keep the database tables and location-125 rows when updating the server. Database backups must include all market tables, `inventory` and `item_stones` together.

## Verification

- GameServer/Commons Maven package build.
- Real-template catalog regression: normal inventory items remain eligible; special inventory items are excluded.
- Isolated MySQL integration checks using a fresh empty schema, with actual matching/cancellation code: partial fills, price improvement, custody, item attributes, priority, self-trade exclusion, cap selection, refunds, rollback, tax rounding and durable records.
- Exact-dimension browser fixture for the combined layout, original item icons, item selection and purchase dialog. This fixture never changes player data.
- Isolated native browser/preview hook execution, exact URL matching, original publisher fallback and DLL change bounds.
- Existing native inventory viewport, search and localized archive regression checks.

These checks do not replace the final in-game transfer, matching and relogin verification after installation.
