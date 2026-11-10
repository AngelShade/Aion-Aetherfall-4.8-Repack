# Aetherfall options implementation and review handoff

## Scope and version

The scope below describes the original Lua interface 1.3.0 work. Interface 1.3.4 is installed on the original client with native Core 1.3.16. It constructs the options dialog at plugin load for native input, while option factories are evaluated on first use. The options layout measures 12,654 queued calls against the Core 1.3.16 capacity of 1,048,576.

The offline framework executes Lua 5.1 from the read-only client widget framework.

## Files and ownership

- `core/DB.lua` owns schemas, scopes, read-only templates and instance views. `DBStorage.lua` owns bounded AFUI2 disk records and the character index. `DBProfiles.lua` owns named profile operations. `ProfileCodec.lua` owns AFUI1 compatibility and AFUI2 codes.
- `core/Config.lua` owns deferred trees, protected option callbacks, merging, search and navigation lookup. `Widgets.lua` owns pooled controls and range/RGB interaction. `Skin.lua` owns palette, images, typography colors and border helpers.
- `core/DataTexts.lua` and ten `datatexts/*.lua` entries replace the HUD-local source table. `core/Tags.lua` and fourteen `tags/*.lua` entries provide data-source and resource tags.
- `core/Native.lua` exposes generated version metadata. `core/Movers.lua` carries page metadata. `compat/Compat.lua` forwards widget arguments with the existing legacy surface.
- `layout/options_window.py` owns options XML. `mover_editor.py` retains the mover widget count. `options_skin.py` generates loose DDS assets. `prepare.py` stages them through the existing manifest and archive-preservation path. `load_order.json` is the sole Lua member list.
- `options/*.lua` are deferred, owner-specific factories. `modules/OptionsWindow.lua` renders any registered tree and delegates controls to the toolkit. `modules/Profiles.lua` owns profile actions and installation presets.
- `tests/options.py` covers the new window and profile contracts. Trace policy, input translation, exact migration ledgers and cumulative state checks live under `tests/trace`. Isolation, lint and synthetic packaging checks cover the added folders and migration.

## Module contracts

Settings remain private read-only DB views. Messages are listed by their literal names in maintained source. Dynamic bar-page messages append the instance index.

### Lifecycle

Needs Core only. Settings none.

Receives EditorStateChanged, HudChanged.

Sends ApplyCamera, BindCommands, CharacterChanged, ClearEditorDrag, DiscardEditor, DisconnectEditor, HideOptions, InvalidateHUD, MarkPinsDirty, PrepareRecoverEditor, QueueVisibleSettings, RecoverEditor, RecoverNativeWindows, RecoverShownSnapshot, ReleaseLayoutPins, ResetLayoutCaches, ResetObservations, RestoreEditor.

### Identity

Needs Core only. Settings none.

### TargetDetails

Needs Core only. Settings manaFormat, targetAbbreviate, assistFormat, healthFormat, dpFormat.

### WorldMap

Needs Lifecycle. Settings mapy, mapx.

Receives RecoverNativeWindows, WorldMapFound.

### Placement

Needs Lifecycle, WorldMap. Settings none.

Receives CharacterChanged, EditorLocked, PinsChanged, ResetAllPositions, ResetLayoutCaches, ResetObservations.

Sends ApplySettings.

### Tooltip

Needs Core only. Settings ttui, tipwidth, ttclamp, ttanchor, ttgrow, ttx, ttuix, tipcursor, ttshow, tty, tipheight, ttuiy.

Receives ResetLayoutCaches.

Sends MoverGeometry, UnlockEditor, WorldMapFound.

### Visibility

Needs Lifecycle. Settings startmenu, xpbar, showDebuffs, party, showTargetAuras, tot, showRadar, showBuffs, showPlayer.

Receives CharacterChanged, RecoverShownSnapshot.

### ActionBars

