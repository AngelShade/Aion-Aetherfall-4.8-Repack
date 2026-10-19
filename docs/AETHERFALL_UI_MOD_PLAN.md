# Aetherfall Interface — complete client UI addon plan

Status: built and installed on the original client, 19 October 2026 (state: on).
In-game acceptance pending. See "As built" at the end for deviations.
Style authority: `docs/AETHERFALL_UI_STYLE.md`. Delivery target: the original
development client; repacks only on explicit request.

## Goal

One complete, switchable addon — **Aetherfall Interface** — that restyles the whole
Aion 4.8 interface in the Aetherfall look (Aion shapes, abyss navy fills,
ice-silver metal, aether-cyan interaction, gold trim) the way ElvUI restyles WoW.
It ships as a single finished mod covering every window family listed below, not
as a series of partial skins. Turning it off returns the exact current interface,
including all other installed mods.

Performance is a hard requirement: the addon must not add draw calls, textures,
VRAM, script work or load time.

## What the client allows (read-only findings, 19 October)

| Layer | Where | Facts |
| --- | --- | --- |
| Skin library | `ui/ui_preload.xml` in `Data/ui/ui.pak` and English override `L10N/enu/data/data.pak` | 2,096 skins, 553 presets, 57 categories, 72 texture sheets. Each skin is a named rectangle on a sheet; presets bind button/tab states (up/over/down/selected). |
| Skin sheets | `Textures/ui/ui.pak` (92 DDS) | Core art is `v5_common`, `v5_common_1…5` (1024×1024), `v5_hud_s1/s2`, `v5_housing`, `v5_l10n` (1024×512), `v5_map`, `v5_indun` (1024×1024), `v5_sign`, `v5_key`. Stored as uncompressed 32-bit BGRA, no mipmaps. `v5_common` alone backs 556 skins. |
| Window layouts | `Data/ui/game/game.pak` (270 dialog XMLs), `Data/ui/game_hud_s1`/`s2` paks, English copies in `data.pak` | Binary XML; geometry, anchors, skin references and font colours. Already modified by Wardrobe, Warehouse, inventory, Journey and shortcut mods. |
| Loose textures | `Textures/ui/<folder>/*.dds` | Existing mods already load their own art this way (`WardrobeNative`, `Aetherfall/aetherfall_logo.dds`). |
| Native addon UI | `Plugin/RelicCalc/RelicCalc.pak` + `PrivateMenus.lua` | Signed with `Addon.key`; Lua is available only through this plugin. |
| Signing | `bin32`, `func_pet`, RelicCalc archives | The UI archives above are **not** in the signature-checked set, so a skin does not require re-signing or touching `Game.dll`/`CrySystem.dll`. |
| Tooling | `client-mods/expanded-warehouse/codec.py` | Existing standard-library PAK and binary-XML codec with round-trip encode. |

## Addon contents

### 1. Skin sheets (the bulk of the look)

Every skin rectangle is classified by its library category and name into a **role**
(window frame, title bar, panel fill, button states, tab states, slot, gauge,
scrollbar, combo/editbox, tooltip, list row, separator, HUD frame, minimap ring,
chat frame, quickbar, number glyphs …). A role map (`roles.json`) assigns each role a
colour transform built from the style palette:

- **Fills** → abyss/deep/surface navy, keeping the original texture's luminance detail.
- **Metal edges and frames** → ice-silver with gold-deep trim on title bars and dividers.
- **Over/selected states** → aether-cyan rim; **down** states darker navy.
- **Primary/confirm buttons** → gold bevel, matching web `.primary`.
- **Gauges**: HP stays red-family, MP blue, DP/XP retinted to aether; cast bar aether.

Transforms work per rectangle with the original alpha, so shapes, corners and
nine-slice borders stay exactly where Aion expects them. Rectangles not mapped to a
role are left byte-identical.

**Never altered (exclusion list):** item/skill/buff icons, class and race art,
faction emblems, world maps and radar masks, cursor textures, login-stage scenery,
`v5_l10n` text glyphs, number fonts, legion emblems, and any rectangle another mod
owns (`companion_journey_shortcuts`, `season_ticket`, `mkt_scales`, `WardrobeNative`).

