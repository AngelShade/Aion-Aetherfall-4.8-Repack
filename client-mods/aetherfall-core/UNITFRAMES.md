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

Unit Frames has General and Shared colors tabs plus Player, Target, Target of
target and Party child pages. Each unit has General, Bars, Colors and Text:

- General: enable, font/shared font, name limit, level, borders, backdrop
  opacity, bot label/rarity/rank; Player also has DP enable and height.
- Bars: health height, power height and spacing within the native bounds.
- Colors: class/shared fixed/custom health; shared/custom power; shared/custom
  dead and unavailable-data colours.
- Text: independent health and power left/centre/right tag strings.
- Shared settings: module enable, update interval (50 ms ticks), font, fixed
  health, power, backdrop, border, text, dead and unavailable colours.

Frames move with `/moveui`. The existing native mover Setup actions open the
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
