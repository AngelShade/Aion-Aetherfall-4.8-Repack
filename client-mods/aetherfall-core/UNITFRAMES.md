# Unit frames 1.4.0

Source-only implementation for review. No client installation, native build,
commit or publication is part of this change.

`modules/UnitFrames.lua` owns lifecycle, settings, bounded scheduling, native
visual snapshots and rendering. Its registered elements live in
`modules/unitframes/`: Backdrop, Health, Power, DP, Name, Level, ClassColor,
State, BotIdentity and TargetOfTarget. `Native.lua` is the single native path
table. `tags/UnitFrames.lua` registers snapshot-based formatters.
`options/UnitFrames.lua` describes deferred pages. `layout/unitframes.py`
generates the passive startup canvas and solid-colour textures.

The canvas is `AFHFrames`, created by `screens()` with the plugin, using
`v5_hud_pet_command_s1` and
`transparent;not_visible;not_movable;not_active`. Images/statics are direct
dialog children. It consumes one additional dialog: the owner's installed
collection becomes 17/20. Existing native movers, positions, targeting,
right-click menus and party actions retain their native owners.

## Native visual ownership

Paths were checked against the installed English UI catalogue and native
resources. The complete executable table is `modules/unitframes/Native.lua`.
Required paths must resolve to their verified native type before takeover:
static `0x2022`, gauge `0x200e`, image `0x2021`. Optional `af_*` labels are
included when present. Unknown types/layouts leave the native frame intact.

| Unit | Native owner | Visual paths hidden with WidgetShown |
| --- | --- | --- |
| Player | `basic_status` / `basic_status_dialog` | `hp_gauge`, `hp_gauge_bg`, `mp_gauge`, `mp_gauge_bg`, `dp_gauge_1`, `dp_gauge_2`, `dp_gauge_3`, `dp_gauge_text`, `level`, `dp_level` |
| Target | `target` / `target_dialog` | Every `container1` through `container7`: `bg`, `name`; containers 1–4 and 7: `level`, `hp_gauge`, `hp_gauge_bg`, optional `af_resource_hp`; container5: `hp_gauge`; container7: `mp_gauge`, `mp_gauge_bg`, optional `af_resource_mp`, `af_resource_dp`, `af_identity_rank`, `af_identity_gear_{poor,ordinary,good,great,excellent}` |
| Target of target | `target_assist` / `target_assist_dialog` | `container/{bg,level,name,hp_gauge,hp_gauge_bg}`, optional `container/af_resource_hp` |
| Party | `party` / `party_dialog` | Each `ctn_member1` through `ctn_member6`: artwork `#2`, `title`, `level`, `hp_bg`, `hp`, `mp_bg`, `mp`; optional `af_identity_rank`, `af_identity_name_{poor,ordinary,good,great,excellent}` |

The player dialog's own skin uses `AFCore.Backdrop(id,0,'',0)`. Releasing it
uses multiplier 1; Opacity then reapplies its current native setting. Other
unit frames hide only the selected passive widgets, so Opacity can continue
styling unreplaced party rows. Input containers/buttons are never hidden.
The native target Codex button keeps a clear header area; party voice controls
keep the right-hand area beyond the 200-unit resource body. Flight, cast,
distance, aura and other native input/status elements remain native.

Snapshots store the widget's **local** shown flag (`flags & 1`), independently
of hidden parents, and the native dialog's session instance identity. A release
restores those local states only on the same native instance and widget type.
It never applies an old snapshot to a recreated dialog. Per-unit disable,
module disable, interface disable, logout and character changes all release
ownership. Existing Identity and TargetDetails owners pause through
`UnitFramesChanged` messages; Identity releases its own name substitution before
the snapshot and resumes normal native bot labels after release.

## Data and limits

Player and party data use stock `UnitName`, `UnitLevel`, `UnitHP`, `UnitHPMax`,
`UnitMP`, `UnitMPMax`, `UnitClass` and `UnitIsDead`. The client binary confirms
`player`, `party1` through `party6` and those API registrations. Party rows are
also checked against their actual native `title`; a mismatched or rearranged
row is left native until names agree. No unverified `GetPartyMemberInfo` field
or invented roster index is used.

