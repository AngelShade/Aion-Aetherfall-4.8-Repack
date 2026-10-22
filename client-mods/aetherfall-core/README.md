# Aetherfall Core (Lua bridge)

`bin64/AetherfallCore.dll` extends the Aion 4.8 client's Lua API for the
Aetherfall Interface. It reads native HUD data and applies the interface's window
positions, visibility and tooltip settings. Settings remain in `Mods/AetherfallUI`.
Existing chat commands run through the native chat input.

## Target movers, Lua 1.2.39

Target, target-of-target, target aura and cast movers hold the current draft
position throughout dragging. Native instances created or refreshed while the
mouse is down are held immediately. The draft is published before native
placement can raise an event, preventing a nested hold pass from restoring the
previous/default anchor. Failed placement preserves the prior saved position.
Save / Lock and Cancel retain their existing behavior and profile format.
Target aura canvases keep their native zero size and use their visible child
bounds for the mover. Native Core 1.2.38, layouts and editor controls are retained.

## MoveUI editor, Lua 1.2.37

`/moveui` opens a native control panel with **Save / Lock** and **Cancel**.
Positions and editor preferences are a draft until saved; autosave does not write
the draft. Cancel restores the positions and settings from when the editor opened.
A failed verified save keeps the editor open. `/moveui` again still saves and locks.

The panel header can be dragged out of the way. Controls include a visible grid
(16-128 UI-pixel spacing), screen center guides, independent grid snapping step,
sticky frame edges/centers, previous/next frame selection, category filters and
temporary box hiding. Hiding a box does not hide the game window. Show all boxes
restores editor visibility. Thin native bars keep their exact hit boxes.

Select a frame to type X/Y, nudge by one physical pixel (Shift: ten), center either
axis or reset it. The nine **From** choices change the coordinate reference only;
they do not create a persistent link between frames. Setup saves/locks and opens
the related settings page. Reset all requires a second click, and Cancel can undo it.
Save before travelling/reconnecting: world entry and disconnect cancel an unfinished
draft. Alt+Tab retains it while cancelling an active drag.

The bounded grid uses whole multiples of the chosen spacing on large viewports;
the panel displays the actual spacing. Guide lines are passive image children of
the existing overlay, keeping Aetherfall at five plugin dialogs. Positions retain
the existing per-character/profile format. Native Core remains 1.2.34.

Build with `prepare.py --update --plugin-only --mover-editor-update`. This replaces
only the installed AFUIMovers dialog plus the maintained Lua, retains the other
dialog bytes and plugin members, and stages one small loose grid-line texture.
The normal signer and guarded installer deliver the affected cohort to the original.
Actual native appearance, mouse/keyboard behavior and transitions need owner acceptance.

## Action-bar controls, Lua 1.2.36

Bars 1-4 use one geometry calculation for button rows, container/window size and
the mover box. Resizing respects saved positions and keeps the full bar on screen.
On a viewport too small for the requested row count, the bar wraps into enough
columns to fit without shrinking buttons or dropping slots. The row value shows
its effective fit (for example `1 (2 fit)`), and the requested preference is kept
for a larger viewport. Open mover boxes update in place without losing selection.

The Bars 5 and 6 switch covers both separated windows after checking their layout
name. Enabling it restores only separated bars the controller actually hid;
natively inactive bars stay inactive. Conditional unit frames and always-present
HUD switches retain their existing behavior. Restoration history is cleared on
character changes.

## Profile settings, Lua 1.2.35

Switching profiles stores the outgoing profile's current settings and restores the
incoming profile's settings together with its existing mover positions. A profile
without a saved settings snapshot starts from its own defaults/preset; older partial
snapshots cannot inherit values from the previous profile. Applying a preset also
preserves the outgoing profile's settings. User1/2/3 Save still copies settings and
positions explicitly. Existing alternating per-character files retain both active
settings and inactive snapshots. Native Core remains 1.2.34 in a plugin-only update.

## Tooltip correction, 1.2.26 (21 October 2026)

