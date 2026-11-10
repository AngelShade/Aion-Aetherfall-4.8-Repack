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

Implemented modules include Lifecycle (world entry, focus, screen
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

Client events reach modules through `AF.Events:Client(event, ...)`. Compat and
generated event scripts forward widget arguments unchanged. `Lifecycle` handles
each event before `HUD` (priorities 0 and 10).

## Files and build

Source lives in folders; the plugin archive is flat, so the build flattens names.

```
client-mods/aetherfall-core/
  core/      Engine, Events, DB and codecs, Native, Movers, Config, Skin, Widgets, DataTexts, Tags
  modules/   <Name>.lua            one per module
  compat/    Compat.lua
  options/   <Module>.lua            deferred factories, no widget construction
  datatexts/ <Name>.lua              one passive registered source per file
  tags/      <Name>.lua              one registered text tag per file
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

## Implemented extension points

### Scoped settings and named profiles

Every module declares a default `scope` of `profile`, `character` or `account`.
A schema rule may override it with `rule.scope`. Profile settings switch with a
named layout. Fixed character and account rules retain their declared scope.
`AF.DB:SetModuleScope(owner, scope)` lets a player retain an otherwise profile
scoped group with the character or share it through the account bucket.

DB alone reads and writes the alternating `ui-<sha1>.{0,1}.cfg` character pair,
`afui-account.{0,1}.cfg` account pair and bounded `afui-characters.cfg` index.
AFUI2 records are data with a sequence, checksum and terminator. The bridge limit
is 32 KiB per file. Invalid files fall back to the other valid slot or defaults.
Profile operations validate and check capacity before committing their in-memory
changes. The six original profiles retain their names and legacy migrations.
The profile cap is 24, with capacity also constrained by the byte limit.

`core/DB.lua` owns schema validation and read-only views. `DBStorage.lua` owns
bounded disk records. `DBProfiles.lua` owns named profile operations.
`ProfileCodec.lua` owns AFUI1 compatibility and AFUI2 sharing. These files share
DB-private state. Feature modules never access that state. New file formats and
settings never become executable Lua.

### Schema templates

`AF.DB:RegisterTemplate(name, schema)` declares repeated settings once. A module
uses `instances = {bar1 = {template='ActionBar', defaults={...}, legacy={...}}}`.
An instance may select supported fields with `only`. `M.db.bar1.rows` is a
read-only instance view. `AF.DB:Set('ActionBars', 'bar1.rows', value)` validates
and dispatches to its owner. Legacy aliases retain old saved values. There is no
shared mutable template instance. Bars 5 and 6 select enable and opacity fields
while their native geometry remains unchanged.

### Lazy options and widget toolkit

`options/<Module>.lua` registers a factory with
`AF.Config:Register('Module', function() return pages end)`. Factories run when
first requested by `/afui`. The initial plugin XML contains no options dialog.
The first queued request builds the single options dialog from the first entry
of `AFUI_LAZY`, then waits two ticks before showing it. The five-dialog budget
includes this lazy dialog.

Pages declare category, optional parent, tabs, captioned groups and options.
Config normalizes and merges contributions by page and tab. The renderer owns
navigation, search, pagination and title dragging. `AF.Widgets` owns pooled
control rendering and interaction. Neither layer knows feature settings.
Options use `key`, or protected owner `get` and `set` callbacks, and support
`disabled`, `hidden`, `order`, `desc`, `width` and destructive confirmation.
Cross-module owner calls remain protected through Engine.

The option kinds are toggle, range, select, color, execute, input, header and
description. Real combobox values are zero-based. Step buttons and Apply buttons
provide the native event fallback. Window position uses CustomWindows and the
existing DB custom-window store. No second position database exists.

### Services and tags

`AF.DataTexts:Register(name, {label=..., text=function(context) ... end})`
registers a passive source. Each of the ten original sources lives in its own
`datatexts/` file. HUD subscribes by evaluating only its configured slots.
The registry creates no widgets, timers or module dependencies.

`AF.Tags:Register(name, formatter)` and `AF.Tags:Format(format, context)` provide
plain-text `[tag]` interpolation. Registered data sources are available as tags.
Resource labels use `[current]`, `[max]` and `[percent]`. Unknown tags remain
visible. Text formats are bounded strings and are never executed. Cooldown and
status-bar registries remain future work until a real consumer and verified
engine surface exist.

### Mover metadata and skin

Movers carry a category and options page through `page`, the legacy `tab` alias
or their owner's `settings` callback. Setup opens that page. Native action-bar
movers link directly to Bar1 through Bar6.

`AF.Skin` owns palette tokens, textures and Panel, Button, Check, Field and text
helpers. It applies images protectively so input survives missing art. Borders use line images and scale conversion, with a one-native-UI-unit
minimum because the native setter truncates fractional dimensions. `options_skin.assets()`
generates loose DDS assets under `Textures/UI/AetherfallOptions`. Both the
options window and mover editor use this styling layer. HUD content and native
resource artwork retain their feature-owned formatting.

### Runtime work

The options window performs no native work while closed. While open, only an
active title or range drag polls the cursor. Search and controls are event or
button driven. The existing shared timer and lifecycle phase order remain the
sole scheduler.