Targets use `AFCore.Unit('target')` / `AFCore.Unit('targettarget')`. This bridge
does not expose target class, reaction or connectivity. Unknown class uses
the shared fixed health colour. Party resource data that is missing is labeled
**Unavailable**, not asserted to be offline. Target death is inferred from
verified zero HP. Player DP uses `basic_status/dp_gauge_text`. Bot gear rarity
and rank are supplied by `Identity:Lookup`, preserving its existing mapping.

Native rectangles are screen pixels; plugin placements explicitly divide by
UI scale. The frame anchor retains its native bounds; body/header reserves
keep the native voice and Codex affordances clear. Requested bar heights shrink
to fit those bounds. Native integer UI-unit truncation means a border can be
slightly thicker than one physical pixel at noninteger scale, including 1.125.

There is no verified image RGB setter in the actual client widget framework.
Skin selects generated RGB444 DDS tiles: 4,096 opaque 4×4 images, 786,432 bytes
before filesystem overhead. Custom image colours round each channel to the
nearest 17/255 (maximum error 8/255). Opacity uses the verified SetAlpha API;
text colours use the existing float-RGBA attribute. No guessed image attribute
or engine change is introduced.

## Options and tags

### Target portraits (Lua 1.4.9 / Core 1.3.20)

The Target portrait uses the selected native creature's object ID, not its name
or bot growth identity. NPCs, other players and playerbots without growth records
therefore share the same path. Duplicate NPC names cannot select another body.
Player bodies include their received appearance and visible equipment. Known bot
gear quality selects its existing border; other targets use the neutral player
border. The existing Target **Show portrait** checkbox is honored without changing
saved choices. Player and party portrait behavior is retained.

`target_portrait.h` follows the native target getter's entity/component resolution
(`0x17be0b..0x17be97`), appearance/model reads (`0x17a44e..0x17a507`) and equipment
copy (`0x17a70e`). The stock portrait calls the same model and gear setters at
`0x60fcc6` and `0x60fd2e`; the model setter explicitly resolves NPC templates at
`0x488144`. No actor pointer is retained between calls. Unavailable models retry
at a bounded interval without leaving a previous target visible; a ready portrait
does not reload on health updates. Native rendering, animation and monster camera
framing still require in-game acceptance; offline fixtures do not establish those.

### Portrait follow-up (Lua 1.4.10 / Core 1.3.21)

- The stock snapshot (`0x17a41c`) takes the player's model, look and gear from
  component type **2** (player character), not type 1. A player target now does
  the same when that component answers with the selected object ID; NPCs keep the
  type-1 template ID, and the four player body models still get their look
  without it.
- Core returns a reason with a failed numeric portrait (`no selection`, `no entity`,
  `no components`, `no creature`, `other selection`, `no model`, `unreadable look`,
  `mesh not built`, `invalid id`). The frame reports (`afui-frames*.cfg`) list each
  frame's portrait setting, ready source, last status, attempts, shown and refused
  state. `afui-frames-portrait.cfg` is written once per session, after the target
  portrait shows or fails three attempts.
- A refused pin of a canvas portrait view retries after the bounded interval; only
  the player's stock view gives its room back for the session.
- The player model is the stock view in `basic_status_dialog`, under the canvas.
  The player's backdrop (and its border) therefore starts beside the portrait
  border, so **Backdrop opacity** no longer dims or hides the model. Target and
  party views are canvas widgets drawn over their own backdrop and are unchanged.