**1.2.33 reconnect and focus recovery:** an empty/nil player name suspends native
placement, releases old pins, closes editors through queued widgets, and retains
the previous character's settings. The timer detects the ready character later,
even if world entry arrived too early or no second event arrives; same-character
reconnects restore the saved layout and character switches load separate profiles.
Editor commands wait for a connected, stable foreground viewport.

Focus loss cancels active mover drags and pauses HUD/browser geometry changes.
Focus regain invalidates placement/observation caches and starts a fresh 20-tick
settling period, including at unchanged screen size, so temporary native positions
cannot be adopted as user dragging. The open mover editor is refreshed after
settling and needs a fresh mouse press. HUD geometry caches are invalidated too.
The existing shared timer continues; no extra timer or native DLL changes.
`/afcore` reports connected/focused/settled state. External checks reproduce the
old empty-name and focus-adoption bugs and cover 40 focus cycles and 32 reconnects
across four scales. Real AFK reconnect/Alt+Tab remains owner acceptance.

**1.2.32 teleport timer recovery:** two owner reports showed unchanged Options
ticks (11974) several minutes apart and HUD ticks zero after world entry, with
no Lua/pin/suspension errors. RelicCalc's shared one-shot timer was no longer
dispatching. Its existing Wardrobe owner now replaces the pending timer chain
on `PLAYER_ENTERING_WORLD`, schedules the next tick before processing UI work,
and reports/catches timer-body errors so consumers continue. The cadence remains
0.05 seconds with one owner and one pending chain. Explicit `/afui`/`/moveui`
requests can ask that owner to resume a lost timer. `/afcore` reports owner
ticks/resumes/errors. No profile positions are reset by this repair.

Normal delivery uses `prepare.py --update --plugin-only --shared-timer-update`;
the explicit timer option includes maintained `transmog-menu/WardrobeNative.lua`
and records its source hash while retaining all generated layouts and other
archive members. External evidence: `diagnostics/aetherfall-teleport-timer-20261021/`.
An actual Lua 5.1 one-shot scheduler reproduces the old frozen counters, then
checks twelve transitions at each of four scales, unchanged menu positions,
single-chain cadence, owner errors and explicit command recovery. Existing
native Wardrobe Lua checks also pass. Real teleports remain owner acceptance.

**1.2.31 editor-opening correction:** the mover and options plugin windows use
only the client's queued `SetWidgetRect`/`SetWidgetVisible` operations. The direct
native rectangle/flag writes added in 1.2.30 were redundant and bypassed the
queue (native widget command types 6/12). Native visibility is still read to
recover a stale editor toggle. All geometry/persistence/world recovery repairs
remain. The 21 October immediate `/moveui` crash was a null command-vtable call
in DXVK's command-stream worker (`d3d9.dll+0x1452d0`); the dump lacks that command's
heap data, so it does not establish which UI operation corrupted it. This repair
removes the verified queue bypass; absence of another crash needs owner testing.

**1.2.30 shared mover and world recovery repair:** movers use actual native
bounds; Target Auras uses the union of populated itemviews and moves its native
zero-sized canvas without changing its size. Verified HUD movers are offered
before their native windows are created. Other windows require the native
movable flag. Locking releases ordinary window pins; reopening restores their
saved position once, and subsequent title-bar dragging updates that position.
Settings verify the write before reporting success, keep the editor open on
failure, bind saved positions to native names, and copy positions into user
profiles. The final mouse-release position is committed.

The HUD XML now dispatches one captured event independently to both Lua
controllers. Nested visibility callbacks cannot recurse through the placement
pass. Commands rebind after UI reloads; world/zone transitions and HUD suspension
invalidate stale placement caches, preserve profiles and restore previously
visible HUD windows after the viewport settles. Initial activation also restores
the layout's minimap if the prior session left it hidden. Hidden editor toggles recover
from native visibility. These changes target the reported cinematic/minimap/menu
failure; actual in-game acceptance remains pending.

