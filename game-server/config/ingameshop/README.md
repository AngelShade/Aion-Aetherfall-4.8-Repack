# Private Black Cloud Marketplace

The NA client's Black Cloud Marketplace button uses two URLs stored in
`L10N/enu/data/data.pak` (`STR_WEB_SHOP` and `STR_WEB_INDIRECT_URL`). This client
ignores the web shop URL console variables. Patch those two strings to point to
the local storefront, then restart the client. In this installation the patched
archive is in the Aion 4.8 NA client folder, and the original is saved as
`data.pak.before-private-marketplace` beside it. Launch with
`Aion Private Marketplace.bat`, log in, and click the regular Black Cloud
Marketplace button. No chat command is needed. The game server must be running
and the character must be in the world.

The storefront listens on `http://127.0.0.1:8091/shop` by default. The client
launcher connects to the private login server at `127.0.0.1:2106`.
The local storefront accepts a logged-in character's session token; for a
single-player local server, it also accepts a loopback request while exactly
one character is online.

Edit the live files in `target-deploy/game-server/config/ingameshop/` to change the running shop. Edits to the copies in `game-server/config/ingameshop/` are source changes for the next deployment. Each tab-separated row contains
`item_id`, `quantity`, `price_in_kinah`, `section`, `unlock_level`, and a short
description. The displayed item name comes from the server's item template.
The shop checks both catalogs on each page request and reloads changed offers,
prices, categories, and descriptions without disconnecting players. Reopen the
shop or browse to another category to see changes. If a catalog edit is invalid,
the last valid catalog stays active and the game server logs the error.
Sections are `starter`, `supplies`, `travel`, `weapons`, `armor`, `upgrades`,
`style`, `food`, `potions`, `outfits`, `hats`, `weapon_skins`, `wings`,
`dyes`, `hair`, `emotes`, `titles`, `pets`, `mounts`, `services`, and `gm`.
Item IDs must exist in
`data/static_data/items/item_templates.xml`, and the quantity must fit one
item stack. The shop blocks purchases below the offer's unlock level and
checks the item's faction and gender restrictions. The larger catalog is
paged, with twelve offers per page.

The storefront's Aion 4.8 art lives in `media/`: one PNG thumbnail per offer,
a client loading-screen image for the header, the client's title font, and the
theme stylesheet. `media/icon_sources.tsv` records each thumbnail's client DDS
source. `exact` means the client item record named that icon; `related` and
`type` use an in-game icon for the same item family when the record did not
specify one. Keep the media directory with the catalog when deploying the shop.
Replace a live PNG, `hero.webp`, `banb.ttf`, or `marketplace.css` in the deployed
`media/` directory to update it without restarting. Media is served without
browser caching; reopen the shop to refresh the page.

`marketplace_extra.tsv` is a reconstructed 4.8 compatible catalog made from
the server's item templates and their supported pet, mount, title, cosmetic,
and consumable actions. It is not a verified copy of the historical NA cash
shop inventory or its prices. All prices here are Kinah. It includes the five
GM named items as requested. To refresh this file after changing static item
data, run `python config/ingameshop/build_extra_catalog.py` from the
`game-server` directory. Copy the regenerated catalog into the deployed
`config/ingameshop/` directory to load it. Changes to the underlying static item
templates still require a game server restart. Purchased items arrive in
Black Cloud mail. Gear is sold piece by piece; choose the item type your class
can equip.

Set `gameserver.marketplace.enable = false` in `config/mygs.properties` to turn
off the storefront. The bind address and port can also be overridden with
`gameserver.marketplace.bind` and `gameserver.marketplace.port`.
