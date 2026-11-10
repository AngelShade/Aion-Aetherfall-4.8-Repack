# Aetherfall Interface architecture

The contract for how the interface Lua is organised. Read this before adding or
changing anything under `client-mods/aetherfall-core`. Its purpose is that a
change inside one module cannot break another module, and that a new module
(unit frames, cooldown text, nameplates) plugs in without editing existing ones.

## Why

Until Lua 1.2.94 the whole interface was `AetherfallOptions.lua`: 2,650 lines,
one state table `U` with 99 fields that every feature read and wrote (settings,
placement, movers, tooltips, minimap, start menu, action bars, opacity, custom
windows, options window, profiles, lifecycle), plus other files reaching into
it (`AFUI.movers`, `AFUI.db`, `AFUI.positions`). Any edit could change behaviour
anywhere. Features were added by threading more state through the same table.

## Layers

Dependencies point downwards only. A file may use the layers below it and
nothing beside or above it.

```
4  Compat        AFUI.* legacy global names kept for other plugins and scripts
3  Modules       one feature each; talk to each other only through Core
2  Core          Engine, Events, DB, Native, Movers, Config, Skin
1  AFCore        AetherfallCore.lua wrappers over the native bridge (unchanged)
0  Native        AetherfallCore.dll, the client's own Lua widget framework
```

### Core (`AF`)

One global table, `AF`. Core holds no feature logic.

| Part | Owns | Public surface |
| --- | --- | --- |
| Engine | module registry and lifecycle, error isolation | `AF:NewModule(name, spec)`, `AF:GetModule(name)`, `AF:Call(module, method, ...)` |
| Events | client events, the shared 20 Hz timer, internal messages | `m:On(event, fn)`, `m:Every(ticks, fn)`, `m:After(ticks, fn)`, `AF:Send(message, ...)`, `m:Listen(message, fn)` |
| DB | settings schema, validation, load/save, profiles, migration, export/import | `m.db.<key>`, `AF.DB:Set(module, key, value)`, `AF.DB:Profile*`, declared in the module spec |
| Native | dialogs, rectangles, screen and scale conversion, identity of the logged-in character | `AF.Native.*` (no feature state) |
| Movers | registry of movable things and their saved positions | `AF.Movers:Register(id, spec)`; the editor UI is a module |
| Config | registry of option trees | `AF.Config:Register(module, tree)`; the options window is a module that renders any tree |
| Skin | colour tokens, textures, apply helpers | `AF.Skin.*` |

### Modules

A module is one file that returns nothing and registers itself:

```lua
local M = AF:NewModule('ActionBars', {
    needs = {'Visibility'},            -- load-order and enable-order dependency, checked at start
    defaults = {                       -- the module's whole settings schema
        row1 = {12, 'int', 1, 12, 1},
        show3 = {true, 'bool'},
    },
    legacy = {abrow1 = 'row1', altbar = 'show3'},   -- old flat keys in saved files and export codes
})
function M:OnEnable() ... end          -- character entered the world, profile loaded
function M:OnDisable() ... end         -- logout, character switch, or the module was switched off
function M:OnSetting(key, value) ... end
function M:OnProfile() ... end         -- whole profile replaced: re-apply everything
```

Planned modules from the existing code: Lifecycle (world entry, focus, screen
size, suspend), Placement (design positions and holds), Visibility (hide and
restore native windows), ActionBars, Tooltip, Minimap, Shortcuts (start menu),
Opacity (backdrops), CustomWindows, Camera, DataTexts, TargetDetails, Identity,
MoverEditor, OptionsWindow, Profiles.

## Rules

1. **No shared state.** A module's state lives in its own locals or `M`. Nothing
   reads or writes another module's table. There is no `U`.
2. **Settings belong to their module.** Declared in `defaults`, read through
   `M.db`, changed only through `AF.DB:Set`. DB validates against the schema, so
   modules never validate saved data themselves. Another module's setting is not
   readable; if two modules need one value it belongs in a module both `need`,
   exposed by a function.
3. **Cross-module calls are explicit.** Either a message (`AF:Send('BarsMoved')`
   with listeners) or a method on a module listed in `needs`. Messages are the
   default; a direct call is for a required answer.
4. **Errors are contained.** Engine runs every module entry point protected. An
   error is reported in chat with the module's name, once per distinct message,
   and the rest keep working. A native window being rebuilt can fail one tick and
   work the next, so a module is switched off for the session only after three
   errors in a row.