Use `prepare.py --update --retain-layout` to build both the bridge and signed
Lua cohort while preserving all installed layout geometry, widgets and load
order. Only the known HUD event-script text changes in the retained XML.
Evidence: external `diagnostics/aetherfall-movers-20261021/`.

**1.2.29 XP mover follow-up:** the Experience Bar mover uses the native bar's
actual dimensions, including its 5 px reference height. It no longer inherits the
40 px fallback or generic 20 px minimum, so dragging, screen-edge clamping and
frame snapping use the visible bar's bounds. Hover identifies the thin strip;
clicking it selects the existing exact-position/arrows panel. Profiles retained.

**1.2.28 data-bar follow-up:** the top bar removes line endings and tabs from native
data strings (the native clock includes a newline), and its three text groups use
separate columns sized to the current screen instead of overlapping fixed widths.
Selected fields and their full text remain unchanged. The focused Lua 5.1 widget
check reproduces both prior causes and passes 120 screen/scale/clock scenarios.
Use `prepare.py --update --plugin-only` for this Lua-only repair: the builder keeps
the installed generated layout/load order and native bridge, preserves other PAK
members, and signs the normal five-file plugin cohort.

**1.2.27 comparison follow-up:** hovering equipment now anchors the primary item
and its one or two equipped-item comparisons as a group. The native comparison
slots (`0x1ec..0x1ee`), sizes, horizontal gap (`5 * UI scale`) and vertical inset
(`20 * UI scale`) are retained. Columns move to the other side or shift together
to fit the screen. Native and hidden modes keep their existing behavior. The
focused native fixture now passes 333 checks, including repeated native placement
and changing comparison sizes. Owner confirmed the earlier UI tooltip repair;
actual comparison appearance still requires in-game acceptance.

The native `tooltip_proto` windows are inner layout templates. Visible tooltips
are temporary `DlgTooltip*` windows, normally registered in slots `0x1ec..0x1ee`.
Moving the templates both missed the outer window and changed the internal layout.

The bridge now applies the existing world/UI policies to the outer window's first
native SetRect, using 54 explicitly identified tooltip classes. Runtime RTTI and
original-function guards protect every replaced class entry; prototypes and other
pop-ups are excluded. The native item/text layout and tooltip sizes remain intact.
Disabling the interface, releasing the tooltip policy, or selecting Native for both
kinds restores the original class entries. Click-through menu gaps count as world.

This repair uses the existing normal builder's `--dll-only` mode:

```
python client-mods/aetherfall-core/prepare.py --client <original client> --output <fresh external stage> --dll-only --client-mods <maintained client-mods folder>
```

The 1.2.25 core source was recovered unchanged from the source directory recorded
by that installed build, then preserved here before repair. Existing generated HUD
layout and signed Lua remain installed. A full HUD rebuild also needs the matching
native UI layout builder (`aetherfall-ui/hud_native.py`), which is outside this repair.

Offline evidence: external `diagnostics/aetherfall-tooltip-20261021/`.
The focused native fixture passes 210 checks without accessing a game process.
Actual cursor following and absence of visible flicker still require the owner's
in-game acceptance after installation.

## Loading and registration

`patch_binary.py` appends one import (`AetherfallCore.dll!AetherfallCoreStatus`) to
`Game.dll`, the same way the loot-roll bridge does. It changes no native code, and only
six PE header bytes differ. At load, the DLL renames the exact duplicate
`UnregisterPredefinedQuestIndicator` row (RVA `0xbad1e0`) of the writable
`luaL_Reg` table at `0xbacd60` to `AetherfallCore`. The first row keeps the native
function. That table is registered in both the UI and the plugin Lua states. Any
unexpected byte means the DLL never writes the row.

