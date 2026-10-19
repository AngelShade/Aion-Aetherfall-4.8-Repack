# Aetherfall UI: ElvUI feature map for Aion 4.8

Date: 19 October 2026. Goal: an ElvUI-equivalent interface addon for the Aion 4.8
client, in the Aetherfall style (`docs/AETHERFALL_UI_STYLE.md`). This document maps
every ElvUI module to what the Aion client can support and how.

## Implementation corrections and delivery (19 October 2026)

The map below is the original research brief, copied unchanged from
`central-market-visual-rework-196cf3` into `4.8`. The following findings supersede
its optimistic API assumptions. Maintained implementation: `client-mods/aetherfall-ui`.

**Status:** the Lua overlay built from these findings was rejected by the owner and
removed from the client on 19 October (receipt `archives/client/aetherfall-hud-removal-20261019`).
Its findings still apply to the bridge and native rewrite in the Direction section.

- `SaveValue` (Game.dll RVA `0x60be00`) and `LoadValue` (`0x60c2c0`) accept a
  fixed social-settings whitelist. They do **not** support arbitrary addon keys.
  The HUD uses bounded scalar files through Lua `io`, two complete-generation
  slots and the client's `sha1.sha1` module for per-character filenames.
- `SKILL_CAST`, `SKILL_SUCCEED`, `SKILL_CANCEL` producers at `0x2cda44`,
  `0x2cdf4b`, `0x2cdd0b` supply two strings: `player` and skill name. No cast
  duration is supplied. The HUD shows elapsed activity, not a completion estimate.
- Damage producers at `0x12369f` / `0x123a19` provide unit/amount or skill/amount;
  the skill event does not supply reliable source or healing attribution.
  DPS/HPS are therefore marked unavailable. The timer measures observed damage
  activity only. Legion count is a member count, not an asserted online count.
- The native unit resolver (`0x618d40`) recognizes `player`, `target`, `summon`
  and `party1` through `party6`. Five other members are shown, excluding self.
  `GetPartyMember` returns a presence value, not a usable unit token. Use tokens
  directly. `GetPlayerMapPosition` requires `player` and returns three numbers.
- Steps 1-5 are installed with these explicit limits: first-run setup/options,
  per-character presets, saved/snap movers, unit frames, supported data panels
  and elapsed cast strip. Step 6 (native HUD replacement and new native layouts)
  remains outside this delivery; the existing skin and native HUD are preserved.

Original installation receipt: external `archives/client/aetherfall-hud-20261019/receipt.json`.
Build: `staging/output/aetherfall-hud-20261019-final/manifest.json`.
Evidence: `diagnostics/aetherfall-ui-plugin-20261019/api-evidence.json` and
`lua-validation.json`. Real client Lua widget checks passed at four scales;
five copied hashes and three signatures passed. Graphics, Custom Quests and
interface ownership/loader checks passed. No native binaries/server/repacks
changed. Appearance, actual native data/targeting, file writes and relog persistence,
cast behavior, summoned pet and Additional Functions remain owner acceptance.

## What ElvUI is (tukui-org/ElvUI, modules and options)

ElvUI is a full WoW UI replacement. Core modules: **ActionBars, Auras, Bags, Blizzard
(frame improvements), Chat, DataBars, DataTexts, Maps, Misc, Nameplates, Skins,
Tooltip, UnitFrames**, plus a core with **movers** (unlock and drag every frame,
saved positions), **profiles/layouts** (DPS/tank/healer), an install wizard and an
in-game options window (`/ec`).

- **General:** UI scale, pixel-perfect thin borders, textures, backdrop/border colours,
  fonts (global, combat, names), top/bottom panels, AFK screen, chat bubbles, auto
  repair/accept invites, loot frame and loot roll, item-level info, totem tracker,
  custom class colours.
- **UnitFrames:** player, target, target-of-target, focus, pet (+ targets), party,
  raid, boss, arena, tank/assist. Per frame: size, health (+ prediction, cutaway),
  power, cast bar, portrait, name/custom text tags, info panel, buffs/debuffs/aura
  bars, class bar, icons (role, raid marker, resurrect, ready check, PvP), range
  fade, threat glow, fader, colours.
