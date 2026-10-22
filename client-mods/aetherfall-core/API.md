# Aetherfall Core: Lua API for Aion 4.8 addons

Aetherfall Core gives plugin addons (`Plugin/<name>/<name>.pak`) the game data the
stock Lua API does not expose. You don't need to hook the client, read packets or
know any engine internals: call these functions from Lua. Install the core once
(`bin64/AetherfallCore.dll` + `AetherfallCore.lua`); every addon can then use it.

Check availability first. Every function returns `nil` when the core is missing.

```lua
if AFCore and AFCore.Loaded() then ... end
```

## Player and target data

| Function | Returns |
| --- | --- |
| `AFCore.Money()` | Kinah in the bag inventory |
| `AFCore.Exp()` | current XP (within the level), XP needed for the level, recoverable XP, repose current, repose max |
| `AFCore.FPS()` | engine frame rate |
| `AFCore.Unit(unit)` | object id, name, level, hp, max hp, mp, max mp: `"target"` or `"targettarget"` |
| `AFCore.NumAuras(unit)` | buff/debuff count: `"player"`, `"target"`, `"targettarget"` |
| `AFCore.Aura(unit, i)` | skill id, name, level, slot (0 buff, 1 debuff, 2 chant, 3/4 special, 5 boost), native remaining s, total (`nil`), raw remaining ms, effector, time mode |
| `AFCore.SkillName(id)` | localized skill name |

The stock API still covers names, levels, HP/MP, class, zone, map position and
party (`UnitName`, `UnitHP`, `GetZoneText`, `GetPlayerMapPosition`, ...).

Since 1.2.34, `NumAuras` returns `nil` when the bridge/unit data is unavailable,
and `0` only for an available empty list. Use `for i = 1, (AFCore.NumAuras(unit) or 0)`
when an unavailable unit should simply be skipped. Aura remaining time comes directly
from the native effect record; mode `2` means native remaining milliseconds, mode `0`
means no positive timed duration. Untimed effects return `nil` remaining. Original
total duration is not exposed by this native record and always returns `nil`.
No countdown is inferred from observation history or the Windows uptime clock.

`AFCore.Version()` returns protocol version `1` and a string containing the actual
native bridge build, for example `AetherfallCore 1.2.34`.

## Native windows

Native windows are addressed by dialog type id. `AFCore.DIALOG` names the common
ones (`basic_status`, `target`, `target_assist`, `party`, `radar`, `start`,
`shortcut`, `shortcut_ctrl`, `shortcut_alt`, `chat`, `chat_input`, `exp_bar`,
`buff_status`, `debuff_status`, `target_buff`, `skill_delay`, `target_delay`).