Needs Placement, Visibility. Settings bar1..bar4.{enabled,rows,size,opacity}, bar5..bar6.{enabled,opacity}.

Sends MoverGeometry, RefreshOptions, UnlockEditor.

### Opacity

Needs Placement, Tooltip, Lifecycle, ActionBars. Settings chatalpha, dataalpha, playeralpha, windowsalpha, targetalpha, opacitydirect, assistalpha, partyalpha, barsalpha.

Receives BackdropsCleared.

### HUD

Needs Lifecycle, Opacity, TargetDetails, Identity. Settings dlsep3, dataHeight, warn, statusWidth, dc2, dcsep2, drsep2, dc1, statusX, drsep1, mapFormat, statusBottom, databar, font, dlsep1, dc3, dataWidth, timers, statusFormat, customAccent, dl3, dr2, chatline, dl2, theme, drsep3, dataPosition, dr1, dcsep1, separator, dlsep2, abbreviate, dl1, dcsep3, clockMode, mapPosition, coordDecimals, minimapinfo, statusHeight, targettimers, clockSeconds, dr3.

Receives ApplyHUD, InvalidateHUD.

Sends HudChanged.

### Camera

Needs Core only. Settings camfov, camdist.

Receives ApplyCamera.

### Companions

Needs Lifecycle. Settings botbarmode, botbar.

### CustomWindows

Needs Lifecycle. Settings none.

Receives ResetLayoutCaches.

### Shortcuts

Needs Placement, HUD. Settings menucols, menusize, menugap.

Receives PinsReleased, RecoverNativeWindows.

Sends HoldLayout, MoverGeometry, RefreshMoverRange, RefreshMovers.

### Minimap

Needs Placement, HUD. Settings mapsize.

Sends HoldLayout, MoverGeometries, RefreshMovers.

### TargetPanels

Needs Placement. Settings none.

Sends HoldLayout, MoverGeometry.

### Layout

Needs Lifecycle, Placement, Tooltip, Shortcuts, ActionBars, Minimap, TargetPanels, WorldMap, Opacity, Visibility, Camera, HUD. Settings none.

Receives ApplySettings, HoldLayout, MarkPinsDirty, NativePinHeld, QueueVisibleSettings, ReleaseLayoutPins, SettingChanged.

Sends PinsChanged, PinsReleased, RefreshMovers, ResetLayoutCaches.

### NativeFrames

Needs Placement, Shortcuts, Tooltip, Minimap, HUD, TargetDetails, Lifecycle. Settings none.

Sends HoldLayout, MoverGeometry, NativePinHeld.

### EditorRender

Needs Core only. Settings snapframes, snap, guidesize, showgrid, snapDistance, gridOpacity, moverLabels, grid.

Sends ResetAllPositions, UnlockEditor.

### MoverEditor

Needs Lifecycle, EditorRender. Settings none.

Receives CharacterChanged, ClearEditorDrag, DiscardEditor, DisconnectEditor, MoverGeometries, MoverGeometry, PrepareRecoverEditor, RecoverEditor, RefreshMoverRange, RefreshMovers, RestoreEditor, UnlockEditor.

Sends ApplySettings, EditorLocked, EditorStateChanged, HideOptions, OpenOptions, RefreshOptions, ReleaseLayoutPins, ResetAllPositions, ResetLayoutCaches.

### OptionsWindow

Needs Lifecycle, MoverEditor, CustomWindows. Settings none.

Receives BindCommands, ClearEditorDrag, GetOptionsCode, HideOptions, OpenOptions, OptionsCode, RefreshOptions.

Sends InstallInterface, OptionsCodeValue, ResetAllPositions.

### Profiles

Needs OptionsWindow. Settings profile.

Receives InstallInterface.

Sends ApplyCamera, ApplySettings, OpenOptions, OptionsCode, RefreshOptions.

### Watch

Needs Lifecycle. Settings none.

## Option tree

