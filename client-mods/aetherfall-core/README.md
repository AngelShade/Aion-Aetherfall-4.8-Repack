# Aetherfall Core (Lua bridge)

`bin64/AetherfallCore.dll` extends the Aion 4.8 client's Lua API for the
Aetherfall Interface. It reads native HUD data and applies the interface's window
positions, visibility and tooltip settings. Settings remain in `Mods/AetherfallUI`.
Existing chat commands run through the native chat input.

## Camera controls, native 1.2.78 / Lua 1.2.80

Open `/afui` and choose **Camera**. Field of view offers 60–170 degrees in
5-degree steps; maximum zoom distance offers 5–50. Changes apply immediately;
use the mouse wheel to zoom out to the new limit. **Native** preserves the current
client value until a custom choice is made, and then restores the exact value
captured before that override. Old saves start on Native. Camera choices save per
character and remain independent of layout profiles and DPS/Healer/Minimal presets.

These are the two camera variables exposed by
[ShugoConsole](https://github.com/grenadium/ShugoConsole/blob/master/shugoconsolewidget.cpp):
`g_minFov` and `g_camMax`. The bridge uses the existing game's console-variable
lookup and verifies the current native class and local numeric/string storage.
Only these two allowlisted variables can change. FPS limits, chat logging, texture
filtering, triple buffering, console availability and input behavior are retained.
No external utility or repeated memory scan is needed. The bridge applies choices
only on an explicit edit or character entry; it does not enforce them every tick.

`camera_settings.py` checks the relevant installed Game/CrySystem ABI before the
normal builder runs, and both files become installation input guards. Build with
`prepare.py --update --retain-layout`. Actual camera appearance, mouse-wheel zoom,
loading/zone/cinematic behavior and restoration remain in-game acceptance checks.

## Quest tracker starting height, Lua 1.2.79 and layout

The stock tracker layout (`quest_indicator_dialog`) is 960 high, the full layout
height; at UI scale 1.125 on a 1080 screen it fills the screen and can only move
sideways. The game derives the height from that frame inside its own SetRect, so
the runtime size hold added in 1.2.77/1.2.78 had no effect and is removed. The
layout frame is now 830, the bottom of the main and temporary-quest lists, staged
by `../transmog-menu/prepare_quest_tracker_height.py` into the base and English
active, Graphics package and restore archives. The size-only pin mode 7 (native
1.2.77) remains available but unused.

## Tooltip policy on all tooltip classes, native 1.2.76

Backdrop and pin hooks no longer redirect a live tooltip class's SetRect slot.
A backdrop on the name-link tooltip classes had taken that slot first, so the
tooltip policy refused every class and no Tooltip option applied. A read-only
reading of the running client confirmed 0 of 54 classes before and 54 of 54 after.
Native 1.2.75 adds the right-drag camera turn (`camera_turn.h`).

## Cursor tooltips use the real mouse, native 1.2.73

Cursor-anchored tooltips, and the over-window/over-world decision, read the real
Windows cursor in UI pixels (as MoveUI dragging does). The engine's dispatcher copy
(`CURSOR_X/Y`) stopped following the mouse with the system cursor hidden, leaving
cursor tooltips at a fixed spot. It remains the fallback when the game is not in
front. Lua 1.2.74 is retained.

## Saved world-tooltip choice, Lua 1.2.74

Before Lua 1.2.53 the world-tooltip default was Mover, and profile switches,
presets and stored snapshots wrote that default into saves. Those characters kept
world tooltips fixed at the mover spot while UI tooltips followed the cursor. Each
save and stored profile now moves a saved Mover choice to Cursor once (`tipcursor`
marks it); any later choice, including Mover, is kept. Native and Hidden are not
changed. Build with `prepare.py --update --plugin-only`; native Core 1.2.72 retained.

## Reset defaults and shortcut group, Lua 1.2.73

Reset and Reset all return an ordinary window to its native layout spot from the
catalogue (or its Aetherfall design spot). Previously the default was wherever the
window was when MoveUI first saw it. Moving **Shortcuts (all)** now moves only the
grid anchor; icons placed individually keep their own offsets, and grid icons stay
aligned at screen edges. Build with `prepare.py --update --plugin-only`; native
Core 1.2.72 is retained.

## Interface work only when something changes, Core/Lua 1.2.72

A faded background's draw checks only its owner dialog slot and object class.
The full widget-path check runs when selections are made and every five seconds.
Re-selecting an unchanged widget returns at once, and a held class reuses its
measured table instead of querying memory for each slot.

Opacity prepares a window instance once at its current value. Target changes and
window openings handle only new or changed windows. A five-second sweep picks up
widgets the game adds later and prunes retired selections. The holds are rebuilt
after a change, while MoveUI is open, and otherwise five times a second. The
tooltip policy still runs every tick, so a cursor tooltip follows the mouse;
an unchanged policy otherwise returns without re-checking the tooltip classes.

Zone changes, loading screens and teleports keep the native holds. Each hold
checks its current dialog, classes and widget path (1.2.71), so the game no longer
places every frame itself for the settling second. An unsaved MoveUI draft is
still cancelled. Character switches and disconnects release the holds as before.

Build with `prepare.py --update --retain-layout`. Only `AetherfallCore.lua` and
`AetherfallOptions.lua` change inside the signed plugin. In-game appearance,
tooltips, teleports, loading screens and combat smoothness need owner testing.

## Quit countdown and retired mover owners, native 1.2.71

Native layout callbacks verify a mover's current dialog slot, owner and target
classes, and widget path before using its saved coordinates. The client can
rebuild UI objects when the quit countdown expires; a reused target address
previously let the mover read a retired owner's memory. A stale selection now
delegates to the saved native layout method until Lua selects the current object.
Removing a mover policy also avoids looking up retired objects. Native class
identity, backdrop rendering, Lua and saved profiles are retained.

The reported access violation maps to the retired owner's flag call inside
`placed`. An isolated native fixture reproduces the former access violation;
492 checks, including 50 dialog recreation cycles, validate native pass-through,
live mover positions, anchor adjustment, class identity and safe policy removal.
Completing the quit countdown in the real client remains owner acceptance.

## First opening, Lua 1.2.69

Existing ordinary dialogs receive their profile position and backdrop policy
while still hidden. The previous visible-only sweep let the native opener draw
one placement/opacity first, then replayed the profile on an addon tick.
Native 1.2.68 retains the prepared backdrop through hide/show; this Lua update
uses that lifetime before the first visible draw. Later native dragging and
reopening remain authoritative, and conditional HUD geometry still waits for
native visibility. No timer acceleration or rendering callback was added.

Lua 5.1 checks reproduce the prior hidden-window omission and exercise preparation,
first-visible state, opacity edits, profile changes, reopen/drag ownership and
window identity at four UI scales. Live appearance and native opener behavior
remain owner acceptance, particularly for windows instantiated only when opened.

## Native class identity, native 1.2.68

Layout and backdrop policies retain each object's native class-table pointer.
The unified inventory and native client use that pointer for concrete-class
admission: substituting a private table made the inventory guards fail after
opacity or a mover selected the window. Guarded native class slots now dispatch
to per-object policies, forwarding unselected objects to saved native methods.
The bounded class registry remains while the bridge is loaded; clearing policies
leaves native pass-through without writing retired objects. Hidden, still-owned
backdrop selections survive reopening so the first draw retains their policy.

The earlier bridge reproduces the shipped inventory guard failure in an isolated
native fixture. The correction retains all five exact inventory admission checks
while geometry and all four opacity delegates remain active; 8,088 checks pass.
Game.dll on disk, renderer/camera DLLs, Lua and saved positions are retained.
Inventory sizing/reopening, bottom-menu controls, opacity and focus return still
require owner acceptance in both renderers.

## Quest mob names and progress, native 1.2.66

Attack-cursor quest mobs gain the existing native quest-type glyph immediately
before their overhead name. The client still owns the nameplate position,
projection, visibility, distance scaling, font and targeting behavior. The actor's
actual name and the larger NPC interaction markers are unchanged. Campaign,
important, legion, event and ordinary types use the same mapping as native quest
tooltips; a campaign takes priority when several quests need the same mob.

The native world-hover quest-text function adds current/required counters before
the tooltip measures and adds each quest row. The original 1.2.63 implementation
hooked NPC dialogue buttons instead; 1.2.66 corrects that route and preserves the
native hover buffer's 256-character capacity and return value. Compiled quests use their native journal parameter callback and authored
journal quotas. The maintained data-driven hunts use their verified six-bit
SECTION_0 stage/SECTION_1 count contract. Collection counters use the native
quest definition and bag inventory. Unavailable or nonnumeric progress retains
the original title. No quest progress is changed or stored by this feature.

The normal native builder regenerates `quest_target_data.h` externally from the
active NPC attack cursors, English journal totals and maintained custom-quest
registration contracts. Rebuild it when those inputs change. The manifest records
their hashes, and installation rejects inputs changed since preparation. The
installed mod inventory is refreshed with every native bridge build. Lua-only
updates retain this native DLL. Game.dll on disk, signing and recovery images do
not change: the guarded native name formatter and world-hover quest-text calls are redirected by the already
imported bridge at startup. A failed guard leaves native formatting intact.

Owner acceptance still covers actual glyph appearance, native scaling/occlusion,
partial kills and pickups, completion/abandonment, several simultaneous quests,
tooltip refresh and relog. Offline adapter checks do not establish rendering.

## Direct backdrop opacity and complete draw coverage, Core/Lua 1.2.67

General has one **Window background opacity** control. Chat and Action Bars each
have an independent **Background opacity** control. Values are direct 0–100
percentages: zero omits the selected backgrounds, and 100 retains their native
opacity. Text and item icons remain visible. There is no active master setting
or second multiplier. Native frame skins include their border artwork.

Existing settings fold the former master into each category once. A saved
master/window pair of 70/70 becomes window opacity 49; zero stays zero. The
`opacitydirect` scalar prevents applying the conversion again after save/reload.
Stored profiles migrate when restored. Fractional products survive file/profile
roundtrips. Other settings, mover positions and profile bindings are preserved.

Owner screenshots confirm that 1.2.65 did not fix the reported reversal. Its
checks covered only float-colour skin drawing. Native `UIDialog::Draw` also
uses byte RGBA drawing in another render mode, which bypassed the policy.
The native 1.2.67 correction covers all four verified relative/absolute
float/byte delegates. Zero omits only the selected skin draw. Positive values
multiply native alpha, with byte results rounded to the nearest byte. This
correction preserves native RGB, state/animation alpha and draw arguments.

An external harness executes the installed `UIDialog::Draw` machine code and
all four delegates with simulated widget/skin objects. The old byte path passes
255 even at a zero setting; the correction passes 7,541 checks across all 101
integer settings and five native alpha values. This proves the omitted path and
the correction's delegate behavior. It does not prove what switches modes in
the owner's screenshots or establish final GPU rendering.

The previous opacity controller wrote the widget's native skin alpha and revisited
it on settings/visibility events or the periodic tick. That field also belongs
to native colour/state/animation updates. A scalar/controller sweep did not test
what a subsequent native draw would receive after another update replaced it.

Backdrop settings are now independent render multipliers on the selected skin
draws. Each call copies the native draw colour and multiplies its alpha by the
current setting. Native RGB, fade/state alpha, coordinates, clipping, text, item
icons and widget state remain untouched. There is no percentage cutoff or
replacement opaque value in the policy.
Only the verified native skin delegates are intercepted through guarded native
class slots. Object class identity and Game.dll on disk are retained.
Selections validate current dialog ownership and exact widget paths and are
released on replacement, character changes or interface disable. Hidden live
selections remain ready for reopening. Geometry
pins retain their own lifetime independently of opacity selections.

The focused native harness executes the installed skin delegate machine code
with simulated native widgets and captures its outgoing colours. It exercises
native alpha changes between controller updates, all integer percentages, zero,
pin/release ownership and stale-instance rejection. Live appearance remains an
owner acceptance check.

The former master and window/chat/bar multipliers were an Aion-specific design.
They have been replaced by the direct category values above. The inspected
ElvUI `GetTemplate`/`SetTemplate` code instead distinguishes Default and
Transparent backdrop colours, preserves a separate border colour, and permits
an explicit per-frame backdrop alpha. Aion's native skins combine their fill and
border artwork, so multiplying a skin does not reproduce that border separation.
The native quest summary still uses parchment and dark text; fading its artwork
does not create a readable dark-theme text skin. That resource styling remains
unmodified by this opacity repair.

## Native reopening and panel opacity, Lua 1.2.64

Ordinary named windows restore their saved profile position once per profile and
viewport. Native title dragging and subsequent close/reopen placement stay with
the game. A visibility notification no longer replays an older addon coordinate
over the native opener. HUD pins and explicit Move UI placement remain separate.

Opacity also includes passive tiled panel skins selected from the installed
catalogue. This covers the quest journal's unnamed tab-page, container and list
backgrounds, which previously remained opaque while the quest scroll frame faded.
Options and HUD panels use the same guarded native backdrop operation; the two
inner options panels participate as well. Text, icons, buttons, HTML content and
foreground gauges are excluded. The existing master/group multipliers and saved
values are retained, including zero. Multiple overlapping skins and different
native artwork can still produce different pixel colours at the same setting.

Focused offline checks reproduce the old reopen replay and journal omissions and
sweep all four controls from 100% through 30%, 20%, 10% and zero using the installed
catalogue. Actual reopening and the reported low-opacity visual reversal require
owner confirmation in the game; controller checks do not prove rendering.

## Window lifecycle and opacity controls, Lua 1.2.62

The UI exposes a master backdrop opacity and multipliers for windows, chat and
action bars. The effective group opacity is master x group (70% x 70% = 49%).
These are Lua policy over the same generic native backdrop setter; border and
background pixels belong to one frame skin. Labels and hints now explain that
relationship. Saved values and confirmation opacity remain unchanged.

Opacity edits update only skins, including the addon data panels. They do not
reapply layout, companion visibility or HUD toggles. Native visibility/target
notifications enqueue a bounded refresh for the normal addon tick instead of
moving or showing/hiding other windows on the native notification stack.
Placement/appearance refreshes no longer include visibility enforcement; periodic
enforcement can hide explicitly disabled HUD elements, while restoration belongs
to an explicit settings change. Ordinary native window closure remains native.

The native drag recorder requires cursor displacement and a matching translation
at constant frame size before saving a named window position. A stationary press
over several opening or resizing windows no longer records their animation as
user dragging. The map uses this recorder too, instead of periodically saving
any native map movement. Existing profiles and unrelated mods are retained.

Build with `prepare.py --update --plugin-only`. The native core remains unchanged.
Owner verifies repeated opening/closing, window positions, native title dragging,
map persistence and all opacity controls after a normal client restart.

## Shared window opening settings, Lua 1.2.61

This earlier revision applied the full appearance/placement pass synchronously
on native visibility changes. Revision 1.2.62 supersedes that lifecycle policy;
notifications now queue work for the normal addon tick.

Unnamed native popups retain their opener's placement. They have no persistent
window identity, so the UI neither restores a saved slot position nor records
their native motion as a named-window layout edit. Existing profile values are
preserved. General, Window, Chat and Action Bar opacity controls keep their
shared behavior, including confirmations; there are no class-specific exceptions.

Build with `prepare.py --update --plugin-only`. Only the Options Lua member
changes in the signed plugin. Native core, theme resources and button actions
are retained. Owner verifies first-frame appearance, waiting, opening several
confirmations, OK/Cancel and saved positions for ordinary named windows.

## Chat and companion-bar background opacity, Lua 1.2.60

Chat opacity now selects the native `v5_chat_tabpage` skins on all seven chat
pages. These panels have no background-style name and were previously skipped.
Chat text, tabs and scroll controls keep their own alpha. The companion command
bar uses the existing guarded native backdrop setter, resolved
by current dialog name on every pass, instead of the queued plugin alpha call.
The shared General value and separate Chat/Window reductions still apply.

Build with `prepare.py --update --plugin-only`. The native bridge, client layouts,
companion controls and saved settings are retained. Owner verifies actual fading,
tab switching, bar expand/collapse and relog in game.

## Countdown timer mover, Lua 1.2.59

Move UI offers **Countdown Timer** independently of the minimap. Its exact native
layout and verified type come from the shared UI catalogue, so the mover is also
available before a countdown starts. The native timer has no title-bar dragging;
the editor uses the existing HUD pin, profile, Save/Cancel and Reset behavior.
The game still owns its text, duration and visibility.

Build with `prepare.py --update --plugin-only`. Only the two maintained Lua
modules change in the plugin; the native bridge and base/English resources stay
unchanged. Owner verifies dragging, Save/Cancel, later countdowns and relog.

## Target number foreground repair, 1.2.57

HP/MP and self-target DP numbers are native target-frame labels, appended after
their bars with white text and a dark shadow. Target-of-target has the same
foreground HP label. They inherit their frame's visibility, movement and UI scale;
the old separate plugin dialog keeps only its existing self-target DP fill.
Values still come from the native target snapshots; unavailable resources stay
blank. Toy-pet feeling gauges retain their native behavior.

`AFCore.SetText` uses the stock widget text setter on existing static widgets,
with bounded UTF-8, fresh path resolution and no retained pointers. Build with
`prepare.py --update --retain-layout --target-foreground-update`. Only the two
target layouts change in the base/English UI archives, with matching owned skin
payloads and graphics recovery images. Other generated plugin dialogs, native
frame geometry and installed client features are retained. Owner acceptance is
still required for actual rendering, target changes, dragging and relog.

## Target panels and artwork-sized movers, Lua 1.2.54

MoveUI offers **Target Altitude / Distance** separately from the target unit
frame. It follows the target until positioned independently; Reset attaches it
again. Save/Cancel and profile positions use the existing editor transaction.
Only declared native panels are offered: the shipped target-of-target layout
has no separate altitude/distance child.

Target and target-of-target mover bounds come from their active panel background,
with catalogue bounds before a frame appears. Hidden alternate target groups and
the detached distance panel do not enlarge the unit-frame box.

The Shop mover fits the sprite's nontransparent pixels. Its catalogue bounds are
derived from the actual atlas alpha (27×31 within a 35×36 source rectangle), so
transparent padding does not enlarge the box. Moving the box retains the native
button origin, click area, shortcut grid, notification and effects.

Build with `prepare.py --update --plugin-only`. The normal builder refreshes the
catalogue and signs the plugin; native bridge 1.2.53 and all UI resources remain.
Owner verifies actual target changes, independent dragging, shop box, Save/Cancel
and relog in game.

## Background opacity, flight artwork and tooltip defaults, 1.2.53

General **Background opacity** is the shared 0–100% backdrop setting. Window,
chat and action-bar controls further reduce their own backgrounds. Native
catalogue paths select background images, containers and background-only gauges;
action-slot skins are adjusted separately from their icon pixels. Text, health
and mana fills, portraits, minimap terrain, flight rings and button states retain
their foreground opacity. Plugin windows use nonrecursive queued alpha changes,
except the companion command bar which uses the guarded native backdrop API.

This follows ElvUI's `GetTemplate` / `SetTemplate` backdrop-color alpha and
`CreateBackdrop` separation, inspected at upstream commit
`ad4a7f7dea79b91f7a7f7bd404e0cede8af9e5fb` in
`ElvUI/Game/Shared/General/Toolkit.lua`. Aion uses its existing native skin alpha
and verified widget paths rather than WoW's frame API. The bridge defaults to
nonrecursive changes; optional recursion only admits named backdrop children.

Flight keeps the native image skins for all states, including radial flight-time
and cooldown slices. Generic tiled-skin conversion is limited to ordinary
shortcuts. Tooltip modes default to Cursor for characters/profiles without a
saved choice; explicit Native/Mover/Cursor choices remain intact. The accepted
native tooltip placement and comparison/map grouping implementation is retained.

Build with `prepare.py --update --retain-layout --shortcut-geometry-update`.
The normal builder composes both HUD styles, the English locale, signed plugin,
interface payloads and Graphics recovery records. Owner verifies real opacity
changes, flight artwork/states and tooltips in game.

## Existing feature controls, Lua 1.2.52

`/moveui` includes **Shortcuts (all)** for moving the complete shortcut group.
Individual icons remain adjustable. In `/afui` > **Minimap**, shortcut size,
spacing and row count rebuild an evenly spaced grid; **Align shortcuts** also
rebuilds it. All icons share square native artwork and click bounds. The active
HUD style is identified through live widget names and catalogue paths; nested
shop controls follow their container in either style. Tall grids gain columns when needed to
keep every icon on screen; the control shows the effective count.

Unpositioned clock, zoom, vendor notice and area controls follow the minimap.
Their individual movers allow separate placement; **Align map controls** places
them below the current map again. Bar 1–4 layout controls use the catalogue's
exact container and slot paths for every size and row count.

Profile switching applies native visibility, bar geometry, backgrounds and
readouts together. Applying a preset resets that profile's arrangement. Saving
a user profile writes its settings and positions immediately. Numeric controls
stop at their limits; choices still cycle. Conditional party/target frames show
only when the corresponding native unit exists. MoveUI remains a draft until
**Save / Lock**, with **Cancel** available while arranging it.

Build with `prepare.py --update --plugin-only`. Native bridge 1.2.51 remains;
this feature update changes the Options controller in the signed plugin.
Focused Lua checks use installed widget wrappers and actual catalogue hierarchy
with simulated native placement. Appearance, input, focus recovery and relog
still require in-game acceptance.

## Shared native UI catalogue and geometry, 1.2.51

The normal builder generates `AetherfallRegistry.lua` from the exact active
base, English and plugin UI definitions. It records every declared window and
widget, including parent paths, variants, presets, skin references and source
hashes. Native IDs come from verified client RTTI/GetType methods. Resource-only
and dynamic definitions keep their distinct status; no IDs are invented.

`AFCore.Widget` and `AFCore.Children` inspect live controls without opening
windows. Placement, rectangles and sparse click testing share one selector
resolver, including named and unnamed nested containers. MoveUI previews now
come from the shared catalogue instead of a separate hand-maintained list.
`/afcore ui` summarizes coverage; `/afcore inspect <id> [path]` shows live bounds.

HUD shortcuts use square native geometry and private stretched-sprite skin
variants. Original artwork, native gauge/button handlers, shop notifications
and particle controls remain. Shortcut size, spacing, row count and **Align
shortcuts** use the same square dimensions. Size, spacing and row changes also
rebuild the grid in 1.2.52. Minimap zoom retains its native handler;
its unnamed parent now resolves correctly during sparse click testing.

Build with `prepare.py --update --retain-layout --ui-registry-update
--shortcut-geometry-update`. Subsequent plugin builds refresh an installed
catalogue automatically. Game.dll and texture archives do not change. The
builder composes affected full-HUD resources, signing, interface payloads and
Graphics restore metadata together. Live artwork/hover/click behavior, flight,
shop states, Save/Cancel, focus changes and relog require owner acceptance.

## MoveUI grid and names, Lua 1.2.47

Grid lines retain at least one whole UI pixel before native scaling, so they
remain visible above 100% UI scale. Small boxes and long names use passive full
captions with a connecting line. Their native drag bounds, saved positions,
profiles and snapping are retained. Editor captions are filled by Lua.

Build with `prepare.py --update --plugin-only --mover-editor-update`. Only the
MoveUI dialog and Options Lua change within the signed plugin; native binaries
and other installed dialog bytes are retained. Actual appearance and input need
in-game acceptance.

## Independent minimap elements and border, 1.2.49

MoveUI offers **Minimap**, **Minimap Zoom**, **Minimap Clock**, **Current Area**,
**Area Sword**, and the conditional **Vendor Notice**. Each has independent profile positions,
Save/Cancel, precise placement and reset. Explicitly positioned controls remain
independent; unpositioned controls follow the map in 1.2.52.
Clock, zoom and vendor notice have separate bounds and independent movers, with
space between them by default. The native area/server text and safe/PvP icon keep their
own native states, localized text, visibility and hover behavior.

The minimap's native Aetherfall frame is restored as a separate image around
the map only. Map drawing uses the existing 1.2.46 renderer correction inside
that frame. Coordinates, when enabled, follow only the map. The invisible native
parent canvases cover their separated children; verified radar/subzone class
hit tests leave gaps clickable in the world. No new native windows are created.
The MoveUI 1.2.47 labels/grid remain intact.

Build with `prepare.py --update --retain-layout --hud-customization-update
--minimap-border-update`. This builds Lua 1.2.49 and bridge 1.2.48, composes the four native
radar layouts with their interface payloads, signs the plugin and stages matching
Graphics package/restore records. The existing Game.dll renderer repair and
other native bytes are retained. Appearance, real dragging, hover/click behavior
between widely separated elements and relog remain owner acceptance checks.

## Minimap rendering and native area strip, 1.2.46

The size setting now changes the map renderer's viewport as well as its window.
The verified native renderer previously drew a fixed 156-pixel square inside the
resized image, with a separate 165-pixel vertical inset calculation. Its viewport
and centring dimensions now come from that image, so map
drawing and marker projection share the chosen bounds. Zoom retains its native
behavior. Clock, zoom and vendor notice occupy separate spaces below the map.

The native area name, server line and PvP/safe-area sword icon follow below those
controls, with optional coordinates underneath. The whole group uses the Minimap
mover. Native text, icon states and hover behavior remain in use; the separate
Zone Name mover is removed from the active interface. Saved profile files remain
intact.

Build with `prepare.py --update --plugin-only --retain-layout
--minimap-renderer-update`. This retains bridge 1.2.45 and installed layouts,
signs the updated Lua plugin, and uses the existing incremental Graphics/cursor
helper to compose the bounded Game.dll edit with both restore baselines. Native
pet/Lua imports, the session repair and all other native bytes are preserved.
In-game resize, zoom, icon/area hover, focus recovery and relog need owner testing.

## HUD size, alignment and transparency, 1.2.43

Open `/afui` to adjust these settings for the current UI profile:

- **Minimap:** size from 120 to 320 UI pixels, with zone and coordinates attached
  immediately below it. The thick black frame and label backdrop are removed.
- **Minimap > Shortcuts:** all fourteen menu controls use the same square size.
  Choose size, spacing and icons per row, then **Align shortcuts** to return
  individually moved icons to the grid. Other saved frame positions are retained.
- **General > Window backgrounds**, **Chat > Background opacity**, and
  **Action Bars > Background opacity:** 0Ã¢â‚¬â€œ100%. Text, resource gauges and skill
  icons retain their foreground opacity. Companion and settings windows use their
  normal queued background control.

Player and target frames retain their matching 251Ãƒâ€”72 dimensions and HP/MP rows.
Action slot pins include the main bar's native container offset, so their visible
and clickable bounds agree after resizing. The minimap reserves space for its
label at the bottom of the viewport. Zoom, clock and vendor notice remain native.

Build with `prepare.py --update --retain-layout --hud-customization-update`.
Use `--target-overlay-update` when composing the earlier target readout repair.
Only the four radar layout members, minimap label and maintained Lua/native bridge
are updated; the builder composes the active English/base archives with the
interface payloads and matching Graphics package metadata. Native window skin
alpha uses the verified base method and a bounded walk of current children;
no pointers persist across window recreation. Appearance and interaction still
need in-game acceptance.

## Placement and MoveUI repair, 1.2.45

Native target, target-of-target and target cast bars now retain their mover
positions after the engine's internal anchor calculation. Target Auras retains
its existing native zero-sized canvas and post-anchor hold. The native client has
no separate target-of-target aura dialog; its cast bar has a permanent mover.

Focus loss no longer treats temporarily hidden HUD windows as a world transition.
Ordinary window positions are saved only after a foreground mouse drag, so engine
layout changes cannot silently overwrite the user's saved position. Hiding an
open MoveUI editor cancels the draft and releases ordinary window pins.

MoveUI includes previews for 154 verified native window layouts before opening
them. Native GetType values and English layout bounds define the catalogue;
unmapped dialogs still require native discovery. Closed ordinary windows show a
preview box when selected. Previous/next selection reaches the entire catalogue
while reusing the existing 56-box pool. No windows are opened to discover them.

Daily/system notification placement has a screen-bounded default. The game still
controls whether a daily quest is available and whether its button is shown.
Shortcut art keeps its shipped bounds, including the nested shop notification
and flight control; cell size controls grid padding. HUD background opacity also
applies to native HUD skins while keeping their text and foregrounds intact.

Build with `prepare.py --update --retain-layout`. Untyped widget lookups use a bounded child traversal: native type zero is not
a wildcard. This also restores named minimap and HUD background lookups.

Native appearance, real focus
changes, title dragging, conditional notifications and relog require owner testing.

## Target readout placement, Lua 1.2.42

The target HP/MP/DP readout uses the existing frameless HUD preset and a passive,
click-through dialog. Queued placement is refreshed after showing it, so native
saved window positions cannot leave it beside the target. Value updates, target
changes and MoveUI dragging use the same native target rectangle.

Build with `prepare.py --update --plugin-only --target-overlay-update`. Only the
target readout dialog and HUD Lua change inside the signed plugin. Native Core
1.2.41, focus/daily-icon repairs, layouts, profiles and other plugin members remain.
Actual rendering, target selection, dragging and relog need owner acceptance.

## Target, notifications and companion bar, 1.2.40

### Focus and daily quest icon repair, 1.2.41

Shortcut pins and click bounds use native screen coordinates, including parent
content offsets and nested containers. Focus return retains live holds while the
viewport settles. `/moveui` now offers **Daily Quest / System Notifications** for
the actual daily quest icon in `gm_indicator_dialog` (native type `0x11f`), with a
box matching its visible icons. **Tutorial Notifications** controls the separate
`help_indicator_dialog`; existing saved tutorial positions remain valid.

Build with `prepare.py --update --retain-layout`. Native rendering and repeated
Alt+Tab, dragging, Save/Cancel and relog still require owner acceptance.

`/moveui` includes **Daily Quest / Notifications** even before the notification
window exists. Saved positions also hold newly shown notifications after locking.
Target aura placement is held after the native anchor calculation inside SetRect;
the earlier incoming-rectangle hold could be overwritten by that calculation.
The native aura canvas stays zero-sized and the game's icon arrangement is retained.

The target frame is 251 by 72 UI pixels, matching the player frame. It shows HP/MP
values and reserves a third row for DP. Self-target DP uses the player's native DP
gauge. The native target record exposes no DP; other targets keep this row empty.
Unavailable MP stays empty too. The passive value overlay adds one plugin dialog.

In `/afui` **General**, turn **Companion bar** Off to hide both the commands and
their icon. World bots and bot interaction continue. `/botbar` restores the bar.
Pressing its X keeps the small icon; that choice is saved immediately per character.
Login waits for the saved mode and character settings before showing the bar.

Build with `prepare.py --update --retain-layout --hud-repair-update`. This builds
the native bridge, updates only the affected Lua/layout members, and composes the
target layout with the installed interface and Graphics menu package. Off/on skin
payloads, restore baselines, other plugin members and profile files are retained.
The normal signer and guarded installer deliver the original-client cohort.
Native rendering, dragging, targeting and relog behavior need owner acceptance.

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

## First-use tooltip initialization, 1.2.70 (26 October 2026)

Configure the verified live tooltip classes from the placement pass with
`AFCore.TipPin(0, ...)`, before any named tooltip template exists. The previous
template discovery path could leave new characters without a policy when only
the skill template existed, while established characters had already created
the shared template. World and UI choices remain independent; the skill
template is never repositioned or used to replace the world policy.

Cursor, Mover, Native and Hidden behavior, native class guards, comparison/map
grouping, profiles and bar positions are preserved. This is a shared code repair:
maintained source and original installation precede the requested repack sync.
Focused native/Lua checks cover empty, skill-only and shared-template inventories;
actual first-use hovering remains an owner acceptance check.

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

The Menu arrow's popup follows its live position using native popup ownership
and the active layout. Lua applies the client's original above/below and
horizontal edge rules when the popup opens. Core only exposes the generic,
read-only `PopupOwner` query; it adds no Menu-specific positioning or command hook.
Lua 1.2.56 limits the anchor adjustment to native root slot `0x1e9`. Slots
`0x1ea`/`0x1eb` are hover submenu depths and retain the client's original cascade
placement, even when they share the Start dialog as their command owner.

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
