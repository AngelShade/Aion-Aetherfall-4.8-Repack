# Aetherfall UI style

The house style for every Aetherfall web window (Central Market, Season Pass,
Cash Shop) and other branded UI. Look: Aion's dark broker/warehouse windows,
lit with Aetherfall's ice-silver and aether glow. Reference implementation:
`game-server/config/central-market/media/market.css`.

## Palette

| Token | Hex | Use |
| --- | --- | --- |
| abyss | `#070d16` | page background, footer |
| deep | `#0a1521` | side columns, warehouse |
| surface | `#0e1b29` | panel titles, cards, table rows |
| raised | `#142538` | active tab/category, book heading |
| line / line-hi | `#1f3447` / `#2e4a61` | dividers / control borders |
| ice / mist / dim | `#dfe9f0` / `#93abbc` / `#6f889a` | text / secondary / hints |
| aether / aether-deep | `#8fe3ec` / `#3a9fbe` | focus, selection, crest, meters, charts |
| gold / gold-hi / gold-deep | `#d6b878` / `#f1dca8` / `#8a6d3c` | trim, prices, headings, section rules |
| title | `#f4e8c8` | window titles |
| rise / fall | `#8fd8b4` / `#e8a08e` | gains, availability / losses, errors |

Item grades follow Aion: common `#dfe9f0`, superior `#8bcb6d`, heroic `#6fb0f5`,
fabled `#f1d08a`, eternal `#f0a85e`, mythic `#d395ee`.

## Language

- Georgia serif for titles, headings and large Kinah values; Tahoma for everything else.
- Gold 1px rule under the header, tab bars, panel titles and table headings; cyan
  for selection and focus. Gold = value/ownership, cyan = aether/interaction.
- Ornament: `✦` crest and section marks (cyan). The realm name appears as
  letter-spaced gold `AETHERFALL` small caps beside the window title.
- Primary actions are gold bevel buttons; everything else is flat navy with a
  lighter top border.

## Rendering rules (embedded Awesomium WebKit, performance first)

- No CSS variables, `calc`, flexbox or grid; ES5 script. Use fixed absolute panes.
- Flat fills. Gradients only on fixed small chrome (header, primary buttons, meters).
- No transitions, animations, filters, blur, `backdrop-filter`, text shadows on
  scrolling content, or box shadows on rows/buttons. One translucent layer max (dialog shade).
- Hover/selection changes colours and pre-reserved borders/outlines only, never size.
- Script toggles layout with classes; avoid reading `offsetHeight`/layout to position panes.
- Images load lazily for visible rows only; no background art behind scrolling lists.
- Bump `?v=` asset versions in the HTML whenever CSS/JS change.

## Client interface

The switchable in-game skin addon follows this guide; its plan is
`docs/AETHERFALL_UI_MOD_PLAN.md`.

### Native animation and effects

For future animated in-game visuals, follow the recruitment pull's native
texture playback approach: author/render expensive glow, particles and
compositing offline, export a finite DDS sequence (DXT1 for opaque scenery,
DXT5 for transparent effects), and play it in the existing native UI timer.
Keep the real character in the native 3D view and names/rolled values in their
native widgets. Do not animate these full-screen effects in Awesomium.
Use bounded texture sizes/frame counts, reuse static plates, change only the
active frame, and stop finite effects when complete or when the screen closes.
Move/fade a composed native panel as a group; its contents enter together.
Do not stretch ornamental art or add branding bars to the recruitment reveal.
Decoration colours do not imply a combined companion quality: Auto-equip and
Combat rank are separate recorded results.

## Companions window

The Journey continuation page uses this same skin and its shared card files.

Companions uses authored castle backgrounds, illustrated class portraits and
ornamental silver/gold window chrome, with silver/navy bevel controls and gold
primary actions. Apply this skin across Party, all companion detail tabs,
collection/saved parties, creation and the quest journal. Shared styles and
packaged artwork live in `game-server/config/playerbots/media`.

Controls and panels use the generated raster material plates (`skin-button-*`,
`skin-field`, `skin-panel`) and painted `skin-icons` atlas. Carry those actual
textures through every view; CSS-only gradients, plain border approximations
and line-drawn icon substitutes do not satisfy this visual direction. CSS
handles layout, readable runtime labels and interaction states. Native item and
class identities/tooltips retain their proper data owners.

Use ivory-white titles and bright ice-blue secondary text on the dark material
surfaces. Navigation, tabs and enabled toggles use `skin-button-selected.png`
(blue/silver), not a yellow/gold selected fill. Reserve gold artwork for primary
action buttons. Keep semantic availability/error and native item-grade colors.

Companions refresh is explicit; do not restore a timer that replaces the user's
open panels. Navigation reuses a bounded set of detail panels and invalidates
them on new server data. Preserve drafts, scrolling and native action admission.
The legacy renderer paints nine-slice centres: avoid painting a second scaled
copy of the same texture beneath each control. Retain the authored image skin.

Portrait cards may use a short, input-triggered flip, with reduced-motion and
compatibility fallbacks. This artwork/flip direction is an explicit exception
to the flat/no-animation preferences above, limited to Companions. Retain legacy
layout compatibility, native item/class icons and tooltips, visible-page asset
loading and change-driven work; no continuous whole-grid effects. Keep text
inside each frame's clear inner area and let names/build labels wrap.

The Companions categories stay in the existing left sidebar in every view.
Roster portraits flip in place on the same card at every window width; do not
replace that action with a separate selected-card preview or inspector. Preserve
the ornate frame's native 2:3 ratio and the portrait atlas tile's 3:4 ratio.
Use proportional card sizing and ordinary pane scrolling rather than stretching
the artwork or moving the categories to a top bar to fit more cards onscreen.
