# ElvUI as the reference

What ElvUI's source (github.com/tukui-org/ElvUI, `main`, read 10 Nov 2026) actually
does, and what Aetherfall Interface takes from it. This is the feature and design
target; `ARCHITECTURE.md` is the binding contract for our code. No ElvUI code is
copied: WoW's API does not exist in Aion, so every module is written against
`AFCore` and the Aion plugin widgets.

## How ElvUI is put together

| ElvUI | Where | What it does | Ours |
| --- | --- | --- | --- |
| Engine `E`, `E:NewModule`, `E:RegisterModule`, `E:InitializeModules` | `General/Initialize.lua`, `Core.lua` | Every feature is a named module with `Initialize`; the engine starts them in order and calls each safely | `AF:NewModule`, lifecycle in `core/Engine.lua` |
| Three setting scopes: `P` profile, `V` private, `G` global | `Defaults/Profile.lua` (135 KB), `Private.lua`, `Global.lua` | Profile = look and layout, shareable and switchable. Private = per character, mostly which modules are on. Global = account-wide | DB needs the same three scopes; today everything is per character |
| Defaults are data, apart from code | `Defaults/*.lua` | One nested table per module (`P.actionbar`, `P.unitframe.units.player`); about 1,700 literal settings | Each module's `defaults` |
| Templates | 227 `E:CopyTable` calls in `Profile.lua` | One bar definition copied for bars 1 to 15, one unit definition copied for target-of-target, focus-target, raid 1 to 3 | Schema templates: Bar 1 to 6 and each unit frame are instances of one definition, never six hand-written copies |
| `E:UpdateAll` and per-module `Update*` | `Core.lua` | Profile change re-applies every module in a fixed order | `OnProfile` driven by ordered phases |
| DB conversions | `E:DBConvertSL/DF/TWW` | Versioned migration of old saved settings | DB migration list plus per-module `legacy` maps |
| Movers `E:CreateMover(parent, name, text, overlay, snapOffset, postdrag, types, shouldDisable, configString)` | `General/Movers.lua` | Any module registers a mover with categories and a link to its options page; nudge, snap, reset per mover or per category | `AF.Movers:Register` must carry category and options-page link |
| Options are a separate addon, loaded on demand | `ElvUI_Options` (576 KB, one file per module) | Declarative trees (`group`, `toggle`, `range`, `select`, `color`, `input`, `execute`, with `get`/`set`/`disabled`/`hidden`/`order`); nothing is built until `/ec` | Option trees live in `options/<Module>.lua`, evaluated on first `/afui`; the window renders any tree |
| Toolkit `SetTemplate`, `Size`, `Point`, `FontTemplate` | `General/Toolkit.lua` | One place styles every frame, so a global border, backdrop or font change restyles everything | `AF.Skin` owns templates; modules never set colours or textures directly |
| Registries: `DT:RegisterDatatext`, `DT:RegisterPanel`, `E:RegisterCooldown`, `E:RegisterStatusBar` | `Modules/DataTexts`, `General/Cooldowns.lua`, `Core.lua` | Small things plug into a service: 59 data texts are 59 tiny files; cooldown text is one service every module opts into | Data texts, cooldown text and bars as registries, one file per entry |
| Unit frames from elements | `Modules/UnitFrames/Elements/*` (Health, Power, CastBar, Auras, Name, Portrait ...) | Each element has `Construct_X` and `Configure_X`; a unit is a set of elements plus settings | Same element pattern over Aion gauge widgets |
| Tags | `Tags/` (96 KB) | Text formats are strings like `[name] [health:current-percent]`, reusable anywhere | A tag formatter service; "text format" options are tag strings |
| Style filters | `Modules/Nameplates`, `Filters/` | Rules (triggers and actions) and aura allow/deny lists | Later; depends on nameplates and auras |
| `ModuleCopy`, `Distributor` | `General/ModuleCopy.lua`, `Distributor.lua` | Copy one module's settings between profiles; export and import a profile, private or global settings as a string | Profiles module |
| Install wizard, status report, tutorials | `General/Install.lua`, `StatusReport.lua` | First-run layout choice by role and resolution; a support page with versions and state | Install and About pages |
| Pixel perfect | `General/PixelPerfect.lua` | Scale so 1 px borders stay 1 px at any resolution | `AF.Skin` scale helper; needed for flat 1 px borders |