Game.dll is usually mapped away from its preferred base. Its packer stub applies
the pointer relocations itself, when Game.dll's entry point runs after this
DllMain. A short-lived thread therefore waits until the row holds its relocated
value, then renames it; this is long before the UI creates its Lua states.
Writing earlier would let the relocation pass corrupt the new pointers, and
version 1.0.0 skipped registration for this reason. `AetherfallCoreStatus()` returns 0
while waiting, 1 when registered and 2 after a two-minute timeout. The exported
`AetherfallCoreDiagnostics[4]` (state, Game.dll base, polls, last row pointer) can be
read externally.

## Lua API (`AetherfallCore(command, ...)`; wrappers in `AetherfallCore.lua` as `AFCore.*`)

| Command | Returns |
| --- | --- |
| `version` | protocol 1, actual native build string (e.g. `AetherfallCore 1.2.34`) |
| `money` | Kinah |
| `exp` | current, needed for level, recoverable, repose current, repose max |
| `fps` | engine frame rate |
| `auras`, unit | count (`player`, `target`, `targettarget`) |
| `aura`, unit, i | skill id, name, level, slot (0 buff, 1 debuff, 2 chant, 3/4 special, 5 boost), remaining s, total s, raw time, first field, time mode |
| `unit`, unit | object id, name, level, hp, max hp, mp, max mp (`target`, `targettarget`) |
| `skill`, id | localized skill name |
| `slot`, dialog, i[, prefix] | native itemview: type, template id, id, count, gauge value, gauge max, shown |
| `gauge`, dialog, widget | value, max, shown (for example the native cast bars' `delay`) |
| `text`, dialog, widget | static widget text |
| `rect`, dialog[, widget, kind] | absolute screen rectangle in pixels, shown |
| `scale` | engine UI scale, screen width, screen height |
| `shown`, dialog | dialog visibility |

Dialog ids are listed in `AFCore.DIALOG`. Since 1.2.34, aura time mode 2 is the
native effect's remaining milliseconds, read without a history/uptime estimate.
Mode 0 is untimed (remaining nil); original total duration is always nil because
this record does not expose it. `NumAuras` returns nil for unavailable bridge/unit
data and 0 for an available empty list; loops should use `NumAuras(unit) or 0`.
`/afcore` prints unavailable explicitly and identifies the actual native build.

`Rect` uses the same parent-to-screen conversion as native widgets, including
nested containers and native offsets. `Rect`, `Slot` and `Gauge` include ancestor
and owning-dialog visibility. Exact client-code comparisons cover direct/nested
children and the native 0x40 coordinate branch. Native render caches are not changed.

## HUD data layer

`AetherfallHUD.lua` and the generated `AetherfallHUD.xml` (inside `RelicCalc.pak`) add
the top data bar and a data line under chat. Scripted aura timer labels remain
disabled to fit the shared plugin-window budget; the game retains its native buff timers. They run only while the Aetherfall Interface `layout` state is applied
(`Mods/AetherfallUI/state.json`). The labels are small click-through dialogs placed
beside native widgets. Cooldown numbers on slots are native: game option *Show cooldown
time* (`show_cooltime`).

## Build, verify, install

The Lua package and native bridge have independent versions. The verifier needs
Lupa with Lua 5.1 in the verification environment; `AION_LUA_TEST_DEPS` can name an
external dependency folder.

```
python client-mods/aetherfall-core/prepare.py --client <original client> --output <fresh external stage>
python client-mods/aetherfall-core/verify_hud_lua.py --client <original client>   (run from a scratch folder)
python client-mods/aetherfall-core/install.py --stage <stage> --client <original client> --backup <fresh external backup>
```

`prepare.py` builds the DLL with MSVC. It patches `Game.dll` in the active copy, in the
Graphics menu package and baseline, and in the native-cursor baseline, and updates their
records. It adds the Lua/XML to `RelicCalc.pak` and signs the plugin cohort with the
normal signer; every other archive member stays byte-identical. `install.py` requires a
successful full CIM process check, backs up every target, verifies copied hashes, then
runs the Graphics menu `-VerifyOnly` check, `--custom-quests-sync` and
`--aetherfall-interface`, and writes a receipt. Install the core before switching the
interface to `layout`.
