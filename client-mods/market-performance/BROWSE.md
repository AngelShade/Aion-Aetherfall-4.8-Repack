# Central Market discovery

Categories expand into item types beneath their heading; clicking a heading again
collapses it. Counts cover the eligible native catalogue, independent of the current
search or stock filter. Classification uses the matching 4.8 item groups, stigma
metadata, actions and consumable ID families, rather than translated item names.
Custody classification and warehouse volume rules remain unchanged.

Search supports combined category/type, armor slot, grade, minimum/maximum item
level and inclusive minimum/maximum **plain-item base price** in Kinah. It retains
the stock, favorites and faction filters. Filtering and ordering happen before
counting/pagination; actual variant prices remain in the item detail order book.
Reset clears search/ranges/grade/slot and keeps the selected category/type.

The opening view is **Price Changes**: plain-item base prices differing from their
previous price, whose last adjustment occurred within 24 hours. Rank is absolute
percentage movement, then name and item ID. Rows show current listed quantity and
cumulative quantity traded across variants, base price, signed direction, Kinah
change and percentage. The previous price is available on the movement tooltip.
The existing simulated traders remain clearly identified in the header; no history,
prices, rankings, accounts or item grants are fabricated by discovery.

BDO research: Pearl Abyss's [Central Market guide](https://blackdesert.pearlabyss.com/Asia/en-US/Game/Wiki?_masterWikiNo=39)
describes expandable category/subcategory navigation. Its [August 7, 2019 notes](https://blackdesert.pearlabyss.com/Asia/en-us/News/Notice/Detail?_boardNo=1097)
document arrow and price information for Volatile Price Items. The publisher's
[2019 Steam announcements](https://store.steampowered.com/news/posts/?appids=582660%2C1324160&enddate=1557248707)
refer to that screen when first entering the market. These sources do not publish
the exact ranking formula; the 24-hour/% rule above is Aetherfall's own explicit rule.

The metadata index is immutable and reused. Normal catalogue reads aggregate
orders/trades only for the visible 24 items; stock/volume sorting requires aggregate
activity for the catalogue. Multi-query reads use a consistent database transaction.
Existing local warehouse pane reuse and visible native icon batches are preserved.

Validation is recorded in `output/market-browse`: isolated database integration,
publisher Awesomium browser runs, preservation manifest and guarded deployment
receipt. Browser fixtures exercise actions/tooltips without touching player data;
they exclude game GPU upload and are not live FPS measurements. The one-time
server verification agent only reads catalogue data and is not a client diagnostic.

Deployment adds CentralMarketBrowse and its two immutable record classes, replaces
only CentralMarketService.catalogView, and makes templateView package-accessible
without changing its body. Every other installed method and 3329 JAR entries remain
identical. Only market HTML/JS/CSS are installed; other configuration, static data
and the installed Aion client remain intact.