- The game shows its stock DP gauge for the current DP level again on DP updates,
  which blinked under the portrait. `dp_gauge_1..3`, `dp_gauge_text` and `dp_level`
  are parked off screen (the party rows' existing park/unpark) and restored on
  release. DP values are still read from `dp_gauge_text`.

### Native-owned portraits (Lua 1.4.11 / Core 1.3.22)

The failed 1.4.10 session was a creation problem, not a typed-lookup problem.
The real plugin `Widget.lua` loader has no `3dview` wrapper and skips its parent
attachment/configuration. Its C++ `AddNewWidget` factory (`0x622450`) also rejects
that type, independently of the native XML factory's supported types. The earlier
offline fixture accepted a portrait without proving its native attachment.

Target and party portrait nodes are now supported passive addon **Containers**.
`portrait_view.h` creates their native-owned `Model` child using the complete
native XML create/register/attach/read/initialize helper (`0x58a190`) and native
XML document constructor/parser/destructor. It uses the original `"portrait"`
camera and native proportional position alignment: resizing the host updates the
view's viewport in the game's own callback. Only the host is pinned. Clear never
allocates; no native pointers survive calls. Native dialog teardown recursively
removes the child; no foreign allocator, direct delete, Game.dll modification,
Lua factory override or second resize controller is used.

Host/creation failures return reason 10–15. The stock player's native portrait,
backdrop exclusion and DP parking remain unchanged.

### 1.4.11 repair (Lua 1.4.12 / Core 1.3.23)

1.4.11 was rolled back after the owner saw the whole interface stop working. One
confirmed defect: `unitclass('player')` called vtable slot `+0x248` on the `PLAYER`
global (`0x12eda48`), a 0x2e60-byte record built by `0x4413f0` that is not the
creature component. Game.dll loads that global 1,220 times and never makes that
call. It ran on every player class refresh. Core 1.3.23 resolves the player's own
object ID (`+0x30`, as `playerid`) through the same entity route as a target
(`unit_class::of_object`, `0x8bb350`). Lua keeps the stock `UnitClass` name when
native returns no class. No game session has confirmed that this was the only
cause.

- **Player portrait in the canvas.** The player now has a host like Target/Party.
  `AFCore.Portrait(dialog, widget, -1)` builds the player's own body from the
  stock snapshot's source (`0x17a3c8`: object at `0x130c5d0`, `+0x1e0` entity,
  `+0xc0` components, type 2), then applies model, look and gear exactly as a
  player target (`target_portrait::apply`). After three failed attempts the frame
  falls back to the stock `basic_status_dialog/portrait` view for the session.
  Only then does the backdrop start beside the border.
- **Portrait background.** `PortraitBG` (declared before the host, so it draws
  behind the model and below the border) fills the border opening in the backdrop
  colour. **Portrait background** (0–100%, default 70%) on the Player, Target and
  Party General tabs sets its opacity. **Backdrop opacity** no longer touches the
  portrait: the model draws over the canvas backdrop.
- The `portrait_view` creation path was rechecked against the disassembly. The
  factory's first argument is unused on the 3dview branch (`0x58a2a0`). Owner
  `+0x2d0` feeds `0x53d5d0` registration, as in 19 stock call sites.

### Separate portrait pictures (Lua 1.4.13 / Core 1.3.24)

In game, 1.4.12 built every body correctly (frame report: player `ready=-1`,
target `ready=<id>`), but all portraits showed the selected target.
UI3DView renders into a surface named `$UI3DView_%d` from its `+0x608` slot
(`0x48cdf1`, string `0xc3eb30`). The constructor leaves 0 (`0x4864df`), and
each stock dialog assigns its own slot (1–7). Every XML-created Model kept slot
0, so all of them shared one picture: whichever body was built last.
`portrait_view::slot` gives each of the eight known hosts a fixed slot (40–47),
set before the first draw and repaired on an already created view. An unknown
host is refused (reason 16).

**1.4.13 crashed the game and was rolled back to 1.4.12.** XRenderD3D9.dll
builds exactly 8 slots (loop `0x1a5274`–`0x1a5393`) in three 8-entry tables on
the renderer (`[[XR+0xc36d78]+0x19d90]`). The tables are `+0x40c18`
`$UI3DView_i`, `+0x40b98` `$UI3DViewPreMask_i` and `+0x40bd8` the
`Textures/UI/v3_UI3DViewMask_i` file; only `_1` ships. Slot 40 read past the
tables at the type-2 begin (Game `0x48d936`) and faulted at `XR+0x706a7`.

### Renderer slots 8–79 (Lua 1.4.14 / Core 1.3.25)