- **ActionBars:** up to 15 bars + pet/stance/micro/totem: buttons, per-row, size,
  spacing, backdrop, mouseover/fade, visibility/paging, keybind/count/macro text,
  cooldown text, colours for range/power.
- **Auras:** buff/debuff headers: size, growth, sort, wrap, durations, filters.
- **Bags:** all-in-one bag/bank, sorting, item level, junk, search, bag bar,
  vendor greys.
- **Chat:** panels with backdrops, tab styles, URL copy, timestamps, history,
  class-colour mentions, spam/combat repeat filter, text fade, keyword alerts.
- **DataBars:** experience, reputation, honour, etc.
- **DataTexts:** ~60 info texts on panels: time, date, gold, coords, location,
  durability, bags, friends, guild, system (FPS/latency), DPS/HPS, combat time,
  stats, XP, quests, mail, volume.
- **Maps:** square/circular minimap, size/scale, zoom reset, location/time text,
  button positions; world map scale, opacity while moving, coordinates.
- **Tooltip:** cursor anchor, health bar, item/spell IDs, guild ranks, target info,
  quality borders, fonts, combat visibility.
- **Nameplates, Skins:** styled nameplates; reskin of every Blizzard window.

## What the Aion 4.8 client exposes (read-only findings)

- **Plugin addons** in `Plugin/<name>/<name>.pak` with WoW-style `.toc`, `.xml`,
  `.lua` (our `RelicCalc.pak` already carries the Wardrobe, browser windows and the
  companion bar). Archives are signed with `Addon.key`; the project's
  `client-mods/transmog-menu/SignClientPackages.java` and
  `client-mods/playerbots/prepare_bar_revision.py` pattern stage and sign new
  entries; `client-mods/playerbots/verify_bar_lua.py` runs plugin Lua against the
  client's real widget wrappers with lupa.
- **Widgets** (client `bin32/bin32.pak` `lua/*.lua`): Dialog, Container,
  Button/Check/Radio, Static, StaticImage, Gauge, EditBox, ListBox, ListViewEx,
  Tab/TabSheet/TabPage, ComboBox, Spin, Scrollable, SimpleHTML, MessageWidget,
  Browser. Methods: Show/Hide, SetRect/SetPos/SetSize, SetText/SetFont/SetTooltip,
  SetValue/SetMaxValue (bars), SetUIImage/SetPreset (skins), SetAlpha,
  SetFlag/SetAttr, capture/release mouse, SetTimer. Dialogs with the `title` flag are
  draggable natively (PlayerBotBar).
- **Game data:** `UnitName/Level/Class/ClassID/Race/Gender/HP/HPMax/MP/MPMax/Ap/
  IsDead/ObjectID`, `TargetUnit`, `FollowUnit`, `AutoTargetAttackable`,
  `GetNumPartyMembers/GetPartyMember/GetPartyMemberInfo`, legion and friend lists,
  `GetZoneText/GetZoneID/GetPlayerMapPosition/GetPlayerPos`, `GetItemInfo`,
  inventory/warehouse item counts, `GetPlayerEquip`, `GetStigmaParams`,
  `GetQuestInfo`, `CastSkillByName`, `GetTime` (formatted text), `GetUIScale`,
  `GetServerName`.
- **Persistence and output:** `SaveValue/LoadValue`, `DisplayChatMessage`,
  `MessageWidgetAddMessage`, `MsgBox`, map marks, slash commands (`SlashCmdList`).
- **Events:** `ADDON_INIT/EXIT`, `ADDON_TIMER` (RelicCalc's shared 20 Hz timer,
  maintained by Wardrobe; do not change its interval), `PLAYER_ENTERING_WORLD`,
  `PLAYER_LEVEL_UP`, `PLAYER_ENTERING_INSTANCE_DUNGEON`, `USER_CHANGED_TARGET`,
  `SKILL_CAST/SKILL_SUCCEED/SKILL_CANCEL`, `UNIT_SKILL_DAMAGE/UNIT_ATTACK_DAMAGE`,
  `PARTY_MEMBERS_CHANGED`, `PARTY_INVITE_REQUEST`, `LEGION_MEMBER_UPDATE`,
  `QUEST_ADD/UPDATE/FINISH/DELETE`, `ITEM_PUSH`, `ITEM_LINK_CLICKED`, `HOT_KEY_PRESSED`,
  `SLASH_CMD`, `GAME_UI_*` mouse/button/dialog-visibility events.