| Function | Returns / does |
| --- | --- |
| `AFCore.Dialog(id)` | layout name, x, y, width, height (screen pixels), shown, class RVA, instance identity, native flags (movable bit `0x8`). Instance identities are session-only. |
| `AFCore.Rect(id[, widget, kind])` | absolute pixel rectangle, using the client's conversion through all parent containers and native offsets (`kind`: `"itemview"`, `"static"`, `"gauge"`), and effective shown |
| `AFCore.Slot(id, i[, prefix])` | item view `prefix..i` (default `shortcut`): type, template id, id, count, gauge value, gauge max, shown |
| `AFCore.Gauge(id, widget)` | value, max, shown (for example the cast bars' `"delay"`) |
| `AFCore.Text(id, widget)` | text of a static widget |
| `AFCore.Move(id, x, y[, w, h])` | moves a native window (pixels), as the engine does; preserves zero-sized canvases such as Target Auras. False means no placement was applied. |
| `AFCore.SetShown(id, on)` | shows or hides a native window |
| `AFCore.Scale()` | engine UI scale, screen width, screen height (pixels) |
| `AFCore.ModPath(name)` | path under `Mods/AetherfallUI/`, whatever the client's working folder |
| `AFCore.Cursor()` | mouse x, y (screen pixels), left button down, Shift down; nil when the game is not in front. Use it to drag frames inside your own window |
| `AFCore.SaveFile(name, text)` | writes `Mods/AetherfallUI/<name>` (`[A-Za-z0-9_.-]`, ending `.cfg`, at most 32 KiB); true when written |

Plugin Lua dialogs use pixels divided by `GetUIScale()`. To place your frame on a
native widget, call `AFCore.Rect` and divide each value by `GetUIScale()`.
The shown results of `Rect`, `Slot` and `Gauge` include the owning dialog and all
parent containers, so locally shown content under a hidden bar/container is false.

## Window budget: 20 for all plugins

The client gives every plugin together 20 windows (`<Dialog>`s). A window created when all
20 are taken is deleted by the client; its Lua object stays but nothing you do to it shows.
The stock RelicCalc plugin uses 7 and Aetherfall Interface 5, so 8 are left for addons.
Put many frames inside one window as child widgets (bars, labels, buttons) instead of one
window per frame, and drag them yourself with `AFCore.Cursor()`, as Aetherfall's movers do.
`/afcore` lists the slots in use ("plugin window slots: N/20").

## Writing a plugin that loads

The client's plugin loader aborts a whole XML file at the first attribute it does
not know. Use only this vocabulary (taken from plugins that load):

- `Dialog`: `name`, `frame`, `text`, `preset`, `flag`, `title_height`, `title_offset`, `close_preset`, `close_offset`, `shadow_offset`
- `Widget`: `type`, `style`, `name`, `frame`, `preset`, `flag`, `text`, `font`, `tooltip`, `HAlign`
- dialog flags: `title`, `close`, `not_visible`, `not_movable`; static widgets: `not_active;truncate;not_multiline;not_wordwrap`
- `Scripts` with `OnLoad`, `OnEvent`, `OnClick`

Register events with `dialog:RegisterEvent('ADDON_TIMER')` (RelicCalc's shared
20 Hz one-shot chain). Consumers never call `SetTimer`/`ResetTimer`; the existing
Wardrobe owner controls the single chain and recovers it on world entry. An
explicit editor request may call `WardrobeNative_ResumeTimer()` to replace a
lost chain without adding another. `WardrobeNative_TimerStatus()` returns its
tick count, resume count and last timer-body error. Set `g_AddonName` in every
handler. Slash commands: `SlashCmdList.NAME = fn; SLASH_NAME1 = '/cmd'`. Use distinctive
names such as `/afcore`: in game, short ones such as `/aui` and `/ec` never reached
their handler (the native chat parser appears to claim them).
The client's Lua `io` can open and read files but has no `write`. Save settings with
`AFCore.SaveFile` and read them back with `io.open(AFCore.ModPath(name), 'r')`.
`SaveValue` only accepts the game's social keys.

`/afcore` prints everything above for the current character, plus a diagnosis of
which interface parts loaded.

## Window control (core 1.2.16 and later)

All positions are screen pixels (the engine's screen size from `AFCore.Scale()`).

| Function | What it does |
|---|---|
| `AFCore.Move(dialog, x, y[, w, h])` | Native SetRect of a game window (top-left, optional size). |
| `AFCore.Pin(dialog, x, y[, mode, clamp, w, h, widget, kind])` | Keeps a window (or one of its widgets) at a spot whenever the game re-places it, applied at once. Modes: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right corner at x,y; 4 beside the mouse (x,y offset); 5 off screen. `clamp` keeps it on screen; `w,h` above 0 replace the game's size. `widget` is a name, `"#n"` (the dialog's n-th child) or `"#n/name"`; `kind`: `"button"`, `"container"`, `"itemview"`, `"static"`, `"flygauge"`, `"any"`. `AFCore.Pin(dialog, nil x6, widget)` releases one pin, `AFCore.Pin()` all. Widget spots are relative to their parent. |
| `AFCore.PinInfo(dialog[, widget])` | pinned, SetRect calls caught, times the game recreated the window, shown. |
| `AFCore.TipPin(template, wmode, wx, wy, umode, ux, uy, clamp)` | Configures world/UI placement of live outer tooltips. The shared `tooltip_proto` supplies both policies; the skill template remains an untouched inner layout. Modes match `Pin`, with -1 meaning native placement. Both native modes release the guarded class hooks. |
| `AFCore.Gaps(dialog, on)` | The window takes the mouse only over its pinned widgets (the start menu's spread-out buttons). |
| `AFCore.HitTest(dialog, x, y)` | Whether the window takes the mouse at that point, as the game asks it. |
| `AFCore.Movable(dialog, on)` | The native "movable" flag: drag the window by its frame (the world map). |
| `AFCore.SetShown(dialog, on)` | Native show/hide. |
| `AFCore.Dialog(dialog)` | layout name, x, y, w, h, shown, class (vtable RVA), session-only instance identity, native flags (`0x8` movable). The original seven return fields retain their order. |
| `AFCore.Cursor()` | mouse x, y, left button down, Shift down (nil when the game is not in front). |
| `AFCore.Command(text)` | One line through the chat box as if typed and sent, e.g. `"/say .broker"`. |
| `AFCore.SaveFile(name, text)` / `AFCore.ReadFile(name)` | Bounded `name.cfg` files in `Mods/AetherfallUI`. |

`/afcore test` runs these against the live game and prints PASS/FAIL lines; `/afcore watch`
lists every game window that opens during 15 seconds with its class (to identify windows such
as tooltips).
