# Central Market HUD shortcut

The Central Market shortcut sits immediately left of the HUD Shop icon. Its generated silver, gold and blue scales artwork has normal, hover and pressed states. The old Central Market Additional Functions entry is removed; the original authenticated `/privatewarehouse` opening function remains.

The patch adds `central_market_button` to both native HUD layouts and the active English locale. Two checked native hooks handle placement after the HUD update and consume clicks only on this named button. Placement uses the current native Shop container and UI scale. Existing menu, browser, speech, graphics and cursor hooks remain intact.

`build_package.py` stages from the installed client into `output/market-shortcut/package`. It composes graphics/cursor recovery baselines and speech installation/removal records so removing those patches retains the Market shortcut. It preserves stock `Pub.key` and signs the existing three addon packages with a fresh ephemeral key. `install.ps1` requires a closed client, checks all prepared/live hashes, backs up every changed file, and rolls back if copying fails.

Validated with `verify_native.py` and `verify_package.py`: executable hooks and compiled callback, passthrough of other clicks, original Market dispatcher, layout at four scales, unchanged stock archive entries, composed launcher/recovery hashes, real installer against a disposable client, and rejection of later changes. Installed 2026-10-02; in-game appearance and click acceptance await reopening the game.

Artwork: `assets/scales.png`; exact built-in generation prompt: `assets/prompts.md`.

## HUD revision 2

`stage_revision.py` stages into `output/market-shortcut/revision-2`. The outlined scales and gift-box icons have normal, hover and pressed states. Market's tooltip uses the new English `STR_PRIVATE_CENTRAL_MARKET_HUD` localization entry; the stock Shop tooltip is `STR_WINDOW_INGAMESHOP` (Black Cloud Marketplace). All three native Shop variants dispatch `/privatecashshop` to the existing embedded authenticated browser. The Cash Shop Additional Functions entry is removed. The window title is restored to the English client's original name, Black Cloud Marketplace.

Game.dll and all existing native hooks remain unchanged. The revision updates the loaded Market extension, native UI archives, plugin title/menu registration, signatures, locale tracking and recovery receipts. Tests accept an optional package path: `python client-mods/market-shortcut/verify_native.py output/market-shortcut/revision-2` and `python client-mods/market-shortcut/verify_package.py output/market-shortcut/revision-2`. Both passed before installation. In-game appearance and hover/click verification still require reopening Aion after installation.

Revision artwork: `assets/scales-outlined.png`, `assets/shop-outlined.png`; both edited with built-in imagegen using the prompts below.

Revision 2 installed on 2026-10-02. All 24 installed file hashes, addon signatures, stock model key, unchanged Game.dll, and graphics/cursor launcher guards passed. Recovery backup: `MarketShortcut-backups/20261002-014255-123` in the client directory. Reopening the game is still required for visual and interaction acceptance.