- **Native windows** (player/target/party/quickbars/buffs/chat/minimap/tooltip) are
  engine dialogs defined in `Data/ui/game/game.pak` + English `L10N/enu/data/data.pak`
  (`ui/game/`, `ui/game_hud_s1|s2/`): art (skin library `ui_preload.xml`), layout,
  size, flags (movable, visible) and colours are editable; their logic is not
  scriptable. Changes ship through the Aetherfall Interface switch
  (`client-mods/aetherfall-ui`, launcher `AetherfallInterface.cs`), which keeps the
  Graphics menu record in step.
- **Not exposed to Lua (so far):** buffs/auras, action slots/cooldowns, cast durations
  of other units, nameplates, FPS/latency, experience values, Kinah balance (check
  `GetInventoryItemCount` for the Kinah item), tooltip contents.

## Direction (owner, 19 October): take over the native UI, extend the client

The Lua overlay test (installed by another session as `Aetherfall.lua/.xml` in
`RelicCalc.pak`, receipt `archives/client/aetherfall-hud-20261019`) is rejected: ElvUI
replaces the default UI with full-fidelity frames because WoW's API exposes
everything; Aion's Lua API does not, so overlays are half-working copies. The owner's
direction: rewrite the native UI itself, and where the client lacks data or features,
hook the client to expose them.

- **Native UI** is data-driven: ~330 dialog layouts (`ui/game/*.xml`, HUD
  `ui/game_hud_s1|s2/*.xml`) plus the skin library (`ui_preload.xml`: ~2,100 skins,
  ~550 presets) over UI sheets. Engine logic stays; look, layout and widget styles
  change. `client-mods/aetherfall-ui/preview.py` renders these offline from the
  client's own files, so designs are reviewed as images first.