5. **No work without a subscriber.** Timers exist only while a module asked for
   them (`Every`/`After`); a disabled module costs nothing. Nothing polls while
   its feature is off or its window is closed.
6. **Options are data.** A module describes its options as a tree registered with
   Config. It never creates option widgets. The options window never contains
   feature knowledge. Adding an option is adding a row to the owning module.
7. **Movers are registered, not hard-coded.** A module that owns something movable
   registers it; the editor knows nothing about specific frames.
8. **Persistence is one place.** Only DB touches `AFCore.SaveFile`/`ReadFile`.
   File formats are versioned; old files and old export codes migrate through
   each module's `legacy` map and DB's migration list.
9. **Compat is the only legacy surface.** `AFUI.Get`, `AFUI.Click`, `AFUI_OnEvent`,
   `AFUI_OnLoad`, `AFUI.Request` and the other names used outside this folder live in
   `Compat` as thin forwards. New code never calls `AFUI.*`.
10. **Globals:** `AF`, `AFCore`, the Compat names, and widget names created by the
    client framework. Nothing else.

## Timer phases

One 20 Hz timer drives everything. The order of work inside a tick fixes real
bugs, so it is explicit: `Lifecycle` runs the phases below in this order and a
module subscribes to a phase by name (`M:Phase(name, fn, priority)`), never by
relying on which file loaded first.

`native-drag`, `custom-windows`, `requests`, `editor`, `holds`, `fit-windows`,
`watch`, `discover`, `visible-settings`, `remember-shown`, `save`.

Client events reach modules through `AF.Events:Client(event)`; `Lifecycle`
handles each event before `HUD` (priorities 0 and 10).

## Files and build

Source lives in folders; the plugin archive is flat, so the build flattens names.

```
client-mods/aetherfall-core/
  core/      Engine.lua Events.lua DB.lua Native.lua Movers.lua Config.lua Skin.lua
  modules/   <Name>.lua            one per module
  compat/    Compat.lua
  layout/    Python generators for the plugin XML (one per window)
  tests/     trace harness, per-module tests, dependency lint
  load_order.json                  the single list of Lua files, in load order
```

`prepare.py` reads `load_order.json` for the archive members and the `.toc`
order. No other place lists the Lua files.

## Tests that guard the rules

- **Golden trace.** `tests/trace` drives fixed scenarios (login, every option
  changed, movers dragged, profile switched, export and import, logout, character
  switch, focus loss, resize) and records every call made to the client
  (`AFCore.*`, widget calls, chat, saved files). The trace recorded from the
  1.2.94 monolith is the reference: a refactor step is correct only when its
  trace is identical. Intended behaviour changes update the reference in the same
  commit, with the diff reviewed.
- **Per-module tests.** Each module loads with Core and stubs only, no other
  module present except its `needs`. A module that cannot load alone is coupled.
- **Dependency lint.** Fails the build when a module file names another module's
  table, `AFUI`, a Compat name, `AFCore.SaveFile`/`ReadFile`, or a Core-private
  field; and when a file exceeds 600 lines.
- **Load budget.** The existing queued-call limit check (`measure_calls.py`).

## Adding a feature

1. New file in `modules/`, add it to `load_order.json`.
2. Declare `needs`, `defaults`, option tree, movers.
3. Implement `OnEnable` / `OnDisable` / `OnSetting`.
4. Add its per-module test and scenarios to the trace.

Nothing else is edited.

## Planned extensions (from `ELVUI-REFERENCE.md`)

Not part of the module split. The split must not make any of these harder; each
lands as its own change with its own trace scenarios.

1. **Three setting scopes.** Every key is declared `profile` (look and layout,
   switchable and shareable), `character` (per character, mostly which modules
   are on) or `account` (shared by all characters). Today all keys are effectively
   `profile` inside a per-character file.
2. **Templates.** A repeated thing (action bar 1 to 6, each unit frame) is one
   schema template instantiated by name; modules receive the instance's `db`.
   No hand-copied per-instance keys such as `abrow1..abrow4`.
3. **Lazy option trees.** Option trees live in `options/<Module>.lua` as functions
   evaluated on the first `/afui`. The options dialog itself is built on first
   use, never in the plugin's one-pass load (measured 3,723 of 4,000 queued calls
   on 10 Nov 2026, so the load has no room for a larger window).
4. **Service registries.** Data texts, cooldown text, status bars and text tags
   are Core-level registries; each entry is its own small file.
5. **Movers carry a category and the id of their options page.**
6. **One styling layer.** Colours, borders, fonts and textures are applied only
   through `AF.Skin` templates, pixel-exact at any UI scale.