### 2. Hand-authored pieces

Drawn at the exact size of the rectangles they replace: ✦ crest corner ornaments for
main window title bars, gold divider strips, the Aetherfall target/player frame
plates, quickbar slot frame, minimap ring, and tooltip header band. Sources are kept
in `client-mods/aetherfall-ui/assets/` (PNG plus generation notes), like the existing
login-branding assets.

### 3. Text colour theme

Rewrite only colour attributes in dialog/HUD XML (base and English copies): window
titles → title/gold-hi, body → ice, secondary → mist, disabled → dim, hyperlinks
and selection → aether. Item-grade colours, chat channel colours and system message
colours stay native. No font, size or geometry changes in this layer.

### 4. Aetherfall layout preset (ElvUI-style arrangement)

A separate sub-option of the same addon: default positions/anchors for the HUD
(player/target frames, party list, quickbars, chat, minimap, buff rows, cast bar)
arranged as a compact bottom-centred layout. Only the default anchors in the HUD XML
change; players can still move windows and Aion's saved positions keep precedence.
Validated at 1024×768, 1366×768, 1920×1080, 2560×1440 and 3440×1440 against
overlap and off-screen anchors.

### 5. Project windows

The Aetherfall-owned windows are brought onto the same palette in the same release:
native Wardrobe surfaces (`native_wardrobe_theme.py` colours), Warehouse/Cash Shop/
Journey browser frames, PlayerBotBar, HUD shortcut art, and the Season Pass and Cash
Shop web pages (Central Market is already done). Their own toggles and behaviour are
unchanged.

### 6. Login and character selection

Frame/button skins on `v3_skin_login`, `v3_skin_create` and lobby sheets follow the
theme; the existing Aetherfall login branding, scenery and character art stay as
installed.

## On/off design

**Launcher control.** A new "Aetherfall interface" section in the launcher's
settings with one master switch and three sub-switches: *Skin*, *Text colours*,
*Layout preset*. The switch applies before Play, only when no Aion process for this
client is running (all multi-client instances closed); otherwise it waits and says why.
A client restart is always required — Aion loads UI archives at startup.

**Mechanism (chosen in phase 0, preferred first):**

1. *Loose override* — if the client prefers `Textures/ui/<sheet>.dds` and loose XML
   over archive entries, the addon is a set of loose files the launcher adds or
   removes. No archive is ever rewritten.
2. *Composed archive pair* — otherwise the builder produces, from the **currently
   installed** archives, an Aetherfall-composed copy of each affected archive. The
   launcher swaps between the installed original and the composed copy with hash
   checks. All entries outside the addon's scope are verified byte-identical.

**Staleness guard.** The manifest records the hash of every base archive the addon
was composed from. If another mod later changes one of them, the launcher refuses to
apply the older composed copy (never overwriting newer content) and asks for a
rebuild; switching *off* always remains possible.

Files live in `Mods/AetherfallUI/` with `mod.json` (`Title`, `Description`,
`Listed`, `Files`) so it appears in Installed Mods, plus `manifest.json` (sources,
outputs, hashes, sub-switch state).

## Performance rules

- Replacement sheets keep identical dimensions, pixel format, mip count and file
  size; no new sheets for the core UI. VRAM and load time are unchanged.
- No Lua, timers or hooks for theming; the Plugin archive and DLLs are untouched.
- Colour-attribute changes cost nothing at runtime; the layout preset changes
  anchors only.
- Hand-authored pieces reuse existing rectangles; no extra draw calls.
- Validated under both Direct3D 9 and DXVK/Vulkan, with ReShade present.

## Build pipeline

`client-mods/aetherfall-ui/` (source only; generated output goes to the external
workspace):

- `palette.json` — tokens copied from the style doc.
- `roles.json` — category/skin-name patterns → role; `exclusions.json`.
- `build.py --client <root> --output <fresh external stage>` — reads the current
  installed archives, extracts the skin library and sheets, applies role transforms
  and hand art, rewrites colour attributes, applies the optional layout preset, and
  writes either loose files or composed archives plus `manifest.json`.