`render_slots.h` raises the renderer's limit tenfold, from 8 to 80. It changes
only memory, and only when all five sites and the create call hold their exact
stock bytes (XRenderD3D9 SHA256 `2083cae8…`).

- The three readers go through near relays: `0x70477` (type 1), `0x7048e`
  (type 2) and the mask pass `0xaf62d`. Slots 0–7 read the stock tables exactly
  as before. Slots 8–79 read Core's own tables. Any other slot reads null, and
  every reader already skips a null.
- Slots 8–79 come from the stock create call (`0x1a2ea0`) with the stock names
  and flags. Their id is automatic (0, like the mask file), so the renderer's id
  map (`0x113770`) skips them. A `$` surface takes no video memory until its
  first draw sizes it. These slots have no mask file. The mask pass skips a null
  mask the same way it skips a missing one.
- The slots are created at install if the renderer is already up, and again
  after the stock loop (`0x1a5399`). They are released just before the stock
  release loop (`0x198e4f`) with the same virtual `+8`, so a renderer rebuild
  keeps both sets in step.
- The portraits use slots 8–15. A view caches its surface by name at `+0x640`
  (lookup `0x48cd8c`), so a slot change on a view that has already drawn first
  drops that surface, the way the destructor does (`0x486831`). If the patch is
  not applied (`AetherfallRenderSlotsDiagnostics[0]` other than 1), views keep
  their native slot. That means one shared picture again, but no crash.

### On-demand slots and own party row (Lua 1.4.15 / Core 1.3.26)

In game, 1.4.14 showed a separate picture in every portrait.
- Slots are created only when a view asks for one (`render_slots::ensure`).
  The portraits use 8–15, so the renderer gets 16 extra surfaces instead of 144.
  The rebuild relay recreates only the slots that were asked for.
- The file `Mods\AetherfallUI\render-slots-off` switches the patch off
  (`AetherfallRenderSlotsDiagnostics[0]=5`). The views then keep their native
  slot.
- The player's own party row uses source -1, the player's own body, like the
  player frame. Party rows used to ask for a portrait only for bots.

### MoveUI sizes, text colour and live fonts (Lua 1.4.16)

- **MoveUI boxes:** `UnitFrames:MoverBounds(id,d)` returns where Refresh draws
  an owned unit, using the same baseline node and scale, relative to the dialog
  corner. For party it returns the held dialog size (`DialogSize`).
  `NativeFrames.frameRect` asks it first for the player, target,
  target-of-target and party dialogs. For target frames, the old measurement
  used the stock `bg` image, which the skin hides, so it fell back to the stock
  layout size.
- **Per-frame text colour:** `textMode` (`shared`/`custom`) and `textColor` on
  each unit, on its Colors tab. It covers resource text, DP and level.
- **Live fonts and colours:** a text widget keeps drawing its current line with
  the old font or colour until it gets text again. `Skin.Font` and `Skin.Text`
  now re-send the cached text once per actual change.
- The trace ledger was re-accepted and the options reference re-recorded
  (backups in `diagnostics/unitframes-textcolor-20261115`). The 42 changed
  blocks come from saves carrying the two new settings, fewer stock rectangle
  reads, and UI-scale reads for the boxes. Native state is equal at all 3224
  boundaries.
- **Monster framing:** `UI_ModelCam.xml` `portrait` lists camera spots for only
  13 models (the four player bodies and some transformation forms). Every other
  creature gets the view's default camera. The camera block sits at view
  `+0x6b8` and is passed to the model draw at `0x48c7e0`. Resolved in 1.4.17 by
  not building those portraits.

### Pin table, unframed creatures, header layout (Lua 1.4.17 / Core 1.3.27)

- **Target portrait loop:** the bridge's pin table held 192 entries (a live
  read showed 188 in use). With 1.4.15's own-row portrait, the target portrait's
  pin was refused without a message (`pin` returned false at a full table). The
  portrait was then built, refused and rebuilt, leaving an empty gap. The table
  now holds 1024 entries (`PIN_LIMIT`), and a full table records the refusal
  "pin table full".