`NEW` marks added setting controls or actions. Existing controls may have a new caption or page. Descriptions and headers explain real surfaces and dependencies.

### General

- Appearance > Fonts > Data text font.
- Appearance > Colors > Colour theme, Custom accent [NEW].
- Appearance > Backdrops > Window and HUD opacity.
- Visibility > Native HUD > Experience bar, Buffs [NEW], Debuffs [NEW], Target auras [NEW].

### Unit Frames

- Visibility > Native frames > Player frame [NEW], Target of target, Party frames.
- Backdrops > Frame backdrops > Player [NEW], Target [NEW], Target of target [NEW], Party [NEW].
- Text > Resource formats > Target health [NEW], Target mana [NEW], Target DP [NEW], Target of target health [NEW], Abbreviate numbers [NEW], Tags [current], [max] and [percent] format the existing native resource labels. Requires the installed target readout surface.

### Action Bars

- General > Action bars > Use the child pages for independent bars. Background opacity can inherit the group value.
- General > Backdrops > Group background opacity.

### Bar 1

- General > Bar 1 > Enable [NEW], Buttons per row, Button size, Background opacity [NEW], Move bar [NEW].

### Bar 2

- General > Bar 2 > Enable [NEW], Buttons per row, Button size, Background opacity [NEW], Move bar [NEW].

### Bar 3

- General > Bar 3 > Enable, Buttons per row, Button size, Background opacity [NEW], Move bar [NEW].

### Bar 4

- General > Bar 4 > Enable, Buttons per row, Button size, Background opacity [NEW], Move bar [NEW].

### Bar 5

- General > Bar 5 > Enable, Background opacity [NEW], Move bar [NEW], Rows and button size use native geometry for separated bars..

### Bar 6

- General > Bar 6 > Enable [NEW], Background opacity [NEW], Move bar [NEW], Rows and button size use native geometry for separated bars..

### Chat

- Status line > Status line > Enable status line, Text format [NEW].
- Status line > Geometry > Width [NEW], Height [NEW], Left offset [NEW], Bottom offset [NEW].
- Appearance > Backdrops > Background opacity.

### Data Texts

- Panel > Data panel > Enable panel, Position [NEW], Height [NEW], Width [NEW], Slot separator [NEW].
- Panel > Backdrops > Background opacity [NEW].
- Slots > Data text sources > Left 1, Left 1 separator [NEW], Left 2, Left 2 separator [NEW], Left 3, Left 3 separator [NEW], Centre 1, Centre 1 separator [NEW], Centre 2, Centre 2 separator [NEW], Centre 3, Centre 3 separator [NEW], Right 1, Right 1 separator [NEW], Right 2, Right 2 separator [NEW], Right 3, Right 3 separator [NEW].
- Formats > Clock > Time format [NEW], Show supplied seconds [NEW].
- Formats > Numbers > Coordinate decimals [NEW], Abbreviate numbers [NEW].

### Tooltip

- General > Enable > Tooltips, Keep on screen.
- General > Anchors > World (NPCs, players), UI (buttons, items), Grow from (Mover), Tooltip mover.
- Offsets > World cursor offset > X offset, Y offset.
- Offsets > UI cursor offset > X offset [NEW], Y offset [NEW].
- Offsets > Mover preview > Preview width [NEW], Preview height [NEW].

### Minimap

- Information > Information label > Enable label, Position [NEW], Text format [NEW].
- World map > Saved position > Horizontal position [NEW], Vertical position [NEW], Both coordinates must be at least zero to restore this position. Native dragging continues to remember the map position..
- General > Visibility > Enable minimap [NEW].
- General > Minimap > Minimap size, Align map controls.

### Shortcuts

- Companions > Companion commands > Enable companion bar, Expand, collapse and icon mode are controlled by the native companion bar buttons and remembered automatically..
- General > Visibility > Shortcut menu.
- General > Shortcuts > Shortcut size, Shortcuts per row, Shortcut spacing, Align shortcuts.