- `verify.py --stage <stage>` — XML/PAK round trips; every untouched entry and
  rectangle byte-identical; replaced sheets keep size/format; both skin libraries
  agree; before/after contact sheets per category (PNG) for review.
- `install.py` / `restore.py` — client must be closed; backups and receipt in the
  external workspace; copied-file hashes; rollback on failure.
- Launcher change in `client-mods/launcher` for the switch section, built with the
  normal launcher builder.

## Work order (one release)

All of these complete before the addon is delivered; there is no partial install.

0. **Research gate:** confirm loose-override precedence for sheets and XML, map
   sheet ownership by other mods, confirm the skin library is read from the English
   override, measure startup/VRAM baseline.
1. Role map and exclusions for all 2,096 skins (generated, then reviewed per
   category with contact sheets).
2. Colour transforms and hand-authored pieces; full contact-sheet review.
3. Text colour theme across dialog and HUD XML.
4. Layout preset with multi-resolution geometry checks.
5. Project windows and web pages on the same palette.
6. Launcher switch, manifest and staleness guard.
7. Build, verify, back up and install on the original client; update
   `docs/INSTALLED_MODS.*` and `project-notes/current-state.md`.

## Acceptance (user, in game)

Switch on and restart, then check: character select, HUD (player/target/party,
quickbars, cast bar, buffs, minimap, chat), inventory and warehouses, character
sheet and stigma, skills, quest journal, map, mail, trade, broker windows, crafting,
tooltips, system menu and options, Wardrobe, PlayerBotBar, market/pass/journey
shortcuts. Switch off and restart: the interface must match the current client.
Repeat once with DXVK.

## Risks and open questions

- Loose-file precedence may not apply to archived UI sheets; then the composed
  archive pair is required and must be rebuilt after other UI mods change.
- Some rectangles are shared between unrelated windows; contact-sheet review per
  category catches unintended retints.
- `v5_l10n` and number glyphs embed text-like art; they stay excluded.
- The layout preset can conflict with players' saved window positions; saved
  positions win by design.
- An in-game switch (instead of the launcher) would need Lua/plugin work and a
  restart anyway; not planned.

## As built (19 October 2026)

- **Mechanism:** entry-level, not whole-archive swaps. Custom Quests rewrites
  `L10N/enu/data/data.pak` at launch, so the addon stores gzip payloads per owned
  entry (39 archive entries, 15 Wardrobe files, 15 MB) and the launcher
  (`AetherfallInterface.cs`, reusing the Custom Quests archive code) rewrites only
  those entries. Loose-override precedence was not needed or tested.
- **Graphics menu:** it tracks exact `game.pak`/`data.pak` hashes and keeps package
  copies. The switch updates those copies and installed hashes when the archives
  differ only in addon entries (approved by the owner after the first install
  stopped Play); its restore baseline is unchanged and holds the entries off.
- **Switches:** two, not three: *Aetherfall skin* (sheets, text colours, Wardrobe)
  and *Target frame above the action bars*. Text colours ship with the skin.
- **Layout preset scope:** the target frame, target-of-target and target buffs
  move from the top edge to bottom-aligned just above the action bars (reference
  y +764, no overlap with s1/s2 bars, pet frame or cast bars). Party, chat, minimap
  and buff rows keep native positions; their real-screen geometry needs in-game
  checks before moving them.
- **Hand-authored pieces:** gold top rule / gold-deep bottom rule on every window
  frame (aether rule on tooltips), drawn into existing rectangles. No new art sheets.
- **Web pages:** Season Pass and Cash Shop already follow the palette; the Cash
  Shop's asset version is compiled into the GameServer JAR, so its CSS was left
  unchanged. Central Market was reworked separately.
- **Verification:** builder applies every state to copies and checks untouched
  entries byte-identical; launcher switch on/layout/on/off on a scratch copy restored
  every entry exactly; Custom Quests re-verified on the installed client with no
  writes. Not verified: in-game rendering, Direct3D/DXVK, the launcher settings UI.