- **Unframed creatures:** `target_portrait::apply` returns `NO_CAMERA` (17)
  before building anything for a model that isn't a player body. The frame then
  keeps its full-width bars.
- **Party identity gaps:** `companion_identity` keeps a bot it answered within
  `KEEP_MS` (2 minutes) when a refresh comes back without it. It also keeps the
  bot's look when an answer has the rarity but no look. When the cache is full,
  it trims old non-bot entries instead of clearing everything.
- **Loot chest:** `containerN/af_codex_open` is a live native button at the
  start of the stock name area. The target header starts after the chest while
  the chest covers the header's start (`Indicators.chestEdge`).
- **Header tab:** each unit has left/right and up/down offsets for the class
  icon, level and state text. Target and party also have the gear label (crest,
  text and stars together). The target also has the loot chest, which is moved
  by a pin from its natural spot and released at offset 0 or when the frame is
  disabled. All offsets at 0 give the automatic layout. The trace ledger and
  options reference were re-accepted (backups in
  `diagnostics/unitframes-header-20261115`).

Party rows: the game shortens a long name in the row title
(`»Admin«Bab...`), so the exact title/name check left the player's own row
stock. A title ending in `...` or `…` now matches when its stem (3+ bytes)
starts the member's name. Different or too-short stems still leave the row
native. The frame report lists title and name for any party row left native.

### Sizes stacking over re-logins (Lua 1.4.19 / Core 1.3.28)

Owner report, 16 November 2026: after a few re-logins without closing the client, the
target and party frames were far larger than their settings. The frame reports of that
session (client up since 08:35, six logins) show a target of 251 × 1.07⁴ by 72 × 1.15⁴
units at 107%/115%, and party rows of 46 × 1.17⁴ at 117%: the percentage applied four
times. The saved settings were intact.

- **Cause:** the plugin's Lua starts again on a re-login (the once-per-session frame
  reports were rewritten at the last login), while the native dialogs and the bridge
  live on. `geometry()` then took its "immutable baseline" from containers and rows
  that still had the previous session's size, and resized them again.
- **Core:** the bridge keeps the local rect an object had before its first size or
  off-screen pin (`remember_stock`), beyond the pin, with the UI scale it was read at.
  `pinrestore(id)` (`AFCore.PinRestore`) gives the dialog back that size and its widgets
  that size and spot, widgets first. Those widget pins are dropped; the dialog's pin
  keeps its spot and stops sizing. Deleted or recreated objects are only forgotten.
- **Lua:** `geometry()` calls it once before measuring a dialog instance it has no
  baseline for. It also returns stock widgets an earlier session had parked off screen,
  so `park()` no longer records −20000 as their place.
- **Checks:** the trace bridge models the stock rects. `tests/unitframes.py` restarts
  the interface on the same native state (`interface_restart`): without the restore it
  must show the stacked sizes, with it the frames, a party row and both dialogs must
  equal the first session. The native code itself is not exercised offline.
- **Not changed:** native widgets an earlier session hid stay hidden until the new
  session takes the frame over or the game shows them again. Other modules' pins from
  an earlier session stay until that module pins again.

### Classes and sizing

`[class]`, class colour and **Show class icon** use native character class IDs
0–16 and localized `STR_CLASS_NAME_*` text. Target and target-of-target require
a resolved matching character entity; NPCs do not receive invented classes.
Party classes come from the native party snapshot. Per-frame class caches are
separate from name-keyed bot rarity, invalidated on unit/native-instance changes,
with bounded retry when class data is unavailable. The icons use the client's
original emblem textures, including its internal class-name mapping.

Each frame's **Bars** page offers whole-frame width/height (50–300%), HP/MP widths
(10–100% of the bar area), their heights and spacing; Player also has DP width and
height. New dimensions default to 100%, preserving existing saves without a
migration. Geometry baselines are captured before resizing, not compounded from
already resized rectangles. Native target containers and party rows resize with
the artwork, keeping targeting/menu ownership native. Party pitch scales with
row height; voice input remains readable and outside the bar area, and native
selection dimensions follow the rows while its position remains native.

