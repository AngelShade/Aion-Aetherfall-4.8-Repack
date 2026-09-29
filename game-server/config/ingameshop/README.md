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
The shop checks `marketplace_cash.tsv` on each page request and reloads changed offers,
prices, categories, and descriptions without disconnecting players. Reopen the
shop or browse to another category to see changes. If a catalog edit is invalid,
the last valid catalog stays active and the game server logs the error.
The storefront opens on `Featured`. Its current collections are `featured`,
`fashion`, `weapon_looks`, `equipment`, `wings`, `companions`, `collectibles`,
`convenience`, `essentials`, `upgrades`, and `bundles`; `all` browses these together.
Every regular current collection has browsable subcategories. Fashion includes
`fashion_outfits`, `fashion_headwear`, `fashion_dyes`, and `fashion_hair`.
Weapon looks includes `weapon_melee`, `weapon_ranged`, and `weapon_arcane`;
Equipment includes `equipment_weapons` and `equipment_armor`; Wings includes
`wing_permanent` and `wing_skins`. Pets & mounts includes `pets` and `mounts`;
Emotes & titles includes `emotes` and `titles`. Convenience includes `storage`,
`entry_scrolls`, `character_services`, `summoned_npcs`, and `travel`. Adventure supplies includes
`food_drink`, `transformation_candy`, `recovery`, and `supply_scrolls`.
Gear upgrades includes `upgrade_stones`, `manastones`, `enchant_supplements`,
`socketing_aids`, `upgrade_shards`, and `upgrade_tools`. Manastones has five
stat families: `manastone_physical`, `manastone_magic`, `manastone_vitality`,
`manastone_defense`, and `manastone_ancient`;
Bundles & gifts includes `gift_boxes` and `travel_bundles`. Parent collections
show all their subcategory offers. The sidebar reveals a collection's children
when browsing it, and its landing page links directly to each child.
`marketplace_cash.tsv` contains 2,473
current Kinah offers covering 2,463 distinct item IDs. It includes 314 wing
offers and 233 convenience offers: 16 storage tickets, 84 instance entry
scrolls, 77 character service items, and 46 travel items. Adventure supplies
includes 86 meals and drinks, 58 recovery offers, and 122 transformation candy
offers. The recovery collection includes 20 instant Life, Mana, and Divine
Serums across level tiers. Convenience includes 10 personal/group summoning
stones for trade brokers, general goods merchants, and warehouse managers.
The summoned NPC lasts five minutes. Gear upgrades includes 196 enchantment
stones, 12 enchanting supplements, eight socketing aids, and 534 manastones
across five stat families. The same item can appear in Featured and a regular
collection; each listing is an offer for the same server item. The catalog has item-specific descriptions based on each
template's name, type, and supported use action. Its detail page also shows
server item attributes and character restrictions.
Food and recovery descriptions are generated from the server's item actions
and skill effects. They show stat bonuses, durations, immediate and periodic
HP/MP restoration, and item reuse times where applicable. Run
`python config/ingameshop/enrich_consumables.py` from the `game-server`
directory after changing those static templates or importing more consumables.
The script keeps existing offer IDs and prices and can be run repeatedly.
The added wings and convenience items were checked against server item templates
and actions. Test and placeholder items were excluded. Matching client icon
art is used where available; otherwise the closest related or item-type art
from the local Aion client is used and labeled in `media/icon_sources.tsv`.
Upgrade item templates with the expected enchant action were included;
items explicitly labeled event, stamp, reward, or test were excluded. Existing offer prices
were kept. Run `python config/ingameshop/expand_upgrade_catalog.py` from the
`game-server` directory after changing item templates or extending these
families. It checks selected item counts and actions and can be run repeatedly.
New templates need a matching thumbnail in `media/icons/` and an entry in
`media/icon_sources.tsv`.
Imported offers retain their Kinah prices, stack quantities, and level
requirements; their descriptions identify each item's name, type, and supported
use action. The historical `marketplace.tsv` and `marketplace_extra.tsv` files
remain only in the source checkout as maintenance inputs; the storefront neither
loads nor displays them, and they are absent from the deployed server folder.
Run `python config/ingameshop/expand_cash_catalog.py` from the
`game-server` directory to import newly added historical IDs into the current
catalog. It keeps existing current offers unchanged and can be run repeatedly.
Item IDs must exist in
`data/static_data/items/item_templates.xml`, and the quantity must fit one
item stack. The shop blocks purchases below the offer's unlock level and
checks the item's faction and gender restrictions. Collections support search,
sorting by name or price, filters for usable and affordable offers, and
pagination with direct page links. Category links show offer counts. Each card
has a quick-look tooltip and a full item page. Purchases
go through a review screen showing the price, remaining Kinah, and Black Cloud
mail destination. The existing one-time purchase form and server checks handle
payment after confirmation. The response is a prominent transaction receipt:
green for confirmed payment and mail delivery, red for a failed attempt, and
amber when the server could not confirm the result. A successful receipt shows
the item, Kinah paid, current balance, and character receiving the mail. An
uncertain result tells the player to check mail and Kinah before buying again.
The purchase button switches to a processing state on submit. Refreshing a
receipt within ten minutes reuses that character's recorded result instead of
attempting another charge or replacing success with an expired-form message.
Shop links land at the collection or item content instead of the top banner.
Returning from item details lands at that item's card in the current list;
purchase confirmation lands at the transaction receipt. These positions use normal
URL fragments so they work in the in-game browser without script storage.

The storefront's Aion 4.8 art lives in `media/`: one PNG thumbnail per offer,
a client loading-screen image for the header, three more client loading images
for the collection panels, the client's title font, and the theme stylesheet.
The interface uses animated hover states and reduced-motion CSS. Awesomium
1.6.3 in the client gets a compatible float/inline layout; modern browsers get
the grid layout. `media/icon_sources.tsv` records each thumbnail's client DDS
source. `exact` means the client item record named that icon; `related` and
`type` use an in-game icon for the same item family when the record did not
specify one. Keep the media directory with the catalog when deploying the shop.
Replace a live PNG, any storefront WebP, `banb.ttf`, or `marketplace.css` in the deployed
`media/` directory to update it without restarting. Media is served without
browser caching; reopen the shop to refresh the page.

`marketplace_extra.tsv` is a historical 4.8 compatible source made from
the server's item templates and their supported pet, mount, title, cosmetic,
and consumable actions. It is not a verified copy of the historical NA cash
shop inventory or its prices. All prices here are Kinah. It includes the five
GM named items as requested. To refresh this file after changing static item
data, run `python config/ingameshop/build_extra_catalog.py` from the
`game-server` directory, then run `expand_cash_catalog.py` to import any new
IDs into the current catalog. Copy the updated `marketplace_cash.tsv` into the
deployed `config/ingameshop/` directory to load it. Changes to the underlying static item
templates still require a game server restart. Purchased items arrive in
Black Cloud mail. Gear is sold piece by piece; choose the item type your class
can equip.

Set `gameserver.marketplace.enable = false` in `config/mygs.properties` to turn
off the storefront. The bind address and port can also be overridden with
`gameserver.marketplace.bind` and `gameserver.marketplace.port`.