## ElvUI's modules and what each needs here

| ElvUI module | Size in ElvUI | Aion equivalent | Needs |
| --- | --- | --- | --- |
| General (media, fonts, colours, UI scale, AFK, loot, misc) | 244 settings | Theme, fonts, accent colour, scale | Lua only |
| ActionBars (bars 1-15, pet, stance, micro, extra, keybind mode) | 184 | Six quick bars, start menu ("micro bar"), pet/spirit bar | Mostly Lua over existing bridge; fading and keybind text need checks |
| UnitFrames (player, target, focus, pet, party, raid, arena, boss; 30 elements) | 249 before templates expand | Player, target, target of target, party, alliance | Own frames from gauge widgets; in-game proof of plugin gauges first |
| NamePlates (elements, style filters) | 191 | Overhead name and HP display | Drawn by the 3D engine; feasibility unknown |
| Auras (buff and debuff headers) | small in defaults, large in options | Buff and debuff rows with timers | Aura data exists in `AFCore.Aura`; own icon rows need icon drawing proof |
| Cooldown text | service | Numbers on skill and aura icons | Slot gauge values exist in `AFCore.Slot`; one overlay window of labels |
| DataTexts (59 sources, custom panels) | 30 plus per-text | Top bar and chat line today (10 sources) | Lua only; registry and custom panels |
| DataBars (XP, reputation, honour, threat) | 26 | XP, AP, DP bars | Gauge widgets |
| Chat (panels, tabs, fonts, history, filters, timestamps) | 84 | Chat window styling | Mostly native; what a plugin can change is unknown |
| Maps (minimap, world map) | in General | Minimap size, shape, buttons, coordinates | Partly there |
| Tooltip | 386 with nested tables | Anchor, offsets, content lines, health bar | Anchoring there; content needs bridge work |
| Bags (all-in-one bag, sorting, bag bar) | 294 | Inventory window | Native window; replacing it is a large separate build |
| Skins (118 window skins) | per window | Flat skin for native Aion windows | Texture replacement per window; large |
| Layout (chat panels, top and bottom panels) | part of General | Data panels | Lua only |
| Blizzard / Misc (quest tracker, alerts, loot roll, raid utility) | varied | Quest tracker, notifications, loot roll | Case by case |

Counts are literal settings in `Defaults/Profile.lua` before `CopyTable` expands templates.

## Implemented contract extensions

The 1.4.0 unit-frame slice was compared with ElvUI revision
`06c6957cccd4aed5f0cfffa99e1ddb5698790e8e`:
[profile defaults](https://github.com/tukui-org/ElvUI/blob/06c6957cccd4aed5f0cfffa99e1ddb5698790e8e/ElvUI/Game/Shared/Defaults/Profile.lua),
[Health](https://github.com/tukui-org/ElvUI/blob/06c6957cccd4aed5f0cfffa99e1ddb5698790e8e/ElvUI/Game/Shared/Modules/UnitFrames/Elements/Health.lua),
and [Power](https://github.com/tukui-org/ElvUI/blob/06c6957cccd4aed5f0cfffa99e1ddb5698790e8e/ElvUI/Game/Shared/Modules/UnitFrames/Elements/Power.lua).
The reference separates construction/configuration, tagged text, bar backgrounds,
colours and shared unit templates. Aetherfall implements these concepts over
pooled plugin images/statics and native Aion geometry; it does not copy WoW code,
oUF, secure buttons or the donor's separate power movers. Native Aion frames
retain targeting and menus. See `UNITFRAMES.md` for the supported data and
rendering limits and the exact native visual ownership table.

The six extension points are described in `ARCHITECTURE.md`. The current
implementation and source-only verification handoff are in `ELVUI-IMPLEMENTATION.md`.

1. Three setting scopes (profile, character, account) in DB, declared per key.
2. Schema templates and instances, so repeated things are defined once.
3. Option trees in separate `options/` files, built only when the window opens.
4. Service registries (data texts, cooldown text, status bars, tags) as Core-level
   extension points, each entry its own small file.
5. Movers carry a category and a link to their options page.
6. All styling through `AF.Skin` templates, pixel-exact at any UI scale.
