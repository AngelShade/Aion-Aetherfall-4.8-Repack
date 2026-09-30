# Cash Shop

The 64-bit Aion 4.8 NA addon opens the private Black Cloud Marketplace inside the game. Purchases use inventory Kinah and deliver items through Black Cloud mail.

## Window and item controls

- Cash Shop uses the same version-checked native viewport hook as Central Market. It fills the current screen, resizes its browser below the title bar, and follows resolution changes. Screen pixels are independent of UI scale.
- Item icons in the catalog, item details, purchase review, receipt and purchase history expose Aion's native item tooltip query through both their link and title.
- Eligible equipment has Item Preview on its card and detail page. It uses the existing native preview bridge and docking behavior. Items without a supported native preview keep their item details.
- Search, category/subcategory navigation, sorting, requirements, appearance and Kinah filters, Favorites and purchase history remain available. Pages contain up to 24 offers.
- Category and item navigation replaces the content inside the browser. New GET requests cancel earlier reads. POST actions are serialized and never automatically retried or cancelled by a category click. Existing server-side account-bound, expiring, single-use purchase forms remain authoritative.
- The purchase review shows quantity, price, remaining Kinah and delivery. A receipt confirms the result. If confirmation is unavailable, check Kinah and purchase history before buying again.

## Performance

Artwork is normalized offline with `normalize_market_icons.py`. Serving an icon no longer decodes or resizes it. A bounded media byte cache uses modification time and size stamps, ETags and one-hour private HTTP caching. Only media is cached this way; balances and purchase results remain live.

Catalog reloads create immutable category-count, item-ID and search indexes. Profile snapshots have a bounded cache, verify their file stamps and invalidate on save. Favorites and purchase history retain atomic file persistence.

The UI uses ES5 and layout rules compatible with the client's older embedded browser. Hover transitions and the request status bar use short CSS animations.

## Deployment

1. Build GameServer and prepare a fresh signed addon package from the current installed client. Preserve the existing inventory, search and Central Market patches.
2. Fully close Aion. Stop GameServer gracefully after all characters are offline.
3. Back up the deployed server JAR and `config/ingameshop/media`. Deploy the new JAR and complete source media directory, including `marketplace.js` and normalized icons. Keep the catalog and player profile files.
4. Install the prepared client package with `client-mods/transmog-menu/Install.ps1`. It checks source and staged hashes, backs up replacements and refuses a running client.
5. Restart GameServer and verify game and marketplace listeners. Verify CSS/JS media responses and ETag conditional requests.
6. Reopen Aion. Check fullscreen sizing, native tooltip position, native Item Preview, category switching, Favorites, review and purchase history. Purchases still require their own in-game confirmation; browser fixture tests never purchase live items.

## Validation boundary

Native viewport execution covers Cash Shop and Central Market across 56 resolution/UI-scale cases, with exact widget scope and original fallback. Browser authentication and native preview docking regressions pass. Browser checks cover 1024x768, 1920x1080 and 3440x1440 layouts, native tooltip markup, Preview dispatch, partial navigation, latest-click handling and duplicate POST suppression using an isolated fixture. Real catalog rendering takes approximately 2–4 ms per category in the current local check; this is not a measurement of total in-game click latency. Native tooltip rendering and full-screen behavior need confirmation in Aion after installation.

Installed on 2026-09-30 from `game-server/target/cash-shop-fullscreen-v10`. The installer verified all ten client replacements. Client backup: `TransmogMenu-backups/signed-20260930-205753-054`. Server backup: `target-deploy/game-server/backups/cash-shop-20260930-205720`. The deployed JAR and all 2,474 source media files matched their hashes. GameServer stopped gracefully with zero online characters, restarted, and connected to login and chat servers. CSS, JavaScript and icon responses matched source bytes, carried one-hour caching, and returned HTTP 304 on matching ETags; the logged-out shop returned HTTP 403. In-game acceptance is pending.
