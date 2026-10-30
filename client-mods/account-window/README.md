# Aetherfall Account window

Profile > Account has an **Account Window** button (and `/account` or `/boons`) that opens the
Aetherfall Account page in the existing fullscreen Market dialog: earned and locked Aetherfall
Boons with progress, renown, Chronicle chapters and the account's characters. The page and its
data are served by the game server (`/market/account`, `AccountHttpService`,
`game-server/config/account/media`); see `client-mods/account-boons/README.md` for the boons.

`prepare.py --client <original-client> --output <fresh-external-folder>` stages:

- `bin64/Game.dll`: `/market/account` joins the Market authentication route table (existing
  cave, 413 of 512 bytes) and the browser hook. The browser hook's last route miss jumps to a new
  bridge at 0x144e800 that checks the account route before the publisher path. The Stigma profile
  click bridge's fallthrough jumps to a bridge at 0x144e880 that runs `/account` for
  `btn_account_window` (through the native slash executor the HUD shortcuts use) before the
  original Help comparison. 0x144e800-0x144e900 was unused in the installed image and both
  graphics/cursor restore baselines.
- `Data/ui/game/game.pak`, `L10N/enu/Data/data.pak`: the button on `tabpage_account_info` and its
  English label/tooltip strings, in the active archives and the Graphics menu restore baseline.
- `Plugin/RelicCalc/RelicCalc.pak`: `PrivateAccount_Open` and the slash commands in
  `PrivateMenus.lua` (no menu entry); package signatures are regenerated.

Every unrelated byte, archive member and Lua file is checked unchanged. `install.py --prepared
<folder> --backup <fresh-external-folder>` requires Aion closed, verifies every hash, backs up and
rolls back on failure. The Account window needs the matching GameServer build.

## Native Account details

`prepare_details.py --client <original-client> --output <fresh-external-folder>` repairs
P > Account using the native list and existing BM descriptions. The stock Details renderer builds text from retail
bonus fields rather than the installed long descriptions. Those retail fields are
intentionally absent because Aetherfall applies the earned benefits on the server.
The original scrolling HTML child is restored. The native selected-row highlight is
enabled; opening the panel selects its first earned boon. Clicking another row displays
that pack's localized `desc_long`, with a title and readable sections. Hover uses its
existing short `desc` name. The list stays earned-only. Account Window shows live points,
steps, timers, locked boons and progress. The earlier static guide did not render in game
and is superseded by this selection-driven implementation.

`details_binary.py` changes only the Account list's initialization, pack insertion,
hover-field choice, local event dispatch and Details call. The name cell's existing
native user-data slot holds the localized description pointer from the native pack
array (`0x1392f48`, 40-byte entries), like the time cell holds its expiry. The short
name is at entry -8 and the long description at +0. This was checked read-only against
the four actual client rows. The earlier pack-ID bridge changed the separate Legion/
security buff branch and left this real pack branch untouched; it is corrected here.
The renderer does not assume row order or synthesize benefit values. Two checked padding
regions hold the bridge; unrelated Profile events retain the original handler.

The builder updates both base and English profile layouts, English pack strings,
the Graphics menu package and its verified restore resources, and affected inventory
records. Native DLL changes are applied equally to Graphics/cursor restore images
through the maintained compatibility helper. Plugin/signing files, Aetherfall Core
and both retail pack tables stay unchanged.
Install with the same `install.py`. In-game scrolling and appearance need owner acceptance.

Permanent boon rows now show **Permanent**, with no expiry warning. The pack insertion
path translates the native zero duration to a negative permanent date whose magnitude
keeps the row in the existing Account refresh loop without countdown updates. Only the
Account pack formatter is wrapped; finite pack dates and all other native timers keep
their original behavior. This is paired with the server's zero-based purchased-pack mask
and timer slots, so each name receives its own duration and the Veteran's Coffer receives
the next account-month countdown.