The existing Layout pin owner coordinates dimensions and restores original native
geometry on reset/disable/profile/character transitions. One native party-aura
layout follows each changed geometry batch; unchanged refreshes do not replay it.
Actual native scene rendering and full gameplay interaction remain acceptance
limits of offline checks.

Build with `prepare.py --update --retain-layout`. If unrelated pending Lua differs
from the installed client, repeat `--retain-installed-lua <manifest-source-path>`
for those unaffected files. The normal builder records their retained provenance
and preserves their compressed archive members; it never rewrites their source.

Unit Frames has General and Shared colors tabs plus Player, Target, Target of
target and Party child pages. Each unit has General, Bars, Colors and Text:

- General: enable, text size (Shared or one of Small 9 px, Standard, Standard (no
  shadow), Medium, Large, Larger, Extra large 16 px — backed by the client shaders
  `v3_shortcut`, `ui_default`, `v3_option`, `large`, `v3_chat_normal`,
  `v3_quest_indicator_title`, `v3_chat_large`; text rows grow for sizes above 13 px;
  default Standard; the saved keys `ufFont`/`<unit>.font` hold this size choice as IDs
  9–15 (Small…Extra large). Older saves may still hold the retired IDs 1–8: they are
  never rewritten and are only read as 1, 4, 6, 7, 8 → Standard, 2, 3 → Standard (no
  shadow), 5 → Medium, in both the window and the frames), name limit, level, borders, backdrop
  opacity, class icon, portrait, portrait background and bot label/rarity/rank;
  Player also has DP enable.
- Bars: whole-frame width/height, resource widths/heights and spacing.
- Colors: class/shared fixed/custom health; shared/custom power; shared/custom
  dead and unavailable-data colours.
- Text: independent health and power left/centre/right tag strings.
- Shared settings: module enable, update interval (50 ms ticks), text size, fixed
  health, power, backdrop, border, text, dead and unavailable colours.

Frames move with the Interface **Toggle anchors** button (`/moveui` is an optional
shortcut). The existing native mover Setup actions open the
corresponding unit page. Native visibility controls still determine whether a
native frame is present. Existing native-resource text/backdrop controls are
identified as fallback controls.

Tags include `[hp:current]`, `[hp:max]`, `[hp:percent]`, `[hp:current-max]`,
`[hp:deficit]`, matching `mp` tags, `[name]`, `[name:short]`, `[level]`,
`[class]`, `[state]`, `[bot]`, `[bot:rarity]` and `[bot:rank]`. Formatting reads
the unit snapshot; it performs no extra unit queries or executable evaluation.

## Review and preparation

Run `tests/run.py --client <read-only development client>` for the preserved
trace, focused unit frames/options, isolation, lint, load budget and packaging.
`tests/unitframes.py` drives actual widget scripts, event dispatch and shared
timer; it uses an opt-in realistic native fixture. The immutable original
fixture does not report valid unit-frame widget types and therefore exercises
the fail-safe native fallback. Native state comparison remains strict at every
original boundary. Only the new plugin's own presentation widgets are added
to the existing presentation policy; native WidgetShown/Backdrop calls remain
fully compared. Trace changes require `tests/trace/review.py --accept REASON`.

`tests/preview.py --page <page> --tab <tab> --client <client> --output <png>`
renders option wireframes. `--frames` renders a separate frame wireframe.
These are offline layout checks, not screenshots or game acceptance.

After review, the existing preparation command is sufficient:

```powershell
python client-mods/aetherfall-core/prepare.py --client $env:AION_CLIENT --output $env:AFUI_OUTPUT --update --plugin-only --options-window-update --mover-editor-update
```

No new flag is required. Ordinary retained plugin-only updates also reconcile
AFHFrames. Repeating the XML migration is byte-idempotent; unrelated dialogs
retain their bytes, and unrelated PAK members retain their compressed bytes.
Preparation stages the generated palette through the normal manifest path.

The running game must still confirm drawing order over open windows, native
visual hiding/recreation, real mouse input passing through to targeting and
menus, and scale/font readability. This source-only work does not claim those
checks passed.
