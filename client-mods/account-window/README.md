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