### Camera

- General > Camera > Field of view, Maximum zoom distance.

### Movers

- General > Frame editor > Movers, All positions, Frame labels [NEW].
- General > Grid guides > Show grid, Guide spacing, Grid opacity [NEW].
- General > Snapping > Snap to grid, Snap to frames, Snap step, Frame snap distance [NEW].

### Profiles

- Profile > Active profile > Profile, New profiles copy the current settings and positions. Names use 1-24 letters, numbers, spaces, underscores or hyphens..
- Profile > Manage profiles > New profile name [NEW], Create and select profile [NEW], Rename current profile to [NEW], Rename current profile [NEW], Profile to delete [NEW], Delete selected profile [NEW].
- Profile > Copy or reset > Copy from profile [NEW], Copy into current profile [NEW], Reset current profile [NEW].
- Scope > Module storage > Module [NEW], Store this module in [NEW], Character settings stay with this character. Account settings are shared across characters. Fixed schema scopes keep their declared storage..
- Scope > Account profiles > Save current layout to account [NEW], Account profile [NEW], Use account profile [NEW].
- Scope > Copy from character > Character [NEW], Profile name. Empty uses their active profile [NEW], Copy character layout into current profile [NEW].
- Share > Export and import > Include settings from [NEW], Export code, Profile code [NEW], Import profile code, AFUI2 imports select the named profile and create it when needed. AFUI1 imports use the current profile. An empty box reads afui-export.cfg..
- Module copy > Copy one module > Source profile [NEW], Module [NEW], Copy module into current profile [NEW].
- Layouts / Install > Installation layouts > Apply DPS layout [NEW], DPS restores the standard interface and default frame placement., Apply Healer layout [NEW], Healer shows party, target-of-target and timers, with the party near the center., Apply Minimal layout [NEW], Minimal hides data panels, extra quick bars, target timers and the XP bar., Reset current profile [NEW], Layouts reset their named built-in profile. Your other profiles and character/account settings are preserved..

### Information / About

- About > Aetherfall Interface > Aetherfall Interface 1.3.0, AetherfallCore trace-fixture 1.3.15, Options and profiles are modular. /afui opens settings. /moveui opens the anchor editor..
- About > Text tags > Data texts use [zone], [coords], [time], [xp], [kinah], [fps], [group], [legion], [level], [hp]., Resource labels use [current], [max] and [percent]. Unknown tags remain visible so typos are easy to find..
- Capabilities > Native dependencies > Unit-frame replacement requires verified gauge construction, unit event subscriptions and secure native targeting., Nameplates require an engine draw and world-unit bridge. Cooldown overlays require verified slot geometry and text ownership., Aura rows require verified icon rendering and native expiration data. Bags and chat styling require native inventory and chat integration..

## Persistence and interaction decisions

- The profile cap is 24, additionally bounded by 32 KiB per file. Every failed profile-capacity operation rolls back its in-memory changes and reports `profile storage full`.
- The character pair, account pair and bounded character index contain data with checksums and terminators. Saved code is never executed. Corrupt or oversized slots fall back to the other valid slot or defaults.
- Old six-profile catalogs migrate to ordinary named profiles. Inactive built-in names can be renamed or deleted. Active deletion is rejected. Applying a built-in layout recreates its named profile when needed.
- AFUI2 imports create/select the exported profile name. AFUI1 imports retain the current destination. Account-profile use copies into the current profile. Full-profile copy includes positions. Module copy changes only the selected module's profile settings.
- Group scope changes retain each existing destination bucket's choices. A previously empty destination initializes from current effective settings. Account settings become shared by characters using that account scope.
- The old separated-bar switch seeds both bar instances during migration. Each new bar control changes its own instance. Old combined cursor offsets seed independent world/UI offsets.
- Sharing retains the old four-decimal position precision. Disk positions and custom windows retain the old five-decimal precision.
- Profile names contain 1–24 letters, digits, spaces, underscores or hyphens. Empty and all-space names are rejected.
- Preset themes and the Custom accent change HUD text and Interface accent text. Generated backgrounds and outline textures use the baked Aetherfall palette.

