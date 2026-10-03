# Cash Shop and Central Market performance

Central Market retains unchanged catalog rows, detail canvas and visible activity
rows. Hidden order/history/notification views render on demand. Selection keys
include the storage and complete item data so handlers cannot retain a different
item's transfer target. Changed data, favorites, prices and viewport dimensions
invalidate the relevant cached content.

Cash Shop moves the already parsed response DOM into the page instead of parsing
the same markup a second time, and retains identical sidebar/header nodes. Two
compatibility bugs in the existing script were also fixed: the reserved `native`
identifier caused a syntax error in Aion's WebKit, and the hidden `action` input
shadowed `form.action`, preventing asynchronous form submissions. The script now
loads and uses the form's action attribute. Artwork and native icon URLs remain
unchanged. Expensive transitions and animated loading indicators are static.

`verify_browser.py` uses the installed publisher Awesomium and native icon bridge
with a localhost fixture. It never touches real balances, mail or orders. Market
uses production HTML; Cash Shop follows MarketplaceService's DOM with real catalog
IDs, descriptions and prices. Functional tests pass at 1024x740, 1366x740,
1920x1052 and 3440x1412: unchanged refresh retention, changed catalog invalidation,
automatic refresh, buy/collect, favorites, review/purchase, single POST submission,
failed-request recovery, native item icon decoding and preview dispatch.
Native tooltip callback events were additionally verified at 1920x1052. A
768x600 exploratory check hit existing overlapping market controls; this update
does not claim support for that size.

Median timings from five operations at 1920x1052 (`before.json`, `after.json`):

| Operation | Before | After |
| --- | ---: | ---: |
| Cash Shop category navigation | 240.09 ms | 94.27 ms |
| Market refresh | 149.31 ms | 130.55 ms |
| Market scroll | 5.64 ms | 5.66 ms |
| Market tab change | 46.61 ms | 47.55 ms |
| Cash Shop scroll | 12.49 ms | 6.51 ms |

These are isolated browser measurements, including fixed input dispatch waits.
They exclude server/database latency and the game's GPU upload/drawing. No
significant market scrolling/tab improvement was measured. This frontend update
does not claim to fix the intermittent shared webpage flash.

Deployment backs up five browser files and the JAR, verifies all configuration
and static data are unchanged, and patches only two asset-version constants in
the currently deployed MarketplaceService class. All method bodies and 3332
other JAR entries remain identical. Windows locked the running JAR against atomic
replacement, so deployment used the native graceful shutdown routine with zero
players online, then restarted GameServer in the requested visible CMD window.
A read-only Java agent verifies the live page's new asset versions. Source-wide
compilation/deployment is avoided because it would include unrelated work.

```powershell
python client-mods/market-performance/stage_versions.py
python client-mods/market-performance/verify_browser.py --browser-bin '<Aion root>/bin64' --report output/market-performance/after.json --verify
python client-mods/market-performance/install_ui.py
```

Receipts and screenshots are under `output/market-performance`. Close and reopen
the in-game windows to load the new CSS/JavaScript versions.

## Storage, custody and AFK removal

The follow-up opens the left storage selector on **Market**. Storage panes reuse
cached content and switch without an HTTP request. Native DDS icons load in small
batches for the visible rows, after the tab/frame paints; scrolling/resizing loads
newly visible icons. With 240 distinct storage items, the final isolated publisher
browser test measured six switches at 55, 38, 48, 43, 32 and 28 ms. The preceding
1024/1920/3440 stress run and final 1920 run passed native tooltip and action checks.

Snapshot responses exclude all stock linked to orders from warehouse slots. The
underlying escrow stays intact: cancellation returns unsold stock, and purchase
collection releases bought stock. My Orders returns active/queued orders and closed
orders with outstanding items/Kinah to collect. Collected/cancelled closed entries
remain in database records and trade history, but disappear from My Orders. Its
count and pagination use the same predicate. The isolated database test passed 169
checks, including collection, cancellation, duplicate protection and history retention.

The rejected AFK extension was removed from source and deployment: service,
configuration class, singleton, startup hook, config registration and properties
file. This includes no Aion client changes. Historic test receipts/source backups
remain under `output/afk-research` and `output/afk-removal`.

The Java 25 class-file staging tool preserves every unedited method byte and field
in the three affected deployed classes. All 3327 other JAR entries are identical;
three AFK classes are removed. Configuration and static data, other than the two
market assets and removed AFK option, are hash-checked as unchanged. Staging,
database/browser results and the deployment receipt are in `output/market-custody`.