- **Client API extension:** `bin64/Game.dll` (protected image; one RWX unnamed section
  plus the project's hook sections `.aicons/.agfx/.asb/.mkt/.rlogin/.rreturn`) holds
  standard `luaL_Reg` tables `{const char* name, lua_CFunction}`:
  RVA `0xbacd60` (108 entries: `AddNewWidget`, widget/system API, events, SaveValue...)
  and RVA `0xbad590` (27 entries: `GetZoneText` ... `UnitHP` at `0x617af0` ...
  `GetPlayerEquip`), each terminated by `{0,0}`; event names at RVA `0xe97960`;
  Lua 5.1 standard libraries are statically linked (base/string/math/table/io/debug
  tables nearby). A hook DLL (existing pattern: `bin64/version.dll` proxy and the
  icon/loot/graphics bridges) can wrap one registered function to capture
  `lua_State*`, then register new functions using the embedded Lua API. Candidates:
  auras for player/target/party, action slots and cooldowns, target cast info,
  experience, Kinah, FPS/latency, more unit tokens (target-of-target, pets).
  Each requires reverse engineering the matching engine structures.
- **Unit API internals (read-only disassembly):** `UnitHP` (`0x617af0`) and the other
  `Unit*` functions call `0x619150(lua_State*, field, int* out)` and push the result
  by writing `L->top` directly (TValue 16 bytes, type 3 = number; Lua 5.1 layout:
  `L+0x10` top, `L+0x18` base). Field ids: `UnitHP` 0, `UnitLevel` 5 (others in
  the jump tables at RVA `0x6195f0`, `0x619610`, `0x619630`). `0x618d40` classifies
  the unit token: 1 player, 2 target (record built via `0x17bd80` on
  `0x1319618`), 3..8 party slots (party block via `0x17ccb0`, records `0x87e` bytes
  apart: HP `+0x984/0x988`, MP `+0x98c/0x990`, level/class bytes `+0x9b4..0x9b6`),
  9 another unit kind. Known globals from the loot-roll bridge: player object
  `Game.dll+0x12eda48`, UI scale/size doubles `+0x1378ec8/+0x1378ea8/+0x1378eb0`.
- **Loading path for the new bridge:** like `client-mods/loot-roll/patch_binary.py`
  (appends an import of `AionLootRoll.dll` to the final section with
  `transmog-menu/patch_game_dll.Assembler`, verified original bytes, writable IATs) and
  its graphics/cursor baseline composition in `prepare.py`. The Lua tables live in a
  writable section, so the bridge can register functions at load by patching table
  entries in memory; obsolete entries (Twitter/PowerBook/TodayWords functions) can
  be repurposed, so `Game.dll` needs only the added import.

## Feature map

Legend: **Addon** = new plugin frames/logic; **Native** = restyle/relayout/movable
engine windows; **No** = not possible without engine work.

| ElvUI module | Aion implementation | Notes |
| --- | --- | --- |
| Movers (`/moveui`) | **Addon + Native** | Addon frames: drag in unlock mode, positions saved with SaveValue, grid snap, reset. Native HUD windows: `movable` flag + title bar in their layouts; engine saves positions (relog persistence to confirm). |
| Profiles/layouts, install wizard, `/ec` options | **Addon** | In-game options window (tabs per module), `/aui` and `/moveui`, presets DPS/healer/minimal, first-run wizard. Archive-level skin/layout stay in the launcher switch. |
| General: scale, colours, fonts, panels | **Addon + Native** | Addon frames follow addon scale/colours; native fonts/colours via layout XML; top/bottom data panels. |
| UnitFrames: player, target, party | **Addon** | Custom frames: name, level, class colour, HP/MP bars with text formats, dead state, click-to-target for party. Update on 20 Hz timer + events. |
| UnitFrames: target-of-target, focus, pet, raid/boss/arena | **Partial/No** | No unit tokens for these found yet; keep native target-of-target; group/alliance via native windows. |
| Cast bar | **Addon (player) + Native (target)** | Player: SKILL_CAST/SUCCEED/CANCEL events (duration from event args to confirm). Target cast bar stays native, moved/styled. |
| Auras | **Native** | Buff/debuff dialogs restyled, resized and movable. |
| ActionBars | **Native** | Quickbars restyled; layouts (rows x columns, button size) and positions via XML; extra native bars. No paging/visibility scripting. |
| Bags | **Native (existing)** | Unified inventory with search already installed; restyle only. |
| Chat | **Native + Addon** | Restyled panels; addon data panel under chat. Chat filters/history not scriptable. |
| DataBars | **Partial** | Native XP bar restyled/moved; no XP values in Lua. |
| DataTexts | **Addon** | Time, date, zone, coordinates, party size, legion online, friends online, Kinah (if exposed), item counts, combat timer, DPS/HPS from damage events, server name. |
| Maps | **Native** | Minimap ring/mask restyle (square option), position; world map coordinates via addon text. |
| Tooltip | **Native** | Skin only; anchor/contents engine-owned. |
| Misc: AFK screen, loot roll, chat bubbles | **Partial** | Loot roll and speech bubbles already exist as separate mods; AFK screen possible as addon overlay on idle timer. |
| Nameplates | **No** | Engine-drawn. |
| Skins (every window) | **Native** | Full new flat Aetherfall art for all skin roles (replaces the earlier recolour). |

## Build order (one addon, delivered complete)

1. **Core plugin** `Aetherfall` inside `RelicCalc.pak`: settings (SaveValue), event and
   timer dispatch, `/aui` + `/moveui`, mover system for addon frames.
2. **Unit frames:** player, target, party (5) with bars, texts, colours, click targeting.
3. **Data panels:** bottom/top bars with the data texts above; DPS/HPS/combat timer.
4. **Player cast bar.**
5. **Options window** with tabs (General, Unit Frames, Data Texts, Movers, Profiles) and
   first-run setup.
6. **Native layer** (interface switch): flat Aetherfall art for every skin role;
   movable HUD windows; quickbar/buff/minimap layouts; hide native player/target frames
   when the addon frames replace them.
7. Build, sign, install on the original client; in-game acceptance by the owner.

Early in-game checks needed from the owner (cannot run the client here): plugin frames
appear and update; drag + saved positions; native movable windows keep positions after
relog; `SKILL_CAST` arguments; Kinah/XP availability.