## Intentional limits and omitted controls

- Bars 5 and 6 have independent enable, backdrop opacity and mover controls. Their duplicate native separated-bar layout has no verified generalized 12-slot geometry contract here, so row and button-size controls are omitted.
- Arbitrary grid/chrome RGB tint needs a proven native image-color operation or a separately verified rendering implementation. The proven text-color path supports HUD accent text and the color swatch. Grid opacity and label visibility work now.
- Borders stay visible with the native setter's integer UI-unit truncation. Exactly one physical pixel at every scale requires native geometry verification. Scale 1 uses one-pixel lines.
- `timers`, `targettimers` and `warn` are retained compatibility data with no active timer-label consumer because the existing aura label pool is zero. They have no inert option controls. `tipcursor` and `opacitydirect` are migration markers. Companion mode belongs to the existing companion-bar buttons and is remembered automatically.
- Clock seconds are displayed only when the stock time source supplies them. No seconds are invented from the addon timer.
- Resource-format controls require the installed target readout surface and are disabled when it is absent. They use the existing native SetText entry points.
- There is no empty Data Bars page. Native XP-bar visibility is a real General control. A custom XP/AP/DP bar service needs verified gauge construction.

## ElvUI modules requiring engine work

| Module | Required native capability |
| --- | --- |
| Unit frames | Proven gauge construction, unit events, safe target/cast interaction and element ownership |
| Nameplates | World-unit enumeration, screen projection and a supported engine draw/ownership surface |
| Cooldown text | Verified action-slot bounds, cooldown values, overlay layering and text ownership |
| Auras | Verified icon rendering, aura events, expiration data and bounded icon pools |
| Bags | Inventory item/control ownership, native transactions, sorting and click behavior |
| Chat styling | Verified chat panels, font/history/filter APIs and native input/tab ownership |

## Verification and trace review

The original monolith references remain intact. Prefix exclusions cover only old/new options widget presentation and mover-editor skin setters. Native option-dialog calls resolve through the preserved fixture slot to its `AFUIOptions` name. Original input markers and all scenarios are retained.

The exact migration ledger records each changed input block, both ordered hashes, every specific call-count delta and its reviewed reason. Cumulative native state must match after every original input boundary. Save envelopes remain visible and are validated independently. A source-hash guard rejects a trace recorded while source changes. A reused trace is accepted only with matching source, raw-content and client-framework hashes.

Interface 1.3.4 passed 3,224 original input boundaries, including 1,169 exact reviewed migration blocks with zero cumulative native-state differences. The options/profile checks covered 253,374 ordered calls. Separate offline checks covered dropdown clicks, scaled title and slider input, accent colours, and anchor selection, dragging, Cancel and Shift-reset when captions overlap. The shipped Bar 5 and Bar 6 click handlers and native chat move-handle capture were checked separately in 1.3.2.

## Installer invocation after review

Use an external output directory and the reviewing installer's own client path.

```powershell
python client-mods/aetherfall-core/prepare.py --client $env:AION_CLIENT --output $env:AFUI_OUTPUT --update --plugin-only --options-window-update --mover-editor-update
```

The options update installs the generated startup dialog, includes every maintained Lua member through the manifest, reconciles the TOC and stages loose skin textures. The mover update adds the four outline widgets per mover box. Other archive members retain their compressed bytes.

Interface 1.3.3 draws a pooled dropdown above the options cells. Its passive arrow sits inside the button, and choice buttons use the same native click route as other settings. Slider hit bands cover their thumbs and title dragging uses the rendered dialog rectangle. The running game must still confirm arrow and slider alignment, title and anchor dragging, colour appearance, and scaled-window readability. Offline hashes and traces establish source behavior and packaging invariants.
